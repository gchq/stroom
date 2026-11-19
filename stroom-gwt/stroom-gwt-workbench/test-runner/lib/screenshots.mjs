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

// Where the runner saves a story's screenshot. Story ids come from the workbench, so are only
// used in file names once made safe: they could contain e.g. '/' or '..'.

import { createHash } from 'node:crypto';
import path from 'node:path';

// The longest story id used in a file name as it is, leaving room for a hash and the extension
// within common 255 byte file name limits
const MAX_BASE_LENGTH = 150;
// Names Windows doesn't allow for files, whatever their extension
const WINDOWS_DEVICE_NAMES = /^(?:con|prn|aux|nul|com\d|lpt\d)(?:\.|$)/i;

// The base of a file name for the story id: the id itself if it is a plain Storybook id (letters,
// digits, '-', '_' and '.', not starting with '.'), otherwise the id with any other characters
// replaced by '_', and a hash of the id so that different ids can't share a file.
export function safeFileBase(storyId) {
  const id = String(storyId);
  const replaced = id.replace(/[^A-Za-z0-9._-]/g, '_').replace(/^\./, '_');
  if (replaced === id && id !== '' && id.length <= MAX_BASE_LENGTH && !WINDOWS_DEVICE_NAMES.test(id)) {
    return id;
  }
  const hash = createHash('sha256').update(id).digest('hex').substring(0, 10);
  return `${replaced.substring(0, MAX_BASE_LENGTH)}-${hash}`;
}

// The screenshot file in screenshotsDir for the story: <id>.png, or <id>.attempt-<n>.png for
// attempt n when attempt is given. Throws if it would somehow be outside screenshotsDir.
export function screenshotFile(screenshotsDir, storyId, attempt = null) {
  const dir = path.resolve(screenshotsDir);
  const name = `${safeFileBase(storyId)}${attempt === null ? '' : `.attempt-${attempt}`}.png`;
  const file = path.resolve(dir, name);
  if (path.dirname(file) !== dir) {
    throw new Error(`The screenshot for story '${storyId}' would be outside ${dir}`);
  }
  return file;
}
