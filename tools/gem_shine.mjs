/** Native gem sheen frames and staggered cooldowns. Author: NingMeng123. */
const SIZE = 16;
const SWEEP_TICKS = 28;
const FLASH_DECAY_TICKS = 4;
const SCHEDULE_CYCLES = 12;

/** Keep the indexed artwork and alpha fixed; animate only its reflected light. */
export function gemShineFrames(pixels, palette, rank) {
  const colors = palette.map(hex => Buffer.from(hex.slice(1) + 'ff', 'hex'));
  const base = Buffer.alloc(SIZE * SIZE * 4);
  let low = Infinity, high = -Infinity;
  for (let y = 0; y < SIZE; y++) {
    for (let x = 0; x < SIZE; x++) {
      if (pixels[y][x] === '.') continue;
      colors[Number(pixels[y][x])].copy(base, (y * SIZE + x) * 4);
      low = Math.min(low, x + y);
      high = Math.max(high, x + y);
    }
  }

  // The octagonal gem has a flat top: use its upper-left corner instead.
  const tipX = rank === 6 ? 5 : 7;
  const tipY = 2;
  const start = high + 3, end = low - 3;
  const arrival = (start - tipX - tipY) / (start - end) * SWEEP_TICKS;
  const frames = [base];
  for (let tick = 0; tick <= SWEEP_TICKS + FLASH_DECAY_TICKS; tick++) {
    const frame = Buffer.from(base);
    // In texture coordinates Y grows downward. Decreasing x+y moves up-left.
    const position = start + (end - start) * tick / SWEEP_TICKS;
    const flashAge = tick - arrival;
    const flash = flashAge < -1 || flashAge > FLASH_DECAY_TICKS ? 0
      : flashAge < 0 ? flashAge + 1 : (1 - flashAge / FLASH_DECAY_TICKS) ** 2;
    for (let y = 0; y < SIZE; y++) {
      for (let x = 0; x < SIZE; x++) {
        const offset = (y * SIZE + x) * 4;
        if (base[offset + 3] === 0) continue;
        const distance = Math.abs(x + y - position);
        const band = distance >= 3 ? 0
          : 0.84 * Math.exp(-0.5 * (distance / 0.65) ** 2)
            + 0.14 * Math.exp(-0.5 * (distance / 1.35) ** 2);
        const dx = Math.abs(x - tipX), dy = Math.abs(y - tipY);
        const ray = dx === 0 || dy === 0 ? Math.max(0, 1 - (dx + dy) / 3) : 0;
        const halo = Math.max(0, 1 - Math.hypot(dx, dy) / 1.5) * 0.35;
        // A tiny four-point glint remains inside the original silhouette, so
        // generated item side geometry never grows around temporary rays.
        const sparkle = Math.min(1, flash * (ray + halo) * 1.2);
        const sheen = band * (pixels[y][x] === '0' ? 0.38 : 1);
        const light = 1 - (1 - sheen) * (1 - sparkle);
        for (let channel = 0; channel < 3; channel++) {
          frame[offset + channel] = Math.round(base[offset + channel]
            + (255 - base[offset + channel]) * light);
        }
      }
    }
    frames.push(frame);
  }
  // All wholly quiet frames belong to the explicit cooldown, not the active
  // phase. This keeps the actual rest between visible shines at 30–100 ticks.
  while (frames.length > 1 && frames[1].equals(base)) frames.splice(1, 1);
  while (frames.length > 1 && frames.at(-1).equals(base)) frames.pop();
  return frames;
}

export function gemShineMetadata(id, frameCount) {
  let seed = 2166136261;
  for (const character of id) seed = Math.imul(seed ^ character.charCodeAt(0), 16777619) >>> 0;
  const frames = [];
  for (let cycle = 0; cycle < SCHEDULE_CYCLES; cycle++) {
    seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
    frames.push({ index: 0, time: 30 + seed % 71 });
    for (let frame = 1; frame < frameCount; frame++) frames.push(frame);
  }
  return { animation: { width: SIZE, height: SIZE, frametime: 1, interpolate: false, frames } };
}
