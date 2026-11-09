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
        CharacterDef("human", listOf("city", "field"), listOf("candle", "book", "sword", "none"), "city", "candle", 0.95, 5.3),
        CharacterDef("wizard", listOf("archmage", "apprentice"), listOf("staff", "book", "candle", "none"), "archmage", "staff", 0.97, 5.4),
        CharacterDef("robot", listOf("plated", "stealth"), listOf("sword", "axe", "candle", "none"), "plated", "sword", 0.95, 5.35),
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

    /** The saved prop in the right hand ('none' for empty hands); set by the renderer whenever the look changes. */
    var carried = "none"

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

/**
 * How the right arm carries each prop. Angles are the arm's pitch and roll in radians (negative pitch lifts the forearm forward), [hand] levels the
 * prop again after the forearm tilts it, [curl] is how far the fingers close around the grip (the bed of the grip itself is the prop's own
 * transform in `world.glb`, baked per prop by `tools/assets/build_world.mjs`). One row per prop: no prop shares another's alignment.
 */
object PropGrips {
    class Grip(val upper: Float, val roll: Float, val fore: Float, val hand: Float, val curl: Float)
    // A fist closes around a rod that runs along the knuckle row (hand-local z), so the arm is bent until that axis points up: the three pitches of
    // each row add up to about -90 degrees (-1.57 rad). The grip itself (which point of the prop the fist closes on) is baked into world.glb.
    private val table = mapOf(
        "candle" to Grip(-0.45f, 0.06f, -0.95f, -0.20f, 0.85f),   // held up in front of the belly, flame upright
        "book" to Grip(-0.40f, 0.08f, -1.00f, -0.20f, 0.55f),     // carried by its spine at the front, cover facing out
        "sword" to Grip(-0.50f, 0.10f, -1.00f, -0.10f, 1f),       // at the ready, blade up
        "axe" to Grip(-0.50f, 0.10f, -1.00f, -0.10f, 1f),
        "staff" to Grip(-0.30f, 0.10f, -1.10f, -0.20f, 1f),       // planted beside the foot, gripped at chest height
    )
    /** The umbrella is held by the weather stance, not chosen in the Studio; its arm pose lives here with the other grips (canopy leaning slightly back, over the head). */
    val UMBRELLA = Grip(-0.60f, -0.06f, -1.20f, -0.05f, 1f)
    fun of(prop: String): Grip? = table[prop]
}

/** Joint rotation offsets, in radians as (pitch about x, yaw about y, roll about z), plus a few non-joint channels. */
class Pose {
    val rot = FloatArray(JOINTS.size * 3)
    var hipsX = 0f; var hipsY = 0f; var hipsZ = 0f
    var rootYaw = 0f
    /** Used for lying down to sleep: roll about the depth axis, a smaller scale, and a shift so the body rests low in the frame. */
    var rootRoll = 0f; var rootPitch = 0f; var rootScale = 1f; var rootX = 0f; var rootY = 0f
    /** 0 = eyes closed, 1 = open, above 1 = widened for a moment. */
    var eyes = 1f
    /** Finger curl of each hand, 0 = relaxed open, 1 = closed around a grip. Applied to the finger joints by the renderer. */
    var curlR = 0f; var curlL = 0f
    /** How much of the saved prop, the umbrella and the bed is "present": the renderer scales each to this, so they grow and shrink with the pose instead of popping. */
    var propScale = 1f; var umbrellaScale = 0f; var bedScale = 0f
    /** The prop the right hand is carrying right now (or putting away / taking out), so the renderer never shows a stale one. */
    var heldProp = "none"
    /** 0 standing .. 1 lying down; the camera widens with it so the whole bed fits. */
    var sleepFrame = 0f
    fun clear() { rot.fill(0f); hipsX = 0f; hipsY = 0f; hipsZ = 0f; rootYaw = 0f; rootRoll = 0f; rootPitch = 0f; rootScale = 1f; rootX = 0f; rootY = 0f; eyes = 1f
        curlR = 0f; curlL = 0f; propScale = 1f; umbrellaScale = 0f; bedScale = 0f; sleepFrame = 0f; heldProp = "none" }
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
 * Turns time, reactions and the gaze into a pose. Layers, from the bottom: idle (breath, weight shift, head drift, blinks), the held scene state
 * (carrying the saved prop, the weather stance with the umbrella, or lying in bed) and short beats on top. Every layer has an eased weight, so a
 * state change is a blend, never a snap, and the prop, umbrella and bed scale with those weights (see [Pose.propScale]).
 *
 * Which arm does what: the right hand carries the prop and the umbrella; one-handed gestures move to the left (free) arm while the right one is
 * busy and are mirrored smoothly as the carry weight changes. [motion] is 0 for reduced motion (held poses only), up to 1 for full motion.
 */
class CharacterAnimator(private val reactor: CharacterReactor) {
    private var gx = 0f; private var gy = 0f
    private var lastSec = -1.0
    private var wait = 0f                                            // 0..1, eased toward the reactor's waiting flag
    private var sleep = 0f                                           // 0..1, eased toward sleeping
    private var hold = 0f                                            // 0..1, eased toward "a prop is in the right hand"
    private val stances = FloatArray(Stance.values().size)           // blend per weather stance
    private var nextBlinkSec = 2.5; private var blinkStart = -10.0
    private var blinkSeed = 7
    val pose = Pose()

