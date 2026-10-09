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

// Phase B: classify each enumerated file (top-level + subcategory) and render
// oracles/gwt-inventory.csv (machine-readable source of truth) + gwt-inventory.md.
// Classification is heuristic: (layer x area x role x sub-package). The fuzzy
// rule inputs below are meant to be tuned after reviewing the distribution.
import { readFileSync, writeFileSync } from 'node:fs';
import { oracle } from '../lib/paths.mjs';

// Only files actually compiled into the GWT app are in scope; server-only
// packages (mostly the non-whitelisted `*.api` service layer) are excluded.
const rows = JSON.parse(readFileSync(oracle('raw.json'), 'utf8'))
  .filter((r) => r.inBuild);

/* ============================ EDITABLE RULE INPUTS ======================== */

// The reusable widget library areas (stroom/<area>/client), not tied to a feature.
// `alert` = the standard alert/confirm/prompt dialog framework (its Events are the
// widget's fire-to-show API), so it lives with the widgets.
const WIDGET_AREAS = new Set(['widget', 'cell', 'editor', 'svg', 'item', 'alert']);

// Cross-cutting screens/dialogs reused across features (not one feature's screen).
// `explorer` = the doc tree + doc-ref choosers + find, wired to data and reused
// everywhere (per review: the data-wiring parts are a shared screen).
const SHARED_SCREEN_AREAS = new Set([
  'entity', 'document', 'docref', 'docrefinfo', 'preferences', 'content', 'explorer',
]);

// Application shell / chrome that is genuine plumbing (NOT a feature screen).
// main/about/welcome were pulled out to Screen/Dialog — they are screens, not plumbing.
// `view` was here and should not have been: `stroom/view/client/` is the VIEW DOCUMENT TYPE, not
// GWTP view plumbing (which is a ROLE — `*ViewImpl` under `client/view/` — and is classified as one).
// It cost the coverage ledger a whole document editor: `ViewPresenter extends DocTabPresenter<_,
// ViewDoc>` was filed under Framework/Application shell, so the crawl could reach it and never count
// it. All 4 client files under the area are the View editor.
const APP_SHELL_AREAS = new Set([
  'menubar', 'app', 'core', 'ui', 'iframe', 'hyperlink', 'lifecycle', 'banner',
]);

// Areas whose Events/Plugins are genuine framework plumbing rather than feature-specific.
const FRAMEWORK_CORE_AREAS = new Set([
  'event', 'dispatch', 'core', 'ui', 'lifecycle', 'hyperlink', 'iframe', 'menubar',
]);

// Pure non-UI utility areas.
const UTIL_AREAS = new Set(['util', 'dispatch']);

// Gradle modules that ARE the reusable widget library — anything in them that
// isn't framework plumbing is a widget, regardless of its `area` (e.g. the
// DataGrid subsystem lives under stroom.data.grid, not stroom.widget).
const WIDGET_MODULES = new Set(['stroom-core-client-widget']);

// Display names for functional areas (subcategory labels). Unknowns are title-cased.
const AREA_NAME = {
  planb: 'State (Plan B)', ai: 'AI', openai: 'AI (OpenAI)', importexport: 'Import / Export',
  gitrepo: 'Git repository', docstore: 'Doc store', contentstore: 'Content store',
  contentstore2: 'Content store', xmlschema: 'XML schema', datagen: 'Data generation',
  pathways: 'Pathways', planb2: 'State (Plan B)', receive: 'Data receipt', meta: 'Meta / streams',
  data: 'Data / streams', processor: 'Processor', statistics: 'Statistics', visualisation: 'Visualisation',
  dictionary: 'Dictionary', annotation: 'Annotation', security: 'Security', dashboard: 'Dashboard',
  query: 'Query / search', search: 'Search', pipeline: 'Pipeline', index: 'Index', feed: 'Feed',
  node: 'Node / cluster', cluster: 'Node / cluster', job: 'Jobs / scheduler', schedule: 'Jobs / scheduler',
  task: 'Tasks', cache: 'Cache', monitoring: 'Monitoring', config: 'Config / properties',
  credentials: 'Credentials', kafka: 'Kafka', aws: 'AWS / S3', http: 'HTTP', script: 'Script',
  documentation: 'Documentation', docs: 'Documentation', activity: 'Activity', folder: 'Folder',
  explorer: 'Explorer', analytics: 'Analytics', storedquery: 'Stored query', suggestions: 'Suggestions',
  crypto: 'Crypto', datasource: 'Data source', collection: 'Collection', searchable: 'Searchable',
  resource: 'Resource', item: 'Selection / items', receive2: 'Data receipt',
};
const titleArea = (a) => AREA_NAME[a] || (a ? a[0].toUpperCase() + a.slice(1) : '(none)');

