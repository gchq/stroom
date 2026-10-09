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
// Document-editor sub-tabs — shared by the recorder and the runner.
//
// An editor is photographed as it opens, which is one pane of several. `Settings`, `Fields`,
// `Documentation` and `Permissions` are where most of the editor's surface lives: the Elastic Index
// editor opens on an eleven-field form and keeps a whole grid, with its own dialogs, one tab away.
//
// This lives in a shared module for the same reason `shoot.mjs` does. If the recorder and the runner
// each had their own idea of which tabs exist and how to click one, a difference between them would
// look exactly like a regression in the UI.

/**
 * The sub-tabs of the open document editor.
 *
 * Two things about the markup matter. A `.linkTab` contains the label AND a hidden width-reserving
 * copy of it (`.linkTabBar-hiddenText`), so reading the tab's own `textContent` gives
 * "DocumentationDocumentation" — the label element is the one to read. And the same bar holds the
 * application's CONTENT tabs (`Welcome`, and the document itself), which are not sub-tabs at all:
 * clicking either navigates away from the editor being photographed.
 */
export async function readEditorTabs(page, docLabel) {
  const all = await page.evaluate(() => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    return [...document.querySelectorAll('.linkTab-label, .curveTab-label')]
      .filter((e) => e.offsetWidth > 0 && e.offsetHeight > 0)
      .map((e) => norm(e.textContent))
      .filter(Boolean);
  });
  return all.filter((t) => t !== 'Welcome' && !t.includes(docLabel));
}

/**
 * Click a sub-tab by its label.
 *
 * Matched on the label, CLICKED on the tab: `.linkTab-hotspot` overlays the label and takes the
 * pointer, so a click aimed at `.linkTab-label` is intercepted rather than delivered.
 */
export async function clickEditorTab(page, name, { timeout = 10_000, nth = 0 } = {}) {
  const tabs = page
    .locator('.linkTab, .curveTab')
    .filter({ has: page.locator(`.linkTab-label:text-is("${name}"), .curveTab-label:text-is("${name}")`) });
  // `nth` because a label is not unique: a dashboard can hold two components both called `Table`, and
  // addressing them by name alone silently reaches the first one twice.
  const tab = tabs.nth(nth);
  if ((await tabs.count()) <= nth) return false;
  await tab.click({ timeout });
  await page.waitForTimeout(1200);
  return true;
}

/** A stable, filesystem-safe name for a (document, tab) pair. */
export const tabSlug = (docSlug, tab) => `${docSlug}-tab-${tab.replace(/[^a-z0-9]+/gi, '-').toLowerCase()}`;
