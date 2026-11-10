/** Closed, faceted gemstone meshes for NeoForge's OBJ loader. Author: NingMeng123. */

const CENTER = [0.5, 0.49, 0.5];
const CUT_NAMES = ['trillion', 'diamond', 'pentagonal', 'hexagonal', 'heptagonal', 'octagonal'];

const add = (a, b) => a.map((value, axis) => value + b[axis]);
const subtract = (a, b) => a.map((value, axis) => value - b[axis]);
const multiply = (vector, scale) => vector.map(value => value * scale);
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const dot = (a, b) => a.reduce((sum, value, axis) => sum + value * b[axis], 0);
const mean = points => multiply(points.reduce(add, [0, 0, 0]), 1 / points.length);

/**
 * Return a closed 0..1 block-space mesh. +Y is the table/crown; -Y is the pavilion
 * point. A 3/4 GUI pose should look down onto the table to reveal the thickness.
 *
 * Every face supplies separate flat normals, a world-space center, its broad
 * material zone, and its facet role so the atlas generator can shade each cut.
 * Each face is a triangle or planar quad; no OBJ n-gon triangulation is needed.
 */
export function buildGemMesh(rank) {
  if (!Number.isInteger(rank) || rank < 1 || rank > 6) throw new Error('Gem rank must be 1..6.');
  const sides = rank + 2;
  const radius = 0.42;
  const tableRatio = rank === 1 ? 0.46 : rank === 2 ? 0.53 : 0.55;
  const topY = 0.76, girdleTopY = 0.52, girdleBottomY = 0.47, pointY = 0.21;
  const phase = -Math.PI / 2;
  const outline = Array.from({ length: sides }, (_, corner) => {
    const angle = phase + corner * Math.PI * 2 / sides;
    return [Math.cos(angle) * radius, 0, Math.sin(angle) * radius];
  });
  const ringPoint = (direction, y, scale = 1) => [0.5 + direction[0] * scale, y, 0.5 + direction[2] * scale];
  const table = outline.map(direction => ringPoint(direction, topY, tableRatio));
  const upper = outline.map(direction => ringPoint(direction, girdleTopY));
  const lower = outline.map(direction => ringPoint(direction, girdleBottomY));
  const tableCenter = [0.5, topY, 0.5];
  const point = [0.5, pointY, 0.5];
  const faces = [];

  function face(vertices, zone, part) {
    let ordered = vertices.map(vertex => [...vertex]);
    const center = mean(ordered);
    let normal = cross(subtract(ordered[1], ordered[0]), subtract(ordered[2], ordered[0]));
    if (dot(normal, subtract(center, CENTER)) < 0) {
      ordered.reverse();
      normal = multiply(normal, -1);
    }
    const length = Math.hypot(...normal);
    if (length < 1e-10) throw new Error(`Degenerate gem facet: rank ${rank}, ${zone}/${part}`);
    faces.push({ vertices: ordered, normal: multiply(normal, 1 / length), center, zone, part, facet: faces.length });
  }

  for (let corner = 0; corner < sides; corner++) {
    const next = (corner + 1) % sides;
    const edgeDirection = multiply(add(outline[corner], outline[next]), 0.5);
    const upperMid = ringPoint(edgeDirection, girdleTopY);
    const lowerMid = ringPoint(edgeDirection, girdleBottomY);
    // The intermediate star facet sits inside each outer edge. Its different
    // height and radius produce real planes instead of a painted flat prism.
    const star = ringPoint(edgeDirection, 0.645, 0.78);

    face([tableCenter, table[corner], table[next]], 'table', 'table');
    face([table[corner], table[next], star], 'crown', 'star');
    face([table[corner], star, upper[corner]], 'crown', 'main_left');
    face([star, table[next], upper[next]], 'crown', 'main_right');
    face([star, upper[next], upperMid], 'crown', 'lower_right');
    face([star, upperMid, upper[corner]], 'crown', 'lower_left');

    // Split the girdle at the same midpoints as the crown so every edge has an
    // exact matching neighbour; there are no T-junctions or floating faces.
    face([upper[corner], upperMid, lowerMid, lower[corner]], 'girdle', 'left');
    face([upperMid, upper[next], lower[next], lowerMid], 'girdle', 'right');
    face([lower[corner], lowerMid, point], 'pavilion', 'left');
    face([lowerMid, lower[next], point], 'pavilion', 'right');
  }

  const vertices = faces.flatMap(face => face.vertices);
  return {
    rank,
    sides,
    cut: CUT_NAMES[rank - 1],
    faces,
    bounds: {
      min: [0, 1, 2].map(axis => Math.min(...vertices.map(vertex => vertex[axis]))),
      max: [0, 1, 2].map(axis => Math.max(...vertices.map(vertex => vertex[axis]))),
    },
    tip: [...point],
    tableTip: [...table[0]],
    tableCenter: [...tableCenter],
    faceCount: faces.length,
    triangleCount: faces.reduce((sum, face) => sum + face.vertices.length - 2, 0),
  };
}

/**
 * Serialize per-face atlas UVs unchanged into a flat-shaded OBJ. The bundled
 * atlas uses top-origin UVs and model JSON "flip_v": false; callers supplying
 * conventional OBJ bottom-origin UVs must choose the matching loader option.
 */
export function buildGemObj(mesh, faceUvs, materialLibrary = 'modern_ite:models/item/gem3d/gem.mtl') {
  if (faceUvs.length !== mesh.faces.length) throw new Error('Every gem face needs its own atlas UV coordinates.');
  const lines = ['# Author: NingMeng123', `mtllib ${materialLibrary}`, `o gem_${mesh.cut}`, 's off', 'usemtl gem'];
  let vertexOffset = 1;
  for (const [index, face] of mesh.faces.entries()) {
    const uvs = faceUvs[index];
    if (uvs.length !== face.vertices.length || uvs.some(uv => uv.length !== 2 || uv.some(value => !Number.isFinite(value) || value < 0 || value > 1))) {
      throw new Error(`Invalid normalized atlas UVs for gem face ${index}.`);
    }
    for (const vertex of face.vertices) lines.push(`v ${vertex.map(value => value.toFixed(8)).join(' ')}`);
    for (const uv of uvs) lines.push(`vt ${uv.map(value => value.toFixed(8)).join(' ')}`);
    lines.push(`vn ${face.normal.map(value => value.toFixed(8)).join(' ')}`);
    lines.push(`f ${face.vertices.map((_, vertex) => `${vertexOffset + vertex}/${vertexOffset + vertex}/${index + 1}`).join(' ')}`);
    vertexOffset += face.vertices.length;
  }
  return lines.join('\n') + '\n';
}
