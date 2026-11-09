// Builds Haven's runtime character GLBs from the CC0 Quaternius downloads in assets-source/ (see docs/assets/README.md).
//   node build.mjs [human|wizard|robot ...]
import { NodeIO } from '@gltf-transform/core';
import { dedup, prune, mergeDocuments, unpartition } from '@gltf-transform/functions';
import sharp from 'sharp';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import { I4, mul, T, pos, sub, norm, xformPoint, xformDir, rotFromTo, about } from './lib.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '../..');
const EX = path.join(ROOT, '.tmp-extract');                      // where assets-source/quaternius/*.zip are unpacked (see docs/assets/README.md)
const UBC = path.join(EX, 'Universal Base Characters[Standard]');
const OUT = path.join(ROOT, 'app/src/main/assets/character');
const BASE = path.join(UBC, 'Base Characters/Godot - UE/Superhero_Male_FullBody.gltf');
const HAIR = (n) => path.join(UBC, `Hairstyles/Rigged to Head Bone/glTF (Godot -Unreal)/${n}.gltf`);
const OUTFIT = (n) => path.join(EX, `outfits/Modular Character Outfits - Fantasy[Standard]/Exports/glTF (Godot-Unreal)/Outfits/${n}.gltf`);
const OLD = (id) => path.join(ROOT, `assets-source/procedural-v1/${id}.glb`);

// Haven joint name -> Quaternius (UE mannequin style) joint name. Everything else (fingers, extra spine bones, toes) rides along.
const JOINTS = { hips: 'pelvis', spine: 'spine_01', chest: 'spine_03', neck: 'neck_01', head: 'Head',
  clavicle_l: 'clavicle_l', clavicle_r: 'clavicle_r', upperarm_l: 'upperarm_l', upperarm_r: 'upperarm_r',
  forearm_l: 'lowerarm_l', forearm_r: 'lowerarm_r', hand_l: 'hand_l', hand_r: 'hand_r',
  thigh_l: 'thigh_l', thigh_r: 'thigh_r', shin_l: 'calf_l', shin_r: 'calf_r', foot_l: 'foot_l', foot_r: 'foot_r' };
const ARM_OUT = 7 * Math.PI / 180;                              // arms hang a little away from the body
const TEX = { color: 1024, normal: 512, mr: 512, hair: 512, eye: 256 };

const io = new NodeIO();
/** Some Quaternius glTF files reference "X_png.png" although only "X.png" ships; make the copy the loader expects. */
function fixExportQuirk() {
  for (const dir of [path.dirname(BASE)]) for (const f of fs.readdirSync(dir)) {
    const m = f.match(/^(T_.*)\.png$/); if (!m || m[1].endsWith('_png')) continue;
    const alt = path.join(dir, m[1] + '_png.png'); if (!fs.existsSync(alt)) fs.copyFileSync(path.join(dir, f), alt);
  }
}
fixExportQuirk();
const log = (...a) => console.log(...a);

// ---------------------------------------------------------------- skeleton helpers
function jointIndex(skin) { const m = new Map(); skin.listJoints().forEach((j, i) => m.set(j.getName(), i)); return m; }

/** Re-points a part's JOINTS_0 at the base skeleton's joint order (same rig, different file). */
function remapJoints(part, baseIdx) {
  const skin = part.getRoot().listSkins()[0];
  const names = skin.listJoints().map((j) => j.getName());
  const miss = names.filter((n) => !baseIdx.has(n)); if (miss.length) throw new Error('unknown joints ' + miss.slice(0, 3));
  const seen = new Set();
  for (const mesh of part.getRoot().listMeshes()) for (const p of mesh.listPrimitives()) {
    const a = p.getAttribute('JOINTS_0'); if (!a || seen.has(a)) continue; seen.add(a);
    const e = [0, 0, 0, 0];
    for (let i = 0; i < a.getCount(); i++) { a.getElement(i, e); a.setElement(i, e.map((v) => baseIdx.get(names[v]))); }
  }
}

