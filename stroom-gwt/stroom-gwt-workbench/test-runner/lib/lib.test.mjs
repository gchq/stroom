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

import path from 'node:path';

import { SharedBrowser } from './browsers.mjs';
import { parseCsf } from './csf-parser.mjs';
import { TIMED_OUT, withDeadline } from './deadline.mjs';
import { selectStories } from './filter.mjs';
import { escapeXml, toJUnitXml } from './junit.mjs';
import { growthPerCycle, judgeLeak, withLeakProbe } from './leak-check.mjs';
import { decideOutcome, runWithRetries } from './outcome.mjs';
import { safeFileBase, screenshotFile } from './screenshots.mjs';
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
  assert.throws(() => parseCsf('export const A = {};', 'f'), /No meta/);
});

test('csf parser: inline default export and auto-titles', () => {
  const inline = [
    'export default {',
    "  title: 'Widgets/Glass',",
    '  component: Glass,',
    '} satisfies Meta<typeof Glass>;',
    'export const Default = {};',
  ].join('\n');
  assert.deepEqual(parseCsf(inline, 'f').stories.map((story) => story.id), ['widgets-glass--default']);
  // Storybook would derive the title from the file's path, which depends on its configuration
  assert.throws(() => parseCsf('export default { component: Glass };\nexport const A = {};', 'f'),
    /No title found in the meta of f \(Storybook's auto-titles aren't supported\)/);
});

test('csf parser: the meta title is a top level property of the meta', () => {
  const source = [
    'const meta = {',
    "  args: { title: 'An arg, not the title' },",
    "  title: 'Widgets/Dialogs/Dialog',",
    '} satisfies Meta;',
    'export default meta;',
    'export const Default: Story = {};',
  ].join('\n');
  assert.equal(parseCsf(source, 'f').title, 'Widgets/Dialogs/Dialog');
});

test('csf parser: meta id, parameters.__id, includeStories and excludeStories', () => {
  const source = [
    'const meta = {',
    "  title: 'Widgets/Buttons/Button',",
    "  id: 'custom-button',",
    "  excludeStories: ['mockData', 'Helper'],",
    '};',
    'export default meta;',
    'export const mockData = { rows: [] };',
    'export const Helper = () => null;',
    'export const Primary: Story = {};',
    "export const Other: Story = { parameters: { __id: 'totally-custom--id' } };",
    "export const __namedExportsOrder = ['Other', 'Primary'];",
  ].join('\n');
  // __namedExportsOrder sets the order, and isn't a story itself
  assert.deepEqual(parseCsf(source, 'f').stories.map((story) => story.id),
    ['totally-custom--id', 'custom-button--primary']);

  const regExpSource = [
    "export default { title: 'A', includeStories: /^[A-Z]/, excludeStories: /Data$/ };",
    'export const Story1 = {};',
    'export const helper = {};',
    'export const MockData = {};',
  ].join('\n');
  assert.deepEqual(parseCsf(regExpSource, 'f').stories.map((story) => story.exportName), ['Story1']);
});

test('csf parser: play functions and names however they are written', () => {
  const source = [
    "const meta = { title: 'A' };",
    'export default meta;',
    'export const Tabs: Story = {',
    "\tname: 'Tab indented',",
    '\tasync play({ canvasElement }) {},',
    '};',
    'export const Method: Story = { play() {} };',
    'const play = async () => {};',
    'export const Shorthand: Story = { args: {}, play };',
    'export const NoPlay: Story = { args: { play: true }, render: () => <p>Don\'t (play)</p> };',
    'export const Undefined: Story = { ...Tabs, play: undefined };',
    'export const RegExps: Story = {',
    "  play: async () => { await find(/it's {here/); },",
    "  name: 'After a regular expression',",
    '};',
    "export const Old = Template.bind({});",
    "Old.storyName = 'Old style';",
    'Old.play = async () => {};',
  ].join('\n');
  assert.deepEqual(parseCsf(source, 'f').stories.map((story) => [story.exportName, story.name, story.hasPlay]), [
    ['Tabs', 'Tab indented', true],
    ['Method', null, true],
    ['Shorthand', null, true],
    ['NoPlay', null, false],
    ['Undefined', null, false],
    ['RegExps', 'After a regular expression', true],
    ['Old', 'Old style', true],
  ]);
  // Code in strings, template literals and comments isn't mistaken for exports
  const tricky = [
    "export default { title: 'B', parameters: { docs: { source: { code: `",
    'export const NotAStory = {};',
    '` } } } };',
    '// export const Commented = {};',
    'export const Real = {};',
  ].join('\n');
  assert.deepEqual(parseCsf(tricky, 'f').stories.map((story) => story.exportName), ['Real']);
});

