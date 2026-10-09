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
// How do you CLICK your way to each presenter? Executable route recipes, mined from the GWT source.
//
//   node stroom-stroom-gwt-suite/tools/build-route-recipes.mjs
//     -> oracles/route-recipes.json   (machine-readable, consumed by stroom-gwt-suite/crawl.mjs)
//     -> oracles/route-recipes.md     (the same thing, readable)
//
// `reachability-graph.md` says a presenter is a "door" or is "embedded" — the TOPOLOGY. That is not
// enough to drive anything: it never says which words to click. So coverage has been measured by what
// the crawler happened to stumble on, which answers "what did we find" and not the question that
// matters, "what did we FAIL to reach".
//
// The recipes are derivable because GWT registers its navigation declaratively:
//
//   event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU,
//           new IconMenuItem.Builder().priority(150).text("Dependencies")
//
// That is a complete click path — Main Menu > Tools > Dependencies — and `MenuKeys.java` supplies the
// group's own label (`SECURITY_MENU` is registered with `.text("Security")`). 43 classes across 9
// groups register this way, which is the whole top-level navigation.
//
// Deliberately reports what it CANNOT route, by name. A recipe list that quietly omits the hard cases
// is indistinguishable from one that covers everything, and this repo has been bitten by that shape of
// silence more than once today.
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { SOURCE, oracle } from '../lib/paths.mjs';

const GWT = SOURCE;
const ROOTS = [
  'stroom-core-client/src/main/java/stroom',
  'stroom-core-client-widget/src/main/java/stroom',
  'stroom-statistics/stroom-statistics-client/src/main/java/stroom',
];
const OUT_JSON = oracle('route-recipes.json');
const OUT_MD = oracle('route-recipes.md');

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
const JAVA_KEYWORDS = new Set(['return', 'throw', 'new', 'case', 'else', 'import', 'package', 'assert',
  'instanceof', 'extends', 'implements', 'throws', 'this', 'super', 'break', 'continue', 'do', 'goto']);
const files = [];
for (const r of ROOTS) files.push(...javaFiles(join(GWT, r)));

/**
 * Read a file with its comments removed.
 *
 * `DocumentPluginEventManager` has a commented-out `// .text("Item")`, which was mined as a real menu
 * recipe named `Item` — a route to a screen that does not exist, presented with the same confidence as
 * the 38 real ones. Dead code is the one input that looks exactly like live code to a regex.
 */
const readSrc = (f) => readFileSync(f, 'utf8')
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .split('\n').map((l) => l.replace(/^\s*\/\/.*$/, '')).join('\n');

// ── 1. Menu group keys -> the label the user actually sees ───────────────────
// `MenuKeys.ADMINISTRATION_MENU` is registered into MAIN_MENU with `.text("Administration")`, so the
// mapping is in the source rather than a table someone has to maintain.
// Scan EVERY file, not just MenuKeys.java. Six groups are registered there, but `Monitoring` is
// registered by MonitoringPlugin and `Tools` elsewhere again — so a MenuKeys-only scan left those two
// keys unlabelled, their leaves were seeded as top-level items, and 15 of 39 recipes failed with
// `no menu group "Main Menu"`. The recipes were wrong; the UI was fine.
const groupLabel = new Map();
for (const f of files) {
  const src = readSrc(f);
  for (const m of src.matchAll(/\.text\("([^"]+)"\)[\s\S]{0,200}?\.menuKey\(MenuKeys\.([A-Z_]+)\)/g)) {
    groupLabel.set(m[2], m[1]);
  }
}
// MAIN_MENU is the hamburger itself; it has no parent item.
groupLabel.set('MAIN_MENU', null);

