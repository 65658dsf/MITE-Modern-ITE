/** Native cuboid geometry for the wooden treasure box. Author: NingMeng123. */
export function gemBoxModel(part = 'closed') {
  const elements = [];
  const body = [], lid = [];
  let target = body;
  const directions = ['down', 'up', 'north', 'south', 'west', 'east'];
  const box = (name, from, to, texture, rotation) => {
    const element = { name, from, to, faces: Object.fromEntries(directions.map(face =>
      [face, { uv: [0, 0, 16, 16], texture: `#${texture}` }])) };
    if (rotation) element.rotation = rotation;
    elements.push(element);
    target.push(element);
  };
  const turned = (y, angle = 45) => ({ origin: [8, y, 8], axis: 'y', angle, rescale: false });

  // A real open cavity: the lid can rotate away without revealing a solid top slab.
  box('wooden_body_floor', [1.5, 0.8, 3], [14.5, 1.7, 13], 'wood');
  box('wooden_body_front', [1.5, 1.7, 3], [14.5, 7.5, 3.8], 'wood');
  box('wooden_body_back', [1.5, 1.7, 12.2], [14.5, 7.5, 13], 'wood');
  box('wooden_body_left', [1.5, 1.7, 3.8], [2.3, 7.5, 12.2], 'wood');
  box('wooden_body_right', [13.7, 1.7, 3.8], [14.5, 7.5, 12.2], 'wood');
  box('dark_inner_floor', [2.3, 1.7, 3.8], [13.7, 1.9, 12.2], 'lining');
  box('dark_inner_front', [2.3, 1.9, 3.8], [13.7, 7.7, 4], 'lining');
  box('dark_inner_back', [2.3, 1.9, 12], [13.7, 7.7, 12.2], 'lining');
  box('dark_inner_left', [2.3, 1.9, 4], [2.5, 7.7, 12], 'lining');
  box('dark_inner_right', [13.5, 1.9, 4], [13.7, 7.7, 12], 'lining');
  box('bottom_gold_rim', [1.25, 0.4, 2.75], [14.75, 1.3, 13.25], 'gold');
  box('body_top_gold_front', [1.25, 6.9, 2.75], [14.75, 7.7, 3.8], 'gold');
  box('body_top_gold_back', [1.25, 6.9, 12.2], [14.75, 7.7, 13.25], 'gold');
  box('body_top_gold_left', [1.25, 6.9, 3.8], [2.3, 7.7, 12.2], 'gold');
  box('body_top_gold_right', [13.7, 6.9, 3.8], [14.75, 7.7, 12.2], 'gold');
  box('dark_lid_seam_front', [1.3, 7.7, 2.8], [14.7, 8.1, 3.85], 'dark');
  box('dark_lid_seam_back', [1.3, 7.7, 12.15], [14.7, 8.1, 13.2], 'dark');
  box('dark_lid_seam_left', [1.3, 7.7, 3.85], [2.35, 8.1, 12.15], 'dark');
  box('dark_lid_seam_right', [13.65, 7.7, 3.85], [14.7, 8.1, 12.15], 'dark');

  for (const [x, name] of [[1.3, 'left'], [13.9, 'right']]) {
    for (const [z, side] of [[2.8, 'front'], [12.45, 'back']])
      box(`${name}_${side}_corner`, [x, 1.3, z], [x + 0.8, 6.9, z + 0.75], 'gold');
  }
  box('left_gold_hinge', [3, 7.4, 13.1], [5, 8.7, 13.8], 'gold');
  box('right_gold_hinge', [11, 7.4, 13.1], [13, 8.7, 13.8], 'gold');

  // Every moving part shares the native model coordinates and rear hinge at (8, 8.1, 13).
  target = lid;
  box('wooden_lid', [1.5, 8.1, 3], [14.5, 10.2, 13], 'wood');
  box('raised_lid_crown', [2.25, 10.2, 3.5], [13.75, 11.25, 12.5], 'wood');
  for (const [x, name] of [[3.2, 'left'], [11.8, 'right']]) {
    box(`${name}_front_lid_band`, [x, 8.1, 2.7], [x + 1, 10.25, 3.25], 'gold');
    box(`${name}_back_lid_band`, [x, 8.1, 12.75], [x + 1, 10.25, 13.3], 'gold');
    box(`${name}_crown_band`, [x, 11.25, 3.5], [x + 1, 11.55, 12.5], 'gold');
    box(`${name}_front_crown_step`, [x, 10.2, 3.25], [x + 1, 11.55, 3.8], 'gold');
    box(`${name}_back_crown_step`, [x, 10.2, 12.2], [x + 1, 11.55, 12.75], 'gold');
  }

  box('front_gold_lock', [6.75, 5.3, 2.25], [9.25, 8.8, 2.8], 'gold');
  box('lock_keyhole', [7.5, 6.25, 2.15], [8.5, 7.25, 2.3], 'dark');
  box('keyhole_stem', [7.75, 5.85, 2.15], [8.25, 6.3, 2.3], 'dark');

  // Tapered, turned tiers form a raised crystal rather than baking perspective into a flat icon.
  box('gold_gem_socket', [5.4, 11.25, 5.4], [10.6, 11.7, 10.6], 'gold', turned(11.25));
  box('blue_crystal_base', [6.15, 11.7, 6.15], [9.85, 12.5, 9.85], 'blue', turned(11.7));
  box('blue_crystal_middle', [6.6, 12.5, 6.6], [9.4, 13.4, 9.4], 'blue', turned(12.5));
  box('blue_crystal_light_facet', [7, 13.4, 7], [9, 14.15, 9], 'light_blue', turned(13.4));
  box('blue_crystal_tip', [7.5, 14.15, 7.5], [8.5, 14.9, 8.5], 'light_blue', turned(14.15));

  const model = {
    credit: 'NingMeng123',
    parent: 'minecraft:block/block',
    ambientocclusion: false,
    gui_light: 'side',
    textures: {
      particle: 'minecraft:block/spruce_planks',
      wood: 'minecraft:block/spruce_planks',
      dark: 'minecraft:block/dark_oak_planks',
      lining: 'minecraft:block/black_wool',
      gold: 'minecraft:block/gold_block',
      blue: 'minecraft:block/blue_concrete',
      light_blue: 'minecraft:block/light_blue_concrete',
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
    elements,
  };
  if (part === 'body' || part === 'lid') {
    model.elements = part === 'body' ? body : lid;
    delete model.display;
  } else if (part !== 'closed') {
    throw new Error(`Unknown gem box model part: ${part}`);
  }
  return model;
}
