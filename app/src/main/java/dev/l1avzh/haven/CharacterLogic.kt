package dev.l1avzh.haven

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Saved identifiers for the character's look. Unknown or missing values fall back to a default, so old installs just work. */
object Look {
    val OUTFITS = listOf("courier", "runner", "astral")
    val PROPS = listOf("lantern", "tablet", "compass", "none")
    const val DEFAULT_OUTFIT = "courier"
    const val DEFAULT_PROP = "lantern"
    fun outfit(id: String?) = if (id in OUTFITS) id!! else DEFAULT_OUTFIT
    fun prop(id: String?) = if (id in PROPS) id!! else DEFAULT_PROP
}

/** What the launcher tells the character. Nothing here knows about rendering or about specific screens. */
sealed class CharInput {
    /** Focus moved to something at ([x], [y]), both in -1..1 relative to the screen centre (x right, y up). */
    class Focus(val x: Float, val y: Float) : CharInput()
    class Key(val kind: KeyKind, val repeat: Int = 0) : CharInput()
    /** A different screen or category became visible. */
    object ScreenChanged : CharInput()
    class Studio(val action: StudioAction) : CharInput()
}
enum class KeyKind { DIRECTION, OK, BACK }
enum class StudioAction { PREVIEW, EQUIP, APPLY, CANCEL }

/** A short, deliberate reaction. Higher priority wins; a lower one never interrupts it. */
enum class Beat(val priority: Int, val durationMs: Long) {
    NAVIGATE(1, 420), CATEGORY(2, 560), BACK(3, 520), PREVIEW(3, 650), CANCEL(4, 520), LONG_PRESS(4, 700), CONFIRM(5, 700), APPLY(6, 800), EQUIP(6, 1100),
}

/**
 * The explicit input-to-reaction mapping. It decides which beat an input starts, if any, and keeps a burst of key
 * repeats from restarting animations: repeats only steer the gaze, they never start a beat.
 */
class CharacterReactor(var enabled: Boolean = true) {
    var gazeX = 0f; private set
    var gazeY = 0f; private set
    private var beat: Beat? = null
    private var startedMs = 0L
    private var lastNavMs = Long.MIN_VALUE / 2

    class Active(val beat: Beat, val progress: Float)

    fun onInput(i: CharInput, nowMs: Long) {
        when (i) {
            is CharInput.Focus -> { gazeX = i.x.coerceIn(-1f, 1f); gazeY = i.y.coerceIn(-1f, 1f); start(Beat.NAVIGATE, nowMs) }
            is CharInput.Key -> when {
                i.repeat > 0 -> {}                                    // held keys steer the gaze via Focus only
                i.kind == KeyKind.OK -> start(Beat.CONFIRM, nowMs)
                i.kind == KeyKind.BACK -> start(Beat.BACK, nowMs)
                else -> {}
            }
            CharInput.ScreenChanged -> start(Beat.CATEGORY, nowMs)
            is CharInput.Studio -> start(when (i.action) {
                StudioAction.PREVIEW -> Beat.PREVIEW; StudioAction.EQUIP -> Beat.EQUIP; StudioAction.APPLY -> Beat.APPLY; StudioAction.CANCEL -> Beat.CANCEL
            }, nowMs)
        }
    }

    /** A held key: one long-press reaction, however many repeats follow. */
    fun onLongPress(nowMs: Long) = start(Beat.LONG_PRESS, nowMs)

    private fun start(b: Beat, now: Long) {
        if (!enabled) return
        val cur = active(now)
        if (b == Beat.NAVIGATE) { if (now - lastNavMs < NAV_GAP_MS) return; lastNavMs = now }
        if (cur != null) {
            if (b.priority < cur.beat.priority) return                      // never interrupt something stronger
            if (b.priority == cur.beat.priority && cur.progress < 0.35f) return   // same strength: let it get going first
        }
        beat = b; startedMs = now
    }

    fun active(nowMs: Long): Active? {
        val b = beat ?: return null
        val p = (nowMs - startedMs).toFloat() / b.durationMs
        if (p >= 1f) { beat = null; return null }
        return Active(b, max(0f, p))
    }

    fun reset() { beat = null; gazeX = 0f; gazeY = 0f }

    companion object { const val NAV_GAP_MS = 90L }
}

