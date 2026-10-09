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
// Seed the DATA the unreached doors need — the counterpart of seed.mjs, which seeds document TYPES.
//
//   URL=http://localhost:8080 STROOM_USER=admin STROOM_PASS=… node stroom-gwt/stroom-gwt-suite/seed-data.mjs
//   ... DRYRUN=1 node stroom-gwt/stroom-gwt-suite/seed-data.mjs           # report what is missing, create nothing
//   ... ONLY="tags,annotation" node stroom-gwt/stroom-gwt-suite/seed-data.mjs
//   ... REMOVE=1 node stroom-gwt/stroom-gwt-suite/seed-data.mjs           # take every seeded item out again
//
// After directed pass 5 (COVERAGE-PLAN.md) the ledger's unreached doors were classified, and ten of
// the twenty-one sat behind data this instance simply did not hold: no node group means the Node
// Groups screen's Edit is never enabled, no saved tab session means `Open Tab Session` is disabled,
// no annotation carries a comment entry so the editor has nothing to press, no visualisation has an
// asset to rename. A walk cannot reach an Edit dialog for a row that does not exist, and no amount
// of walking changes that. The remedy is a row.
//
// Everything here goes through the REST API from INSIDE the signed-in page — the same session cookie
// and `X-CSRF` header the GWT client sends — because this is data, not UI: what the walk then does
// with it is the test. seed.mjs drives the UI for its documents because creating a document IS one
// of the surfaces under test; a node group's row is not.
//
// THIS SCRIPT MUTATES THE INSTANCE. Like seed.mjs it runs WITHOUT `attachReadOnlyGuard`,
// deliberately and visibly. Every item is created only if nothing of its name exists, so a rerun is
// a no-op and an interrupted run resumes. The one exception is the RESULT STORE: a store lives in a
// node's memory for 24h (`ResultStoreSettingsFactory`) and is gone after a restart, so it is
// re-created whenever the list is empty — a walk of the Result Stores dialog wants one to exist NOW.
//
// Seeding changes what the instance answers, which invalidates every baseline recorded before it —
// this suite's corpus and `compare/`'s. A full re-record follows a successful seed (README.md).
import { writeFile, mkdir } from 'node:fs/promises';
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { closeDialogs } from './compare/lib/structure.mjs';
import { SEED_DATA } from './lib/seed-names.mjs';

// The suite's folder, so its corpus and output are found wherever the suite is run from
const SUITE = import.meta.dirname;

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const URL = env('URL', 'http://localhost:8080');
const FOLDER = env('FOLDER', 'Seed Content');
const DRYRUN = env('DRYRUN', '') === '1';
const REMOVE = env('REMOVE', '') === '1';
const ONLY = env('ONLY') ? new Set(env('ONLY').split(',').map((s) => s.trim())) : null;

/** The names every seeded item carries — shared with the walker, which addresses them by name. */
const SEED = SEED_DATA;

const browser = await chromium.launch({ headless: env('HEADLESS', '1') !== '0' });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
gwt.baseUrl = URL;
await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
await gwt.signInFully(page, credentials());
await closeDialogs(page);
await mkdir(`${SUITE}/out/seed`, { recursive: true });

/**
 * A REST call from inside the page. The GWT client's session is a cookie plus an `X-CSRF` header the
 * server's filter requires on every request; `page.request` from outside answers 403 without it.
 * A non-2xx is thrown with the server's message, because a seed that silently failed is worse than
 * none (seed.mjs learned that from an Alert behind a New dialog).
 */
const api = async (method, path, body, { raw = false } = {}) => {
  const r = await page.evaluate(async ([method, path, body, raw]) => {
    // `raw`: a resource that takes a bare `String` (NodeGroupResource.create) reads the entity as
    // text whatever the media type says, so a JSON-encoded name arrives with its quotes and is
    // stored that way. The media type still has to be the one the resource consumes.
    const res = await fetch(`/api${path}`, {
      method,
      body: body == null ? undefined : raw ? String(body) : JSON.stringify(body),
      headers: { 'X-CSRF': '1', Accept: 'application/json', 'Content-Type': 'application/json' },
    });
    const text = await res.text();
    let json;
    try { json = JSON.parse(text); } catch { json = text; }
    return { status: res.status, json };
  }, [method, path, body ?? null, raw]);
  if (r.status < 200 || r.status >= 300) {
    const msg = typeof r.json === 'object' && r.json ? [r.json.message, r.json.details].filter(Boolean).join(': ') || JSON.stringify(r.json) : String(r.json);
    throw new Error(`${method} ${path} → ${r.status} ${String(msg).slice(0, 200)}`);
  }
  return r.json;
};

