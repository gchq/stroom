# Activity comparison

Compares one person's recent activity with their own past, from the indexes' aggregates:
volume, hours, breadth and new values, with change points and drifts. A general starting point for "is this person
unusual"; the findings are indicators with evidence, never a conclusion about the person.

## When to use

- "is this person unusual compared with their team?"
- "has this user's activity changed recently?"
- "anything odd about this person this month?"

## Parameters

- person (required): the person id, as the indexes' person field holds it.
- window_days (default 30): how many days back from now to compare.
- baseline_days (default 210): how many days back from now the baseline starts (before the window).
- unit (default week): day, week or month.
- logon_ids (default none): the person's accounts, for sources with no person field, separated by commas.
- leave (default none): the person's leave, each yyyy-MM-dd..yyyy-MM-dd, from the question or the AGENTS doc.

## Steps

1. Compare the window with the person's baseline:
   ```json
   {"tool": "compare_activity", "arguments": {"person": "{person}", "logonIds": "{logon_ids}", "windowFrom": "now()-{window_days}d", "windowTo": "now()", "baselineFrom": "now()-{baseline_days}d", "unit": "{unit}", "sources": "[[the indexes to compare, by name, or leave out for every one with a person field]]", "breadthFields": "[[fields for breadth and new values, or leave out for the host, application and object fields found]]", "leave": "{leave}"}}
   ```
2. Where findings name days, build the first few days' timelines with activity_timeline (entity the person's logon id,
   timeFrom the day, timeTo the next day) only if the user asks what happened then.

## Output

The findings in the chat, largest first, each an indicator with its evidence and alternative explanation; a line a
month; the sources compared and left out; and the workspace files of the daily profile and monthly rollup, which
can be charted.
