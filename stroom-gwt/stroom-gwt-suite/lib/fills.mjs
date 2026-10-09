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
// Per-type test data for the seeded documents.
import { fileURLToPath } from 'node:url';

// The suite's folder, so its corpus and output are found wherever the suite is run from
const SUITE = fileURLToPath(new URL('..', import.meta.url)).replace(/\/$/, '');
//
// A created-but-empty document proves the editor RENDERS; it does not exercise what the editor is
// for. An Elastic Index with no cluster and no index name draws the same empty grid whatever the
// code does, and a Documentation doc with no markdown is a blank pane. So each type gets a fill.
//
// Values are deterministic — no random, no clock — because the corpus recorded from them has to be
// byte-comparable between runs. Where a value must look plausible (a URL, a bucket, a cron) it is
// obviously fake rather than borrowed from anything real.
//
// A fill returns a short string describing what it did, for the run log; it returns null if it found
// nothing to do, which is a signal that the editor changed rather than an error.

/** GWT form field: the `.form-group` whose label matches, then the control inside it. */
export function field(scope, label) {
  return scope.locator('.form-group').filter({ has: scope.locator(`label:text-is("${label}")`) }).first();
}

/**
 * Type into the text box of a labelled form field. Returns false if the field is not there, or is
 * there but not editable — several are disabled until a toggle above them is on, and waiting the
 * default 30s for one of those to become enabled turns a skipped field into a failed fill.
 */
export async function setField(scope, label, value) {
  const g = field(scope, label);
  if (!(await g.count())) return false;
  const box = g.locator('input.gwt-TextBox, input[type=text], input[type=password], textarea').first();
  if (!(await box.count())) return false;
  return box
    .fill(String(value), { timeout: 5_000 })
    .then(() => true)
    .catch(() => false);
}

/**
 * Save the open document, and prove it saved.
 *
 * `Ctrl+S` is the accelerator, but whether it lands depends on where the focus is — after typing
 * into an Ace editor it may not reach the app at all, which is how a "filled" document came back
 * still dirty. The toolbar button is unambiguous in both directions: it is `disabled` when there is
 * nothing to save, so clicking it and waiting for it to go back to disabled is the save AND the
 * confirmation.
 */
export async function saveDocument(page, { budgetMs = 15_000 } = {}) {
  const btn = page.locator('button[title="Save"]').first();
  if (!(await btn.count())) return 'no save button';
  if (await btn.isDisabled().catch(() => false)) return 'nothing to save';
  await btn.click({ timeout: 10_000 });
  const deadline = Date.now() + budgetMs;
  while (Date.now() < deadline) {
    await page.waitForTimeout(400);
    if (await btn.isDisabled().catch(() => false)) return 'saved';
    // A rejected save raises an Alert and leaves the button enabled. Report the SERVER's reason —
    // "still dirty" alone sent one of these fills round three diagnostic loops.
    const alert = await page.evaluate(() => {
      const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
      const p = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].find(
        (x) => norm(x.querySelector('.dialog-titleText')?.textContent) === 'Alert',
      );
      if (!p) return null;
      const detail = [...p.querySelectorAll('textarea, .gwt-TextArea')].map((t) => t.value || t.textContent).join(' ');
      return `${norm(p.querySelector('.dialog-content')?.textContent)} ${detail}`.replace(/(Show|Hide) Detail/g, '').trim();
    });
    if (alert) return `rejected: ${alert.slice(0, 300)}`;
  }
  return 'still dirty';
}

/** Tick/untick the checkbox of a labelled form field. */
export async function setCheck(scope, label, on = true) {
  const g = field(scope, label);
  if (!(await g.count())) return false;
  const box = g.locator('input[type=checkbox], .gwt-CheckBox input').first();
  if (!(await box.count())) return false;
  if ((await box.isChecked()) !== on) await box.click({ force: true });
  return true;
}

/**
 * Set the text of an Ace editor.
 *
 * Ace keeps its value in a hidden textarea and renders its own DOM, so `fill()` on anything visible
 * does nothing. The editor instance is reachable from the container element, which is what Ace's own
 * API expects — typing the text instead would be slower and would trip auto-indent and autocomplete.
 */
