#!/usr/bin/env python3
"""Generates the Haven "Warden" character as a rigged, skinned glTF binary (GLB), using only the Python standard library.

Everything here is original geometry built from primitives (ellipsoids, tapered tubes, boxes, rings) and skinned to a
19-joint skeleton, so there are no third-party models, textures or licences involved.

    python3 tools/character/gen_character.py            ->  app/src/main/assets/character/warden.glb

Scene contract used by the app (node names):
  joints:  root hips spine chest neck head clavicle_l/r upperarm_l/r forearm_l/r hand_l/r thigh_l/r shin_l/r foot_l/r
  attach:  attach_hand_r / attach_hand_l (empty nodes under the hands, where hand-held props are parented)
  body:    body, eye_l, eye_r
  outfits: outfit_courier, outfit_runner, outfit_astral   (each skinned to the same skeleton)
  props:   prop_lantern, prop_tablet, prop_compass       (parented to attach_hand_r)
"""
import json, math, os, struct, sys

# ---------------------------------------------------------------- vector helpers
def sub(a, b): return (a[0]-b[0], a[1]-b[1], a[2]-b[2])
def add(a, b): return (a[0]+b[0], a[1]+b[1], a[2]+b[2])
def mul(a, s): return (a[0]*s, a[1]*s, a[2]*s)
def dot(a, b): return a[0]*b[0] + a[1]*b[1] + a[2]*b[2]
def cross(a, b): return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
def length(a): return math.sqrt(dot(a, a))
def norm(a):
    l = length(a)
    return (0.0, 1.0, 0.0) if l < 1e-9 else (a[0]/l, a[1]/l, a[2]/l)
def mirror(p): return (-p[0], p[1], p[2])
def smooth(x):
    x = max(0.0, min(1.0, x)); return x*x*(3-2*x)

# ---------------------------------------------------------------- skeleton
# name: (parent, world position in the bind pose). Rest pose: no joint rotation, a relaxed A-pose.
J = {}
ORDER = []
def joint(name, parent, pos):
    J[name] = (parent, pos); ORDER.append(name)

joint('root', None, (0.0, 0.0, 0.0))
joint('hips', 'root', (0.0, 0.95, 0.0))
joint('spine', 'hips', (0.0, 1.08, 0.0))
joint('chest', 'spine', (0.0, 1.27, 0.0))
joint('neck', 'chest', (0.0, 1.50, 0.0))
joint('head', 'neck', (0.0, 1.60, 0.0))
for side, m in (('l', 1), ('r', -1)):
    joint('clavicle_' + side, 'chest', (0.09*m, 1.46, 0.0))
    joint('upperarm_' + side, 'clavicle_' + side, (0.21*m, 1.45, 0.0))
    joint('forearm_' + side, 'upperarm_' + side, (0.43*m, 1.20, 0.02))
    joint('hand_' + side, 'forearm_' + side, (0.55*m, 0.95, 0.07))
    joint('thigh_' + side, 'hips', (0.095*m, 0.92, 0.0))
    joint('shin_' + side, 'thigh_' + side, (0.115*m, 0.50, 0.015))
    joint('foot_' + side, 'shin_' + side, (0.12*m, 0.10, 0.0))
JI = {n: i for i, n in enumerate(ORDER)}
def P(n): return J[n][1]

