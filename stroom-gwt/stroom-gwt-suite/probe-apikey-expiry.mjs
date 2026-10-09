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
// Does `Add new API Key` survive an EMPTY expiry date?
//
// EditApiKeyPresenter.handlePreCreateModeHide:259 reads
//     final long expireTimeEpochMs = getView().getExpiresOnMs();
// and `getExpiresOnMs()` is `MyDateBox.getMilliseconds()` -> `ClientDateUtil.fromISOString(text)`,
// which returns **null** for a blank or unparsable string (ClientDateUtil:120-132). Unboxing that
// null into a `long` is the same shape as gwt-bugs #38. Clear the box, press OK, read what the user
// is shown.
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { readTopDialogBody } from './lib/alerts.mjs';
import { clickMenuItem } from './compare/lib/structure.mjs';

const URL = process.env.URL || 'http://localhost:8080';
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
const errors = [];
page.on('pageerror', (e) => errors.push(String(e.message ?? e).slice(0, 300)));
await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
await gwt.signInFully(page, credentials());

const click = async (name) => {
  if (await clickMenuItem(page, name)) { await page.waitForTimeout(700); return; }
  const el = page.locator(`button[title="${name}"], button:has-text("${name}"), [role=button]:has-text("${name}")`).first();
  await el.click({ timeout: 10_000 });
  await page.waitForTimeout(900);
};
// Security › Manage API Keys, by the main menu.
await click('Main Menu');
await click('Security');
await click('Manage API Keys');
await page.waitForTimeout(1500);
await click('Add new API Key');
await page.waitForTimeout(1200);

const before = await page.evaluate(() => {
  const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
  const form = popups[popups.length - 1];
  return [...(form?.querySelectorAll('input[type=text], input:not([type])') ?? [])]
    .filter((e) => e.offsetWidth > 0).map((e) => ({ value: e.value, cls: e.className }));
});
console.log('boxes as opened:', JSON.stringify(before, null, 1));

// The expiry box is the one holding an ISO date. Clear it, and give the name a value so the
// expiry rule is the one under test.
const box = await page.evaluate(() => {
  const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
  const form = popups[popups.length - 1];
  const el = [...(form?.querySelectorAll('input[type=text], input:not([type])') ?? [])]
    .find((e) => e.offsetWidth > 0 && /^\d{4}-\d{2}-\d{2}T/.test(String(e.value ?? '')));
  if (!el) return null;
  const r = el.getBoundingClientRect();
  return { x: r.x + r.width / 2, y: r.y + r.height / 2, was: el.value };
});
if (!box) { console.log('no ISO-dated box found — the dialog may have changed'); await browser.close(); process.exit(1); }
console.log('expiry box held:', box.was);

const name = await page.evaluate(() => {
  const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
  const form = popups[popups.length - 1];
  const el = [...(form?.querySelectorAll('input[type=text], input:not([type])') ?? [])]
    .find((e) => e.offsetWidth > 0 && !String(e.value ?? '').trim());
  if (!el) return null;
  const r = el.getBoundingClientRect();
  return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
});
if (name) { await page.mouse.click(name.x, name.y); await page.keyboard.type('Probe key', { delay: 20 }); }

await page.mouse.click(box.x, box.y);
await page.keyboard.press('Control+A');
await page.keyboard.press('Delete');
await page.keyboard.press('Tab');           // blur: does the box put the date back?
await page.waitForTimeout(600);
const afterBlur = await page.evaluate(() => {
  const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
  const form = popups[popups.length - 1];
  return [...(form?.querySelectorAll('input[type=text], input:not([type])') ?? [])]
    .filter((e) => e.offsetWidth > 0).map((e) => e.value);
});
console.log('boxes after clearing the expiry and blurring:', JSON.stringify(afterBlur));

const dialogs = () => page.evaluate(() => [...document.querySelectorAll('.dialog-titleText')]
  .filter((e) => e.offsetWidth > 0).map((e) => e.textContent.trim()));
console.log('dialogs before OK:', JSON.stringify(await dialogs()));
// The TOPMOST dialog's OK, and its state — a disabled OK is #40's shape.
const okState = await page.evaluate(() => {
  const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
  const top = popups[popups.length - 1];
  const btns = [...top.querySelectorAll('button, [role=button], .Button')].filter((b) => b.offsetWidth > 0);
  // A GWT button's text is DOUBLED ("OK\nOK") — walk.mjs undoubles it before matching.
  const undouble = (t) => { const s = String(t ?? '').replace(/\s+/g, ' ').trim(); const h = s.slice(0, s.length / 2); return s.length % 2 === 0 && h === s.slice(s.length / 2) ? h.trim() : s; };
  // "OK\nOK" normalises to "OK OK", whose halves are not equal once a space is in the middle —
  // so match the repeated form directly.
  const ok = btns.find((b) => /^(ok)( ok)*$/i.test(undouble(b.textContent || b.getAttribute('title') || '')));
  if (!ok) return { buttons: btns.map((b) => (b.textContent || b.title || '').trim()) };
  const r = ok.getBoundingClientRect();
  return { disabled: ok.disabled || ok.classList.contains('disabled'), cls: ok.className,
    x: r.x + r.width / 2, y: r.y + r.height / 2, buttons: btns.map((b) => (b.textContent || b.title || '').trim()) };
});
console.log('OK button:', JSON.stringify(okState));
if (okState.x !== undefined) await page.mouse.click(okState.x, okState.y);
await page.waitForTimeout(2000);
console.log('dialogs after OK:', JSON.stringify(await dialogs()));
console.log('after OK, top dialog:', JSON.stringify(await readTopDialogBody(page), null, 1));
console.log('feedback/invalid:', JSON.stringify(await page.evaluate(() => {
  const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
  const top = popups[popups.length - 1];
  return { feedback: [...top.querySelectorAll('.feedback, .invalid-feedback')].map((e) => e.textContent.trim()),
    invalid: top.querySelectorAll('.invalid').length };
})));
console.log('page errors:', JSON.stringify(errors, null, 1));
await browser.close();
