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
   - The fewest elements the pattern and the layout need: no wrappers that only carry a style.
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

| Component | Session | Old classes | New | Pattern | Replace / retrofit | Uses | Status | Known gaps |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Text inputs | 1 Inputs | `TextBox`, `TextArea`, `LineColInput` | (the same classes, with `text-field.css` and `linecolinput.css`) | text field | retrofit | many (~132 text boxes and ~39 text areas in templates) | built: every text box and text area is the BEM block `text-field` (a text area with `text-field--multiline`), given by Stroom's copies of GWT's `TextBox` and `TextArea` (one line each, approved), in its own stylesheet (`text-field.css`), which has all its looks (size, disabled, read only, keyboard focus, invalid from `aria-invalid`, placeholder, browser autofill), each at the weight of the rule it replaced, as `stroom.css`'s rules for bare inputs leave text fields out; the password box's input is a text field too; radio buttons' looks are in `radio-button.css`; the `.gwt-TextBox` and `.gwt-TextArea` rules in `stroom.css`, `standard.css` and `standard_mod.css` are gone; `LineColInput` is the block `line-col-input`, marking a value that is no position `aria-invalid` (it was red text from a `highlight` class); unnamed fields on Stroom's screens named (the dashboard's Text Input and Key Value Input, for their component; the annotation editor's Title and Subject; a pipeline property's Value; Query Info; Dependency Information; the default activity form's fields, in their labels), and the annotation history's copy box hidden from screen readers; a read-only field is only `[readonly]` (Stroom's copy of GWT's `ValueBoxBase` no longer adds a `<primary>-readonly` class, approved), and an invalid one only `aria-invalid` (`FieldValidity` no longer adds an `invalid` class, and `stroom.css`'s fallback uses the attribute too) | `stroom.css` still gives bare `input`, `textarea` and `select` elements the field look, for inputs with no block of their own (hand-built forms, checkboxes, file inputs, and nine text fields whose template replaces the class with `styleName`: the pager's two page boxes, the query data box, the annotation editor's Title, Subject and comment, two include/exclude filter areas and Create Multiple Users' batch); a site's own activity form needs its own labels; not tried with a screen reader |
| Password box | 1 Inputs | `PasswordTextBox` and a copied show/hide overlay | `PasswordBox` | text field with a toggle button | replaced | 9 fields | done: one control for every password field, its show/hide button in a toolbar and named for what it does, with a live region; its input is a text field, given by Stroom's copy of GWT's `PasswordTextBox` (approved) in place of `gwt-PasswordTextBox` (`text-field.css` gives its looks, `password-box.css` its room for the button and badge), and the `.gwt-PasswordTextBox` rules in `standard.css` and `standard_mod.css` are gone | not tried with a screen reader |
| Quick filter | 1 Inputs | `QuickFilter` | (the same class, with `quick-filter.css`) | search field | retrofit | ~83 | done: a search field (`type="search"`, so a `searchbox`; the browser's own clear button hidden, as it has its own), BEM (`quick-filter`, `__input`, `__toolbar`, `__recent`, the `--recent` modifier, and `quick-filter-layout` for a panel holding one above what it filters) in its own stylesheet, with the column function editor's and query help's flat look and the selection list's gap moved into it; Clear and syntax help are a toolbar (one tab stop) of 24px buttons, in the form help icon's colour, Clear with its own icon (`CLEAR_OUTLINE`); every quick filter is named for what it filters (e.g. "Filter users and groups"; a selection box's list's, "Filter" and the box's name), by its form group's label, its template (`accessibleName`) or its presenter (`setAccessibleName` on the shared quick filter views) | its syntax help is its own popup (`quickFilter-tooltip`), not the shared `help-popup`, and its Advanced Query dialog (`advancedQuickFilter-*`) isn't BEM: both are done with the popups and dialogs (session 3); it hasn't been tried with a screen reader |
| Form groups | 1 Inputs | `FormGroup`, `FormLabel`, `HelpButton` | `FormGroupHelpButton` | — | retrofit | most forms | done: its own help button (out of the tab order, F1 shows the same help), BEM names (`form-group__label-row`, `__label`, `__help`, `__description`, `__feedback`, …) in `form.css`, help popup `help-popup`; feedback can have a short form, shown, and a full one, its tooltip and what screen readers read (`setFeedback(short, full)`) | the disabled description's contrast (baselined, `ISSUES.md`) |
| Selection box | 1 Inputs | `SelectionBox`, `BaseSelectionBox`, `SelectionPopup`, `SelectionList`, `SvgIconBox` | (the same classes, with `selection-box.css` and `selection-popup.css`) | combobox | retrofit | ~211 | built: the WAI-ARIA combobox's keys (Enter, Down, Up and Alt+Down open the list, and Space and typing a character in a box that can't be typed in; in the list, Enter or Alt+Up chooses (moving only highlights, as a combobox's list does), Escape closes, Tab closes it leaving the value and goes on to what follows the box, Shift+Tab closes it and goes back to the box, and typing goes to the list's quick filter, or to the next option starting with what was typed); its drop-down arrow is the value spinner's (`SvgIconBox.setDropDownWidget`, a picture hidden from screen readers), as is QuickFilter's Recent Filters arrow; its value stays on one line, ending in an ellipsis clear of the arrow; BEM CSS (`selection-box.css`, `selection-popup.css`, `svg-icon-box.css`); `SelectionBox.css`, the global `pointer` class and `DROP_DOWN` are deleted | not tried with a screen reader; the list's own classes (`selectionList-*`, in `components.css`) aren't BEM yet; Home and End in the closed box don't open the list at its ends (optional in the pattern) |
| Document and user pickers | 1 Inputs | `DocSelectionBoxPresenter`, `UserRefSelectionBoxPresenter`, `DropDownView`, and their dialogs (`ExplorerPopupPresenter`, `UserRefPopupPresenter`) | (the same classes, with `drop-down.css`) | button opening a modal dialog (a quick filter over a tree, or a grid) | retrofit | ~49 (37 document, 12 user) | built: the picker is a button (`aria-haspopup="dialog"`), Enter or Space opening its dialog, which is now modal (glass, `aria-modal`, the focus kept in it). In the dialog: the focus starts in the quick filter, Down goes to the chosen item, Up from the first back to the filter, and typing in the list goes to the filter; moving chooses (selection follows focus), announced in a live region ("Normalise chosen"); tabbing into the list lands on the chosen item; Enter on an item chooses it and closes the dialog (on the grid, the row with the focus, even one only highlighted after filtering); Escape cancels, putting back what was chosen, and the focus goes back to the picker. The lists are named ("Documents", "Users and groups"). The picker is the BEM block `drop-down` in its own stylesheet (`drop-down.css`; its rules are gone from `components.css`), its value filling the box and ending in an ellipsis clear of the icon (it was cut off mid-letter). A document that can't be found is shown as a problem with the field, as #5409's form feedback is: the picker is `aria-invalid` with the red border, and a short message is under it ("'Deleted Feed' can't be found, or you don't have permission to see it."), in its form group's feedback or a line of its own (`drop-down__feedback`), the full message (with the id, and what to do) its tooltip and what screen readers read (`FormGroup.setFeedback(short, full)`); the warning button (17px, mouse only, inside the picker's button) and its alert are gone | the tree and grid have no roles or states yet (sessions 5 and 4); not tried with a screen reader |
| Tick box | 1 Inputs | `CustomCheckBox`, `TickBoxCell` | (the same classes, with `tick-box.css`) | checkbox | retrofit | ~154 (~124 widgets, ~21 grid cells) | built: the BEM block `tick-box` (`tick-box.css`; `tickbox.css` and `tickboxcell.css` are deleted). The widget is a `label` wrapping its native check box (`tick-box__input`, unseen over the box), the drawn box (`tick-box__mark`) and its text (`tick-box__label`): one wrapper fewer (the `checkbox-container` div is gone), and clicking anywhere on it ticks it. Its states are the check box's (`:checked`, `:disabled`, `aria-readonly`), with no `-disabled` or `-readonly` classes. The grid's cell is drawn the same way (`tick-box--cell`, an icon's size), with `role="checkbox"`, `aria-checked` (true, false or mixed) and `aria-readonly` giving its look (the read-only `NoBorderAppearance` needs no class of its own now), its part-ticked square drawn by the stylesheet; it has the widget's blue (`--blue-500`) and tick, where it had Bootstrap's darker blue and a tick of its own; `ColumnValueCell` draws the cell's markup too. The widget looks as it did (screenshots unchanged) | the grid cell can't be reached from the keyboard (session 4, the grid's keyboard pattern); the widget has no part-ticked state (the cell's only, as agreed); not tried with a screen reader |
| Value spinner | 1 Inputs | `ValueSpinner`, `Spinner`, `ValueSpinnerCell` | (the same classes, with `value-spinner.css`) | spinbutton | retrofit | ~58 | built: the field is a spin button (`aria-valuenow`, `-min`, `-max`, `-valuetext`) with Up, Down, Page Up, Page Down, Home and End; the arrows are 24px pointer-only targets inside its border (no tab stop, hidden from screen readers; mouse, finger or pen); a rejected value is put back and explained (in the form group's feedback, or under the field) and announced; BEM CSS (`value-spinner.css`); the component's own `label` baseline entries are gone, and no screen story has an unnamed spinner; the data grid's `ValueSpinnerCell` looks the same (the widget's markup and stylesheet, `value-spinner--cell`, in 110px columns with 28px rows), its field a named spin button with the same keys while it has the focus, and its arrows step it with the mouse (a finger's tap steps once) | not tried with a screen reader (VoiceOver on iOS may not step a custom spin button by swiping, though the value can still be typed); Home and End go to the bounds rather than moving the caret; its default width is now 96px (was 60px), for screens to check at rollout; the grid's spinner can't be reached from the keyboard (its field is `tabindex="-1"`, waiting on the data grid's keyboard pattern), is named by its column but not its row, changes a typed value silently (non-digits dropped, then kept within its bounds), doesn't repeat while a finger is held, and can't stop Page Up and Page Down scrolling, as the grid hands a cell its events after the browser has handled them |
| Colour box | 1 Inputs | `ColourBox` | (the same class, with `colour-box.css`) | a native colour input | retrofit | 3 (the floor map's layer style, group and object editors) | done: a native `input type="color"`, so its role, keys and focus are the browser's; every use is named; the BEM block `colour-box`, as high as a text field and 3rem wide unless the screen sizes it (it had no class, only `stroom.css`'s bare input rules), with its swatch, focus, disabled and dark looks; the unused `ColourInputCell` and its stylesheet are deleted; checked on 2026-10-19 in all 3 uses ("Colour" in the layer style and group editors, "Area fill colour" in the object editor) | it hasn't been tried with a screen reader |
| File upload | 1 Inputs | `CustomFileUpload` | (the same class, with `file-upload.css`) | button (a native file input) | retrofit | 4 | done: the button opens the hidden file input; to its form group it is a picker (`aria-haspopup="dialog"`), so it is named by the group's label and its own text ("File, Choose File"), described by its help, and marked invalid by its feedback; it is described by the chosen file's name, "No file chosen" until one is, which is announced when it changes; BEM `file-upload`, `__button`, `__name` (`--empty`), `__input`; checked on 2026-10-19 in all 4 uses (data upload, import, a visualisation's assets, a key store), each named by its label and "Choose File" and described by the file and its help | it hasn't been tried with a screen reader |
| Icon picker | 1 Inputs | `FloorMapIconPicker` | (the same class, with `floormap-icon-picker.css`) | radio group | retrofit | 1 (the floor map's layer style) | done: a `radiogroup` named by its row, each icon a label holding an unseen native radio button named by its text, so it is one tab stop with the arrow keys choosing (it was mouse only); the chosen icon and the focused one are drawn from the radio button's state, the focus as every control's; disabled with its row; checked on 2026-10-19: a `radiogroup` "Icon" of 21 named radios, 4 elements an icon | it hasn't been tried with a screen reader; the arrow keys move through the icons in order (a radio group's), not up and down the grid |
| Time zone | 1 Inputs | `TimeZoneWidget` | — | a selection box and value spinners | retrofit | ~4 | done: its parts are a selection box and value spinners; the offset's hours spinner is named by its "Time Zone Offset" label (it was only "Hours"); the minutes spinner, which was always hidden as browsers don't support minute offsets, and its wrapper are gone, the minutes kept as they were set, so a time zone that has them keeps them | — |
| Radio group | 1 Inputs | `RadioButton` | `RadioGroup` (`stroom.widget.form.client`, with `radio-button.css`) | radio group | replace | 1 (the floor map layer style's graphic) | done: a `radiogroup` of native radio buttons sharing a name (one tab stop, the arrow keys choosing), each labelled by its text, the group by its form group's label (which keeps its role) or `setAccessibleName`; BEM `radio-group`, `__option`, `__input`, `__label`; GWT's `gwt-RadioButton` rules are gone, and every radio input (an activity's form has its own) still looks like these | it hasn't been tried with a screen reader |
| Term editor | 1 Inputs | `TermEditor`, `OperatorEditor` | — | (fields inside the expression tree) | retrofit, with the expression tree | ~4 (every expression) | not started | `TermEditor.css` not BEM (`termEditor-*`); its keys go with the expression tree's; its value, from and to fields are GWT's own `SuggestBox` (a text box with a suggestion popup, not a WAI-ARIA combobox, fed by `AsyncSuggestOracle`), to be selection boxes that can be typed in (`setAllowTextEntry(true)`, as the dashboard's list input is) with an async model of the suggestions (as its field box's `FieldSelectionListModel` is); for that, typing in such a box should open its list filtered by what was typed (a combobox with list autocomplete), where now the list opens unfiltered and is filtered by typing again in its quick filter |
| Raw GWT controls | 1 Inputs | `ListBox`, `IntegerBox`, `RadioButton`, `CheckBox` | (the controls above, and the radio group) | — | replace | 3 left of 12 (the explorer's tag lists), on 2 screens | 9 replaced (2026-10-19): the Elastic cluster's connection and response timeouts are value spinners (`ElasticClusterSettingsViewImpl`; a blank `IntegerBox` failed on save); the floor map's value format (`FloorMapSettingsPresenter`) and scale unit (`FloorMapSetScaleViewImpl`) are selection boxes; its group's candidates (`FloorMapGroupEditViewImpl`) and an area's fill Default (`FloorMapObjectEditViewImpl`) are tick boxes; its layer style's Shape, Icon and Image (`FloorMapLayerStyleViewImpl`) are a radio group | the explorer's Edit Tags (two) and Remove Tags lists are still `ListBox`es (multi-select lists tags move between: `ExplorerNodeEditTagsViewImpl`, `ExplorerNodeRemoveTagsViewImpl`), to be selection lists with the selection list (session 4); the floor map settings' value format has no story (its presenter needs the whole document editor); the term editor's `SuggestBox`es are its row's |
| Date and time | 2 Date and time | `DateTimeBox`, `TimeBox`, `CustomDatePicker`, `DefaultMonthSelector`, `SvgIconBox`, `MyDateBox`, `CustomDateBox`, `DurationPicker` | — | text field, grid (calendar) | ? | ~16 (+17 date boxes, 15 duration pickers) | in part: the calendar's previous and next buttons are named "Year" and "Month" toolbars, one tab stop each; the year and month rows are 4px apart, so a box's focus ring doesn't cover the other; the calendar and clock icons are `SvgIconBox` pictures (BEM, `svg-icon-box.css`), hidden from screen readers, as Enter in the field opens the same dialog | the calendar grid's keys and names; no accessible name in places (`label` baseline entries) |
| Schedule box | 2 Date and time | `ScheduleBox` | — | text field with a dialog button | retrofit | ~9 | not started | `ScheduleBox.css` not BEM (`ScheduleBox-*`) |
| Time range selector | 2 Date and time | `TimeRangeSelector`, `TimeRangePopup` | — | combobox or button opening a dialog | ? | ~4 (every query) | not started | styles in `components.css` (`timeRange-*`), not BEM |
| Menus | 3 Overlays | `Menu`, `MenuPresenter`, `MenuViewImpl`, `MenuItemCell`, `MenuOpener`, `EditorMenuPresenter`, context menus (`HasContextMenus`, `HasContextMenusCell`), the grid's `ActionMenuCell` and `HoverActionMenuCell` | — | menu / menubar, and menu buttons | retrofit | ~19 places show menus | in part (items have `role="menuitem"`; `MenuRequest` lets widgets ask for one) | menu buttons don't say they open a menu (`ISSUES.md`); BEM pass not done (`menuBar`, `menuItem`) |
| Dialogs | 3 Overlays | `ResizableDialog`, `Dialog`, `DialogButtons`, `PopupManager`, `PopupSupportImpl`, alerts and confirmations (`CommonAlertPresenter`) | — | dialog (modal), alertdialog | retrofit | ~142 | in part: `role="dialog"`, `aria-modal`, named by its title (`aria-labelledby`); the focus is put back on close (`CurrentFocus`); toolbars in a dialog keep their role, though it is laid out hidden (`ButtonGroupFocus`); a modal dialog keeps the focus in it, Tab from its last tab stop going to its first and Shift+Tab back (`DialogFocusTrap`) | only 27 of about 160 popups are modal (`.modal(true)`), so the rest, most OK/Cancel dialogs among them, let the page behind be used and Tab leave them: which should be modal is to decide; Escape to check; alerts aren't `alertdialog`s; BEM pass not done (`dialog-*`, `resizableDialog-*`) |
| Popups | 3 Overlays | `SimplePopup`, `SimplePopupLayout`, `AbstractPopupPanel`, `Popup`, `IndicatorPopup`, `TextBoxPopup`, `ExplorerPopupPresenter`, the popup positioner | — | non-modal dialog, or none, case by case | retrofit | ? | not started | a popup that belongs to an input control is in that control's row (`SelectionPopup` and `DocSelectionPopup` with the selection box; `DateTimePopup`, `TimePopup`, `SchedulePopup` and `TimeRangePopup` with date and time); a lot of nesting (`popupContent` > `simplePopup-container` > two backgrounds > content); BEM pass not done (`simplePopup-*`, `popupSupport-*`) |
| Tooltips and help popups | 3 Overlays | `FocusTooltip`, `TooltipPresenter`, the form group's help popup, the quick filter's syntax help | — | tooltip, non-modal dialog | retrofit | every button, most forms | in part: the form group's help popup is shared (`help-popup`) | the quick filter's syntax help is a popup of its own, not `help-popup`; a tooltip takes a button's title while it shows |
| Data grids | 4 Grids | `MyDataGrid`, `PagerViewImpl` | — | grid | probably retrofit (GWT cell table, many features) | ~78 (+~86 pagers) | not started | one tab stop with arrows inside; header and cell semantics; sortable headers are `role="button"`; cells get their events after the browser has handled them (`AbstractHasData.onBrowserEvent` defers them), so a cell can't `preventDefault()` one, and touch events don't reach cells |
| Cell tables | 4 Grids | `MyCellTable` | — | grid (or table, where read only) | ? | ~14 | not started | as data grids: no header or cell semantics, no keyboard inside; whether each one is a grid or a plain table is for its research pass |
| Explorer tree | 5 Trees | explorer tree (cell tree, `explorerCell`), `EntityTreePresenter`, pickers | — | tree | ? | ~66 | not started | tree roles and keyboard |
| Expression tree | 5 Trees | `ExpressionTreePresenter`, `ExpressionTreeViewImpl`, `ExpressionTreePanel`, `ExpressionItemBox`, `TermEditor` | — | tree | ? | every query, dashboard query and filter (~17 files) | not started | no roles, names or keys; terms are edited in place, so the tree's keys and the term editor's fields need working out together |
| Pipeline tree | 5 Trees | `PipelineTreePresenter`, `PipelineTreeViewImpl`, `PipelineTreePanel`, `PipelineElementBox`, `DraggableTreePanel` | — | tree | retrofit | pipeline structure editor, stepping filter dialog | building, on branch `stepping_display` (not on this branch): a `tree` with `treeitem`s (level, position, selected), arrow keys, and moving elements with Ctrl+X and Ctrl+V (a keyboard alternative to dragging) | here: no roles or keys, mouse-only drag; on `stepping_display`: `PipelineElementBox.css` not BEM |
| Pipeline bar | 5 Trees | (the stepping screen's pipeline tree) | `PipelinePath`, `PipelinePathViewImpl`, `PipelinePathPresenter` | toolbar, with menu buttons at forks | replaced (stepping screen only) | stepping screen | building, on branch `stepping_display` (not on this branch): a chevron bar showing one path, with drop downs at forks, severity icons, forced colours and announced progress; BEM CSS (`pipeline-path.css`) | not on this branch; not tried with a screen reader |
| Code editor | 6 Code editor | Ace (`EditorPresenter`, `EditorViewImpl`) | — | — | retrofit around Ace | ~34 | not started | keyboard trap: Tab indents (`ISSUES.md`); Ace sources need human approval to change |
| Toolbars | Tidy-ups | `ButtonPanel`, `SvgButton`, `InlineSvgButton`, `InlineSvgToggleButton`, `SvgToggleButton`, `BaseSvgButton`, `TableExpandButton`, `TableCollapseButton`, `HelpButton`, `Preset`, `SvgPresets` | `Toolbar`, `ToolbarRow`, `ToolbarSeparator`, `ToolbarButton`, `ToolbarToggleButton`, `ButtonGroupFocus`, `FocusTooltip`, `GroupLevelButtons` | toolbar | replaced | ~100 | done: every icon button is made in a toolbar (`toolbar.button`, `toolbar.toggle`, `toolbar.refresh` or a `<b:Toolbar>` child); the old button classes, `ButtonPanel`, `PagerView.addButton` and `Preset`/`SvgPresets` are deleted | buttons are 22px with a 2px gap, under 2.5.8's 24px (most of the `target-size` baseline entries); some toolbars unnamed; text/icon display preference (later); not tried with a screen reader |
| Floating action button | Tidy-ups | `FabButton` | — | button | retrofit | ~8 | in part (one tab stop) | BEM pass not done (`stroom-button-fab`, `face`) |
| Refresh button | Tidy-ups | `RefreshButton` | (a `ToolbarButton`, made by `Toolbar.refresh`) | button | retrofit | pagers, data navigators, visualisations | done (a visualisation's pause button is in a toolbar of its own) | — |
| Split panes | Tidy-ups | `ThinSplitLayoutPanel`, `MySplitLayoutPanel` | `SplitPane` | window splitter | replaced | ~43 (37 thin, 6 wide) | done: every view uses `SplitPane` (User Groups first, then by area, ending with the main screen's explorer and docked AI chat); the old panels, their CSS, their stories and the comparison stories are deleted | 2.5.8 where splitters meet in a T at a centre (accepted, see below); theme colours to revisit (the line is drawn in `--border-color`, as other borders are: 1.3:1 dark, 1:1 light, under the 3:1 of 1.4.11); not tried with a screen reader or touch device; `ReadingOrder` deleted (no views need it now); views that call `onResize()` to force a relayout need checking (e.g. stepping) |
| Dashboard splitters | Tidy-ups | `FlexLayout`, `Splitter` | (the same classes, with `FocusMemory`, `LayoutSizes` and `flex-layout.css`) | window splitter | retrofit (not `SplitPane`: a dashboard resizes on release, and its splitters must sit above the visualisations' floating frames) | every dashboard | built: splitters are tab stops in layout order (each after the panel before it, the dashboard's own width and height last), named for the panels either side ("Resize Query and Table"), with a value; arrow keys, Shift, Home and End; the focus is kept when the layout is rebuilt (maximise, restore); BEM CSS (`flex-layout.css`) | drags are mouse only (no touch or pen) and there is no live resize; Tab from a visualisation's frame goes to the end of the page; a splitter's name isn't refreshed when a tab is renamed while it has the focus; no real-input checks (drags are tested with synthetic events); line and focus contrast not measured; new `label` and `frame-title` baseline entries (from the dashboard content the stories show); not tried with a screen reader |
| Tab bars (documents) | Tidy-ups | `CurveTabBar`, `AbstractTabBar`, `CurveTabLayoutViewImpl` | — | tablist | retrofit (in `AbstractTabBar`, shared with link tabs) | every document | in part: tab list, keys (and the tab menu from the keyboard), focus ring, panel link, sidebar toggle name and BEM CSS (`tab-bar.css`) done | left: link tabs are about 16px tall, under 2.5.8's 24px (baselined for the Data Retention impact stories, where one sits above a toolbar); link tab bars have no tab list name; a tab panel holding nothing focusable isn't a tab stop; a document tab's name is cut at 50 characters (with "..."), so a screen reader hears the shortened name; a tab's loading spinner isn't announced |
| Link tabs (inside documents) | Tidy-ups | `LinkTabBar`, `LinkTabPanelPresenter` | — | tablist | retrofit | ~16 | in part: as document tabs (shared `AbstractTabBar`) | as document tabs |
| Layout panels | Tidy-ups | `DockLayoutPanel` and friends | (grid/flex, `SplitPane`) | — | replace as screens are touched | 6 (the floor map views) | in part: every view had its `DockLayoutPanel` replaced with grid or flex layout (`9dc39a1829`), but the floor map views added since use it again (`FloorMapEditorViewImpl`, `FloorMapFactListViewImpl`, `FloorMapGroupsViewImpl`, `FloorMapTimeListViewImpl`, `FloorMapTrackingViewImpl` and the template `FloorMapMapViewImpl.ui.xml`) | DOM order can differ from layout where a panel places its children (`ReadingOrder` is deleted, as no split panel is left); the floor map views not checked |
| Content focus | Tidy-ups | tab pane | `ContentFocus` | — | added | all tabs | done (#5405) | — |

## Checklist

Each component against the things it needs before it counts as done. The Status table above says
what has been done and what is left in words; this one is for seeing at a glance what is missing.

- **Roles and states**: the WAI-ARIA pattern's roles, and its states (`aria-expanded`,
  `aria-selected`, `aria-valuenow` and so on) kept up to date.
- **Names**: every use has an accessible name (no `label` baseline entries for it).
- **Keyboard**: the pattern's keys, and nothing reachable only by the mouse.
- **Focus**: tab stops as the pattern says (often one, with arrows inside), a visible focus ring,
  and the focus kept or put back sensibly when the component changes or closes.
- **Own CSS**: the component's styles in its own stylesheet, `<block>.css`, not spread through
  shared ones.
- **BEM**: every class the component uses is a BEM name of its block (`block`, `block__element`,
  `block--modifier`), states from ARIA attributes where there is one; shared tokens and the
  shared control classes (`stroom-control`, `allow-focus`) aside.
- **Minimal DOM**: no more elements than the pattern and the layout need.
- **Screen reader**: tried with a real screen reader (NVDA, VoiceOver), not only axe.

`done`, `part`, `no`; `?` not yet checked; `—` doesn't apply. The quick filter, selection box, value spinner, date and time and icon box rows
were audited on 2026-10-19, from their DOM in the workbench, their stories and their stylesheets. Pipeline tree and bar describe this
branch, with `stepping_display` in brackets.

| Component | Roles and states | Names | Keyboard | Focus | Own CSS | BEM | Minimal DOM | Screen reader |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Text inputs | done (native) | done (every text field on Stroom's screens is named, the quick filters for what they filter) | done (native) | done | done (`text-field.css` has every look; nine fields that replace the class still get `stroom.css`'s bare element rules) | done | done | no |
| Password box | done | done | done | done | done (`password-box.css`, with its field's looks in `text-field.css`) | done | done (the block, its input, an end holding the toolbar and any badge, and a live region) | no |
| Quick filter | done (a `searchbox`; its toolbar is one only while two buttons show, as it should be) | done (named for what it filters, not only by its placeholder) | done | done | done (`quick-filter.css`, with the rules other stylesheets had for it) | done (`quick-filter`, `__input`, `__toolbar`, `__recent`, `--recent`; its syntax help and Advanced Query dialog with session 3) | done | no |
| Form groups | done | done | done | done | done (`form.css`) | done (its label is also the `form-label` block, a BEM mix; the hidden help text is `form-group__help-text`) | done (the feedback is only there while there is feedback) | no |
| Selection box | done (the list's options have `role="option"`; moving doesn't choose, as in a combobox's list) | done (on Stroom's screens; its list is named for it, and the list's quick filter "Filter" and its name, or "Filter options") | done | done (Tab from the open list closes it and goes on, Shift+Tab back to the box; Escape back to the box) | done (`selection-box.css`, `selection-popup.css`, with the rules the date picker's, Git repo's, conditional formatting's and term editor's stylesheets had for it) | part (the box is; its list's `selectionList-*`, `simplePopup-*`, `pager-*`, `explorerCell` and `selectionItemCell-text` aren't) | part (the value is drawn in an element over the input; the list has about 40 elements before its first option, with a hidden quick filter and pager, and each option is three deep) | no |
| Document and user pickers | part (the picker a button with `aria-haspopup`, the dialog modal; its tree and grid have no roles yet) | done (the picker by its label and value, and described by a problem with it; the filter and the list) | done | done (kept in the dialog, back to the picker on close) | done (`drop-down.css`) | done (`drop-down`, `__value`, `__icon`, `__feedback`; the shared `stroom-control` classes aside) | done (its value and icon, and a feedback line only while there is a problem outside a form group; its dialog's nesting is the Dialogs row's) | no |
| Tick box | done (a native check box; the cell `role="checkbox"` with `aria-checked`) | done (unnamed only in its own story, now named) | done (the cell: session 4) | done | done (`tick-box.css`) | done | done (a label, its input, the box and the text; the cell, itself and the box) | no |
| Value spinner | done | done (the grid's cell: part, its column but not its row) | done (the grid's cell: part, it can't be reached) | done (the grid's cell: no, it can't be reached) | done (`value-spinner.css`; its field is a text field, `text-field.css`) | done | done | no |
| Icon box (`SvgIconBox`) | done (a picture hidden from screen readers) | — | — | — | done (`svg-icon-box.css`) | done | done | no |
| Colour box | done (native) | done (each use, by its row or `aria-label`) | done (native) | done | done (`colour-box.css`) | done (`colour-box`) | done (one element) | no |
| File upload | done (a button that opens a dialog, invalid from its form group; a required one is described as "Required.", as a button can't be `aria-required`) | done (its label and text; described by the chosen file) | done (native button) | done | done (`file-upload.css`) | done | done (a button, the file's name and the hidden input) | no |
| Icon picker | done (a `radiogroup` of native radio buttons) | done (the group by its row, each icon by its text) | done (one tab stop, the arrow keys) | done (the focus ring, apart from the chosen look) | done (`floormap-icon-picker.css`) | done | done (a label, its radio button, picture and text) | no |
| Time zone | done (a selection box and value spinners) | done | done | done | — (its parts' own) | — | done (the selection boxes and the hours spinner) | no |
| Term editor | no | ? | ? | ? | part (`TermEditor.css`) | no | ? | no |
| Radio group | done (`radiogroup`, native radios) | done (the group by its label, each option by its text) | done (native: one tab stop, arrow keys) | done | done (`radio-button.css`) | done | done (a label per option, holding its radio button and text) | no |
| Raw GWT controls | — | done (the tag lists: "All known tags", "Document Tags", "Existing Tags") | — | — | — | — | — | — |
| Date and time | part (the day table has no `grid` role, and its cells have `aria-selected` with no role to allow it) | part (the date field is unnamed in places) | part (arrows and Enter in the calendar; no Home, End, Page Up or Page Down) | done (the Year and Month toolbars are one tab stop each, in the dialog too) | part (`CustomDatePicker.css`; the date-time field borrows the schedule box's classes and stylesheet) | no (only the icon box is) | part (two wrappers in each day cell) | no |
| Schedule box | ? | ? | ? | ? | part (`ScheduleBox.css`) | no | ? | no |
| Time range selector | ? | ? | ? | ? | no (`components.css`) | no | ? | no |
| Menus | part | ? | ? | ? | part (`celltable/Menu.css`, `menubar.css`) | no | ? | no |
| Dialogs | part | done | ? | part (modal ones keep the focus in them) | part (`Dialog.css`, `ResizableDialog.css`) | no | ? | no |
| Popups | ? | ? | ? | ? | part (`SimplePopup.css`, `PopupSupport.css`) | no | no (much nesting) | no |
| Tooltips and help popups | ? | ? | ? | ? | part (`focus-tooltip.css`, `form.css`) | part | ? | no |
| Data grids | no | ? | no | no | part (`celltable/DataGrid.css`) | no | ? | no |
| Cell tables | no | ? | no | no | part (`celltable/DefaultCellTable.css`) | no | ? | no |
| Explorer tree | no | ? | ? | ? | part (`celltable/ExplorerTree.css`) | no | ? | no |
| Expression tree | no | ? | no | ? | part (`ExpressionItemBox.css`, `TermEditor.css`) | no | ? | no |
| Pipeline tree | no (done) | no (done) | no (done) | no (done) | part (`PipelineElementBox.css`) | no | ? | no |
| Pipeline bar | — (done) | — (done) | — (done) | — (done) | — (done, `pipeline-path.css`) | — (done) | — (?) | — (no) |
| Code editor | — (Ace) | ? | no (Tab is a trap) | ? | done (`editor.css`) | part | — (Ace) | no |
| Toolbars | done | part | done | done | done (`toolbar.css`) | done | ? | no |
| Floating action button | done | ? | done | ? | no (`stroom-button.css`) | no | ? | no |
| Refresh button | done | done | done | done | done (`refresh-button.css`) | done | ? | no |
| Split panes | done | done | done | done | done (`split-pane.css`) | done | ? | no |
| Dashboard splitters | done | done | done | done | done (`flex-layout.css`) | done | ? | no |
| Tab bars (documents) | done | done | done | part | done (`tab-bar.css`) | done | ? | no |
| Link tabs (inside documents) | done | part | done | part | done (`tab-bar.css`) | done | ? | no |
| Layout panels | — | — | — | — | — | — | part (the floor map views) | — |
| Content focus | — | — | — | done | — | — | — | no |

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

## Sessions

The work is done a session (a worktree and its conversations) at a time, each taking components
that share code and behaviour, so the shared code is fixed once. The Status table's Session column
says which session each component is in.

| # | Session | Components | What they share |
| --- | --- | --- | --- |
| 1 | Inputs (this worktree, `worktree-quifilter_buttons`) | Text inputs, password box, quick filter, form groups, selection box with the document and user pickers, tick box, value spinner, colour box, file upload, icon picker, time zone, the term editor's fields, the raw GWT controls | The field's base look (the shared `.gwt-TextBox` rules and `stroom-control`), names from form groups and `AccessibleName`, explaining a rejected value |
| 2 | Date and time | Date-time box, date box, calendar grid, time box, duration picker, schedule box, time range selector | The calendar grid's keys and semantics, `CustomDatePicker.css`, the schedule box classes the date box borrows, their dialogs |
| 3 | Overlays | Dialogs, popups, menus and menu buttons, tooltips and help popups | `PopupSupport` and `PopupManager`, the popup positioner, Escape, keeping and putting back the focus, `aria-modal`, dialogs laid out hidden |
| 4 | Grids | Data grids, cell tables, pagers, the cells grids hold (tick box, value spinner, colour, action and menu cells), the selection box's list | Stroom's copy of `AbstractHasData` (a cell gets its events after the browser has handled them, and no touch events), the grid's keyboard pattern, `celltable/*.css` |
| 5 | Trees | Explorer tree, expression tree, pipeline tree, pipeline bar | The tree pattern, `htree` drawing, alternatives to dragging; the pipeline tree and bar are under way on `stepping_display` |
| 6 | Code editor | Ace (`EditorPresenter`, `EditorViewImpl`) | Its keyboard trap (Tab indents); Ace's sources need human approval to change |
| — | Tidy-ups | Floating action button, link tab gaps, the floor map views' `DockLayoutPanel`, refresh button | Small leftovers of finished work (toolbars, split panes, splitters, tab bars, content focus) |

Where a component sits:

- A popup that belongs to an input control is in that control's session (the selection box's
  list popup in 1, the date and time dialogs in 2); the list inside the selection box's popup is a
  cell table, so its markup and classes are in 4.
- A control's grid cell is in 4, not with the control: the value spinner's and tick box's cells
  wait on the grid's keyboard pattern. Cells that only show something or act as buttons
  (`ActionCell`, `CopyTextCell`, `SvgCell` and the like) are in 4 too.
- The term editor's fields are in 1; the expression tree around them is in 5.

Order: 1, 2, 3, 4, 5, 6, with tidy-ups between.

- 1 and 2 first: they hold most of the `label` baseline entries, and are what dialogs and grids
  hold. 2 after 1, as it uses the field look and naming that 1 settles.
- 3 before 4, as grids sit in dialogs and open menus; 5 after 4, as the explorer tree shares the
  cell code.
- 3, 4 and 6 share little code, so they can run side by side in their own worktrees.
- Then page-level focus-order stories, once the components that make up a page are rolled out.

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
