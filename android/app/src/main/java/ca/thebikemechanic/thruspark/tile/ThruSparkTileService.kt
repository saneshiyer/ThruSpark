package ca.thebikemechanic.thruspark.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import ca.thebikemechanic.thruspark.data.ProfileStateStore
import ca.thebikemechanic.thruspark.engine.ProfileEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "ThruSparkTile"

/**
 * Quick Settings tile — the primary one-tap activation surface.
 *
 * To add the tile: pull down Quick Settings → tap the edit/pencil icon →
 * find "ThruSpark" and drag it into your tiles.
 *
 * v0.5+: the tile observes ProfileStateStore.isActiveFlow while it's listening
 * (between onStartListening and onStopListening), so the on/off badge updates
 * the moment the underlying state changes — no more 300ms-polling-then-hope hack.
 */
class ThruSparkTileService : TileService() {

    /**
     * Per-instance coroutine scope. Cancelled in onStopListening so we don't
     * leak observers when the tile collapses. SupervisorJob so a single
     * collection failure doesn't tear down the parent.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateJob: Job? = null

    /**
     * Called by the system when the user expands the Quick Settings shade and
     * the tile becomes visible. We start observing here so the badge is
     * accurate from the first frame the user sees.
     */
    override fun onStartListening() {
        super.onStartListening()
        stateJob?.cancel()
        stateJob = scope.launch {
            ProfileStateStore.isActiveFlow(this@ThruSparkTileService).collect { active ->
                renderTile(active)
            }
        }
    }

    /**
     * Called when the shade collapses. Stop observing so we don't burn cycles
     * when nothing's reading the badge.
     */
    override fun onStopListening() {
        super.onStopListening()
        stateJob?.cancel()
        stateJob = null
    }

    /**
     * One-tap toggle: if a profile is active, deactivate; otherwise activate
     * the user's last-used profile (resolved by ProfileEngine.activateDefault).
     * No badge-refresh hack needed — the Flow observation in onStartListening
     * picks up the state change as soon as ProfileStateStore writes it.
     */
    override fun onClick() {
        super.onClick()
        val isActive = ProfileStateStore.isActive(this)
        Log.d(TAG, "Tile tapped — currently active: $isActive")
        if (isActive) {
            ProfileEngine.deactivate(this)
        } else {
            ProfileEngine.activateDefault(this)
        }
    }

    private fun renderTile(active: Boolean) {
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "ThruSpark"
            subtitle = if (active) "Active" else "Off"
            updateTile()
        }
    }
}
