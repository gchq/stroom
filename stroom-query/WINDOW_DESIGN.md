# StroomQL Window Design

Snapshot of the design document, last updated 2026-11-19

## Summary

The `window` clause only gives sensible results when `advance` equals the window size, and even then it has boundary, alignment and error-handling faults. With a smaller `advance` it does not produce hopping windows at all: most rows carry one populated period and the rest are empty.

Seven issues are open against the feature. Reading the code on `7.14` (`dcba0c9167`) gives a cause, or a likely cause, for six of them; the seventh ([#4127](https://github.com/gchq/stroom/issues/4127)) has no description and is best read as the general complaint that this document explains. The code also has eleven faults that no issue covers.

The faults share four roots:

- **The model is wrong for `advance` smaller than the window.** Events are bucketed by window size and then copied forward by `advance`, so `advance` sets how many period columns exist, not where windows start.
- **Window columns bypass the normal column path.** The time column and the `periodN` columns are bolted on after `select`, so `having`, `show` and detections see them inconsistently.
- **Bad input is not caught at parse time.** A wrong time field, a null time or a zero `advance` fails per row at search time, or hangs.
- **There are no tests of the window maths.** Two integration tests cover one happy path (`1y advance 1y`).

Everything below comes from reading the code and the issues. Nothing was run, so each cause is marked as confirmed by the code or as likely.

The agreed direction is to replace the clause, not repair it: `window` will only bucket time, as true tumbling or hopping windows, and new `lag` and `lead` functions will read other windows. The "Agreed design" and "Decisions" sections hold the detail.

## The current clause

`window` exists so that one result row can hold the count for a time bucket beside the counts for earlier buckets, which lets a query or rule compare them, e.g. "today's logons are five times yesterday's".

It does this by projecting data forward: each event is counted in its own bucket and also copied into later buckets, tagged with how far it was moved. A row therefore holds everything needed for the comparison, which suits stores that are built up incrementally, such as table builder rules.

The syntax, as the parser accepts it today:

```
window <time field> by <window size> [advance <duration>] [using <function>]
```

- `advance` defaults to the window size.
- `using` defaults to `count()`. It was added for #4475 and is not in the editor help or the completion snippet.
- `advance` and `using` are not keywords. The parser looks for those words among plain string tokens.
- The clause must come after `where` and before `filter`, `group by`, `having` and `select`. `eval` may come before or after it.

The clause adds columns to the table:

- The time field, grouped at depth 0 and sorted ascending, added if `select` does not name it.
- `period0` … `periodN`, where `period0` is the value for the row's own window and `periodK` is the value for the window `K` advances earlier.

The editor help describes the intent as:

| Query | Help text |
| --- | --- |
| `window EventTime by 1y` | Counts for grouped rows per year plus the previous year |
| `window EventTime by 1y advance 1m` | Counts for each year-long period every month, including the previous 12 months |

The first description matches the code. The second does not: see [Root causes](#root-causes). The help example also uses `1m`, which is minutes; months are `1M`.

A worked example of the case that works, `window EventTime by 1d` with `group by UserId`:

| Event | Stored as | Tagged |
| --- | --- | --- |
| user5 at 3 Jan 09:14 | row (user5, 3 Jan 00:00) | period 0 |
|  | row (user5, 4 Jan 00:00) | period 1 |

So the row for 4 Jan holds `period0` = events on 4 Jan and `period1` = events on 3 Jan.

## How it works today

The feature is small: one parser method, one class that does the maths, one aggregate function and one hook in the data store. All of it is in `stroom-query`.

| Part | Where | What it does |
| --- | --- | --- |
| Parsing | `SearchRequestFactory.processWindow` | Reads the clause into a `HoppingWindow` and stores it on `TableSettings.window` |
| Model | `HoppingWindow`, `Window` (`stroom-query-api`) | Time field, window size, advance size and function, all as strings |
| Maths | `CompiledWindow` (`stroom-query-common`) | Builds the offsets, the period expressions and columns, and the per-row processor |
| Function | `Period` (`stroom-query-language`) | `period(n, fn)`: applies `fn` only to rows tagged with period `n` |
| Store | `LmdbDataStore.accept` | Passes every incoming row through the window processor before adding it |

At parse time:

1. `processWindow` reads the field, `by`, `advance` and `using`, and builds the `HoppingWindow`.
2. `CompiledWindow.create` builds a list of offsets: `0`, `advance`, `2 × advance` … up to and including the window size. That is `window ÷ advance + 1` offsets.
3. `addWindowFields` parses `period(i, <function>)` for each offset and puts it in the expression map as `period<i>`, so later clauses can name it.
4. At the end of `processSelect`, `addPeriodColumns` forces the time column to a plain `${field}` expression grouped at depth 0, adding it if absent, then adds a visible `period<i>` column per offset.

At search time, for every row that passes the value filter:

1. `OffsetWindowProcessor` reads the time field and finds the start of the row's window (see below).
2. For each offset `i` it copies the row, replaces the time with `window start + offset`, and adds it to the store tagged with period `i`.
3. `Period.Gen.set` lets only the matching `period<i>` column count that copy. Copies with the same group key and time merge into one row.

The window start is found in `adjustWithOffset` in two steps:

1. `SimpleDurationUtil.roundDown` truncates the time to the next unit up from the window's unit: minutes go to the hour, hours to the day, days to the month, weeks and months to the year, years to 1970. The number in the duration is ignored.
2. A loop then adds the window size until it reaches the event time, and keeps the last value before it.

The tag is held on `StoredValues.period` in memory while the row is added. All times are handled in UTC.

History: windowing was added in March 2023 (`c9757514e5`). In October 2024 (`cd048ece6f`, for #4475) `mask()` became `period()`, `WindowSupport` became `CompiledWindow` and `using` was added. Stack traces in the older issues name `WindowSupport`.

## Issues

All seven issues are open. Two are partly addressed by the October 2024 change, one is probably fixed by it, and four still stand.

| Issue | Raised | Title | Finding |
| --- | --- | --- | --- |
| [#3837](https://github.com/gchq/stroom/issues/3837) | Oct 2023 | Allow `window` to count other fields | Partly addressed by `using`; undocumented and unvalidated |
| [#4022](https://github.com/gchq/stroom/issues/4022) | Jan 2024 | NPE when querying with `window` | NPE now caught, cause remains; confirmed by the code |
| [#4025](https://github.com/gchq/stroom/issues/4025) | Jan 2024 | Lots of stacks in logs with the wrong field | Still stands; confirmed by the code |
| [#4127](https://github.com/gchq/stroom/issues/4127) | Feb 2024 | Does not appear to be working correctly | No description; explained by the root causes |
| [#4475](https://github.com/gchq/stroom/issues/4475) | Sep 2024 | Compare periods in `having` or `eval` | Partly addressed; order-dependent and untested |
| [#4512](https://github.com/gchq/stroom/issues/4512) | Oct 2024 | Can't use EventTime in vis of window query | Probably fixed as a side effect; needs a run |
| [#4520](https://github.com/gchq/stroom/issues/4520) | Oct 2024 | Detections have duplicate period values | Still stands; likely cause found |

### #3837 Window on something other than count

The request was to window a conditional sum, written as `window conditionalSum by 1d` after an `eval`. The October 2024 change added `using <function>` instead, e.g. `window EventTime by 1d using sum(Bytes)`.

- `using` is missing from the editor help, the snippet and the keyword list.
- The check that the token after `using` is a function is commented out, so any token is accepted and pasted into `period(i, <text>)` as a string.
- Naming an `eval`, which is what the issue asked for, is untested. A bare name is likely read as a string literal.
- A window has one function, so a row cannot hold both a count and a sum per period.

### #4022 NPE with window

The stack trace shows `val.toLong()` called on a null time value in the old `WindowSupport`. Today `CompiledWindow` behaves in two ways, neither good:

- If the value is a Java `null`, it throws `Unable to find time field for window offsets` from inside the row consumer, which fails the search partway through extraction.
- If the value is `ValNull` or a string that is not a date, `toLong()` returns null, the unboxing throws, and the catch block logs it and returns the original value. The row is then stored once per offset at its unmodified time, so it is counted in every period.

The cause is unchanged: nothing checks at parse time that the window field exists and is a date, and nothing decides what a row without a time should do.

### #4025 Stack traces for the wrong field

The catch block in `adjustWithOffset` logs at error with the full stack. It runs once per row per offset, so a window on a non-time field with 11 offsets logs 11 stacks for every event searched. This is the same fault as #4022 seen from the logs.

### #4127 Not working correctly

The issue has a title only. The most likely trigger is a window with `advance` smaller than the window size, which gives mostly empty period columns (root cause 1). The boundary and alignment faults below would also fit.

### #4475 Comparing periods

The request was `having ${period-0} > (${period-1} * 5)` or `eval Diff = ${period-0} - ${period-1}`. After the October 2024 change:

- The columns are `period0` and `period1`, registered in the expression map by the `window` clause.
- An `eval` can only see them if it comes after `window`. An `eval` before `window` that names `${period0}` does not resolve.
- `having` terms are field, condition, value. It cannot compare two columns or do arithmetic, so the comparison needs an `eval` first, then `having Diff > 0`.
- No test covers an `eval` over period columns, so whether this works end to end is unknown.

### #4512 EventTime in a visualisation

The query in the issue uses `mask()`, so it was written against the code before the October 2024 change. In today's code the time column is added at the end of `select`, before `show` builds its column map by name, so `x=EventTime` should resolve. This needs a run to confirm before the issue is closed.

### #4520 Duplicate period values in detections

The detection held `period0`, `period1`, `period1`, `period0`. The likely cause:

1. `addPeriodColumns` adds the period columns directly and never records them in `addedFields`.
2. A `having` that names a period column puts it on the `additionalFields` list.
3. After parsing, every additional field not in `addedFields` gets a hidden column, `__period0__`, with the display name `period0`.
4. `TableBuilderAnalyticExecutor` writes a detection value for every column by display name, hidden ones included.

The order in the issue fits a `having` that names `period1` before `period0`. The same would happen for a `having` on the window's time field. This is from reading the code, not a reproduction.

## Root causes

### 1. `advance` does not make hopping windows

A hopping window of 1 hour advancing by 30 minutes should give windows starting at 10:00, 10:30, 11:00 and so on, each an hour long. The code never builds those windows. It buckets each event by the window size, then copies it forward in steps of `advance`.

Take `window EventTime by 1h advance 30m` and three events at 10:10, 10:40 and 11:20. The offsets are 0, 30m and 1h, so there are three period columns:

| Row time | period0 | period1 | period2 | What a hopping window would count |
| --- | --- | --- | --- | --- |
| 10:00 | 2 |  |  | 10:00–11:00 = 2 |
| 10:30 |  | 2 |  | 10:30–11:30 = 2 |
| 11:00 | 1 |  | 2 | 11:00–12:00 = 1 |
| 11:30 |  | 1 |  | 11:30–12:30 = 0 |
| 12:00 |  |  | 1 | 12:00–13:00 = 0 |

The half-hour rows repeat the count of the hour before them in `period1` and have nothing in `period0`. No row counts 10:30–11:30 as a window. The value for 10:30 is right here only by chance.

So `advance` really means "how many columns", and only one setting is useful: `advance` equal to the window, which gives the current and previous window. There is also no way to ask for more than one previous window, such as today against each of the last seven days.

### 2. Window columns bypass the column path

The time column and period columns are added by `addPeriodColumns` after `select` has finished, outside `createColumn`. The consequences:

- They are not in `addedFields`, so `having` adds them again as hidden columns (#4520).
- The time column's expression, group and sort are overwritten, whatever `select` or `eval` said about that field.
- The period expressions exist only after the `window` clause, so `eval` order matters (#4475).
- A query with no `group by` becomes grouped by time anyway, while the StreamId and EventId columns are still added as if it were not grouped.

### 3. Input is checked per row, not at parse time

Nothing validates the clause beyond its token shapes:

- The time field is not checked against the data source, so a wrong or non-date field fails on every row (#4022, #4025).
- `advance` is not checked against the window size, for zero, or for a sensible ratio.
- A parse failure of a period expression in `addWindowFields` is logged and swallowed. `addPeriodColumns` then calls `toString()` on the missing expression and throws a `NullPointerException`.

### 4. The maths has no unit tests

There is no test class for `CompiledWindow` or `Period`, and no window case in the `SearchRequestFactory` tests. The only coverage is two cases in `TestTableBuilderAnalytics`, both `window EventTime by 1y advance 1y`. That is the one configuration where the faults in this document do not show.

## Other findings

Eleven faults turned up in the code that no issue covers. The first four give wrong results or take a node down; the rest are costs and rough edges.

| # | Fault | Effect |
| --- | --- | --- |
| 1 | A zero `advance` never ends the offset loop in `CompiledWindow.create` | `advance 0m` is a valid duration token. The loop adds offsets until memory runs out, at parse time |
| 2 | No limit on the number of periods | `by 1d advance 1s` makes 86,401 columns and stores every event 86,401 times |
| 3 | Windows restart at the next unit up, and the number is ignored when truncating | `by 7d` starts on the 1st, 8th, 15th, 22nd and 29th, so the last window of a month is 2 or 3 days. `by 45m` gives a 45 minute window then a 15 minute one. Projecting a short window forward lands between window starts |
| 4 | An event exactly on a window boundary goes to the earlier window | With `by 1d`, 5 Jan 00:00:00.000 counts as 4 Jan, yet 1 Jan 00:00:00.000 counts as 1 Jan. Times floored by an `eval` or by the source hit this every time |
| 5 | The window start is found by stepping from the truncated time, once per offset | `by 1y` steps from 1970, over 50 steps per copy. `by 1ms` steps up to 1,000 times. The start is the same for every offset but is recomputed for each |
| 6 | All window maths is in UTC | The query's time zone is ignored, so `by 1d` does not break at local midnight |
| 7 | The edges of the searched time range are treated as real zeros | The first window has no earlier data, so `period1` is empty and an "increase" rule can fire. Rows projected past the newest data have only earlier periods. A rule also compares a part-filled current window with a full previous one |
| 8 | Period columns are always added, all visible, with fixed names | A query cannot hide them, pick some of them or rename them |
| 9 | `HoppingWindow` built from JSON with missing sizes fails in the data store constructor | `SimpleDurationUtil.parse(null)` throws. The builder defaults (10m, 1m) do not apply to JSON, and `function` is missing from the property order |
| 10 | The editor help is wrong | `1m` is minutes, not months. The `advance` description does not match the behaviour. The snippet always inserts `advance`, steering users into the broken case |
| 11 | `SimpleDurationUtil.roundDown` has a bit-mask slip for nanoseconds | `& 1_000_000` where a multiply was meant. Shared utility; it only matters for `ns` windows |

Also untested: a window inside a bracketed query or beside a join, and a window with more than one `group by` level.

## Related open issues

No other open issue is about the `window` clause. A search of open issues in `gchq/stroom` on 19 November 2026, over titles, descriptions and comments, found the seven above and three that touch the same ground.

| Issue | Title | Why it matters here |
| --- | --- | --- |
| [#5917](https://github.com/gchq/stroom/issues/5917) | `floorTime` and `roundTime` give the wrong buckets for times before 1970 | Same kind of bucketing fault. `window` has its own version: a year window truncates to 1970 and only steps forward, so every earlier event lands in the 1970 window. A fix should share one tested rounding routine |
| [#4047](https://github.com/gchq/stroom/issues/4047) | Add period parsing and formatting functions to StroomQL | Uses "period" to mean a duration. That clashes with `period()` and `periodN`. The agreed design drops both names, which removes the clash |
| [#3829](https://github.com/gchq/stroom/issues/3829) | Table Builder Time Field missing? | A table builder rule demands `EventTime`. Rules are where `window` is most used, so its time-field check should give the same answer as the rule's |

The search used these terms: window, windowing, windowed, hopping, tumbling, sliding, `mask(`, `period(`, `period0`, `countPrevious`, previous period, time bucket, bucket, histogram, time series, threshold, baseline, anomaly, trend, timeframe, table builder and having. The other matches were about browser windows, Windows events or unrelated uses of the words.

## The point of windowing

Windowing exists to answer one kind of question: how does this time bucket compare with earlier ones, for each group? The feature is judged by whether a row can say so reliably enough to alert on.

The questions it should answer:

| Question | Example |
| --- | --- |
| Has it risen or fallen against the bucket before? | A user's logons today are five times yesterday's |
| Has it changed against the same point in an earlier cycle? | This hour against the same hour yesterday, or the same weekday last week |
| Is it out of line with a run of earlier buckets? | Today against the average of the last seven days |
| Is it new, or has it stopped? | A host with events today and none before; a feed that sent data yesterday and nothing today |
| How much happened in a moving span? | Failed logons in any 10 minutes, checked every minute |

These are two different needs that the current clause mixes together:

- **Look-back.** Put earlier buckets' values on the current bucket's row. The first four questions need only this.
- **Overlap.** Let buckets overlap so a burst that straddles a boundary is not split in two. Only the last question needs this.

What a correct feature has to guarantee:

1. Every event is counted in exactly the buckets it belongs to, with a stated rule for boundaries, alignment and time zone.
2. A bucket with no events is a zero, not a missing row, where a comparison needs it. "Stopped" cannot be detected otherwise.
3. A comparison says when it has no data to compare with, at the start of the searched range and for a bucket that is still filling.
4. It works per group, and where results are built up incrementally, as in table builder rules.
5. The values can be used in `eval`, `having`, `select` and `show` like any other column.

The current implementation meets 4 in part and none of the others.

## How other platforms do it

Every platform looked at keeps two things apart that StroomQL's `window` joins together: putting events into time buckets, and reading an earlier bucket's value. None has one clause that does both and adds columns by itself.

| Platform | Bucketing | Reading an earlier bucket | Overlapping windows | Empty buckets |
| --- | --- | --- | --- | --- |
| [Kusto](https://learn.microsoft.com/en-us/kusto/query/bin-function) | `summarize count() by bin(Timestamp, 1d), User`; `bin_at` takes an origin | [`prev(column, offset, default)`](https://learn.microsoft.com/en-us/kusto/query/prev-function) over sorted rows; or arrays from [`make-series`](https://learn.microsoft.com/en-us/kusto/query/make-series-operator) | Copy each record into every bucket it feeds, then summarise ([tutorial](https://learn.microsoft.com/en-us/kusto/query/tutorials/use-aggregation-functions)); [`sliding_window_counts`](https://learn.microsoft.com/en-us/kusto/query/sliding-window-counts-plugin) plugin | `summarize` leaves them out; `make-series` fills with `default=` |
| Splunk SPL (from memory, see note) | `bin _time span=1d` then `stats … by _time, user`; `timechart span=1d` | `streamstats current=f window=1 last(count) by user`; `timewrap 1d` makes a column per earlier period | `streamstats time_window=10m` | `timechart` fills them |
| [PostgreSQL](https://www.postgresql.org/docs/current/functions-window.html) | [`date_bin(stride, source, origin)`](https://www.postgresql.org/docs/current/functions-datetime.html) | `lag(value, offset, default) OVER (PARTITION BY … ORDER BY …)` | Frame `RANGE BETWEEN '7 days' PRECEDING AND CURRENT ROW` | Left out unless joined to a generated series |
| [ksqlDB](https://docs.ksqldb.io/en/latest/concepts/time-and-windows-in-ksqldb-queries/) | `WINDOW TUMBLING (SIZE 1 HOUR)` | None described | `WINDOW HOPPING (SIZE 30 SECONDS, ADVANCE BY 10 SECONDS)` | Not stated |
| [Flink SQL](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/sql/queries/window-tvf/) | `TUMBLE(TABLE t, DESCRIPTOR(time), size [, offset])` | Not read | `HOP(TABLE t, DESCRIPTOR(time), slide, size [, offset])`; `CUMULATE` | Not stated |
| [Azure Stream Analytics](https://learn.microsoft.com/en-us/azure/stream-analytics/stream-analytics-window-functions) | `TumblingWindow(second, 10)` | [`LAG(x) OVER (PARTITION BY k LIMIT DURATION(hour, 1))`](https://learn.microsoft.com/en-us/stream-analytics-query/lag-azure-stream-analytics) | `HoppingWindow(second, 10, 5)`; `SlidingWindow` | Not stated |
| [Elasticsearch](https://www.elastic.co/docs/reference/aggregations/search-aggregations-bucket-datehistogram-aggregation) | `date_histogram` with `calendar_interval` or `fixed_interval`, `time_zone`, `offset` | [`serial_diff`](https://www.elastic.co/docs/reference/aggregations/search-aggregations-pipeline-serialdiff-aggregation) with `lag: 7` subtracts the value 7 buckets ago | Moving functions over buckets | Buckets between first and last are returned; `gap_policy` for pipelines |
| [PromQL](https://prometheus.io/docs/prometheus/latest/querying/basics/) | Range selector `[5m]` at each step | `offset 1w` on a selector shifts older data to now | Ranges overlap whenever the range exceeds the step | No sample, no value |
| [Flux](https://docs.influxdata.com/flux/v0/stdlib/universe/aggregatewindow/) | `aggregateWindow(every:, period:, offset:, location:)` | [`timeShift(duration:)`](https://docs.influxdata.com/flux/v0/stdlib/universe/timeshift/) moves times so periods line up | `period` longer than `every` | `createEmpty: true` by default |
| [Sigma](https://sigmahq.io/docs/meta/correlations.html) | `timespan: 5m` with `group-by` | None; rules test one window only | Left to the backend | Not applicable |

Points that bear on the redesign:

- **Look-back is by rows or by time.** Kusto `prev`, SQL `lag` and Elastic `serial_diff` count rows or buckets, so a missing bucket silently shifts the comparison. PromQL `offset`, Flux `timeShift` and Splunk `timewrap` shift by time, which cannot go wrong that way. Stroom's forward projection is a time shift.
- **Kusto documents the same trick Stroom uses.** Its sliding-window tutorial bins each record, builds the list of buckets the record contributes to with `range`, and copies the record into each with `mv-expand` before summarising. The plugin requires the look-back to be a multiple of the bin.
- **Kusto's `prev` has no grouping.** The pages read document no partition argument; the examples filter to one series first. SQL and Azure Stream Analytics partition explicitly.
- **Hopping has a settled definition.** A size and a slide; an event belongs to every window that covers it. ksqlDB states the start is inclusive and the end exclusive. Stroom's `HoppingWindow` and `advance` use ksqlDB's words but not its meaning.
- **Alignment is explicit.** `bin` rounds to a multiple of the size; `bin_at`, `date_bin` and the `offset` parameters move the origin; Elasticsearch separates calendar intervals from fixed ones and takes a time zone, with UTC the default.
- **Empty buckets are a choice the query makes.** `make-series default=`, `createEmpty` and `gap_policy` all exist because the answer differs by use.
- **Names come from the query.** In every language but Splunk's `timewrap`, the author names the earlier value. None adds unasked columns.

Notes on the sources: Splunk's documentation site refused automated reads, so its row is from memory and should be checked before it is relied on. The Flink page was read through a summary, not verbatim. The Elasticsearch moving functions, the PostgreSQL empty-bucket cell and the PromQL overlap and empty cells are general knowledge, not from the pages read. Flink's `OVER` and `LAG` pages were not read.

## Options

Option B was chosen and then refined into the agreed design below: forward projection is kept, and the look-back moved out of the clause into `lag` and `lead`. The comparison of the three mechanisms is kept here as the record of why.

|  | A. Repair in place | B. Redefine the clause | C. Look back at read time |
| --- | --- | --- | --- |
| Idea | Fix the faults; allow only `advance` equal to the window | Window starts step by `advance`; earlier windows are projected onto each row | Store plain time buckets; fetch earlier buckets when rows are read |
| Hopping windows | No | Yes | Possible |
| More than one earlier window | No | Yes | Yes |
| Copies per event | 2 | (window ÷ advance) × (shifts used + 1), capped | 1 |
| Works with stores built incrementally | Yes | Yes | Needs each row to find its neighbours at read time |
| Size of change | Small, inside `CompiledWindow` and the parser | Medium; same classes, new maths and syntax | Large; changes the result path, `having` and rules |
| Existing queries | Same results where they were right | Break, as agreed; old forms are refused | Same |

Option A closes the bug issues but leaves `advance` as a word that only accepts one value. Option C is the cheapest at search time, but a row would no longer hold everything `having` and a rule need, which is what projection gives.

## Agreed design

`window` only buckets time, and `lag` and `lead` read other windows wherever an expression is allowed. Forward projection stays as the mechanism underneath. The old clause is removed, not kept alongside. Agreed 19 November 2026.

```
window <time field> by <size> [advance <slide>] [offset <duration>]

lag(<aggregate>, <n> [, <default>])
lead(<aggregate>, <n> [, <default>])
windowStart()
windowEnd()
```

An example:

```
from Events
window EventTime by 1d
eval today     = count()
eval yesterday = lag(count(), 1)
group by UserId
having today > yesterday * 5
select windowStart() as Day, UserId, today, yesterday
```

### The clause

- Without `advance` the windows are tumbling: each event is in one window.
- With `advance` they are hopping: a window starts every slide, and an event is in every window that covers it. The size must be a whole multiple of the slide.
- A window includes its start and excludes its end.
- Fixed sizes (seconds to hours) are whole multiples of the size from midnight in the zone's standard time, its time with no daylight saving. So an hour window breaks on the local hour in every zone, and a 24 hour window is always 24 hours, on local midnight in winter and an hour off in summer. Calendar sizes (days, weeks, months, years) follow the calendar in the query's time zone; weeks start on Monday. `offset` moves the start.
- Alignment uses the query's time zone, and UTC where a rule has none.
- Rows are grouped by window as well as by the query's `group by`. Nothing is added to the table: the query selects `windowStart()` or `windowEnd()` if it wants them. The time field keeps its meaning.

* `1d` is a calendar day, 23 or 25 hours long when the clocks change; `24h` is always 24 hours.
* An `advance` may be in a smaller unit where the units divide exactly: a year by months (`by 1y advance 1M`), a week by days or less (`by 1w advance 1d`), a day by hours or less. Months and years cannot advance by weeks, days or less.
* `7d` counts every seven days from 1 January 1970, so it starts on Thursdays; `1w` is the calendar week from Monday. `offset` moves either.
* A positive `offset` moves starts later: `by 1d offset 6h` runs 06:00 to 06:00. Negative values are accepted.
* The clause is allowed in a bracketed query and with an ordinary join. It is refused on a query with an event-to-event join.

### The functions

- `lag(agg, n)` is the aggregate for the window `n` sizes earlier; `lead(agg, n)` for the window `n` sizes later. Both are by time, per group.
- An earlier or later window with no events for the group gives the aggregate's empty value, 0 for a count, if it lies between the first and last windows that hold an event of any group.
- A window before the first window that holds an event, or after the last, gives null, or the default if one is given. The first and last are of all groups, and are used whether or not the query has a time range. A comparison with null is false, so a rule does not fire on the first window that has events. A scheduled rule searches back before its time range for the windows it reads with `lag`, and judges a row that reads a later window with `lead` once that window has ended.
- `having` takes expressions, so `having count() > lag(count(), 1) * 5` works without an `eval`.
- `lag` or `lead` without a `window` clause is refused.

### Mechanism

- The parser collects the distinct shifts the query uses. Each event is copied to each of its windows, and for each shift to `window start + shift × size`, tagged with the shift. `lead` is a negative shift.
- Copies per event are (size ÷ advance) × (distinct shifts + 1). A query needing more than the limit, default 100 and set by maxWindowCopiesPerEvent, is refused when parsed.
- A window field that is not a date field in the data source is refused when parsed. A row with no time is skipped, and the search reports one warning with the count.
- Queries show a window that is still filling. Rules judge a window only once it is closed, by the same clock-and-coverage rule as the views design. A rule using `lead` waits for the later window too.

### What is removed

| Removed | Replaced by |
| --- | --- |
| Automatic `period0`…`periodN` columns | `eval` over `lag` |
| `period(n, fn)` | `lag(fn, n)` |
| `using <function>` | The first argument of `lag` |
| The forced, automatically added time column | `windowStart()` |
| `advance` as the number of columns | `advance` as the slide |
| `HoppingWindow`, `CompiledWindow`, `Period` | New classes; the old forms are refused with a message that shows the new one |

### Stages

One new implementation on one branch, in staged commits, with no repairs to the old code.

1. **Window maths.** A class that assigns a time to its windows, with unit tests for size, slide, offset, time zone, calendar sizes, boundaries, daylight-saving changes and times before 1970. Written on 19 November 2026 as TimeWindows and reworked after a code review, with 44 passing tests; committed as e56bc1270b. A window is identified by a key on the wall clock, not by its start instant, so windows stay distinct and lag and lead stay exact when the clocks change.
2. **Parser.** The new clause and its validation; refusal of the old forms; the copy limit. Done on 19 November 2026 in commit 1b6af44cf2, together with stage 4 and the removal of the old classes. Code review fixes followed in commit 2c97b1a27f: the window is read before the other clauses, so `eval` may come before `window`; the result store checks the limits again for a table that did not come from StroomQL; and a streaming rule with a `window` clause is refused.
3. **`lag` and `lead`.** The functions, projection in the data store, and null against zero at the edges of the range. Done on 19 November 2026 in commits d58979609c and 07c1aaf7a7. The first parameter must be a plain aggregate such as `count()`, `sum()` or `max()`. Each row is copied to the window that reads it, and only the aggregate inside `lag` or `lead` counts the copy. The edges of the range are the first and last windows that hold a real event, across all groups: this is the data's own extent, used whether or not the query has a time range, and it differs from the first form of decision 8 only when the first or last window of a searched range is empty for everyone. The copy limit is the `maxWindowCopiesPerEvent` property, default 100, and counts the windows per event times one more than the number of different `lag` and `lead` distances. The warning for rows with no time gives a count when a search ends; a table builder rule, whose store never ends, warns once with no count.
4. **`windowStart()` and `windowEnd()`,** and grouping by window. Done in commit 1b6af44cf2. Each function carries its query's windows inside its own expression, so it works wherever the expression is compiled again. Rows are grouped by a hidden window key column, and a second hidden column gives time-keyed stores, such as a table builder rule's, the window start as the row's time.
5. **Expressions in `having`.** Committed as cedc749f1e. An expression on the left of a condition, as in `having count() - lag(count(), 1) > 5`, becomes a hidden column that the condition tests. A comparison with an expression on the right, as in `having count() > lag(count(), 1) * 2`, becomes one hidden column that is true or false. An expression can start with a bracket, as in `(count() - 1) > 3`, and can use the names that the select clause gives its columns. A name on its own on the right is a column if there is an eval or select column of that name, and text if not (commit 9117902aef). A row is kept only when the comparison is true, so a row with nothing to compare with is left out.
6. **Rules.** Closed windows only; detection values checked for duplicates. Committed as c8704798fa. A table builder rule reads rows up to the time of the last event it has read, less its time to wait for data. With a window clause it now reads a row only when the row's window has ended by that time, and for a row that uses lead, when the later window has ended too. This is the rule the views design calls coverage; the executor has no clock part, so the last window is judged only when a later event arrives. Hidden columns, such as the window key and the columns of having expressions, are no longer values of a detection, which was the cause of #4520.
7. **Removal and help.** Delete the old classes, rewrite the editor help and snippet, add the changelog entries. The old classes are deleted and the help rewritten in commit 1b6af44cf2; the help for `lag`, `lead` and expressions in `having` is written with their stages, and `period(` is refused with the `lag` form to use. The changelog entries are committed as ea409462dc.

Each issue is closed by a test: #4022 and #4025 in stage 2, #4127 in stages 1 and 3, #3837 in stage 3, #4512 in stage 4, #4475 in stage 5, #4520 in stage 6.

### After the stages

A code review of the whole branch on 19 November 2026 led to three more commits.

| Commit | What changed |
| --- | --- |
| dad6458316 | The range of windows could be kept for rows stored after a read began, which dropped the newest windows; it is now taken before the read. The hidden stream and event id columns are the linked event of a table builder detection, and stay as values of a scheduled query detection. A bracketed query no longer takes the window of the query it is in. A window that would start in an hour the clocks skip starts when the clocks resume. |
| ec99bc4097 | A scheduled query rule judges only the windows that lie wholly inside the time range of its query. The range ends at the time the rule is run for if the query gives no earlier end. A row that uses `lead` needs the later window to be whole, and a row that uses `lag` is left out if the earlier window is cut by the start of the range. |
| 7cd6a96902 | The first and last windows with data are noted as rows are stored. The store is read through to find them only when a store that holds rows is opened, after a delete that removed rows, and when rows arrive from another node. |

A second review, of those three commits, led to these changes, written on 19 November 2026:

- **Data retention and windows.** A table builder rule's store no longer deletes a window before the rule has judged it, nor while a later window that is still to be judged reads it with `lag`. Before, retention deleted by window start, so a window could go before it closed.
- **`lag` at the start of a scheduled rule's range.** A row is left out if the window it reads with `lag` is not wholly inside the range. A window before the range may have had events that were not searched, so the rule would fire on the default.
- **Time range of a scheduled rule.** The range only limits the windows if the data source applies it to the window's time field. If not, only the time the rule is run for limits them.
- **Commit race.** A read that begins while rows are being committed finds its windows by reading through the store, as it can't tell whether it sees those rows.
- **Two windows that start together.** When the clocks go forward two windows can share a start. A rule now reads neither until both are closed.
- **Counting rows.** Only top-level rows are read in full when counting, not every row at every depth.

A bare name on the right of a `having` comparison is read as a column if an eval or a select column has that name, and as text if not (commit 9117902aef).

### Follow-up (#6077)

A review of the merged work found that a scheduled rule using `lag` could never fire: with a range of `day() - 1d` to `day()`, the day before the range was not searched, so yesterday was left out on every run, and nothing said why. A rule run on a schedule now judges each window once, in the run whose time range holds the time the window is ready: the end of the window, or the end of the last window it reads with `lead`. The rule searches from the start of the earliest window it judges, less its furthest `lag`, so a range that does not line up with the windows, or that `lag` reads before, no longer drops windows. Runs whose ranges follow on from each other judge each window once. This needs a time range with a start, on the field of the window; without one, each run judges every window that has ended, as before.

The search back applies to the whole query, so a join or bracketed query is searched over the same longer range, and the rows of a window that `lag` reads join as they would in a query over that range. A `limit` in the query applies to the rows the rule judges, not to the store, so the rows searched only for `lag` do not take the place of judged rows.

### Known limits

- **Rule clock.** A table builder rule closes a window on the time of the last event it has read, less its time to wait for data. That time can move back when events arrive out of order, and there is no clock part, so the last window is judged only when a later event arrives. The clock-and-coverage rule comes with the views work.
- **Clustered search.** Each payload from another node starts with the first and last window that the sending store holds real rows for, so the store that merges them no longer reads through its rows after each payload (#6077). The span is of every row the sender has written so far, and a sender can split its rows over several payloads, so during a search a window can be noted just before its rows arrive. A store that is empty when opened does not read through its rows either. Store tests merge the rows of two nodes' stores into a third, for lag, lead and hopping windows, and get the rows that one store gives. A window clause has not been run on a real cluster.
- **`lag` default that starts with `window:`.** The windows of a query are passed to window functions as a hidden last parameter of that form. A value that the query gives, such as a default, is now refused with a message if it starts with that prefix, as in `'window:none'`. One case remains: a default typed in exactly the form Stroom writes, with the query's own windows, is taken as the hidden parameter and the function has no default.
- **Copy limit check.** The parser compiles the columns a second time to count `lag` and `lead` distances, and reports an error from it against the first word of the query.
- **Window key column.** Fixed after the second review above: the store now finds it by its compiled function, not the text of its expression.
- **Clocks going back.** For day or week windows with an advance or offset of less than an hour, an event in the repeated hour can fall in a window that a rule has already judged. The row is not judged again.
- **`is null`.** The tokeniser does not read `is null` in any clause, so it can't be used in `having`.
- **Double-quoted names.** Inside an expression a double-quoted name is text, as in `eval`; only a bare name is a column.
- **Build.** The full build passed on 19 November 2026, after the changes of the second review. The Docker image build has not been run.

## Coverage against Kusto

The agreed design covers Kusto's fixed-time-window cases and not the rest. It handles tumbling and hopping buckets, `lag` and `lead`, and moving aggregates; it does not handle sessions, running totals, row-ordered functions, gap-filled series or sequence matching.

Kusto has no named window types. "Tumbling", "hopping" and "session" do not appear as types on the pages read; those names come from ksqlDB, Flink and Azure Stream Analytics. Kusto builds each from functions over sorted rows.

| Kusto use | Kusto form | Design | How, or why not |
| --- | --- | --- | --- |
| Fixed buckets | `summarize … by bin(t, 1d)` | Yes | `window t by 1d` |
| Bucket origin and calendar starts | [`bin_at`](https://learn.microsoft.com/en-us/kusto/query/bin-at-function), [`startofweek`](https://learn.microsoft.com/en-us/kusto/query/startofweek-function), `startofmonth` | Yes | `offset`, and calendar sizes. The first day of the week needs a rule; Kusto uses Sunday |
| Earlier or later bucket's aggregate | `prev`, [`next`](https://learn.microsoft.com/en-us/kusto/query/next-function) on summarised rows | Yes | `lag(agg, n)`, `lead(agg, n)`. Per group and by time, which Kusto's are not |
| Overlapping windows | Copy-and-summarise tutorial; `sliding_window_counts` | Yes | `advance` |
| Moving sum, count, average or percentile | [`series_fir`](https://learn.microsoft.com/en-us/kusto/query/series-fir-function), [`rolling_percentile`](https://learn.microsoft.com/en-us/kusto/query/rolling-percentile-plugin) | Yes | A hopping window is one: `by 7d advance 1d` is a 7-day moving aggregate per day |
| Distinct count over a look-back | `sliding_window_counts`, [`active_users_count`](https://learn.microsoft.com/en-us/kusto/query/active-users-count-plugin) | Partly | A distinct-count aggregate in a hopping window. "Active in at least N periods" is not covered |
| Previous or next event, not bucket | `prev(col)` on raw sorted rows | No | `lag` here reads a bucket's aggregate. Time since a user's last event needs rows in order |
| Running total | [`row_cumsum(term, restart)`](https://learn.microsoft.com/en-us/kusto/query/row-cumsum-function) | No | Needs every earlier bucket, not a fixed number of them |
| Sessions | [`row_window_session`](https://learn.microsoft.com/en-us/kusto/query/row-window-session-function), [`session_count`](https://learn.microsoft.com/en-us/kusto/query/session-count-plugin) | No | A session's bounds depend on the data, so an event cannot be assigned to one by itself |
| Row number and rank | `row_number`, `row_rank_dense` | No | Row-ordered, not time-bucketed |
| Series with gaps filled, anomaly detection | `make-series default=`, `series_decompose_anomalies` | No | Empty buckets are not created and StroomQL has no array values |
| Sequence matching | [`scan`](https://learn.microsoft.com/en-us/kusto/query/scan-operator) | No | A state machine over ordered rows |

The split is by mechanism. Forward projection works whenever an event's windows can be worked out from the event alone. Everything marked "No" needs a group's rows read in time order with state carried between them, which is a second mechanism: Kusto's `serialize`, SQL's `OVER (PARTITION BY … ORDER BY …)`.

**Scope, agreed 19 November 2026:** this work does the projection half, and the ordered-rows half is designed with views. Ordered-row functions run over rows that are already aggregated and stored in time order, which is what a view is. Sessions and running totals in a rule also need state kept between runs, and the view's store is where that state would live.

Two things in the views design bear on the projection half and should be settled together with it:

- **Merge semantics.** `lag` and `lead` values are aggregates tagged by shift. They have to merge correctly when parts of a store are compacted.
- **When a window is closed.** The views design closes a window on clock and coverage before it notifies. "A bucket that is still filling" in this document should use the same rule, not a second one.

`lead` works the same way as `lag`, copying an event back to earlier windows. A row that uses it is not final until the later window has data, so a rule using `lead` reports late by that much.

A true sliding window, one that moves with each event as in Azure Stream Analytics, is also outside the design. A hopping window with a small `advance` approximates it at a cost in copies per event.

## Future work

Eight Kusto uses are outside this work. Three can be reached later on the projection mechanism; five need ordered rows and go to the views design. Each has a proposal to start from. None of the syntax below is agreed.

| # | Use | Kusto form | Mechanism | Proposal |
| --- | --- | --- | --- | --- |
| 1 | Compare with a run of earlier windows; anomaly against a baseline | `series_fir`, `series_decompose_anomalies` | Projection | `lagRange(agg, from, to)` gives the aggregate over windows `from` to `to` back, e.g. `lagRange(count(), 1, 7) / 7` for a seven-window average. Add `stddev` the same way for "more than three deviations above the baseline". No array type and no decomposition |
| 2 | Active in at least N of the last M periods | `active_users_count` | Projection | `lagCount(agg, from, to)` counts how many of those windows have a non-zero aggregate. `having lagCount(count(), 1, 7) >= 3` then says "seen on three of the last seven days" |
| 3 | Running total inside a fixed span | `row_cumsum` with a restart | Projection | A cumulative window type, as Flink's `CUMULATE`: `window t by 1M cumulate 1d` gives month-to-date per day. An event belongs to every step after it within the span, so it can be assigned from the event alone |
| 4 | Gap-filled series | `make-series default=` | Read time | `window t by 1d fill 0` creates the missing buckets for each group between the first and last bucket of the searched range when rows are read. Needed before "stopped" can be detected for a group with no later events at all |
| 5 | Previous or next event | `prev`, `next` on raw sorted rows | Ordered rows | Row functions `previous(expr, n, default)` and `next(expr, n, default)`, valid in a query that declares an order: `sequence by <time field>` after `group by`. Evaluated per group in time order over a view's rows |
| 6 | Unbounded running total, row number, rank | `row_cumsum`, `row_number`, `row_rank_dense` | Ordered rows | `runningSum(expr)`, `rowNumber()` and `rank()` under the same `sequence by` clause. State is carried per group and saved with the view so a rule can continue between runs |
| 7 | Sessions | `row_window_session`, `session_count` | Ordered rows | A session window type with Kusto's two limits: `window t session gap 5m max 1h`. The row's time becomes the session start. Count, duration and sessions-per-bucket follow from ordinary aggregates |
| 8 | True sliding window that moves with each event | Azure Stream Analytics `SlidingWindow`; no direct Kusto form | Ordered rows | A time frame on an aggregate under `sequence by`, e.g. `count() over 10m`. Until then a hopping window with a small `advance` approximates it |

Sequence matching (`scan`) is not proposed as a language feature. A two-step sequence is already covered by StroomQL's event-to-event join, `within 10m after`. Longer sequences can chain joins once a query may have more than one windowed join, which is a limit of the join work, not of windowing. A general state machine should wait for a concrete need.

Order of value, as a suggestion: 1 and 4 first, since baselines and "stopped" are the commonest rule needs after a plain comparison; then 7 with views; the rest on demand.

## Decisions

Fourteen decisions were made on 19 November 2026.

| # | Question | Decision |
| --- | --- | --- |
| 1 | Compatibility | May break. A larger change is accepted to make the feature correct |
| 2 | Scope | Projection half only: tumbling and hopping windows, `lag` and `lead`. Ordered-row features are designed with views |
| 3 | Look-back functions | `lag` and `lead` |
| 4 | Old syntax | Removed outright. Old forms are refused with a message that shows the new form |
| 5 | Word for the slide | `advance` |
| 6 | Time zone | The query's; UTC where a rule has none |
| 7 | Week start | Monday |
| 8 | Window before the first that holds an event, or after the last | Null, with an optional default argument. First agreed as the window outside the searched range; changed when implemented to the extent of the data, across all groups |
| 9 | Window still filling | Queries show it; rules wait until it is closed |
| 10 | Row with no usable time | Non-date field refused at parse; row skipped at search with one counted warning |
| 11 | Limit on copies per event | Configured, default 100; size must be a whole multiple of the slide |
| 12 | Window time as a column | `windowStart()` and `windowEnd()`; nothing added automatically |
| 13 | Expressions in `having` | In this work |
| 14 | Future items pulled in | None |

Delivery is one new implementation in staged commits.

Six further points were settled the same day. No design question is now open.

| # | Question | Decision |
| --- | --- | --- |
| 15 | `advance` on calendar sizes | Same unit only for weeks, months and years, e.g. `by 3M advance 1M`. Day and smaller sizes may mix units, e.g. `by 7d advance 1d` |
| 16 | Days across a clock change | `1d` is a calendar day of 23 or 25 hours; `24h` is always 24 hours |
| 17 | Sign of `offset` | Positive moves starts later, so `by 1d offset 6h` runs 06:00 to 06:00. Negative values are accepted |
| 18 | Query with no time range | The earliest window with data is the edge, so `lag` into the window before it gives null. A query with a time range now has the same edge, see decision 8 |
| 19 | With brackets and joins | Allowed in a bracketed query and with an ordinary join; refused with an event-to-event join until tested |
| 20 | Limit property | `maxWindowCopiesPerEvent`, beside the search result store limits; exact config class confirmed when implementing |

Three points were revised on 19 November 2026 after the code review of stage 1.

| # | Question | Decision |
| --- | --- | --- |
| 21 | `advance` units, replacing decision 15 | Allowed where the units divide exactly: years by months, weeks by days or less, days by hours or less. Months and years against weeks, days or less stay refused |
| 22 | `7d` against `1w` | Left as two rules: `7d` counts from 1 January 1970 (Thursdays), `1w` is the calendar week from Monday |
| 23 | Alignment of fixed sizes | Counted from midnight in the zone's standard time, not from the UTC epoch, so `1h` breaks on the local hour in half-hour zones |

## Sources

- Issues: [#3837](https://github.com/gchq/stroom/issues/3837), [#4022](https://github.com/gchq/stroom/issues/4022), [#4025](https://github.com/gchq/stroom/issues/4025), [#4127](https://github.com/gchq/stroom/issues/4127), [#4475](https://github.com/gchq/stroom/issues/4475), [#4512](https://github.com/gchq/stroom/issues/4512), [#4520](https://github.com/gchq/stroom/issues/4520), read 19 November 2026.
- Code on `7.14` at `dcba0c9167`: `SearchRequestFactory`, `CompiledWindow`, `HoppingWindow`, `Period`, `LmdbDataStore`, `SimpleDurationUtil`, `TokenType`, `Structures`, `VisualisationTokenConsumerImpl`, `TableBuilderAnalyticExecutor`, `TestTableBuilderAnalytics`.
- Commits `c9757514e5` (windowing added) and `cd048ece6f` (`period()` and `using`).
