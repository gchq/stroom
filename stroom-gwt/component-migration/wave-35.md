# Wave 35: UI audit wave 1, dead code part 1

The first of the UI audit's waves ([../UI_AUDIT.md](../UI_AUDIT.md), decision D4: the whole
removal list, code only stories or tests use included); written 29 October 2026, after wave 34.

## Done (29 October 2026)

- **Whole classes nothing uses** (each checked again for whole-word references in every module,
  template, GWT module and script): `RecordingEventBus`, `MyScriptInjector`,
  `ElasticIndexFieldDataProvider`, `InsertQueryElementEvent`, `SearchStartEvent`, `SearchEndEvent`,
  `Views`, `DataUploadService`(`Async`), `CommonSpanBuilder`, `FileData`,
  `InterceptingSelectionChangeHandler`, `CloseExplorerTabEvent`, `OpenExplorerTabEvent`, the
  explorer's `FocusEvent`, `UrlQueryParameterChangeEvent`, `HelpActionEvent`/`Handler`,
  `DocumentAssetImageResource`, `FindDocResultListHandler`, `SizeUtil`, the tab `CloseEvent`,
  `MaximiseRequestEvent`, `HasMaximiseRequestHandlers`, `HandlerRegistrations`, `TableUtil`,
  `Debug`, `FixedNodeExtentProvider`, `IndicatorEvent`, `HasIndicatorHandlers`, `XMLStyler` (with
  its test), `ConstrainedLong` and `EditableLong` (a story now uses `EditableInteger`).
- **Events that were never fired, or fired by a controller to itself:** `ReopenResultStoreEvent`
  (and with it the chain it fed, `DashboardEditor`, `DashboardBodyController`, `Queryable` and
  `QueryPaneController`'s `setResultStoreInfo`, which ended in `SearchModel`'s empty one),
  `ShowAboutEvent`, `DialogEvent` (and `PopupManager`'s handler), `PromptEvent` and
  `PromptCallback` (fired only by stories; the prompt stories went with them), and
  `FocusExplorerFilterEvent`/`FocusExplorerTreeEvent` (`NavigationController`'s key bindings call
  its view and tree directly).
- **GIN:** `UserPermissionsReportPlugin` (only a commented-out binding), `SqlTemporalStoreGinjector`,
  `EntityGinjector` and `AlertGinjector` (empty once its unused getter went); `HyperlinkEventHandlerImpl`
  bound once per app (`AppModule`, `DashboardAppModule`), `VisFunctionCache` once (`VisModule`), and
  `VisModule` and `EmbeddedQueryModule` no longer listed twice (`DashboardGinjector` lists them).
- **Unused members:** `getSelectedCredentialsId`, `getTreeRowHandler`, `isUtc`, `buildAsList`,
  `buildClassAttribute`, `MenuBuilder`'s simple and info item builders (and their commented-out
  callers), `GroupHeading` with its appearance and `.menu__group-heading`.
- **Story-only code, with its stories:** `SingleLineCodeEditor` and its controller (and the single
  line editor's three CSS rules); the first tree renderer family (`TreeRenderer`, `CellRenderer`,
  `TextCellRenderer`, `ShadowBox`, `RoundedRectangle`, `ArrowConnectorRenderer`; the app's panels
  use `TreeRenderer2`'s layer names) with `HTreeStories`, and wave 32's pointer rule, which only the
  old renderer set. Their baselines went with them.
- **GWT modules and resources:** `Vis.gwt.xml`, `WebSocket.gwt.xml` (and its two `inherits`), the
  `shared` source paths of the editor and tree modules (no such packages anywhere), GWT's scroll
  panel stylesheet overrides (no GWT cell view or custom scroll panel is used) and an empty
  `.gitignore`.
- **The workbench:** the nine GIN modules that bound nothing (out of every story ginjector's list), an
  empty file named `.java`, two ginjector getters, story helpers (`drawnGrid`, `isCreated`, `named`,
  `MENU`) and framework API nothing calls (`isAnyOpen`, `keyForName`, `keyForCode`,
  `getSourceRoots`, `PLAY_STATUS_ATTRIBUTE`, and the inline radio and check controls).
- **Kept for later waves:** the empty `TaskModule` (D3 moves `TaskManagerPlugin` into it, in the
  package wave) and `HasFormatHandlers` (wave 2 makes `FormatEvent` a callback).
