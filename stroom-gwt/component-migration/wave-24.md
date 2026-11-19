# Wave 24: Input that can't be kept

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 28 October 2026, after wave 23. The fields that
quietly dropped, replaced or defaulted what was typed (ISSUES.md, "Values quietly replaced or
dropped", "Elastic number fields can fail when blank") and the durations that couldn't be 0 or none
("Some HTTP client durations can't be set to 0", "Duration fields can't be left blank"), done as the
user decided on 28 October 2026: bad input blocks OK and Save; durations allow 0 or none per field.

## Done (28 October 2026)

- **One rule for every dialog and document** (`stroom.widget.form.client`): a field that can hold
  input that can't be kept is a `HasInvalidInput` (`DateTimeBox`, and Stroom's `TextBox` given a check
  with `setInvalidReason`, e.g. `InputChecks.numberFrom(0, 1)`). `InvalidInputs` walks a dialog's or
  tab's widgets for the first such field shown, says why under it (`FormGroup.setInvalidReason`, which
  leaves a controller's own feedback alone) and focuses it. Every dialog's OK goes through it
  (`ShowPopupEvent.Builder.fire`), and so do a document's Save and Save As (`DocumentEditorHost`,
  `DocumentPlugin`). Content whose fields aren't all on the page (tabs) implements
  `RefusesInvalidInput` and shows the tab first (`TabbedController`, `ComponentSettingsDialogController`).
  A read-only component, a disabled or read-only field and a hidden one are never refused. Input a
  field can't keep makes its document dirty (`TabbedController.hasInvalidInput`, in the dirty rule's
  "associated" changes), though what is written keeps the value read: otherwise Save stayed disabled
  and closing the tab didn't ask, losing what was typed (found by the full run).
- **The fields:** the Elastic index's and a dense vector field's Minimum Rerank Score (a number from
  0 to 1; the index's threw as soon as the box was left), Plan B's Max Store Size and Max Spans Per
  Trace (no longer replaced by 10 GiB and 100,000), a table's Maximum Results (no longer dropping the
  parts that aren't numbers), and every date box (text that isn't a time was saved as none).
- **Durations:** `DurationPicker` takes `zeroAllowed` (the HTTP client's Timeout, Connection Timeout,
  Keep Alive and Validate After Inactivity Period, whose 0 means something) and `noneText` (Resume
  Notifications After's "Never", a table builder's "Keep all", both none to the server). A selection
  box's no-value item (e.g. "[ none ]") is now shown in the box when nothing is chosen, not an empty
  box. A new notification still resumes after a day, and a table builder whose retention is none now
  shows Keep all (before, 1 hour, which a save then stored).
- **Tests:** `TestInputChecks`; stories `PlanBEditor` `MaxStoreSizeNotASizeIsRefused`, `ElasticIndexEditor`
  `RerankScoreNotAScore`, `IndexEditor` `DenseVectorFieldBadScoreRefused`, `ProcessChoiceDialog`
  `CreateTimeNotATimeRefused`, `DashboardEditor` `TableMaxResultsRefused` (from another tab),
  `ElasticClusterEditor` `KeepAliveTakesZero`, `AnalyticRuleEditor` `ResumeAfterNever`.
- **UI-GUIDE.md** has the rule.
