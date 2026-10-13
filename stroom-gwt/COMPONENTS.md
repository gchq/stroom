# Component status and replacement plan

The UI's widgets, how far each is from its WAI-ARIA keyboard pattern and the BEM styling rules,
and the plan for replacing or fixing them. Each component is built and proved in its own
worktree; rolling it out to Stroom's screens happens on the main branch after that worktree is
merged. See `COMPONENT_MODEL_DESIGN.md` for the design rules, and `ISSUES.md` for the history of
each piece of work.

## How a component gets done

1. **Brief** (this file): the old classes, the WAI-ARIA pattern, replacement or retrofit, known
   gaps.
2. **Build** (worktree, branch `gh-<issue>-<description>`): the new widget beside the old one,
   added without changing any screen:
   - Java class(es) with `///` javadoc, `final` variables, British English.
   - Its own stylesheet, `<block>.css`, imported by `app.css`: BEM names, states from ARIA
     attributes (a state with no ARIA attribute is a block modifier, said so in the stylesheet),
     colours only from theme tokens.
   - Workbench stories: behaviour, keyboard, names and roles, a UiBinder template, and comparison
     stories showing the old widget above the new one with Stroom's real content.
   - Real-input checks (real keys and mouse) in the component's own file under
     `test-runner/lib/real-input/`.
   - No new a11y baseline entries; contrast measured in both themes where it draws lines or
     focus.
   - A section in `COMPONENT_MODEL_DESIGN.md`, and this file's row updated.
3. **Merge**: one worktree at a time, rebased on main; full workbench suite run after; a11y
   baseline and screenshot baseline regenerated, never merged by hand.
4. **Bridge** (where it can): old classes become thin subclasses or adapters of the new one, so
   most screens move without being edited (as `SvgButton extends ToolbarButton` did).
5. **Rollout** (main branch, after merge): screen by screen, with screenshot diffs before and
   after; old classes, CSS and workarounds deleted with their last use.

Status values: `not started`, `briefed`, `building`, `built`, `merged`, `rolling out`, `done`.

## Status

Uses are rough: the number of files in `stroom-core-client` and the dashboard modules that mention
the component's classes (some names also match related classes), to help prioritise. Cells with
`?` are settled by each component's research pass.

