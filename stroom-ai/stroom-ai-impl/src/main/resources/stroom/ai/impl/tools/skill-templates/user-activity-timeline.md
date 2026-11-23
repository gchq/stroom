# User activity timeline

Builds a timeline of one user's activity over a period, from the site's endpoint sources: what they did, in time
order, on each host, with the gaps where they were idle.

## When to use

- "build a timeline of jsmith's activity over the past 7 days"
- "what was this user doing last week?"
- "show me a user's activity, in order, across their hosts"

## Parameters

- user (required): the user's account name, as the sources hold it.
- days (default 7): how many days back from now.

## Steps

1. Find the user's endpoint events in time order ([[endpoint index]]; the actions that count: [[endpoint role
   conditions, e.g. window focus, clipboard copy, file copy]]):
   ```json
   {"tool": "run_stroomql", "arguments": {"query": "from \"[[endpoint index]]\" where [[user field]] = '{user}' and [[time field]] > now() - {days}d select [[time field]], [[host field]], [[action field]], [[object field]] sort by [[time field]]", "out": "/outputs/skills/timeline-events.md"}}
   ```
2. Count the actions on each host, to see where the user worked:
   ```json
   {"tool": "table", "arguments": {"path": "/outputs/skills/timeline-events.md", "groupBy": ["[[host field]]"], "aggregates": [{"function": "count"}]}}
   ```
3. Read the events in time order with `table` and mark each gap of more than 30 minutes between them as idle.

## Output

A table in the chat, in time order: the time, host, action and object; with the idle gaps and the counts by host.
