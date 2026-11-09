import { NodeIO } from '@gltf-transform/core';
const doc = await new NodeIO().read(process.argv[2]);
for (const n of doc.getRoot().listNodes()) {
  const nm = n.getName();
  if (/^(prop_|sleep_|acc_|attach_|eye_|hand_|chest|body|root|hips|outfit_)/.test(nm)) {
    const p = n.getParentNode()?.getName() ?? 'scene';
    console.log(nm.padEnd(16), 'parent', p.padEnd(14), 't', n.getTranslation().map(v=>+v.toFixed(3)).join(','), 'mesh', !!n.getMesh(), 'skin', !!n.getSkin());
  }
}
