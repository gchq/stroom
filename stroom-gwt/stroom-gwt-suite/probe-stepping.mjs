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
// What stepping mode does now that `POST /stepping/v1/step` is allowed: which stream the meta list
// auto-selects, what `Refresh Current Step` answers, and whether a selected element gets its panes.
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { attachReadOnlyGuard } from './compare/lib/readonly-guard.mjs';
import { closeDialogs } from './compare/lib/structure.mjs';
import { clickEditorTab } from './lib/tabs.mjs';
const BASE = 'http://localhost:8080';
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
const { violations } = attachReadOnlyGuard(page, { enabled: true });
const api = [];
page.on('response', async (r) => { if (/\/api\/stepping\//.test(r.url())) { let body = ''; try { body = (await r.text()).slice(0, 600); } catch { /* ignore */ } api.push({ url: r.url().replace(BASE, ''), status: r.status(), body }); } });
gwt.baseUrl = BASE;
await page.goto(BASE, { waitUntil: 'domcontentloaded', timeout: 60000 });
await gwt.signInFully(page, credentials());
await gwt.assertSignedIn(page);
await closeDialogs(page);
await page.locator('button[title="Expand All"]').first().click({ timeout: 10000 });
await page.waitForTimeout(3000);
const firstOf = (type) => page.evaluate((t) => { const c = [...document.querySelectorAll('.explorerCell')].find((e) => e.offsetWidth > 0 && e.querySelector('.explorerCell-icon')?.getAttribute('title') === t); return c?.textContent.trim(); }, type);
const buttons = () => page.evaluate(() => [...document.querySelectorAll('button, .Button, [role=button]')].filter((b) => b.offsetWidth > 0).map((b) => (b.getAttribute('title') || b.textContent.trim()).slice(0, 30) + (b.disabled ? '(d)' : '')));
const dialogs = () => page.evaluate(() => [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0).map((e) => ({ caption: e.querySelector('.dialog-titleText')?.textContent.trim(), text: e.textContent.replace(/\s+/g, ' ').trim().slice(0, 300) })));
const name = await firstOf('Pipeline');
console.log('pipeline:', name);
await gwt.openDocByName(page, name, 'Pipeline');
await page.waitForTimeout(2500);
console.log('structure tab:', await clickEditorTab(page, 'Structure', { nth: 0 }));
await page.waitForTimeout(1500);
await page.locator('button[title="Enter Stepping Mode"]').first().click();
await page.waitForTimeout(6000);
console.log('after enter — dialogs:', JSON.stringify(await dialogs()));
console.log('meta rows:', await page.evaluate(() => [...document.querySelectorAll('.dataGridRow')].filter((e) => e.offsetWidth > 0).map((e) => e.textContent.replace(/\s+/g, ' ').trim().slice(0, 90)).slice(0, 5)));
console.log('location label:', await page.evaluate(() => [...document.querySelectorAll('.stepLocationLink')].map((e) => e.textContent)));
console.log('step message:', await page.evaluate(() => [...document.querySelectorAll('[class*=stepping], [class*=Stepping]')].filter((e) => e.offsetWidth > 0).map((e) => e.className + ': ' + e.textContent.replace(/\s+/g, ' ').trim().slice(0, 80)).slice(0, 8)));
console.log('api so far:', JSON.stringify(api, null, 1));
await closeDialogs(page);
// how long does the meta list take, and what does selecting a stream do?
const t0 = Date.now();
let rows = [];
while (Date.now() - t0 < 30000) { rows = await page.evaluate(() => [...document.querySelectorAll('.dataGridWidget tbody tr')].filter((e) => e.offsetHeight > 0 && e.textContent.trim()).map((e) => e.textContent.replace(/\s+/g, ' ').trim().slice(0, 90))); if (rows.length) break; await page.waitForTimeout(500); }
console.log('meta rows after', Date.now() - t0, 'ms:', rows.length, rows.slice(0, 3));
api.length = 0;
const cell = await page.evaluate(() => { const row = [...document.querySelectorAll('.dataGridWidget tbody tr')].find((r) => r.offsetHeight > 0 && /\bEvents\b/.test(r.textContent) && !/Error|Raw/.test(r.textContent)); console.log('picked', row.textContent.slice(0, 80)); const td = [...row.querySelectorAll('td')].find((c) => c.offsetWidth > 20 && c.textContent.trim()); const r = td.getBoundingClientRect(); return { x: r.x + Math.min(r.width / 2, 60), y: r.y + r.height / 2 }; });
await page.mouse.click(cell.x, cell.y);
await page.waitForTimeout(8000);
console.log('after select stream — dialogs:', JSON.stringify(await dialogs()));
console.log('location label:', await page.evaluate(() => [...document.querySelectorAll('.stepLocationLink')].map((e) => e.textContent)));
console.log('api:', JSON.stringify(api.map((a) => ({ url: a.url, status: a.status, body: a.body.slice(0, 300) })), null, 1));
console.log('buttons:', (await buttons()).filter((b) => /step|terminate/i.test(b)).join(' | '));
await page.locator('button[title="Step Forward"]').first().click().catch((e) => console.log('forward failed:', e.message.split('\n')[0]));
await page.waitForTimeout(5000);
console.log('panes after forward:', await page.evaluate(() => [...document.querySelectorAll('.dashboard-panel')].filter((e) => e.offsetWidth > 0).map((e) => e.offsetWidth + 'x' + e.offsetHeight + ' ' + e.textContent.replace(/\s+/g, ' ').trim().slice(0, 60))));
console.log('after forward — location:', await page.evaluate(() => [...document.querySelectorAll('.stepLocationLink')].map((e) => e.textContent)), 'dialogs:', JSON.stringify(await dialogs()));
api.length = 0;
await page.locator('button[title="Refresh Current Step"]').first().click();
await page.waitForTimeout(6000);
console.log('after refresh — dialogs:', JSON.stringify(await dialogs()));
console.log('api:', JSON.stringify(api, null, 1));
await closeDialogs(page);
// select the xslt element in the stepping tree
const box = await page.evaluate(() => { const boxes = [...document.querySelectorAll('.pipelineElementBox-label')].filter((e) => e.offsetWidth > 0); const el = boxes.find((e) => /xslt/i.test(e.textContent)); if (!el) return { n: boxes.length, labels: boxes.map((b) => b.textContent) }; const r = el.getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2, labels: boxes.map((b) => b.textContent) }; });
console.log('tree:', JSON.stringify(box));
if (box.x) { await page.mouse.move(box.x, box.y); await page.mouse.down(); await page.mouse.up(); await page.waitForTimeout(2500); }
console.log('after select — dialogs:', JSON.stringify(await dialogs()));
console.log('panes:', await page.evaluate(() => [...document.querySelectorAll('.dashboard-panel')].filter((e) => e.offsetWidth > 0).map((e) => e.className + ' ' + e.offsetWidth + 'x' + e.offsetHeight + ' ace=' + !!e.querySelector('.ace_editor'))));
console.log('buttons:', (await buttons()).join(' | '));
console.log('violations:', JSON.stringify(violations));
await browser.close();
