package ca.thebikemechanic.thruspark.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.Ringtone
import android.util.Log

private const val TAG = "AlarmSoundPlayer"

/**
 * Plays and stops the system alarm ringtone.
 *
 * Uses the user's default alarm sound (set in system settings), which means
 * the alarm will feel familiar — the same sound their old alarm clock used.
 *
 * Call play() when AlarmActivity appears and stop() when the user dismisses
 * or snoozes.
 */
object AlarmSoundPlayer {

    private var ringtone: Ringtone? = null

    fun play(context: Context) {
        if (ringtone?.isPlaying == true) return   // already playing, nothing to do

        // Fetch the user's default alarm sound URI
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) // fallback

        val rt = RingtoneManager.getRingtone(context, uri) ?: run {
            Log.e(TAG, "Could not load ringtone")
            return
        }

        // Route audio through the ALARM stream so volume control works as expected
        rt.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_ALARM)
            .build()

        rt.isLooping = true
        rt.play()
        ringtone = rt
        Log.d(TAG, "Alarm sound started")
    }

    fun stop() {
        ringtone?.let {
            if (it.isPlaying) it.stop()
        }
        ringtone = null
        Log.d(TAG, "Alarm sound stopped")
    }
}
