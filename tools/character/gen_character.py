#!/usr/bin/env python3
"""Generates Haven's three rigged characters (human, wizard, robot) as skinned glTF binaries (GLB), using only the Python
standard library. All geometry is original: ellipsoids, tapered tubes, boxes and rings, skinned to a 17-joint skeleton
whose proportions differ per character. No third-party models, textures or licences are involved.

    python3 tools/character/gen_character.py        ->  app/src/main/assets/character/{human,wizard,robot}.glb

Asset contract (identical for every character, so the app can pose any of them):
  joints:  root hips spine chest neck head clavicle_l/r upperarm_l/r forearm_l/r hand_l/r thigh_l/r shin_l/r foot_l/r
  nodes:   body, eye_l, eye_r (under head), attach_hand_r / attach_hand_l, acc_star (under chest; the hidden pendant),
           sleep_blanket / sleep_pillow (under root; shown only while the character sleeps), prop_umbrella (weather prop)
  outfits: outfit_<id> skinned meshes (ids per character, see CHARACTERS below)
  props:   prop_<id> meshes parented to attach_hand_r
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


# ---------------------------------------------------------------- skeleton (switchable per character)
J = {}; ORDER = []; JI = {}; H = {}
def P(n): return J[n][1]

def use_rig(r):
    """r: dict of key heights and limb numbers. Rebuilds the module-level skeleton tables."""
    J.clear(); ORDER.clear(); H.clear(); H.update(r)
    def joint(name, parent, pos): J[name] = (parent, pos); ORDER.append(name)
    joint('root', None, (0.0, 0.0, 0.0))
    joint('hips', 'root', (0.0, r['hip'], 0.0))
    joint('spine', 'hips', (0.0, r['spine'], 0.0))
    joint('chest', 'spine', (0.0, r['chest'], 0.0))
    joint('neck', 'chest', (0.0, r['neck'], 0.0))
    joint('head', 'neck', (0.0, r['head'], 0.0))
    for side, m in (('l', 1), ('r', -1)):
        sy = r['sh_y']; sx = r['sx']
        joint('clavicle_' + side, 'chest', (r['cl'] * m, sy + 0.01, 0.0))
        up = (sx * m, sy, 0.0)
        el = (up[0] + math.sin(r['a1']) * r['ua'] * m, up[1] - math.cos(r['a1']) * r['ua'], 0.02)
        wr = (el[0] + math.sin(r['a2']) * r['fa'] * m, el[1] - math.cos(r['a2']) * r['fa'], 0.07)
        joint('upperarm_' + side, 'clavicle_' + side, up)
        joint('forearm_' + side, 'upperarm_' + side, el)
        joint('hand_' + side, 'forearm_' + side, wr)
        hx = r['hx'] * m
        joint('thigh_' + side, 'hips', (hx, r['hip'] - 0.03, 0.0))
        joint('shin_' + side, 'thigh_' + side, (hx + 0.02 * m, r['knee'], 0.015))
        joint('foot_' + side, 'shin_' + side, (hx + 0.025 * m, r['ankle'], 0.0))
    JI.clear(); JI.update({n: i for i, n in enumerate(ORDER)})


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

    def ellipsoid(self, c, r, wf, seg=18, ring=12, th0=0.0, th1=math.pi):
        rx, ry, rz = r
        base = len(self.pos)
        for i in range(ring + 1):
            th = th0 + (th1 - th0) * i / ring
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
    p0, p1 = P(j0), P(j1); ax = sub(p1, p0); L2 = max(dot(ax, ax), 1e-9)
    def f(p):
        t = dot(sub(p, p0), ax) / L2
        s = smooth((t - (1 - blend)) / blend)
        return [(j0, 1 - 0.5*s), (j1, 0.5*s)] if t > 0 else [(j0, 1.0)]
    return f
def fixed(*pairs): return lambda p: list(pairs)
def one(j): return lambda p: [(j, 1.0)]
def between(ja, jb): return lambda p: [(ja, 0.5), (jb, 0.5)]
def torso_w(p):
    y = p[1]; hip, sp, ch, nk = H['hip'], H['spine'], H['chest'], H['neck']
    if y < hip + 0.05: return [('hips', 1.0)]
    if y < sp + 0.06: s = smooth((y - hip - 0.05) / (sp + 0.01 - hip)); return [('hips', 1-s), ('spine', s)]
    if y < ch + 0.03: s = smooth((y - sp - 0.06) / (ch - sp)); return [('spine', 1-s), ('chest', s)]
    if y < nk: return [('chest', 1.0)]
    s = smooth((y - nk) / 0.10); return [('chest', 1-s), ('neck', s)]
def hip_leg_blend(side):
    thigh = 'thigh_' + side
    def f(p):
        s = smooth((H['hip'] - p[1]) / 0.35)
        return [('hips', 1 - 0.8*s), (thigh, 0.8*s)]
    return f


# ---------------------------------------------------------------- materials
MATS = {}
def mat(name, base, metal=0.0, rough=0.6, emissive=None, double=False):
    MATS[name] = dict(name=name, pbrMetallicRoughness=dict(baseColorFactor=[*base, 1.0], metallicFactor=metal, roughnessFactor=rough),
                      doubleSided=double, **({'emissiveFactor': list(emissive)} if emissive else {}))

# human
mat('h_skin', (0.78, 0.58, 0.46), 0.0, 0.55); mat('h_lips', (0.62, 0.34, 0.33), 0.0, 0.45); mat('h_hair', (0.09, 0.06, 0.045), 0.0, 0.5)
mat('h_under', (0.88, 0.88, 0.9), 0.0, 0.7); mat('h_trousers', (0.11, 0.12, 0.16), 0.0, 0.7); mat('h_shoe', (0.07, 0.07, 0.08), 0.1, 0.4)
mat('h_eyewhite', (0.92, 0.92, 0.9), 0.0, 0.3); mat('h_iris', (0.16, 0.27, 0.36), 0.0, 0.3)
mat('h_coat', (0.15, 0.16, 0.2), 0.0, 0.75, double=True); mat('h_coat_trim', (0.75, 0.62, 0.35), 0.8, 0.35); mat('h_coat_dark', (0.06, 0.06, 0.08), 0.2, 0.5)
mat('h_field', (0.36, 0.42, 0.3), 0.0, 0.8, double=True); mat('h_field_orange', (0.95, 0.45, 0.12), 0.0, 0.6); mat('h_scarf', (0.7, 0.18, 0.14), 0.0, 0.85, double=True)
# wizard
mat('w_skin', (0.84, 0.68, 0.58), 0.0, 0.55); mat('w_beard', (0.92, 0.92, 0.94), 0.0, 0.8); mat('w_tunic', (0.28, 0.3, 0.38), 0.0, 0.75)
mat('w_boot', (0.25, 0.15, 0.09), 0.0, 0.5); mat('w_eye', (0.15, 0.3, 0.5), 0.0, 0.3)
mat('w_robe', (0.17, 0.12, 0.45), 0.0, 0.7, double=True); mat('w_robe2', (0.32, 0.2, 0.62), 0.0, 0.7, double=True); mat('w_gold', (0.95, 0.76, 0.28), 0.9, 0.28)
mat('w_green', (0.2, 0.42, 0.28), 0.0, 0.8, double=True); mat('w_brown', (0.4, 0.27, 0.16), 0.0, 0.7, double=True)
mat('w_wood', (0.35, 0.22, 0.12), 0.0, 0.6); mat('w_crystal', (0.5, 0.8, 1.0), 0.0, 0.2, (0.25, 0.6, 1.0))
# robot
mat('r_frame', (0.18, 0.2, 0.24), 0.45, 0.45); mat('r_joint', (0.34, 0.37, 0.42), 0.5, 0.4); mat('r_visor', (0.01, 0.015, 0.02), 0.8, 0.15)
mat('r_glow', (0.2, 0.9, 1.0), 0.0, 0.3, (0.25, 0.95, 1.0)); mat('r_cable', (0.03, 0.03, 0.04), 0.0, 0.6)
mat('r_plate', (0.88, 0.9, 0.93), 0.15, 0.35); mat('r_plate_orange', (1.0, 0.44, 0.08), 0.15, 0.4); mat('r_plate_dark', (0.14, 0.15, 0.19), 0.3, 0.4)
mat('r_stealth', (0.09, 0.1, 0.13), 0.3, 0.4); mat('r_line', (0.1, 0.8, 1.0), 0.0, 0.3, (0.1, 0.7, 1.0))
# props and accessory
mat('lantern_glass', (1.0, 0.72, 0.30), 0.0, 0.2, (1.0, 0.62, 0.2)); mat('prop_metal', (0.55, 0.57, 0.62), 0.9, 0.3)
mat('tablet_body', (0.07, 0.08, 0.1), 0.6, 0.3); mat('tablet_screen', (0.2, 0.7, 1.0), 0.0, 0.3, (0.1, 0.55, 0.95))
mat('umbrella_cloth', (0.16, 0.2, 0.5), 0.0, 0.7, double=True); mat('umbrella_rib', (0.7, 0.72, 0.78), 0.8, 0.35)
mat('blanket', (0.2, 0.24, 0.5), 0.0, 0.9, double=True); mat('blanket_trim', (0.78, 0.8, 0.9), 0.0, 0.85); mat('pillow', (0.9, 0.9, 0.94), 0.0, 0.9)
mat('compass_core', (0.6, 1.0, 0.8), 0.0, 0.3, (0.3, 1.0, 0.7)); mat('star_gold', (0.98, 0.8, 0.32), 0.95, 0.22, (0.25, 0.18, 0.04))

def T0(p): return []                      # unskinned: no weights
def sk(p): return p

# ---------------------------------------------------------------- shared limb builders
def limbs(prims, r_up, r_fore, r_th, r_sh, mat_arm, mat_leg, hand_fn, foot_fn, joint_r=1.0, joint_mat=None):
    """Arms and legs as tapered tubes between the rig's joints, with the given radii."""
    for s, m in (('l', 1), ('r', -1)):
        up, fo, ha = 'upperarm_' + s, 'forearm_' + s, 'hand_' + s
        prims[mat_arm].ellipsoid(P(up), (r_up*1.1,)*3, between('clavicle_' + s, up))
        prims[mat_arm].tube(P(up), P(fo), r_up, r_fore*1.12, bone(up, fo), seg=14, rings=6)
        (prims[joint_mat or mat_arm]).ellipsoid(P(fo), (r_fore*1.15*joint_r,)*3, between(up, fo))
        prims[mat_arm].tube(P(fo), P(ha), r_fore*1.1, r_fore*0.8, bone(fo, ha), seg=14, rings=6)
        hand_fn(prims, s, m)
        th, sh, ft = 'thigh_' + s, 'shin_' + s, 'foot_' + s
        prims[mat_leg].ellipsoid(P(th), (r_th*1.05,)*3, between('hips', th))
        prims[mat_leg].tube(P(th), P(sh), r_th, r_sh*1.1, bone(th, sh), seg=16, rings=8)
        (prims[joint_mat or mat_leg]).ellipsoid(P(sh), (r_sh*1.12*joint_r,)*3, between(th, sh))
        prims[mat_leg].tube(P(sh), P(ft), r_sh*1.05, r_sh*0.7, bone(sh, ft), seg=14, rings=8)
        foot_fn(prims, s, m)

