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

// Text that doesn't fit (--text-fit): in each story, every text that is cut off by a box that hides
// what overflows it, shortened with an ellipsis, or drawn over another text. Run at two text sizes
// and compared (text-fit-diff.mjs), what is new at the larger size is what doesn't fit because the
// text grew (A11Y.md, "Text size and high contrast").

import { writeFile } from 'node:fs/promises';

// The most texts looked at in a story, over all its frames
export const MAX_TEXTS_PER_STORY = 3000;

// Run in a frame: its texts that don't fit, as {kind, path, text, by, other?}:
//   cut-off  - its line boxes reach past a box that hides its overflow (by: how far, in px)
//   ellipsis - shortened with text-overflow: ellipsis (by: how much is hidden, in px)
//   overlap  - drawn over another text, neither inside the other nor covering it with a box that
//              has a background (by: the overlap's smaller side; other: the other text's {path, text})
//   scrolls-sideways - a dialog whose content is wider than it (text: its title; by: how much wider)
// Each problem is listed once, however many times it appears.
function collectFitInFrame(max) {
  // Overflow of less than this is rounding, not text that doesn't fit
  const SLACK = 1.5;
  // How many ancestors may cut a text off (beyond that, it is the layout's, not the text's); every
  // ancestor clips what is drawn
  const MAX_CLIPPING_ANCESTORS = 6;
  // Boxes this small hide text on purpose (visually hidden text for screen readers)
  const HIDDEN_BOX = 2;
  // The size of the cells texts are bucketed in, to find overlaps without comparing every pair
  const CELL = 64;
  // How many times bigger than a text a box with a background must be to cover the text below it
  const COVER_AREA = 4;

  const describe = (element) => {
    const classes = [...(element.classList ?? [])].slice(0, 3).map((name) => `.${name}`).join('');
    return `${element.localName}${classes}`;
  };
  const pathOf = (element) => {
    const parts = [];
    for (let e = element; e && e.nodeType === 1 && parts.length < 4; e = e.parentElement) {
      parts.unshift(describe(e));
      if (e.localName === 'body' || e.localName === 'svg' && parts.length > 1) {
        break;
      }
    }
    return parts.join(' > ');
  };
  const ownTextNodes = (element) => [...element.childNodes]
    .filter((node) => node.nodeType === 3 && node.nodeValue.trim() !== '');
  const visible = (element, style) => {
    if (style.visibility === 'hidden' || style.display === 'none' || Number(style.opacity) === 0) {
      return false;
    }
    const rect = element.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  };
  // Where its own text is drawn: the union of its text nodes' lines, each the font size tall, centred
  // in its line box. A line box is the font's content area, which for some fonts (Roboto Mono's is
  // 1.32em) reaches well past the glyphs; the font size is nearer what is drawn.
  const textRect = (nodes, fontSize) => {
    let rect = null;
    for (const node of nodes) {
      const range = document.createRange();
      range.selectNodeContents(node);
      for (const r of range.getClientRects()) {
        if (r.width === 0 || r.height === 0) {
          continue;
        }
        const trim = Math.max(0, (r.height - fontSize) / 2);
        const top = r.top + trim;
        const bottom = r.bottom - trim;
        rect = rect
          ? { left: Math.min(rect.left, r.left), top: Math.min(rect.top, top),
              right: Math.max(rect.right, r.right), bottom: Math.max(rect.bottom, bottom) }
          : { left: r.left, top, right: r.right, bottom };
      }
    }
    return rect;
  };
  const hides = (value) => value === 'hidden' || value === 'clip';
  const scrolls = (value) => value === 'auto' || value === 'scroll';
  // Boxes that hide their overflow but are scrolled by script, with scroll bars of their own: the code
  // editor's (Ace) text and the line numbers beside it
  const SCRIPT_SCROLLERS = '.ace_scroller, .ace_gutter';
  // How far the text reaches past the nearest boxes that hide their overflow (or 0), and the part of
  // it shown by every box that clips it (null if none). Past a box that scrolls, text can be scrolled
  // to: neither it nor the boxes beyond it cut it off in that direction, though they still clip what
  // is drawn (rows scrolled out of a grid's view are not drawn over what is below it).
  const clip = (element, rect) => {
    let by = 0;
    const shown = { ...rect };
    let scrolledX = false;
    let scrolledY = false;
    let e = element;
    for (let i = 0; e && e.nodeType === 1; e = e.parentElement, i++) {
      if (e.localName === 'body' || e.localName === 'html') {
        break;
      }
      const style = getComputedStyle(e);
      const clipsX = style.overflowX !== 'visible';
      const clipsY = style.overflowY !== 'visible';
      const near = i < MAX_CLIPPING_ANCESTORS;
      const byScript = e.matches(SCRIPT_SCROLLERS);
      const cutsX = near && hides(style.overflowX) && !scrolledX && !byScript;
      const cutsY = near && hides(style.overflowY) && !scrolledY && !byScript;
      scrolledX ||= scrolls(style.overflowX) || byScript;
      scrolledY ||= scrolls(style.overflowY) || byScript;
      if (!clipsX && !clipsY) {
        continue;
      }
      const box = e.getBoundingClientRect();
      const left = box.left + e.clientLeft;
      const top = box.top + e.clientTop;
      const right = left + e.clientWidth;
      const bottom = top + e.clientHeight;
      if ((cutsX || cutsY) && (e.clientWidth <= HIDDEN_BOX || e.clientHeight <= HIDDEN_BOX)) {
        return { by: 0, shown: null };
      }
      if (cutsX) {
        by = Math.max(by, rect.right - right, left - rect.left);
      }
      if (cutsY) {
        by = Math.max(by, rect.bottom - bottom, top - rect.top);
      }
      if (clipsX) {
        shown.left = Math.max(shown.left, left);
        shown.right = Math.min(shown.right, right);
      }
      if (clipsY) {
        shown.top = Math.max(shown.top, top);
        shown.bottom = Math.min(shown.bottom, bottom);
      }
    }
    return {
      by: by > SLACK ? by : 0,
      shown: shown.right > shown.left && shown.bottom > shown.top ? shown : null,
    };
  };

  const problems = new Map();
  const add = (problem) => {
    const key = `${problem.kind}|${problem.path}|${problem.text}|${problem.other?.path ?? ''}|${problem.other?.text ?? ''}`;
    if (!problems.has(key)) {
      problems.set(key, { ...problem, by: Math.round(problem.by * 10) / 10 });
    }
  };
  const texts = [];
  const walker = document.createTreeWalker(document.body ?? document.documentElement, NodeFilter.SHOW_ELEMENT);
  for (let element = walker.currentNode; element && texts.length < max; element = walker.nextNode()) {
    if (['script', 'style', 'noscript', 'template', 'textarea', 'option'].includes(element.localName)) {
      continue;
    }
    const nodes = ownTextNodes(element);
    if (nodes.length === 0) {
      continue;
    }
    const style = getComputedStyle(element);
    if (!visible(element, style)) {
      continue;
    }
    const rect = textRect(nodes, parseFloat(style.fontSize) || 0);
    if (!rect) {
      continue;
    }
    const text = nodes.map((node) => node.nodeValue).join('').replace(/\s+/g, ' ').trim().slice(0, 40);
    const path = pathOf(element);
    const { by, shown } = clip(element, rect);
    // Only the part shown can be drawn over another text
    if (shown) {
      texts.push({ element, rect: shown, path, text });
    }
    if (by > 0) {
      add({ kind: 'cut-off', path, text, by });
    }
    if (style.textOverflow === 'ellipsis' && element.scrollWidth - element.clientWidth > SLACK) {
      add({ kind: 'ellipsis', path, text, by: element.scrollWidth - element.clientWidth });
    }
  }

  // Dialogs whose content is wider than they are, so it scrolls sideways
  for (const content of document.querySelectorAll('.dialog__content')) {
    const over = content.scrollWidth - content.clientWidth;
    if (over > SLACK && visible(content, getComputedStyle(content))) {
      const title = content.closest('.dialog__container')?.querySelector('.dialog__title');
      add({ kind: 'scrolls-sideways', path: pathOf(content),
            text: (title?.textContent ?? '').replace(/\s+/g, ' ').trim().slice(0, 40), by: over });
    }
  }

  // Whether something with a background lies between two texts at a point, so the lower is covered
  // rather than drawn over (text under a dialog, rows scrolled under a grid's sticky header). Only a
  // box much bigger than the upper text counts: a button or lozenge drawn over text is an overlap.
  const covered = (a, b, x, y) => {
    const stack = document.elementsFromPoint(x, y);
    const ia = stack.indexOf(a.element);
    const ib = stack.indexOf(b.element);
    if (ia < 0 || ib < 0) {
      return false;
    }
    const [upper, lower] = ia < ib ? [a, b] : [b, a];
    const upperArea = (upper.rect.right - upper.rect.left) * (upper.rect.bottom - upper.rect.top);
    for (const e of stack.slice(0, Math.max(ia, ib))) {
      if (e === upper.element || e.contains(lower.element)) {
        continue;
      }
      const style = getComputedStyle(e);
      const painted = style.backgroundImage !== 'none'
        || !/^(transparent|rgba\(.*,\s*0\))$/.test(style.backgroundColor);
      const box = e.getBoundingClientRect();
      if (painted && box.width * box.height > COVER_AREA * upperArea) {
        return true;
      }
    }
    return false;
  };

  // Overlaps: texts in the same cells, neither inside the other, whose line boxes cross
  const cells = new Map();
  texts.forEach((item, index) => {
    for (let x = Math.floor(item.rect.left / CELL); x <= Math.floor(item.rect.right / CELL); x++) {
      for (let y = Math.floor(item.rect.top / CELL); y <= Math.floor(item.rect.bottom / CELL); y++) {
        const key = `${x},${y}`;
        if (!cells.has(key)) {
          cells.set(key, []);
        }
        cells.get(key).push(index);
      }
    }
  });
  const checked = new Set();
  for (const indexes of cells.values()) {
    for (let i = 0; i < indexes.length; i++) {
      for (let j = i + 1; j < indexes.length; j++) {
        const pair = `${indexes[i]},${indexes[j]}`;
        if (checked.has(pair)) {
          continue;
        }
        checked.add(pair);
        const a = texts[indexes[i]];
        const b = texts[indexes[j]];
        if (a.element.contains(b.element) || b.element.contains(a.element)) {
          continue;
        }
        const width = Math.min(a.rect.right, b.rect.right) - Math.max(a.rect.left, b.rect.left);
        const height = Math.min(a.rect.bottom, b.rect.bottom) - Math.max(a.rect.top, b.rect.top);
        const x = Math.max(a.rect.left, b.rect.left) + width / 2;
        const y = Math.max(a.rect.top, b.rect.top) + height / 2;
        if (width > SLACK && height > SLACK && !covered(a, b, x, y)) {
          // In document order, so the same pair is described the same way at any text size
          add({ kind: 'overlap', path: a.path, text: a.text, by: Math.min(width, height),
                other: { path: b.path, text: b.text } });
        }
      }
    }
  }
  return [...problems.values()];
}

