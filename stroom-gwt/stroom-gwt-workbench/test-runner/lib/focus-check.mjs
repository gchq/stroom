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

// The focus check: after a story that shows a screen (ScreenHarness.show) passes, the runner
// focuses the screen as Stroom's tab pane does when it opens it (ContentFocus, through the page's
// window.__workbenchContentFocus) and fails the story if the focus doesn't go into the screen, onto
// something a keyboard user can use (WCAG 2.4.3, Focus Order: opening a screen takes the user to
// it). A screen with nothing to tab to takes the focus itself, which fails too, as a keyboard user
// can do nothing there. It runs after the story's screenshot, as it moves the focus.

// Focuses the story's screen and returns {checked: false} if the story shows none, otherwise
// {checked: true, inside, tabStop, name}.
export async function measureContentFocus(page) {
  return page.evaluate(() => {
    if (typeof window.__workbenchContentFocus !== 'function') {
      return { checked: false };
    }
    document.activeElement?.blur();
    return { checked: true, ...window.__workbenchContentFocus() };
  });
}

// Returns the error to fail the story with, or null if the focus went where it should.
export function judgeContentFocus(measured) {
  if (!measured?.checked) {
    return null;
  }
  if (!measured.inside) {
    return 'Focus on open: focusing the screen left the focus outside it '
      + `(on ${measured.name ?? 'nothing'})`;
  }
  if (!measured.tabStop) {
    return 'Focus on open: the screen has nothing a keyboard user can tab to, so it took the focus '
      + 'itself';
  }
  return null;
}
