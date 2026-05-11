package ca.thebikemechanic.thruspark.util

import android.content.Context

/**
 * Maps abstract app categories (used in profile JSON) to concrete package names.
 * detectInstalled() returns only packages that are actually installed on this device.
 */
object AppCategories {

    val CATEGORY_PACKAGES: Map<String, List<String>> = mapOf(
        "nav" to listOf(
            "com.trailbehind.android.gaiagps",       // Gaia GPS
            "net.osmand",                             // OsmAnd
            "net.osmand.plus",                        // OsmAnd+
            "com.komoot.app",                         // Komoot
            "com.ridewithgps.mobile",                 // Ride with GPS
            "app.organicmaps",                        // Organic Maps
            "menion.android.locus",                   // Locus Map
            "com.mapswithme.maps.pro",                // MAPS.ME Pro
            "com.garmin.android.apps.connectmobile",  // Garmin Connect
            "com.alltrails.alltrails"                 // AllTrails
        ),
        "messaging" to listOf(
            "com.whatsapp",
            "org.thoughtcrime.securesms",             // Signal
            "com.google.android.apps.messaging",      // Google Messages
            "org.telegram.messenger",
            "com.discord"
        ),
        "emergency" to listOf(
            "com.garmin.android.apps.inreach",        // Garmin inReach
            "com.zoleo.app",                          // Zoleo
            "com.findmespot.spot",                    // SPOT
            "com.bivy.app",                           // Bivy Stick
            "com.garmin.android.apps.explore"         // Garmin Explore
        ),
        "camera" to listOf(
            "com.google.android.GoogleCamera",
            "net.sourceforge.opencamera",
            "org.codeaurora.snapcam",
            "com.sec.android.app.camera",             // Samsung Camera
            "com.oneplus.camera"
        ),
        "media" to listOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.audible.application",
            "com.amazon.kindle",
            "com.google.android.apps.books",          // Google Play Books
            "com.pocketcasts.android"
        ),
        "books" to listOf(
            "com.amazon.kindle",
            "com.google.android.apps.books",
            "com.audible.application",
            "com.scribd.app.reader0"
        ),
        "clock" to listOf(
            "com.google.android.deskclock",            // Google Clock (Pixel + stock Android)
            "com.samsung.android.app.clockpackage",    // Samsung Clock
            "com.oneplus.clock",                       // OnePlus Clock
            "com.motorola.timeweatherwidget",          // Motorola Clock
            "com.htc.android.worldclock",              // HTC Clock
            "com.asus.deskclock"                       // ASUS Clock
        )
    )

    /**
     * Returns a map of category → list of installed packages for that category.
     */
    fun detectInstalled(context: Context, categories: List<String>): Map<String, List<String>> {
        val pm = context.packageManager
        return categories.associateWith { category ->
            CATEGORY_PACKAGES[category]?.filter { packageName ->
                runCatching { pm.getPackageInfo(packageName, 0); true }.getOrDefault(false)
            } ?: emptyList()
        }
    }

    /**
     * Flat list of all installed packages across all given categories.
     */
    fun installedPackages(context: Context, categories: List<String>): List<String> =
        detectInstalled(context, categories).values.flatten().distinct()
}
