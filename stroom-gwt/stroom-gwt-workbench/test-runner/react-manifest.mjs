#!/usr/bin/env node
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

// Generates the manifest of every story in the React Storybook (stroom-ui-react), which the
// workbench's coverage test (TestReactStoryCoverage) checks the GWT stories against.
//
// The stories are read from the first of these that is available, in order of preference:
//
//   1. The running React Storybook's index, http://localhost:6006/index.json (--index-url)
//   2. The built React Storybook's index, <react dir>/storybook-static/index.json
//   3. Parsing <react dir>/src/**/*.stories.tsx with Storybook's id rules
//
// Whichever is used, the *.stories.tsx files are also parsed (if present) and any differences
// reported, so the parser stays trustworthy for when no index is available.
//
// Usage: node react-manifest.mjs [options]   (see --help)

import { readFile, readdir, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

import { parseCsf } from './lib/csf-parser.mjs';
import { storyNameFromExport } from './lib/storybook-ids.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(HERE, '../../..');
const DEFAULT_OUT = path.resolve(HERE, '../src/test/resources/react-stories.json');
const DEFAULT_REACT_DIR = process.env.STROOM_UI_REACT_DIR ?? path.resolve(REPO_ROOT, '../stroom-ui-react');
const DEFAULT_INDEX_URL = 'http://localhost:6006/index.json';

const HELP = `Usage: node react-manifest.mjs [options]

Writes the manifest of every React story to ${path.relative(process.cwd(), DEFAULT_OUT)}

Options:
  --react-dir <dir>    The stroom-ui-react checkout (default: $STROOM_UI_REACT_DIR or
                       ${DEFAULT_REACT_DIR})
  --index-url <url>    A running React Storybook's index.json (default: ${DEFAULT_INDEX_URL})
  --source <source>    auto (default), url, static or parse - where to read the stories from
  --out <file>         Where to write the manifest
  --check              Don't write anything; exit 1 if the manifest is out of date
  -h, --help           Show this help
`;

async function fetchIndex(url) {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(5000) });
    if (!response.ok) {
      return null;
    }
    return await response.json();
  } catch {
    return null;
  }
}

async function readJson(file) {
  return JSON.parse(await readFile(file, 'utf8'));
}

async function findStoryFiles(dir) {
  const files = [];
  async function walk(current) {
    const entries = await readdir(current, { withFileTypes: true });
    for (const entry of entries) {
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) {
        if (entry.name !== 'node_modules') {
          await walk(full);
        }
      } else if (/\.stories\.tsx?$/.test(entry.name)) {
        files.push(full);
      }
    }
  }
  await walk(dir);
  return files.sort();
}

// Parses every story file, returning the stories in file then export order.
async function parseStories(reactDir) {
  const srcDir = path.join(reactDir, 'src');
  if (!existsSync(srcDir)) {
    return null;
  }
  const stories = [];
  for (const file of await findStoryFiles(srcDir)) {
    const relative = './' + path.relative(reactDir, file).split(path.sep).join('/');
    const { stories: fileStories } = parseCsf(await readFile(file, 'utf8'), relative);
    for (const story of fileStories) {
      stories.push({ ...story, file: relative });
    }
  }
  return stories;
}

// Converts a Storybook index.json (v5) into manifest entries. The index is in file order.
function fromIndex(index) {
  return Object.values(index.entries)
    .filter((entry) => entry.type === 'story')
    .map((entry) => ({
      id: entry.id,
      title: entry.title,
      name: entry.name,
      exportName: entry.exportName,
      file: entry.importPath,
      hasPlay: (entry.tags ?? []).includes('play-fn'),
    }));
}

// Puts stories (in file order) in the sidebar order: grouped by title, in the order each title (and each
// of its parent groups) first appears, as Storybook's sidebar does.
function sidebarOrder(stories) {
  const groupOrder = new Map();
  for (const story of stories) {
    const parts = story.title.split('/');
    for (let i = 1; i <= parts.length; i++) {
      const group = parts.slice(0, i).join('/');
      if (!groupOrder.has(group)) {
        groupOrder.set(group, groupOrder.size);
      }
    }
  }
  const key = (story) => {
    const parts = story.title.split('/');
    return parts.map((_, i) => groupOrder.get(parts.slice(0, i + 1).join('/')));
  };
  return stories
    .map((story, i) => ({ story, i, key: key(story) }))
    .sort((a, b) => {
      for (let j = 0; j < Math.min(a.key.length, b.key.length); j++) {
        if (a.key[j] !== b.key[j]) {
          return a.key[j] - b.key[j];
        }
      }
      return a.key.length - b.key.length || a.i - b.i;
    })
    .map(({ story }) => story);
}