// Widget sub-package -> subcategory grouping.
const WIDGET_SUB = {
  button: 'Input / Form', form: 'Input / Form', tickbox: 'Input / Form', valuespinner: 'Input / Form',
  spinner: 'Feedback', linecolinput: 'Input / Form', customdatebox: 'Input / Form', datepicker: 'Input / Form',
  popup: 'Popup / Overlay', contextmenu: 'Menu', menu: 'Menu', tooltip: 'Popup / Overlay', help: 'Popup / Overlay',
  tab: 'Tabs', htree: 'Tree', dropdowntree: 'Tree', xsdbrowser: 'Editor / Viewer',
  progress: 'Feedback', util: 'Widget utilities', debug: 'Widget utilities',
};
// Subcategory for a file that qualifies as a widget only via its module.
function widgetModuleSub(area, sub, role) {
  if (area === 'data' && ['grid', 'pager', 'table'].includes(sub)) return 'Data / Table';
  if (area === 'vis') return 'Visualisation';
  if (role === 'Cell') return 'Data / Cells';
  return widgetSub(area, sub, role);
}

function widgetSub(area, sub, role) {
  if (area === 'alert') return 'Dialogs (alert / confirm / prompt)';
  if (area === 'cell' || role === 'Cell') return 'Data / Cells';
  if (area === 'editor') return 'Editor / Viewer';
  if (area === 'svg') return 'Icons / SVG';
  if (area === 'item') return 'Selection / items';
  return WIDGET_SUB[sub] || 'General';
}

/* ============================ CLASSIFIER ================================== */

const MVP = new Set(['Presenter', 'View', 'ViewImpl', 'UiHandlers', 'PresenterWidget']);
const isMvp = (role) => MVP.has(role);

// Screen bucket for a feature area: Shared vs feature Screen/Dialog.
function screenBucket(area) {
  return SHARED_SCREEN_AREAS.has(area)
    ? ['Shared Screen/Dialog', titleArea(area)]
    : ['Screen/Dialog', titleArea(area)];
}

function classify(r) {
  const { layer, area, role, sub, pkg, base, ext, module } = r;

  // 0. Vendored / framework-override libraries (always in the GWT build)
  if (area === '@acegwt') return ['Widget', 'Editor (ACE wrapper, vendored)'];
  if (area === '@gwtp') return ['Framework', 'MVP framework (GWTP, vendored)'];
  if (area === '@gwt') {
    return pkg.includes('cellview')
      ? ['Widget', 'Data / Table (GWT cellview overrides)']
      : ['Widget', 'Core GWT widget overrides (vendored)'];
  }

  // 1. GWT module descriptors
  if (ext === 'gwt.xml') return ['Framework', 'GWT module descriptors'];

  // 3. REST API contracts (the JAX-RS *Resource interfaces shared with the client)
  if (role === 'Resource') return ['REST API', titleArea(area)];

  // 4. Widget library (incl. alert/confirm/prompt dialog framework)
  if (WIDGET_AREAS.has(area)) return ['Widget', widgetSub(area, sub, role)];
  if (role === 'Cell') return ['Widget', 'Data / Cells'];

  // 5. Dependency-injection wiring is pure plumbing -> Framework, in any area.
  if (role === 'Ginjector' || role === 'Module' || role === 'Provider' || base.endsWith('Gin'))
    return ['Framework', 'Dependency injection (GIN wiring)'];
  if (role === 'EntryPoint') return ['Framework', 'Application entry points'];
  if (role === 'Proxy') return ['Framework', 'MVP proxies'];

  // 6. Events / Plugins: framework by default, but route feature-specific ones to
  //    their screen (per review: "framework unless specific to a screen or widget").
  if (role === 'Event' || role === 'EventHandler') {
    if (FRAMEWORK_CORE_AREAS.has(area)) return ['Framework', 'Client events (event bus)'];
    if (WIDGET_MODULES.has(module)) return ['Widget', widgetModuleSub(area, sub, role)]; // widget's own event
    return screenBucket(area); // feature-specific event -> its screen
  }
  if (role === 'Plugin') {
    if (FRAMEWORK_CORE_AREAS.has(area)) return ['Framework', 'Plugins (doc-type / menu registration)'];
    return screenBucket(area); // feature doc-type/menu plugin -> its screen
  }

  // 7. Application shell chrome (genuine plumbing)
  if (isMvp(role) && APP_SHELL_AREAS.has(area)) return ['Framework', 'Application shell'];

  // 7b. Anything left in the widget-library module is a widget (the DataGrid
  //     subsystem, vis, etc. live outside the stroom.widget package).
  if (WIDGET_MODULES.has(module)) return ['Widget', widgetModuleSub(area, sub, role)];

  // 8. Utility
  if (UTIL_AREAS.has(area)) return ['Utility', titleArea(area)];
  if (role === 'Util' || role === 'Utils' || role === 'Constants') return ['Utility', titleArea(area)];

  // 9. Screens (MVP quartets: Presenter/View/ViewImpl/UiHandlers + .ui.xml)
  if (isMvp(role)) return screenBucket(area);

  // 10. Model — shared/api data types (DTOs, enums, config, criteria, fields)
  if (layer === 'shared' || layer === 'api') return ['Model', titleArea(area)];

  // 11. Remaining client support code -> its feature area (or shared/shell)
  if (layer === 'client') {
    if (APP_SHELL_AREAS.has(area)) return ['Framework', 'Application shell'];
    if (FRAMEWORK_CORE_AREAS.has(area)) return ['Framework', 'Event bus / core infrastructure'];
    return screenBucket(area);
  }

  return ['Uncategorised', 'review'];
}

