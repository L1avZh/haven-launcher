// Builds assets/character/world.glb: props, bed, scenery and weather effects, from CC0 downloads (see docs/assets/README.md).
//   node build_world.mjs
import { NodeIO } from '@gltf-transform/core';
import { KHRMaterialsUnlit } from '@gltf-transform/extensions';
import { dedup, prune, mergeDocuments, unpartition } from '@gltf-transform/functions';
import sharp from 'sharp';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '../..');
const EX = path.join(ROOT, '.tmp-extract');
const PROPS = path.join(EX, 'props/Exports/glTF');
const NATURE = path.join(EX, 'nature/glTF');
const STAFF = path.join(EX, 'staff/SWS.glb');
const V1 = path.join(ROOT, 'assets-source/procedural-v1/human.glb');
const OUT = path.join(ROOT, 'app/src/main/assets/character/world.glb');

const io = new NodeIO().registerExtensions([KHRMaterialsUnlit]);
const doc = new (await import('@gltf-transform/core')).Document();
const buffer = doc.createBuffer();
const scene = doc.createScene('world');
const unlit = doc.createExtension(KHRMaterialsUnlit);

// ---------------------------------------------------------------- matrix helpers (column-major)
const mat = (s = 1, rx = 0, ry = 0, rz = 0, tx = 0, ty = 0, tz = 0) => {
  const cx = Math.cos(rx), sx = Math.sin(rx), cy = Math.cos(ry), sy = Math.sin(ry), cz = Math.cos(rz), sz = Math.sin(rz);
  // R = Rz * Ry * Rx
  const r = [cz*cy, cz*sy*sx - sz*cx, cz*sy*cx + sz*sx,  sz*cy, sz*sy*sx + cz*cx, sz*sy*cx - cz*sx,  -sy, cy*sx, cy*cx];
  return [r[0]*s, r[3]*s, r[6]*s, 0,  r[1]*s, r[4]*s, r[7]*s, 0,  r[2]*s, r[5]*s, r[8]*s, 0,  tx, ty, tz, 1];
};
const mulm = (a, b) => { const o = new Array(16).fill(0); for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) for (let k = 0; k < 4; k++) o[c*4+r] += a[k*4+r] * b[c*4+k]; return o; };
const apply = (m, p) => [m[0]*p[0]+m[4]*p[1]+m[8]*p[2]+m[12], m[1]*p[0]+m[5]*p[1]+m[9]*p[2]+m[13], m[2]*p[0]+m[6]*p[1]+m[10]*p[2]+m[14]];
const applyDir = (m, p) => { const v = [m[0]*p[0]+m[4]*p[1]+m[8]*p[2], m[1]*p[0]+m[5]*p[1]+m[9]*p[2], m[2]*p[0]+m[6]*p[1]+m[10]*p[2]]; const l = Math.hypot(...v) || 1; return v.map((x) => x / l); };

// ---------------------------------------------------------------- source import + baking
const cache = new Map();
async function source(file) {                       // load once, merge into the world document, return {prims,...}
  if (cache.has(file)) return cache.get(file);
  const src = await io.read(file);
  const before = new Set(doc.getRoot().listNodes()); const scenesBefore = new Set(doc.getRoot().listScenes());
  mergeDocuments(doc, src);
  const fresh = doc.getRoot().listNodes().filter((n) => !before.has(n));
  const prims = []; const named = {};
  for (const nd of fresh) { const m = nd.getMesh(); if (m) { named[nd.getName()] = m; for (const p of m.listPrimitives()) prims.push(p); } }
  for (const nd of fresh) nd.dispose();
  for (const s of doc.getRoot().listScenes()) if (!scenesBefore.has(s)) s.dispose();
  const r = { prims, named }; cache.set(file, r); return r;
}
/** Adds baked, transformed copies of the source primitives to a group's mesh. */
function bake(groupMesh, prims, m) {
  for (const p of prims) {
    const np = doc.createPrimitive().setMaterial(p.getMaterial()).setIndices(p.getIndices());
    for (const sem of p.listSemantics()) {
      const a = p.getAttribute(sem);
      if (sem === 'POSITION' || sem === 'NORMAL') {
        const c = a.clone(); const v = [0, 0, 0];
        for (let i = 0; i < c.getCount(); i++) { a.getElement(i, v); c.setElement(i, sem === 'POSITION' ? apply(m, v) : applyDir(m, v)); }
        np.setAttribute(sem, c);
      } else if (sem === 'TANGENT') { /* dropped: baked props use no tangent-space detail */ }
      else np.setAttribute(sem, a);
    }
    groupMesh.addPrimitive(np);
  }
}
function group(name) { const mesh = doc.createMesh(name); const nd = doc.createNode(name).setMesh(mesh); scene.addChild(nd); return { mesh, nd }; }

