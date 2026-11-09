import sharp from 'sharp';
const [out, ...ins] = process.argv.slice(2);
const tiles = await Promise.all(ins.map(f => sharp(f).extract({ left: 0, top: 300, width: 700, height: 560 }).resize(560, 448).toBuffer()));
await sharp({ create: { width: 560 * tiles.length, height: 448, channels: 3, background: '#000' } }).composite(tiles.map((b, i) => ({ input: b, left: i * 560, top: 0 }))).png().toFile(out);
