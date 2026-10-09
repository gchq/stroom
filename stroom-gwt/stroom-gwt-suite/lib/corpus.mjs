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
// The recorded corpus: everything the GWT UI asked the server for, and what came back.
//
// Two halves, kept apart because they are refreshed on different cadences:
//
//   api/    - the REST exchanges, as SEQUENCES per request. Refresh whenever the data or the
//             endpoints change.
//   app/    - the compiled UI itself (the .nocache.js bootstrap, the selected permutation, CSS,
//             fonts, images). Refresh only when GWT is rebuilt.
//
// Recording both is what lets the suite run with no Stroom instance at all: `serve.mjs` puts the
// app back on an HTTP origin and answers its API calls from the corpus, so the GWT UI boots,
// navigates and renders with nothing behind it.
//
// Responses are stored per key as an ORDERED LIST, not a single value. Stroom's search endpoints
// poll — the client asks the same question until the result is complete — so a corpus that replays
// the first answer forever leaves those screens spinning. The list is replayed in order and its
// last entry repeats, which also means a poll that outlives the recording still terminates on the
// completed response rather than restarting.
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, readdirSync, writeFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';

const sha1 = (s) => createHash('sha1').update(s).digest('hex').slice(0, 16);

export const isApi = (url) => /\/api\//.test(url);

/** Path + query without the origin, so a corpus recorded from :8080 replays on any port. */
export function pathOf(url) {
  try {
    const u = new URL(url);
    return u.pathname + u.search;
  } catch {
    return url;
  }
}

export const keyExact = (method, url, body) => `${method} ${pathOf(url)} #${sha1(body || '')}`;
export const keyNoBody = (method, url) => `${method} ${pathOf(url)}`;
export const keyPath = (method, url) => `${method} ${pathOf(url).split('?')[0]}`;

const API_MANIFEST = (dir) => join(dir, 'api', 'manifest.json');
const APP_MANIFEST = (dir) => join(dir, 'app', 'manifest.json');

const write = (file, buf) => {
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, buf);
};

export function loadCorpus(dir) {
  if (!existsSync(API_MANIFEST(dir))) return null;
  return {
    api: JSON.parse(readFileSync(API_MANIFEST(dir), 'utf8')),
    app: existsSync(APP_MANIFEST(dir)) ? JSON.parse(readFileSync(APP_MANIFEST(dir), 'utf8')) : { entries: {} },
    dir,
  };
}

/**
 * Record every exchange a page makes into `dir`.
 *
 * API calls are keyed and sequenced; everything else is stored once per path, because the app
 * bundle does not vary within a run.
 */
/** Distinguishes the blob files of recorders that share a corpus directory. */
let recorderSeq = 0;
/** Distinguishes the blob files of separate recording SESSIONS. See `sessionToken`. */
const sessionByDir = new Map();

/**
 * The next unused blob-name prefix for this corpus directory.
 *
 * `save()` MERGES into the manifest already on disk, so a re-record keeps entries recorded by an
 * earlier session. Those entries name blob FILES — and a per-process counter starting at zero meant
 * the second session rewrote `r0b00000`, `r0b00001` … with entirely unrelated bodies while the old
 * manifest entries went on pointing at them.
 *
 * The result is a corpus that looks healthy — every key present, zero misses at replay — and serves
 * the wrong bytes. It cost a full re-record to find: `GET /api/dictionary/v1/<uuid>` answered 200
 * with `{"authenticated":true,…}`, so the editor never opened, and 95 targets failed with no error
 * anywhere. A session token in the name makes the collision impossible rather than unlikely.
 *
 * Derived by scanning what is already there rather than from a clock, so the names stay short and a
 * corpus is reproducible.
 */
function sessionToken(dir) {
  if (sessionByDir.has(dir)) return sessionByDir.get(dir);
  let max = -1;
  for (const sub of ['api', 'app']) {
    const d = join(dir, sub);
    if (!existsSync(d)) continue;
    for (const f of readdirSync(d)) {
      const m = /^s(\d+)r/.exec(f);
      if (m) max = Math.max(max, Number(m[1]));
    }
  }
  const token = `s${max + 1}`;
  sessionByDir.set(dir, token);
  return token;
}