test('csf parser: comments and type operators after values', () => {
  const source = [
    'const meta = {',
    "  component: Glass,",
    "  title: 'Widgets/Glass' as const // the React title",
    '} satisfies Meta<typeof Glass>;',
    'export default meta;',
    'export const Named: Story = {',
    "  name: 'Named story' /* display name */ as string,",
    '  play: undefined, // no play',
    '};',
    'export const Last: Story = {',
    "  name: 'Last' satisfies string // trailing comment, no comma",
    '};',
    "export const Old = Template.bind({}); Old.storyName = 'Old; style'; // note",
  ].join('\n');
  assert.deepEqual(parseCsf(source, 'f').stories.map((story) => [story.id, story.name, story.hasPlay]), [
    ['widgets-glass--named', 'Named story', false],
    ['widgets-glass--last', 'Last', false],
    ['widgets-glass--old', 'Old; style', false],
  ]);
  // The meta's id, and includeStories/excludeStories, with comments
  const meta = [
    "export default { title: 'A', id: 'custom' /* note */, excludeStories: ['Helper', // not a story",
    "  'Data'] as string[] };",
    'export const Helper = {};',
    'export const Data = {};',
    'export const Real = {};',
  ].join('\n');
  assert.deepEqual(parseCsf(meta, 'f').stories.map((story) => story.id), ['custom--real']);
});

test('csf parser: regular expressions after =>', () => {
  const source = [
    "export default { title: 'A' };",
    'export const First: Story = {',
    '  play: async () => { const match = (x) => /{/.test(x); },',
    "  name: 'After an arrow function',",
    '};',
    'export const Second: Story = { args: { check: (x) => /[(]/.test(x) } };',
  ].join('\n');
  assert.deepEqual(parseCsf(source, 'f').stories.map((story) => [story.exportName, story.name, story.hasPlay]), [
    ['First', 'After an arrow function', true],
    ['Second', null, false],
  ]);
});

test('csf parser: export lists and several statements on a line', () => {
  const source = [
    "const meta = { title: 'Widgets/Lists' };",
    "const Primary: Story = { name: 'The primary' };",
    'const Secondary: Story = { play: async () => {} };',
    'const helper = () => null;',
    'export { meta as default, Primary, Secondary as Renamed };',
    'export type { Story };',
    'export { type Other, helper as Helper };',
    "export const Inline: Story = {}; export const SameLine: Story = { name: 'Same line' };",
  ].join('\n');
  const { title, stories, warnings } = parseCsf(source, 'f');
  assert.equal(title, 'Widgets/Lists');
  assert.deepEqual(stories.map((story) => [story.id, story.name, story.hasPlay]), [
    ['widgets-lists--primary', 'The primary', false],
    ['widgets-lists--renamed', null, true],
    ['widgets-lists--helper', null, false],
    ['widgets-lists--inline', null, false],
    ['widgets-lists--same-line', 'Same line', false],
  ]);
  assert.deepEqual(warnings, []);

  // The default export on the same line as a story
  const sameLine = [
    "const meta = { title: 'B' }; export default meta; export const A: Story = {};",
    "export default { title: 'Not the meta' };",
  ].join('\n');
  assert.deepEqual(parseCsf(sameLine, 'f').stories.map((story) => story.id), ['b--a']);
  // `export default function` isn't a meta
  assert.throws(() => parseCsf('export default function Page() {}\n', 'f'), /No meta/);
});

