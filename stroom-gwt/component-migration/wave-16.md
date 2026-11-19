# Wave 16: The last presenters and the presenter framework

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 28 October 2026, after wave 15, from the code as it
then stood. Each unit is done when it meets "Done means, for every unit".

Waves 3, 6, 8 and 14 left some units as thin presenters over their new controllers, so that their
callers could move later; nothing else in the app is a presenter now. This wave moves those last
callers, converts the presenters that are left, re-keys the popup and content-tab events on
`Controller`, and then deletes the presenter framework (`MyPresenterWidget`, `View`, `ViewImpl`,
`UiHandlers`, `PresenterAdapter` and the GIN helpers), with the dead code the plan deferred.

## Audited (28 October 2026)

Five reviewers read the wave against this plan, "Done means" and the UI guide (the popup layer;
the editor and picker wrappers; the document host and tab types; the framework's deletion; the
workbench and docs). Fixed, with the wave:

- **A dialog shown again hid its owner's task monitor once hidden.** Taking the dialog's spinner
  back set the controller's task monitor to the default rather than removing it, and a lookup stops
  at the nearest node that provides one, so a pane's settings dialog, once shown and hidden, sent its
  requests to the app's spinner rather than its tab's. `ControllerNode.unprovide` takes it away now,
  through `DialogTaskMonitor` (`TestDialogTaskMonitor`). The wave's own regression, never in master.
- **The task monitor helper**, also copied under other names (`MenuController`,
  `KeyStoreSecretFormController`, the asset upload dialog, and the `createTaskMonitor()` of
  `ContentTabController`, `DocumentEditorHost` and `AskStroomAiController`), is `taskMonitor()`
  everywhere; its tests check the app-wide task's event and a lookup per task.
- **Tab types:** Processor Filter Tasks and Execution Schedules return their `TAB_TYPE` rather than
  a literal, and `TestContentTabController` checks only content tabs, and that none returns a
  literal; the changelog entry says the fix is for sessions saved from now on.
- **`DialogCoverage`** ignores comments (javadoc examples were read as calls) and skips build
  directories, which made `TestPresenterCoverage` flaky.
- **Smaller fixes:** javadoc on the popup events' and full screen event's renamed members, the
  popup support's constructor and the `read` methods that lost their interface; `Main.maximise`'s
  parameter is `content`; dead code naming the link tabs view in `EmbeddedQueryController`; an
  unused import; an orphan stories comment; and docs (`COMPONENT_MODEL_DESIGN.md`,
  `COMPONENTS.md`, `UI-GUIDE.md`, this record).

Left (recorded in ISSUES.md, "Left from wave 16"): tab sessions saved before the wave keep the
old types; a session stops partway when a document fails to load; the pipeline's source fails
silently; a pane's selection info clicked twice keeps a listener; the test suite's inventories to
regenerate; the dialog stories still show `StoryContent` with `ShowPopupEvent.builder`. After the fixes, both apps GWT compile, the
JVM tests pass and the full workbench run passed all 1,722 stories with the leak check.

## Done (28 October 2026)

Every unit, in 9 commits (and 4 for the UI guide). Nothing is a presenter: the popup events (`ShowPopupEvent` and the
rest), the content tab and full screen events and the open callback carry a `Controller`;
`ControllerDialog` shows a controller itself (its `DialogHost` and `ControllerView` went), gives it
the dialog's task monitor while shown, and can rename it; `MyPresenterWidget`, `View`, `ViewImpl`,
`ViewWithUiHandlers`, `UiHandlers`, `HasUiHandlers`, `PresenterAdapter` and
`AbstractPresenterModule` are deleted, and the GIN modules extend `AbstractGinModule`. Checked by
compiling every client module, checkstyle, the client, widget and workbench JVM tests (2,913), both
apps' GWT compiles, and two full workbench runs with the leak check: before unit 12, all 1,722
stories passed and of their screenshots 1,614 match, 12 are new (waves 13 and 14's stories with no
baseline yet) and of the 96 that differ none is the wave's but `LinkTabPanel.Basic` (below); the
others are those wave 15 listed, the visualisations' and `AppShell.OpensDocument`, which differ from
run to run. After unit 12 and the audit's fixes, a second full run passed all 1,722 stories with the leak
check, the same screenshots matching and differing (but for visualisations that differ from run to
run, and `LinkTabPanel.Basic`, whose new baseline it matched).

