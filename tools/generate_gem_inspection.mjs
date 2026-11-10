/** Generate the main mod's optional Punchy inspection data. Author: NingMeng123. */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { getGemInspectionResources, GEM_INSPECTION_ITEMS } from './gem_3d_inspection.mjs';

const args = process.argv.slice(2);
if (args.some(arg => arg !== '--check')) {
  console.error('Usage: node tools/generate_gem_inspection.mjs [--check]');
  process.exit(1);
}
const check = args.includes('--check');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'src', 'main', 'resources');
const mismatches = [];
const resources = getGemInspectionResources();
for (const [relative, content] of Object.entries(resources)) {
  const output = path.join(root, relative);
  if (check) {
    if (!fs.existsSync(output) || fs.readFileSync(output, 'utf8') !== content) mismatches.push(relative);
  } else {
    fs.mkdirSync(path.dirname(output), { recursive: true });
    fs.writeFileSync(output, content, 'utf8');
  }
}
if (mismatches.length) {
  console.error(`Gem inspection resources need regeneration:\n${mismatches.join('\n')}`);
  process.exitCode = 1;
} else {
  console.log(`${check ? 'Verified' : 'Generated'} ${Object.keys(resources).length} Punchy resources for ${GEM_INSPECTION_ITEMS.length} gems/shards in the main mod.`);
}
