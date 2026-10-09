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
/**
 * #35 claims a document whose load returns 500 "presents identically to an editor that is broken":
 * no dialog, no message, the tab simply does not appear. Re-reading the source casts doubt on that
 * — `DocumentPlugin.showDocument:253` builds a RestErrorHandler that fires `AlertEvent.fireError`,
 * and `AnalyticsPlugin.load:80` wires it with `.onFailure(errorHandler)`. So either the claim is
 * stale or the alert never reaches the user.
 *
 * Settled WITHOUT writing anything: the failure is injected by intercepting the fetch and answering
 * 500 with the body the server gave. The client cannot tell that apart from the real thing, so the
 * reproduction needs no rule with an illegal status — which could not be made through the API in any
 * case, because the same enum rejects `ENABLED` on the way IN.
 *
 *   DOCTYPE=AnalyticRule node probe-loadfail.mjs
 */
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { attachReadOnlyGuard } from './compare/lib/readonly-guard.mjs';
import { settleExplorer } from './compare/lib/settle.mjs';
import { readDialogs } from './compare/lib/structure.mjs';

const env = (k, d) => process.env[k] ?? d;
const URL = env('URL', 'http://localhost:8080');
const DOCTYPE = env('DOCTYPE', 'AnalyticRule');
/** The path whose fetch gets the injected 500. */
const ENDPOINT = env('ENDPOINT', '/api/analyticRule/v1/');

/** Verbatim from the real 500 recorded in gwt-bugs.md #35. */
const JACKSON = 'Cannot deserialize value of type `stroom.analytics.shared.AnalyticRuleStatus`'
  + ' from String "ENABLED": not one of the values accepted for Enum class:'
  + ' [TESTING, STABLE, EXPERIMENTAL, DEPRECATED]';

const browser = await chromium.launch({ headless: env('HEADLESS', '1') !== '0' });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
attachReadOnlyGuard(page, { enabled: true });
gwt.baseUrl = URL;
await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
await gwt.signInFully(page, credentials());

let injected = 0;
await page.route((u) => u.pathname.startsWith(ENDPOINT), async (route) => {
  injected += 1;
  console.log(`  injected 500 for ${new globalThis.URL(route.request().url()).pathname}`);
  await route.fulfill({ status: 500, contentType: 'application/json',
    body: JSON.stringify({ code: 500, message: JACKSON, details: JACKSON }) });
});

await page.locator('button[title="Expand All"]').first().click({ timeout: 10_000 }).catch(() => {});
await settleExplorer(page, { label: 'expand', budget: 8_000 }).catch(() => {});

const name = await page.evaluate((t) => {
  const norm = (s) => String(s ?? '').replace(/\s+/g, ' ').trim();
  const squash = (s) => s.toLowerCase().replace(/[^a-z0-9]/g, '');
  const rows = [...document.querySelectorAll('.explorerCell')].filter((e) => e.offsetWidth > 0)
    .map((e) => ({ title: norm(e.querySelector('.explorerCell-icon')?.getAttribute('title')), label: norm(e.textContent) }))
    .filter((r) => r.title);
  for (const test of [(x) => x === t, (x) => squash(x) === squash(t), (x) => squash(x).startsWith(squash(t))]) {
    const hit = rows.find((r) => test(r.title));
    if (hit) return hit.label;
  }
  return null;
}, DOCTYPE);
if (!name) { console.error(`no ${DOCTYPE} in the explorer`); await browser.close(); process.exit(2); }
console.log(`\n  opening "${name}" (${DOCTYPE}) with its fetch answering 500\n`);

const tabsBefore = await page.evaluate(() => [...document.querySelectorAll('.curveTab')]
  .filter((e) => e.offsetWidth > 0).map((e) => e.textContent.replace(/\s+/g, ' ').trim()));

await gwt.openDocByName(page, name, DOCTYPE).catch((e) => console.log(`  (open threw: ${e.message.split('\n')[0]})`));
await page.waitForTimeout(4_000);

const dialogs = await readDialogs(page).catch(() => []);
const tabsAfter = await page.evaluate(() => [...document.querySelectorAll('.curveTab')]
  .filter((e) => e.offsetWidth > 0).map((e) => e.textContent.replace(/\s+/g, ' ').trim()));
const bodyText = await page.evaluate(() => {
  const pops = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
  return pops.map((p) => p.textContent.replace(/\s+/g, ' ').trim()).join(' || ');
});

console.log(`  injected responses : ${injected}`);
console.log(`  dialogs up         : ${dialogs.length ? dialogs.map((d) => d.caption).join(' | ') : '(none)'}`);
console.log(`  dialog text        : ${bodyText ? bodyText.slice(0, 400) : '(none)'}`);
console.log(`  tabs before        : ${tabsBefore.join(' | ') || '(none)'}`);
console.log(`  tabs after         : ${tabsAfter.join(' | ') || '(none)'}`);

const told = /unable to load|cannot deserialize|error/i.test(bodyText);
const openedTab = tabsAfter.length > tabsBefore.length;
console.log(`\n  VERDICT: ${told
  ? 'the user IS told — an alert names the failure'
  : 'the user is told NOTHING — silent failure, as #35 claims'}`);
console.log(`           a tab ${openedTab ? 'DID' : 'did NOT'} open`);
if (told) console.log('           so #35\'s client-side half needs correcting');
await browser.close();
