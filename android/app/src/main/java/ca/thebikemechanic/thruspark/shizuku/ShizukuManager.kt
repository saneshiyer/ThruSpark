package ca.thebikemechanic.thruspark.shizuku

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private const val TAG = "ShizukuManager"
private const val PERMISSION_REQUEST_CODE = 7301

enum class ShizukuState {
    Unknown,                // not yet checked
    NotInstalled,           // Shizuku app is not on this device
    BinderUnavailable,      // Shizuku app installed but service not running (user needs to launch Shizuku and start it)
    UnsupportedVersion,     // Pre-v11 Shizuku, our integration won't work
    NeedsPermission,        // ready to ask, user hasn't granted yet
    PermanentlyDenied,      // user said "deny + don't ask again"
    Granted                 // good to go
}

sealed class ShellResult {
    data class Success(val exitCode: Int, val stdout: String, val stderr: String) : ShellResult() {
        val ok: Boolean get() = exitCode == 0
    }
    data class Error(val message: String) : ShellResult()
}

/**
 * Singleton wrapper around the Shizuku SDK.
 *
 * Lifecycle:
 *  - init() must be called once (from Application or first activity).
 *  - The state flow updates whenever the Shizuku binder connects, disconnects,
 *    or a permission request resolves.
 *  - Call requestPermission() to prompt the user; the result arrives via the
 *    listener and updates state automatically.
 *  - Call exec() to run a shell command via the Shizuku binder.
 *
 * Shizuku permission survives app updates (it's tied to package signature),
 * so we don't need to re-prompt on every launch — just check current state.
 */
object ShizukuManager {

    private val _state = MutableStateFlow(ShizukuState.Unknown)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private var initialized = false

    fun init(context: android.content.Context) {
        if (initialized) {
            updateState(context)
            return
        }
        initialized = true

        Shizuku.addBinderReceivedListener {
            Log.d(TAG, "Shizuku binder connected")
            updateState(context)
        }
        Shizuku.addBinderDeadListener {
            Log.d(TAG, "Shizuku binder dead")
            _state.value = ShizukuState.BinderUnavailable
        }
        Shizuku.addRequestPermissionResultListener { code, _ ->
            if (code == PERMISSION_REQUEST_CODE) {
                Log.d(TAG, "Shizuku permission result received")
                updateState(context)
            }
        }
        updateState(context)
    }

    fun requestPermission() {
        when (_state.value) {
            ShizukuState.NeedsPermission -> {
                try {
                    Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
                } catch (e: Exception) {
                    Log.e(TAG, "requestPermission failed", e)
                }
            }
            else -> Log.w(TAG, "requestPermission called in state ${_state.value} — ignoring")
        }
    }

    /**
     * Reflection handle for Shizuku.newProcess(). The method is @RestrictTo(LIBRARY_GROUP)
     * in Shizuku v13, so a direct call won't compile — but the method exists in the JAR.
     * The "official" path is to bind a custom UserService; that's planned for v0.2.
     * For v0.1, reflection lets us run the simple shell commands Tier 2 needs.
     */
    private val newProcessMethod by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }
    }

    /**
     * Run a shell command via Shizuku. Use sparingly — each call spawns a
     * subprocess. Returns Error if Shizuku isn't granted.
     *
     * Examples:
     *   exec("settings put global airplane_mode_on 1")
     *   exec("svc wifi disable")
     *   exec("pm disable com.example.app")
     */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        if (_state.value != ShizukuState.Granted) {
            return@withContext ShellResult.Error("Shizuku not granted (state: ${_state.value})")
        }
        try {
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process
            process.waitFor()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            ShellResult.Success(process.exitValue(), stdout, stderr).also {
                Log.d(TAG, "exec '$command' → exit=${it.exitCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "exec '$command' threw", e)
            ShellResult.Error(e.message ?: "unknown")
        }
    }

    /**
     * Recompute state. Public so the Setup screen can refresh after the user
     * installs Shizuku or starts the service externally.
     */
    fun refresh(context: android.content.Context) = updateState(context)

    private fun updateState(context: android.content.Context) {
        if (!isShizukuInstalled(context)) {
            _state.value = ShizukuState.NotInstalled
            return
        }
        if (!Shizuku.pingBinder()) {
            _state.value = ShizukuState.BinderUnavailable
            return
        }
        if (Shizuku.isPreV11()) {
            _state.value = ShizukuState.UnsupportedVersion
            return
        }
        _state.value = when {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.Granted
            Shizuku.shouldShowRequestPermissionRationale() -> ShizukuState.PermanentlyDenied
            else -> ShizukuState.NeedsPermission
        }
    }

    private fun isShizukuInstalled(context: android.content.Context): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
