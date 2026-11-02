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
    private lateinit var glowView: View
    private lateinit var header: LinearLayout
    private lateinit var hero: View
    private lateinit var heroCaption: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroHint: TextView
    private lateinit var favBlock: View
    private lateinit var allBlock: View
    private lateinit var favEmpty: TextView
    private lateinit var favRV: RecyclerView
    private lateinit var allRV: RecyclerView
    private lateinit var favAdapter: TileAdapter
    private lateinit var allAdapter: TileAdapter

    private var all: List<AppEntry> = emptyList()
    private var rtl = false
    private val main = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor()
    private val dialogs = mutableListOf<Dialog>()
    private var lastList: RecyclerView? = null
    private var lastPos = 0
    private var focusedKey: String? = null
    private var entered = false

    private val glowDrawable = GradientDrawable()
    private var glowColor = T.ACCENT
    private var glowAnim: ValueAnimator? = null

    // Hidden Easter egg: a remote-control sequence on the home screen, or seven OK presses on About's version row.
    private var overlay: KeyOverlay? = null
    private var egg: EggView? = null
    private val autoCloseEgg = Runnable { closeEgg() }
    private var konamiIdx = 0
    private var konamiLast = 0L
    private var settings: SettingsScreen? = null
    private var eggRevealed = false
    private lateinit var homeContent: LinearLayout
    private lateinit var dimView: View
    private lateinit var timeClock: TextClock
    private lateinit var clockCol: LinearLayout
    private var favW = 400
    private var libW = 256
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
        T.init(this, prefs.animLevel, prefs.performance)
        favW = intArrayOf(352, 400, 448)[prefs.cardSize]; libW = intArrayOf(224, 256, 288)[prefs.cardSize]
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
        closeOverlay()
        settings?.let { frame.removeView(it) }; settings = null; homeContent.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        T.init(this, prefs.animLevel, prefs.performance)
        if (currentFocus == null) restoreFocus()
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

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        overlay?.let { return it.onKey(e, getString(R.string.yes), KeyOverlay.Labels(getString(R.string.test_code), getString(R.string.test_device), getString(R.string.test_usable))) }
        if (egg != null) { if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) closeEgg(); return true }
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 && dialogs.isEmpty() && settings == null && konami(e.keyCode)) return true
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 && dialogs.isEmpty() && settings == null && remoteKey(e)) return true
        return super.dispatchKeyEvent(e)
    }

    // ---------- Remote control: mappings, number keys, tester ----------

    /** Custom bindings first, then digits. Only runs on the home screen (no menu open), so text entry is never affected. */
    private fun remoteKey(e: KeyEvent): Boolean {
        if (prefs.mappingsEnabled) prefs.bindings.firstOrNull { it.key == e.keyCode }?.let { runAction(it.action, it.arg); return true }
        if (prefs.numbersOpenFavs && currentFocus !is EditText && e.keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            val n = e.keyCode - KeyEvent.KEYCODE_0
            val fav = favAdapter.items.getOrNull(if (n == 0) 9 else n - 1) ?: return false
            launch(fav); return true
        }
        return false
    }

    private fun runAction(action: String, arg: String) {
        when (action) {
            "app" -> all.firstOrNull { it.key == arg }?.let { launch(it) }
                ?: Toast.makeText(this, getString(R.string.cant_open, arg.substringBefore('/')), Toast.LENGTH_LONG).show()
            "search" -> openSearch()
            "settings" -> openSettings()
            "start" -> focusStart()
        }
    }

    private fun actionLabel(b: Binding) = when (b.action) {
        "app" -> all.firstOrNull { it.key == b.arg }?.label ?: b.arg.substringBefore('/')
        "search" -> getString(R.string.action_search)
        "settings" -> getString(R.string.action_settings)
        else -> getString(R.string.action_start)
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

    /** Waits for one key press and binds it. Back cancels. */
    private fun startLearn(action: String, arg: String, label: String) {
        showOverlay(KeyOverlay(this, s, label, getString(R.string.press_button), null, { e ->
            prefs.bindings = prefs.bindings.filter { it.key != e.keyCode } + Binding(e.keyCode, action, arg)
            closeOverlay()
            Toast.makeText(this, getString(R.string.mapping_saved, keyName(e.keyCode), label), Toast.LENGTH_LONG).show()
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
        bs.forEach { b -> items += MenuItem(keyName(b.key), actionLabel(b)) { d ->
            d.dismiss()
            track(showMenu(s, keyName(b.key), listOf(MenuItem(getString(R.string.remove), actionLabel(b)) { d2 -> prefs.bindings = prefs.bindings - b; d2.dismiss(); openMappings(0) })))
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
            MenuItem(getString(R.string.action_start)) { d -> d.dismiss(); startLearn("start", "", getString(R.string.action_start)) },
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
        val m = s.px(T.MARGIN)
        frame = FrameLayout(this).apply { clipChildren = false; setBackgroundColor(Color.BLACK) }

        // Layer 1: wallpaper. Layer 2: accent glow. (A bottom vignette was measured and dropped: it cost frame time.)
        wallpaperView = View(this).apply { background = wallpaperDrawable() }
        glowDrawable.gradientType = GradientDrawable.RADIAL_GRADIENT
        glowDrawable.setGradientCenter(if (rtl) 0.8f else 0.2f, 0.22f)
        glowDrawable.gradientRadius = s.px(1250f)
        setGlowColor(glowColor)
        glowView = View(this).apply { background = glowDrawable; visibility = if (prefs.glow && !prefs.performance) View.VISIBLE else View.GONE }
        dimView = View(this).apply { setBackgroundColor(dimColor()) }
        frame.addView(wallpaperView, -1, -1); frame.addView(glowView, -1, -1); frame.addView(dimView, -1, -1)

        val content = LinearLayout(this).apply {
            homeContent = this
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            setPaddingRelative(m, s.px(40), m, 0)
        }
        frame.addView(content, -1, -1)
        setContentView(frame)

        // Header: discreet clock + date at the start edge, two icon buttons at the end edge.
        val locale = Lang.locale(this)
        val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
        clockCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        timeClock = TextClock(this)
        clockCol.addView(timeClock.apply {
            applyTimeFormat(this); sizePx(s.px(42f)); setTextColor(T.TEXT)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); includeFontPadding = false
        })
        clockCol.addView(TextClock(this).apply {
            format12Hour = datePattern; format24Hour = datePattern; sizePx(s.px(T.LABEL + 2)); setTextColor(T.TEXT_DIM)
        })
        clockCol.visibility = if (prefs.showClock) View.VISIBLE else View.INVISIBLE
        header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(clockCol, LinearLayout.LayoutParams(0, -2, 1f))
            addView(iconButton(R.drawable.ic_home, getString(R.string.back_to_start)) { focusStart() })
            addView(iconButton(R.drawable.ic_search, getString(R.string.search)) { openSearch() })
            addView(iconButton(R.drawable.ic_settings, getString(R.string.settings)) { openSettings() })
        }
        content.addView(header, LinearLayout.LayoutParams(-1, s.px(84)))

        // Hero: names whatever is focused, in the colour of its artwork. Shows a quiet loading line until apps arrive.
        heroCaption = TextView(this).apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_START; sizePx(s.px(T.LABEL)); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        heroTitle = TextView(this).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START; sizePx(s.px(T.HERO)); setTextColor(T.TEXT); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); includeFontPadding = false
            text = getString(R.string.loading); alpha = 0.5f
        }
        heroHint = TextView(this).apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_START; sizePx(s.px(T.LABEL)); setTextColor(T.TEXT_DIM); alpha = 0.8f }
        hero = Block(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM
            setPadding(0, 0, 0, s.px(28))
            addView(heroCaption)
            addView(heroTitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(8) })
            addView(heroHint, LinearLayout.LayoutParams(-2, -2).apply { topMargin = s.px(10) })
        }
        content.addView(hero, LinearLayout.LayoutParams(-1, s.px(250)))

        // Favorites shelf (large artwork) and library shelf (compact, every app).
        favAdapter = TileAdapter(s, icons, favW, T.FAV_GAP, 1.12f, ::onTile, { menuFor(it, true) }, ::artReady)
        favRV = shelf(favAdapter, favW)
        favEmpty = TextView(this).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            text = getString(R.string.no_favorites)
            sizePx(s.px(T.BODY - 2)); setTextColor(T.TEXT_DIM); gravity = Gravity.CENTER_VERTICAL or Gravity.START; visibility = View.GONE
        }
        favBlock = Block(this).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false
            addView(shelfLabel(getString(R.string.favorites))); addView(favRV, shelfParams(favW))
            addView(favEmpty, LinearLayout.LayoutParams(-1, s.px(favW * 9 / 16 + 52)))
        }
        content.addView(favBlock, LinearLayout.LayoutParams(-1, -2))

        allAdapter = TileAdapter(s, icons, libW, T.LIB_GAP, 1.12f, ::onTile, { menuFor(it, false) }, ::artReady)
        allRV = shelf(allAdapter, libW)
        allBlock = Block(this).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false
            addView(shelfLabel(getString(R.string.library))); addView(allRV, shelfParams(libW))
        }
        content.addView(allBlock, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(26) })

        if (T.animated) listOf(header, hero, favBlock, allBlock).forEach { it.alpha = 0f; it.translationY = s.px(30f) }

        frame.viewTreeObserver.addOnGlobalFocusChangeListener { _, new ->
            val rv = new?.parent as? RecyclerView
            if (rv === favRV || rv === allRV) {
                val pos = rv.getChildAdapterPosition(new)
                val ad = rv.adapter as TileAdapter
                val e = ad.items.getOrNull(pos) ?: return@addOnGlobalFocusChangeListener
                lastList = rv; lastPos = pos
                focusedKey = e.key
                showHero(e, rv === favRV)
                emphasize(rv === favRV)
            }
        }
    }

    private fun shelf(a: TileAdapter, w: Int) = Row(this).apply {
        layoutManager = AnchorLayoutManager(this@MainActivity, a.cellWidth)
        adapter = a; clipChildren = false; clipToPadding = false; overScrollMode = View.OVER_SCROLL_NEVER
        val gapHalf = a.cellWidth - s.px(w)
        setPaddingRelative(s.px(T.MARGIN) - gapHalf / 2, 0, 0, 0)
        itemAnimator = DefaultItemAnimator().apply {
            supportsChangeAnimations = false
            addDuration = T.d(T.MED); removeDuration = T.d(T.FAST); moveDuration = T.d(T.MED)
        }
    }

    /** Shelves bleed to both screen edges so tiles slide off-screen instead of being cut at the margin. */
    private fun shelfParams(w: Int) = LinearLayout.LayoutParams(-1, s.px(w * 9 / 16 + 52)).apply {
        marginStart = -s.px(T.MARGIN); marginEnd = -s.px(T.MARGIN)
    }

    private fun shelfLabel(t: String) = TextView(this).apply {
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        sizePx(s.px(T.LABEL)); setTextColor(T.TEXT_DIM); Caps.set(this, t, 0.16f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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
        glowDrawable.colors = intArrayOf((c and 0x00FFFFFF) or (0x66 shl 24), c and 0x00FFFFFF)
    }

    private fun glowTo(target: Int) {
        glowAnim?.cancel()
        if (!T.animated || target == glowColor) { setGlowColor(target); return }
        glowAnim = ValueAnimator.ofObject(ArgbEvaluator(), glowColor, target).apply {
            duration = T.d(T.SLOW + 100); interpolator = T.STD
            addUpdateListener { setGlowColor(it.animatedValue as Int) }
            start()
        }
    }

    private fun showHero(e: AppEntry, fav: Boolean) {
        val accent = accentFor(e)
        heroTitle.text = e.label; heroTitle.alpha = 1f
        Caps.set(heroCaption, getString(if (e.virtual) R.string.not_installed else if (fav) R.string.tag_favorite else R.string.library), 0.22f)
        heroCaption.setTextColor(accent)
        heroHint.text = if (e.virtual) getString(R.string.adguard_open_page) else getString(R.string.hold_ok)
        glowTo(accent)
        if (T.animated) {
            heroTitle.animate().cancel()
            heroTitle.alpha = 0f; heroTitle.translationX = if (rtl) s.px(18f) else -s.px(18f)
            heroTitle.animate().alpha(1f).translationX(0f).setDuration(T.d(T.MED)).setInterpolator(T.OUT).start()
        }
    }

    /** The shelf holding focus is full strength; the other recedes, which shows where you are. */
    private fun emphasize(favs: Boolean) {
        val d = T.d(T.MED)
        favBlock.animate().alpha(if (favs || favAdapter.itemCount == 0) 1f else 0.5f).setDuration(d).setInterpolator(T.STD).start()
        allBlock.animate().alpha(if (favs) 0.5f else 1f).setDuration(d).setInterpolator(T.STD).start()
    }

    /** Coordinated, short entrance. Everything is already focusable while it plays. */
    private fun playEntrance() {
        if (entered) return
        entered = true
        if (!T.animated) return
        listOf(header to 0L, hero to 50L, favBlock to 110L, allBlock to 180L).forEach { (v, delay) ->
            v.animate().alpha(1f).translationY(0f).setStartDelay(T.d(delay)).setDuration(T.d(T.SLOW)).setInterpolator(T.OUT).start()
        }
    }

    private fun artReady(e: AppEntry) {
        if (e.key != focusedKey) return
        val a = accentFor(e)
        heroCaption.setTextColor(a); glowTo(a)
    }

    // ---------- Data ----------

    private fun loadApps() {
        loader.execute {
            val apps = AppRepo.load(this, prefs.showSystemApps, prefs.sortMode)
            main.post {
                all = apps
                if (!prefs.seeded) prefs.favorites = AppRepo.seedFavorites(apps)
                render()
                if (lastList == null || currentFocus == null) restoreFocus()
                frame.post { playEntrance() }
                if (prefs.reopenSettings) { prefs.reopenSettings = false; openSettings() }
            }
        }
    }

    private fun visible() = all.filter { it.key !in prefs.hidden }

    private fun render() {
        val vis = visible()
        val byKey = vis.associateBy { it.key }
        val favs = prefs.favorites.mapNotNull { byKey[it] }
        // The AdGuard shortcut lives at the end of the library until the real app is installed (or the user hides it).
        val lib = if (!AppRepo.hasAdGuard(all) && ADGUARD_ENTRY.key !in prefs.hidden) vis + ADGUARD_ENTRY else vis
        favAdapter.submit(favs)
        allAdapter.submit(lib)
        val none = favs.isEmpty()
        favRV.visibility = if (none) View.GONE else View.VISIBLE
        favEmpty.visibility = if (none) View.VISIBLE else View.GONE
    }

    // ---------- Actions ----------

    private fun onTile(e: AppEntry) { if (e.virtual) adguardMenu() else launch(e) }

    private fun launch(e: AppEntry) {
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
                prefs.hidden = prefs.hidden + ADGUARD_ENTRY.key; d.dismiss(); render(); refocus(allRV, 0, allRV, 0)
            },
        )
        track(showMenu(s, "AdGuard", items) { box, _ ->
            box.addView(TextView(this).apply {
                text = getString(R.string.adguard_note); sizePx(s.px(T.BODY - 6)); setTextColor(T.TEXT_DIM)
                setPaddingRelative(s.px(8), 0, s.px(8), s.px(14))
            })
        })
    }

    private fun menuFor(e: AppEntry, inFavs: Boolean) {
        if (e.virtual) { adguardMenu(); return }
        val favs = prefs.favorites.toMutableList()
        val idx = favs.indexOf(e.key)
        val items = mutableListOf(MenuItem(getString(R.string.open)) { it.dismiss(); launch(e) })
        if (idx < 0) items += MenuItem(getString(R.string.add_fav)) { d ->
            prefs.favorites = favs + e.key; d.dismiss(); render(); refocus(allRV, allAdapter.items.indexOf(e), allRV, 0)
        } else {
            items += MenuItem(getString(R.string.remove_fav)) { d ->
                favs.removeAt(idx); prefs.favorites = favs; d.dismiss(); render()
                refocus(if (inFavs) favRV else allRV, if (inFavs) idx.coerceAtMost(favAdapter.items.lastIndex) else allAdapter.items.indexOf(e), allRV, 0)
            }
            if (inFavs) {
                val visFavs = favAdapter.items.map { it.key }
                val vi = visFavs.indexOf(e.key)
                if (vi > 0) items += MenuItem(getString(R.string.move_earlier)) { d -> swap(favs, e.key, visFavs[vi - 1]); d.dismiss(); render(); refocus(favRV, vi - 1, allRV, 0) }
                if (vi < visFavs.lastIndex) items += MenuItem(getString(R.string.move_later)) { d -> swap(favs, e.key, visFavs[vi + 1]); d.dismiss(); render(); refocus(favRV, vi + 1, allRV, 0) }
            }
        }
        items += MenuItem(getString(R.string.hide)) { d ->
            prefs.hidden = prefs.hidden + e.key; d.dismiss(); render(); refocus(if (inFavs) favRV else allRV, 0, allRV, 0)
        }
        items += MenuItem(getString(R.string.app_info)) { d ->
            d.dismiss()
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${e.pkg}"))) }
            catch (_: Exception) { Toast.makeText(this, getString(R.string.info_unavailable), Toast.LENGTH_SHORT).show() }
        }
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
        val e = (favAdapter.items + allAdapter.items).firstOrNull { it.key == focusedKey } ?: return
        val a = accentFor(e); heroCaption.setTextColor(a); glowTo(a)
    }

    private fun applyGlow() { glowView.visibility = if (prefs.glow && !prefs.performance) View.VISIBLE else View.GONE }

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
            Action(g(R.string.return_shortcut), g(R.string.return_shortcut_d)) { startLearn("start", "", g(R.string.action_start)) },
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
            Info(g(R.string.priv_network), null, g(R.string.none)),
            Info(g(R.string.priv_permissions), null, g(R.string.none)),
            Info(g(R.string.priv_diag), null, g(R.string.none)),
            Info(g(R.string.priv_note), null),
            Action(g(R.string.reset_all), null) { confirmRestore() },
        ) }
        val about = Category(R.drawable.ic_cat_about, g(R.string.cat_about), g(R.string.d_about)) { listOf(
            Info(g(R.string.app_name), null, getString(R.string.ver_build, appInfo?.versionName ?: "", nf.format(buildNumber(appInfo)))),
            Info(g(R.string.developer), null, "L1avZh"),
            Info(g(R.string.licenses), g(R.string.licenses_d)),
            Secret(g(R.string.egg_hint)) { showEgg() },
            if (eggRevealed) Info(g(R.string.egg_sequence), null) else Action(g(R.string.egg_reveal), null) { eggRevealed = true; settings?.refresh() },
        ) }
        return listOf(general, appearance, apps, remote, language, display, adguard, privacy, about)
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

    private fun focusItem(rv: RecyclerView, pos: Int) {
        if (pos < 0 || pos >= (rv.adapter?.itemCount ?: 0)) return
        rv.scrollToPosition(pos)
        rv.post {
            val v = rv.findViewHolderForAdapterPosition(pos)?.itemView
            if (v != null) v.requestFocus() else rv.postDelayed({ rv.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus() }, 100)
        }
    }

    /** Focus [pos] in [rv] if it is on screen, otherwise fall back to [alt]. */
    private fun refocus(rv: RecyclerView, pos: Int, alt: RecyclerView, altPos: Int) {
        main.postDelayed({
            if (rv.visibility == View.VISIBLE && (rv.adapter?.itemCount ?: 0) > 0) focusItem(rv, pos) else focusItem(alt, altPos)
        }, T.d(T.MED))
    }

    private fun focusStart() {
        allRV.scrollToPosition(0)
        if (favRV.visibility == View.VISIBLE && favAdapter.itemCount > 0) focusItem(favRV, 0) else focusItem(allRV, 0)
    }

    private fun restoreFocus() {
        val rv = lastList
        if (rv != null && rv.visibility == View.VISIBLE && lastPos < (rv.adapter?.itemCount ?: 0)) focusItem(rv, lastPos) else focusStart()
    }


}
