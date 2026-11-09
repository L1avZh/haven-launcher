import { NodeIO } from '@gltf-transform/core';
const io = new NodeIO();
const doc = await io.read(process.argv[2]);
const root = doc.getRoot();
const skin = root.listSkins()[0];
const js = skin.listJoints();
const pos = (n) => { const m = n.getWorldMatrix(); return [m[12], m[13], m[14]].map(v => +v.toFixed(3)); };
for (const n of ['root','pelvis','spine_01','spine_02','spine_03','neck_01','Head','clavicle_l','upperarm_l','lowerarm_l','hand_l','thigh_l','calf_l','foot_l','ball_l','upperarm_r','hand_r'])
  { const j = js.find(x => x.getName() === n); console.log(n.padEnd(11), j ? pos(j) : 'missing', j ? 'parent=' + (j.getParentNode()?.getName() ?? 'scene') : ''); }
console.log('scene children:', root.listScenes()[0].listChildren().map(c => c.getName()));
for (const m of root.listMeshes()) for (const p of m.listPrimitives()) { const a = p.getAttribute('POSITION'); console.log('mesh', m.getName(), 'verts', a.getCount(), 'bbox', a.getMin([]).map(v=>+v.toFixed(2)), a.getMax([]).map(v=>+v.toFixed(2)), 'mat', p.getMaterial()?.getName()); }
const wm = js[0].getParentNode()?.getWorldMatrix(); console.log('root parent world', wm && Array.from(wm).map(v=>+v.toFixed(3)).join(','));