// ── 2. Every menu registration: which class puts which words in which menu ───
const menuRoutes = [];        // { owner, group, leaf }
const docTypes = new Map();   // plugin class -> document type string
for (const f of files) {
  const src = readSrc(f);
  const owner = cls(f);
  // `addMenuItem(MenuKeys.X_MENU, <builder>… .text("Leaf")…)`. Scoped to a window after the key so a
  // second registration in the same file cannot borrow the first one's text.
  // A label may be a CONSTANT — `.text(SCREEN_NAME)` with `static final String SCREEN_NAME = "Users"`
  // in the same file — which is how all seven Security plugins register, and why the Security menu
  // had three recipes for nine leaves. And the builder may be built FIRST and passed as a variable:
  // `addMenuItem(MenuKeys.SECURITY_MENU, apiKeysMenuItem)`.
  const constants = new Map();
  for (const c of src.matchAll(/static\s+final\s+String\s+([A-Z_][A-Z0-9_]*)\s*=\s*"([^"]+)"/g)) constants.set(c[1], c[2]);
  const labelOfExpr = (expr) => (/^"[^"]*"$/.test(expr) ? expr.slice(1, -1) : constants.get(expr) ?? null);
  const sites = [];
  for (const m of src.matchAll(/addMenuItem\(\s*MenuKeys\.([A-Z_]+)\s*,([\s\S]{0,600}?)\)\s*;/g)) {
    const [, key, rest] = m;
    let text = rest.match(/\.text\(\s*("[^"]+"|[A-Za-z_][A-Za-z0-9_]*)\s*\)/)?.[1];
    if (!text) {
      // `addMenuItem(KEY, someVar)` — find where someVar was built.
      const v = rest.trim().match(/^([a-zA-Z_][A-Za-z0-9_]*)\s*$/)?.[1];
      if (v) text = src.match(new RegExp(`${v}\\s*=[\\s\\S]{0,600}?\\.text\\(\\s*("[^"]+"|[A-Z_][A-Z0-9_]*)\\s*\\)`))?.[1];
    }
    if (!text) {
      // `addMenuItem(KEY, createMenuItem())` — the builder lives in a METHOD of the same file. The
      // three receive-rules plugins (Data Retention, Data Receipt Rules, Content Templates) register
      // this way, and without a recipe for their leaves the ledger could not say which screen a
      // dialog under them was opened from — `New Field` names four presenters application-wide.
      const call = rest.trim().match(/^([a-zA-Z_][A-Za-z0-9_]*)\(\s*\)\s*$/)?.[1];
      if (call) text = src.match(new RegExp(`MenuItem\\s+${call}\\s*\\(\\s*\\)\\s*\\{[\\s\\S]{0,600}?\\.text\\(\\s*("[^"]+"|[A-Z_][A-Z0-9_]*)\\s*\\)`))?.[1];
    }
    let leaf = text ? labelOfExpr(text) : null;
    if (leaf) { sites.push([key, leaf, owner]); continue; }
    // `.text(tabLabel)` where tabLabel is a CONSTRUCTOR PARAMETER of an abstract plugin: each
    // subclass supplies the words in its `super(…)` call. AnnotationPlugin registers five leaves
    // this way — Collections, Comments, Labels, Status, … — one per subclass, and none of them
    // could be mined from the parent alone.
    const param = text && /^[a-z][A-Za-z0-9_]*$/.test(text) && new RegExp(`String\\s+${text}\\s*[,)]`).test(src) ? text : null;
    if (!param) continue;
    for (const g of files) {
      const gsrc = readSrc(g);
      const sub = cls(g);
      if (!new RegExp(`class\\s+${sub}\\s+extends\\s+${owner}\\b`).test(gsrc)) continue;
      const sup = gsrc.match(/super\(([\s\S]{0,800}?)\)\s*;/);
      const literals = sup ? [...sup[1].matchAll(/"([^"]+)"/g)].map((x) => x[1]) : [];
      if (literals.length === 1) sites.push([key, literals[0], sub]);
    }
  }
  for (const [key, leaf, who] of sites) {
    // A KeyedParentMenuItem registers the GROUP itself (Security, Help). Those are not leaves.
    const isGroupHeader = new RegExp(`\\.text\\("${leaf}"\\)[\\s\\S]{0,200}?\\.menuKey\\(`).test(src);
    if (isGroupHeader) continue;
    menuRoutes.push({ owner: who ?? owner, group: groupLabel.get(key) ?? null, groupKey: key, leaf });
  }
  // `public String getType() { return DictionaryDoc.TYPE; }` — the doc class, not a literal. The
  // display name shown in the New submenu comes from the server's DocumentType registry, so the class
  // stem is the best static handle and the crawler matches it case-insensitively.
  const dt = src.match(/getType\(\)\s*\{\s*return\s+([A-Za-z_][A-Za-z0-9_]*)\s*\.\s*(?:TYPE|DOCUMENT_TYPE)/);
  if (/extends\s+DocumentPlugin\b/.test(src) && dt) docTypes.set(owner, dt[1].replace(/Doc$/, ''));
}

// ── 2b. Dialog captions -> the presenter behind them ─────────────────────────
// The join that makes coverage countable. A crawl records what a dialog CALLS ITSELF; the source says
// which presenter is being shown under that caption:
//
//   ShowPopupEvent.builder(rulePresenter).caption("Edit Rule")
//
// so `dialog / … / Edit Rule` in a crawl is DataRetentionRulePresenter, checked off by name rather
// than guessed at from a slug. Without this, "73 distinct places" cannot be compared to "414
// presenters" at all, and coverage stays a number about the crawler instead of about the product.
// Who SHOWS THEMSELVES, and who handles which event — needed by both the popup mining below and the
// event mining after it, so computed once up front. `showsItself` is the same test the reachability
// graph uses: a class that raises its own popup is a dialog, whatever its name ends in.
const handlerOf = new Map();
const selfShows = new Set();
for (const f of files) {
  const src = readSrc(f);
  const c = cls(f);
  if (/ShowPopupEvent\s*\.\s*builder\(\s*this\s*\)/.test(src) || /ShowPopupEvent\.Builder\s+[a-z]/.test(src)) {
    selfShows.add(c);
  }
  for (const m of src.matchAll(/implements\s+([A-Za-z0-9_.\s,<>]+?)\s*\{/g)) {
    for (const part of m[1].split(',')) {
      const h = /^([A-Za-z0-9_]+)\.Handler$/.exec(part.trim());
      if (h) {
        if (!handlerOf.has(h[1])) handlerOf.set(h[1], new Set());
        handlerOf.get(h[1]).add(c);
      }
    }
  }
}

const showEdges = [];
for (const f of files) {
  let src = readSrc(f);
  const shower = cls(f);
  // A caption built from a METHOD that returns a literal in the same file:
  //
  //   final String caption = getEntityDisplayType() + " - " + configProperty.getName();
  //   private String getEntityDisplayType() { return "Application Property"; }
  //
  // Templating the call as a gap left `… - …`, which the template rule rightly rejects (no constant
  // word), so ManageGlobalPropertyEditPresenter and ConfigPropertyClusterValuesPresenter — both
  // captioned this way, both children of the same screen — could not be told apart. Substituting
  // the literal is one hop within one file, the same distance as a local's initialiser.
  for (const [, name, lit] of src.matchAll(/String\s+([a-zA-Z_]\w*)\(\)\s*\{\s*return\s+"([^"]*)"\s*;/g)) {
    src = src.replace(new RegExp(`\\b${name}\\(\\)`, 'g'), `"${lit}"`);
  }
  // Field/param declarations, so a shown variable resolves to its declared type (incl. Provider<T>).
  //
  // The terminator has to include `,`: a parameter that is not the LAST one in a signature ends in a
  // comma, and `showRulePresenter(final RulePresenter rulePresenter, ...)` is exactly that shape. Two
  // edges were being published under a variable name rather than a type — the ledger reported a
  // presenter called `rulePresenter`, which is nothing, so the dialog it names went uncounted.
  //
  // A GENERIC declaration resolves to the raw type, not the type argument. `ChooserPresenter<
  // AnnotationTag> annotationStatusPresenter` was resolving to `AnnotationTag` — a model class, not a
  // presenter — and published five edges naming it, so the annotation status and label choosers were
  // attributed to something that cannot be reached at all. `Provider<X>` still resolves to `X`,
  // because that is a holder rather than a generic type.
  // A KEYWORD is not a type. `return userRefPopupPresenter;` matched as a declaration of type
  // `return`, and because a later match overwrites an earlier one, the real declaration eleven lines
  // above it was lost — so `userRefPopupPresenter.show("Add User Or Group", …)` had no receiver type
  // and UserAndGroupsPresenter published no edge to the popup at all. 2,042 `return x;` statements in
  // the client, each one able to erase the type of whatever it returns.
  const typeOf = new Map();
  // Every declaration WITH ITS POSITION, because a name is not a type either: `SteppingPresenter`
  // declares `final ExpressionPresenter presenter` inside the stream-filter click handler (line 304)
  // and `final ElementPresenter presenter` in `getContent` (line 522), and last-wins typed the
  // `Filter Streams` show site as ElementPresenter — a door the ledger then credited from a dialog
  // that never opens it. A show site resolves to the nearest declaration BEFORE it (a local in the
  // enclosing method, or a field, which comes first in a Java file); only when none precedes it
  // (a field declared below its use) does the file-wide last declaration stand in.
  const declsOf = new Map();
  for (const d of src.matchAll(
    /(?:private|protected|public)?\s*(?:final\s+)?(?:Provider\s*<\s*([A-Za-z_][A-Za-z0-9_]*)\s*>|([A-Za-z_][A-Za-z0-9_]*)(?:\s*<[^<>;=()]*>)?)\s+([a-zA-Z_][A-Za-z0-9_]*)\s*[;=),]/g)) {
    if (JAVA_KEYWORDS.has(d[1] ?? d[2])) continue;
    typeOf.set(d[3], d[1] ?? d[2]);
    if (!declsOf.has(d[3])) declsOf.set(d[3], []);
    declsOf.get(d[3]).push({ type: d[1] ?? d[2], at: d.index });
  }
  const typeAt = (name, pos) => {
    const before = (declsOf.get(name) ?? []).filter((d) => d.at < pos);
    return before.length ? before[before.length - 1].type : typeOf.get(name);
  };
  // `final String caption = "Rename " + entity.getDisplayValue();` then `.caption(caption)` — the
  // caption is one hop away from the site, which is how NameDocumentPresenter, CopyDocumentPresenter,
  // MoveDocumentPresenter and ExplorerNodeEditTagsPresenter all name their dialogs.
  // Collected as a LIST per variable, not a value: `CopyDocumentPresenter` sets
  // `caption = "Copy " + name` in one branch and `caption = "Copy Multiple Items"` in another, and
  // both are real captions of the same dialog. A declaration and every later assignment count.
  const strings = new Map();
  const stringVars = new Set();
  for (const d of src.matchAll(/\bString\s+([a-zA-Z_][A-Za-z0-9_]*)\s*[;=]/g)) stringVars.add(d[1]);
  const addString = (name, expr) => {
    if (!expr.includes('"')) return;
    if (!strings.has(name)) strings.set(name, []);
    if (!strings.get(name).includes(expr)) strings.get(name).push(expr);
  };
  for (const d of src.matchAll(/\bString\s+([a-zA-Z_][A-Za-z0-9_]*)\s*=\s*([^;]+);/g)) addString(d[1], d[2]);
  for (const d of src.matchAll(/([a-zA-Z_][A-Za-z0-9_]*)\s*=\s*([^;={}]+);/g)) {
    if (stringVars.has(d[1])) addString(d[1], d[2]);
  }

  // A variable assigned from another variable resolves through it: `DataDisplaySupport` declares
  // `final MyPresenterWidget<?> presenter` and assigns it `dataPresenter` in one branch and
  // `sourcePresenter` in the other, so `builder(presenter)` is an edge to BOTH. Emitting both is the
  // honest reading — the site really can show either — where resolving to the declared type would
  // give `MyPresenterWidget`, which names nothing.
  const aliases = new Map();
  for (const a of src.matchAll(/([a-zA-Z_][A-Za-z0-9_]*)\s*=\s*([a-zA-Z_][A-Za-z0-9_]*)\s*;/g)) {
    const t = typeOf.get(a[2]);
    if (!t || !t.endsWith('Presenter')) continue;
    if (!aliases.has(a[1])) aliases.set(a[1], new Set());
    aliases.get(a[1]).add(t);
  }

  // EVERY `builder(X)` is an edge — `shower` opens `X` — whether or not a literal caption follows.
  //
  // This used to require the caption in the same match, and threw away the 61 of 147 sites that
  // build their caption at runtime or set it elsewhere. That made the edge list thinner than the
  // caption list, which inverted how the coverage ledger had to work: it fell back to naming a
  // dialog by its caption, and a caption is not unique (`Settings` belongs to two presenters, `New`
  // to three, `Confirm` to every delete dialog in the application). The ROUTE is the identity; the
  // caption is at best a discriminator between one parent's children.
  // The tail is a LOOKAHEAD so it consumes nothing. As a normal group it swallowed up to 400
  // characters, and `matchAll` resumes after the match — so two `builder(...)` sites close together
  // lost the second. `CommonAlertPresenter` is exactly that shape: a `Confirm` branch and an `Alert`
  // branch, one after the other, and only `Confirm` was ever mined. That single miss made every
  // alert the crawl reached — 169 nodes — unnameable.
  for (const m of src.matchAll(
    // `new ShowPopupEvent.Builder(editAssetDialog)` is the builder site spelt as a constructor.
    /ShowPopupEvent\s*\.\s*(?:builder|Builder)\(\s*([A-Za-z_][A-Za-z0-9_.]*)\s*\)(?=([\s\S]{0,400}))/g)) {
    const [, shown, tail] = m;
    const base = shown.split('.')[0];
    const direct = shown === 'this' ? shower : (typeAt(base, m.index) ?? base);
    const shownAs = direct.endsWith('Presenter') ? [direct] : [...(aliases.get(base) ?? [direct])];
    // A LITERAL caption, or the constant part of a built one. `"Rename " + entity.getDisplayValue()`
    // is not nothing: it says every caption from this site reads `Rename <something>`, and the ledger
    // already normalises a document's name out of what it observed. What must not happen is treating
    // the variable part as if it were fixed, which is why the gap is explicit.
    let captions = captionsAt(tail, strings);
    // A caption that is the ENCLOSING METHOD's parameter: `chooseTabSessionThenAccept(final String
    // caption, …)` builds `.caption(caption)` once and is called twice, with `Select Tab Session To
    // Open:` and `… To Delete:`. Same evidence as the `show(…)` helper form below — every same-file
    // call of the helper contributes what it passes in that position. Without it the walk's
    // `Select Tab Session To Open:` had an edge to TabSessionChooserPresenter but no caption to
    // join on, and the door stayed unnamed after it had been opened.
    if (!captions.length) {
      const expr = splitArgs(tail.slice(tail.indexOf('.caption(') + '.caption('.length))[0]?.trim();
      if (expr && /^[a-zA-Z_][A-Za-z0-9_]*$/.test(expr) && tail.indexOf('.caption(') >= 0) captions = captionsViaHelper(src, m.index, [expr]);
    }
    for (const presenter of shownAs) {
      if (!captions.length) showEdges.push({ shower, presenter });
      for (const caption of captions) showEdges.push({ shower, presenter, caption });
    }
  }

  // A dialog that is handed a `ShowPopupEvent.Builder` and captions ITSELF:
  //
  //   public void setupPopup(final ShowPopupEvent.Builder builder, …) {
  //       builder.popupType(…).caption("New " + dialogType.toTitleCaseString());
  //
  // The reachability graph already treats this as self-shown; the caption was being missed because
  // the mining is anchored on `ShowPopupEvent.builder(X)` and there is no such call here. It matters:
  // this site is a SECOND source of `New …`, so without it the template looks unambiguous and names
  // 35 nodes CreateDocumentPresenter when two presenters caption their dialogs that way.
  if (/ShowPopupEvent\.Builder\s+[a-z]/.test(src)) {
    for (const m of src.matchAll(/\.\s*caption\((?=([\s\S]{0,200}))/g)) {
      if (/ShowPopupEvent\s*\.\s*builder\(/.test(src.slice(Math.max(0, m.index - 200), m.index))) continue;
      for (const caption of captionsAt(`.caption(${m[1]}`, strings)) {
        showEdges.push({ shower, presenter: shower, caption });
      }
    }
  }

  // A dialog whose caption is passed to its OWN `show(...)` helper rather than to a builder:
  //
  //   indexFieldEditPresenter.show("New Field", e -> { … });
  //
  // Same evidence as a builder call — this file opens that presenter, under that caption — and it is
  // the shape behind `New Field` / `Edit Field`, which four different field editors share. Mining it
  // is also what stops the `New …` template from claiming those nodes: an exact caption is more
  // specific than a template, and an exact caption naming four presenters is honestly ambiguous.
  //
  // A dialog does not have to be called `*Presenter`: `textBoxPopup.show("Save New Tab Session", …)`
  // opens `TextBoxPopup`, which shows itself and is in the reachability graph like any other. Gating
  // on the name left that caption — the last one nothing could name — on the worklist.
  //
  // And the caption need not be the FIRST argument, or a literal at all:
  //
  //   editor.show(volumeGroup, "Edit Volume Group - " + volumeGroup.getName(), result -> { … });
  //
  // FsVolumeGroupPresenter.edit() opens FsVolumeGroupEditPresenter exactly this way, and the only
  // edge the miner had for that door was the door showing ITSELF — so a walk that selected a row and
  // clicked Edit reached "Edit Volume Group - Default Volume Group" and the ledger could not narrow it
  // from the screen it was opened from. The declared type of the receiver is the evidence; the
  // caption is whichever argument carries a string literal (a concatenation becomes a template), and
  // with no literal the edge is published without one — the route can still narrow it.
  // `open(` is the same verb under another name: AnnotationTagPresenter opens its create and edit
  // dialogs as `createProvider.get().open(type, …)` and `editor.open(tag, caption, …)`.
  // And the literal may be ONE CALL AWAY, passed through a same-file helper's parameter:
  //
  //   showSummary(criteria, DocumentPermission.VIEW, null, null, "Selection Summary", false, null);
  //   private void showSummary(…, final String caption, …) {
  //       selectionSummaryPresenterProvider.get().show(criteria, permission, postAction, action, caption, …);
  //
  // AbstractMetaListPresenter opens SelectionSummaryPresenter seven times this way and the miner
  // saw one edge with no caption, so the walk's `Selection summary` dialog could not be named. When an
  // argument at the show site is a bare parameter of the enclosing method, every same-file call of
  // that method contributes what it passes in that position — for a parameter NAMED caption/title
  // only, since `showSummary` also takes `postAction` ("delete") and `action` ("deleted"). One hop,
  // like the string locals above.
  // And the receiver may be a same-file GETTER: `getChangeStatusPresenter().show(list)`, the lazy
  // `if (x == null) x = provider.get(); return x;` — typed by the getter's return type.
  const returns = new Map();
  for (const g of src.matchAll(/(?:private|protected|public)?\s*(?:static\s+)?([A-Z][A-Za-z0-9_]*)\s+([a-z][A-Za-z0-9_]*)\s*\(\s*\)\s*\{/g)) returns.set(g[2], g[1]);
  for (const m of src.matchAll(/([a-zA-Z_][A-Za-z0-9_]*)(\(\s*\))?(?:\s*\.\s*get\(\s*\))?\s*\.\s*(?:show|open)\(/g)) {
    const t = m[2] ? returns.get(m[1]) : typeOf.get(m[1]);
    if (!t || !(t.endsWith('Presenter') || selfShows.has(t))) continue;
    const args = splitArgs(src.slice(m.index + m[0].length, m.index + m[0].length + 400));
    let captions = [...new Set(args.flatMap(branches).map(captionTemplate).filter(Boolean))];
    if (!captions.length) captions = captionsViaHelper(src, m.index, args);
    if (captions.length) for (const caption of captions) showEdges.push({ shower, presenter: t, caption });
    else showEdges.push({ shower, presenter: t });
  }
}
// ── 2d. Sub-tabs: which presenter each editor TAB embeds ──────────────────────
// `private static final TabData SETTINGS = new TabDataImpl("Settings");` then
// `addTab(SETTINGS, new DocTabProvider<>(settingsPresenterProvider::get))` — the label the walk
// reads off the tab bar, joined to the provider's type. The walk records an editor's sub-tab under
// the EDITOR's name; this is what lets the ledger credit the presenter actually shown on it.
const allClasses = new Set(files.map(cls));
/** `class DocumentUserPermissionsTabProvider extends AbstractTabProvider<Doc, DocumentUserPermissionsPresenter>` */
const tabProviderPresenter = new Map();
for (const f of files) {
  const name = cls(f);
  if (!/TabProvider$/.test(name)) continue;
  const m = readSrc(f).match(/extends\s+[A-Za-z]+TabProvider<\s*[A-Za-z_][A-Za-z0-9_<>]*\s*,\s*([A-Za-z_][A-Za-z0-9_]*Presenter)\s*>/);
  if (m) tabProviderPresenter.set(name, m[1]);
}
const tabsOf = [];
for (const f of files) {
  const src = readSrc(f);
  const owner = cls(f);
  if (!/addTab\(/.test(src)) continue;
  const labels = new Map();
  for (const m of src.matchAll(/TabData\s+([A-Z_][A-Z0-9_]*)\s*=\s*new\s+TabDataImpl\(\s*"([^"]+)"/g)) labels.set(m[1], m[2]);
  // The builder form: `TabData WORDS = TabDataImpl.builder().label("Words")…` (Dictionary).
  for (const m of src.matchAll(/TabData\s+([A-Z_][A-Z0-9_]*)\s*=\s*TabDataImpl\.builder\(\)[\s\S]{0,200}?\.(?:label|withLabel)\(\s*"([^"]+)"/g)) labels.set(m[1], m[2]);
  // A KEYWORD is not a type. `return userRefPopupPresenter;` matched as a declaration of type
  // `return`, and because a later match overwrites an earlier one, the real declaration eleven lines
  // above it was lost — so `userRefPopupPresenter.show("Add User Or Group", …)` had no receiver type
  // and UserAndGroupsPresenter published no edge to the popup at all. 2,042 `return x;` statements in
  // the client, each one able to erase the type of whatever it returns.
  const typeOf = new Map();
  for (const d of src.matchAll(
    /(?:private|protected|public)?\s*(?:final\s+)?(?:Provider\s*<\s*([A-Za-z_][A-Za-z0-9_]*)\s*>|([A-Za-z_][A-Za-z0-9_]*)(?:\s*<[^<>;=()]*>)?)\s+([a-zA-Z_][A-Za-z0-9_]*)\s*[;=),]/g)) {
    if (JAVA_KEYWORDS.has(d[1] ?? d[2])) continue;
    typeOf.set(d[3], d[1] ?? d[2]);
  }
  // A `*TabProvider` stands for the presenter it provides: DocumentUserPermissionsTabProvider ->
  // DocumentUserPermissionsTabPresenter, MarkdownTabProvider(eventBus, markdownEditPresenterProvider)
  // -> MarkdownEditPresenter. Resolve the provider's own name first, then any Presenter-typed
  // argument it was built with.
  const presenterFor = (t) => {
    if (!t) return null;
    if (/Presenter$/.test(t)) return t;
    if (/TabProvider$/.test(t)) {
      // The provider CLASS says what it provides: `class XTabProvider extends
      // AbstractTabProvider<Doc, XPresenter>`. Name-stem guessing second.
      if (tabProviderPresenter.has(t)) return tabProviderPresenter.get(t);
      const stem = t.replace(/TabProvider$/, '');
      for (const c of [`${stem}TabPresenter`, `${stem}Presenter`, `${stem.replace(/Document$/, '')}Presenter`]) if (allClasses.has(c)) return c;
    }
    return null;
  };
  for (const m of src.matchAll(/addTab\(\s*([A-Z_][A-Z0-9_]*)\s*,\s*([\s\S]{0,700}?)\)\s*;/g)) {
    const tab = labels.get(m[1]);
    if (!tab) continue;
    // An anonymous provider names its presenter in the generic: `new AbstractTabProvider<Doc,
    // MetaPresenter>(eventBus) { … }` (Pipeline's Data tab, Dictionary's Words).
    const anon = m[2].match(/new\s+[A-Za-z]*TabProvider<\s*[A-Za-z_][A-Za-z0-9_]*\s*,\s*([A-Za-z_][A-Za-z0-9_]*Presenter)\s*>/);
    if (anon) { tabsOf.push({ owner, tab, presenter: anon[1] }); continue; }
    // DECLARED variables first — `settingsPresenterProvider` is a Provider<FeedSettingsPresenter> —
    // and class names only if none resolves. Taking the first identifier that matched anything made
    // every `new DocTabProvider<>(…)` tab resolve to the generic wrapper: 41 tabs called
    // DocTabPresenter, and eight distinct presenters credited from 107 tab visits.
    const ids = [...m[2].matchAll(/[a-zA-Z_][A-Za-z0-9_]*/g)].map((x) => x[0]);
    let presenter = null;
    for (const id of ids) { presenter = presenterFor(typeOf.get(id)); if (presenter) break; }
    if (!presenter) for (const id of ids) { presenter = presenterFor(allClasses.has(id) ? id : null); if (presenter && presenter !== 'DocTabPresenter') break; presenter = null; }
    if (presenter) tabsOf.push({ owner, tab, presenter });
  }
  // The LAZY form: `addTab(DOC_PERMS_TAB)` with no provider, and the content chosen later in
  // `getContent(tab, callback)` — `if (DOC_PERMS_TAB.equals(tab)) { … callback.onReady(x); }`,
  // where `x` is declared (`LazyValue<UserPermissionReportPresenter>` resolves through the generic)
  // or is a call whose provider is. UserTabPresenter's six tabs are all of this shape.
  for (const m of src.matchAll(/([A-Z_][A-Z0-9_]*)\s*\.equals\(\s*tab\s*\)\s*\)\s*\{(?=([\s\S]{0,500}))/g)) {
    const tab = labels.get(m[1]);
    if (!tab) continue;
    const body = m[2].split(/\}\s*else|\n\s*\}\s*\n/)[0];
    const ready = /onReady\(\s*([a-zA-Z_][A-Za-z0-9_]*)/.exec(body);
    if (!ready) continue;
    let presenter = presenterFor(typeOf.get(ready[1]));
    if (!presenter) {
      // `LazyValue<X> xLazyValue` — the local it was assigned from names the generic.
      const decl = new RegExp(`([A-Za-z_][A-Za-z0-9_]*Presenter)\\s+${ready[1]}\\s*=`).exec(body);
      if (decl) presenter = decl[1];
    }
    if (presenter && !tabsOf.some((t) => t.owner === owner && t.tab === tab)) tabsOf.push({ owner, tab, presenter });
  }
}

// ── 2e. Dashboard components: the tab label IS the component's display name ──
// `public static final ComponentType TYPE = new ComponentType(1, "table", "Table", …)` in each
// component presenter. A dashboard's sub-tabs are its components, labelled by that display name, so
// `DashboardSuperPresenter :: Table` is TablePresenter — which is how the five component presenters
// the walk visits on every dashboard get credited.
for (const f of files) {
  const src = readSrc(f);
  const m = src.match(/ComponentType\s+TYPE\s*=\s*new\s+ComponentType\(\s*\d+\s*,\s*"[^"]+"\s*,\s*"([^"]+)"/);
  if (!m) continue;
  for (const owner of ['DashboardSuperPresenter', 'DashboardPresenter']) tabsOf.push({ owner, tab: m[1], presenter: cls(f) });
}

// ── 2c. A dialog opened by an EVENT, not by `ShowPopupEvent.builder(...)` ────
// The single biggest class of dialog the ledger could not name was not exotic. `Save '…' as` — 98
// nodes — and `New <Doc Type>` — 35 more — are the SAME dialog, and no `builder(...)` site raises
// it. It is fired as an event:
//
//   ShowCreateDocumentDialogEvent.fire(this, "Save '" + docRef.getName() + "' as", ...)
//
// and answered by whoever implements `ShowCreateDocumentDialogEvent.Handler` — CreateDocumentPresenter.
// So an event with exactly ONE handler is an edge, on the same terms as a builder call: the file
// that fires it is the shower, the handler is what appears.
//
// Two filters, because an event bus will otherwise explain everything and explanation-of-everything
// is not evidence:
//
//   * the handler must SHOW ITSELF (`ShowPopupEvent.builder(this)`) — the same test the reachability
//     graph uses for "is a dialog". This is what drops `RefreshExplorerTreeEvent`,
//     `FocusExplorerFilterEvent` and `HighlightExplorerNodeEvent`, all handled by NavigationPresenter,
//     which redraws the explorer rather than opening anything.
//   * `ShowPopupEvent` itself is excluded — it is the popup framework's own event, already mined
//     properly above, and one presenter implements its Handler, which would have made every popup in
//     the application an edge to that one class.

/**
 * A caption built at runtime, reduced to what is CONSTANT about it.
 *
 *   "Save '" + docRef.getName() + "' as"   ->  Save '…' as
 *   "New " + documentType.getDisplayType() ->  New …
 *
 * The gaps are exactly where the ledger already normalises a document's name to `'…'`, so the
 * template joins to an observed caption without either side guessing. Returns null when nothing in
 * the expression is a literal, because `caption` alone names nothing.
 */
function captionTemplate(expr) {
  if (!expr || !expr.includes('"')) return null;
  const parts = [];
  for (const piece of expr.split('+')) {
    const lit = /^\s*"([^"]*)"\s*$/.exec(piece);
    parts.push(lit ? lit[1] : '…');
  }
  const t = parts.join('').replace(/…+/g, '…').replace(/\s+/g, ' ').trim();
  // The constant part has to be a WORD. `x + " - " + y` reduces to `… - …`, which matches half the
  // application's captions and names nothing — a template with no word in it is not evidence.
  return /[A-Za-z]{3}/.test(t.replace(/…/g, ' ')) ? t : null;
}

/**
 * The caption at a show site: `.caption(<expr>)`, where the expression may be a literal, a
 * concatenation, or a local holding either.
 */
function captionsAt(tail, strings) {
  const at = tail.indexOf('.caption(');
  if (at < 0) return [];
  const expr = splitArgs(tail.slice(at + '.caption('.length))[0]?.trim();
  if (!expr) return [];
  const own = [...new Set(branches(expr).map(captionTemplate).filter(Boolean))];
  const direct = own.length === 1 ? own[0] : null;
  if (own.length && !own.some((c) => c.includes('…'))) return own;
  if (direct && !direct.includes('…')) return [direct];
  // A bare local: look up what it was built from. Only one hop — chasing further is how a miner
  // starts inventing captions out of code it has not really read.
  if (/^[a-zA-Z_][A-Za-z0-9_]*$/.test(expr) && strings.has(expr)) {
    const built = strings.get(expr).flatMap(branches).map(captionTemplate).filter(Boolean);
    if (built.length) return [...new Set(built)];
  }
  return own.length ? own : [];
}

/**
 * A ternary is two captions, not one. `isSingleDocRef() ? "Edit Tags on " + name : "Edit Tags on "
 * + n + " Documents"` is how the explorer dialogs caption themselves, and templating the whole
 * expression reduces it to gaps. Only split when the `?` precedes any string literal, so a `?` INSIDE
 * a caption is left alone.
 */
function branches(expr) {
  const q = expr.indexOf('"');
  const t = expr.indexOf('?');
  if (t < 0 || (q >= 0 && q < t)) return [expr];
  const rest = expr.slice(t + 1);
  const out = [];
  let depth = 0;
  let cur = '';
  let inStr = false;
  for (let i = 0; i < rest.length; i += 1) {
    const ch = rest[i];
    if (inStr) { cur += ch; if (ch === '"' && rest[i - 1] !== '\\') inStr = false; continue; }
    if (ch === '"') { inStr = true; cur += ch; continue; }
    if (ch === '(' || ch === '[') depth += 1;
    if (ch === ')' || ch === ']') depth -= 1;
    if (ch === ':' && depth === 0 && rest[i + 1] !== ':' && rest[i - 1] !== ':') { out.push(cur); cur = ''; continue; }
    cur += ch;
  }
  out.push(cur);
  return out;
}

/**
 * The captions a show site receives through the ENCLOSING METHOD's parameters. Finds the method
 * declaration the site sits in, the parameter index of each bare-identifier argument, then every
 * same-file call of that method and the literal (or template) it passes at that index.
 */
function captionsViaHelper(src, at, args) {
  const before = src.slice(0, at);
  const decls = [...before.matchAll(/(?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?[A-Za-z_][A-Za-z0-9_<>,\s]*?\s+([a-zA-Z_][A-Za-z0-9_]*)\s*\(([^)]*)\)\s*(?:throws\s+[A-Za-z0-9_.,\s]+)?\{/g)];
  const decl = decls[decls.length - 1];
  if (!decl) return [];
  const params = decl[2].split(',').map((p) => p.trim().split(/\s+/).pop()).filter(Boolean);
  const out = new Set();
  for (const a of args) {
    const id = a.trim();
    // Only a parameter NAMED as a caption. `showSummary` also takes `postAction` and `action`
    // ("delete", "deleted", …), which are words in the dialog's body, not its caption.
    const idx = /^[a-zA-Z_]*(?:caption|title)$/i.test(id) ? params.indexOf(id) : -1;
    if (idx < 0) continue;
    for (const call of src.matchAll(new RegExp(`(?<![A-Za-z0-9_.])${decl[1]}\\(`, 'g'))) {
      if (call.index === decl.index + decl[0].indexOf(decl[1])) continue;
      const passed = splitArgs(src.slice(call.index + call[0].length, call.index + call[0].length + 600))[idx];
      if (!passed) continue;
      for (const c of branches(passed).map(captionTemplate).filter(Boolean)) out.add(c);
    }
  }
  return [...out];
}

/** Split an argument list at top-level commas, so a nested `f(a, b)` stays one argument. */
function splitArgs(text) {
  const out = [];
  let depth = 0;
  let cur = '';
  let inStr = false;
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    if (inStr) {
      cur += ch;
      if (ch === '"' && text[i - 1] !== '\\') inStr = false;
      continue;
    }
    if (ch === '"') { inStr = true; cur += ch; continue; }
    if (ch === '(' || ch === '[') depth += 1;
    if (ch === ')' || ch === ']') { if (depth === 0) break; depth -= 1; }
    if (ch === ',' && depth === 0) { out.push(cur); cur = ''; continue; }
    cur += ch;
  }
  out.push(cur);
  return out;
}

const eventEdges = [];
const eventEdgesAmbiguous = [];
for (const f of files) {
  const src = readSrc(f);
  const shower = cls(f);
  // The tail is a LOOKAHEAD, not a capture that consumes: a consumed 400-character tail swallowed
  // every `fire(` within 400 characters of the previous one, so DependenciesPresenter's five fires
  // yielded two and `ShowDependenciesInfoDialogEvent` — its Properties action, one handler — was
  // never an edge. The same bug hit `ShowPopupEvent.builder(` in August (6ec436a); this is the
  // same shape one idiom over.
  for (const m of src.matchAll(/([A-Za-z0-9_]+Event)\s*\.\s*fire\((?=([\s\S]{0,400}))/g)) {
    const [, event, tail] = m;
    if (event === 'ShowPopupEvent') continue;
    const hs = handlerOf.get(event);
    if (!hs) continue;
    const shown = [...hs].filter((h) => selfShows.has(h));
    if (!shown.length) continue;
    if (shown.length > 1) { eventEdgesAmbiguous.push({ event, shower, candidates: shown }); continue; }
    const presenter = shown[0];
    // The caption is whichever argument is a string expression; `fire(this, caption, …)` in practice.
    let caption = null;
    for (const arg of splitArgs(tail).slice(0, 4)) {
      caption = captionTemplate(arg);
      if (caption) break;
    }
    eventEdges.push(caption ? { shower, presenter, caption, via: event } : { shower, presenter, via: event });
  }
}
showEdges.push(...eventEdges);

/** Kept as its own list because consumers join on it; every entry is an edge that HAS a caption. */
const captionOf = showEdges.filter((e) => e.caption)
  .map((e) => ({ caption: e.caption, presenter: e.presenter, shower: e.shower }));

// ── 3. Which presenter does a plugin actually reveal? ────────────────────────
// `DependenciesPlugin` opens `DependenciesPresenter`; the naming is consistent, but verify it against
// the file list rather than assuming, and record the ones where it does not hold.
/** plugin -> the presenter it declares a Provider for, which is the authoritative answer. */
const declaredPresenter = new Map();
for (const f of files) {
  const name = cls(f);
  if (!/Plugin$/.test(name)) continue;
  const src = readSrc(f);
  // `private final Provider<QueryDocPresenter> editorProvider;` — the plugin says what it opens.
  const m = src.match(/Provider<\s*([A-Za-z_][A-Za-z0-9_]*Presenter)\s*>\s+(?:editorProvider|presenterProvider|[a-z][A-Za-z0-9_]*Provider)/)
    ?? src.match(/Provider<\s*([A-Za-z_][A-Za-z0-9_]*Presenter)\s*>/);
  if (m) declaredPresenter.set(name, m[1]);
}
/**
 * Which presenter a plugin reveals.
 *
 * The DECLARED Provider first, guessing by name only as a fallback. `QueryPlugin` opens
 * `QueryDocPresenter`, but the stem guess found `QueryPresenter` — a real class, and the wrong one:
 * it is the dashboard's query component. Observations from the Query document editor were then
 * attributed to a screen we had never opened, and the capability diff duly reported seven buttons
 * "missing" from it. A plausible wrong name is worse than no name.
 */
/** `class UsersPlugin extends MonitoringPlugin<UsersPresenter>` — the base's type argument IS the presenter. */
const genericPresenter = new Map();
for (const f of files) {
  const name = cls(f);
  if (!/Plugin$/.test(name)) continue;
  const m = readSrc(f).match(new RegExp(`class\\s+${name}\\s+extends\\s+[A-Za-z]+Plugin<\\s*([A-Za-z_][A-Za-z0-9_]*Presenter)\\b`));
  if (m) genericPresenter.set(name, m[1]);
}
/** `class AnnotationCollectionPlugin extends AnnotationPlugin` — the parent plugin's presenter is inherited. */
const parentPlugin = new Map();
for (const f of files) {
  const name = cls(f);
  if (!/Plugin$/.test(name)) continue;
  const m = readSrc(f).match(new RegExp(`class\\s+${name}\\s+extends\\s+([A-Za-z_][A-Za-z0-9_]*Plugin)\\b`));
  if (m && allClasses.has(m[1])) parentPlugin.set(name, m[1]);
}
const presenterOf = (plugin, depth = 0) => {
  if (genericPresenter.has(plugin)) return genericPresenter.get(plugin);
  if (!declaredPresenter.has(plugin) && parentPlugin.has(plugin) && depth < 4) {
    const inherited = presenterOf(parentPlugin.get(plugin), depth + 1);
    if (inherited) return inherited;
  }
  if (declaredPresenter.has(plugin)) return declaredPresenter.get(plugin);
  const stem = plugin.replace(/Plugin$/, '');
  for (const suffix of ['Presenter', 'TabPresenter', 'ListPresenter']) {
    if (allClasses.has(stem + suffix)) return stem + suffix;
  }
  return null;
};

// ── 4. Assemble ─────────────────────────────────────────────────────────────
const recipes = [];
for (const r of menuRoutes) {
  const target = presenterOf(r.owner);
  recipes.push({
    presenter: target,
    via: 'menu',
    owner: r.owner,
    // MAIN_MENU items sit directly under the hamburger; everything else is one group deep.
    route: r.group ? [{ via: 'menu', group: r.group, leaf: r.leaf }] : [{ via: 'menu', group: r.leaf }],
    label: r.group ? `Main Menu > ${r.group} > ${r.leaf}` : `Main Menu > ${r.leaf}`,
    resolved: Boolean(target),
  });
}
for (const [plugin, type] of docTypes) {
  const target = presenterOf(plugin);
  recipes.push({
    presenter: target,
    via: 'doc',
    owner: plugin,
    route: [{ via: 'newDoc', type }],
    label: `Explorer > New > … > ${type}`,
    resolved: Boolean(target),
  });
}

// ── 5. What could not be routed, said out loud ──────────────────────────────
const csv = readFileSync(oracle('gwt-inventory.csv'), 'utf8').split('\n').slice(1).filter(Boolean);
const presenters = [];
for (const line of csv) {
  const c = line.split(',');
  // Same filter the reachability graph uses: role=Presenter, top=Screen/Dialog. Matching on the wrong
  // column found 16 presenters instead of 414 and reported "16 of 16 unrouted", which looks like a
  // finding about the product and was a finding about this file.
  if (c[4] !== 'Presenter') continue;
  if (c[6] !== 'Screen/Dialog' && c[6] !== 'Shared Screen/Dialog') continue;
  presenters.push(cls(c[0]));
}
const routed = new Set(recipes.filter((r) => r.presenter).map((r) => r.presenter));
const unrouted = presenters.filter((p) => !routed.has(p));

writeFileSync(OUT_JSON, JSON.stringify({ recipes, captionOf, showEdges, eventEdgesAmbiguous, tabsOf, unrouted }, null, 1));

const lines = ['# Route recipes — the clicks that reach each presenter', '',
  '**Generated** by `stroom-stroom-gwt-suite/tools/build-route-recipes.mjs`. Do not hand-edit.', '',
  '`reachability-graph.md` gives the topology; this gives the words to click. Mined from GWT\'s own',
  'declarative menu registration, so it re-derives instead of rotting.', '',
  '## Totals', '', '| | count |', '| --- | ---: |',
  `| Menu recipes | ${recipes.filter((r) => r.via === 'menu').length} |`,
  `| Document-type recipes | ${recipes.filter((r) => r.via === 'doc').length} |`,
  `| …naming a presenter we can check off | ${recipes.filter((r) => r.resolved).length} |`,
  `| Dialog captions mapped to a presenter | ${captionOf.length} |`,
  `| Parent→child show edges (\`shower\` opens \`presenter\`) | ${showEdges.length} |`,
  `| …of those with no literal caption, so only the ROUTE can name them | ${showEdges.length - captionOf.length} |`,
  `| Presenters with NO recipe (see below) | ${unrouted.length} of ${presenters.length} |`, ''];
lines.push('## Menu recipes', '');
for (const r of recipes.filter((x) => x.via === 'menu').sort((a, b) => a.label.localeCompare(b.label))) {
  lines.push(`* \`${r.label}\` → **${r.presenter ?? '(unresolved)'}** _(${r.owner})_`);
}
lines.push('', '## Document-type recipes', '');
for (const r of recipes.filter((x) => x.via === 'doc').sort((a, b) => a.label.localeCompare(b.label))) {
  lines.push(`* \`${r.label}\` → **${r.presenter ?? '(unresolved)'}** _(${r.owner})_`);
}
lines.push('', '## No recipe yet', '',
  'Named rather than omitted. Most are dialogs reached FROM one of the above — the parent edges are in',
  '`reachability-graph.md` — plus the embedded panels, which have no route of their own by definition.', '');
for (const u of unrouted) lines.push(`* ${u}`);
writeFileSync(OUT_MD, lines.join('\n'));

console.log(`${recipes.length} recipes (${recipes.filter((r) => r.via === 'menu').length} menu, ` +
  `${recipes.filter((r) => r.via === 'doc').length} doc), ${recipes.filter((r) => r.resolved).length} resolved`);
console.log(`${unrouted.length} of ${presenters.length} presenters have no recipe -> ${OUT_MD}`);