# ---------------------------------------------------------------- geometry
class Prim:
    """One glTF primitive: geometry sharing a material."""
    def __init__(self, material, skinned=True, double=False):
        self.material, self.skinned, self.double = material, skinned, double
        self.pos, self.nor, self.jnt, self.wgt, self.idx = [], [], [], [], []

    def _vert(self, p, n, w):
        w = [(j, x) for j, x in w if x > 1e-4][:4] or [(JI['root'], 1.0)]
        tot = sum(x for _, x in w)
        w = [(j, x / tot) for j, x in w]
        while len(w) < 4: w.append((0, 0.0))
        self.pos.append(p); self.nor.append(norm(n))
        self.jnt.append([JI[j] if isinstance(j, str) else j for j, _ in w]); self.wgt.append([x for _, x in w])
        return len(self.pos) - 1

    def _tri(self, a, b, c):
        pa, pb, pc = self.pos[a], self.pos[b], self.pos[c]
        n = cross(sub(pb, pa), sub(pc, pa))
        avg = add(add(self.nor[a], self.nor[b]), self.nor[c])
        if dot(n, avg) < 0: b, c = c, b          # keep triangles counter-clockwise as seen from outside
        self.idx += [a, b, c]
        if self.double: self.idx += [a, c, b]

    def ellipsoid(self, c, r, wf, seg=18, ring=12):
        rx, ry, rz = r
        base = len(self.pos)
        for i in range(ring + 1):
            th = math.pi * i / ring
            for j in range(seg + 1):
                ph = 2 * math.pi * j / seg
                d = (math.sin(th) * math.cos(ph), math.cos(th), math.sin(th) * math.sin(ph))
                p = (c[0] + rx*d[0], c[1] + ry*d[1], c[2] + rz*d[2])
                self._vert(p, (d[0]/rx, d[1]/ry, d[2]/rz), wf(p))
        for i in range(ring):
            for j in range(seg):
                a = base + i*(seg+1) + j; b = a + seg + 1
                self._tri(a, b, a+1); self._tri(a+1, b, b+1)

    def tube(self, a, b, ra, rb, wf, seg=14, rings=6, sx=1.0, sz=1.0, caps=False):
        ax = norm(sub(b, a))
        ref = (0, 0, 1) if abs(ax[2]) < 0.9 else (1, 0, 0)
        u = norm(cross(ax, ref)); v = cross(ax, u)
        base = len(self.pos)
        for i in range(rings + 1):
            t = i / rings; c = add(mul(a, 1-t), mul(b, t)); r = ra*(1-t) + rb*t
            for j in range(seg + 1):
                ph = 2 * math.pi * j / seg
                d = add(mul(u, math.cos(ph)), mul(v, math.sin(ph)))
                d = (d[0]*sx, d[1], d[2]*sz)
                p = add(c, mul(d, r))
                self._vert(p, (d[0]/(sx*sx), d[1], d[2]/(sz*sz)), wf(p))
        for i in range(rings):
            for j in range(seg):
                p0 = base + i*(seg+1) + j; p1 = p0 + seg + 1
                self._tri(p0, p1, p0+1); self._tri(p0+1, p1, p1+1)
        if caps:
            for end, flip in ((a, -1), (b, 1)):
                r = ra if end is a else rb
                ci = self._vert(end, mul(ax, flip), wf(end)); ring_i = []
                for j in range(seg + 1):
                    ph = 2 * math.pi * j / seg
                    d = add(mul(u, math.cos(ph)), mul(v, math.sin(ph)))
                    d = (d[0]*sx, d[1], d[2]*sz)
                    ring_i.append(self._vert(add(end, mul(d, r)), mul(ax, flip), wf(end)))
                for j in range(seg): self._tri(ci, ring_i[j], ring_i[j+1])

    def box(self, c, h, wf, round_=0.0):
        """Axis-aligned box with half extents h (flat shaded)."""
        faces = [((1,0,0), (0,1,0), (0,0,1)), ((-1,0,0), (0,1,0), (0,0,-1)), ((0,1,0), (0,0,1), (1,0,0)),
                 ((0,-1,0), (0,0,-1), (1,0,0)), ((0,0,1), (0,1,0), (-1,0,0)), ((0,0,-1), (0,1,0), (1,0,0))]
        for n, up, rt in faces:
            q = []
            for su, sr in ((-1,-1), (-1,1), (1,1), (1,-1)):
                o = add(add(mul(n, 1.0), mul(up, su)), mul(rt, sr))
                p = (c[0] + o[0]*h[0], c[1] + o[1]*h[1], c[2] + o[2]*h[2])
                q.append(self._vert(p, n, wf(p)))
            self._tri(q[0], q[1], q[2]); self._tri(q[0], q[2], q[3])

    def torus(self, c, R, r, axis, wf, seg=28, sides=10):
        ax = norm(axis); ref = (0, 1, 0) if abs(ax[1]) < 0.9 else (1, 0, 0)
        u = norm(cross(ax, ref)); v = cross(ax, u)
        base = len(self.pos)
        for i in range(seg + 1):
            a = 2 * math.pi * i / seg
            ring = add(mul(u, math.cos(a)), mul(v, math.sin(a)))
            for j in range(sides + 1):
                b = 2 * math.pi * j / sides
                n = add(mul(ring, math.cos(b)), mul(ax, math.sin(b)))
                p = add(add(c, mul(ring, R)), mul(n, r))
                self._vert(p, n, wf(p))
        for i in range(seg):
            for j in range(sides):
                a0 = base + i*(sides+1) + j; a1 = a0 + sides + 1
                self._tri(a0, a1, a0+1); self._tri(a0+1, a1, a1+1)

