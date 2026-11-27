# Joins: combining data from two places

Let Stroom match and count over all the rows; pairing rows by reading them only sees the rows you were given.

## StroomQL joins (run_stroomql)

Join lines go straight after `from`, one per joined source. A joined source's fields are named `<alias>.<field>`
(the alias defaults to the source's name); the first source's stay as they are. Either side may be a bracketed
query, e.g. a grouped summary: `join (from "Logons" group by UserId select UserId, count() as N) as l on ...`.

| Kind | Also written | Rows out |
| --- | --- | --- |
| `join` | `inner join` | One per match; first-source rows with no match dropped |
| `left join` | `left outer join` | One per match; unmatched first-source rows kept, joined fields blank |
| `right join` | `right outer join` | One per match, plus joined rows with no match (first-source fields blank) |
| `full join` | `full outer join` | One per match, plus unmatched rows of both sides |
| `semi join` | `left semi join`, `where exists (from …)`, `where X in (from …)` | Each first-source row with a match, once; no joined fields |
| `anti join` | `left anti join`, `where not exists (from …)`, `where X not in (from …)` | Each first-source row with no match |

```text
from "Staff activity"
join "HR" as hr on UserId = hr.Username
where Action = Export
group by hr.Department
select hr.Department, sum(Files) as Files
```

```text
from "Staff activity"
where Action = Logon and UserId in (from "HR" where Status = Leaver select Username)
group by UserId
select UserId, count() as Logons
```

```text
from "Staff activity" as a
where exists (from "HR" as hr where hr.Username = a.UserId and hr.Status = Leaver)
select UserId, EventTime
```

A windowed join pairs events: each joined row whose time is within the window of a first-source row with the
same key.

```text
from "Staff activity" as logon
join "Staff activity" as export on logon.UserId = export.UserId within 5m after logon.EventTime
where logon.Action = Logon and export.Action = Export
select logon.UserId, logon.EventTime, export.EventTime
```

### Keys

- One or more `a = b` pairs joined by `and`; either side may be an expression, e.g.
  `on lowerCase(UserId) = hr.Username`, or the text before `@`: `on substringBefore(Email, '@') = hr.Username`.
- Keys compare exactly, as text, and case-sensitively. A blank or null key never matches, `not in` included.
- A later join line's keys may use an earlier join's fields (user to department to cost centre).
- In `where exists`, the keys are `=` comparisons that name the first source's fields by its alias or name
  (`a.UserId`); its other conditions filter the joined source.

### Limits

- The joined side (the first source in a windowed join) is held in a join table of at most 1,000,000 rows or
  256 MB; over that the query fails. Filter or group that side to keep it small.
- A join reads every row of both sides within those limits: a query's row limit (e.g. run_stroomql's 10,000)
  cuts only the rows the query returns, after the join and any group by.
- A join whose table has at most 1,000 keys is also pushed down to the first source's search as an `in` term
  (`anti join` only on ID and whole-number fields), so the first source reads only matching events.
- `where` may name any side's fields: top-level `and` terms on the first source's fields are pushed down to its
  index, except with a right or full join; the rest run on the joined rows.
- A right or full join must be the last join line and can't have a time window.
- A windowed join is the only join line in its query, neither side may be a bracketed query, and the joined
  source needs a time field: a Data Connection has none, so it can't be the joined side of a window.
- A bracketed query can't hold join lines, an alias of its own, or `where exists` / `in (from …)`.
- `where exists`, `where not exists` and `in (from …)` go at the top level of `where`, joined by `and`, not
  beside `or`.
- One first-source row matching many joined rows gives a row per match; Stroom warns above 1,000 for one row.
- StreamId and EventId come from the first source, so a joined row still opens its first-source event.
- Classic dashboard queries (the expression tree) can't join; a StroomQL query, an Embedded Query, a scheduled
  query or a report can. Streaming analytic rules refuse joins.

## Sequences: "X, then Y, within N" (sequence_query)

sequence_query finds ordered events by the same entity within a time span, in one data source or across several.

- On Elastic Index docs on one cluster with the same time field, it runs one EQL sequence in Elasticsearch: 2 or
  more steps, `until`, `missing` (X and then not Y) and `runs` (N in a row).
- On any other data source (a Lucene index, a view), it runs a StroomQL windowed join in Stroom: 2 steps joined by
  their `by` fields, step 2 with a condition, and no `until`, `missing` or `runs`. A first step with too many
  events for the join table is refused before it runs.