// Every text that doesn't fit in the page's frames (see collectFitInFrame), each with the frame it
// is in: '' for the page, otherwise the frame's path, e.g. /ui/vis.html
export async function collectTextFit(page) {
  const problems = [];
  for (const frame of page.frames()) {
    const found = await frame.evaluate(collectFitInFrame, MAX_TEXTS_PER_STORY).catch(() => []);
    const name = frame === page.mainFrame() ? '' : frameName(frame.url());
    found.forEach((problem) => problems.push(name ? { ...problem, frame: name } : problem));
  }
  return problems;
}

function frameName(url) {
  try {
    return new URL(url).pathname;
  } catch {
    return url;
  }
}

// Writes the problems of each story (story id -> problems) to the file, with the font size they
// were shown at
export async function writeTextFit(file, size, problemsByStory) {
  const stories = {};
  for (const id of [...problemsByStory.keys()].sort()) {
    stories[id] = problemsByStory.get(id);
  }
  await writeFile(file, JSON.stringify({ fontSize: size ?? 'page', stories }, null, 1) + '\n');
}

// The identity of a problem within a story, the same at any text size. A text that doesn't fit in
// its box is one problem however it shows (shortened with an ellipsis, or cut off, by the box or
// by the one around it), so only its frame and text identify it.
function problemKey(problem) {
  if (problem.kind === 'cut-off' || problem.kind === 'ellipsis') {
    return ['too-long', problem.frame ?? '', problem.text].join('|');
  }
  return [problem.kind, problem.frame ?? '', problem.path, problem.text,
    problem.other?.path ?? '', problem.other?.text ?? ''].join('|');
}

