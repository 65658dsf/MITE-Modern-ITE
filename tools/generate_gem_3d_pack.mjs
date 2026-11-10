/** Reproducible faceted OBJ resource pack. Author: NingMeng123. */
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { deflateSync, deflateRawSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { buildGemMesh, buildGemObj } from './gem_3d_mesh.mjs';
import { gemShineMetadata } from './gem_shine.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const source = JSON.parse(await readFile(new URL('./gem_textures/classic_b.json', import.meta.url), 'utf8'));
const directory = path.join(root, 'resourcepacks/modern-ite-gems-3d');
const zipPath = path.join(root, 'build/resourcepacks/MITE-Modern-ITE-3D-Gems-1.21.1.zip');
const checkOnly = process.argv.includes('--check');
const SIZE = 64, TILE = 4, COLUMNS = SIZE / TILE, FRAME_COUNT = 28;
const files = new Map();
const json = value => JSON.stringify(value, null, 2) + '\n';
const mix = (a, b, amount) => a.map((value, channel) => value + (b[channel] - value) * amount);
const clamp = value => Math.max(0, Math.min(1, value));
const rgb = hex => [1, 3, 5].map(offset => parseInt(hex.slice(offset, offset + 2), 16));
const lightDirection = [-0.48, 0.72, -0.5];
const dispersion = [[255, 139, 211], [135, 255, 216], [255, 234, 135], [145, 177, 255]];
const dot = (a, b) => a.reduce((sum, value, axis) => sum + value * b[axis], 0);

const crcTable = Array.from({ length: 256 }, (_, n) => {
  for (let bit = 0; bit < 8; bit++) n = n & 1 ? 0xedb88320 ^ (n >>> 1) : n >>> 1;
  return n >>> 0;
});
function crc32(data) {
  let crc = 0xffffffff;
  for (const byte of data) crc = crcTable[(crc ^ byte) & 255] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}
function pngChunk(type, data) {
  const name = Buffer.from(type, 'ascii'), length = Buffer.alloc(4), checksum = Buffer.alloc(4);
  length.writeUInt32BE(data.length); checksum.writeUInt32BE(crc32(Buffer.concat([name, data])));
  return Buffer.concat([length, name, data, checksum]);
}
function png(width, height, pixels) {
  const header = Buffer.alloc(13), scanlines = Buffer.alloc((width * 4 + 1) * height);
  header.writeUInt32BE(width); header.writeUInt32BE(height, 4); header[8] = 8; header[9] = 6;
  for (let y = 0; y < height; y++) pixels.copy(scanlines, y * (width * 4 + 1) + 1, y * width * 4, (y + 1) * width * 4);
  return Buffer.concat([Buffer.from('89504e470d0a1a0a', 'hex'), pngChunk('IHDR', header),
    pngChunk('tEXt', Buffer.from('Author\0NingMeng123')),
    pngChunk('tEXt', Buffer.from('Description\0Faceted 3D gem surface; native sheen animation')),
    pngChunk('IDAT', deflateSync(scanlines, { level: 9 })), pngChunk('IEND', Buffer.alloc(0))]);
}

function uvForFace(face, index) {
  const x = index % COLUMNS * TILE, y = Math.floor(index / COLUMNS) * TILE;
  const local = face.vertices.length === 3 ? [[0, 0], [1, 0], [0, 1]] : [[0, 0], [1, 0], [1, 1], [0, 1]];
  return local.map(([u, v]) => [(x + 0.5 + u * (TILE - 1)) / SIZE, (y + 0.5 + v * (TILE - 1)) / SIZE]);
}
function facePoint(face, u, v) {
  let weights;
  if (face.vertices.length === 3) {
    if (u + v > 1) { const sum = u + v; u /= sum; v /= sum; }
    weights = [1 - u - v, u, v];
  } else weights = [(1 - u) * (1 - v), u * (1 - v), u * v, (1 - u) * v];
  return [0, 1, 2].map(axis => weights.reduce((sum, weight, index) => sum + weight * face.vertices[index][axis], 0));
}
// Screen-space diagonal of the default 35-degree GUI pose, applied in model space.
function sweepCoordinate([x, y, z]) {
  const angle = 35 * Math.PI / 180, c = Math.cos(angle), s = Math.sin(angle);
  x -= 0.5; y -= 0.5; z -= 0.5;
  return c * x + s * z - (c * y - s * (c * z - s * x));
}
function facetColor(face, swatches, u, v, point) {
  const lighting = clamp(dot(face.normal, lightDirection));
  const variation = ((face.facet * 37 + 17) % 13) / 12;
  const sector = Math.floor(face.facet / 10);
  let shade = face.zone === 'table' ? 0.27
    : face.zone === 'girdle' ? 0.28 + lighting * 0.45
      : face.zone === 'pavilion' ? 0.32 + lighting * 0.36 + variation * 0.22
        : 0.18 + lighting * 0.79 + variation * 0.23;
  shade = clamp(shade);
  let color = mix(swatches[1], swatches[5], shade);
  // Broad reflective facets surround a single dark table. Reflections follow
  // model-space planes, avoiding a repeated pinwheel painted on every triangle.
  if (face.zone === 'crown' && lighting > 0.58 && face.part !== 'main_right') {
    color = mix(color, [248, 255, 249], 0.78);
  } else if (face.part === 'star') color = mix(color, swatches[6], 0.28);
  if (face.zone === 'pavilion' && sector % 3 === 0 && face.part === 'left') {
    color = mix(color, swatches[7], 0.65);
  }
  const reflectedPlane = point[0] + point[2] * 0.46;
  if (face.zone === 'table' && reflectedPlane < 0.52) color = mix(color, swatches[7], 0.90);
  if (face.zone !== 'table' && face.facet % 7 === 0 && u > 0.77 && v < 0.36) {
    color = mix(color, dispersion[sector % 4], 0.72);
  }
  if (face.zone === 'girdle' && sector % 3 === 0) color = mix(color, swatches[7], 0.55);
  return color;
}
function surfaceFrames(mesh, palette) {
  const swatches = palette.map(rgb), samples = [];
  const points = mesh.faces.flatMap(face => face.vertices);
  const low = Math.min(...points.map(sweepCoordinate)), high = Math.max(...points.map(sweepCoordinate));
  const tip = points.reduce((best, point) => sweepCoordinate(point) < sweepCoordinate(best) ? point : best);
  for (const face of mesh.faces) {
    for (let y = 0; y < TILE; y++) for (let x = 0; x < TILE; x++) {
      const u = x / (TILE - 1), v = y / (TILE - 1), point = facePoint(face, u, v);
      const offset = ((Math.floor(face.facet / COLUMNS) * TILE + y) * SIZE + face.facet % COLUMNS * TILE + x) * 4;
      samples.push({ offset, color: facetColor(face, swatches, u, v, point), point, coordinate: sweepCoordinate(point) });
    }
  }
  const pixels = Buffer.alloc(SIZE * SIZE * 4 * FRAME_COUNT);
  for (let frame = 0; frame < FRAME_COUNT; frame++) {
    const progress = (frame - 1) / (FRAME_COUNT - 2);
    const band = high + 0.025 + (low - high - 0.05) * progress;
    const envelope = clamp((progress + 0.004) / 0.08) * clamp((1.004 - progress) / 0.08);
    const flash = frame === 0 ? 0 : envelope * Math.max(0, 1 - Math.abs(progress - 0.91) / 0.10);
    for (const sample of samples) {
      const distance = Math.abs(sample.coordinate - band);
      const sheen = frame === 0 ? 0 : envelope * Math.min(0.98,
        0.87 * Math.exp(-0.5 * (distance / 0.043) ** 2) + 0.11 * Math.exp(-0.5 * (distance / 0.085) ** 2));
      const sparkle = flash * clamp(1 - Math.hypot(...sample.point.map((value, axis) => value - tip[axis])) / 0.14);
      const color = mix(sample.color, [255, 255, 255], 1 - (1 - sheen) * (1 - sparkle));
      const offset = frame * SIZE * SIZE * 4 + sample.offset;
      for (let channel = 0; channel < 3; channel++) pixels[offset + channel] = Math.round(color[channel]);
      pixels[offset + 3] = 255;
    }
  }
  return pixels;
}

const display = {
  gui: { rotation: [35, 35, 0], translation: [0, 0, 0], scale: [1.15, 1.15, 1.15] },
  ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.55, 0.55, 0.55] },
  fixed: { rotation: [35, 0, 0], translation: [0, 0, 0], scale: [0.9, 0.9, 0.9] },
  thirdperson_righthand: { rotation: [55, 35, 0], translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
  thirdperson_lefthand: { rotation: [55, -35, 0], translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
  firstperson_righthand: { rotation: [25, 35, -10], translation: [0, 2.5, 0], scale: [0.65, 0.65, 0.65] },
  firstperson_lefthand: { rotation: [25, -35, 10], translation: [0, 2.5, 0], scale: [0.65, 0.65, 0.65] },
};
files.set('pack.mcmeta', json({ pack: { pack_format: 34, description: '立体切面宝石 · NingMeng123\n65种宝石 · Minecraft 1.21.1 / NeoForge' } }));
files.set('assets/modern_ite/models/item/gem3d/gem.mtl', '# Author: NingMeng123\nnewmtl gem\nKd 1 1 1\nmap_Kd #gem\n');
const manifest = { author: 'NingMeng123', format: 1, minecraft: '1.21.1', pack_format: 34, frames: FRAME_COUNT, frame_size: SIZE, items: [], cuts: [] };
for (const rank of source.ranks) {
  const mesh = buildGemMesh(rank.rank);
  const uvs = mesh.faces.map(uvForFace);
  files.set(`assets/modern_ite/models/item/gem3d/rank_${rank.rank}.obj`, buildGemObj(mesh, uvs));
  manifest.cuts.push({ rank: rank.rank, sides: mesh.sides, faces: mesh.faceCount, cut: mesh.cut, bounds: mesh.bounds });
  for (const [kind, palette] of Object.entries(source.palettes)) {
    if (kind === 'shard' && rank.rank === 1) continue;
    const id = `gem_${kind}_${rank.rank}`, texture = `modern_ite:item/gem3d/${id}`;
    files.set(`assets/modern_ite/models/item/${id}.json`, json({ parent: 'neoforge:item/default',
      loader: 'neoforge:obj', model: `modern_ite:models/item/gem3d/rank_${rank.rank}.obj`,
      automatic_culling: false, shade_quads: true, flip_v: false, emissive_ambient: false,
      ambientocclusion: false, gui_light: 'front', textures: { gem: texture, particle: '#gem' }, display }));
    files.set(`assets/modern_ite/textures/item/gem3d/${id}.png`, png(SIZE, SIZE * FRAME_COUNT, surfaceFrames(mesh, palette)));
    const metadata = gemShineMetadata(id, FRAME_COUNT);
    metadata.animation.width = SIZE; metadata.animation.height = SIZE;
    files.set(`assets/modern_ite/textures/item/gem3d/${id}.png.mcmeta`, json(metadata));
    manifest.items.push({ id: `modern_ite:${id}`, rank: rank.rank, kind, texture });
  }
}
if (manifest.items.length !== 65) throw new Error('Expected exactly 65 model overrides.');
files.set('modern-ite-gems-3d.json', json(manifest));
files.set('README.txt', '立体切面宝石 / Faceted Gems 3D\nAuthor: NingMeng123\n\nMinecraft 1.21.1 + NeoForge，适用于 MITE: Modern ITE。\n将此 ZIP 放入 resourcepacks 并在资源包列表置顶。\n覆盖全部65种宝石，保持原有类型颜色和六阶外形区分。\n右下到左上扫光，1.5～5秒冷却。真实OBJ多面几何，无额外模型加载模组。\n检视动作由新版 ITE 模组与 Punchy 提供；默认 I 键，不要求启用本资源包。\n');

// Deterministic ZIP: UTF-8 paths, ordinary DEFLATE, fixed timestamps.
function zip(entries) {
  const records = [], central = [];
  let offset = 0;
  for (const [filename, value] of entries) {
    const data = Buffer.isBuffer(value) ? value : Buffer.from(value), name = Buffer.from(filename);
    const compressed = deflateRawSync(data, { level: 9 }), checksum = crc32(data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50); local.writeUInt16LE(20, 4); local.writeUInt16LE(0x800, 6); local.writeUInt16LE(8, 8);
    local.writeUInt16LE(0x5c21, 12); local.writeUInt32LE(checksum, 14); local.writeUInt32LE(compressed.length, 18);
    local.writeUInt32LE(data.length, 22); local.writeUInt16LE(name.length, 26);
    const header = Buffer.alloc(46);
    header.writeUInt32LE(0x02014b50); header.writeUInt16LE(20, 4); header.writeUInt16LE(20, 6); header.writeUInt16LE(0x800, 8);
    header.writeUInt16LE(8, 10); header.writeUInt16LE(0x5c21, 14); header.writeUInt32LE(checksum, 16);
    header.writeUInt32LE(compressed.length, 20); header.writeUInt32LE(data.length, 24); header.writeUInt16LE(name.length, 28);
    header.writeUInt32LE(offset, 42); central.push(header, name);
    records.push(local, name, compressed); offset += local.length + name.length + compressed.length;
  }
  const directory = Buffer.concat(central), end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50); end.writeUInt16LE(entries.length, 8); end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(directory.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...records, directory, end]);
}
for (const [relative, contents] of files) {
  const destination = path.join(directory, relative);
  if (checkOnly) {
    if (!(await readFile(destination)).equals(Buffer.from(contents))) throw new Error(`Stale generated resource: ${relative}`);
  } else {
    await mkdir(path.dirname(destination), { recursive: true });
    await writeFile(destination, contents);
  }
}
const archive = zip([...files.entries()].sort(([a], [b]) => a.localeCompare(b, 'en')));
if (checkOnly) {
  if (!(await readFile(zipPath)).equals(archive)) throw new Error('Resource pack ZIP differs from generated contents.');
} else {
  await mkdir(path.dirname(zipPath), { recursive: true }); await writeFile(zipPath, archive);
}
console.log(`${checkOnly ? 'Verified' : 'Generated'} ${manifest.items.length} true 3D gems, ${files.size} files, ${archive.length} ZIP bytes. Author: NingMeng123.`);
console.log(zipPath);
