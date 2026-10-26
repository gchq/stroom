# Visualisations

A chart of a table's results, on a dashboard or in a StroomQL query, generated from the user's request. You never
write a chart's code or its settings JSON: you choose a chart kind, the table columns it draws and its style, and
Stroom builds the Visualisation from a fixed template, drawn by its own reviewed adapter.

## Steps

1. Call `list_visualisations`: which charting libraries are installed (ECharts and D3, which Stroom generates
   charts with, and the visualisations content pack's own D3 charts), the chart kinds each can draw, and each
   kind's columns and style settings.
2. If both ECharts and D3 are installed and the user hasn't said, ask which to use (`ask_user`), recommending
   ECharts (it has a few more settings). Ask too about anything unclear that changes the chart: which column is the weight of a link, whether
   to count messages or distinct conversations, the time bucket for a time axis.
3. Look at the table's columns, and its rows with `current_results` when the shape matters. A chart needs a column
   for each required input; values such as a bar's length or a link's weight must be numbers.
4. If the table lacks what the chart needs (e.g. no count to size bars by), say so and offer the change that adds
   it with `propose_dashboard_change` (e.g. an `add_column` with `count()`, grouped by the category), or the revised
   StroomQL with `propose_query_change`; chart it once that is applied.
5. Call `propose_visualisation` with the library, the kind, the columns for each input, a name saying what it shows,
   and any style. It checks the table's rows can make the chart.
   - With `echarts` or `d3` it offers a "Create visualisation" button. The user names it and picks its folder (the
     dashboard's, by default) in Stroom's own dialog; it is then added below the table (or as a tab beside it), or
     put in the query's `show` clause, unsaved. It redraws whenever the table updates.
   - With `content-pack` it gives the `add_vis` change (or the `show` clause) that wires the content pack's
     existing D3 Visualisation to the table, for you to propose.
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
| graph | **source**, **target**, weight, sourceGroup, targetGroup, level3 to level6 | Relationships and how connected things are, e.g. who messages whom |
| heatmap | **x**, **y**, **value** | A value across two dimensions, e.g. events by hour of day and day of week |
| calendar | **date**, **value** | A value a day over weeks or months, e.g. daily event counts (`floorDay()`, Date Time) |
| histogram | **value**, series | How a number is spread, e.g. response times or bytes; one row per event, not grouped |
| sankey | **source**, **target**, value, level3 to level6 | Flows from one thing to the next, e.g. source IP to destination IP to port |
| chord | **source**, **target**, weight, level3 to level6 | Who links to whom round a circle; reads better than a network graph for a few dozen nodes |
| sunburst | **name**, level2 to level6, value | A whole split by levels, as rings, e.g. events by region, then host, then process; a click selects a segment's path (its name and each level out to it) |
| gauge | **value**, name | A value against its range, e.g. the latest or the maximum; a gauge per name when a name column is mapped, else the first row's value |

A histogram counts rows itself, so its table should have one row per event (not grouped), and clicking its bars
selects nothing (they are ranges). A Sankey diagram's or chord diagram's links are added up per pair.

A Sankey diagram, network graph or chord diagram can follow more than two columns: map level3 (then level4 to
level6) to the columns after target, e.g. source IP, destination IP, then port. Each row links each column to the
next, up to the first it leaves empty. A Sankey diagram's levels each have their own nodes (an IP at two levels is
two nodes, so no flow loops); a network graph's and chord diagram's nodes are shared. A click on a link between two
levels selects those two levels' values (e.g. target and level3): give a filter a term for each level's column
with its own selection, as for source and target.

A network graph sizes each node by its number of connections (its degree), or with `nodeSize` total by its links'
total, and draws each link as wide as its weight, or (without a weight column) as the rows it comes from. So a
table of one row per message needs no change: links between the same pair are added up, and links from a node to
itself are left out. Without sourceGroup or targetGroup columns, each node has its own colour (and each link its
source's).

## Suggesting a chart

Asked to suggest a chart for a dashboard, start with a table no chart is linked to. Look at its columns (the types
of their values in a snapshot, their formats, grouping, sorting and filters) and at the charts already on the
dashboard (their kinds and tables), so as not to repeat one. Then ask the user which chart to make (`ask_user`),
unless they named one: offer the two to four kinds from the table above that suit the data, each with the columns
it would map and what it would show (e.g. "Network graph: who messages whom, sender to recipient"), recommending
one. Ask it in the same questions as the library and how the chart's clicks should filter, so the user answers
once. Never pick the kind for them: seen in testing, a network graph was offered when the user wanted to choose.
Then propose the chart they chose.

Asked for a chart described in words, map the description to a kind and its settings, and say which variant you
chose and which you didn't. For example, "a calendar-like heatmap of user activity" is either a calendar of counts
by day, or a heatmap of weekday by hour of day. If no table has the columns the kind needs, give
`propose_visualisation` a `newTable`: the changes that add one (as `propose_dashboard_change` takes them:
`add_table`, then its columns, each with `table` set to its name). If the table charted only needs changing (e.g.
grouped by the pair a network's links join, with a `count()` for their weight), give `tableChanges` instead: its
changes, without `table`. Either way the chart's button changes the table and adds the chart (and its filter)
together, so never propose the table change first, with a second button for the chart. This holds when updating a
chart too (`replace`): with `newTable` the chart replaced is moved to the new table, in the same button. Its rows
aren't known until its query runs, so only its columns are checked. A chart is drawn from a table's top-level
rows, so the columns it maps are grouped at the same level (both at level 1, not one at 1 and the next at 2).
Columns for the common kinds:

- A day for a calendar or line: `floorDay(${EventTime})` formatted as Date Time. Text from `formatDate()` isn't
  read as a date.
- An hour and a weekday for a heatmap: `formatDate(${EventTime}, 'HH')` and `formatDate(${EventTime}, 'EEE')`
  as text (they are categories), both grouped at level 1, with a `count()`.

Asked which charts are supported, take the kinds from `list_visualisations` and sort them by the data. Use the
types of the columns in a snapshot of the rows, and the table's grouping. Give three groups:

- the kinds it can make now, with the columns each would map;
- the kinds it could make after a change to the table, naming the change;
- the kinds that don't suit it.

Keep it short, and offer the likeliest.

A chart over time should have a row per interval, not per event. The chart is sent at most its Max Rows rows; the
rest are left out, not summarised, and the chart says "First N rows". When `propose_visualisation` warns that a
time column is raw, offer a chart of a new table that aggregates it (`newTable`): grouped by a rounded time
(`floorMonth()`, `floorWeek()`, `floorDay()`, `floorHour()` or `floorMinute()`, as the warning suggests, formatted
as Date Time) with a `count()`, or the measure summed or averaged.

## Several charts at once

Asked for a set of charts, by some rule, of some value, possibly summarised first (a gauge per numeric column, a
line chart per metric, a pie per event type), propose them together: one `propose_visualisation` with `charts`
(up to 12, each with its own `kind`, `columns`, `name` and, if not the default, `table`, `style` or `options`).
They are offered with one "Create visualisations" button: the user picks one folder, and each is created there.

- Work out the items from the request and a snapshot of the rows: column types or names, or a column's distinct
  values. If the rule is vague, list them and ask before proposing.
- One chart per item, or one chart of them all when that reads better (e.g. a bar per column), saying why.
- For a summary (the maximum of each column), propose the table change first (an aggregate table or columns),
  then the charts on it. A gauge without a name column shows one value: the first row's.
- `layout` arranges them on the dashboard: `row` (side by side), `column` (one below another), `grid` (rows of up
  to three) or `tabs` (stacked as tabs in one pane). Ask the user unless they have said; the tool asks you to.
- A chart the data can't make is left out, and the result says why; the rest are still offered.

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
- Before proposing a chart on a dashboard, ask the user (`ask_user`, not a question in your answer) how its
  clicks should filter it: the whole dashboard (the query), one table, or nothing. Give their choice as
  `propose_visualisation`'s `filter` (as `add_selection_filter` takes it, without `chart`): the chart's button
  adds the filter with the chart. For a chart already on the dashboard, propose `add_selection_filter`.
- A network graph's node selects `node`: to filter by it at either end of a link, give a term for each end with
  `match` any. A link selects its `source` and `target`: add a term for each (the source's field with `source`,
  the target's with `target`), which are kept together, so a link filters by both its ends. An undirected graph
  or a chord diagram sends a link both ways round, so it matches in either direction; a Sankey diagram's flows,
  and a directed graph's links, match only their own direction. One filter with all four terms (`match` any)
  filters by a clicked node or a clicked link: there is no need for two.
- Several filters on one target match when any of them does.
- Ctrl-click (or Shift-click) selects several items, and the filter matches any of them.
- Dates are filtered by time, never by equality. A term giving a date field (or a table's date column) a date
  the chart selects (a time axis's x, a calendar's date, any Date Time column) keeps it within the selected
  time: the range dragged along a line chart's or scatter plot's time axis, or across a bar chart's bars of
  dates in time order (its range tool), or the time a clicked point stands for (its whole day, for `floorDay()` values; its hour, for `floorHour()`). E.g.
  `{"field": "EventTime", "selection": "x"}` (or `"range"`, the same) narrows the dashboard to a period. It
  becomes two terms (on or after the start, on or before the end); `between` isn't used, as its value would be a
  bare comma while nothing is selected. Say so as it is: the result reads "within its selected time".
- With nothing selected, the filter's terms drop out and the target shows everything.
- While something is selected, the chart shows a "Selected: …" bar with a button that clears the selection, even
  if the filter has left the chart with nothing to draw.
- The dashboard file lists each component's selection filters, each with its id, e.g. `[id ab12c]`. To remove
  one, use a `remove_selection_filter` change naming the `target` and the id as its `filter`. To change one,
  remove it and add the new one in the same proposal.

### What the user is looking at

A chart's render report (in the dashboard's description) also says what the user sees on it now, each left out
when there is none: `shown`, the part zoomed in on (e.g. `Day from 2026-10-14 00:00 to 2026-10-16 12:00`, or a
network graph `zoomed in 2x`); `selection`, what is clicked, as the chart labels it (e.g. `devans → phall · 3`);
`hiddenSeries`, the series hidden from the legend; and `interval`, the interval Auto Interval groups by. Use them
for "what's this spike" or "explain what I've selected": read the rows behind them from the chart's table
(`current_results`, filtered to the time shown or the item selected), as the report has no other values.

### When a chart shows nothing

Check these in turn:

1. The chart's render report: `status`, `rows`, `droppedRows`, `warnings` and `error`. `selected` true means
   something is clicked on the chart, so its selection is filtering whatever uses it.
2. The rows of the chart's table (`current_results`).
3. That table's filters, grouping and query.
4. The selection filters on the queries and tables that feed the chart.

A selection filter whose terms name the same chart's selection in one operator needs all of them to hold, even
under OR.

Say what is filtering the rows out, and offer the fix:

- clear the selection, with the ✕ on the chart's "Selected: …" bar;
- correct the filter or remove it;
- or remove every selection filter, in one proposal.

## Embedded queries

A dashboard's Embedded Query component runs StroomQL (shown in the dashboard's description) with its own results
table and chart. `current_results` snapshots its results by its name, as a table's.

- Chart it with `propose_visualisation` (library echarts or d3), naming it as the table: Stroom puts the chart's `show`
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
- Converting a chart to another kind (`replace`): the pane's own column and style settings are cleared for the new
  chart's, and the selection filters its clicks drive follow it where the new chart selects the same column under
  another key (e.g. a line chart's `x` becomes a bar chart's `category`). The result says what follows and what is
  lost: a filter on a selection the new kind doesn't make (e.g. a time range, once a line chart is a bar chart),
  a column it no longer shows, or a time axis. Tell the user before they click, and offer the fix it names.
- A conversion that can't work, or would mislead (e.g. a pie chart of event types as a time series, when the table
  has no Date Time column): don't force the columns to fit. Explain why, and recommend what would work: the kinds
  the table can make as it is (the tool lists them when a chart can't be made), or the table change that gives it
  what it lacks (e.g. a time field rounded with `floorHour()`, formatted Date Time and grouped, with
  `propose_dashboard_change`), then the conversion.
- The content pack's D3 Visualisations take only their own settings, by their own ids.
- A setting a chart's Visualisation doesn't have (one generated before the setting was added, e.g. `nodeColours`)
  can't be set on its pane: replace the chart (`replace`, the same kind) to regenerate it with every setting.
- A network graph of one row per message (or event) needs no new table to show magnitude: without a `weight`
  column each row counts once, so each link is as wide as its rows (e.g. a pair's messages), and `nodeSize`
  total sizes each node by its links' total (the messages a person sent and received). Map `weight` only for a
  column of counts or amounts (e.g. a grouped table's `count()`).
- A network graph's node colours: `nodeColours` group (by its `sourceGroup`/`targetGroup` columns) or node (each
  node its own colour). Its links are drawn in their source node's colour: don't override that (e.g. with a
  `lineStyle` colour in options), which leaves every link one colour.
- A chart replaced (`replace`) keeps the old chart's style settings that the new one has (e.g. `nodeColours`),
  unless you give others; the result says which were kept. Its layout animates by default; `list_visualisations` gives the `seriesDefaults.force`
  options that tune its physics.

## Notes

- A chart gets only its table's top-level rows: Stroom doesn't open a table's groups for it. Group every column
  the chart uses at the same level (level 1), e.g. sender and recipient both at level 1 for one row per pair; a
  column grouped at level 2 is blank in every row the chart gets.
- Dates must be in a column formatted as Date Time to go on a time axis; dates held as text are drawn as
  categories, or can't be placed at all on a scatter plot.
- Bar, line and scatter charts zoom with the mouse wheel and pan by dragging (a scatter plot on both axes), and a
  network graph zooms and pans the same way. The toolbar above the chart (under its title) has its controls:
  Select a time range (a time axis, or bars of dates; then drag across the chart), Clear the selection, Zoom in,
  Zoom out, Reset zoom, Pause layout (a network graph's, while it settles), Show all series (once some are hidden
  from the legend), Download (a PNG image, or the data drawn as CSV for those who may download search results),
  Ask Stroom AI (attaches the data the chart draws), Interval (with Auto Interval: Auto, or a fixed interval),
  what is selected, and the refresh button. Point the user to these rather than to settings. Charts have no title
  of their own: the dashboard shows the Visualisation pane's name above it, so name the pane well.
- A line chart over a Date Time x, or a bar chart of dates (not sorted by value), takes `autoInterval`: the chart
  groups the table's rows into an interval that suits the time shown, coarser zoomed out and finer zoomed in, and
  says which in its toolbar. It can't be finer than the table's own grouping, so group the table finely (e.g.
  `floorMinute()`) with a Max Rows that holds the span, and let the chart coarsen it; the toolbar's Interval can
  fix one instead. `intervalCombine` (Sum,
  Average, Min, Max) is how an interval's values are combined; it follows the measure column (count() and sum()
  Sum, max() Max) unless given. Offer it for a time series over a long or varying span.
- Bar, line and scatter charts take `units` (count, bytes, percent, ms, seconds) for how values read, `logScale`,
  a `threshold` line (with `thresholdLabel`), `breakOutliers` to cut the axis when a few values dwarf the rest, and
  `zoomSlider`; bar and line charts an `averageLine`, a scatter plot `jitter`. Pie, heatmap, calendar and histogram
  charts take `units` too. A network graph takes `physics` (settled, lively, spread) and a `thumbnail` overview.
  Both libraries draw every setting, and look and work alike; with D3 the options take only `color` (the
  others are ECharts' own).
- Charts follow the user's theme and their date and time preferences (format and time zone), and update when those
  change.
- A chart draws at most its Max Rows (5000 by default) of the table's rows, and a network graph at most its Max
  Nodes (300), the most connected first.
- ECharts is installed by an administrator, as a Script doc holding `echarts.min.js` from an Apache ECharts
  release, version 6 or later; an administrator upgrades it by putting a newer release's build in that Script doc.
  Stroom adds its own adapter beside it the first time a chart is created.
- D3 is installed the same way, as a Script doc holding a D3 build, version 3 or later (e.g. `d3.min.js` from D3
  7); the visualisations content pack includes D3 3. Where several are installed the newest is used.
