import { NodeIO } from '@gltf-transform/core';
import { KHRMaterialsUnlit } from '@gltf-transform/extensions';
const doc = await new NodeIO().registerExtensions([KHRMaterialsUnlit]).read('../../app/src/main/assets/character/world.glb');
for (const nd of doc.getRoot().listNodes()) { const m = nd.getMesh(); if (!m || !/^prop_/.test(nd.getName())) continue;
  console.log(nd.getName(), m.listPrimitives().map(p => { const a = p.getAttribute('POSITION'); return p.getMaterial().getName() + ' ' + a.getCount() + ' lo' + a.getMin([]).map(v=>+v.toFixed(2)) + ' hi' + a.getMax([]).map(v=>+v.toFixed(2)); }).join(' | ')); }