# ---------------------------------------------------------------- skin weights
def bone(j0, j1, blend=0.45):
    """Weights for a point on the segment from joint j0 to its child j1: the far end eases into the child joint."""
    p0, p1 = P(j0), P(j1); ax = sub(p1, p0); L2 = max(dot(ax, ax), 1e-9)
    def f(p):
        t = dot(sub(p, p0), ax) / L2
        s = smooth((t - (1 - blend)) / blend)
        return [(j0, 1 - 0.5*s), (j1, 0.5*s)] if t > 0 else [(j0, 1.0)]
    return f
def fixed(*pairs):
    return lambda p: list(pairs)
def one(j): return lambda p: [(j, 1.0)]
def between(ja, jb): return lambda p: [(ja, 0.5), (jb, 0.5)]
def torso_w(p):
    y = p[1]
    if y < 1.00: return [('hips', 1.0)]
    if y < 1.14: s = smooth((y-1.00)/0.14); return [('hips', 1-s), ('spine', s)]
    if y < 1.30: s = smooth((y-1.14)/0.16); return [('spine', 1-s), ('chest', s)]
    if y < 1.50: return [('chest', 1.0)]
    s = smooth((y-1.50)/0.10); return [('chest', 1-s), ('neck', s)]
def side_leg_w(side):
    thigh, shin = 'thigh_' + side, 'shin_' + side
    return lambda p: bone(thigh, shin)(p)
def hip_leg_blend(side):
    """Skirts and coats hang from the hips but follow the leg on their own side as they go down."""
    thigh = 'thigh_' + side
    def f(p):
        s = smooth((0.92 - p[1]) / 0.35)
        return [('hips', 1 - 0.8*s), (thigh, 0.8*s)]
    return f

# ---------------------------------------------------------------- materials
MATS = {}
def mat(name, base, metal=0.0, rough=0.6, emissive=None, double=False):
    MATS[name] = dict(name=name, pbrMetallicRoughness=dict(baseColorFactor=[*base, 1.0], metallicFactor=metal, roughnessFactor=rough),
                      doubleSided=double, **({'emissiveFactor': list(emissive)} if emissive else {}))

mat('suit', (0.09, 0.10, 0.13), 0.25, 0.55)
mat('suit_light', (0.20, 0.22, 0.28), 0.3, 0.45)
mat('visor', (0.015, 0.02, 0.03), 0.7, 0.18)
mat('eye', (0.2, 0.9, 1.0), 0.0, 0.4, (0.25, 0.95, 1.0))
mat('courier_cloth', (0.10, 0.13, 0.36), 0.0, 0.7, double=True)
mat('courier_trim', (0.1, 0.8, 0.9), 0.0, 0.4, (0.15, 0.8, 0.95))
mat('courier_dark', (0.05, 0.06, 0.12), 0.2, 0.55)
mat('runner_white', (0.86, 0.88, 0.92), 0.1, 0.42, double=True)
mat('runner_orange', (1.0, 0.42, 0.08), 0.1, 0.5)
mat('runner_dark', (0.12, 0.13, 0.16), 0.5, 0.4)
mat('astral_cloth', (0.30, 0.17, 0.58), 0.0, 0.65, double=True)
mat('astral_gold', (0.95, 0.74, 0.25), 0.9, 0.28)
mat('astral_glow', (1.0, 0.85, 0.5), 0.0, 0.4, (1.0, 0.8, 0.35))
mat('lantern_glass', (1.0, 0.72, 0.30), 0.0, 0.2, (1.0, 0.62, 0.2))
mat('prop_metal', (0.55, 0.57, 0.62), 0.9, 0.3)
mat('tablet_body', (0.07, 0.08, 0.1), 0.6, 0.3)
mat('tablet_screen', (0.2, 0.7, 1.0), 0.0, 0.3, (0.1, 0.55, 0.95))
mat('compass_core', (0.6, 1.0, 0.8), 0.0, 0.3, (0.3, 1.0, 0.7))