def hand_human(prims, s, m, mat='h_skin', scale=1.0):
    ha = 'hand_' + s; c = P(ha); k = scale
    prims[mat].ellipsoid((c[0] + 0.004*m, c[1] - 0.045*k, c[2] + 0.01), (0.036*k, 0.055*k, 0.02*k), one(ha), seg=12, ring=8)
    for i, dx in enumerate((-0.022, -0.007, 0.007, 0.022)):
        L = (0.05, 0.058, 0.054, 0.044)[i] * k
        prims[mat].tube((c[0] + dx*m*k, c[1] - 0.085*k, c[2] + 0.012), (c[0] + dx*m*k, c[1] - 0.085*k - L, c[2] + 0.02), 0.0072*k, 0.0058*k, one(ha), seg=8, rings=2)
    prims[mat].tube((c[0] + 0.03*m*k, c[1] - 0.03*k, c[2] + 0.02), (c[0] + 0.045*m*k, c[1] - 0.075*k, c[2] + 0.04), 0.009*k, 0.0075*k, one(ha), seg=8, rings=2)

def foot_shoe(prims, s, m, mat='h_shoe', length=0.13, width=0.05, h=0.045):
    ft = 'foot_' + s; c = P(ft)
    prims[mat].ellipsoid((c[0], h*0.9, 0.045), (width, h, length), one(ft), seg=14, ring=10)

def eye_node(prim_white, prim_iris, x, y, z, m, rw=(0.013, 0.01, 0.009), ri=0.0065):
    prim_white.ellipsoid((0, 0, 0), rw, lambda p: [], seg=12, ring=8)
    prim_iris.ellipsoid((0, 0, rw[2] * 0.78), (ri, ri, 0.004), lambda p: [], seg=10, ring=6)

