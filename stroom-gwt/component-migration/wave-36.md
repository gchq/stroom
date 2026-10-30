# Wave 36: UI audit wave 2, dead code part 2

The second of the UI audit's waves ([../UI_AUDIT.md](../UI_AUDIT.md), decisions D4 and D8); written
29 October 2026, after wave 35.

## Done (29 October 2026)

- **Commented-out code** (dashboard-query-06, security-settings-23, floormap-pathways-stores-18,
  data-pipeline-processing-23, explorer-documents-shell-25, widgets-1-04, widgets-2-11): about
  2,300 lines in about 100 client files, found by a script that takes a run of `//` lines only when
  its lines read as code (and keeps a comment after the last code line, which describes what
  follows), then read through by hand for what it missed (multi-line log calls, sample HTML, old
  template markup). The App's and DashboardApp's place manager notes (GWTP's) went too; a TODO that
  names missing behaviour (`ImportConfigConfirmController.clearCaches`) was kept, without its dead
  lines. Blocks and methods left empty were removed or say why they're empty.
- **Unused members** (dashboard-query-05 and 07, security-settings-22, floormap-pathways-stores-19 to
  24, data-pipeline-processing-22, explorer-documents-shell-23, widgets-1-09 to 11, widgets-2-04, 06,
  09 and 10), each checked again for callers; with them what only they used: `ResultStorePlugin`'s
  model and security context, `OpenFeedEvent`'s builders and unused state, `OpenTaskManagerEvent`'s
  user name, `OpenNodeEvent`'s name-only form (and `NodesController.setSelected(String)`; the Nodes
  story selects as the app does), `DataGrid`'s `MyDataGrid` compatibility API, `DataGridSortIcon`
  (`GridHtml` uses `SortIcon`), `Pager`'s pass-through overrides and title, `TickBox`'s unused GWT
  `CheckBox` API, `IntlDateTimeFormat`'s unused options, the Java-to-moment.js format conversion,
  `LayerContainer.setFade` (its fade was commented out, so it did nothing) and many small methods.
  Methods used only inside their class became private. Floor map geometry the tests check stays
  package-private for them.
- **Harness hooks** (explorer-documents-shell-26): `removeWindowClosingHandler`, `stopRefreshing` and
  `setOnHidden` stay, documented as the workbench's.
- **`FormatEvent`** (widgets-2-05) is gone: `addFormatListener` takes a `Runnable`, and
  `HasFormatHandlers` and the `stroom.editor.client.event` package went with it.
- **`EventBinder`** (widgets-1-12) is gone: `SelectionList`, `SelectionPopup` and the dashboard's
  `TabLayout` wire their own children's handlers once, in their constructors.
- **`xsd-browser.css`'s twelve unused rules** (widgets-2-08). The images only they named are left,
  in ISSUES.md.
- **The XSD browser's pattern heading** (widgets-2-07, the audit's one behaviour bug): a pattern
  restriction is headed "Pattern", not "Enumeration". New stories, Enumeration Constraint and
  Pattern Constraint; changelog entry (master has it).
- **The workbench** (workbench-04 and 13): the plays' selectors are `DataGrid`'s alone (no `th`/`td`),
  the inline copies use `StroomDom`'s constants, one `StroomDom.clickHeader` replaces two copies
  (without `MyDataGrid`'s move-and-wait), and the toolbar template's two unbound fields went.
- **The template binding test** (D8): `TestTemplateBindings` checks every `@UiField` is used by its
  owner and every `ui:field` is bound (or named in the template). It found 27 problems, the audit's
  and seven more (`FeedDependencyList`, `ProfilePeriodList`, `SourceViewer`, `Dialog`,
  `CurveTabLayout`, `AnnotationEdit`, `Navigation`); all are fixed. `SourceViewer`'s hidden, never
  wired stepping button went, and its panel keeps its `position: relative` in a class of its own.
- **Checked:** the client modules' compile, checkstyle and tests (242 classes), the app's and the
  dashboard's GWT compile (which found four templates still setting `fade`), and a full run: all
  1,741 stories pass with the leak check. Eighteen passed only on a second attempt, all in the
  run's first minutes, and one screenshot (Processor Task Summary) was taken before its styles had
  loaded; their eight groups all passed at 4 workers with no retries and no change (ISSUES.md,
  "Leak check under load"). The Percent Columns story's screenshot changed as it now uses
  `addColumn` with a percentage width, as the app does (its headers are as the app's); the two XSD
  stories are new.
