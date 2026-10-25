# Visualisations

A chart of a table's results, on a dashboard or in a StroomQL query, generated from the user's request. You never
write a chart's code or its settings JSON: you choose a chart kind, the table columns it draws and its style, and
Stroom builds the Visualisation from a fixed template, drawn by its own reviewed adapter.

## Steps

1. Call `list_visualisations`: which charting libraries are installed (ECharts, and the visualisations content
   pack's D3 ones), the chart kinds each can draw, and each kind's columns and style settings.
2. If more than one library is installed and the user hasn't said, ask which to use (`ask_user`), recommending
   ECharts. Ask too about anything unclear that changes the chart: which column is the weight of a link, whether
   to count messages or distinct conversations, the time bucket for a time axis.
3. Look at the table's columns, and its rows with `current_results` when the shape matters. A chart needs a column
   for each required input; values such as a bar's length or a link's weight must be numbers.
4. If the table lacks what the chart needs (e.g. no count to size bars by), say so and offer the change that adds
   it with `propose_dashboard_change` (e.g. an `add_column` with `count()`, grouped by the category), or the revised
   StroomQL with `propose_query_change`; chart it once that is applied.
5. Call `propose_visualisation` with the library, the kind, the columns for each input, a name saying what it shows,
   and any style. It checks the table's rows can make the chart.
   - With ECharts it offers a "Create visualisation" button. The user names it and picks its folder (the
     dashboard's, by default) in Stroom's own dialog; it is then added below the table (or as a tab beside it), or
     put in the query's `show` clause, unsaved. It redraws whenever the table updates.
   - With D3 it gives the `add_vis` change (or the `show` clause) that wires the content pack's existing
     Visualisation to the table, for you to propose.
   - If it says the data can't make the chart, tell the user why and suggest the fix it gives. Don't propose it
     anyway, and don't try other columns that don't mean what the user asked.
6. Once the chart draws, its render report (what it drew, rows left out and why, or its error) shows on the
   button's card and in the dashboard's description under the Visualisation. If it failed or drew nothing, explain
   why and offer a fix.

## Kinds

| Kind | Columns (required in bold) | Good for |
| --- | --- | --- |
| bar | **category**, **value**, series | Comparing totals across categories, e.g. events per user |
| line | **x**, **y**, series | Change over time; give x a Date Time column (round it with e.g. `roundHour()`) |
| pie | **name**, **value** | Shares of a whole with few parts (12 or fewer; the rest become Other) |
| scatter | **x**, **y**, size, series | How two measures relate, e.g. bytes against duration |
| graph | **source**, **target**, weight, sourceGroup, targetGroup | Relationships and how connected things are, e.g. who messages whom |
| heatmap | **x**, **y**, **value** | A value across two dimensions, e.g. events by hour of day and day of week |
| calendar | **date**, **value** | A value a day over weeks or months, e.g. daily event counts (`floorDay()`, Date Time) |
| histogram | **value**, series | How a number is spread, e.g. response times or bytes; one row per event, not grouped |
| sankey | **source**, **target**, value | Flows from one thing to the next, e.g. source IP to port; loops are left out |
| chord | **source**, **target**, weight | Who links to whom round a circle; reads better than a network graph for a few dozen nodes |

A histogram counts rows itself, so its table should have one row per event (not grouped), and clicking its bars
selects nothing (they are ranges). A Sankey diagram's or chord diagram's links are added up per pair; a Sankey
diagram leaves out the weakest flow that would close a loop (a to b, b to a), and says so.

A network graph sizes each node by its number of connections (its degree) and draws each link as wide as its
weight. Its table usually wants one row per pair, e.g. grouped by sender and recipient (both at level 1) with a
`count()` as the weight; links between the same pair are added up, and links from a node to itself are left out.

## Filtering by a chart

Clicking a chart Stroom AI generated (a slice, bar, point, link or node) selects it; clicking it again, or empty
space, clears it. Other components can be filtered by the selection with an `add_selection_filter` change in
`propose_dashboard_change`, naming the `target` to filter, the `chart`, and `terms` pairing a field with what the
selection holds (e.g. `{"field": "event.code", "selection": "category"}`).

- `mode` query filters the target query's search (a classic Query's or an Embedded Query's), which re-runs as the
  selection changes: every table it feeds narrows, the chart's own too, a drill-down. Its fields are the query's
  data source fields, so the chart's column must read a field as it is (not e.g. a `formatDate()` of one).
- `mode` table filters one table's rows (or an Embedded Query's results) and the chart stays whole, e.g. clicking
  a slice to list its events in a detail table. Its fields are the table's columns.
- Unless the user has said which, ask them (`ask_user`), saying what each does.
- A network graph's node selects `node`: to filter by it at either end of a link, give a term for each end with
  `match` any.
- With nothing selected, the filter's terms drop out and the target shows everything.

## Embedded queries

A dashboard's Embedded Query component runs StroomQL (shown in the dashboard's description) with its own results
table and chart. `current_results` snapshots its results by its name, as a table's.

