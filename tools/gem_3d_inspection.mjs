/** Original Punchy gemstone inspection resources for the main mod. Author: NingMeng123. */

export const GEM_INSPECTION_NAME = 'modern_ite_gem_inspect';
export const GEM_INSPECTION_SECONDS = 5.2;
const GEM_TYPES = ['damage', 'health', 'protection', 'recover', 'effect', 'durable',
  'speed', 'fireproof', 'explosionproof', 'nutrition'];
export const GEM_INSPECTION_ITEMS = Object.freeze([
  ...GEM_TYPES.flatMap(type => Array.from({ length: 6 }, (_, i) => `modern_ite:gem_${type}_${i + 1}`)),
  ...Array.from({ length: 5 }, (_, i) => `modern_ite:gem_shard_${i + 2}`),
]);

const round = value => Math.abs(value) < 0.00005 ? 0 : Number(value.toFixed(4));
const smooth = value => value * value * value * (value * (value * 6 - 15) + 10);
function curve(time, points) {
  for (let i = 1; i < points.length; i++) {
    const [end, target] = points[i];
    const [start, source] = points[i - 1];
    if (time <= end) return source + (target - source) * smooth(Math.max(0, (time - start) / (end - start)));
  }
  return points.at(-1)[1];
}

/**
 * Two-hand appraisal inspired by the user's video: support the gem, take it
 * with the other hand, raise it to eye level, tilt slowly, and put it away.
 * Punchy's renderer-only item overrides move a copy of the inspected stack;
 * the optional compatibility mixin resolves that copy without inventory edits.
 * The two physical-arm variants mirror the entire choreography. No camera or
 * finger animation is invented for Minecraft's original block-shaped hands.
 */