# ---------------------------------------------------------------- HUMAN
def human_body():
    pr = {k: Prim(k) for k in ('h_skin', 'h_under', 'h_trousers', 'h_hair', 'h_lips', 'h_shoe')}
    hip, sp, ch, nk, hd = H['hip'], H['spine'], H['chest'], H['neck'], H['head']
    pr['h_trousers'].ellipsoid((0, hip + 0.02, 0), (0.15, 0.115, 0.105), torso_w)
    pr['h_under'].tube((0, hip + 0.06, 0), (0, ch, 0), 0.128, 0.148, torso_w, seg=20, rings=8, sx=1.12, sz=0.76)
    pr['h_under'].ellipsoid((0, ch + 0.09, -0.005), (0.178, 0.155, 0.105), torso_w, seg=20, ring=12)
    pr['h_skin'].tube((0, nk - 0.02, 0), (0, hd + 0.03, 0), 0.046, 0.043, between('neck', 'head'), seg=14, rings=3)
    hc = (0, hd + 0.115, 0.004)
    pr['h_skin'].ellipsoid(hc, (0.092, 0.113, 0.099), one('head'), seg=26, ring=20)
    pr['h_skin'].ellipsoid((0, hc[1] - 0.068, 0.034), (0.07, 0.056, 0.07), one('head'), seg=16, ring=10)            # jaw and chin
    pr['h_skin'].ellipsoid((0, hc[1] - 0.012, 0.099), (0.015, 0.027, 0.02), one('head'), seg=10, ring=8)             # nose
    pr['h_skin'].ellipsoid((0, hc[1] - 0.034, 0.1), (0.022, 0.012, 0.014), one('head'), seg=10, ring=8)              # nose base
    pr['h_lips'].ellipsoid((0, hc[1] - 0.064, 0.09), (0.03, 0.0085, 0.013), one('head'), seg=12, ring=6)
    for m in (1, -1):
        pr['h_skin'].ellipsoid((0.09*m, hc[1] - 0.012, 0.0), (0.011, 0.028, 0.02), one('head'), seg=10, ring=8)       # ears
        pr['h_hair'].tube((0.014*m, hc[1] + 0.047, 0.095), (0.056*m, hc[1] + 0.056, 0.084), 0.0065, 0.0055, one('head'), seg=8, rings=2)   # brows
    pr['h_hair'].ellipsoid((0, hc[1] + 0.012, -0.008), (0.099, 0.118, 0.108), one('head'), seg=24, ring=16, th0=0.0, th1=1.0)           # top cap
    pr['h_hair'].ellipsoid((0, hc[1] + 0.07, 0.05), (0.086, 0.038, 0.052), one('head'), seg=16, ring=8)             # fringe
    pr['h_hair'].ellipsoid((0, hc[1] - 0.012, -0.062), (0.088, 0.105, 0.058), one('head'), seg=18, ring=12)         # back of the head
    for m in (1, -1): pr['h_hair'].ellipsoid((0.083*m, hc[1] + 0.012, 0.02), (0.014, 0.05, 0.05), one('head'), seg=10, ring=8)  # sideburns
    limbs(pr, 0.047, 0.037, 0.074, 0.054, 'h_under', 'h_trousers', lambda p, s, m: hand_human(p, s, m), lambda p, s, m: foot_shoe(p, s, m))
    return list(pr.values())

def human_eyes():
    hd = H['head']; hy = 0.115 + 0.006
    out = {}
    for s, m in (('l', 1), ('r', -1)):
        w = Prim('h_eyewhite', skinned=False); i = Prim('h_iris', skinned=False); eye_node(w, i, 0, 0, 0, m)
        out['eye_' + s] = ([w, i], (0.034*m, hy, 0.093))
    return out

def human_coat():
    cl = Prim('h_coat', double=True); tr = Prim('h_coat_trim'); dk = Prim('h_coat_dark'); hip, ch = H['hip'], H['chest']
    cl.tube((0, hip + 0.04, 0), (0, ch, 0), 0.158, 0.172, torso_w, seg=22, rings=8, sx=1.12, sz=0.82)
    cl.ellipsoid((0, ch + 0.09, -0.005), (0.2, 0.17, 0.125), torso_w, seg=22, ring=14)
    for s, m in (('l', 1), ('r', -1)):
        up, fo, ha = 'upperarm_' + s, 'forearm_' + s, 'hand_' + s
        cl.tube(P(up), P(fo), 0.062, 0.054, bone(up, fo), seg=14, rings=6); cl.ellipsoid(P(fo), (0.056,)*3, between(up, fo))
        cl.tube(P(fo), add(P(ha), (0, 0.025, 0)), 0.055, 0.047, bone(fo, ha), seg=14, rings=6)
        tr.tube(add(P(ha), (0, 0.045, 0)), add(P(ha), (0, 0.032, 0)), 0.05, 0.05, bone(fo, ha, 0.2), seg=14, rings=1)          # cuff
        cl.tube((H['hx']*m, hip + 0.02, 0), (H['hx']*m + 0.02*m, H['knee'] + 0.06, 0.015), 0.112, 0.095, hip_leg_blend(s), seg=16, rings=8)  # coat skirt
        dk.ellipsoid(add(P(up), (0.0, 0.03, 0.0)), (0.09, 0.05, 0.085), between('clavicle_' + s, up), seg=12, ring=8)         # shoulder yoke
    tr.tube((0, hip + 0.045, 0), (0, hip + 0.085, 0), 0.164, 0.164, one('hips'), seg=22, rings=1, sx=1.12, sz=0.82)           # belt
    dk.torus((0, H['neck'] + 0.005, 0.0), 0.078, 0.028, (0, 1, 0), one('neck'), seg=22, sides=8)                              # raised collar
    for m in (1, -1): dk.ellipsoid((0.045*m, ch + 0.16, 0.115), (0.04, 0.09, 0.012), torso_w, seg=10, ring=8)                 # lapels
    for k in range(4): tr.ellipsoid((0.0, hip + 0.13 + k*0.075, 0.153), (0.012, 0.012, 0.007), torso_w, seg=8, ring=6)        # buttons
    return [cl, tr, dk]