# ---------------------------------------------------------------- body
def build_body():
    body = Prim('suit'); trim = Prim('suit_light'); visor = Prim('visor')
    # pelvis, waist, chest
    body.ellipsoid((0, 0.98, 0), (0.155, 0.12, 0.105), torso_w)
    body.tube((0, 1.00, 0), (0, 1.27, 0), 0.135, 0.15, torso_w, seg=18, rings=8, sx=1.1, sz=0.78)
    body.ellipsoid((0, 1.38, -0.005), (0.185, 0.17, 0.115), torso_w)
    body.tube((0, 1.45, 0), (0, 1.52, 0), 0.052, 0.045, torso_w)
    # neck and head
    body.tube(P('neck'), P('head'), 0.048, 0.044, between('neck', 'head'), seg=12, rings=2)
    body.ellipsoid((0, 1.725, 0.005), (0.098, 0.122, 0.108), one('head'), seg=22, ring=16)
    body.ellipsoid((0, 1.668, 0.03), (0.074, 0.068, 0.076), one('head'), seg=16, ring=10)     # jaw
    # ear pods and a swept crest give the head its silhouette
    for m in (1, -1):
        trim.tube((0.098*m, 1.735, 0.0), (0.118*m, 1.735, 0.0), 0.038, 0.038, one('head'), seg=16, rings=1, caps=True)
    trim.ellipsoid((0, 1.80, -0.075), (0.016, 0.075, 0.04), one('head'), seg=10, ring=8)
    trim.ellipsoid((0, 1.86, -0.12), (0.014, 0.05, 0.03), one('head'), seg=10, ring=8)
    # visor band across the eyes
    visor.ellipsoid((0, 1.735, 0.048), (0.092, 0.038, 0.072), one('head'), seg=20, ring=10)
    # arms and legs
    for s, m in (('l', 1), ('r', -1)):
        up, fo, ha = 'upperarm_' + s, 'forearm_' + s, 'hand_' + s
        body.ellipsoid(add(P('clavicle_' + s), (0.0, 0.0, 0.0)), (0.07, 0.055, 0.06), fixed(('chest', 0.6), ('clavicle_' + s, 0.4)))
        body.ellipsoid(P(up), (0.062, 0.062, 0.062), between('clavicle_' + s, up))
        body.tube(P(up), P(fo), 0.052, 0.042, bone(up, fo), seg=12, rings=6)
        body.ellipsoid(P(fo), (0.044, 0.044, 0.044), between(up, fo))
        body.tube(P(fo), P(ha), 0.042, 0.034, bone(fo, ha), seg=12, rings=6)
        trim.ellipsoid(P(ha), (0.038, 0.055, 0.032), one(ha), seg=12, ring=8)                 # hand
        trim.ellipsoid(add(P(ha), (0.026*m, 0.0, 0.03)), (0.016, 0.034, 0.016), one(ha), seg=8, ring=6)   # thumb
        th, sh, fo_ = 'thigh_' + s, 'shin_' + s, 'foot_' + s
        body.ellipsoid(P(th), (0.075, 0.075, 0.075), between('hips', th))
        body.tube(P(th), P(sh), 0.072, 0.052, bone(th, sh), seg=14, rings=8)
        body.ellipsoid(P(sh), (0.054, 0.054, 0.054), between(th, sh))
        body.tube(P(sh), P(fo_), 0.052, 0.036, bone(sh, fo_), seg=14, rings=8)
        trim.ellipsoid((P(fo_)[0], 0.055, 0.045), (0.05, 0.05, 0.12), one(fo_), seg=14, ring=10)  # boot
    return [body, trim, visor]

