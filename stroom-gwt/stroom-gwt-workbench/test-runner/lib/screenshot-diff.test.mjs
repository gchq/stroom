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

// Tests for the screenshot comparison: node --test lib/

import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdir, mkdtemp, readdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { test } from 'node:test';

import {
  compareScreenshots, formatReport, imageMagickComparator, listScreenshots, parseAeCount, readPngSize,
} from './screenshot-diff.mjs';

// Whether ImageMagick's compare (and magick, to make test images) are installed
const hasImageMagick = (() => {
  try {
    execFileSync('magick', ['-version'], { stdio: 'ignore' });
    execFileSync('compare', ['-version'], { stdio: 'ignore' });
    return true;
  } catch {
    return false;
  }
})();

async function withTempDir(body) {
  const dir = await mkdtemp(path.join(tmpdir(), 'screenshot-diff-'));
  try {
    await body(dir);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}

async function writeFiles(dir, files) {
  await mkdir(dir, { recursive: true });
  for (const [name, content] of Object.entries(files)) {
    await writeFile(path.join(dir, name), content);
  }
}

test('parseAeCount', () => {
  assert.equal(parseAeCount('1234'), 1234);
  assert.equal(parseAeCount('1234 (0.0123)\n'), 1234);
  assert.equal(parseAeCount('0'), 0);
  assert.equal(parseAeCount('compare: image widths or heights differ'), null);
  assert.equal(parseAeCount(''), null);
  assert.equal(parseAeCount(undefined), null);
});

test('readPngSize', async () => {
  await withTempDir(async (dir) => {
    // A PNG's signature and IHDR chunk: 300 x 20
    const header = Buffer.alloc(24);
    header.writeUInt32BE(0x89504e47, 0);
    header.writeUInt32BE(0x0d0a1a0a, 4);
    header.writeUInt32BE(13, 8);
    header.write('IHDR', 12, 'ascii');
    header.writeUInt32BE(300, 16);
    header.writeUInt32BE(20, 20);
    await writeFiles(dir, { 'a.png': header, 'b.png': 'not a png at all, but long enough' });
    assert.deepEqual(await readPngSize(path.join(dir, 'a.png')), { width: 300, height: 20 });
    assert.equal(await readPngSize(path.join(dir, 'b.png')), null);
  });
});

test('listScreenshots: story screenshots only, and none for a missing directory', async () => {
  await withTempDir(async (dir) => {
    await writeFiles(dir, {
      'b--two.png': 'b',
      'a--one.png': 'a',
      'a--one.attempt-1.png': 'retry',
      'report.json': '{}',
    });
    assert.deepEqual([...(await listScreenshots(dir)).entries()], [
      ['a--one', path.join(dir, 'a--one.png')],
      ['b--two', path.join(dir, 'b--two.png')],
    ]);
    assert.equal((await listScreenshots(path.join(dir, 'missing'))).size, 0);
  });
});

test('compareScreenshots: unchanged, changed, new and missing stories', async () => {
  await withTempDir(async (dir) => {
    const baselineDir = path.join(dir, 'baseline');
    const actualDir = path.join(dir, 'actual');
    const outputDir = path.join(dir, 'out');
    await writeFiles(baselineDir, {
      'same.png': 'S', 'small.png': 'A', 'big.png': 'B', 'resized.png': 'R', 'gone.png': 'G',
    });
    await writeFiles(actualDir, {
      'same.png': 'S', 'small.png': 'a', 'big.png': 'b', 'resized.png': 'r', 'new.png': 'N',
    });
    await writeFiles(outputDir, { 'stale.png': 'old' });
    // A fake comparator: the visible count is decided by the story
    const counts = { same: [0, 0], small: [5, 40], big: [900, 1200] };
    const compare = async ({ actualFile, diffFile }) => {
      const id = path.basename(actualFile, '.png');
      await writeFile(diffFile, `diff ${id}`);
      if (id === 'resized') {
        return { error: 'image widths or heights differ' };
      }
      const [visible, raw] = counts[id];
      return { visible, raw };
    };

    const result = await compareScreenshots({ baselineDir, actualDir, outputDir, compare, maxVisiblePixels: 10 });

    assert.deepEqual(result.unchanged, ['same', 'small']);
    assert.deepEqual(result.changed, [
      { id: 'resized', error: 'image widths or heights differ' },
      { id: 'big', visible: 900, raw: 1200 },
    ]);
    assert.deepEqual(result.added, ['new']);
    assert.deepEqual(result.missing, ['gone']);
    // Only the changed stories' images are kept, and the output was emptied first
    assert.deepEqual((await readdir(outputDir)).sort(), [
      'big.actual.png', 'big.diff.png', 'big.expected.png',
      'report.json',
      'resized.actual.png', 'resized.diff.png', 'resized.expected.png',
    ]);
    assert.equal(String(await readFile(path.join(outputDir, 'big.expected.png'))), 'B');
    assert.equal(String(await readFile(path.join(outputDir, 'big.actual.png'))), 'b');
    assert.deepEqual(JSON.parse(String(await readFile(path.join(outputDir, 'report.json')))), result);

    const report = formatReport(result, outputDir);
    assert.match(report, /^Screenshots: 2 unchanged, 2 changed, 1 new \(no baseline\), 1 missing \(baseline only\)/);
    assert.match(report, /\n {2}big: 900 visible, 1200 raw\n/);
    assert.match(formatReport({ ...result, changed: [{ id: 'x', visible: 2, raw: 2.00392 }] }, outputDir),
      /\n {2}x: 2 visible, 2 raw\n/);
    assert.match(report, /\n {2}resized: image widths or heights differ\n/);
    assert.match(report, /New \(no baseline\):\n {2}new\n/);
    assert.match(report, /Missing \(baseline only\):\n {2}gone\n/);

    // When only some stories were run, those that weren't aren't missing
    const someRun = await compareScreenshots({
      baselineDir, actualDir, outputDir, compare, maxVisiblePixels: 10, ignoreMissing: true,
    });
    assert.deepEqual(someRun.missing, []);
  });
});

test('imageMagickComparator: counts raw and visible differences', { skip: !hasImageMagick && 'ImageMagick isn\'t installed' },
  async () => {
    await withTempDir(async (dir) => {
      const file = (name) => path.join(dir, name);
      const magick = (...args) => execFileSync('magick', args);
      magick('-size', '10x10', 'xc:white', file('white.png'));
      // Two pixels a person could see, and one that differs by under the 1% fuzz
      magick(file('white.png'), '-fill', 'black', '-draw', 'point 1,1', '-draw', 'point 2,2',
        '-fill', '#FEFEFE', '-draw', 'point 5,5', file('changed.png'));
      magick('-size', '12x10', 'xc:white', file('wider.png'));
      const compare = imageMagickComparator();

      assert.deepEqual(
        await compare({ actualFile: file('white.png'), baselineFile: file('white.png'), diffFile: file('d1.png') }),
        { raw: 0, visible: 0 });
      // The near-white pixel counts in raw (as a fraction of a pixel), but isn't visible
      const changed = await compare({
        actualFile: file('changed.png'), baselineFile: file('white.png'), diffFile: file('d2.png'),
      });
      assert.equal(changed.visible, 2);
      assert.ok(changed.raw > 2 && changed.raw <= 3, `raw ${changed.raw}`);
      // The raw differences are drawn
      assert.ok((await readFile(file('d2.png'))).length > 0);
      const resized = await compare({
        actualFile: file('wider.png'), baselineFile: file('white.png'), diffFile: file('d3.png'),
      });
      // ImageMagick would compare only where they overlap (all white, so 'no difference')
      assert.deepEqual(resized, { error: 'the size changed from 10x10 to 12x10' });
      assert.deepEqual(
        await compare({ actualFile: file('d1.png'), baselineFile: path.join(dir, 'missing.png'), diffFile: file('d4.png') })
          .catch((error) => ({ thrown: error.code })),
        { thrown: 'ENOENT' });
    });
  });
