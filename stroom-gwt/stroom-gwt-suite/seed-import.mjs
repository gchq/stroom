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
// Seed the four document types Stroom CANNOT CREATE, by importing them instead.
//
//   URL=http://localhost:8080 USER=admin PASS=… node stroom-gwt/stroom-gwt-suite/seed-import.mjs
//   ... KEEP=1 …                    # leave the built zip in stroom-gwt-suite/out/seed for inspection
//
// SUPERSEDED, and kept only for an instance that does not have the fix.
//
// The defect below is FIXED (see stroom-gwt/ISSUES.md #31) and verified on 2026-08-16: all six
// affected types now create normally, so `seed.mjs` alone will seed a fresh instance. Use this only
// against a server predating that fix.
//
// `View`, `Analytic Rule`, `Report` and `Data Generator` could not be created through the UI at all:
// `POST /api/explorer/v2/create` returned 403
//
//     Expecting a stroom user identity (i.e. HasUserRef), but got InternalIdpProcessingUserIdentity
//
// Each of those four stores does a read-then-write inside `securityContext.asProcessingUser(...)`
// straight after creating the document (ViewStoreImpl:55, AnalyticRuleStoreImpl:94,
// ReportStoreImpl:86, DataGenStoreImpl:65), and the write stamps the audit user from
// `securityContext.getUserRef()` (StoreImpl:710) — which the internal-IdP processing identity does
// not have. See stroom-gwt/ISSUES.md.
//
// Import does not go through those stores' `createDocument`, so it works. That also means this file
// becomes unnecessary the day the server bug is fixed: `seed.mjs` will simply create all ten.
//
// The documents are authored here as Stroom's own export format — `<Name>.<Type>.<uuid>.meta` plus a
// `.node` giving its place in the tree — copied from stroom-core's sample content. UUIDs are fixed
// rather than generated, so a second run re-imports the SAME documents (an update) instead of
// creating duplicates.
import { mkdir, rm, writeFile } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { join, resolve } from 'node:path';
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { clickMenuItem, closeDialogs, dismissMenus } from './compare/lib/structure.mjs';
import { settleExplorer } from './compare/lib/settle.mjs';

// The suite's folder, so its corpus and output are found wherever the suite is run from
const SUITE = import.meta.dirname;

const run = promisify(execFile);
const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const URL = env('URL', 'http://localhost:8080');
const FOLDER = env('FOLDER', 'Seed Content');
const STAGE = `${SUITE}/out/seed/pack`;
const ZIP = resolve(`${SUITE}/out/seed/seed-content.zip`);

/**
 * Documents that exist in this instance and are safe to point at.
 *
 * A View with no data source, or a Data Generator with no feed, renders the same empty editor
 * whatever the code does — the point of seeding is that the editors have something to draw. These
 * UUIDs are read out of the recorded corpus rather than guessed.
 */
const REF = {
  index: { type: 'Index', uuid: '57a35b9a-083c-4a93-a813-fc3ddfe1ff44', name: 'Example Index' },
  pipeline: { type: 'Pipeline', uuid: 'c9c879ee-d88a-40ff-8654-d018f1c2d957', name: 'Example Index' },
  feed: { type: 'Feed', uuid: '1c09667a-fe2a-4758-80cd-1b9342b1475a', name: 'XML_FILTERING_ANALYTIC' },
};

// `limit` goes BEFORE `select` in StroomQL; after it, the query errors with `Unexpected token LIMIT
// after SELECT`, which is what every walk of these editors' result tables had been walking over
// (found by B0's alert reading, 2026-09-22). And no time bound: the instance's data is months old,
// and a result table with ROWS is what the behaviour checkers need.
const QUERY = `from "Example Index"
limit 100
select StreamId, EventTime, UserId, Description`;

/** name, type, uuid, and the type-specific body of the .meta. Fixed UUIDs — see the header. */
const DOCS = [
  {
    name: 'Seed View',
    type: 'View',
    uuid: '5eed0001-0000-4000-8000-000000000001',
    body: {
      description: 'Seeded View over Example Index, for the GWT regression suite.',
      dataSource: REF.index,
      pipeline: REF.pipeline,
      filter: {
        type: 'operator',
        op: 'AND',
        children: [{ type: 'term', field: 'UserId', condition: 'CONTAINS', value: 'user', enabled: true }],
        enabled: true,
      },
    },
  },
  {
    name: 'Seed Analytic Rule',
    type: 'AnalyticRule',
    uuid: '5eed0002-0000-4000-8000-000000000002',
    body: {
      description: 'Seeded Analytic Rule, for the GWT regression suite.',
      languageVersion: 'STROOM_QL_VERSION_0_1',
      query: QUERY,
      analyticProcessType: 'SCHEDULED_QUERY',
      errorFeed: REF.feed,
      includeRuleDocumentation: true,
      rememberNotifications: false,
      suppressDuplicateNotifications: false,
      level: 'MEDIUM',
      // Must be one of AnalyticRuleStatus — EXPERIMENTAL / TESTING / STABLE / DEPRECATED. This said
      // `ENABLED`, which was legal only while the field was a free-text String, and #5774 narrowed
      // it to an enum with no tolerant reader: the seeded rule then answered 500 on load and its
      // editor could not be opened at all. See stroom-gwt/ISSUES.md #35.
      status: 'EXPERIMENTAL',
      timeRange: { name: 'Last day', condition: 'BETWEEN', from: 'now()-1d', to: 'now()' },
    },
  },
  {
    name: 'Seed Report',
    type: 'Report',
    uuid: '5eed0003-0000-4000-8000-000000000003',
    body: {
      description: 'Seeded Report, for the GWT regression suite.',
      languageVersion: 'STROOM_QL_VERSION_0_1',
      query: QUERY,
      errorFeed: REF.feed,
      timeRange: { name: 'Last day', condition: 'BETWEEN', from: 'now()-1d', to: 'now()' },
    },
  },
  {
    name: 'Seed Data Generator',
    type: 'DataGen',
    uuid: '5eed0004-0000-4000-8000-000000000004',
    body: {
      description: 'Seeded Data Generator, for the GWT regression suite.',
      feed: REF.feed,
      template: [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<Events xmlns="event-logging:3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" Version="3.2.3">',
        '  <Event>',
        '    <EventTime><TimeCreated>${now}</TimeCreated></EventTime>',
        '    <EventSource><User><Id>${random(1,100)}</Id></User></EventSource>',
        '    <EventDetail><TypeId>SEED</TypeId><Description>Seeded test event</Description></EventDetail>',
        '  </Event>',
        '</Events>',
      ].join('\n'),
    },
  },
];

