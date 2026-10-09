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
// Why did `New Field` › OK on the Lucene index's Fields tab register as `nothing` — neither closed
// nor alerted? Open it, read the form, press OK, and read what is there afterwards.
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { attachReadOnlyGuard } from './compare/lib/readonly-guard.mjs';
import { closeDialogs } from './compare/lib/structure.mjs';
import { clickEditorTab } from './lib/tabs.mjs';
const BASE = 'http://localhost:8080';
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
attachReadOnlyGuard(page, { enabled: true });
gwt.baseUrl = BASE;
await page.goto(BASE, { waitUntil: 'domcontentloaded', timeout: 60000 });
await gwt.signInFully(page, credentials());
await closeDialogs(page);
await page.locator('button[title="Expand All"]').first().click({ timeout: 10000 });
await page.waitForTimeout(2000);
const first = await page.evaluate(() => [...document.querySelectorAll('.explorerCell')].find((e) => e.querySelector('.explorerCell-icon')?.getAttribute('title') === 'Lucene Index')?.textContent.trim());
console.log('index:', first);
await gwt.openDocByName(page, first, 'Lucene Index');
await page.waitForTimeout(2500);
await clickEditorTab(page, 'Fields', { nth: 0 });
await page.waitForTimeout(1500);
const dump = (label) => page.evaluate((label) => {
  const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
  const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
  const top = popups[popups.length - 1];
  const rows = [...document.querySelectorAll('.dataGridWidget tbody tr')].filter((r) => r.offsetHeight > 0 && norm(r.textContent)).map((r) => norm(r.textContent).slice(0, 60));
  return { label, dialogs: popups.map((p) => norm(p.querySelector('.dialog-titleText')?.textContent)),
    inputs: top ? [...top.querySelectorAll('input, select, textarea')].filter((i) => i.offsetWidth > 0).map((i) => `${i.tagName}.${i.className.split(' ')[0]}=${JSON.stringify(i.value)}`) : [],
    buttons: top ? [...top.querySelectorAll('button, .Button')].filter((b) => b.offsetWidth > 0).map((b) => norm(b.textContent || b.title) + (b.disabled ? '(d)' : '')) : [], rows: rows.length, first: rows[0] };
}, label);
console.log(JSON.stringify(await dump('at rest')));
await page.locator('button[title="New Field"]').first().click();
await page.waitForTimeout(1200);
console.log(JSON.stringify(await dump('dialog open')));
const okBox = await page.evaluate(() => { const p = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0).pop(); const b = [...p.querySelectorAll('button, .Button')].find((x) => /^\s*OK\s*(OK)?\s*$/.test(x.textContent)); const r = b.getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2, cls: b.className, tag: b.tagName }; });
console.log('OK button', JSON.stringify(okBox));
await page.mouse.click(okBox.x, okBox.y);
await page.waitForTimeout(1500);
console.log(JSON.stringify(await dump('after OK')));
await browser.close();
