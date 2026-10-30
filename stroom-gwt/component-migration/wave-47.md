# Wave 47: the client bug batch

The client-side bugs left in [../ISSUES.md](../ISSUES.md) after the audit's follow-ups (wave 46);
written 30 October 2026. Each was checked against the code first, fixed in its controller or
component, and given a story or JUnit test that fails without the fix. Each is marked fixed in
ISSUES.md, and each that master has has a changelog entry (13).

## Done (30 October 2026)

- **Read-only screens that could still be changed:** a floor map the user may only view (its
  layers, fact list, time list, groups, tracking and query), the pipeline structure, a
  visualisation's assets and a data generator's execution now follow `Contexts.READ_ONLY`,
  including when the document's lock changes while it is open.
- **Controls left enabled when the tick box that governs them is off:** a column format's date
  format and time zone (Use Preferences), a List Input's Dictionary picker (Use Dictionary), an
  analytic notification's Maximum and Resume After (Limit Notifications) and a Solr index's
  ZooKeeper fields (Use ZK). Removing a processor profile period just edited now removes it (the
  list keeps its selection by uuid).
- **Lost input:** Rename and the two document permission dialogs stay open when the save fails;
  rejecting the splash screen's terms shows them again.
- **Smaller:** the annotation chooser selects the first annotation found, the activity chooser
  highlights the current activity, the pathway constraints list heads its value column "Value",
  and a disabled or read-only link button no longer underlines on hover (a real-input check,
  `test-runner/lib/real-input/link-button.mjs`).
- NotificationListController's possibly stale selection is recorded in ISSUES.md, not changed.

## Checked

Compile, checkstyle and tests of the client modules, the app's GWT compile, and the full story run
with the leak check (1,755 stories, 165 checks): all pass. The changed screenshots are only the
fixes themselves (selections shown, disabled controls, the "Value" heading, the splash accepted)
and the new stories', and were taken. The link button check's hovers scrolled the ViewStates story
before its screenshot; the check now puts the scroll back, and the screenshot is as before.
TopBar `Keyboard`'s tooltip difference is the known one (ISSUES.md) and its baseline was left.
