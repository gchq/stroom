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
// Why does Ctrl+Enter do nothing on some dialogs whose OK button works?
//
// `ResizableOkCancelContent`'s OK button calls `onDialogAction(DialogAction.OK)` — the very method
// `AbstractPopupPanel` calls for `Action.OK` (Ctrl+Enter, KeyBinding:79). The two are the same path,
// so a dialog where the button works and the key does not means the key never arrived as Action.OK.
// This opens `Edit Property` on a Pipeline's Structure tab and reports, at the moment of the press,
// what has focus and what the key did.
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { attachReadOnlyGuard } from './compare/lib/readonly-guard.mjs';
import { clickButton, dialogCaptions } from './lib/cycle.mjs';

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
const guard = attachReadOnlyGuard(page, { enabled: true });
await page.goto(env('URL', 'http://localhost:8080'), { waitUntil: 'domcontentloaded', timeout: 60_000 });
await gwt.signInFully(page, credentials());
const settle = (ms) => page.waitForTimeout(ms);

// `Save Tab Session` — four of the ledger's current `OK did nothing` fails — is three clicks from
// the main menu, so it is the cheapest of them to reproduce.
import { clickMenuItem } from './compare/lib/structure.mjs';
console.log(`Main Menu: ${await clickButton(page, 'Main Menu')}`);
await settle(800);
console.log(`Navigation: ${await clickMenuItem(page, 'Navigation')}`);
await settle(700);
console.log(`Save Tab Session: ${await clickMenuItem(page, 'Save Tab Session')}`);
await settle(1800);
console.log(`dialogs: ${JSON.stringify(await dialogCaptions(page))}`);
console.log(JSON.stringify(await page.evaluate(() => {
  const n = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
  const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
  const top = popups[popups.length - 1];
  if (!top) return null;
  return {
    groups: [...top.querySelectorAll('.form-group')].map((g) => n(g.querySelector('.form-group-label')?.textContent)),
    boxes: [...top.querySelectorAll('input, textarea')].map((e) => ({ cls: String(e.className).slice(0, 40), value: e.value, disabled: e.disabled })),
    feedback: [...top.querySelectorAll('.feedback, .invalid-feedback')].map((e) => n(e.textContent)),
  };
}), null, 1));

const focusNow = () => page.evaluate(() => {
  const a = document.activeElement;
  return a ? `${a.tagName.toLowerCase()}.${String(a.className || '').split(' ').slice(0, 3).join('.')}` : 'none';
});
console.log(`focus before the key: ${await focusNow()}`);
// Watch what the page makes of the key: is it seen, and is ctrl set?
await page.evaluate(() => {
  window.__keys = [];
  document.addEventListener('keydown', (e) => window.__keys.push(
    `${e.key} ctrl=${e.ctrlKey} target=${e.target?.tagName?.toLowerCase()}.${String(e.target?.className || '').split(' ')[0]} defaultPrevented=${e.defaultPrevented}`), true);
});
await page.keyboard.press('Control+Enter');
await settle(1800);
console.log(`keydown seen: ${JSON.stringify(await page.evaluate(() => window.__keys))}`);
console.log(`dialogs after Ctrl+Enter: ${JSON.stringify(await dialogCaptions(page))}`);
console.log(`OK clicked: ${await clickButton(page, 'OK')}`);
await settle(1800);
console.log(`dialogs after OK: ${JSON.stringify(await dialogCaptions(page))}`);
console.log(`feedback after OK: ${JSON.stringify(await page.evaluate(() => {
  const n = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
  const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
  const top = popups[popups.length - 1];
  return top ? { feedback: [...top.querySelectorAll('.feedback, .invalid-feedback')].map((e) => n(e.textContent)).filter(Boolean), invalid: top.querySelectorAll('.invalid').length } : null;
}))}`);
console.log(`guard aborted: ${JSON.stringify((guard.violations ?? []).map((v) => `${v.method} ${v.path}`))}`);
await browser.close();
