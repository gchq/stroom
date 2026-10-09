# Stroom GWT Workbench test runner and React story coverage

The workbench's equivalent of the React project's `npm run test:storybook` (Storybook's
`test-storybook`), plus the tools that track the porting of the React Storybook's stories
(`stroom-ui-react`) to the GWT workbench.

| What                                   | Command (from the repository root)                                            |
|----------------------------------------|-------------------------------------------------------------------------------|
| Run every story and play function      | `./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchTest`                    |
| Run against a running workbench        | `./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchTest -PworkbenchUrl=http://localhost:6008` |
| Coverage of the React stories, by group | `./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchCoverage`               |
| List the stories still to port         | `./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchCoverage -PworkbenchCoverageList=Widgets/Buttons` |
| Check the ported story ids             | `./gradlew :stroom-gwt:stroom-gwt-workbench:test` (`TestReactStoryCoverage`)  |
| Regenerate the React story manifest    | `./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchReactManifest`           |
| Check the React story manifest is current | `./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchReactManifestCheck`   |

None of these are part of `build`/`check`, so normal builds don't need Node, npm or Playwright.
The test runner needs Node.js 20+ (with npm). `workbenchTest` fails if npm isn't available, so
that a CI build without Node can't pass without running the tests; add
`-PworkbenchTestSkipIfNoNpm` to skip it (with a warning) instead.

## Running the tests

`workbenchTest`:

1. Runs `npm ci` in this directory (only when `package.json`/`package-lock.json` change) to install
   Playwright, then `npx playwright install chromium` (see [The browser](#the-browser)).
2. Unless `-PworkbenchUrl` is given, does a draft GWT compile (skip it with
   `-PworkbenchSkipCompile` to reuse the last compile), fetches axe-core for the Accessibility
   addon (as `workbenchServe` does) and starts a workbench server on a free port (or
   `-PworkbenchTestPort=N`, which must be free: the task fails rather than test whatever is
   already listening there), stopping it afterwards. It waits until the server serves a file
   unique to the run, so the tests can't run against another server. Its log is
   `build/workbench-test/server.log`.
3. Runs `node run.mjs` against it, failing the task if any story fails (or no stories match).
   The previous run's reports are deleted before any task runs, so they can't be mistaken for this
   run's even if the run is skipped or a task it depends on fails.
4. If `selftest/selftest.mjs` exists, runs the browser self-test of the workbench's test tooling with
   `node selftest/selftest.mjs --url <the same url>`, failing the task if it fails (it runs even if
   stories failed, so that both are reported). `-PworkbenchTestSkipSelfTest` skips it.

If the build is cancelled (e.g. Ctrl-C) or fails, the server, Node and Chromium are all stopped.

Options (Gradle properties):

* `-PworkbenchTestFilter=Widgets/Buttons,*--with-icons` - only run matching stories (see below).
* `-PworkbenchTestWorkers=4` - the number of stories to run at once.
* `-PworkbenchTestScreenshots` - save a PNG of every story.
* `-PworkbenchTestArgs='--timeout 30000 --retries 1 -v'` - any other `run.mjs` options. They are
  split at spaces, except within quotes, e.g.
  `-PworkbenchTestArgs="--exclude 'Widgets/Date & Time' -v"`. Patterns containing spaces can also
  be given with `-PworkbenchTestFilter`, which is only split at commas.
* `-PworkbenchTestSkipIfNoNpm` - skip, rather than fail, if npm isn't available.
* `-PworkbenchTestSkipSelfTest` - don't run the browser self-test (`selftest/selftest.mjs`).

### The browser

`npx playwright install chromium` downloads the Chromium build for the pinned Playwright version
(about 300 MB: Chromium, its headless shell and ffmpeg) into Playwright's cache,
`~/.cache/ms-playwright` (`%LOCALAPPDATA%\ms-playwright` on Windows, `~/Library/Caches/ms-playwright`
on macOS), or `$PLAYWRIGHT_BROWSERS_PATH` if set. This is outside the repository, so isn't removed
by `clean`, and is shared by every checkout using the same Playwright version; it is only
downloaded when missing. On a Linux distribution Playwright doesn't support (it supports recent
Ubuntu and Debian), it warns and falls back to its Ubuntu build, which usually works but may need
extra system libraries (`npx playwright install-deps chromium` lists/installs them on Debian and
Ubuntu).

Or run the runner directly, e.g. against `workbenchServe` (port 6008) or `workbenchCodeServer`:

```bash
cd stroom-gwt/stroom-gwt-workbench/test-runner
npm ci && npx playwright install chromium     # once
node run.mjs                                  # every story at http://localhost:6008
node run.mjs Widgets/Buttons -v               # stories titled Widgets/Buttons/...
node run.mjs 'widgets-*--disabled' --url http://localhost:6008 --workers 8 --screenshots
node run.mjs --list app-main-                 # just list the matching stories
node run.mjs --help
```

Patterns: a pattern containing `/` matches story titles (`Widgets/Buttons` matches titles equal to
or under it, `Widgets/*/TickBox` is a glob); any other pattern matches ids (`widgets-buttons-` is a
prefix, `*--with-icons` a glob). Matching ignores case. `--exclude`, `--include-tags`,
`--exclude-tags` and `--skip-tags` (default `skip-test`) narrow the selection further, as with
`test-storybook`.

### What it checks

For each story the runner opens `<url>/iframe.html?id=<story id>&viewMode=story` (the same preview
page the workbench's canvas shows) in a fresh page, in its own browser context (so nothing such as
local storage or cookies carries over from other stories), and waits, up to `--timeout` (15 s by
default), for the story to finish, then keeps watching it for `--settle` ms (100 by default, after
the next animation frame) so that an error thrown just after it finished isn't missed:

| Outcome | When |
|---------|------|
| **PASS** | A story with a play function passed every step; a story without one rendered. |
| **FAIL** | A play function step failed (as in Storybook, the remaining steps aren't run); the story couldn't be found or threw while rendering; the page had an uncaught error (including one while the story settles); a console error, with `--fail-on-console`; `Timed out after ... waiting for the story` (it didn't finish within `--timeout`); `Timed out after ...: the page stopped responding` (e.g. an endless loop in the story, its play function or a timer, which would otherwise hang the run: the runner gives up on the page `--timeout` + `--settle` + 5 s after the story started). |
| **ERROR** | The story's page couldn't be opened, or crashed; `The page reloaded while the story was being tested`; `The page navigated away, to <url>, ...` (both however soon after opening it happens, as each new document in the page is numbered); the story finished, then its status went back (e.g. it re-rendered); `The workbench didn't start within ...` (e.g. it hasn't been compiled); `Chromium stopped unexpectedly ...` and couldn't be relaunched (see below); not run, as the run failed or was cancelled. |
| **SKIP** | The story has one of the `--skip-tags` (default `skip-test`). |

Console errors are always reported, but only fail the story with `--fail-on-console`. Errors the
page reports while a failed story's screenshot is taken don't change its outcome, and are recorded
separately (`screenshotErrors` in `results.json`).

If Chromium stops unexpectedly (e.g. it crashes or runs out of memory) it is relaunched, and the
stories that were running are run again (their results note it in `browserRestarts`, and
`results.json` has `browserRelaunches`). It is relaunched up to 3 times in a run; after that, or if
it can't be relaunched, the run stops and the remaining stories are reported as not run.

If the run is cancelled (Ctrl-C, or Gradle stopping it), the stories running are stopped, the
reports are still written (with `runError` saying it was cancelled, and the stories not finished as
not run) and the runner exits with 128 + the signal's number (130 for Ctrl-C, 143 for SIGTERM).

`--retries N` retries failed stories. A story that fails and then passes is reported as **flaky**:
it is listed (with each failed attempt) after the failures, counted in the summary, recorded in
`results.json` (`flaky`, `attempts`, `attemptDurationsMs` and `previousAttempts`) and, in
`junit.xml`, passes with a `<flakyFailure>`/`<flakyError>` for each failed attempt, as Maven
Surefire records reruns. A story that fails every attempt has a `<failure>`/`<error>` and a
`<rerunFailure>`/`<rerunError>` for each earlier attempt. A retried story's time (`durationMs`, and
in `junit.xml`) is the total of all its attempts.

### Output

As with `test-storybook`, each component is listed as it finishes (each story with `-v`),
followed by the details of every failure and a summary:

```
 PASS  Widgets/Buttons/Button (6 stories, 1.1 s)
 FAIL  Widgets/Inputs/TickBox (3 stories, 2.3 s)
    ✕ Disabled (1.9 s)

Failures:

  ● Widgets/Inputs/TickBox › Disabled  [widgets-inputs-tickbox--disabled]

    Failed step: expect(getByRole("checkbox")).toBeDisabled()
    Expected the element to be disabled
    ...
    http://localhost:45621/iframe.html?id=widgets-inputs-tickbox--disabled&viewMode=story
    Screenshot: .../build/workbench-test/screenshots/widgets-inputs-tickbox--disabled.png

Test Suites: 1 failed, 1 passed, 2 total
Tests:       1 failed, 8 passed, 9 total
Time:        3.0 s
```

Reports are written to `--output-dir` (default `stroom-gwt/stroom-gwt-workbench/build/workbench-test`):

* `junit.xml` - JUnit XML for CI, one `<testsuite>` per component and one `<testcase>` per story.
  Each `<testsuite>` is valid against Maven Surefire's `surefire-test-report.xsd`. Screenshots are
  attached with `[[ATTACHMENT|<file>]]` in the `<testcase>`'s `<system-out>` (read by e.g. the
  Jenkins JUnit attachments plugin), for failed and flaky stories.
* `results.json` - every story's status (`PASS`, `FAIL`, `ERROR` or `SKIP`), time, failed step,
  error, page/console errors, play function steps, screenshot and attempts.
* `screenshots/<story id>.png` - for failed stories, or every story with `--screenshots`. With
  `--retries`, each earlier attempt's is `<story id>.attempt-<n>.png`. A story id that isn't safe
  as a file name (e.g. containing `/`) has its other characters replaced by `_` and a hash added.

The previous run's reports and screenshots are deleted when a run starts.

The exit code is:

| Code | Meaning |
|------|---------|
| 0 | Every story passed (or was skipped). |
| 1 | A story failed, or was an error. |
| 2 | The stories couldn't be run at all (e.g. the workbench isn't running), or the run failed part way, in which case the reports are still written, with any stories not run as errors. |
| 3 | No stories match the patterns and tags given (use `--list` to check them). |
| 128 + signal | The run was cancelled, e.g. 130 for Ctrl-C. |

`workbenchTest`'s failure message says which of these happened.

### How the runner reads the workbench

The workbench puts its state on the page for the runner (see `RunnerHooks` in
`stroom-gwt-workbench-framework`), so nothing is scraped from the page:

* `window.__workbenchIndex` - every story, in sidebar order, in the shape of a Storybook
  `index.json` (`{v: 5, entries: {<id>: {type, id, title, name, tags, hasPlay, sourceClass}}}`).
  Set on both `index.html` and `iframe.html` when the workbench starts; the runner reads it from
  `iframe.html`.
* `<html data-play-status="...">` on the preview page - `RENDERING`, then `PLAYING` while the
  play function runs, then `COMPLETED` or `ERRORED` (`PAUSED` only happens when the Interactions
  addon's debugger controls are used). A story without a play function goes straight to
  `COMPLETED` once rendered.
* `window.__workbenchPlay` - `{storyId, status, hasPlay, nextStep, stepCount, failedStep, error,
  entries: [{depth, text, status, error}]}`, replaced whenever the status changes.

These work whether the preview is opened on its own or in the manager's canvas, so they can also be
used from any Playwright test, e.g.
`await page.waitForSelector('html[data-play-status=COMPLETED]')`.

## Porting a React story

The goal is a GWT story for each of the React Storybook's stories (831 at the time of writing, 595
of which have play functions), with the **same id**, so that the two can be compared at the same
URL and the play functions ported step by step.

1. Find the story in the React project, e.g. `src/widgets/Button/Button.stories.tsx`. Its id is
   derived from the `title` of the file's `meta` and the story's **export name**, exactly as
   Storybook does: `Widgets/Buttons/Button` + `WithIcons` = `widgets-buttons-button--with-icons`.
   A story's `name:` changes only its display name, not its id.
2. Write (or add to) the GWT stories class for the component, in the package for its group:
   `stroom.gwt.workbench.client.widgets.<group>` (`Widgets/<Group>/*`, e.g. `widgets.buttons`),
   `...client.app` (`App/*`) or
   `...client.screens` (`Screens/*`). See `widgets/buttons/ButtonStories.java`:
   ```java
   public static void addTo(final StoryRegistry registry) {
       registry.component("Widgets/Buttons/Button", ButtonStories.class)    // the React title
               .story("WithIcons", context -> ...)                          // the React export name
               .story("DialogClose", "Dialog — Close button", context -> ...) // if it has a name:
               .withPlay(play -> { ... });                                  // if it has a play:
   }
   ```
3. Register the class in the group's registrar - `WidgetsStories`, `AppStories` or
   `ScreensStories` in `stroom.gwt.workbench.client` - on the line after the comment holding its
   React title:
   ```java
   // Widgets/Buttons/Button
   ButtonStories.addTo(registry);
   ```
   Every React component has its own comment line, in the React sidebar's order, so people porting
   different components edit different lines and rarely conflict. Don't register stories in
   `AllStories`, which only calls the registrars.
4. Run `./gradlew :stroom-gwt:stroom-gwt-workbench:test`. `TestReactStoryCoverage` fails if a
   workbench story's id isn't in the React manifest (a wrong title or export name), and logs
   warnings for e.g. a display name that differs from the React one.
5. Run the story's tests with
   `./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchTest -PworkbenchTestFilter=Widgets/Buttons/Button`.

### Stories that won't be ported

Stories are counted as **ported** simply by being registered. A React story that doesn't make
sense in GWT, or can't be ported yet, is recorded in
`src/test/resources/react-story-status.json`, keyed by story id or by title (a title covers every
story under it, e.g. `App/AI`), with a reason:

```json
{
  "stories": {
    "widgets-buttons-button--loading": { "status": "n/a", "reason": "GWT Button has no loading state" },
    "App/AI": { "status": "blocked", "reason": "Needs the AI REST resources faked" }
  }
}
```

`TestReactStoryCoverage` fails if a key matches no React story, a status isn't `n/a` or `blocked`
or a reason is missing.

## Screenshots

`stroom-gwt/stroom-gwt-workbench/screenshots/` holds a baseline screenshot of every story
(`<story id>.png`; git-ignored, so local to each checkout), so that a change to Stroom's widgets
(e.g. for accessibility) can be reviewed for its effect on every story's appearance:

```bash
# Replace the baseline with a screenshot of every story (all must pass)
./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchScreenshotBaseline
# After a change: screenshot every story again, and compare them with the baseline
./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchScreenshotDiff
```

Both run `workbenchTest` (so take its options, e.g. `-PworkbenchSkipCompile`), with `--screenshots`
and, so that the screenshots are the same each run:

* `--fixed-time 2026-01-01T12:00:00Z`: the stories' clock (`Date`) is fixed (Playwright's
  `clock.setFixedTime`), so the times they show don't change. Timers still run, and the play engine
  measures its timeouts with `performance.now()`, which it doesn't affect.