/** Bakes the T-pose into an "arms hanging" bind pose and gives every joint an identity rotation, so Haven's pose deltas act on world axes. */
function neutralize(doc) {
  const skin = doc.getRoot().listSkins()[0];
  const joints = skin.listJoints(); const n = joints.length;
  const W = joints.map((j) => Float32Array.from(j.getWorldMatrix()));
  const IBM = skin.getInverseBindMatrices().getArray();
  const byName = new Map(joints.map((j, i) => [j.getName(), i]));
  const parentOf = joints.map((j) => joints.indexOf(j.getParentNode()));
  const Q = joints.map(() => I4());
  const subtree = (i) => { const out = [i]; joints.forEach((_, k) => { let p = parentOf[k]; while (p >= 0) { if (p === i) { out.push(k); break; } p = parentOf[p]; } }); return out; };
  const apply = (i, rigid) => subtree(i).forEach((k) => { Q[k] = mul(rigid, Q[k]); });
  for (const s of ['l', 'r']) {
    const ua = byName.get('upperarm_' + s), la = byName.get('lowerarm_' + s), hd = byName.get('hand_' + s);
    const side = pos(W[ua])[0] > 0 ? 1 : -1;
    const target = norm([side * Math.sin(ARM_OUT), -Math.cos(ARM_OUT), 0]);
    const pUa = pos(W[ua]);
    const r1 = about(pUa, rotFromTo(norm(sub(pos(W[la]), pUa)), target)); apply(ua, r1);
    const pLa = xformPoint(r1, pos(W[la])), pHd = xformPoint(r1, pos(W[hd]));
    const r2 = about(pLa, rotFromTo(norm(sub(pHd, pLa)), target)); apply(la, r2);
  }
  const M = joints.map((_, i) => mul(mul(Q[i], W[i]), IBM.subarray(i * 16, i * 16 + 16)));   // rest -> new pose, rigid per joint
  const P = joints.map((_, i) => mul(Q[i], W[i]));
  // re-skin every skinned primitive into the new pose
  const done = new Set();
  const nodes = doc.getRoot().listNodes().filter((nd) => nd.getMesh() && nd.getSkin());
  for (const nd of nodes) for (const p of nd.getMesh().listPrimitives()) {
    const P0 = p.getAttribute('POSITION'); if (done.has(P0)) continue; done.add(P0);
    const J = p.getAttribute('JOINTS_0'), Wt = p.getAttribute('WEIGHTS_0'), N = p.getAttribute('NORMAL'), TG = p.getAttribute('TANGENT');
    if (p.listTargets().length) throw new Error('morph targets not supported');
    const v = [0, 0, 0], j4 = [0, 0, 0, 0], w4 = [0, 0, 0, 0], t4 = [0, 0, 0, 0];
    for (let i = 0; i < P0.getCount(); i++) {
      P0.getElement(i, v); J.getElement(i, j4); Wt.getElement(i, w4);
      const op = [0, 0, 0]; const on = [0, 0, 0]; const ot = [0, 0, 0];
      const nv = N ? N.getElement(i, [0, 0, 0]) : null; const tv = TG ? TG.getElement(i, t4) : null;
      for (let k = 0; k < 4; k++) { const w = w4[k]; if (!w) continue; const m = M[j4[k]];
        const a = xformPoint(m, v); op[0] += a[0]*w; op[1] += a[1]*w; op[2] += a[2]*w;
        if (nv) { const b = xformDir(m, nv); on[0] += b[0]*w; on[1] += b[1]*w; on[2] += b[2]*w; }
        if (tv) { const c = xformDir(m, tv); ot[0] += c[0]*w; ot[1] += c[1]*w; ot[2] += c[2]*w; } }
      P0.setElement(i, op); if (nv) { const u = norm(on); N.setElement(i, u); } if (tv) { const u = norm(ot); TG.setElement(i, [u[0], u[1], u[2], t4[3]]); }
    }
  }
  // joints: translation-only world poses, identity rotation, matching inverse bind matrices
  const wp = P.map((m) => pos(m));
  joints.forEach((j, i) => { const pi = parentOf[i]; const d = pi >= 0 ? sub(wp[i], wp[pi]) : wp[i];
    j.setTranslation(d.map((x) => +x.toFixed(6))); j.setRotation([0, 0, 0, 1]); j.setScale([1, 1, 1]); });
  const ibm = skin.getInverseBindMatrices(); const arr = new Float32Array(n * 16);
  joints.forEach((_, i) => arr.set(T(-wp[i][0], -wp[i][1], -wp[i][2]), i * 16)); ibm.setArray(arr);
  // anything above the root joint must not move the character
  const rootJoint = joints[parentOf.indexOf(-1)];
  for (let a = rootJoint.getParentNode(); a; a = a.getParentNode()) { a.setTranslation([0, 0, 0]); a.setRotation([0, 0, 0, 1]); a.setScale([1, 1, 1]); }
  for (const nd of doc.getRoot().listNodes()) if (nd.getSkin()) { nd.setTranslation([0, 0, 0]); nd.setRotation([0, 0, 0, 1]); nd.setScale([1, 1, 1]); }
  return { wp, byName };
}

