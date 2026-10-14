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

// Real input checks: after some stories pass, the runner presses real keys and clicks with the
// real mouse (Playwright's page.keyboard and page.mouse) on them. A play can only dispatch events
// itself, which the browser doesn't trust: its Tab is simulated (as user-event's), and the browser
// can't tell its keys from its clicks, which matters to :focus-visible. These checks confirm the
// browser itself behaves as the plays expect.
//
// Each check is keyed by the id of the story it runs on, and throws (e.g. an assertion) if the page
// doesn't behave as it should.
//
// Each component's checks are in a file of their own here, exporting CHECKS, so work on one
// component (e.g. in its own worktree) adds a file rather than editing a shared one. A story's
// checks belong to one file: a story id in two files is an error.

import { CHECKS as BUTTONS } from './buttons.mjs';
import { CHECKS as SPLIT_PANE } from './split-pane.mjs';
import { CHECKS as TABS } from './tabs.mjs';

const FILES = { 'buttons.mjs': BUTTONS, 'split-pane.mjs': SPLIT_PANE, 'tabs.mjs': TABS };

// Merges each file's checks, refusing a story id found in two files
export function mergeChecks(files) {
  const checks = {};
  const owners = {};
  for (const [file, fileChecks] of Object.entries(files)) {
    for (const [storyId, check] of Object.entries(fileChecks)) {
      if (owners[storyId]) {
        throw new Error(`The real input check for ${storyId} is in both ${owners[storyId]} and ${file}`);
      }
      owners[storyId] = file;
      checks[storyId] = check;
    }
  }
  return checks;
}

export const REAL_INPUT_CHECKS = mergeChecks(FILES);
