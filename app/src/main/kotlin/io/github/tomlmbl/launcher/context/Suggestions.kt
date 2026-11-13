package io.github.tomlmbl.launcher.context

/**
 * App suggestions per profile: a static list of package names, matched locally against installed apps.
 * Shown in the pickers only, never preselected or applied.
 */
object Suggestions {

    private val packages: Map<Profile, List<String>> = mapOf(
        Profile.WORK to listOf(
            "com.google.android.gm", "com.microsoft.office.outlook", "com.slack", "com.microsoft.teams",
            "com.google.android.apps.meetings", "com.google.android.calendar", "com.google.android.apps.docs",
            "us.zoom.videomeetings", "com.notion.id", "org.thunderbird.android",
        ),
        Profile.HOME to listOf(
            "com.spotify.music", "com.google.android.youtube", "com.netflix.mediaclient",
            "com.google.android.apps.chromecast.app", "com.whatsapp", "org.thoughtcrime.securesms",
            "com.google.android.apps.photos", "com.google.android.apps.youtube.music",
        ),
        Profile.OUT to listOf(
            "com.google.android.apps.maps", "app.organicmaps", "com.google.android.GoogleCamera", "app.grapheneos.camera",
            "com.whatsapp", "org.thoughtcrime.securesms", "com.spotify.music", "com.ubercab",
        ),
        Profile.WORKOUT to listOf(
            "com.strava", "com.google.android.apps.fitness", "com.nike.plusgps", "de.komoot.android",
            "com.spotify.music", "com.google.android.apps.youtube.music", "com.garmin.android.apps.connectmobile",
        ),
        Profile.TRAVEL to listOf(
            "com.google.android.apps.maps", "app.organicmaps", "com.google.android.apps.translate",
            "com.booking", "com.airbnb.android", "com.google.android.apps.photos", "com.google.android.GoogleCamera",
            "app.grapheneos.camera",
        ),
    )

    /** Installed apps suggested for this profile, in list order. [packageOf] gives an app's package. */
    fun <A> forProfile(profile: Profile, installed: List<A>, packageOf: (A) -> String): List<A> {
        val wanted = packages[profile].orEmpty()
        return installed.filter { packageOf(it) in wanted }.sortedBy { wanted.indexOf(packageOf(it)) }
    }
}