// ---------------------------------------------------------------- scene assembly
function rename(doc) {
  const skin = doc.getRoot().listSkins()[0];
  for (const j of skin.listJoints()) for (const [h, q] of Object.entries(JOINTS)) if (j.getName() === q) j.setName(h);
}

/** Moves the named meshes of a part into one node (one renderable) called nodeName, bound to the base skin. */
function groupNode(doc, skin, sceneRoot, nodeName, prims) {
  const mesh = doc.createMesh(nodeName); for (const p of prims) mesh.addPrimitive(p);
  const nd = doc.createNode(nodeName).setMesh(mesh).setSkin(skin); sceneRoot.addChild(nd); return nd;
}

/** Culls base-body triangles below `minY` (the outfit supplies everything under the neck) and returns the head primitive. */
function cullBelow(doc, prim, minY) {
  const P = prim.getAttribute('POSITION'); const idx = prim.getIndices(); const ia = idx.getArray();
  const keep = []; const v = [0, 0, 0];
  const ymax = (i) => P.getElement(i, v)[1];
  for (let t = 0; t < ia.length; t += 3) { if (Math.max(ymax(ia[t]), ymax(ia[t + 1]), ymax(ia[t + 2])) >= minY) keep.push(ia[t], ia[t + 1], ia[t + 2]); }
  idx.setArray(ia instanceof Uint32Array ? Uint32Array.from(keep) : Uint16Array.from(keep));
  log('  culled body triangles', ia.length / 3, '->', keep.length / 3);
}

async function loadPart(file, baseIdx) { const d = await io.read(file); remapJoints(d, baseIdx); return d; }

/** Merges a part into the base document and returns the primitives of its meshes whose node name matches `filter`; the part's own nodes are discarded. */
function importPrims(doc, part, filter) {
  const before = new Set(doc.getRoot().listNodes()); const scenesBefore = new Set(doc.getRoot().listScenes());
  mergeDocuments(doc, part);
  const fresh = doc.getRoot().listNodes().filter((n) => !before.has(n));
  const prims = [];
  for (const nd of fresh) { const m = nd.getMesh(); if (m && filter(nd.getName())) for (const p of m.listPrimitives()) prims.push(p); }
  for (const nd of fresh) nd.dispose();
  for (const s of doc.getRoot().listScenes()) if (!scenesBefore.has(s)) s.dispose();
  return prims;
}

