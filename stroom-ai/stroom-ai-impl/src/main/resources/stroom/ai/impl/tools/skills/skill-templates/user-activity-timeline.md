# User activity timeline

Builds a timeline of one user's activity over a period from the site's sources, in time order: what they did on
each host or in each source (applications in focus where a source records it, a process from its start to its end,
a lock to its unlock, activity periods in sources such as web proxy logs), what they were working on, the gaps where
they were idle, and the events missing from the sources.

## When to use

- "build a timeline of jsmith's activity over the past 7 days"
- "what was this user doing last week?"
- "show me a user's activity, in order, across their hosts"
- "what was this user working on?"

## Parameters

- user (required): the user's account name, as the sources hold it.
- days (default 7): how many days back from now.
- sources (default ask): the indexes to read, by name and separated by commas; ask to ask the user which; all for
  every one they can use.
- user_field (default [[user field, or leave empty to find it in each index]]): the field holding the user in every
  source that has it.
- detail (default both): the level to draw: activities (what they were working on), detail (the spans by host or
  source), or both.

## Steps

1. Build the timeline from the sources, applying this skill's Event chains:
   ```json
   {"tool": "activity_timeline", "arguments": {"entity": "{user}", "timeFrom": "now()-{days}d", "timeTo": "now()", "sources": "{sources}", "userField": "{user_field}", "view": "{detail}", "skill": "User activity timeline"}}
   ```
   Events with no roles are classified from their Events XML (or the index's fields). A source that records window
   focus is given its roles instead, as an object in sources with its index, fields and roles:
   [[the source recording window focus, its fields and its focus role's conditions, or none]].
2. Draw each view the result gives with gantt_chart, using the arguments it gives for that view; where it says a view
   is too dense for one chart, draw one day or one activity.
3. Say what the data-quality flags found (unpaired starts and ends, idle starts with no end, a source silent while
   another shows the user active, clocks that disagree): events may be missing, so say so rather than read a gap as
   idleness.
4. Where the result lists borderline pairs, ask the user whether any is a real pair; where it offers to record what
   was learned in this skill's Event chains, ask the user, and if they agree record it as the result says.

## Event chains

What each source's events mean beyond the schema, learned by earlier runs and applied before the schema's pairs and
inference: a line each, a pair as source: pair start -> end by key, or a type's role as source: type is idle.

## Output

The timeline in the chat at the level asked for (the activities, each label marked as an inference with its
evidence; the spans by host or source), the active time by application or place, and the data-quality flags with
examples.
