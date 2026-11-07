package dev.l1avzh.haven

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class AppEntry(val component: ComponentName, val label: String, val installed: Long = 0L) {
    val key: String get() = component.flattenToShortString()
    val pkg: String get() = component.packageName
    /** A shortcut tile that is not an installed app (the AdGuard "get it" entry). */
    val virtual: Boolean get() = component.className == ADGUARD_CLASS
}

const val ADGUARD_CLASS = "virtual.adguard"
const val ADGUARD_PAGE = "https://adguard.com/en/adguard-android-tv/overview.html"
val ADGUARD_ENTRY = AppEntry(ComponentName("dev.l1avzh.haven", ADGUARD_CLASS), "AdGuard")

class Wallpaper(val nameRes: Int, val top: Int, val bottom: Int)

val WALLPAPERS = listOf(
    Wallpaper(R.string.wp_midnight, 0xFF070A12.toInt(), 0xFF141B33.toInt()),
    Wallpaper(R.string.wp_ember, 0xFF0D0707.toInt(), 0xFF2B1418.toInt()),
    Wallpaper(R.string.wp_forest, 0xFF050D0A.toInt(), 0xFF0F2A21.toInt()),
    Wallpaper(R.string.wp_slate, 0xFF0E0E10.toInt(), 0xFF26262B.toInt()),
    Wallpaper(R.string.wp_violet, 0xFF0A0712.toInt(), 0xFF231640.toInt()),
    Wallpaper(R.string.wp_black, 0xFF000000.toInt(), 0xFF000000.toInt()),
)

/** All user state lives here, in app-private SharedPreferences. Nothing leaves the device. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("haven", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = sp.getString("favorites", null)?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()
        set(v) { sp.edit().putString("favorites", v.joinToString("\n")).apply() }
    val seeded: Boolean get() = sp.contains("favorites")

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("hidden", v).apply() }

    var wallpaper: Int
        get() = sp.getInt("wallpaper", 0).coerceIn(0, WALLPAPERS.lastIndex)
        set(v) { sp.edit().putInt("wallpaper", v).apply() }

    var showClock: Boolean
        get() = sp.getBoolean("clock", true)
        set(v) { sp.edit().putBoolean("clock", v).apply() }

    /** Set before a language change so Settings reopens afterwards (the activity is recreated). */
    var reopenSettings: Boolean
        get() = sp.getBoolean("reopen_settings", false)
        set(v) { sp.edit().putBoolean("reopen_settings", v).apply() }

    var mappingsEnabled: Boolean
        get() = sp.getBoolean("mappings_enabled", true)
        set(v) { sp.edit().putBoolean("mappings_enabled", v).apply() }

    /** Digits 1-9 (0 = tenth) open the matching favorite while the home screen has focus. */
    var numbersOpenFavs: Boolean
        get() = sp.getBoolean("numbers_open_favs", true)
        set(v) { sp.edit().putBoolean("numbers_open_favs", v).apply() }

    var bindings: List<Binding>
        get() = (sp.getString("bindings", "") ?: "").split('\n').mapNotNull {
            val p = it.split('|'); if (p.size < 2 || p[0].toIntOrNull() == null) null else Binding(p[0].toInt(), p[1], p.getOrElse(2) { "" }, p.getOrElse(3) { "0" } == "1")
        }
        set(v) { sp.edit().putString("bindings", v.joinToString("\n") { "${it.key}|${it.action}|${it.arg}|${if (it.long) 1 else 0}" }).apply() }

    /** When each app was last opened from Haven (key to epoch millis). Local only; powers the Recent list. */
    val launchLog: Map<String, Long>
        get() = (sp.getString("launch_log", "") ?: "").split('\n').mapNotNull {
            val p = it.split('|'); if (p.size == 2 && p[1].toLongOrNull() != null) p[0] to p[1].toLong() else null
        }.toMap()

    fun recordLaunch(key: String) {
        val m = launchLog.toMutableMap(); m[key] = System.currentTimeMillis()
        val keep = m.entries.sortedByDescending { it.value }.take(40)
        sp.edit().putString("launch_log", keep.joinToString("\n") { "${it.key}|${it.value}" }).apply()
    }

    fun resetRemote() { sp.edit().remove("bindings").remove("mappings_enabled").remove("numbers_open_favs").apply() }

    var glow: Boolean
        get() = sp.getBoolean("glow", true)
        set(v) { sp.edit().putBoolean("glow", v).apply() }

    /** 2 = full, 1 = subtle, 0 = off. Older installs that had "reduce motion" on map to 0. */
    var animLevel: Int
        get() = if (sp.contains("anim_level")) sp.getInt("anim_level", 2).coerceIn(0, 2) else if (sp.getBoolean("reduce_motion", false)) 0 else 2
        set(v) { sp.edit().putInt("anim_level", v).apply() }

    var performance: Boolean
        get() = sp.getBoolean("performance", false)
        set(v) { sp.edit().putBoolean("performance", v).apply() }

    /** 0 = follow the focused app's artwork, 1..5 = fixed accent colours. */
    var accentMode: Int
        get() = sp.getInt("accent_mode", 0).coerceIn(0, 5)
        set(v) { sp.edit().putInt("accent_mode", v).apply() }

    var dimming: Int
        get() = sp.getInt("dimming", 0).coerceIn(0, 3)
        set(v) { sp.edit().putInt("dimming", v).apply() }

    var cardSize: Int
        get() = sp.getInt("card_size", 1).coerceIn(0, 2)
        set(v) { sp.edit().putInt("card_size", v).apply() }

    /** 0 = follow the system, 1 = 12-hour, 2 = 24-hour. */
    var timeFormat: Int
        get() = sp.getInt("time_format", 0).coerceIn(0, 2)
        set(v) { sp.edit().putInt("time_format", v).apply() }

    var showSystemApps: Boolean
        get() = sp.getBoolean("show_system_apps", true)
        set(v) { sp.edit().putBoolean("show_system_apps", v).apply() }

    /** 0 = A to Z, 1 = newest first. */
    var sortMode: Int
        get() = sp.getInt("sort_mode", 0).coerceIn(0, 1)
        set(v) { sp.edit().putInt("sort_mode", v).apply() }

    var returnStartOnHome: Boolean
        get() = sp.getBoolean("return_start_home", true)
        set(v) { sp.edit().putBoolean("return_start_home", v).apply() }

    var settingsCategory: Int
        get() = sp.getInt("settings_cat", 0)
        set(v) { sp.edit().putInt("settings_cat", v).apply() }

    /** Back to first-run state. The launcher language is owned by the system and is left alone. */
    fun resetAll() { sp.edit().clear().apply() }
}