    private fun ease(cur: Float, target: Float, dt: Double, rate: Double, snap: Boolean) = if (snap) target else cur + (target - cur) * (1 - exp(-dt * rate).toFloat())

    // weights derived each frame
    private var umb = 0f      // umbrella weight: the strongest weather stance
    private var rBusy = 0f    // how much the right arm is owned by a prop or the umbrella; one-handed gestures move to the left arm by this much
    private var lBusy = 0f    // how much the left arm is owned by the weather pose; gestures on it are dropped by this much
    private var shownProp = "none"   // the prop the right hand carries now; changes only through an empty hand, so a swap is put-away then take-out

    fun update(nowMs: Long, motion: Float): Pose {
        val t = nowMs / 1000.0
        val dt = if (lastSec < 0) 0.016 else (t - lastSec).coerceIn(0.0, 0.1)
        lastSec = t
        pose.clear()
        val snap = motion <= 0f
        sleep = ease(sleep, if (reactor.sleeping) 1f else 0f, dt, 0.9, snap)
        for (s in Stance.values()) stances[s.ordinal] = ease(stances[s.ordinal], if (reactor.stance == s && s != Stance.SLEEP) 1f else 0f, dt, 2.6, snap)
        umb = 0f; for (s in Stance.values()) if (s != Stance.NONE && s != Stance.SLEEP) umb = max(umb, stances[s.ordinal])
        val awake = 1f - smooth(sleep / 0.3f)                      // lying down takes the prop and the arm pose away first
        if (shownProp != reactor.carried && (hold <= 0.03f || snap)) shownProp = reactor.carried     // a different prop is swapped in only once the hand is empty
        val grip = PropGrips.of(shownProp)
        hold = ease(hold, if (grip != null && shownProp == reactor.carried) 1f else 0f, dt, 3.0, snap)
        pose.heldProp = shownProp
        val carry = hold * (1f - umb) * awake                      // the saved prop is carried only when nothing stronger holds the hand
        rBusy = max(carry, umb) * awake; lBusy = umb * awake
        pose.propScale = if (grip != null) carry else 0f
        pose.umbrellaScale = umb * awake
        pose.curlR = max(carry * (grip?.curl ?: 0f), umb * awake); pose.curlL = 0.16f
        sleepPhases()

        if (snap) {
            gx = 0f; gy = 0f
            heldPose(grip, carry, t, 0f)
            return pose
        }
        val k = 1 - exp(-dt * 7.0).toFloat()                        // gaze eases toward its target, frame-rate independent
        wait = ease(wait, if (reactor.waiting) 1f else 0f, dt, 3.0, false)
        val tx = reactor.gazeX * (1 - wait) + 0.55f * sin(t * 0.55).toFloat() * wait     // while waiting the eyes wander instead of following a target
        val ty = reactor.gazeY * (1 - wait) + 0.12f * sin(t * 0.31 + 1.0).toFloat() * wait
        gx += (tx - gx) * k; gy += (ty - gy) * k

        // idle: breathing, weight shift, small head drift
        val breath = sin(t * 2 * PI * 0.26).toFloat()
        val sway = sin(t * 2 * PI * 0.07).toFloat()
        val up = awake                                              // idle sway fades out as the body lies down
        pose.hipsY = 0.003f * breath * motion * up
        pose.hipsX = 0.012f * sway * motion * up
        pose.add("spine", -0.014f * breath * motion, 0f, -0.03f * sway * motion * up)
        pose.add("chest", -0.02f * breath * motion, 0f, 0.02f * sway * motion * up)
        pose.add("head", 0.02f * sin(t * 2 * PI * 0.11).toFloat() * motion * up, 0.05f * sin(t * 2 * PI * 0.083).toFloat() * motion * up, 0f)
        pose.add("thigh_l", 0f, 0f, -0.03f * sway * motion * up); pose.add("thigh_r", 0f, 0f, 0.03f * sway * motion * up)
        // attention: the head and upper body turn a little toward whatever has focus
        val look = 0.55f * motion * up
        pose.add("head", -gy * 0.30f * look, gx * 0.65f * look, 0f)
        pose.add("neck", -gy * 0.10f * look, gx * 0.22f * look, 0f)
        pose.add("chest", 0f, gx * 0.16f * look, 0f)

        // blink
        if (t >= nextBlinkSec) { blinkStart = t; blinkSeed = (blinkSeed * 1103515245 + 12345) and 0x7fffffff; nextBlinkSec = t + 2.6 + (blinkSeed % 3000) / 1000.0 }
        val bu = ((t - blinkStart) / 0.16).toFloat()
        if (bu in 0f..1f) pose.eyes = 1f - 0.92f * sin(bu * PI.toFloat())

        heldPose(grip, carry, t, motion)
        if (wait > 0.01f && sleep < 0.01f) waitingPose(t, wait * motion)
        reactor.active(nowMs)?.let { a -> if (sleep < 0.05f) beat(a.beat, a.progress, motion) }
        return pose
    }

