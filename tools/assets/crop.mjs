import sharp from 'sharp';
const [out, ...ins] = process.argv.slice(2);
const tiles = await Promise.all(ins.map(f => sharp(f).extract({ left: 40, top: 100, width: 520, height: 640 }).toBuffer()));
await sharp({ create: { width: 520 * tiles.length, height: 640, channels: 3, background: '#000' } }).composite(tiles.map((b, i) => ({ input: b, left: i * 520, top: 0 }))).png().toFile(out);
