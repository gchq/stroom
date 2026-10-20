# Play API self-test

Checks the browser side of the workbench's play API (the JSNI in the workbench framework's
`client/play` package: `Dom`, `UserEventHelpers`, `Aria`) against the code Storybook plays really
use: Testing Library, user-event 14 and jest-dom, from the `storybook` package's `storybook/test`
bundle. Both run in the same headless Chromium, on the same preview page, and the results must be
identical.

It checks:

* **Roles, accessible names and accessibility** of every element of the HTML snippets in
  `corpus.mjs` (`ARIA_SNIPPETS`), against Testing Library's `*ByRole` roles (aria-query),
  `computeAccessibleName` (dom-accessibility-api) and `isInaccessible`;
* **jest-dom matchers**: `toBeVisible`, `toBeChecked`, `toBeDisabled` and `toHaveStyle`
  (`MATCHER_SNIPPETS`, `STYLE_CASES`);
* **user events** (`SCENARIOS`): clicks, double and triple clicks, right clicks, hover/unhover,
  Tab, typing, keys and `fireEvent`, comparing every event fired (type, target, button, buttons,
  detail, key, code, modifiers, inputType, data, relatedTarget) and the resulting focus, values,
  field selections, document selection and content editable markup. Coordinates and the legacy
  `keyCode` aren't compared: the workbench deliberately differs there (see WRITING-STORIES.md).

## Running

Start the workbench, e.g.

```
./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchDraftCompile
./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchServe -PworkbenchPort=6008
```

then, in `stroom-gwt/stroom-gwt-workbench/test-runner` (after `npm ci`, which installs
Playwright and its Chromium):

```
node selftest/selftest.mjs --url http://localhost:6008
```

It exits with 0 if everything matches, 1 if anything differs (printing the first difference of
each check) and 2 if it couldn't run.

Options:

* `--url URL` - the workbench (default `http://localhost:6008`, or `$WORKBENCH_URL`). The page
  used is `iframe.html?id=widgets-buttons-button--default&viewMode=story&selftest`; the `selftest`
  parameter makes the preview expose `window.__workbenchDom` (see `SelfTestHooks`).
* `--storybook-modules DIR` - a `node_modules` holding the `storybook` package (default
  `selftest/reference/node_modules`, or `$STORYBOOK_MODULES`).
* `--update-golden` - also write the reference's results to `golden/reference.json`.
* `--only TEXT` - only run the scenarios whose name contains `TEXT`.
* `--verbose` - list each scenario that passes.

## The reference, and the golden file

The reference isn't a dependency of the test runner, so normal runs don't download it. To compare
with it live (e.g. to add checks), install it, git-ignored, in `selftest/reference`:

```
npm run selftest:reference
```

Nothing from it is copied into this repository. `reference.mjs` serves
`storybook/dist/test/index.js` (and the chunks it imports) to the page with Playwright's request
routing, replacing `process.env.NODE_ENV` as Storybook's builder does and stubbing its two imports
of Storybook internals (the instrumenter, which only wraps functions for the Interactions panel,
and the client logger), and exports a few of its internal functions (`computeAccessibleName`,
the implicit roles, `isInaccessible`).

When the reference isn't installed (e.g. on a build machine), the workbench's results are
compared with `golden/reference.json` instead: the reference's results, written by
`--update-golden` (from Storybook 10.4.6). Regenerate it whenever `corpus.mjs` changes, or after
moving `selftest:reference` to a newer Storybook:

```
node selftest/selftest.mjs --url http://localhost:6008 --update-golden
```

## Adding checks

Add snippets or scenarios to `corpus.mjs`. A scenario's actions are `[name, selector, text]`, where
`name` is `click`, `dblClick`, `tripleClick`, `rightClick`, `hover`, `unhover`, `type`, `clear`,
`keyboard` (no selector), `tab`, `shiftTab` or `fireEvent` (with the Testing Library method as the
text, e.g. `contextMenu`). `setup` is JavaScript run with `host` (the element holding the snippet)
before the actions, e.g. to add listeners that cancel events.