    /** The held layers: carrying the prop, the weather stance, and lying down. */
    private fun heldPose(grip: PropGrips.Grip?, carry: Float, t: Double, motion: Float) {
        if (grip != null && carry > 0f) {
            pose.add("upperarm_r", grip.upper * carry, 0f, grip.roll * carry); pose.add("forearm_r", grip.fore * carry, 0f, 0f); pose.add("hand_r", grip.hand * carry, 0f, 0f)
        }
        for (s in Stance.values()) if (s != Stance.NONE && s != Stance.SLEEP && stances[s.ordinal] > 0.001f) stancePose(s, t, stances[s.ordinal] * (1f - smooth(sleep / 0.3f)), motion)
        if (sleep > 0.001f) sleepPose(t, motion)
    }

    // ---- lying down -----------------------------------------------------------------------------------------------------------------
    private var bedRise = 0f; private var lie = 0f
    /** Phase 1 (first 30%): the bed grows under the standing character, who rises with its mattress. Phase 2: it lies down on its side. */
    private fun sleepPhases() {
        bedRise = smooth(sleep / 0.3f); lie = smooth((sleep - 0.3f) / 0.7f)
        pose.bedScale = bedRise; pose.sleepFrame = sleep
        pose.rootY = BED_TOP * bedRise + LIE_LIFT * lie
        pose.rootPitch = (-PI / 2).toFloat() * lie; pose.rootYaw = (PI / 2).toFloat() * lie; pose.rootX = LIE_X * lie       // on the back, face up, head toward the pillow
        pose.eyes = 1f - 0.97f * smooth((lie - 0.6f) / 0.4f)
    }

    /** On the back, face up, seen from the side: head on the pillow, one knee drawn up, both arms resting along the body. */
    private fun sleepPose(t: Double, motion: Float) {
        val w = lie; if (w <= 0f) return
        val br = sin(t * 2 * PI * 0.14).toFloat() * motion          // with reduced motion the figure still lies down, it just does not breathe
        pose.hipsY += 0.004f * br * w
        pose.add("chest", -0.03f * br * w, 0f, 0f); pose.add("head", 0.20f * w, 0f, 0f); pose.add("neck", 0.08f * w, 0f, 0f)
        pose.add("upperarm_l", 0.02f * w, 0f, 0.12f * w); pose.add("forearm_l", -0.22f * w, 0f, 0f)       // both arms rest along the body, elbows slightly bent
        pose.add("upperarm_r", 0.02f * w, 0f, 0.12f * w); pose.add("forearm_r", -0.28f * w, 0f, 0f)
        pose.add("thigh_l", -0.75f * w, 0f, 0f); pose.add("shin_l", 1.25f * w, 0f, 0f)
        pose.add("thigh_r", -0.04f * w, 0f, 0f); pose.add("shin_r", 0.10f * w, 0f, 0f)
        pose.curlL = 0.30f * w + pose.curlL * (1 - w); pose.curlR = 0.30f * w + pose.curlR * (1 - w)
    }