def build_eyes():
    out = {}
    for s, m in (('l', 1), ('r', -1)):
        e = Prim('eye', skinned=False)
        # eyes live under the head joint, so their positions are local to it
        e.ellipsoid((0.036*m, 0.135, 0.124), (0.020, 0.011, 0.006), lambda p: [], seg=10, ring=8)
        out['eye_' + s] = e
    return out

# ---------------------------------------------------------------- outfits
def outfit_courier():
    cloth = Prim('courier_cloth', double=True); trim = Prim('courier_trim'); dark = Prim('courier_dark')
    cloth.tube((0, 0.98, 0), (0, 1.27, 0), 0.165, 0.185, torso_w, seg=20, rings=8, sx=1.1, sz=0.82)
    cloth.ellipsoid((0, 1.38, -0.005), (0.205, 0.185, 0.135), torso_w, seg=20, ring=12)
    for s, m in (('l', 1), ('r', -1)):
        up, fo, ha = 'upperarm_' + s, 'forearm_' + s, 'hand_' + s
        cloth.tube(P(up), P(fo), 0.068, 0.056, bone(up, fo), seg=12, rings=6)
        cloth.tube(P(fo), add(P(ha), (0.0, 0.03, 0.0)), 0.058, 0.052, bone(fo, ha), seg=12, rings=6)
        cloth.ellipsoid(P(fo), (0.058, 0.058, 0.058), between(up, fo))
        trim.tube(add(P(fo), mul(norm(sub(P(ha), P(fo))), 0.17)), add(P(fo), mul(norm(sub(P(ha), P(fo))), 0.2)), 0.056, 0.056, bone(fo, ha, 0.3), seg=14, rings=1)
        th, sh = 'thigh_' + s, 'shin_' + s
        cloth.tube((0.095*m, 0.98, 0), (0.115*m, 0.46, 0.015), 0.115, 0.098, hip_leg_blend(s), seg=14, rings=8)
    cloth.ellipsoid((0, 0.96, -0.01), (0.2, 0.09, 0.15), hip_leg_blend('l'), seg=18, ring=8)
    trim.tube((0, 0.985, 0), (0, 1.03, 0), 0.172, 0.172, one('hips'), seg=20, rings=1, sx=1.1, sz=0.82)    # belt
    dark.torus((0, 1.52, 0.0), 0.075, 0.026, (0, 1, 0), one('neck'), seg=22, sides=8)                       # collar
    trim.tube((0.0, 1.43, 0.17), (0.0, 1.05, 0.15), 0.008, 0.008, torso_w, seg=6, rings=6)                  # front seam
    return [cloth, trim, dark]