- On Elastic Index docs it can also run as a streaming join in Stroom (`engine=stream`; chosen by itself where
  EQL can't: a first step too big for EQL, docs with different time fields, `keysOnly` or `transform`): each step
  read in join-key order and merged, so no side is capped at 10,000. A `keysOnly` step only says which entities
  count, at any time (a team's users in a directory index; with `missing`, those not in it). `transform`
  (`lowercase`, `strip_domain`, `file_name`) bridges join values sources write differently (`CORP\jsmith` and
  `jsmith`). Its count is exact; sizes are estimated first, and a join near its caps is asked about.
- A transform is computed by Elasticsearch for every event (about twice the time of the same join without one in
  the benchmark); a field indexed already normalised joins at full speed.
- `maxspan` is at most 24h. Each step is counted first, and a sequence too big for EQL's time limit runs as the
  streaming join, or with `engine=eql` is refused unless `runAnyway` is true.
- Its result says which engine ran, and gives a StroomQL review query. A streaming join that finds nothing says
  why: each step's own count, and values of each side when they share none.

## A user's activity over time (activity_timeline)

activity_timeline builds one user's (or entity's) timeline for a period from any Elastic Index sources, each read
for that user alone as the streaming join reads a side, and worked out in code.

- Sources: given (index names will do), `all`, or omitted: the user is asked in a form (with a dashboard attached,
  its data source, all supported, or their own words matched to indexes and confirmed); over MCP with no form, every
  Elastic Index the user can use, those without the user's events listed and left out.
- Each index's user field: the call's (`userField`, a source's `user`), else the AGENTS doc's `## Fields`
  (`- user: ...`), else a field convention's (`user.name`, `UserId`), else the index's documentation, else asked.
  The result says each index's field and where it came from. The user and time always come from the index.
- Each index is read once (one scan of the user's events in the period); its fields are found once (the call's,
  AGENTS `## Fields`, the feeding pipelines' index plans, the index documentation's From column, conventions).
  Roles are decided once per event type: the call's or skill's, the catalogue of the stream's translation, AGENTS
  `## Event types` (`- <type>: <role>`), the schema's element and Action where indexed (Authenticate Logon,
  ScreenUnlock, Connect and Reconnect active; Logoff, ScreenLock and Disconnect idle; Process Execute and Terminate
  open and close; other elements actions of that kind), else the source's own codes classified once. Events XML is
  read only for what the index can't give, and the result names the paths to add to the index plan. Window focus
  has no schema element: roles (`focus`, `open`, `close`, `idle_start`, `idle_end`, `actions`) give it.
- A period whose events pass the cap gives a day-by-day overview instead, to pick days from.
- Spans: a focus session runs from a focus to the next focus, an idle start, or the idle cap (30 minutes by
  default, `idleCap`). A pair (`Process/Execute` to `Process/Terminate` by ProcessId, a lock to its unlock, a skill's
  Event chains, a source's `pairs`, or two types inferred from the user's own events: alternating for 90% of keys,
  counts about equal) runs from a start to its end of the same key. Actions in neither, close in time, are activity
  periods by host (else source) and application, else the object's place (a URL's domain), split where it changes:
  they show when the user was active there, not what had focus. Borderline pairs are listed, not used.
- Missing events are flagged with counts and examples (unpaired starts and ends, idle starts with no end, a source
  silent while another shows the user active, clocks that disagree), never smoothed over.
- Two levels, three views (`view`): `detail` (spans.csv, lanes by host or source), `activities` (activities.csv, a
  lane a day or an activity; turns `characterise` on), `both`. The result gives gantt_chart's arguments for each and
  says when one is too dense for one chart. With `characterise`: activities, spans tied by the documents, pages and
  terms they share (Leiden clusters), and loose items classified by a model outside the conversation. Labels are
  inferences. Corrections (`move`, `merge`, `rename`) are kept with the chat.
- On a dashboard: a dashboard can't read the chat's workspace, so propose_timeline_dashboard writes spans.csv or
  activities.csv, as it is, as a CSV Data Connection (typed, `start` its time field, a dated snapshot named for the
  entity and period) with its timeline chart, in a folder the user chooses, then a dashboard of them. It holds the
  user's activity with the folder's permissions; a re-run for the same entity replaces it.
- With `skill`, the skill's Event chains are applied first, and what was learned (pairs inferred, the source's own
  `pairs` and `typeRoles`) is offered for them through propose_skill where the user may edit the skills doc.

## The table tool's join

For data StroomQL can't join: attached tables, a tab's results saved by current_results, esql_query or search
results saved as tables, and .csv or .tsv files. `join` takes the other table's `path`, `on` (columns whose values
must be the same, as exact text; an empty cell matches nothing), `rightOn` if the other table names them
differently, `keep` (`both`, the default, or `unmatched` for this table's rows nothing matches) and `within`
(`column`, `seconds`, and `after` true when the other table's time must be at or after this one's). At most
1,000,000 joined rows are held.

## What the rows are

- A query's rows are cut at its row limit (run_stroomql adds one, by default 10,000, when it has none), and esql_query's at
  its `limit`. Joining rows fetched by two calls joins samples, not the data.
- An exact term on an ID field (`UserId = jbloggs`, `in (…)`) is pushed down to the index and is fast. A wildcard
  on a name (`UserId = *bloggs*`) is slow and matches values you didn't mean.
