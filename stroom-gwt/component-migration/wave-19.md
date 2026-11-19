# Wave 19: Behaviour bugs left from waves 14 and 16

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 28 October 2026, after wave 18. The bugs ISSUES.md
recorded as left from wave 14's audit and wave 16 (all as on master), each fixed with a regression
test that fails without the fix, and a changelog entry.

## Done (28 October 2026)

| Bug | Fix | Test |
|---|---|---|
| Save As of a visualisation or floor map whose Assets tab was never opened failed: the tab copied from the document it was last given, which it is given only once shown. | `DocumentAssetsTabController.onSaveAs` takes the source document; `VisualisationEditor` and `FloorMapDocEditor` pass their own. | `TestDocumentAssetsTabController` |
| The text converter's code was highlighted for a new converter type only once saved. | `TextConverterSettingsController.setOnTypeChange`; the editor sets the Conversion tab's mode. | `TestTextConverterSettingsController` |
| Traces opened from a pathway showed empty Settings, Documentation and Permissions tabs. | `TracesEditor.showTraces` hides them. | `App/Main/TracesScreen` `Traces` |
| A query opened from an embedded query pane was disposed while open when the pane or the dashboard went. | `OwnedContentTabs`: the content manager owns the query's tab, and the pane closes it when it goes. | `TestOwnedContentTabs` |
| Showing a statistic store's Custom Roll-ups tab made a store with none dirty for good. | `StatisticsDataSourceEditor.masksToWrite`: only the implied "no roll-up" row, on a store read with none, is written as read. | `TestStatisticsDataSourceEditor` |
| A tab session stopped at a document that couldn't be loaded, or a reference with no type. | `DocumentPlugin.open` and `showDocument` take the failure callback and run it; the tab never shown is forgotten and its editor released. | `TestDocumentPlugin` |
| View Source of a pipeline whose source couldn't be fetched did nothing; its editor stayed owned. | An error alert, and the editor is released. | `App/Main/PipelineEditor` `ViewSourceFails` |
| Clicking a dashboard's View Current Selection twice left a listener refreshing the hidden popup. | `ListenerWhileShown`: added on the first showing only, removed on hide. | `TestListenerWhileShown` |

Where a story can't see the bug (the popup's listener, a tab the harness doesn't show, the editor's
highlighting), the logic is in a plain class or method with a JVM test. Checked by compiling,
checkstyle, the JVM tests and the affected areas' stories (editors, dashboards, the app shell,
pipelines, traces, tab sessions and visualisation assets) with the leak check and the screenshot
diff.