def human_field():
    jk = Prim('h_field', double=True); org = Prim('h_field_orange'); sc = Prim('h_scarf', double=True); dk = Prim('h_coat_dark')
    hip, ch = H['hip'], H['chest']
    jk.tube((0, hip + 0.02, 0), (0, ch, 0), 0.15, 0.168, torso_w, seg=22, rings=6, sx=1.12, sz=0.82)
    jk.ellipsoid((0, ch + 0.09, -0.005), (0.198, 0.165, 0.122), torso_w, seg=22, ring=14)
    org.tube((0, hip + 0.02, 0), (0, hip + 0.045, 0), 0.157, 0.157, one('hips'), seg=22, rings=1, sx=1.12, sz=0.82)           # hem band
    for s, m in (('l', 1), ('r', -1)):
        up, fo, ha = 'upperarm_' + s, 'forearm_' + s, 'hand_' + s
        jk.tube(P(up), P(fo), 0.058, 0.05, bone(up, fo), seg=14, rings=6); jk.ellipsoid(P(fo), (0.052,)*3, between(up, fo))
        jk.tube(P(fo), add(P(ha), (0, 0.04, 0)), 0.051, 0.043, bone(fo, ha), seg=14, rings=6)
        org.ellipsoid(add(P(up), (0.0, 0.06, 0.0)), (0.07, 0.022, 0.07), between('clavicle_' + s, up), seg=12, ring=6)       # shoulder tab
        dk.tube((H['hx']*m, H['knee'] + 0.1, 0.01), (H['hx']*m + 0.02*m, H['knee'] - 0.12, 0.015), 0.082, 0.074, bone('thigh_' + s, 'shin_' + s), seg=14, rings=4)   # knee guards
    sc.torus((0, H['neck'] + 0.02, 0.0), 0.07, 0.035, (0, 1, 0), one('neck'), seg=22, sides=8)                                 # scarf ring
    sc.tube((0.05, H['neck'] - 0.02, 0.07), (0.07, ch - 0.12, 0.14), 0.03, 0.026, torso_w, seg=10, rings=4)                    # hanging tail
    dk.ellipsoid((0, ch + 0.06, -0.14), (0.1, 0.13, 0.05), torso_w, seg=14, ring=10)                                           # backpack
    return [jk, org, sc, dk]

# ---------------------------------------------------------------- WIZARD
def wizard_body():
    pr = {k: Prim(k) for k in ('w_skin', 'w_tunic', 'w_beard', 'w_boot')}
    hip, sp, ch, nk, hd = H['hip'], H['spine'], H['chest'], H['neck'], H['head']
    pr['w_tunic'].ellipsoid((0, hip + 0.02, 0), (0.15, 0.11, 0.105), torso_w)
    pr['w_tunic'].tube((0, hip + 0.05, 0), (0, ch, 0), 0.128, 0.145, torso_w, seg=20, rings=8, sx=1.1, sz=0.78)
    pr['w_tunic'].ellipsoid((0, ch + 0.09, -0.005), (0.176, 0.15, 0.105), torso_w, seg=20, ring=12)
    pr['w_skin'].tube((0, nk - 0.02, 0), (0, hd + 0.03, 0), 0.046, 0.043, between('neck', 'head'), seg=14, rings=3)
    hc = (0, hd + 0.115, 0.004)
    pr['w_skin'].ellipsoid(hc, (0.093, 0.116, 0.1), one('head'), seg=26, ring=20)
    pr['w_skin'].ellipsoid((0, hc[1] - 0.07, 0.03), (0.07, 0.05, 0.068), one('head'), seg=14, ring=8)
    pr['w_skin'].ellipsoid((0, hc[1] - 0.015, 0.1), (0.019, 0.034, 0.026), one('head'), seg=12, ring=8)              # larger nose
    for m in (1, -1):
        pr['w_skin'].ellipsoid((0.09*m, hc[1] - 0.012, 0.0), (0.011, 0.03, 0.02), one('head'), seg=10, ring=8)
        pr['w_beard'].tube((0.012*m, hc[1] + 0.05, 0.096), (0.062*m, hc[1] + 0.066, 0.08), 0.011, 0.008, one('head'), seg=8, rings=2)   # bushy brows
    # beard: mustache, then a long tapered fall over the chest
    pr['w_beard'].ellipsoid((0, hc[1] - 0.05, 0.092), (0.048, 0.016, 0.02), one('head'), seg=14, ring=8)
    pr['w_beard'].ellipsoid((0, hc[1] - 0.085, 0.07), (0.072, 0.06, 0.05), one('head'), seg=16, ring=10)
    pr['w_beard'].tube((0, hc[1] - 0.09, 0.07), (0, nk - 0.28, 0.1), 0.07, 0.012, fixed(('head', 0.4), ('chest', 0.6)), seg=16, rings=8)
    pr['w_beard'].ellipsoid((0, nk - 0.28, 0.1), (0.02, 0.025, 0.02), fixed(('chest', 1.0)), seg=8, ring=6)
    # hair: swept back and long
    pr['w_beard'].ellipsoid((0, hc[1] + 0.012, -0.012), (0.099, 0.12, 0.108), one('head'), seg=24, ring=16, th0=0.0, th1=1.05)
    pr['w_beard'].tube((0, hc[1] + 0.02, -0.075), (0, nk - 0.2, -0.085), 0.09, 0.05, fixed(('head', 0.5), ('chest', 0.5)), seg=16, rings=8)
    limbs(pr, 0.046, 0.036, 0.072, 0.054, 'w_tunic', 'w_tunic', lambda p, s, m: hand_human(p, s, m, 'w_skin'), lambda p, s, m: foot_shoe(p, s, m, 'w_boot', 0.14, 0.052, 0.05))
    return list(pr.values())

def wizard_eyes():
    hy = 0.115 + 0.006; out = {}
    for s, m in (('l', 1), ('r', -1)):
        w = Prim('h_eyewhite', skinned=False); i = Prim('w_eye', skinned=False); eye_node(w, i, 0, 0, 0, m, rw=(0.012, 0.0095, 0.009), ri=0.006)
        out['eye_' + s] = ([w, i], (0.034*m, hy, 0.094))
    return out

def hat(cl_main, cl_trim, tall=0.5, brim=0.19, droop=0.0):
    hd = H['head']; base = hd + 0.2
    top = Prim(cl_main, double=True); trim = Prim(cl_trim)
    top.tube((0, base + 0.01, 0), (0, base + tall * 0.55, -0.01), 0.108, 0.07, one('head'), seg=22, rings=6)
    top.tube((0, base + tall * 0.55, -0.01), (0, base + tall * 0.98, -0.045 - droop), 0.07, 0.012, one('head'), seg=18, rings=6)
    top.ellipsoid((0, base + tall * 0.99, -0.047 - droop), (0.017, 0.017, 0.017), one('head'), seg=8, ring=6)
    top.tube((0, base - 0.075, 0), (0, base + 0.012, 0), brim, 0.11, one('head'), seg=28, rings=3)                             # brim
    trim.tube((0, base - 0.012, 0), (0, base + 0.042, 0), 0.11, 0.106, one('head'), seg=24, rings=1)                          # band
    return [top, trim]

