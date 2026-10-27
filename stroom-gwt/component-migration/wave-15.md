# Wave 15: Content-tab screens and the shell

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 24 October 2026 from a review of every unit in the wave against the code after master (with `data_grid2` and `copy_and_paste`) was merged in. Line numbers are as of then. Each unit is done when it meets "Done means, for every unit".

> **Since wave 5 (25 October 2026):** `PagerView`/`PagerViewImpl` is `PagerPanel` and `PagerViewWithHeading` is `HeadedPagerPanel` (both components in `stroom.widget.datagrid.client`; the plans below keep the old names and line numbers); `MessagePanelImpl` is `MessagePanel` (block `message-panel`); `SvgCell` is `IconCell`; the ref cells are in `stroom.data.client.component`, configured by one `RefCellsController`. `DataGrid` takes no bus (`DataGrid()`, `DataGrid(int pageSize)`): its context menu, links and Stroom AI questions go up the page as requests (`MenuRequest`, `OpenHyperlinkRequest`, `AskStroomAiTableRequest`) to `CorePresenter`'s relays. Not there yet, for the plans below that assume it: `PagerPanel` can't take children in a template (no `@UiChild` or `HasWidgets`: the grid goes in with `setDataWidget`, buttons with `getToolbar()`, and the toolbar's name is set in Java), and it is still the list's `TaskMonitorFactory` (about 90 uses); the grid's sort, range and selection outputs are still GWT's handlers. See `wave-05.md`.

> **Since wave 6 (25 October 2026):** the code editor is `CodeEditor` (`stroom.editor.client.component`; declare it, e.g. `<ed:CodeEditor ui:field="editor"/>`, and attach its controller, `make(codeEditorControllerProvider).attach(view.getEditor())`), with `CodeEditorOptions` and `CodeEditorController` (`EditorPresenter` is a thin wrapper over them; `Editor` is only the Ace wrapper inside it); the single line editor is `SingleLineCodeEditor` with `SingleLineCodeEditorController.attach(...)`; the explorer tree is one `ExplorerTree` (no-arg; `ExplorerTickBoxTree` is `setTickBoxes(true)`; `ExtendedExplorerTree` is gone, its overrides `setOnChoose` and `setOnInitialSelect`), filled by `ExplorerTreeController.attach(tree)` (was `ExplorerTreeModel`); `DraggableTreePanel` is concrete, with its callbacks (`setIsValidTarget`, `setOnSelect`, `setOnDragStart`, `setOnDragEnd`, `setOnDragCancel`), but takes no children in a template: its two panels go in with `setTreePanels(tree, subTree)`; `DocumentAssetTree` has a no-arg constructor and `setAccessibleName`; the pickers are a `DropDownBox` with a controller attached (`DocumentAssetSelectionController` in `stroom.document.asset.client.component`, `UserRefSelectionController` in `stroom.security.client.component`; `DropDownView` is gone); the annotation choosers are one-use popups, `ChooserController` and `MultiChooserController`, `show(opener, element, caption, onChoose)`, each opening tracked by an `OpenChooser` so that a second click closes it; the menu is `MenuController`. The plans below keep the old names. See `wave-06.md`.

> **Since wave 7 (26 October 2026):** Plan B's settings widgets are components with `setOnChange`, and its type settings editors `PlanBTypeSettingsEditor`s (`stroom.planb.client.component`); the secret forms are `SecretForm`s (`stroom.credentials.client.component`; the key store's is a `KeyStoreSecretFormController`, which uploads); the floor map's Set Scale, Timeline Settings, group edit, layer appearance (wave 11's `FloorMapLayerStylePresenter` is done too), dock, Layers panel and canvas are components in `stroom.floormap.client.component`, with `FloorMapSetScaleController`, `FloorMapTimelineSettingsController`, `FloorMapGroupEditController`, `FloorMapLayerStyleController`, `FloorMapDockController` (`addTab(String, IsWidget)`), `FloorMapCanvasController`, and `FloorMapLayers` with no controller; the pipeline's tree is `PipelineTree` and the stepping screen's bar `PipelinePathBar` (attach a `PipelinePathBarController` for its menus), both on `AbstractPipelineTree`; the pathway tree is `PathwayTree` with `PathwayTreeController`; the expression tree is `ExpressionTree` with `ExpressionTreeController` (`init(dataSource, fields)`, `setOnChange`, `setOnSearch`), each `TermEditor` with a `TermEditorController`, and both `EditExpressionPresenter`s are one `ExpressionEditor` with `ExpressionEditorController` (`stroom.query.client.component`; `setFramed(false)` where the owner frames it); `ControllerDialog.show(controller)` shows a controller made once, again and again, through one host; and a `DataGrid` given one of GWT's selection models selects on a press only with a `SingleSelectionEventManager`. The plans below keep the old names. See `wave-07.md`.

> **Since wave 8 (26 October 2026):** the explorer chooser is `ExplorerPopup` with `ExplorerPopupController` (`show(consumer)` to reuse one, `showOnce(opener, ...)` for one use; `DocSelectionPopup` is gone, its `setIncludeNullSelection` public), `DocSelectionController` is in `stroom.explorer.client.component` (`DocSelectionBoxPresenter` stays as the wrapper for callers not yet converted), and the document folder tree is `EntityTree` with `EntityTreeController` (the export tick tree an `ExplorerTree` with `EntityCheckTreeController`); a `DocTabPresenter`'s tab can show a controller through `ControllerTabProvider` (`new ControllerTabProvider<>(getEventBus(), provider::get, Foo::read, Foo::write)`; `isPresenter(content)` asks whether a shown presenter is the tab's without making it); the Documentation tab is `MarkdownEditController` (`stroom.entity.client.component`, with `MarkdownPreviewController`), and `MarkdownTabProvider` takes the event bus and a `Supplier` (`new MarkdownTabProvider<>(getEventBus(), markdownEditProvider::get) { onRead…; onWrite… }`, an abstract class whose `onRead`/`onWrite` are given the controller; in a `DocumentEditor` tab call `followReadOnly()`); the settings tabs (data generator, visualisation, XML schema, text converter, SQL temporal store, feed, pathways, Elastic index, index, analytic rule, report, floor map event store; the error feed and its Set Default are `ErrorFeedController`), the preferences' Theme, Editor and Date and Time tabs, the Ask Stroom AI config tabs, `DenseVectorField`, `CredentialSettings`, `TableBuilderProcessing`, `QueryToolbar`, `DataTextPane` (the data viewer's `TextPresenter`; its editor options are `getOptions()`), `QueryTextPane` (the query result's `TextPresenter`), `ColumnFilterEditor` (`edit(opener, column, onChange)`), `AnalyticEmailDestination`, `CommentEdit` (`edit(opener, text, onOk)`), `WordsTab`, `XSDBrowser`, `QueryHelp` (`linkToEditor` kept), `ContentStoreContentPackDetails` (`setOnInstalled`) and `FloorMapTimeline` are components with a `...Controller` beside each; `AriaPanel` has `ariaValueMin`, `ariaValueMax` and `ariaValueNow`; and the size modifiers `text-field--narrow`/`--wide` and `value-spinner--narrow`/`--wide` replace widths in templates. The plans below keep the old names. See `wave-08.md`.

> **Since wave 9 (26 October 2026):** the small dialogs are components with a controller made for one use, shown with `ControllerDialog.showOnce(opener, this)` from a method that takes the result's callback; take a GIN `Provider` of the controller and call it with `this` as the opener (`AppRoot` from a plugin or helper). Among them: a name is a `NameEntry` (`stroom.entity.client.component`; `RenameDocumentController.rename`, `CreateNewGroupController`, `NewNodeGroupController`, `NewFsVolumeGroupController`, `NewIndexVolumeGroupController`), an alert or confirmation a `CommonAlert` (`CommonAlertController`; `AlertPlugin` keeps the queue, and a prompt is the browser's), the filter dialogs one `ExpressionDialog` (`stroom.data.client.component`; `show(opener, caption, size, expression, dataSource, fields, (expression, e) -> ...)`), the column format `ColumnFormat`, the Edit Property dialog `NewProperty`, the Create Processors dialog `ProcessChoiceDialog`, the index fields' `IndexFieldEditor` and `SolrIndexFieldEditor`, the retention period `RetentionPeriod`, Download Options `DownloadSearchResults` (one kept by each table), and the About, Splash, Dependency Information, query info, document Info, Current Selection, Run Now, keyboard shortcut recorder, custom row style, XPath filter, profile period, feed dependency, pipeline reference, new element, favourite, event link, asset and Git Push dialogs; the step controls are `StepControl` with `StepControlController`. Every component template declares Stroom's `TextBox` or `TextArea` (`stroom.widget.form.client`), never GWT's `TextBox` or `TextArea`. The plans below keep the old names. See `wave-09.md`.

> **Since wave 10 (27 October 2026):** the lists are components with a controller each (`stroom.<area>.client.component`). A list's template declares its `PagerPanel` (or `HeadedPagerPanel`) with a `toolbarName`, holding its `DataGrid` (`tableName`, `multiLine`, `pageSize`) and its toolbar's `ToolbarButton`s (`svg`, `title`, `enabled="false"`); a quick-filtered list's root is a `QuickFilterPage` (`accessibleName`). The component adds its columns and gives its pager as `getTaskMonitor()`, with `getSelectionModel()`, `getSortList()` and `getToolbar()`; its buttons, info and row menus go out through callbacks (`setOnAdd`, `setOnShowInfo`, `setOnShowActionMenu`), and its controller passes in the ref cells configured (`refCells.configure(new DocRefCell.Builder<>())`, `FeedRefCell`, `UserRefCell`). Only a list inside a document watches `Contexts.READ_ONLY`, which is true where nothing provides it. A controller shown as a tab is a `ControllerLayer`, which passes the focus on. Among the new names: `MetaList` with `MetaListController`, `SteppingMetaListController` and `MetaRelationListController`; `JobNodeColumns` and `JobNodeActionsController` (were `JobNodeListHelper`); one `NotificationList` for analytic and report notifications; `ScriptDependencyList` (`ScriptListPresenter` merged in); `IncludeExcludeFilterEditor` and `IncludeExcludeFilterDictionary`; `DependencyList` (was `DependenciesPresenter`); `TabSessionChooser`; `CredentialsList`; `FindDocResultList`; `EffectiveWordsList`; `TypeFilter`. The plans below keep the old names. See `wave-10.md`.

Convert the tool screens onto a content-tab component base: Users, Accounts, API keys, Credentials, Nodes, Jobs, Cache, Volumes, Meta, Retention, Content Store and so on. Then convert the shell: ContentTabPane, CurveTabLayout, Navigation, Main, Core, FullScreen and TabSessionManager. Replace explorer bus events with callbacks and context where possible, and finally delete ContentTabPresenter, WrapperView's GIN binding and the popup bridges.

## CSS and template notes for the wave

