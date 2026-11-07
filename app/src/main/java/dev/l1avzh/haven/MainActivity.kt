package dev.l1avzh.haven

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.TransitionDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var s: Scale
    private lateinit var prefs: Prefs
    private lateinit var icons: IconLoader
    private lateinit var frame: FrameLayout
    private lateinit var wallpaperView: View
    private lateinit var glowView: LiveBackground
    private lateinit var header: LinearLayout
    private lateinit var railBox: View
    private lateinit var stageBody: LinearLayout
    private lateinit var stageArt: ImageView
    private lateinit var stageTag: TextView
    private lateinit var stageTitle: TextView
    private lateinit var stageMeta: TextView
    private lateinit var stageEmpty: TextView
    private lateinit var scopeHero: ScopeHero
    private lateinit var btnOpen: TextView
    private lateinit var btnFav: TextView
    private lateinit var btnMore: TextView
    private lateinit var rail: RailView
    private lateinit var railAdapter: RailAdapter
    private val tabs = mutableListOf<LinearLayout>()
    private var favList: List<AppEntry> = emptyList()
    private var recentList: List<AppEntry> = emptyList()
    private var allList: List<AppEntry> = emptyList()
    private var scope = 0                       // 0 favorites, 1 recent, 2 library
    private val lastIdx = IntArray(3)
    private var focusedEntry: AppEntry? = null
    private var loaded = false
    private var artToken = 0

    private var all: List<AppEntry> = emptyList()
    private var rtl = false
    private val main = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor()
    private val dialogs = mutableListOf<Dialog>()
    private var focusedKey: String? = null
    private var entered = false

    private var glowColor = T.ACCENT
    private var glowAnim: ValueAnimator? = null

    // Hidden Easter egg: a remote-control sequence on the home screen, or seven OK presses on About's version row.
    private var overlay: KeyOverlay? = null
    private var egg: EggView? = null
    private val autoCloseEgg = Runnable { closeEgg() }
    private var konamiIdx = 0
    private var konamiLast = 0L
    private var settings: SettingsScreen? = null
    private var suppressUp = -1
    private var eggRevealed = false
    private lateinit var homeContent: LinearLayout
    private lateinit var dimView: View
    private lateinit var timeClock: TextClock
    private lateinit var clockCol: LinearLayout
    private var artW = 960
    private val accentColors = intArrayOf(0xFF6C8CFF.toInt(), 0xFF4CD6A0.toInt(), 0xFFFF7A6B.toInt(), 0xFFF2B84B.toInt(), 0xFFA77BFF.toInt())

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { main.removeCallbacks(reload); main.postDelayed(reload, 600) }
    }
    private val reload = Runnable { loadApps() }

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Lang.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = Scale(this); prefs = Prefs(this); icons = IconLoader(packageManager)
        rtl = Lang.isRtl(this)
        Fonts.init(this)
        T.init(this, prefs.animLevel, prefs.performance)
        artW = intArrayOf(800, 960, 1112)[prefs.cardSize]
        buildUi()
        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }, RECEIVER_EXPORTED)
        loadApps()
    }

    override fun onDestroy() { unregisterReceiver(pkgReceiver); main.removeCallbacks(autoCloseEgg); super.onDestroy() }
    override fun onStop() {
        dialogs.toList().forEach { it.dismiss() }; dialogs.clear()
        egg?.let { frame.removeView(it) }; egg = null
        main.removeCallbacks(saverRun); stopSaver()
        closeOverlay()
        settings?.let { frame.removeView(it) }; settings = null; homeContent.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        T.init(this, prefs.animLevel, prefs.performance)
        if (loaded) render()
        if (currentFocus == null) restoreFocus()
        armSaver()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while we are already on top: close any menu and go back to the start.
        dialogs.toList().forEach { it.dismiss() }; dialogs.clear()
        closeEgg(); closeOverlay(); closeSettings()
        if (prefs.returnStartOnHome) focusStart()
    }

    @Deprecated("Back never leaves the launcher; it returns to the start position.")
    override fun onBackPressed() { if (egg != null) closeEgg() else settings?.back() ?: focusStart() }

    private lateinit var widgetHost: FrameLayout
    private fun applyWidgets() {
        widgetHost.removeAllViews()
        val ids = Widgets.parse(prefs.homeWidgets).take(3)
        LiveBackground.ambient = LiveBackground.ambientFor(this, ids)
        if (ids.isNotEmpty()) widgetHost.addView(WidgetStrip(this, s, ids, false), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL or Gravity.START))
    }

    private var saverView: SaverView? = null
    private val saverRun = Runnable { startSaver() }
    private val SAVER_MINUTES = intArrayOf(0, 2, 5, 10, 20)

    private fun armSaver() {
        main.removeCallbacks(saverRun)
        val m = SAVER_MINUTES[prefs.saver]
        if (m > 0 && saverView == null) main.postDelayed(saverRun, m * 60_000L)
    }

    private fun startSaver() {
        if (saverView != null || overlay != null) return
        val v = SaverView(this, s, timeClock.format12Hour, timeClock.format24Hour, DateFormat.getBestDateTimePattern(Lang.locale(this), "EEEdMMM"), Widgets.parse(prefs.saverWidgets))
        saverView = v; LiveBackground.ambient = LiveBackground.ambientFor(this, Widgets.parse(prefs.saverWidgets).take(4)); frame.addView(v, -1, -1)
    }

    private fun stopSaver() { saverView?.let { frame.removeView(it) }; saverView = null; LiveBackground.ambient = LiveBackground.ambientFor(this, Widgets.parse(prefs.homeWidgets).take(3)) }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (saverView != null) { if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) { stopSaver(); armSaver() }; return true }
        armSaver()
        overlay?.let { return it.onKey(e, getString(R.string.yes), KeyOverlay.Labels(getString(R.string.test_code), getString(R.string.test_device), getString(R.string.test_usable))) }
        if (egg != null) { if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) closeEgg(); return true }
        if (dialogs.isEmpty() && settings == null) {
            // Long-press bindings fire once, on the first repeat; the release that follows is swallowed so Back or OK does not also act.
            if (e.action == KeyEvent.ACTION_UP && e.keyCode == suppressUp) { suppressUp = -1; return true }
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount >= 1 && prefs.mappingsEnabled) {
                val b = prefs.bindings.firstOrNull { it.long && it.key == e.keyCode }
                if (b != null) { if (e.repeatCount == 1) { suppressUp = e.keyCode; runAction(b.action, b.arg) }; return true }
            }
        }
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 && dialogs.isEmpty() && settings == null && konami(e.keyCode)) return true
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 && dialogs.isEmpty() && settings == null && remoteKey(e)) return true
        return super.dispatchKeyEvent(e)
    }

    // ---------- Remote control: mappings, number keys, tester ----------

    /** Custom bindings first, then digits. Only runs on the home screen (no menu open), so text entry is never affected. */
    private fun remoteKey(e: KeyEvent): Boolean {
        if (prefs.mappingsEnabled) prefs.bindings.firstOrNull { !it.long && it.key == e.keyCode }?.let { runAction(it.action, it.arg); return true }
        if (prefs.numbersOpenFavs && currentFocus !is EditText && e.keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            val n = e.keyCode - KeyEvent.KEYCODE_0
            val fav = favList.getOrNull(if (n == 0) 9 else n - 1) ?: return false
            launch(fav); return true
        }
        return false
    }

    /** Holding OK normally opens the app menu; if the user bound a long press of OK, that binding wins instead. */
    private fun okHeldIsBound() = prefs.mappingsEnabled && prefs.bindings.any {
        it.long && (it.key == KeyEvent.KEYCODE_DPAD_CENTER || it.key == KeyEvent.KEYCODE_ENTER)
    }

    private fun runAction(action: String, arg: String) {
        when (action) {
            "app" -> all.firstOrNull { it.key == arg }?.let { launch(it) }
                ?: Toast.makeText(this, getString(R.string.cant_open, arg.substringBefore('/')), Toast.LENGTH_LONG).show()
            "search" -> openSearch()
            "settings" -> openSettings()
            "home", "start" -> focusStart()
        }
    }

    private fun actionLabel(b: Binding) = when (b.action) {
        "app" -> all.firstOrNull { it.key == b.arg }?.label ?: b.arg.substringBefore('/')
        "search" -> getString(R.string.action_search)
        "settings" -> getString(R.string.action_settings)
        else -> getString(R.string.action_home)
    }

    private fun closeOverlay() { overlay?.let { frame.removeView(it) }; overlay = null }

    private fun showOverlay(v: KeyOverlay) {
        closeOverlay(); dialogs.toList().forEach { it.dismiss() }
        overlay = v; frame.addView(v, -1, -1)
    }

    private fun startTester() {
        showOverlay(KeyOverlay(this, s, getString(R.string.button_test), getString(R.string.button_test_hint), getString(R.string.tester_note), null) {
            closeOverlay()
        })
    }

    /** Waits for one key press (short or held) and binds it. A quick Back cancels. */
    private fun bindingName(code: Int, long: Boolean) = keyName(code) + if (long) " \u00B7 ${getString(R.string.long_press)}" else ""

    private fun startLearn(action: String, arg: String, label: String) {
        showOverlay(KeyOverlay(this, s, label, getString(R.string.press_button), getString(R.string.learn_hold_hint), { e, long ->
            prefs.bindings = prefs.bindings.filter { !(it.key == e.keyCode && it.long == long) } + Binding(e.keyCode, action, arg, long)
            closeOverlay()
            Toast.makeText(this, getString(R.string.mapping_saved, bindingName(e.keyCode, long), label), Toast.LENGTH_LONG).show()
            openMappings(0)
        }) { closeOverlay(); openMappings(0) })
    }

    private fun openDevices() {
        val items = listInputDevices().map { (n, src) -> MenuItem(n, src) { it.dismiss() } }
        track(showMenu(s, getString(R.string.input_devices), items.ifEmpty { listOf(MenuItem("\u2014") { it.dismiss() }) }))
    }

    private fun openMappings(focus: Int) {
        val items = mutableListOf<MenuItem>()
        items += MenuItem(getString(R.string.custom_mappings), onOff(prefs.mappingsEnabled)) { d -> prefs.mappingsEnabled = !prefs.mappingsEnabled; d.dismiss(); openMappings(0) }
        val bs = prefs.bindings
        if (bs.isEmpty()) items += MenuItem(getString(R.string.no_mappings)) { it.dismiss(); openMappings(0) }
        bs.forEach { b -> items += MenuItem(bindingName(b.key, b.long), actionLabel(b)) { d ->
            d.dismiss()
            track(showMenu(s, bindingName(b.key, b.long), listOf(MenuItem(getString(R.string.remove), actionLabel(b)) { d2 -> prefs.bindings = prefs.bindings - b; d2.dismiss(); openMappings(0) })))
        } }
        items += MenuItem(getString(R.string.add_mapping)) { d -> d.dismiss(); chooseAction() }
        items += MenuItem(getString(R.string.reset_mappings)) { d ->
            prefs.resetRemote(); d.dismiss(); Toast.makeText(this, getString(R.string.reset_done), Toast.LENGTH_SHORT).show(); openMappings(0)
        }
        track(showMenu(s, getString(R.string.custom_mappings), items, focus))
    }

    private fun chooseAction() {
        val items = listOf(
            MenuItem(getString(R.string.action_open_app)) { d -> d.dismiss(); chooseApp() },
            MenuItem(getString(R.string.action_search)) { d -> d.dismiss(); startLearn("search", "", getString(R.string.action_search)) },
            MenuItem(getString(R.string.action_settings)) { d -> d.dismiss(); startLearn("settings", "", getString(R.string.action_settings)) },
            MenuItem(getString(R.string.action_home)) { d -> d.dismiss(); startLearn("home", "", getString(R.string.action_home)) },
        )
        track(showMenu(s, getString(R.string.choose_action), items))
    }

    private fun chooseApp() {
        val items = visible().map { e -> MenuItem(e.label) { d -> d.dismiss(); startLearn("app", e.key, e.label) } }
        track(showMenu(s, getString(R.string.action_open_app), items))
    }

    private fun konami(key: Int): Boolean {
        val seq = intArrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER)
        val k = if (key == KeyEvent.KEYCODE_ENTER) KeyEvent.KEYCODE_DPAD_CENTER else key
        val now = System.currentTimeMillis()
        if (now - konamiLast > 2000) konamiIdx = 0
        konamiLast = now
        konamiIdx = if (k == seq[konamiIdx]) konamiIdx + 1 else if (k == seq[0]) 1 else 0
        if (konamiIdx < seq.size) return false
        konamiIdx = 0
        showEgg()
        return true   // the final OK is swallowed so it does not launch the focused app
    }

    private fun showEgg() {
        if (egg != null) return
        val v = EggView(this, getString(R.string.app_name), "Made By L1avZh", T.animated)
        v.alpha = if (T.animated) 0f else 1f
        frame.addView(v, -1, -1)
        if (T.animated) v.animate().alpha(1f).setDuration(T.d(450)).start()
        egg = v
        main.postDelayed(autoCloseEgg, 16000)
    }

    private fun closeEgg() {
        val v = egg ?: return
        main.removeCallbacks(autoCloseEgg)
        egg = null
        v.close { frame.removeView(v) }
    }

    // ---------- UI ----------

    private fun buildUi() {
        frame = FrameLayout(this).apply { clipChildren = false; setBackgroundColor(Color.BLACK) }

        // Layer 1: wallpaper. Layer 2: accent glow behind the stage. Layer 3: optional dimming.
        wallpaperView = View(this).apply { background = wallpaperDrawable() }
        glowView = LiveBackground(this).apply { visibility = if (prefs.glow) View.VISIBLE else View.GONE; setStatic(prefs.performance); setAccent(glowColor) }
        dimView = View(this).apply { setBackgroundColor(dimColor()) }
        frame.addView(wallpaperView, -1, -1); frame.addView(glowView, -1, -1); frame.addView(dimView, -1, -1)

        homeContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false
            setPaddingRelative(s.px(T.MARGIN), s.px(40), s.px(T.MARGIN), 0)
        }
        frame.addView(homeContent, -1, -1)
        setContentView(frame)

        // ----- Rail: clock, scope tabs, and one typographic row per app.
        val locale = Lang.locale(this)
        val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
        clockCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        timeClock = TextClock(this)
        clockCol.addView(timeClock.apply {
            applyTimeFormat(this); sizePx(s.px(40f)); setTextColor(T.TEXT); typeface = Fonts.displayBold; includeFontPadding = false
        })
        clockCol.addView(TextClock(this).apply {
            format12Hour = datePattern; format24Hour = datePattern; sizePx(s.px(T.LABEL + 2)); setTextColor(T.TEXT_DIM)
        })
        clockCol.visibility = if (prefs.showClock) View.VISIBLE else View.INVISIBLE

        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(R.string.favorites, R.string.recent, R.string.library).forEachIndexed { i, res -> tabRow.addView(tab(i, getString(res))) }

        railAdapter = RailAdapter(s, ::onRailFocus, ::onTile, { if (!okHeldIsBound()) menuFor(it) }, { btnOpen.requestFocus() }, { tabs[scope].requestFocus() })
        rail = RailView(this).apply {
            layoutManager = RailLayoutManager(this@MainActivity, 3)
            adapter = railAdapter; clipChildren = true; clipToPadding = false; overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, s.px(8), 0, s.px(560))
            addOnLayoutChangeListener { v, l, tp, r, b, _, _, _, _ -> v.clipBounds = android.graphics.Rect(0, 0, r - l, b - tp) }
            itemAnimator = DefaultItemAnimator().apply {
                supportsChangeAnimations = false
                addDuration = T.d(T.MED); removeDuration = T.d(T.FAST); moveDuration = T.d(T.MED)
            }
        }
        railBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false
            addView(clockCol)
            addView(tabRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(30) })
            addView(rail, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = s.px(10) })
        }
        homeContent.addView(railBox, LinearLayout.LayoutParams(s.px(560), -1))

        // ----- Stage: big artwork, name, last-opened line, and the actions for the focused app.
        widgetHost = FrameLayout(this).apply { clipChildren = false }
        applyWidgets()
        header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            addView(widgetHost, LinearLayout.LayoutParams(0, -1, 1f))
            addView(iconButton(R.drawable.ic_home, getString(R.string.back_to_start)) { focusStart() })
            addView(iconButton(R.drawable.ic_search, getString(R.string.search)) { openSearch() })
            addView(iconButton(R.drawable.ic_settings, getString(R.string.settings)) { openSettings() })
        }
        val artH = artW * 9 / 16
        val radius = s.px(28f)
        stageArt = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY }
        val artCard = FrameLayout(this).apply {
            background = rounded(T.CARD, radius)
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, radius) }
            }
            addView(stageArt, FrameLayout.LayoutParams(-1, -1))
        }
        stageTag = TextView(this).apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_START; sizePx(s.px(T.LABEL)); typeface = Fonts.medium }
        stageTitle = TextView(this).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START; sizePx(s.px(58f)); setTextColor(T.TEXT); maxLines = 1
            ellipsize = TextUtils.TruncateAt.END; typeface = Fonts.display; includeFontPadding = false
        }
        stageMeta = TextView(this).apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_START; sizePx(s.px(T.LABEL + 4)); setTextColor(T.TEXT_DIM); maxLines = 1 }
        btnOpen = stagePill(this, s, true, getString(R.string.open)) { focusedEntry?.let { onTile(it) } }
        btnFav = stagePill(this, s, false, "") { focusedEntry?.let { toggleFavorite(it) } }
        btnMore = stagePill(this, s, false, "\u22EF") { focusedEntry?.let { menuFor(it) } }.also { it.contentDescription = getString(R.string.app_info) }
        val pills = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; clipChildren = false
            listOf(btnOpen, btnFav, btnMore).forEach { b ->
                addView(b, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = s.px(14) })
                b.setOnKeyListener { _, code, e ->
                    val toRail = if (rtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                    if (e.action == KeyEvent.ACTION_DOWN && code == toRail && b === btnOpen) { focusRail(lastIdx[scope]); true } else false
                }
            }
        }
        stageBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false
            addView(artCard, LinearLayout.LayoutParams(s.px(artW), s.px(artH)))
            addView(stageTag, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(30) })
            addView(stageTitle, LinearLayout.LayoutParams(s.px(artW), -2).apply { topMargin = s.px(6) })
            addView(stageMeta, LinearLayout.LayoutParams(s.px(artW), -2).apply { topMargin = s.px(8) })
            addView(pills, LinearLayout.LayoutParams(-2, -2).apply { topMargin = s.px(26) })
        }
        stageEmpty = TextView(this).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START; sizePx(s.px(T.BODY)); setTextColor(T.TEXT_DIM); typeface = Fonts.light
            text = getString(R.string.loading); setLineSpacing(0f, 1.2f)
        }
        scopeHero = ScopeHero(this, s).apply { visibility = View.GONE }
        val stageCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false
            addView(header, LinearLayout.LayoutParams(-1, s.px(84)))
            addView(FrameLayout(this@MainActivity).apply {
                clipChildren = false
                addView(stageBody, FrameLayout.LayoutParams(-2, -2))
                addView(scopeHero, FrameLayout.LayoutParams(-2, -2).apply { topMargin = s.px(200) })
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = s.px(14) })
            addView(stageEmpty, LinearLayout.LayoutParams(s.px(artW), -2).apply { topMargin = s.px(160) })
        }
        homeContent.addView(stageCol, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = s.px(56) })
        stageBody.visibility = View.INVISIBLE

        if (T.animated) listOf(railBox, header, stageBody).forEach { it.alpha = 0f; it.translationY = s.px(26f) }
    }

    /** Scope tab: Favorites / Recent / Library. Focusing a tab switches the list immediately. */
    private fun tab(i: Int, label: String): LinearLayout {
        val txt = TextView(this).apply { text = label; sizePx(s.px(28f)); typeface = Fonts.medium; setTextColor(T.TEXT_DIM); textAlignment = View.TEXT_ALIGNMENT_VIEW_START }
        val bar = View(this).apply { background = rounded(T.ACCENT, s.px(2f)); alpha = 0f }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; isFocusable = true; isClickable = true
            setPadding(0, s.px(8), s.px(34), s.px(6))
            addView(txt, LinearLayout.LayoutParams(-2, -2)); addView(bar, LinearLayout.LayoutParams(s.px(34), s.px(3)).apply { topMargin = s.px(8) })
            tag = Pair(txt, bar)
            setOnFocusChangeListener { _, f -> if (f) { if (scope != i) setScope(i); showHero(i) }; updateTabs() }
            setOnClickListener { focusRail(lastIdx[i]) }
            setOnKeyListener { _, code, e ->
                if (e.action == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN) { focusRail(lastIdx[scope]); true } else false
            }
        }.also { tabs += it }
    }

    /** A scope tab has focus rather than an app: show the scope's name instead of an empty app card. */
    private fun showHero(i: Int) {
        val n = scopeItems().size
        val name = getString(intArrayOf(R.string.favorites, R.string.recent, R.string.library)[i])
        val summary = if (n > 0) resources.getQuantityString(R.plurals.apps_count, n, n)
            else getString(when (i) { 0 -> R.string.no_favorites; 1 -> R.string.no_recent; else -> R.string.no_hidden })
        focusedEntry = null
        stageBody.visibility = View.INVISIBLE; stageEmpty.visibility = View.GONE
        scopeHero.set(name, summary, glowColor); scopeHero.visibility = View.VISIBLE
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateTabs() {
        tabs.forEachIndexed { i, t ->
            val (txt, bar) = t.tag as Pair<TextView, View>
            val sel = i == scope
            txt.setTextColor(if (t.isFocused) Color.WHITE else if (sel) T.TEXT else T.TEXT_DIM)
            bar.animate().alpha(if (sel) 1f else 0f).setDuration(T.d(T.FAST)).start()
        }
    }

    private fun iconButton(res: Int, desc: String, onClick: () -> Unit): View {
        val size = s.px(72)
        val icon = ImageView(this).apply { setImageResource(res); setColorFilter(T.TEXT) }
        val b = Cell(this).apply {
            isFocusable = true; isClickable = true; contentDescription = desc
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), rounded(Color.WHITE, size / 2f))
                addState(intArrayOf(), rounded(0x26FFFFFF, size / 2f))
            }
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, f ->
                icon.setColorFilter(if (f) T.ON_ACCENT else T.TEXT)
                val sc = if (f && T.animated) 1.1f else 1f
                v.animate().scaleX(sc).scaleY(sc).setDuration(T.d(T.FAST)).setInterpolator(T.OUT).start()
            }
        }
        b.addView(icon, FrameLayout.LayoutParams(s.px(34), s.px(34), Gravity.CENTER))
        b.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginStart = s.px(18) }
        return b
    }

    private fun wallpaperDrawable(): GradientDrawable {
        val w = WALLPAPERS[prefs.wallpaper]
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(w.top, w.bottom))
    }

    private fun applyWallpaper() {
        val next = wallpaperDrawable()
        if (T.animated) {
            val t = TransitionDrawable(arrayOf(wallpaperView.background, next))
            wallpaperView.background = t; t.startTransition(T.d(T.SLOW).toInt())
        } else wallpaperView.background = next
    }

    // ---------- Motion ----------

    private fun setGlowColor(c: Int) {
        glowColor = c
        if (::glowView.isInitialized) glowView.setAccent(c)
    }

    private fun glowTo(target: Int) {
        glowAnim?.cancel()
        if (::glowView.isInitialized) glowView.pulse()
        if (!T.animated || target == glowColor) { setGlowColor(target); return }
        glowAnim = ValueAnimator.ofObject(ArgbEvaluator(), glowColor, target).apply {
            duration = T.d(T.SLOW + 100); interpolator = T.STD
            addUpdateListener { setGlowColor(it.animatedValue as Int) }
            start()
        }
    }

    private fun scopeItems(): List<AppEntry> = when (scope) { 0 -> favList; 1 -> recentList; else -> allList }

    private fun setScope(i: Int) {
        scope = i
        updateTabs()
        railAdapter.submit(scopeItems())
        updateStageEmpty()
    }

    private fun updateStageEmpty() {
        val none = scopeItems().isEmpty()
        if (tabs.any { it.isFocused }) { showHero(scope); return }
        stageBody.visibility = if (none) View.INVISIBLE else View.VISIBLE
        stageEmpty.visibility = if (none) View.VISIBLE else View.GONE
        if (none) stageEmpty.text = getString(when { !loaded -> R.string.loading; scope == 0 -> R.string.no_favorites; scope == 1 -> R.string.no_recent; else -> R.string.no_hidden })
    }

    private fun onRailFocus(e: AppEntry, pos: Int) { lastIdx[scope] = pos; showStage(e) }

    private fun openedText(e: AppEntry): String = prefs.launchLog[e.key]?.let {
        android.text.format.DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
    }.orEmpty()

    /** Names the focused app and shows its artwork; the glow takes the artwork's accent colour. */
    private fun showStage(e: AppEntry) {
        focusedEntry = e; focusedKey = e.key
        stageEmpty.visibility = View.GONE; scopeHero.visibility = View.GONE; stageBody.visibility = View.VISIBLE
        val accent = accentFor(e)
        val isFav = e.key in prefs.favorites
        Caps.set(stageTag, getString(if (e.virtual) R.string.not_installed else if (isFav) R.string.tag_favorite else R.string.library), 0.22f)
        stageTag.setTextColor(accent)
        stageTitle.text = e.label; stageTitle.typeface = Fonts.familyFor(e.key).first
        stageMeta.text = if (e.virtual) getString(R.string.adguard_open_page) else openedText(e)
        btnFav.text = (if (isFav) "\u2605 " else "\u2606 ") + getString(R.string.tag_favorite)
        val real = if (e.virtual) View.GONE else View.VISIBLE
        btnFav.visibility = real; btnMore.visibility = real
        glowTo(accent)
        railAdapter.tint(accent)
        val token = ++artToken
        stageArt.animate().cancel(); stageArt.alpha = if (T.animated) 0.25f else 1f
        icons.load(e, s.px(artW), s.px(artW) * 9 / 16) { bmp ->
            if (token == artToken) {
                if (bmp != null) stageArt.setImageBitmap(bmp)
                stageArt.animate().alpha(1f).setDuration(T.d(T.MED)).start()
                artReady(e)
            }
        }
        if (T.animated) {
            stageTitle.animate().cancel()
            stageTitle.alpha = 0f; stageTitle.translationX = if (rtl) s.px(16f) else -s.px(16f)
            stageTitle.animate().alpha(1f).translationX(0f).setDuration(T.d(T.MED)).setInterpolator(T.OUT).start()
        }
    }

    private fun toggleFavorite(e: AppEntry) {
        val favs = prefs.favorites
        prefs.favorites = if (e.key in favs) favs - e.key else favs + e.key
        render()
        if (scope == 0 && e.key !in prefs.favorites) focusRail(lastIdx[0]) else showStage(e)
    }


    private fun appInfo(e: AppEntry) {
        try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${e.pkg}"))) }
        catch (_: Exception) { Toast.makeText(this, getString(R.string.info_unavailable), Toast.LENGTH_SHORT).show() }
    }

    /** Coordinated, short entrance. Everything is already focusable while it plays. */
    private fun playEntrance() {
        if (entered) return
        entered = true
        if (!T.animated) return
        listOf(railBox to 0L, header to 60L, stageBody to 120L).forEach { (v, delay) ->
            v.animate().alpha(1f).translationY(0f).setStartDelay(T.d(delay)).setDuration(T.d(T.SLOW)).setInterpolator(T.OUT).start()
        }
    }

    private fun artReady(e: AppEntry) {
        if (e.key != focusedKey) return
        val a = accentFor(e)
        stageTag.setTextColor(a); railAdapter.tint(a); glowTo(a)
    }

    // ---------- Data ----------

    private fun loadApps() {
        loader.execute {
            val apps = AppRepo.load(this, prefs.showSystemApps, prefs.sortMode)
            main.post {
                all = apps
                if (!prefs.seeded) prefs.favorites = AppRepo.seedFavorites(apps)
                if (!loaded) { loaded = true; scope = if (prefs.favorites.any { k -> all.any { it.key == k && k !in prefs.hidden } }) 0 else 2 }
                render()
                if (currentFocus == null) restoreFocus()
                frame.post { playEntrance() }
                if (prefs.reopenSettings) { prefs.reopenSettings = false; openSettings() }
            }
        }
    }

    private fun visible() = all.filter { it.key !in prefs.hidden }

    private fun render() {
        val vis = visible()
        val byKey = vis.associateBy { it.key }
        favList = prefs.favorites.mapNotNull { byKey[it] }
        // The AdGuard shortcut lives at the end of the library until the real app is installed (or the user hides it).
        allList = if (!AppRepo.hasAdGuard(all) && ADGUARD_ENTRY.key !in prefs.hidden) vis + ADGUARD_ENTRY else vis
        recentList = prefs.launchLog.entries.sortedByDescending { it.value }.mapNotNull { byKey[it.key] }.take(12)
        railAdapter.submit(scopeItems())
        updateStageEmpty()
    }

    // ---------- Actions ----------

    private fun onTile(e: AppEntry) { if (e.virtual) adguardMenu() else launch(e) }

    private fun launch(e: AppEntry) {
        prefs.recordLaunch(e.key)
        try {
            startActivity(Intent(Intent.ACTION_MAIN).setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        } catch (_: ActivityNotFoundException) { failed(e) } catch (_: SecurityException) { failed(e) }
    }

    private fun failed(e: AppEntry) {
        Toast.makeText(this, getString(R.string.cant_open, e.label), Toast.LENGTH_LONG).show()
        loadApps()
    }

    /** AdGuard is a separate paid app. This only opens its official page; nothing is downloaded or changed. */
    private fun adguardMenu() {
        val items = listOf(
            MenuItem(getString(R.string.adguard_open_page)) { d ->
                d.dismiss()
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ADGUARD_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) { Toast.makeText(this, getString(R.string.cant_open, "AdGuard"), Toast.LENGTH_LONG).show() }
            },
            MenuItem(getString(R.string.hide)) { d ->
                prefs.hidden = prefs.hidden + ADGUARD_ENTRY.key; d.dismiss(); render(); focusRail(lastIdx[scope])
            },
        )
        track(showMenu(s, "AdGuard", items) { box, _ ->
            box.addView(TextView(this).apply {
                text = getString(R.string.adguard_note); sizePx(s.px(T.BODY - 6)); setTextColor(T.TEXT_DIM)
                setPaddingRelative(s.px(8), 0, s.px(8), s.px(14))
            })
        })
    }

    private fun menuFor(e: AppEntry) {
        if (e.virtual) { adguardMenu(); return }
        val inFavs = scope == 0
        val favs = prefs.favorites.toMutableList()
        val idx = favs.indexOf(e.key)
        val at = lastIdx[scope]
        val items = mutableListOf(MenuItem(getString(R.string.open)) { it.dismiss(); launch(e) })
        if (idx < 0) items += MenuItem(getString(R.string.add_fav)) { d -> prefs.favorites = favs + e.key; d.dismiss(); render(); focusRail(at) }
        else {
            items += MenuItem(getString(R.string.remove_fav)) { d -> favs.removeAt(idx); prefs.favorites = favs; d.dismiss(); render(); focusRail(at) }
            if (inFavs) {
                val keys = favList.map { it.key }
                val vi = keys.indexOf(e.key)
                if (vi > 0) items += MenuItem(getString(R.string.move_earlier)) { d -> swap(favs, e.key, keys[vi - 1]); d.dismiss(); render(); focusRail(vi - 1) }
                if (vi < keys.lastIndex) items += MenuItem(getString(R.string.move_later)) { d -> swap(favs, e.key, keys[vi + 1]); d.dismiss(); render(); focusRail(vi + 1) }
            }
        }
        items += MenuItem(getString(R.string.hide)) { d -> prefs.hidden = prefs.hidden + e.key; d.dismiss(); render(); focusRail(at) }
        items += MenuItem(getString(R.string.app_info)) { d -> d.dismiss(); appInfo(e) }
        track(showMenu(s, e.label, items))
    }

    private fun swap(favs: MutableList<String>, a: String, b: String) {
        val i = favs.indexOf(a); val j = favs.indexOf(b)
        if (i >= 0 && j >= 0) { favs[i] = b; favs[j] = a; prefs.favorites = favs }
    }

    private fun onOff(b: Boolean) = getString(if (b) R.string.on else R.string.off)

    private fun currentLanguageName(): String =
        LANGUAGES.firstOrNull { it.tag == Lang.current(this) }?.name ?: getString(R.string.system_default)

    // ---------- Settings screen ----------

    private fun dimColor() = Color.argb(intArrayOf(0, 0x40, 0x70, 0xA0)[prefs.dimming], 0, 0, 0)

    private fun applyTimeFormat(c: TextClock) {
        when (prefs.timeFormat) {
            1 -> { c.format12Hour = "h:mm"; c.format24Hour = "h:mm" }
            2 -> { c.format12Hour = "HH:mm"; c.format24Hour = "HH:mm" }
            else -> { c.format12Hour = "h:mm"; c.format24Hour = "HH:mm" }
        }
    }

    private fun accentFor(e: AppEntry): Int =
        if (prefs.accentMode > 0) accentColors[prefs.accentMode - 1] else icons.accent(e.key) ?: T.ACCENT

    private fun refreshAccent() {
        val e = focusedEntry ?: return
        val a = accentFor(e); stageTag.setTextColor(a); railAdapter.tint(a); glowTo(a)
    }

    private fun applyGlow() { glowView.visibility = if (prefs.glow) View.VISIBLE else View.GONE; glowView.setStatic(prefs.performance) }

    private fun openSettings(cat: Int = prefs.settingsCategory) {
        if (settings != null) return
        dialogs.toList().forEach { it.dismiss() }
        val sc = SettingsScreen(this, s, getString(R.string.settings), categories(), cat, { prefs.settingsCategory = it }) { closeSettings() }
        settings = sc
        homeContent.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        sc.show(frame)
    }

    private fun closeSettings() {
        val sc = settings ?: return
        settings = null
        homeContent.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        sc.close { frame.removeView(sc); restoreFocus() }
    }

    private fun categories(): List<Category> {
        val nf = NumberFormat.getInstance(Lang.locale(this))
        fun g(r: Int) = getString(r)
        val hiddenCount = all.count { it.key in prefs.hidden }
        val ag = all.firstOrNull { it.pkg == "com.adguard.android" } ?: all.firstOrNull { it.pkg.startsWith("com.adguard.") }
        val agVersion = ag?.let { try { packageManager.getPackageInfo(it.pkg, 0).versionName } catch (_: Exception) { "" } }
        val appInfo = try { packageManager.getPackageInfo(packageName, 0) } catch (_: Exception) { null }

        val general = Category(R.drawable.ic_cat_general, g(R.string.cat_general), g(R.string.d_general)) { listOf(
            Toggle(g(R.string.return_start_home), g(R.string.return_start_home_d), { prefs.returnStartOnHome }, { prefs.returnStartOnHome = it }),
            Action(g(R.string.set_home), g(R.string.set_home_d)) { homeSettings() },
            Action(g(R.string.reset_all), g(R.string.reset_all_d)) { confirmRestore() },
        ) }
        val appearance = Category(R.drawable.ic_cat_appearance, g(R.string.cat_appearance), g(R.string.d_appearance)) { listOf(
            Choice(g(R.string.wallpaper), null, WALLPAPERS.map { getString(it.nameRes) }, { prefs.wallpaper }, { prefs.wallpaper = it; applyWallpaper() }),
            Choice(g(R.string.accent_color), g(R.string.accent_color_d),
                listOf(R.string.accent_auto, R.string.accent_blue, R.string.accent_green, R.string.accent_coral, R.string.accent_gold, R.string.accent_violet).map { getString(it) },
                { prefs.accentMode }, { prefs.accentMode = it; refreshAccent() }, swatches = listOf(T.TEXT_DIM) + accentColors.toList()),
            Choice(g(R.string.dimming), g(R.string.dimming_d), listOf(g(R.string.off), g(R.string.level_light), g(R.string.level_medium), g(R.string.level_strong)),
                { prefs.dimming }, { prefs.dimming = it; dimView.setBackgroundColor(dimColor()) }),
            Choice(g(R.string.card_size), g(R.string.card_size_d), listOf(g(R.string.size_small), g(R.string.size_medium), g(R.string.size_large)),
                { prefs.cardSize }, { prefs.cardSize = it; prefs.reopenSettings = true; main.postDelayed({ recreate() }, T.d(T.MED)) }),
            Choice(g(R.string.screensaver), g(R.string.screensaver_d), SAVER_MINUTES.map { if (it == 0) g(R.string.off) else getString(R.string.minutes_fmt, it) },
                { prefs.saver }, { prefs.saver = it; armSaver() }),
            Toggle(g(R.string.clock), null, { prefs.showClock }, { prefs.showClock = it; clockCol.visibility = if (it) View.VISIBLE else View.INVISIBLE }),
        ) }
        val apps = Category(R.drawable.ic_cat_apps, g(R.string.cat_apps), g(R.string.d_apps)) { listOf(
            Action(g(R.string.manage_apps), null) { openManageApps() },
            Action(g(R.string.hidden_apps), null, { resources.getQuantityString(R.plurals.apps_count, hiddenCount, hiddenCount) }) { openHidden() },
            Toggle(g(R.string.show_system_apps), g(R.string.show_system_apps_d), { prefs.showSystemApps }, { prefs.showSystemApps = it; loadApps() }),
            Choice(g(R.string.sort_apps), null, listOf(g(R.string.sort_az), g(R.string.sort_new)), { prefs.sortMode }, { prefs.sortMode = it; loadApps() }),
            Action(g(R.string.app_shortcuts), g(R.string.app_shortcuts_d)) { openMappings(0) },
            Action(g(R.string.refresh), null) { loadApps() },
        ) }
        val remote = Category(R.drawable.ic_cat_remote, g(R.string.remote_control), g(R.string.d_remote)) { listOf(
            Action(g(R.string.button_test), g(R.string.tester_note)) { startTester() },
            Action(g(R.string.custom_mappings), null, { onOff(prefs.mappingsEnabled) }) { openMappings(0) },
            Toggle(g(R.string.numbers_open_favs), g(R.string.numbers_open_favs_d), { prefs.numbersOpenFavs }, { prefs.numbersOpenFavs = it }),
            Action(g(R.string.return_shortcut), g(R.string.return_shortcut_d)) { startLearn("home", "", g(R.string.action_home)) },
            Action(g(R.string.input_devices), null, { nf.format(listInputDevices().size) }) { openDevices() },
            Info(g(R.string.floating_home), g(R.string.floating_home_d)),
        ) }
        val language = Category(R.drawable.ic_cat_language, g(R.string.cat_language), g(R.string.d_language)) { listOf(
            Action(g(R.string.language), null, { currentLanguageName() }) { openLanguage() },
            Choice(g(R.string.time_format), null, listOf(g(R.string.system_default), g(R.string.time_12), g(R.string.time_24)), { prefs.timeFormat }, { prefs.timeFormat = it; applyTimeFormat(timeClock) }),
            Info(g(R.string.text_direction), null, g(if (Lang.isRtl(this)) R.string.dir_rtl else R.string.dir_ltr)),
            Info(g(R.string.languages_available), null, nf.format(LANGUAGES.size + 0)),
        ) }
        val display = Category(R.drawable.ic_cat_display, g(R.string.cat_display), g(R.string.d_display)) { listOf(
            Choice(g(R.string.animation), g(R.string.animation_d), listOf(g(R.string.anim_full), g(R.string.anim_subtle), g(R.string.off)),
                { 2 - prefs.animLevel }, { prefs.animLevel = 2 - it; T.init(this, prefs.animLevel, prefs.performance) }),
            Toggle(g(R.string.accent_glow), g(R.string.accent_glow_d), { prefs.glow }, { prefs.glow = it; applyGlow() }),
            Toggle(g(R.string.performance), g(R.string.performance_d), { prefs.performance }, { prefs.performance = it; T.init(this, prefs.animLevel, it); applyGlow() }),
        ) }
        val adguard = Category(R.drawable.ic_cat_adguard, "AdGuard", g(R.string.d_adguard)) { listOf(
            Info(g(R.string.ag_status), null, if (ag != null) getString(R.string.ag_installed, agVersion) else g(R.string.not_installed)),
            if (ag != null) Action(g(R.string.ag_launch), null) { launch(ag) }
            else Action(g(R.string.adguard_open_page), null) {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ADGUARD_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) { Toast.makeText(this, getString(R.string.cant_open, "AdGuard"), Toast.LENGTH_LONG).show() }
            },
            Info(g(R.string.ag_compat), getString(R.string.ag_compat_d, android.os.Build.VERSION.RELEASE)),
            Info(g(R.string.ag_verify), null),
            Info(g(R.string.adguard_note), null),
        ) }
        val privacy = Category(R.drawable.ic_cat_privacy, g(R.string.cat_privacy), g(R.string.d_privacy)) { listOf(
            Info(g(R.string.priv_stored), getString(R.string.priv_counts, nf.format(prefs.favorites.size), nf.format(prefs.hidden.size), nf.format(prefs.bindings.size))),
            Info(g(R.string.priv_network), null, if (prefs.weatherCity > 0) g(R.string.priv_net_weather) else g(R.string.none)),
            Info(g(R.string.priv_permissions), null, g(R.string.priv_perm_internet)),
            Info(g(R.string.priv_diag), null, g(R.string.none)),
            Info(g(R.string.priv_note), null),
            Action(g(R.string.reset_all), null) { confirmRestore() },
        ) }
        val widgets = Category(R.drawable.ic_cat_apps, g(R.string.cat_widgets), g(R.string.d_widgets)) {
            val homeOn = Widgets.parse(prefs.homeWidgets); val saverOn = Widgets.parse(prefs.saverWidgets)
            listOf<Item>(Info(g(R.string.widgets_home), null)) +
                Widgets.ALL.map { d -> Toggle(g(d.nameRes), null, { d.id in Widgets.parse(prefs.homeWidgets) }, { prefs.homeWidgets = Widgets.toggle(prefs.homeWidgets, d.id, it); applyWidgets() }) } +
                listOf<Item>(
                    Choice(g(R.string.weather_city), g(R.string.weather_city_d), listOf(g(R.string.weather_unset)) + Weather.CITIES.map { it.name },
                        { prefs.weatherCity }, { prefs.weatherCity = it; prefs.weatherCache = ""; if (it > 0) { prefs.homeWidgets = Widgets.toggle(prefs.homeWidgets, "weather", true); applyWidgets() } }),
                    Choice(g(R.string.weather_unit), null, listOf(g(R.string.celsius), g(R.string.fahrenheit)), { prefs.weatherUnit }, { prefs.weatherUnit = it; WidgetStrip.refreshAll() }),
                ) +
                Info(g(R.string.widgets_saver), null) +
                Widgets.ALL.map { d -> Toggle(g(d.nameRes), null, { d.id in Widgets.parse(prefs.saverWidgets) }, { prefs.saverWidgets = Widgets.toggle(prefs.saverWidgets, d.id, it) }) }
        }
        val about = Category(R.drawable.ic_cat_about, g(R.string.cat_about), g(R.string.d_about)) { listOf(
            Info(g(R.string.app_name), null, getString(R.string.ver_build, appInfo?.versionName ?: "", nf.format(buildNumber(appInfo)))),
            Info(g(R.string.developer), null, "L1avZh"),
            Info(g(R.string.licenses), g(R.string.licenses_d)),
            Secret(g(R.string.egg_hint)) { showEgg() },
            if (eggRevealed) Info(g(R.string.egg_sequence), null) else Action(g(R.string.egg_reveal), null) { eggRevealed = true; settings?.refresh() },
        ) }
        return listOf(general, appearance, widgets, apps, remote, language, display, adguard, privacy, about)
    }

    @Suppress("DEPRECATION")
    private fun buildNumber(i: android.content.pm.PackageInfo?): Long =
        if (i == null) 0L else if (android.os.Build.VERSION.SDK_INT >= 28) i.longVersionCode else i.versionCode.toLong()

    private fun confirmRestore() {
        val items = listOf(
            MenuItem(getString(R.string.cancel)) { it.dismiss() },
            MenuItem(getString(R.string.restore)) { d ->
                d.dismiss(); prefs.resetAll()
                Toast.makeText(this, getString(R.string.settings_reset), Toast.LENGTH_SHORT).show()
                main.postDelayed({ recreate() }, T.d(T.MED))
            },
        )
        track(showMenu(s, getString(R.string.restore_title), items, 0) { box, _ ->
            box.addView(TextView(this).apply {
                text = getString(R.string.restore_body); sizePx(s.px(T.BODY - 4)); setTextColor(T.TEXT_DIM); textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setPaddingRelative(s.px(8), 0, s.px(8), s.px(14))
            })
        })
    }

    /** Every installed app with its state; OK opens the same actions as the home screen menu. */
    private fun openManageApps() {
        val favs = prefs.favorites.toSet()
        val items = all.map { e ->
            val tag = when { e.key in prefs.hidden -> getString(R.string.hidden_tag); e.key in favs -> getString(R.string.tag_favorite); else -> null }
            MenuItem(e.label, tag) { d -> d.dismiss(); manageApp(e) }
        }
        track(showMenu(s, getString(R.string.manage_apps), items))
    }

    private fun manageApp(e: AppEntry) {
        val isFav = e.key in prefs.favorites; val isHidden = e.key in prefs.hidden
        val items = listOf(
            MenuItem(getString(if (isFav) R.string.remove_fav else R.string.add_fav)) { d ->
                prefs.favorites = if (isFav) prefs.favorites - e.key else prefs.favorites + e.key; d.dismiss(); render(); openManageApps()
            },
            MenuItem(getString(if (isHidden) R.string.show else R.string.hide)) { d ->
                prefs.hidden = if (isHidden) prefs.hidden - e.key else prefs.hidden + e.key; d.dismiss(); render(); openManageApps()
            },
            MenuItem(getString(R.string.app_info)) { d ->
                d.dismiss()
                try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${e.pkg}"))) }
                catch (_: Exception) { Toast.makeText(this, getString(R.string.info_unavailable), Toast.LENGTH_SHORT).show() }
            },
        )
        track(showMenu(s, e.label, items))
    }

    private fun openLanguage() {
        val cur = Lang.current(this)
        val tick = "✓"
        val items = mutableListOf(MenuItem(getString(R.string.system_default), if (cur.isEmpty()) tick else null) { d -> chooseLanguage(d, "") })
        LANGUAGES.forEach { l -> items += MenuItem(l.name, if (l.tag == cur) tick else null) { d -> chooseLanguage(d, l.tag) } }
        val focus = if (cur.isEmpty()) 0 else 1 + LANGUAGES.indexOfFirst { it.tag == cur }
        track(showMenu(s, getString(R.string.language), items, focus.coerceAtLeast(0)))
    }

    private fun chooseLanguage(d: Dialog, tag: String) {
        d.dismiss()
        if (tag == Lang.current(this)) return
        prefs.reopenSettings = true
        Lang.apply(this, tag)
    }

    private fun openHidden() {
        val hidden = all.filter { it.key in prefs.hidden } + (if (ADGUARD_ENTRY.key in prefs.hidden) listOf(ADGUARD_ENTRY) else emptyList())
        val items = if (hidden.isEmpty()) listOf(MenuItem(getString(R.string.no_hidden)) { it.dismiss() })
        else hidden.map { e -> MenuItem(e.label, getString(R.string.show)) { d -> prefs.hidden = prefs.hidden - e.key; d.dismiss(); render(); openHidden() } }
        track(showMenu(s, getString(R.string.hidden_apps), items))
    }

    private fun homeSettings() {
        try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)); return } catch (_: Exception) {}
        track(showMenu(s, getString(R.string.set_home), listOf(
            MenuItem(getString(R.string.no_home_picker)) { it.dismiss() },
            MenuItem(getString(R.string.use_adb)) { it.dismiss() },
        )))
    }

    private fun openSearch() {
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lateinit var dialog: Dialog
        fun refresh(q: String) {
            results.removeAllViews()
            val hits = if (q.isBlank()) emptyList() else visible().filter { it.label.contains(q, ignoreCase = true) }.take(6)
            if (q.isNotBlank() && hits.isEmpty()) results.addView(TextView(this).apply {
                text = getString(R.string.no_results); sizePx(s.px(T.BODY)); setTextColor(T.TEXT_DIM)
                setPaddingRelative(s.px(24), s.px(16), s.px(24), s.px(16))
            })
            for (e in hits) results.addView(TextView(this).apply {
                text = e.label; sizePx(s.px(T.BODY)); setTextColor(T.TEXT); isFocusable = true; isClickable = true
                setPaddingRelative(s.px(24), s.px(16), s.px(24), s.px(16))
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), rounded(T.ACCENT, s.px(T.R_ROW)))
                    addState(intArrayOf(), rounded(Color.TRANSPARENT, s.px(T.R_ROW)))
                }
                setOnClickListener { dialog.dismiss(); launch(e) }
            })
        }
        dialog = showMenu(s, getString(R.string.search_apps), emptyList()) { box, _ ->
            val input = EditText(this).apply {
                hint = getString(R.string.type_to_search); sizePx(s.px(T.BODY)); setTextColor(T.TEXT); setHintTextColor(T.TEXT_DIM)
                setSingleLine(); imeOptions = EditorInfo.IME_ACTION_DONE
                background = rounded(0x22FFFFFF, s.px(T.R_ROW)); setPaddingRelative(s.px(24), s.px(16), s.px(24), s.px(16))
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(x: Editable?) { refresh(x?.toString().orEmpty()) }
                    override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                    override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                })
            }
            box.addView(input, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = s.px(12) })
            box.addView(results)
            input.post { input.requestFocus() }
        }
        track(dialog)
    }

    private fun track(d: Dialog) { dialogs += d; d.setOnDismissListener { dialogs -= d } }

    // ---------- Focus ----------

    private fun focusRail(idx: Int) {
        val n = railAdapter.itemCount
        if (n == 0) return
        val i = idx.coerceIn(0, n - 1)
        rail.scrollToPosition(i)
        rail.post {
            val v = rail.findViewHolderForAdapterPosition(i)?.itemView
            if (v != null) v.requestFocus() else rail.postDelayed({ rail.findViewHolderForAdapterPosition(i)?.itemView?.requestFocus() }, 100)
        }
    }

    private fun focusStart() {
        setScope(if (favList.isNotEmpty()) 0 else 2)
        lastIdx[scope] = 0
        rail.postDelayed({ focusRail(0) }, 40)
    }

    private fun restoreFocus() { if (railAdapter.itemCount > 0) focusRail(lastIdx[scope]) else focusStart() }

}