object AppRepo {
    /** Packages used only to pick a first-run favorites row, and only if actually installed. */
    private val SEED_PACKAGES = listOf(
        "com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
        "com.plexapp.android", "com.stremio.one", "org.videolan.vlc", "com.crunchyroll.crunchyroid",
        "com.android.vending", "com.android.tv.settings",
    )

    /** One PackageManager pass per call: leanback activities win over plain launcher ones. */
    fun load(ctx: Context, showSystem: Boolean = true, sort: Int = 0): List<AppEntry> {
        val pm = ctx.packageManager
        fun query(cat: String) = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(cat), PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        ).filter { it.activityInfo.packageName != ctx.packageName }
        val tv = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val tvPkgs = tv.map { it.activityInfo.packageName }.toSet()
        val phone = query(Intent.CATEGORY_LAUNCHER).filter { it.activityInfo.packageName !in tvPkgs }
        fun isSystem(r: android.content.pm.ResolveInfo): Boolean {
            val f = r.activityInfo.applicationInfo.flags
            return (f and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 && (f and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        }
        val list = (tv + phone).filter { showSystem || !isSystem(it) }.map {
            val t0 = try { pm.getPackageInfo(it.activityInfo.packageName, 0).firstInstallTime } catch (_: Exception) { 0L }
            AppEntry(ComponentName(it.activityInfo.packageName, it.activityInfo.name), it.loadLabel(pm).toString(), t0)
        }.distinctBy { it.key }
        return if (sort == 1) list.sortedByDescending { it.installed } else list.sortedBy { it.label.lowercase() }
    }

    /** Official AdGuard apps all use the com.adguard. package prefix; if one is installed the promo tile is not shown. */
    fun hasAdGuard(apps: List<AppEntry>) = apps.any { it.pkg.startsWith("com.adguard.") }

    fun seedFavorites(apps: List<AppEntry>): List<String> =
        SEED_PACKAGES.mapNotNull { p -> apps.firstOrNull { it.pkg == p }?.key }
}
