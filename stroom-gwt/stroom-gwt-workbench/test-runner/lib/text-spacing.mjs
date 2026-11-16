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

// Text spacing (WCAG 1.4.12).
//
// --text-spacing shows the stories with the spacing a user may set for themselves (with a
// bookmarklet or a browser extension), which must lose no content or function: lines 1.5 times
// the font size apart, letters 0.12 times it, words 0.16 times it, and 2 times it after each
// paragraph. With --text-fit, compared with a run without it (text-fit-diff.mjs), it finds the text
// the spacing cuts off or runs together.

// The style sheet a user's text spacing override applies, as in WCAG's own test
export const TEXT_SPACING_CSS = `
* {
  line-height: 1.5 !important;
  letter-spacing: 0.12em !important;
  word-spacing: 0.16em !important;
}
p {
  margin-bottom: 2em !important;
}`;

// A script for every frame that adds the text spacing style sheet to the document, and adds it
// again if something replaces the document's head
export function textSpacingScript() {
  return `(() => {
  const css = ${JSON.stringify(TEXT_SPACING_CSS)};
  const apply = () => {
    const root = document.head ?? document.documentElement;
    if (!root || document.getElementById('workbench-text-spacing')) {
      return;
    }
    const style = document.createElement('style');
    style.id = 'workbench-text-spacing';
    style.textContent = css;
    root.appendChild(style);
  };
  apply();
  document.addEventListener('DOMContentLoaded', apply);
  new MutationObserver(apply).observe(document, { childList: true, subtree: true });
})();`;
}
