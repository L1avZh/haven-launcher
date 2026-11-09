package dev.l1avzh.haven

/** What kind of app something is, as far as Haven can tell from its own metadata. It says nothing about what the app is doing. */
enum class AppKind { MUSIC, VIDEO, OTHER }

object AppKinds {
    // Values of android.content.pm.ApplicationInfo.CATEGORY_*; spelled out here so this file stays free of Android types.
    const val CATEGORY_AUDIO = 1
    const val CATEGORY_VIDEO = 2

    /** Well-known packages, including the TV and the phone/tablet variants of the same service. */
    private val MUSIC_PACKAGES = listOf(
        "com.spotify.", "com.google.android.youtube.tvmusic", "com.google.android.apps.youtube.music", "com.apple.android.music",
        "deezer.android.", "com.aspiro.tidal", "com.amazon.mp3", "com.amazon.music", "com.pandora.", "com.soundcloud.", "fm.last.", "org.videolan.vlc.music",
        "com.sirius", "com.iheart", "tunein", "com.shazam", "org.xbmc.kodi.music",
    )
    private val VIDEO_PACKAGES = listOf(
        "com.netflix.", "com.plexapp.", "com.amazon.amazonvideo", "com.amazon.avod", "com.disney", "com.hbo.", "com.wbd.", "com.google.android.youtube.tv",
        "com.google.android.youtube", "com.apple.atve", "tv.twitch", "com.hulu", "com.stremio.", "org.xbmc.kodi", "org.videolan.vlc", "com.crunchyroll.",
        "com.mubi", "com.mxtech", "com.keshet.", "com.applicaster.", "com.joxdev.orbia", "tv.pluto", "com.vudu", "com.cbs", "com.paramount", "com.peacocktv",
    )
    private val MUSIC_WORDS = listOf("music", "spotify", "radio", "podcast", "deezer", "tidal", "soundcloud", "audio", "שירים", "מוזיקה", "מוסיקה")
    private val VIDEO_WORDS = listOf("netflix", "plex", "movie", "movies", "cinema", "film", "video", "tv", "iptv", "kodi", "prime", "disney", "hbo", "סרטים", "וידאו")

    /**
     * Order of trust: the category the app declares for itself, then well-known package names, then words in the label.
     * [androidCategory] is ApplicationInfo.category, or -1 when unknown.
     */
    fun classify(pkg: String, label: String, androidCategory: Int = -1): AppKind {
        // A package that is known to be one thing wins over a vague category; YouTube Music must not be treated as video.
        val p = pkg.lowercase()
        if (MUSIC_PACKAGES.any { p.startsWith(it) || p.contains(it) }) return AppKind.MUSIC
        when (androidCategory) { CATEGORY_AUDIO -> return AppKind.MUSIC; CATEGORY_VIDEO -> return AppKind.VIDEO }
        if (VIDEO_PACKAGES.any { p.startsWith(it) }) return AppKind.VIDEO
        val l = label.lowercase()
        val words = Regex("[\\p{L}\\p{N}]+").findAll(l).map { it.value }.toSet()
        if (MUSIC_WORDS.any { it in words || (it.length > 4 && l.contains(it)) }) return AppKind.MUSIC
        if (VIDEO_WORDS.any { it in words || (it.length > 4 && l.contains(it)) }) return AppKind.VIDEO
        return AppKind.OTHER
    }
}
