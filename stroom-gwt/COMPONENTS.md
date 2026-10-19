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
| Toolbars | `ButtonPanel`, `SvgButton`, `InlineSvgButton`, `InlineSvgToggleButton`, `SvgToggleButton`, `BaseSvgButton`, `TableExpandButton`, `TableCollapseButton`, `HelpButton`, `Preset`, `SvgPresets` | `Toolbar`, `ToolbarRow`, `ToolbarSeparator`, `ToolbarButton`, `ToolbarToggleButton`, `ButtonGroupFocus`, `FocusTooltip`, `GroupLevelButtons` | toolbar | replaced | ~100 | done: every icon button is made in a toolbar (`toolbar.button`, `toolbar.toggle`, `toolbar.refresh` or a `<b:Toolbar>` child); the old button classes, `ButtonPanel`, `PagerView.addButton` and `Preset`/`SvgPresets` are deleted | buttons are 22px with a 2px gap, under 2.5.8's 24px (most of the `target-size` baseline entries); some toolbars unnamed; text/icon display preference (later); not tried with a screen reader |
| Floating action button | `FabButton` | — | button | retrofit | ~8 | in part (one tab stop) | BEM pass not done (`stroom-button-fab`, `face`) |
| Refresh button | `RefreshButton` | (a `ToolbarButton`, made by `Toolbar.refresh`) | button | retrofit | pagers, data navigators, visualisations | done (a visualisation's pause button is in a toolbar of its own) | — |
| Split panes | `ThinSplitLayoutPanel`, `MySplitLayoutPanel` | `SplitPane` | window splitter | replaced | ~43 (37 thin, 6 wide) | done: every view uses `SplitPane` (User Groups first, then by area, ending with the main screen's explorer and docked AI chat); the old panels, their CSS, their stories and the comparison stories are deleted | 2.5.8 where splitters meet in a T at a centre (accepted, see below); theme colours to revisit (the line is drawn in `--border-color`, as other borders are: 1.3:1 dark, 1:1 light, under the 3:1 of 1.4.11); not tried with a screen reader or touch device; `ReadingOrder` deleted (no views need it now); views that call `onResize()` to force a relayout need checking (e.g. stepping) |
| Dashboard splitters | `FlexLayout`, `Splitter` | (the same classes, with `FocusMemory`, `LayoutSizes` and `flex-layout.css`) | window splitter | retrofit (not `SplitPane`: a dashboard resizes on release, and its splitters must sit above the visualisations' floating frames) | every dashboard | built: splitters are tab stops in layout order (each after the panel before it, the dashboard's own width and height last), named for the panels either side ("Resize Query and Table"), with a value; arrow keys, Shift, Home and End; the focus is kept when the layout is rebuilt (maximise, restore); BEM CSS (`flex-layout.css`) | drags are mouse only (no touch or pen) and there is no live resize; Tab from a visualisation's frame goes to the end of the page; a splitter's name isn't refreshed when a tab is renamed while it has the focus; no real-input checks (drags are tested with synthetic events); line and focus contrast not measured; new `label` and `frame-title` baseline entries (from the dashboard content the stories show); not tried with a screen reader |
| Tab bars (documents) | `CurveTabBar`, `AbstractTabBar`, `CurveTabLayoutViewImpl` | — | tablist | retrofit (in `AbstractTabBar`, shared with link tabs) | every document | in part: tab list, keys (and the tab menu from the keyboard), focus ring, panel link, sidebar toggle name and BEM CSS (`tab-bar.css`) done | left: link tabs are about 16px tall, under 2.5.8's 24px (baselined for the Data Retention impact stories, where one sits above a toolbar); link tab bars have no tab list name; a tab panel holding nothing focusable isn't a tab stop; a document tab's name is cut at 50 characters (with "..."), so a screen reader hears the shortened name; a tab's loading spinner isn't announced |
| Link tabs (inside documents) | `LinkTabBar`, `LinkTabPanelPresenter` | — | tablist | retrofit | ~16 | in part: as document tabs (shared `AbstractTabBar`) | as document tabs |
| Data grids | `MyDataGrid`, `MyCellTable`, `PagerViewImpl` | — | grid | probably retrofit (GWT cell table, many features) | ~78 (+14 cell tables, ~86 pagers) | not started | one tab stop with arrows inside; header and cell semantics; sortable headers are `role="button"`; cells get their events after the browser has handled them (`AbstractHasData.onBrowserEvent` defers them), so a cell can't `preventDefault()` one, and touch events don't reach cells |
| Explorer tree | explorer tree (cell tree, `explorerCell`), `EntityTreePresenter`, pickers | — | tree | ? | ~66 | not started | tree roles and keyboard |
| Menus | `Menu`, `MenuPresenter`, menu items | — | menu / menubar | retrofit | ~19 places show menus | in part (items have `role="menuitem"`; `MenuRequest` lets widgets ask for one) | menu buttons don't say they open a menu (`ISSUES.md`) |
| Dialogs and popups | popup support, `ResizableDialog` | — | dialog | ? | ~142 | ? | focus is restored on close (`CurrentFocus`); trapping, naming and Escape to check |
| Quick filter | `QuickFilter` | — | search field | retrofit | ~83 | in part: Clear and syntax help are a toolbar (`quickFilter-toolbar`, one tab stop), in the form help icon's colour, Clear with its own icon (`CLEAR_OUTLINE`) | buttons below 24px (`target-size` baseline entries); not a `role="search"` or `type="search"` field; its syntax help is its own popup, not the shared `help-popup`; BEM pass not done (`quickFilter-…` classes) |
| Form groups | `FormGroup`, `FormLabel`, `HelpButton` | `FormGroupHelpButton` | — | retrofit | most forms | done: its own help button (out of the tab order, F1 shows the same help), BEM names (`form-group__label-row`, `__label`, `__help`, `__description`, `__feedback`, …) in `form.css`, help popup `help-popup` | the disabled description's contrast (baselined, `ISSUES.md`) |
| Password box | `PasswordTextBox` and a copied show/hide overlay | `PasswordBox` | text field with a toggle button | replaced | 9 fields | done: one control for every password field, its show/hide button in a toolbar and named for what it does, with a live region | — |
| Selection box | `SelectionBox`, `BaseSelectionBox`, `SelectionPopup`, `SelectionList`, `SvgIconBox` | (the same classes, with `selection-box.css` and `selection-popup.css`) | combobox | retrofit | ~211 | built: the WAI-ARIA combobox's keys (Enter, Down, Up and Alt+Down open the list, and Space and typing a character in a box that can't be typed in; in the list, Enter or Alt+Up chooses, Escape closes, and typing goes to the list's quick filter, or to the next option starting with what was typed); its drop-down arrow is the value spinner's (`SvgIconBox.setDropDownWidget`, a picture hidden from screen readers), as is QuickFilter's Recent Filters arrow; its value stays on one line, ending in an ellipsis clear of the arrow; BEM CSS (`selection-box.css`, `selection-popup.css`, `svg-icon-box.css`); `SelectionBox.css`, the global `pointer` class and `DROP_DOWN` are deleted | no accessible name in places (`label` baseline entries); not tried with a screen reader; the list's own classes (`selectionList-*`, in `components.css`) aren't BEM yet; Home and End in the closed box don't open the list at its ends (optional in the pattern) |
| Tick box | `TickBox`, `CustomCheckBox` | — | checkbox | ? | ~154 | not started | no accessible name in places, e.g. in grid rows (jobs and nodes screens; `label` baseline entries) |
| Value spinner | `ValueSpinner`, `Spinner`, `ValueSpinnerCell` | (the same classes, with `value-spinner.css`) | spinbutton | retrofit | ~58 | built: the field is a spin button (`aria-valuenow`, `-min`, `-max`, `-valuetext`) with Up, Down, Page Up, Page Down, Home and End; the arrows are 24px pointer-only targets inside its border (no tab stop, hidden from screen readers; mouse, finger or pen); a rejected value is put back and explained (in the form group's feedback, or under the field) and announced; BEM CSS (`value-spinner.css`); the component's own `label` baseline entries are gone, and no screen story has an unnamed spinner; the data grid's `ValueSpinnerCell` looks the same (the widget's markup and stylesheet, `value-spinner--cell`, in 110px columns with 28px rows), its field a named spin button with the same keys while it has the focus, and its arrows step it with the mouse (a finger's tap steps once) | not tried with a screen reader (VoiceOver on iOS may not step a custom spin button by swiping, though the value can still be typed); Home and End go to the bounds rather than moving the caret; its default width is now 96px (was 60px), for screens to check at rollout; the grid's spinner can't be reached from the keyboard (its field is `tabindex="-1"`, waiting on the data grid's keyboard pattern), is named by its column but not its row, changes a typed value silently (non-digits dropped, then kept within its bounds), doesn't repeat while a finger is held, and can't stop Page Up and Page Down scrolling, as the grid hands a cell its events after the browser has handled them |
| Date and time | `DateTimeBox`, `TimeBox`, `CustomDatePicker`, `DefaultMonthSelector`, `SvgIconBox` | — | text field, grid (calendar) | ? | ~16 | in part: the calendar's previous and next buttons are named "Year" and "Month" toolbars, one tab stop each; the year and month rows are 4px apart, so a box's focus ring doesn't cover the other; the calendar and clock icons are `SvgIconBox` pictures (BEM, `svg-icon-box.css`), hidden from screen readers, as Enter in the field opens the same dialog | the calendar grid's keys and names; no accessible name in places (`label` baseline entries) |
| Text inputs | `TextBox`, `TextArea`, `LineColInput` | — | text field | retrofit | many | not started | no accessible name in places (`label` baseline entries) |
| Code editor | Ace (`EditorPresenter`, `EditorViewImpl`) | — | — | retrofit around Ace | ~34 | not started | keyboard trap: Tab indents (`ISSUES.md`); Ace sources need human approval to change |
| Layout panels | `DockLayoutPanel` and friends | (grid/flex, `SplitPane`) | — | replace as screens are touched | 6 (the floor map views) | in part: every view had its `DockLayoutPanel` replaced with grid or flex layout (`9dc39a1829`), but the floor map views added since use it again (`FloorMapEditorViewImpl`, `FloorMapFactListViewImpl`, `FloorMapGroupsViewImpl`, `FloorMapTimeListViewImpl`, `FloorMapTrackingViewImpl` and the template `FloorMapMapViewImpl.ui.xml`) | DOM order can differ from layout where a panel places its children (`ReadingOrder` is deleted, as no split panel is left); the floor map views not checked |
| Content focus | tab pane | `ContentFocus` | — | added | all tabs | done (#5405) | — |

## Tab decisions

Agreed for both tab bars (document and link tabs), following the WAI-ARIA tabs pattern:

- **Roles.** `role="tablist"` with a name, `role="tab"` with `aria-selected` and `aria-controls`,
  and `role="tabpanel"` on the content.
- **Keys.** One tab stop on the selected tab (as now); Left and Right move between tabs and wrap at
  the ends; Home and End go to the first and last. Arrows move the focus, and Enter or Space
  selects, on both bars: selecting a tab can load a document, and many link tabs load too (the
  data viewer's tabs fetch from the server; document sub-tabs build their content on first view).
  A/D/H/L no longer move between tabs.
- **Closing.** Delete closes the focused closable tab (Escape no longer does); Alt+W stays. Focus
  moves to the newly selected tab, not to the page.
- **Menu.** The context menu key or Shift+F10 opens the focused tab's menu (Close Others and so
  on), below the tab, as a right click does; Escape gives the focus back to the tab.
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
2. Form controls (selection box, tick box, value spinner, date and time, text inputs): most of the
   `label` baseline entries, and the controls that grids hold in their cells.
3. Data grids.
4. Explorer tree.
5. Menus, dialogs, quick filter, code editor, as their briefs are ready.
6. Then page-level focus-order stories, once the components that make up a page are rolled out.

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
