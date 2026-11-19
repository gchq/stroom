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

// The class census (--class-census <file>): every class name the stories put on an element, at any
// time while they run (a popup opened and closed again included), and the stories that put it
// there. It tells which stylesheet rules can still match something, e.g. GWT's own classes.

import { writeFile } from 'node:fs/promises';

// Records, in every frame, each class name an element has when the page loads or gets later (an
// element added, or its class changed), in window.__workbenchClassCensus
export const CLASS_CENSUS_SCRIPT = `(() => {
  const seen = new Set();
  Object.defineProperty(window, '__workbenchClassCensus', { value: seen });
  const note = (element) => {
    if (element.classList) {
      for (const name of element.classList) {
        seen.add(name);
      }
    }
  };
  const noteTree = (node) => {
    if (node.nodeType !== 1) {
      return;
    }
    note(node);
    for (const element of node.querySelectorAll('[class]')) {
      note(element);
    }
  };
  const observer = new MutationObserver((records) => {
    for (const record of records) {
      if (record.type === 'attributes') {
        note(record.target);
      } else {
        record.addedNodes.forEach(noteTree);
      }
    }
  });
  const start = () => {
    noteTree(document.documentElement);
    observer.observe(document.documentElement, {
      subtree: true, childList: true, attributes: true, attributeFilter: ['class'],
    });
  };
  if (document.documentElement) {
    start();
  } else {
    document.addEventListener('readystatechange', start, { once: true });
  }
})();`;

// The class names the census script has seen in the page's frames, or none if it can't be read
export async function collectClasses(page) {
  const names = new Set();
  for (const frame of page.frames()) {
    const seen = await frame.evaluate(() => [...(window.__workbenchClassCensus ?? [])]).catch(() => []);
    seen.forEach((name) => names.add(name));
  }
  return names;
}

// Adds the story's class names to the census (class name -> the ids of the stories that set it)
export function addToCensus(census, storyId, names) {
  for (const name of names) {
    if (!census.has(name)) {
      census.set(name, new Set());
    }
    census.get(name).add(storyId);
  }
}

// The census as JSON: each class name, sorted, with how many stories set it and the first few
export function censusJson(census) {
  const classes = {};
  for (const name of [...census.keys()].sort()) {
    const stories = [...census.get(name)].sort();
    classes[name] = { stories: stories.length, examples: stories.slice(0, 3) };
  }
  return JSON.stringify({ classes }, null, 2) + '\n';
}

// Writes the census to the file
export async function writeCensus(file, census) {
  await writeFile(file, censusJson(census));
}