- New utility class border-none (border-style: none !important) in the LAYOUT section of stroom-app/src/main/resources/ui/css/stroom.css, beside .overflow-hidden (:859): replaces setBorderStyle(BorderStyle.NONE) on a list's root in ContentTemplateTabPresenter.java:147 (this wave) and DataRetentionPolicyPresenter.java:131, RuleSetSettings.java:68, RulesPresenter.java:106, SelectionHandlersPresenter.java:129. !important because the inline style it replaces beat the list's own border rule.
- default-min-sizes (components.css:1085) is added in Java to the Expression dialog by all six of its callers (BatchDocumentPermissionsPresenter.java:151, ExecutionScheduleManager.java:613, ProcessorPresenter.java:253, MetaPresenter.java:266, SteppingPresenter.java:362, UserPermissionReportPresenter.java:164): put it on the root of the wave-9 Expression.ui.xml once, and drop the calls.
- ARIA roles and labels on panels (banner role=alert, region, landmarks, rowheader) can't be set on a GWT widget from UiBinder: use raw HTML elements inside a g:HTMLPanel, as SpinnerLarge.ui.xml:5 does with role="status" (Main banner, ContentStoreTab details region, Navigation and CurveTabLayout landmarks, WelcomeTab th scope=row).
- Existing classes reused by the split screens: max (stroom.css:793), dashboard-panel (dashboard.css:167), overflow-hidden (stroom.css:859), dock-container-vertical / dock-min / dock-max (stroom.css:810-833), form-padding; Nodes, Jobs, Cache, ContentStoreTab, ProcessorTasksTab and UserAccess all keep their SplitPane-of-two-lists layout as is.
- The filter-above-list layout (max dock-container-vertical, dock-min form-padding QuickFilter, dock-max list) repeats in ServerTasks, GlobalPropertyTab and DependenciesTab (and commented out in ApiKeys and Accounts): a candidate for the shared FilteredList component from the shared work, rather than three copies.
- Buttons added to a declared list's toolbar (NodeGroups, ProcessorProfiles, FsVolumeGroups, IndexVolumeGroups, AnnotationTags, ApiKeys, SigningKey, UserAccess, Users, GlobalPropertyTab, BatchDocumentPermissions, ContentTemplateTab) follow the design's rule: the component is given that toolbar and adds them in Java, one action enum with setOnAction/setAvailableActions. Navigation's buttons are on its own template's toolbar, so they go in the template.
- Screens over the pass-through WrapperView (NodeGroups, ProcessorProfiles, FsVolumeGroups, IndexVolumeGroups, AnnotationTags) get a one-element template whose root is the declared list, so the DOM and screenshots don't change.
- Dynamic values as custom properties: --curve-tab__selected-color already exists (tab-bar.css:100) for Main's selected tab colour; new --iframe-content__left/__top/__width/__height for IFrameContent's overlay frame, used by a new .iframe-content__frame beside .iframe-frame (stroom.css:897).
- New state classes: mainViewImpl--banner (top: 20px, components.css by .mainViewImpl-banner :820) and #loading.loading--hidden (opacity 0, visibility hidden, stroom.css by #loading :168).
- Keep the ViewImpl-named classes (curveTabLayoutViewImpl-*, mainViewImpl-*, welcomeViewImpl-*, dataRetentionViewImpl-*, askStroomAiViewImpl-*, fullScreenViewImpl-*) this wave: MainViewImpl.java:70, AppShellStories.java:101 and CurveTabBarStories.java:320-322 select on them, and renaming to BEM is a separate, screenshot-checked change.

## `ContentTabPresenter`

framework, presenter; complexity 2, effort M; used by 33.

- **Component:** `none (it has no view; each screen supplies its own component)`
- **Controller:** `stroom.content.client.component.ContentTabController (abstract Controller implementing DocumentTabData: isCloseable() true, getDocRef() from getType(), as ContentTabPresenter.java:34-42 does)`
- **Template:** `none` → `none (built in Java because it is a base class with no view of its own)`
- **Goes:** stroom-core-client/src/main/java/stroom/content/client/presenter/ContentTabPresenter.java, after its 34 subclasses have moved (last step of the wave); `No GIN binding`
- **Names:** ContentTab is not created: the base has no view, so only the controller base is needed. ContentTabController is free. This is the content-tab screen base the order asks for; OpenContentTabEvent's presenter payload must be re-keyed on it (or on HasControllerNode and IsWidget) first.

**Stays in Java:** Tab icon, label and type: each screen's controller's TabData.

## `CorePresenter`

shell, presenter; complexity 2, effort M; used by 7.

- **Component:** `stroom.core.client.component.Core`
- **Controller:** `stroom.core.client.component.CoreController`
- **Template:** `none (CoreViewImpl builds a SimplePanel in Java)` → `stroom-core-client/src/main/java/stroom/core/client/component/Core.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/core/client/presenter/CorePresenter.java (with nested CoreView :127)`; `stroom-core-client/src/main/java/stroom/core/client/view/CoreViewImpl.java`; `stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:118 bindPresenter(CorePresenter, CoreView, CoreViewImpl)`; `stroom-dashboard-gwt/src/main/java/stroom/dashboard/client/gin/DashboardAppModule.java:114 (same)`; `CoreScreens.java stays (AppModule.java:116, DashboardAppModule.java:113), re-pointed at Provider<CoreController>`
- **Names:** Core and CoreController are free.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/core/client/view/CoreViewImpl.java:55-56 | `loading.getStyle().setOpacity(1); setVisibility(VISIBLE)` | static | Remove the state class loading--hidden from the host page's #loading; the rule #loading.loading--hidden { opacity: 0; visibility: hidden; } goes in stroom-app/src/main/resources/ui/css/stroom.css after #loading (:168). |
| CoreViewImpl.java:65,67 | `loading.getStyle().setOpacity(0); setVisibility(HIDDEN)` | static | Add the state class loading--hidden (same rule). |

| Into the template | What | Detail |
| --- | --- | --- |
| CoreViewImpl.java:37-38 | structure | The SimplePanel with class app-panel (stroom.css:159) becomes the template: <g:SimplePanel ui:field="content" styleName="app-panel"/>. |

**Stays in Java:** #loading, #loadingText and #logo are host-page elements found with RootPanel.get (CoreViewImpl.java:39-40, CorePresenter.java:119); Loading message text (CoreViewImpl.java:59); Task counting and start-up routing (CorePresenter.java:57-109).

## `WelcomePresenter`

screen, presenter; complexity 2, effort S; used by 6.

- **Component:** `stroom.welcome.client.component.WelcomeTab`
- **Controller:** `stroom.welcome.client.component.WelcomeTabController`
- **Template:** `stroom-core-client/src/main/resources/stroom/welcome/client/view/WelcomeViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/welcome/client/component/WelcomeTab.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/welcome/client/presenter/WelcomePresenter.java (with nested WelcomeView :96)`; `stroom-core-client/src/main/java/stroom/welcome/client/view/WelcomeViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/welcome/client/gin/WelcomeModule.java:30 bindPresenterWidget(WelcomePresenter, WelcomeView, WelcomeViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/shell/ShellAppScreenModule.java:114 (WelcomeView binding)`
- **Names:** Welcome clashes with stroom-core/src/main/java/stroom/core/welcome/Welcome.java (server class). Chosen WelcomeTab, like the other content tabs.

| Into the template | What | Detail |
| --- | --- | --- |
| WelcomeViewImpl.java:58-60 | aria | role="rowheader" set on each row's first cell: replace the g:Grid (WelcomeViewImpl.ui.xml:5) with a table in a g:HTMLPanel whose label cells are <th scope="row"> (rowheader by default), keeping classes welcomeViewImpl-buildInfo stroom-control-grid w-100 and the Label fields; change the rule at components.css:979 from td:first-child to th. |

**Stays in Java:** Session and build values (WelcomePresenter.java:55-63): from the server; Welcome HTML from config (:72; WelcomeViewImpl.java:70).

## `IFrameContentPresenter`

screen, presenter; complexity 2, effort S; used by 4.

- **Component:** `stroom.iframe.client.component.IFrameContent`
- **Controller:** `stroom.iframe.client.component.IFrameContentController`
- **Template:** `none (IFrameContentViewImpl builds a SimplePanel in Java)` → `stroom-core-client/src/main/java/stroom/iframe/client/component/IFrameContent.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/iframe/client/presenter/IFrameContentPresenter.java (with nested IFrameContentView :90)`; `stroom-core-client/src/main/java/stroom/iframe/client/view/IFrameContentViewImpl.java`; stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:137 bindPresenterWidget(IFrameContentPresenter, IFrameContentView, IFrameContentViewImpl); `stroom-dashboard-gwt/src/main/java/stroom/dashboard/client/gin/DashboardAppModule.java:145-148 (same)`; `stroom-gwt/stroom-gwt-workbench/.../app/gin/query/QueryExtrasScreenModule.java:40 (IFrameContentView binding)`; `IFrameLoadUiHandlers.java stays: IFramePresenter and IFrameViewImpl still use it`
- **Names:** Free.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/iframe/client/view/IFrameContentViewImpl.java:50-56 | `frame style: position ABSOLUTE, opacity 1, zIndex 2, width/height 100%, borderWidth 0` | static | New class iframe-content__frame in stroom-app/src/main/resources/ui/css/stroom.css beside .iframe-frame (:897): position: absolute; z-index: 2; border-style: none; (opacity 1 is the default and goes). |
| IFrameContentViewImpl.java:77-79 | `widget style width 100%, height 100%, borderWidth 0` | static | Existing class iframe-panel (stroom-app/src/main/resources/ui/css/stroom.css:903: width and height 100%; a div has no border), on the template root. |
| IFrameContentViewImpl.java:130-134 | `frame style left/top/width/height from the panel's position, PX` | dynamic | Custom properties --iframe-content__left, --iframe-content__top, --iframe-content__width, --iframe-content__height set on the frame, used by .iframe-content__frame (left: var(--iframe-content__left); top: ...; width: var(--iframe-content__width, 100%); height: var(--iframe-content__height, 100%)). |

| Into the template | What | Detail |
| --- | --- | --- |
| IFrameContentViewImpl.java:60-79 | structure | The anonymous SimplePanel becomes the template root <g:SimplePanel ui:field="panel" styleName="iframe-panel"/>; its onAttach/onDetach hooks move to the component's onLoad/onUnload. |

**Stays in Java:** The Frame itself (IFrameContentViewImpl.java:49,57-58): added to RootPanel as an overlay outside the component's DOM, so it is built in Java; Frame visibility on attach/detach and the title polling (:64-96); Default title "Loading..." (:39): the tab label, not shown in the component.

## `NavigationPresenter`

shell, presenter; complexity 5, effort XL; used by 3.

**GWT button.** It still uses GWT's own `Button` (`com.google.gwt.user.client.ui.Button`), which nothing may use (agreed 24 October 2026): the explorer's logo (`NavigationViewImpl.ui.xml`, `<g:Button ui:field="logo">`) and the activity button (`NavigationPresenter.java`). Replace it in this wave, together with the stories that copy them (`LogoStories`, `ActivityChooserStories`), and check the look against the screenshots.