// Compares two runs (as written by writeTextFit) at different text sizes: the problems, in the
// stories both ran, that only the second has. Returns
// {stories, problems, added: [{kind, path, frame, stories, examples: [{story, text, by, other?}]}]},
// grouped by kind, path and frame, the groups seen in the most stories first.
export function compareTextFit(baseline, actual) {
  const groups = new Map();
  let stories = 0;
  let problems = 0;
  for (const [story, actualProblems] of Object.entries(actual.stories ?? {})) {
    const baseProblems = baseline.stories?.[story];
    if (!baseProblems) {
      continue;
    }
    stories++;
    const known = new Set(baseProblems.map(problemKey));
    for (const problem of actualProblems) {
      if (known.has(problemKey(problem))) {
        continue;
      }
      problems++;
      const groupKey = `${problem.kind}|${problem.frame ?? ''}|${problem.path}`;
      if (!groups.has(groupKey)) {
        groups.set(groupKey, {
          kind: problem.kind, path: problem.path, frame: problem.frame ?? '', stories: new Set(), examples: [],
        });
      }
      const group = groups.get(groupKey);
      group.stories.add(story);
      if (group.examples.length < 3) {
        group.examples.push({ story, text: problem.text, by: problem.by, ...(problem.other ? { other: problem.other } : {}) });
      }
    }
  }
  const added = [...groups.values()]
    .map((group) => ({ ...group, stories: group.stories.size }))
    .sort((a, b) => b.stories - a.stories || a.kind.localeCompare(b.kind) || a.path.localeCompare(b.path));
  return { stories, problems, added };
}

// Exported for its test, which runs it against a page
export { collectFitInFrame };
