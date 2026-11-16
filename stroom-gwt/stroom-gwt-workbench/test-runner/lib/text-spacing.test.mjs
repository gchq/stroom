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

import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';

import { textSpacingScript } from './text-spacing.mjs';

let browser;
before(async () => {
  const { chromium } = await import('playwright');
  browser = await chromium.launch();
});
after(async () => {
  await browser?.close();
});

const spacing = () => {
  const p = document.querySelector('p');
  const style = getComputedStyle(p);
  return [style.lineHeight, style.letterSpacing, style.wordSpacing, style.marginBottom];
};

test('the text spacing a user may set overrides the page\'s own, in every frame', async () => {
  const context = await browser.newContext();
  await context.addInitScript(textSpacingScript());
  const page = await context.newPage();
  const body = '<p style="font-size:10px;line-height:1;letter-spacing:0;margin:0">Text</p>';
  await page.setContent(`<html><body>${body}<iframe srcdoc='${body}'></iframe></body></html>`);
  assert.deepEqual(await page.evaluate(spacing), ['15px', '1.2px', '1.6px', '20px']);
  const frame = page.frames()[1];
  await frame.waitForLoadState();
  assert.deepEqual(await frame.evaluate(spacing), ['15px', '1.2px', '1.6px', '20px']);
  await context.close();
});

test('without it the page\'s own spacing stands', async () => {
  const page = await browser.newPage();
  await page.setContent('<p style="font-size:10px;line-height:1;letter-spacing:0;margin:0">Text</p>');
  assert.deepEqual(await page.evaluate(spacing), ['10px', 'normal', '0px', '0px']);
  await page.close();
});
