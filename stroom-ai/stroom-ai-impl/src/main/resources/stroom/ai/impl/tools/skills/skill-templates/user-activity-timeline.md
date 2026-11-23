# User activity timeline

Builds a timeline of one user's activity over a period, from the site's endpoint and logon sources: their sessions
of active use of each application on each host, the actions in each, the gaps where they were idle, the events
missing from the sources, and what they were working on.

## When to use

- "build a timeline of jsmith's activity over the past 7 days"
- "what was this user doing last week?"
- "show me a user's activity, in order, across their hosts"
- "what was this user working on?"

## Parameters

- user (required): the user's account name, as the sources hold it.
- days (default 7): how many days back from now.

## Steps

1. Build the timeline from the endpoint source ([[endpoint index]]: what a window gaining focus, opening and closing
   look like there, and the actions that count, e.g. a clipboard copy or a file copy) and the logon source
   ([[logon index]]: what a lock or logoff and an unlock or logon look like there), with what the user was working on:
   ```json
   {"tool": "activity_timeline", "arguments": {"entity": "{user}", "timeFrom": "now()-{days}d", "timeTo": "now()", "characterise": true, "sources": [{"index": "[[endpoint index]]", "user": "[[endpoint user field]]", "host": "[[endpoint host field]]", "process": "[[process field]]", "title": "[[window title field]]", "object": "[[object field]]", "roles": {"focus": {"where": [{"field": "[[endpoint event field]]", "value": "[[focus value]]"}]}, "open": {"where": [{"field": "[[endpoint event field]]", "value": "[[open value]]"}]}, "close": {"where": [{"field": "[[endpoint event field]]", "value": "[[close value]]"}]}, "actions": [{"kind": "[[action name]]", "where": [{"field": "[[endpoint event field]]", "value": "[[action value]]"}]}]}}, {"index": "[[logon index]]", "user": "[[logon user field]]", "transform": ["strip_domain"], "host": "[[logon host field]]", "roles": {"idle_start": {"where": [{"field": "[[logon event field]]", "value": "[[lock value]]"}]}, "idle_end": {"where": [{"field": "[[logon event field]]", "value": "[[unlock value]]"}]}}}]}}
   ```
2. Draw the sessions as a timeline in the chat, a lane a host:
   ```json
   {"tool": "gantt_chart", "arguments": {"path": "/outputs/timelines/{user}/sessions.csv", "start": "start", "end": "end", "label": "process", "lane": "host", "title": "{user}'s activity"}}
   ```
3. Draw what they were working on, a section an activity:
   ```json
   {"tool": "gantt_chart", "arguments": {"path": "/outputs/timelines/{user}/activities.csv", "start": "start", "end": "end", "label": "process", "lane": "activity_label", "title": "What {user} was working on"}}
   ```
4. Say what the data-quality flags found (unpaired opens and closes, idle starts with no end, a source silent while
   another shows the user active, clocks that disagree): events may be missing, so say so rather than read a gap as
   idleness.

## Output

Two timelines in the chat (the sessions by host, and the activities), each activity's label marked as an
inference with its evidence, the active time by application, and the data-quality flags with examples.