/** Joint rotation offsets, in radians as (pitch about x, yaw about y, roll about z), plus a few non-joint channels. */
class Pose {
    val rot = FloatArray(JOINTS.size * 3)
    var hipsX = 0f; var hipsY = 0f; var hipsZ = 0f
    var rootYaw = 0f
    /** 0 = eyes closed, 1 = open, above 1 = widened for a moment. */
    var eyes = 1f
    fun clear() { rot.fill(0f); hipsX = 0f; hipsY = 0f; hipsZ = 0f; rootYaw = 0f; eyes = 1f }
    fun add(joint: String, pitch: Float, yaw: Float, roll: Float) {
        val i = INDEX[joint] ?: return; rot[i*3] += pitch; rot[i*3 + 1] += yaw; rot[i*3 + 2] += roll
    }
    fun get(joint: String, axis: Int): Float { val i = INDEX[joint] ?: return 0f; return rot[i * 3 + axis] }

    companion object {
        val JOINTS = listOf("hips", "spine", "chest", "neck", "head", "clavicle_l", "clavicle_r", "upperarm_l", "upperarm_r",
            "forearm_l", "forearm_r", "hand_l", "hand_r", "thigh_l", "thigh_r", "shin_l", "shin_r")
        val INDEX = JOINTS.withIndex().associate { it.value to it.index }
    }
}

/**
 * Turns time, reactions and the gaze into a pose. Idle motion is a slow breath, a weight shift, small head drift and
 * blinks; beats add short gestures on top. [motion] is 0 for reduced motion (a still pose), up to 1 for full motion.
 */
class CharacterAnimator(private val reactor: CharacterReactor) {
    private var gx = 0f; private var gy = 0f
    private var lastSec = -1.0
    private var nextBlinkSec = 2.5; private var blinkStart = -10.0
    private var blinkSeed = 7
    val pose = Pose()

    fun update(nowMs: Long, motion: Float): Pose {
        val t = nowMs / 1000.0
        val dt = if (lastSec < 0) 0.016 else (t - lastSec).coerceIn(0.0, 0.1)
        lastSec = t
        pose.clear()
        if (motion <= 0f) { gx = 0f; gy = 0f; return pose }
        val k = 1 - exp(-dt * 7.0).toFloat()                        // gaze eases toward its target, frame-rate independent
        gx += (reactor.gazeX - gx) * k; gy += (reactor.gazeY - gy) * k

        // idle: breathing, weight shift, small head drift
        val breath = sin(t * 2 * PI * 0.26).toFloat()
        val sway = sin(t * 2 * PI * 0.07).toFloat()
        pose.hipsY = 0.003f * breath * motion
        pose.hipsX = 0.012f * sway * motion
        pose.add("spine", -0.014f * breath * motion, 0f, -0.03f * sway * motion)
        pose.add("chest", -0.02f * breath * motion, 0f, 0.02f * sway * motion)
        pose.add("head", 0.02f * sin(t * 2 * PI * 0.11).toFloat() * motion, 0.05f * sin(t * 2 * PI * 0.083).toFloat() * motion, 0f)
        pose.add("upperarm_l", 0f, 0f, (0.02f * breath) * motion); pose.add("upperarm_r", 0f, 0f, (-0.02f * breath) * motion)
        pose.add("thigh_l", 0f, 0f, -0.03f * sway * motion); pose.add("thigh_r", 0f, 0f, 0.03f * sway * motion)

        // attention: the head and upper body turn a little toward whatever has focus
        val look = 0.55f * motion
        pose.add("head", -gy * 0.30f * look, gx * 0.65f * look, 0f)
        pose.add("neck", -gy * 0.10f * look, gx * 0.22f * look, 0f)
        pose.add("chest", 0f, gx * 0.16f * look, 0f)

        // blink
        if (t >= nextBlinkSec) { blinkStart = t; blinkSeed = (blinkSeed * 1103515245 + 12345) and 0x7fffffff; nextBlinkSec = t + 2.6 + (blinkSeed % 3000) / 1000.0 }
        val bu = ((t - blinkStart) / 0.16).toFloat()
        if (bu in 0f..1f) pose.eyes = 1f - 0.92f * sin(bu * PI.toFloat())

        reactor.active(nowMs)?.let { a -> beat(a.beat, a.progress, motion) }
        return pose
    }

    private fun shape(u: Float): Float {                           // quick in, short hold, soft out; 0 at both ends
        val a = smooth(u / 0.28f); val r = 1 - smooth((u - 0.6f) / 0.4f)
        return a * r
    }
    private fun smooth(x: Float): Float { val c = min(1f, max(0f, x)); return c * c * (3 - 2 * c) }