test('csf parser: unsupported exports are reported', () => {
  const source = [
    "export default { title: 'C' };",
    "export * from './other.stories';",
    "export { Shared } from './shared.stories';",
    'export class NotAStory {}',
    'export interface Args { label: string }',
    'export type Story = StoryObj;',
    "import { Imported } from './imported';",
    'export { Imported };',
    'export const Real = {};',
  ].join('\n');
  const { stories, warnings } = parseCsf(source, 'c.stories.tsx');
  assert.deepEqual(stories.map((story) => story.exportName), ['Imported', 'Real']);
  assert.deepEqual(warnings, [
    "c.stories.tsx:2: ignored 'export * from './other.stories';': this form of export isn't supported",
    "c.stories.tsx:3: ignored 'export { Shared } from './shared.stories';': re-exports from other files "
      + "aren't supported",
    "c.stories.tsx:4: ignored 'export class NotAStory {}': this form of export isn't supported",
    "c.stories.tsx:8: 'Imported' is exported but not declared in the file (e.g. it is imported), so its "
      + 'name and play function are unknown',
  ]);
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
      error: 'Boom\nmore', pageErrors: [], consoleErrors: [], screenshot: 'shots/a--y.png' },
    { id: 'b--z', title: 'B', name: 'Z', status: 'SKIP', durationMs: 0, url: 'u' },
  ]);
  assert.match(xml, /<testsuites name="stroom-gwt-workbench" tests="3" failures="1" errors="0" skipped="1"/);
  assert.match(xml, /<testsuite name="A" tests="2" failures="1" errors="0" skipped="0" flakes="0" time="1.510">/);
  assert.match(xml, /<testcase classname="A" name="X" time="1.500"\/>/);
  assert.match(xml, new RegExp('<failure message="Boom" type="StoryFailure">Failed step: click\\(\\)\nBoom\nmore\n'
    + 'URL: u</failure>\n      <system-out>\\[\\[ATTACHMENT\\|shots/a--y.png\\]\\]</system-out>'));
  assert.match(xml, /<testcase classname="B" name="Z" time="0.000">\n {6}<skipped\/>\n {4}<\/testcase>/);
});

test('junit xml: retried stories are recorded as Surefire records reruns', () => {
  // Flaky stories pass, with their earlier failures recorded as Surefire does, failures first
  const flakyXml = toJUnitXml([
    { id: 'a--x', title: 'A', name: 'X', status: 'PASS', durationMs: 5, url: 'u', attempts: 4, flaky: true,
      previousAttempts: [
        { status: 'FAIL', error: 'Boom', failedStep: 'click()', pageErrors: [], consoleErrors: [],
          screenshot: 's/a--x.attempt-1.png' },
        { status: 'ERROR', error: 'The page crashed', pageErrors: [], consoleErrors: [] },
        { status: 'FAIL', error: 'Boom again', pageErrors: [], consoleErrors: [] },
      ] },
    { id: 'a--y', title: 'A', name: 'Y', status: 'ERROR', durationMs: 5, url: 'u', attempts: 3, flaky: false,
      error: 'Still broken', screenshot: 's/a--y.png', previousAttempts: [
        { status: 'ERROR', error: 'Broken' }, { status: 'FAIL', error: 'Failed' }] },
  ]);
  assert.match(flakyXml, /<testsuites [^>]* tests="2" failures="0" errors="1"/);
  assert.match(flakyXml, /<testsuite name="A" [^>]* flakes="1"/);
  assert.match(flakyXml, new RegExp('<testcase classname="A" name="X" time="0.005">\n'
    + '      <flakyFailure message="Boom" type="StoryFailure">\n'
    + '        <stackTrace>Failed step: click\\(\\)\nBoom\nURL: u</stackTrace>\n'
    + '        <system-out>\\[\\[ATTACHMENT\\|s/a--x.attempt-1.png\\]\\]</system-out>\n'
    + '      </flakyFailure>\n'
    + '      <flakyFailure message="Boom again" type="StoryFailure">\n'
    + '        <stackTrace>Boom again\nURL: u</stackTrace>\n'
    + '      </flakyFailure>\n'
    + '      <flakyError message="The page crashed" type="StoryError">\n'
    + '        <stackTrace>The page crashed\nURL: u</stackTrace>\n'
    + '      </flakyError>\n'
    + '      <system-out>Flaky: failed 3 time\\(s\\), then passed on attempt 4\n'
    + '\\[\\[ATTACHMENT\\|s/a--x.attempt-1.png\\]\\]</system-out>\n'
    + '    </testcase>'));
  // A story that failed every time: <rerunFailure> before <error>, then <rerunError>
  assert.match(flakyXml, new RegExp('<testcase classname="A" name="Y" time="0.005">\n'
    + '      <rerunFailure message="Failed" type="StoryFailure">\n'
    + '        <stackTrace>Failed\nURL: u</stackTrace>\n'
    + '      </rerunFailure>\n'
    + '      <error message="Still broken" type="StoryError">Failed on all 3 attempts\nStill broken\nURL: u</error>\n'
    + '      <rerunError message="Broken" type="StoryError">\n'
    + '        <stackTrace>Broken\nURL: u</stackTrace>\n'
    + '      </rerunError>\n'
    + '      <system-out>\\[\\[ATTACHMENT\\|s/a--y.png\\]\\]</system-out>\n'
    + '    </testcase>'));
});

