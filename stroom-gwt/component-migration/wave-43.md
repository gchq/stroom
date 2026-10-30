# Wave 43: UI audit wave 9, names that say what things are

The ninth of the UI audit's waves ([../UI_AUDIT.md](../UI_AUDIT.md), theme T9, decision D2);
written 30 October 2026, after wave 42. Names only: nothing is drawn differently, and no story's
registered title changed (titles are the stories' ids and baselines; story classes were renamed
with what they show).

## Done (30 October 2026)

- **Edit and Dialog suffixes** (D2: "Editor" for everything that edits; a component says what it
  is, not that it is shown in a dialog): about forty item editors (`FsVolumeEdit` →
  `FsVolumeEditor`, `ProcessorEdit`, `AnnotationEdit`, `CredentialEdit`, `ActivityEdit`,
  `GlobalPropertyEdit`, the permission editors, `QueryEdit` and the rest, with their controllers,
  tests and blocks), and the dialogs named for what they are (`ChangeStatusDialog` →
  `ChangeStatus`, `ExportConfigDialog` → `ExportConfig`, `ProcessChoiceDialog` →
  `CreateProcessors`, `CredentialsManagerDialog` → `CredentialsPicker`, `UserPreferencesDialog` →
  `UserPreferencesEditor`, …). Where the plain name clashed with an existing class, a clear one was
  chosen (`AskStroomAiConfigEditor`, `ColumnFilterTabs`, `QueryInfoPrompt`, …).
- **Presenter-era leftovers:** `ButtonView` → `IsButton` (`ToggleButtonView` and its aliases went;
  its callers use `setPressed`), `DataGridView` → `GridDisplay`, `LayerContainerImpl` and its
  interface → one `LayerContainer`, `PopupSupportImpl` → `PopupHost`, `MySingleSelectionModel` →
  `SingleItemSelectionModel`, `HyperlinkEventHandlerImpl` → `HyperlinkOpener`, the workbench's
  `SidebarView` → `Sidebar`.
- **Clashes:** the dashboard's query pane is `QueryPanePlugin`/`QueryPaneModule`, its result panes
  `DashboardResultPane` (the query's `QueryResultConsumer`); dashboard "components" are panes
  (`PaneRegistry`, `PaneSettings`, `UnknownPane`; saved dashboards' field names unchanged); the code
  editor's `Action`/`Option` are `CodeEditorAction`/`CodeEditorOption`; the HTML table builder's
  classes are `HtmlTable…`; `Hyperlink` → `LinkValue`; the busy spinners are `SmallBusySpinner` and
  `LargeBusySpinner`, the value spinner's model `ValueSpinnerModel`.
- **Misleading names:** `Navigation` is the `Explorer` (block `explorer`); `MarkdownEdit` is the
  `DocumentationTab` and `MarkdownPreview` the `MarkdownEditor`; `EntityTree` → `DocumentPicker`,
  `ExplorerPopup` → `DocumentChooser`; the floor map's swapped editors are `FloorMapEditor` (the
  document's) and `FloorMapEditorTab`/`FloorMapMapTab`; the Server Tasks screen has one name
  (`ServerTaskList`, `ServerTasksPlugin`, `OpenServerTasksEvent`); `UserAndGroups` →
  `UsersAndGroups`; the monitoring plugins take their screens' names (`JobsPlugin`, `NodesPlugin`,
  `CachesPlugin`, …); `Core` → `AppPage`; the code editor's `RightBar` → `OverviewBar`; the XSD
  browser's classes are `Xsd…`; `DatabaseTablesMonitoringController` → `DatabaseTablesController`.
- **Fields:** GitRepoSettings' and the credentials picker's Hungarian names went (`urlBox`, `note`).
- **Docs** under `stroom-gwt/*.md` name the classes by their new names (the wave records and the
  audit's findings stay as written).

## Checked

Compile and checkstyle of every client module, every client test, both GWT compiles, and a full
run at 6 workers with the leak check against the baselines (the pre-audit screenshots, wave 42's
kept changes aside): all 1,741 stories pass, none on a second attempt, and no screenshot changed
but those that differ run to run (the force visualisations, the tree visualisation, the top bar's
tooltip).

## Left, and why

- Time Preferences still declares its own time zone fields rather than a `TimeZoneBox`: its help,
  widths, the offset's accessible name and the id a story finds it by differ from the box's, so it
  is more than a rename.
- Package moves (the `customdatebox` package, `XSDBrowser.gwt.xml`) are wave 10's.
- `NameDialog` duplicates `NameEntry` and should be merged, not renamed.
