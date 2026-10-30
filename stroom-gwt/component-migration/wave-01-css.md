# Wave 1: stylesheets and BEM

Part of `../COMPONENT_MIGRATION_ORDER.md`. Written 24 October 2026, after wave 1, from a review of every wave 1 unit against rule 6 of "Done means, for every unit" (each component its own lower-case kebab-case stylesheet, BEM lower-case kebab-case classes, shared classes and theme variables staying shared).

## Decisions (24 October 2026)

- The button's per-variant custom properties (`Button_Themes.css`) are the button block's: renamed `--button--<variant>__*` and merged into `button.css`, with the floor map's and Ask Stroom AI's two readers.
- Blocks that wave 1's cells borrow from other widgets keep their names until their owners' waves: `svg-cell` (`ActionCell`, `SvgStringCell`; owned by `SvgCell`, wave 5) and `duration-label` (`FindAnnotationCell`; owned by `DurationLabel`). `explorerCell*` (`DocumentTypeCell`; owned by `ExplorerCell`, wave 6) likewise.
- Checked with a screenshot baseline of every story taken before the first batch, and a diff after the last.

## Already compliant

`toolbar*`, `toolbar-button*`, `toolbar-row`, `refresh-button*`, `busy-spinner*`, `layered-canvas*`, `quick-settings*`, `form-group*`, `form-label*`, `setting-block`, `settings-group`, `task-spinner`, `tick-box--cell`, `text-field--cell`, `value-spinner--cell`, and the custom properties `--percent-bar-cell__width`, `--colour-swatch-cell__colour`, `--expander-cell__indent`, `--layered-canvas__*`. Shared and staying: `svgIcon`, `sr-only` (name), `allow-focus`, `w-100`, `icon-colour__grey`, `disabled`, `.form`, `.form-padding`, the theme variables, and the `busy-spinner-spin` animation (also used by the button).

## Batches (one commit each)

File moves only:

1. `spinner.css` → `busy-spinner.css`; the shared `sr-only` rule moves to `stroom.css` (after Bootstrap, as now).
2. `task-spinner` from `components.css` to `task-spinner.css`, imported straight after `busy-spinner.css`.
3. `toolbar.css` splits: `toolbar-button.css` (before it), `toolbar.css`, `toolbar-row.css` (after it).
4. `form.css` splits: `form-label.css`, `form.css` (`.form`, `.form-padding`), `form-group.css`, then `settings-group.css` (from `components.css`, after `form-label.css` because of an equal-specificity colour).

Renames:

5. `refresh-button.css`'s animations: `spinner-border` (also Bootstrap's name) → `refresh-button-spin`, `pulse-animation` → `refresh-button-pulse`.
6. FabButton: `stroom-button.css` → `fab-button.css`; `stroom-button-fab` → `fab-button`, `face` → `fab-button__face` (with `--disabled`, `--down`); `FocusTooltip`, `stroom.css`'s `.stepping-button .face`, `ButtonRuns`.
7. LinkButton: `linkButton` → `link-button`, its rules from `components.css` to `link-button.css` (imported after `components.css`, keeping its `:where`).
8. `icon-box--readonly` → `icon-box--read-only` (with `SelectionBoxCell`'s literal and the stories).
9. SettingsGroup: `settings-group-label` → `settings-group__label` (with `PlanBEditorStories`).
10. SettingBlock: `setting-block-icon` → `setting-block__icon` (with `AnnotationEditPresenter`'s SafeHtml), its rules from `stroom-annotation.css` to `setting-block.css`.
11. AssignYourselfLink: `annotationAssignYourself` → `assign-yourself-link`, in `assign-yourself-link.css` (with the test suite's `lib/cycle.mjs`).

Cells:

12. ExpanderCell: `expanderCell` → `expander-cell`, `expanderIcon` → `expander-cell__icon`, `active` → `expander-cell__icon--active`; `ExpanderCell.css` → `expander-cell.css`; the theme variables `--expander-cell-icon__background-color--*` → `--expander-cell__icon-background-color--*`; seven story files.
13. `percent-bar-cell` (`__bar`, `--healthy`/`--warning`/`--danger`), `red-green-text-cell` (`--green`/`--red`), `colour-swatch-cell` (`__swatch`, `__text`), each in its own kebab-case file.
14. `findCell-*` → `find-result-cell` (`__main`, `__icon`, `__name`, `__sub`, `__path`, `__uuid`, `__tags`, `__sample`, `__sample--truncated`, `__sample-before`, `__highlight`, `__sample-after`, `__permission`, `__inherited-permission`), from `components.css` to `find-result-cell.css`.
15. `queryCell-*` → `query-cell` (`__time`, `__expression`, `__gap`; its class literals move into its template) and `columnValueCell*` → `column-value-cell` (`__text`), each in its own file.
16. `aiChatHistoryCell-*` → `ai-chat-cell` (`__title`, `__age`) and `findAnnotationCell-*` → `find-annotation-cell` (`__main`, `__icon`, `__sub`), each in its own file.

Last:

17. Button: `Button.css` and `Button_Themes.css` → `button.css`; `Button` → `button` (with `Button--base` folded into it, base declarations first), `Button__*` → `button__*` (`__background-text`), `ripple-container`/`ripple` → `button__ripple-container`/`button__ripple` (and the `button-ripple` animation), `Button--*` → `button--*`; `--color-btn-*` → `--button__*`; `--ripple__*` → `--button__ripple-*`; `--button-<variant>__*` → `--button--<variant>__*`; the 20 templates using the contained variants, `ButtonStories`, `DialogWidgets`, `ApiErrorAlertHostStories`, `form.css`/`form-group.css`, `ask-stroom-ai.css`, `components.css`, `stroom-floormap.css`, and the test suite's 11 files.

Unused CSS met on the way (the default, outline and icon button variants, `.stroom-theme-dark2`, `Button__container`, `svgCell-face`, roots no code emits) and unused classes (`SvgIconCell`, `FindResultWithPermissionsCell`, `ToolTipCell`) are renamed, not deleted (removals wait), and recorded in `ISSUES.md`, as is a hover bug: a disabled step location link and a disabled `TimeRangeSelector` still underline on hover (`.linkButton:hover` wins).

## Risks the diff must clear

Import order (each block's file is imported where its rules were, so equal-specificity pairs keep their winner: `settings-group__label` after `form-label`, `link-button` after `components.css`, the cells' icons before `svg-icon.css`, `sr-only` and the form files after Bootstrap); two specificity drops nothing competes with (`percent-bar-cell__bar--*`, the expander's active hover); classes used by unconverted code, renamed everywhere at once; and selectors in the stories and the test suite, which would silently match nothing if left on an old name.
