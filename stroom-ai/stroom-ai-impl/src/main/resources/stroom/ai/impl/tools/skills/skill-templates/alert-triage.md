# Alert triage

Triages an alert about a person: how the day of the alert compares with the person's own same weekdays, what they did in the hours around it, and, apart, what was done to their account (a reset, MFA changed,
failed logons against it, access granted), so an analyst can weigh whether it is unusual for them or their account
was acted on. The comparison gives indicators with evidence, never a verdict on the alert or the person.

## When to use

- "triage this alert for a user: is it unusual for them?"
- "is this person's activity on the day of the alert out of the ordinary?"
- "what was this user doing around the time of the alert?"

## Parameters

- person (required): the person id, as the indexes' person field holds it.
- alert_day (required): the alert's day, yyyy-MM-dd, in the person's zone.
- timeline_from (required): two hours before the alert's time, ISO 8601.
- timeline_to (required): two hours after the alert's time, ISO 8601.
- baseline_start (default 8 weeks before alert_day): yyyy-MM-dd.
- logon_ids (default none): the person's accounts, for sources with no person field, separated by commas.

## Steps

1. Compare the alert's day with the same weekdays before it:
   ```json
   {"tool": "compare_activity", "arguments": {"person": "{person}", "logonIds": "{logon_ids}", "windowFrom": "{alert_day}", "windowTo": "{alert_day}", "baselineFrom": "{baseline_start}", "unit": "day", "sources": "[[the indexes the alert's activity would show in, by name]]", "breadthFields": "[[fields for breadth and new values: hosts, destinations, applications]]", "includeTargets": true}}
   ```
2. Build the timeline of the hours around the alert with activity_timeline: entity the person's logon id, timeFrom
   {timeline_from}, timeTo {timeline_to} and includeTargets true.
3. Say how the day compares (each finding as an indicator with its alternative explanation, or that nothing departed
   from the baseline), what the timeline shows around the alert, what was done to their account (the "done to them"
   findings, apart, worded as done to the person or their account, by which account, never as their own acts:
   someone else acting on an account is the other reading of an alert), and what can't be told (sources with gaps,
   or not compared).

## Output

A short triage note in the chat: the day against the person's baseline, the timeline around the alert,
what was done to their account, and the gaps; never a verdict, only what the evidence shows and its other
explanations.
