# Wave 9: Near-dumb small dialogs

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 24 October 2026 from a review of every unit in the wave against the code after master (with `data_grid2` and `copy_and_paste`) was merged in. Line numbers are as of then. Each unit is done when it meets "Done means, for every unit".

> **Since wave 7 (26 October 2026):** Plan B's settings widgets are components with `setOnChange`, and its type settings editors `PlanBTypeSettingsEditor`s (`stroom.planb.client.component`); the secret forms are `SecretForm`s (`stroom.credentials.client.component`; the key store's is a `KeyStoreSecretFormController`, which uploads); the floor map's Set Scale, Timeline Settings, group edit, layer appearance (wave 11's `FloorMapLayerStylePresenter` is done too), dock, Layers panel and canvas are components in `stroom.floormap.client.component`, with `FloorMapSetScaleController`, `FloorMapTimelineSettingsController`, `FloorMapGroupEditController`, `FloorMapLayerStyleController`, `FloorMapDockController` (`addTab(String, IsWidget)`), `FloorMapCanvasController`, and `FloorMapLayers` with no controller; the pipeline's tree is `PipelineTree` and the stepping screen's bar `PipelinePathBar` (attach a `PipelinePathBarController` for its menus), both on `AbstractPipelineTree`; the pathway tree is `PathwayTree` with `PathwayTreeController`; the expression tree is `ExpressionTree` with `ExpressionTreeController` (`init(dataSource, fields)`, `setOnChange`, `setOnSearch`), each `TermEditor` with a `TermEditorController`, and both `EditExpressionPresenter`s are one `ExpressionEditor` with `ExpressionEditorController` (`stroom.query.client.component`; `setFramed(false)` where the owner frames it); `ControllerDialog.show(controller)` shows a controller made once, again and again, through one host; and a `DataGrid` given one of GWT's selection models selects on a press only with a `SingleSelectionEventManager`. The plans below keep the old names. See `wave-07.md`.

> **Since wave 8 (26 October 2026):** the explorer chooser is `ExplorerPopup` with `ExplorerPopupController` (`show(consumer)` to reuse one, `showOnce(opener, ...)` for one use; `DocSelectionPopup` is gone, its `setIncludeNullSelection` public), `DocSelectionController` is in `stroom.explorer.client.component` (`DocSelectionBoxPresenter` stays as the wrapper for callers not yet converted), and the document folder tree is `EntityTree` with `EntityTreeController` (the export tick tree an `ExplorerTree` with `EntityCheckTreeController`); a `DocTabPresenter`'s tab can show a controller through `ControllerTabProvider` (`new ControllerTabProvider<>(getEventBus(), provider::get, Foo::read, Foo::write)`; `isPresenter(content)` asks whether a shown presenter is the tab's without making it); the Documentation tab is `MarkdownEditController` (`stroom.entity.client.component`, with `MarkdownPreviewController`), and `MarkdownTabProvider` takes the event bus and a `Supplier` (`new MarkdownTabProvider<>(getEventBus(), markdownEditProvider::get) { onRead…; onWrite… }`, an abstract class whose `onRead`/`onWrite` are given the controller; in a `DocumentEditor` tab call `followReadOnly()`); the settings tabs (data generator, visualisation, XML schema, text converter, SQL temporal store, feed, pathways, Elastic index, index, analytic rule, report, floor map event store; the error feed and its Set Default are `ErrorFeedController`), the preferences' Theme, Editor and Date and Time tabs, the Ask Stroom AI config tabs, `DenseVectorField`, `CredentialSettings`, `TableBuilderProcessing`, `QueryToolbar`, `DataTextPane` (the data viewer's `TextPresenter`; its editor options are `getOptions()`), `QueryTextPane` (the query result's `TextPresenter`), `ColumnFilterEditor` (`edit(opener, column, onChange)`), `AnalyticEmailDestination`, `CommentEdit` (`edit(opener, text, onOk)`), `WordsTab`, `XSDBrowser`, `QueryHelp` (`linkToEditor` kept), `ContentStoreContentPackDetails` (`setOnInstalled`) and `FloorMapTimeline` are components with a `...Controller` beside each; `AriaPanel` has `ariaValueMin`, `ariaValueMax` and `ariaValueNow`; and the size modifiers `text-field--narrow`/`--wide` and `value-spinner--narrow`/`--wide` replace widths in templates. The plans below keep the old names. See `wave-08.md`.

Collapse each small dialog (presenter, view interface and ViewImpl) into one component dialog. Each gets a show or edit(..., callback) method and is shown with ComponentDialog.showOnce. Validation returns a message, and the opener raises the alert. Callers stop reading getView() getters.

## Audited (26 October 2026)

Six reviewers read the wave's commits against this plan, "Done means" and AGENTS.md (the About and
name dialogs; the dashboard's dialogs and the four of `48db2e72e4`; the asset, Git Push and info
dialogs; the alert, splash and step controls; the period, retention, XPath, download, feed dependency,
pipeline reference and constraint dialogs; and the last six units), then the app and the standalone
dashboard were GWT compiled. Fixed:

- **Two regressions on this branch.**
  - The app and the dashboard didn't GWT compile: `CurrentUser` was given the `SplashScreen` helper,
    which needs the UI config, which needs the security context `CurrentUser` is part of, a cycle
    GIN refuses. It now takes a `Provider<SplashScreen>`. The workbench's ginjectors have no such
    cycle, so its stories passed; the GWT compile is what found it.
  - The pathway Constraint dialog trimmed String, Regex and String Set values (`ConstraintValueText`
    trimmed every type's text); they are kept as typed again, numbers and ranges still read without
    spaces (`TestConstraintValueText.testTextKeptAsTyped`).
- **Two bugs master has too**, each with a changelog entry and a story:
  - Git Push accepted a commit message of only spaces (`GitRepoEditor.PushBlankMessageAndCancel`).
  - Edit Property lost a changed value when its Source was chosen again, and showed the changed value
    under another Source: a value change showed Local but didn't make the dialog's source Local
    (`PipelineEditor.PropertySourceAfterChange`).
- **Stroom's widgets only.** Every component template that still declared GWT's `TextBox` or
  `TextArea` (29, from this wave and earlier ones, the pilot's `FieldEditor` and `RuleEditor` among
  them) declares Stroom's `TextInput` or `TextAreaInput`, as do `NameEntry`, the asset dialogs and Git
  Push; `TextAreaInput` is listed beside `TextInput` in AGENTS.md and the design.
- **BEM.** The alert's classes are `common-alert`, `common-alert__icon`, `__message-area`,
  `__message`, `__show-hide`, `__show-hide-arrow`, `__detail` and `__detail-text`, in
  `common-alert.css` (was `alert.css`; the suite's alert helpers follow); the keyboard shortcut
  recorder's are `key-shortcut-recorder__keys` and `__message`, in `key-shortcut-recorder.css` (they
  were in `components.css`).
- **Owned work and replies.** The step controls' stories own their controllers (through a harness);
  Create and Edit User Group's REST calls have `.owner(this)`; New Node Group ignores a name check
  that comes back after the dialog is cancelled (`NodeGroupClient` takes no owner; a create that has
  been sent still adds the group); an alert with no level shows as an error rather than throwing,
  which would have stopped the alert queue.
- **Smaller.** Edit User Group refuses a blank name as Create does; Edit Property fetches its stream
  types or volume groups once shown, with the dialog's task monitor; the Prompt story uses Stroom's
  `AlertPlugin`; `LayoutConstraint` and `StepControl` use their widgets' callbacks; unused
  `AddEventLink.setText`, a duplicate focus on attach (`FavouriteName`, `QueryInfoDialog`) and empty
  module comments and blank lines went; `NameEntry`'s identity is `nameEntryName`; javadoc for
  `QueryInfo` and `StepControlController.setEnabledButtons`; stale names in ISSUES.md,
  WRITING-STORIES.md, COMPONENTS.md and a few stories.
- **Tests.** JUnit for `AddEventLinkController.parse`, `QueryInfo.isRequired` and
  `NewPropertyController.editorFor`; stories for what had none: the new groups' Cancel (data volume,
  index volume, node), Link An Event's refusal, Set Retention Period, Rename Field's and Rename Tab's
  Cancel, a favourite with no name, New File's OK and Cancel, Rename Selected Asset, the query info
  dialog (`App/Main/QueryInfoDialog`: Default, Cancel, NotValid), XPath filter Edit and Cancel, an
  invalid period time and Download Options' sample.

Found and recorded (ISSUES.md): a pipeline property's default value on its selected row fails colour
contrast.

## Stroom's widgets (26 October 2026)

Wherever Stroom has its own version of one of GWT's widgets, Stroom's is used, and checked:
the 59 templates and 70 classes left with GWT's `TextBox` or `TextArea` (presenter views, widgets'
insides, stories) have `TextInput` and `TextAreaInput`; the explorer's logo and Current Activity
button are Stroom's `Button`, with its new `setContent(SafeHtml)` for content of its own; the login's
"Forgot password?" is a `LinkButton` (a `Hyperlink` added a history entry); the stories' native list
and radio buttons are a `SelectionBox` and a `RadioGroup`. Checkstyle refuses GWT's versions' imports
and `new TextBox(`/`new TextArea(` (`GwtWidgetWithStroomVersion`, `GwtTextBoxWithStroomVersion`),
and `TestStroomWidgetsOnly` their tags in templates (AGENTS.md, "Stroom's widgets only"). A bug master
has too, fixed on the way (changelog, `TestActivitySummary`): the Current Activity summary showed an
activity's property names and values as HTML; `ActivitySummary` escapes them.

Then (26 October 2026) `TextInput` and `TextAreaInput` were renamed `TextBox` and `TextArea` (still in
`stroom.widget.form.client`), to match `PasswordBox`, `SelectionBox` and the other boxes; they extend
Stroom's copies of GWT's `TextBox` and `TextArea`, which only they (and `PasswordBox`, its copy of
`PasswordTextBox`) name, in full, as checkstyle now refuses importing GWT's. The rest of this file
keeps the names of the time. `MaxScrollPanel` (in GWT's package, sized with inline styles) became
`ScrollPane` (`stroom.widget.layout.client`, `scroll-pane.css`), and `FocusFlowPanel` the curved tab
layout's own `CurveTabLayoutPanel`.

## Done (26 October 2026)

All 42 units, in 17 commits, the audit's and the Stroom widgets' (below): the last full run passed all 1,709 stories with the leak check; of its 46 screenshots that differed, 40 are the force graphs and 4 other recorded noise (the Markdown preview's font, the Tree visualisation's `ShowAndHide`, the data grid's spinner column, the line chart's `PageKeys`), and the 2 changed on purpose are the view states table (no native list; a radio group) and the form group's several controls (a selection box). The app and the standalone dashboard GWT compile, checkstyle is clean and the client modules' 1,908 JUnit tests pass. Each commit ran its stories with the leak check
and the screenshot diff. Six bugs fixed on the way, each with a changelog entry and a story or test
(master has each): a refused new asset name always said "Item name not set"; Edit Element didn't show
the element's own description (a typed and cancelled one was shown again, and OK could give it to
another element); a conditional format's custom style with only one theme's colours threw; and the
pathway Constraint dialog's New threw, a renamed constraint was kept under its old name too, an
integer or long range couldn't be saved unchanged and a value that couldn't be read closed the
dialog. What was decided on the way, where it differs from the plans below:

- **One use, made by the caller.** Every dialog's controller is made for one use and shown with
  `ControllerDialog.showOnce(opener, this)` from a method that takes its result's callback (`show`,
  `edit`, `rename`); callers take a GIN `Provider` in place of an owned presenter or `makeDialog`.
  Plugins and app-wide helpers (About, Dependencies, the alert queue, the splash and query info
  helpers, `DocumentDialogScreens`) pass `AppRoot` as the opener. Two stay made once and shown again
  with `ControllerDialog.show`, as they keep what was chosen: Download Options (one per table) and
  Current Selection (one per dashboard). Making each dialog afresh fixed the stale state of the shared
  ones (Edit Element's description; the Format dialog's settings, already fixed and now structural).
- **The alert queue is `AlertPlugin`'s.** `CommonAlertController` shows one alert or confirmation;
  `AlertPlugin` queues them, in the same order, running each callback once its dialog is hidden, and
  the prompt is the browser's (`Window.prompt`), so `PromptPresenter` went.
- **Helpers make dialogs only when shown.** `QueryInfo` (its `isRequired` is public and static) and
  `SplashScreen` check the activity and the config first and make the dialog only if it is needed.
- **Slots kept where the embedded controller takes its content by injection.** `ExpressionDialog`
  (`setExpressionEditor`) and `IndexFieldEditor` (`setDenseVectorOptions`): `ExpressionEditorController`
  and `DenseVectorFieldController` are made by GIN with their own components, with no `attach`.
  The planned `<d:EditExpression/>` and `<ix:DenseVectorField/>` declarations wait for those to attach.
- **Names.** `NameDocumentPresenter` and the volume group name dialogs are one `NameEntry` with a
  controller each (`RenameDocumentController`, `CreateNewGroupController`, `NewNodeGroupController`,
  `NewFsVolumeGroupController`, `NewIndexVolumeGroupController`); `FormatPresenter` is `ColumnFormat`,
  `DurationPresenter` `RetentionPeriod`, `DownloadPresenter` `DownloadSearchResults`,
  `ExpressionPresenter` `ExpressionDialog` (one for the six filter dialogs), `NewPropertyPresenter`
  `NewProperty` (with `NewProperty.ValueEditor`), `ConstraintEditPresenter` `ConstraintEditor` (its
  text conversion in `ConstraintValueText`).
- **Components show their own settings.** `ColumnFormat` shows the chosen type's settings itself, so
  it has no `setOnTypeChange`; `NewProperty` declares every value editor and shows the one for the
  property's type (`showValueEditor`), each named Value, as the form group's label is only for the one
  shown when it opens.
- **A text area input.** `TextAreaInput` (`stroom.widget.form.client`: `placeholder`, `ariaLabel`)
  joins `TextInput`, for the Dependency Information and query info text, then (in the audit) every
  component's text area.
- **Left, on purpose.** The old About rules (`.aboutViewImpl-*`) stay in `components.css` until
  removals are allowed; the copyright year of a moved file is its original's; `NodeGroupClient` still
  takes no owner; a column rename's "name in use" has no story (the stories' table has one column).

## CSS and template notes for the wave

