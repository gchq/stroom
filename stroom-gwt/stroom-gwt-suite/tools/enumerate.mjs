#!/usr/bin/env node
/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Phase A: enumerate GWT files under client/shared/api package segments and
// extract metadata (no classification yet). Prints distributions for review.
import { execSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { SOURCE, oracle } from '../lib/paths.mjs';

const STROOM = SOURCE;

// Files with `client`, `shared` or `api` as a package path segment, in
// src/main, extensions .java/.ui.xml/.gwt.xml/.css, excluding build output.
const find = [
  `find . -type f \\( -name '*.java' -o -name '*.ui.xml' -o -name '*.gwt.xml' -o -name '*.css' \\)`,
  `\\( -path '*/client/*' -o -path '*/shared/*' -o -path '*/api/*' \\)`,
  `-path '*/src/main/*' -not -path '*/build/*'`,
].join(' ');

const paths = execSync(find, { cwd: STROOM, maxBuffer: 1 << 28 })
  .toString().trim().split('\n')
  .map((p) => p.replace(/^\.\//, ''))
  .sort();

// ---- GWT translatable-source whitelist -----------------------------------
// A .gwt.xml at package P declares `<source path="X"/>` (default "client") to
// make P.X.** translatable-to-JS. The union of these prefixes is the set of
// packages actually compiled into the GWT app; anything else is server-only.
const gwtXmls = execSync(
  `find . -name '*.gwt.xml' -path '*/src/main/*' -not -path '*/build/*'`,
  { cwd: STROOM, maxBuffer: 1 << 26 },
).toString().trim().split('\n').map((p) => p.replace(/^\.\//, ''));

const gwtPrefixes = new Set();
for (const gx of gwtXmls) {
  const m = gx.match(/\/(?:java|resources)\/(.+)\/[^/]+\.gwt\.xml$/);
  if (!m) continue;
  const modulePkg = m[1].replace(/\//g, '.');
  const body = readFileSync(`${STROOM}/${gx}`, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
  const sources = [...body.matchAll(/<source\s+path=['"]([^'"]+)['"]/g)].map((x) => x[1]);
  const paths2 = sources.length ? sources : ['client'];
  for (const sp of paths2) gwtPrefixes.add(`${modulePkg}.${sp.replace(/\//g, '.')}`);
}
const prefixes = [...gwtPrefixes];
const inGwtBuild = (pkg) => prefixes.some((p) => pkg === p || pkg.startsWith(p + '.'));

function ext(p) {
  if (p.endsWith('.ui.xml')) return 'ui.xml';
  if (p.endsWith('.gwt.xml')) return 'gwt.xml';
  const m = p.match(/\.([^./]+)$/);
  return m ? m[1] : '';
}

// package = dotted path between /java/ and the file; layer = client|shared|api
// segment; area = first package segment for stroom.*, else a vendored marker.
function meta(p) {
  const module = p.split('/')[0];
  // UiBinder .ui.xml + .css live under src/main/resources mirroring the package;
  // .java under src/main/java. Extract the package dir from either tree.
  const jm = p.match(/\/(?:java|resources)\/(.+)\/[^/]+$/);
  const pkg = jm ? jm[1].replace(/\//g, '.') : '';
  const segs = jm ? jm[1].split('/') : [];
  const layer = ['client', 'shared', 'api'].find((l) => segs.includes(l)) || '';
  const file = p.split('/').pop();
  const base = file.replace(/\.(java|ui\.xml|gwt\.xml|css)$/, '');

  let area;
  if (segs[0] === 'stroom') area = segs[1] || '(root)';
  else if (pkg.startsWith('com.google.gwt')) area = '@gwt';
  else if (pkg.startsWith('com.gwtplatform')) area = '@gwtp';
  else if (pkg.startsWith('edu.ycp')) area = '@acegwt';
  else area = segs[0] || '(none)';

  // sub-area: for stroom.<area>.<sub>... the segment right after area (skipping
  // a leading client/shared/api), useful for widget sub-packages.
  let sub = '';
  if (segs[0] === 'stroom') {
    const after = segs.slice(2).filter((s) => !['client', 'shared', 'api'].includes(s));
    sub = after[0] || '';
  }

  const ROLES = ['Ginjector', 'PresenterWidget', 'Presenter', 'ViewImpl', 'UiHandlers',
    'View', 'EventHandler', 'Event', 'Plugin', 'ResourceImpl', 'Resource', 'Proxy',
    'Module', 'Provider', 'EntryPoint', 'Cell', 'Util', 'Utils', 'Constants',
    'Settings', 'Config', 'Fields', 'Field', 'Criteria', 'Builder', 'Serialiser',
    'Serializer', 'Manager', 'Factory', 'Handler', 'Model', 'Dto', 'Widget',
    'Panel', 'Popup', 'Dialog', 'Type', 'Types', 'Client'];
  const role = ROLES.find((r) => base.endsWith(r)) || '';

  // Vendored client libs (GWT/GWTP/ACE overrides) are always in the build even
  // though no stroom .gwt.xml <source> covers their default-namespace packages.
  const vendored = area === '@gwt' || area === '@gwtp' || area === '@acegwt';
  const inBuild = vendored || inGwtBuild(pkg);

  return { path: p, module, layer, area, sub, pkg, file, base, ext: ext(p), role, inBuild };
}

const rows = paths.map(meta);
writeFileSync(oracle('raw.json'), JSON.stringify(rows, null, 0));

const tally = (key) => {
  const m = {};
  for (const r of rows) m[r[key]] = (m[r[key]] || 0) + 1;
  return Object.entries(m).sort((a, b) => b[1] - a[1]);
};

console.log('TOTAL FILES:', rows.length);
console.log('GWT prefixes:', prefixes.length,
  '| inBuild:', rows.filter((r) => r.inBuild).length,
  '| server-only:', rows.filter((r) => !r.inBuild).length);
console.log('\nSERVER-ONLY by layer:', JSON.stringify(Object.fromEntries(
  Object.entries(rows.filter((r) => !r.inBuild).reduce((m, r) => ((m[r.layer] = (m[r.layer] || 0) + 1), m), {})))));
console.log('\nBY EXT:', JSON.stringify(Object.fromEntries(tally('ext'))));
console.log('\nBY LAYER:', JSON.stringify(Object.fromEntries(tally('layer'))));
console.log('\nBY ROLE (top 30):');
for (const [k, v] of tally('role').slice(0, 30)) console.log(`  ${String(v).padStart(4)}  ${k || '(none)'}`);
console.log('\nBY AREA (all):');
for (const [k, v] of tally('area')) console.log(`  ${String(v).padStart(4)}  ${k}`);