// ---------------------------------------------------------------- procedural bits that have no CC0 equivalent
const buf = (a, type, arr) => doc.createAccessor().setType(type).setArray(arr).setBuffer(buffer);
function meshFrom(name, pos, idx, material) {
  const p = doc.createPrimitive().setAttribute('POSITION', buf(0, 'VEC3', new Float32Array(pos))).setIndices(buf(0, 'SCALAR', new Uint16Array(idx))).setMaterial(material);
  const m = doc.createMesh(name).addPrimitive(p); return m;
}
const unlitMat = (name, rgba, alpha) => { const m = doc.createMaterial(name).setBaseColorFactor(rgba).setDoubleSided(true); m.setExtension('KHR_materials_unlit', unlit.createUnlit()); if (alpha) m.setAlphaMode('BLEND'); return m; };
function sphere(r, seg = 14, ring = 10) {
  const pos = [], idx = [];
  for (let j = 0; j <= ring; j++) { const th = Math.PI * j / ring; for (let i = 0; i <= seg; i++) { const ph = 2 * Math.PI * i / seg; pos.push(r * Math.sin(th) * Math.cos(ph), r * Math.cos(th), r * Math.sin(th) * Math.sin(ph)); } }
  for (let j = 0; j < ring; j++) for (let i = 0; i < seg; i++) { const a = j * (seg + 1) + i, b = a + seg + 1; idx.push(a, b, a + 1, a + 1, b, b + 1); }
  return { pos, idx };
}
function disc(r, h, seg = 28) {
  const pos = [], idx = []; pos.push(0, h, 0); for (let i = 0; i < seg; i++) { const a = 2 * Math.PI * i / seg; pos.push(r * Math.cos(a), h, r * Math.sin(a)); }
  for (let i = 0; i < seg; i++) idx.push(0, 1 + (i + 1) % seg, 1 + i);
  const base = pos.length / 3; for (let i = 0; i < seg; i++) { const a = 2 * Math.PI * i / seg; pos.push(r * Math.cos(a), h, r * Math.sin(a), r * Math.cos(a), 0, r * Math.sin(a)); }
  for (let i = 0; i < seg; i++) { const a = base + i * 2, b = base + ((i + 1) % seg) * 2; idx.push(a, a + 1, b, b, a + 1, b + 1); }
  return { pos, idx };
}
/** A column of small 3D drops (diamond bipyramids) or flakes (octahedra), repeating every `period` so the layer can scroll seamlessly. */
function dropLayer(name, material, count, period, seed, size, tall) {
  const pos = [], idx = []; let s = seed; const rnd = () => (s = (s * 1664525 + 1013904223) >>> 0) / 4294967296;
  const addDrop = (cx, cy, cz) => { const b = pos.length / 3; const w = size, h = size * tall;
    pos.push(cx, cy + h, cz, cx, cy - h, cz, cx + w, cy, cz, cx, cy, cz + w, cx - w, cy, cz, cx, cy, cz - w);
    for (const [x, y, z] of [[2,3,0],[3,4,0],[4,5,0],[5,2,0],[3,2,1],[4,3,1],[5,4,1],[2,5,1]]) idx.push(b + x, b + y, b + z); };
  for (let k = 0; k < count; k++) { const x = (rnd() - 0.5) * 1.5, y = rnd() * period, z = -0.9 + rnd() * 1.5; addDrop(x, y, z); addDrop(x, y + period, z); }
  return meshFrom(name, pos, idx, material);
}

