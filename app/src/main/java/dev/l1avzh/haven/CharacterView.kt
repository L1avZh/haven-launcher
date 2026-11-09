package dev.l1avzh.haven

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View as FView
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hosts the 3D character. All rendering-engine code lives here; the rest of the launcher only sends [CharInput]s and
 * a look (outfit and prop id). The view is transparent, takes no focus, draws at most 30 frames a second, and renders
 * only while it is visible, started and the character has something to show.
 *
 * If the engine or the model cannot be created the view hides itself and the launcher carries on without it.
 */
class CharacterView(ctx: Context) : TextureView(ctx), Choreographer.FrameCallback {
    private val reactor = CharacterReactor()
    private val animator = CharacterAnimator(reactor)

    /** 1 = full motion, 0 = reduced motion (still pose); set from the launcher's animation and performance settings. */
    var motion = 1f
        set(v) { field = v; reactor.enabled = v > 0f; dirty = true }

    /** Fired once an outfit or prop change has been swapped in (halfway through an equip turn). */
    var failed = false; private set

    /** Minimum time between frames: about 30 fps normally, about 20 fps in performance mode. */
    var frameGapMs = 30L

    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: FView? = null
    private var camera: Camera? = null
    private var cameraEntity = 0
    private var swapChain: SwapChain? = null
    private val ui = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var materials: UbershaderProvider? = null
    private var assetLoader: AssetLoader? = null
    private var resources: ResourceLoader? = null
    private val lights = ArrayList<Int>()
    private var ibl: IndirectLight? = null

    private class Joint(val entity: Int, val inst: Int, val bind: FloatArray)
    /** One loaded character: its skeleton, eyes and the optional pieces that can be shown or hidden. */
    private class Model(val def: CharacterDef, val asset: FilamentAsset, val joints: Map<String, Joint>, val eyes: List<Joint>,
                        val outfits: Map<String, Int>, val props: Map<String, Int>, val star: Int, val always: IntArray,
                        val umbrella: Int, val blanket: Int, val pillow: Int)
    private val models = HashMap<String, Model>()
    private var active: Model? = null

    private var look = LookState("human", "city", "lantern", false)
    private var pendingLook: LookState? = null
    private var scenery = SceneLook(Stance.NONE, null, false)
    private var started = false
    private var dirty = true
    private var lastFrameMs = 0L
    private val scratch = FloatArray(16)

    init {
        isOpaque = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = false; isFocusableInTouchMode = false
    }

    // ------------------------------------------------------------------ public control

    fun input(i: CharInput) { reactor.onInput(i, SystemClock.uptimeMillis()); dirty = true; kick() }
    fun longPress() { reactor.onLongPress(SystemClock.uptimeMillis()); dirty = true; kick() }

    /** Shows this look at once (no turn), e.g. at startup. */
    fun setLook(l: LookState) { look = l; pendingLook = null; applyLook(); dirty = true; kick() }

    /** Previews a look with the equip turn; the swap happens while the character is turned away. */
    fun equip(l: LookState) {
        if (l == look && pendingLook == null) return
        if (l == look) { pendingLook = null; return }
        if (motion <= 0f) { setLook(l); return }
        pendingLook = l
        reactor.onInput(CharInput.Studio(StudioAction.EQUIP), SystemClock.uptimeMillis()); dirty = true; kick()
    }

    /** The look currently on screen, or the one it is about to swap to. */
    fun currentLook() = pendingLook ?: look

    /** Standing relaxed and looking around because nothing is selected. */
    fun setWaiting(on: Boolean) { if (reactor.waiting != on) { reactor.waiting = on; dirty = true; kick() } }

    /** Weather and day/night staging: a held pose, a temporary prop (the umbrella) and the sleeping setup. The saved look is untouched. */
    fun setScene(s: SceneLook) {
        if (s.stance == scenery.stance && s.propOverride == scenery.propOverride && s.sleeping == scenery.sleeping) return
        scenery = s; reactor.stance = s.stance; applyLook(); frameCamera(); dirty = true; kick()
    }

    fun start() { if (started || failed) return; started = true; lastFrameMs = 0; dirty = true; kick() }
    fun stop() { started = false; Choreographer.getInstance().removeFrameCallback(this) }

    fun destroy() {
        stop()
        try { ui.detach() } catch (_: Exception) {}
        destroyEngine()
    }

