# Wave 30: CSS step 4, part 2: components.css dissolved

Part of `../COMPONENT_MIGRATION_ORDER.md` and step 4 of `../CSS_PLAN.md`; written 29 October 2026,
after wave 29.

## Done (29 October 2026)

- **Gone:** 28 rules for classes nothing sets (the class census of every story, and the code):
  the old accept/reject, item and character navigator and pager buttons, `focus-without-border`,
  the query help's `-description` and `-table`, `multiChooser`, the visualisation asset tree's GWT
  tree rules; and `command-link-cell.css` (`.CommandLinkCell`) and `icon-cell.css`'s
  `.svgCell-face`, which nothing sets either.
- **A sheet per block**, 27 new, imported where `components.css` was and in its order, so which rule
  wins is as before: `info-layout`, `info-line`, `ref-link` (the document, user and command links),
  `pager-toolbar`, `inline-label-icon`, `button-group`, `key-binding-preferences`, `form-controls`
  (Bootstrap's `form-control` as Stroom styles it, `form-control-border`, `form-control-background`,
  `form-heading`; wave 29's `form-control.css` folded in), `info-table`, `query-help-detail`,
  `node-ping-bar`, `message-container`, `list-container`, `find-buttons`, `selection-item-cell`,
  `row-action`, `permission-details`, `create-document-types`, `document-user-permissions`,
  `duration-setting`, `scroll-region`, `contentstore-details`, `gitrepo-settings-form`,
  `credentials-details-dialog`, `lozenge`, `table-filter` and `table-filter-cell`. Into existing
  sheets: the tab bar container (`curve-tab-layout.css`), the navigation's title bar
  (`navigation.css`), the annotation history's focus rings (`annotation-history.css`), and
  `default-min-sizes` and `section--disabled` (`layout-helpers.css`).
- **Renamed BEM style:** `rowAction` (`row-action`), `infoTable`, `infoLine`, `queryHelpDetail`,
  `listContainer`, `messageContainer`, `permissionDetails`, `durationSetting`, `tabBarContainer`
  (to their kebab case); `docRefLinkContainer`, `-Text`, `-Copy`, `-Open`, `-Insert` (`doc-ref-link`,
  `__text`, `__copy`, `__open`, `__insert`), the same for `userRefLink*` and `commandLink*`;
  `hoverIcon` (`svg-image--hover`); `keyBindingPreferences-list`
  (`key-binding-preferences__list`); `documentUserPermissions-table`
  (`document-user-permissions__table`); `lozengeOuter` (`lozenge-list`); `svgIcon` (`icon-wrap`, as
  `svg-icon` is already on some SVGs, and its `small` `icon-wrap--small`); the node ping bar
  (`nodePingBar-*`, built in `NodeStatusList`, now `node-ping-bar`, `__bar`, `__bar--healthy` …);
  the selection list's items (`selectionItemCell-*`, built in `SelectionItemCell`, now
  `selection-item-cell__*`); and the permission description's `inherited`, `bold` and `delimiter`
  (`DescriptionBuilder`, now `permission-details__line--*`).
- **What the first full run caught:** two links that no longer opened (the processor filter list's
  details link, a processor task's info feed link), as `ElementUtil` matched class names by
  substring and a BEM block's name starts its elements' (now matched whole, `TestElementUtil`; see
  ISSUES.md's Fixed); and the explorer's small alert icon, which lost Bootstrap's `small` (80% text)
  with its rename, so its line grew and its cell scrolled (axe's `scrollable-region-focusable`):
  `icon-wrap--small` sets the same size.
- **Checked:** all 1,746 stories pass with the leak check; no screenshot changed but the
  visualisations' and the top bar's Keyboard story, whose ⋮ tooltip showed in the full run and
  not alone (timing; ISSUES.md's "Screenshots that differ in every run").
- **What isn't BEM now** is only Ace's (`ace_*`, and the `stroom_info` … markers Stroom gives Ace's
  annotations) and GWT's `popupContent`, the box GWT's `PopupPanel` puts its content in, both
  outside Stroom's naming.
