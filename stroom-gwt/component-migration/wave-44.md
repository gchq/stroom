# Wave 44: UI audit wave 10, packages

The tenth of the UI audit's waves ([../UI_AUDIT.md](../UI_AUDIT.md), theme T8, decision D3); written
30 October 2026, after wave 43. Moves only: class names (settled in wave 43), behaviour, layout and
story titles are unchanged.

## Done (30 October 2026)

- **Shared parts out of features:** the tree widgets the pipeline and expression trees share
  (`DraggableTree`, `TreePanel`, `TreeKeyboard`, `Box`) are in `stroom.widget.htree.client`;
  `TimeZoneBox`, `DaysOfWeek`, `SettingsGroup`, `HandlerRegistry`, `ListDataProvider`,
  `ClassNameBuilder`, `FloorMapAria`, the grid cells, `ColumnSizeConstants`, `TreeRowHandler` and
  `DirtyKeyDownHander` are in the widget module's packages; `RestDataProvider` and
  `AbstractRestClient` in `stroom.dispatch.client`; the ref cells in `stroom.util.client.cell`.
- **Features out of shared packages:** `CurveTabLayout` is the content tabs'
  (`stroom.content.client.component`); `OwnedContentTabs` the dashboard's; `DynamicStyles` the
  conditional formatting's; `HistogramQueryHelper` the floor map's; `DocumentList` and
  `DocumentPermissionsList` security's.
- **The monitoring plugins are in their features** (D3): jobs in `job`, nodes, node groups and
  database tables in `node`, processor profiles in `processor`, execution schedules in `analytics`,
  global properties in `config.global`; `ToolsContentPlugin` sits with `MonitoringPlugin` in the menu
  code. `stroom.monitoring` and its GWT module went; the features have GIN modules of their own, and
  `TaskModule` binds the Server Tasks plugin.
- **`entity` is merged into `document`:** the editors, `LinkTabPanel` and `TabbedController` are in
  `stroom.document.client.editor`, the document dialogs in `stroom.document.client.component`, and
  `EntityModule` is `DocumentModule`.
- **The widget module's grab-bags are split:** `stroom.item.client` is `stroom.widget.selection.client`;
  the cell, pager, task, hyperlink, preferences, help, duration and date picker parts are in
  `stroom.widget.*` packages; the XSD browser is the XML schema editor's
  (`stroom.xmlschema.client.*`), whose selection is a callback (`DataSelectionEvent` went). GWT
  modules went, were renamed or added to match (no two modules share a name now).
- **Layout within features:** models, events and components in their sub-packages (the query's
  `component`, `model` and `cell`; the floor map's `model`, `query` and `geometry`; the annotation
  events; the security events; the global properties', data retention's and credentials' models).
- **The dashboard's pane registrars** (dashboard-query-11): the nine `XxxPlugin` classes, their six
  GIN modules and the empty ginjector are one `DashboardPaneTypes`.
- **The workbench:** 68 story classes moved from `app.main` into their areas' packages (titles
  unchanged); the toolbar and split pane templates sit beside their Java.

## Checked

Compile and checkstyle of every client module (main and test), every client and workbench test,
both GWT compiles (which find a package no GWT module covers), and a full run at 6 workers with the
leak check against the baselines: all 1,741 stories pass, none on a second attempt, and no
screenshot changed but the visualisations that differ run to run.

## Left, and why

- The flex layout widgets stay in `flexlayout`: they use its package-private helpers.
- The query and dashboard result twins are wave 11's.
- `FloorMapAria` keeps its name in the shared package (a later rename).
- Two story classes share the title `App/Query/QueryResultsTable` (ISSUES.md), for wave 11.
- `stroom-gwt-suite/oracles/` is a generated inventory, to regenerate.
