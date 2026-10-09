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

//
// The behaviour denominator — BEHAVIOUR-PLAN.md § 2 and § B1.
//
//   node stroom-stroom-gwt-suite/tools/build-handler-inventory.mjs
//     -> oracles/handler-inventory.json   (every handler registration, its label, whether the walk fired it)
//     -> oracles/handler-inventory.md     (handlers exercised / declared, and the unexercised by what would fire them)
//
// A behaviour in a GWT presenter is a HANDLER: the code that runs when the user does something.
// Reaching a screen (COVERAGE-PLAN.md, DOORS) says nothing about whether its handlers do what the
// source says; this inventory is the list of them, mined from the source the way the capability
// specs mine buttons, so that a handler nothing fires is a gap and not noise.
//
// What is mined, by registration idiom:
//
//   click       `x.addClickHandler(…)`, `@UiHandler("x")` on a ClickEvent, a menu item's `.command(…)`
//   ok / cancel `.onHideRequest(e -> { if (e.isOk()) … })` — two behaviours per site, and the OK
//               branch is where the writes are
//   selection   addSelectionHandler / addSelectionChangeHandler / addDataSelectionHandler
//   value       addValueChangeHandler / addChangeHandler
//   key         addKeyDownHandler / addKeyUpHandler / addDomHandler(…, KeyDownEvent.getType())
//   context     addContextMenuHandler
//   dblclick / mousedown / dirty / close / open
//
// Attribution is by DECLARING class, never by inheritance: `ButtonPanel`'s one click handler is
// inherited by fifty presenters and is one behaviour. The join to the walk uses the coverage
// ledger's own node attribution (`stroom-gwt-suite/out/node-presenters.json`), so a click handler counts
// as exercised when an affordance with its label was clicked on a node the ledger attributes to
// the declaring class or a subclass of it.
import { readdirSync, readFileSync, statSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { SOURCE, oracle, outPath } from '../lib/paths.mjs';

const GWT = SOURCE;
const ROOTS = [
  'stroom-core-client/src/main/java/stroom',
  'stroom-core-client-widget/src/main/java/stroom',
  'stroom-app-client/src/main/java/stroom',
  'stroom-statistics/stroom-statistics-client/src/main/java/stroom',
];
const CRAWLS = outPath('crawl');
const NODE_PRESENTERS = outPath('node-presenters.json');

function* javaFiles(dir) {
  let entries;
  try { entries = readdirSync(dir); } catch { return; }
  for (const e of entries) {
    const p = join(dir, e);
    if (statSync(p).isDirectory()) yield* javaFiles(p);
    else if (e.endsWith('.java')) yield p;
  }
}
const cls = (p) => p.split('/').pop().replace(/\.java$/, '');
const files = [];
for (const r of ROOTS) files.push(...javaFiles(join(GWT, r)));
/** Comments stripped: dead code is the one input that looks exactly like live code to a regex. */
const readSrc = (f) => readFileSync(f, 'utf8')
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .split('\n').map((l) => l.replace(/^\s*\/\/.*$/, '')).join('\n');
const lineOf = (src, index) => src.slice(0, index).split('\n').length;

// ── SvgPresets: constant -> title ────────────────────────────────────────────
const preset = new Map();
{
  const f = files.find((p) => cls(p) === 'SvgPresets');
  const src = f ? readSrc(f) : '';
  for (const m of src.matchAll(/public\s+static\s+final\s+Preset\s+([A-Z0-9_]+)\s*=\s*(enabled|disabled)\([^,]+,\s*"([^"]*)"/g)) {
    preset.set(m[1], m[3]);
  }
}

// ── The class taxonomy, from the reachability graph ──────────────────────────
const reach = existsSync(oracle('reachability-graph.json'))
  ? JSON.parse(readFileSync(oracle('reachability-graph.json'), 'utf8')) : { presenters: [] };
const presenterNames = new Set((reach.presenters ?? []).map((p) => p.name ?? p));
const kindOfClass = (name, src) => {
  if (presenterNames.has(name)) return 'presenter';
  if (/ViewImpl$/.test(name) || /extends\s+ViewImpl\b|extends\s+ViewWithUiHandlers\b/.test(src)) return 'view';
  if (/Plugin$/.test(name)) return 'plugin';
  if (/Presenter$/.test(name)) return 'presenter';
  return 'widget';
};

// ── ui.xml: field -> title / text, for @UiHandler methods in views ──────────
const uiXml = new Map(); // ViewImpl class -> Map(field -> label)
for (const r of ROOTS) {
  const resDir = join(GWT, r.replace('/java/', '/resources/'));
  const walk = (dir) => {
    let entries;
    try { entries = readdirSync(dir); } catch { return; }
    for (const e of entries) {
      const p = join(dir, e);
      if (statSync(p).isDirectory()) walk(p);
      else if (e.endsWith('.ui.xml')) {
        const xml = readFileSync(p, 'utf8');
        const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
        const m = new Map();
        // A box's own title/text names a BUTTON; a text box is named by the `FormGroup` around it,
        // which carries `label="Unique User Identity"` and usually `identity="subjectId"` as well.
        // Scan the tags in order and keep the FormGroup label in hand, so a `ui:field` inside one
        // is named by it even when `identity` is absent — that label is exactly what the walk
        // reads off the rendered `.form-group-label`, which is what joins the two.
        let group = null;
        for (const t of xml.matchAll(/<([A-Za-z:]+)\b[^>]*>/g)) {
          const [tag, tagName] = t;
          const label = /\blabel="([^"]+)"/.exec(tag)?.[1] ?? null;
          if (/FormGroup$/.test(tagName)) {
            group = label;
            const identity = /\bidentity="([^"]+)"/.exec(tag)?.[1] ?? null;
            if (identity && label) m.set(identity, label);
          }
          const field = /\bui:field="([^"]+)"/.exec(tag)?.[1] ?? null;
          if (field) {
            // A label can be the element's own TEXT rather than an attribute:
            // `<g:Label ui:field="assignYourself">Assign Yourself</g:Label>`.
            const after = xml.slice(t.index + tag.length);
            const inner = tag.endsWith('/>') ? null
              : norm(after.slice(0, after.indexOf('<')).replace(/\s+/g, ' '));
            const own = /\btitle="([^"]+)"/.exec(tag)?.[1] ?? /\btext="([^"]+)"/.exec(tag)?.[1] ?? (inner || null);
            // The FormGroup's label belongs to the BOX it wraps, not to a button inside it: a
            // button is named by its own text, and handing it the group's label moved thirteen
            // click handlers from `unlabelled` to a label no affordance could ever match.
            const isBox = /(TextBox|TextArea|DateBox|DateTimeBox|TimeBox|ValueSpinner|SelectionBox|ListBox|TickBox|Editor)$/.test(tagName);
            // A `title` on a BOX is a tooltip, and Stroom's tooltips are prose: OpenAIModel's
            // baseUrl is titled "Override the URL for the vector embeddings endpoint. Example: …".
            // The box's NAME is its FormGroup's label. On a button, `title` IS the name.
            const best = isBox ? (group ?? own) : own;
            // Whatever the source, a sentence is help text, not a label.
            const usable = best && best.length <= 60 && !/\.\s/.test(best) ? best : null;
            if (usable && !m.get(field)) m.set(field, usable);
          }
        }
        uiXml.set(e.replace(/\.ui\.xml$/, ''), m);
      }
    }
  };
  walk(resDir);
}

/**
 * `ui:field` name -> every label any ui.xml gives it. A handler whose receiver names a field
 * declared in a view it does not own can be resolved through this, but only when the name is
 * unambiguous across the whole application.
 */
const uiFieldLabels = new Map();
for (const [, fields] of uiXml) {
  for (const [field, label] of fields) {
    if (!label) continue;
    if (!uiFieldLabels.has(field)) uiFieldLabels.set(field, new Set());
    uiFieldLabels.get(field).add(label);
  }
}

