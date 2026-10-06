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

// Loads the reference implementation - React Storybook's `storybook/test` bundle (Testing Library,
// user-event 14, jest-dom, dom-accessibility-api and aria-query) - into a page, unmodified apart
// from replacing process.env.NODE_ENV (as Storybook's builder does), stubbing its two imports of
// Storybook internals (the instrumenter, which only wraps the
// functions for the Interactions panel, and the client logger) and exporting a few of its internal
// functions, so the self-test can call the very code React Storybook plays use.
//
// Nothing is copied into this repository: the files are read from the React project's
// node_modules (see --react-modules) and served to the page with Playwright's request routing.

import { existsSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import path from 'node:path';

const PREFIX = '/__selftest_ref/';

// Internal functions of the bundle the self-test calls, by the names the bundle gives them
const INTERNALS = `
export {
  computeAccessibleName2 as __computeAccessibleName,
  getImplicitAriaRoles2 as __getImplicitAriaRoles,
  isInaccessible as __isInaccessible,
  getNodeText as __getNodeText,
};
`;

const STUBS = {
  'instrumenter.js': 'export const instrument = (object) => object;\n',
  'client-logger.js': 'const noop = () => {};\n'
    + 'export const once = { warn: noop, error: noop, info: noop, log: noop, debug: noop, trace: noop };\n'
    + 'export const logger = once;\n',
};

/// @param reactModules The React project's node_modules directory.
/// @return The directory of Storybook's browser build, or null if it isn't there.
export function referenceDir(reactModules) {
  const dir = path.join(reactModules, 'storybook', 'dist');
  return existsSync(path.join(dir, 'test', 'index.js')) ? dir : null;
}

/// Serves the reference to the page and imports it, setting `window.__ref` to the module.
///
/// @param page The Playwright page, already showing a page on the workbench's origin.
/// @param dir  The directory from referenceDir().
export async function loadReference(page, dir) {
  await page.route(`**${PREFIX}**`, async (route) => {
    const url = new URL(route.request().url());
    const relative = decodeURIComponent(url.pathname.slice(PREFIX.length));
    if (relative.startsWith('stub/')) {
      await route.fulfill({ contentType: 'text/javascript', body: STUBS[relative.slice(5)] ?? '' });
      return;
    }
    const file = path.resolve(dir, relative);
    if (!file.startsWith(dir + path.sep)) {
      await route.fulfill({ status: 404, body: '' });
      return;
    }
    let body = await readFile(file, 'utf8');
    body = body
      .replaceAll('"storybook/internal/instrumenter"', `"${PREFIX}stub/instrumenter.js"`)
      .replaceAll('"storybook/internal/client-logger"', `"${PREFIX}stub/client-logger.js"`)
      // As Storybook's builder does when it bundles the preview
      .replaceAll('process.env.NODE_ENV', '"production"');
    if (relative === 'test/index.js') {
      body += INTERNALS;
    }
    await route.fulfill({ contentType: 'text/javascript', body });
  });
  await page.addScriptTag({
    type: 'module',
    content: `import * as reference from '${PREFIX}test/index.js'; window.__ref = reference;`,
  });
  await page.waitForFunction(() => window.__ref !== undefined, null, { timeout: 30000 });
}
