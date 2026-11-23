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

activity_timeline builds one user's (or entity's) timeline for a period from several Elastic Index sources, each
read for that user alone as the streaming join reads a side, and worked out in code.

- Each source gives the fields that hold the user, host, application, window title, window, object and text, and
  its roles as conditions (as sequence_query's steps take them): `focus`, `open`, `close`, `idle_start` (lock,
  sleep, logoff), `idle_end` (unlock, wake, logon) and `actions`, each with its `kind`. Which conditions mean which
  role is the site's: a skill, an AGENTS doc or the source's documentation holds them.
- A session runs from a focus to the next focus, an idle start, or the idle cap (no event for 30 minutes by default,
  `idleCap`), marked uncertain then. Hosts are separate lanes. An action with no host is attached by time.
- Missing events are flagged with counts and examples (unpaired opens and closes, idle starts with no end, a source
  silent while another shows the user active, clocks that disagree), never smoothed over.
- Its sessions table is drawn by gantt_chart as it is. With `characterise`: activities, sessions tied by the
  documents, pages and terms they share (Leiden clusters), and loose items (messages, prompts) classified by a
  model outside the conversation, low confidence left unclear. Labels are inferences. Corrections (`move`,
  `merge`, `rename`) are kept with the chat, so a re-run keeps them.

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
