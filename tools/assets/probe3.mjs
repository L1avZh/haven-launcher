import { NodeIO } from '@gltf-transform/core';
const doc = await new NodeIO().read(process.argv[2]);
const dim = (t)=>{ try{ return t.getSize()?.join('x') }catch(e){return '?'} };
for (const m of doc.getRoot().listMaterials()) {
  const t = (x)=> x ? (x.getURI()+' '+dim(x)) : '-';
  console.log(m.getName(), 'mode', m.getAlphaMode(), 'ds', m.getDoubleSided(), 'bc', m.getBaseColorFactor().map(v=>+v.toFixed(2)), 'met', m.getMetallicFactor(), 'rough', m.getRoughnessFactor(), '\n   base', t(m.getBaseColorTexture()), '\n   mr  ', t(m.getMetallicRoughnessTexture()), '\n   norm', t(m.getNormalTexture()), 'em', m.getEmissiveFactor(), t(m.getEmissiveTexture()), 'occ', t(m.getOcclusionTexture()));
}
console.log(doc.getRoot().listExtensionsUsed().map(e=>e.extensionName));