- **Component:** `stroom.explorer.client.component.Navigation`
- **Controller:** `stroom.explorer.client.component.NavigationController`
- **Template:** `stroom-core-client/src/main/resources/stroom/explorer/client/view/NavigationViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/explorer/client/component/Navigation.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/explorer/client/presenter/NavigationPresenter.java (with nested NavigationView :461)`; `stroom-core-client/src/main/java/stroom/explorer/client/presenter/NavigationUiHandlers.java`; `stroom-core-client/src/main/java/stroom/explorer/client/view/NavigationViewImpl.java and its .ui.xml`; stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:126-129 bindPresenter(NavigationPresenter, NavigationView, NavigationViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/shell/ShellAppScreenModule.java:80 (NavigationView binding)`
- **Names:** Free.

| Into the template | What | Detail |
| --- | --- | --- |
| NavigationViewImpl.java:72 | aria | setAccessibleName("Filter explorer") overrides the template's accessibleName="Filter documents" (NavigationViewImpl.ui.xml:16): put "Filter explorer" in the template. |
| NavigationViewImpl.java:80-81 | aria | Logo tabindex -2 and aria-hidden="true": tabIndex="-2" on the g:Button (:9) and aria-hidden on a raw wrapper element in a g:HTMLPanel; or make the logo a non-focusable element (it is mouse-only and hidden from screen readers) so the attributes sit on raw HTML. |
| NavigationViewImpl.java:50,69 | structure | layout is @UiField(provided = true) as a plain new FlowPanel(): make it an ordinary ui:field. |
| stroom-core-client/src/main/java/stroom/main/client/view/MainViewImpl.java:104 | aria | The explorer's navigation landmark (role navigation, aria-label "Explorer") belongs on Navigation.ui.xml's root as a raw <nav aria-label="Explorer"> (or div role="navigation") in a g:HTMLPanel, not set on it by Main. |
| NavigationPresenter.java:152-164 | component-declaration | The explorer toolbar's own buttons, built in Java on the template's toolbar (NavigationViewImpl.ui.xml:19), are declared there: ToolbarButton add svg=ADD title="New Document" opensMenu=true enabled=false; delete DELETE "Delete Selected Documents" enabled=false; ToolbarToggleButton showAlerts EXCLAMATION "Show Alerts"; locate LOCATE "Locate Current Item" enabled=false; expandAll EXPAND_ALL "Expand All Folders"; collapseAll COLLAPSE_ALL "Collapse All Folders"; ToolbarToggleButton filter FILTER "Filter Document Types"; find FIND "Find in Content"; one setOnAction(Consumer<NavigationAction>). |
| NavigationPresenter.java:111-112,180-184 | css-class | Activity SimplePanel and Button built in Java with classes activityOuter and activityButton dashboard-panel (stroom.css:750,755): declare them in the activityPanel (NavigationViewImpl.ui.xml:23) with visible="false". |
| NavigationPresenter.java:280 | text | Fixed heading "Current Activity" goes in the template's activity button markup; the properties below it stay. |
| NavigationPresenter.java:168-172,419 | component-declaration | ExplorerTree built in Java with new ExplorerTree(restFactory, ...) is declared (wave 6 explorer tree component) inside the MaxScrollPanel (NavigationViewImpl.ui.xml:22); its controller attaches. |
| NavigationViewImpl.ui.xml:10-11,16,19-20 | component-declaration | Declares checked: Toolbar, ToolbarButton, QuickFilter and SpinnerSmall already declared; TypeFilter is a popup shown from the filter button (NavigationPresenter.java:397), so the controller makes it rather than declaring it. |

**Stays in Java:** Button enabled/pressed state (NavigationPresenter.java:233,269-270,396,402) and Show Alerts visibility from config (:186); Activity shown only when enabled in config (:178), and its property lines from data (:282-296); build them as SafeHtml, not setHTML of a raw string (:298); Logo SVG content (NavigationViewImpl.java:74-76) unless a template icon widget exists; Filter error, popup text and context handler.

## `TabSessionManager`

plugin-or-infrastructure, smart class; complexity 3, effort M; used by 2.

- **Component:** `none (a Plugin with no view)`
- **Controller:** `stroom.explorer.client.presenter.TabSessionManager (keeps its name: it is a Plugin, not a presenter; may move to stroom.explorer.client beside NavigationPlugin)`
- **Template:** `none` → `none (built in Java because it has no view; it only opens dialogs)`
- **Goes:** `Nothing: stroom-app-gwt/src/main/java/stroom/app/client/gin/PluginsModule.java:33 bindPlugin(TabSessionManager.class) stays`
- **Names:** TabSessionManager is its own entry in all_simple_names.json. The suggested TabSessionManager + TabSessionManagerController split does not apply: there is no component, so no controller pair.

| Into the template | What | Detail |
| --- | --- | --- |
| TabSessionManager.java:67,76 | component-declaration | TabSessionChooserPresenter (wave 10 TabSessionChooser) is injected once and never owned: make it per use with appRoot.makeDialog(tabSessionChooserControllerProvider), as TextBoxPopup already is (:97). Dialogs are not declared in templates. |

**Stays in Java:** Dialog captions "Select Tab Session To Open:", "Select Tab Session To Delete:", "Save New Tab Session" (:88,:94,:98) passed to the dialogs; Alert and confirm messages (:101,:122,:133,:224).

## `CurveTabLayoutPresenter`

shell, presenter; complexity 3, effort M; used by 1.

- **Component:** `stroom.widget.tab.client.component.CurveTabLayout`
- **Controller:** `none (the abstract CurveTabLayoutPresenter merges: its tab-to-layer map and add/remove/select/move go into the CurveTabLayout component as display state; its bus forwarding, RequestCloseTabEvent at CurveTabLayoutPresenter.java:42-46 and ShowMenuEvent at :48-49, and the abstract fireSelectedTabChange at :127 go to stroom.content.client.component.ContentTabPaneController, unit 288, through setOnSelect/setOnRequestClose/setOnShowMenu/setOnShowTabMenu/setOnToggleSidebar callbacks)`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/tab/client/view/CurveTabLayoutViewImpl.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/component/CurveTabLayout.ui.xml`
- **Goes:** `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/presenter/CurveTabLayoutPresenter.java (merged, see controller)`; `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/presenter/CurveTabLayoutView.java`; `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/view/CurveTabLayoutViewImpl.java and its .ui.xml`; stroom-core-client-widget/src/main/java/stroom/widget/tab/client/view/CurveTabLayoutUiHandlers.java (maximise() becomes setOnToggleSidebar(Runnable)); stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:170 bindSharedView(CurveTabLayoutView.class, CurveTabLayoutViewImpl.class); `stroom-gwt/stroom-gwt-workbench/.../app/gin/ScreenViewsModule.java:92 bindSharedView(CurveTabLayoutView...)`
- **Names:** No clash for CurveTabLayout. No CurveTabLayoutController: the abstract presenter has a single subclass (ContentTabPanePresenter), so it merges into ContentTabPaneController rather than being renamed. Presenter units go to <area>.client.component, so stroom.widget.tab.client.component (not .presenter as the record suggested). Workbench CurveTabBarStories.java:320-322 and AppShellStories.java:101 and MainViewImpl.java:70 use the curveTabLayoutViewImpl-* classes by name; keep the class names this wave.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/widget/tab/client/view/CurveTabLayoutViewImpl.java:88 | `tabBarOuter.getElement().getStyle().setRight(indent, Unit.PX)` | static | Only caller passes 32 (ContentTabPanePresenter.java:96). Add right: 32px to the existing .curveTabLayoutViewImpl-tabBarOuter rule (stroom-app/src/main/resources/ui/css/components.css:188) and drop setRightIndent from the API. |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/main/client/view/MainViewImpl.java:111 (setLandmark(centerWidget, "main", null)) | aria | The content area's main landmark belongs in CurveTabLayout.ui.xml (raw <main> or <div role="main"> inside a g:HTMLPanel wrapping the layout, as SpinnerLarge.ui.xml carries role="status"), not set on it by Main. |
| CurveTabLayoutViewImpl.java:45-46,60-65 | structure | layout is @UiField(provided = true) only to override focus(); make it a plain ui:field and have CurveTabLayout implement Focus (focus the Hide Sidebar button). |
| CurveTabLayoutViewImpl.ui.xml:8-9,13,18 | component-declaration | Declares checked: Toolbar, ToolbarButton (menu), CurveTabBar, LayerContainerImpl are already in the template; nothing to add. |

**Stays in Java:** Sidebar button svg and title toggle Show/Hide Sidebar (CurveTabLayoutViewImpl.java:104-112): state; tabBar.setTabPanel(layerContainer.getElement()) wiring (:67); Tabs and layers added, removed, selected and moved: data.

## `ContentTabPanePresenter`

shell, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.widget.tab.client.component.CurveTabLayout (unit 42); no component of its own`
- **Controller:** `stroom.content.client.component.ContentTabPaneController (with the abstract CurveTabLayoutPresenter merged in)`
- **Template:** `none (used the shared CurveTabLayoutView)` → `none (its view is CurveTabLayout.ui.xml, unit 42)`
- **Goes:** `stroom-core-client/src/main/java/stroom/content/client/presenter/ContentTabPanePresenter.java`; `stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:130 bindPresenter(ContentTabPanePresenter.class)`; stroom-gwt/stroom-gwt-workbench/.../app/gin/content/ContentAppScreenModule.java:44 bind(ContentTabPanePresenter.class); ContentScreenGinjector.java:115 and ShellScreenGinjector.java:175 getters re-pointed
- **Names:** ContentTabPane is not created (it shows CurveTabLayout); ContentTabPaneController is free.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/content/client/presenter/ContentTabPanePresenter.java:96 | `getView().setRightIndent(32)` | static | right: 32px in .curveTabLayoutViewImpl-tabBarOuter (stroom-app/src/main/resources/ui/css/components.css:188), see unit 42; the call goes. |

**Stays in Java:** History handling, key bindings and app-wide event listeners (:81-140); maximise via MaximiseEvent (:144-146) set as CurveTabLayout's setOnToggleSidebar; Tab task monitor context (:159-165, :250-262).

## `DatabaseTablesMonitoringPresenter`

screen, presenter; complexity 2, effort S; used by 1.

**Since the merge.** Renames only (DataGrid, PagerView package). Nothing material.

- **Component:** `stroom.monitoring.client.component.DatabaseTablesMonitoring`
- **Controller:** `stroom.monitoring.client.component.DatabaseTablesMonitoringController`
- **Template:** `none (the shared PagerView with a DataGrid built in Java)` → `stroom-core-client/src/main/java/stroom/monitoring/client/component/DatabaseTablesMonitoring.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/monitoring/client/presenter/DatabaseTablesMonitoringPresenter.java`; `No GIN binding of its own (PagerView's shared binding stays for its other users)`
- **Names:** Free. DatabaseTables would be shorter (label "Database Tables"), also free; optional.

| Into the template | What | Detail |
| --- | --- | --- |
| DatabaseTablesMonitoringPresenter.java:62,65-66 | component-declaration | <dg:PagerPanel ui:field="pager"><dg:DataGrid ui:field="dataGrid" tableName="Database Tables" pageSize="1000"/></dg:PagerPanel> (stroom.widget.datagrid.client, wave 5). Needs DataGrid's no-argument constructor from wave 5: today it takes a HasHandlers (DataGrid.java:192,200). |

**Stays in Java:** Column definitions with their value getters (:68-106); Key provider (:64), sort handling and RestDataProvider (:111-136).

## `FullScreenPresenter`

shell, presenter; complexity 2, effort S; used by 6.

- **Component:** `stroom.core.client.component.FullScreen`
- **Controller:** `stroom.core.client.component.FullScreenController`
- **Template:** `stroom-core-client/src/main/resources/stroom/core/client/view/FullScreenViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/core/client/component/FullScreen.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/core/client/presenter/FullScreenPresenter.java (with nested FullScreenView :67)`; `stroom-core-client/src/main/java/stroom/core/client/view/FullScreenViewImpl.java and its .ui.xml`; stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:120-123 bindPresenter(FullScreenPresenter, FullScreenView, FullScreenViewImpl); `stroom-dashboard-gwt/src/main/java/stroom/dashboard/client/gin/DashboardAppModule.java:116-119 (same)`
- **Names:** Free.

| Into the template | What | Detail |
| --- | --- | --- |
| FullScreenViewImpl.ui.xml:8 | component-declaration | DocTabPresenter is not declared: what it shows is whichever document the event carries (FullScreenPresenter.java:47-48), so the component keeps a setContent(IsWidget) slot. |

**Stays in Java:** sinkEvents(KEYEVENTS) (FullScreenViewImpl.java:40); Window title and closing prompt from the document (FullScreenPresenter.java:50-62).

## `ProcessorTaskPresenter`

tab, presenter; complexity 1, effort S; used by 5.

- **Component:** `stroom.data.client.component.ProcessorTasksTab`
- **Controller:** `stroom.data.client.component.ProcessorTasksTabController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/StreamTaskViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/client/component/ProcessorTasksTab.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/ProcessorTaskPresenter.java (with nested StreamTaskView :79)`; `stroom-core-client/src/main/java/stroom/data/client/view/StreamTaskViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/data/client/gin/StreamStoreModule.java:117-120 bindPresenterWidget(ProcessorTaskPresenter, StreamTaskView, StreamTaskViewImpl); stroom-gwt/stroom-gwt-workbench/.../app/gin/{processing,query,content}/StreamStoreScreenModule.java:87 and editors/StreamStoreScreenModule.java:88 (StreamTaskView binding)
- **Names:** ProcessorTask clashes three ways: stroom.processor.shared.ProcessorTask, the jOOQ table stroom.processor.impl.db.jooq.tables.ProcessorTask, and wave 12 unit 224's planned stroom.processor.task.client.component.ProcessorTask (a different presenter of the same simple name, the Processor Tasks screen). Chosen ProcessorTasksTab: this one is the Processor Tasks tab of Feed, Pipeline and Folder (HasDocumentRead, used by FeedPresenter, PipelinePresenter and FolderTabs). The two are near copies (their templates differ only in the first split size, 400px here and 200px in ProcessorTaskViewImpl.ui.xml); they could share one component and template if the split size is a template attribute. Unit 224's own name should also change, since it clashes with the shared DTO.

| Into the template | What | Detail |
| --- | --- | --- |
| StreamTaskViewImpl.ui.xml:7; ProcessorTaskPresenter.java:47 | component-declaration | <d:ProcessorTaskSummary ui:field="summary" addStyleNames="max dashboard-panel overflow-hidden"/> (wave 10) |
| StreamTaskViewImpl.ui.xml:8; ProcessorTaskPresenter.java:48 | component-declaration | <d:ProcessorTaskList ui:field="list" addStyleNames="max dashboard-panel overflow-hidden"/> (wave 10) |
| StreamTaskViewImpl.ui.xml:5 | component-declaration | SplitPane already declared |

**Stays in Java:** Expression built from the selected summary row (ProcessorTaskPresenter.java:50-70); read(docRef, document) (:74-77).

## `CachePresenter`

screen, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.cache.client.component.Cache`
- **Controller:** `stroom.cache.client.component.CacheController`
- **Template:** `stroom-core-client/src/main/resources/stroom/cache/client/view/CacheViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/cache/client/component/Cache.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/cache/client/presenter/CachePresenter.java (with nested CacheView :82)`; `stroom-core-client/src/main/java/stroom/cache/client/view/CacheViewImpl.java and its .ui.xml`; `stroom-core-client/src/main/java/stroom/cache/client/gin/CacheModule.java:30 bindPresenterWidget(CachePresenter, CacheView, CacheViewImpl)`; `stroom-gwt/stroom-gwt-workbench/.../app/gin/content/CacheScreenModule.java:34 (CacheView binding)`
- **Names:** Cache is free in the repo. Caches (its label and TAB_TYPE) would read better beside Nodes and Jobs, which are forced plural by clashes; either is fine.

| Into the template | What | Detail |
| --- | --- | --- |
| CacheViewImpl.ui.xml:6; CachePresenter.java:45 | component-declaration | <c:CacheList ui:field="cacheList" addStyleNames="max"/> (wave 10) |
| CacheViewImpl.ui.xml:7; CachePresenter.java:46 | component-declaration | <c:CacheNodeList ui:field="cacheNodeList" addStyleNames="max"/> (wave 10) |
| CacheViewImpl.ui.xml:4 | component-declaration | SplitPane already declared |

**Stays in Java:** Selected cache pushed to the node list (CachePresenter.java:48-56).

## `SigningKeyPresenter`

screen, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.security.client.component.SigningKey`
- **Controller:** `stroom.security.client.component.SigningKeyController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/SigningKeyViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/SigningKey.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/client/presenter/SigningKeyPresenter.java (with nested SigningKeyView :213)`; `stroom-core-client/src/main/java/stroom/security/client/view/SigningKeyViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:191-194 bindPresenterWidget(SigningKeyPresenter, SigningKeyView, SigningKeyViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/SecurityScreenModule.java:137 (SigningKeyView binding)`
- **Names:** SigningKey is free (no shared DTO of that simple name; the row type is SigningKeyRow). SigningKeys would match its label "Signing Keys" and ApiKeys; optional.

| Into the template | What | Detail |
| --- | --- | --- |
| SigningKeyViewImpl.ui.xml:7; SigningKeyPresenter.java:70 | component-declaration | Template root <s:SigningKeyList ui:field="list" addStyleNames="max"/> (wave 10), keeping the explanatory comment (:4-6). |
| SigningKeyPresenter.java:72-79 | text | Toolbar name "Signing key actions" and Revoke / Revoke All buttons on the list's toolbar: stay in the component's Java (toolbar-of-another-part rule), as one action enum. |

**Stays in Java:** Revoke enabled from selection status (:89-92); Confirm messages (:147-182) built from the row's status and expiry.

## `UserAccessPresenter`

screen, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.security.client.component.UserAccess`
- **Controller:** `stroom.security.client.component.UserAccessController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/UserAccessViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/UserAccess.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/client/presenter/UserAccessPresenter.java (with nested UserAccessView :236)`; `stroom-core-client/src/main/java/stroom/security/client/view/UserAccessViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:187-190 bindPresenterWidget(UserAccessPresenter, UserAccessView, UserAccessViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/SecurityScreenModule.java:134 (UserAccessView binding)`
- **Names:** Free.

| Into the template | What | Detail |
| --- | --- | --- |
| UserAccessViewImpl.ui.xml:13; UserAccessPresenter.java:78 | component-declaration | <s:UserAccessList ui:field="list" addStyleNames="max"/> (wave 10) |
| UserAccessViewImpl.ui.xml:14; UserAccessPresenter.java:79 | component-declaration | <s:UserSessionsList ui:field="sessions" addStyleNames="max"/> (wave 10) |
| UserAccessViewImpl.ui.xml:11 | component-declaration | SplitPane already declared |
| UserAccessPresenter.java:86-97 | text | Toolbar name and Revoke / Open User buttons on the list's toolbar: stay in the component's Java (toolbar-of-another-part rule), as one action enum. |

**Stays in Java:** Button enabled states (:128-132); Confirm message built from the row and the identity provider (:172-199); Sessions list's user from the selection (:112-119).

## `TaskManagerPresenter`

screen, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.task.client.component.ServerTasks`
- **Controller:** `stroom.task.client.component.ServerTasksController`
- **Template:** `stroom-core-client/src/main/resources/stroom/task/client/view/TaskManagerViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/task/client/component/ServerTasks.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/task/client/presenter/TaskManagerPresenter.java (with nested TaskManagerView :160)`; `stroom-core-client/src/main/java/stroom/task/client/presenter/TaskManagerUiHandlers.java`; `stroom-core-client/src/main/java/stroom/task/client/view/TaskManagerViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/monitoring/client/gin/MonitoringModule.java:134-137 bindPresenterWidget(TaskManagerPresenter, TaskManagerView, TaskManagerViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/MonitoringScreenModule.java:77 (TaskManagerView binding)`
- **Names:** TaskManager clashes with stroom-task/stroom-task-api/.../stroom/task/api/TaskManager.java. Chosen ServerTasks, its label and TAB_TYPE (TaskManagerPresenter.java:46,87).

| Into the template | What | Detail |
| --- | --- | --- |
| TaskManagerViewImpl.java:51 | aria | setAccessibleName("Filter server tasks") overrides the template's accessibleName="Filter tasks" (TaskManagerViewImpl.ui.xml:6): put "Filter server tasks" in the template and drop the Java. |
| TaskManagerViewImpl.ui.xml:8; TaskManagerPresenter.java:59 | component-declaration | <t:TaskManagerList ui:field="list" addStyleNames="dock-max"/> (wave 10) replaces the SimplePanel slot. |
| TaskManagerViewImpl.ui.xml:6 | component-declaration | QuickFilter already declared |

**Stays in Java:** Quick filter tooltip (TaskManagerPresenter.java:68-80): built with the help URL from config; Filter text built from node/task/user (:111-141) and setText (TaskManagerViewImpl.java:91); Filter error and context handler.

## `DependenciesTabPresenter`

screen, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.importexport.client.component.DependenciesTab`
- **Controller:** `stroom.importexport.client.component.DependenciesTabController`
- **Template:** `stroom-core-client/src/main/resources/stroom/importexport/client/view/DependenciesTabViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/importexport/client/component/DependenciesTab.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/importexport/client/presenter/DependenciesTabPresenter.java (with nested DependenciesTabView :120)`; `stroom-core-client/src/main/java/stroom/importexport/client/presenter/DependenciesUiHandlers.java`; `stroom-core-client/src/main/java/stroom/importexport/client/view/DependenciesTabViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/importexport/client/gin/ImportExportConfigModule.java:67-70 bindPresenterWidget(DependenciesTabPresenter, DependenciesTabView, DependenciesTabViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/content/ImportExportScreenModule.java:57 (DependenciesTabView binding)`
- **Names:** Free; the wave-10 list it shows is Dependencies (DependenciesPresenter), so the Tab suffix keeps them apart.

| Into the template | What | Detail |
| --- | --- | --- |
| DependenciesTabViewImpl.java:51 | aria | setAccessibleName("Filter dependencies") repeats the template's accessibleName (DependenciesTabViewImpl.ui.xml:7): delete the Java. |
| DependenciesTabViewImpl.ui.xml:9; DependenciesTabPresenter.java:55 | component-declaration | <ie:Dependencies ui:field="list" addStyleNames="dock-max"/> (wave 10) |
| DependenciesTabViewImpl.ui.xml:7 | component-declaration | QuickFilter already declared |

**Stays in Java:** Quick filter tooltip built with the help URL (DependenciesTabPresenter.java:62-74); Filter text, filter error and context handler (DependenciesTabViewImpl.java:75-88).

## `AccountsPresenter`

screen, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.security.identity.client.component.Accounts`
- **Controller:** `stroom.security.identity.client.component.AccountsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/identity/client/view/AccountsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/identity/client/component/Accounts.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/identity/client/presenter/AccountsPresenter.java (with nested AccountsView :80)`; `stroom-core-client/src/main/java/stroom/security/identity/client/presenter/AccountsUiHandlers.java (unused)`; `stroom-core-client/src/main/java/stroom/security/identity/client/view/AccountsViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/security/identity/client/gin/ChangePasswordModule.java:73-75 bindPresenterWidget(AccountsPresenter, AccountsView, AccountsViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/security/IdentityScreenModule.java:46 (AccountsView binding)`
- **Names:** Free.

| Into the template | What | Detail |
| --- | --- | --- |
| AccountsViewImpl.ui.xml:5-7 | structure | Delete the commented-out QuickFilter: the list filters itself; QuickFilter is not declared. |
| AccountsViewImpl.ui.xml:9; AccountsPresenter.java:45 | component-declaration | <i:AccountsList ui:field="list" addStyleNames="dock-max"/> (wave 10) |

**Stays in Java:** Filter input pushed to the list (AccountsPresenter.java:72-74).

## `MainPresenter`

shell, presenter; complexity 4, effort L; used by 3.

- **Component:** `stroom.main.client.component.Main`
- **Controller:** `stroom.main.client.component.MainController`
- **Template:** `stroom-core-client/src/main/resources/stroom/main/client/view/MainViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/main/client/component/Main.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/main/client/presenter/MainPresenter.java (with nested MainView :266)`; stroom-core-client/src/main/java/stroom/main/client/presenter/MainUiHandlers.java (once MainToolbar, wave 3, has its callbacks; MainToolbar.java still takes it); `stroom-core-client/src/main/java/stroom/main/client/view/MainViewImpl.java and its .ui.xml`; `stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:125 bindPresenter(MainPresenter, MainView, MainViewImpl)`; stroom-gwt/stroom-gwt-workbench/.../app/gin/shell/ShellAppScreenModule.java:78 bind(MainView.class).to(MainViewImpl.class); ShellScreenGinjector.java:169 getMainView() re-pointed; `MainScreens (AppModule.java:117) stays, re-pointed at the controller`; `DynamicStyles.java stays: ConditionalFormattingDynamicStyles.java:42,45 still uses it`
- **Names:** Main and MainController are free. Declares: ValueSpinner is not used here (the Spinner is inside MainToolbar); MainToolbar keeps its name in stroom.main.client.view (wave 3).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/main/client/view/MainViewImpl.java:252 | `innerContent.setSize("100%", "100%")` | static | Existing class max (stroom-app/src/main/resources/ui/css/stroom.css:793) added to the inner content. |
| MainViewImpl.java:359 | `root.getElement().setPropertyString("style", pageBorder)` | dynamic | pageBorder is a documented config value holding any CSS declarations (ThemeConfig.java:35-38, e.g. 'border: solid #BA2727; border-width: 0px 10px'), so one custom property can't carry it without changing the config contract: put it in a rule in the dynamic stylesheet (DynamicStyles.put on a .mainViewImpl--themed selector the root carries) instead of the element's style. If the config can change to a border value, use --main__page-border on the root with .mainViewImpl { border: var(--main__page-border, none) }. |
| MainViewImpl.java:366-367 | `DynamicStyles.put(".tab-bar--curved .tab-bar__tab[aria-selected=\"true\"]",…` | dynamic | Set the existing custom property --curve-tab__selected-color on the root element; tab-bar.css:99-100 already uses it for border-bottom-color. Main stops using DynamicStyles for this. |
| MainViewImpl.java:451,455 | `main.getElement().getStyle().setTop(0 or 20, Unit.PX)` | static | State class mainViewImpl--banner (top: 20px) on main, toggled with the banner, in stroom-app/src/main/resources/ui/css/components.css beside .mainViewImpl-banner (:820). |

| Into the template | What | Detail |
| --- | --- | --- |
| MainViewImpl.java:88 | structure | banner.setVisible(false): visible="false" in the template (MainViewImpl.ui.xml:9). |
| MainViewImpl.java:91 | aria | role="alert" on the banner: the banner becomes a raw <div ui:field="banner" class="mainViewImpl-banner" role="alert"> inside a g:HTMLPanel (DivElement field). |
| MainViewImpl.java:104,111,437-444 | aria | setLandmark on the explorer (navigation, "Explorer") and content (main) moves into those components' own templates (Navigation.ui.xml, unit 393; CurveTabLayout.ui.xml, unit 42); setLandmark goes. |
| MainViewImpl.java:230-235 | component-declaration | The navigation SplitPane built in Java on every rebuild is declared once: <sp:SplitPane ui:field="navigationSplit" thin="true" names="Explorer, Content" addStyleNames="split-pane--navigation mainViewImpl-splitPanel"/>; only its sizes are set at runtime. |
| MainViewImpl.java:250-251 | structure | contentWrapper ResizeFlowPanel with class mainViewImpl-contentWrapper declared once in the template. |
| MainViewImpl.java:260-264 | component-declaration | The dock SplitPane declared once: <sp:SplitPane ui:field="dockSplit" thin="true" addStyleNames="mainViewImpl-outerSplitPanel"/>. |
| MainViewImpl.ui.xml:13 | component-declaration | MainToolbar already declared. |

**Stays in Java:** maximised state class (MainViewImpl.java:217,222); Split sizes, dock orientation and pane order and names, which depend on the dock location (:234,:257-276); Re-parenting of explorer, content and docked widget as the layout changes (:190-283); Banner text (:453,:457); Focus moving between areas (:372-425); DockResizeEvent fired from the view (:345) becomes a callback the controller turns into the event.

## `BrowseAnnotationPresenter`

screen, presenter; complexity 2, effort S; used by 1.

- **Component:** `stroom.annotation.client.component.BrowseAnnotation`
- **Controller:** `stroom.annotation.client.component.BrowseAnnotationController`
- **Template:** `none of its own: it uses the shared FindView (stroom-core-client/src/main/resources/stroom/explorer/client/view/FindViewImpl.ui.xml)` → `stroom-core-client/src/main/java/stroom/annotation/client/component/BrowseAnnotation.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/annotation/client/BrowseAnnotationPresenter.java`; No GIN binding of its own; AnnotationModule.java:88 bindSharedView(AbstractFindPresenter.FindView, FindViewImpl) stays until FindAnnotationPresenter and the other find screens move
- **Names:** Free.

| Into the template | What | Detail |
| --- | --- | --- |
| BrowseAnnotationPresenter.java:48-49 | component-declaration | Template root is the wave-11 find component (AbstractFind's dumb part) with dialogMode="false" (from setDialogMode(false)), holding <a:FindAnnotationList ui:field="list"/> (wave 10) in its results slot. |

**Stays in Java:** Filter text and Enter/Down keys (:71-83); Opening an annotation (:55-59).

## `DataPreviewTabPresenter`

screen, presenter; complexity 1, effort S; used by 4.

- **Component:** `stroom.data.client.component.DataPreviewTab`
- **Controller:** `stroom.data.client.component.DataPreviewTabController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/DataPreviewTabViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/client/component/DataPreviewTab.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/DataPreviewTabPresenter.java (with nested DataPreviewTabView :76)`; `stroom-core-client/src/main/java/stroom/data/client/view/DataPreviewTabViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/data/client/gin/StreamStoreModule.java:105-108 bindPresenterWidget(DataPreviewTabPresenter, DataPreviewTabView, DataPreviewTabViewImpl); `stroom-dashboard-gwt/src/main/java/stroom/dashboard/client/gin/DashboardAppModule.java:149-152 (same)`; stroom-gwt/stroom-gwt-workbench/.../app/gin/{processing,query,content}/StreamStoreScreenModule.java:78 and editors/StreamStoreScreenModule.java:79 (DataPreviewTabView binding)
- **Names:** Free.

| Into the template | What | Detail |
| --- | --- | --- |
| DataPreviewTabViewImpl.ui.xml:4; DataPreviewTabPresenter.java:43 | component-declaration | Template root <d:Data ui:field="data" addStyleNames="max"/> (wave 13); DataController attaches. |
| DataPreviewTabPresenter.java:42 | structure | setDisplayMode(DisplayMode.STROOM_TAB) is fixed: displayMode="STROOM_TAB" on the declared Data if it exposes the setter; else the controller sets it. |

**Stays in Java:** Tab label "Stream N" from the source location (:52-56); fetchData and init view type (:58-65).

## `AppPermissionsPresenter`

screen, presenter; complexity 2, effort S; used by 11.

- **Component:** `stroom.security.client.component.AppPermissions`
- **Controller:** `stroom.security.client.component.AppPermissionsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/AppPermissionsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/AppPermissions.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/client/presenter/AppPermissionsPresenter.java (with nested AppPermissionsView :124)`; stroom-core-client/src/main/java/stroom/security/client/view/AppPermissionsViewImpl.java and its .ui.xml (its unused constants :36-37 and dead setUserRef :84-86 go with it); stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:140-143 bindPresenterWidget(AppPermissionsPresenter, AppPermissionsView, AppPermissionsViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/SecurityScreenModule.java:100 (AppPermissionsView binding)`
- **Names:** AppPermissions and AppPermissionsController are free.

| Into the template | What | Detail |
| --- | --- | --- |
| AppPermissionsViewImpl.ui.xml:37; AppPermissionsPresenter.java:56 | component-declaration | <s:AppUserPermissionsList ui:field="appUserPermissionsList" addStyleNames="max"/> (wave 10) |
| AppPermissionsViewImpl.ui.xml:42; AppPermissionsPresenter.java:57 | component-declaration | <s:AppPermissionsEdit ui:field="appPermissionsEdit" addStyleNames="max"/> (wave 10) |
| AppPermissionsViewImpl.ui.xml:9,12-29 | component-declaration | SplitPane, FormGroup and SelectionBox already declared |

**Stays in Java:** SelectionBox items PermissionShowLevel.ITEMS and default SHOW_EXPLICIT (AppPermissionsPresenter.java:60-61): items are set through the API; Show level, selection and change wiring (:62-83).

## `GlobalPropertyTabPresenter`

screen, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.config.global.client.component.GlobalPropertyTab`
- **Controller:** `stroom.config.global.client.component.GlobalPropertyTabController`
- **Template:** `stroom-core-client/src/main/resources/stroom/config/global/client/view/GlobalPropertyTabViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/config/global/client/component/GlobalPropertyTab.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/config/global/client/presenter/GlobalPropertyTabPresenter.java (with nested GlobalPropertyTabView :167); `stroom-core-client/src/main/java/stroom/config/global/client/presenter/ManageGlobalPropertyUiHandlers.java`; `stroom-core-client/src/main/java/stroom/config/global/client/view/GlobalPropertyTabViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/monitoring/client/gin/MonitoringModule.java:118-121 bindPresenterWidget(GlobalPropertyTabPresenter, GlobalPropertyTabView, GlobalPropertyTabViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/MonitoringScreenModule.java:68 (GlobalPropertyTabView binding)`
- **Names:** Free. Aside: its TAB_TYPE is "Caches" (GlobalPropertyTabPresenter.java:45), the same as CachePresenter's (CachePresenter.java:29), which looks like a copy-paste bug in the tab type key.

| Into the template | What | Detail |
| --- | --- | --- |
| GlobalPropertyTabViewImpl.java:52 | aria | setAccessibleName("Filter properties") repeats the template's accessibleName (GlobalPropertyTabViewImpl.ui.xml:7): delete the Java. |
| GlobalPropertyTabViewImpl.ui.xml:9; GlobalPropertyTabPresenter.java:65 | component-declaration | <cg:ManageGlobalPropertyList ui:field="list" addStyleNames="dock-max"/> (wave 10) |
| GlobalPropertyTabPresenter.java:71-76 | text | Toolbar name "Property actions" and Edit / Show Warnings buttons on the list's toolbar: stay in the component's Java (toolbar-of-another-part rule). |

**Stays in Java:** Show Warnings visibility from the errors (:109-112) and Edit enabled (:121-124); Quick filter tooltip built with the help URL (:78-86); ManageGlobalPropertyEdit is a dialog (makeDialog :134).

## `UsersPresenter`

screen, presenter; complexity 2, effort S; used by 4.

- **Component:** `stroom.security.client.component.Users`
- **Controller:** `stroom.security.client.component.UsersController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/UsersViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/Users.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/client/presenter/UsersPresenter.java (with nested UsersView :159)`; `stroom-core-client/src/main/java/stroom/security/client/view/UsersViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:119-122 bindPresenterWidget(UsersPresenter, UsersView, UsersViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/SecurityScreenModule.java:85 (UsersView binding)`
- **Names:** Free.

| Into the template | What | Detail |
| --- | --- | --- |
| UsersViewImpl.ui.xml:6; UsersPresenter.java:69 | component-declaration | <s:UserList ui:field="userList" addStyleNames="max"/> (wave 10) inside the userViewImpl max FlowPanel (:5). |
| UsersPresenter.java:71 | text | label "Users:" becomes label="Users:" on the declared UserList (if UserList exposes setLabel to UiBinder). |
| UsersPresenter.java:72-75 | text | Help text "Lists all Stroom users. Users can be added, deleted, disabled and enabled." becomes a HelpHTML child of the declared UserList, as FormGroup takes one (AppPermissionsViewImpl.ui.xml:15). |
| UsersPresenter.java:76-79 | structure | Fixed list configuration (USERS_ONLY mode, unique id and enabled columns) can become template attributes on UserList if it exposes them; else stays in the component. |
| UsersPresenter.java:81-86 | text | Toolbar name "User actions" and Create User / Delete buttons on the list's toolbar: stay in the component's Java (toolbar-of-another-part rule). |

**Stays in Java:** Delete button title "Delete User '...'" and enabled state (:118-130): built from the selection; CreateUser (injected and owned, :63) and CreateNewGroup (makeDialog :102) are dialogs, not declared.

## `NodeGroupPresenter`

screen, presenter; complexity 2, effort S; used by 1.

- **Component:** `stroom.node.client.component.NodeGroups`
- **Controller:** `stroom.node.client.component.NodeGroupsController`
- **Template:** `none (uses the shared pass-through WrapperView, stroom.widget.datagrid.client.WrapperViewImpl, which adds no element)` → `stroom-core-client/src/main/java/stroom/node/client/component/NodeGroups.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/node/client/presenter/NodeGroupPresenter.java`; No GIN binding of its own (made just in time); MonitoringModule.java:106 bindSharedView(WrapperView...) stays until WrapperView's last user moves
- **Names:** NodeGroup clashes with stroom.node.shared.NodeGroup and the jOOQ table stroom.node.impl.db.jooq.tables.NodeGroup. Chosen NodeGroups (label "Node Groups", TAB_TYPE "NodeGroups").

| Into the template | What | Detail |
| --- | --- | --- |
| NodeGroupPresenter.java:72 (view.setView(nodeGroupListPresenter.getView())) | component-declaration | Template root is <n:NodeGroupList ui:field="list"/> (wave 10), so the DOM is unchanged from WrapperView's pass-through. |
| NodeGroupPresenter.java:63-70 | text | Toolbar name "Node group actions" and the New Node Group / Edit Selected Node Group / Delete Selected Node Group buttons go on the list's toolbar: per the design they stay in the component's Java (given the list's toolbar), as one NodeGroupAction enum with setOnAction/setAvailableActions; into the template only if NodeGroupList takes toolbar buttons as template children. |

**Stays in Java:** Edit/Delete enabled from selection (:67,:70,:122-126); Dialog caption "Edit Node Group - " + name (:100) and confirm messages (:106-109): built from data; NewNodeGroup and NodeGroupEdit are dialogs (makeDialog :84, :99), not declared.

## `AskStroomAiPresenter`

screen, presenter; complexity 5, effort XL; used by 8.

**Since the merge.** Copying a message now uses Clipboard.writeText with success/failure callbacks instead of ClipboardUtil.copy. Nothing material.

- **Component:** `stroom.ai.client.component.AskStroomAi`
- **Controller:** `stroom.ai.client.component.AskStroomAiController`
- **Template:** `stroom-core-client/src/main/resources/stroom/ai/client/AskStroomAiViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAi.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/ai/client/AskStroomAiPresenter.java (with nested AskStroomAiView :1088)`; `stroom-core-client/src/main/java/stroom/ai/client/AskStroomAiUiHandlers.java`; `stroom-core-client/src/main/java/stroom/ai/client/AskStroomAiViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/ai/client/gin/AskStroomAIModule.java:46-49 bindPresenterWidget(AskStroomAiPresenter, AskStroomAiView, AskStroomAiViewImpl); stroom-gwt/stroom-gwt-workbench/.../app/gin/editors/AskStroomAIScreenModule.java:50 (AskStroomAiView binding); AskStroomAiDialogStories.java uses the UiHandlers; `AskStroomAiScreens.java stays (the area's eager class, AskStroomAIModule.java:45), re-pointed at Provider<AskStroomAiController>`
- **Names:** Free.

| Into the template | What | Detail |
| --- | --- | --- |
| AskStroomAiViewImpl.java:83,85 | aria | placeholder "How can I help?" and aria-label "Message" on the TextArea (AskStroomAiViewImpl.ui.xml:27): GWT's TextArea is in the protected com/ directory and has no such setters, so they move to the template only through a Stroom text area widget with setPlaceholder/setAriaLabel (placeholder="How can I help?" ariaLabel="Message"); until then they stay. |
| AskStroomAiViewImpl.java:216-217 | structure | The context chip's markup built as an HTML string becomes template structure: <g:HTMLPanel ui:field="contextIndicator" styleName="askStroomAiViewImpl-contextChip" visible="false"><span ui:field="contextIcon" class="svgIcon"/> <span ui:field="contextText"/></g:HTMLPanel> replacing the Label at :24; the icon and text stay set at runtime, the text with setInnerText (today the description is concatenated into innerHTML). |
| AskStroomAiViewImpl.ui.xml:30; AskStroomAiPresenter.java:144 | component-declaration | <dd:DropDownBox ui:field="modelRef" addStyleNames="dock-max"/> (wave 8) replaces the SimplePanel slot; the controller attaches a DocSelectionController. |
| AskStroomAiViewImpl.ui.xml:11-16,31-34 | component-declaration | Toolbar, ToolbarButton and SpinnerSmall already declared. AiAttachmentData, AiChatHistory, AskStroomAiConfig and DownloadChat are dialogs (makeDialog AskStroomAiPresenter.java:304,927,935,959), not declared. |

**Stays in Java:** Send button title Send/Cancel, PLAY/STOP svg and play/stop classes (AskStroomAiViewImpl.java:109-124): state; empty state class (:205-210); Chat title from the chat (:197-198); Download and Delete All enabled (:231-238), message text; HTML sanitising scratch element (AskStroomAiPresenter.java:567).

## `JobPresenter`

screen, presenter; complexity 2, effort S; used by 5.

- **Component:** `stroom.job.client.component.Jobs`
- **Controller:** `stroom.job.client.component.JobsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/job/client/view/JobViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/job/client/component/Jobs.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/job/client/presenter/JobPresenter.java (with nested JobView :102)`; `stroom-core-client/src/main/java/stroom/job/client/view/JobViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/monitoring/client/gin/MonitoringModule.java:108-111 bindPresenterWidget(JobPresenter, JobView, JobViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/MonitoringScreenModule.java:63 (JobView binding)`
- **Names:** Job clashes with stroom.job.shared.Job and the jOOQ table stroom.job.impl.db.jooq.tables.Job. Chosen Jobs (label and TAB_TYPE "Jobs").

| Into the template | What | Detail |
| --- | --- | --- |
| JobViewImpl.ui.xml:8; JobPresenter.java:50 | component-declaration | <j:JobList ui:field="jobList" addStyleNames="max dashboard-panel"/> (wave 10) |
| JobViewImpl.ui.xml:10; JobPresenter.java:51 | component-declaration | <j:JobNodeList ui:field="jobNodeList" addStyleNames="max dashboard-panel"/> (wave 10) inside the existing max dashboard-panel form FlowPanel (:9) |
| JobViewImpl.ui.xml:6 | component-declaration | SplitPane already declared |

**Stays in Java:** Selected job pushed to the node list (JobPresenter.java:53-60, :83-91).

## `IndexVolumeGroupPresenter`

screen, presenter; complexity 3, effort M; used by 2.

- **Component:** `stroom.index.client.component.IndexVolumeGroups`
- **Controller:** `stroom.index.client.component.IndexVolumeGroupsController`
- **Template:** `none (uses the shared pass-through WrapperView)` → `stroom-core-client/src/main/java/stroom/index/client/component/IndexVolumeGroups.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/index/client/presenter/IndexVolumeGroupPresenter.java`; `stroom-core-client/src/main/java/stroom/index/client/gin/IndexModule.java:48 bind(IndexVolumeGroupPresenter.class)`; stroom-gwt/stroom-gwt-workbench/.../app/gin/editors/IndexScreenModule.java:59 and content/IndexVolumeScreenModule.java:35 bind(IndexVolumeGroupPresenter.class); ContentScreenGinjector.java:104 getter re-pointed
- **Names:** IndexVolumeGroup clashes with stroom.index.shared.IndexVolumeGroup and the jOOQ table stroom.index.impl.db.jooq.tables.IndexVolumeGroup. Chosen IndexVolumeGroups.

| Into the template | What | Detail |
| --- | --- | --- |
| IndexVolumeGroupPresenter.java:77 | component-declaration | Template root <ix:IndexVolumeGroupList ui:field="list"/> (wave 10). |
| IndexVolumeGroupPresenter.java:67-75 | text | Toolbar name "Volume group actions" and New/Edit/Delete buttons on the list's toolbar: stay in the component's Java (toolbar-of-another-part rule), as one action enum. |

**Stays in Java:** Edit/Delete enabled from selection (:161-165); Dialog caption and confirm messages from data (:133,:140-143); NewIndexVolumeGroup and IndexVolumeGroupEdit are dialogs (makeDialog :111,:131).

## `ProcessorProfilePresenter`

screen, presenter; complexity 3, effort M; used by 1.

- **Component:** `stroom.processor.client.component.ProcessorProfiles`
- **Controller:** `stroom.processor.client.component.ProcessorProfilesController`
- **Template:** `none (uses the shared pass-through WrapperView)` → `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorProfiles.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/processor/client/presenter/ProcessorProfilePresenter.java`; `No GIN binding of its own`
- **Names:** ProcessorProfile clashes with stroom.processor.shared.ProcessorProfile and the jOOQ table stroom.processor.impl.db.jooq.tables.ProcessorProfile. Chosen ProcessorProfiles (label "Processor Profiles"). Aside: the plural delete message (ProcessorProfilePresenter.java:110) repeats the singular one (:108).

| Into the template | What | Detail |
| --- | --- | --- |
| ProcessorProfilePresenter.java:77 | component-declaration | Template root <p:ProcessorProfileList ui:field="list"/> (wave 10). |
| ProcessorProfilePresenter.java:61-75 | text | Toolbar name "Profile actions" and New/Edit/Delete buttons on the list's toolbar: stay in the component's Java (toolbar-of-another-part rule), as one action enum. |

**Stays in Java:** Edit/Delete enabled from selection (:124-128); New profile default name "New Profile" (:88) and dialog captions (:90,:102); ProcessorProfileEdit is a dialog (makeDialog :89,:101).

## `FsVolumeGroupPresenter`

screen, presenter; complexity 2, effort M; used by 1.

- **Component:** `stroom.data.store.impl.fs.client.component.FsVolumeGroups`
- **Controller:** `stroom.data.store.impl.fs.client.component.FsVolumeGroupsController`
- **Template:** `none (uses the shared pass-through WrapperView)` → `stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/component/FsVolumeGroups.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/presenter/FsVolumeGroupPresenter.java`; `No GIN binding of its own`
- **Names:** FsVolumeGroup clashes with stroom.data.store.impl.fs.shared.FsVolumeGroup and the jOOQ table stroom.data.store.impl.fs.db.jooq.tables.FsVolumeGroup. Chosen FsVolumeGroups.

| Into the template | What | Detail |
| --- | --- | --- |
| FsVolumeGroupPresenter.java:79 | component-declaration | Template root <fs:FsVolumeGroupList ui:field="list"/> (wave 10). |
| FsVolumeGroupPresenter.java:69-77 | text | Toolbar name "Volume group actions" and New/Edit/Delete buttons on the list's toolbar: stay in the component's Java (toolbar-of-another-part rule), as one action enum. |

**Stays in Java:** Edit/Delete enabled from selection (:144-148); Dialog caption and confirm messages from data (:112,:122-125); NewFsVolumeGroup and FsVolumeGroupEdit are dialogs (makeDialog :90,:111).

## `NodePresenter`

screen, presenter; complexity 2, effort S; used by 4.

- **Component:** `stroom.node.client.component.Nodes`
- **Controller:** `stroom.node.client.component.NodesController`
- **Template:** `stroom-core-client/src/main/resources/stroom/node/client/view/NodeViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/node/client/component/Nodes.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/node/client/presenter/NodePresenter.java (with nested NodeView :104)`; `stroom-core-client/src/main/java/stroom/node/client/view/NodeViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/monitoring/client/gin/MonitoringModule.java:93-96 bindPresenterWidget(NodePresenter, NodeView, NodeViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/MonitoringScreenModule.java:57 (NodeView binding)`
- **Names:** Node clashes with stroom.node.shared.Node, the jOOQ table stroom.node.impl.db.jooq.tables.Node and stroom.pipeline.xml.converter.ds3.Node. Chosen Nodes (its label and TAB_TYPE are "Nodes", NodePresenter.java:36,80).

| Into the template | What | Detail |
| --- | --- | --- |
| NodeViewImpl.ui.xml:9; NodePresenter.java:52 | component-declaration | <n:NodeStatusList ui:field="nodeList" addStyleNames="max dashboard-panel"/> (wave 10) |
| NodeViewImpl.ui.xml:11; NodePresenter.java:53 | component-declaration | <n:NodeJobList ui:field="nodeJobList" addStyleNames="max"/> (wave 10), inside the existing max dashboard-panel form FlowPanel (:10) |
| NodeViewImpl.ui.xml:7 | component-declaration | SplitPane already declared |

**Stays in Java:** Selected node pushed to the job list (NodePresenter.java:55-59, :88-98).

## `SourceTabPresenter`

screen, presenter; complexity 1, effort S; used by 4.

- **Component:** `stroom.data.client.component.SourceTab`
- **Controller:** `stroom.data.client.component.SourceTabController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/SourceTabViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/client/component/SourceTab.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/SourceTabPresenter.java (with nested SourceTabView :92)`; `stroom-core-client/src/main/java/stroom/data/client/view/SourceTabViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/data/client/gin/StreamStoreModule.java:129-132 bindPresenterWidget(SourceTabPresenter, SourceTabView, SourceTabViewImpl); `stroom-dashboard-gwt/src/main/java/stroom/dashboard/client/gin/DashboardAppModule.java:165-168 (same)`; stroom-gwt/stroom-gwt-workbench/.../app/gin/{processing,query,content}/StreamStoreScreenModule.java:93 and editors/StreamStoreScreenModule.java:94 (SourceTabView binding)
- **Names:** Free. Aside: getLabel() dereferences sourceKey (SourceTabPresenter.java:54) before its null check (:57).

| Into the template | What | Detail |
| --- | --- | --- |
| SourceTabViewImpl.ui.xml:4; SourceTabPresenter.java:43 | component-declaration | Template root <d:Source ui:field="source" addStyleNames="max"/> (wave 13); SourceController attaches. |

**Stays in Java:** Tab label built from the source key (:52-63); Source location pushed to the source (:65-73).

## `AnnotationTagPresenter`

screen, presenter; complexity 3, effort M; used by 5.

**Since the merge.** Import changes only (WrapperView now in stroom.widget.datagrid.client). Nothing material.

- **Component:** `stroom.annotation.client.component.AnnotationTags`
- **Controller:** `stroom.annotation.client.component.AnnotationTagsController`
- **Template:** `none (uses the shared pass-through WrapperView)` → `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationTags.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/annotation/client/AnnotationTagPresenter.java`; `No GIN binding of its own`
- **Names:** AnnotationTag clashes with stroom.annotation.shared.AnnotationTag and the jOOQ table stroom.annotation.impl.db.jooq.tables.AnnotationTag. Chosen AnnotationTags.

| Into the template | What | Detail |
| --- | --- | --- |
| AnnotationTagPresenter.java:78 | component-declaration | Template root <a:AnnotationTagList ui:field="list"/> (wave 10). |
| AnnotationTagPresenter.java:67-76 | text | Toolbar name "Tag actions" and the three buttons on the list's toolbar: stay in the component's Java; their initial titles (:68,:71,:74) are always overwritten by setAnnotationTagType (:174-177), so only those remain. |

**Stays in Java:** Button titles built from the tag type (:174-177); Tab label and type from the tag type (:157-168); Edit caption and confirm message from data (:102,:115-117); AnnotationTagCreate and AnnotationTagEdit are dialogs (makeDialog :89,:100).

## `ApiKeysPresenter`

screen, presenter; complexity 3, effort M; used by 4.

- **Component:** `stroom.security.client.component.ApiKeys`
- **Controller:** `stroom.security.client.component.ApiKeysController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/ApiKeysViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/ApiKeys.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/client/presenter/ApiKeysPresenter.java (with nested ApiKeysView :268)`; stroom-core-client/src/main/java/stroom/security/client/presenter/ApiKeyUiHandlers.java (never set: ApiKeysPresenter.java:75 is commented out); `stroom-core-client/src/main/java/stroom/security/client/view/ApiKeysViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:183-186 bindPresenterWidget(ApiKeysPresenter, ApiKeysView, ApiKeysViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/SecurityScreenModule.java:131 (ApiKeysView binding)`
- **Names:** ApiKeys and ApiKeysController are free. UserTab (unit 263) makes it as its API Keys tab, so it moves with UserTab's callers.

| Into the template | What | Detail |
| --- | --- | --- |
| ApiKeysViewImpl.ui.xml:5-7; ApiKeysViewImpl.java:33-34,44-47,64-72 | structure | Delete the commented-out QuickFilter: filtering is in the list; QuickFilter is not declared. |
| ApiKeysViewImpl.ui.xml:9; ApiKeysPresenter.java:76 | component-declaration | <s:ApiKeysList ui:field="list" addStyleNames="dock-max"/> (wave 10) |
| ApiKeysPresenter.java:78-85 | text | Toolbar name "API key actions" and New/Edit/Delete buttons on the list's toolbar: stay in the component's Java per the toolbar-of-another-part rule, as one action enum. |

**Stays in Java:** Delete button title "Delete N Selected API Keys" and enabled states (ApiKeysPresenter.java:105-114): built from the selection count; Confirm messages (:179-181, :202-208) from data; EditApiKey is a dialog (makeDialog :130, :138).

## `CredentialsPresenter`

screen, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.credentials.client.component.Credentials`
- **Controller:** `stroom.credentials.client.component.CredentialsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/credentials/client/view/CredentialsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/credentials/client/component/Credentials.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/credentials/client/presenter/CredentialsPresenter.java (with nested CredentialsView :90)`; `stroom-core-client/src/main/java/stroom/credentials/client/view/CredentialsViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/credentials/client/gin/CredentialsModule.java:59-61 bindPresenterWidget(CredentialsPresenter, CredentialsView, CredentialsViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/CredentialsScreenModule.java:57 (CredentialsView binding)`
- **Names:** Credentials and CredentialsController are free in all_simple_names.json and in this wave.

| Into the template | What | Detail |
| --- | --- | --- |
| CredentialsViewImpl.ui.xml:4; CredentialsPresenter.java:52 | component-declaration | Replace the SimplePanel slot with <c:CredentialsList ui:field="credentialsList" addStyleNames="max credentials-manager"/> (wave 10 component); CredentialsListController attaches to it. |

**Stays in Java:** Tab icon KEY_BLUE, label and type (TabData of the controller).

## `ContentStorePresenter`

screen, presenter; complexity 2, effort M; used by 5.

- **Component:** `stroom.contentstore.client.component.ContentStoreTab`
- **Controller:** `stroom.contentstore.client.component.ContentStoreTabController`
- **Template:** `stroom-core-client/src/main/resources/stroom/contentstore/client/view/ContentStoreViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/contentstore/client/component/ContentStoreTab.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/contentstore/client/presenter/ContentStorePresenter.java (with nested ContentStoreView :118)`; `stroom-core-client/src/main/java/stroom/contentstore/client/view/ContentStoreViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/contentstore/client/gin/ContentStoreModule.java:36-38 bindPresenterWidget(ContentStorePresenter, ContentStoreView, ContentStoreViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/content/ContentStoreScreenModule.java:34 (ContentStoreView binding)`
- **Names:** ContentStore clashes with stroom-contentstore/stroom-contentstore-impl/.../stroom/contentstore/impl/ContentStore.java (server class). Chosen ContentStoreTab, matching the existing content-tab names DependenciesTab, GlobalPropertyTab and SourceTab.

| Into the template | What | Detail |
| --- | --- | --- |
| ContentStoreViewImpl.java:51-53 | aria | tabindex=0, role="region", aria-label="Content pack details" set in Java move into the template: the details slot at ContentStoreViewImpl.ui.xml:11 becomes a raw <div class="max dashboard-panel contentstore-scroll-y" role="region" aria-label="Content pack details" tabindex="0"> inside a g:HTMLPanel, holding the declared details component. |
| ContentStoreViewImpl.ui.xml:10; ContentStorePresenter.java:64 | component-declaration | <cs:ContentStoreContentPackList ui:field="contentPackList" addStyleNames="max dashboard-panel"/> (wave 10) |
| ContentStoreViewImpl.ui.xml:11; ContentStorePresenter.java:65 | component-declaration | <cs:ContentStoreContentPackDetails ui:field="contentPackDetails"/> (wave 8) inside the region div |
| ContentStoreViewImpl.ui.xml:8 | component-declaration | SplitPane already declared |

**Stays in Java:** Selected pack pushed to the details (ContentStorePresenter.java:109-113); Children's back-reference setContentStorePresenter (:61,:63) and updateState (:135-138) become callbacks the controller sets (setOnChanged).

## `BatchDocumentPermissionsPresenter`

screen, presenter; complexity 3, effort M; used by 5.

- **Component:** `stroom.security.client.component.BatchDocumentPermissions`
- **Controller:** `stroom.security.client.component.BatchDocumentPermissionsController`
- **Template:** `none in use: the presenter takes the shared QuickFilterPageView (BatchDocumentPermissionsPresenter.java:54,71), so stroom-core-client/src/main/resources/stroom/security/client/view/BatchDocumentPermissionsViewImpl.ui.xml and its ViewImpl are dead` → `stroom-core-client/src/main/java/stroom/security/client/component/BatchDocumentPermissions.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/security/client/presenter/BatchDocumentPermissionsPresenter.java (with the unused nested BatchDocumentPermissionsView :262); `stroom-core-client/src/main/java/stroom/security/client/view/BatchDocumentPermissionsViewImpl.java and its .ui.xml (dead)`; stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:165-168 bindPresenterWidget(BatchDocumentPermissionsPresenter, BatchDocumentPermissionsView, BatchDocumentPermissionsViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/SecurityScreenModule.java:118 (BatchDocumentPermissionsView binding)`
- **Names:** Free. Same shape as UserPermissionReport (wave 12); keep the two templates alike.

| Into the template | What | Detail |
| --- | --- | --- |
| BatchDocumentPermissionsPresenter.java:89 (view.setDataView(documentListPresenter.getView())) | component-declaration | Template declares the wave-10 QuickFilterPage (or the shared FilteredList, if built) with <e:DocumentList ui:field="documentList"/> inside it. |
| BatchDocumentPermissionsPresenter.java:79 | aria | setAccessibleName("Filter documents") becomes accessibleName="Filter documents" on the declared QuickFilterPage/FilteredList. |
| BatchDocumentPermissionsPresenter.java:151 | css-class | default-min-sizes (components.css:1085) is added to the Expression dialog by all six of its callers (here, ExecutionScheduleManager.java:613, ProcessorPresenter.java:253, MetaPresenter.java:266, SteppingPresenter.java:362, UserPermissionReportPresenter.java:164): put it on the root of the wave-9 Expression.ui.xml and drop it here. |
| BatchDocumentPermissionsPresenter.java:95-109 | text | Toolbar name and Filter/Edit/Batch Edit buttons on the document list's toolbar: stay in the component's Java (toolbar-of-another-part rule), as one action enum. |

**Stays in Java:** Button enabled states (:104,:109,:114,:126); Dialog captions "Filter Documents" (:156) and "Change Permissions" (:230) and popup sizes: the controller's dialog chrome; ExpressionPresenter and BatchDocumentPermissionsEdit are dialogs (makeDialog :138, :174), not declared.

## `ContentTemplateTabPresenter`

screen, presenter; complexity 4, effort L; used by 3.

- **Component:** `stroom.receive.content.client.component.ContentTemplateTab`
- **Controller:** `stroom.receive.content.client.component.ContentTemplateTabController`
- **Template:** `stroom-core-client/src/main/resources/stroom/receive/content/client/view/ContentTemplateTabViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/receive/content/client/component/ContentTemplateTab.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/receive/content/client/presenter/ContentTemplateTabPresenter.java (with nested ContentTemplateTabView :759); `stroom-core-client/src/main/java/stroom/receive/content/client/view/ContentTemplateTabViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/receive/content/client/gin/ContentTemplateModule.java:34-36 bindPresenterWidget(ContentTemplateTabPresenter, ContentTemplateTabView, ContentTemplateTabViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/content/ContentTemplateScreenModule.java:37 (ContentTemplateTabView binding)`
- **Names:** Free.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/receive/content/client/presenter/ContentTemplateTabPresenter.java:143-147 | `listPresenter.getView().asWidget().getElement().getStyle().setBorderStyle(BorderStyle.NON…` | static | New utility class border-none (border-style: none !important) in the LAYOUT section of stroom-app/src/main/resources/ui/css/stroom.css, beside .overflow-hidden (:859); add it in the template on the declared list (addStyleNames="max border-none"). Same statement at DataRetentionPolicyPresenter.java:131, RuleSetSettings.java:68, RulesPresenter.java:106, SelectionHandlersPresenter.java:129. |

| Into the template | What | Detail |
| --- | --- | --- |
| ContentTemplateTabViewImpl.java:52-53 | structure | descriptionFormGroup and expressionFormGroup start hidden: visible="false" on the FormGroups at ContentTemplateTabViewImpl.ui.xml:11 and :19. |
| ContentTemplateTabViewImpl.ui.xml:8; ContentTemplateTabPresenter.java:115 | component-declaration | <rc:ContentTemplateList ui:field="list" addStyleNames="max border-none"/> (wave 10) |
| ContentTemplateTabViewImpl.ui.xml:24; ContentTemplateTabPresenter.java:342,349 | component-declaration | <q:ExpressionTree ui:field="expression" addStyleNames="w-100"/> (wave 7) declared once; the controller shows or hides the FormGroup instead of swapping the view in and out. |
| ContentTemplateTabViewImpl.ui.xml:6,10-25 | component-declaration | SplitPane and FormGroup already declared |
| ContentTemplateTabPresenter.java:121-141 | text | Toolbar name and the eight template buttons on the list's toolbar: stay in the component's Java (toolbar-of-another-part rule), as one action enum. |

**Stays in Java:** Description HTML from the selected template and FormGroup visibility (ContentTemplateTabViewImpl.java:67-86); Button enabled states (ContentTemplateTabPresenter.java:662-683); Row context menu item texts (:267-310): the menu's item data; Validation and confirm messages (:409, :554-572) and dirty label; ContentTemplateEdit is a dialog (caption :529, :590), not declared.

## `ExecutionScheduleManager`

screen, presenter; complexity 4, effort L; used by 1.

- **Component:** `stroom.analytics.client.component.ExecutionSchedules`
- **Controller:** `stroom.analytics.client.component.ExecutionSchedulesController`
- **Template:** `none (the shared PagerView with a DataGrid and info label built in Java)` → `stroom-core-client/src/main/java/stroom/analytics/client/component/ExecutionSchedules.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/analytics/client/presenter/ExecutionScheduleManager.java`; `No GIN binding of its own; ExecutionScheduleManagerPlugin.java:39,44 is re-pointed at the controller`
- **Names:** ExecutionScheduleManager in all_simple_names.json is this class itself, so no real clash, but "Manager" names a service; chosen ExecutionSchedules (plural like the other list screens; label "Execution Schedule Manager" can stay).

| Into the template | What | Detail |
| --- | --- | --- |
| ExecutionScheduleManager.java:133-134,143 | component-declaration | <dg:PagerPanel ui:field="pager"><dg:DataGrid ui:field="dataGrid" tableName="Execution Schedules"/></dg:PagerPanel> (wave 5; needs DataGrid's no-argument constructor). |
| ExecutionScheduleManager.java:149-151 | css-class | Selection count label built in Java with classes paging-bar paging-bar__label (paging-bar.css; pager-paging pager-label until wave 2): declare <g:HTML ui:field="selectionCount" styleName="paging-bar paging-bar__label"/> in the template and hand it to the pager's info area (or as a PagerView child if wave 5 gives it one). |
| ExecutionScheduleManager.java:613 | css-class | default-min-sizes on the Expression dialog: into Expression.ui.xml's root, as for BatchDocumentPermissions (unit 252). |
| ExecutionScheduleManager.java:205-224 | text | Toolbar name "Execution schedule actions" and six buttons on its own pager's toolbar: into the template if PagerView takes toolbar children; else they stay in the component's Java as one action enum. |

**Stays in Java:** Column definitions (:154-161, :229+); Selection count text "N schedule(s) selected" (:462-469); Button enabled states (setButtonState); Dialog apply buttons wired through the dialogs' views (:174-200) become callbacks on those dialogs; ScheduledProcessEdit, BatchExecutionScheduleEdit, ExecutionScheduleRunNow (injected and owned :121-123) and Expression (makeDialog :585) are dialogs, not declared.

## `UserTabPresenter`

screen, presenter; complexity 3, effort M; used by 4.

- **Component:** `none of its own: the shared stroom.entity.client.presenter.LinkTabPanelView (LinkTabPanelViewImpl.ui.xml), which TabbedController takes`
- **Controller:** `stroom.security.client.component.UserTabController (extends stroom.entity.client.component.TabbedController<UserRef>)`
- **Template:** `none (uses LinkTabPanelView)` → `none (built by TabbedController over the shared LinkTabPanelViewImpl.ui.xml; the screen has no markup of its own)`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/client/presenter/UserTabPresenter.java`; `stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:177 bind(UserTabPresenter.class)`; stroom-gwt/stroom-gwt-workbench/.../app/gin/SecurityScreenModule.java:126 bind(UserTabPresenter.class); SecurityScreenGinjector.java:88 getUserTabPresenter() re-pointed
- **Names:** UserTab and UserTabController are free. Its six tabs are made on first show with addTab(tab, make, reader), so UserAndGroups, UserInfo, AppPermissionsEdit, UserPermissionReport, UserDependenciesList and ApiKeys are tab bodies, not template declarations.

**Stays in Java:** Tab labels and tooltips (UserTabPresenter.java:51-75): TabData given to addTab, the tab bar's data; Which tabs show, by permission (:128-141); Tab label and icon built from the user (:211-237).

## `DataRetentionPresenter`

screen, presenter; complexity 3, effort M; used by 4.

- **Component:** `stroom.receive.rules.client.component.DataRetention`
- **Controller:** `stroom.receive.rules.client.component.DataRetentionController`
- **Template:** `stroom-core-client/src/main/resources/stroom/receive/rules/client/view/DataRetentionViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/receive/rules/client/component/DataRetention.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/receive/rules/client/presenter/DataRetentionPresenter.java (with nested DataRetentionView :223)`; `stroom-core-client/src/main/java/stroom/receive/rules/client/view/DataRetentionViewImpl.java and its .ui.xml`; stroom-core-client/src/main/java/stroom/receive/rules/client/gin/PolicyModule.java:42 bindPresenterWidget(DataRetentionPresenter, DataRetentionView, DataRetentionViewImpl); `stroom-gwt/stroom-gwt-workbench/.../app/gin/processing/PolicyScreenModule.java:40 (DataRetentionView binding)`
- **Names:** Free; joins the pilot's package (RuleSetSettings, RuleEditor, FieldEditor). The controller can be a TabbedController only if that base accepts this component's tab bar and layer container: it takes LinkTabPanelView today, whose template differs (no dataRetentionViewImpl-tabBarOuter padding, components.css:938), so moving to it would change the look; otherwise a plain Controller with the same make-on-first-show tabs.

| Into the template | What | Detail |
| --- | --- | --- |
| DataRetentionViewImpl.ui.xml:7,9 | component-declaration | LinkTabBar and LayerContainerImpl already declared; DataRetentionPolicy and DataRetentionImpact are tab bodies made on first show (DataRetentionPresenter.java:92-111, :188-193), not declared. |

**Stays in Java:** Tab labels "Rules" and "Impact Summary" (DataRetentionPresenter.java:50-51): TabData for the tab bar; Dirty label "* Data Retention" (:157-160) and the close confirm (:196-214); tabBar.setTabPanel(layerContainer) wiring (DataRetentionViewImpl.java:42).

