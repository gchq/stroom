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
// Settle a screen, then photograph it.
//
// Shared by the recorder and the runner ON PURPOSE. The baseline and the run must be taken under
// identical conditions or the suite reports differences it created itself: the first version of this
// suite screenshotted raw while recording and parked the pointer while running, and every screen came
// back 600-2600px "changed" — all of it a hover highlight on whichever row the last click landed on.
//
// Each step here is a defence against a difference the HARNESS makes rather than the UI:
//   - a menu or popup left open by the navigation that got us here
//   - a focus ring on the element that was clicked
//   - a :hover row under the pointer where the click landed
import { gzipSync } from 'node:zlib';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { dismissMenus } from '../compare/lib/structure.mjs';

/**
 * Settle, photograph, and dump the DOM.
 *
 * The screenshot answers "did this change"; the DOM answers "what IS this", which is the question
 * that actually gets asked when diagnosing a port gap. A pixel diff can say a row is 4px tall of
 * GWT's; only the markup says the cell holds a 22px button where GWT emits a bare 17px div. Having
 * GWT's real markup on disk, per screen, turns that from a live-probing exercise into a grep.
 *
 * Stored gzipped — the raw ground truth rather than a summary, because the summaries worth having
 * (class lists, geometry, cell contents) can all be derived from it later and none of them can be
 * un-derived from a summary that dropped the wrong thing.
 */
export async function settleAndShoot(page, file, { wait = 4500, viewportHeight = 1000, domFile } = {}) {
  await page.waitForTimeout(wait);
  await dismissMenus(page).catch(() => {});
  await page.evaluate(() =>
    document.activeElement instanceof HTMLElement ? document.activeElement.blur() : undefined,
  );
  await page.mouse.move(4, viewportHeight - 6);
  await page.waitForTimeout(350);
  await page.screenshot({ path: file });
  if (domFile) {
    const html = await page.evaluate(() => document.documentElement.outerHTML);
    mkdirSync(dirname(domFile), { recursive: true });
    writeFileSync(domFile, gzipSync(Buffer.from(html, 'utf8')));
  }
}
