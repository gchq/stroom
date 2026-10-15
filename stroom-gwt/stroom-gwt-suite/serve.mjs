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
// The simulated backend.
//
// Puts a recorded corpus back on an HTTP origin: the compiled GWT UI on `/`, and every REST call it
// makes answered from the recording. Nothing else is running — no Stroom, no database, no cluster.
//
//   node stroom-gwt/stroom-gwt-suite/serve.mjs                       # corpus/default on :9099
//   CORPUS=stroom-gwt-suite/corpus/default PORT=9099 node stroom-gwt/stroom-gwt-suite/serve.mjs
//
// Requests the corpus cannot answer return 404 with a JSON body and are counted; `--strict` makes
// the process exit non-zero if any occurred, which is what turns "it rendered" into a test.
//
// A miss is not automatically wrong. The recording captured one session's worth of traffic, and a
// screen reached by a different route may ask a question that session never asked. The miss LIST is
// the useful artefact: it says exactly which exchanges the corpus is short of, so the next recording
// pass can cover them.
import { createServer } from 'node:http';
import { loadCorpus, makeResolver, isApi } from './lib/corpus.mjs';
import { fromSuite } from './lib/paths.mjs';

// The suite's folder, so its corpus and output are found wherever the suite is run from
const SUITE = import.meta.dirname;

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const CORPUS = fromSuite(env('CORPUS', `${SUITE}/corpus/default`));
const PORT = Number(env('PORT', '9099'));

const corpus = loadCorpus(CORPUS);
if (!corpus) {
  console.error(`no corpus at ${CORPUS} — run: node stroom-gwt/stroom-gwt-suite/record.mjs`);
  process.exit(2);
}
const resolver = makeResolver(corpus);

/** Read a request body (needed to key POSTs, which is most of Stroom's API). */
const readBody = (req) =>
  new Promise((resolve) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', () => resolve(''));
  });

export function createSimServer(res_corpus = corpus, res_resolver = resolver) {
  return createServer(async (req, res) => {
    const url = `http://localhost${req.url}`;
    if (isApi(url)) {
      const body = await readBody(req);
      const hit = res_resolver.resolve(req.method, url, body);
      if (!hit) {
        res.writeHead(404, { 'content-type': 'application/json' });
        return res.end('{"simulated":"no recorded response"}');
      }
      res.writeHead(hit.status, { 'content-type': hit.contentType, ...(hit.headers || {}) });
      return res.end(hit.buffer);
    }
    // The app itself. `/` is the bootstrap page; everything else is looked up by path, then by path
    // without its query (GWT cache-busts some assets with one).
    const path = req.url === '/' ? '/' : req.url;
    const entry = res_resolver.app(path) || res_resolver.app(path.split('?')[0]);
    if (!entry) {
      res.writeHead(404, { 'content-type': 'text/plain' });
      return res.end('not recorded');
    }
    const { readFileSync } = await import('node:fs');
    const { join } = await import('node:path');
    res.writeHead(entry.status, { 'content-type': entry.contentType, ...(entry.headers || {}) });
    res.end(readFileSync(join(res_corpus.dir, 'app', entry.body)));
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const server = createSimServer();
  server.listen(PORT, () => {
    console.log(`simulated backend on http://localhost:${PORT}`);
    console.log(`  corpus     ${CORPUS}`);
    console.log(`  api        ${Object.keys(corpus.api.entries).length} recorded exchanges`);
    console.log(`  app        ${Object.keys(corpus.app.entries).length} assets`);
  });
  const report = () => {
    const { exact, noBody, path, miss } = resolver.stats;
    console.log(`\nserved exact=${exact} relaxed=${noBody + path} miss=${miss}`);
    for (const m of [...new Set(resolver.misses)].slice(0, 40)) console.log(`  miss ${m}`);
    process.exit(process.argv.includes('--strict') && miss > 0 ? 1 : 0);
  };
  process.on('SIGINT', report);
  process.on('SIGTERM', report);
}