def outfit_runner():
    white = Prim('runner_white', double=True); orange = Prim('runner_orange'); dark = Prim('runner_dark')
    white.tube((0, 1.06, 0), (0, 1.27, 0), 0.155, 0.172, torso_w, seg=20, rings=6, sx=1.1, sz=0.8)
    white.ellipsoid((0, 1.38, -0.005), (0.2, 0.17, 0.128), torso_w, seg=20, ring=12)
    orange.tube((0, 1.265, 0.0), (0, 1.30, 0.0), 0.186, 0.186, torso_w, seg=20, rings=1, sx=1.1, sz=0.8)     # chest band
    for s, m in (('l', 1), ('r', -1)):
        up, fo, ha = 'upperarm_' + s, 'forearm_' + s, 'hand_' + s
        dark.ellipsoid(add(P(up), (0.03*m, 0.045, 0.0)), (0.1, 0.07, 0.09), between('clavicle_' + s, up), seg=16, ring=10)   # pauldron
        orange.ellipsoid(add(P(up), (0.05*m, 0.06, 0.0)), (0.07, 0.025, 0.07), between('clavicle_' + s, up), seg=14, ring=8)
        white.tube(P(up), P(fo), 0.06, 0.05, bone(up, fo), seg=12, rings=6)
        dark.tube(add(P(fo), mul(norm(sub(P(ha), P(fo))), 0.05)), add(P(ha), mul(norm(sub(P(fo), P(ha))), 0.04)), 0.052, 0.045, bone(fo, ha), seg=12, rings=4)
        orange.tube(add(P(fo), mul(norm(sub(P(ha), P(fo))), 0.14)), add(P(fo), mul(norm(sub(P(ha), P(fo))), 0.165)), 0.054, 0.054, bone(fo, ha, 0.3), seg=14, rings=1)
        th, sh, ft = 'thigh_' + s, 'shin_' + s, 'foot_' + s
        white.tube(P(th), add(P(th), mul(sub(P(sh), P(th)), 0.8)), 0.085, 0.07, bone(th, sh), seg=14, rings=6)
        dark.ellipsoid(add(P(sh), (0.0, 0.01, 0.055)), (0.06, 0.075, 0.05), between(th, sh), seg=14, ring=10)           # knee pad
        dark.tube(add(P(sh), mul(sub(P(ft), P(sh)), 0.45)), P(ft), 0.06, 0.056, bone(sh, ft), seg=14, rings=4)
        orange.ellipsoid((P(ft)[0], 0.05, 0.06), (0.054, 0.04, 0.12), one(ft), seg=14, ring=8)
    orange.tube((0, 0.98, 0), (0, 1.02, 0), 0.16, 0.16, one('hips'), seg=20, rings=1, sx=1.1, sz=0.8)
    return [white, orange, dark]

def outfit_astral():
    cloth = Prim('astral_cloth', double=True); gold = Prim('astral_gold'); glow = Prim('astral_glow')
    cloth.tube((0, 1.00, 0), (0, 1.27, 0), 0.16, 0.18, torso_w, seg=22, rings=6, sx=1.1, sz=0.82)
    cloth.ellipsoid((0, 1.38, -0.005), (0.205, 0.18, 0.13), torso_w, seg=20, ring=12)
    for s, m in (('l', 1), ('r', -1)):
        cloth.tube((0.07*m, 1.0, 0), (0.12*m, 0.12, 0.0), 0.15, 0.22, hip_leg_blend(s), seg=16, rings=10)          # robe skirt, flaring
        up, fo, ha = 'upperarm_' + s, 'forearm_' + s, 'hand_' + s
        cloth.tube(P(up), P(fo), 0.07, 0.075, bone(up, fo), seg=12, rings=6)
        cloth.tube(P(fo), add(P(ha), (0.07*m, -0.12, 0.0)), 0.078, 0.13, bone(fo, ha, 0.2), seg=14, rings=6)       # wide hanging sleeve
        gold.tube(add(P(fo), (0.0, 0.0, 0.0)), add(P(fo), mul(norm(sub(P(ha), P(fo))), 0.03)), 0.078, 0.078, bone(fo, ha, 0.2), seg=14, rings=1)
    # a cape that hangs behind the shoulders and follows the spine
    cloth.box((0, 1.12, -0.17), (0.2, 0.38, 0.012), lambda p: [('chest', 0.6), ('spine', 0.4)] if p[1] > 1.1 else [('spine', 0.6), ('hips', 0.4)])
    gold.torus((0, 1.52, 0.0), 0.095, 0.02, (0, 1, 0), one('neck'), seg=24, sides=8)                            # gorget
    gold.tube((0, 1.0, 0), (0, 1.04, 0), 0.172, 0.172, one('hips'), seg=22, rings=1, sx=1.1, sz=0.82)
    glow.torus((0, 1.80, -0.14), 0.16, 0.012, (0, 0.25, 1), one('head'), seg=40, sides=8)                        # halo behind the head
    return [cloth, gold, glow]