    // ------------------------------------------------------------------ lifecycle of the surface

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (failed) { visibility = GONE; return }
        try { ensureEngine(); ui.attachTo(this) } catch (t: Throwable) { fail(t) }
        kick()
    }

    override fun onDetachedFromWindow() {
        stop()
        try { ui.detach() } catch (_: Exception) {}
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(v: android.view.View, vis: Int) {
        super.onVisibilityChanged(v, vis)
        if (vis == VISIBLE) kick() else Choreographer.getInstance().removeFrameCallback(this)
    }
    override fun onWindowVisibilityChanged(vis: Int) {
        super.onWindowVisibilityChanged(vis)
        if (vis == VISIBLE) kick() else Choreographer.getInstance().removeFrameCallback(this)
    }

    private fun fail(t: Throwable) {
        Log.w("Haven", "character disabled: $t")
        failed = true; stop(); destroyEngine(); visibility = GONE
    }

    // ------------------------------------------------------------------ engine and scene

    private fun ensureEngine() {
        if (engine != null) return
        Filament.init(); Gltfio.init()
        val e = Engine.create(); engine = e
        renderer = e.createRenderer().also {
            it.clearOptions = Renderer.ClearOptions().apply { clear = true; clearColor = floatArrayOf(0f, 0f, 0f, 0f) }
        }
        scene = e.createScene()
        cameraEntity = EntityManager.get().create(); camera = e.createCamera(cameraEntity).also { it.setExposure(16f, 1f / 125f, 100f) }
        view = e.createView().also {
            it.scene = scene; it.camera = camera
            it.blendMode = FView.BlendMode.TRANSLUCENT
            it.renderQuality = it.renderQuality.apply { hdrColorBuffer = FView.QualityLevel.HIGH }
            it.antiAliasing = FView.AntiAliasing.FXAA
            it.dithering = FView.Dithering.NONE
        }
        buildLights(e)
        loadModels(e)
        ui.isOpaque = false
        ui.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                val eng = engine ?: return
                swapChain?.let { eng.destroySwapChain(it) }
                swapChain = eng.createSwapChain(surface, ui.swapChainFlags)
                dirty = true; kick()
            }
            override fun onDetachedFromSurface() {
                val eng = engine ?: return
                swapChain?.let { eng.destroySwapChain(it); eng.flushAndWait() }
                swapChain = null
            }
            override fun onResized(width: Int, height: Int) {
                view?.viewport = Viewport(0, 0, width, height)
                aspect = width.toDouble() / height
                frameCamera(); dirty = true
            }
        }
    }

    private fun buildLights(e: Engine) {
        val em = EntityManager.get()
        fun sun(color: FloatArray, lux: Float, dir: FloatArray) {
            val l = em.create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL).color(color[0], color[1], color[2]).intensity(lux)
                .direction(dir[0], dir[1], dir[2]).castShadows(false).build(e, l)
            scene!!.addEntity(l); lights += l
        }
        sun(floatArrayOf(1.0f, 0.93f, 0.84f), 60000f, floatArrayOf(-0.55f, -0.55f, -0.65f))     // key, high and to the character's left-front
        sun(floatArrayOf(0.40f, 0.75f, 1.0f), 60000f, floatArrayOf(0.85f, -0.10f, 0.55f))       // cool rim from behind-right
        // soft sky/ground ambient: band 0 plus a gentle up-down gradient
        val sh = floatArrayOf(0.62f, 0.64f, 0.7f, 0.3f, 0.32f, 0.4f, 0f, 0f, 0f, 0f, 0f, 0f)
        ibl = IndirectLight.Builder().irradiance(2, sh).intensity(15000f).build(e)
        scene!!.indirectLight = ibl
    }

    private var aspect = 0.73
    private fun frameCamera() {
        val d = (active?.def ?: Look.def(look.character))
        // An umbrella needs headroom; a sleeper is lower and smaller, so it is framed lower. The default framing is unchanged.
        val umbrella = scenery.propOverride == "umbrella"
        var dist = d.cameraDistance * (if (umbrella) 1.32 else 1.0); var ty = d.cameraTargetY + (if (umbrella) 0.2 else 0.0)
        if (scenery.sleeping) {
            // A lying figure is wide and low: come in until it fills the view's width, but never further out than the standing frame.
            val fit = 1.75 / (2 * Math.tan(Math.toRadians(10.5)) * aspect)
            dist = fit.coerceIn(2.4, d.cameraDistance); ty = 0.3
        }
        camera?.setProjection(21.0, aspect, 0.1, 30.0, Camera.Fov.VERTICAL)
        camera?.lookAt(0.0, ty + 0.03, dist, 0.0, ty, 0.0, 0.0, 1.0, 0.0)
    }

    /** Loads every character once; only the selected one is added to the scene. A model that fails to load is simply skipped. */
    private fun loadModels(e: Engine) {
        val provider = UbershaderProvider(e); materials = provider
        val loader = AssetLoader(e, provider, EntityManager.get()); assetLoader = loader
        val rl = ResourceLoader(e); resources = rl
        val tm = e.transformManager
        for (def in Look.CHARACTERS) {
            try {
                val bytes = context.assets.open(def.asset).use { it.readBytes() }
                val buf = ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
                val a = loader.createAsset(buf) ?: continue
                rl.loadResources(a); a.releaseSourceData()
                fun joint(name: String): Joint? { val ent = a.getFirstEntityByName(name); if (ent == 0) return null
                    val inst = tm.getInstance(ent); val m = FloatArray(16); tm.getTransform(inst, m); return Joint(ent, inst, m) }
                val joints = (Pose.JOINTS + "root").mapNotNull { n -> joint(n)?.let { n to it } }.toMap()
                val outfits = def.outfits.associateWith { a.getFirstEntityByName("outfit_$it") }
                val props = def.props.associateWith { a.getFirstEntityByName("prop_$it") }
                val star = a.getFirstEntityByName("acc_star")
                val umbrella = a.getFirstEntityByName("prop_umbrella"); val blanket = a.getFirstEntityByName("sleep_blanket"); val pillow = a.getFirstEntityByName("sleep_pillow")
                val optional = (outfits.values + props.values + star + umbrella + blanket + pillow).toSet()
                models[def.id] = Model(def, a, joints, listOfNotNull(joint("eye_l"), joint("eye_r")), outfits, props, star, a.entities.filter { it !in optional }.toIntArray(), umbrella, blanket, pillow)
            } catch (t: Throwable) { Log.w("Haven", "character ${def.id} not loaded: $t") }
        }
        if (models.isEmpty()) throw IllegalStateException("no character model could be loaded")
        applyLook()
    }

    /** Shows the selected character with its outfit, prop and (if worn) pendant; everything else stays out of the scene. */
    private fun applyLook() {
        val s = scene ?: return
        val want = models[look.character] ?: models.values.first()
        for ((id, m) in models) {
            if (m !== want) { s.removeEntities(m.always); (m.outfits.values + m.props.values + m.star + m.umbrella + m.blanket + m.pillow).forEach { if (it != 0) s.removeEntity(it) }; continue }
            s.addEntities(m.always)
            for ((o, ent) in m.outfits) if (ent != 0) { if (o == look.outfit) s.addEntity(ent) else s.removeEntity(ent) }
            val shown = if (scenery.sleeping) null else scenery.propOverride ?: look.prop
            for ((p, ent) in m.props) if (ent != 0) { if (p == shown) s.addEntity(ent) else s.removeEntity(ent) }
            fun toggle(ent: Int, on: Boolean) { if (ent != 0) { if (on) s.addEntity(ent) else s.removeEntity(ent) } }
            toggle(m.umbrella, shown == "umbrella"); toggle(m.blanket, scenery.sleeping); toggle(m.pillow, scenery.sleeping)
            if (m.star != 0) { if (look.star) s.addEntity(m.star) else s.removeEntity(m.star) }
        }
        if (active !== want) { active = want; frameCamera() }
    }

    private fun destroyEngine() {
        val e = engine ?: return
        try {
            swapChain?.let { e.destroySwapChain(it) }; swapChain = null
            models.values.forEach { assetLoader?.destroyAsset(it.asset) }; models.clear(); active = null
            resources?.destroy(); resources = null
            assetLoader?.destroy(); assetLoader = null
            materials?.destroyMaterials(); materials?.destroy(); materials = null
            lights.forEach { l -> scene?.removeEntity(l); e.lightManager.destroy(l); EntityManager.get().destroy(l) }; lights.clear()
            ibl?.let { e.destroyIndirectLight(it) }; ibl = null
            view?.let { e.destroyView(it) }; view = null
            scene?.let { e.destroyScene(it) }; scene = null
            camera?.let { e.destroyCameraComponent(cameraEntity); EntityManager.get().destroy(cameraEntity) }; camera = null
            renderer?.let { e.destroyRenderer(it) }; renderer = null
            e.destroy()
        } catch (t: Throwable) { Log.w("Haven", "character teardown: $t") }
        engine = null
    }

    // ------------------------------------------------------------------ frame loop

    private fun kick() {
        if (!started || failed || !isAttachedToWindow || visibility != VISIBLE || windowVisibility != VISIBLE) return
        Choreographer.getInstance().removeFrameCallback(this)
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!started || failed) return
        val now = SystemClock.uptimeMillis()
        // 30 fps at most
        if (lastFrameMs != 0L && now - lastFrameMs < frameGapMs) { Choreographer.getInstance().postFrameCallback(this); return }
        lastFrameMs = now
        val moving = motion > 0f || reactor.active(now) != null
        if (moving || dirty) {
            try { renderOnce(now, frameTimeNanos) } catch (t: Throwable) { fail(t); return }
            dirty = false
        }
        // keep going while anything can change; a still pose needs no further frames
        if (motion > 0f || reactor.active(now) != null || dirty) Choreographer.getInstance().postFrameCallback(this)
    }

    private fun renderOnce(nowMs: Long, frameTimeNanos: Long) {
        val e = engine ?: return; val r = renderer ?: return; val v = view ?: return
        val sc = swapChain ?: return
        if (!ui.isReadyToRender) return
        pendingLook?.let { p ->
            val act = reactor.active(nowMs)
            if (act == null || act.beat != Beat.EQUIP || act.progress > 0.45f) { look = p; pendingLook = null; applyLook() }
        }
        val pose = animator.update(nowMs, motion)
        poseJoints(e, pose)
        active?.asset?.instance?.animator?.updateBoneMatrices()
        if (r.beginFrame(sc, frameTimeNanos)) { r.render(v); r.endFrame() }
    }

    // ------------------------------------------------------------------ pose to transforms

    private fun poseJoints(e: Engine, p: Pose) {
        val m = active ?: return
        val tm = e.transformManager
        for (name in Pose.JOINTS) {
            val j = m.joints[name] ?: continue
            val i = Pose.INDEX[name]!!
            compose(j.bind, p.rot[i * 3], p.rot[i * 3 + 1], p.rot[i * 3 + 2], scratch)
            if (name == "hips") { scratch[12] += p.hipsX; scratch[13] += p.hipsY; scratch[14] += p.hipsZ }
            tm.setTransform(j.inst, scratch)
        }
        m.joints["root"]?.let { j ->
            compose(j.bind, 0f, p.rootYaw, p.rootRoll, scratch)
            if (p.rootScale != 1f) for (k in 0 until 12) scratch[k] *= p.rootScale
            scratch[12] += p.rootX; scratch[13] += p.rootY
            tm.setTransform(j.inst, scratch)
        }
        for (eye in m.eyes) blink(tm, eye, p.eyes)
    }

    /** Eyes sit at their own position (the node's bind translation), so blinking is just a scale about that point. */
    private fun blink(tm: com.google.android.filament.TransformManager, eye: Joint, open: Float) {
        val sy = open.coerceAtLeast(0.05f); val sx = if (open > 1f) 1f + (open - 1f) * 0.5f else 1f
        val m = scratch
        m.fill(0f); m[0] = sx; m[5] = sy; m[10] = 1f; m[15] = 1f
        m[12] = eye.bind[12]; m[13] = eye.bind[13]; m[14] = eye.bind[14]
        tm.setTransform(eye.inst, m)
    }

    /** out = bind * R, where R = Rz(roll) * Ry(yaw) * Rx(pitch), rotating the joint about its own origin. */
    private fun compose(bind: FloatArray, pitch: Float, yaw: Float, roll: Float, out: FloatArray) {
        val cx = cos(pitch); val sx = sin(pitch); val cy = cos(yaw); val sy = sin(yaw); val cz = cos(roll); val sz = sin(roll)
        // row-major 3x3 of R
        val r00 = cz * cy; val r01 = cz * sy * sx - sz * cx; val r02 = cz * sy * cx + sz * sx
        val r10 = sz * cy; val r11 = sz * sy * sx + cz * cx; val r12 = sz * sy * cx - cz * sx
        val r20 = -sy;     val r21 = cy * sx;                val r22 = cy * cx
        // bind is column-major; take its 3x3 (b[col*4+row]) and multiply B3 * R3
        for (c in 0 until 3) {
            val rc0 = when (c) { 0 -> r00; 1 -> r01; else -> r02 }
            val rc1 = when (c) { 0 -> r10; 1 -> r11; else -> r12 }
            val rc2 = when (c) { 0 -> r20; 1 -> r21; else -> r22 }
            for (row in 0 until 3) out[c * 4 + row] = bind[0 * 4 + row] * rc0 + bind[1 * 4 + row] * rc1 + bind[2 * 4 + row] * rc2
            out[c * 4 + 3] = 0f
        }
        out[12] = bind[12]; out[13] = bind[13]; out[14] = bind[14]; out[15] = 1f
    }
}
