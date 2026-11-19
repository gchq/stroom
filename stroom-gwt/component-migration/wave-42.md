# Wave 42: UI audit wave 8, one block per sheet, and the layout put back

The eighth of the UI audit's waves ([../UI_AUDIT.md](../UI_AUDIT.md), theme T6); written 30 October
2026, after wave 41. It also puts back the layout waves 38 to 41 had let change.

## The rule this wave keeps (30 October 2026)

The user's direction: simplify the CSS, but keep the layout. Padding, sizes and positions stay as
they were, to within a pixel here and there, unless a change has a reason of its own. A screenshot
that changes in a CSS or refactoring wave is a regression to put right, not a baseline to take.
Waves 38 to 41 had taken such changes as following from their decisions; this wave undoes them.

**How it was checked:** the baselines are git-ignored and had been overwritten, so the commit before
the audit's first wave (`b70e37f6b3`) was extracted (`git archive`) and its stories run to make
reference screenshots of the UI as it was (all 1,746 pass; the visualisations' content and the Ace
editor's files, which the build makes, were given to it). Those are the baselines for every story
that still exists, and the current UI is run against them.

## One block per sheet (wave 8 proper)

`TestStylesheets`'s list of sheets still to split is empty and gone: every one of Stroom's sheets
styles only the block it is named after (another block may be the context), but for the shared
sheets (`layout-helpers.css`, `form-controls.css`, `font.css`, `conditional-formatting.css`) and
`vis.css`, whose classes (e.g. `vis-point`) are an interface the visualisation packs, kept in their
own repository and installed as content, style too.

- **The floor map and the traces** (floormap-pathways-stores-02 to 07, 09): `stroom-floormap.css`
  (1,312 lines) is twelve sheets, one per block, imported where it was; the trace overview's flat
  names are `trace-overview-chart`'s elements; the content store's details, the git repo settings
  (no element or id selectors) and the imports tab are a block each; the floor map's live regions
  use the shared `sr-only`.
- **The dashboard and query** (dashboard-query-16, 17, 19, 22, 25, widgets-1-24): the flex layout,
  text pane, filter cell, table filter column and markdown container have sheets of their own; the
  result table holds its own cells' rules; the grid's alignment and icon headers are `data-grid`'s
  modifiers and elements; the expression tree is `expression-tree`, its panel `expression-tree-panel`.
  The conditional formatting's `cf-*` classes keep their names: the docs copy that sheet.
- **The shell, sign-in and AI chat** (security-settings-08, 11, explorer-documents-shell-10, 28 to 30,
  widgets-1-20): the page's root is `app-page`, the navigation's parts its elements, the ref links a
  sheet each, the AI chat's model picker raises its icon with `drop-down--icon-raised`.
- **The widgets** (widgets-1-21, 22, widgets-2-15, 16): `base.css` has no class rules (a text field's
  are in `text-field.css`, the links' in their blocks' sheets); the calendar, curve tab layout, date
  and time view, dialog, popup and selection popup style their own elements; the drop-down's
  wrapper is `drop-down__wrapper`.
- **`doc-ref-link__text`** went from the cells that set it: nothing styled it but the old trace
  overview's rules.

## The layout put back

Run against the reference screenshots, 99 stories differed. Each cause was put right without
bringing back what the waves removed:

- **Panels that `stroom-control` framed** (wave 41's 19, among them Manage Activity, the volume
  groups, the query history and favourites, the step filter, the dictionary's imports, the floor
  map's settings, the script settings, the dashboard's include/exclude filter): `control-box--padded`
  (form-controls.css) gives them the field's padding, text colour and grid header colour again;
  Search Result Stores keeps its old override of the padding (`search-result-stores.css`).
- **Grids in boxes that were always `control-box`** (the import dialog, document create permissions,
  pathway constraints, node and user groups, the processor screens): `control-box` no longer sets the
  grid header's colour, so their headers are the dialog's again.
- **The sign-in pages and the name dialog:** `form-group--reserve-feedback` keeps a line under each
  field for its message whether or not there is one, as the old bare labels did, so the forms are
  their old height and centred as before.
- **Credential Edit's tabs:** `LinkTabPanel.setFlush` (`flush="true"`) puts its tab row flush with
  the dialog, as the bar was before wave 38 (the form below is 1px lower, within the rule).
- **User Preferences and the AI settings:** their tab bar and layer container are in their forms
  again (as before wave 38): inside `LinkTabPanel` the tab bar lays itself out 3.6px shorter, which
  no rule could put right without overriding the panel from outside. `LinkTabPanel` keeps hiding its
  toolbar row while it is empty.
- **The jobs screen's list heading** kept its nudge in wave 41 (`form-group--inset-label`).

## Checked

Compile and checkstyle of every client module, every client test (with `TestStylesheets`,
`TestTemplateBindings` and `TestStroomWidgetsOnly`), both GWT compiles, and a full run at 6 workers
with the leak check against the reference screenshots: all 1,741 stories pass, none on a second
attempt, and the only screenshots that differ are those kept below. The baselines are the
reference's for every story but those, which are taken from this run.

## Kept, with their reasons

- The XSD browser's Pattern heading (a bug fix, wave 36) and its two new stories.
- The data grid's Percent Columns story (its code now sizes columns as the app does) and Header
  Input story (it uses the app's own filter cell, 2px in).
- The visualisations that differ run to run, and the top bar's Keyboard story's tooltip (ISSUES.md,
  "Screenshots that differ in every run").