// ---------------------------------------------------------------- textures
async function shrink(tex, size, jpegQ = 86, tint = null) {
  if (!tex || tex.__done) return;
  let img = sharp(Buffer.from(tex.getImage()));
  const meta = await img.metadata();
  if (meta.width > size) img = img.resize(size, size, { kernel: 'lanczos3' });
  if (tint?.modulate) img = img.modulate(tint.modulate);
  if (tint?.tint) img = img.tint(tint.tint);
  img = img.removeAlpha().jpeg({ quality: jpegQ, mozjpeg: true });
  tex.setImage(new Uint8Array(await img.toBuffer())).setMimeType('image/jpeg'); tex.__done = true;
}
async function shrinkAll(doc, tints = {}) {
  for (const m of doc.getRoot().listMaterials()) {
    const t = tints[m.getName()];
    await shrink(m.getBaseColorTexture(), m.getName().includes('Hair') ? TEX.hair : m.getName().includes('Eyes') ? TEX.eye : TEX.color, 86, t || null);
    await shrink(m.getNormalTexture(), m.getName().includes('Eyes') ? TEX.eye : m.getName().includes('Hair') ? 256 : TEX.normal, 88);
    await shrink(m.getMetallicRoughnessTexture(), TEX.mr, 85);
  }
}

// ---------------------------------------------------------------- Haven extras (props, hands, sleep set) from the v1 models
async function addExtras(doc, id, headY) {
  const old = await io.read(OLD(id));
  const names = new Set(['prop_lantern', 'prop_tablet', 'prop_compass', 'prop_staff', 'prop_umbrella', 'sleep_blanket', 'sleep_pillow', 'acc_star']);
  const oldHead = old.getRoot().listNodes().find((n) => n.getName() === 'head').getWorldMatrix()[13];
  const before = new Set(doc.getRoot().listNodes()); const scenesBefore = new Set(doc.getRoot().listScenes());
  mergeDocuments(doc, old);
  const fresh = doc.getRoot().listNodes().filter((n) => !before.has(n));
  const skin = doc.getRoot().listSkins()[0]; const J = (n) => skin.listJoints().find((j) => j.getName() === n);
  const handR = J('hand_r'), handL = J('hand_l'), chest = J('chest');
  const mk = (name, parent, t) => { const nd = doc.createNode(name).setTranslation(t); parent.addChild(nd); return nd; };
  const attachR = mk('attach_hand_r', handR, [0, -0.085, 0.035]); mk('attach_hand_l', handL, [0, -0.085, 0.035]);
  const rootJoint = J('hips').getParentNode();
  for (const nd of fresh.filter((n) => names.has(n.getName()))) {
    const nn = nd.getName(); const parent = nn === 'acc_star' ? chest : nn.startsWith('sleep_') ? rootJoint : attachR;
    const copy = doc.createNode(nn).setMesh(nd.getMesh());
    if (nn === 'acc_star') copy.setTranslation([0, 0.01, 0.11]);
    if (nn === 'sleep_pillow') copy.setTranslation([0, headY - oldHead, 0]);
    parent.addChild(copy);
  }
  // drop the v1 body, skeleton and scene that came along with the merge
  for (const nd of fresh) nd.dispose();
  for (const s of doc.getRoot().listScenes()) if (!scenesBefore.has(s)) s.dispose();
  return { oldHead };
}