// ---------------------------------------------------------------- assemble
async function build() {
  // ---- props: each is oriented, scaled and then shifted so its GRIP point is the node origin with the shaft along +y (the hand's long axis) ----
  // A prop's `orient` is its own matrix (no two props share an alignment); `grip(bounds)` picks the point the fist closes on, in the oriented frame.
  const bounds = (prims, m) => { const lo = [1e9, 1e9, 1e9], hi = [-1e9, -1e9, -1e9], v = [0, 0, 0];
    for (const p of prims) { const a = p.getAttribute('POSITION'); for (let i = 0; i < a.getCount(); i++) { const q = apply(m, a.getElement(i, v)); for (let k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], q[k]); hi[k] = Math.max(hi[k], q[k]); } } }
    return { lo, hi, c: lo.map((x, i) => (x + hi[i]) / 2), h: hi.map((x, i) => x - lo[i]) }; };
  const alongZ = (b, f) => [b.c[0], b.c[1], b.lo[2] + f * b.h[2]];          // a point f of the way along the shaft (now +z)
  const along = (b, f) => [b.c[0], b.lo[1] + f * b.h[1], b.c[2]];            // a point f of the way up the prop, on its axis
  const permute = (cols, s) => [cols[0][0]*s, cols[0][1]*s, cols[0][2]*s, 0, cols[1][0]*s, cols[1][1]*s, cols[1][2]*s, 0, cols[2][0]*s, cols[2][1]*s, cols[2][2]*s, 0, 0, 0, 0, 1];
  const spin = (m) => mulm(mat(1, Math.PI / 2, 0, 0), m);     // shaft from +y to the hand's +z (the knuckle row)
  const props = [
    // sword: blade up, held at the middle of the hilt
    ['prop_sword', path.join(PROPS, 'Sword_Bronze.gltf'), spin(mat(0.5)), () => [0, 0, -0.10 * 0.5]],
    // axe: the model's long axis is x with the head at -x; turn it upright, head up, and grip the handle a third of the way up
    ['prop_axe', path.join(PROPS, 'Axe_Bronze.gltf'), spin(mat(0.5, 0, 0, -Math.PI / 2)), (b) => alongZ(b, 0.30)],
    // candle: gripped by its stem, below the flame
    ['prop_candle', path.join(PROPS, 'CandleStick.gltf'), spin(mat(1.1)), (b) => alongZ(b, 0.30)],
    // book: its thickness along x, height along y, width along +z; held by the back edge of the spine, so it extends forward of the hand
    ['prop_book', path.join(PROPS, 'Book_7.gltf'), mat(0.8), (b) => [b.lo[0] + 0.015, b.c[1], b.c[2]]],
    // staff: planted beside the foot, gripped 42% up
    ['prop_staff', STAFF, spin(mat(0.8)), (b) => alongZ(b, 0.55)],
  ];
  for (const [name, file, orient, gripAt] of props) {
    const s = await source(file); const b = bounds(s.prims, orient); const gp = gripAt(b);
    const m = orient.slice(); m[12] -= gp[0]; m[13] -= gp[1]; m[14] -= gp[2];
    const g = group(name); bake(g.mesh, s.prims, m);
  }
  { // umbrella: Haven's own mesh from the v1 models (no CC0 umbrella in glTF form was found); the pole runs along -y from the handle, gripped just below the hook
    const s = await source(V1);
    const ms = doc.getRoot().listMeshes().filter((m) => /umbrella/i.test(m.getName())); const g = group('prop_umbrella'); for (const m of ms) bake(g.mesh, m.listPrimitives(), mulm(mat(1, -Math.PI / 2, 0, 0), mat(1, 0, 0, 0, 0, 0.08, 0))); }
  // ---- bed: the Quaternius twin bed, laid so its long side runs along x (the sleeper lies along x) ----
  { const s = await source(path.join(PROPS, 'Bed_Twin1.gltf')); const g = group('bed'); bake(g.mesh, s.prims, mat(1.0, 0, Math.PI / 2, 0, 0, 0, 0)); }
  // ---- scenery ----
  const N = (n) => path.join(NATURE, n + '.gltf');
  { const g = group('env_day');
    bake(g.mesh, (await source(N('CommonTree_1'))).prims, mat(0.14, 0, 0.6, 0, -0.33, 0, -0.42));
    bake(g.mesh, (await source(N('Bush_Common_Flowers'))).prims, mat(0.2, 0, 0.4, 0, 0.3, 0, -0.22));
    const grass = (await source(N('Grass_Common_Tall'))).prims;
    for (const [x, z, s, r] of [[-0.2, 0.2, 0.16, 0.2], [0.22, 0.22, 0.14, 1.1], [0.4, 0.04, 0.16, 2.0], [-0.38, 0.0, 0.14, 0.7]]) bake(g.mesh, grass, mat(s, 0, r, 0, x, 0, z));
    bake(g.mesh, (await source(N('Flower_3_Group'))).prims, mat(0.1, 0, 0.3, 0, 0.12, 0, 0.3));
    const peb = (await source(N('Pebble_Round_1'))).prims; bake(g.mesh, peb, mat(0.4, 0, 0.5, 0, -0.14, 0, 0.3)); bake(g.mesh, peb, mat(0.3, 0, 2.1, 0, 0.1, 0, 0.34)); }
  { const g = group('env_snow');
    const pine = (await source(N('Pine_1'))).prims;
    bake(g.mesh, pine, mat(0.12, 0, 0.3, 0, -0.33, 0, -0.42)); bake(g.mesh, pine, mat(0.1, 0, 1.4, 0, 0.33, 0, -0.4));
    bake(g.mesh, (await source(N('Pebble_Round_1'))).prims, mat(0.4, 0, 0.5, 0, 0.18, 0, 0.3)); }
  // ground (grass, snow), sun, moon: Haven's own plain shapes
  const lit = (name, rgb, rough) => doc.createMaterial(name).setBaseColorFactor([...rgb, 1]).setMetallicFactor(0).setRoughnessFactor(rough);
  for (const [name, rgb] of [['ground_grass', [0.30, 0.52, 0.24]], ['ground_snow', [0.92, 0.94, 0.98]]]) { const d = disc(0.5, 0.03); const n = doc.createNode(name).setMesh(meshFrom(name, d.pos, d.idx, lit(name, rgb, 1))).setTranslation([0, 0, -0.1]); scene.addChild(n); }
  { const s = sphere(0.11); scene.addChild(doc.createNode('env_sun').setMesh(meshFrom('env_sun', s.pos, s.idx, unlitMat('sun', [1.0, 0.62, 0.12, 1]))).setTranslation([0.34, 1.85, -0.9])); }
  { const s = sphere(0.1); scene.addChild(doc.createNode('env_moon').setMesh(meshFrom('env_moon', s.pos, s.idx, unlitMat('moon', [0.86, 0.9, 1.0, 1]))).setTranslation([0.34, 1.85, -0.9])); }
  // weather: three scrolling layers each of 3D drops and flakes
  const rainMat = unlitMat('rain', [0.72, 0.84, 1.0, 0.55], true), snowMat = unlitMat('snow', [1, 1, 1, 0.9], true);
  for (let i = 0; i < 2; i++) scene.addChild(doc.createNode('fx_rain_' + i).setMesh(dropLayer('fx_rain_' + i, rainMat, 46, 2.4, 11 + i * 7, 0.006, 5.5)));
  for (let i = 0; i < 2; i++) scene.addChild(doc.createNode('fx_snow_' + i).setMesh(dropLayer('fx_snow_' + i, snowMat, 40, 2.4, 31 + i * 5, 0.011, 1)));

  // The source trims are fully metallic; under Haven's soft ambient light that renders black, so metal is toned down.
  for (const m of doc.getRoot().listMaterials()) if (m.getMetallicFactor() > 0.4 && !m.getExtension('KHR_materials_unlit')) m.setMetallicFactor(0.3).setRoughnessFactor(Math.max(m.getRoughnessFactor(), 0.6));
  // ---- textures: shrink for the TV box ----
  for (const t of doc.getRoot().listTextures()) {
    let img = sharp(Buffer.from(t.getImage())); const meta = await img.metadata();
    const isNormal = /Normal|ORM/.test(t.getURI() + t.getName());
    const size = isNormal ? 256 : 512;
    if (meta.width > size) img = img.resize(size, size);
    const alpha = meta.hasAlpha && /Leaf|Leaves|Grass|Flower|Petal/i.test(t.getURI() + t.getName());
    if (alpha) t.setImage(new Uint8Array(await img.png({ palette: true, quality: 80 }).toBuffer())).setMimeType('image/png');
    else t.setImage(new Uint8Array(await img.removeAlpha().jpeg({ quality: 82, mozjpeg: true }).toBuffer())).setMimeType('image/jpeg');
  }
  await doc.transform(dedup(), prune({ keepLeaves: true }), unpartition());
  const glb = await io.writeBinary(doc); fs.writeFileSync(OUT, glb);
  console.log('wrote world.glb', (glb.length / 1024).toFixed(0), 'KiB');
}
await build();