/**
 * The whole explorer tree. The endpoint answers with only the OPEN folders' children, so open every
 * folder (and folder-like GitRepo) it shows and ask again until no new one appears.
 */
async function fetchTree() {
  const opened = new Map();
  let tree;
  let more = true;
  while (more) {
    const openItems = [...opened].map(([uuid, type]) => ({ type, uuid, rootNodeUuid: '0' }))
      .concat([{ type: 'System', uuid: '0', rootNodeUuid: '0' }]);
    tree = await api('POST', '/explorer/v2/fetchExplorerNodes', {
      filter: { includedTypes: null, tags: null, nameFilter: null, requiredPermissions: ['VIEW'] },
      openItems, temporaryOpenedItems: [], ensureVisible: [], minDepth: 1, showAlerts: false,
    });
    more = false;
    const collect = (n) => {
      if ((n.type === 'Folder' || n.type === 'GitRepo') && !opened.has(n.uuid)) { opened.set(n.uuid, n.type); more = true; }
      for (const c of n.children ?? []) collect(c);
    };
    for (const r of tree.rootNodes ?? []) collect(r);
  }
  const docs = [];
  const walk = (n, parent) => {
    docs.push({ type: n.type, uuid: n.uuid, name: n.name, parent, node: n });
    for (const c of n.children ?? []) walk(c, n);
  };
  for (const r of tree.rootNodes ?? []) walk(r, null);
  return docs;
}
const docRef = (d) => ({ type: d.type, uuid: d.uuid, name: d.name });

const tagCriteria = (displayValue) => ({
  expression: { type: 'operator', op: 'AND', enabled: true,
    children: [{ type: 'term', enabled: true, field: 'TypeId', condition: 'EQUALS', value: displayValue }] },
  pageRequest: { offset: 0, length: 1000 },
});
const findTags = async (displayValue) => (await api('POST', '/annotation/v1/findAnnotationTags', tagCriteria(displayValue))).values ?? [];
const findAnnotations = async () => (await api('POST', '/annotation/v1/findAnnotations', { pageRequest: { offset: 0, length: 1000 } })).values ?? [];
const findNodeGroups = async () => (await api('POST', '/node/nodeGroup/v2/find', { pageRequest: { offset: 0, length: 1000 }, sortList: [], filter: null })).values ?? [];
const listTabSessions = async () => (await api('GET', '/tabSession/v1')) ?? [];
const nodeNames = async () => (await api('GET', '/node/v1/all')) ?? [];
const findStores = async () => {
  const out = [];
  for (const node of await nodeNames()) {
    const page = await api('POST', `/result-store/v1/find/${node}`, { pageRequest: { offset: 0, length: 1000 } }).catch(() => null);
    for (const v of page?.values ?? []) out.push({ node, ...v });
  }
  return out;
};

const results = [];
const report = (item, status, detail = '') => {
  const mark = { present: '=', created: '✓', dryrun: '+', failed: '✗', removed: '−', skipped: '·' }[status] ?? '?';
  console.log(`  ${mark} ${item}${detail ? ` — ${detail}` : ''}`);
  results.push({ item, status, detail });
};
const wants = (key) => !ONLY || ONLY.has(key);

/** One item: `have` says what exists, `create` makes it, `remove` takes it away. */
async function item(key, label, { have, create, remove }) {
  if (!wants(key)) return;
  try {
    const existing = await have();
    if (REMOVE) {
      if (!existing) return report(label, 'skipped', 'not present');
      if (DRYRUN) return report(label, 'dryrun', 'would remove');
      await remove(existing);
      return report(label, 'removed');
    }
    if (existing) return report(label, 'present');
    if (DRYRUN) return report(label, 'dryrun', 'would create');
    const detail = await create();
    return report(label, 'created', detail);
  } catch (e) {
    return report(label, 'failed', String(e?.message ?? e).slice(0, 200));
  }
}

console.log(`${REMOVE ? 'removing' : DRYRUN ? 'checking' : 'seeding'} data on ${URL}`);
const docs = await fetchTree();
const seedFolder = docs.find((d) => d.type === 'Folder' && d.name === FOLDER);
if (!seedFolder && !REMOVE) console.log(`  (no "${FOLDER}" folder — run seed.mjs first; the visualisation goes there)`);

// ── A node group: Monitoring › Node Groups › Edit (NodeGroupEditPresenter) ────
await item('nodeGroup', `node group "${SEED.nodeGroup}"`, {
  have: async () => (await findNodeGroups()).find((g) => g.name === SEED.nodeGroup),
  create: async () => { const g = await api('POST', '/node/nodeGroup/v2', SEED.nodeGroup, { raw: true }); return `id ${g.id}`; },
  remove: (g) => api('DELETE', `/node/nodeGroup/v2/${g.id}`),
});

