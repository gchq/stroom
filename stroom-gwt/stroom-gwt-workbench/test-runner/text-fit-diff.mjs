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

// Compares two runs' text that doesn't fit (run.mjs --text-fit) at different Stroom text size
// preferences (--font-size) and lists what only the larger one has: text that no longer fits.

import { readFile, writeFile } from 'node:fs/promises';
import { parseArgs } from 'node:util';

import { compareTextFit } from './lib/text-fit.mjs';

const HELP = `Usage: node text-fit-diff.mjs --baseline <file> --actual <file> [--output <file>]

Lists the text that doesn't fit in the second of two runs (run.mjs --text-fit) but did in the first,
e.g. at the large and medium text size preferences (--font-size): text cut off by a box that hides
its overflow, shortened with an ellipsis, or drawn over another text. Each is grouped by its kind and
its element's path (the element and up to three of its ancestors, with up to three classes each),
the groups seen in the most stories first, with up to three examples.

Options:
  --baseline <file>   The first run's text fit, e.g. at --font-size medium
  --actual <file>     The second run's, e.g. at --font-size large
  --output <file>     Also write the result as JSON

Exit code: 0 if nothing new doesn't fit, 1 if anything does, 2 if the files couldn't be read.
`;

async function main() {
  const { values } = parseArgs({
    options: {
      baseline: { type: 'string' },
      actual: { type: 'string' },
      output: { type: 'string' },
      help: { type: 'boolean', short: 'h', default: false },
    },
  });
  if (values.help || !values.baseline || !values.actual) {
    console.log(HELP);
    return values.help ? 0 : 2;
  }
  const baseline = JSON.parse(await readFile(values.baseline, 'utf8'));
  const actual = JSON.parse(await readFile(values.actual, 'utf8'));
  const result = compareTextFit(baseline, actual);
  console.log(`Text fit: ${baseline.fontSize} → ${actual.fontSize}, ${result.stories} stories compared, `
    + `${result.problems} new problems in ${result.added.length} kinds`);
  for (const group of result.added) {
    console.log(`\n  ${group.kind}  ${group.path}${group.frame ? `  (in ${group.frame})` : ''}  [${group.stories} stories]`);
    for (const example of group.examples) {
      const other = example.other ? `  over "${example.other.text}" (${example.other.path})` : '';
      console.log(`    ${example.by}px  "${example.text}"${other}  ${example.story}`);
    }
  }
  if (values.output) {
    await writeFile(values.output, JSON.stringify({
      baseline: baseline.fontSize, actual: actual.fontSize, ...result,
    }, null, 1) + '\n');
  }
  return result.added.length > 0 ? 1 : 0;
}

main().then((code) => {
  process.exitCode = code;
}, (error) => {
  console.error(error.message);
  process.exitCode = 2;
});
