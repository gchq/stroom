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

// Compares a run's story screenshots with the baseline, with ImageMagick's `compare` (see
// lib/screenshot-diff.mjs and the README's 'Screenshots' section). Exits 0 whatever has changed:
// the report is for review.

import { parseArgs } from 'node:util';

import { compareScreenshots, DEFAULT_FUZZ, formatReport, imageMagickComparator }
  from './lib/screenshot-diff.mjs';

const USAGE = `Usage: node screenshot-diff.mjs --baseline <dir> --actual <dir> --output <dir>
    [--fuzz <percent>] [--max-visible-pixels <n>] [--ignore-missing]

  --baseline <dir>            The baseline screenshots, <story id>.png
  --actual <dir>              This run's screenshots (run.mjs --screenshots)
  --output <dir>              Where to write each changed story's expected, actual and diff PNGs,
                              and report.json (emptied first)
  --fuzz <percent>            The colour difference under which a pixel isn't visibly changed
                              (default ${DEFAULT_FUZZ})
  --max-visible-pixels <n>    How many pixels may visibly change before a story counts as changed
                              (default 0)
  --ignore-missing            Don't report baselines with no screenshot (when only some stories
                              were run)

Needs ImageMagick's 'compare' on the PATH.
`;

let values;
try {
  ({ values } = parseArgs({
    options: {
      baseline: { type: 'string' },
      actual: { type: 'string' },
      output: { type: 'string' },
      fuzz: { type: 'string' },
      'max-visible-pixels': { type: 'string' },
      'ignore-missing': { type: 'boolean', default: false },
      help: { type: 'boolean', short: 'h' },
    },
  }));
} catch (error) {
  console.error(`${error.message}\n\n${USAGE}`);
  process.exit(2);
}
if (values.help) {
  console.log(USAGE);
  process.exit(0);
}
for (const required of ['baseline', 'actual', 'output']) {
  if (!values[required]) {
    console.error(`--${required} is required\n\n${USAGE}`);
    process.exit(2);
  }
}
const fuzz = values.fuzz ?? DEFAULT_FUZZ;
const maxVisiblePixels = values['max-visible-pixels'] === undefined ? 0 : Number(values['max-visible-pixels']);
if (!/^\d+(\.\d+)?%$/.test(fuzz) || !(Number.isInteger(maxVisiblePixels) && maxVisiblePixels >= 0)) {
  console.error(`--fuzz must be a percentage (e.g. 1%) and --max-visible-pixels a whole number\n\n${USAGE}`);
  process.exit(2);
}

try {
  const result = await compareScreenshots({
    baselineDir: values.baseline,
    actualDir: values.actual,
    outputDir: values.output,
    compare: imageMagickComparator({ fuzz }),
    maxVisiblePixels,
    ignoreMissing: values['ignore-missing'],
  });
  process.stdout.write(formatReport(result, values.output));
} catch (error) {
  console.error(error.message);
  process.exit(1);
}
