# Wave 22: Records and trustworthy screenshots

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 28 October 2026, after wave 21. Before the
client-side bug wave and the CSS plan (`../CSS_PLAN.md`, whose step 0 this is): bring the records up
to date, and make the screenshot diff show only real changes.

## Done (28 October 2026)

- **Records:** ISSUES.md's "Workbench stories that fail" has a dated status (all 1,729 stories pass
  with the leak check; the flaky stories it lists haven't failed in six full runs; what still needs
  a retry now and then); "The leak check fails 50 stories" is marked fixed; "Closed screens are
  never released" says what the waves did about it and what hasn't been measured again.
- **A new finding, recorded:** an opened document's focus lands on its selected tab or its first
  toolbar button depending on whether its tab bar has been laid out (ISSUES.md, "An opened
  document's focus lands on its tab bar or its first button, by timing"), which is why
  `App/Main/AppShell` `OpensDocument`'s screenshot differed.
- **Visualisations draw the same each run:** with `--fixed-time` (screenshot runs) the runner seeds
  `Math.random` in every frame (`run.mjs`, `SEEDED_RANDOM_SCRIPT`), so the force and tree layouts
  are the same from run to run.
- **Fonts are loaded before a screenshot:** the runner waits for `document.fonts.ready` in every
  frame. The documentation editor's preview, missing from its old baseline, is now always drawn.
- **`Widgets/Data Grid/DataGrid` `ValueSpinnerColumn`** stops polling before its screenshot, and
  the column shows "Stopped" rather than a count that depended on how long the story ran.
- **Stale baselines retaken:** 52 that had differed in every run since waves 13 and 14 (whose
  changes were deliberate and recorded then), and the 42 the fixes above changed.

Checked with a run of the affected groups (505 stories, all pass): 463 screenshots unchanged and the
42 changed ones those expected. A second run of the changed groups (70 stories, all pass) against the
new baselines left 2 differing: `App/Visualisations/Tree` `Keyboard` and `Force` `SelectionPayload`,
each by the same pixels as before, so each flips between two states (a tooltip shown on focus).

Not done, recorded in ISSUES.md: those two visualisation stories, and the focus race (for the bug
wave).