// ── Build the zip ────────────────────────────────────────────────────────────
await rm(STAGE, { recursive: true, force: true });
await mkdir(join(STAGE, FOLDER.replace(/\s+/g, '_')), { recursive: true });
const path = FOLDER.replace(/\s+/g, '_');
for (const d of DOCS) {
  const stem = `${d.name.replace(/\s+/g, '_')}.${d.type}.${d.uuid}`;
  const meta = { type: d.type, uuid: d.uuid, name: d.name, version: d.uuid, ...d.body };
  await writeFile(join(STAGE, path, `${stem}.meta`), JSON.stringify(meta, null, 2));
  await writeFile(join(STAGE, path, `${stem}.node`), `name=${d.name}\npath=${FOLDER}\ntype=${d.type}\nuuid=${d.uuid}\n`);
}
await rm(ZIP, { force: true });
await run('zip', ['-r', '-q', ZIP, path], { cwd: STAGE });
console.log(`built ${ZIP} with ${DOCS.length} documents`);

// ── Import it ────────────────────────────────────────────────────────────────
const browser = await chromium.launch({ headless: env('HEADLESS', '1') !== '0' });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
gwt.baseUrl = URL;
await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
await gwt.signInFully(page, credentials());
await closeDialogs(page);
await settleExplorer(page, { label: 'reload' }).catch(() => {});

await gwt.openMainMenu(page);
await page.waitForTimeout(500);
if (!(await clickMenuItem(page, 'Tools'))) throw new Error('no Tools menu');
await page.waitForTimeout(600);
if (!(await clickMenuItem(page, 'Import'))) throw new Error('no Import item');
const d = page.locator('.resizableDialog-popup, .dialog-popup').last();
await d.waitFor({ state: 'visible', timeout: 20_000 });
await page.waitForTimeout(1000);

// The file control is a hidden <input type=file> behind a styled button; set it directly.
await page.locator('input[type=file]').first().setInputFiles(ZIP);
await page.waitForTimeout(2500);
await page.screenshot({ path: `${SUITE}/out/seed/import-step1.png` }).catch(() => {});

// Step one uploads the file; step two is "Confirm Import", a grid of what the zip contains.
await d.getByText('OK', { exact: true }).first().click();
const confirm = page.locator('.resizableDialog-popup, .dialog-popup').last();
await confirm.getByText('Confirm Import', { exact: true }).first().waitFor({ state: 'visible', timeout: 30_000 });
await page.waitForTimeout(1500);

// Every row arrives UNTICKED, and OK on an all-unticked grid imports nothing while reporting
// success — which is what the first run of this script did, silently.
//
// The tick is a `div.tickBox.tickBox-untick`, not a checkbox, so it is clicked as an element and its
// state is read from the class. Clicking the header's select-all turns the HEADER tick blue and
// leaves the rows untouched, so each row is ticked individually and the result is asserted.
const rows = confirm.locator('.dataGridWidget tbody tr');
const n = await rows.count();
for (let i = 0; i < n; i += 1) {
  await rows.nth(i).locator('.tickBox').first().click().catch(() => {});
  await page.waitForTimeout(200);
}
const ticked = await confirm.locator('.dataGridWidget tbody .tickBox-tick').count();
console.log(`confirm grid: ${ticked}/${n} rows ticked`);
if (ticked < n) throw new Error(`only ${ticked} of ${n} import rows ticked — OK would import nothing`);
await page.screenshot({ path: `${SUITE}/out/seed/import-ticked.png` }).catch(() => {});
await confirm.getByText('OK', { exact: true }).first().click();
await page.waitForTimeout(6000);
await page.screenshot({ path: `${SUITE}/out/seed/import-done.png` }).catch(() => {});

// ── Did they land? ───────────────────────────────────────────────────────────
await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
await gwt.waitForApp(page);
await closeDialogs(page);
await dismissMenus(page).catch(() => {});
await settleExplorer(page, { label: 'reload' }).catch(() => {});
await page.locator('button[title="Expand All"]').first().click({ timeout: 10_000 }).catch(() => {});
await settleExplorer(page, { label: 'expand' }).catch(() => {});
const tree = await page.evaluate(() => {
  const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
  return [...document.querySelectorAll('.explorerCell')].map((c) => ({
    type: norm(c.querySelector('.explorerCell-icon')?.getAttribute('title')),
    label: norm(c.textContent),
  }));
});
console.log('');
for (const doc of DOCS) {
  const hit = tree.find((t) => t.label === doc.name);
  console.log(`  ${hit ? '✓' : '✗'} ${doc.name}${hit ? ` (${hit.type})` : ' — not in the explorer'}`);
}
if (env('KEEP') !== '1') await rm(STAGE, { recursive: true, force: true });
await browser.close();