- w-100 (stroom.css:843) replaces every Java setWidth(100, PCT) in this wave: FeedDependencyEditor (400), NewPipelineReference (695) and NewProperty (696) put it on their declared DropDownBox, SelectionBox, ValueSpinner and TextBox.
- The 11 Java setMarginBottom(0, PX) calls (400, 695, 696) do nothing: .drop-down (drop-down.css:27), .selection-box (selection-box.css:29), .value-spinner and .text-field (text-field.css:36, margin: 0) have no bottom margin. Drop them and confirm the screenshots match.
- New value-spinner--wide { width: 200px; } in value-spinner.css. .value-spinner is 96px (value-spinner.css:29) and 17 spinners in templates set width="200px" (ProcessorLimits here; BasicTableSettingsViewImpl.ui.xml:39,49, BasicEmbeddedQuerySettingsViewImpl.ui.xml:46 and others). 39 templates set width="200px" on some widget, so later waves need the same kind of modifier for buttons and text boxes.
- New dialog__button-cell { width: 45%; } in dialog.css, for the two button cells in ExecutionScheduleRunNow.ui.xml (516). BatchExecutionScheduleEditViewImpl.ui.xml:76,79 has the same cells.
- CustomRowStyleEditor (454): add width: 200px to .cf-light-example/.cf-dark-example (conditional-formatting.css:465,469, used only there). Add .cf-colour-swatch--custom using --cf-swatch__background-color and --cf-swatch__color for the user's colours. The grid's SafeHtml swatches (ConditionalFormattingSwatchUtil) stay.
- ARIA and placeholders that can't go in a template yet: Stroom's TextBox and TextArea copies (stroom-core-client-widget/src/main/java/com/google/gwt/user/client/ui, protected, needs human approval) have no accessibleName or placeholder setter. This affects DependenciesInfo (347), KeyShortcutRecorder (416, placeholder "Press keys") and QueryInfoDialog (547). ValueSpinner (Stroom's own, wave 2) needs setAccessibleName for ColumnFormat's Hours and Minutes (445) and NewProperty's Value (696).
- Template attribute limits: ValueSpinner.setValue is overloaded (Integer, Long), so default values stay in Java. min, max, minStep, maxStep and enabled can be attributes. TickBox value="true" and Button icon="OK" (an SvgImage enum) can too.
- Where an ARIA attribute goes on something that isn't a widget property (aria-live on KeyShortcutRecorder's message; role="region", aria-label and tabindex on CommonAlert's detail), use a plain <div ui:field> element inside a g:HTMLPanel.
- info-layout (components.css:58) is shared by DependenciesInfo (347) and InfoDocument (768). DependenciesInfo sets it with setStyleName on a TextArea, which drops text-field. The text field rule is addStyleNames, so compare screenshots.
- Class names with no CSS rule, which can be dropped when the templates move: stepping-buttons (StepControlViewImpl.ui.xml:4), XPathFilterViewImpl (XPathFilterViewImpl.ui.xml:7), visualisation-assets-add-dialog (both document asset templates).
- Class names keyed on the old ViewImpl, used only by their unit (aboutViewImpl-* components.css:519-570, splashViewImpl-content :987, keyShortcutRecorder-* :1075-1080), could become BEM names of the new component (about__logo, splash__content). This is optional: a rename in components.css plus the template.
- ProfilePeriodEditor's root borrows credentials-details-dialog (components.css:2021), and the comment there says so. Keep it, or give the period its own class.
- Shared component across units: NameEntry (from unit 770's NameDocumentView/Impl) is the content of the New Node Group, Create Group, New Data Volume Group and New Index Volume Group dialogs (205, 238, 577, 628). Each has only a controller. SelectionSummaryController (306) likewise shows CommonAlert (497). Convert 770 and 497 first in this wave.

## `CommonAlertPresenter`

dialog, presenter; complexity 2, effort S; used by 4.

- **Component:** `stroom.alert.client.component.CommonAlert`
- **Controller:** `stroom.alert.client.component.CommonAlertController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/alert/client/component/CommonAlert.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/alert/client/presenter/CommonAlertPresenter.java (inner CommonAlertView, :144-155)`; `stroom-core-client/src/main/java/stroom/alert/client/view/CommonAlertViewImpl.java`; `AlertModule.java:35 bindPresenterWidget(CommonAlertPresenter.class, CommonAlertView.class, CommonAlertViewImpl.class)`; stroom-gwt-workbench .../gin/processing/ProcessingExtrasScreenModule.java:43-45, query/AlertScreenModule.java:39-41, editors/AlertScreenModule.java:33-35, content/ContentExtrasScreenModule.java:36-38
- **Names:** No clash. The alert queue (CommonAlertPresenter.java:42-142) goes to CommonAlertController, made by AlertPlugin. SelectionSummaryController (unit 306) reuses the CommonAlert component, so convert this first in the wave.

| Into the template | What | Detail |
| --- | --- | --- |
| CommonAlertViewImpl.java:48-52,57-61,79-80 | structure | The layout built with new FlowPanel/SimplePanel/HTML/LinkButton: root alert-layout, icon, message area holding message, Show Detail button and detail |
| CommonAlertViewImpl.java:58,63,64,65,67,78 | css-class | alert-message-area, alert-icon, alert-message, alert-showHide, alert-detail, alert-layout (all in alert.css) |
| CommonAlertViewImpl.java:45-46,66 | text | <b:LinkButton ui:field="showHideDetail" addStyleNames="alert-showHide" visible="false"><span class="alert-showHide-arrow" aria-hidden="true"></span>Show Detail</b:LinkButton> (ButtonBase takes HTML content) |
| CommonAlertViewImpl.java:74-76 | aria | The detail as a plain element in a g:HTMLPanel message area: <div ui:field="detail" class="alert-detail" role="region" aria-label="Detail" tabindex="0" hidden=""/> |
| CommonAlertViewImpl.java:82-83 | structure | Show Detail and the detail start hidden (visible="false" / hidden) |

**Stays in Java:** aria-controls with the detail's generated id (CommonAlertViewImpl.java:69-71); aria-expanded and the detail's visibility (:166-171): state; The icon per level, SvgImage QUESTION/INFO/WARNING/ERROR (:118-143): state; Message and detail HTML (data), the long-message move to detail and the stack trace check (:140-164); The dialog's alertdialog role and description once attached (:87-91, PopupUtil).

## `PromptPresenter`

dialog, presenter; complexity 1, effort S; used by 4.

- **Component:** `none (folded into stroom.alert.client.AlertPlugin, which calls Window.prompt)`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because there is no widget: it is the browser's Window.prompt; PromptViewImpl.asWidget returns null)`
- **Goes:** `stroom-core-client/src/main/java/stroom/alert/client/presenter/PromptPresenter.java (inner PromptView, :41-44)`; `stroom-core-client/src/main/java/stroom/alert/client/view/PromptViewImpl.java`; `AlertModule.java:34 bindPresenter(PromptPresenter.class, PromptView.class, PromptViewImpl.class)`; `stroom-gwt-workbench .../gin/query/AlertScreenModule.java:36-38`; `AlertPlugin.java:44 handler and its Provider<PromptPresenter> become event.getCallback().onResult(Window.prompt(...))`
- **Names:** Prompt/PromptController are not made: there is no UI to split. A real prompt dialog component would be new work, not this unit.

**Stays in Java:** The Window.prompt call (message and initial value from the event).

## `CurrentSelectionPresenter`

composite, presenter; complexity 1, effort S; used by 4.

- **Component:** `stroom.dashboard.client.component.CurrentSelection`
- **Controller:** `stroom.dashboard.client.component.CurrentSelectionController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/query/CurrentSelectionViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/CurrentSelection.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/query/CurrentSelectionPresenter.java (inner CurrentSelectionView, :55-58)`; `stroom-core-client/src/main/java/stroom/dashboard/client/query/CurrentSelectionViewImpl.java`; QueryModule.java:71-74 bindPresenterWidget(CurrentSelectionPresenter.class, CurrentSelectionPresenter.CurrentSelectionView.class, CurrentSelectionViewImpl.class); `stroom-gwt-workbench .../gin/query/DashboardQueryScreenModule.java:84-86`
- **Names:** No clash. The view exposes its HTML widget (getHtml); the component gets setHtml(SafeHtml) and setOnInsert(Consumer<String>) (the copy/insert click, Presenter:42-43). MaxScrollPanel and HTML are GWT/Stroom panels, not earlier-wave components.

**Stays in Java:** The dashboard context HTML (DashboardContext.toSafeHtml): data.

## `AboutPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.about.client.component.About`
- **Controller:** `stroom.about.client.component.AboutController`
- **Template:** `stroom-core-client/src/main/resources/stroom/about/client/view/AboutViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/about/client/component/About.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/about/client/presenter/AboutPresenter.java (and its inner AboutView interface, :89-100)`; `stroom-core-client/src/main/java/stroom/about/client/view/AboutViewImpl.java`; `AboutModule.java:31 bindPresenter(AboutPresenter.class, AboutView.class, AboutViewImpl.class)`; `stroom-gwt-workbench .../gin/shell/ShellAppScreenModule.java:85-87 bindPresenterWidget(AboutPresenter...)`; `AboutPlugin.java:38,41 Provider<AboutPresenter> becomes Provider<AboutController> (caller moves in the same commit)`
- **Names:** About and AboutController are free (all_simple_names.json, other waves). The REST call fired in the constructor (AboutPresenter.java:54) moves to the controller's show(). Optional: the CSS classes named after the ViewImpl (aboutViewImpl-layout/-logo/-body/-buildInfo, components.css:519-570) could become BEM about__* as only this unit uses them.

| Into the template | What | Detail |
| --- | --- | --- |
| AboutPresenter.java:63 | text | "Build Version: " as a fixed <span> in the template beside a value <span ui:field="buildVersion"/> (the Label at AboutViewImpl.ui.xml:10) |
| AboutPresenter.java:64 | text | "Build Date: " fixed span beside <span ui:field="buildDate"/> |
| AboutPresenter.java:66 | text | "Up Date: " fixed span beside <span ui:field="upDate"/> |
| AboutPresenter.java:68 | text | "Node Name: " fixed span beside <span ui:field="nodeName"/> |
| AboutViewImpl.java:52 | structure | The fixed logo (SvgImage.LOGO put in the logo SimplePanel's inner HTML). No widget takes an SvgImage as a template attribute today (only ToolbarButton's svg=); with a small dumb icon widget (e.g. a new SvgImagePanel with svg="LOGO", shared with CommonAlert's icon) it moves to the template |

**Stays in Java:** The configured About HTML (UiConfig aboutHtml, AboutViewImpl.java:62 setInnerHTML): data, via setAboutHtml; The build version, build and up dates and node name values (data from SessionInfoResource, formatted with DateTimeFormatter); The logo, until a template-able SVG widget exists.

## `DependenciesInfoPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.importexport.client.component.DependenciesInfo`
- **Controller:** `stroom.importexport.client.component.DependenciesInfoController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/importexport/client/component/DependenciesInfo.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/importexport/client/presenter/DependenciesInfoPresenter.java (inner DependenciesInfoView, :67-69)`; `stroom-core-client/src/main/java/stroom/importexport/client/presenter/DependenciesInfoViewImpl.java`; `ImportExportConfigModule.java:62-65 bindPresenterWidget(DependenciesInfoPresenter...)`; `stroom-gwt-workbench .../gin/content/ImportExportScreenModule.java:53-55`; `DependenciesPlugin.java:66 handler calls the controller instead`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| DependenciesInfoViewImpl.java:33 | structure | <g:TextArea ui:field="info" .../> as the template's root, in place of new TextArea() |
| DependenciesInfoViewImpl.java:34 | structure | readOnly="true" attribute |
| DependenciesInfoViewImpl.java:35 | css-class | info-layout (components.css:58). setStyleName replaced the text-field class today; the text field rule is addStyleNames="info-layout", which changes the look, so compare screenshots (or keep styleName and record it) |
| DependenciesInfoViewImpl.java:37 | aria | Name "Dependency Information": needs an accessibleName setter on Stroom's TextArea copy (stroom-core-client-widget/src/main/java/com/google/gwt/user/client/ui/TextArea.java, protected directory: human approval), then accessibleName="Dependency Information" |

**Stays in Java:** The info text built from the DocRef (DependenciesInfoPresenter.java:53-62); AccessibleName.set until the TextArea copy has a setter.

## `QueryInfoPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.dashboard.client.component.QueryInfoDialog`
- **Controller:** `stroom.dashboard.client.component.QueryInfoDialogController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/query/QueryInfoViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryInfoDialog.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/dashboard/client/query/QueryInfoPresenter.java (inner QueryInfoView :121-126; its inner State class :128-145 becomes a record at the bottom of the controller); `stroom-core-client/src/main/java/stroom/dashboard/client/query/QueryInfoViewImpl.java`; `QueryModule.java:57 bindPresenterWidget(QueryInfoPresenter.class, QueryInfoView.class, QueryInfoViewImpl.class)`; `stroom-gwt-workbench .../gin/query/DashboardQueryScreenModule.java:66-68`
- **Names:** QueryInfo clashes with this unit's own helper stroom.dashboard.client.query.QueryInfo (AppRoot pattern), which stays and makes QueryInfoDialogController with appRoot.makeDialog. Named QueryInfoDialog (free).

| Into the template | What | Detail |
| --- | --- | --- |
| QueryInfoViewImpl.java:44 | aria | accessibleName="Query Info" on the TextArea: needs a setter on Stroom's TextArea copy (protected com/google/gwt/user/client/ui directory: human approval) |

**Stays in Java:** The query info value; the caption, enabled flag and validation regex from UiConfig (QueryInfoPresenter.java:71-74): controller; AccessibleName.set until the TextArea copy has a setter.

## `InfoDocumentPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.entity.client.component.InfoDocument`
- **Controller:** `stroom.entity.client.component.InfoDocumentController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/entity/client/component/InfoDocument.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/entity/client/presenter/InfoDocumentPresenter.java (inner InfoDocumentView :154-157; commented-out code :89-96,103,105,112); `stroom-core-client/src/main/java/stroom/entity/client/view/InfoDocumentViewImpl.java`; `EntityModule.java:57-60 bindPresenter(InfoDocumentPresenter.class, ..., InfoDocumentViewImpl.class)`; `stroom-gwt-workbench .../gin/EntityScreenModule.java:60-62`; `DocumentDialogScreens.java:57 handler calls the controller`
- **Names:** No clash (kept after ShowInfoDocumentDialogEvent; DocumentInfo would also be free).

| Into the template | What | Detail |
| --- | --- | --- |
| InfoDocumentViewImpl.java:35-40 | structure | <g:SimplePanel styleName="max"><g:HTML ui:field="info" styleName="info-layout"/></g:SimplePanel> in place of new SimplePanel/new HTML |
| InfoDocumentViewImpl.java:36,40 | css-class | info-layout (components.css:58) and max (stroom.css:793) |

**Stays in Java:** The info HTML built from the node, tags and audit entries (InfoDocumentPresenter.java:88-131), incl. infoLine divs and the audit table rows: data; isShowing (non-modal, reused while open) and the copy click handler (:59-60).

## `SplashPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.activity.client.component.Splash`
- **Controller:** `stroom.activity.client.component.SplashController`
- **Template:** `stroom-core-client/src/main/resources/stroom/activity/client/SplashViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/activity/client/component/Splash.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/activity/client/SplashPresenter.java (inner SplashView, :114-117)`; `stroom-core-client/src/main/java/stroom/activity/client/SplashViewImpl.java`; `ActivityModule.java:28 bindPresenterWidget(SplashPresenter.class, SplashView.class, SplashViewImpl.class)`; `stroom-gwt-workbench .../gin/query/ActivityScreenModule.java:39-41, content/ActivityScreenModule.java:39-41`
- **Names:** No clash. The template's ui:field="scrollPanel" has no @UiField and its comment about a focus panel is stale. Optional: splashViewImpl-content (components.css:987) could become splash__content.

**Stays in Java:** The splash body HTML, title and version from UiConfig: data; The accept REST call and the warning on reject: controller.

## `SelectionSummaryPresenter`

dialog, presenter; complexity 2, effort S; used by 4.

- **Component:** `stroom.alert.client.component.CommonAlert (unit 497, this wave; SelectionSummary has no view of its own)`
- **Controller:** `stroom.data.client.component.SelectionSummaryController`
- **Template:** `none` → `none (built in Java because it has no view of its own: it shows CommonAlert, whose template is stroom-core-client/src/main/java/stroom/alert/client/component/CommonAlert.ui.xml, unit 497)`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/SelectionSummaryPresenter.java`; `No GIN binding of its own (it injects CommonAlertView, bound at AlertModule.java:35)`; Provider<SelectionSummaryPresenter> in AbstractMetaListPresenter.java:106,127, MetaListPresenter.java:40, SteppingMetaListPresenter.java:43, MetaRelationListPresenter.java:64 become Provider<SelectionSummaryController>
- **Names:** SelectionSummary clashes with the shared DTO stroom.meta.shared.SelectionSummary (which this unit uses), so no component of that name; it reuses CommonAlert, so only SelectionSummaryController (free) is new. Convert after unit 497 in this wave.

**Stays in Java:** The summary HTML, built from the SelectionSummary result (SelectionSummaryPresenter.java:107-240): text from data, passed to CommonAlert.setInfo/setQuestion; "Fetching selection summary. Please wait..." (:68): one of the messages CommonAlert shows, passed to setInfo.

## `ExecutionScheduleRunNowPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.analytics.client.component.ExecutionScheduleRunNow`
- **Controller:** `stroom.analytics.client.component.ExecutionScheduleRunNowController`
- **Template:** `stroom-core-client/src/main/resources/stroom/analytics/client/view/ExecutionScheduleRunNowViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/analytics/client/component/ExecutionScheduleRunNow.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/analytics/client/presenter/ExecutionScheduleRunNowPresenter.java (inner ExecutionScheduleRunNowView, :78-88); `stroom-core-client/src/main/java/stroom/analytics/client/view/ExecutionScheduleRunNowViewImpl.java`; `Its no-op ProcessingStatusUiHandlers implementation (Presenter:68-75; the shared interface stays)`; `AnalyticsModule.java:94-96 bindPresenterWidget(ExecutionScheduleRunNowPresenter...)`; stroom-gwt-workbench .../gin/processing/AnalyticsScreenModule.java:45-47, query/AnalyticsScreenModule.java:95-97, editors/ScheduleScreenModule.java:52-54
- **Names:** No clash. ExecutionScheduleManager (:96) reaches the buttons with getApplySelectionButton/getApplyFilteredButton; they become one callback, setOnAction(Consumer<...>) with an enum of the two actions.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| ExecutionScheduleRunNowViewImpl.ui.xml:8 | `<g:SimplePanel width="45%">` | static | New dialog__button-cell { width: 45%; } in dialog.css (no existing class; BatchExecutionScheduleEditViewImpl.ui.xml:76,79 has the same two cells) |
| ExecutionScheduleRunNowViewImpl.ui.xml:11 | `<g:SimplePanel width="45%">` | static | dialog__button-cell (dialog.css, new) |

| Into the template | What | Detail |
| --- | --- | --- |
| ExecutionScheduleRunNowViewImpl.java:62-63 (in focus()) / ui.xml:6 | text | "The chosen set of execution schedules will be run now, executing according to frequency until up to date." as the text of the label element in the template (setText and withLineBreaks go: the text has no line breaks) |
| ExecutionScheduleRunNowViewImpl.java:50-51 | structure | icon="OK" and icon="GENERATE" on the two b:Button (Button.setIcon(SvgImage), an enum attribute) |
| ExecutionScheduleRunNowViewImpl.ui.xml:9,12 | component-declaration | Checked: both Buttons (wave 1) are already declared |

**Stays in Java:** Nothing: the dialog is fixed.

## `KeyShortcutRecorderPresenter`

dialog, presenter; complexity 2, effort S; used by 4.

- **Component:** `stroom.preferences.client.component.KeyShortcutRecorder`
- **Controller:** `stroom.preferences.client.component.KeyShortcutRecorderController`
- **Template:** `stroom-core-client/src/main/resources/stroom/preferences/client/KeyShortcutRecorderViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/preferences/client/component/KeyShortcutRecorder.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/preferences/client/KeyShortcutRecorderPresenter.java (inner KeyShortcutRecorderView, :170-185)`; `stroom-core-client/src/main/java/stroom/preferences/client/KeyShortcutRecorderUiHandlers.java (becomes setOnKeyStroke(Consumer<KeyStroke>))`; `stroom-core-client/src/main/java/stroom/preferences/client/KeyShortcutRecorderViewImpl.java`; `UserPreferencesModule.java:67-70 bindPresenterWidget(KeyShortcutRecorderPresenter...)`; `stroom-gwt-workbench .../gin/shell/ShellAppScreenModule.java:105-107`
- **Names:** No clash. The area has no client.presenter package (classes sit in stroom.preferences.client), so stroom.preferences.client.component is new.

| Into the template | What | Detail |
| --- | --- | --- |
| KeyShortcutRecorderViewImpl.java:56 | structure | readOnly="true" on <g:TextBox ui:field="keys"> |
| KeyShortcutRecorderViewImpl.java:59 | aria | aria-live="polite": make the message a plain element in a g:HTMLPanel root, <div ui:field="message" class="keyShortcutRecorder-message" aria-live="polite"/> (a DivElement field; Label has no setter for it) |
| KeyShortcutRecorderViewImpl.java:57 | text | placeholder="Press keys": needs a placeholder setter on Stroom's TextBox copy (com/google/gwt/user/client/ui/TextBox.java, protected directory: human approval) |

**Stays in Java:** Form group label "Keys for '<action>'" (KeyShortcutRecorderViewImpl.java:74): text from data; Shortcut text, error and message, built from the bindings (KeyShortcutRecorderPresenter.java:114-164); KeyBinding.setRecording on focus and blur, and the key handling (ViewImpl:92-120); The placeholder setAttribute, until the TextBox copy has a setter.

## `RenameColumnPresenter`

dialog, presenter; complexity 1, effort S; used by 4.

- **Component:** `stroom.dashboard.client.component.RenameColumn`
- **Controller:** `stroom.dashboard.client.component.RenameColumnController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/table/RenameColumnViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/RenameColumn.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/table/RenameColumnPresenter.java (inner RenameColumnView, :117-122)`; `stroom-core-client/src/main/java/stroom/dashboard/client/table/RenameColumnViewImpl.java`; `TableModule.java:82 bindPresenterWidget(RenameColumnPresenter.class, RenameColumnView.class, RenameColumnViewImpl.class)`; `stroom-gwt-workbench .../gin/query/TableScreenModule.java:86-88`
- **Names:** No clash. The TablePresenter reference (RenameColumnPresenter.java:43,88-100) becomes callbacks (the rename of conditional formatting and the name-in-use check); Enter (:51-55) becomes setOnEnter(Runnable).

**Stays in Java:** The name value; "Field name \"<name>\" is already in use" (:99): built from data, controller.

## `RenameTabPresenter`

dialog, presenter; complexity 1, effort S; used by 4.

- **Component:** `stroom.dashboard.client.component.RenameTab`
- **Controller:** `stroom.dashboard.client.component.RenameTabController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/main/RenameTabViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/RenameTab.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/main/RenameTabPresenter.java (inner RenameTabView, :98-103)`; `stroom-core-client/src/main/java/stroom/dashboard/client/main/RenameTabViewImpl.java`; `DashboardModule.java:52-55 bindPresenterWidget(RenameTabPresenter...)`; `stroom-gwt-workbench .../gin/dashboard/DashboardScreenModule.java:55-57`
- **Names:** No clash. The DashboardPresenter and TabLayout references (RenameTabPresenter.java:42-43,86-88) go: the caller's name callback refreshes the layout and marks the dashboard changed. Enter (:51-55) becomes setOnEnter(Runnable).

**Stays in Java:** The name value.

## `DocumentAssetAddItemDialogPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.document.asset.client.component.DocumentAssetAddItemDialog`
- **Controller:** `stroom.document.asset.client.component.DocumentAssetAddItemDialogController`
- **Template:** `stroom-core-client/src/main/resources/stroom/document/asset/client/view/DocumentAssetAddItemDialogViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/document/asset/client/component/DocumentAssetAddItemDialog.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/document/asset/client/presenter/DocumentAssetAddItemDialogPresenter.java (inner DocumentAssetAddItemDialogView, :131-143); `stroom-core-client/src/main/java/stroom/document/asset/client/view/DocumentAssetAddItemDialogViewImpl.java`; `DocumentAssetModule.java:45-47 bindPresenterWidget(DocumentAssetAddItemDialogPresenter...)`; `stroom-gwt-workbench .../gin/editors/VisualisationScreenModule.java:58-60`
- **Names:** No clash. The inner DialogType enum (DocumentAssetAddItemDialogPresenter.java:48-67) moves to the bottom of the controller (callers refer to it). The template's g:FormPanel ui:field="form" has no @UiField and the class visualisation-assets-add-dialog (ui.xml:6) has no CSS rule: both can go when the template moves (screenshot check).

**Stays in Java:** The path label (setPath, DocumentAssetAddItemDialogViewImpl.java:66); Caption "New " + dialog type (DocumentAssetAddItemDialogPresenter.java:94), built from data, set by the controller; Validation messages built from the dialog type and illegal characters (:109-129).

## `DocumentAssetEditAssetDialogPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.document.asset.client.component.DocumentAssetEditAssetDialog`
- **Controller:** `stroom.document.asset.client.component.DocumentAssetEditAssetDialogController`
- **Template:** `stroom-core-client/src/main/resources/stroom/document/asset/client/view/DocumentAssetEditAssetDialogViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/document/asset/client/component/DocumentAssetEditAssetDialog.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/document/asset/client/presenter/DocumentAssetEditAssetDialogPresenter.java (inner DocumentAssetEditAssetDialogView, :107-129); `stroom-core-client/src/main/java/stroom/document/asset/client/view/DocumentAssetEditAssetDialogViewImpl.java`; `DocumentAssetModule.java:48-50 bindPresenterWidget(DocumentAssetEditAssetDialogPresenter...)`; `stroom-gwt-workbench .../gin/editors/VisualisationScreenModule.java:61-63`
- **Names:** No clash. The view holds model state (the item's id and leaf flag, DocumentAssetEditAssetDialogViewImpl.java:39-42,66-68): it moves to the controller; the component only has setName/getName. Same unused ui:field="form" and visualisation-assets-add-dialog class (no CSS rule) as unit 267.

**Stays in Java:** The name value; Validation messages built from data (DocumentAssetEditAssetDialogPresenter.java:89-102).

## `GitRepoCommitDialogPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.gitrepo.client.component.GitRepoCommitDialog`
- **Controller:** `stroom.gitrepo.client.component.GitRepoCommitDialogController`
- **Template:** `stroom-core-client/src/main/resources/stroom/gitrepo/client/view/GitRepoCommitDialogViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/gitrepo/client/component/GitRepoCommitDialog.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/gitrepo/client/presenter/GitRepoCommitDialogPresenter.java (inner GitRepoCommitDialogView, :87-99)`; stroom-core-client/src/main/java/stroom/gitrepo/client/view/GitRepoCommitDialogViewImpl.java (with its empty GitRepoCommitDialogUiHandlers, :74-76); `GitRepoModule.java:38-40 bindPresenterWidget(GitRepoCommitDialogPresenter...)`; `stroom-gwt-workbench .../gin/editors/GitRepoScreenModule.java:42-44`
- **Names:** No clash.

**Stays in Java:** The commit message value (reset to empty on show); Validation message "Please enter a commit message" (Presenter:76-82): controller.

## `CustomRowStylePresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.dashboard.client.component.CustomRowStyleEditor`
- **Controller:** `stroom.dashboard.client.component.CustomRowStyleEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/table/cf/CustomRowStyleViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/CustomRowStyleEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/table/cf/CustomRowStylePresenter.java (inner CustomRowStyleView, :87-106)`; `stroom-core-client/src/main/java/stroom/dashboard/client/table/cf/CustomRowStyleViewImpl.java`; TableModule.java:90-93 bindPresenterWidget(CustomRowStylePresenter.class, CustomRowStylePresenter.CustomRowStyleView.class, CustomRowStyleViewImpl.class); `stroom-gwt-workbench .../gin/QueryResultTableScreenModule.java:77-79, query/TableScreenModule.java:98-100`
- **Names:** CustomRowStyle clashes with stroom.query.api.CustomRowStyle, which this unit builds (CustomRowStylePresenter.java:70-75); named CustomRowStyleEditor (free). Its package-private read/write/show are called from the cf package; they become public on the controller.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| CustomRowStyleViewImpl.ui.xml:39 | `<g:SimplePanel ui:field="lightExample" addStyleNames="cf-light-example" width="200px"/>` | static | Add width: 200px to .cf-light-example (conditional-formatting.css:465; used only here) |
| CustomRowStyleViewImpl.ui.xml:75 | `<g:SimplePanel ui:field="darkExample" addStyleNames="cf-dark-example" width="200px"/>` | static | Add width: 200px to .cf-dark-example (conditional-formatting.css:469) |
| CustomRowStyleViewImpl.java:118-134 (via ConditionalFormattingSwatchUtil.java:83-100) | `createCustomSwatch: SafeStyles background-color and color from the user's colours, put…` | dynamic | --cf-swatch__background-color and --cf-swatch__color set on a swatch element declared in the template, used by a new .cf-colour-swatch--custom { background-color: var(--cf-swatch__background-color); color: var(--cf-swatch__color); } in conditional-formatting.css (colours still checked with SafeHtmlUtil.asTrustedColour). The grid cells keep createCustomSwatch (SafeHtml stays for cells) |

| Into the template | What | Detail |
| --- | --- | --- |
| CustomRowStyleViewImpl.java:118-134 | structure | The swatch: <div ui:field="lightSwatch" class="cf-colour-swatch cf-colour-swatch--custom">Custom</div> inside the light example panel, and the same for dark, in place of the SafeHtml built in Java |

**Stays in Java:** Bold and italic state classes cf-bold/cf-italic from the text attributes (ConditionalFormattingSwatchUtil.getTextAttributeClassNames); The four colour values and the swatches' custom properties.

## `DownloadChatPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.ai.client.component.DownloadChat`
- **Controller:** `stroom.ai.client.component.DownloadChatController`
- **Template:** `stroom-core-client/src/main/resources/stroom/ai/client/DownloadChatViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/ai/client/component/DownloadChat.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/ai/client/DownloadChatPresenter.java (inner DownloadChatView, :37-40)`; `stroom-core-client/src/main/java/stroom/ai/client/DownloadChatViewImpl.java`; `AskStroomAIModule.java:66-69 bindPresenterWidget(DownloadChatPresenter...)`; `stroom-gwt-workbench .../gin/editors/AskStroomAIScreenModule.java:64-66`
- **Names:** No clash. stroom.ai.client.component is a new package (the area has no presenter package).

**Stays in Java:** The tick box value.

## `LayoutConstraintPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.dashboard.client.component.LayoutConstraint`
- **Controller:** `stroom.dashboard.client.component.LayoutConstraintController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/main/LayoutConstraintViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/LayoutConstraint.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/main/LayoutConstraintPresenter.java (inner LayoutConstraintView, :53-62)`; stroom-core-client/src/main/java/stroom/dashboard/client/main/LayoutConstraintViewImpl.java (its private SimpleEventBus and HasValueChangeHandlers, :46,83-91, become setOnChange(Consumer<LayoutConstraints>)); `DashboardModule.java:56-59 bindPresenterWidget(LayoutConstraintPresenter...)`; `stroom-gwt-workbench .../gin/dashboard/DashboardScreenModule.java:58-60`
- **Names:** LayoutConstraint is free; close to the shared DTO stroom.dashboard.shared.LayoutConstraints (plural), which it edits; no exact clash.

**Stays in Java:** The two tick values.

## `NamePresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.dashboard.client.component.FavouriteName`
- **Controller:** `stroom.dashboard.client.component.FavouriteNameController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/query/NameViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/FavouriteName.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/query/NamePresenter.java (inner NameView, :133-138)`; `stroom-core-client/src/main/java/stroom/dashboard/client/query/NameViewImpl.java`; `Its DialogActionUiHandlers implementation (NamePresenter.java:128-131, NameViewImpl.java:64-69) becomes setOnEnter(Runnable)`; `QueryModule.java:56 bindPresenterWidget(NamePresenter.class, NameView.class, NameViewImpl.class)`; `stroom-gwt-workbench .../gin/query/DashboardQueryScreenModule.java:63-65`
- **Names:** Name is free but too generic (and java.lang names); it names a favourite query, so FavouriteName (free). It could instead reuse NameEntry (unit 770), but its help text and required flag differ, so it keeps its own template.

**Stays in Java:** The name value; Caption "Create New Favourite"/"Rename Favourite" chosen from data (NamePresenter.java:63-65): controller.

## `AddEventLinkPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.annotation.client.component.AddEventLink`
- **Controller:** `stroom.annotation.client.component.AddEventLinkController`
- **Template:** `stroom-core-client/src/main/resources/stroom/annotation/client/AddEventLinkViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/annotation/client/component/AddEventLink.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/annotation/client/AddEventLinkPresenter.java (inner AddEventLinkView, :98-103)`; `stroom-core-client/src/main/java/stroom/annotation/client/AddEventLinkViewImpl.java`; `AnnotationModule.java:81 bindPresenterWidget(AddEventLinkPresenter.class, AddEventLinkView.class, AddEventLinkViewImpl.class)`; `stroom-gwt-workbench .../gin/query/AnnotationScreenModule.java:76-78, content/AnnotationScreenModule.java:76-78`
- **Names:** No clash. Enter (AddEventLinkPresenter.java:44-48) becomes setOnEnter(Runnable).

| Into the template | What | Detail |
| --- | --- | --- |
| AddEventLinkViewImpl.ui.xml:6 | aria | identity="searchDownloadFileType" is copied from the download dialog (DownloadViewImpl.ui.xml:8); give it its own, e.g. identity="addEventLinkEvent" |

**Stays in Java:** The event id value, cleared on show (:52); "Invalid event id '<text>'" (:67,78): built from data.

## `StepControlPresenter`

composite, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.pipeline.stepping.client.component.StepControl`
- **Controller:** `stroom.pipeline.stepping.client.component.StepControlController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pipeline/stepping/client/view/StepControlViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/StepControl.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/presenter/StepControlPresenter.java (inner StepControlView, :194-207)`; stroom-core-client/src/main/java/stroom/pipeline/stepping/client/presenter/StepControlUIHandlers.java (becomes setOnAction with one enum of filter and the StepTypes, or setOnStep(Consumer<StepType>) and setOnFilter(Runnable) as the presenter has); `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/view/StepControlViewImpl.java`; `PipelineModule.java:119 bindPresenterWidget(StepControlPresenter.class, StepControlView.class, StepControlViewImpl.class)`; stroom-gwt-workbench .../gin/processing/PipelineScreenModule.java:114-116, query/PipelineScreenModule.java:114-116, content/PipelineScreenModule.java:114-116
- **Names:** No clash. The enabling logic (setEnabledButtons, markEndLocation, StepControlPresenter.java:121-178) goes to the controller; the component gets setAvailableActions/setEnabled per step.

| Into the template | What | Detail |
| --- | --- | --- |
| StepControlViewImpl.ui.xml:4-10 | component-declaration | Checked: Toolbar and six ToolbarButtons (wave 1) already declared with svg, title and enabled. The class stepping-buttons (ui.xml:4) has no CSS rule |

**Stays in Java:** Each button's enabled state.

## `NewElementPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.pipeline.structure.client.component.NewElement`
- **Controller:** `stroom.pipeline.structure.client.component.NewElementController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pipeline/structure/client/view/NewElementViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/NewElement.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/pipeline/structure/client/presenter/NewElementPresenter.java (inner NewElementView, :83-92)`; `stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/NewElementViewImpl.java`; `PipelineModule.java:128 bindPresenterWidget(NewElementPresenter.class, NewElementView.class, NewElementViewImpl.class)`; stroom-gwt-workbench .../gin/processing/PipelineScreenModule.java:129-131, query/PipelineScreenModule.java:129-131, content/PipelineScreenModule.java:129-131
- **Names:** No clash. Enter on the name (NewElementPresenter.java:43-49) becomes setOnEnter(Runnable); getDescriptionBox is unused.

**Stays in Java:** Name and description values; Caption, from the caller.

## `NameDocumentPresenter`

dialog, presenter; complexity 1, effort S; used by 2.

- **Component:** `stroom.entity.client.component.NameEntry`
- **Controller:** `stroom.entity.client.component.RenameDocumentController`
- **Template:** `stroom-core-client/src/main/resources/stroom/entity/client/view/NameDocumentViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/entity/client/component/NameEntry.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/entity/client/presenter/NameDocumentPresenter.java`; `stroom-core-client/src/main/java/stroom/entity/client/presenter/NameDocumentView.java`; `stroom-core-client/src/main/java/stroom/entity/client/view/NameDocumentViewImpl.java`; `EntityModule.java:68 bindSharedView(NameDocumentView.class, NameDocumentViewImpl.class) and :69 bindPresenter(NameDocumentPresenter.class)`; `stroom-gwt-workbench .../gin/EntityScreenModule.java:69-71 (bindSharedView and bind)`; `DialogActionUiHandlers on the view (NameDocumentViewImpl.java:79-84) becomes NameEntry.setOnEnter(Runnable)`; `DocumentDialogScreens.java:61 handler calls the controller; NodeGroupsScreenStories uses NameDocumentView (moves)`
- **Names:** NameDocument is free but fits neither half: the view is a name field shared by five dialogs (units 205, 238, 577, 628 and this), and the presenter renames explorer documents. So the shared dumb component is NameEntry and this unit's controller RenameDocumentController (both free). Convert first among the name dialogs; the others then reuse NameEntry with their own controllers. They show the same help text today, so sharing one template changes nothing.

**Stays in Java:** The name value; Feedback "You must provide a new name for <document>" (NameDocumentPresenter.java:99), built from data, via setNameInvalid; Caption "Rename <document>" (:74): data, controller; Clearing the feedback once a name is typed (NameDocumentViewImpl.java:87-92).

## `CreateNewGroupPresenter`

dialog, presenter; complexity 2, effort S; used by 4.

- **Component:** `stroom.entity.client.component.NameEntry (shared, unit 770)`
- **Controller:** `stroom.security.client.component.CreateNewGroupController`
- **Template:** `none (shares stroom-core-client/src/main/resources/stroom/entity/client/view/NameDocumentViewImpl.ui.xml)` → `none (built in Java because it has no view of its own: it shows the shared NameEntry.ui.xml, unit 770)`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/client/presenter/CreateNewGroupPresenter.java`; `setDialogActionUiHandlers (CreateNewGroupPresenter.java:51-53) becomes NameEntry.setOnEnter(Runnable)`; stroom-core-client/src/test/java/stroom/security/client/presenter/TestCreateNewGroupPresenter.java moves to the component package as TestCreateNewGroupController; `No GIN binding of its own (shared NameDocumentView, EntityModule.java:68)`
- **Names:** CreateNewGroupController is free. Its four callers (CreateUserPresenter, UsersPresenter, UserAndGroupsPresenter, UserAndGroupHelper) move in the same commit. Convert after unit 770.

**Stays in Java:** The name value; Feedback "You must provide a name for the group" (CreateNewGroupPresenter.java:72), passed to NameEntry.setNameInvalid (shown only when invalid); ConfirmEvent text for restoring a deleted group (:96-98): the controller's.

## `ProcessorLimitsPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.dashboard.client.component.ProcessorLimits`
- **Controller:** `stroom.dashboard.client.component.ProcessorLimitsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/query/ProcessorLimitsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/ProcessorLimits.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/query/ProcessorLimitsPresenter.java (inner ProcessorLimitsView, :49-58)`; `stroom-core-client/src/main/java/stroom/dashboard/client/query/ProcessorLimitsViewImpl.java`; `QueryModule.java:59 bindPresenterWidget(ProcessorLimitsPresenter.class, ProcessorLimitsView.class, ProcessorLimitsViewImpl.class)`; `stroom-gwt-workbench .../gin/processing/ProcessingExtrasScreenModule.java:49-51, query/DashboardQueryScreenModule.java:72-74`
- **Names:** No clash. The presenter is a pass-through to its view; the controller may be trivial (it only shows the dialog), so the owner could use the component directly.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| ProcessorLimitsViewImpl.ui.xml:10 | `<v:ValueSpinner ui:field="recordLimit" width="200px"/>` | static | New modifier value-spinner--wide { width: 200px; } in value-spinner.css (17 spinners in templates set width="200px"; .value-spinner is 96px, value-spinner.css:29) |
| ProcessorLimitsViewImpl.ui.xml:15 | `<v:ValueSpinner ui:field="timeLimit" width="200px"/>` | static | value-spinner--wide (value-spinner.css, new) |

| Into the template | What | Detail |
| --- | --- | --- |
| ProcessorLimitsViewImpl.java:40-41 | structure | <v:ValueSpinner ui:field="recordLimit" min="1" max="1000000000" addStyleNames="value-spinner--wide"/> |
| ProcessorLimitsViewImpl.java:44-45 | structure | <v:ValueSpinner ui:field="timeLimit" min="1" max="1440" addStyleNames="value-spinner--wide"/> |

**Stays in Java:** Defaults 1,000,000 records and 60 minutes (ProcessorLimitsViewImpl.java:42,46): setValue is overloaded, so Java; Values.

## `NewNodeGroupPresenter`

dialog, presenter; complexity 2, effort S; used by 1.

- **Component:** `stroom.entity.client.component.NameEntry (shared name field, made from NameDocumentView/NameDocumentViewImpl by unit 770)`
- **Controller:** `stroom.node.client.component.NewNodeGroupController`
- **Template:** `none (shares stroom-core-client/src/main/resources/stroom/entity/client/view/NameDocumentViewImpl.ui.xml)` → `none (built in Java because it has no view of its own: it shows the shared NameEntry, stroom-core-client/src/main/java/stroom/entity/client/component/NameEntry.ui.xml, unit 770)`
- **Goes:** `stroom-core-client/src/main/java/stroom/node/client/presenter/NewNodeGroupPresenter.java`; Its DialogActionUiHandlers implementation (NewNodeGroupPresenter.java:113-116, DialogEvent.fire on Enter) becomes NameEntry.setOnEnter(Runnable); `No GIN binding of its own (it injects the shared NameDocumentView, bound at EntityModule.java:68)`
- **Names:** A NewNodeGroup component is not made: its content would be a copy of NameEntry (one name field). NewNodeGroupController is free. Convert after unit 770 (NameEntry).

**Stays in Java:** The name value (setName/getName); Validation messages, incl. text built from data: "Group name '<name>' is already in use by another group." (NewNodeGroupPresenter.java:96-98) and PathSafeNames.validate's message; Caption "New" is the dialog chrome the controller sets.

## `NewFsVolumeGroupPresenter`

dialog, presenter; complexity 1, effort S; used by 1.

- **Component:** `stroom.entity.client.component.NameEntry (shared, unit 770)`
- **Controller:** `stroom.data.store.impl.fs.client.component.NewFsVolumeGroupController`
- **Template:** `none (shares stroom-core-client/src/main/resources/stroom/entity/client/view/NameDocumentViewImpl.ui.xml)` → `none (built in Java because it has no view of its own: it shows the shared NameEntry.ui.xml, unit 770)`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/presenter/NewFsVolumeGroupPresenter.java`; `Its DialogActionUiHandlers implementation (:130-133) becomes NameEntry.setOnEnter(Runnable)`; `No GIN binding of its own (shared NameDocumentView, EntityModule.java:68)`
- **Names:** NewFsVolumeGroupController is free; no NewFsVolumeGroup component (it would copy NameEntry). Convert after unit 770.

**Stays in Java:** The name value; "Group name '<name>' is already in use by another group." (:104-106), built from data.

## `NewIndexVolumeGroupPresenter`

dialog, presenter; complexity 2, effort S; used by 1.

- **Component:** `stroom.entity.client.component.NameEntry (shared, unit 770)`
- **Controller:** `stroom.index.client.component.NewIndexVolumeGroupController`
- **Template:** `none (shares stroom-core-client/src/main/resources/stroom/entity/client/view/NameDocumentViewImpl.ui.xml)` → `none (built in Java because it has no view of its own: it shows the shared NameEntry.ui.xml, unit 770)`
- **Goes:** `stroom-core-client/src/main/java/stroom/index/client/presenter/NewIndexVolumeGroupPresenter.java`; `Its DialogActionUiHandlers implementation (:129-132) becomes NameEntry.setOnEnter(Runnable)`; `No GIN binding of its own (shared NameDocumentView, EntityModule.java:68)`
- **Names:** NewIndexVolumeGroupController is free; no NewIndexVolumeGroup component (it would copy NameEntry). Convert after unit 770.

**Stays in Java:** The name value; "Group name '<name>' is already in use by another group." (:103-105).

## `ProfilePeriodEditPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.processor.client.component.ProfilePeriodEditor`
- **Controller:** `stroom.processor.client.component.ProfilePeriodEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/processor/client/view/ProfilePeriodEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/processor/client/component/ProfilePeriodEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/processor/client/presenter/ProfilePeriodEditPresenter.java (inner ProfilePeriodEditView, :112-137)`; `stroom-core-client/src/main/java/stroom/processor/client/view/ProfilePeriodEditViewImpl.java`; `PipelineModule.java:159-162 bindPresenterWidget(ProfilePeriodEditPresenter...)`; stroom-gwt-workbench .../gin/processing/PipelineScreenModule.java:168-170, query/PipelineScreenModule.java:168-170, editors/PipelineScreenModule.java:80-82, content/PipelineScreenModule.java:168-170
- **Names:** ProfilePeriodEdit (a verb) renamed ProfilePeriodEditor after FieldEditor; free. Its root class credentials-details-dialog (ui.xml:9) is borrowed from the credentials dialog (components.css:2021, comment says so): fine to keep; a profile-period class would be clearer.

| Into the template | What | Detail |
| --- | --- | --- |
| ProfilePeriodEditViewImpl.java:57-58 | structure | <valuespinner:ValueSpinner ui:field="maxNodeThreads" min="0" max="1000000" .../> |
| ProfilePeriodEditViewImpl.java:59-60 | structure | <valuespinner:ValueSpinner ui:field="maxClusterThreads" min="0" max="1000000" .../> |
| ProfilePeriodEditPresenter.java:50-51 | component-declaration | TimeBox x2 are declared (ui.xml:20,26); their popup provider set in Java becomes wave 4's TimeBoxController attached to each (make(provider).attach(view.getStartTime())). DaysOfWeek, TickBox x2, ValueSpinner x2, FormGroup: checked, already declared |

**Stays in Java:** Max threads spinners enabled by their tick boxes (ProfilePeriodEditViewImpl.java:101,122,137,142): state; Values; 'Invalid start/end time' warnings (controller).

## `FormatPresenter`

dialog, presenter; complexity 2, effort S; used by 6.

- **Component:** `stroom.dashboard.client.component.ColumnFormat`
- **Controller:** `stroom.dashboard.client.component.ColumnFormatController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/table/FormatViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/ColumnFormat.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/table/FormatPresenter.java (inner FormatView, :192-235)`; `stroom-core-client/src/main/java/stroom/dashboard/client/table/FormatUihandlers.java (becomes setOnTypeChange(Consumer<Type>))`; `stroom-core-client/src/main/java/stroom/dashboard/client/table/FormatViewImpl.java`; `TableModule.java:76 bindPresenterWidget(FormatPresenter.class, FormatView.class, FormatViewImpl.class)`; `stroom-gwt-workbench .../gin/QueryResultTableScreenModule.java:56-58, query/TableScreenModule.java:74-76`
- **Names:** Format clashes with stroom.query.api.Format (the model this dialog edits, FormatPresenter.java:77) and java.text.Format; named ColumnFormat (free).

| Into the template | What | Detail |
| --- | --- | --- |
| FormatViewImpl.java:114-115 | structure | <valuespinner:ValueSpinner ui:field="decimalPlaces" min="0" max="10" .../>; the setValue(0) at :113 is redundant (the controller's read sets it) |
| FormatViewImpl.java:124-128 | structure | <valuespinner:ValueSpinner ui:field="timeZoneOffsetHours" min="-12" max="12" minStep="1" maxStep="1"/>; setValue(0) at :126 redundant |
| FormatViewImpl.java:130-134 | structure | <valuespinner:ValueSpinner ui:field="timeZoneOffsetMinutes" min="0" max="59" minStep="30" maxStep="30"/>; setValue(0) at :132 redundant |
| FormatViewImpl.java:110-111 | aria | accessibleName="Hours" and accessibleName="Minutes" on the two spinners: needs a setAccessibleName on ValueSpinner (wave 2 widget, an outward tidy) |

**Stays in Java:** STANDARD_FORMATS items and setPattern's matching (FormatViewImpl.java:42-56,117,210-225); Time zone use items (:119-122) and ids from TimeZones (FormatPresenter.java:69); Group visibility by type and time zone use (:153-168) and Custom Format enabled (:223,300): state; Caption "Format '<column>'" (FormatPresenter.java:95): from data, controller.

## `DownloadPresenter`

dialog, presenter; complexity 1, effort S; used by 4.

- **Component:** `stroom.dashboard.client.component.DownloadSearchResults`
- **Controller:** `stroom.dashboard.client.component.DownloadSearchResultsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/table/DownloadViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/DownloadSearchResults.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/table/DownloadPresenter.java (inner DownloadView, :54-65)`; `stroom-core-client/src/main/java/stroom/dashboard/client/table/DownloadViewImpl.java`; `TableModule.java:81 bindPresenterWidget(DownloadPresenter.class, DownloadView.class, DownloadViewImpl.class)`; `stroom-gwt-workbench .../gin/QueryResultTableScreenModule.java:65-67, query/TableScreenModule.java:83-85`
- **Names:** Download is free but too generic beside DownloadChat (unit 471) in the same wave and the JDK/GWT download helpers; named DownloadSearchResults after DownloadSearchResultFileType (free).

| Into the template | What | Detail |
| --- | --- | --- |
| DownloadViewImpl.java:54-55,57 | structure | <v:ValueSpinner ui:field="percent" min="1" max="100" enabled="false"/> |

**Stays in Java:** Percent default 100 (DownloadViewImpl.java:56): setValue is overloaded, so the component's Java sets it; File type items and default (:59-60): enum list and ReportSettings.DEFAULT_FILE_TYPE; Download All Tables enabled only for Excel (:62-68) and Sample Size enabled by Sample Only (:112): state; Download All visible (setShowDownloadAll :97).

## `SolrIndexFieldEditPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.search.solr.client.component.SolrIndexFieldEditor`
- **Controller:** `stroom.search.solr.client.component.SolrIndexFieldEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/search/solr/client/view/SolrIndexFieldEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/search/solr/client/component/SolrIndexFieldEditor.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/search/solr/client/presenter/SolrIndexFieldEditPresenter.java (inner SolrIndexFieldEditView, :133-212); `stroom-core-client/src/main/java/stroom/search/solr/client/view/SolrIndexFieldEditViewImpl.java`; `SolrIndexModule.java:44-46 bindPresenterWidget(SolrIndexFieldEditPresenter...)`; `stroom-gwt-workbench .../gin/editors/SolrIndexScreenModule.java:48-50`
- **Names:** Becomes suggested SolrIndexFieldEdit (a verb); renamed SolrIndexFieldEditor after the pilot's FieldEditor. SolrIndexFieldEditor/Controller are free. AlertEvent.fireWarn validation (Presenter:79-90) goes to the controller.

| Into the template | What | Detail |
| --- | --- | --- |
| SolrIndexFieldEditViewImpl.ui.xml:89 | structure | Remove <g:HTML ui:field="synchState" styleName="dock-min"/>: no @UiField binds it and nothing sets it |
| SolrIndexFieldEditViewImpl.ui.xml:11,21,30-86 | component-declaration | Checked: SelectionBox (type, fieldType), TickBox (15 ticks) and FormGroup are already declared in the template |

**Stays in Java:** Field use items LuceneFieldTypes.FIELD_TYPES (SolrIndexFieldEditViewImpl.java:81) and Solr field types read from Solr (setFieldTypes :285-288): lists in Java; Every field value.

## `EditFeedDependencyPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.processor.client.component.FeedDependencyEditor`
- **Controller:** `stroom.processor.client.component.FeedDependencyEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/processor/client/view/EditFeedDependencyViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/processor/client/component/FeedDependencyEditor.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/processor/client/presenter/EditFeedDependencyPresenter.java (inner EditFeedDependencyView, :144-149); `stroom-core-client/src/main/java/stroom/processor/client/view/EditFeedDependencyViewImpl.java`; `PipelineModule.java:151-154 bindPresenterWidget(EditFeedDependencyPresenter...)`; stroom-gwt-workbench .../gin/processing/PipelineScreenModule.java:162-164, query/PipelineScreenModule.java:162-164, editors/PipelineScreenModule.java:74-76, content/PipelineScreenModule.java:162-164
- **Names:** EditFeedDependency is free, but named FeedDependencyEditor (free) after the pilot's FieldEditor; either is fine.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| EditFeedDependencyPresenter.java:70 | `feedPresenter.getWidget().getElement().getStyle().setMarginBottom(0, PX)` | static | Drop: the picker (.drop-down, drop-down.css:27; stroom-control) has no bottom margin. Confirm with the screenshots |
| EditFeedDependencyPresenter.java:74 | `dataTypeWidget.getElement().getStyle().setMarginBottom(0, PX)` | static | Drop: .selection-box (selection-box.css:29) has no bottom margin |
| EditFeedDependencyViewImpl.java:51 | `feed widget getStyle().setWidth(100, PCT)` | static | w-100 (stroom.css:843) on the declared <dd:DropDownBox> |
| EditFeedDependencyViewImpl.java:57 | `type widget getStyle().setWidth(100, PCT)` | static | w-100 (stroom.css:843) on the declared <i:SelectionBox> |

| Into the template | What | Detail |
| --- | --- | --- |
| EditFeedDependencyViewImpl.ui.xml:10 / EditFeedDependencyPresenter.java:62-71 | component-declaration | <dd:DropDownBox ui:field="feed" addStyleNames="w-100"/> (xmlns:dd="urn:import:stroom.widget.dropdowntree.client") in place of the SimplePanel slot; the controller makes a DocSelectionController and attaches it (feed types and USE permission set on it), as AnalyticStreamDestinationController does |
| EditFeedDependencyViewImpl.ui.xml:16 / EditFeedDependencyPresenter.java:73-75 | component-declaration | <i:SelectionBox ui:field="type" addStyleNames="w-100"/> in place of the slot and new SelectionBox<>() |

**Stays in Java:** Stream types from MetaResource.getTypes (REST, controller) and the selected values; Dirty tracking (controller).

## `XPathFilterPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.pipeline.stepping.client.component.XPathFilterEditor`
- **Controller:** `stroom.pipeline.stepping.client.component.XPathFilterEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pipeline/stepping/client/view/XPathFilterViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/XPathFilterEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/presenter/XPathFilterPresenter.java (inner XPathFilterView, :101-122)`; `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/view/XPathFilterViewImpl.java`; `PipelineModule.java:136 bindPresenterWidget(XPathFilterPresenter.class, XPathFilterView.class, XPathFilterViewImpl.class)`; stroom-gwt-workbench .../gin/processing/PipelineScreenModule.java:144-146, query/PipelineScreenModule.java:144-146, content/PipelineScreenModule.java:144-146
- **Names:** XPathFilter clashes with the shared DTO stroom.pipeline.shared.XPathFilter that it edits; named XPathFilterEditor (free). The template's root class XPathFilterViewImpl (ui.xml:7) has no CSS rule.

**Stays in Java:** Match type and search type items (XPathFilterViewImpl.java:58,61; enum values) and their defaults EQUALS/ALL (:59,62; read overwrites them); Visibility of XPath, Value and Ignore Case (:68,143-144,152,155): state; XPath set to "//" when switching to XPath (:156): state.

## `NewPipelineReferencePresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.pipeline.structure.client.component.NewPipelineReference`
- **Controller:** `stroom.pipeline.structure.client.component.NewPipelineReferenceController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pipeline/structure/client/view/NewPipelineReferenceViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/NewPipelineReference.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/pipeline/structure/client/presenter/NewPipelineReferencePresenter.java (inner NewPipelineReferenceView, :175-184); `stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/NewPipelineReferenceViewImpl.java`; PipelineModule.java:130-131 bindPresenterWidget(NewPipelineReferencePresenter.class, NewPipelineReferenceView.class, NewPipelineReferenceViewImpl.class); stroom-gwt-workbench .../gin/processing/PipelineScreenModule.java:135-137, query/PipelineScreenModule.java:135-137, content/PipelineScreenModule.java:135-137
- **Names:** No clash. The empty onChange (NewPipelineReferencePresenter.java:142-144) and its listeners do nothing: drop them or give the component setOnChange.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| NewPipelineReferencePresenter.java:81 | `pipelinePresenter.getWidget().getElement().getStyle().setMarginBottom(0, PX)` | static | Drop: .drop-down (drop-down.css:27) has no bottom margin |
| NewPipelineReferencePresenter.java:84 | `feedPresenter.getWidget().getElement().getStyle().setMarginBottom(0, PX)` | static | Drop (as above) |
| NewPipelineReferencePresenter.java:88 | `dataTypeWidget.getElement().getStyle().setMarginBottom(0, PX)` | static | Drop: .selection-box (selection-box.css:29) has no bottom margin |
| NewPipelineReferenceViewImpl.java:61 | `pipeline widget getStyle().setWidth(100, PCT)` | static | w-100 (stroom.css:843) on the declared DropDownBox |
| NewPipelineReferenceViewImpl.java:67 | `feed widget getStyle().setWidth(100, PCT)` | static | w-100 (stroom.css:843) on the declared DropDownBox |
| NewPipelineReferenceViewImpl.java:73 | `type widget getStyle().setWidth(100, PCT)` | static | w-100 (stroom.css:843) on the declared SelectionBox |

| Into the template | What | Detail |
| --- | --- | --- |
| NewPipelineReferenceViewImpl.ui.xml:13 / NewPipelineReferencePresenter.java:67,75-76,82 | component-declaration | <dd:DropDownBox ui:field="pipeline" addStyleNames="w-100"/>, with a DocSelectionController attached (pipeline, Plan B and SQL temporal store types, USE permission, tag quick filter from UiConfig) |
| NewPipelineReferenceViewImpl.ui.xml:17 / NewPipelineReferencePresenter.java:68,78-79,85 | component-declaration | <dd:DropDownBox ui:field="feed" addStyleNames="w-100"/>, with a DocSelectionController attached (feed type, USE) |
| NewPipelineReferenceViewImpl.ui.xml:22 / NewPipelineReferencePresenter.java:87-89 | component-declaration | <i:SelectionBox ui:field="type" addStyleNames="w-100"/> in place of new SelectionBox<>() |

**Stays in Java:** The element id (data); Stream types from MetaResource.getTypes and the selections.

## `NewPropertyPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.pipeline.structure.client.component.NewProperty`
- **Controller:** `stroom.pipeline.structure.client.component.NewPropertyController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pipeline/structure/client/view/NewPropertyViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/NewProperty.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/pipeline/structure/client/presenter/NewPropertyPresenter.java (inner NewPropertyView, :441-460)`; `stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/NewPropertyViewImpl.java`; stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/NewPropertyUiHandlers.java (becomes setOnSourceChange(Consumer<Source>)); `PipelineModule.java:129 bindPresenterWidget(NewPropertyPresenter.class, NewPropertyView.class, NewPropertyViewImpl.class)`; stroom-gwt-workbench .../gin/processing/PipelineScreenModule.java:132-134, query/PipelineScreenModule.java:132-134, content/PipelineScreenModule.java:132-134
- **Names:** No clash. Opened by PropertyListPresenter with makeDialog (caller moves). Stream type and volume group modes share one SelectionBox and one 'initialised' flag (Presenter:80,299-390); separate declared boxes fix that.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| NewPropertyPresenter.java:221,265,284,327,377,400 | `value widget getStyle().setWidth(100, PCT) (boolean list, spinner, text box, stream…` | static | w-100 (stroom.css:843) on each editor declared in the template |
| NewPropertyPresenter.java:222,266,285,328,378,401 | `value widget getStyle().setMarginBottom(0, PX)` | static | Drop: .selection-box, .value-spinner, .text-field (margin: 0, text-field.css:36) and .drop-down have no bottom margin |

| Into the template | What | Detail |
| --- | --- | --- |
| NewPropertyPresenter.java:210-420 / NewPropertyViewImpl.ui.xml:55 | component-declaration | Every value editor declared in the Value form group in place of the SimplePanel slot, one shown at a time with setVisible: <i:SelectionBox ui:field="booleanValue" addStyleNames="w-100"/>, <v:ValueSpinner ui:field="numberValue" min="0" max="10000000" addStyleNames="w-100"/> (Presenter:257-258), <g:TextBox ui:field="textValue" addStyleNames="w-100"/>, <i:SelectionBox ui:field="streamTypeValue" addStyleNames="w-100"/>, <i:SelectionBox ui:field="volumeGroupValue" addStyleNames="w-100"/>, <dd:DropDownBox ui:field="docValue" addStyleNames="w-100"/> with a DocSelectionController attached (Presenter:58,89,392-420) |
| NewPropertyViewImpl.java:63-64 | structure | visible="false" on defaultValueGroup and inheritedValueGroup |
| NewPropertyViewImpl.java:136 | aria | "Value" name on each declared editor (accessibleName="Value" where the widget has the setter; the form group label names only the first control) |

**Stays in Java:** Source items DEFAULT/INHERIT/LOCAL (NewPropertyViewImpl.java:65-67) and EMBEDDED when the type can embed (Presenter:121); Boolean items "true"/"false" (Presenter:213-214): SelectionBox items are set in Java; "Inherited Value From '<element>'" label (NewPropertyViewImpl.java:116) and the element, name, description, default and inherited values: data; Embedded value text built from data (Presenter:152-153) and the text box disabled for it; Which editor shows (from the property type), stream types and volume groups from REST, Source set to Local on change: state.

## `ConstraintEditPresenter`

dialog, presenter; complexity 3, effort S; used by 3.

- **Component:** `stroom.pathways.client.component.ConstraintEditor`
- **Controller:** `stroom.pathways.client.component.ConstraintEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pathways/client/view/ConstraintEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pathways/client/component/ConstraintEditor.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/pathways/client/presenter/ConstraintEditPresenter.java (inner ConstraintEditView :461-478; and the commented-out dead code :60-61, :67-126, :128-248, :331-348); `stroom-core-client/src/main/java/stroom/pathways/client/view/ConstraintEditViewImpl.java`; `PathwaysModule.java:74-76 bindPresenterWidget(ConstraintEditPresenter...)`; `stroom-gwt-workbench .../gin/editors/PathwaysScreenModule.java:60-62, content/PathwaysScreenModule.java:74-76`
- **Names:** ConstraintEdit (a verb) renamed ConstraintEditor; free. The value-to-text switches (ConstraintEditPresenter.java:255-326, 363-442) go to the controller or a small model helper. Note: INTEGER_RANGE and LONG_RANGE set the value twice, the second (toString) overwriting the first (:297-299, :311-313).

| Into the template | What | Detail |
| --- | --- | --- |
| ConstraintEditViewImpl.ui.xml:12,18 | component-declaration | Checked: SelectionBox, TickBox and FormGroup are already declared |

**Stays in Java:** Type items ConstraintValueType.values() (ConstraintEditViewImpl.java:47); Name, type, value text and optional values.

## `DurationPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.annotation.client.component.RetentionPeriod`
- **Controller:** `stroom.annotation.client.component.RetentionPeriodController`
- **Template:** `stroom-core-client/src/main/resources/stroom/annotation/client/DurationViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/annotation/client/component/RetentionPeriod.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/annotation/client/DurationPresenter.java (inner DurationView, :56-61)`; `stroom-core-client/src/main/java/stroom/annotation/client/DurationViewImpl.java`; `AnnotationModule.java:86 bindPresenterWidget(DurationPresenter.class, DurationView.class, DurationViewImpl.class)`; `stroom-gwt-workbench .../gin/query/AnnotationScreenModule.java:85-87, content/AnnotationScreenModule.java:85-87`
- **Names:** Duration is free in the repo but reads as java.time.Duration and names only the value; the dialog sets an annotation's retention period (caption "Set Retention Period"), so RetentionPeriod (free).

| Into the template | What | Detail |
| --- | --- | --- |
| DurationViewImpl.ui.xml:9,14 | component-declaration | Checked: TickBox and DurationPicker (wave 2) and FormGroup are already declared |

**Stays in Java:** Default of 5 years when there is none (DurationViewImpl.java:51-53); Retain For enabled unless Keep Forever (:50,76): state.

## `IndexFieldEditPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.index.client.component.IndexFieldEditor`
- **Controller:** `stroom.index.client.component.IndexFieldEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/index/client/view/IndexFieldEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/index/client/component/IndexFieldEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/index/client/presenter/IndexFieldEditPresenter.java (inner IndexFieldEditView, :142-175)`; `stroom-core-client/src/main/java/stroom/index/client/view/IndexFieldEditViewImpl.java`; `Its ChangeUiHandlers implementation (the shared stroom.document.client.event.ChangeUiHandlers stays) becomes setOnTypeChange`; `IndexModule.java:58 bindPresenterWidget(IndexFieldEditPresenter.class, IndexFieldEditView.class, IndexFieldEditViewImpl.class)`; `stroom-gwt-workbench .../gin/editors/IndexScreenModule.java:69-71`
- **Names:** IndexFieldEdit (a verb) renamed IndexFieldEditor after FieldEditor; free.

| Into the template | What | Detail |
| --- | --- | --- |
| IndexFieldEditViewImpl.ui.xml:40 / IndexFieldEditViewImpl.java:149-151 / IndexFieldEditPresenter.java:54-56 | component-declaration | <ix:DenseVectorField ui:field="denseVectorOptions"/> (wave 8, stroom.index.client.component) in place of the SimplePanel slot; the controller attaches DenseVectorFieldController |
| IndexFieldEditViewImpl.ui.xml:11,17,21-38 | component-declaration | Checked: SelectionBox x2, TickBox x4 and FormGroup are already declared |

**Stays in Java:** Type and analyser items (IndexFieldEditViewImpl.java:64-65); Dense vector options visible for DENSE_VECTOR only (IndexFieldEditPresenter.java:139): state; Values.

## `ProcessChoicePresenter`

dialog, presenter; complexity 1, effort S; used by 6.

- **Component:** `stroom.data.client.component.ProcessChoiceDialog`
- **Controller:** `stroom.data.client.component.ProcessChoiceDialogController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/ProcessChoiceViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/client/component/ProcessChoiceDialog.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/ProcessChoicePresenter.java (inner ProcessChoiceView, :64-81)`; `stroom-core-client/src/main/java/stroom/data/client/view/ProcessChoiceViewImpl.java`; `stroom-core-client/src/main/java/stroom/data/client/presenter/ProcessChoiceUiHandler.java (becomes Consumer<ProcessChoice>)`; `StreamStoreModule.java:125-128 bindPresenterWidget(ProcessChoicePresenter...)`; stroom-gwt-workbench .../gin/processing/StreamStoreScreenModule.java:89-91, query/StreamStoreScreenModule.java:89-91, editors/StreamStoreScreenModule.java:90-92, content/StreamStoreScreenModule.java:89-91
- **Names:** ProcessChoice clashes: it is this unit's own result DTO (stroom.data.client.presenter.ProcessChoice), which stays as the model (moved beside the controller, or kept). Component named ProcessChoiceDialog (free).

| Into the template | What | Detail |
| --- | --- | --- |
| ProcessChoiceViewImpl.java:53-54 | structure | <v:ValueSpinner ui:field="priority" min="1" max="100"/> (setMin/setMax take long) |
| ProcessChoiceViewImpl.java:57 | structure | <tickbox:TickBox ui:field="autoPriority" ... value="true"/> |
| ProcessChoiceViewImpl.java:59 | structure | <tickbox:TickBox ui:field="enabled" ... value="true"/> |
| ProcessChoiceViewImpl.ui.xml:12,15,18,21,26,31 | component-declaration | Checked: ValueSpinner, TickBox x3, DateTimeBox x2 and FormGroup are already declared; once wave 4's DateTimeBoxController exists the controller attaches it to each DateTimeBox |

**Stays in Java:** Priority's default 10 (ProcessChoiceViewImpl.java:55): ValueSpinner.setValue is overloaded (Integer, Long) so it can't be a template attribute; the controller sets it; Max create time set to now when specific items are selected or Reprocess changes (ProcessChoicePresenter.java:40, ProcessChoiceViewImpl.java:113-114): state.

## `ExpressionPresenter`

dialog, presenter; complexity 1, effort S; used by 9.

- **Component:** `stroom.data.client.component.ExpressionDialog`
- **Controller:** `stroom.data.client.component.ExpressionDialogController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/ExpressionViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/client/component/ExpressionDialog.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/ExpressionPresenter.java (inner ExpressionView, :78-81)`; `stroom-core-client/src/main/java/stroom/data/client/view/ExpressionViewImpl.java`; `StreamStoreModule.java:121-124 bindPresenterWidget(ExpressionPresenter...)`; `stroom-dashboard-gwt .../dashboard/client/gin/DashboardAppModule.java:133-136`; `stroom-gwt-workbench .../gin/ScreenViewsModule.java:119`; Callers' Provider<ExpressionPresenter>: SteppingPresenter.java:126,167,316, ExecutionScheduleManager.java:97,118,585, ProcessorPresenter.java:78,108,237, MetaPresenter.java:79
- **Names:** Expression clashes (stroom.pipeline.xml.converter.ds3.Expression, stroom.query.language.functions.Expression); named ExpressionDialog (free). It depends on wave 7's data EditExpression (unit 310), whose suggested name EditExpression also clashed with wave 7's dashboard EditExpressionPresenter (unit 444): wave 7 merged the two into `ExpressionEditor` with `ExpressionEditorController`. As its content is only the expression editor, the controller could show an `ExpressionEditorController` directly and skip this component.

| Into the template | What | Detail |
| --- | --- | --- |
| ExpressionViewImpl.ui.xml:4 / ExpressionViewImpl.java:47 / ExpressionPresenter.java:50 | component-declaration | Replace the SimplePanel slot with <d:EditExpression ui:field="expression" addStyleNames="max"/> (wave 7, stroom.data.client.component); the controller does make(editExpressionControllerProvider).attach(view.getExpression()). Until wave 7 splits it, it stays a slot (setExpressionEditor(IsWidget)) |

**Stays in Java:** The expression, data source and field list (read/write).

