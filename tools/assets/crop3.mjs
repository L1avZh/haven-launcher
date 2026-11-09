import sharp from 'sharp';
const [out, ...ins] = process.argv.slice(2);
const tiles = await Promise.all(ins.map(f => sharp(f).extract({ left: 20, top: 90, width: 640, height: 960 }).resize(480, 720).toBuffer()));
await sharp({ create: { width: 480 * tiles.length, height: 720, channels: 3, background: '#000' } }).composite(tiles.map((b, i) => ({ input: b, left: i * 480, top: 0 }))).png().toFile(out);
