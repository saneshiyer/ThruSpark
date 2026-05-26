package ca.thebikemechanic.thruspark.data

import android.content.Context
import android.content.Intent
import ca.thebikemechanic.thruspark.model.AlarmEntry
import ca.thebikemechanic.thruspark.model.ThruSparkProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GDPR Article 20 ("right to data portability") implementation.
 *
 * Bundles everything ThruSpark stores about the user:
 *  - all custom profile JSON
 *  - all alarms
 *
 * Output is shared via Android's standard share sheet as plain text JSON. The
 * user picks where it goes (email, Drive, copy to clipboard, etc.).
 *
 * Note: data stored in CustomProfileStore as JSON files is already in the
 * correct schema, so we just embed it verbatim. No transformation needed.
 */
object DataExporter {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class ExportPayload(
        val exportedAtMs: Long,
        val schemaVersion: String = "1.0",
        val customProfiles: List<ThruSparkProfile>,
        val alarms: List<AlarmEntry>
    )

    suspend fun buildJson(context: Context): String {
        val profiles = CustomProfileStore.list(context)
        val alarms = AlarmEntryStore.getAll(context)
        val payload = ExportPayload(
            exportedAtMs = System.currentTimeMillis(),
            customProfiles = profiles,
            alarms = alarms
        )
        return json.encodeToString(ExportPayload.serializer(), payload)
    }

    fun share(context: Context, jsonContent: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "ThruSpark data export")
            putExtra(Intent.EXTRA_TEXT, jsonContent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Export ThruSpark data").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
