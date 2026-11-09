import { NodeIO } from '@gltf-transform/core';
const io = new NodeIO();
const doc = await io.read('../../app/src/main/assets/character/human.glb');
const nodes = doc.getRoot().listNodes();
const W = (n) => { const m = n.getWorldMatrix(); return [m[12], m[13], m[14]].map(v => +v.toFixed(3)); };
for (const n of ['hand_r','thumb_01_r','thumb_02_r','thumb_03_r','thumb_04_leaf_r','index_01_r','index_02_r','index_03_r','index_04_leaf_r','middle_01_r','middle_03_r','middle_04_leaf_r','pinky_01_r','pinky_04_leaf_r','attach_hand_r','hand_l','middle_01_l','attach_hand_l','forearm_r','upperarm_r','head','hips'])
  { const x = nodes.find(k => k.getName() === n); console.log(n.padEnd(18), x ? W(x) : 'missing'); }
const bed = await io.read('../../app/src/main/assets/character/world.glb');
for (const nd of bed.getRoot().listNodes().filter(n => n.getName() === 'bed')) for (const p of nd.getMesh().listPrimitives()) { const a = p.getAttribute('POSITION'); console.log('bed prim', p.getMaterial().getName(), 'min', a.getMin([]).map(v=>+v.toFixed(2)), 'max', a.getMax([]).map(v=>+v.toFixed(2))); }
