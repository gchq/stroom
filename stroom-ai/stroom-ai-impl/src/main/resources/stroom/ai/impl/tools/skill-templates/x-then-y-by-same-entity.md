# X then Y by the same entity

Finds where one entity (a user, a host) did one thing and then another within a window, across one or two
sources: "a device plugged in, then a file copied to it".

## When to use

- "which users did X and then Y within an hour?"
- "find hosts where X was followed by Y"
- "did anyone do X then Y in the past day?"

## Parameters

- days (default 1): how many days back from now.
- within (default 1h): the most time between the two, e.g. 5m or 1h.

## Steps

1. Run the sequence, the first step's events then the second's, by the same [[entity field]] ([[first index]]:
   [[first step's conditions]]; [[second index]]: [[second step's conditions]]):
   ```json
   {"tool": "sequence_query", "arguments": {"steps": [{"index": "[[first index]]", "where": [{"field": "[[first condition field]]", "value": "[[first condition value]]"}], "by": ["[[entity field]]"]}, {"index": "[[second index]]", "where": [{"field": "[[second condition field]]", "value": "[[second condition value]]"}], "by": ["[[entity field]]"]}], "maxspan": "{within}", "timeFrom": "now()-{days}d"}}
   ```
2. List each entity found with when it did each, from the sequence's results, with `table`.

## Output

A table in the chat: each entity, when it did the first and when the second; and how many there were.
