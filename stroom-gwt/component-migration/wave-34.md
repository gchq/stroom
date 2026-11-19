# Wave 34: the deferred deletions

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 29 October 2026, after wave 33, at the user's
request ("can we do Deferred deletions now").

## Done (29 October 2026)

- **The deferred list was already done:** wave 16's units 7 and 11 (`ItemCell`, `SvgIconCell`,
  `FindResultWithPermissionsCell`, `JobTypeCell`, `SvgStringCell`, `ToolTipCell`, `MyTreeLayout`,
  `LinkTabsPresenter`) went on 28 October 2026 (`79d9ed716d`, `30e3609241`), and the merge of master
  took `HoverActionMenuCell`, `CustomCellList`, `FindCellTable` and `SelectionEventManager`; the order's
  list hadn't been updated, and now is.
- **The rest of the reviewers' dead code went:** `CustomDateTimeFormat`, `ExpanderEvent`,
  `ResourceCache`, `KeyboardInterceptor`, `NameTokens` and `SteppingPipelineTreeBuilder` (none of
  them used anywhere), and `Templates.input()`.
- **`button.css`'s unused variants** (wave 1's stylesheet review found them): the default, outline
  and icon buttons, `button--selected`, `.small`, `.large` and `button__container` (46 rules nothing
  sets), and the 38 custom properties only they read.
- **Checked:** the GWT compile and the app shell and stepping screen stories (64), then a full run:
  all 1,746 stories pass with the leak check, and no screenshot changed but the visualisations'. A
  first full run, sharing the machine with the UI audit's 17 agents, failed six of the Analytic Rule
  editor's leak checks; all 22 of its stories passed at 4 workers (ISSUES.md, "Leak check under
  load").
