# Wave 28: CSS step 3, BEM names for the components' own classes

Part of `../COMPONENT_MIGRATION_ORDER.md` and step 3 of `../CSS_PLAN.md`; written 29 October 2026,
after wave 27.

## Done (29 October 2026)

- **How:** `scratchpad/cssa/bemrename.py` renames a class in the sheets' selectors and, in the
  code, only inside a string literal or a template's class attribute (`styleName`,
  `addStyleNames`, `class`), never an HTML attribute's name (`annotationId="6"`); every other hit
  is listed to look at. Names that are also something else (`annotationId`, a URL parameter and an
  attribute; `showValueFilters` and `passwordPolicyMessage`, JSON properties; the term editor's
  bare `wide`, `narrow` and the like) were renamed by hand, and names built at runtime
  (`"listInputItem" + suffix`) by changing the code that builds them.
- **Annotation** (`stroom-annotation.css`, gone), in four blocks, each its own sheet imported where
  it was: `annotation-edit` (the Annotation tab: `annotationContainer`, `annotationTop` … to
  `annotation-edit__*`), `annotation-setting` (a setting's heading and value, shared with the
  Change Status and Change Assigned To dialogs; `clickable` is `annotation-setting__heading--clickable`),
  `annotation-history` (the history built by `AnnotationEditController`: lines, comments, groups,
  items, actions, tables, links, the group expander that was `setting-block-expander`) and
  `duration-label`. Empty rules and the classes only they named went.
- **Term editor** (`term-editor.css`): `termEditor-outer`, `-inner`, `-item` and `-label` are
  `term-editor`, `__fields`, `__item` and `__label`; the bare modifiers (`wide`, `narrow`,
  `operator`, `condition`, `condition-wide`, `docRef`, `fieldType`) are `term-editor__item--*`
  (`docRef` is `--picker`) and `term-editor__label--field-type`, written `.term-editor__item.term-editor__item--wide`
  so they keep their specificity. `dropdown` had no rule and went; `term-editor__item--field` has
  no rule either and stays as the stories' hook for the field box.
- **Dashboard** (`dashboard.css`), split into `dashboard` (`dashboard__outer`, `__scroll` and its
  `--horizontal`/`--vertical`, `__design-surface`, `dashboard--design-mode`), `tab-layout` (a pane:
  `tabLayout-*` to `tab-layout__*`), `column-header` (a table column's header: `column-top`,
  `-label`, `-sortIcon`, `-sortOrder`, `-valueFilter`, and the filter icon's hook
`column-valueFilterIcon`, `column-header__value-filter-icon`), `vis-layout` (the dashboard's and query's
  visualisation panes, which had the same rules under two names, and `visBody`),
  `dashboard-input` and `list-input-item`; `flexLayout-selected` is `flex-layout__tab--selected`
  in `flex-layout.css`. Two commented-out blocks went.
- **The rest:** `dataGridDisabledCell` and `dataGridAlertText` are `data-grid__cell--disabled` and
  `data-grid__alert-text`; `conditionalFormatStyleSelection` is `selection-box--swatches`;
  `showValueFilters` is `result-table__grid--value-filters`; `resizeGlass` is `timeline-glass`;
  the sign-in page's user name is `sign-in-page__user-name` (and `-outer`); the password policy
  is `password-policy`; the reset email's field takes the layout helper `w-100` in place of
  `emailTextBox`, whose rule was only that.
- **Stories and scripts** follow the names (selectors in the stories, `cycle.mjs`, `walk.mjs` and
  the compare adapters); `TestCommentDecoration` and `TestExpiryFormatter` expect the new names.

- **What the first full run caught:** a story still selecting `.annotationTitleTextBox` (a class
  that had no rule and went), and `components.css`'s `.data-grid.showValueFilters`, which the hand
  rename of `showValueFilters` missed, so a dashboard table's filter row stayed hidden (Table
  Filter Row). Both fixed. The Analytic Rule editor's Table Preferences Persist On The Doc failed
  its leak check once under the full run's load and passed when retried.

## Left for step 4

What isn't BEM now is in the shared sheets: `components.css` (`rowAction`, `infoTable`,
`queryHelpDetail`, `listContainer`, the link family `docRefLink*`, `userRefLink*` and
`commandLink*`, `selectionItemCell*`, `nodePingBar-*` …), `stroom.css` (`svgIcon`, `textBox`,
`statusIcon` …) and `standard*.css` (`popupContent` …), with the sheets that only refer to them.
Ace's classes (`ace_*`) are Ace's.
