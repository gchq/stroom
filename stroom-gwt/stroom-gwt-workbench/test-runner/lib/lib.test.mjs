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

// Tests for the test runner's helpers: node --test lib/

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { parseCsf } from './csf-parser.mjs';
import { selectStories } from './filter.mjs';
import { escapeXml, toJUnitXml } from './junit.mjs';
import { sanitize, storyId, storyNameFromExport } from './storybook-ids.mjs';

test('storybook ids', () => {
  assert.equal(storyNameFromExport('WithIcons'), 'With Icons');
  assert.equal(storyNameFromExport('S3Config'), 'S 3 Config');
  assert.equal(storyNameFromExport('Alerts_'), 'Alerts');
  assert.equal(storyNameFromExport('XMLEditor'), 'XML Editor');
  assert.equal(sanitize('Widgets/Editors & Viewers/AceEditor'), 'widgets-editors-viewers-aceeditor');
  assert.equal(storyId('Widgets/Buttons/Button', 'WithIcons'), 'widgets-buttons-button--with-icons');
  assert.equal(storyId('App/Editors/CodeDocumentEditor', 'S3Config'), 'app-editors-codedocumenteditor--s-3-config');
  assert.throws(() => storyId('!!!', 'Default'), /Invalid kind/);
});

test('csf parser', () => {
  const source = [
    "const meta = { title: 'Widgets/Dialogs/AlertDialog', parameters: { layout: 'centered' } } satisfies Meta;",
    'export default meta;',
    'type Story = StoryObj<typeof meta>;',
    'export const Alerts_: Story = {',
    "  name: 'Alerts (info / warn / error)',",
    '  args: {',
    "    name: 'not the story name',",
    '  },',
    '  play: async () => {},',
    '};',
    "const data = {\n  name: 'test data, not a story',\n};",
    'export const Empty: Story = {};',
    'export const WithRender: Story = {',
    '  render: () => <div/>,',
    '};',
  ].join('\n');
  const { title, stories } = parseCsf(source, 'x.stories.tsx');
  assert.equal(title, 'Widgets/Dialogs/AlertDialog');
  assert.deepEqual(stories.map((story) => [story.id, story.name, story.hasPlay]), [
    ['widgets-dialogs-alertdialog--alerts', 'Alerts (info / warn / error)', true],
    ['widgets-dialogs-alertdialog--empty', null, false],
    ['widgets-dialogs-alertdialog--with-render', null, false],
  ]);
  assert.throws(() => parseCsf('export default {};', 'f'), /No title/);
});

test('filters', () => {
  const stories = [
    { id: 'widgets-buttons-button--default', title: 'Widgets/Buttons/Button', tags: [] },
    { id: 'widgets-buttons-button--with-icons', title: 'Widgets/Buttons/Button', tags: ['skip-test'] },
    { id: 'widgets-buttonsbar--default', title: 'Widgets/ButtonsBar', tags: ['play-fn'] },
    { id: 'widgets-inputs-tickbox--basic', title: 'Widgets/Inputs/TickBox', tags: [] },
  ];
  const ids = (options) => selectStories(stories, options).map((story) => story.id);
  assert.equal(ids({}).length, 4);
  assert.deepEqual(ids({ include: ['widgets-buttons-'] }),
    ['widgets-buttons-button--default', 'widgets-buttons-button--with-icons']);
  // A title prefix must match whole parts of the title
  assert.deepEqual(ids({ include: ['Widgets/Buttons'] }),
    ['widgets-buttons-button--default', 'widgets-buttons-button--with-icons']);
  assert.deepEqual(ids({ include: ['widgets/*/tickbox'] }), ['widgets-inputs-tickbox--basic']);
  assert.deepEqual(ids({ include: ['*--default'], exclude: ['Widgets/ButtonsBar'] }),
    ['widgets-buttons-button--default']);
  assert.deepEqual(ids({ includeTags: ['play-fn'] }), ['widgets-buttonsbar--default']);
  assert.deepEqual(ids({ excludeTags: ['play-fn', 'skip-test'] }),
    ['widgets-buttons-button--default', 'widgets-inputs-tickbox--basic']);
  assert.deepEqual(selectStories(stories, { skipTags: ['skip-test'] }).map((story) => story.skip),
    [false, true, false, false]);
});

test('junit xml', () => {
  assert.equal(escapeXml('a<b>&"c\'\u0001'), 'a&lt;b&gt;&amp;&quot;c&apos;');
  const xml = toJUnitXml([
    { id: 'a--x', title: 'A', name: 'X', status: 'PASS', durationMs: 1500, url: 'u' },
    { id: 'a--y', title: 'A', name: 'Y', status: 'FAIL', durationMs: 10, url: 'u', failedStep: 'click()',
      error: 'Boom\nmore', pageErrors: [], consoleErrors: [] },
    { id: 'b--z', title: 'B', name: 'Z', status: 'SKIP', durationMs: 0, url: 'u' },
  ]);
  assert.match(xml, /<testsuites name="stroom-gwt-workbench" tests="3" failures="1" errors="0" skipped="1"/);
  assert.match(xml, /<testsuite name="A" tests="2" failures="1" errors="0" skipped="0" time="1.510">/);
  assert.match(xml, /<testcase classname="A" name="X" time="1.500"\/>/);
  assert.match(xml, /<failure message="Boom">Failed step: click\(\)\nBoom\nmore\nURL: u<\/failure>/);
  assert.match(xml, /<testcase classname="B" name="Z" time="0.000"><skipped\/><\/testcase>/);
});
