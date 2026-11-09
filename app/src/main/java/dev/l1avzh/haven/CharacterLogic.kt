package dev.l1avzh.haven

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** One selectable character: its asset, the outfits and props that exist for it, and how the camera frames it. */
class CharacterDef(
    val id: String, val outfits: List<String>, val props: List<String>, val defaultOutfit: String, val defaultProp: String,
    val cameraTargetY: Double, val cameraDistance: Double,
) { val asset get() = "character/$id.glb" }

/**
 * Stable identifiers for the character roster. Saved ids that are missing or unknown (older installs, other characters'
 * outfits) fall back to defaults, so every combination that can be read back is valid.
 */
object Look {
    val CHARACTERS = listOf(
        CharacterDef("human", listOf("city", "field"), listOf("lantern", "tablet", "compass", "none"), "city", "lantern", 0.95, 5.3),
        CharacterDef("wizard", listOf("archmage", "apprentice"), listOf("staff", "lantern", "compass", "none"), "archmage", "staff", 0.97, 5.4),
        CharacterDef("robot", listOf("plated", "stealth"), listOf("tablet", "compass", "lantern", "none"), "plated", "tablet", 0.95, 5.35),
    )
    const val DEFAULT_CHARACTER = "human"
    val ALL_PROPS = CHARACTERS.flatMap { it.props }.distinct()

    fun def(id: String?): CharacterDef = CHARACTERS.firstOrNull { it.id == id } ?: CHARACTERS.first { it.id == DEFAULT_CHARACTER }
    fun character(id: String?) = def(id).id
    fun outfit(character: String?, id: String?) = def(character).let { if (id in it.outfits) id!! else it.defaultOutfit }
    fun prop(character: String?, id: String?) = def(character).let { if (id in it.props) id!! else it.defaultProp }
}

/** Everything that decides how the character looks. [star] is the hidden pendant; it is only ever worn once unlocked. */
data class LookState(val character: String, val outfit: String, val prop: String, val star: Boolean) {
    companion object {
        fun of(character: String?, outfit: String?, prop: String?, star: Boolean, starUnlocked: Boolean): LookState {
            val c = Look.character(character)
            return LookState(c, Look.outfit(c, outfit), Look.prop(c, prop), star && starUnlocked)
        }
    }
}

/** What the launcher tells the character. Nothing here knows about rendering or about specific screens. */
sealed class CharInput {
    /** Focus moved to something at ([x], [y]), both in -1..1 relative to the screen centre (x right, y up). */
    class Focus(val x: Float, val y: Float) : CharInput()
    class Key(val kind: KeyKind, val repeat: Int = 0) : CharInput()
    /** A different screen or category became visible. */
    object ScreenChanged : CharInput()
    class Studio(val action: StudioAction) : CharInput()
    /** Focus landed on an app of this kind. Themed, brief, and only a pose: it does not mean anything is playing. */
    class AppFocus(val kind: AppKind) : CharInput()
    /** The user opened an app of this kind. */
    class AppLaunch(val kind: AppKind) : CharInput()
    /** Haven came back to the front after the user had opened another app. */
    object Returned : CharInput()
}
enum class KeyKind { DIRECTION, OK, BACK }
enum class StudioAction { PREVIEW, EQUIP, APPLY, CANCEL }

/** A short, deliberate reaction. Higher priority wins; a lower one never interrupts it. */
enum class Beat(val priority: Int, val durationMs: Long) {
    NAVIGATE(1, 420), CATEGORY(2, 560), LISTEN(2, 1500), WATCH(2, 1300), BACK(3, 520), PREVIEW(3, 650), RETURN(3, 1000), CANCEL(4, 520), LONG_PRESS(4, 700),
    CONFIRM(5, 700), LAUNCH_LISTEN(6, 1100), LAUNCH_WATCH(6, 1100), APPLY(6, 800), EQUIP(6, 1100),
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
    private var lastThemedMs = Long.MIN_VALUE / 2

    /** True while nothing is selected: the character stands relaxed and looks around instead of at a target. */
    var waiting = false

