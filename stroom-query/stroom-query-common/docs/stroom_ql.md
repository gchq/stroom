# Query Format

```
from <DATA_SOURCE>
where <FIELD> <CONDITION> <VALUE> [and|or|not]
[and|or|not]
[window] <TIME_FIELD> by <WINDOW_SIZE> [advance <ADVANCE_WINDOW_SIZE>]
[filter] <FIELD> <CONDITION> <VALUE> [and|or|not]
[and|or|not]
[eval...] <FIELD> = <EXPRESSION>
[having] <FIELD> <CONDITION> <VALUE> [and|or|not]
[group by] <FIELD>
[sort by] <FIELD> [desc|asc] // asc by default
select <FIELD> [as <COLUMN NAME>], ...
[limit] <MAX_ROWS> 
```



# From Data source
The first part of a StroomQL expression is the data source to query. If it has a name that contains whitespace then it must be contained in quotes.

# Where
The where clause defines the start of a query.

# Filter
Add filters for values that are not indexed.

# Conditions
Supported conditions are:
=
!=
>
>=
<
<=
is null
is not null

# And|Or|Not
Logical operators to add to where and filter clauses.

# Bracket groups
You can force evaluation of items in a specific order using bracketed groups.
`and X = 5 OR (name = foo and surname = bar)`

# Having
A post aggregate filter that is applied at query time to return only rows that match the `having` conditions.

# Comments
## Single line
StroomQL supports single line comments using `//`.

## Multi line
Multiple lines can be commented by surrounding sections with `/*` and `*/`.

# Window
`window <TIME_FIELD> by <WINDOW_SIZE> [advance <ADVANCE_WINDOW_SIZE>]`
Windowing groups data by a specified window size applied to a time field.

A window inserts additional rows for future periods so that rows for future periods contain count columns for previous periods.

Specify the field to window by and a duration. Durations are specified in simple terms e.g. `1d`, `2w` etc.

By default, a window will insert a count into the next period row. This is because by default we advance by the specified window size. If you wish to advance by a different duration you can speficy the advance amount which will insert counts into multiple future rows.

# Examples
from "index_view" // view
// add a where
where EventTime > now() - 1227d
// and StreamId = 1210
eval UserId = any(upperCase(UserId))
eval FirstName = lowerCase(substringBefore(UserId, '.'))
eval FirstName = any(FirstName)
eval Sl = stringLength(FirstName)
eval count = count()
group by StreamId
sort by Sl desc
select Sl, StreamId as "Stream Id", EventId as "Event Id", EventTime as "Event Time", UserId as "User Id", FirstName, count
limit 10


/*
doc comment
*/

from "index_view" // view
// add a where
where EventTime > now() - 1227d
// and StreamId = 1210
eval UserId = any(upperCase(UserId))
eval FirstName = lowerCase(substringBefore(UserId, '.'))
eval FirstName = any(FirstName)
eval Sl = stringLength(FirstName)
eval count = count()
group by StreamId
sort by Sl desc
select Sl, StreamId as "Stream Id", EventId as "Event Id", EventTime as "Event Time", UserId as "User Id", FirstName, count
limit 10



/*
doc comment
*/

from "index_view" // view
// add a where
where EventTime > now() - 1227d
// and StreamId = 1210
eval UserId = any(upperCase(UserId))
eval FirstName = lowerCase(substringBefore(UserId, '.'))
eval FirstName = any(FirstName)
eval Sl = stringLength(FirstName)
// eval count = count()
// group by StreamId
// sort by Sl desc
select StreamId as "Stream Id", EventId as "Event Id"
// limit 10



/*
doc comment
*/

from "index_view" // view
// add a where
where EventTime > now() - 1227d
// and StreamId = 1210
eval UserId = any(upperCase(UserId))
eval FirstName = lowerCase(substringBefore(UserId, '.'))
eval FirstName = any(FirstName)
eval Sl = stringLength(FirstName)
eval count = count()
group by StreamId
sort by Sl desc
select Sl, StreamId as "Stream Id", EventId as "Event Id", EventTime as "Event Time", UserId as "User Id", FirstName, count
limit 10