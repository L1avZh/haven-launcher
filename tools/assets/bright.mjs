import sharp from 'sharp';
for (const f of process.argv.slice(2)) { const s = await sharp(f).stats(); console.log(f.split('/').pop().padEnd(24), s.channels.map(c=>c.mean|0).join(',')); }