def wizard_archmage():
    rb = Prim('w_robe', double=True); r2 = Prim('w_robe2', double=True); gd = Prim('w_gold')
    hip, ch, nk = H['hip'], H['chest'], H['neck']
    rb.tube((0, hip + 0.04, 0), (0, ch, 0), 0.16, 0.18, torso_w, seg=24, rings=8, sx=1.1, sz=0.82)
    rb.ellipsoid((0, ch + 0.09, -0.005), (0.205, 0.17, 0.13), torso_w, seg=22, ring=14)
    for s, m in (('l', 1), ('r', -1)):
        up, fo, ha = 'upperarm_' + s, 'forearm_' + s, 'hand_' + s
        rb.tube((H['hx']*m*0.8, hip + 0.04, 0), (0.12*m, 0.06, 0.01), 0.16, 0.27, hip_leg_blend(s), seg=18, rings=12)       # flared skirt
        r2.tube((H['hx']*m*0.8, hip - 0.35, 0), (0.12*m, 0.06, 0.012), 0.2, 0.285, hip_leg_blend(s), seg=18, rings=8)         # layered under-skirt
        gd.tube((0.12*m, 0.075, 0.012), (0.12*m, 0.045, 0.012), 0.285, 0.29, hip_leg_blend(s), seg=24, rings=1)               # hem
        rb.tube(P(up), P(fo), 0.07, 0.078, bone(up, fo), seg=14, rings=6)
        r2.tube(P(fo), add(P(ha), (0.09*m, -0.14, 0)), 0.082, 0.15, bone(fo, ha, 0.25), seg=16, rings=8)                    # wide hanging sleeve
        gd.tube(add(P(fo), (0, 0, 0)), add(P(fo), mul(norm(sub(P(ha), P(fo))), 0.03)), 0.084, 0.084, bone(fo, ha, 0.2), seg=16, rings=1)
        gd.ellipsoid(add(P(up), (0.03*m, 0.045, 0)), (0.095, 0.05, 0.09), between('clavicle_' + s, up), seg=14, ring=8)       # mantle plates
    rb.box((0, hip + 0.3, -0.19), (0.19, 0.46, 0.012), lambda p: [('chest', 0.6), ('spine', 0.4)] if p[1] > hip + 0.2 else [('spine', 0.6), ('hips', 0.4)])  # cape
    gd.tube((0, hip + 0.045, 0), (0, hip + 0.085, 0), 0.172, 0.172, one('hips'), seg=24, rings=1, sx=1.1, sz=0.82)            # sash
    gd.torus((0, nk + 0.005, 0), 0.095, 0.02, (0, 1, 0), one('neck'), seg=24, sides=8)                                       # gorget
    return [rb, r2, gd] + hat('w_robe', 'w_gold', 0.52, 0.2)

def wizard_apprentice():
    rb = Prim('w_green', double=True); br = Prim('w_brown', double=True); gd = Prim('w_gold')
    hip, ch, nk = H['hip'], H['chest'], H['neck']
    rb.tube((0, hip + 0.04, 0), (0, ch, 0), 0.152, 0.172, torso_w, seg=22, rings=8, sx=1.1, sz=0.8)
    rb.ellipsoid((0, ch + 0.09, -0.005), (0.198, 0.165, 0.126), torso_w, seg=20, ring=12)
    for s, m in (('l', 1), ('r', -1)):
        up, fo, ha = 'upperarm_' + s, 'forearm_' + s, 'hand_' + s
        rb.tube((H['hx']*m*0.8, hip + 0.04, 0), (0.12*m, H['knee'] - 0.02, 0.01), 0.15, 0.2, hip_leg_blend(s), seg=16, rings=8)
        rb.tube(P(up), P(fo), 0.062, 0.058, bone(up, fo), seg=12, rings=6)
        rb.tube(P(fo), add(P(ha), (0, 0.04, 0)), 0.06, 0.064, bone(fo, ha), seg=12, rings=6)
    br.tube((0, hip + 0.05, 0), (0, hip + 0.1, 0), 0.165, 0.165, one('hips'), seg=22, rings=1, sx=1.1, sz=0.8)                 # leather belt
    gd.ellipsoid((0, hip + 0.075, 0.15), (0.028, 0.026, 0.012), one('hips'), seg=10, ring=6)                                  # buckle
    br.ellipsoid((0.15, hip + 0.02, 0.06), (0.05, 0.07, 0.04), one('hips'), seg=12, ring=8)                                   # pouch
    br.torus((0, nk + 0.01, 0), 0.09, 0.032, (0, 1, 0), one('neck'), seg=22, sides=8)                                         # cowl
    return [rb, br, gd] + hat('w_brown', 'w_green', 0.4, 0.17, droop=0.07)

