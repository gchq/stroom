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
// Does `Create new API key` accept an UNPARSEABLE expiry date?
//
// A shard reported: OK (illegal time in "Expiry Date") -> accepted (and asked to confirm),
// Expiry Date="not-a-time". `EditApiKeyPresenter:251/254` declares a rule for an expiry in the PAST
// ("API Key expiry date cannot be in the past"), but a rule for text that is not a date at all is a
// different thing. Before that is filed it has to survive:
//
//   1. Did the box actually TAKE the text? A DateTimeBox that refuses it proves nothing.
//   2. What did OK really do — close, confirm, or nothing?
//   3. Was a write attempted, i.e. did the form send an unparseable expiry to the server?
//
// The guard is armed, so nothing is created either way.
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { attachReadOnlyGuard } from './compare/lib/readonly-guard.mjs';
import { clickMenuItem } from './compare/lib/structure.mjs';
import { feedbackShown, readFeedback } from './lib/checks.mjs';
import { readFields } from './lib/illegal.mjs';

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const BAD = env('BAD', 'not-a-time');
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
const guard = attachReadOnlyGuard(page, { enabled: true });
await page.goto(env('URL', 'http://localhost:8080'), { waitUntil: 'domcontentloaded', timeout: 60_000 });
await gwt.signInFully(page, credentials());
const settle = (ms) => page.waitForTimeout(ms);
const un = (t) => { const x = String(t ?? '').replace(/\s+/g, ' ').trim(); return /^(\S+)( \1)+$/.test(x) ? x.split(' ')[0] : x; };

const press = async (want) => {
  const at = await page.evaluate(([w, dedupe]) => {
    const u = (t) => { const x = String(t ?? '').replace(/\s+/g, ' ').trim(); return new RegExp(dedupe).test(x) ? x.split(' ')[0] : x; };
    const b = [...document.querySelectorAll('button, [role=button], .Button')].filter((e) => e.offsetWidth > 0)
      .find((e) => u(e.getAttribute('title')) === w || u(e.textContent) === w);
    if (!b || b.disabled || b.classList.contains('disabled')) return null;
    const r = b.getBoundingClientRect();
    return { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2) };
  }, [want, '^(\\S+)( \\1)+$']);
  if (!at) return false;
  await page.mouse.click(at.x, at.y);
  await settle(1600);
  return true;
};
const caps = () => page.evaluate(() => [...document.querySelectorAll('.dialog-titleText')]
  .filter((e) => e.offsetWidth > 0).map((e) => e.textContent.trim()));

await gwt.openMainMenu(page);
await settle(400);
await clickMenuItem(page, 'Security');
await settle(500);
await clickMenuItem(page, 'Manage API Keys');
await settle(2200);
if (!(await press('Add new API Key'))) { console.log('Add new API Key is not available'); await browser.close(); process.exit(0); }

const form = await readFields(page);
console.log(`dialog: "${form?.caption}"`);
const expiry = (form?.fields ?? []).find((f) => /expiry/i.test(f.label));
const name = (form?.fields ?? []).find((f) => /name/i.test(f.label));
if (!expiry) { console.log('no Expiry Date box'); await browser.close(); process.exit(0); }
console.log(`Expiry Date opens as ${JSON.stringify(expiry.value)}`);

// A legal name, so the only thing under test is the expiry.
if (name) {
  await page.mouse.click(name.x, name.y);
  await page.keyboard.press('Control+A');
  await page.keyboard.type('zz-probe-badtime', { delay: 15 });
}
await page.mouse.click(expiry.x, expiry.y);
await page.keyboard.press('Control+A');
await page.keyboard.type(BAD, { delay: 25 });
await page.keyboard.press('Tab');
await settle(600);

const after = await readFields(page);
const got = (after?.fields ?? []).find((f) => /expiry/i.test(f.label))?.value ?? null;
console.log(`after typing ${JSON.stringify(BAD)} the box holds ${JSON.stringify(got)} — landed=${got === BAD}`);
if (got !== BAD) {
  console.log('→ the widget refused or normalised it, so the form was never asked. NOT a finding.');
  await browser.close();
  process.exit(0);
}

const before = await caps();
const vBefore = guard.violations.length;
if (!(await press('OK'))) { console.log('OK would not take a click'); await browser.close(); process.exit(0); }
const now = await caps();
const feed = await readFeedback(page);
const alertText = await page.evaluate(() => {
  const a = [...document.querySelectorAll('.alert-message')].filter((e) => e.offsetWidth > 0).pop();
  return a ? String(a.textContent ?? '').replace(/\s+/g, ' ').trim() : null;
});
const blocked = guard.violations.slice(vBefore).map((v) => `${v.method} ${v.path}`);
console.log(`\nAFTER OK:`);
console.log(`  dialogs ${JSON.stringify(before)} -> ${JSON.stringify(now)}`);
console.log(`  alert: ${JSON.stringify(alertText)}`);
console.log(`  inline: shown=${feedbackShown(feed)} ${JSON.stringify(feed).slice(0, 180)}`);
console.log(`  writes the guard blocked: ${JSON.stringify(blocked)}`);
console.log(`\n  verdict: ${blocked.length
  ? 'the form SENT an unparseable expiry to the server — a real gap, no client-side rule'
  : alertText ? 'the form answered with a message' : 'the form did nothing visible'}`);
void un;
await browser.close();
