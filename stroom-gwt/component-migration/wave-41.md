# Wave 41: UI audit wave 7, the stylesheet check and shared-class decisions

The seventh of the UI audit's waves ([../UI_AUDIT.md](../UI_AUDIT.md), theme T5, decisions D1 and
D8); written 29 October 2026, after wave 40.

## Done (29 October 2026)

- **`TestStylesheets` checks two more things** (D8):
  - *A sheet styles its own block*, the one it is named after: another block may be the context
    (`.data-grid .my-block__cell`), and the page's settings classes (theme, density, font size) and
    names that aren't Stroom's are left aside, but the compound a rule styles is the sheet's block.
    The shared sheets (`layout-helpers.css`, `form-controls.css`, `font.css`,
    `conditional-formatting.css`) are allowed several. `SHEETS_TO_SPLIT` lists the sheets that
    don't yet (65 when written, 59 after this wave); a listed sheet that passes fails the test, so
    the list only shrinks. Wave 8 empties it.
  - *No bare state classes* (`readonly`, `selected`, `empty` …): a state is the block's modifier;
    only `disabled`, which GWT's widgets set, stays.
- **`dashboard-panel` went** (D1): from 24 templates, a story, two test runner scripts and a doc.
  No pane has a form group as a direct child, so its one rule (`.dashboard-panel > .form-group
  label`) styled nothing, and went too. The `--dashboard-panel__*` colours stay.
- **`stroom-control` as a panel's frame became `control-box`** (dashboard-query-23,
  data-pipeline-processing-15, explorer-documents-shell-08), app-wide: the query history and
  favourites, the result stores, the volume groups, the stepping filter, the explorer popup, and
  six more. The expression editor frames itself with `expression-editor--framed`. The grid's header
  colour inside a control box is `control-box`'s.
- **`stroom-control.css` is one block** (widgets-1-25, widgets-2-26, data-pipeline-processing-25,
  explorer-documents-shell-24): the build info grid is `welcome-tab__build-info`'s, the value label
  is `text-label--value`, and the drop-down's bare `readonly` is `drop-down--read-only`. The field
  box's own name waits for wave 8.
- **The identity forms' messages are their form groups' feedback** (security-settings-10,
  widgets-1-23): Change Password, Login, Current Password, Email Reset Password and the Name dialog;
  `change-password.css` is one block (`change-password__policy`, `__strength`, `__length--*`).
- **Each component its own block:** the credential settings and replace secret forms (the
  `credentials-details-dialog` sheet went, and `wide-control`, which did nothing in the floor map's
  object editor); the navigation's title bar, logo and toolbar are `navigation`'s elements; the play
  and stop buttons are `toolbar-button--play`/`--stop` (the query, data panes, stepping and AI chat
  had borrowed `query-buttons`, whose sheet went); the pager panel is `pager-panel` (the
  `pager-toolbar` and `list-container` sheets went); the document asset tree, the explorer's cell,
  Find in Content, the sort icon and the step location link have sheets of their own.
- **Bare states:** `ask-stroom-ai--empty`, `operation-item--selected`, `drop-down--read-only`, and
  the time range selector's `--readonly` is `--read-only` like the rest.

- **The AI chat's stories** found its Run and Stop button by the bare `play` and `stop` classes;
  they find it by `toolbar-button--play`/`--stop` now (the first full run failed their nine).

- **A nudge kept** (D1's case): since wave 39 the jobs screen's lower pane holds its list's form
  group as a direct child, so `dashboard-panel`'s label rule did apply there; its heading sits 0.3em
  down and 0.2em in again through a modifier of the form group's own block,
  `form-group--inset-label` (the nodes screen's list never had it: its pane's form group isn't a
  direct child).

- **Checked:** compile and checkstyle of every client module, every client test (with the extended
  `TestStylesheets`), both GWT compiles, and full runs at 6 workers with the leak check: the first
  failed the AI chat's nine stories (their selectors), the second passed all 1,741 with no retries.
  Its screenshots were read group by group: 84 taken (the changes below); the jobs screen's showed
  the lost nudge, and after it was restored its and the nodes screen's 10 stories match their
  baselines.

> **Undone in wave 42** (30 October 2026): the user asked for the layout to stay as it was, to
> within a pixel; the look changes this wave took are put back there (see `wave-42.md`).

## Looks that changed

- Panels framed by `control-box` rather than `stroom-control` lose its 0.4rem padding and take the
  text colour they are in (the result stores and the framed expression editor are unchanged).
- A grid in a `control-box` has its header on the box's colour (the import confirmation, the
  document create permissions, the pathway's constraints), one shade from the dialog's.
- The identity forms are a little shorter while valid: a form group's feedback takes no room until
  there is something to say, where the bare labels always kept a line.

## Left

- Find in Content's pattern feedback and hint use the form group's classes outside one.
- dashboard-query-22's `result-table`/`stroom-dashboard-table` parts and widgets-1-24's alignment
  and icon column header classes go with wave 8's sheets.