* `--settle 750`: a longer wait after each play, for its last asynchronous updates.
* `--retries 1`: a story that fails once under the load of a full run is retried (and reported as
  flaky), so that the screenshots can still be used.

Every screenshot is taken with animations stopped and text carets (including Ace's) hidden, and the
preview always waits for Stroom's fonts to load before it renders a story, as Stroom's widgets
measure text as they render.

`workbenchScreenshotDiff` compares each story's screenshot with its baseline using ImageMagick's
`compare -metric AE` (as the React port's `compare/pixel-diff.mjs` does), so ImageMagick must be
installed. It counts the differing pixels twice: `raw` (any difference, including sub-level
anti-aliasing, which ImageMagick 7 counts as fractions of a pixel) and `visible` (differences
over the fuzz, 1%). A story has changed if it has a whole visible pixel more than the allowance
(0), or its size changed (ImageMagick 7 would only compare where two sizes overlap). The report
lists the changed, new (no baseline) and missing (baseline only) stories, and the changed stories'
`<id>.expected.png`, `<id>.actual.png` and `<id>.diff.png` are written to
`build/workbench-screenshot-diff` with `report.json`. It reports, rather than fails on, changes.
`-PworkbenchScreenshotFuzz=2%` and `-PworkbenchScreenshotMaxVisiblePixels=N` tune it. With
`-PworkbenchTestFilter` only the stories run are compared (and replaced in the baseline).

A few stories' final states still vary between runs (e.g. which row ends up selected, or a hover
left by the play), so expect a handful of small differences even without a change; run the diff
again to check a surprising one.

## Leak check

`-PworkbenchLeakCheck` (the runner's `--leak-check`) also checks each story that passes for memory
leaks. For a story that opens its screen with `ScreenHarness.afterStartUp` (with `probe=1` in its
address the harness exposes `window.__leakProbe`; see `LeakProbe`), it closes the screen as closing
a Stroom tab does, disposing the presenters made while opening it (as `DocumentPlugin` does), and
opens it again, 2 times to warm up and then 8 times (`--leak-cycles N`), measuring the DOM nodes
and event listeners left after each close (once garbage has been collected). The story fails if
each close keeps more than 20 nodes or 5 listeners on average. A story that seems to leak is
measured again, waiting 1.5 s after each close, since e.g. a deferred command keeps a closed
screen until it has run; only if it still leaks does it fail. Stories that don't open their screen
with `afterStartUp`, or show nothing from it, aren't checked (`-v` shows each story's growth per
close, or why it wasn't checked).

```bash
./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchTest -PworkbenchLeakCheck -PworkbenchTestFilter='app-editors-*'
```

A leak found this way can be investigated with `stroom-gwt/stroom-gwt-suite/mem-leak.mjs` (which
classes grow, and what keeps them) against the full app.

## Coverage

`./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchCoverage` prints, for each top level group
(`App`, `Screens`, `Widgets`) and its child groups, the number of React stories, how many are
ported, `n/a`, `blocked` and still to do, and how many of the React play functions have been
ported. `-PworkbenchCoverageList=<id or title prefix>` also lists the remaining stories matching
it (`-PworkbenchCoverageList=` lists them all), and ported stories still missing their play
function. The same report is logged by `TestReactStoryCoverage`.

### The React story manifest

`src/test/resources/react-stories.json` lists every React story (id, title, name, export name,
file and whether it has a play function), in the React sidebar's order. It is generated by
`react-manifest.mjs`, which reads the first available of:

1. the running React Storybook's `http://localhost:6006/index.json` (`--index-url`),
2. the built React Storybook's `storybook-static/index.json`,
3. the React `src/**/*.stories.tsx` files, parsed with Storybook's id rules (`lib/csf-parser.mjs`).

When an index is used the files are parsed too and any differences reported, to keep the parser
honest. The parser handles the usual CSF forms, including `export { A, B as C }` lists (and
`export { meta as default }`), several statements on one line, and values followed by comments or
`as const`/`satisfies ...`; exports it can't handle (e.g. `export * from '...'` or
`export { A } from '...'`) are reported as warnings. A file the parser can't handle (e.g. one using Storybook's auto-titles, which depend on
the Storybook configuration) is reported as a difference; without an index it stops the manifest
being generated, as its stories would be missing. Regenerate the manifest when the React stories
change:

```bash
node react-manifest.mjs [--react-dir ../../../../stroom-ui-react] [--source auto|url|static|parse]
node react-manifest.mjs --check    # exit 1 if it is out of date
```

`--check` (and `workbenchReactManifestCheck`) never reads a running Storybook unless given
`--source url`, as that may be another branch's: it checks against the built index
(`storybook-static/index.json`), or the parsed files if there isn't one.

The React checkout defaults to `stroom-ui-react` next to this repository (or
`$STROOM_UI_REACT_DIR`). The group registrars' title comments come from the manifest; add a comment
for a new React component in its sidebar position when it is ported.

## Files

* `run.mjs` - the test runner.
* `react-manifest.mjs` - generates the React story manifest.
* `screenshot-diff.mjs` - compares a run's screenshots with the baseline (see
  [Screenshots](#screenshots)); `lib/screenshot-diff.mjs` does the comparison.
* `lib/` - Storybook's id rules, the `*.stories.tsx` parser, story selection, deciding a story's
  outcome (and retrying it), the shared browser (relaunched if it stops), screenshot file names,
  deadlines, the leak check and the JUnit XML writer, with tests (`node --test lib/`). `lib/run.test.mjs` tests
  `run.mjs` itself against a fake workbench whose stories misbehave (endless loops, reloads,
  navigating away, crashing the browser etc.); it needs Playwright's Chromium, and is skipped
  without it.