| Component | Old classes | New | Pattern | Replace / retrofit | Uses | Status | Known gaps |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Toolbars | `ButtonPanel`, `SvgButton`, `InlineSvgButton`, `InlineSvgToggleButton`, `SvgToggleButton`, `BaseSvgButton` | `Toolbar`, `ToolbarRow`, `ToolbarSeparator`, `ToolbarButton`, `ToolbarToggleButton`, `ButtonGroupFocus`, `FocusTooltip` | toolbar | replaced | ~100 | done | `SvgButton.create(Preset)` stays until `SvgPresets` goes; some toolbars unnamed; text/icon display preference (later); not tried with a screen reader |
| Floating action button | `FabButton` | — | button | retrofit | ~8 | in part (one tab stop) | BEM pass not done (`stroom-button-fab`, `face`) |
| Refresh button | `RefreshButton` | (rebuilt on `ToolbarButton`) | button | retrofit | pagers | done | — |
| Split panes | `ThinSplitLayoutPanel`, `MySplitLayoutPanel` | `SplitPane` | window splitter | replaced | ~43 (37 thin, 6 wide) | done: every view uses `SplitPane` (User Groups first, then by area, ending with the main screen's explorer and docked AI chat); the old panels, their CSS, their stories and the comparison stories are deleted | 2.5.8 where splitters meet in a T at a centre (accepted, see below); theme colours to revisit (line contrast relies on `--grey-600`); not tried with a screen reader or touch device; `ReadingOrder` deleted (no views need it now); views that call `onResize()` to force a relayout need checking (e.g. stepping) |
| Tab bars (documents) | `CurveTabBar`, `AbstractTabBar`, `CurveTabLayoutViewImpl` | — | tablist | retrofit (tied to `ContentTabPanePresenter`, document plugins) | every document | investigated, decisions below | one tab stop with Left/Right already (roving `tabindex`, shared with link tabs); no roles, selected state or panel link; no Home/End or wrap; Escape closes the focused tab and drops focus to the body; A/D/H/L also move; focus drawn as the hover tint; close cross is an unnamed `div`; sidebar toggle has no name |
| Link tabs (inside documents) | `LinkTabBar`, `LinkTabPanelPresenter` | — | tablist | retrofit | ~16 | investigated, decisions below | one tab stop with Left/Right already; no roles; separators read as "\|"; focus drawn as the hover underline; the light theme's widget stories draw the bar on a blue canvas, not the shell's white |
| Data grids | `MyDataGrid`, `MyCellTable`, `PagerViewImpl` | — | grid | probably retrofit (GWT cell table, many features) | ~78 (+14 cell tables, ~86 pagers) | not started | one tab stop with arrows inside; header and cell semantics; sortable headers are `role="button"` |
| Explorer tree | explorer tree (cell tree, `explorerCell`), `EntityTreePresenter`, pickers | — | tree | ? | ~66 | not started | tree roles and keyboard |
| Menus | `Menu`, `MenuPresenter`, menu items | — | menu / menubar | retrofit | ~19 places show menus | in part (items have `role="menuitem"`; `MenuRequest` lets widgets ask for one) | menu buttons don't say they open a menu (`ISSUES.md`) |
| Dialogs and popups | popup support, `ResizableDialog` | — | dialog | ? | ~142 | ? | focus is restored on close (`CurrentFocus`); trapping, naming and Escape to check |
| Quick filter | `QuickFilter` | — | search field | retrofit | ~83 | not started | help button below 24px (`target-size` baseline entries) |
| Form controls | `SelectionBox`, `CustomDatePicker`, `TickBox`, `ValueSpinner`, text inputs | — | combobox, grid (date), checkbox, spinbutton | ? | many (`SelectionBox` ~211, `TickBox` ~154, `ValueSpinner` ~58, dates ~16) | not started | controls with no accessible name (`ISSUES.md`, `label` baseline entries) |
| Code editor | Ace (`EditorPresenter`, `EditorViewImpl`) | — | — | retrofit around Ace | ~34 | not started | keyboard trap: Tab indents (`ISSUES.md`); Ace sources need human approval to change |
| Layout panels | `DockLayoutPanel` and friends | (grid/flex, `SplitPane`) | — | replace as screens are touched | few direct (most via split panels) | not started | DOM order differs from layout (`ReadingOrder` stopgap) |
| Content focus | tab pane | `ContentFocus` | — | added | all tabs | done (#5405) | — |

## Tab decisions

Agreed for both tab bars (document and link tabs), following the WAI-ARIA tabs pattern:

- **Roles.** `role="tablist"` with a name, `role="tab"` with `aria-selected` and `aria-controls`,
  and `role="tabpanel"` on the content.
- **Keys.** One tab stop on the selected tab (as now); Left and Right move between tabs and wrap at
  the ends; Home and End go to the first and last. Document tabs keep manual activation (arrows
  move focus, Enter or Space selects, as selecting can load a document); link tabs select as the
  arrows move. A/D/H/L no longer move between tabs.
- **Closing.** Delete closes the focused closable tab (Escape no longer does); Alt+W stays. Focus
  moves to the newly selected tab, not to the page.
- **Focus.** Shown only for keyboard focus (`:focus-visible`), as a 2px rounded ring in
  `--splitter__focus-color` (6.9:1 dark, 5.8:1 light against the shell): inside the edge of a
  document tab (the selected bar stays below it), and around a link tab's label, 4px past the text
  each side (the separators need about 2px more room). Hover keeps its tint (document tabs) and a
  thin underline (link tabs); selected keeps its bar (document tabs) and bold (link tabs).
- **To fix alongside.** The selected document tab's bar (`--blue-300`) is 2.2:1 on white in the
  light theme, below 3:1, and is the only cue for the selected tab there.

## Accepted deviations

- **Split panes, WCAG 2.5.8 (Target Size, Minimum) where splitters meet in a T.** A splitter's grab
  area is 7px, reaching 3px either side of its line, so where a nested split pane's splitter
  starts at another splitter's centre (e.g. User Groups' 50/50 split), the 24px circle 2.5.8
  centres on the outer splitter touches the inner one, and their grab areas overlap in a 7×3px
  patch (the inner splitter, later in the page, takes it). The guidance gives no exception for
  this: the overlapping area counts for neither target, which one is on top doesn't matter, and
  the only way out ("Equivalent") is a separate 24px control that resizes the panes, which was
  tried (a collapse button on each splitter) and dropped, as it sat over pane content. The
  guidance doesn't address splitters or long thin targets, and both can be grabbed along their
  whole length but for that patch, as with the old panels and most UIs. Accepted; the
  `Template` story pins it, so a change shows. A split moved more than 12px off the outer
  splitter's centre passes, if a screen ever needs to.

## Groundwork on main before any worktree

1. Done: `test-runner/lib/real-input/` has a file per component (`buttons.mjs`,
   `split-pane.mjs`), gathered by `index.mjs`, which refuses a story id found in two files; a
   new component adds its file and one import line there.
2. Consider the same for story registration (one registry file per component area instead of
   `WidgetsStories.java` alone).
3. Agree that `a11y-baseline.json` and the screenshot baseline are regenerated after each merge.
4. Fill in the `?` cells above (uses, replace or retrofit) with a short research pass per
   component; that pass becomes each worktree's brief.

## Order

1. Tab bars, both kinds: they shape the focus order of nearly every screen.
2. Data grids.
3. Explorer tree.
4. Menus, dialogs, quick filter, form controls, code editor, as their briefs are ready.
5. Then page-level focus-order stories, once the components that make up a page are rolled out.

Components that use each other (e.g. tabs inside split panes, grids using `ButtonGroupFocus`)
merge dependency first; the dependent worktree rebases onto it.

## Merge rules

- One worktree merged at a time; full workbench suite after each.
- Shared files are kept to additions: one import line in `app.css`, one registration line, a new
  `KeyBinding.Action` and binding, new theme tokens.
- Each worktree changes only its own row here and its own section in `COMPONENT_MODEL_DESIGN.md`
  and `ISSUES.md`.
- Before a pull request (by a human): `./gradlew clean build shadowJar buildDistribution` and
  `./buildDockerImages.sh`, plus a CHANGELOG entry via `./log_change.sh`.

## Running worktrees side by side

- Claude Code can run an agent in its own git worktree, working in parallel and leaving its
  changes there for review; the tool names the worktree's branch itself, so where a branch
  should follow `gh-<issue>-<description>`, a human creates it first and the agent works in it.
- Each needs its own GWT compile and build output (heavy on disk and CPU; a full story run is
  about six minutes), so two or three at once is realistic.
- `workbenchServe` uses port 6008 by default; give each worktree its own with
  `-PworkbenchPort=NNNN`. The test runner picks a free port itself.