test('junit xml: an error taken from a page or console error is only given once', () => {
  const xml = toJUnitXml([
    { id: 'a--x', title: 'A', name: 'X', status: 'FAIL', durationMs: 5, url: 'u',
      error: 'Console error: oops', pageErrors: [], consoleErrors: ['oops'] },
    { id: 'a--y', title: 'A', name: 'Y', status: 'FAIL', durationMs: 5, url: 'u',
      error: 'Uncaught error in the page: Error: late', pageErrors: ['Error: late\n  at x'], consoleErrors: [] },
  ]);
  assert.match(xml, /<failure message="Console error: oops" type="StoryFailure">Console error: oops\nURL: u<\/failure>/);
  assert.match(xml, /type="StoryFailure">Page error: Error: late\n {2}at x\nURL: u<\/failure>/);
});

test('outcome', () => {
  const play = (status, extra = {}) => ({ storyId: 's--a', status, entries: [], stepCount: 0, nextStep: 0, ...extra });
  const decide = (seen) => decideOutcome({
    storyId: 's--a', crashed: false, timedOut: false, timeoutMillis: 15000, pageErrors: [], consoleErrors: [],
    failOnConsole: false, ...seen,
  });
  const done = (p, status = p?.status) => ({ state: { play: p, status, started: true } });

  assert.deepEqual(decide(done(play('COMPLETED'))), { status: 'PASS', error: null, failedStep: null });
  assert.deepEqual(decide(done(play('ERRORED', { error: 'Boom', failedStep: 'click()' }))),
    { status: 'FAIL', error: 'Boom', failedStep: 'click()' });
  // The status attribute is set but window.__workbenchPlay can't be read (this used to throw)
  assert.deepEqual(decide(done(null, 'COMPLETED')), { status: 'ERROR', failedStep: null,
    error: "The page's data-play-status is COMPLETED but window.__workbenchPlay isn't set" });
  // The status went back after the story finished
  assert.equal(decide(done(play('RENDERING'))).status, 'ERROR');
  assert.match(decide(done(null, null)).error, /status became 'none'/);
  assert.deepEqual(decide({ ...done(null, null), crashed: true }),
    { status: 'ERROR', error: 'The page crashed', failedStep: null });
  assert.match(decide(done(play('COMPLETED', { storyId: 'other' }))).error, /reported story 'other'/);
  // An error after the story completed (seen while it settled) fails it
  assert.deepEqual(decide({ ...done(play('COMPLETED')), pageErrors: ['Error: late\n  at x'] }),
    { status: 'FAIL', error: 'Uncaught error in the page: Error: late', failedStep: null });
  assert.equal(decide({ ...done(play('COMPLETED')), consoleErrors: ['404'] }).status, 'PASS');
  assert.equal(decide({ ...done(play('COMPLETED')), consoleErrors: ['404'], failOnConsole: true }).status, 'FAIL');
  // A page that stopped responding, however far it got
  assert.deepEqual(decide({ unresponsive: true, timedOut: true, state: { play: null, status: null, started: true } }),
    { status: 'FAIL', failedStep: null, error: 'Timed out after 15.0 s: the page stopped responding (e.g. a '
      + 'story, play function or timer that never returns, such as an endless loop)' });
  // Reloading or navigating away is an error, even if it stopped the story finishing in time
  assert.deepEqual(decide({ timedOut: true, navigated: { url: 'http://x/iframe.html', reloaded: true },
    state: { play: null, status: null, started: true } }), { status: 'ERROR', failedStep: null,
    error: 'The page reloaded while the story was being tested (does the story or its play function reload '
      + 'the page?)' });
  assert.deepEqual(decide({ ...done(null, null), navigated: { url: 'about:blank', reloaded: false } }),
    { status: 'ERROR', failedStep: null, error: 'The page navigated away, to about:blank, while the story was '
      + 'being tested (does the story or its play function change the location?)' });
  // Timeouts
  assert.deepEqual(decide({ timedOut: true, state: { play: null, status: null, started: false } }),
    { status: 'ERROR', error: "The workbench didn't start within 15.0 s", failedStep: null });
  assert.deepEqual(decide({ timedOut: true, state: { started: true, status: 'PLAYING', play: play('PLAYING', {
    stepCount: 3, nextStep: 1, entries: [{ status: 'DONE', text: 'a' }, { status: 'ACTIVE', text: 'b' }] }) } }),
  { status: 'FAIL', error: 'Timed out after 15.0 s waiting for the story (status PLAYING, at step 2 of 3)',
    failedStep: 'b' });
});

