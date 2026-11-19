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

// Forced colours (e.g. Windows high contrast) for the real input checks.

// Whether the page is shown in forced colours (the run's --forced-colours, or emulated)
export function isForced(page) {
  return page.evaluate(() => matchMedia('(forced-colors: active)').matches);
}

// Runs the check with forced colours emulated, then puts back what the page had: none in a normal
// run, but still forced in a forced-colours run (--forced-colours), whose screenshots and later
// checks must stay forced
export async function withForcedColours(page, check) {
  const was = await isForced(page);
  await page.emulateMedia({ forcedColors: 'active' });
  try {
    return await check();
  } finally {
    await page.emulateMedia({ forcedColors: was ? 'active' : 'none' });
  }
}
