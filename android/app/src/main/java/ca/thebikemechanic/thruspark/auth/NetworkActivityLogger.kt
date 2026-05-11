package ca.thebikemechanic.thruspark.auth

import android.content.Context
import ca.thebikemechanic.thruspark.data.NetworkActivityEntry
import ca.thebikemechanic.thruspark.data.NetworkActivityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that logs every request to NetworkActivityStore so the
 * user can audit ThruSpark's network behavior in Settings → Network activity.
 *
 * Logging is fire-and-forget on a background scope — we never block the
 * actual network call on storage I/O.
 *
 * URL params are stripped before logging in case they ever contain secrets;
 * we only persist the path. Sign-in calls log as "POST /auth/v1/token" not
 * "POST /auth/v1/token?grant_type=password".
 */
class NetworkActivityLogger(private val appContext: Context) : Interceptor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val started = System.currentTimeMillis()
        val resp = chain.proceed(req)
        val duration = System.currentTimeMillis() - started

        // Strip query params before logging — keep only host + path
        val safeUrl = req.url.let { url ->
            "${url.scheme}://${url.host}${url.encodedPath}"
        }

        scope.launch {
            runCatching {
                NetworkActivityStore.append(
                    appContext,
                    NetworkActivityEntry(
                        timestampMs = started,
                        method = req.method,
                        url = safeUrl,
                        statusCode = resp.code,
                        durationMs = duration
                    )
                )
            }
        }
        return resp
    }
}
