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

None of these are part of `build`/`check`, so normal builds don't need Node, npm or Playwright.
The test runner needs Node.js 20+ (with npm); `workbenchTest` skips with a message if npm isn't
available.

## Running the tests

`workbenchTest`:

1. Runs `npm ci` in this directory (only when `package.json`/`package-lock.json` change) to install
   Playwright, then `npx playwright install chromium`, which downloads Chromium into Playwright's
   cache (`~/.cache/ms-playwright`, or `$PLAYWRIGHT_BROWSERS_PATH`) if it isn't already there.
2. Unless `-PworkbenchUrl` is given, does a draft GWT compile (skip it with
   `-PworkbenchSkipCompile` to reuse the last compile) and starts a workbench server on a free
   port (or `-PworkbenchTestPort=N`), stopping it afterwards. Its log is
   `build/workbench-test/server.log`.
3. Runs `node run.mjs` against it, failing the task if any story fails.

Options (Gradle properties):

* `-PworkbenchTestFilter=Widgets/Buttons,*--with-icons` - only run matching stories (see below).
* `-PworkbenchTestWorkers=4` - the number of stories to run at once.
* `-PworkbenchTestScreenshots` - save a PNG of every story.
* `-PworkbenchTestArgs='--timeout 30000 --retries 1 -v'` - any other `run.mjs` options.

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
page the workbench's canvas shows) in a fresh page and waits, up to `--timeout` (15 s by default),
for the story to finish:

* A story with a play function **passes** when every step has passed, and **fails** at the first
  failing step (as in Storybook, the remaining steps aren't run).
* A story without one passes once it has rendered.
* A story fails if it can't be found, throws while rendering, doesn't finish in time, or the page
  has an uncaught error. Console errors are reported, and fail the story with `--fail-on-console`.
* A story is an **error** (rather than a failure) if its page can't be opened or the workbench
  doesn't start on it, e.g. it hasn't been compiled.

`--retries N` retries failed stories.

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
* `results.json` - every story's status (`PASS`, `FAIL`, `ERROR` or `SKIP`), time, failed step,
  error, page/console errors, play function steps and screenshot.
* `screenshots/<story id>.png` - for failed stories, or every story with `--screenshots`.

The exit code is 0 if every story passed, 1 if any failed and 2 if the stories couldn't be run at
all (e.g. the workbench isn't running).

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
   `stroom.gwt.workbench.client.widgets` (`Widgets/*`), `...client.app` (`App/*`) or
   `...client.screens` (`Screens/*`). See `widgets/ButtonStories.java`:
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
honest. Regenerate the manifest when the React stories change:

```bash
node react-manifest.mjs [--react-dir ../../../../stroom-ui-react] [--source auto|url|static|parse]
node react-manifest.mjs --check    # exit 1 if it is out of date
```

The React checkout defaults to `stroom-ui-react` next to this repository (or
`$STROOM_UI_REACT_DIR`). The group registrars' title comments come from the manifest; add a comment
for a new React component in its sidebar position when it is ported.

## Files

* `run.mjs` - the test runner.
* `react-manifest.mjs` - generates the React story manifest.
* `lib/` - Storybook's id rules, the `*.stories.tsx` parser, story selection and the JUnit XML
  writer, with tests (`node --test lib/`).
