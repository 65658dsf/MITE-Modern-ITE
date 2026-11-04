/** Native blackstone, cyan-seal and bone geometry. Author: NingMeng123. */
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const WITHER_TREASURE_HINGE = [8, 8.1, 13];

export function witherTreasureModel(part = 'closed') {
  const body = [], lid = [];
  let target = body;
  const faces = ['down', 'up', 'north', 'south', 'west', 'east'];
  const box = (name, from, to, texture) => target.push({
    name,
    from: from.map(value => Math.round(value * 1000) / 1000),
    to: to.map(value => Math.round(value * 1000) / 1000),
    faces: Object.fromEntries(faces.map(face => [face, { uv: [0, 0, 16, 16], texture: `#${texture}` }])),
  });

  // Separate floor and four walls leave a genuine cavity when the lid opens.
  box('blackstone_floor', [1.5, 0.8, 3], [14.5, 1.8, 13], 'stone');
  box('blackstone_front_wall', [1.5, 1.8, 3], [14.5, 7.7, 3.85], 'stone');
  box('blackstone_back_wall', [1.5, 1.8, 12.15], [14.5, 7.7, 13], 'stone');
  box('blackstone_left_wall', [1.5, 1.8, 3.85], [2.35, 7.7, 12.15], 'stone');
  box('blackstone_right_wall', [13.65, 1.8, 3.85], [14.5, 7.7, 12.15], 'stone');
  box('recessed_inner_floor', [2.35, 1.8, 3.85], [13.65, 1.96, 12.15], 'void');
  box('inner_front_lining', [2.35, 1.96, 3.85], [13.65, 7.55, 4.02], 'void');
  box('inner_back_lining', [2.35, 1.96, 11.98], [13.65, 7.55, 12.15], 'void');
  box('inner_left_lining', [2.35, 1.96, 4.02], [2.52, 7.55, 11.98], 'void');
  box('inner_right_lining', [13.48, 1.96, 4.02], [13.65, 7.55, 11.98], 'void');

  // Frame members also remain four individual strips, never a solid top slab.
  for (const [name, y0, y1] of [['base', 0.65, 1.35], ['rim', 7.4, 8.1]]) {
    box(`${name}_front_frame`, [1.3, y0, 2.8], [14.7, y1, 3.9], 'frame');
    box(`${name}_back_frame`, [1.3, y0, 12.1], [14.7, y1, 13.2], 'frame');
    box(`${name}_left_frame`, [1.3, y0, 3.9], [2.4, y1, 12.1], 'frame');
    box(`${name}_right_frame`, [13.6, y0, 3.9], [14.7, y1, 12.1], 'frame');
  }

  for (const [name, x0, x1] of [['left', 2.35, 6.6], ['right', 9.4, 13.65]]) {
    box(`${name}_front_deepslate_panel`, [x0, 1.5, 2.86], [x1, 6.65, 3.1], 'panel');
    box(`${name}_back_deepslate_panel`, [x0, 1.5, 12.9], [x1, 6.65, 13.14], 'panel');
  }
  box('left_deepslate_panel', [1.36, 1.5, 4], [1.6, 6.65, 12], 'panel');
  box('right_deepslate_panel', [14.4, 1.5, 4], [14.64, 6.65, 12], 'panel');

  // Thin cyan strips are real shallow cuboids attached to the outer stone walls.
  for (const [name, z0, z1] of [['front', 2.68, 3.04], ['back', 12.96, 13.32]]) {
    box(`${name}_cyan_seal_band`, [1.45, 6.78, z0], [14.55, 7.22, z1], 'cyan');
    box(`${name}_seal_highlight`, [2.1, 6.94, z0 - (name === 'front' ? 0.025 : -0.025)],
      [13.9, 7.08, z1 + (name === 'back' ? 0.025 : -0.025)], 'glow');
    for (const [rune, x] of [['left', 4.05], ['right', 10.75]]) {
      box(`${name}_${rune}_rune_stem`, [x, 2.15, z0], [x + 0.4, 5.45, z1], 'cyan');
      box(`${name}_${rune}_rune_upper_step`, [x - 0.7, 4.65, z0], [x + 0.4, 5.05, z1], 'cyan');
      box(`${name}_${rune}_rune_lower_step`, [x, 2.75, z0], [x + 1.1, 3.15, z1], 'cyan');
      box(`${name}_${rune}_rune_core`, [x + 0.1, 3.25, z0], [x + 0.3, 4.25, z1], 'glow');
    }
  }
  for (const [name, x0, x1] of [['left', 1.18, 1.54], ['right', 14.46, 14.82]]) {
    box(`${name}_cyan_seal_band`, [x0, 6.78, 3.02], [x1, 7.22, 12.98], 'cyan');
    box(`${name}_side_rune_stem`, [x0, 2.2, 7.65], [x1, 5.7, 8.05], 'cyan');
    box(`${name}_side_rune_crossbar`, [x0, 3.8, 6.2], [x1, 4.2, 9.5], 'cyan');
    box(`${name}_side_rune_tip`, [x0, 4.2, 6.2], [x1, 5.05, 6.6], 'glow');
  }

  // Bone feet, corner posts and short ribs belong to the stationary body.
  for (const [side, x] of [['left', 1.1], ['right', 13.7]]) {
    for (const [end, z] of [['front', 2.6], ['back', 12.2]]) {
      box(`${side}_${end}_bone_foot`, [x - 0.15, 0.3, z - 0.15], [x + 1.35, 1.45, z + 1.35], 'bone');
      box(`${side}_${end}_bone_post`, [x + 0.15, 1.4, z + 0.15], [x + 1.05, 6.6, z + 1.05], 'bone');
      box(`${side}_${end}_lower_bone_cuff`, [x - 0.1, 1.35, z - 0.1], [x + 1.3, 2.15, z + 1.3], 'bone_end');
      box(`${side}_${end}_upper_bone_cuff`, [x - 0.1, 5.75, z - 0.1], [x + 1.3, 6.6, z + 1.3], 'bone_end');
    }
  }
  box('front_left_lower_bone_rib', [2.3, 1.15, 2.55], [4.4, 1.75, 3.2], 'bone');
  box('front_right_lower_bone_rib', [11.6, 1.15, 2.55], [13.7, 1.75, 3.2], 'bone');
  box('back_left_lower_bone_rib', [2.3, 1.15, 12.8], [4.4, 1.75, 13.45], 'bone');
  box('back_right_lower_bone_rib', [11.6, 1.15, 12.8], [13.7, 1.75, 13.45], 'bone');
  box('left_hinge_mount', [3, 7.35, 12.9], [5, 8.45, 13.6], 'frame');
  box('right_hinge_mount', [11, 7.35, 12.9], [13, 8.45, 13.6], 'frame');

  // Moving geometry retains the GemBox rear hinge at (8, 8.1, 13).
  target = lid;
  box('blackstone_lid', [1.5, 8.1, 3], [14.5, 10.35, 13], 'stone');
  box('stepped_deepslate_lid_crown', [2, 10.35, 3.45], [14, 11.15, 12.55], 'panel');
  box('lid_inner_face', [2.45, 8.07, 3.95], [13.55, 8.14, 12.05], 'void');
  box('front_lid_lower_frame', [1.3, 8.1, 2.8], [14.7, 8.65, 3.4], 'frame');
  box('back_lid_lower_frame', [1.3, 8.1, 12.6], [14.7, 8.65, 13.2], 'frame');
  box('left_lid_lower_frame', [1.3, 8.1, 3.4], [1.9, 8.65, 12.6], 'frame');
  box('right_lid_lower_frame', [14.1, 8.1, 3.4], [14.7, 8.65, 12.6], 'frame');
  box('left_lid_hinge', [3.25, 8.1, 12.95], [4.75, 9.0, 13.55], 'bone');
  box('right_lid_hinge', [11.25, 8.1, 12.95], [12.75, 9.0, 13.55], 'bone');

  // Each corner cap wraps the top and both vertical sides, attached to the lid.
  for (const [side, x0, x1] of [['left', 1.08, 3.35], ['right', 12.65, 14.92]]) {
    for (const [end, z0, z1] of [['front', 2.58, 5.2], ['back', 10.8, 13.42]]) {
      box(`${side}_${end}_lid_bone_cap`, [x0, 10.35, z0], [x1, 11.65, z1], 'bone');
      const faceZ = end === 'front' ? [2.45, 3.15] : [12.85, 13.55];
      box(`${side}_${end}_lid_corner_face`, [x0, 8.65, faceZ[0]], [x1, 10.55, faceZ[1]], 'bone');
      const cheekX = side === 'left' ? [1.05, 1.8] : [14.2, 14.95];
      box(`${side}_${end}_lid_corner_cheek`, [cheekX[0], 8.65, z0], [cheekX[1], 10.55, z1], 'bone');
      const insetZ = end === 'front' ? [2.42, 2.5] : [13.5, 13.58];
      box(`${side}_${end}_corner_socket`, [x0 + 0.65, 9.0, insetZ[0]], [x1 - 0.45, 9.85, insetZ[1]], 'void');
      box(`${side}_${end}_top_bone_arm`, [side === 'left' ? x1 - 0.45 : x0 - 0.9, 11.12, z0 + 0.5],
        [side === 'left' ? x1 + 0.9 : x0 + 0.45, 11.5, z1 - 0.5], 'bone_end');
    }
  }

  // Raised cyan wither seal: a central spine with three connected prongs.
  box('crown_seal_spine', [7.6, 11.15, 5.85], [8.4, 11.31, 10.25], 'cyan');
  box('crown_seal_crossbar', [4.85, 11.15, 8.8], [11.15, 11.31, 9.5], 'cyan');
  box('crown_seal_left_prong', [4.85, 11.15, 7.2], [5.55, 11.31, 9.5], 'cyan');
  box('crown_seal_right_prong', [10.45, 11.15, 7.2], [11.15, 11.31, 9.5], 'cyan');
  box('crown_seal_spine_core', [7.85, 11.3, 6.55], [8.15, 11.35, 8.9], 'glow');
  box('crown_seal_left_core', [5.05, 11.3, 7.45], [5.35, 11.35, 8.25], 'glow');
  box('crown_seal_right_core', [10.65, 11.3, 7.45], [10.95, 11.35, 8.25], 'glow');

  // The clasp hangs from the moving lid. Separate brow, temples and nose bridge
  // surround actual recessed eye sockets, rather than drawing a face on a plane.
  box('skull_clasp_mount', [7.35, 8.65, 2.35], [8.65, 10.6, 3.2], 'bone');
  box('skull_recess_backing', [5.85, 5.5, 2.48], [10.15, 8.95, 2.95], 'void');
  box('skull_brow', [6.05, 7.85, 1.95], [9.95, 8.95, 2.7], 'bone');
  box('skull_left_temple', [5.85, 6.35, 2.03], [6.75, 8.4, 2.8], 'bone');
  box('skull_right_temple', [9.25, 6.35, 2.03], [10.15, 8.4, 2.8], 'bone');
  box('skull_nose_bridge', [7.65, 6.65, 1.88], [8.35, 7.95, 2.7], 'bone');
  box('skull_left_cheek', [6.2, 6.25, 1.97], [7.6, 6.7, 2.7], 'bone');
  box('skull_right_cheek', [8.4, 6.25, 1.97], [9.8, 6.7, 2.7], 'bone');
  box('skull_left_cyan_eye', [6.85, 6.9, 2.36], [7.5, 7.35, 2.55], 'glow');
  box('skull_right_cyan_eye', [8.5, 6.9, 2.36], [9.15, 7.35, 2.55], 'glow');
  box('skull_jaw', [6.85, 5.6, 2.04], [9.15, 6.3, 2.75], 'bone_end');
  for (const [name, x] of [['left', 6.95], ['middle', 7.75], ['right', 8.55]])
    box(`skull_${name}_tooth`, [x, 5.05, 2.03], [x + 0.5, 5.7, 2.65], 'bone');

  const model = {
    credit: 'NingMeng123',
    parent: 'minecraft:block/block',
    ambientocclusion: false,
    gui_light: 'side',
    textures: {
      particle: 'minecraft:block/blackstone',
      stone: 'minecraft:block/blackstone',
      panel: 'minecraft:block/deepslate_tiles',
      frame: 'minecraft:block/polished_blackstone',
      void: 'minecraft:block/black_concrete',
      cyan: 'minecraft:block/cyan_concrete',
      glow: 'minecraft:block/light_blue_concrete',
      bone: 'minecraft:block/bone_block_side',
      bone_end: 'minecraft:block/bone_block_top',
    },
    display: {
      gui: { rotation: [27, 225, 0], translation: [0, 0.4, 0], scale: [0.85, 0.85, 0.85] },
      ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.5, 0.5, 0.5] },
      fixed: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [0.75, 0.75, 0.75] },
      firstperson_righthand: { rotation: [0, -20, 0], translation: [0, 0.5, 0], scale: [0.8, 0.8, 0.8] },
      firstperson_lefthand: { rotation: [0, 20, 0], translation: [0, 0.5, 0], scale: [0.8, 0.8, 0.8] },
      thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.55, 0.55, 0.55] },
      thirdperson_lefthand: { rotation: [75, -45, 0], translation: [0, 2.5, 0], scale: [0.55, 0.55, 0.55] },
    },
    elements: [...body, ...lid],
  };
  if (part === 'body' || part === 'lid') {
    model.elements = part === 'body' ? body : lid;
    delete model.display;
  } else if (part !== 'closed') throw new Error(`Unknown wither treasure model part: ${part}`);
  return model;
}