# ---------------------------------------------------------------- props (parented to attach_hand_r, local space)
def prop_lantern():
    glass = Prim('lantern_glass', skinned=False); metal = Prim('prop_metal', skinned=False)
    nowt = lambda p: []
    glass.tube((0, -0.19, 0), (0, -0.07, 0), 0.052, 0.06, nowt, seg=16, rings=3, caps=True)
    metal.tube((0, -0.21, 0), (0, -0.19, 0), 0.05, 0.052, nowt, seg=16, rings=1, caps=True)
    metal.tube((0, -0.07, 0), (0, -0.045, 0), 0.065, 0.02, nowt, seg=16, rings=2, caps=True)
    metal.torus((0, -0.02, 0), 0.022, 0.006, (0, 0, 1), nowt, seg=16, sides=6)
    return [glass, metal]

def prop_tablet():
    body = Prim('tablet_body', skinned=False); screen = Prim('tablet_screen', skinned=False)
    nowt = lambda p: []
    body.box((0, 0.02, 0.0), (0.095, 0.135, 0.008), nowt)
    screen.box((0, 0.02, 0.0085), (0.083, 0.122, 0.001), nowt)
    return [body, screen]

def prop_compass():
    core = Prim('compass_core', skinned=False); metal = Prim('prop_metal', skinned=False)
    nowt = lambda p: []
    core.ellipsoid((0, 0.045, 0.0), (0.035, 0.035, 0.035), nowt, seg=16, ring=10)
    metal.torus((0, 0.045, 0.0), 0.062, 0.006, (0, 1, 0), nowt, seg=28, sides=6)
    metal.torus((0, 0.045, 0.0), 0.062, 0.006, (1, 0, 0), nowt, seg=28, sides=6)
    metal.torus((0, 0.045, 0.0), 0.062, 0.006, (0.7, 0, 0.7), nowt, seg=28, sides=6)
    return [core, metal]

# ---------------------------------------------------------------- glTF writer
class Writer:
    def __init__(self):
        self.bin = bytearray(); self.views = []; self.acc = []; self.meshes = []; self.nodes = []; self.skins = []
        self.mat_index = {}; self.materials = []

    def _view(self, data, target=None):
        while len(self.bin) % 4: self.bin.append(0)
        off = len(self.bin); self.bin += data
        v = dict(buffer=0, byteOffset=off, byteLength=len(data))
        if target: v['target'] = target
        self.views.append(v); return len(self.views) - 1

    def _accessor(self, data, ctype, count, atype, target=None, minmax=None, normalized=False):
        a = dict(bufferView=self._view(data, target), componentType=ctype, count=count, type=atype)
        if minmax: a['min'], a['max'] = minmax
        if normalized: a['normalized'] = True
        self.acc.append(a); return len(self.acc) - 1

    def material(self, name):
        if name not in self.mat_index:
            self.mat_index[name] = len(self.materials); self.materials.append(MATS[name])
        return self.mat_index[name]

    def mesh(self, name, prims):
        out = []
        for p in prims:
            if not p.pos: continue
            pos = b''.join(struct.pack('<3f', *v) for v in p.pos)
            nor = b''.join(struct.pack('<3f', *v) for v in p.nor)
            lo = [min(v[i] for v in p.pos) for i in range(3)]; hi = [max(v[i] for v in p.pos) for i in range(3)]
            attrs = dict(POSITION=self._accessor(pos, 5126, len(p.pos), 'VEC3', 34962, (lo, hi)),
                         NORMAL=self._accessor(nor, 5126, len(p.nor), 'VEC3', 34962))
            if p.skinned:
                jn = b''.join(struct.pack('<4B', *j) for j in p.jnt)
                wg = b''.join(struct.pack('<4f', *w) for w in p.wgt)
                attrs['JOINTS_0'] = self._accessor(jn, 5121, len(p.jnt), 'VEC4', 34962)
                attrs['WEIGHTS_0'] = self._accessor(wg, 5126, len(p.wgt), 'VEC4', 34962)
            big = len(p.pos) > 65535
            idx = struct.pack('<%d%s' % (len(p.idx), 'I' if big else 'H'), *p.idx)
            ia = self._accessor(idx, 5125 if big else 5123, len(p.idx), 'SCALAR', 34963)
            mt = self.material(p.material)
            if p.double: self.materials[mt] = dict(self.materials[mt], doubleSided=True)
            out.append(dict(attributes=attrs, indices=ia, material=mt, mode=4))
        self.meshes.append(dict(name=name, primitives=out)); return len(self.meshes) - 1

