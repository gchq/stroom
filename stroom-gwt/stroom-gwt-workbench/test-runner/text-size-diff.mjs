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

// Compares two runs' text sizes (run.mjs --text-sizes) at different Stroom text size preferences
// (--font-size) and lists the text whose size didn't follow the preference.

import { readFile, writeFile } from 'node:fs/promises';
import { parseArgs } from 'node:util';

import { compareTextSizes } from './lib/text-sizes.mjs';

const HELP = `Usage: node text-size-diff.mjs --baseline <file> --actual <file> [--min-ratio <n>] [--output <file>]

Lists the text whose size is the same in two runs' text sizes (run.mjs --text-sizes), e.g. at the
medium and large text size preferences (--font-size): text that doesn't follow the preference.
Each is grouped by its element's path (the element and up to three of its ancestors, with up to
three classes each), the groups seen in the most stories first, with up to three examples.

Options:
  --baseline <file>   The first run's text sizes, e.g. at --font-size medium
  --actual <file>     The second run's, e.g. at --font-size large
  --min-ratio <n>     How much a text must grow (or shrink) to count as changed (default: 1.03)
  --output <file>     Also write the result as JSON

Exit code: 0 if every text changed, 1 if any didn't, 2 if the files couldn't be read.
`;

async function main() {
  const { values } = parseArgs({
    options: {
      baseline: { type: 'string' },
      actual: { type: 'string' },
      'min-ratio': { type: 'string', default: '1.03' },
      output: { type: 'string' },
      help: { type: 'boolean', short: 'h', default: false },
    },
  });
  if (values.help || !values.baseline || !values.actual) {
    console.log(HELP);
    return values.help ? 0 : 2;
  }
  const minRatio = Number(values['min-ratio']);
  if (!(minRatio > 1)) {
    throw new Error(`--min-ratio must be a number above 1, not '${values['min-ratio']}'`);
  }
  const baseline = JSON.parse(await readFile(values.baseline, 'utf8'));
  const actual = JSON.parse(await readFile(values.actual, 'utf8'));
  const result = compareTextSizes(baseline, actual, minRatio);
  console.log(`Text sizes: ${baseline.fontSize} → ${actual.fontSize}, ${result.compared} texts compared, `
    + `${result.unchanged.length} kinds of text didn't change`);
  for (const group of result.unchanged) {
    console.log(`\n  ${group.path}${group.frame ? `  (in ${group.frame})` : ''}  [${group.stories} stories]`);
    for (const example of group.examples) {
      console.log(`    ${example.from}px → ${Math.round(example.to * 100) / 100}px  "${example.text}"  ${example.story}`);
    }
  }
  if (values.output) {
    await writeFile(values.output, JSON.stringify({
      baseline: baseline.fontSize, actual: actual.fontSize, minRatio, ...result,
    }, null, 1) + '\n');
  }
  return result.unchanged.length > 0 ? 1 : 0;
}

main().then((code) => {
  process.exitCode = code;
}, (error) => {
  console.error(error.message);
  process.exitCode = 2;
});