    /** Weather stance and sleep. Sleeping silences every reaction; the stance is a held pose, not a beat. */
    var stance = Stance.NONE
    val sleeping get() = stance == Stance.SLEEP

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
            is CharInput.AppFocus -> if (nowMs - lastThemedMs >= THEMED_GAP_MS) themed(i.kind, Beat.LISTEN, Beat.WATCH, nowMs)
            is CharInput.AppLaunch -> themed(i.kind, Beat.LAUNCH_LISTEN, Beat.LAUNCH_WATCH, nowMs)
            CharInput.Returned -> start(Beat.RETURN, nowMs)
            is CharInput.Studio -> start(when (i.action) {
                StudioAction.PREVIEW -> Beat.PREVIEW; StudioAction.EQUIP -> Beat.EQUIP; StudioAction.APPLY -> Beat.APPLY; StudioAction.CANCEL -> Beat.CANCEL
            }, nowMs)
        }
    }

    private fun themed(kind: AppKind, music: Beat, video: Beat, now: Long) {
        val b = when (kind) { AppKind.MUSIC -> music; AppKind.VIDEO -> video; AppKind.OTHER -> return }
        if (!enabled) return
        lastThemedMs = now
        start(b, now)
    }

    /** A held key: one long-press reaction, however many repeats follow. */
    fun onLongPress(nowMs: Long) = start(Beat.LONG_PRESS, nowMs)

    private fun start(b: Beat, now: Long) {
        if (!enabled || sleeping) return
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

    fun reset() { beat = null; gazeX = 0f; gazeY = 0f; waiting = false }

    companion object { const val NAV_GAP_MS = 90L; const val THEMED_GAP_MS = 1800L }
}

/** Joint rotation offsets, in radians as (pitch about x, yaw about y, roll about z), plus a few non-joint channels. */
class Pose {
    val rot = FloatArray(JOINTS.size * 3)
    var hipsX = 0f; var hipsY = 0f; var hipsZ = 0f
    var rootYaw = 0f
    /** Used for lying down to sleep: roll about the depth axis, a smaller scale, and a shift so the body rests low in the frame. */
    var rootRoll = 0f; var rootScale = 1f; var rootX = 0f; var rootY = 0f
    /** 0 = eyes closed, 1 = open, above 1 = widened for a moment. */
    var eyes = 1f
    fun clear() { rot.fill(0f); hipsX = 0f; hipsY = 0f; hipsZ = 0f; rootYaw = 0f; rootRoll = 0f; rootScale = 1f; rootX = 0f; rootY = 0f; eyes = 1f }
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
    private var wait = 0f                                            // 0..1, eased toward the reactor's waiting flag
    private var sleep = 0f                                           // 0..1, eased toward sleeping
    private val stances = FloatArray(Stance.values().size)           // blend per weather stance
    private var nextBlinkSec = 2.5; private var blinkStart = -10.0
    private var blinkSeed = 7
    val pose = Pose()

