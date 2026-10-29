# UI audit: findings

*An audit of the GWT UI against the plan's goals (UI-GUIDE.md, COMPONENT_MODEL_DESIGN.md), 29 October
2026: naming, package structure, dead code, BEM CSS and stylesheet placement, and declarative
composition (widgets and child components declared in templates all the way up; Java adds only
children chosen at runtime). Read-only: eight agents each audited a slice of the client modules and
the workbench in all five dimensions, a second agent per slice tried to refute each finding (one of
211 was refuted, 29 adjusted), and a last agent merged, ranked and proposed waves. Each finding's
evidence and its verifier's note are in [ui-audit/findings.md](ui-audit/findings.md). Nothing was
changed by the audit itself; the waves below were approved on 29 October 2026 (Decisions).*

**Progress.** Wave 1 done 29 October 2026 (order wave 35, `component-migration/wave-35.md`); wave 2
done 29 October 2026 (order wave 36, `component-migration/wave-36.md`); wave 3 done 29 October 2026
(order wave 37, `component-migration/wave-37.md`).

## Summary

**Scope.** Eight slices covered the GWT client: dashboard and query, security and settings, floor map, pathways and stores, data, pipeline and processing, explorer, documents and shell, two widget-module slices, and the workbench. There were 210 verified findings. Sixteen of them are the same defect reported from two or three slices. They are merged below under the first id, with the others noted, which leaves about 190 distinct defects.

**Counts.** These are raw counts, before merging.

| Dimension | High | Medium | Low | Total |
|---|---|---|---|---|
| Composition | 6 | 19 | 15 | 40 |
| BEM CSS / sheets | 2 | 28 | 16 | 46 |
| Dead code | 0 | 16 | 36 | 52 |
| Naming | 1 | 16 | 26 | 43 |
| Packages | 1 | 16 | 12 | 29 |
| **Total** | **10** | **95** | **105** | **210** |

**The four themes that matter most:**

1. **Fixed children are put into slots from Java instead of being declared (T1).** About 75 slots in about 50 parent components have this problem. Each slot always gets the same child, and that child already has a component of its own. This is the biggest gap against the plan's "composed all the way up" goal. Most child controllers call `initWidget(view)` on a component injected into them, so the parent's template cannot declare that component. One framework change fixes most of these cases: give controllers an `attach(component)` form, as `DocSelectionController`, `EntityTreeController` and `TermEditorController` already have. After that, the rest is mechanical.
2. **Components borrow other blocks' classes, and some class names are not real BEM blocks (T5, T6).** For example, `dashboard-panel` is used in about 50 places and styles nothing. Other problems include using `stroom-control` as a panel, the bare `feedback` class, `wide-control`, `maximised`, and copies of `link-tab-panel`. In the floor map, trace overview and content store, flat hyphenated names stand in for BEM elements, and several sheets hold many unrelated blocks. `TestStylesheets` passes all of these, because every name is lexically a valid block. A sheet-level check would catch the whole class of defect.
3. **Duplicate components and look-alikes (T3).** The dashboard and the query screen each have their own copies of the table, the visualisation, the text pane and the column managers. Two components are byte-identical twins. Other duplicates are the two processor-task screens, the four dialog button frames, `HeadedPagerPanel` (a copy of `PagerPanel`), three hand-copied `LinkTabPanel`s, and two old sort-icon and spinner families.
4. **Package cycles, and shared parts living in feature packages (T8).** `query` and `dashboard` import each other: 22 types one way and 29 the other. The query expression tree is built on pipeline tree widgets. Generic cells and helpers live in `data`, `config.global`, `security`, `planb` and `floormap`. Two packages, `stroom.hyperlink.client` and `stroom.task.client`, are split across the widget module and `stroom-core-client`.

There is also a lot of plain dead code (T7). It is low risk to remove, but removals need your approval first.

**Not covered:** see the last section. In short:
- Dead methods with common names (`setData`, `refresh`, `read` and so on) were not checked.
- Very large files were sampled, not read line by line: FlexLayout, DocumentPluginEventManager, AnnotationEditController, the floor map canvas, the grid internals, KeyBinding and IntlDateTimeFormat.
- No build or story run was done.

## Themes

### T1. Declare fixed children and attach their controllers (composition, high)

Each of these parents fills slots from Java, always with the same child, and the child already has a component of its own. Some cases also make widgets from Java that could be declared.