# ---------------------------------------------------------------- ROBOT
def robot_body():
    fr = Prim('r_frame'); jt = Prim('r_joint'); vs = Prim('r_visor'); gl = Prim('r_glow'); cb = Prim('r_cable')
    hip, sp, ch, nk, hd = H['hip'], H['spine'], H['chest'], H['neck'], H['head']
    fr.ellipsoid((0, hip + 0.02, 0), (0.17, 0.1, 0.115), torso_w)
    fr.tube((0, hip + 0.05, 0), (0, ch, 0), 0.1, 0.12, torso_w, seg=16, rings=6, sx=1.0, sz=0.8)                                # spine column
    for k in range(5): jt.torus((0, hip + 0.1 + k*0.05, 0), 0.105 + 0.004*k, 0.012, (0, 1, 0), torso_w, seg=20, sides=6)      # ribs
    fr.ellipsoid((0, ch + 0.1, -0.005), (0.215, 0.17, 0.13), torso_w, seg=20, ring=14)
    gl.ellipsoid((0, ch + 0.07, 0.123), (0.04, 0.04, 0.02), one('chest'), seg=14, ring=8)                                      # core light
    jt.torus((0, ch + 0.07, 0.115), 0.052, 0.01, (0, 0, 1), one('chest'), seg=22, sides=6)
    jt.tube((0, nk - 0.03, 0), (0, hd + 0.04, 0), 0.05, 0.045, between('neck', 'head'), seg=12, rings=3)
    for m in (1, -1): cb.tube((0.03*m, nk - 0.02, -0.03), (0.07*m, ch + 0.02, -0.06), 0.008, 0.008, torso_w, seg=6, rings=4)
    hc = (0, hd + 0.115, 0.0)
    fr.ellipsoid(hc, (0.105, 0.12, 0.115), one('head'), seg=26, ring=20)                                                         # helmet
    vs.ellipsoid((0, hc[1] + 0.005, 0.045), (0.088, 0.058, 0.082), one('head'), seg=20, ring=12)                                # faceplate
    gl.ellipsoid((0, hc[1] + 0.003, 0.118), (0.062, 0.011, 0.01), one('head'), seg=14, ring=8)                                  # visor light bar
    for m in (1, -1):
        jt.tube((0.104*m, hc[1], 0.0), (0.128*m, hc[1], 0.0), 0.042, 0.042, one('head'), seg=18, rings=1, caps=True)          # ear modules
        gl.torus((0.128*m, hc[1], 0.0), 0.028, 0.005, (1, 0, 0), one('head'), seg=18, sides=6)
    fr.tube((0, hc[1] + 0.1, -0.02), (0, hc[1] + 0.145, -0.07), 0.02, 0.012, one('head'), seg=10, rings=3)                    # crest
    def hand(prims, s, m):
        ha = 'hand_' + s; c = P(ha)
        jt.ellipsoid((c[0], c[1] - 0.04, c[2] + 0.01), (0.04, 0.05, 0.035), one(ha), seg=12, ring=8)
        for dx in (-0.02, 0.0, 0.02): fr.tube((c[0] + dx, c[1] - 0.08, c[2] + 0.015), (c[0] + dx, c[1] - 0.14, c[2] + 0.03), 0.009, 0.006, one(ha), seg=8, rings=2)
        fr.tube((c[0] + 0.035*m, c[1] - 0.03, c[2] + 0.02), (c[0] + 0.055*m, c[1] - 0.08, c[2] + 0.04), 0.009, 0.007, one(ha), seg=8, rings=2)
    def foot(prims, s, m):
        ft = 'foot_' + s; c = P(ft)
        fr.ellipsoid((c[0], 0.05, 0.055), (0.06, 0.05, 0.15), one(ft), seg=14, ring=10)
        jt.ellipsoid((c[0], 0.085, 0.0), (0.05, 0.035, 0.05), one(ft), seg=10, ring=6)
    pr = {'r_frame': fr, 'r_joint': jt}
    limbs(pr, 0.058, 0.046, 0.085, 0.062, 'r_frame', 'r_frame', hand, foot, joint_r=1.15, joint_mat='r_joint')
    for s in ('l', 'r'):                                                                                                       # piston rods beside the limbs
        for a, b in (('upperarm_' + s, 'forearm_' + s), ('thigh_' + s, 'shin_' + s)):
            pa, pb = P(a), P(b); off = (0.0, 0.0, -0.05)
            cb.tube(add(pa, off), add(pb, off), 0.01, 0.01, bone(a, b), seg=8, rings=4)
    return [fr, jt, vs, gl, cb]

def robot_eyes():
    out = {}                                                                          # the visor bar is the "eyes"; two small lamps that blink
    for s, m in (('l', 1), ('r', -1)):
        e = Prim('r_glow', skinned=False); e.ellipsoid((0, 0, 0), (0.022, 0.011, 0.008), lambda p: [], seg=10, ring=6)
        out['eye_' + s] = ([e], (0.034*m, 0.122, 0.126))
    return out

def robot_plated():
    pl = Prim('r_plate'); og = Prim('r_plate_orange'); dk = Prim('r_plate_dark'); hip, ch, nk = H['hip'], H['chest'], H['neck']
    pl.ellipsoid((0, ch + 0.11, 0.03), (0.215, 0.15, 0.115), torso_w, seg=22, ring=12, th0=0.0, th1=0.98)                      # chest shell
    pl.ellipsoid((0, ch + 0.11, 0.04), (0.19, 0.12, 0.11), torso_w, seg=20, ring=10)
    og.ellipsoid((0, ch + 0.0, 0.12), (0.11, 0.022, 0.05), torso_w, seg=14, ring=6)
    dk.tube((0, hip + 0.03, 0), (0, hip + 0.1, 0), 0.178, 0.178, one('hips'), seg=22, rings=1, sx=1.05, sz=0.82)
    pl.ellipsoid((0, hip + 0.07, 0.0), (0.185, 0.05, 0.14), one('hips'), seg=20, ring=8)
    for s, m in (('l', 1), ('r', -1)):
        up, fo, ha, th, sh = 'upperarm_' + s, 'forearm_' + s, 'hand_' + s, 'thigh_' + s, 'shin_' + s
        pl.ellipsoid(add(P(up), (0.04*m, 0.05, 0)), (0.13, 0.085, 0.12), between('clavicle_' + s, up), seg=18, ring=10)         # pauldron
        og.ellipsoid(add(P(up), (0.05*m, 0.085, 0)), (0.09, 0.02, 0.09), between('clavicle_' + s, up), seg=14, ring=6)
        pl.tube(add(P(up), mul(sub(P(fo), P(up)), 0.35)), add(P(up), mul(sub(P(fo), P(up)), 0.8)), 0.068, 0.06, bone(up, fo), seg=14, rings=3)
        pl.tube(add(P(fo), mul(sub(P(ha), P(fo)), 0.15)), add(P(fo), mul(sub(P(ha), P(fo)), 0.85)), 0.058, 0.05, bone(fo, ha), seg=14, rings=3)
        og.tube(add(P(fo), mul(sub(P(ha), P(fo)), 0.12)), add(P(fo), mul(sub(P(ha), P(fo)), 0.17)), 0.06, 0.06, bone(fo, ha, 0.3), seg=14, rings=1)
        pl.tube(add(P(th), mul(sub(P(sh), P(th)), 0.18)), add(P(th), mul(sub(P(sh), P(th)), 0.82)), 0.095, 0.078, bone(th, sh), seg=16, rings=4)
        dk.ellipsoid(add(P(sh), (0.0, 0.01, 0.07)), (0.07, 0.085, 0.06), between(th, sh), seg=14, ring=10)                      # knee armour
        pl.tube(add(P(sh), mul(sub(P('foot_' + s), P(sh)), 0.3)), add(P(sh), mul(sub(P('foot_' + s), P(sh)), 0.9)), 0.075, 0.062, bone(sh, 'foot_' + s), seg=14, rings=3)
    pl.ellipsoid((0, H['head'] + 0.115, 0.0), (0.112, 0.128, 0.122), one('head'), seg=24, ring=14, th0=0.0, th1=0.7)               # helmet armour
    og.tube((0, H['head'] + 0.2, -0.04), (0, H['head'] + 0.245, -0.09), 0.026, 0.014, one('head'), seg=10, rings=3)
    return [pl, og, dk]

