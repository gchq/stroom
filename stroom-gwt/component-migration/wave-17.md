# Wave 17: Tidy up

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 28 October 2026, after wave 15, from the code as it
then stood (counts will move a little with wave 16). It comes after wave 16, once nothing is a
presenter.

The migration left the code working but still shaped like the presenter framework in places:
classes in `client.presenter` and `client.view` packages that are now controllers, components,
models and helpers; names that say "presenter", "view" or "widget"; and CSS classes named after views
that no longer exist. This wave changes nothing a user sees: it moves, renames and restyles to the
same look, so every step is checked by compiling, the JVM tests and a screenshot diff that must show
no change.

## Audited (28 October 2026)

Reviewers read the wave against this plan, "Done means" and the UI guide (the package moves; the
names, comments and javadoc; the CSS; the workbench and the docs). Nothing a user sees was wrong;
fixed, with the wave:

- **The main screen's block is `main-screen`**, not `main` (in `main-screen.css`): `main` is a
  likely class name for a page's main area, and a page shown in the Documentation tab's frame, or
  a later Stroom block, would have been styled by it.
- **Dead and commented-out CSS** the moves carried along went: the dashboard body's top bar and
  design buttons (their markup is commented out in `DashboardBodyController`), the explorer filter's
  rule inside a dashboard (a dashboard has no explorer), the rule editor's, old explorer filter's and
  main screen's commented-out rules in `components.css`, three empty rules in
  `authentication-error.css`, and a class `Main` added that no rule matched
  (`mainViewImpl-splitPanel`). `navigation.css` is imported straight after `components.css`, where
  all its rules now came from, and the query buttons' block comment is back in `query-buttons.css`.
- **Comments and javadoc:** a few renames read oddly ("a controller its controller owns",
  "unbinds", a history note that called a presenter a controller), two named classes that are gone
  (`FolderPresenter`, `DocumentAssetAddDialogPresenter`), and some `@param` blocks were misaligned or
  named the old parameter. The floor map's shared and server comments say controllers.
- **Docs:** the design doc, the quick filter design and the story guide named old packages and two
  workbench modules that don't exist; ISSUES.md's paths are the new ones. Both apps' GWT modules say
  which of the two `QuickFilter` modules is which.

Recorded in ISSUES.md: the workbench's `ScreenViewsModule`, which binds nothing now. One reviewer
point was wrong: `TestFloorMapEventRowParsing` tests `FloorMapEventQuery`, in its own package.