export async function setAce(page, text, index = 0) {
  return page.evaluate(
    ([t, i]) => {
      const nodes = [...document.querySelectorAll('.ace_editor')];
      const el = nodes[i];
      if (!el) return false;
      const ed = window.ace?.edit ? window.ace.edit(el) : el.env?.editor;
      if (!ed) return false;
      ed.setValue(t, -1);
      return true;
    },
    [text, index],
  );
}

/**
 * Switch to a named sub-tab of the open document editor.
 *
 * Matched on `.linkTab-label` but CLICKED on the tab: `.linkTab` also holds a hidden
 * width-reserving copy of the label (so `textContent` reads "DocumentationDocumentation") and a
 * `.linkTab-hotspot` overlay that takes the pointer, which makes a click aimed at the label itself
 * bounce off with an interception error.
 */
export async function tab(page, name) {
  const t = page
    .locator('.linkTab, .curveTab')
    .filter({ has: page.locator(`.linkTab-label:text-is("${name}"), .curveTab-label:text-is("${name}")`) })
    .first();
  if (!(await t.count())) return false;
  await t.click({ timeout: 10_000 });
  await page.waitForTimeout(800);
  return true;
}

/** The Documentation sub-tab is markdown in an Ace editor, and nearly every doc type has one. */
export async function fillDocumentationTab(page, text) {
  if (!(await tab(page, 'Documentation'))) return false;
  const ok = await setAce(page, text);
  await page.waitForTimeout(300);
  return ok;
}

/**
 * Choose a document in a DocRef field.
 *
 * The control is a `SelectionBox` — a read-only text box that opens a popup list — the same widget
 * the create dialog uses for its destination folder. Returns false rather than throwing if the
 * field, the popup or the named document is not there, so a fill degrades to "partly filled"
 * instead of failing the seed.
 */