test('retries', async () => {
  const runner = (statuses) => {
    let i = 0;
    return async (attempt) => {
      assert.equal(attempt, i + 1);
      return { status: statuses[i++], error: `attempt ${i}`, durationMs: 10 * i, pageErrors: [], consoleErrors: [],
        screenshot: `s.attempt-${i}.png` };
    };
  };
  const flaky = await runWithRetries(runner(['FAIL', 'ERROR', 'PASS']), 2);
  assert.equal(flaky.status, 'PASS');
  assert.equal(flaky.flaky, true);
  assert.equal(flaky.attempts, 3);
  assert.deepEqual(flaky.previousAttempts.map((attempt) => [attempt.status, attempt.error, attempt.screenshot]),
    [['FAIL', 'attempt 1', 's.attempt-1.png'], ['ERROR', 'attempt 2', 's.attempt-2.png']]);
  // The time of every attempt counts
  assert.deepEqual([flaky.durationMs, flaky.attemptDurationsMs], [60, [10, 20, 30]]);

  const failing = await runWithRetries(runner(['FAIL', 'FAIL', 'PASS']), 1);
  assert.deepEqual([failing.status, failing.flaky, failing.attempts, failing.durationMs], ['FAIL', false, 2, 30]);

  const passing = await runWithRetries(runner(['PASS']), 3);
  assert.deepEqual([passing.status, passing.flaky, passing.attempts, passing.previousAttempts, passing.durationMs],
    ['PASS', false, 1, [], 10]);
});

test('screenshot files', () => {
  const dir = path.resolve('shots');
  assert.equal(screenshotFile(dir, 'widgets-buttons-button--default'),
    path.join(dir, 'widgets-buttons-button--default.png'));
  assert.equal(screenshotFile(dir, 'a--b', 2), path.join(dir, 'a--b.attempt-2.png'));
  // Ids that aren't safe as file names stay in the directory, and can't share a file
  for (const id of ['../../../../evil', '..', '.', '/etc/passwd', 'a\\..\\b', '', 'con', 'x'.repeat(300)]) {
    const file = screenshotFile(dir, id);
    assert.equal(path.dirname(file), dir, id);
    assert.ok(!path.basename(file).startsWith('.'), id);
    assert.ok(path.basename(file).length < 200, id);
  }
  assert.match(safeFileBase('sub/dir'), /^sub_dir-[0-9a-f]{10}$/);
  assert.notEqual(safeFileBase('sub/dir'), safeFileBase('sub_dir'));
  assert.equal(safeFileBase('sub_dir'), 'sub_dir');
});

