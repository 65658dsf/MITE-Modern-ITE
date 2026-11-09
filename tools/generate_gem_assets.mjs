/** Package the approved B artwork from its indexed raster source. Author: NingMeng123. */
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { deflateSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { gemBoxModel } from './gem_box_model.mjs';
import { gemShineFrames, gemShineMetadata } from './gem_shine.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const source = JSON.parse(await readFile(new URL('./gem_textures/classic_b.json', import.meta.url), 'utf8'));
const output = path.join(root, 'src/main/resources/assets/modern_ite/textures/item');
await mkdir(output, { recursive: true });
const checkOnly = process.argv.includes('--check');
async function save(filename, contents) {
  if (checkOnly) {
    if (!(await readFile(filename)).equals(Buffer.from(contents))) {
      throw new Error(`Generated gem resource differs: ${filename}`);
    }
  } else {
    await writeFile(filename, contents);
  }
}

const crcTable = Array.from({ length: 256 }, (_, n) => {
  for (let i = 0; i < 8; i++) n = n & 1 ? 0xedb88320 ^ (n >>> 1) : n >>> 1;
  return n >>> 0;
});

function chunk(type, data) {
  const name = Buffer.from(type, 'ascii');
  let crc = 0xffffffff;
  for (const byte of Buffer.concat([name, data])) crc = crcTable[(crc ^ byte) & 255] ^ (crc >>> 8);
  const length = Buffer.alloc(4), checksum = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  checksum.writeUInt32BE((crc ^ 0xffffffff) >>> 0);
  return Buffer.concat([length, name, data, checksum]);
}

function png(rank, palette) {
  const pixels = rank.pixels;
  const size = source.logical_size;
  if (pixels.length !== size || pixels.some(row => row.length !== size || /[^.0-7]/.test(row))) {
    throw new Error('Gem source must contain sixteen rows of sixteen palette indices.');
  }
  if (palette.length !== 8 || palette.some(color => !/^#[0-9a-f]{6}$/i.test(color))) {
    throw new Error('Every gem palette must have eight RGB swatches.');
  }
  const frames = gemShineFrames(pixels, palette, rank.rank);
  const height = size * frames.length;
  const scanlines = Buffer.alloc((size * 4 + 1) * height);
  for (let frame = 0; frame < frames.length; frame++) {
    for (let y = 0; y < size; y++) {
      frames[frame].copy(scanlines, (frame * size + y) * (size * 4 + 1) + 1,
        y * size * 4, (y + 1) * size * 4);
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(size, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8; // Eight-bit RGBA; each native animation frame remains 16x16.
  header[9] = 6;
  return { frameCount: frames.length, data: Buffer.concat([
    Buffer.from('89504e470d0a1a0a', 'hex'), chunk('IHDR', header),
    chunk('tEXt', Buffer.from('Author\0NingMeng123')),
    chunk('tEXt', Buffer.from('Description\0Classic B gem; 16x16 frames; lower-right to upper-left sheen and tip sparkle')),
    chunk('IDAT', deflateSync(scanlines, { level: 9 })), chunk('IEND', Buffer.alloc(0)),
  ]) };
}

if (source.logical_size !== 16 || source.ranks.length !== 6) throw new Error('Invalid classic B source.');
let count = 0;
for (const [kind, palette] of Object.entries(source.palettes)) {
  for (const rank of source.ranks) {
    if (kind === 'shard' && rank.rank === 1) continue;
    const id = `gem_${kind}_${rank.rank}`;
    const { data, frameCount } = png(rank, palette);
    await save(path.join(output, `${id}.png`), data);
    await save(path.join(output, `${id}.png.mcmeta`),
      JSON.stringify(gemShineMetadata(id, frameCount), null, 2) + '\n');
    count++;
  }
}
if (count !== 65) throw new Error(`Expected 65 gem textures, received ${count}.`);
const box = JSON.parse(await readFile(new URL('./gem_textures/gem_box.json', import.meta.url), 'utf8'));
const boxPixels = Buffer.from(box.rgba_base64, 'base64');
if (box.width !== 32 || box.height !== 32 || boxPixels.length !== 32 * 32 * 4) throw new Error('Invalid gem box source.');
const boxScanlines = Buffer.alloc(32 * (32 * 4 + 1));
for (let y = 0; y < 32; y++) boxPixels.copy(boxScanlines, y * 129 + 1, y * 128, (y + 1) * 128);
const boxHeader = Buffer.alloc(13);
boxHeader.writeUInt32BE(32, 0); boxHeader.writeUInt32BE(32, 4);
boxHeader[8] = 8; boxHeader[9] = 6;
await save(path.join(output, 'gem_box.png'), Buffer.concat([
  Buffer.from('89504e470d0a1a0a', 'hex'), chunk('IHDR', boxHeader),
  chunk('tEXt', Buffer.from('Author\0NingMeng123')),
  chunk('tEXt', Buffer.from('Description\0Gem box; 32x32 pixel art; generated with image_gen')),
  chunk('IDAT', deflateSync(boxScanlines, { level: 9 })), chunk('IEND', Buffer.alloc(0)),
]));
console.log(`${checkOnly ? 'Verified' : 'Packaged'} ${count} animated classic B gems (16x16 frames; 1.5–5 second cooldowns). Author: NingMeng123.`);
console.log(`${checkOnly ? 'Verified' : 'Packaged'} the redesigned gem box (32x32 RGBA). Author: NingMeng123.`);
const modelDirectory = path.join(root, 'src/main/resources/assets/modern_ite/models/item');
await mkdir(modelDirectory, { recursive: true });
for (const [filename, part] of [['gem_box', 'closed'], ['gem_box_body', 'body'], ['gem_box_lid', 'lid']])
  await save(path.join(modelDirectory, `${filename}.json`), JSON.stringify(gemBoxModel(part), null, 2) + '\n');
console.log(`${checkOnly ? 'Verified' : 'Packaged'} the native 3D gem box, hollow body and hinged lid models. Author: NingMeng123.`);