Checked by both apps' GWT compiles, checkstyle, the JVM tests and a full workbench run with the leak check: all 1,722 stories passed (three visualisation and dashboard stories flaky, passing when retried), and the screenshots that changed are the same set as the wave's own run (visualisations and the annotation screens' dates) but for three more visualisations, so nothing a user sees changed.

## Done (28 October 2026)

Every step, in 41 commits, each a move or a rename that compiled, passed checkstyle and was
committed on its own. No class is in a `presenter` or `view` package: the widget library's merged
into their parents (the Ace wrapper into `stroom.editor.client.ace`, the quick filter into
`stroom.widget.quickfilter.client`, with a GWT module of its own), the app's 176 classes went to their
area's `client.component` or `client`, area by area, and `Layer`, `LayerContainer`, `Page` and
`HandlerRegistrations` to `stroom.widget.component.client` (the `Presenter` GWT module went). Plan
B's 17 settings widgets are `...SettingsForm`s, `TraceOverviewWidget` is `TraceOverviewChart`, and
`AbstractTabPlugin`, `DocumentAssetUtils` and `SourceStories` lost "presenter"; so did 70 identifiers
and the comments and javadoc. About 75 classes named after views are BEM blocks, each block's rules
in its own stylesheet (16 new), the 25 nothing used are gone, and the eight stylesheets without
kebab-case names have them. Checked by both apps' GWT compiles, the JVM tests and a full workbench
run with the leak check: all 1,722 stories passed, and the screenshots matched wave 16's but for the
visualisations that differ from run to run, so nothing a user sees changed.

What was decided on the way, where it differs from the plan below:

- **Four types keep "View"** (`ButtonView`, `ToggleButtonView`, `SidebarView`, `DataGridView`): none
  is a presenter's view (recorded in ISSUES.md).
- **`FloorMapGrid` went to `client`**, not `client.component`: it builds the grid's SVG for the
  canvas, and isn't a widget.
- **Two packages moved whole** to `client.component` (the pipeline tree's and the asset tree's), as
  their helpers are parts of the widget.
- **Block names:** `sign-in-page` for the `LoginView*` classes (the sign-in and reset password
  pages share them), `result-table` for both results tables (`TableViewImpl`, `TablePresenter`),
  `main` for the main screen (`main-screen` since the audit); the rest after their components. `showValueFilters` stays (it is a
  saved settings' JSON property too), and `toolbarContainer` was renamed only as a class.
- **Dead styling:** the rule editor's `ruleViewImpl-name` class, which no rule matched, went, rather
  than making the `.ruleView-name` and `.ruleView-optional` rules meant for it apply (a look change).
  Those rules were already commented out; the audit deleted them.
- **The cascade:** each new stylesheet is imported straight after the last one its rules came from;
  the dark theme's rules stay in `theme.css`. A rule naming two blocks went to `navigation.css`
  (the audit found it dead, and deleted it).
- **Comments that record history** ("as it was in the presenter") kept their wording; commented-out
  code was renamed with its fields.
- **Tool slips caught:** the first CSS clean-up treated commented-out CSS as rules and cut into it (the
  stylesheets were restored and the step redone with comments masked); two JSNI bodies still named
  renamed parameters (fixed before the GWT compile); the quick filter's new package had no GWT module
  (the GWT compile caught it). So five commits don't GWT compile on their own: `3ed25416c5`,
  `3020edac7b`, `a58855963a` and `0c840861a6` (no quick filter module yet) and `c1e5363e6d` (the
  JSNI bodies); each compiles as Java and passes the JVM tests.

The user's decisions (28 October 2026): the Ace editor wrapper's classes go to
`stroom.editor.client.ace`; the quick filter's to `stroom.widget.quickfilter.client`; Plan B's
settings widgets are all `...SettingsForm`s and `TraceOverviewWidget` is `TraceOverviewChart`; and
every name that still says "presenter" is renamed, area by area.

## Rules for the wave

- **Packages say what is in them.** A component, widget or controller goes in its area's
  `client.component` package (as every wave since 7 has done); anything else (a model, a REST client,
  an event, a cell, a helper) goes in the area's `client` package. The widget library's packages
  (`stroom.widget.<x>.client.presenter` and `.view`) merge into `stroom.widget.<x>.client`.
- **A move is a move.** No edits beyond the package, imports and `urn:import` lines in the same
  commit, so a review can check it by the rename alone (`git log --follow` keeps each file's
  history). Tests move with their classes.
- **No clashes.** Merging each old package into its target was checked on 28 October 2026 and
  no simple name clashes; check again before each move.
- **Story titles never change.** A story class can be renamed; its registered titles and their
  screenshot baselines stay.
- **Protected code is left alone:** `stroom-core-client-widget/src/main/java/com/` and `edu/ycp`.

## 1. The widget library's packages (`stroom-core-client-widget`)

| From | Classes | To |
| --- | --- | --- |
| `stroom.widget.popup.client.presenter` and `.view` | 8 and 22 (with 6 templates): `PopupManager`, `PopupSupportImpl`, `Dialog`, `ResizableDialog`, `SimplePopup`, the button bars, `PopupPosition`, `PopupSize` and the rest | `stroom.widget.popup.client` |
| `stroom.widget.tab.client.presenter` and `.view` | 5 and 14 (6 templates): `TabData`, `TabDataImpl`, `TabBar`, `CurveTabBar`, `LinkTabBar`, `LayerContainerImpl`, `DraggableTabBar` and the rest | `stroom.widget.tab.client` |
| `stroom.widget.menu.client.presenter` | 23 (`Menu.ui.xml`): `Menu`, `MenuController`, `MenuManager`, the menu items, `ShowMenuEvent` | `stroom.widget.menu.client` |
| `stroom.editor.client.presenter` and `.view` | 6 and 9: `Option`, `Action`, `CurrentPreferences`, `DelegatingAceCompleter`; Ace's `Editor`, `RightBar`, `Marker`, `Annotation`, `IndicatorPopup` and the rest | `stroom.editor.client`; the Ace wrapper's nine (from `view`) to `stroom.editor.client.ace` |
| `stroom.widget.dropdowntree.client.view` | 7 (3 templates): `QuickFilter`, `QuickFilterPage`, `QuickFilterDialog`, its history and tooltip helpers | `stroom.widget.quickfilter.client` (they are the quick filter, not the drop-down tree) |
| `stroom.widget.xsdbrowser.client.view` | 13 (1 template) | `stroom.widget.xsdbrowser.client` |
| `stroom.widget.tickbox.client.view` | `TickBox` (1 template) | `stroom.widget.tickbox.client`; a string names it in full, so search for `"stroom.widget.tickbox.client.view.TickBox"` |
| `stroom.widget.help.client.presenter` | `HelpManager` | `stroom.widget.help.client` |
| `stroom.widget.presenter.client` (what wave 16 leaves) | `Layer`, `LayerContainer`, `Page`, `HandlerRegistrations` | `stroom.widget.component.client`; `Presenter.gwt.xml` goes, and `Component.gwt.xml` and `Util.gwt.xml` stop inheriting it |

## 2. The app's packages (`stroom-core-client`, statistics)

Components and widgets go to `client.component`, the rest to `client`:

| Area | To `client.component` | To `client` |
| --- | --- | --- |
| `data` | `ClassificationLabel` (+ template), `ClassificationLabelController` (from `view`) | 16 from `presenter`: `RestDataProvider`, `ShowDataEvent`, `DataTypeUiManager`, `ExpressionValidator`, `CopyTextCell`, `HasContextMenusCell`, `CriteriaUtil` and the rest; `FileData` |
| `query` | `TimeZoneBox`, `QueryButtons`, `TimeRangeSelector`, `TimeRangeSelectorController` (from `view`, with templates) | 29 from `presenter`: `QueryModel`, `ResultStoreModel`, `TableRow`, `ColumnHeader`, the selection models and cells, `TimeZones` and the rest |
| `pipeline.structure` | `PipelineTreePanel`, `DraggableTreePanel`, `PipelineElementBox`, `TreePanel` and their renderers (from `view`, 3 templates) | 8 from `presenter`: `PipelineModel`, the tree builders, `PipelineElementTypes`, `StructureValidationUtil` |
| `pipeline.stepping` | | `SteppingPipelineTreeBuilder`; `PipelinePath`, `PipelinePathCollapse`, `SeverityChanges` |
| `planb` | the 15 settings widgets and `SettingsGroup` (from `view`, 18 templates) | |
| `main` | `TaskSpinner`, `MainToolbar` (from `view`, 2 templates) | `MainScreens`, `GlobalKeyHandlerImpl`, `PageTitle`; `DynamicStyles` |
| `pathways` | `TraceOverviewWidget` (+ template), `TraceHistogramChart` | `CommonSpanBuilder`, `Bezier`, `ShowTracesEvent` |
| `floormap` | `FloorMapGrid`, `FloorMapIconPicker` (+ template) | `FloorMapQueryBuilder`, `FloorMapEventQuery`, `MapContextMenu` |
| `document.asset` | `DocumentAssetTree` (+ template), `DocumentAssetTreeItem`, `DocumentAssetImageResource` (from `presenter.assets`) | `DocumentAssetAddFileCallback`, `DocumentAssetState`, `DocumentAssetPresenterUtils` (renamed, unit 3) |
| `processor` | `DaysOfWeek` (+ template) | `ProcessorProfileClient`, `ProcessorProfileListModel` |
| `explorer` | | 12 from `presenter` (`TabSessionManager`, `RecentItems`, the cells and selection models, `DocumentTypeCache`), `ExplorerCell` |
| `security` | | 9 from `presenter`: `ExplorerClient`, `UserScreen`, the REST clients and builders |
| `entity` | | `MarkdownConverter`, `HasToolbar`, `TreeRowHandler`, `HasReadAndWrite`, `HasCommonTabs` |
| `core` | | `CoreScreens`, `Plugin`, `MonitoringPlugin` |
| `task`, `config.global`, `credentials`, `contentstore`, `visualisation`, `node`, `feed`, `importexport`, `search.elastic`, `statistics.impl.sql` | | their few models, clients and helpers |

`stroom.core.client.presenter.Plugin` is the base of every plugin; moving it touches every plugin's
import, so it can go in its own commit.

## 3. Names

- **"Presenter" in class names:** `AbstractTabPresenterPlugin` (a plugin base for tabs:
  `AbstractTabPlugin`), `DocumentAssetPresenterUtils` (`DocumentAssetUtils`), the story class
  `SourcePresenterStories` (`SourceStories`, titles kept).
- **"Presenter" in fields, parameters, variables, javadoc and comments:** about 140 distinct names
  in about 1,000 files once the package names have gone (`tablePresenter`, `presenterProvider`,
  `currentVisPresenter`, `queryEditPresenter`, "the presenter" in comments). Each becomes what it
  holds (`table`, `tableController`, `editor`). Done area by area, as plain renames.
- **Named for how it was made:** Plan B's 15 `...SettingsWidget`s become `...SettingsForm`s
  (`GeneralSettingsForm`, `RetentionSettingsForm`; three would clash with shared classes without a
  qualifier, so the whole family takes one), and `TraceOverviewWidget` becomes `TraceOverviewChart`
  (`TraceOverview` is shared data), beside `TraceHistogramChart`.
- **"View" in names of things that aren't views:** after wave 16 deletes `ControllerView` and
  `LinkTabsLayoutView`, `ButtonView`, `ToggleButtonView` (the button interfaces), `DataGridView` and
  `SidebarView` are left; each is checked and named for what it is.

## 4. CSS classes named after deleted views

About 35 class blocks are still named after views or presenters that are gone, in seven
stylesheets (`components.css`, `dashboard.css`, `standard.css`, `ask-stroom-ai.css`,
`query-buttons.css`, `item-selection.css`, `character-range-selection.css`) and `Login.css`. Each
becomes a BEM block in its component's own stylesheet, renamed in the CSS, templates, Java,
`SafeHtmlTemplates` and stories in one commit, with a screenshot diff showing no change:

- the shell: `mainViewImpl*`, `curveTabLayoutViewImpl-*`, `fullScreenViewImpl-*`, `welcomeViewImpl-*`,
  `explorerTreeViewImpl*`, `linkTabPanelViewImpl*` (with `toolbarContainer` and `docTab-readOnly*`,
  wave 14's leftover);
- Ask Stroom AI: `askStroomAiViewImpl*` (28 rules);
- the sign-in pages: `LoginView*` and `AuthErrorView*` (`Login.css`; `Login`, `ResetPassword`,
  `AuthenticationError`, and the `RoundedPanel` and `InfinityLogo` stories use them);
- the dashboard and query: `DashboardViewImpl*`, `TablePresenter*`, `TableViewImpl*`,
  `QueryResultTabsViewImpl*`, `QueryEditViewImpl*`, `queryDataView*`;
- data and pipelines: `dataViewImpl*`, `sourceViewImpl*`, `stepLocationViewImpl*`,
  `pipelineStructureViewImpl*`, `selectionHandlerViewImpl*`, `itemSelectionViewImpl*`,
  `characterRangeSelectionViewImpl*`;
- the rest: `chooserViewImpl*`, `dataRetentionViewImpl*`, `aboutViewImpl*`, `taskManangerViewImpl*`
  (misspelt), `importConfigViewImpl*`, `documentPermissionsViewImpl*`, `ruleView*`, `entityTreeView*`,
  the index, dictionary and field list settings (`solr...`, `elastic...`, `dictionarySettingsViewImpl`),
  and `contentstore-scroll-y` (wave 15's leftover).

Classes no template, Java file or story uses any more are deleted, after a search of every
template, Java file, story and stylesheet.

**Stylesheet names.** Eight are not kebab case: `CellList.css`, `ChangePassword.css`,
`CommandLinkCell.css`, `Login.css`, `SessionList.css`, `TermEditor.css`, `standard_mod.css`,
`material_design_colors.css`; each is renamed to its block's name (and `app.css`'s import with it).

## 5. Loose ends

- **Empty directories.** About 76 empty `client/presenter` and `client/view` directories (main,
  resources and test trees) left behind by moves; git doesn't track them, so they are only removed
  from disk.
- **Comments and docs** that still describe presenters, views and slots: `StroomWorkbench.gwt.xml`
  ("real presenters and views"), `WRITING-STORIES.md`, `COMPONENT_MODEL_DESIGN.md`, the wave plans'
  "now" sections where they would mislead. Records of what was done stay as written.
- **The workbench's names:** story helper and harness methods named for presenters.

## Checks

Per step: compile, checkstyle, the client and workbench JVM tests; the stories of the areas moved.
After units 2 and 4: a full workbench run with the leak check and a screenshot diff that must show
no change (any difference is a mistake in the move, not a baseline to update). Both apps' GWT
compiles at the end.