export async function pickDocRef(page, label, name) {
  const g = field(page, label);
  if (!(await g.count())) return false;
  const box = g.locator('input.SelectionBox-textBox, .SelectionBox').first();
  if (!(await box.count())) return false;
  await box.click();
  await page.waitForTimeout(800);
  const filter = page.locator('input.quickFilter-textBox:visible').last();
  if (await filter.count()) {
    await filter.pressSequentially(name, { delay: 30 });
    await page.waitForTimeout(1000);
  }
  const item = page.locator(`.explorerCell:has-text("${name}"), .selectionBoxItem:has-text("${name}")`).first();
  if (!(await item.count())) {
    await page.keyboard.press('Escape');
    return false;
  }
  await item.click({ timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(600);
  await page.keyboard.press('Escape').catch(() => {});
  return true;
}

/** Apply a list of [label, value] to whatever pane is showing; returns how many landed. */
async function setFields(page, pairs) {
  let n = 0;
  for (const [label, value] of pairs) if (await setField(page, label, value)) n += 1;
  return n;
}

/**
 * Test data, per document type.
 *
 * Only the types this suite SEEDS are here — the other sixteen already had example documents in the
 * instance. Values are fixed strings: obviously fake, and identical on every run so the corpus
 * recorded from them is comparable.
 *
 * DocRef fields are filled where the target exists in this instance; where the pick fails the fill
 * still reports what it managed, because a partly-filled editor is still far better for a baseline
 * than an empty one.
 */
export const FILLS = {
  Documentation: async (page) => {
    // A Documentation document opens in PREVIEW on every visit after the first, and preview has no
    // Ace editor mounted — so a re-run found nothing to type into and reported "nothing to fill" on
    // a document that was in fact already correct. Switch to edit mode first.
    const edit = page.locator('button[title="Edit"]').first();
    if ((await edit.count()) && (await edit.isVisible().catch(() => false))) {
      await edit.click().catch(() => {});
      await page.waitForTimeout(800);
    }
    const ok = await setAce(
      page,
      [
        '# Seed Documentation',
        '',
        'A seeded Documentation document, created by `${SUITE}/seed.mjs` so that the',
        'Documentation editor has something to render in the regression baselines.',
        '',
        '## Why it exists',
        '',
        'The corpus can only record documents that exist. This instance held no Documentation',
        'document, so the editor could not be photographed at all.',
        '',
        '| Column | Meaning |',
        '| --- | --- |',
        '| `type` | the document type |',
        '| `uuid` | its stable identifier |',
        '',
        '- a bullet',
        '- and another',
        '',
        '```',
        'a fenced code block, to exercise the markdown renderer',
        '```',
      ].join('\n'),
    );
    return ok ? 'markdown body' : null;
  },

  'Elastic Cluster': async (page) => {
    // "Use authentication" gates the two key fields — tick it FIRST or they are disabled and the
    // fill silently skips them.
    await setCheck(page, 'Use authentication', true);
    await page.waitForTimeout(400);
    const n = await setFields(page, [
      ['Connection URLs', 'http://elastic.seed.invalid:9200'],
      ['API key ID', 'seed-api-key-id'],
      ['API key secret', 'seed-api-key-secret'],
      ['Connection timeout (ms)', '5000'],
      ['Response timeout (ms)', '30000'],
    ]);
    await fillDocumentationTab(page, '# Seed Elastic Cluster\n\nPoints at a host that does not exist, on purpose.');
    return `${n} settings + documentation`;
  },

  'OpenAI Model': async (page) => {
    const n = await setFields(page, [
      ['Base URL (optional)', 'https://openai.seed.invalid/v1'],
      ['API Key (optional)', 'sk-seed-0000000000000000'],
      ['Model ID', 'seed-model-1'],
      ['Maximum context window tokens', '128000'],
      ['Embedding model dimensions', '1536'],
    ]);
    await fillDocumentationTab(page, '# Seed OpenAI Model\n\nA fake model definition, for baselines only.');
    return `${n} settings + documentation`;
  },

  'S3 Configuration': async (page) => {
    // The editor's pane looks like free text but the server PARSES it into `S3ClientConfig` on save
    // and rejects anything it cannot deserialise. `credentials` is polymorphic on `type`, and the
    // valid names are the @JsonSubTypes of `AwsCredentials` — "basic", not "static".
    const ok = await setAce(
      page,
      JSON.stringify(
        {
          credentials: { type: 'basic', accessKeyId: 'SEEDACCESSKEY', secretAccessKey: 'seed-secret-access-key' },
          region: 'eu-west-2',
          endpointOverride: 'https://s3.seed.invalid',
          bucketName: 'seed-stroom-bucket',
          keyPattern: '${feed}/${year}/${month}/${day}/${uuid}',
          forcePathStyle: true,
          createBuckets: false,
          async: false,
          multipart: false,
          crossRegionAccessEnabled: false,
        },
        null,
        2,
      ),
    );
    await fillDocumentationTab(page, '# Seed S3 Configuration\n\nAn S3 target that does not resolve, on purpose.');
    return ok ? 'config JSON + documentation' : 'documentation only';
  },

  'Elastic Index': async (page) => {
    const n = await setFields(page, [
      ['Index name or pattern', 'seed-index-*'],
      ['Main date field', '@timestamp'],
      ['Number of slices', '1'],
      ['Scroll size', '1000'],
      ['Rerank text field suffix', '_text'],
      ['Rerank score field suffix', '_score'],
      ['Minimum rerank score', '0.5'],
    ]);
    const cluster = await pickDocRef(page, 'Cluster configuration', 'Seed Elastic Cluster');
    await fillDocumentationTab(page, '# Seed Elastic Index\n\nIndex pattern `seed-index-*` on the seeded cluster.');
    return `${n} settings${cluster ? ' + cluster ref' : ''} + documentation`;
  },

  Pathways: async (page) => {
    // Pathways is experimental (see stroom-gwt/ISSUES.md), so the fill stays on the Settings tab's
    // plain controls rather than trying to author a pathway in the grid.
    if (!(await tab(page, 'Settings'))) return null;
    const boxes = ['Allow Pathway Creation', 'Allow Pathway Mutation', 'Allow Constraint Creation', 'Allow Constraint Mutation'];
    let n = 0;
    for (const b of boxes) if (await setCheck(page, b, true)) n += 1;
    await fillDocumentationTab(page, '# Seed Pathways\n\nSeeded so the Pathways editor has a baseline.');
    return `${n} toggles + documentation`;
  },
};