for (const r of rows) {
  const [top, subc] = classify(r);
  r.top = top;
  r.subcategory = subc;
}

/* ============================ OUTPUT ===================================== */

// Category display order.
const TOP_ORDER = [
  'Widget', 'Screen/Dialog', 'Shared Screen/Dialog', 'Model', 'REST API',
  'Utility', 'Framework', 'Uncategorised',
];
const orderIdx = (t) => { const i = TOP_ORDER.indexOf(t); return i < 0 ? 99 : i; };

// CSV (source of truth).
const csvEsc = (s) => /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
const csvCols = ['path', 'module', 'layer', 'area', 'role', 'ext', 'top', 'subcategory'];
const csv = [csvCols.join(',')]
  .concat(rows.map((r) => csvCols.map((c) => csvEsc(String(r[c] ?? ''))).join(',')))
  .join('\n');
writeFileSync(oracle('gwt-inventory.csv'), csv + '\n');

// Distribution (printed for review + embedded in the doc).
const byTop = {};
for (const r of rows) {
  byTop[r.top] ??= { count: 0, subs: {} };
  byTop[r.top].count++;
  byTop[r.top].subs[r.subcategory] = (byTop[r.top].subs[r.subcategory] || 0) + 1;
}
const tops = Object.keys(byTop).sort((a, b) => orderIdx(a) - orderIdx(b));

console.log('TOTAL:', rows.length);
for (const t of tops) {
  console.log(`\n${t}  (${byTop[t].count})`);
  for (const [s, n] of Object.entries(byTop[t].subs).sort((a, b) => b[1] - a[1]))
    console.log(`   ${String(n).padStart(4)}  ${s}`);
}

// Markdown.
const md = [];
md.push('# Stroom GWT UI — file inventory', '');
md.push('> Generated by `stroom-gwt-suite/tools/build-inventory.mjs` from the Stroom source.');
md.push('> Scope: `src/main` files under a `client`/`shared`/`api` package segment, extensions');
md.push('> `.java` / `.ui.xml` / `.gwt.xml` / `.css` (tests excluded). Files **not** compiled into');
md.push('> the GWT app are excluded — a package is GWT-translatable only where a `.gwt.xml`');
md.push('> `<source path>` whitelists it, so most of the server-side `*.api` layer is left out.');
md.push('> Paths are relative to the');
md.push('> `stroom` project root. Categorisation is heuristic (path + filename role); tune the rule');
md.push('> inputs at the top of the build script and re-run. The companion `gwt-inventory.csv` is the');
md.push('> machine-readable source of truth.', '');
md.push(`**Total files: ${rows.length}**  ·  client ${rows.filter((r) => r.layer === 'client').length} · shared ${rows.filter((r) => r.layer === 'shared').length} · api ${rows.filter((r) => r.layer === 'api').length}`, '');
md.push('## Category summary', '');
md.push('| Top-level category | Files |', '| --- | ---: |');
for (const t of tops) md.push(`| [${t}](#${anchor(t)}) | ${byTop[t].count} |`);
md.push(`| **Total** | **${rows.length}** |`, '');

for (const t of tops) {
  md.push(`## ${t}`, '');
  md.push(`_${byTop[t].count} files._`, '');
  const subs = Object.entries(byTop[t].subs).sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));
  // Subcategory summary
  md.push('| Subcategory | Files |', '| --- | ---: |');
  for (const [s, n] of subs) md.push(`| ${s} | ${n} |`);
  md.push('');
  for (const [s] of subs) {
    md.push(`### ${t} — ${s}`, '');
    md.push('| File | Module | Role |', '| --- | --- | --- |');
    const files = rows.filter((r) => r.top === t && r.subcategory === s)
      .sort((a, b) => a.path.localeCompare(b.path));
    for (const r of files)
      md.push(`| \`${r.path}\` | ${r.module} | ${r.role || '—'} |`);
    md.push('');
  }
}

function anchor(s) {
  return s.toLowerCase().replace(/[^a-z0-9 -]/g, '').replace(/ /g, '-');
}

writeFileSync(oracle('gwt-inventory.md'), md.join('\n'));
console.log('\nWrote oracles/gwt-inventory.csv and oracles/gwt-inventory.md');