What was decided on the way, where it differs from the plan below:

- **`makeDialog` stays**, for any controller (`Controller.makeDialog`, `AppRoot.makeDialog`): it
  makes one owned until it is hidden, e.g. the pipeline's JSON source dialog, set up before its text
  is fetched.
- **A code editor's controller is its widget.** `CodeEditorController.attach` makes the editor its
  widget, so it can be shown in a slot or as a dialog; the query's editor is declared in
  `QueryEdit.ui.xml`.
- **The pickers have `getBox()`.** `DocSelectionController` and `UserRefSelectionController` give
  the box they are attached to, for the stories that show one they made; they aren't made widgets,
  as they are attached in 62 places.
- **New controllers:** `InfoTooltipController` and `IFrameController`, over the components the
  presenters wrapped; `StoryContent` in the workbench for `ContentPresenter`.
- **The popup chrome's handlers** are a `DialogActionHandler` interface (three implement it) and a
  `Consumer<HideRequest>`; `HideRequestUiHandlers` and `DefaultHideRequestUiHandlers` went.
- **The dialog's task monitor** is provided to the shown controller only when it provides none of
  its own (e.g. a list's pager), and taken back when it is hidden.
- **One task monitor helper.** There were 103 copies (the plan said about two dozen): 77 resolved
  the provided monitor when called and 26 when a task started. `Controller.taskMonitor()` does the
  latter, the same for a request made at once and right for one made before it is owned.
- **`ScreenHarness.shownContent()` went**, as `shownComponent()` does the same for any controller.
- **`DialogCoverage`** finds a screen as a controller (not by a `Presenter` name) and a dialog from
  `ControllerDialog.show`/`showOnce` as well as `ShowPopupEvent.builder`: 74 screens and 146 dialogs,
  where the old check found almost none.
- **Tab sessions** skip a tab whose type no plugin opens (a document's permissions, which needs its
  document) rather than stopping the tabs after it.
- **The link tab ring check** (`test-runner/lib/real-input/tabs.mjs`) checks the link tab panel's
  story now its own went; that story's screenshot then shows the selected tab's focus ring, and its
  baseline was updated.
- **Dead code.** The commented-out move and rename code in `DocumentPlugin`, the index volume
  groups' old popup code and the email destination's old editor code went; `ApiKeysListViewImpl.ui.xml`
  had no class.
- **The dialog stories** show `StoryContent` with `ShowPopupEvent.builder`, as they showed
  `ContentPresenter`, rather than through `ControllerDialog` (unit 9 said); recorded in ISSUES.md.
- **The UI rules moved** (at the user's request) to `stroom-gwt/UI-GUIDE.md`, which `AGENTS.md`
  points to with the three rules most costly to miss; it now also says handlers are wired in Java,
  never with `@UiHandler`, and that raw HTML controls aren't used.

The user's decisions (28 October 2026):

- **The framework goes** (unit 12) once units 1 to 11 are in and a full workbench run with the
  leak check and a screenshot diff passes; no need to stop and ask again.
- **Two screens' shared tab types are fixed** (unit 14).
- **`LinkTabsWidgetStories` are deleted** with their baselines (unit 7).
- **The coverage check is renamed** `DialogCoverage` (unit 9).

## Order

The popup events come first: every other unit shows a dialog or popup, and the tooltip and IFrame
wrappers stayed only because the popup manager couldn't show a controller again or rename one.

1. The popup layer, keyed on `Controller` (unit 1).
2. The wrappers' callers, in any order (units 2 to 7).
3. The document host and the open callback (unit 8).
4. The workbench's helpers and the coverage check (unit 9).
5. Unused presenter-era types and the deferred dead code (units 10 and 11).
6. The framework (unit 12), after a full run passes, then the docs (unit 13).

The tab types (unit 14) can go at any point.

Each step compiles and runs its stories on its own; a full workbench run with the leak check and a
screenshot diff goes before and after unit 12.

## 1. The popup layer

framework; effort L; used by about 40 (the six events, `PopupManager`, `PopupSupportImpl`,
`ControllerDialog`, the stories).

- **Now:** `ShowPopupEvent`, `HidePopupEvent`, `HidePopupRequestEvent`, `RenamePopupEvent`,
  `EnablePopupEvent`, `DisablePopupEvent` and `DialogEvent` carry a `MyPresenterWidget<?>`;
  `PopupManager` keys its `PopupSupport`s on it, and `PopupSupportImpl` takes its `View`.
  `ControllerDialog` wraps each controller in a `DialogHost` (a `MyPresenterWidget<ControllerView>`)
  to get through.
- **Becomes:** the events carry a `Controller`; `PopupManager` keys on it; `PopupSupportImpl` takes
  the controller's widget. `ControllerDialog.show` and `showOnce` fire the event for the controller
  itself, so `DialogHost` and `ControllerView` go. New `ControllerDialog.rename(controller, caption)`
  (for the IFrame dialog, unit 3).
- **Task monitor:** `PopupSupportImpl.setTaskMonitor` gives the dialog's spinner to the shown
  presenter today; it becomes a `Contexts.TASK_MONITOR` provided to the shown controller while it is
  shown, so a controller's requests show on its dialog's spinner. Fixes the wave 15 leftover "the
  dialog host's task monitor" (ISSUES.md, "Left from wave 15"); Ask Stroom AI in dialog mode is the
  story to check.
- **Popup chrome callbacks:** `HideRequestUiHandlers`, `DialogActionUiHandlers` and
  `DefaultHideRequestUiHandlers` become plain callbacks (`DialogButtons.setOnAction`, the popup's
  hide request as a `Consumer`).
- **Watch:** showing the same controller twice hides it (the popup manager's toggle, which the
  tooltip relies on); a controller shown with `show` stays owned by its owner and isn't disposed
  when hidden, one shown with `showOnce` is.
- **Tests:** `TestAppRoot`, `TestController` and `TestPresenterAdapter` fire hide events with
  presenters; they move to controllers (`TestPresenterAdapter` goes with the adapter, unit 12).

## 2. `TooltipPresenter`

widget, wrapper; effort S; used by 12 (7 lists and `InfoColumn`, 4 stories).

- **Becomes:** a new `InfoTooltipController` (`stroom.widget.tooltip.client.component`, beside the
  `InfoTooltip` component, which the presenter wraps today). Each owner keeps one and it shows itself
  with `ControllerDialog.show(this)`, so a second click on the icon closes it, as now.
  `show(text|html, popupPosition[, anchor])` moves to it unchanged.
- **Callers:** `ImportConfigConfirmController`, `ProcessorListController`, `IndexShardListController`,
  `ProcessorTaskSummaryListController`, `ProcessorTasksListController`, `NodeStatusListController`,
  `TaskManagerListController`, `InfoColumn`; stories `TooltipWidgetStories`, `TooltipStories`,
  `PopoverStories`, `InfoPopoverCellStories` (story titles kept).
- **Goes:** `stroom.widget.tooltip.client.presenter.TooltipPresenter`.

## 3. `IFramePresenter`

widget, wrapper; effort S; used by 1 (`HyperlinkEventHandlerImpl.openDialog`).

- **Becomes:** a new `IFrameController` (`stroom.iframe.client.component`, over the `IFrame`
  component the presenter wraps today: URL, custom title, label, title polling). The hyperlink
  handler makes one with `appRoot`, shows it with
  `ControllerDialog.showOnce`, and renames the dialog as the page's title changes with
  `ControllerDialog.rename` (unit 1). The frame is removed on dispose, as the IFrame tab's is.
- **Goes:** `stroom.iframe.client.presenter.IFramePresenter`.

## 4. `EditorPresenter`

widget, wrapper; effort M; used by 15.

- **Becomes:** each caller declares a `CodeEditor` (or makes one where it has no template) and
  attaches a `CodeEditorController`, as the wave 6 callers do.
- **Callers:** `TextPaneController` (the raw text pane), `PipelineStructureController` (the
  pipeline's JSON source dialog: a `CodeEditorController` shown with `ControllerDialog.showOnce`),
  `AnalyticEmailDestinationController` (subject and body templates), `QueryEditController`;
  workbench `SplitPaneContent`, `EditorWidgets`, `AceEditorStories`, `AceEditorFeatureStories`,
  `AceEditorThemeStories`, `ViewStateStories`, `ScreenHarness`. Comments only: `CodeEditor`,
  `CodeEditorController`, `DelegatingAceCompleter`, `PresenterAdapter`.
- **Goes:** `stroom.editor.client.presenter.EditorPresenter`. `CodeEditor` stops implementing `View`.

## 5. `DocSelectionBoxPresenter`

widget, wrapper; effort M; used by 7.

- **Becomes:** a `DropDownBox` in the template with a `DocSelectionController` attached, as wave 8's
  callers do.
- **Callers:** `AskStroomAiController` (the model picker, in a slot today: the wave 15 leftover;
  `AskStroomAi.ui.xml` declares the `DropDownBox`); workbench `DocumentEditors`, `SelectorWidgets`,
  `DocSelectionBoxStories`, `FloorMapInitStories`, `ViewStateStories`. `DropDownBox` only names it in
  a comment.
- **Goes:** `stroom.explorer.client.presenter.DocSelectionBoxPresenter`. `DropDownBox` stops
  implementing `View`.

## 6. `UserRefSelectionBoxPresenter`

widget, wrapper; effort S; used by 2 (stories only: `SelectorWidgets`, `UserRefSelectionBoxStories`).

- **Becomes:** the stories declare a `DropDownBox` and attach a `UserRefSelectionController`.
- **Goes:** `stroom.security.client.presenter.UserRefSelectionBoxPresenter`.

## 7. `LinkTabsPresenter`

widget, unused; effort S. On the plan's deferred list since the first review: nothing in the app
uses it (the settings screens are `TabbedController`s).

- **Goes:** `LinkTabsPresenter`, `LinkTabsLayoutView`, `LinkTabsLayoutViewImpl` and its template;
  the `bindSharedView(LinkTabsLayoutView...)` lines in `DashboardModule`, `ScreenViewsModule` and
  `DashboardScreenModule`; `LinkTabsWidgetStories`, whose stories show only this widget (their
  screenshot baselines go with them; no other story is renamed). Any CSS only its template uses
  goes too, after a search of every template and stylesheet.

## 8. `DocumentEditorHost` and the open callback

framework; effort L; used by the content manager, the full screen and the document plugins.

- **Now:** `DocumentEditorHost<D>` is a `MyPresenterWidget<ControllerView>` (implementing
  `DocumentTabData`, `HasSave`, `HasCommonTabs`) so it can be a content tab and full screen.
  `ContentManager.open`, `OpenContentTabEvent`, `ShowFullScreenEvent`, `FullScreenController`,
  `ContentFocus` and the plugins (`DocumentPlugin`, `DashboardPlugin`, `PipelinePlugin`,
  `FolderPlugin`, `FolderRootPlugin`, `DocumentPluginEventManager`) pass presenters.
- **Becomes:** `DocumentEditorHost` is a `LayerController` (as `ContentTabController` is), and the
  content tab and full screen events and `ContentManager.open` take a `Controller`.
- **The open callback** (`OpenDocumentEvent`, `OpenContentTabEvent`, the plugins' `open(...)`)
  becomes a `Consumer<Controller>`, so `ContentOpenCallback` no longer gives a controller as null;
  `DashboardBodyController`'s duplicate-tab callback (which needs the dashboard's host) and
  `TabSessionManager` move with it. Closes the wave 15 leftover in ISSUES.md;
  `TestContentOpenCallback` changes with it (or goes, if `ContentOpenCallback` does).
- **Watch:** tab session restore (each tab opens the next from the callback), Save on close, dirty
  marks, maximise and full screen; the `AppShell` and document plugin stories cover them.

## 9. The workbench's helpers and coverage check

workbench; effort M.

- `ContentPresenter` (a presenter showing any widget, for the dialog stories): goes; the stories
  (`ModalStories`, `StaticDialogStories`, `DialogStories`, `EscapeStories`, `PopupHarnesses`) show a
  small controller with `ControllerDialog`. Story titles kept.
- `TabWidgets.ContentView`, `QuickFilterPanelStories.RowsView` (and its `QuickFilterUiHandlers`),
  `LinkTabPanelStories`, `StoryPopups`, `StoryDocumentPlugins`, `DocPluginStories`: off
  `MyPresenterWidget` and `ViewImpl`.
- `ScreenHarness`: the `ownPopup(MyPresenterWidget)` overload and the editor helper go; its hide
  events use controllers.
- `PresenterCoverage` and `TestPresenterCoverage` (the Gradle task `workbenchPresenterCoverage`)
  find dialogs from `ShowPopupEvent.builder(...)` arguments: re-point them at
  `ControllerDialog.show`/`showOnce` calls, keeping the report's meaning (every dialog has a story).
  Renamed for what it checks: `DialogCoverage`, `TestDialogCoverage` and the Gradle task
  `workbenchDialogCoverage` (the user's decision).

## 10. Unused presenter-era types

effort S; each checked for users before it goes.

- `UiHandlers` interfaces with no users or only each other: `ProcessingStatusUiHandlers`,
  `ChangeUiHandlers`, `ExpressionUiHandlers`, `DirtyUiHandlers`, `ExplorerTreeUiHandlers`,
  `QuickFilterUiHandlers` (after unit 9).
- `HasDocumentRead` (six controllers implement it, nothing reads through it; wave 14's leftover).
- `ApiKeysListViewImpl.ui.xml` (no class uses it) and the commented-out `ApiKeysView` at the
  bottom of `ApiKeysController`.
- `MaximiseEvent` carries a `View`: it takes a `Widget`. `PagerPanel` stops implementing `View`.

## 11. The deferred deletions

effort S. From "Deferred: delete rather than convert", now that removals don't wait for other
work: `ItemCell`, `SvgIconCell`, `FindResultWithPermissionsCell`, `JobTypeCell`, `ToolTipCell`,
`SvgStringCell` and `MyTreeLayout`, each checked again for users (code, templates, reflection,
stories) and its CSS with it.

## 12. The presenter framework

framework; effort M.

- **Goes:** `MyPresenterWidget`, `View`, `ViewImpl`, `ViewWithUiHandlers`, `UiHandlers`,
  `HasUiHandlers` (`stroom.widget.presenter.client`), `PresenterAdapter`, `ControllerView` (if unit 1
  hasn't removed it), `Controller.makeDialog` and `AppRoot.makeDialog` (presenter dialogs; their
  callers will have moved by then), `TestMyPresenterWidget`, `TestPresenterAdapter`.
- **Moves:** `MyPresenterWidget`'s task monitor rule (one set explicitly, else the context's, else
  app-wide) into `Controller`, which also replaces the lazy `taskMonitor()` about two dozen
  controllers copy (wave 14's leftover "one task monitor helper, copied").
- **GIN:** `AbstractPresenterModule` has only `bindPresenter`, `bindPresenterWidget` and
  `bindSharedView` over GIN's `AbstractGinModule`; its 24 modules extend `AbstractGinModule` and it
  goes.
- **Stays:** `Layer`, `LayerContainer`, `Page` and `HandlerRegistrations`, which aren't presenter
  types, keep their package until a package tidy (below).
- **Checks:** full workbench run with the leak check and a screenshot diff, both apps' GWT
  compiles, checkstyle and the JVM tests. The unowned-controller count in development builds must
  stay at nothing.

## 13. Docs

- `COMPONENT_MODEL_DESIGN.md` (migration progress, the presenter sections),
  `stroom-gwt/stroom-gwt-workbench/WRITING-STORIES.md` (`ownPopup`, presenter stories), this plan's
  progress, and ISSUES.md's leftovers this wave closes.
- **AGENTS.md** still says "Every presenter is a controller too (`MyPresenterWidget extends
  Controller`)" and describes `makeDialog(provider)` for presenter dialogs. It isn't edited by
  agents: the lines to change are listed for the user instead.
  (Done otherwise: at the user's request, `AGENTS.md`'s section moved to `stroom-gwt/UI-GUIDE.md`,
  with a pointer left in `AGENTS.md`; see "Done".)

## 14. Screens that share a tab type

effort S; a bug master has too (changelog entry).

- **Now:** `GlobalPropertyTabController.TAB_TYPE` is "Caches", as `CachesController`'s is, so a
  saved tab session holding Properties reopens Caches (or nothing); `BatchDocumentPermissionsController`
  and `DocumentUserPermissionsContentTabController` share "DocumentPermissions".
- **Becomes:** Properties' own type, "Properties"; the two permissions screens each their own type,
  each plugin opening its own. Sessions saved before keep what they hold (a Properties tab saved as
  "Caches" reopens Caches once); new ones are right.
- **Tests:** a JUnit regression test that every content screen's tab type is unique (fails before
  the fix); the tab session stories.

## Left for wave 17

The package moves, the names that still say "presenter" and the CSS classes named after deleted
views are wave 17 (`wave-17.md`).