// ---------------------------------------------------------------- per character
async function build(id) {
  log('==', id);
  const doc = await io.read(BASE);
  const root = doc.getRoot(); const scene = root.listScenes()[0];
  const baseIdx = jointIndex(root.listSkins()[0]);
  const skin = root.listSkins()[0];
  const meshNodes = Object.fromEntries(root.listNodes().filter((n) => n.getMesh()).map((n) => [n.getMesh().getName(), n]));
  const bodyMesh = root.listMeshes().find((m) => m.getName().startsWith('Sphere'));
  const brows = root.listMeshes().find((m) => m.getName() === 'Face'); const eyes = root.listMeshes().find((m) => m.getName() === 'Face.001');
  const sceneRoot = scene;  // Armature + part nodes live at the scene top
  const optionalGroups = {};
  const tints = {};

  const addGroup = (name, prims) => { optionalGroups[name] = groupNode(doc, skin, sceneRoot, name, prims); };
  const outfitPrims = async (file, filt) => { const part = await loadPart(file, baseIdx); return importPrims(doc, part, filt); };
  const hairPrims = (n) => outfitPrims(HAIR(n), () => true);

  if (id === 'robot') {
    // armored android: the base head plus the Ranger armor pieces (no hood), every surface replaced by solid metal; the eyes glow.
    // Two finishes are the two "outfits". No bare-body mesh is used.
    cullBelow(doc, bodyMesh.listPrimitives()[0], 1.5);
    const headP = bodyMesh.listPrimitives()[0];
    const armor = await outfitPrims(OUTFIT('Male_Ranger'), (n) => !/Hood/.test(n));
    const mk = (name, base, met, rough, nm) => { const m = doc.createMaterial(name).setBaseColorFactor(base).setMetallicFactor(met).setRoughnessFactor(rough); if (nm) m.setNormalTexture(nm); return m; };
    const finishes = { outfit_plated: mk('Robot_Plated', [0.80, 0.82, 0.86, 1], 0.55, 0.38), outfit_stealth: mk('Robot_Stealth', [0.10, 0.11, 0.13, 1], 0.5, 0.45) };
    const skinNormal = headP.getMaterial().getNormalTexture();
    for (const [name, mat] of Object.entries(finishes)) {
      const copies = [headP, ...armor].map((src) => { const p = doc.createPrimitive(); for (const sem of src.listSemantics()) p.setAttribute(sem, src.getAttribute(sem)); p.setIndices(src.getIndices()).setMaterial(mat); return p; });
      addGroup(name, copies);
    }
    meshNodes[bodyMesh.getName()].dispose();
    meshNodes['Face'].dispose();                                  // no eyebrows on a machine
    const em = eyes.listPrimitives()[0].getMaterial(); em.setEmissiveFactor([0.35, 0.95, 1.0]); em.setEmissiveTexture(em.getBaseColorTexture()); em.setBaseColorFactor([0.2, 0.2, 0.2, 1]);
  } else {
    const headCut = 1.5;                                          // neck base: the outfit covers everything below
    cullBelow(doc, bodyMesh.listPrimitives()[0], headCut);
    if (id === 'human') {
      addGroup('outfit_city', [...await outfitPrims(OUTFIT('Male_Peasant'), () => true), ...await hairPrims('Hair_SimpleParted'), ...await hairPrims('Hair_Beard')]);
      addGroup('outfit_field', [...await outfitPrims(OUTFIT('Male_Ranger'), () => true), ...await hairPrims('Hair_Beard')]);
    } else {
      addGroup('outfit_archmage', [...await outfitPrims(OUTFIT('Male_Ranger'), () => true), ...await hairPrims('Hair_Beard')]);
      addGroup('outfit_apprentice', [...await outfitPrims(OUTFIT('Male_Peasant'), () => true), ...await hairPrims('Hair_Long'), ...await hairPrims('Hair_Beard')]);
    }
  }
  if (id === 'wizard') {                       // robes in deep indigo (hue-rotated leather and cloth), keeping the source detail
    tints.MI_Ranger = { modulate: { hue: 135, saturation: 1.05, brightness: 0.95 } };
    tints.MI_Peasant = { tint: { r: 112, g: 126, b: 190 } };
  }
  neutralize(doc); rename(doc);
  const headY = skin.listJoints().find((j) => j.getName() === 'head').getWorldMatrix()[13];
  await addExtras(doc, id, headY);
  // nodes in the base file that are no longer needed, and unused resources
  await doc.transform(dedup(), prune({ keepLeaves: true }), unpartition());
  await shrinkAll(doc, tints);
  fs.mkdirSync(OUT, { recursive: true });
  const glb = await io.writeBinary(doc); fs.writeFileSync(path.join(OUT, id + '.glb'), glb);
  log('  wrote', id + '.glb', (glb.length / 1024).toFixed(0), 'KiB');
}

const want = process.argv.slice(2); for (const id of (want.length ? want : ['human', 'wizard', 'robot'])) await build(id);