def robot_stealth():
    sh = Prim('r_stealth'); ln = Prim('r_line'); hip, ch, nk = H['hip'], H['chest'], H['neck']
    sh.tube((0, hip + 0.05, 0), (0, ch, 0), 0.138, 0.158, torso_w, seg=22, rings=6, sx=1.05, sz=0.82)
    sh.ellipsoid((0, ch + 0.11, 0.0), (0.222, 0.17, 0.132), torso_w, seg=22, ring=14)
    ln.tube((0.0, ch + 0.2, 0.128), (0.0, hip + 0.1, 0.12), 0.006, 0.006, torso_w, seg=6, rings=8)
    for m in (1, -1): ln.tube((0.08*m, ch + 0.17, 0.12), (0.06*m, hip + 0.12, 0.115), 0.005, 0.005, torso_w, seg=6, rings=8)
    for s, m in (('l', 1), ('r', -1)):
        up, fo, ha, th, sk_ = 'upperarm_' + s, 'forearm_' + s, 'hand_' + s, 'thigh_' + s, 'shin_' + s
        sh.tube(P(up), P(fo), 0.07, 0.063, bone(up, fo), seg=14, rings=5); sh.tube(P(fo), add(P(ha), (0, 0.03, 0)), 0.062, 0.054, bone(fo, ha), seg=14, rings=5)
        ln.tube(add(P(up), (0.0, -0.03, 0.065)), add(P(fo), (0.0, 0.03, 0.058)), 0.004, 0.004, bone(up, fo), seg=6, rings=6)
        sh.tube((H['hx']*m, hip - 0.02, 0), (H['hx']*m + 0.02*m, H['knee'] - 0.02, 0.015), 0.098, 0.08, hip_leg_blend(s), seg=16, rings=6)
        ln.tube(add(P(th), (0.0, -0.05, 0.095)), add(P(sk_), (0.0, 0.05, 0.075)), 0.004, 0.004, bone(th, sk_), seg=6, rings=6)
        sh.ellipsoid(add(P(up), (0.03*m, 0.04, 0)), (0.11, 0.07, 0.1), between('clavicle_' + s, up), seg=16, ring=10)
    sh.ellipsoid((0, H['head'] + 0.115, 0.0), (0.11, 0.126, 0.12), one('head'), seg=22, ring=12, th0=0.0, th1=0.62)
    ln.tube((0, H['head'] + 0.24, -0.02), (0, H['head'] + 0.24, -0.1), 0.006, 0.006, one('head'), seg=6, rings=2)
    return [sh, ln]

# ---------------------------------------------------------------- props and the hidden accessory
def prop_lantern():
    glass = Prim('lantern_glass', skinned=False); metal = Prim('prop_metal', skinned=False)
    glass.tube((0, -0.19, 0), (0, -0.07, 0), 0.052, 0.06, T0, seg=16, rings=3, caps=True)
    metal.tube((0, -0.21, 0), (0, -0.19, 0), 0.05, 0.052, T0, seg=16, rings=1, caps=True)
    metal.tube((0, -0.07, 0), (0, -0.045, 0), 0.065, 0.02, T0, seg=16, rings=2, caps=True)
    metal.torus((0, -0.02, 0), 0.022, 0.006, (0, 0, 1), T0, seg=16, sides=6)
    return [glass, metal]

def prop_tablet():
    body = Prim('tablet_body', skinned=False); screen = Prim('tablet_screen', skinned=False)
    body.box((0, 0.02, 0.0), (0.095, 0.135, 0.008), T0); screen.box((0, 0.02, 0.0085), (0.083, 0.122, 0.001), T0)
    return [body, screen]

def prop_compass():
    core = Prim('compass_core', skinned=False); metal = Prim('prop_metal', skinned=False)
    core.ellipsoid((0, 0.045, 0.0), (0.035, 0.035, 0.035), T0, seg=16, ring=10)
    for ax in ((0, 1, 0), (1, 0, 0), (0.7, 0, 0.7)): metal.torus((0, 0.045, 0.0), 0.062, 0.006, ax, T0, seg=28, sides=6)
    return [core, metal]

def prop_staff():
    wood = Prim('w_wood', skinned=False); gold = Prim('w_gold', skinned=False); cr = Prim('w_crystal', skinned=False)
    wood.tube((0, -0.78, 0), (0, 0.62, 0), 0.017, 0.022, T0, seg=12, rings=8)
    gold.tube((0, -0.8, 0), (0, -0.76, 0), 0.02, 0.019, T0, seg=12, rings=1, caps=True)
    for a in (0.0, 2.09, 4.19): gold.tube((0.03*math.cos(a), 0.6, 0.03*math.sin(a)), (0.055*math.cos(a), 0.74, 0.055*math.sin(a)), 0.007, 0.005, T0, seg=6, rings=3)   # claws
    gold.torus((0, 0.62, 0), 0.03, 0.007, (0, 1, 0), T0, seg=18, sides=6)
    cr.ellipsoid((0, 0.7, 0), (0.035, 0.05, 0.035), T0, seg=14, ring=10)
    return [wood, gold, cr]

def prop_umbrella():
    """Weather prop. The canopy is on the -y side of the hand, so it ends up overhead when the forearm is raised. Scaled to 0.75 so it fits the frame."""
    k = 0.75
    cloth = Prim('umbrella_cloth', skinned=False); rib = Prim('umbrella_rib', skinned=False)
    rib.tube((0, 0.0, 0), (0, -0.88 * k, 0), 0.0075, 0.0065, T0, seg=8, rings=6)
    rib.torus((0, 0.02, 0), 0.026, 0.0085, (0, 0, 1), T0, seg=14, sides=6)                      # handle hook
    cloth.ellipsoid((0, -0.82 * k, 0), (0.5 * k, 0.2 * k, 0.5 * k), T0, seg=24, ring=8, th0=math.pi - 1.35, th1=math.pi)   # dome
    for n in range(8):
        a = n * math.pi / 4
        rib.tube((0.0, -1.02 * k, 0), (0.5 * k * math.cos(a) * 0.98, -0.83 * k, 0.5 * k * math.sin(a) * 0.98), 0.0035, 0.0035, T0, seg=6, rings=3)
    rib.ellipsoid((0, -1.03 * k, 0), (0.012, 0.02, 0.012), T0, seg=8, ring=6)                   # tip
    return [cloth, rib]

def sleep_blanket(front):
    b = Prim('blanket', skinned=False); tr = Prim('blanket_trim', skinned=False)
    z = front + 0.07
    b.ellipsoid((0, 0.74, z), (0.36, 0.6, 0.06), T0, seg=22, ring=14)
    tr.tube((-0.36, 1.27, z + 0.01), (0.36, 1.27, z + 0.01), 0.04, 0.04, T0, seg=14, rings=1)   # folded top edge
    return [b, tr]

def sleep_pillow(head_y):
    p = Prim('pillow', skinned=False)
    p.ellipsoid((0, head_y + 0.1, -0.13), (0.22, 0.14, 0.09), T0, seg=20, ring=10)
    return [p]

