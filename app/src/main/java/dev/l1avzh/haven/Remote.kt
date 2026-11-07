package dev.l1avzh.haven

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** One custom mapping: a remote key code (short or long press) bound to an action ("app", "search", "settings", "home"). */
class Binding(val key: Int, val action: String, val arg: String = "", val long: Boolean = false)

/** A held key: Android flags it, and HDMI-CEC repeats arrive as repeat counts. */
fun isLong(e: KeyEvent) = e.isLongPress || e.repeatCount == 1

fun keyName(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")

/** Human description of where an event came from. HDMI-CEC presses arrive as system-injected events (device id -1). */
fun sourceNames(src: Int): String {
    val out = mutableListOf<String>()
    if (src and InputDevice.SOURCE_HDMI == InputDevice.SOURCE_HDMI) out += "HDMI-CEC"
    if (src and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD) out += "keyboard"
    if (src and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD) out += "dpad"
    if (src and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) out += "gamepad"
    if (src and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN) out += "touch"
    return if (out.isEmpty()) "0x" + Integer.toHexString(src) else out.joinToString(", ")
}

fun deviceName(e: KeyEvent): String {
    val d = e.device
    return when {
        d != null -> "${d.name} (id ${e.deviceId})"
        e.deviceId < 0 -> "virtual / system-injected (id ${e.deviceId})"
        else -> "id ${e.deviceId}"
    }
}

fun listInputDevices(): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    for (id in InputDevice.getDeviceIds()) {
        val d = InputDevice.getDevice(id) ?: continue
        if ((d.sources and (InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD)) == 0) continue
        out += d.name to sourceNames(d.sources)
    }
    return out
}

/**
 * Full-screen overlay used for two jobs: showing every key Android delivers ("test"), and waiting for one key
 * to bind to an action ("learn"). It sits above the home screen and swallows keys while open.
 */
class KeyOverlay(
    ctx: Context, private val s: Scale, title: String, hint: String, note: String?,
    private val learn: ((KeyEvent, Boolean) -> Unit)?, private val onExit: () -> Unit,
) : FrameLayout(ctx) {
    private val big = TextView(ctx)
    private val details = TextView(ctx)
    private val history = TextView(ctx)
    private val seen = ArrayDeque<String>()
    private var backCount = 0
    private var backLast = 0L
    private var pending = -1   // key being watched while learning: a quick release is a short press, a hold is a long press

    init {
        setBackgroundColor(0xFA060910.toInt())
        isFocusable = false
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
            text = t; sizePx(s.px(size)); setTextColor(color); gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        col.addView(tv(title, T.TITLE, T.TEXT, true))
        col.addView(tv(hint, T.BODY - 4, T.TEXT_DIM), LinearLayout.LayoutParams(s.px(1200), -2).apply { topMargin = s.px(14) })
        note?.let { col.addView(tv(it, T.LABEL + 2, T.TEXT_DIM), LinearLayout.LayoutParams(s.px(1200), -2).apply { topMargin = s.px(10) }) }
        big.apply { sizePx(s.px(96f)); setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); text = "—" }
        col.addView(big, LinearLayout.LayoutParams(-2, -2).apply { topMargin = s.px(46) })
        details.apply { sizePx(s.px(T.BODY - 2)); setTextColor(T.TEXT); gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setLineSpacing(0f, 1.3f) }
        col.addView(details, LinearLayout.LayoutParams(s.px(1300), -2).apply { topMargin = s.px(24) })
        history.apply { sizePx(s.px(T.LABEL + 2)); setTextColor(T.TEXT_DIM); gravity = Gravity.CENTER }
        col.addView(history, LinearLayout.LayoutParams(s.px(1500), -2).apply { topMargin = s.px(34) })
        addView(col, LayoutParams(-2, -2, Gravity.CENTER))
    }

    /** Returns true: the overlay always consumes the key. */
    fun onKey(e: KeyEvent, yes: String, labels: Labels): Boolean {
        if (learn != null) {
            // Back cancels on a quick press, so a held Back is the only way to bind Back.
            when {
                e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 -> pending = e.keyCode
                e.action == KeyEvent.ACTION_DOWN && pending == e.keyCode && isLong(e) -> { pending = -1; learn.invoke(e, true) }
                e.action == KeyEvent.ACTION_UP && pending == e.keyCode -> {
                    pending = -1
                    if (e.keyCode == KeyEvent.KEYCODE_BACK) onExit() else learn.invoke(e, false)
                }
            }
            return true
        }
        if (e.action != KeyEvent.ACTION_DOWN) return true
        big.text = keyName(e.keyCode)
        details.text = "${labels.code}: ${e.keyCode}  ·  scan ${e.scanCode}\n" +
            "${labels.device}: ${deviceName(e)}\n" +
            "source: ${sourceNames(e.source)}  ·  repeat ${e.repeatCount}${if (e.isLongPress) "  ·  long press" else ""}\n" +
            "${labels.usable}: $yes"
        if (e.repeatCount == 0) { seen.addFirst(keyName(e.keyCode)); while (seen.size > 9) seen.removeLast() }
        history.text = seen.joinToString("   ›   ")
        val now = System.currentTimeMillis()
        if (e.keyCode == KeyEvent.KEYCODE_BACK && e.repeatCount == 0) {
            backCount = if (now - backLast > 2000) 1 else backCount + 1; backLast = now
            if (backCount >= 3) onExit()
        } else if (e.keyCode != KeyEvent.KEYCODE_BACK) backCount = 0
        return true
    }

    class Labels(val code: String, val device: String, val usable: String)
}
