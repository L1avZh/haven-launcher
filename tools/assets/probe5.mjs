import { NodeIO } from '@gltf-transform/core';
const io = new NodeIO();
for (const f of process.argv.slice(2)) {
  const doc = await io.read(f); const r = doc.getRoot();
  let tris = 0, mn = [1e9,1e9,1e9], mx = [-1e9,-1e9,-1e9];
  for (const m of r.listMeshes()) for (const p of m.listPrimitives()) { tris += p.getIndices().getCount() / 3; const a = p.getAttribute('POSITION'); const lo = a.getMin([]), hi = a.getMax([]); for (let i=0;i<3;i++){mn[i]=Math.min(mn[i],lo[i]);mx[i]=Math.max(mx[i],hi[i]);} }
  console.log(f.split('/').pop().padEnd(18), 'tris', tris, 'size', mx.map((v,i)=>+(v-mn[i]).toFixed(2)).join('x'), 'min', mn.map(v=>+v.toFixed(2)).join(','), 'mats', r.listMaterials().map(m=>m.getName()).join('+'), 'nodes', r.listNodes().map(n=>n.getName()).join(','));
}