- **Parents filling fixed slots from Java:** dashboard-query-01, security-settings-03, floormap-pathways-stores-01, data-pipeline-processing-01, explorer-documents-shell-01 (Main), explorer-documents-shell-02.
- **Show/hide slots filled and cleared from Java:** data-pipeline-processing-02.
- **Children inserted with `insert()` or `add()`:** data-pipeline-processing-03, security-settings-04.
- **Children made with `new`:** data-pipeline-processing-18 (PipelineTree and ExpressionTree with DraggableTreePanel), widgets-1-16 (SelectionList's grid), widgets-1-17 (TabSelector).
- **Lists without a component, whose grid and buttons are built in Java:** floormap-pathways-stores-13 (DatabaseTables), explorer-documents-shell-03 (ExecutionSchedules).
- **Fixed toolbar buttons added from Java:** explorer-documents-shell-04 (LinkedEvents, AnnotationTags, AnnotationLinks), floormap-pathways-stores-14 (ImportsTab/DocRefList).

**Fix:**
1. **Framework step.** Add a standard `attach(Component)` form to `Controller`, used as `make(provider).attach(view.getX())`. The parent's component exposes a getter. The `setX(IsWidget)` setter and its `SimplePanel` are deleted.
2. **Convert the most-reused children first:** ExpressionTree, ExpressionEditor, QueryHelp, the list controllers (UserList, the job and node lists, the processor task lists) and ResultStoreList. ExpressionTree and ExpressionEditor alone appear in about 15 parents.
3. **Then go area by area.** Every converted screen needs its workbench stories run.

Out of scope, and correctly so:
- HasToolbar and document toolbars, which sit outside the tab's DOM (floormap-pathways-stores-14 and explorer-documents-shell-04 explain why).
- Runtime-chosen children, such as the AI dock, the maximised view, AnalyticProcessing and Core.

### T2. Delete empty wrapper components

Each of these is a component that wraps a single smart part and holds nothing else:
- ApiKeys, Users, SigningKeys, Credentials and Accounts (security-settings-02)
- DataPreviewTab and SourceTab (data-pipeline-processing-04)
- StreamingProcessing (explorer-documents-shell-06)

**Fix:** the controller calls `initWidget(part.asWidget())`. UI-GUIDE:27 already requires this. Small effort, 8 components.

### T3. Collapse duplicate components

- **Dashboard and query twins** (dashboard-query-02): Vis/QueryResultVis, Table/QueryResultTable and MultiRules/TableFilterInput are identical. TextPane, the column managers and SearchModel/QueryModel are near copies.
- **Two processor-task screens** (data-pipeline-processing-05).
- **Dialog frames:** four `*Content` frames, plus Dialog against ResizableDialog (widgets-1-19).
- **HeadedPagerPanel** (widgets-1-18).
- **LinkTabPanel copies:** UserPreferences, AskStroomAiConfig, CredentialEdit and FloorMapDock (security-settings-05 and floormap-pathways-stores-10, merged).
- **Old families kept beside their replacements:** DataGridSortIcon against SortIcon (widgets-1-10), Spinner against SpinStepper (widgets-2-13), and the old htree renderers (widgets-2-03 and widgets-2-14).
- **Repeated markup:** setting blocks, the filter-over-tree pickers and build info (explorer-documents-shell-08), and TimePreferences repeating TimeZoneBox (dashboard-query-15).

**Fix:** one component per thing. Do the dashboard/query twins together with T8's shared results package.

### T4. Move markup out of controllers and Java

- **Controllers that build their screen's HTML:**
  - AskStroomAiController, which also writes raw `<button>`s (security-settings-01)
  - AnnotationEditController's history, InfoDocumentController, and DocumentPluginEventManager's delete confirmation (explorer-documents-shell-05)
  - DescriptionBuilder (security-settings-07)
- **Raw controls in cell markup:** FilterCell's `<input>` and `<button>` (dashboard-query-20), and the workbench's look-alike cell (workbench-11).
- **Hand-built buttons, ARIA attributes and icons:**
  - AssignYourselfLink and SettingBlock (explorer-documents-shell-07)
  - Login, AuthenticationError, ChangePassword and CommonAlert (security-settings-06)
  - DataViewer's source link (data-pipeline-processing-20)
  - DropDownBox, ValueSpinner and the busy spinner (widgets-2-28)
- **DOM built in Java for fixed parts:** DropDownBox's feedback (widgets-2-29), the XSD wrappers (widgets-2-30), IndicatorPopup and RightBar (widgets-2-31), FloorMapCanvas's caption and FloorMapLayerStyle's preview (floormap-pathways-stores-16), and PipelinePathBar's segments (data-pipeline-processing-19).
- **Inline styles:**
  - result table cells (dashboard-query-21)
  - Main (explorer-documents-shell-09)
  - floor map swatches (floormap-pathways-stores-12)
  - popups (widgets-1-26)
  - SafeHtmlUtil and RightBar (widgets-2-27)
  - the pipeline source dialog's classes (data-pipeline-processing-17)

**Fix:** controllers pass data only. Fixed parts go in templates. Repeated rows use SafeHtmlTemplates in the component. Icons use `stroom.svg.client.Icon`. Raw `<input>` and `<button>` in cells become Stroom cells.

### T5. Borrowed and shared classes need one decision each

Each item is a single app-wide decision, then a sweep.

- **`dashboard-panel`:** dashboard-query-26, security-settings-13, floormap-pathways-stores-11, data-pipeline-processing-16, explorer-documents-shell-27, widgets-2-32. Its only rule is `.dashboard-panel > .form-group label`, so about 50 uses do nothing. Decision: remove it wherever no direct form-group child exists. Where the label rule is needed, replace it with a named shared helper in layout-helpers.css.
- **`stroom-control` used as a panel border:** dashboard-query-23, data-pipeline-processing-15, explorer-documents-shell-08 (ExplorerPopup). Use `control-box` instead.
- **`stroom-control.css` mixes three blocks and a bare `readonly` class:** widgets-1-25 and widgets-2-26 (merged), plus data-pipeline-processing-25 for `stroom-control-label`. Move `stroom-control-grid` to welcome-tab. It is live, because the root has class `stroom` (explorer-documents-shell-24).
- **Bare `feedback` labels in change-password.css** (security-settings-10, widgets-1-23, merged): use FormGroup's own feedback.
- **`credentials-details-dialog` and `wide-control`** (security-settings-12, floormap-pathways-stores-08, data-pipeline-processing-14, merged): give each component its own block. `wide-control` does nothing in FloorMapObjectEdit.
- **Bare `maximised` class** (dashboard-query-24, explorer-documents-shell-09, merged): use a modifier on Main's own block.
- **Other borrowed blocks:**
  - `navigation-header-spinner` (security-settings-08, explorer-documents-shell-10)
  - `query-buttons__*` (dashboard-query-22, data-pipeline-processing-14)
  - `pager` and `pager-toolbar` (dashboard-query-22, widgets-1-21)
  - `explorer-tree` and `find-panel` (explorer-documents-shell-28)
  - `column-header` in the grid (widgets-1-24)
  - `link-tab-panel` (see T3)

### T6. Make sheets one block each, with BEM names (CSS)

- **Flat names standing in for BEM elements:**
  - floor map, trace overview and content store (floormap-pathways-stores-03, high)
  - `cf-*` classes (dashboard-query-18)
  - table filter classes (dashboard-query-19)
  - `ai-message-*` (security-settings-09)
  - `navigation-*` (explorer-documents-shell-10)
  - CurveTabLayout (widgets-1-22)
  - `center-align` and `right-align` (widgets-1-24)
- **Sheets holding many unrelated blocks:**
  - stroom-floormap.css, 1,312 lines (floormap-pathways-stores-02, high)
  - pathways.css (floormap-pathways-stores-05)
  - change-password.css (security-settings-10)
  - sign-in-page.css (security-settings-11)
  - quick-filter.css, lozenge.css, app-page.css and iframe.css (explorer-documents-shell-29, widgets-1-20)
  - stroom-dashboard.css (dashboard-query-22)
  - base.css's class rules (widgets-2-32)
- **Blocks that don't match their component, or roots without a block:**
  - dashboard-query-16, 17 and 25
  - floormap-pathways-stores-04
  - data-pipeline-processing-10
  - explorer-documents-shell-14 and 30
  - widgets-2-15 (`date-time-view`) and widgets-2-16 (`drop-down`)
  - widgets-1-21
- **Bare state classes:**
  - `empty`, `play` and `stop` (security-settings-08)
  - `selected` (floormap-pathways-stores-06)
  - `readonly` (widgets-2-26)
  - `--readonly` instead of `--read-only` (dashboard-query-24)
- **Element and id selectors:** gitrepo-settings-form.css (floormap-pathways-stores-09).
- **Copy of a shared helper:** the floor map's copy of `sr-only` (floormap-pathways-stores-07).

**Systematic fix:** extend `TestStylesheets` so that:
- each sheet's class selectors belong to one block, named after the sheet, with an allowlist for the shared helper sheets
- no class anywhere carries a bare state class other than the documented `disabled`

This turns the remaining renames into failures the build catches, and stops the defect coming back.

### T7. Dead code sweeps (all removals need your approval first)

- **Unused classes and events:**
  - dashboard-query-04
  - floormap-pathways-stores-17
  - data-pipeline-processing-21
  - explorer-documents-shell-21
  - widgets-1-05, 06 and 08
  - widgets-2-01
- **Events never fired, or fired by a controller to itself:**
  - security-settings-21 (PromptEvent, fired only by stories)
  - explorer-documents-shell-22
  - widgets-2-05 (FormatEvent, which should become a callback)
  - widgets-2-24 (DataSelectionEvent)
  - floormap-pathways-stores-19 and data-pipeline-processing-22 (unused fire overloads)
- **Unused `@UiField`s, mostly `pager`:**
  - dashboard-query-07
  - security-settings-22
  - floormap-pathways-stores-23
  - explorer-documents-shell-23 (NotificationList)
  - widgets-2-06 (Pager's separator fields)
  - workbench-13

  Systematic fix: add a check, as a JUnit test over the templates, that every `@UiField` is read and every `ui:field` is bound.
- **Commented-out code**, about 1,500 lines in total. This is one mechanical sweep:
  - dashboard-query-06 and security-settings-23
  - floormap-pathways-stores-18 and data-pipeline-processing-23
  - explorer-documents-shell-25
  - widgets-1-04 and widgets-2-11
- **Unused methods:**
  - dashboard-query-05 and security-settings-20
  - floormap-pathways-stores-20, 22 and 24
  - data-pipeline-processing-22 and explorer-documents-shell-23
  - widgets-1-07, 09, 11 and 12
  - widgets-2-04, 06, 09 and 10 (widgets-2-10 mixes "delete" and "make private"; follow its adjusted list)
- **GIN leftovers:**
  - dashboard-query-08 (duplicate bindings)
  - security-settings-16 and 20 (getAlertController)
  - floormap-pathways-stores-21
  - data-pipeline-processing-08 (the empty TaskModule)
  - explorer-documents-shell-21 (EntityGinjector)
  - workbench-01 and 02
- **Code used only by stories or tests:**
  - widgets-2-02 (SingleLineCodeEditor)
  - widgets-2-03 (the old htree renderers)
  - widgets-1-08 (ConstrainedLong and EditableLong)
  - widgets-1-09 (DataGrid's MyDataGrid compatibility API)
  - explorer-documents-shell-26 (harness hooks: document them rather than delete)
  - floormap-pathways-stores-24 (test seams)
- **Dead CSS:** widgets-2-08 (12 xsd-browser rules), widgets-1-07 (`menu__group-heading`), explorer-documents-shell-24 (`#main`, `#content` and `docRefLinkIcon`), widgets-2-12 (empty `gwt.xml` modules and GWT CSS overrides).

### T8. Package structure: break the cycle and lift shared parts

- **The query↔dashboard cycle** (dashboard-query-03): move the shared results parts to `stroom.query.client.result.*`. Do this together with T3's twin merge.
- **Generic tree widgets in pipeline** (dashboard-query-10, data-pipeline-processing-06, merged): move them to `stroom.widget.htree.client`.
- **Generic helpers in feature packages:**
  - TimeZoneBox, HandlerRegistry and the Clear*CacheEvents (dashboard-query-10)
  - ListDataProvider, ClassNameBuilder and AbstractRestClient (security-settings-18)
  - the grid cells, ColumnSizeConstants and RestDataProvider (data-pipeline-processing-07)
  - SettingsGroup and FloorMapAria (floormap-pathways-stores-30)
  - DaysOfWeek (data-pipeline-processing-24)
  - Glass, MouseHelper and GlobalResizeObserver (widgets-1-13)
  - the reverse case, feature code in shared packages (explorer-documents-shell-19)
- **Split and misplaced widget-module packages:**
  - hyperlink and task (explorer-documents-shell-20, widgets-2-22, merged)
  - the `stroom.data.*` grab-bag (widgets-2-21)
  - classes in the wrong package (widgets-2-23)
  - the XSD browser, which belongs in xmlschema (widgets-2-24)
  - CurveTabLayout, which belongs in content (widgets-1-01)
- **Layout within features:**
  - dashboard-query-09 and 11
  - security-settings-19
  - floormap-pathways-stores-32 and 33
  - data-pipeline-processing-12 (ShowDataEvent)
  - explorer-documents-shell-18
  - widgets-1-14 and 15
  - widgets-2-25
- **Merging `entity` into `document`** (explorer-documents-shell-17).
- **Workbench story packages** (workbench-07 and 12).

### T9. Naming: presenter-era leftovers and names that don't say what the thing is

- **"View", "Impl", "My" and "Custom" leftovers:**
  - ButtonView, ToggleButtonView and DataGridView (widgets-1-02)
  - LayerContainerImpl, including its no-op `setFade` (widgets-1-03)
  - `setXxxView` slot setters (floormap-pathways-stores-26)
  - the `expression-tree-view` block (dashboard-query-17)
  - MySingleSelectionModel (widgets-2-19)
  - the `customdatebox` package (widgets-2-25)
  - HyperlinkEventHandlerImpl (explorer-documents-shell-15)
  - PopupSupportImpl (widgets-1-27)
  - workbench-05, 14 and 15
- **Name clashes:**
  - two QueryPlugins, two QueryModules and two ResultComponents (dashboard-query-12)
  - "component" used for dashboard panes (dashboard-query-13)
  - Action, Option, Refreshable, TableRow and Hyperlink (widgets-2-18)
  - Spinner (widgets-2-13)
- **Names that are wrong or misleading:**
  - MarkdownPreview and MarkdownEdit are the wrong way round (explorer-documents-shell-13)
  - Navigation is really the Explorer (explorer-documents-shell-12)
  - EntityTree and ExplorerPopup (explorer-documents-shell-11)
  - the floor map's editor names are swapped (floormap-pathways-stores-25)
  - the Server Tasks screen has three names (data-pipeline-processing-11)
  - UserAndGroups against UsersAndGroups (security-settings-15)
  - NodeToolsContentPlugin (data-pipeline-processing-09)
- **Suffix rules to settle once:**
  - Edit against Editor for item dialogs (data-pipeline-processing-13)
  - the Dialog suffix (explorer-documents-shell-16, security-settings-17)
  - XxxPlugin for pane registrars (dashboard-query-11)
- **Hungarian field names:** security-settings-17 and floormap-pathways-stores-29.

### Contradictions resolved

- **CurveTabLayout: app-specific or generic?** widgets-1-01 calls it app-specific. floormap-pathways-stores-27's verifier called it a generic widget. I checked: its template has the "Hide Sidebar" toolbar, and its only app user is ContentTabPaneController. So widgets-1-01 stands, and the remark in floormap-pathways-stores-27 is dropped.
- **Monitoring plugins.** floormap-pathways-stores-31 says to move them into their features. data-pipeline-processing-08's verifier says grouping them by menu is defensible. This needs your decision, recorded as D3. Only the uncontested parts of data-pipeline-processing-08 are scheduled: the empty TaskModule, ProcessorTaskPlugin's binding, ManageGlobalPropertiesPlugin's package, and the DelegatingAceCompleter binding.
- **What "Editor" means.** explorer-documents-shell-16 assumes "Editor" means a DocumentEditor subclass. data-pipeline-processing-13 notes that the plan's pilot uses FieldEditor and RuleEditor for item dialogs, and 62 component classes end in Editor against 27 in Edit. The ex-16 rename of the HTTP dialogs to `...Form` waits on the same suffix decision.
- **Fixes for `dashboard-panel`.** dashboard-query-26 says rename, explorer-documents-shell-27 says remove, and floormap-pathways-stores-11 says decide app-wide. These are merged into the single decision in T5.
- **TokenErrorListener** stays (dashboard-query-04, as adjusted). **ButtonView.setEnabled** has live callers (widgets-1-02, as adjusted). **`.stroom .stroom-control-grid`** is live (explorer-documents-shell-24, as adjusted).
- **Spot-checks.** Only one finding rested on its own evidence without independent verification: security-settings-17. I checked it and it holds: CredentialsManagerDialog.ui.xml:7-8 has `lblHtml` and `pnlCredentialsList`. The `maximised` usage in Main is confirmed by two slices.

## Findings by dimension

Ids are listed most severe first within each dimension. Effort is S (small), M (medium) or L (large). The full evidence for each finding is in the slice output; only the fix is repeated here.

### Composition

| id | sev | eff | title / where | fix |
|---|---|---|---|---|
| dashboard-query-01 | high | M | Fixed children (ExpressionTree, ExpressionEditor, QueryHelp, CurrentSelection, ResultStoreList, QueryResultTable, QueryTextPane) in slots. QueryPane, QueryFavourites, QueryHistory, ExpressionEditor, ConditionalFormattingRuleEditor, SelectionHandlerEditor, SolrIndexSettings, ColumnFunctionEditor, QueryEdit, SearchResultStores, QueryResultTableSplit, QueryDataPane | T1: declare in the template and attach |
| security-settings-03 | high | L | 15 parents. UserAccess, AppPermissions, UserAndGroups (3 lists), DocumentUserPermissionsTab, UserPermissionReport, CredentialsManagerDialog, GlobalPropertyTab, ManageActivity, RuleSetSettings, DataRetentionPolicy, ContentTemplateTab, RuleEditor, DataRetentionRuleEditor, ContentTemplateEdit | T1 |
| floormap-pathways-stores-01 | high | L | 22 slots. FloorMapEditor (5), FloorMapMap (3), PathwaysSplit, PathwayEdit, TracesListTab, ContentStoreTab, Caches, ScriptSettings, ImportsTab | T1 |
| data-pipeline-processing-01 | high | L | 18 parents, about 27 slots. Jobs, Nodes, MetaBrowser, ProcessorTasksTab, ProcessorFilterTasks, Processors, ProcessorEdit, FeedDependenciesEditor, ProcessorProfileEdit, NodeGroupEdit, the volume group edits, IndexFieldEditor, PipelineStructure, ServerTasks, ExpressionDialog, SourceViewer | T1. ExpressionTree and ExpressionEditor are already components, so nothing waits on them |
| explorer-documents-shell-01 | high | L | Main.java:95-258 rebuilds the explorer/content SplitPane in Java. MainController.java:96-97 | Declare a `SplitPane` holding Navigation and ContentTabPane in Main.ui.xml. Keep only the AI dock and the maximised view as runtime slots |
| dashboard-query-02 | high | L | Dashboard/query twin components and controllers (Table, Vis, TextPane, MultiRules/TableFilterInput, column managers, SearchModel/QueryModel) | T3: one component per thing, then shared controllers |
| explorer-documents-shell-02 | med | L | FindPanel, LinkedEvents, DependenciesTab, DuplicateManagement, ScheduledProcessing, AnalyticDataShards | T1. Make FindPanel a container that wraps its declared list |
| explorer-documents-shell-03 | med | M | ExecutionSchedulesController.java:86-222: bare PagerPanel view, grid, buttons and label built in Java | Add an ExecutionSchedules component |
| floormap-pathways-stores-13 | med | S | DatabaseTablesMonitoringController.java:64-75: bare PagerPanel and `new DataGrid` | Add a DatabaseTables component |
| explorer-documents-shell-05 | med | L | AnnotationEditController:132-542, InfoDocumentController:102-123 and DocumentPluginEventManager:864-956 build markup | AnnotationHistory, InfoDocument and DeleteConfirmation components |
| security-settings-01 | med | L | AskStroomAiController.java:78-830: message markup and raw `<button>`s in the controller | An AiMessage component with ToolbarButtons |
| security-settings-02 | med | S | ApiKeys, Users, SigningKeys, Credentials, Accounts | T2: delete |
| data-pipeline-processing-04 | med | S | DataPreviewTab, SourceTab | T2: delete |
| security-settings-04 | med | S | CreateUser.java:66 inserts the group form | Declare a NameEntry, and give CreateNewGroupController an attach form |
| security-settings-05 | med | M | UserPreferencesDialog, AskStroomAiConfigDialog and CredentialEdit copy LinkTabPanel (+ floormap-pathways-stores-10) | Declare `<e:LinkTabPanel>`. CredentialEdit uses TabbedController |
| security-settings-06 | med | M | Login and AuthenticationError draw SVG with setInnerHTML. ChangePassword's badge is made in Java. CommonAlert sets aria with setAttribute | Use `svg:Icon`. Add typed autocomplete and aria setters. Declare the badge |
| dashboard-query-20 | med | S | FilterCell.java:259-269: raw input and button | Build it from TextFieldCell and a toolbar-button cell, then drop `.result-table__grid input` |
| data-pipeline-processing-02 | med | M | The DataViewer and SourceViewer navigator and progress slots | Declare the children, and use setXxxVisible |
| data-pipeline-processing-03 | med | S | SteppingScreen.java:88 inserts StepLocationLink and StepControl | Declare and attach both. Make stepMessage a Label |
| explorer-documents-shell-07 | med | M | AssignYourselfLink is a hand-made button. SettingBlock builds its icon and ARIA in Java | Use LinkButton (keep stopPropagation). Use a declared InlineSvgButton |
| widgets-1-16 | med | S | SelectionList.java:109-142: grid made in Java | Declare `<dg:DataGrid>` inside the PagerPanel |
| widgets-1-18 | med | S | HeadedPagerPanel copies PagerPanel | Delete it. JobNodeList and NodeJobList wrap a FormGroup around a PagerPanel |
| widgets-1-19 | med | L | OkCancel, Close, AcceptReject and CreateOkCancelContent frames; Dialog and ResizableDialog | One DialogFrame. ResizableDialog becomes Dialog plus resize handles |
| widgets-2-29 | med | M | DropDownBox.java:161-177 inserts its feedback into the parent's DOM | Declare the feedback label inside its own root |
| workbench-11 | med | S | ColumnInteractionGrids.java:525: raw `<input>` cell | Use FilterCell or TextFieldCell |
| security-settings-07 | low | M | DescriptionBuilder sits in the security root | Move it next to its users. Optionally add a PermissionDetails component |
| floormap-pathways-stores-14 | low | M | ImportsTabController.java:77 adds DocRefList's column and buttons from Java | Declare them in DocRefList. The HasToolbar toolbars stay as they are |
| floormap-pathways-stores-16 | low | S | FloorMapCanvas.java:1066 caption and FloorMapLayerStyle.java:223 preview made with `new` | Declare a Label and an Html |
| data-pipeline-processing-17 | low | S | PipelineStructureController.java:638 sets classes from Java | Use the CodeEditor's API, and drop the commented lines |
| data-pipeline-processing-18 | low | S | PipelineTree.java:52 (and ExpressionTree.java:70) make tree panels with `new` | Add a `@UiChild` to DraggableTreePanel |
| data-pipeline-processing-19 | low | M | PipelinePathBar.java:385 builds segments with DOM calls | A segment template. Leave ProcessorInfoBuilder as it is |
| data-pipeline-processing-20 | low | S | DataViewer.ui.xml:22: div used as a button | Use LinkButton, and drop the `--enabled` modifier |
| explorer-documents-shell-04 | low | M | LinkedEvents.java:57, AnnotationTagsController:76, AnnotationLinksController:85 | Declare the buttons in the templates |
| explorer-documents-shell-06 | low | S | StreamingProcessing | T2: delete |
| explorer-documents-shell-08 | low | M | Setting blocks, filter-over-tree pickers and build info repeated | Shared components. ExplorerPopup switches to ScrollPane and control-box |
| widgets-1-17 | low | M | AbstractTabBar.java:104: TabSelector made in Java | HTMLPanel roots that declare it. Fix the comments |
| widgets-2-28 | low | S | Icons set from Java in DropDownBox, ValueSpinner and SpinnerSmall | Declare `svg:Icon` |
| widgets-2-30 | low | M | XSDConstraintDisplay and XSDDisplayBox wrappers built in Java | Templates. The XSDDisplay scaffold stays |
| widgets-2-31 | low | S | IndicatorPopup.java:31, RightBar.java:40 | Templates |
| workbench-08 | low | M | Stories copy template fragments (logo, sign-in card, activity button, badge, tab row) | Show the real component, or delete the story. Fix the stale comment |

### BEM CSS and sheets

| id | sev | eff | title / where | fix |
|---|---|---|---|---|
| floormap-pathways-stores-02 | high | M | stroom-floormap.css: 1,312 lines, about 15 blocks | Split into one sheet per component |
| floormap-pathways-stores-03 | high | L | Flat names in the floor map, TraceOverviewChart (generic names: `children`, `duration`, ...) and ContentStoreContentPackDetails | Use `block__element` / `block--modifier` throughout |
| dashboard-query-16 | med | S | FlexLayout uses the `dashboard` block. DashboardBody sets `dashboard--design-mode` | Move to the `flex-layout` block, add FlexLayout.setDesignMode, delete dashboard.css |
| dashboard-query-17 | med | S | `expression-tree-view` | ExpressionTree becomes `expression-tree`, the panel `expression-tree-panel`. Update the stories |
| dashboard-query-18 | med | S | Flat `cf-*` classes and `cfDynamicN` | One `conditional-formatting` block. Change the class strings in query-api |
| dashboard-query-19 | med | S | Table filter flat names in FilterCell | `table-filter-columns`, `table-filter-column` and `filter-cell` blocks |
| dashboard-query-21 | med | S | Inline SafeStyles in TableController:841 and QueryResultTableController:777 | `result-table__cell--wrap` and `--grouped` |
| dashboard-query-22 | med | S | `stroom-dashboard-table` repeated; VisToolbarRow borrows pager; QueryDataPane borrows `query-buttons` | Each component's own block. Create text-pane.css |
| security-settings-08 | med | S | AskStroomAi uses bare `empty`, `play` and `stop`, and `navigation-header-spinner` | Modifiers plus `ask-stroom-ai__spinner` |
| security-settings-09 | med | M | `ai-message-*` parts | ai-message.css with BEM elements (goes with security-settings-01) |
| security-settings-10 | med | M | `feedback` and `password-policy` in change-password.css (+ widgets-1-23) | FormGroup feedback. One block per sheet |
| security-settings-11 | med | S | sign-in-page.css styles authentication-error; base.css:433 | Move the rules into authentication-error.css. Share the hide-loading helper with Core |
| floormap-pathways-stores-05 | med | M | pathways.css holds four components | traces-list, traces-list-tab and trace-overview-chart sheets |
| floormap-pathways-stores-06 | med | S | Bare `selected` (TraceOverviewChart.java:902) | `__operation--selected` |
| floormap-pathways-stores-08 | med | S | `wide-control` in FloorMapObjectEdit does nothing | Remove it, or use `floor-map-object-edit__field` |
| floormap-pathways-stores-09 | med | S | gitrepo-settings-form.css element and id selectors | `git-repo-settings__field` |
| floormap-pathways-stores-10 | med | M | FloorMapDock copies LinkTabPanel | Merged into security-settings-05 |
| data-pipeline-processing-14 | med | S | ProfilePeriodEditor uses `credentials-details-dialog`; the stepping Terminate button uses `query-buttons`; step-location-link sits in stepping-screen.css | `form`. A shared `toolbar-button--stop`. Its own sheet |
| data-pipeline-processing-15 | med | S | `stroom-control` used as a panel (volume groups, stepping) | Use `control-box` plus a FormGroup |
| data-pipeline-processing-16 | med | S | `dashboard-panel` and redundant wrappers (27 uses) | T5 |
| explorer-documents-shell-09 | med | S | `main-screen` nested on three elements; inline border and size; `maximised` | One root. A custom property. A modifier (+ dashboard-query-24) |
| explorer-documents-shell-10 | med | S | Navigation's elements named as separate blocks | `navigation__*` (or `explorer__*`, with explorer-documents-shell-12) |
| explorer-documents-shell-28 | med | S | DocumentAssetTree uses `explorer-tree`; FindInContent uses `find-panel` and `form-group` elements | Their own blocks. Fold `find-buttons` in |
| widgets-1-21 | med | S | PagerPanel has no block (`pager`, `pager-toolbar`, `list-container`) | A `pager-panel` block. Delete the two one-rule sheets |
| widgets-1-22 | med | S | CurveTabLayout uses `tab-bar-container` and `stroom-main-content-container` | Block root with `__bar` and `__content` |
| widgets-1-23 | med | S | NameDialog's `feedback` label | Merged into security-settings-10 |
| widgets-1-24 | med | S | `center-align`, `*-icon-column-header`, `column-header__sort` in the grid | `data-grid__header-cell--align-*`, `data-grid__sort` |
| widgets-2-26 | med | S | DropDownBox's bare `readonly`; stroom-control.css holds unrelated blocks (+ widgets-1-25, data-pipeline-processing-25) | A modifier. Split the sheet. Fix the stale comment |
| widgets-2-27 | med | M | Inline styles in RightBar and SafeHtmlUtil (grey, colour) | SafeHtmlTemplates, classes, custom properties. Delete divWithStyle |
| workbench-09 | med | M | About 238 inline styles in stories; StoryPanels | A story-layout.css block. Reuse control-box |
| dashboard-query-23 | low | S | `stroom-control` used as a frame; `stroom-control-label` | control-box, `expression-editor--framed`, a basic-settings element |
| dashboard-query-24 | low | S | `.maximised .dashboard-body`; `--readonly` | Merged with explorer-documents-shell-09. Rename to `--read-only` |
| dashboard-query-25 | low | S | Block missing on the QueryDataPane, ElasticIndexFieldList and SolrIndexFieldList roots; query-help has no sheet | Add the root classes. Create query-help.css |
| security-settings-12 | low | S | `credentials-details-dialog` shared by three components; `wide-control` | One block per component, with an `__expiry` element |
| security-settings-13 | low | S | `document-user-permissions` block; `dashboard-panel` in RuleSetSettings | `document-user-permissions-list`. T5 |
| floormap-pathways-stores-07 | low | S | `stroom-floormap-visually-hidden` | Use `sr-only` |
| floormap-pathways-stores-11 | low | S | Unstyled `contentstore-details-name`; `dashboard-panel` | Style or drop it. T5 |
| floormap-pathways-stores-12 | low | S | FloorMapSwatchHtml.java:185 inline style | A class plus a `--floor-map-swatch__size` property |
| data-pipeline-processing-25 | low | S | `form-page` hook; `stroom-control-label`; `form-group` on a SimplePanel | Remove the hook. Give the label its own block. Use a FormGroup |
| explorer-documents-shell-27 | low | S | `dashboard-panel` in analytics, Markdown and Navigation | T5 |
| explorer-documents-shell-29 | low | S | quick-filter.css, lozenge.css, app-page.css and iframe.css hold extra blocks | One block per sheet |
| explorer-documents-shell-30 | low | S | TypeFilter: block on the grid, element on the root | Swap them. Fix the comment |
| widgets-1-25 | low | M | stroom-control.css | Merged into widgets-2-26 |
| widgets-1-26 | low | S | PopupUtil.java:165 inline width and height | `--popup__width` and `--popup__height` |
| widgets-2-32 | low | S | base.css class rules; `dashboard-panel` in XSDBrowser | Move each rule to its block's sheet. T5 |
| workbench-10 | low | S | Unstyled `hoverable-row` | Delete it, or use the story-layout block |

### Dead code (ask before removing)

| id | sev | eff | title | fix |
|---|---|---|---|---|
| dashboard-query-04 | med | S | RecordingEventBus, MyScriptInjector, ElasticIndexFieldDataProvider, InsertQueryElementEvent, Search{Start,End}Event, Views, ReopenResultStoreEvent and DashboardPlugin.reopen | Delete. Keep TokenErrorListener |
| dashboard-query-05 | med | S | createCustomSwatch, fetchDataSourceDescription, setSourceType, getResultStoreModel, getOrigin, removeTokenErrorListener, the ComponentState getters, App.appendStackTraces | Delete |
| dashboard-query-06 | med | S | About 500 commented-out lines, including GWTP place-manager leftovers | Delete |
| security-settings-20 | med | S | UserPermissionsReportPlugin, getAlertController, five unused methods | Delete |
| floormap-pathways-stores-17 | med | S | DataUploadService(Async), CommonSpanBuilder | Delete |
| data-pipeline-processing-21 | med | S | FileData, InterceptingSelectionChangeHandler | Delete |
| explorer-documents-shell-21 | med | S | CloseExplorerTabEvent, OpenExplorerTabEvent, the explorer FocusEvent, UrlQueryParameterChangeEvent, HelpAction*, DocumentAssetImageResource, FindDocResultListHandler, SizeUtil, EntityGinjector | Delete |
| widgets-2-01 | med | S | HandlerRegistrations, TableUtil, Debug, FixedNodeExtentProvider, Indicator and Format handler types, XMLStyler (+ its test) | Delete |
| widgets-2-02 | med | S | SingleLineCodeEditor(+Controller), used only by stories | Delete, or record which screen will adopt it |
| widgets-2-03 | med | S | The old htree renderer family, used only by stories | Move the layer constants, then delete |
| widgets-2-04 | med | S | INDICATOR_POPUP, RightBar.getWidth, CodeEditorController's dead methods | Delete. Make Editor.markClean private |
| widgets-2-05 | med | S | FormatEvent | Replace with a Runnable callback (7 callers and 1 test) |
| widgets-2-06 | med | S | Pager: 13 pass-through overrides, startLoading, the separator fields, a setTitle override | Delete. Make two methods private |
| widgets-2-07 | med | S | XSDConstraintDisplay.java:49 always passes `true`, so a pattern list is headed "Enumeration". This is a visible bug | Pass `false`, or use an enum. Add a story |
| workbench-01 | med | S | 9 empty GIN mirror modules | Delete them from the ginjector lists |
| workbench-04 | med | S | MyDataGrid `th`/`td` selectors and the duplicated clickHeader | Use the DataGrid selectors. Keep one helper |
| dashboard-query-07 | low | S | Unused pager fields; dead steppingButton in QueryTextPane | Remove |
| dashboard-query-08 | low | S | Duplicate HyperlinkEventHandlerImpl and VisFunctionCache bindings; modules listed twice | Bind each once |
| security-settings-21 | low | S | PromptEvent, fired only by stories | Delete, or record why it is kept |
| security-settings-22 | low | S | Unused @UiFields (AskStroomAi, KeyBindingPreferences, FieldList) | Remove |
| security-settings-23 | low | S | Commented-out code | Delete |
| floormap-pathways-stores-18 | low | S | Commented blocks (TraceOverviewChart, TracesList, NodeMonitoringPlugin) | Delete |
| floormap-pathways-stores-19 | low | S | OpenFeedEvent and CopyFeedUrlEvent overloads and builders | Delete |
| floormap-pathways-stores-20 | low | S | 7 FloorMapAria helpers | Delete |
| floormap-pathways-stores-21 | low | S | SqlTemporalStoreGinjector | Delete |
| floormap-pathways-stores-22 | low | S | DocRefList.setRefreshVisible; fixed-value parameters | Delete. Make the column fixed |
| floormap-pathways-stores-23 | low | S | Unused pager (ConstraintList, ScriptDependencyList) | Remove |
| floormap-pathways-stores-24 | low | S | TracesPlugin.securityContext; methods used only by tests | Remove, or mark as test seams |
| data-pipeline-processing-22 | low | S | 7 methods and 3 fire overloads | Delete |
| data-pipeline-processing-23 | low | S | Commented-out code | Delete |
| explorer-documents-shell-22 | low | S | ShowAboutEvent; the self-fired Focus*Event | Delete. Call the methods directly |
| explorer-documents-shell-23 | low | S | ExplorerCell and ExplorerTreeController methods, 7 DataGridUtil builders, DynamicStyles.remove, the NotificationList pager | Delete |
| explorer-documents-shell-24 | low | S | `#main` and `#content` rules, `docRefLinkIcon`, commented CSS | Delete. Keep `stroom-control-grid` |
| explorer-documents-shell-25 | low | S | Commented-out code | Delete |
| explorer-documents-shell-26 | low | S | Methods used only by the harness | Document them as harness hooks |
| widgets-1-04 | low | S | Commented-out code (PopupUtil, LayerContainerImpl, FormGroup, ...) | Delete |
| widgets-1-05 | low | S | The tab CloseEvent, MaximiseRequestEvent, HasMaximiseRequestHandlers | Delete |
| widgets-1-06 | low | S | DialogEvent | Delete it and its handler |
| widgets-1-07 | low | S | GroupHeading, withSimpleMenuItem(If), withInfoMenuItem(If) | Delete. Keep withItemIf |
| widgets-1-08 | low | S | ConstrainedLong, EditableLong | Delete. Move the story to EditableInteger |
| widgets-1-09 | low | S | DataGrid's MyDataGrid compatibility API; InfoColumn's `td` check | Delete |
| widgets-1-10 | low | S | DataGridSortIcon; SortIcon.UNSORTED | Delete, and use SortIcon |
| widgets-1-11 | low | S | FindResultLists.create | Delete |
| widgets-1-12 | low | S | EventBinder (used, but presenter-style) | Wire the handlers in constructors, then delete it |
| widgets-2-08 | low | S | 12 unused xsd-browser rules | Delete |
| widgets-2-09 | low | M | Unused GWT CheckBox API in TickBox | Remove |
| widgets-2-10 | low | M | Unused methods across the widget module | Delete or make private, following the adjusted list |
| widgets-2-11 | low | S | Commented-out code | Delete |
| widgets-2-12 | low | S | Vis and WebSocket `gwt.xml`, missing shared source paths, GWT CSS overrides, empty .gitignore | Delete |
| workbench-02 | low | S | Empty `.java` file in app/gin/dashboard | `git rm` |
| workbench-03 | low | S | Unused ginjector getters, story helpers and framework methods | Delete |
| workbench-13 | low | S | ToolbarTemplate's `saveAs` and `last` fields | Remove |

### Naming

| id | sev | eff | title | fix |
|---|---|---|---|---|
| widgets-1-01 | high | M | CurveTabLayout is app-specific but lives in the widget module, named unlike its controller | Rename to ContentTabPane and move it into content.client.component. Fold in CurveTabLayoutPanel |
| dashboard-query-12 | med | M | Two QueryPlugins, two QueryModules, two ResultComponents | Rename the dashboard ones (QueryPaneType, ...) |
| dashboard-query-13 | med | M | Dashboard "component" means pane | DashboardPaneController, PaneRegistry, UnknownPane, ... |
| floormap-pathways-stores-04 | med | M | Blocks and sheets don't match their classes; floormap- against floor-map- | Use the kebab case of the class name |
| floormap-pathways-stores-25 | med | M | FloorMapDocEditor and FloorMapEditor are swapped; FloorMapMap | FloorMapEditor (the document), FloorMapEditorTab, FloorMapMapTab |
| data-pipeline-processing-09 | med | S | NodeToolsContentPlugin | Rename to ToolsContentPlugin, in content.client |
| explorer-documents-shell-11 | med | M | EntityTree, EntityCheckTreeController, ExplorerPopup | DocumentPicker, TickTreeController, DocumentChooser |
| explorer-documents-shell-13 | med | M | MarkdownPreview and MarkdownEdit are the wrong way round | MarkdownEditor and DocumentationTab |
| widgets-1-02 | med | M | ButtonView, ToggleButtonView (aliases), DataGridView | Delete ToggleButtonView and migrate its 6 callers. Rename the others |
| widgets-1-03 | med | M | LayerContainerImpl; no-op setFade | Rename to LayerContainer. Delete fade |
| widgets-1-20 | med | M | QuickFilterDialog and QuickFilterPage are layouts | QuickFilterPanel with modifiers and its own sheet |
| widgets-2-13 | med | M | Spinner (a model) against the busy spinners; it duplicates SpinStepper | Merge the models. Rename to BusySpinner |
| widgets-2-14 | med | S | TreeRenderer2, CellRenderer2 | Drop the 2 after widgets-2-03 |
| widgets-2-15 | med | S | `date-time-view` block; Calendar fields named datePicker | `date-time-picker` block; `calendar` |
| widgets-2-16 | med | M | The `dropdowntree` package; `drop-down` block; `ellipsesBtnPanel` | `stroom.widget.dropdown`; `drop-down-box`; `icon` |
| widgets-2-17 | med | M | RightBar, IndicatorPopup, Editor against AceEditorController | OverviewBar, OverviewTooltip, AceEditorPane |
| workbench-05 | med | M | Story titles named for things that no longer exist (SourcePresenter, ...) | Rename them and move the screenshot baselines |
| dashboard-query-14 | low | S | Rules, MultiRules, QueryButtons, VisToolbarButton, AbstractRowStyles, State, the QueryDoc* names | Renames as adjusted |
| dashboard-query-15 | low | S | TimeZoneBox fields and ids; TimePreferences repeats its markup | Rename. TimePreferences declares a TimeZoneBox |
| dashboard-query-26 | low | M | `dashboard-panel` | T5 |
| security-settings-14 | low | S | UserAndGroupActions, CreateNewGroupController, SignInPageController | Add the Controller suffix; GroupNameController; Abstract prefix |
| security-settings-15 | low | S | UserAndGroups against UsersAndGroups | Use UsersAndGroups throughout |
| security-settings-16 | low | S | ChangePasswordGinjector/Module, PolicyModule, AskStroomAIModule | Identity*, ReceiveRulesModule, AskStroomAiModule |
| security-settings-17 | low | S | `lblHtml` and `pnlCredentialsList`; CredentialsManagerDialog | `note` and `list`; CredentialsPicker |
| floormap-pathways-stores-26 | low | S | `setXxxView` slot setters | Removed by T1 |
| floormap-pathways-stores-27 | low | M | PathwaysSplit, FloorMapInit, DocRefList, FactObject, FloorMapEventsQuery | Renames as listed |
| floormap-pathways-stores-28 | low | S | Monitoring plugin names; `onOpen` in CopyFeedUrlEvent | JobsPlugin and the others; `onCopyUrl` |
| floormap-pathways-stores-29 | low | S | GitRepoSettings Hungarian field names | Rename |
| data-pipeline-processing-10 | low | M | Pipeline tree classes against their blocks | Make the names and blocks agree |
| data-pipeline-processing-11 | low | S | Server Tasks named three ways | Use ServerTasks* throughout |
| data-pipeline-processing-12 | low | S | Event Handler interface names | Nested `Handler`. Move ShowDataEvent to an event package |
| data-pipeline-processing-13 | low | S | Edit against Editor; DocRefSelection | Decision D2, then one pass |
| explorer-documents-shell-12 | low | M | Navigation is really the Explorer | Explorer / ExplorerController / ExplorerPlugin, block `explorer` |
| explorer-documents-shell-14 | low | S | Core / `app-panel`; IFrame blocks; `main-screen` | AppPage; one IFrame block; document why main-screen |
| explorer-documents-shell-15 | low | S | DirtyKeyDownHander, HyperlinkEventHandlerImpl, DurationLabel, AnnotationEditSupport | Fix the spelling; HyperlinkOpener; RelativeTimeRenderer; AnnotationScreens |
| explorer-documents-shell-16 | low | M | Dialog suffix on 6 components; HttpClient/TlsConfigEditor | Drop "Dialog". The HTTP rename waits on D2 |
| widgets-1-27 | low | S | contextmenu TextField, DescriptionHTML/HelpHTML, PopupSupportImpl | TextFieldTarget; *Html; PopupHost |
| widgets-2-18 | low | M | Action, Option, Refreshable, TableRow, Hyperlink | Names that say what each is |
| widgets-2-19 | low | S | MySingleSelectionModel (13 users) | Rename |
| widgets-2-20 | low | M | XSD/XML casing; *Display names; `--xsdbrowser-*` variables | XsdDiagram, ...; `--xsd-browser__*` |
| workbench-06 | low | M | Lower-camel, spaced or groupless story titles | UpperCamel titles under a group |
| workbench-14 | low | S | javadoc links to deleted ViewImpl classes | Repoint |
| workbench-15 | low | S | SidebarView (a known exception) | Optional rename |

### Packages

| id | sev | eff | title | fix |
|---|---|---|---|---|
| dashboard-query-03 | high | L | query↔dashboard cycle | `stroom.query.client.result.*` |
| dashboard-query-09 | med | M | Components outside client.component; query.client is a mixed package | One package per role: component, model, cell, event |
| dashboard-query-10 | med | S | TimeZoneBox, HandlerRegistry, the Clear*CacheEvents; pipeline tree widgets (+ data-pipeline-processing-06) | Widget module or owning feature; tree widgets to htree |
| security-settings-18 | med | S | ListDataProvider, ClassNameBuilder, AbstractRestClient, alert helpers | Shared packages |
| floormap-pathways-stores-30 | med | S | SettingsGroup, FloorMapAria | widget.form, widget.util |
| floormap-pathways-stores-31 | med | M | Monitoring plugins live outside their features | Decision D3 |
| floormap-pathways-stores-32 | med | M | Floor map editor/component/root mixing; pathways Bezier | model, query and geometry packages |
| data-pipeline-processing-05 | med | M | Processor-task screens duplicated, and lists in `data` | Merge into ProcessorTasks, in processor.task |
| data-pipeline-processing-06 | med | M | Tree widgets in pipeline | Merged into dashboard-query-10 |
| data-pipeline-processing-07 | med | M | Generic cells and helpers in `data` | Widget module, or `util.client.cell` |
| explorer-documents-shell-17 | med | L | `entity` against `document` | Merge into `stroom.document.client.*` |
| widgets-1-13 | med | S | Glass, MouseHelper, DoubleClickEvent, GlobalResizeObserver | `widget.util.client` |
| widgets-2-21 | med | M | `stroom.data.*` grab-bag in the widget module | Rehome each class; AI events to ai.client |
| widgets-2-22 | med | M | Split hyperlink and task packages (+ explorer-documents-shell-20) | One package per module |
| widgets-2-23 | med | M | IconBox, HtmlPane, CurrentPreferences, ShowHelpEvent, ScrollbarMetrics | Rehome |
| widgets-2-24 | med | M | XSD browser in the widget module; DataSelectionEvent | Move to xmlschema. Use a callback |
| workbench-07 | med | L | app/main catch-all; duplicate title; `Screens` root | Group by area. Merge the duplicate title |
| dashboard-query-11 | low | S | Nine XxxPlugin pane registrars across six modules | One DashboardPaneTypes class |
| security-settings-19 | low | S | ContentTemplatePlugin, ExplorerClient, two event packages, splash, rows and models | Rehome |
| floormap-pathways-stores-33 | low | S | Feed and pathways events at the root; SupportedRetentionAge | client.event; index |
| data-pipeline-processing-08 | low | M | Empty TaskModule; misplaced bindings | Uncontested parts only (see D3) |
| data-pipeline-processing-24 | low | S | DaysOfWeek | Widget module |
| explorer-documents-shell-18 | low | S | Components and events outside client.component/event; non-events in event packages | Rehome |
| explorer-documents-shell-19 | low | M | Feature classes in shared packages | Move the adjusted list only |
| explorer-documents-shell-20 | low | M | quickfilter, help, histogram and menubar package split | Rehome (hyperlink merged into widgets-2-22) |
| widgets-1-14 | low | L | `stroom.cell.info`, `cell.*.shared`, `stroom.item`, RedGreenTextCell | Rename the packages |
| widgets-1-15 | low | S | FileChooserController and MenuController not in component; QuickSettings | Move |
| widgets-2-25 | low | M | datepicker, progress and tooltip layouts; customdatebox | One rule. Rename to duration |
| workbench-12 | low | S | Story templates in resources | Move beside their Java. Extend Component |

## Proposed waves

Waves are ordered so that the mechanical ones run first. They are low risk and they clear the ground, which makes the composition diffs smaller. Every wave ends with a build, the stylesheet audit, and the targeted workbench stories (run in an rsync copy). Composition waves also need the read-only and dialog stories.

**Decisions (taken 29 October 2026):**
- D1, `dashboard-panel`: **removed** from all its places; a pane whose direct form-group labels need
  the nudge gets it as a modifier of its own block (the screenshot diff shows which). The
  `--dashboard-panel__*` colours stay.
- D2, suffixes: **Editor** for everything that edits (item dialogs and documents alike), and **no
  Dialog suffix**: a component says what it is; being shown in a dialog is how its controller shows it.
- D3, monitoring plugins: **moved into their features' packages**; the menu grouping stays in the
  menu code.
- D4, removals: **all of the list is approved**, code only stories or tests use included (its
  stories go with it), except harness hooks the workbench needs, which get a comment saying so.
- D5, pace: **all waves in order**, each committed with its build, TestStylesheets and story checks
  (a full workbench run every couple of waves); a report at the end; stop only for a decision not
  covered here.
- D6, attach: **every component that is ever a child** gets a controller with
  `attach(component)` that no longer injects its view; top-level screens and dialogs keep injecting
  theirs. Both forms coexist while the waves convert them.
- D7, the dashboard/query twins: **merged into one shared package** (`stroom.query.client.result`),
  breaking the cycle; their controllers merged where they behave alike, kept apart (sharing the
  component) where they don't.
- D8, checks: **TestStylesheets extended** (a sheet's classes belong to one block named after the
  sheet, shared sheets allow-listed; no bare state classes but the documented `disabled`), and a
  **template binding test** (every `@UiField` read, every `ui:field` bound).

1. **Dead code, part 1: whole classes, events, GIN leftovers and empty files.** About 60 files, small.
   - dashboard-query-04 and 08
   - security-settings-20 and 21
   - floormap-pathways-stores-17 and 21
   - data-pipeline-processing-21 and 08 (TaskModule only)
   - explorer-documents-shell-21 and 22
   - widgets-1-05, 06, 07 and 08
   - widgets-2-01, 02, 03 and 12
   - workbench-01, 02 and 03
2. **Dead code, part 2: commented-out code, unused methods and fields, unused @UiFields.** Also add the `@UiField`/`ui:field` binding test. About 120 files, small to medium.
   - dashboard-query-05, 06 and 07
   - security-settings-22 and 23
   - floormap-pathways-stores-18, 19, 20, 22, 23 and 24
   - data-pipeline-processing-22 and 23
   - explorer-documents-shell-23, 25 and 26
   - widgets-1-04, 09, 10, 11 and 12
   - widgets-2-04, 06, 08, 09, 10 and 11
   - workbench-04 and 13
   - widgets-2-07 (the visible bug) and widgets-2-05 (the callback) go here too
3. **Framework: `Controller.attach(component)`.**
   - Convert ExpressionTree, ExpressionEditor, QueryHelp and CurrentSelection to attach.
   - Declare them in their roughly 20 parents: the expression parts of dashboard-query-01, security-settings-03 and data-pipeline-processing-01.
   - Medium effort. Run the stories for every parent.
4. **Empty wrappers and duplicate frames.** Medium.
   - security-settings-02, data-pipeline-processing-04 and explorer-documents-shell-06 (T2)
   - widgets-1-18, security-settings-05 with floormap-pathways-stores-10, and security-settings-04
5. **Composition by area: lists and fixed slots.** Large. Split it into sub-waves by area, using the attach form from wave 3:
   - 5a: security
   - 5b: data, processing and pipeline (data-pipeline-processing-01, 02 and 03)
   - 5c: floor map, pathways and stores (floormap-pathways-stores-01, 13 and 14)
   - 5d: explorer, analytics and annotation (explorer-documents-shell-02, 03 and 04)
   - 5e: the Main shell (explorer-documents-shell-01, with explorer-documents-shell-09's single root)
   - 5f: widget internals (widgets-1-16, widgets-1-17, data-pipeline-processing-18)
   - 5g: the rest of the query slots in dashboard-query-01
6. **Markup out of controllers and Java (T4).** Medium to large.
   - security-settings-01 with 09 (AiMessage)
   - explorer-documents-shell-05
   - dashboard-query-20 with workbench-11
   - security-settings-06 and explorer-documents-shell-07
   - data-pipeline-processing-20
   - widgets-2-28, 29, 30 and 31
   - floormap-pathways-stores-16
   - data-pipeline-processing-17 and 19
   - the inline styles: dashboard-query-21, floormap-pathways-stores-12, widgets-1-26, widgets-2-27
7. **CSS check plus shared-class decisions (T5).** Medium. First extend `TestStylesheets` with the "one block per sheet, no bare state classes" rule and an allowlist. That gives the list of failures to work through. Then apply D1 and the T5 sweeps:
   - dashboard-query-23, 24 and 26
   - security-settings-10, 12 and 13
   - floormap-pathways-stores-07, 08 and 11
   - data-pipeline-processing-14, 15, 16 and 25
   - explorer-documents-shell-27 and 24
   - widgets-1-23 and 25
   - widgets-2-26 and 32
8. **BEM renames, sheet by sheet (T6).** Large but mechanical; each sheet is its own commit and story run.
   - floormap-pathways-stores-02, 03, 04, 05, 06 and 09
   - dashboard-query-16, 17, 18, 19, 22 and 25
   - security-settings-08 and 11
   - explorer-documents-shell-10, 28, 29 and 30
   - widgets-1-21, 22 and 24
   - widgets-2-15 and 16 (block part)
9. **Class renames (T9).** Medium, mechanical; stories are renamed with their classes.
   - widgets-1-02, 03 and 27
   - widgets-2-13, 14, 17, 18, 19 and 20
   - dashboard-query-12, 13, 14 and 15
   - floormap-pathways-stores-25 to 29
   - data-pipeline-processing-09 to 13 (with D2)
   - explorer-documents-shell-11 to 16
   - security-settings-14 to 17
   - workbench-05, 06, 14 and 15
10. **Package moves (T8).** Large, mechanical: IDE moves plus `gwt.xml` updates.
    - dashboard-query-09, 10 (with data-pipeline-processing-06) and 11
    - security-settings-18 and 19
    - floormap-pathways-stores-30 to 33 (31 with D3)
    - data-pipeline-processing-07 and 24
    - explorer-documents-shell-17 to 20
    - widgets-1-01, 13, 14 and 15
    - widgets-2-21 to 25
    - workbench-07 and 12
11. **Duplicates, part 2: the query/dashboard merge.** Large. It goes last because it touches the most code and gains from every wave before it.
    - dashboard-query-02 and 03 (the shared results package, then one component per thing)
    - data-pipeline-processing-05
    - widgets-1-19 (DialogFrame)
    - explorer-documents-shell-08

## Not covered or uncertain

- **Dead methods with common names** (`setData`, `refresh`, `read`, `setList`, `show`, ...) were not checked in any slice. Unused overloads that share a name with a used method may also remain.
- **Large files were sampled, not read line by line:** FlexLayout, TermEditor and the large table/vis controllers; AskStroomAi, ContentTemplateTab, DataRetentionPolicy and UserAndGroups controllers; DocumentPluginEventManager, AnnotationEditController, ExplorerTreeController and the analytics processing controllers; the floor map canvas, editor and map controllers and the editor model; TraceOverviewChart; the grid controllers' column code; DataGrid's internals (GridRows, GridHtml, keyboard handling, column interactions); KeyBinding; IntlDateTimeFormat; the Abego tree layout port.
- **Public-method fan-in in widgets-1** was checked only for DataGrid and MenuBuilder. Other classes there were checked at class level only.
- **The templates of dumb list components** in the security slice were not opened to look for unused CSS.
- **Data-driven DOM was accepted as allowed,** and its presentation was not audited: the SVG drawing in FloorMapCanvas, PathwayTree and TraceHistogramChart, ActivityEdit's configured HTML, and ProcessorInfoBuilder.
- **The workbench framework** (wbm- and wbp- classes) was audited for naming and dead code only, by design. Story plays were not read line by line for stale assertions. Workbench test sources were counted only as fan-in.
- **Classes bound by AppModule from other slices** were not audited by the dashboard-query slice. Who declares Pager or SourceProgressBar in Java was not traced.
- **Two files already being edited in the working tree** (Templates.java and button.css) were skipped.
- **Uncertain effort.** The attach framework step (wave 3) is the key unknown: a few child controllers do work in their constructors that assumes an injected view. Wave 5 sizes depend on it. The query/dashboard merge (wave 11) may turn up behaviour that differs between the twin controllers despite their similar size; read the controller diffs before committing to it.
- **Counts are raw.** About 16 findings are duplicates across slices (merged above), so the distinct defect count is about 190.
- **No build, checkstyle or story run was done.** Every fix above still needs the build and story checks in its wave.