def build():
    w = Writer()
    nodes = []
    # joint nodes: local translation = world - parent world
    node_of = {}
    for n in ORDER:
        parent, pos = J[n]
        loc = pos if parent is None else sub(pos, P(parent))
        node_of[n] = len(nodes); nodes.append(dict(name=n, translation=[round(c, 6) for c in loc]))
    for n in ORDER:
        parent = J[n][0]
        if parent: nodes[node_of[parent]].setdefault('children', []).append(node_of[n])
    # attachment points and eyes
    def add_node(parent, **kw):
        nodes.append(kw); i = len(nodes) - 1
        nodes[parent].setdefault('children', []).append(i); return i
    attach_r = add_node(node_of['hand_r'], name='attach_hand_r', translation=[0.0, -0.045, 0.045], rotation=[0.0, 0.0, 0.0, 1.0])
    add_node(node_of['hand_l'], name='attach_hand_l', translation=[0.0, -0.045, 0.045])
    head_pos = P('head')
    eyes = build_eyes()
    for k, e in eyes.items():
        mi = w.mesh(k, [e]); add_node(node_of['head'], name=k, mesh=mi)
        # the eye geometry was built in world space for convenience; shift it into head-local space
        nodes[-1]['translation'] = [0.0, 0.0, 0.0]
    # (eye vertices are already authored head-local in build_eyes, so no extra offset is needed)

    # skin
    ibm = b''.join(struct.pack('<16f', 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, -P(n)[0], -P(n)[1], -P(n)[2], 1) for n in ORDER)
    ibm_acc = w._accessor(ibm, 5126, len(ORDER), 'MAT4')
    skin = dict(name='warden_skin', inverseBindMatrices=ibm_acc, joints=[node_of[n] for n in ORDER], skeleton=node_of['root'])

    scene_nodes = [node_of['root']]
    def skinned(name, prims):
        mi = w.mesh(name, prims); nodes.append(dict(name=name, mesh=mi, skin=0)); scene_nodes.append(len(nodes) - 1)
    skinned('body', build_body())
    skinned('outfit_courier', outfit_courier())
    skinned('outfit_runner', outfit_runner())
    skinned('outfit_astral', outfit_astral())
    for name, fn in (('prop_lantern', prop_lantern), ('prop_tablet', prop_tablet), ('prop_compass', prop_compass)):
        mi = w.mesh(name, fn()); add_node(attach_r, name=name, mesh=mi)

    gltf = dict(asset=dict(version='2.0', generator='haven gen_character.py', copyright='Original work for Haven, MIT licence'),
                scene=0, scenes=[dict(nodes=scene_nodes)], nodes=nodes, skins=[skin], meshes=w.meshes, materials=w.materials,
                accessors=w.acc, bufferViews=w.views, buffers=[dict(byteLength=len(w.bin))])
    js = json.dumps(gltf, separators=(',', ':')).encode()
    while len(js) % 4: js += b' '
    bn = bytes(w.bin)
    while len(bn) % 4: bn += b'\0'
    glb = struct.pack('<4sII', b'glTF', 2, 12 + 8 + len(js) + 8 + len(bn)) + struct.pack('<I4s', len(js), b'JSON') + js + struct.pack('<I4s', len(bn), b'BIN\0') + bn
    return glb, gltf

if __name__ == '__main__':
    glb, gltf = build()
    out = os.path.join(os.path.dirname(__file__), '..', '..', 'app', 'src', 'main', 'assets', 'character', 'warden.glb')
    out = os.path.abspath(out); os.makedirs(os.path.dirname(out), exist_ok=True)
    open(out, 'wb').write(glb)
    tris = sum(len(a) for a in [])  # placeholder to keep the summary simple
    print('wrote', out, len(glb) // 1024, 'KiB;', len(gltf['nodes']), 'nodes,', len(gltf['meshes']), 'meshes,', len(gltf['materials']), 'materials')