    fun update(nowMs: Long, motion: Float): Pose {
        val t = nowMs / 1000.0
        val dt = if (lastSec < 0) 0.016 else (t - lastSec).coerceIn(0.0, 0.1)
        lastSec = t
        pose.clear()
        if (motion <= 0f) {
            // Reduced motion: no easing, no idle, no breathing, but the scene's held pose (lying down, umbrella) still shows.
            gx = 0f; gy = 0f; sleep = if (reactor.sleeping) 1f else 0f
            for (s in Stance.values()) stances[s.ordinal] = if (reactor.stance == s && s != Stance.SLEEP) 1f else 0f
            if (sleep > 0f) sleepPose(t, 1f, 0f) else for (s in Stance.values()) if (stances[s.ordinal] > 0f && s != Stance.NONE) stancePose(s, t, 1f, 0f)
            return pose
        }
        val k = 1 - exp(-dt * 7.0).toFloat()                        // gaze eases toward its target, frame-rate independent
        wait += ((if (reactor.waiting) 1f else 0f) - wait) * (1 - exp(-dt * 3.0).toFloat())
        // while waiting the eyes wander slowly instead of following a target
        val tx = reactor.gazeX * (1 - wait) + 0.55f * sin(t * 0.55).toFloat() * wait
        val ty = reactor.gazeY * (1 - wait) + 0.12f * sin(t * 0.31 + 1.0).toFloat() * wait
        gx += (tx - gx) * k; gy += (ty - gy) * k

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

        sleep += ((if (reactor.sleeping) 1f else 0f) - sleep) * (1 - exp(-dt * 2.2).toFloat())
        for (s in Stance.values()) { val target = if (reactor.stance == s && s != Stance.SLEEP) 1f else 0f; stances[s.ordinal] += (target - stances[s.ordinal]) * (1 - exp(-dt * 2.6).toFloat()) }
        if (sleep > 0.01f) sleepPose(t, sleep, motion)
        else if (wait > 0.01f) waitingPose(t, wait * motion)
        for (s in Stance.values()) if (s != Stance.NONE && s != Stance.SLEEP && stances[s.ordinal] > 0.01f && sleep < 0.5f) stancePose(s, t, stances[s.ordinal], motion)
        reactor.active(nowMs)?.let { a -> beat(a.beat, a.progress, motion) }
        return pose
    }

    /** Lying down: the whole body rolls onto its side under a blanket, arms folded, eyes closed, slow breathing. */
    private fun sleepPose(t: Double, w: Float, motion: Float) {
        val br = sin(t * 2 * PI * 0.14).toFloat() * motion          // with reduced motion the figure still lies down, it just does not breathe
        pose.rootRoll = (PI / 2).toFloat() * w; pose.rootScale = 1f - 0.38f * w; pose.rootX = 0.56f * w; pose.rootY = 0.14f * w
        pose.eyes = 1f - 0.97f * w
        pose.hipsY += 0.004f * br * w
        pose.add("chest", -0.03f * br * w, 0f, 0f); pose.add("head", 0.12f * w, 0f, 0.15f * w)
        pose.add("upperarm_l", -0.55f * w, 0f, -0.15f * w); pose.add("forearm_l", -1.75f * w, 0f, 0f)
        pose.add("upperarm_r", -0.55f * w, 0f, 0.15f * w); pose.add("forearm_r", -1.75f * w, 0f, 0f)
        pose.add("shin_l", 0.18f * w, 0f, 0f); pose.add("shin_r", 0.12f * w, 0f, 0f)
    }

    /** Holding an umbrella overhead with the right arm, plus what the weather does to the rest of the body. */
    private fun stancePose(s: Stance, t: Double, w: Float, motion: Float) {
        pose.add("upperarm_r", -0.8f * w, 0f, 0.0f * w); pose.add("forearm_r", -1.4f * w, 0f, 0f); pose.add("hand_r", -1.0f * w, 0f, 0.45f * w)
        when (s) {
            Stance.RAIN -> { pose.add("head", -0.1f * w, 0f, 0f); pose.add("upperarm_l", -0.35f * w, 0f, -0.28f * w); pose.add("forearm_l", -0.7f * w, 0f, 0f) }
            Stance.SNOW -> { pose.add("head", -0.18f * w, 0f, 0.05f * w); pose.add("upperarm_l", -0.7f * w, 0f, -0.35f * w); pose.add("forearm_l", -0.5f * w, 0f, 0f); pose.add("hand_l", -0.4f * w, 0f, 0f) }
            Stance.STORM -> {
                val gust = sin(t * 9).toFloat() * motion
                pose.add("spine", 0.16f * w, 0f, 0f); pose.add("chest", 0.08f * w, 0f, 0f); pose.hipsZ -= 0.03f * w
                pose.add("upperarm_r", 0f, 0f, 0.05f * gust * w); pose.add("upperarm_l", -1.0f * w, 0f, -0.2f * w); pose.add("forearm_l", -1.3f * w, 0f, 0f)
                pose.eyes = max(pose.eyes, 1f + 0.25f * w)
            }
            Stance.COLD -> {
                val shiver = sin(t * 28).toFloat() * 0.012f * w * motion
                pose.add("upperarm_l", -0.5f * w, 0f, -0.45f * w); pose.add("forearm_l", -2.0f * w, 0f, 0f)
                pose.add("clavicle_l", 0f, 0f, 0.14f * w); pose.add("clavicle_r", 0f, 0f, -0.14f * w)
                pose.add("spine", 0.06f * w, 0f, shiver); pose.add("head", 0.1f * w, 0f, 0f); pose.hipsX += shiver
            }
            else -> {}
        }
    }