- Chart it with `propose_visualisation` (library echarts), naming it as the table: Stroom puts the chart's `show`
  clause at the end of its StroomQL, unsaved, and runs it. It has one chart, so a new one takes the old one's place.
- One that shows a Query by reference is charted in that Query: ask the user to open and attach it.
- Its settings (`queryOnOpen`, `autoRefresh`, `refreshInterval`) change with a `set_setting` in
  `propose_dashboard_change`, giving its name as the component and target. Table changes don't apply to it: its
  StroomQL makes its results.

## Tailoring a chart

- Columns and style of a chart already on a dashboard (e.g. "use Recipient for the legend", "make it horizontal"):
  a `set_vis_settings` change with `propose_dashboard_change`, naming the Visualisation pane and the settings by
  their ids (in the dashboard's description). An empty value puts a setting back to its default.
- Something its settings can't express (another kind of chart, e.g. "make it a stacked bar chart"): unless the
  user has said, ask them (`ask_user`) whether to replace the chart or add a new one beside it. Then call
  `propose_visualisation` with `replace` (the chart's pane name) or `addNew` true; it asks for this itself when the
  table already has a chart Stroom AI generated. Replacing rewrites that chart's Visualisation for the new one and
  renames it to `name`, saved at once (the user needs Edit permission on it), keeping its pane and place; the pane
  keeps its name unless you give `componentName`. Only charts Stroom AI generated can be replaced.
- The content pack's D3 Visualisations take only their own settings, by their own ids.
- A setting a chart's Visualisation doesn't have (one generated before the setting was added, e.g. `nodeColours`)
  can't be set on its pane: replace the chart (`replace`, the same kind) to regenerate it with every setting.
- A network graph's node colours: `nodeColours` group (by its `sourceGroup`/`targetGroup` columns) or node (each
  node its own colour). Its layout animates by default; `list_visualisations` gives the `seriesDefaults.force`
  options that tune its physics.

## Notes

- A chart gets only its table's top-level rows: Stroom doesn't open a table's groups for it. Group every column
  the chart uses at the same level (level 1), e.g. sender and recipient both at level 1 for one row per pair; a
  column grouped at level 2 is blank in every row the chart gets.
- Dates must be in a column formatted as Date Time to go on a time axis; dates held as text are drawn as
  categories, or can't be placed at all on a scatter plot.
- Bar, line and scatter charts zoom with the mouse wheel and pan by dragging (a scatter plot on both axes), and a
  network graph zooms and pans the same way; a reset zoom button at the top right of the chart puts it back. Charts
  have no title of their own: the dashboard shows the Visualisation pane's name above it, so name the pane well.
- Charts follow the user's theme and their date and time preferences (format and time zone), and update when those
  change.
- A chart draws at most its Max Rows (5000 by default) of the table's rows, and a network graph at most its Max
  Nodes (300), the most connected first.
- ECharts is installed by an administrator, as a Script doc holding `echarts.min.js` from an Apache ECharts
  release, version 6 or later; an administrator upgrades it by putting a newer release's build in that Script doc.
  Stroom adds its own adapter beside it the first time a chart is created.