test('deadlines', async () => {
  assert.equal(await withDeadline(new Promise(() => {}), 10), TIMED_OUT);
  assert.equal(await withDeadline(Promise.resolve(1), 1000), 1);
  await assert.rejects(withDeadline(Promise.reject(new Error('Boom')), 1000), /Boom/);
});

test('shared browser: relaunched when it stops, up to a limit', async () => {
  let launched = 0;
  const fakeBrowser = () => ({ number: ++launched, closed: false, async close() { this.closed = true; } });
  const browsers = new SharedBrowser(async () => fakeBrowser(), { maxRelaunches: 2, pauseMillis: 0 });
  await browsers.start();
  const first = browsers.browser;
  // Two stories notice that the same browser has stopped: it is only replaced once
  await Promise.all([browsers.replace(first), browsers.replace(first)]);
  assert.deepEqual([browsers.browser.number, browsers.relaunches, first.closed], [2, 1, true]);
  // A story that noticed late doesn't replace the new browser
  await browsers.replace(first);
  assert.equal(browsers.browser.number, 2);
  await browsers.replace(browsers.browser);
  assert.equal(browsers.browser.number, 3);
  // The limit has been reached
  await assert.rejects(browsers.replace(browsers.browser),
    /Chromium stopped unexpectedly 3 times, and has been relaunched as many times as allowed \(2\)/);
  assert.ok(browsers.failure);
  await assert.rejects(browsers.replace(browsers.browser), /as many times as allowed/);
});

test('shared browser: not relaunched once the run is cancelled, or if it fails to launch', async () => {
  let cancelled = false;
  const cancellable = new SharedBrowser(async () => ({ close: async () => {} }),
    { isCancelled: () => cancelled, pauseMillis: 0 });
  await cancellable.start();
  cancelled = true;
  await assert.rejects(cancellable.replace(cancellable.browser), /The test run was cancelled/);

  let launches = 0;
  const failing = new SharedBrowser(async () => {
    if (++launches > 1) {
      throw new Error('No Chromium\nmore detail');
    }
    return { close: async () => {} };
  }, { pauseMillis: 0 });
  await failing.start();
  await assert.rejects(failing.replace(failing.browser), /^Error: Unable to relaunch Chromium: No Chromium$/);
});

test('leak check: growth per close and judgement', () => {
  const samples = [0, 1, 2, 3].map((i) => ({ nodes: 1000 + 50 * i, listeners: 200 + 2 * i, heap: 4096 * i }));
  assert.deepEqual(growthPerCycle(samples), { nodes: 50, listeners: 2, heapKB: 4 });
  assert.deepEqual(growthPerCycle([{ nodes: 5, listeners: 5, heap: 5 }]), { nodes: 0, listeners: 0, heapKB: 0 });

  assert.equal(judgeLeak({ checked: false }), null);
  assert.equal(judgeLeak({ checked: true, cycles: 8, nodes: 20, listeners: 5, heapKB: 1 }), null);
  assert.match(judgeLeak({ checked: true, cycles: 8, nodes: 21, listeners: 0, heapKB: 9 }),
    /^Leak: each close of the screen keeps 21 DOM nodes and 0 event listeners \(9 KB of heap\), over 8 closes/);
  assert.match(judgeLeak({ checked: true, cycles: 8, nodes: 0, listeners: 6, heapKB: 0 }), /keeps 0 DOM nodes and 6/);
  assert.equal(withLeakProbe('http://x/iframe.html?id=a&viewMode=story'), 'http://x/iframe.html?id=a&viewMode=story&probe=1');
});