// ── Mine every registration ──────────────────────────────────────────────────
const IDIOMS = [
  ['click', /([A-Za-z_][A-Za-z0-9_]*(?:\(\))?)\s*\.\s*addClickHandler\s*\(/g],
  ['selection', /([A-Za-z_][A-Za-z0-9_().]*)\s*\.\s*(?:addSelectionHandler|addSelectionChangeHandler|addDataSelectionHandler)\s*\(/g],
  ['value', /([A-Za-z_][A-Za-z0-9_().]*)\s*\.\s*(?:addValueChangeHandler|addChangeHandler)\s*\(/g],
  ['key', /([A-Za-z_][A-Za-z0-9_().]*)\s*\.\s*(?:addKeyDownHandler|addKeyUpHandler|addKeyPressHandler)\s*\(/g],
  ['context', /([A-Za-z_][A-Za-z0-9_().]*)\s*\.\s*addContextMenuHandler\s*\(/g],
  ['dblclick', /([A-Za-z_][A-Za-z0-9_().]*)\s*\.\s*addDoubleClickHandler\s*\(/g],
  ['mousedown', /([A-Za-z_][A-Za-z0-9_().]*)\s*\.\s*addMouseDownHandler\s*\(/g],
  ['dirty', /([A-Za-z_][A-Za-z0-9_().]*)\s*\.\s*addDirtyHandler\s*\(/g],
  ['close', /([A-Za-z_][A-Za-z0-9_().]*)\s*\.\s*addCloseHandler\s*\(/g],
  ['open', /([A-Za-z_][A-Za-z0-9_().]*)\s*\.\s*addOpenHandler\s*\(/g],
];
const handlers = [];
const perClass = new Map(); // class -> { kind, file }
for (const f of files) {
  const src = readSrc(f);
  const name = cls(f);
  const classKind = kindOfClass(name, src);
  const extendsM = /class\s+[A-Za-z0-9_]+(?:<[^>]*>)?\s+extends\s+([A-Za-z0-9_]+)/.exec(src);
  perClass.set(name, { kind: classKind, extends: extendsM?.[1] ?? null });

  /** The label a receiver field carries, from how it was made or titled in this file. */
  const labelOf = (recv) => {
    const field = recv.replace(/\(\)$/, '').split('.').pop();
    // `x.setTitle("…")` / `x.setText("…")` wins over the preset it was made from: QueryPresenter
    // makes `addTermButton` from SvgPresets.ADD ("Add") and then titles it "Add Term", and the
    // title is what the button reader sees. The LAST such call is the one that stands.
    // ... but a SENTENCE is a tooltip, not a label: `RuleSetSettingsPresenter` re-titles its
    // disable button at runtime with "Select one or more rules with the same enabled state to
    // enable/disable them." for the no-selection case, and taking the last call blindly made that
    // the button's name. Keep the last call that reads like a LABEL.
    const isLabel = (t) => t && t.length <= 60 && !/\.\s/.test(t) && !/\.$/.test(t);
    const titled = [...src.matchAll(new RegExp(`${field}\\s*\\.\\s*set(?:Title|Text)\\(\\s*"([^"]*)"`, 'g'))]
      .filter((m2) => isLabel(m2[1])).pop()
      ?? new RegExp(`${field}\\s*=\\s*new\\s+[A-Za-z]+\\(\\s*"([^"]*)"`).exec(src);
    if (titled && isLabel(titled[1])) return titled[1];
    // `x = view.addButton(SvgPresets.EDIT[.title("…")])` — the preset's title or its override.
    const made = new RegExp(`${field}\\s*=\\s*[^;]*?SvgPresets\\s*\\.\\s*([A-Z0-9_]+)\\s*(?:\\.\\s*title\\(\\s*"([^"]*)"\\s*\\))?`).exec(src);
    if (made) return made[2] ?? preset.get(made[1]) ?? null;
    // `x = getView().addButton(new Preset(SvgImage.EDIT, "Edit Selected Schedule", false))` — the
    // Preset's SECOND argument is the title. ExecutionScheduleManager builds all seven of its
    // buttons this way, and the SvgPresets pattern above cannot see them.
    const inline = new RegExp(`${field}\\s*=\\s*[^;]*?new\\s+Preset\\s*\\(\\s*[A-Za-z0-9_.]+\\s*,\\s*"([^"]*)"`).exec(src);
    if (inline) return inline[1];
    // A view's @UiField: the ui.xml declares its title, text, or inner text.
    const fromXml = uiXml.get(name)?.get(field);
    if (fromXml) return fromXml;
    // `x = listPresenter.add(EDIT_RULE_SVG_PRESET)` where the file declares
    // `static final Preset EDIT_RULE_SVG_PRESET = SvgPresets.EDIT.title("Edit rule")`. The list
    // editors — content templates, data retention — name all their buttons through such constants.
    const viaConst = new RegExp(`${field}\\s*=\\s*[^;]*?\\b([A-Z][A-Z0-9_]{2,})\\b`).exec(src);
    if (viaConst && viaConst[1] !== 'SVG') {
      const decl = new RegExp(`\\b${viaConst[1]}\\s*=\\s*[^;]*?SvgPresets\\s*\\.\\s*([A-Z0-9_]+)\\s*(?:\\.\\s*title\\(\\s*"([^"]*)"\\s*\\))?`).exec(src);
      if (decl) return decl[2] ?? preset.get(decl[1]) ?? null;
    }
    const getter = /^get([A-Z]\w*)$/.exec(field);
    if (getter) {
      const asField = getter[1][0].toLowerCase() + getter[1].slice(1);
      // A PRESENTER reaching a view's button — `getView().getSendTestEmailBtn()`: the label is the
      // view's, under the field the getter is named for.
      for (const view of [name.replace(/Presenter$/, 'ViewImpl'), name.replace(/Presenter$/, 'View')]) {
        const fromView = uiXml.get(view)?.get(asField);
        if (fromView) return fromView;
      }
      // Or a getter over this class's OWN field — `getApplySelectionButton()` returning
      // `applySelectionButton`. Resolve the field, but only one hop, so a getter named for itself
      // cannot loop.
      // The button may belong to a view this class merely USES: `ExecutionScheduleManager` registers
      // a handler on `getApplySelectionButton()`, declared on another presenter's view interface
      // and implemented in two ViewImpls. Accept a global ui.xml match only when every declaration
      // of that field agrees on the label — an ambiguous name stays unlabelled.
      const global = uiFieldLabels.get(asField);
      if (global && global.size === 1) return [...global][0];
      if (new RegExp(`\\b${asField}\\b`).test(src) && asField !== field) {
        const asPreset = new RegExp(`${asField}\\s*=\\s*[^;]*?new\\s+Preset\\s*\\(\\s*[A-Za-z0-9_.]+\\s*,\\s*"([^"]*)"`).exec(src);
        if (asPreset) return asPreset[1];
        const asSvg = new RegExp(`${asField}\\s*=\\s*[^;]*?SvgPresets\\s*\\.\\s*([A-Z0-9_]+)\\s*(?:\\.\\s*title\\(\\s*"([^"]*)"\\s*\\))?`).exec(src);
        // Group 2 is the title override; group 1 is only the PRESET's name, which is an id, not a
        // label — it has to go through the preset table or not at all.
        if (asSvg) return asSvg[2] ?? preset.get(asSvg[1]) ?? null;
      }
    }
    return null;
  };

  /**
   * Buttons whose caption CHANGES with their state, so one mined label can only ever miss.
   *
   * `RefreshButton:78` is titled "Refresh", but `updateRefreshState():160-164` re-titles it
   * "Resume Update", "Pause Update" or "Not Updating" depending on what the screen is doing. And the
   * auto-refresh toggle carries whichever of its two titles is NOT its current state —
   * `JobNodeListPresenter:72-73` names both constants explicitly, and they are crossed over:
   * `AUTO_REFRESH_ON_TITLE = "Turn Auto Refresh Off"`.
   *
   * So these receivers get a SET of acceptable labels, every one of them taken from a literal in the
   * source rather than guessed, and the join accepts whichever the walk actually clicked. Twelve
   * handlers were `unlabelled` for want of this — among them `Pager`'s own Refresh, which every grid
   * screen in the application has and the walk presses constantly.
   */
  const LABEL_SETS = [
    { when: /^(getRefreshButton|refresh|refreshButton)$/i,
      labels: ['Refresh', 'Resume Update', 'Pause Update', 'Not Updating'] },
    { when: /^autoRefreshButton$/i, labels: ['Turn Auto Refresh On', 'Turn Auto Refresh Off'] },
  ];
  const labelsOf = (recv) => {
    const field = String(recv).replace(/\(\)$/, '').split('.').pop();
    return LABEL_SETS.find((x) => x.when.test(field))?.labels ?? null;
  };

  for (const [kind, re] of IDIOMS) {
    for (const m of src.matchAll(re)) {
      const recv = m[1];
      if (/^(this|super|event|e)$/.test(recv)) continue;
      const one = kind === 'click' ? labelOf(recv) : null;
      const many = kind === 'click' && !one ? labelsOf(recv) : null;
      handlers.push({ class: name, classKind, kind, receiver: recv, label: one ?? many?.[0] ?? null,
        ...(many ? { labels: many } : {}), line: lineOf(src, m.index) });
    }
  }
  // `@UiHandler("x") void onX(ClickEvent e)` in a view: the ui.xml gives x its title/text.
  for (const m of src.matchAll(/@UiHandler\(\s*"([^"]+)"\s*\)\s*(?:public\s+|private\s+|protected\s+)?void\s+[A-Za-z0-9_]+\s*\(\s*(?:final\s+)?([A-Za-z]+)/g)) {
    const kind = /Click/.test(m[2]) ? 'click' : /Key/.test(m[2]) ? 'key' : /Change|Value/.test(m[2]) ? 'value' : /Selection/.test(m[2]) ? 'selection' : 'dom';
    // The ui.xml first, then how the field was MADE: `CharacterNavigatorViewImpl` declares bare
    // `<btn:SvgButton ui:field="showHeadCharactersBtn"/>` and titles it in Java —
    // `SvgButton.create(SvgPresets.FAST_BACKWARD_BLUE.title("Show Beginning"))`. Reading only the
    // xml left every navigator and step-control button unlabelled.
    // A `@UiHandler` field gets the same state-dependent label sets as a directly-registered one:
    // `Pager`'s own Refresh is a UiHandler, and so are the navigators' — three of the twelve.
    const uiOne = uiXml.get(name)?.get(m[1]) ?? labelOf(m[1]);
    const uiMany = kind === 'click' && !uiOne ? labelsOf(m[1]) : null;
    handlers.push({ class: name, classKind, kind, receiver: m[1], label: uiOne ?? uiMany?.[0] ?? null,
      ...(uiMany ? { labels: uiMany } : {}), line: lineOf(src, m.index), via: 'UiHandler' });
  }
  // A menu item's command: `.text("…") … .command(…)` in one builder chain (either order).
  for (const m of src.matchAll(/\.command\s*\(/g)) {
    const chainStart = src.lastIndexOf('new ', m.index);
    const chain = src.slice(Math.max(chainStart, m.index - 700), m.index + 400);
    const text = /\.text\(\s*"([^"]*)"\s*\)/.exec(chain)?.[1] ?? null;
    handlers.push({ class: name, classKind, kind: 'command', receiver: 'menu item', label: text, line: lineOf(src, m.index) });
  }
  // A dialog's OK / Cancel: `onHideRequest(e -> { if (e.isOk()) … })`.
  for (const m of src.matchAll(/\.onHideRequest\s*\(/g)) {
    const body = src.slice(m.index, m.index + 900);
    const twoWay = /\.isOk\(\)/.test(body);
    handlers.push({ class: name, classKind, kind: twoWay ? 'ok' : 'hide', receiver: 'dialog', label: 'OK', line: lineOf(src, m.index) });
    if (twoWay) handlers.push({ class: name, classKind, kind: 'cancel', receiver: 'dialog', label: 'Cancel', line: lineOf(src, m.index) });
  }
  // `addDomHandler(…, KeyDownEvent.getType())` and friends.
  for (const m of src.matchAll(/addDomHandler\s*\([\s\S]{0,400}?,\s*([A-Za-z]+Event)\s*\.\s*getType\(\)\s*\)/g)) {
    // Every mouse event used to collapse into one kind called `mousedown`, so "mousedown 0/27"
    // was really four different gestures — a press, a release, a move and a hover — and no driver
    // could tell from the number what it owed. `Dialog` wants press-move-release (a drag);
    // `Spinner` wants all four, each with its own class (`valueSpinner-arrowUpPressed` vs
    // `…Hover`); `MenubarItem` wants only the hover. They are separate contracts, so count them
    // separately.
    const mouse = /^Mouse(Down|Up|Move|Over|Out)Event$/.exec(m[1]);
    // DoubleClick BEFORE Click: `DoubleClickEvent` contains "Click", so it was being counted as a
    // plain click — three handlers in the wrong column, and `dblclick` reading 0/3 when it is 0/6.
    const kind = /Key/.test(m[1]) ? 'key'
      : /DoubleClick/.test(m[1]) ? 'dblclick'
        : /Click/.test(m[1]) ? 'click'
          : mouse ? `mouse${mouse[1].toLowerCase()}`
            : /Mouse/.test(m[1]) ? 'mousedown' : /Focus|Blur/.test(m[1]) ? 'focus' : 'dom';
    handlers.push({ class: name, classKind, kind, receiver: m[1], label: null, line: lineOf(src, m.index), via: 'addDomHandler' });
  }
}

// ── The messages a presenter can show (BEHAVIOUR-PLAN.md § B3) ───────────────
// Every `AlertEvent.fireWarn / fireError / fireInfo(source, <message>, …)`, `ConfirmEvent.fire(source,
// <message>, …)`, `throw new ValidationException(<message>)` and `ErrorEvent.fire(source, <message>)`
// whose message has a literal part. The literal part becomes a TEMPLATE (`…` for the variable
// parts), joined to the alerts the walk read (lib/alerts.mjs) by prefix. A message never observed
// is a validation the walk has never provoked — B3's matrix is exactly that list.
const messages = [];
const templateOf = (expr) => {
  const lits = [...expr.matchAll(/"((?:[^"\\]|\\.)*)"/g)].map((m) => m[1].replace(/\\n/g, ' ').replace(/\\"/g, '"'));
  if (!lits.length) return null;
  const plain = expr.replace(/"(?:[^"\\]|\\.)*"/g, '\u0000');
  const parts = plain.split('\u0000');
  let out = '';
  for (let i = 0; i < parts.length; i += 1) {
    if (parts[i].replace(/[\s+()]/g, '')) out += '…';
    if (i < lits.length) out += lits[i];
  }
  return out.replace(/\s+/g, ' ').trim();
};
for (const f of files) {
  const src = readSrc(f);
  const name = cls(f);
  const kindOf = { fireWarn: 'warn', fireError: 'error', fireInfo: 'info', fire: 'confirm' };
  for (const m of src.matchAll(/(AlertEvent|ConfirmEvent|ErrorEvent)\s*\.\s*(fireWarn|fireError|fireInfo|fire)\s*\(/g)) {
    const args = splitArgs(src.slice(m.index + m[0].length, m.index + m[0].length + 700));
    const expr = (args[1] ?? '').trim();
    const template = templateOf(expr);
    if (!template || template === '…') continue;
    const kind = m[1] === 'ErrorEvent' ? 'error-event' : m[1] === 'ConfirmEvent' ? 'confirm' : kindOf[m[2]];
    messages.push({ class: name, classKind: kindOfClass(name, src), kind, template, line: lineOf(src, m.index) });
  }
  for (const m of src.matchAll(/throw new ValidationException\s*\(/g)) {
    const args = splitArgs(src.slice(m.index + m[0].length, m.index + m[0].length + 300));
    const template = templateOf((args[0] ?? '').trim());
    if (template && template !== '…') messages.push({ class: name, classKind: kindOfClass(name, src), kind: 'validation-exception', template, line: lineOf(src, m.index) });
  }
}
/** Split an argument list at top-level commas, so a nested `f(a, b)` stays one argument. */
function splitArgs(text) {
  const out = [];
  let depth = 0;
  let cur = '';
  let inStr = false;
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    if (inStr) { cur += ch; if (ch === '"' && text[i - 1] !== '\\') inStr = false; continue; }
    if (ch === '"') { inStr = true; cur += ch; continue; }
    if (ch === '(' || ch === '[' || ch === '{') depth += 1;
    if (ch === ')' || ch === ']' || ch === '}') { if (depth === 0) break; depth -= 1; }
    if (ch === ',' && depth === 0) { out.push(cur); cur = ''; continue; }
    cur += ch;
  }
  out.push(cur);
  return out;
}

// ── Join to the walk ─────────────────────────────────────────────────────────
// Subclasses: a node attributed to FeedPresenter fires the click handlers DocTabPresenter declared.
const subclassesOf = new Map();
for (const [name, { extends: base }] of perClass) {
  if (!base) continue;
  subclassesOf.set(base, [...(subclassesOf.get(base) ?? []), name]);
}
const closureOf = (name) => {
  const out = new Set([name]);
  const q = [name];
  while (q.length) for (const c of subclassesOf.get(q.pop()) ?? []) if (!out.has(c)) { out.add(c); q.push(c); }
  return out;
};
/**
 * A view and its presenter are the same surface to the ledger, which attributes a node to the
 * PRESENTER — so a handler declared in `EditAccountViewImpl` is never reached through the view's
 * own name. GWT-P names them in pairs, so the pair is the link: `FooViewImpl` / `FooView` <->
 * `FooPresenter`, in both directions, and only when the partner class actually exists.
 */
const partnerOf = (name) => {
  const n = String(name ?? '');
  const other = /ViewImpl$/.test(n) ? n.replace(/ViewImpl$/, 'Presenter')
    : /View$/.test(n) ? n.replace(/View$/, 'Presenter')
      : /Presenter$/.test(n) ? n.replace(/Presenter$/, 'ViewImpl') : null;
  return other && perClass.has(other) ? other : null;
};

const nodePresenters = existsSync(NODE_PRESENTERS) ? JSON.parse(readFileSync(NODE_PRESENTERS, 'utf8')) : {};
/** every alert / confirm / dialog body the walk read. */
const observedTexts = new Set();
/** presenter -> Map(label -> {outcomes:Set, blocked:boolean, n}) from every affordance on a node it owns. */
const clickedOn = new Map();
/** presenter -> kinds of post-action states seen under it (selection, tree, …). */
const statesOn = new Map();
/** every menu-item label ever clicked, with outcomes (menu nodes carry no presenter). */
const menuClicked = new Map();
/** every affordance label the walk clicked ANYWHERE, attributed or not. */
const anyClicked = new Map();
/** presenter -> the FIELD LABELS the walk pressed Enter in, under it. */
const enterOn = new Map();
/** presenter -> the FIELD LABELS the walk TYPED INTO under it (the illegal-value driver). */
const typedOn = new Map();
/** presenters under which typing actually marked the document dirty. */
const dirtiedOn = new Set();
/**
 * Presenters under which typing IN AN ACE EDITOR marked the document dirty. That is stronger
 * evidence than it looks: a code document's editor is wired
 * `registerHandler(editorPresenter.addValueChangeHandler(event -> onChange()))`
 * (XsltPresenter:56, TextConverterPresenter:73) and `onChange()` is what calls `setDirty(true)`.
 * So a `dirty` pass on an ace field PROVES that value handler ran — it is the only path from a
 * keystroke to the Save button enabling. The label join can never see it, because an editor has
 * no `FormGroup` label naming `editorPresenter`.
 */
const aceDirtiedOn = new Set();
/**
 * The dirty CHAIN. A `dirty` pass means Save lit up, and Save only lights up because the dirtiness
 * travelled from the tab's own presenter to the document — so every link on that path ran:
 *
 *   the tab's presenter fires DirtyEvent
 *   DocTabProvider:65          addDirtyHandler -> getPresenter().addDirtyHandler(handler)
 *   TabContentProvider:115     registerHandler(currentTabProvider.addDirtyHandler(this::fireEvent))
 *   DocTabPresenter:94         registerHandler(tabContentProvider…)   (already credited)
 *   DocTabPresenter:274        saveButton.setEnabled(isDirty())        <- what the driver observed
 *
 * `TabContentProvider:100` is deliberately NOT on this list: its own comment says it exists because
 * "a replacement provider bypasses getPresenter()", so it fires only when a provider is REPLACED,
 * which an ordinary dirty pass does not show. The receiver is matched, not just the class, to keep
 * the two apart.
 *
 * `MarkdownTabProvider:36` is a narrower case — `addDirtyHandler(event -> fireDirtyEvent(true))` on a
 * `MarkdownEditPresenter` — so it needs a dirty pass on a DOCUMENTATION tab specifically.
 */
const DIRTY_CHAIN = new Map([
  ['TabContentProvider', new Set(['currenttabprovider'])],
  ['DocTabProvider', new Set(['presenter'])],
]);
const MARKDOWN_CHAIN = new Map([['MarkdownTabProvider', new Set(['markdowneditpresenter'])]]);
let tabDirty = false;
let markdownDirty = false;
/**
 * class -> the mouse KINDS a gesture proved fired on it (lib/mouse.mjs).
 *
 * These three classes are not attributed to any node, and never will be: `nodePresenters` names the
 * presenter behind a screen, while `Dialog`, `ResizableDialog` and `Spinner` are the shared widgets
 * underneath every one of them. But there is exactly one of each in the application, so the gesture
 * itself identifies the class:
 *
 *   a drag that moved a `.dialog-popup`          -> Dialog's down + move + up
 *   a drag or resize of a `.resizableDialog-popup` -> ResizableDialog's down + move + up
 *   a spinner arrow gesture that passed          -> Spinner's handler FOR THAT EVENT
 *
 * Only the event that actually fired is credited: a hover does not pay for a press. The two
 * registrations per event (the increment arrow and the decrement arrow) are indistinguishable in
 * the source — `addDomHandler`'s receiver is captured as the event type — and the driver presses
 * both arrows, the second to put the value back, so both are credited together.
 */
/**
 * The five mouse kinds, split out of the old single `mousedown` bucket. Declared HERE rather than
 * beside `EXERCISABLE_NOW`, because the crawl-reading loop below uses it and a `const` is in its
 * temporal dead zone until its own line runs.
 */
const MOUSE_KINDS = new Set(['mousedown', 'mouseup', 'mousemove', 'mouseover', 'mouseout']);
const mouseProven = new Map();
/**
 * Classes whose `close` handler a completed SELECTION proves fired, with the receivers it covers.
 *
 * `BaseSelectionBox:171-178` — picking an item runs the selection handler, which sets the value and
 * calls `hidePopup()` -> `popup.hide()` -> `SelectionPopup.hide()` -> `popupPanel.hide()`, and a GWT
 * `PopupPanel.hide()` fires CloseEvent. So the handlers registered on that popup ran:
 *
 *   BaseSelectionBox:163  handlerRegistrations.add(popup.addCloseHandler(...))
 *   SelectionPopup:47     registerHandler(popupPanel.addCloseHandler(event -> hide()))
 *   SelectionPopup:135    addCloseHandler(...) — the method BaseSelectionBox:163 calls
 *
 * `SelectionPopup:48` watches the selectionList's OWN close event, which is a different thing, and is
 * deliberately NOT credited — hence matching the receiver and not just the class. `MyDateBox`'s
 * receiver also reduces to "popup", which is why the class has to match too.
 */
/**
 * What a completed SELECTION also proves beyond the popup closing: the CLICK that opened it.
 *
 * `BaseSelectionBox:44` registers a click handler on `svgIconBox`, and that icon is exactly what the
 * field driver presses to open the popup (lib/dirty.mjs's `select` nudge targets `.svgIconBox-icon`,
 * because a SelectionBox does not open from its text box). So a selection that completed went through
 * this handler. `ScheduleBox`, `DateTimeBox` and `TimeBox` have an `svgIconBox` too and are NOT
 * credited: nothing opens those.
 */
const CLICK_BY_SELECTION = new Map([['BaseSelectionBox', new Set(['svgicon'])]]);
const CLOSE_BY_SELECTION = new Map([
  ['BaseSelectionBox', new Set(['popup'])],
  ['SelectionPopup', new Set(['popuppanel'])],
]);
/** Did any completed selection happen anywhere in the walk? */
let selectionMade = false;
/**
 * `Pager`'s receivers that the row-range gesture drove (lib/range.mjs). Like the dialog and the
 * spinner, `Pager` is one class under every grid in the application and is never attributed to a
 * node, so the gesture identifies it.
 *
 * Which receivers depends on what the gesture actually touched, and the two sides are separate:
 *
 *   lblFrom click -> txtFrom focus, type, ENTER, blur      always, when the jump passes
 *   lblTo   click -> txtTo   focus, type, ENTER, blur      only on the restore pass (`toSide`)
 *
 * `fireMoveEvent` reads both boxes (Pager:202-203), which is what lets the restore press Enter in
 * txtTo and still apply the from value — so one restore earns the four handlers the jump never
 * touches. Without that the claim would be eight handlers and the truth would be four.
 */
const rangeProven = new Set();
/**
 * Presenters whose OK handler a compensating cycle drove with the write ACTUALLY GOING THROUGH
 * (out/cycles/cycles.json).
 *
 * `ok-blocked` means the OK fired and the read-only guard aborted the request, so nothing proved the
 * handler's own work — it may have thrown on the way to the wire for all the walk could tell. A cycle
 * run with MUTATE=1 removes exactly that doubt: the row appeared, survived a reload and was deleted
 * again.
 *
 * Keyed on the CREATE phase passing and a declared write having been made, not on the cycle's overall
 * verdict: `annotation comments` fails at its EDIT phase (bug #45, a 500 from updateAnnotationTag) and
 * its create still went through, so `AnnotationTagEditPresenter`'s OK handler plainly ran.
 */
const cycleProved = new Set();
{
  const f = outPath('cycles/cycles.json');
  if (existsSync(f)) {
    const { results = [] } = JSON.parse(readFileSync(f, 'utf8'));
    for (const r of results) {
      const made = (r.phases ?? []).some((p) => p.ok && /^create /.test(p.what));
      if (made && (r.writes ?? []).length) for (const c of r.okPresenters ?? []) cycleProved.add(c);
    }
  }
}
const proveMouse = (cls, kinds) => {
  if (!cls) return;
  const set = mouseProven.get(cls) ?? new Set();
  for (const k of kinds) set.add(k);
  mouseProven.set(cls, set);
};
if (existsSync(CRAWLS)) {
  for (const file of readdirSync(CRAWLS).filter((x) => x.startsWith('crawl-') && x.endsWith('.json'))) {
    const d = JSON.parse(readFileSync(join(CRAWLS, file), 'utf8'));
    const run = d.summary?.area ?? file;
    const attributed = nodePresenters[run] ?? {};
    const byId = new Map((d.nodes ?? []).map((n) => [n.id, n]));
    // A node the ledger did not attribute inherits its nearest attributed ANCESTOR's presenters:
    // the ledger names a seeded dialog, and the post-action states and dialogs walked under it
    // (`… / Add / after OK (a row added client-side) / Edit`) are that presenter's surface too.
    // Keyed by PATH, not id: an id is `<kind> / <path>`, and a post-action state's parent is an
    // `editor-tab` — a different kind — so stripping segments off the id never met it.
    const byPath = new Map();
    for (const [id, ps] of Object.entries(attributed)) byPath.set(id.slice(id.indexOf(' / ') + 3), ps);
    const inherited = (id) => {
      let cur = id.slice(id.indexOf(' / ') + 3);
      while (cur) {
        if (byPath.get(cur)?.length) return byPath.get(cur);
        const cut = cur.lastIndexOf(' / ');
        if (cut < 0) return [];
        cur = cur.slice(0, cut);
      }
      return [];
    };
    for (const a of d.affordances ?? []) {
      if (a.said?.text) observedTexts.add(a.said.text);
      // An affordance the walk DECLINED to click is not evidence that its handler ran. The walker
      // records one for `Sign Out`, `Delete Store` and now everything on `LEAVES_THE_PAGE`, so that
      // the ledger shows what was deliberately left alone — but those records carry a LABEL, and the
      // label is what the click and command joins read, so each one was being counted as a click.
      // `Export Table` came back `HIT … (attributed)` on a run where nothing clicked it: a safety net
      // manufacturing coverage. Pre-existing for SELF_DESTRUCTIVE; my denylist made it visible.
      if (a.kind === 'skipped' || a.outcome === 'skipped' || a.outcome === 'not clicked') continue;
      if (a.said?.caption && /^(Confirm|Alert)$/.test(a.said.caption) && a.said.text) observedTexts.add(a.said.text);
      const node = byId.get(a.node);
      const owners = inherited(a.node);
      const rec = (map, key, label) => {
        const m = map.get(key) ?? new Map();
        const e = m.get(label) ?? { outcomes: new Set(), blocked: false, n: 0 };
        e.outcomes.add(a.outcome);
        if (a.blocked?.length) e.blocked = true;
        e.n += 1;
        m.set(label, e);
        map.set(key, m);
      };
      for (const p of owners) rec(clickedOn, p, a.label);
      // EVERY label, attributed or not. `everClicked` used to be derived from `clickedOn`, which
      // holds only affordances on nodes the ledger could attribute to a presenter — so a label
      // clicked solely on an unattributed node was invisible to the label-only join, and 59
      // labelled widget and view handlers whose labels the walk HAD clicked stayed unexercised.
      rec(anyClicked, '*', a.label);
      if (node?.kind === 'menu') rec(menuClicked, '*', a.label);
      // ENTER in a named box (stroom-gwt-suite/lib/keys.mjs): the field's LABEL is what joins it to a key
      // handler's receiver below.
      if (a.kind === 'key' && a.label === 'Enter') {
        for (const p of owners) enterOn.set(p, new Set([...(enterOn.get(p) ?? []), a.field ?? '']));
      }
      // The illegal-value driver typed into these boxes and then pressed OK, which blurs the last
      // one: that is what fires `addValueChangeHandler` / `addChangeHandler`. The dirty driver
      // types on an editor TAB and blurs with Tab, which fires the same handlers.
      // `changed: false` means the control did not move — a number box handed a letter, or a box
      // refilled with the value it already held — so nothing fired and crediting it would be a lie.
      // BOTH drivers now mark it from what they observed: the field driver compares the control
      // before and after, and the illegal driver reads the form back after typing. Plans recorded
      // before either did carry no flag, and are taken as they were.
      for (const f of a.check?.plan ?? []) {
        if (f.changed === false) continue;
        for (const p of owners) typedOn.set(p, new Set([...(typedOn.get(p) ?? []), f.label ?? '']));
      }
      // An ACE editor's own value handler has TWO proofs, and it needs both because they are not
      // available at the same time. The strong one is a `dirty` pass (below), which only the first
      // field on a clean tab can earn. The other is the editor's text actually changing: ACE fires
      // its change event, which is precisely what `EditorPresenter` turns into the ValueChangeEvent
      // that `XsltPresenter:56` registers. Without this, driving every field would have COST the
      // ace credits won last week, whenever a text box happened to be dirty first.
      if (a.check?.kind === 'value' && a.check.verdict === 'pass'
        && (a.check.plan ?? []).some((f) => f.kind === 'ace' && f.target)) {
        for (const p of owners) aceDirtiedOn.add(p);
      }
      // A `dirty` handler is exercised when typing on a screen attributed to the class actually
      // marked the document dirty — that IS the handler running (DocTabPresenter:274).
      // A drag or a resize fires press, move and release on the dialog class that owns it.
      if (a.check?.gesture && a.check.verdict === 'pass') {
        proveMouse(a.check.evidence?.owner, ['mousedown', 'mousemove', 'mouseup']);
      }
      // The pager's row range — see `rangeProven`.
      if (a.check?.gesture === 'range' && a.check.verdict === 'pass') {
        rangeProven.add('lblfrom');
        rangeProven.add('txtfrom');
        if (a.check.evidence?.toSide) { rangeProven.add('lblto'); rangeProven.add('txtto'); }
      }
      // A completed SELECTION closes the SelectionBox's popup — see CLOSE_BY_SELECTION.
      if (a.check?.kind === 'value' && a.check.verdict === 'pass'
        && (a.check.plan ?? []).some((f) => f.kind === 'select' && f.target && f.changed !== false)) {
        selectionMade = true;
      }
      // A spinner gesture fires exactly one of the four, and says which.
      if (a.check?.verdict === 'pass' && MOUSE_KINDS.has(a.check.kind) && !a.check.gesture && a.check.field) {
        proveMouse('Spinner', [a.check.kind]);
      }
      if (a.check?.kind === 'dirty' && a.check.verdict === 'pass') {
        for (const p of owners) dirtiedOn.add(p);
        tabDirty = true;
        if ((a.check.plan ?? []).some((f) => f.target && /^Documentation$/i.test(String(f.label ?? '')))) markdownDirty = true;
        if ((a.check.plan ?? []).some((f) => f.kind === 'ace' && f.target)) {
          for (const p of owners) aceDirtiedOn.add(p);
        }
      }
    }
    for (const n of d.nodes ?? []) {
      if (n.status !== 'reached' || n.kind !== 'post-action') continue;
      const last = String(n.path.at(-1) ?? '');
      const state = /after select row/.test(last) ? 'selection' : /after select tree/.test(last) ? 'selection'
        : /after select element/.test(last) ? 'selection' : /after (Enter|Exit|Ask|Hide|Show)/.test(last) ? 'toggle' : null;
      if (!state) continue;
      for (const p of inherited(n.id)) statesOn.set(p, new Set([...(statesOn.get(p) ?? []), state]));
    }
  }
}

// Embedding: a node the ledger attributes to AnalyticRulePresenter carries the toolbar of the
// ScheduledProcessListPresenter it embeds, and the walk's click on `Add` there fired the child's
// handler. One level, as the ledger narrows (transitive embedding is half the application).
const embedsOf = new Map();
if (existsSync(oracle('capability-specs.json'))) {
  for (const sp of JSON.parse(readFileSync(oracle('capability-specs.json'), 'utf8')).specs ?? []) embedsOf.set(sp.presenter, sp.embeds ?? []);
}
const embeddedBy = new Map();
for (const [parent, kids] of embedsOf) for (const k of kids) embeddedBy.set(k, [...(embeddedBy.get(k) ?? []), parent]);
// How many classes register a handler under a label: a label used by one or two classes is
// distinctive enough to join on its own; `Delete` is not.
const classesByLabel = new Map();
for (const h of handlers) if (h.label) classesByLabel.set(h.label, new Set([...(classesByLabel.get(h.label) ?? []), h.class]));
const everClicked = anyClicked.get('*') ?? new Map();

/**
 * A receiver and a FIELD LABEL reduced to the same word, by two different routes because they are
 * two different things.
 *
 * A receiver is CODE, and the miner sometimes catches the whole expression rather than the name —
 * `registerHandler(getView().getNameBox()`, `registerHandler(valueFrom`, `registerHandler(textBox`.
 * The name is its LAST identifier, minus a `get`/`set`/`is` prefix and the widget suffix GWT names
 * boxes with (`ListBox` before `Box`, or `nodeTagsListBox` reduces to `nodeTagsList`). A suffix is
 * only stripped when something is left: `textBox` is `text`, not nothing.
 *
 * A label is WORDS, so its last identifier is the wrong end of it — "Subject ID" is `subjectid`,
 * not `id`. It is simply its letters, lower-cased, minus a trailing "(optional)" and the like.
 */
const WIDGET_SUFFIX = /(ListBox|TextBox|TextArea|SelectionBox|DateBox|Box|Field|Input|Filter|Entry|Editor|Picker|Widget|List)$/i;
const reduceReceiver = (raw) => {
  const ids = String(raw ?? '').match(/[A-Za-z_$][A-Za-z0-9_$]*/g) ?? [];
  let t = (ids[ids.length - 1] ?? '').replace(/^(get|set|is)(?=[A-Z])/, '');
  t = t.replace(WIDGET_SUFFIX, '') || t;
  return t.replace(/[^A-Za-z]/g, '').toLowerCase() || null;
};
const reduceLabel = (raw) => {
  let t = String(raw ?? '').replace(/\((optional|required)\)/ig, '').replace(/[*:]/g, '');
  t = t.replace(/[^A-Za-z]/g, '');
  const stripped = t.replace(WIDGET_SUFFIX, '');
  return (stripped || t).toLowerCase() || null;
};
/**
 * Is one of the boxes the walk typed in THIS handler's receiver? Two ways, and the first is the
 * reliable one: the receiver's `ui:field` is named by the `FormGroup` around it in the view's
 * ui.xml, and that label is exactly the `.form-group-label` the walker reads — "Unique User
 * Identity" for `subjectId`, which no name reduction could ever have bridged. Failing that, the
 * names themselves (`nameFilter` <-> "Name").
 */
const matchesField = (h, fields) => {
  if (!fields.length) return null;
  const want = new Set();
  const uiLabel = uiXml.get(h.class)?.get(h.receiver);
  if (uiLabel) want.add(reduceLabel(uiLabel));
  const byName = reduceReceiver(h.receiver);
  if (byName) want.add(byName);
  return fields.some((f) => want.has(reduceLabel(f)))
    ? { outcomes: new Set(['changed']), n: 1, join: 'attributed' } : null;
};
/**
 * Receivers that ARE the ace editor, for the dirty-proof join above. These are the reductions
 * `reduceReceiver` actually produces: WIDGET_SUFFIX has no `Presenter` alternative, so
 * `editorPresenter` reduces to "editorpresenter", not "editor". `editorTheme` and
 * `editorKeyBindings` reduce to themselves and are correctly excluded — they are the preference
 * boxes in `EditorPreferencesViewImpl`, which no driver has touched.
 */
const ACE_RECEIVER = new Set(['editorpresenter', 'codepresenter', 'editor', 'code']);
// All five are driveable now (lib/mouse.mjs), so one left unexercised is unreached rather than
// waiting for a driver to exist. `MenubarItem`'s four are the clear case: its DOM is not in the
// running application at all, which the probe confirmed by finding no `.menuItem > .background`.
const EXERCISABLE_NOW = new Set(['click', 'command', 'ok', 'cancel', 'hide', 'selection', 'context', 'dblclick',
  ...MOUSE_KINDS]);
/**
 * Classes whose DOM is NOT in the running application, so no driver can ever reach them and they
 * should not read as outstanding work. Each needs evidence, not a hunch:
 *
 *   MenubarItem  `probe-mouse.mjs` finds no `.menuItem > .background` anywhere in the application.
 *                Its ui.xml builds `.menuItem > .background + .text`, which is a different DOM from
 *                the popup menu's `.menuItem-outer > .menuItem-text`; the application's menu is a
 *                popup, and this GWTP view is only ever built by injection. 4 handlers.
 */
const NOT_IN_APP = new Map([['MenubarItem', 'no .menuItem > .background exists in the application (probe-mouse.mjs)']]);
// `key` is deliberately NOT in that set: a key handler the Enter probe did not reach stays
// `needs-key`, which is what B3 still owes. Joining one is `hit`'s business, not this list's.
for (const h of handlers) {
  const owners = closureOf(h.class);
  for (const o of [...owners]) { const p = partnerOf(o); if (p) for (const c of closureOf(p)) owners.add(c); }
  for (const o of [...owners]) for (const parent of embeddedBy.get(o) ?? []) owners.add(parent);
  const seen = (label) => {
    // MERGE every owner's record, do not take the first. `find(Boolean)` made the answer depend on the
    // order the owner set happens to iterate: one owner's record carrying `blocked` and no `closed`
    // outcome masked another's that had closed, so an `ok` handler was downgraded to `ok-blocked` on
    // the strength of the wrong half of the evidence. Adding three shards took `ok` from 61 to 60 —
    // a credit LOST by learning more, which is the tell that evidence was being picked rather than
    // pooled.
    const recs = [...owners].map((o) => clickedOn.get(o)?.get(label)).filter(Boolean);
    if (recs.length) {
      return {
        outcomes: new Set(recs.flatMap((r) => [...r.outcomes])),
        blocked: recs.some((r) => r.blocked),
        n: recs.reduce((t, r) => t + r.n, 0),
        join: 'attributed',
      };
    }
    // A distinctive label, clicked somewhere: the join is by label alone and says so.
    if ((classesByLabel.get(label)?.size ?? 99) <= 2 && everClicked.has(label)) return { ...everClicked.get(label), join: 'label' };
    return null;
  };
  /** Any of the handler's acceptable labels, for a button whose caption changes with its state. */
  const seenAny = () => {
    for (const l of (h.labels ?? [h.label])) { const got = l ? seen(l) : null; if (got) return got; }
    return null;
  };
  let hit = null;
  // `Pager`'s range editor, driven as one gesture: its click, key and focus/blur handlers all ride on
  // it, so they join by the receiver the gesture reached rather than by a label or a node.
  if (h.class === 'Pager' && ['click', 'key', 'dom'].includes(h.kind) && rangeProven.has(reduceReceiver(h.receiver))) {
    hit = { outcomes: new Set(['changed']), n: 1, join: 'range' };
  } else if (h.class === 'Pager' && h.kind === 'click' && reduceReceiver(h.receiver) === 'refresh'
    && (h.labels ?? []).some((l) => everClicked.has(l))) {
    // `Pager` is one class under EVERY grid in the application, and it is never attributed to a node
    // because node attribution names screen presenters, not the widgets inside them. So a Refresh
    // pressed on any grid IS this handler — the same argument that credits `Dialog`, `Spinner` and
    // the range editor by their gesture rather than by a label on a node.
    hit = { outcomes: new Set(['changed']), n: 1, join: 'widget-under-all' };
  } else if (h.kind === 'click' && h.label) hit = seenAny();
  else if (h.kind === 'command' && h.label) {
    // A menu item is usually clicked on a `menu` node, but not always: a tab's context menu is
    // reached through `tab-context` and its items are recorded on the node the menu was opened
    // from. So fall back to the same distinctive-label rule clicks use — `Close Others`,
    // `Close Saved` and `Close All` are each registered by one class and were all clicked.
    hit = menuClicked.get('*')?.get(h.label)
      ?? ((classesByLabel.get(h.label)?.size ?? 99) <= 2 && everClicked.has(h.label)
        ? { ...everClicked.get(h.label), join: 'label' } : null);
  }
  else if (h.kind === 'ok' || h.kind === 'hide') hit = seen('OK');
  else if (h.kind === 'cancel') hit = seen('Cancel') ?? seen('Close');
  else if (h.kind === 'selection') hit = [...owners].some((o) => statesOn.get(o)?.has('selection')) ? { outcomes: new Set(['changed']), n: 1 } : null;
  else if (h.kind === 'value') {
    // A value handler fires when ITS box changes, so it joins the same way a key handler does —
    // by the label of the box the walk typed in (lib/illegal.mjs).
    const fields = [...owners].flatMap((o) => [...(typedOn.get(o) ?? [])]);
    hit = matchesField(h, fields);
    // An ACE editor is the one box no label can reach, so it joins by the dirty proof instead
    // (see `aceDirtiedOn`). Only a receiver that reduces to the editor itself qualifies:
    // `editorPresenter` -> "editor" and `codePresenter` -> "code", while `editorTheme` and
    // `editorKeyBindings` reduce to themselves and stay unexercised, which is right — they are
    // preference boxes the driver never touched.
    if (!hit && ACE_RECEIVER.has(reduceReceiver(h.receiver)) && [...owners].some((o) => aceDirtiedOn.has(o))) {
      hit = { outcomes: new Set(['changed']), n: 1, join: 'ace-dirty' };
    }
  } else if (h.kind === 'dirty') {
    hit = [...owners].some((o) => dirtiedOn.has(o)) ? { outcomes: new Set(['changed']), n: 1, join: 'attributed' } : null;
    // The links the dirtiness had to travel along to reach the Save button the driver watched.
    if (!hit && tabDirty && DIRTY_CHAIN.get(h.class)?.has(reduceReceiver(h.receiver))) {
      hit = { outcomes: new Set(['changed']), n: 1, join: 'dirty-chain' };
    }
    if (!hit && markdownDirty && MARKDOWN_CHAIN.get(h.class)?.has(reduceReceiver(h.receiver))) {
      hit = { outcomes: new Set(['changed']), n: 1, join: 'dirty-chain' };
    }
  } else if (h.kind === 'key') {
    // A key handler is fired by a key ON ITS RECEIVER, so a press somewhere under the class is not
    // enough: `SolrIndexSettingsViewImpl` binds five boxes and Enter went into one. Join the box
    // the walk typed in to the handler's receiver by NAME — `nameBox` / `nameFilter` / `name` all
    // reduce to `name`, and so does a field labelled "Name". A handler whose receiver the miner
    // could not name is left `needs-key`, which is the truth about it.
    const fields = [...owners].flatMap((o) => [...(enterOn.get(o) ?? [])]);
    hit = matchesField(h, fields);
  } else if (h.kind === 'click' && selectionMade && CLICK_BY_SELECTION.get(h.class)?.has(reduceReceiver(h.receiver))) {
    hit = { outcomes: new Set(['changed']), n: 1, join: 'selection' };
  } else if (h.kind === 'close') {
    hit = selectionMade && CLOSE_BY_SELECTION.get(h.class)?.has(reduceReceiver(h.receiver))
      ? { outcomes: new Set(['closed']), n: 1, join: 'selection' } : null;
  } else if (MOUSE_KINDS.has(h.kind)) {
    hit = mouseProven.get(h.class)?.has(h.kind) ? { outcomes: new Set(['changed']), n: 1, join: 'gesture' } : null;
  } else if (h.kind === 'context') hit = [...owners].map((o) => clickedOn.get(o)).some((m) => m && [...m.keys()].some((l) => /right-click|context/i.test(l))) ? { outcomes: new Set(['menu']), n: 1 } : null;

  if (hit) {
    h.exercised = true;
    h.join = hit.join ?? 'attributed';
    h.outcomes = [...hit.outcomes];
    // ... unless a compensating cycle drove this very OK with the write going through, which is the
    // one thing that settles what the guard's abort left open.
    if (h.kind === 'ok' && hit.blocked && ![...hit.outcomes].some((o) => o === 'closed') && !cycleProved.has(h.class)) {
      h.exercised = false; h.why = 'ok-blocked';
    } else if (h.kind === 'ok' && cycleProved.has(h.class)) h.join = 'cycle';
  } else {
    h.exercised = false;
    h.why = NOT_IN_APP.has(h.class) ? 'absent'
      : !EXERCISABLE_NOW.has(h.kind) ? `needs-${h.kind}`
      : h.kind === 'click' && !h.label ? 'unlabelled'
        : h.classKind === 'widget' ? 'widget'
          : h.classKind === 'view' ? 'view'
            : 'unreached';
  }
}

// A template matches an observed text when the text starts with its first literal and contains
// each further literal in order (`…` is anything). Short first literals ("Error") are too loose
// to trust alone, so a template needs eight characters of literal to join.
const literalsOf = (t) => t.split('…').map((x) => x.trim()).filter(Boolean);
const observedList = [...observedTexts];
for (const m of messages) {
  const lits = literalsOf(m.template);
  if (lits.join('').length < 8) { m.observed = null; m.why = 'template too short to join'; continue; }
  m.observed = observedList.some((t) => {
    let at = 0;
    for (const [i, lit] of lits.entries()) {
      const found = t.indexOf(lit, at);
      if (found < 0 || (i === 0 && !m.template.startsWith('…') && found !== 0)) return false;
      at = found + lit.length;
    }
    return true;
  });
}
const msgDeclared = messages.filter((m) => m.observed !== null).length;
const msgObserved = messages.filter((m) => m.observed).length;

// ── Report ───────────────────────────────────────────────────────────────────
const byKind = new Map();
for (const h of handlers) {
  const e = byKind.get(h.kind) ?? { declared: 0, exercised: 0 };
  e.declared += 1;
  if (h.exercised) e.exercised += 1;
  byKind.set(h.kind, e);
}
const byPresenter = new Map();
for (const h of handlers) {
  if (h.classKind !== 'presenter') continue;
  const e = byPresenter.get(h.class) ?? { declared: 0, exercised: 0, why: new Map() };
  e.declared += 1;
  if (h.exercised) e.exercised += 1;
  else e.why.set(h.why, (e.why.get(h.why) ?? 0) + 1);
  byPresenter.set(h.class, e);
}
const whyTotals = new Map();
for (const h of handlers) if (!h.exercised) whyTotals.set(h.why, (whyTotals.get(h.why) ?? 0) + 1);
const declared = handlers.length;
const exercised = handlers.filter((h) => h.exercised).length;
const byLabelOnly = handlers.filter((h) => h.exercised && h.join === 'label').length;
const pDeclared = handlers.filter((h) => h.classKind === 'presenter').length;
const pExercised = handlers.filter((h) => h.classKind === 'presenter' && h.exercised).length;
const pct = (a, b) => (b ? `${(100 * a / b).toFixed(1)}%` : '—');

writeFileSync(oracle('handler-inventory.json'), JSON.stringify({
  generated: new Date().toISOString(), declared, exercised, presenters: { declared: pDeclared, exercised: pExercised },
  byKind: Object.fromEntries(byKind), why: Object.fromEntries(whyTotals), handlers,
  messages: { declared: msgDeclared, observed: msgObserved, list: messages },
}, null, 1));

const L = [];
L.push('# Handler inventory — the behaviour denominator', '');
L.push('**Generated** by `stroom-stroom-gwt-suite/tools/build-handler-inventory.mjs`. Do not hand-edit. BEHAVIOUR-PLAN.md § B1.', '');
L.push('A behaviour is a handler registration in the GWT client, attributed to the class that DECLARES it. A');
L.push('handler is *exercised* when the walk fired it: a click handler when an affordance with its label was');
L.push('clicked on a node the coverage ledger attributes to the declaring class or a subclass; OK / Cancel when');
L.push('a dialog under that class had them pressed (an OK the guard aborted is `ok-blocked`); a selection handler');
L.push('when a post-action selection state was reached under it. The rest are classified by what would fire them.', '');
L.push('## Totals', '');
L.push('| | declared | exercised | |');
L.push('| --- | ---: | ---: | ---: |');
L.push(`| **all classes** | ${declared} | ${exercised} | ${pct(exercised, declared)} |`);
L.push(`| **presenters** | ${pDeclared} | ${pExercised} | ${pct(pExercised, pDeclared)} |`);
L.push('');
L.push(`_${byLabelOnly} of the exercised are joined by LABEL alone (a label one or two classes use, clicked somewhere the ledger did not attribute to the class); the rest by the ledger's attribution of the node to the class, a subclass, or a presenter that embeds it._`, '');
L.push('| kind | declared | exercised | |');
L.push('| --- | ---: | ---: | ---: |');
for (const [k, e] of [...byKind].sort((a, b) => b[1].declared - a[1].declared)) L.push(`| ${k} | ${e.declared} | ${e.exercised} | ${pct(e.exercised, e.declared)} |`);
L.push('');
L.push('## The unexercised, by what would fire them', '');
L.push('| why | handlers | meaning |');
L.push('| --- | ---: | --- |');
const WHY = {
  absent: 'the class is not in the running application — unreachable, not outstanding',
  'needs-value': 'a value must be typed or chosen (B3)', 'needs-key': 'a keyboard event (B3)', 'needs-dirty': 'an editor made dirty (B3)',
  'needs-focus': 'focus / blur (B3)', 'needs-dom': 'a DOM event the walk has no step for', 'needs-close': 'a tab or panel closed', 'needs-open': 'a tree node opened',
  'ok-blocked': 'the OK writes, and the guard aborted it (B4)', unlabelled: 'a click handler on a receiver whose label the miner could not resolve (miner gap)',
  widget: 'declared in a widget class — inherited by many presenters, counted once here', view: 'declared in a ViewImpl (a @UiHandler) — joined by ui.xml label, none matched',
  unreached: 'labelled, in a presenter, and never clicked on a node attributed to it',
};
for (const [w, n] of [...whyTotals].sort((a, b) => b[1] - a[1])) L.push(`| \`${w}\` | ${n} | ${WHY[w] ?? ''} |`);
L.push('');
L.push('## Per presenter', '');
L.push('_Presenters with any unexercised handler, most gaps first; the `why` column is the split._', '');
L.push('| presenter | declared | exercised | unexercised by why |');
L.push('| --- | ---: | ---: | --- |');
for (const [p, e] of [...byPresenter].filter(([, e]) => e.exercised < e.declared).sort((a, b) => (b[1].declared - b[1].exercised) - (a[1].declared - a[1].exercised))) {
  L.push(`| \`${p}\` | ${e.declared} | ${e.exercised} | ${[...e.why].map(([w, n]) => `${w} ${n}`).join(', ')} |`);
}
L.push('');
L.push('## Messages the client can show — the validation matrix (B3)', '');
L.push('_Every `AlertEvent.fireWarn / fireError / fireInfo`, `ConfirmEvent.fire`, `ErrorEvent.fire` and `throw new'
  + ' ValidationException` with a literal message, as a template (`…` = a variable part), joined by prefix to'
  + ' the alert and confirm bodies the walk read. An unobserved message is a validation the walk has never'
  + ' provoked — the row of the matrix still to drive. Templates with under eight literal characters do not join._', '');
L.push(`**${msgObserved} of ${msgDeclared} observed (${pct(msgObserved, msgDeclared)}).**`, '');
L.push('| kind | declared | observed |');
L.push('| --- | ---: | ---: |');
{
  const by = new Map();
  for (const m of messages.filter((x) => x.observed !== null)) { const e = by.get(m.kind) ?? { d: 0, o: 0 }; e.d += 1; if (m.observed) e.o += 1; by.set(m.kind, e); }
  for (const [k, e] of [...by].sort((a, b) => b[1].d - a[1].d)) L.push(`| ${k} | ${e.d} | ${e.o} |`);
}
L.push('', '### Never observed', '');
for (const m of messages.filter((x) => x.observed === false && x.classKind === 'presenter').sort((a, b) => a.class.localeCompare(b.class))) {
  L.push(`- \`${m.class}\` (${m.kind}, line ${m.line}) — ${m.template}`);
}
L.push('');
L.push('## Unreached click handlers in presenters', '');
L.push('_A labelled button in a presenter the walk attributes nodes to, whose label was never clicked there. Each is a walker gap, a miner mislabel, or a button the UI never shows — and only reading it says which._', '');
for (const h of handlers.filter((x) => x.classKind === 'presenter' && !x.exercised && x.why === 'unreached' && x.kind === 'click')) {
  L.push(`- \`${h.class}\` · \`${h.label}\` (${h.receiver}, line ${h.line})`);
}
L.push('');
writeFileSync(oracle('handler-inventory.md'), L.join('\n'));
console.log(`handlers: ${exercised}/${declared} exercised (${pct(exercised, declared)}) · presenters ${pExercised}/${pDeclared} (${pct(pExercised, pDeclared)})`);
console.log(`  by kind: ${[...byKind].map(([k, e]) => `${k} ${e.exercised}/${e.declared}`).join(' · ')}`);
console.log(`  unexercised: ${[...whyTotals].sort((a, b) => b[1] - a[1]).map(([w, n]) => `${w}=${n}`).join(' ')}`);
console.log(`  messages: ${msgObserved}/${msgDeclared} observed (${pct(msgObserved, msgDeclared)})`);
