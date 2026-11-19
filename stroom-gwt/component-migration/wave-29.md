# Wave 29: CSS step 4, part 1: standard.css, standard-mod.css and stroom.css dissolved

Part of `../COMPONENT_MIGRATION_ORDER.md` and step 4 of `../CSS_PLAN.md`; written 29 October 2026,
after wave 28. `components.css` follows in wave 30.

## Done (29 October 2026)

- **A class census** (the test runner's `--class-census <file>`, `lib/class-census.mjs`, with
  tests): a script in every frame records each class an element has or is given while a story runs
  (a popup opened and closed again included), and the run writes them out with the stories that
  set each. Run over every story, it showed which of the three sheets' classes can still be on the
  page: of GWT's own, only `gwt-PopupPanelGlass` (the modal glass) and `popupContent` (inside every
  popup); none of the old page classes (`root`, `title`, `page`, `loginPage`, `crumbs`, `error`,
  `logout`, `paging`, `splitPanel`, `homeLink`, `scrollable`). Each "not set" was checked against the
  code as well (`clipboardCopy` is set from JSNI, so stays).
- **Gone:** 146 rules, for GWT's widgets Stroom no longer shows (buttons, check boxes, dialog
  boxes, disclosure, stack and tab panels, push and toggle buttons, rich text, suggest boxes, trees,
  list boxes, value spinners …), the old page classes, empty rules and commented-out rules.
- **`base.css`** (imported where `standard.css` was): the element rules of the three sheets, in
  their old order, so the later still win (no sheet imported between them has element rules), and
  the underline on links in running text.
- **`layout-helpers.css`**: `max`, the dock containers, `w-100`, `h-100`, `align-center`,
  `overflow-hidden`, `input-row`, `focus-ring-room` (with the form's room), `stroom-border-top` and
  `-bottom`, `lower-case-input` and `sr-only`, as the plan decided (short names kept, in one place).
- **Their own sheets**, imported where `stroom.css` was: `app-page.css` (the body, the app panel,
  the loading banner and logo), `stepping-button.css`, `status-icon.css` (was `statusIcon`),
  `info-page.css`, `iframe.css`, `clipboard.css` (was `clipboardCopy`, now `clipboard-copy`) and
  `form-control.css` (Bootstrap's class's placeholder). `stroom-control.css` takes the control's own
  look and is imported there too, so the text field's rules after it still win (a selection box cell's
  input has both classes); its commented-out rules went.
- **Into existing sheets:** `about.css` (`.about`), `curve-tab-layout.css`
  (`stroom-main-content-container`), `navigation.css` (`activityOuter` and `activityButton`, now
  `navigation__activity` and `navigation__activity-button`), `popup.css` (the modal glass, which
  `PopupUtil` now names `popup-glass` with `setGlassStyleName`, and the drag glass
  `popupPanel-dragGlass`, now `popup-drag-glass` and `--visible`), `data-viewer.css` (the source
  link's `clickableLabel` and `enabled`, now `data-viewer__source-link-label--enabled`; disabled is
  the shared `disabled`) and `data-grid.css` (a button in a cell that isn't a row action).
- Comments that said where a rule lives point to its new sheet.
- **Checked:** all 1,746 stories pass with the leak check. Two screenshots changed: the About
  dialog (its `stroom.css` rule `.about { height: 100% }`, which `about.css`'s own height always
  outweighed, won once appended after it; only its `overflow: auto` ever applied, which `about.css`'s
  rule now has) and the tree visualisation's Keyboard story, whose tooltip sits 1px further right
  (the same in a run of its own; a new baseline).