export function buildGemInspectClip(sourceSide = 'right') {
  if (!['right', 'left'].includes(sourceSide)) throw new Error('Unknown source arm');
  const bones = {};
  for (const side of ['right', 'left']) {
    bones[`${side}_arm`] = { rotation: {}, position: {} };
    bones[`itemgrip_${side}`] = { rotation: {}, position: {}, scale: {} };
  }
  const sample = (time, keys) => [0, 1, 2].map(axis => curve(time, keys.map(([at, value]) => [at, value[axis]])));
  const rightArmPosition = [
    [0, [0, 0, 0]], [0.25, [0, -5, 1]], [0.75, [0, -5, 1]],
    [1.25, [6, 2.5, -0.4]], [1.65, [9.5, 4.8, -1.5]],
    [2.2, [6.5, 5, -2]], [2.9, [6.8, 5.2, -2.1]],
    [3.7, [6.1, 4.8, -1.8]], [4.15, [6.4, 4.8, -2]],
    [4.55, [7, 0.5, -0.8]], [4.92, [0, -5, 1]], [5.2, [0, 0, 0]],
  ];
  const rightArmRotation = [
    [0, [0, 0, 0]], [0.25, [8, 0, 4]], [0.75, [8, 0, 4]],
    [1.25, [-24, 22, -20]], [1.65, [-28, 18, -26]],
    [2.2, [-23, -5, -6]], [2.9, [-21, -12, -10]],
    [3.7, [-25, 7, -6]], [4.15, [-23, 0, -4]],
    [4.55, [-10, 10, -14]], [4.92, [8, 0, 4]], [5.2, [0, 0, 0]],
  ];
  const leftArmPosition = [
    [0, [0, 0, 0]], [0.25, [0, -5, 1]], [0.8, [-8.5, 2.6, -1.2]],
    [1.3, [-8.5, 2.8, -1.2]], [1.65, [-9, 3.3, -1.5]],
    [2.15, [-1, -5, 1]], [3.95, [-1, -5, 1]],
    [4.5, [-8, 0.5, -0.8]], [4.92, [0, -5, 1]], [5.2, [0, 0, 0]],
  ];
  const leftArmRotation = [
    [0, [0, 0, 0]], [0.25, [8, 0, -4]], [0.8, [-25, -12, 8]],
    [1.3, [-26, -14, 9]], [1.65, [-28, -18, 7]],
    [2.15, [8, 0, -6]], [3.95, [8, 0, -6]],
    [4.5, [-18, -12, 12]], [4.92, [8, 0, -4]], [5.2, [0, 0, 0]],
  ];
  const rightGripRotation = [
    [0, [0, 0, 0]], [0.3, [-16, 10, -8]], [1.65, [-26, 10, -8]],
    [2.2, [-28, 12, -8]], [2.9, [-42, -24, 5]],
    [3.7, [-18, 28, -5]], [4.15, [-27, 6, -6]],
    [4.6, [-12, 10, -8]], [5.2, [0, 0, 0]],
  ];
  const leftGripRotation = [
    [0, [0, 0, 0]], [0.3, [-20, -12, 8]], [0.8, [-27, -8, 6]],
    [1.3, [-22, -14, 6]], [1.65, [-26, -10, 8]],
    [2.15, [0, 0, 0]], [5.2, [0, 0, 0]],
  ];
  for (let frame = 0; frame <= Math.round(GEM_INSPECTION_SECONDS * 30); frame++) {
    const time = frame / 30;
    const key = frame === 0 ? '0.0' : time.toFixed(4);
    const gripLift = curve(time, [[0, 0], [0.3, 0.6], [4.6, 0.6], [5.2, 0]]);
    const gripScale = curve(time, [[0, 1], [0.3, 0.82], [4.6, 0.82], [5.2, 1]]);
    for (const canonicalSide of ['right', 'left']) {
      const side = sourceSide === 'right' ? canonicalSide : canonicalSide === 'right' ? 'left' : 'right';
      const mirror = sourceSide === 'right' ? 1 : -1;
      const frameValue = vector => ({ post: vector.map(round), lerp_mode: 'catmullrom' });
      const arm = bones[`${side}_arm`];
      const rotation = sample(time, canonicalSide === 'right' ? rightArmRotation : leftArmRotation);
      const position = sample(time, canonicalSide === 'right' ? rightArmPosition : leftArmPosition);
      arm.rotation[key] = frameValue([rotation[0], rotation[1] * mirror, rotation[2] * mirror]);
      arm.position[key] = frameValue([1.74 * position[0] * mirror, position[1], position[2]]);
      const grip = bones[`itemgrip_${side}`];
      const gripRotation = sample(time, canonicalSide === 'right' ? rightGripRotation : leftGripRotation);
      grip.rotation[key] = frameValue([gripRotation[0], gripRotation[1] * mirror, gripRotation[2] * mirror]);
      grip.position[key] = frameValue([0, gripLift, 0]);
      grip.scale[key] = frameValue([gripScale, gripScale, gripScale]);
    }
  }
  return {
    loop: false,
    animation_length: GEM_INSPECTION_SECONDS,
    timeline: {
      '0.0': `use_keyframes_v2;anim_speed_1;dual_handed;show_offhand_if_empty;hide_${sourceSide === 'right' ? 'left' : 'right'}_switch_item_*;`,
      '0.3': `${sourceSide === 'right' ? 'left' : 'right'}_switch_item_modern_ite:inspection_source;hide_${sourceSide}_switch_item_*;`,
      '1.65': `${sourceSide}_switch_item_modern_ite:inspection_source;hide_${sourceSide === 'right' ? 'left' : 'right'}_switch_item_*;`,
      '4.95': 'show_left_switch_item;show_right_switch_item;hide_offhand_if_empty;',
    },
    bones,
  };
}

/**
 * Map resource-relative path -> UTF-8 JSON text. Punchy 3.1b only discovers these
 * resources in its own or the minecraft namespace, so do not relocate them to
 * assets/modern_ite. Unique names avoid replacing other packs' sword animations.
 * Exact item IDs deliberately exclude gem_box and all other equipment. These
 * files ship in the main mod, so inspection also works with its default sprites.
 */
export function getGemInspectionResources(itemIds = GEM_INSPECTION_ITEMS) {
  const ids = [...itemIds];
  if (ids.length !== 65 || new Set(ids).size !== 65 || ids.some(id => !GEM_INSPECTION_ITEMS.includes(id))) {
    throw new Error('Gem inspection requires exactly the 60 Modern ITE gems and five shards.');
  }
  const itemSpecific = Object.fromEntries(ids.map(id => [id, {
    customAnimation: [{ type: 'inspect', name: GEM_INSPECTION_NAME }],
  }]));
  const encode = value => `${JSON.stringify(value, null, 2)}\n`;
  return {
    'assets/minecraft/punchy/compat/modern_ite_gems.json': encode({ itemSpecific }),
    'assets/minecraft/punchy/animations/modern_ite_gems.animation.json': encode({
      format_version: '1.8.0',
      animations: {
        [GEM_INSPECTION_NAME]: buildGemInspectClip(),
        [`${GEM_INSPECTION_NAME}_right`]: buildGemInspectClip('right'),
        [`${GEM_INSPECTION_NAME}_left`]: buildGemInspectClip('left'),
      },
    }),
  };
}