// ── Two tab sessions: Main Menu › Navigation › Open Tab Session shows the CHOOSER only for ≥2
// (TabSessionManager: none → an info alert, one → opened straight away). Each names two real docs.
for (const name of SEED.tabSessions) {
  await item('tabSessions', `tab session "${name}"`, {
    have: async () => (await listTabSessions()).find((s) => s.name === name),
    create: async () => {
      const pick = docs.filter((d) => d.type !== 'Folder' && d.type !== 'System' && d.type !== 'Favourites' && d.type !== 'GitRepo');
      const refs = (seedFolder ? pick.filter((d) => d.parent?.uuid === seedFolder.uuid) : pick).slice(0, 2).map(docRef);
      if (!refs.length) throw new Error('no documents to put in the session');
      await api('POST', '/tabSession/v1', { name, docRefs: refs });
      return refs.map((r) => `${r.type} "${r.name}"`).join(', ');
    },
    remove: (s) => api('DELETE', '/tabSession/v1', { sessionId: s.sessionId }),
  });
}

// ── One tag of each type: Annotations › Statuses / Labels / Collections / Comments › Edit (AnnotationTagEdit) ──
for (const [type, displayValue, name, tagText] of SEED.tags) {
  await item('tags', `annotation ${displayValue.toLowerCase()} "${name}"`, {
    have: async () => (await findTags(displayValue)).find((t) => t.name === name),
    create: async () => { const t = await api('POST', '/annotation/v1/createAnnotationTag', { type, name, tagText: tagText ?? null }); return `uuid ${t.uuid}`; },
    remove: (t) => api('DELETE', '/annotation/v1/deleteAnnotationTag', t),
  });
}

// ── An annotation WITH A COMMENT: the editor's history holds a comment entry whose mousedown menu
// offers Edit Entry (CommentEditPresenter). The walk's `annotation` seed selects it by title.
await item('annotation', `annotation "${SEED.annotation.title}"`, {
  // The annotation without its comment counts as absent: `create` adds the comment to it.
  have: async () => {
    const a = (await findAnnotations()).find((x) => x.name === SEED.annotation.title);
    if (!a || REMOVE) return a;
    const entries = await api('POST', '/annotation/v1/getAnnotationEntries', docRef(a));
    return (entries ?? []).some((e) => e.entryType === 'COMMENT') ? a : null;
  },
  create: async () => {
    // `create` writes TITLE/STATUS/ASSIGNED entries only (AnnotationDaoImpl.createAnnotation); the
    // comment is a CHANGE, the way the Create dialog's own comment box sends it.
    const a = (await findAnnotations()).find((x) => x.name === SEED.annotation.title) ?? await api('POST', '/annotation/v1/create', {
      title: SEED.annotation.title, subject: SEED.annotation.subject, status: null, assignTo: null,
      comment: null, table: null, linkedEvents: [], linkedAnnotations: [],
    });
    await api('POST', '/annotation/v1/change', { annotationRef: docRef(a), change: { type: 'comment', comment: SEED.annotation.comment } });
    const entries = await api('POST', '/annotation/v1/getAnnotationEntries', docRef(a));
    const comment = (entries ?? []).find((e) => e.entryType === 'COMMENT');
    if (!comment) throw new Error(`created id ${a.id} but no COMMENT entry came back (${(entries ?? []).map((e) => e.entryType).join(', ') || 'none'})`);
    return `id ${a.id}, ${entries.length} entries`;
  },
  remove: (a) => api('DELETE', '/annotation/v1/delete', docRef(a)),
});

