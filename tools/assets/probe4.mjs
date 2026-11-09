import { NodeIO } from '@gltf-transform/core';
import sharp from 'sharp';
const doc = await new NodeIO().read(process.argv[2]);
let tot=0;
for (const t of doc.getRoot().listTextures()) { const m = await sharp(Buffer.from(t.getImage())).metadata(); const mem=m.width*m.height*4*1.33/1048576; tot+=mem; console.log(t.getName()||t.getURI()||'tex', m.width+'x'+m.height, (t.getImage().length/1024).toFixed(0)+'KiB', t.getMimeType(), mem.toFixed(1)+'MiB'); }
console.log('materials', doc.getRoot().listMaterials().map(m=>m.getName()).join(','), 'total GPU est', tot.toFixed(1),'MiB');
let tris=0; for (const m of doc.getRoot().listMeshes()) for (const p of m.listPrimitives()) tris+=p.getIndices().getCount()/3; console.log('tris(all variants)',tris);
