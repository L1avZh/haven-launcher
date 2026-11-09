import sharp from 'sharp';
const [out, ...ins] = process.argv.slice(2);
const tiles = await Promise.all(ins.map(f => sharp(f).resize(960, 540).toBuffer()));
await sharp({ create: { width: 960 * tiles.length, height: 540, channels: 3, background: '#000' } }).composite(tiles.map((b, i) => ({ input: b, left: i * 960, top: 0 }))).png().toFile(out);