    /** Relaxed "waiting for a selection": weight on one leg, a hand raised to the chin, the head tilted, slow breathing. */
    private fun waitingPose(t: Double, w: Float) {
        val sway = sin(t * 2 * PI * 0.05).toFloat()
        pose.hipsX += 0.035f * w; pose.hipsY -= 0.006f * w
        pose.add("spine", 0.02f * w, 0f, -0.05f * w); pose.add("chest", 0f, -0.06f * w, 0.03f * w)
        pose.add("head", 0.04f * w, 0f, 0.1f * w + 0.015f * sway * w)
        pose.add("upperarm_r", -0.55f * w, 0f, 0.2f * w); pose.add("forearm_r", -1.35f * w, 0f, 0f); pose.add("hand_r", -0.2f * w, 0f, 0f)
        pose.add("upperarm_l", 0f, 0f, 0.06f * w); pose.add("forearm_l", -0.12f * w, 0f, 0f)
        pose.add("thigh_l", 0f, 0f, -0.07f * w); pose.add("thigh_r", 0f, 0f, 0.05f * w)
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
            Beat.LISTEN -> {                                        // both hands to the ears, a small nod to a beat that only the character hears
                val bob = sin(u * 2 * PI.toFloat() * 3f) * m
                pose.add("upperarm_l", -0.9f * e, 0f, -0.25f * e); pose.add("forearm_l", -1.7f * e, 0f, 0f)
                pose.add("upperarm_r", -0.9f * e, 0f, 0.25f * e); pose.add("forearm_r", -1.7f * e, 0f, 0f)
                pose.add("head", 0.05f * bob * e, 0f, 0.03f * bob * e); pose.hipsY += 0.006f * bob * e
            }
            Beat.WATCH -> {                                         // turns toward the screen and presents a remote
                pose.rootYaw = -0.25f * e
                pose.add("upperarm_r", -0.7f * e, 0f, 0.3f * e); pose.add("forearm_r", -0.95f * e, 0f, 0f); pose.add("hand_r", -0.4f * e, 0f, 0f)
                pose.add("head", 0.03f * e, 0.2f * e, 0f)
            }
            Beat.LAUNCH_LISTEN -> {                                 // a bigger version of the same gesture as the app opens
                val bob = sin(u * 2 * PI.toFloat() * 3f) * m
                pose.add("upperarm_l", -1.0f * e, 0f, -0.3f * e); pose.add("forearm_l", -1.8f * e, 0f, 0f)
                pose.add("upperarm_r", -1.0f * e, 0f, 0.3f * e); pose.add("forearm_r", -1.8f * e, 0f, 0f)
                pose.add("head", 0.08f * bob * e, 0f, 0f); pose.eyes = max(pose.eyes, 1f + 0.2f * e)
            }
            Beat.LAUNCH_WATCH -> {
                pose.rootYaw = -0.3f * e
                pose.add("upperarm_r", -0.9f * e, 0f, 0.35f * e); pose.add("forearm_r", -1.1f * e, 0f, 0f)
                pose.add("head", 0.05f * e, 0.25f * e, 0f); pose.eyes = max(pose.eyes, 1f + 0.2f * e)
            }
            Beat.RETURN -> {                                        // a small wave: nothing is claimed about what happened in the other app
                val wave = sin(u * 2 * PI.toFloat() * 2.5f)
                pose.add("upperarm_l", -0.2f * e, 0f, -1.9f * e); pose.add("forearm_l", -0.5f * e + 0.35f * wave * e, 0f, 0f)
                pose.add("head", 0f, 0f, 0.07f * e)
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