export function validateWitherTreasureModels() {
  const closed = witherTreasureModel(), body = witherTreasureModel('body'), lid = witherTreasureModel('lid');
  assert.deepEqual(closed.elements, [...body.elements, ...lid.elements]);
  assert.equal(new Set(closed.elements.map(e => e.name)).size, closed.elements.length);
  const bounds = { from: [Infinity, Infinity, Infinity], to: [-Infinity, -Infinity, -Infinity] };
  let cuboidVolume = 0;
  for (const element of closed.elements) {
    assert.equal(element.from.length, 3);
    assert.equal(element.to.length, 3);
    let volume = 1;
    for (let axis = 0; axis < 3; axis++) {
      assert(Number.isFinite(element.from[axis]) && Number.isFinite(element.to[axis]));
      assert(element.from[axis] >= -16 && element.to[axis] <= 32);
      assert(element.to[axis] > element.from[axis], `${element.name} must have positive volume`);
      volume *= element.to[axis] - element.from[axis];
      bounds.from[axis] = Math.min(bounds.from[axis], element.from[axis]);
      bounds.to[axis] = Math.max(bounds.to[axis], element.to[axis]);
    }
    cuboidVolume += volume;
    assert.deepEqual(Object.keys(element.faces), ['down', 'up', 'north', 'south', 'west', 'east']);
    for (const face of Object.values(element.faces)) {
      assert(face.texture.startsWith('#') && closed.textures[face.texture.slice(1)]);
      assert.deepEqual(face.uv, [0, 0, 16, 16]);
    }
  }
  assert(Object.values(closed.textures).every(texture => texture.startsWith('minecraft:block/')));
  assert(!body.elements.some(e => e.from.every((from, axis) => from < [8, 6, 8][axis])
    && e.to.every((to, axis) => to > [8, 6, 8][axis])), 'The body must leave its interior hollow');
  assert(lid.elements.some(e => e.name === 'skull_brow') && lid.elements.some(e => e.name === 'skull_left_temple'));
  return { body: body.elements.length, lid: lid.elements.length, closed: closed.elements.length,
    bounds, cuboidVolume: Math.round(cuboidVolume * 1000) / 1000, hinge: WITHER_TREASURE_HINGE };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const output = new URL('../src/main/resources/assets/modern_ite/models/item/', import.meta.url);
  await mkdir(output, { recursive: true });
  const summary = validateWitherTreasureModels();
  for (const [part, suffix] of [['closed', ''], ['body', '_body'], ['lid', '_lid']])
    await writeFile(new URL(`wither_treasure${suffix}.json`, output), `${JSON.stringify(witherTreasureModel(part), null, 2)}\n`);
  console.log(JSON.stringify(summary, null, 2));
}