    private fun beat(b: Beat, u: Float, m: Float) {
        val e = shape(u) * m
        when (b) {
            Beat.NAVIGATE -> {                                      // a small nod toward the new target and a glance of the hand
                pose.add("head", 0.05f * e, gx * 0.10f * e, 0f)
                pose.add("spine", 0.02f * e, gx * 0.06f * e, 0f)
                pose.add("forearm_r", -0.10f * e, 0f, 0f)
            }
            Beat.CATEGORY -> {                                      // chest lifts, shoulders settle: "new place"
                pose.add("chest", -0.06f * e, 0f, 0f); pose.add("clavicle_l", 0f, 0f, 0.06f * e); pose.add("clavicle_r", 0f, 0f, -0.06f * e)
                pose.add("head", 0.04f * e, 0f, 0f)
            }
            Beat.BACK -> {                                          // a step back and a small open-hand dismissal
                pose.hipsZ = -0.035f * e
                pose.add("spine", -0.04f * e, 0f, 0f); pose.add("head", 0f, -0.18f * sin(u * 2 * PI.toFloat()) * m, 0f)
                pose.add("upperarm_l", -0.25f * e, 0f, -0.2f * e); pose.add("forearm_l", -0.45f * e, 0f, 0f)
            }
            Beat.CONFIRM -> {                                       // nod, raised hand, eyes widen for a moment
                pose.add("head", 0.20f * sin(u * PI.toFloat() * 1.6f) * m, 0f, 0f)
                pose.add("upperarm_r", -0.85f * e, 0f, 0.35f * e); pose.add("forearm_r", -1.05f * e, 0f, 0f); pose.add("hand_r", -0.2f * e, 0f, 0f)
                pose.hipsY += 0.012f * e
                pose.eyes = max(pose.eyes, 1f + 0.35f * e)
            }
            Beat.LONG_PRESS -> {                                    // both hands lift: "options"
                pose.add("upperarm_l", -0.55f * e, 0f, -0.15f * e); pose.add("forearm_l", -0.8f * e, 0f, 0f)
                pose.add("upperarm_r", -0.55f * e, 0f, 0.15f * e); pose.add("forearm_r", -0.8f * e, 0f, 0f)
                pose.add("head", 0.05f * e, 0f, 0f)
            }
            Beat.PREVIEW -> {                                       // quarter turn to show the outfit off
                pose.rootYaw = 0.55f * e
                pose.add("head", 0f, -0.30f * e, 0f)
                pose.add("upperarm_l", -0.2f * e, 0f, -0.25f * e)
            }
            Beat.CANCEL -> {                                        // shrug
                pose.add("clavicle_l", 0f, 0f, 0.20f * e); pose.add("clavicle_r", 0f, 0f, -0.20f * e)
                pose.add("head", 0f, 0f, 0.10f * e); pose.add("upperarm_l", 0f, 0f, -0.10f * e); pose.add("upperarm_r", 0f, 0f, 0.10f * e)
            }
            Beat.APPLY -> {                                         // a small bow of thanks
                pose.add("spine", 0.30f * shape(u) * m, 0f, 0f); pose.add("head", 0.22f * shape(u) * m, 0f, 0f)
                pose.add("upperarm_l", 0f, 0f, -0.05f * e)
            }
            Beat.EQUIP -> {                                         // a full turn on the spot while the new look is swapped in
                pose.rootYaw = (2 * PI.toFloat()) * smooth(u) * m
                pose.add("upperarm_l", -0.3f * e, 0f, -0.45f * e); pose.add("upperarm_r", -0.3f * e, 0f, 0.45f * e)
                pose.hipsY += 0.015f * sin(u * PI.toFloat()) * m
            }
        }
    }
}

/** Where on screen focus is, as the character wants it: centre-relative, x right, y up, both in -1..1. */
fun gazeFromScreen(cx: Float, cy: Float, width: Int, height: Int, originX: Float, originY: Float): Pair<Float, Float> {
    if (width <= 0 || height <= 0) return 0f to 0f
    val dx = (cx - originX) / (width / 2f)
    val dy = (originY - cy) / (height / 2f)
    return dx.coerceIn(-1f, 1f) to dy.coerceIn(-1f, 1f)
}
