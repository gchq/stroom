# Wave 39: UI audit wave 5, composed all the way up

The fifth of the UI audit's waves ([../UI_AUDIT.md](../UI_AUDIT.md), theme T1 and decision D6),
in its seven parts by area; written 29 October 2026, after wave 38. Every part uses wave 37's form: a
child's controller attaches to the component its parent's template declares (`attach(Component)`)
rather than injecting its view, and the parent's slot goes. A slot's panel that carried classes
stays in the template, without a `ui:field`, with the child declared inside it, so layout is
unchanged.

## Done (29 October 2026)

- **5a, security** (security-settings-03): `UserAccess` (its user and session lists),
  `AppPermissions`, `UserAndGroups` (three user lists; the panes stay, as hiding and `inert` act on
  them), `DocumentUserPermissionsTab`, `UserPermissionReport`, `CredentialsManagerDialog`,
  `GlobalPropertyTab`, `ManageActivity`, `RuleSetSettings` and `DataRetentionPolicy` (whose
  components now put the actions on their own list's toolbar) and `ContentTemplateTab`. Users,
  Credentials and the user's App Permissions tab, shown on their own, attach to one they make.
- **5b, data, processing and pipeline** (data-pipeline-processing-01, 02, 03): `Jobs`, `Nodes`,
  `MetaBrowser` (both stream lists and the data viewer), the processor task tabs, `Processors`,
  `FeedDependenciesEditor`, `ProcessorProfileEdit`, `NodeGroupEdit`, `FsVolumeGroupEdit`,
  `PipelineStructure` (its property and reference lists), `ServerTasks`, `SourceViewer`'s text pane
  and `LinkedEvents`' data viewer. `DataViewer` and `SourceViewer` declare their navigators and
  progress bars (shown with `setNavigatorVisible`/`setProgressVisible`), and the stepping screen
  declares its location link and step controls (`stepMessage` is a `Label`).
- **5c, floor map, pathways and stores** (floormap-pathways-stores-01, 13, 14, 16): all 22 slots
  (the floor map's canvas, timeline, fact and time lists; the pathways split and edit; traces,
  content store, caches, script settings and imports). The database tables screen has a component
  of its own declaring its grid; `DocRefList` declares its toolbar and link column; the floor map's
  tooltip caption and the layer style's preview are declared.
- **5d, explorer, analytics and annotation** (explorer-documents-shell-02, 03, 04): `FindPanel` is a
  container its users' templates fill (`FindDocuments`, `FindAnnotations`, `AiChatHistory`);
  `DependenciesTab`, `DuplicateManagement`, `ScheduledProcessing` and `AnalyticDataShards` declare
  their lists; the execution schedules screen has a component declaring its grid, buttons and
  selection count (`PagerPanel` takes declared toolbar widgets, `@UiChild`); `LinkedEvents`, the
  annotation tag list and the annotation links declare their buttons, behind action enums.
- **5e, the Main shell** (explorer-documents-shell-01, 09): `Main.ui.xml` declares the explorer and
  content `SplitPane` (with `Navigation` and the content tabs, their landmarks set by typed setters)
  rather than `Main.java` rebuilding it on each change; hiding the explorer hides its pane. Only the
  docked AI panel and the maximised view are placed at runtime. Main has one root
  (`main-screen`), the full screen layer lost its duplicate, and the theme's page border is a rule
  `DynamicStyles` writes, not an inline style. The split keeps a dragged width as before; a pane
  collapsed or maximised from the keyboard now also stays so across docking the AI panel (the
  rebuild used to reset it).
- **5f, widget internals** (widgets-1-16, data-pipeline-processing-18): `SelectionList` declares its
  grid; `DraggableTreePanel` takes its tree as a declared child (`<tree>`), so the pipeline and
  expression trees declare theirs (the drag copy is still made in Java).
- **5g, the query's slots** (dashboard-query-01): `SearchResultStores` (its list),
  `QueryResultTableSplit` (its table and text pane) and `QueryDataPane` (its table, for the index,
  Plan B, SQL store and floor map data tabs). The embedded query's table or vis is chosen at runtime
  and stays so.

- **Checked:** compile and checkstyle of every client module, every client test (with
  `TestTemplateBindings` and `TestStylesheets`), both GWT compiles, and a full run at 6 workers: all
  1,741 stories pass with the leak check, none needed a second attempt, and no screenshot changed
  but the visualisations' known unsteady ones.

## Left, and why

- **widgets-1-17** (the tab bars' selector made in Java): the bars' templates are element roots and
  `AbstractTabBar` places and moves its tabs itself; declaring the selector means rebuilding the bar,
  which risks its keyboard and drag behaviour. Only the wrong comments were fixed.
- **dashboard-query-15** (Time Preferences repeating `TimeZoneBox`): `TimeZoneBox` fires its change
  on every field, has its own help, widths and no accessible name for the offset, where the
  preferences apply on each change and say more. It needs `TimeZoneBox` made settable, with its
  rename in wave 9.
- **The index's volume group and field editors** (data-pipeline-processing-01): not reached; they
  go with wave 9's index work or a later pass.
- **`DatabaseTablesMonitoringController`** keeps its name until wave 9.
