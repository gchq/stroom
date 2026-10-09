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

// Compares the story screenshots of a run (run.mjs --screenshots) with a baseline of them, so that
// a change to Stroom's widgets can be reviewed for its effect on every story's appearance.
//
// Each pair is compared with ImageMagick's `compare -metric AE`, measuring the difference twice:
// `raw` counts any difference, including sub-level anti-aliasing (ImageMagick 7 counts a pixel
// that differs by less than a level as a fraction of one, so it may not be a whole number), and
// `visible` counts the pixels that differ by more than the fuzz (1%), so what a person could see. A story has changed if its visible count
// is over the allowance.

import { execFile } from 'node:child_process';
import { copyFile, mkdir, open, readdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { promisify } from 'node:util';

// The colour difference under which a pixel doesn't count as visibly changed
export const DEFAULT_FUZZ = '1%';
// How many screenshots are compared at once
const CONCURRENCY = 4;

// A screenshot of a story, <id>.png. Retried stories also have <id>.attempt-<n>.png, which aren't
// compared.
const SCREENSHOT = /^(.+)\.png$/;
const ATTEMPT = /\.attempt-\d+\.png$/;

const run = promisify(execFile);

// The differing pixel count from `compare -metric AE`'s output (stderr), e.g. "1234" or
// "1234 (0.0123)", or null if there is none (e.g. the images' sizes differ).
export function parseAeCount(output) {
  const first = String(output ?? '').trim().split(/\s+/)[0];
  return first !== '' && Number.isFinite(Number(first)) ? Number(first) : null;
}

// A PNG's size, { width, height }, from its header, or null if it isn't a PNG.
export async function readPngSize(file) {
  const handle = await open(file, 'r');
  try {
    const header = Buffer.alloc(24);
    const { bytesRead } = await handle.read(header, 0, 24, 0);
    // The signature, then the IHDR chunk, whose data starts with the width and height
    if (bytesRead < 24 || header.readUInt32BE(0) !== 0x89504e47 || header.toString('ascii', 12, 16) !== 'IHDR') {
      return null;
    }
    return { width: header.readUInt32BE(16), height: header.readUInt32BE(20) };
  } finally {
    await handle.close();
  }
}

// ImageMagick's comparator: compare({ actualFile, baselineFile, diffFile }) resolves to
// { raw, visible }, the differing pixel counts, or { error } if the images can't be compared (e.g.
// their sizes differ: ImageMagick 7 would compare only where they overlap). It writes the raw
// differences to diffFile. `compare` exits 1 when the images differ, which is the normal case here,
// so its count is read rather than its failure thrown.
export function imageMagickComparator({ command = 'compare', fuzz = DEFAULT_FUZZ } = {}) {
  const count = async (args) => {
    try {
      const { stderr } = await run(command, args);
      return { count: parseAeCount(stderr) };
    } catch (error) {
      if (error.code === 'ENOENT') {
        throw new Error(`ImageMagick's '${command}' isn't installed (or isn't on the PATH); `
          + 'install ImageMagick to compare screenshots');
      }
      const value = parseAeCount(error.stderr);
      return value === null ? { error: String(error.stderr ?? error.message).trim() } : { count: value };
    }
  };
  return async ({ actualFile, baselineFile, diffFile }) => {
    const [actualSize, baselineSize] = await Promise.all([readPngSize(actualFile), readPngSize(baselineFile)]);
    if (!actualSize || !baselineSize) {
      return { error: 'not a PNG' };
    }
    if (actualSize.width !== baselineSize.width || actualSize.height !== baselineSize.height) {
      return {
        error: `the size changed from ${baselineSize.width}x${baselineSize.height} `
          + `to ${actualSize.width}x${actualSize.height}`,
      };
    }
    const raw = await count(['-metric', 'AE', actualFile, baselineFile, diffFile]);
    if (raw.error) {
      return { error: raw.error };
    }
    const visible = await count(['-metric', 'AE', '-fuzz', fuzz, actualFile, baselineFile, 'null:']);
    return visible.error ? { error: visible.error } : { raw: raw.count, visible: visible.count };
  };
}

// The story ids of the screenshots in dir, mapped to their files, or an empty map if dir doesn't
// exist.
export async function listScreenshots(dir) {
  let names;
  try {
    names = await readdir(dir);
  } catch (error) {
    if (error.code === 'ENOENT') {
      return new Map();
    }
    throw error;
  }
  const screenshots = new Map();
  for (const name of names.sort()) {
    const match = SCREENSHOT.exec(name);
    if (match && !ATTEMPT.test(name)) {
      screenshots.set(match[1], path.join(dir, name));
    }
  }
  return screenshots;
}

// Compares the screenshots in actualDir with those in baselineDir. For each story whose screenshot
// has changed, leaves <id>.expected.png (the baseline), <id>.actual.png and <id>.diff.png (the raw
// differences) in outputDir, which is emptied first. compare is a comparator as returned by
// imageMagickComparator(). A story has changed if more than maxVisiblePixels of its pixels visibly
// differ, or the images can't be compared. With ignoreMissing (when only some stories were run), a
// baseline with no screenshot isn't reported as missing. Returns { unchanged, changed: [{ id, raw, visible,
// error }], added, missing }, each in story id order (changed most visible first): added stories
// have a screenshot but no baseline, missing ones the reverse. Also writes it to report.json.
export async function compareScreenshots({ baselineDir, actualDir, outputDir, compare, maxVisiblePixels = 0,
  ignoreMissing = false }) {
  const baseline = await listScreenshots(baselineDir);
  const actual = await listScreenshots(actualDir);
  await rm(outputDir, { recursive: true, force: true });
  await mkdir(outputDir, { recursive: true });

  const result = { unchanged: [], changed: [], added: [], missing: [] };
  const pairs = [];
  for (const [id, actualFile] of actual) {
    const baselineFile = baseline.get(id);
    if (baselineFile) {
      pairs.push({ id, actualFile, baselineFile });
    } else {
      result.added.push(id);
    }
  }
  for (const id of baseline.keys()) {
    if (!ignoreMissing && !actual.has(id)) {
      result.missing.push(id);
    }
  }

  const compared = new Map();
  let next = 0;
  const worker = async () => {
    while (next < pairs.length) {
      const pair = pairs[next];
      next += 1;
      const diffFile = path.join(outputDir, `${pair.id}.diff.png`);
      const comparison = await compare({ ...pair, diffFile });
      // ImageMagick may count a pixel that only just differs as a fraction of one, which isn't a
      // pixel a person could see
      const changed = comparison.error !== undefined || Math.floor(comparison.visible) > maxVisiblePixels;
      if (changed) {
        await copyFile(pair.baselineFile, path.join(outputDir, `${pair.id}.expected.png`));
        await copyFile(pair.actualFile, path.join(outputDir, `${pair.id}.actual.png`));
      } else {
        await rm(diffFile, { force: true });
      }
      compared.set(pair.id, { changed, comparison });
    }
  };
  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, pairs.length) }, worker));

  for (const { id } of pairs) {
    const { changed, comparison } = compared.get(id);
    if (changed) {
      result.changed.push({ id, ...comparison });
    } else {
      result.unchanged.push(id);
    }
  }
  result.changed.sort((a, b) => (b.visible ?? Infinity) - (a.visible ?? Infinity) || a.id.localeCompare(b.id));
  await writeFile(path.join(outputDir, 'report.json'), `${JSON.stringify(result, null, 2)}\n`);
  return result;
}

// The comparison as text, for the console.
export function formatReport(result, outputDir) {
  const lines = [`Screenshots: ${result.unchanged.length} unchanged, ${result.changed.length} changed, `
    + `${result.added.length} new (no baseline), ${result.missing.length} missing (baseline only)`];
  if (result.changed.length > 0) {
    lines.push(`Changed (differing pixels: visible = over the fuzz, raw = any):`);
    for (const { id, visible, raw, error } of result.changed) {
      lines.push(error === undefined
        ? `  ${id}: ${visible} visible, ${Number(raw.toFixed(2))} raw`
        : `  ${id}: ${error}`);
    }
    lines.push(`Their baselines, screenshots and differences are in ${outputDir}`);
  }
  for (const [heading, ids] of [['New (no baseline)', result.added], ['Missing (baseline only)', result.missing]]) {
    if (ids.length > 0) {
      lines.push(`${heading}:`);
      ids.forEach((id) => lines.push(`  ${id}`));
    }
  }
  return `${lines.join('\n')}\n`;
}