    /** Holding an umbrella with the right arm, plus what the weather does to the rest of the body. */
    private fun stancePose(s: Stance, t: Double, w: Float, motion: Float) {
        val u = PropGrips.UMBRELLA
        pose.add("upperarm_r", u.upper * w, 0f, u.roll * w); pose.add("forearm_r", u.fore * w, 0f, 0f); pose.add("hand_r", u.hand * w, 0f, 0f)
        when (s) {
            Stance.RAIN -> { pose.add("head", -0.1f * w, 0f, 0f); pose.add("upperarm_l", -0.2f * w, 0f, -0.22f * w); pose.add("forearm_l", -0.55f * w, 0f, 0f) }
            Stance.SNOW -> { pose.add("head", -0.18f * w, 0f, 0.05f * w); pose.add("upperarm_l", -0.35f * w, 0f, -0.28f * w); pose.add("forearm_l", -0.5f * w, 0f, 0f) }
            Stance.STORM -> {
                val gust = sin(t * 9).toFloat() * motion
                pose.add("spine", 0.16f * w, 0f, 0f); pose.add("chest", 0.08f * w, 0f, 0f); pose.hipsZ -= 0.03f * w
                pose.add("upperarm_r", 0f, 0f, 0.05f * gust * w); pose.add("upperarm_l", -0.5f * w, 0f, -0.2f * w); pose.add("forearm_l", -0.9f * w, 0f, 0f)
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

    /**
     * One arm of a gesture. [side] is 'l' or 'r'; a positive [roll] always means "away from the body". An arm that the held layers own (the right one while it
     * carries a prop or the umbrella, the left one while the weather pose uses it) ignores the gesture by that much: gestures never fight a held pose.
     */
    private fun arm(side: Char, up: Float, roll: Float, fore: Float, hand: Float, w0: Float) {
        val w = w0 * (1f - (if (side == 'r') rBusy else lBusy)); if (w <= 0f) return
        val sgn = if (side == 'r') 1f else -1f
        pose.add("upperarm_$side", up * w, 0f, roll * sgn * w); pose.add("forearm_$side", fore * w, 0f, 0f); pose.add("hand_$side", hand * w, 0f, 0f)
    }
    /** A one-handed gesture: the right hand when it is empty, the left (if the weather is not using it) while the right is busy; with both busy it is dropped. */
    private fun oneHand(up: Float, roll: Float, fore: Float, hand: Float, w: Float) { arm('r', up, roll, fore, hand, w); arm('l', up, roll, fore, hand, w * rBusy) }

    /** Relaxed "waiting for a selection": weight on one leg, a free hand at the chin, the head tilted, slow breathing. */
    private fun waitingPose(t: Double, w: Float) {
        val sway = sin(t * 2 * PI * 0.05).toFloat()
        pose.hipsX += 0.035f * w; pose.hipsY -= 0.006f * w
        pose.add("spine", 0.02f * w, 0f, -0.05f * w); pose.add("chest", 0f, -0.06f * w, 0.03f * w)
        pose.add("head", 0.04f * w, 0f, 0.1f * w + 0.015f * sway * w)
        oneHand(-0.55f, 0.2f, -1.35f, -0.2f, w)
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
            Beat.NAVIGATE -> {                                      // a small nod toward the new target
                pose.add("head", 0.05f * e, gx * 0.10f * e, 0f)
                pose.add("spine", 0.02f * e, gx * 0.06f * e, 0f)
            }
            Beat.CATEGORY -> {                                      // chest lifts, shoulders settle: "new place"
                pose.add("chest", -0.06f * e, 0f, 0f); pose.add("clavicle_l", 0f, 0f, 0.06f * e); pose.add("clavicle_r", 0f, 0f, -0.06f * e)
                pose.add("head", 0.04f * e, 0f, 0f)
            }
            Beat.BACK -> {                                          // a step back and a small open-hand dismissal with the free hand
                pose.hipsZ = -0.035f * e
                pose.add("spine", -0.04f * e, 0f, 0f); pose.add("head", 0f, -0.18f * sin(u * 2 * PI.toFloat()) * m, 0f)
                arm('l', -0.25f, 0.2f, -0.45f, 0f, e)
            }
            Beat.CONFIRM -> {                                       // a nod and the free hand lifts a little
                pose.add("head", 0.20f * sin(u * PI.toFloat() * 1.6f) * m, 0f, 0f)
                oneHand(-0.45f, 0.18f, -0.75f, -0.1f, e)
                pose.hipsY += 0.012f * e
                pose.eyes = max(pose.eyes, 1f + 0.35f * e)
            }
            Beat.LONG_PRESS -> {                                    // both hands lift: "options" (a busy hand stays where it is)
                arm('l', -0.55f, 0.15f, -0.8f, 0f, e); arm('r', -0.55f, 0.15f, -0.8f, 0f, e)
                pose.add("head", 0.05f * e, 0f, 0f)
            }
            Beat.PREVIEW -> {                                       // quarter turn to show the outfit off
                pose.rootYaw = 0.55f * e
                pose.add("head", 0f, -0.30f * e, 0f)
                arm('l', -0.2f, 0.25f, 0f, 0f, e)
            }
            Beat.CANCEL -> {                                        // shrug
                pose.add("clavicle_l", 0f, 0f, 0.20f * e); pose.add("clavicle_r", 0f, 0f, -0.20f * e)
                pose.add("head", 0f, 0f, 0.10f * e)
            }
            Beat.APPLY -> {                                         // a small bow of thanks
                pose.add("spine", 0.30f * shape(u) * m, 0f, 0f); pose.add("head", 0.22f * shape(u) * m, 0f, 0f)
            }
            Beat.LISTEN -> {                                        // hands to the ears, a small nod to a beat that only the character hears
                val bob = sin(u * 2 * PI.toFloat() * 3f) * m
                arm('l', -0.9f, 0.25f, -1.7f, 0f, e); arm('r', -0.9f, 0.25f, -1.7f, 0f, e)
                pose.add("head", 0.05f * bob * e, 0f, 0.03f * bob * e); pose.hipsY += 0.006f * bob * e
            }
            Beat.WATCH -> {                                         // turns toward the screen and presents a remote with the free hand
                pose.rootYaw = -0.25f * e
                oneHand(-0.7f, 0.3f, -0.95f, -0.4f, e)
                pose.add("head", 0.03f * e, 0.2f * e, 0f)
            }
            Beat.LAUNCH_LISTEN -> {                                 // a bigger version of the same gesture as the app opens
                val bob = sin(u * 2 * PI.toFloat() * 3f) * m
                arm('l', -1.0f, 0.3f, -1.8f, 0f, e); arm('r', -1.0f, 0.3f, -1.8f, 0f, e)
                pose.add("head", 0.08f * bob * e, 0f, 0f); pose.eyes = max(pose.eyes, 1f + 0.2f * e)
            }
            Beat.LAUNCH_WATCH -> {
                pose.rootYaw = -0.3f * e
                oneHand(-0.9f, 0.35f, -1.1f, 0f, e)
                pose.add("head", 0.05f * e, 0.25f * e, 0f); pose.eyes = max(pose.eyes, 1f + 0.2f * e)
            }
            Beat.RETURN -> {                                        // a glad nod and an open free hand; nothing is claimed about what happened in the other app
                pose.add("head", 0.12f * sin(u * PI.toFloat()) * m, 0f, 0.05f * e)
                arm('l', -0.35f, 0.25f, -0.7f, 0f, e)
            }
            Beat.EQUIP -> {                                         // a full turn on the spot while the new look is swapped in
                pose.rootYaw = (2 * PI.toFloat()) * smooth(u) * m
                arm('l', -0.1f, 0.3f, 0f, 0f, e); arm('r', -0.1f, 0.3f, 0f, 0f, e)
                pose.hipsY += 0.015f * sin(u * PI.toFloat()) * m
            }
        }
    }

    companion object {
        /** Mattress top of `world.glb`'s bed at scale 1, and where a side-lying body rests relative to it (set from the model; see docs/character.md). */
        const val BED_TOP = 0.52f; const val LIE_LIFT = 0.17f; const val LIE_X = 0.84f
    }
}

/** Where on screen focus is, as the character wants it: centre-relative, x right, y up, both in -1..1. */
fun gazeFromScreen(cx: Float, cy: Float, width: Int, height: Int, originX: Float, originY: Float): Pair<Float, Float> {
    if (width <= 0 || height <= 0) return 0f to 0f
    val dx = (cx - originX) / (width / 2f)
    val dy = (originY - cy) / (height / 2f)
    return dx.coerceIn(-1f, 1f) to dy.coerceIn(-1f, 1f)
}