// ── A visualisation with an ASSET: the Assets tab's tree has an item to select, and Rename opens
// VisualisationAssetsEditAssetDialogPresenter. Its own document in the seed folder, so the walk can
// open it by name rather than by "the first Visualisation in the tree".
await item('visualisation', `visualisation "${SEED.visualisation}" with asset "${SEED.asset}"`, {
  // The document without its asset counts as absent: `create` adds the file to an existing document.
  have: async () => {
    const doc = docs.find((d) => d.type === 'Visualisation' && d.name === SEED.visualisation);
    if (!doc) return null;
    if (REMOVE) return doc;
    const assets = await api('GET', `/visualisationAssets/fetchDraftAssets/${doc.uuid}`);
    return (assets.assets ?? []).some((a) => a.path.replace(/^\//, '') === SEED.asset) ? doc : null;
  },
  create: async () => {
    if (!seedFolder) throw new Error(`no "${FOLDER}" folder to create the visualisation in`);
    const existing = docs.find((d) => d.type === 'Visualisation' && d.name === SEED.visualisation);
    const node = existing?.node ?? await api('POST', '/explorer/v2/create', {
      docType: 'Visualisation', docName: SEED.visualisation, destinationFolder: seedFolder.node, permissionInheritance: 'DESTINATION',
    });
    await api('PUT', `/visualisationAssets/updateNewFile/${node.uuid}`, { path: SEED.asset, resourceKey: null });
    // Draft → live, so the document is not left dirty (a dirty Assets tab has Save lit on open).
    await api('PUT', `/visualisationAssets/saveDraftToLive/${node.uuid}`);
    return `${existing ? 'existing doc' : 'created doc'} ${node.uuid}`;
  },
  remove: (doc) => api('DELETE', '/explorer/v2/delete', { docRefs: [docRef(doc)] }),
});
// ── A Git repository with a URL: every GitRepo here is a content pack (`contentStoreContentPackId`
// set), and GitRepoSettingsViewImpl hides `Push to Git` for those, and for a repo with no URL or a
// pinned commit. A plain repo with a URL shows it, and the click opens the commit dialog
// (GitRepoCommitDialogPresenter) BEFORE anything is pushed — the push is the OK, which the guard
// blocks. The URL is unreachable by design.
await item('gitRepo', `git repository "${SEED.gitRepo}" with a URL`, {
  have: async () => {
    const doc = docs.find((d) => d.type === 'GitRepo' && d.name === SEED.gitRepo);
    if (!doc || REMOVE) return doc;
    const full = await api('GET', `/gitRepo/v1/${doc.uuid}`);
    return full?.url === SEED.gitRepoUrl && !full.commit && !full.contentStoreContentPackId ? doc : null;
  },
  create: async () => {
    if (!seedFolder) throw new Error(`no "${FOLDER}" folder to create the repository in`);
    const existing = docs.find((d) => d.type === 'GitRepo' && d.name === SEED.gitRepo);
    const node = existing?.node ?? await api('POST', '/explorer/v2/create', {
      docType: 'GitRepo', docName: SEED.gitRepo, destinationFolder: seedFolder.node, permissionInheritance: 'DESTINATION',
    });
    const full = await api('GET', `/gitRepo/v1/${node.uuid}`);
    await api('PUT', `/gitRepo/v1/${node.uuid}`, { ...full, url: SEED.gitRepoUrl, branch: 'main', path: '', commit: null, autoPush: false });
    return `${existing ? 'existing doc' : 'created doc'} ${node.uuid}`;
  },
  remove: (doc) => api('DELETE', '/explorer/v2/delete', { docRefs: [docRef(doc)] }),
});

// ── A result store: the Search Result Stores dialog lists one, whose row enables Store Settings
// (ResultStoreSettingsPresenter). A search run here is not a tab, so `destroyOnTabClose` never
// fires; the store lives its 24h idle/live default on the node that ran it.
await item('resultStore', 'a search result store', {
  have: async () => { const stores = await findStores(); return stores.length ? stores : null; },
  create: async () => {
    const r = await api('POST', '/query/v1/search', {
      query: SEED.query, incremental: false, storeHistory: false, timeout: 30_000,
      queryContext: { params: null, timeRange: null, queryInfo: null, dateTimeSettings: null },
      searchRequestSource: { sourceType: 'QUERY_UI', ownerDocRef: null, componentId: null, componentName: null },
    });
    if (!r?.queryKey) throw new Error(`search answered without a queryKey: ${JSON.stringify(r).slice(0, 160)}`);
    const stores = await findStores();
    const mine = stores.find((s) => s.queryKey?.uuid === r.queryKey.uuid);
    if (!mine) throw new Error(`searched (${r.queryKey.uuid}) but no store listed on ${(await nodeNames()).join('/')}`);
    return `queryKey ${r.queryKey.uuid} on ${mine.node}, ${r.complete ? 'complete' : 'running'}`;
  },
  remove: async (stores) => { for (const s of stores) await api('POST', `/result-store/v1/destroy/${s.node}`, { queryKey: s.queryKey }); },
});

await browser.close();

const counts = {};
for (const r of results) counts[r.status] = (counts[r.status] ?? 0) + 1;
console.log(`\n${Object.entries(counts).map(([k, v]) => `${v} ${k}`).join(', ') || 'nothing to do'}`);
await writeFile(`${SUITE}/out/seed/seed-data.json`, JSON.stringify({ url: URL, at: new Date().toISOString(), dryrun: DRYRUN, remove: REMOVE, results }, null, 1));
if (results.some((r) => r.status === 'created') && !DRYRUN) {
  console.log('the instance has changed: re-record the corpus (README.md) before the next suite run');
}
process.exit(results.some((r) => r.status === 'failed') ? 1 : 0);
