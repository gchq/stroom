# Wave 37: UI audit wave 3, children attached

The third of the UI audit's waves ([../UI_AUDIT.md](../UI_AUDIT.md), theme T1 and decision D6);
written 29 October 2026, after wave 36.

## Done (29 October 2026)

- **The form.** A child's controller doesn't inject its view; it has `attach(Component)`, which
  takes the component its parent's template declares, makes it its widget, does what touches it
  and returns itself (as `CodeEditorController`, `DocSelectionController` and `TermEditorController`
  already did). Its parent's component declares the child and has a getter for it, and the parent's
  controller attaches: `expressionEditor = own(expressionEditor).attach(view.getExpressionEditor())`.
  The parent's slot (its `setXxx(IsWidget)` and the panel it filled) goes. Top-level screens and
  dialogs keep injecting their views (D6), and both forms live side by side until the later waves
  convert the rest. UI-GUIDE.md ("Smart parts attach") and COMPONENT_MODEL_DESIGN.md say so.
- **Converted:** `ExpressionTreeController`, `ExpressionEditorController`, `QueryHelpController` and
  `CurrentSelectionController`, the audit's most reused children (dashboard-query-01's expression
  parts, security-settings-03, data-pipeline-processing-01).
- **Their parents now declare them** (20): `ExpressionEditor` (its tree), `QueryFavourites`,
  `QueryHistory`, `QueryPane`, `Processors`, `DataRetentionPolicy`, `RuleSetSettings`,
  `ContentTemplateTab` (which showed and hid its expression by passing it or null, and now has
  `setExpressionVisible`), `ConditionalFormattingRuleEditor`, `ContentTemplateEdit`,
  `DataRetentionRuleEditor`, `RuleEditor`, `ExpressionDialog`, `AdvancedQuickFilter`,
  `SelectionHandlerEditor` (its expression and current selection), `ProcessorEdit`,
  `SolrIndexSettings`, `ViewSettings`, `ColumnFunctionEditor` and `QueryEdit` (their query help).
  Two show the child as a dialog and attach a component they make (`DashboardBodyController`'s
  current selection, `DataRetentionImpactController`'s filter).
- **Layout unchanged:** where the slot's panel carried classes (a position for the tree's absolute
  root, a form group's focus target, a flex item) it stays in the template, without a `ui:field`,
  with the child declared inside it; the two with no classes went.
- **Checked:** compile, checkstyle, the client tests (with `TestTemplateBindings`), the app's GWT
  compile, and the 361 stories of every parent's screens and the app shell, with the leak check:
  all pass, and no screenshot changed but two Pipeline Editor ones taken before their styles had
  loaded (with three second-attempt passes); its 30 stories passed at 4 workers with no retry and no
  change (ISSUES.md, "Screenshots without styles").
