package dev.l1avzh.haven

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.view.View
import android.widget.TextView
import java.util.Locale

/**
 * Per-app language. On Android 13+ the system LocaleManager owns the choice (and shows it in system settings);
 * on older versions the saved tag is applied by wrapping the base context.
 */
object Lang {
    private const val KEY = "lang"
    private fun sp(c: Context) = c.getSharedPreferences("haven", Context.MODE_PRIVATE)

    fun saved(c: Context): String = sp(c).getString(KEY, "") ?: ""

    /** The language tag actually in effect for this app ("" = system default). Asks the system on Android 13+. */
    fun current(c: Context): String {
        if (Build.VERSION.SDK_INT < 33) return saved(c)
        val list = c.getSystemService(LocaleManager::class.java).applicationLocales
        if (list.isEmpty) return ""
        val loc = list[0]
        LANGUAGES.firstOrNull { it.tag.equals(loc.toLanguageTag(), ignoreCase = true) }?.let { return it.tag }
        return LANGUAGES.firstOrNull { it.tag.substringBefore('-') == loc.language && !it.tag.startsWith("zh") }?.tag ?: ""
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = saved(base)
        if (tag.isEmpty()) return base
        val loc = Locale.forLanguageTag(tag)
        Locale.setDefault(loc)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(loc)
        return base.createConfigurationContext(cfg)
    }

    fun apply(a: Activity, tag: String) {
        sp(a).edit().putString(KEY, tag).apply()
        if (Build.VERSION.SDK_INT >= 33) {
            // The system recreates the activity with the new locale (and RTL direction) by itself.
            a.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else a.recreate()
    }

    fun isRtl(c: Context) = c.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    fun locale(c: Context): Locale = c.resources.configuration.locales[0]
}

/** Uppercase + tracking only for scripts where that is typographically valid; never for Arabic, Indic, CJK... */
object Caps {
    fun applies(s: String): Boolean {
        val cp = s.codePoints().filter { Character.isLetter(it) }.findFirst()
        if (!cp.isPresent) return false
        return when (Character.UnicodeScript.of(cp.asInt)) {
            Character.UnicodeScript.LATIN, Character.UnicodeScript.CYRILLIC, Character.UnicodeScript.GREEK -> true
            else -> false
        }
    }

    fun set(tv: TextView, text: String, spacing: Float) {
        val caps = applies(text)
        tv.text = if (caps) text.uppercase(Lang.locale(tv.context)) else text
        tv.letterSpacing = if (caps) spacing else 0f   // letter-spacing would break Arabic joining
    }
}