export async function attachRecorder(page, dir) {
  const api = new Map(); // key -> [{status, contentType, body}]
  const app = new Map(); // path -> {status, contentType, body}
  // Each recorder needs its OWN blob prefix, and so does each SESSION. A run attaches more than one
  // recorder — the interaction targets record from their own page — and a per-recorder counter
  // starting at zero made the second recorder overwrite `b00000`, `b00001` … under the first one's
  // manifest entries. `sessionToken` closes the same hole between runs.
  const prefix = `${sessionToken(dir)}r${recorderSeq++}`;
  let n = 0;

  await page.route('**/*', async (route) => {
    const req = route.request();
    let res;
    try {
      // `maxRedirects: 0` matters: by default `route.fetch` FOLLOWS redirects and returns the final
      // response, so the browser never sees the 302 and Stroom's sign-in chain never completes —
      // the username field simply never appears. Passing the redirect back verbatim lets the browser
      // drive the chain itself, and records each hop so the simulation can reproduce it.
      res = await route.fetch({ maxRedirects: 0 });
    } catch {
      return route.continue().catch(() => {});
    }
    const body = await res.body().catch(() => Buffer.alloc(0));
    const h = res.headers();
    const ct = h['content-type'] || 'application/octet-stream';
    // Keep the headers a redirect or a download depends on; drop the rest (hop-by-hop headers and
    // content-length would fight the replayed body).
    const keep = {};
    for (const k of ['location', 'content-disposition', 'cache-control']) if (h[k]) keep[k] = h[k];

    if (isApi(req.url())) {
      const reqBody = req.postData() || '';
      const key = keyExact(req.method(), req.url(), reqBody);
      const file = `${prefix}b${String(n++).padStart(5, '0')}`;
      write(join(dir, 'api', file), body);
      // The REQUEST body is kept too, so replay can map the identifiers the client mints. A search
      // carries a client-generated query key that no recording can predict; without the recorded
      // request there is nothing to map it FROM. See `rewriteMintedIds`.
      let reqFile;
      if (reqBody) {
        reqFile = `${prefix}q${String(n++).padStart(5, '0')}`;
        write(join(dir, 'api', reqFile), Buffer.from(reqBody, 'utf8'));
      }
      if (!api.has(key)) api.set(key, []);
      api.get(key).push({ status: res.status(), contentType: ct, headers: keep, body: file, req: reqFile });
    } else {
      const p = pathOf(req.url());
      if (!app.has(p)) {
        const file = `${prefix}b${String(n++).padStart(5, '0')}`;
        write(join(dir, 'app', file), body);
        app.set(p, { status: res.status(), contentType: ct, headers: keep, body: file });
      }
    }
    await route.fulfill({ response: res, body }).catch(() => {});
  });

  return {
    counts: () => ({ api: api.size, app: app.size }),
    /** Merge into any corpus already on disk, so recording can be done screen by screen. */
    save() {
      const prevApi = existsSync(API_MANIFEST(dir)) ? JSON.parse(readFileSync(API_MANIFEST(dir), 'utf8')).entries : {};
      const prevApp = existsSync(APP_MANIFEST(dir)) ? JSON.parse(readFileSync(APP_MANIFEST(dir), 'utf8')).entries : {};
      const apiOut = { ...prevApi };
      // `>=`, not `>`: a fresh capture of the same length REPLACES the older one, so re-recording a
      // screen actually refreshes it. A longer recorded sequence still wins, because that is a poll
      // captured to completion and a shorter one would leave the screen spinning.
      for (const [k, v] of api) if (!apiOut[k] || v.length >= apiOut[k].length) apiOut[k] = v;
      const appOut = { ...prevApp, ...Object.fromEntries(app) };
      write(API_MANIFEST(dir), JSON.stringify({ entries: apiOut }, null, 1));
      write(APP_MANIFEST(dir), JSON.stringify({ entries: appOut }, null, 1));
      return { api: Object.keys(apiOut).length, app: Object.keys(appOut).length };
    },
  };
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Map the identifiers a client mints onto the ones in the recording.
 *
 * Stroom's client generates its own query key and sends it with the search; the recorded response
 * carries the key from the recording session. The client then matches responses against the key IT
 * sent, so a naive replay is half-accepted: the searched dashboard replays "1 to 100 of 108"
 * correctly and shows blank cells, because the counts survive the mismatch and the values do not.
 *
 * Walking the recorded and live request bodies together gives the mapping directly — same position,
 * different UUID means "this is the identifier that moved" — and applying it to the response makes
 * the reply answer the question that was actually asked. Only UUID-shaped strings are mapped, so a
 * differing name or timestamp is left alone.
 */
export function mintedIdMap(recordedReq, liveReq) {
  const map = new Map();
  let a;
  let b;
  try {
    a = JSON.parse(recordedReq);
    b = JSON.parse(liveReq);
  } catch {
    return map;
  }
  const walk = (x, y) => {
    if (typeof x === 'string' && typeof y === 'string') {
      if (x !== y && UUID_RE.test(x) && UUID_RE.test(y)) map.set(x, y);
      return;
    }
    if (Array.isArray(x) && Array.isArray(y)) {
      for (let i = 0; i < Math.min(x.length, y.length); i += 1) walk(x[i], y[i]);
      return;
    }
    if (x && y && typeof x === 'object' && typeof y === 'object') {
      for (const k of Object.keys(x)) if (k in y) walk(x[k], y[k]);
    }
  };
  walk(a, b);
  return map;
}

/** Apply a minted-id map to a recorded response body. */
export function rewriteMintedIds(buffer, map) {
  if (!map.size) return buffer;
  let text = buffer.toString('utf8');
  for (const [from, to] of map) text = text.split(from).join(to);
  return Buffer.from(text, 'utf8');
}

/**
 * Resolve a request against a corpus. Falls back exact -> ignore-body -> ignore-query, and reports
 * which tier answered so a run can tell "served from the recording" from "guessed at it".
 */
export function makeResolver(corpus) {
  const byExact = new Map(Object.entries(corpus.api.entries));
  const byNoBody = new Map();
  const byPath = new Map();
  // The relaxed indexes keep the LONGEST sequence recorded for a URL, not the first one seen.
  //
  // The same endpoint is hit by several targets: opening a dashboard polls it once and gets an empty
  // result, while running its search polls it repeatedly until the rows arrive. Those are different
  // exact keys (the search POST carries a client-generated query key), so the relaxed index has to
  // choose — and choosing the first meant the searched dashboard replayed the UNSEARCHED capture:
  // right row counts, blank cells. The longest sequence is the one that ran to completion.
  for (const [k, v] of byExact) {
    const noBody = k.replace(/ #[0-9a-f]+$/, '');
    if (!byNoBody.has(noBody) || v.length > byNoBody.get(noBody).length) byNoBody.set(noBody, v);
    const p = noBody.split('?')[0];
    if (!byPath.has(p) || v.length > byPath.get(p).length) byPath.set(p, v);
  }
  const cursor = new Map();
  const stats = { exact: 0, noBody: 0, path: 0, miss: 0, rewritten: 0 };
  const misses = [];

  return {
    stats,
    misses,
    app: (p) => corpus.app.entries[p],
    resolve(method, url, body) {
      const kE = keyExact(method, url, body || '');
      let seq = byExact.get(kE);
      let tier = 'exact';
      // The cursor must be keyed by the key that MATCHED, not by the exact key. A dashboard search
      // POST carries a client-generated query key, so its body differs every run: the exact lookup
      // misses, the relaxed tier answers, and a cursor keyed on the exact key is looked up under a
      // name that is never seen twice — so index 0 is returned forever. For a POLLED endpoint that
      // is the first, empty response, and the screen never fills. It cost a 6,726px "regression" on
      // the searched dashboard that was entirely this.
      let cursorKey = kE;
      if (!seq) { cursorKey = keyNoBody(method, url); seq = byNoBody.get(cursorKey); tier = 'noBody'; }
      if (!seq) { cursorKey = keyPath(method, url); seq = byPath.get(cursorKey); tier = 'path'; }
      if (!seq) {
        stats.miss += 1;
        if (misses.length < 200) misses.push(`${method} ${pathOf(url)}`);
        return null;
      }
      stats[tier] += 1;
      // An EXACT repeat is the client asking the identical question again, so give it the next
      // answer in order. A RELAXED match means we are approximating, and walking from the start is
      // actively wrong for a poll: the simulation answers instantly where the recording waited on
      // real work, so the client consumes the early "nothing yet" responses and stops before it
      // reaches the data. That is what left the searched dashboard with correct row counts and blank
      // cells — the result-store poll replayed its first 46-entry response instead of its last.
      // For an approximate match, serve the settled answer.
      const i = tier === 'exact' ? Math.min(cursor.get(cursorKey) ?? 0, seq.length - 1) : seq.length - 1;
      cursor.set(cursorKey, i + 1);
      const hit = seq[i];
      let buffer = readFileSync(join(corpus.dir, 'api', hit.body));
      if (hit.req && body) {
        const map = mintedIdMap(readFileSync(join(corpus.dir, 'api', hit.req), 'utf8'), body);
        if (map.size) {
          stats.rewritten += 1;
          buffer = rewriteMintedIds(buffer, map);
        }
      }
      return { ...hit, buffer };
    },
  };
}

/**
 * Pin the browser clock.
 *
 * Identical bytes still render differently when a cell says "5 days ago" — Stroom's grids use
 * `formatWithDuration` widely — so a corpus is only reproducible with `now` fixed to the same
 * instant on every run.
 */
export async function freezeClock(page, fixedMs) {
  // `setFixedTime`, NOT `install`: installing a fake clock also stops timers advancing, and the GWT
  // bootstrap is driven by them — the app never finishes loading and even the sign-in field never
  // appears. This pins what `Date.now()` and `new Date()` return while leaving setTimeout/interval
  // running normally, which is the whole requirement: stable relative-time rendering, live app.
  await page.clock.setFixedTime(new Date(fixedMs));
}