def accessory_star(front_z, y):
    """The hidden pendant: two interlaced triangles on a chain, built in chest-local space."""
    gold = Prim('star_gold', skinned=False)
    R = 0.046; cz = front_z
    def v(a): return (R * math.cos(a), y + R * math.sin(a), cz)
    for base in (math.pi / 2, -math.pi / 2):
        pts = [v(base + k * 2 * math.pi / 3) for k in range(3)]
        for k in range(3): gold.tube(pts[k], pts[(k + 1) % 3], 0.0034, 0.0034, T0, seg=8, rings=1)
        for p in pts: gold.ellipsoid(p, (0.0048,) * 3, T0, seg=8, ring=6)
    gold.torus((0, y + R + 0.012, cz), 0.008, 0.0026, (0, 0, 1), T0, seg=12, sides=6)
    for m in (1, -1): gold.tube((0.0, y + R + 0.02, cz), (0.07 * m, y + R + 0.2, cz - 0.045), 0.0018, 0.0018, T0, seg=6, rings=6)
    return [gold]

# ---------------------------------------------------------------- character specs
CHARACTERS = {
    'human': dict(rig=dict(hip=0.93, spine=1.06, chest=1.25, neck=1.47, head=1.57, sh_y=1.42, sx=0.185, cl=0.09, ua=0.29, fa=0.26, a1=0.28, a2=0.18, hx=0.09, knee=0.50, ankle=0.08),
                  body=human_body, eyes=human_eyes, outfits={'city': human_coat, 'field': human_field},
                  props=['lantern', 'tablet', 'compass'], front=0.14, hand=(0.0, -0.045, 0.04)),
    'wizard': dict(rig=dict(hip=0.95, spine=1.08, chest=1.28, neck=1.50, head=1.60, sh_y=1.45, sx=0.19, cl=0.09, ua=0.29, fa=0.27, a1=0.3, a2=0.2, hx=0.09, knee=0.51, ankle=0.08),
                   body=wizard_body, eyes=wizard_eyes, outfits={'archmage': wizard_archmage, 'apprentice': wizard_apprentice},
                   props=['staff', 'lantern', 'compass'], front=0.16, hand=(0.0, -0.045, 0.04)),
    'robot': dict(rig=dict(hip=0.98, spine=1.12, chest=1.33, neck=1.58, head=1.68, sh_y=1.52, sx=0.27, cl=0.11, ua=0.31, fa=0.29, a1=0.3, a2=0.2, hx=0.12, knee=0.52, ankle=0.10),
                  body=robot_body, eyes=robot_eyes, outfits={'plated': robot_plated, 'stealth': robot_stealth},
                  props=['tablet', 'compass', 'lantern'], front=0.19, hand=(0.0, -0.06, 0.05)),
}
PROPS = {'lantern': prop_lantern, 'tablet': prop_tablet, 'compass': prop_compass, 'staff': prop_staff, 'umbrella': prop_umbrella}


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


# ---------------------------------------------------------------- build one character into a GLB
def build_character(cid):
    spec = CHARACTERS[cid]; use_rig(spec['rig'])
    w = Writer(); nodes = []; node_of = {}
    for n in ORDER:
        parent, pos = J[n]; loc = pos if parent is None else sub(pos, P(parent))
        node_of[n] = len(nodes); nodes.append(dict(name=n, translation=[round(c, 6) for c in loc]))
    for n in ORDER:
        parent = J[n][0]
        if parent: nodes[node_of[parent]].setdefault('children', []).append(node_of[n])
    def add_node(parent, **kw):
        nodes.append(kw); i = len(nodes) - 1; nodes[parent].setdefault('children', []).append(i); return i
    attach_r = add_node(node_of['hand_r'], name='attach_hand_r', translation=list(spec['hand']))
    add_node(node_of['hand_l'], name='attach_hand_l', translation=list(spec['hand']))
    for k, (prims, pos) in spec['eyes']().items():
        mi = w.mesh(k, prims); add_node(node_of['head'], name=k, mesh=mi, translation=[round(c, 5) for c in pos])
    ibm = b''.join(struct.pack('<16f', 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, -P(n)[0], -P(n)[1], -P(n)[2], 1) for n in ORDER)
    skin = dict(name=cid + '_skin', inverseBindMatrices=w._accessor(ibm, 5126, len(ORDER), 'MAT4'), joints=[node_of[n] for n in ORDER], skeleton=node_of['root'])
    scene_nodes = [node_of['root']]
    def skinned(name, prims):
        mi = w.mesh(name, prims); nodes.append(dict(name=name, mesh=mi, skin=0)); scene_nodes.append(len(nodes) - 1)
    skinned('body', spec['body']())
    for oid, fn in spec['outfits'].items(): skinned('outfit_' + oid, fn())
    for pid in spec['props']:
        mi = w.mesh('prop_' + pid, PROPS[pid]()); add_node(attach_r, name='prop_' + pid, mesh=mi)
    mi = w.mesh('prop_umbrella', PROPS['umbrella']()); add_node(attach_r, name='prop_umbrella', mesh=mi)     # weather prop, not offered in the Studio
    star = accessory_star(spec['front'], -0.07)
    mi = w.mesh('acc_star', star); add_node(node_of['chest'], name='acc_star', mesh=mi)
    add_node(node_of['root'], name='sleep_blanket', mesh=w.mesh('sleep_blanket', sleep_blanket(spec['front'])))
    add_node(node_of['root'], name='sleep_pillow', mesh=w.mesh('sleep_pillow', sleep_pillow(spec['rig']['head'])))
    gltf = dict(asset=dict(version='2.0', generator='haven gen_character.py', copyright='Original work for Haven, MIT licence'),
                scene=0, scenes=[dict(nodes=scene_nodes)], nodes=nodes, skins=[skin], meshes=w.meshes, materials=w.materials,
                accessors=w.acc, bufferViews=w.views, buffers=[dict(byteLength=len(w.bin))])
    js = json.dumps(gltf, separators=(',', ':')).encode()
    while len(js) % 4: js += b' '
    bn = bytes(w.bin)
    while len(bn) % 4: bn += b'\0'
    return struct.pack('<4sII', b'glTF', 2, 12 + 8 + len(js) + 8 + len(bn)) + struct.pack('<I4s', len(js), b'JSON') + js + struct.pack('<I4s', len(bn), b'BIN\0') + bn

if __name__ == '__main__':
    out = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'app', 'src', 'main', 'assets', 'character'))
    os.makedirs(out, exist_ok=True)
    for cid in CHARACTERS:
        glb = build_character(cid); open(os.path.join(out, cid + '.glb'), 'wb').write(glb)
        print(cid, len(glb) // 1024, 'KiB')