// Reports differences between the stories in an index and the parsed ones.
function crossCheck(indexed, parsed) {
  const problems = [];
  const parsedById = new Map(parsed.map((story) => [story.id, story]));
  const indexedIds = new Set(indexed.map((story) => story.id));
  for (const story of indexed) {
    const other = parsedById.get(story.id);
    if (!other) {
      problems.push(`In the index but not found by parsing: ${story.id} (${story.file})`);
    } else {
      if ((other.name ?? story.name) !== story.name) {
        problems.push(`Name differs for ${story.id}: index '${story.name}', parsed '${other.name}'`);
      }
      if (other.hasPlay !== story.hasPlay) {
        problems.push(`hasPlay differs for ${story.id}: index ${story.hasPlay}, parsed ${other.hasPlay}`);
      }
    }
  }
  for (const story of parsed) {
    if (!indexedIds.has(story.id)) {
      problems.push(`Found by parsing but not in the index: ${story.id} (${story.file})`);
    }
  }
  return problems;
}

// One story per line, so that changes to the manifest are easy to review
function toManifestJson(source, stories) {
  const lines = stories.map((story) => '    ' + JSON.stringify(story));
  return '{\n'
    + '  "_comment": "Every story in the React Storybook (stroom-ui-react). Generated by '
    + 'stroom-gwt-workbench/test-runner/react-manifest.mjs - do not edit by hand.",\n'
    + `  "source": ${JSON.stringify(source)},\n`
    + `  "storyCount": ${stories.length},\n`
    + `  "playCount": ${stories.filter((story) => story.hasPlay).length},\n`
    + '  "stories": [\n'
    + lines.join(',\n') + '\n'
    + '  ]\n'
    + '}\n';
}

async function main() {
  const { values } = parseArgs({
    options: {
      'react-dir': { type: 'string', default: DEFAULT_REACT_DIR },
      'index-url': { type: 'string', default: DEFAULT_INDEX_URL },
      source: { type: 'string', default: 'auto' },
      out: { type: 'string', default: DEFAULT_OUT },
      check: { type: 'boolean', default: false },
      help: { type: 'boolean', short: 'h', default: false },
    },
  });
  if (values.help) {
    process.stdout.write(HELP);
    return 0;
  }
  const reactDir = path.resolve(values['react-dir']);
  const staticIndex = path.join(reactDir, 'storybook-static', 'index.json');
  const source = values.source;
  if (!['auto', 'url', 'static', 'parse'].includes(source)) {
    console.error(`Unknown --source '${source}'\n\n${HELP}`);
    return 2;
  }

  let stories = null;
  let sourceName = null;
  if (source === 'auto' || source === 'url') {
    const index = await fetchIndex(values['index-url']);
    if (index) {
      stories = sidebarOrder(fromIndex(index));
      sourceName = 'React Storybook index.json (running Storybook)';
    } else if (source === 'url') {
      console.error(`Unable to read ${values['index-url']}. Is the React Storybook running?`);
      return 2;
    }
  }
  if (!stories && (source === 'auto' || source === 'static')) {
    if (existsSync(staticIndex)) {
      stories = sidebarOrder(fromIndex(await readJson(staticIndex)));
      sourceName = 'React Storybook index.json (storybook-static)';
    } else if (source === 'static') {
      console.error(`${staticIndex} doesn't exist. Run 'npm run build-storybook' in ${reactDir}`);
      return 2;
    }
  }

  const parsed = await parseStories(reactDir);
  if (!stories) {
    if (!parsed) {
      console.error(`No React Storybook index is available and ${reactDir}/src doesn't exist. `
        + 'Use --react-dir or --index-url.');
      return 2;
    }
    stories = sidebarOrder(parsed).map((story) => ({
      id: story.id,
      title: story.title,
      // Storybook derives the name from the export name unless the story sets one
      name: story.name ?? storyNameFromExport(story.exportName),
      exportName: story.exportName,
      file: story.file,
      hasPlay: story.hasPlay,
    }));
    sourceName = 'Parsed from the React *.stories.tsx files';
  } else if (parsed) {
    const problems = crossCheck(stories, parsed);
    if (problems.length > 0) {
      console.warn(`The *.stories.tsx parser disagrees with the index in ${problems.length} place(s); `
        + 'the index has been used:');
      problems.slice(0, 50).forEach((problem) => console.warn(`  ${problem}`));
    } else {
      console.log(`Cross-checked ${parsed.length} parsed stories against the index: no differences.`);
    }
  }

  const json = toManifestJson(sourceName, stories);
  const out = path.resolve(values.out);
  if (values.check) {
    // Only the stories matter, not where they were read from
    const existing = existsSync(out) ? JSON.parse(await readFile(out, 'utf8')).stories : [];
    if (JSON.stringify(existing) !== JSON.stringify(stories)) {
      console.error(`${out} is out of date. Run: node ${path.relative(process.cwd(), fileURLToPath(import.meta.url))}`);
      return 1;
    }
    console.log(`${out} is up to date (${stories.length} stories).`);
    return 0;
  }
  await writeFile(out, json);
  const playCount = stories.filter((story) => story.hasPlay).length;
  console.log(`Wrote ${stories.length} React stories (${playCount} with play functions) to ${out}`);
  console.log(`Source: ${sourceName}`);
  return 0;
}

process.exitCode = await main();
