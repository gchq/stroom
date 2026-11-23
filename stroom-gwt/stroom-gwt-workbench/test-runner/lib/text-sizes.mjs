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

// Text size checks.
//
// --font-size <small|medium|large> shows the stories at a Stroom text size preference: the page's
// stroom-font-size-* class, as ThemeCssUtil sets it in Stroom (the workbench's page has
// stroom-font-size-medium).
//
// --text-sizes <file> records, for each story that passes, every piece of text it shows (an
// element with text of its own, HTML or SVG, in any frame) and its computed font size and line
// height, as JSON. Two runs at different text sizes are compared with text-size-diff.mjs, which
// lists the text whose size didn't follow the preference.

import { writeFile } from 'node:fs/promises';

export const FONT_SIZES = ['small', 'medium', 'large'];

// The most texts recorded for one story, so that a story with a huge list can't make the file huge
export const MAX_TEXTS_PER_STORY = 3000;

// The font size preference given with --font-size, or null for the page's own
export function fontSize(text) {
  if (text === undefined) {
    return null;
  }
  if (FONT_SIZES.includes(text)) {
    return text;
  }
  throw new Error(`--font-size must be one of ${FONT_SIZES.join(', ')}, not '${text}'`);
}

// A script for every frame that gives the page the stroom-font-size-<size> class in place of any
// other, and keeps it so if something sets the page's classes again
export function fontSizeScript(size) {
  return `(() => {
  const wanted = 'stroom-font-size-${size}';
  const apply = () => {
    const html = document.documentElement;
    if (!html) {
      return;
    }
    // Only switch a class Stroom gave the document: a frame without one (a visualisation's)
    // doesn't follow the preference in the app, so it mustn't here either
    const others = [...html.classList].filter((name) => name.startsWith('stroom-font-size-') && name !== wanted);
    if (others.length > 0) {
      html.classList.remove(...others);
      html.classList.add(wanted);
    }
  };
  // The document's element may not exist yet, so watch the document for it and for its classes
  new MutationObserver(apply).observe(document, {
    childList: true, subtree: true, attributes: true, attributeFilter: ['class'],
  });
  apply();
})();`;
}

// Run in a frame: every visible element with text of its own, as
// {path, text, px, lineHeight, count}, identical ones (same path, text and sizes) counted once
function collectInFrame(max) {
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
  const ownText = (element) => {
    let text = '';
    for (const node of element.childNodes) {
      if (node.nodeType === 3) {
        text += node.nodeValue;
      }
    }
    return text.replace(/\s+/g, ' ').trim();
  };
  const visible = (element, style) => {
    if (style.visibility === 'hidden' || style.display === 'none' || Number(style.opacity) === 0) {
      return false;
    }
    const rect = element.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  };
  const found = new Map();
  const walker = document.createTreeWalker(document.body ?? document.documentElement, NodeFilter.SHOW_ELEMENT);
  for (let element = walker.currentNode; element && found.size < max; element = walker.nextNode()) {
    if (['script', 'style', 'noscript', 'template'].includes(element.localName)) {
      continue;
    }
    const text = ownText(element);
    if (!text) {
      continue;
    }
    const style = getComputedStyle(element);
    if (!visible(element, style)) {
      continue;
    }
    const item = {
      path: pathOf(element),
      text: text.slice(0, 40),
      px: Math.round(parseFloat(style.fontSize) * 100) / 100,
      lineHeight: style.lineHeight,
    };
    const key = `${item.path}|${item.text}|${item.px}|${item.lineHeight}`;
    const seen = found.get(key);
    if (seen) {
      seen.count++;
    } else {
      found.set(key, { ...item, count: 1 });
    }
  }
  return [...found.values()];
}

// Every text the page's frames show (see collectInFrame), each with the frame it is in: '' for
// the page, otherwise the frame's path, e.g. /ui/vis.html
export async function collectTextSizes(page) {
  const texts = [];
  for (const frame of page.frames()) {
    const remaining = MAX_TEXTS_PER_STORY - texts.length;
    if (remaining <= 0) {
      break;
    }
    const found = await frame.evaluate(collectInFrame, remaining).catch(() => []);
    const name = frame === page.mainFrame() ? '' : frameName(frame.url());
    found.forEach((text) => texts.push(name ? { ...text, frame: name } : text));
  }
  return texts;
}

function frameName(url) {
  try {
    return new URL(url).pathname;
  } catch {
    return url;
  }
}

// Writes the texts of each story (story id -> texts) to the file, with the font size they were
// shown at
export async function writeTextSizes(file, size, textsByStory) {
  const stories = {};
  for (const id of [...textsByStory.keys()].sort()) {
    stories[id] = textsByStory.get(id);
  }
  await writeFile(file, JSON.stringify({ fontSize: size ?? 'page', stories }, null, 1) + '\n');
}

// The identity of a text within a story, the same at any text size
function textKey(text) {
  return `${text.frame ?? ''}|${text.path}|${text.text}`;
}

// Compares two runs (as written by writeTextSizes) at different text sizes: the texts, in the
// stories both ran, whose size grew by less than minRatio (or shrank by less, from a larger to a
// smaller size). A text is matched by its frame, path and text; one that appears several times
// at different sizes is matched to the closest size. Returns
// {compared, unchanged: [{path, frame, examples: [{story, text, from, to}], stories}]}, grouped by
// path and frame, the groups seen in the most stories first.
export function compareTextSizes(baseline, actual, minRatio = 1.03) {
  const groups = new Map();
  let compared = 0;
  for (const [story, baseTexts] of Object.entries(baseline.stories ?? {})) {
    const actualTexts = actual.stories?.[story];
    if (!actualTexts) {
      continue;
    }
    const byKey = new Map();
    for (const text of actualTexts) {
      const key = textKey(text);
      if (!byKey.has(key)) {
        byKey.set(key, []);
      }
      byKey.get(key).push(text);
    }
    for (const base of baseTexts) {
      const candidates = byKey.get(textKey(base));
      if (!candidates || base.px <= 0) {
        continue;
      }
      compared++;
      // The size grew as expected if any of its matches did
      const ratios = candidates.map((text) => text.px / base.px);
      const best = ratios.reduce((a, b) => (Math.abs(Math.log(b)) > Math.abs(Math.log(a)) ? b : a));
      if (Math.abs(Math.log(best)) >= Math.log(minRatio)) {
        continue;
      }
      const groupKey = `${base.frame ?? ''}|${base.path}`;
      if (!groups.has(groupKey)) {
        groups.set(groupKey, { path: base.path, frame: base.frame ?? '', stories: new Set(), examples: [] });
      }
      const group = groups.get(groupKey);
      group.stories.add(story);
      if (group.examples.length < 3) {
        group.examples.push({ story, text: base.text, from: base.px, to: base.px * best });
      }
    }
  }
  const unchanged = [...groups.values()]
    .map((group) => ({ ...group, stories: group.stories.size }))
    .sort((a, b) => b.stories - a.stories || a.path.localeCompare(b.path));
  return { compared, unchanged };
}
