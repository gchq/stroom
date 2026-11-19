# Wave 45: UI audit wave 11, one component per thing

The last of the UI audit's waves ([../UI_AUDIT.md](../UI_AUDIT.md), themes T3 and T8, decision D7);
written 30 October 2026, after wave 44. Layout and behaviour unchanged; no story title changed.

## Done (30 October 2026)

- **The query/dashboard cycle is broken** (dashboard-query-03): no `stroom.query.client` class
  imports `stroom.dashboard.client`. The results parts both use are in `stroom.query.client.result`:
  the vis frame, the conditional formatting rules and their editors, and the column filter, format,
  values and download dialogs in `result.component`; the vis toolbar, selection, tooltip and
  messages in `result.vis`; the table rows, cells, filters, group levels and row styles in
  `result.table`. Four links were cut rather than moving dashboard code into query: the rules
  controller is a plain layer (the dashboard's settings tab is `ConditionalFormattingSettingsController`),
  the query table takes its hyperlink context as a value, `UniqueUtil` takes a type id, and
  `HasParams` went with `ComponentSelection`.
- **One component per thing** (dashboard-query-02): `Vis` and `QueryResultVis` are `ResultVis`;
  `Table` and `QueryResultTable` are `ResultTable` (the query's "Query Results" name is its
  `tableName`); the table filter's input and conditional formatting columns are `TableFilterColumns`.
- **The twins' controllers stay apart** (D7: merged only where they behave alike): the table
  controllers (a dashboard pane against a StroomQL screen; even their rows' expander depth differs),
  the vis controllers (pane settings against `QLVisResult`), the column managers (different models),
  the column values suppliers (different resources; one clears on failure), the text panes (stepping
  and hyperlinks against a Close button) and the search models (dashboard against StroomQL
  searches). The text panes' identical highlight finding is one `TextHighlights`, with a test.
- **The processor tasks** (data-pipeline-processing-05): the document's and the filter's task screens
  are one `ProcessorTasks` with one controller (the filter's adds its filter term); the summary pane
  keeps its height on each (400px on a document, 200px for a filter).
- **The annotation's setting blocks** (explorer-documents-shell-08): the status and assignee blocks
  the editor and the two change dialogs repeated are `AnnotationStatusSetting` and
  `AnnotationAssignedToSetting`.
- **The dialog frames** (widgets-1-19): the four button frames (`OkCancelContent`, `CloseContent`,
  `AcceptRejectContent`, `CreateOkCancelContent`) are one `DialogFrame`, and `ResizableDialog` is
  `Dialog` given a size (`new Dialog(PopupSize)`); what differed (the resizable modifier, maximise on
  double-click, the resize handles, the window listener) follows from whether it has one. The frame
  keeps only its kind's buttons in the page (the first full run found a story's "New" three times
  when the others were only hidden).
- **The two story classes that shared `App/Query/QueryResultsTable`** are one (ISSUES.md).

## Checked

Compile and checkstyle of every client module (main and test), every client and workbench test
(with the new `TestTextHighlights`), both GWT compiles, a check that no query class imports
dashboard, and full runs at 6 workers with the leak check against the baselines (the pre-audit
screenshots): the first failed the pathways Constraint Dialog story (the hidden buttons, fixed);
the second passed all 1,741 stories, none on a second attempt, and no screenshot changed but those
that differ run to run (the visualisations, the top bar's tooltip).

## Left, and why

- The tree pickers (the document chooser, the document picker, Export's) and the build info (About
  against the welcome tab) aren't the same: one component would redraw a screen.
