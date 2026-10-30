# Wave 8: Composite forms and panes built on the new pickers and editors

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 24 October 2026 from a review of every unit in the wave against the code after master (with `data_grid2` and `copy_and_paste`) was merged in. Line numbers are as of then. Each unit is done when it meets "Done means, for every unit".

> **Since wave 6 (25 October 2026):** the code editor is `CodeEditor` (`stroom.editor.client.component`; declare it, e.g. `<ed:CodeEditor ui:field="editor"/>`, and attach its controller, `make(codeEditorControllerProvider).attach(view.getEditor())`), with `CodeEditorOptions` and `CodeEditorController` (`EditorPresenter` is a thin wrapper over them; `Editor` is only the Ace wrapper inside it); the single line editor is `SingleLineCodeEditor` with `SingleLineCodeEditorController.attach(...)`; the explorer tree is one `ExplorerTree` (no-arg; `ExplorerTickBoxTree` is `setTickBoxes(true)`; `ExtendedExplorerTree` is gone, its overrides `setOnChoose` and `setOnInitialSelect`), filled by `ExplorerTreeController.attach(tree)` (was `ExplorerTreeModel`); `DraggableTreePanel` is concrete, with its callbacks (`setIsValidTarget`, `setOnSelect`, `setOnDragStart`, `setOnDragEnd`, `setOnDragCancel`), but takes no children in a template: its two panels go in with `setTreePanels(tree, subTree)`; `DocumentAssetTree` has a no-arg constructor and `setAccessibleName`; the pickers are a `DropDownBox` with a controller attached (`DocumentAssetSelectionController` in `stroom.document.asset.client.component`, `UserRefSelectionController` in `stroom.security.client.component`; `DropDownView` is gone); the annotation choosers are one-use popups, `ChooserController` and `MultiChooserController`, `show(opener, element, caption, onChoose)`, each opening tracked by an `OpenChooser` so that a second click closes it; the menu is `MenuController`. The plans below keep the old names. See `wave-06.md`.

> **Since wave 4 (25 October 2026):** the date-time, time, schedule and time range boxes' controllers (`DateTimeBoxController`, `TimeBoxController`, `ScheduleBoxController`, `TimeRangeSelectorController`) are already attached by today's presenters, so there is no `setPopupProvider` or `setSchedulePresenterProvider` left to replace: a converted controller makes and attaches them as they do. `CustomFileUpload` is `FileChooser`, with a `FileChooserController` its owner attaches; `ClassificationWrapperView` is `ClassificationWrapper` (`stroom.data.client.component`); the schedule dialog is `SchedulePickerController`. The rows below name what was there when they were written.

> **Since wave 7 (26 October 2026):** Plan B's settings widgets are components with `setOnChange`, and its type settings editors `PlanBTypeSettingsEditor`s (`stroom.planb.client.component`); the secret forms are `SecretForm`s (`stroom.credentials.client.component`; the key store's is a `KeyStoreSecretFormController`, which uploads); the floor map's Set Scale, Timeline Settings, group edit, layer appearance (wave 11's `FloorMapLayerStylePresenter` is done too), dock, Layers panel and canvas are components in `stroom.floormap.client.component`, with `FloorMapSetScaleController`, `FloorMapTimelineSettingsController`, `FloorMapGroupEditController`, `FloorMapLayerStyleController`, `FloorMapDockController` (`addTab(String, IsWidget)`), `FloorMapCanvasController`, and `FloorMapLayers` with no controller; the pipeline's tree is `PipelineTree` and the stepping screen's bar `PipelinePathBar` (attach a `PipelinePathBarController` for its menus), both on `AbstractPipelineTree`; the pathway tree is `PathwayTree` with `PathwayTreeController`; the expression tree is `ExpressionTree` with `ExpressionTreeController` (`init(dataSource, fields)`, `setOnChange`, `setOnSearch`), each `TermEditor` with a `TermEditorController`, and both `EditExpressionPresenter`s are one `ExpressionEditor` with `ExpressionEditorController` (`stroom.query.client.component`; `setFramed(false)` where the owner frames it); `ControllerDialog.show(controller)` shows a controller made once, again and again, through one host; and a `DataGrid` given one of GWT's selection models selects on a press only with a `SingleSelectionEventManager`. The plans below keep the old names. See `wave-07.md`.

Convert the settings forms, editor panes and explorer pickers that embed DocSelectionBox, UserRef, EditorPresenter, Markdown or the explorer tree. Replace embedded DocSelectionBoxPresenter with a DropDownBox in the template plus DocSelectionController. Views stop exposing their widgets, and onReadOnly becomes setReadOnly.

## Audited (26 October 2026)

Seven reviewers read the wave against this plan, "Done means" and AGENTS.md (the explorer chooser
and its trees, the settings tabs, the forms and dialogs, the query panes, the editors and Markdown,
the XSD browser, content pack details and floor map timeline, and the records). Fixed:

- **A regression on this branch.** An XSD's occurrences (`0..*` over a sequence) lost their class
  in the stylesheet's rename: `XSDDisplay` still wrote `occuranceLabel` in its HTML, so they showed
  as plain text below their images. Now `xsd-browser__occurrence-label` (story
  `XsdBrowser.Occurrences`).
- **Three bugs master has too**, each with a changelog entry and a story that failed before:
  - An Elastic index's Rerank Score Field Suffix now makes the index changed
    (`ElasticIndexEditor.RerankScoreSuffixChange`; found in wave 8 and left, now fixed).
  - A read-only pathways document's Trace Store and Info Feed pickers no longer open
    (`PathwaysEditor.ReadOnly`).
  - The Markdown preview (read and edited) renders again in the new theme when the theme changes:
    the preferences' change re-rendered it only if its text had changed, and the edited preview
    didn't listen at all (`MarkdownEditor.ThemeChange`).
- **Names (rule 4).** Callbacks are `setOnX`: `ExplorerPopup.setOnFilterDown`,
  `EntityCheckTreeController.setOnFilterError`, `ExplorerPopupController.setOnSelectionChange`,
  the timeline's `setOnPlayStateChange`, `setOnClearAnimationState`, `setOnDiscontinuity` and
  `setOnTimeRangeChange`; `QueryHelp.setButtonsEnabled`. `QueryTextPane` follows its HTML's links
  itself and tells its controller with `setOnLink` (the controller handled the DOM click).
  `MarkdownTabProvider` takes a `Supplier`, as `ControllerTabProvider` does.
- **Templates and styles.** The index's "No partition" is in its template (`nonSelectString`); the
  floor map event store's condense warning has its own sheet,
  `floor-map-event-store-settings.css` (`floor-map-event-store-settings__condense-warning`); the
  XSD browser's box title lost a modifier with no rule (`--no-content`); `LinkButton`'s javadoc
  example is the XSD browser's own.
- **Owned work.** The chooser's quick filter help, the table builder's node list failure and the
  XSD browser's Home, Back and Forward (before a schema is set) do nothing once there is nothing to
  do it for; the error feed's config lookup and Set Default use the task monitor given, else a
  spinner of their own (`getProvidedTaskMonitor()` could be null). Firing an alert from a
  controller after it is disposed still reaches the app's bus, so the chooser's Create path is
  left as it is.
- **Smaller things.** Five components and controllers had each other's copyright year
  (`QueryToolbar`, `ColumnFilterEditor`, `AiConfigGeneral`, `CommentEdit`, `DataGenSettings`; a
  sweep of the wave found no others); a final local in `ErrorFeedController`;
  `MarkdownEditController.setReadOnly`'s javadoc; `java.util.function.Consumer` written out in the
  timeline; the table builder story's counter reset for each story, and two story comments.
- **Stories.** Typing in the Documentation tab and a data generator's template enable Save; Cancel
  in a column filter leaves the query unchanged; the Elastic index's read-only fields; the content
  pack details' links are underlined.
- **The records.** Done's counts (the wrapper's callers, the templates that declare a picker, the
  force graphs among the changed screenshots), its list of the screens the plans gave no
  controller, the CSS kept and the two `TextPresenter` sections; the order's rows;
  `COMPONENT_MODEL_DESIGN.md` (the Documentation tab tells its document with `markChanged()`, and
  `MarkdownTabProvider` takes the bus again), `COMPONENTS.md` (`DocSelectionPopup`), AGENTS.md
  (`AriaPanel`'s value setters), the later plans' note on `MarkdownTabProvider`, and five of
  ISSUES.md's references to deleted files.

Left: pre-existing bugs, in ISSUES.md (the dashboard filter dialog's focus, the column filter's
dead condition, the time preferences' unreported edits, a rule switched to Table Builder not read,
the email recipients reported only on a key up, the source pane's unused stepping button, debug
logging carried over, and the stepping chooser left unowned if its lookups fail); GWT's `TextBox`
in templates, as in the worked examples (rule 2 asks for `TextInput` where ARIA is needed); the
query toolbar's `getQueryButtons`, the timeline's help button made in Java and its classes not BEM
yet, the query help's rule in `quick-filter.css`, and the content pack's late reply dropped once
closed (as `.owner` means). Stories not added: the one-use chooser's Cancel, double click and an
invalid folder; the stepping button's plays; the model pickers and Ask Stroom AI's dock type;
the rule set's and dictionary's Documentation tabs; the email's validation and Send Test Email;
moving the timeline's slider and its chevrons; the content pack's credentials dialog; the error
feed's Set Default; Test Connection; edit enables Save for the text converter, Lucene index and
analytic rule. History: `ce28951a50` left the client tests not compiling until `4b41c1a3ad`, and
`734bf3d636` the dashboard until `787c1eeede`.

All 1,668 stories passed with the leak check after the fixes, the 38 screenshots that differed
being the noise named in Done, below, and `PathwaysEditor.ReadOnly` (its pickers now look read
only); the 4 stories added after that run pass with the leak check, their screenshots unchanged
elsewhere, and the 6 new stories have baselines. The app and the standalone dashboard GWT compile.

## Done (26 October 2026)

All units but `DocSelectionBoxPresenter`, in 25 commits: 20 classes in later waves (24 pickers)
still use that wrapper, so it stays, as its plan below allows; 9 templates here declare a
`DropDownBox` and attach a `DocSelectionController`, and 12 classes stopped using the wrapper. Each commit ran its stories with the leak check and
the screenshot diff; the last ran all of them (1,666 passed, one flaky force graph passing on its second try; the 43 screenshots that differed are the force graphs (40), the Tree visualisation's `Keyboard`, the data grid's spinner column and AppShell's `OpensDocument`, all recorded as noise in ISSUES.md; the 4 baselines missing were the old `TextPresenter` stories' names), and the app and the standalone dashboard GWT
compile. Three bugs fixed on the way, each with a changelog entry and a story (master has each; see
"Fixed" in ISSUES.md): the floor map event store's Event Expiry fields had no accessible names; an XML
schema's Home, Back and Forward threw when nothing had been shown; and the content pack details'
links were told apart from the text beside them by colour alone. One bug found and left (ISSUES.md):
an Elastic index's Rerank Score Field Suffix doesn't make the document changed (fixed in the audit,
above). What was decided on
the way, where it differs from the plans below:

- **Hosting controllers in documents not yet converted.** `ControllerTabProvider` (in
  `stroom.entity.client.presenter`) shows a controller as a `DocTabPresenter`'s tab through a small
  `DocPresenter` that owns it, so it sees the document's read-only state and tells it of changes
  with `markChanged()`; it takes a `Supplier` (the hosts use both GIN's and javax's providers), and
  `isPresenter(content)` tells whether a shown presenter is the tab's without making it.
  `MarkdownTabProvider` does the same for the Documentation tab, passing on its toolbar buttons, and
  now takes the event bus (`getEventBus()` at its 30 call sites) and, since the audit, a `Supplier`
  too (`markdownEditProvider::get`). Both go with wave 14, once the
  hosts are `DocumentEditor`s.
- **Every screen is a pair.** The plans gave no controller to the XML schema, text converter and
  floor map event store settings, the time preferences, the table analysis tab and the XSD browser,
  and suggested Edit Comment's could go; each has one, as a document tab or dialog needs one (read-only, `markChanged`, a dialog's owner), and
  AGENTS.md asks for a pair.
- **Read-only.** `Contexts.READ_ONLY` is true where nothing gives it, so a controller hosted where it
  isn't given (the Documentation tab's other hosts, the rule set screen, the annotation) is told with
  `setReadOnly`, as before; `MarkdownEditController.followReadOnly()` watches it instead, for a
  `DocumentEditor`'s tab. Stories that show a converted tab give the story's root `READ_ONLY`.
- **The explorer chooser.** `ExplorerPopupController.show(consumer)` reuses one (for
  `DocSelectionController` and the screens that keep one); `showOnce(opener, ...)` makes one for one
  use. `DocSelectionPopup` is gone; its one job, `setIncludeNullSelection(false)`, is public and its
  callers call it. `PipelinePlugin` injects `AppRoot` to own its chooser while shown. The export
  dialog's tick tree is an `ExplorerTree` it declares, with `EntityCheckTreeController` attached and
  no component of its own.
- **Pickers.** A `DropDownBox` beside a button in a `button-group` stays in a plain `SimplePanel`, as
  the slot was: directly in the flex group it is stretched 1px to the button's height.
- **Code editors.** Declared in the bordered `SimplePanel` their slot was, not given its classes, so
  the pages don't change; their mode is set by their controller (`CodeEditor` has no mode setter).
  The data viewer's text pane drops its option pass-throughs for `getOptions()`. The query result's
  source pane declares its editor and HTML pane, one shown; the Markdown editor declares its editor
  and its rendered page, one shown, so a hidden Ace editor is in the page (the Documentation story
  checks it is hidden, not absent).
- **Frames.** The Markdown preview and the rendered documentation are the wave-3 `IFrame` component
  declared in their templates, not `IFramePresenter` (a presenter can't be declared, and its title
  poll is for tabs showing a page's title). UiBinder reserves `id`, so their controllers set it.
- **Dialogs made for each use** (`ControllerDialog.showOnce`): the column filter (its helper is given
  GIN's provider and the table as opener), Edit Comment (OK can keep it open when the save fails) and
  the content pack's credentials (`makeDialog`); they were made once and kept.
- **Kept, not as planned.** The credential settings show the secret form in a slot, made by wave 7's
  `SecretForms` (shared with Replace Secret, keeping what was typed for each type), rather than
  declaring all five; the query help keeps `linkToEditor(EditorPresenter)` on its controller, as
  both its owners still hold one (revisit when their editors are `CodeEditorController`s).
- **Templates.** Spinner bounds, steps and names (`minValue`, `maxValue`, `delta`,
  `accessibleName`), icons (`icon="REFRESH"`, `svg="ERROR"`), a selection list's
  `keyboardSelectionPolicy` and fixed ARIA are in templates; a `ValueSpinner`'s first value stays in
  Java (its `setValue` overloads are ambiguous to UiBinder). `AriaPanel` has `ariaValueMin`,
  `ariaValueMax` and `ariaValueNow`, for the floor map timeline's slider. Width attributes became
  `text-field--narrow`/`--wide` and `value-spinner--narrow`/`--wide` (or went, where the class was
  already that wide), and `width="100%"` on a text area `w-100`.
- **Stylesheets.** New: `query-toolbar.css` (the errors hidden by a modifier on their toolbar,
  `query-toolbar__errors--hidden`) and `analytic-email-destination.css`, where the plans said
  `dashboard.css` and `components.css`, as AGENTS.md asks for a component's own sheet;
  `xsdbrowser.css` is `xsd-browser.css`, BEM with flat selectors (the box title's states modifiers
  added in Java, no longer GWT dependent names); the query help's rule is `.query-help` in
  `quick-filter.css`'s grouped rule. The floor map timeline's progress is
  `--stroom-floormap-timeline__progress` (BEM, rather than the plan's `--timeline-progress`); its
  classes aren't BEM yet. Class names with no rules (`CommentEditViewImpl`, `TextView`,
  `QueryHelpViewImpl-details` and `-buttons`, the XSD browser's `bar`) went, with rules or with
  an empty one. Kept, as other screens share them or until their dialogs convert (rule 6): the
  content pack details' `contentstore-details*` and the credentials' `credentials-details-dialog`
  and `wide-control` in `components.css`, `durationSetting` (Plan B's settings too), and
  `queryHelpDetail` (the code editor's tooltip too).
- **Stories.** 17 new, for what no story showed or pressed: Set As Default in the Ask Stroom AI
  settings, a dense vector field's save, a table builder rule's Execution tab (editable and read only: its node, progress, Enabled and Refresh), a
  search's warnings, the source pane's Close, Insert into Query, an email template's Test Template,
  Upgrade in the content store, the XML schema's Graphical tab, the timeline's speed and help, the
  floor map event store settings (4) and the date and time preferences; the data text pane's 4 are
  its old `TextPresenter` stories, renamed. Of the two `TextPresenter` sections below, the first
  (the data viewer's, 307 lines) has no plan of its own, and the second has the query pane's
  figures (880 lines) but the data viewer's plan: the data viewer's became `DataTextPane`, the
  query's `QueryTextPane`; the order's rows have the same mix-up in their "Becomes" column. Two fixtures were wrong in a
  way that hid what they were for (the query editor's source reply's type, the content store's
  upgrade reply's message); the stories that build editors by hand are owned by a harness.

## CSS and template notes for the wave

- New size modifiers instead of width="..." in templates (none exist today): text-field--narrow (80px) and text-field--wide (400px) in text-field.css beside text-field--cell; value-spinner--narrow (80px) and value-spinner--wide (200px) in value-spinner.css beside value-spinner--cell. Used by ElasticIndexSettings, FeedSettings, XMLSchemaSettings, IndexSettings, DenseVectorField, AiConfigGeneral and AiConfigTableAnalysis.
- Delete width="200px" on a TextBox: TextBox carries .text-field (TextBox.java:78), which is already 200px (text-field.css:34). Delete width="200px" on a Button in a form: .form > .Button has min-width: 200px (form.css:69-72).
- width="100%" on a TextArea becomes w-100 (stroom.css:843); 7 TextAreas in AiConfigGeneral, AiConfigTableAnalysis and ReportSettingsTab.
- Bordered panel without padding: form-control-border form-control-background (components.css:1227, 1235), as EntityTree and ColumnFilterEditor already use.
- Screen-reader-only text: sr-only (spinner.css:126). stroom-floormap-visually-hidden (stroom-floormap.css:547) does the same; merge it later (removals wait for other work).
- Runtime positions use a custom property, e.g. FloorMapTimeline's --timeline-progress on the bar, read by the inner bar (width), handle (left) and scrub tooltip (left) in stroom-floormap.css.
- Fixed ARIA on a plain panel (role, aria-live, aria-label): UiBinder sets a widget's attributes through setters, so use a g:HTMLPanel root with a plain <div ui:field=... role=... aria-...> (DivElement field). Needed by ExplorerPopup (status), XSDBrowser (navigation group) and FloorMapTimeline (bar, speed badge, status). No live-region widget exists to reuse.
- Icons go in templates as enum attributes: icon="STEPPING" on FabButton, icon="TICK"/"REFRESH" on Button (Button.setIcon, FabButton.setIcon), svg="ERROR" on ToolbarButton, as svg="CLOSE" is used today.
- CSS classes named after deleted ViewImpls are renamed to the component's BEM block: QueryToolbarViewImpl-outer/-inner/-errors (dashboard.css:66,72,80,142) to query-toolbar; QueryHelpViewImpl (quick-filter.css:97) to query-help; AnalyticEmailDestinationViewImpl (components.css:1782-1795) to analytic-email-destination. QueryHelpViewImpl-details, QueryHelpViewImpl-buttons and CommentEditViewImpl have no rules, so they are dropped.
- Visibility-only hiding (keeps layout, leaves the tab order): new state class query-toolbar__errors--hidden { visibility: hidden; } in dashboard.css. No Stroom class does this, and Bootstrap's invisible is not to be used.
- Bootstrap flex-grow-1 (VisualisationSettingsViewImpl.ui.xml:17) becomes dock-max (stroom.css).
- Twelve units in this wave replace DocSelectionBoxPresenter slots (SimplePanel + set*View) with <d:DropDownBox ui:field=.../> in the template plus make(docSelectionControllerProvider).attach(view.getXBox()), after DocSelectionController moves to stroom.explorer.client.component.
- The wave-6 code editor is written CodeEditor here: Editor is taken (stroom.editor.client.view.Editor), and wave 6's text already says 'a dumb CodeEditor'. It is declared in 10 templates (WordsTab, DataTextPane, QueryTextPane, DataGenSettings, ColumnFilterEditor, VisualisationSettings, AnalyticEmailDestination, CommentEdit, MarkdownPreview), so a mode attribute (mode="JSON") on it would let fixed modes move into templates.
- ValueSpinner needs setAccessibleName (the wave-2 contract) for TimePreferences' accessibleName="Hours"/"Minutes".

## `TextPresenter`

composite, presenter; complexity 2, effort S; used by 5.

No plan yet (a second unit of the same name, or new with the merge): plan it as it is converted.

## `ColumnFilterPresenter`

composite, presenter; complexity 1, effort S; used by 5.

- **Component:** `stroom.dashboard.client.component.ColumnFilterEditor`
- **Controller:** `stroom.dashboard.client.component.ColumnFilterEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/table/ColumnFilterViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/ColumnFilterEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/table/ColumnFilterPresenter.java (nested ColumnFilterView)`; `stroom-core-client/src/main/java/stroom/dashboard/client/table/ColumnFilterViewImpl.java`; `TableModule.java:80 bindPresenterWidget(ColumnFilterPresenter.class, ColumnFilterView.class, ColumnFilterViewImpl.class)`
- **Names:** ColumnFilter clashes with stroom.query.api.ColumnFilter (stroom-query/stroom-query-api), the DTO this dialog edits (ColumnFilterPresenter.java:70-87). Chosen ColumnFilterEditor / ColumnFilterEditorController, matching the pilot's dialog names (FieldEditor, RuleEditor); the controller gets edit(owner, column, onChange) shown with ComponentDialog.showOnce.

| Into the template | What | Detail |
| --- | --- | --- |
| ColumnFilterPresenter.java:45 / ColumnFilterViewImpl.ui.xml:9 | component-declaration | <e:CodeEditor ui:field="editor" styleName="dock-max form-control-border form-control-background"/> (wave 6) in place of <g:SimplePanel ui:field="editorContainer"> and setEditor(EditorView) |

**Stays in Java:** dialog caption "Filter '" + column name + "'" (ColumnFilterPresenter.java:52): built from data; filter expression text.

## `XSDBrowserPresenter`

tab, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.widget.xsdbrowser.client.component.XSDBrowser`
- **Controller:** `none`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/xsdbrowser/client/view/XSDBrowserViewImpl.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/component/XSDBrowser.ui.xml`
- **Goes:** `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/presenter/XSDBrowserPresenter.java (and its nested XSDBrowserView)`; `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/presenter/XSDBrowserUiHandlers.java`; `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/view/XSDBrowserViewImpl.java`; `XMLSchemaModule.java:38 bindPresenterWidget(XSDBrowserPresenter.class, XSDBrowserView.class, XSDBrowserViewImpl.class)`
- **Names:** XSDBrowser is free (no class or other unit of that name). Dumb: back/forward/home just call the XSDModel, so no controller; read(model) keeps the model's selection registration and replaces it on the next read. XSDDisplay and XSDConstraintDisplay stay in stroom.widget.xsdbrowser.client.view.

| Into the template | What | Detail |
| --- | --- | --- |
| XSDBrowserViewImpl.java:55-56 | aria | role="group" aria-label="Schema navigation" on the navigation bar: make it a plain <div class="dock-min bar" role="group" aria-label="Schema navigation"> inside a g:HTMLPanel holding the three LinkButtons |
| XSDBrowserViewImpl.ui.xml:8-23 | component-declaration | LinkButton x3, SplitPane x2 and XSDDisplay already declared; keep |

**Stays in Java:** setSelectedNode: documentation text and constraints from the node (XSDBrowserViewImpl.java:73-79).

**Stylesheet (from wave 2).** `xsdbrowser.css` is this block's, and isn't BEM yet: rename it `xsd-browser.css`, with the block `xsd-browser` (now `xsdBrowser`) and its elements and modifiers in BEM form, e.g. `xsd-browser__box`, `xsd-browser__box-title` (`--selected`, `--selectable`, `--no-content`), `xsd-browser__node-grid` and its `__cell` modifiers, `xsd-browser__node-label`, `xsd-browser__occurrence`, and the diagram's images (`xsdBrowser-xsdTree01` to `11`, `xsdBrowser-xsdTitle*` and the node images). Wave 2 left the names as they were (so `XSDDisplay` and `XSDDisplayBox`, wave 2, use them) and added `boxTitle-text`, `boxTitle-selectable`, `xsdBrowser-occurrence`, `xsdBrowser-nodeGrid__cell--start`/`--end`/`--top`/`--bottom`/`--separator` and `--xsd-node-grid__columns` in the same style, to be renamed with the rest here.

## `TextPresenter`

composite, presenter; complexity 4, effort L; used by 3.

- **Component:** `stroom.data.client.component.DataTextPane`
- **Controller:** `stroom.data.client.component.DataTextPaneController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/TextViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/client/component/DataTextPane.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/TextPresenter.java (nested TextView)`; `stroom-core-client/src/main/java/stroom/data/client/view/TextViewImpl.java`; `stroom-core-client/src/main/java/stroom/data/client/presenter/TextUiHandlers.java`; `StreamStoreModule.java:113-116 bindPresenterWidget(TextPresenter.class, TextView.class, TextViewImpl.class)`; `DashboardAppModule.java:157-160 bindPresenterWidget(TextPresenter.class, TextView.class, TextViewImpl.class)`
- **Names:** `Text` clashes with unit 182, wave 13's dashboard Text and the DOM Text node; chosen DataTextPane / DataTextPaneController (the data viewer's text body, used by DataPresenter and SourcePresenter). Its template is only stroom/data/client/view/TextViewImpl.ui.xml, not the dashboard or query ones the record lists. The controller only attaches the wave-6 editor controller and sets its options; its dozen option pass-throughs (TextPresenter.java:97-147) go once callers use the editor settings model.

| Into the template | What | Detail |
| --- | --- | --- |
| data/client/view/TextViewImpl.java:45 | aria | icon="STEPPING" on <button:FabButton ui:field="steppingButton"> (title already there) |
| TextPresenter.java:69 / TextViewImpl.ui.xml:6 | component-declaration | <e:CodeEditor ui:field="editor" styleName="max"/> (wave 6) in place of <g:SimplePanel ui:field="textContainer"> and setTextView(View) |
| TextViewImpl.ui.xml:7 | component-declaration | FabButton already declared; keep |

**Stays in Java:** stepping button visibility (TextViewImpl.java:60); editor option set-up (TextPresenter.java:52-67): read-only, no line numbers or indicators, wrap on, format unavailable, on the wave-6 settings model.

## `SqlTemporalStoreSettingsPresenter`

tab, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.sqlstore.client.component.SqlTemporalStoreSettings`
- **Controller:** `stroom.sqlstore.client.component.SqlTemporalStoreSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/sqlstore/client/view/SqlTemporalStoreSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/sqlstore/client/component/SqlTemporalStoreSettings.ui.xml`
- **Goes:** `SqlTemporalStoreSettingsPresenter.java (nested SqlTemporalStoreSettingsView)`; `stroom-core-client/src/main/java/stroom/sqlstore/client/presenter/SqlTemporalStoreSettingsUiHandlers.java`; `stroom-core-client/src/main/java/stroom/sqlstore/client/view/SqlTemporalStoreSettingsViewImpl.java`; SqlTemporalStoreModule.java:34-37 bindPresenterWidget(SqlTemporalStoreSettingsPresenter.class, SqlTemporalStoreSettingsView.class, SqlTemporalStoreSettingsViewImpl.class)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| SqlTemporalStoreSettingsViewImpl.ui.xml:23-27 | component-declaration | FormGroup and Button already declared; keep |

**Stays in Java:** entry count text (SqlTemporalStoreSettingsViewImpl.java:55): data; reset enabled by read-only (:60); REST count/clear and confirm (SqlTemporalStoreSettingsPresenter.java:63-87) in the controller.

## `AnalyticEmailDestinationPresenter`

composite, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.analytics.client.component.AnalyticEmailDestination`
- **Controller:** `stroom.analytics.client.component.AnalyticEmailDestinationController`
- **Template:** `stroom-core-client/src/main/resources/stroom/analytics/client/view/AnalyticEmailDestinationViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/analytics/client/component/AnalyticEmailDestination.ui.xml`
- **Goes:** `AnalyticEmailDestinationPresenter.java (nested AnalyticEmailDestinationView)`; `stroom-core-client/src/main/java/stroom/analytics/client/view/AnalyticEmailDestinationViewImpl.java`; AnalyticsModule.java:73-75 bindPresenterWidget(AnalyticEmailDestinationPresenter.class, AnalyticEmailDestinationView.class, AnalyticEmailDestinationViewImpl.class)
- **Names:** No clash (NotificationEmailDestination is the DTO). It has both ChangeUiHandlers and an onChange Runnable; only setOnChange stays.

| Into the template | What | Detail |
| --- | --- | --- |
| AnalyticEmailDestinationViewImpl.java:61-62 | aria | icon="TICK" on both Test Template buttons |
| AnalyticEmailDestinationPresenter.java:76-77 / AnalyticEmailDestinationViewImpl.ui.xml:33,46 | component-declaration | two <e:CodeEditor ui:field="subjectTemplate\|bodyTemplate" addStyleNames="dock-max form-control-border"/> (wave 6) in place of the SimplePanels and set*TemplateEditorView(View) |
| AnalyticEmailDestinationPresenter.java:198 | structure | mode="TEXT" on both CodeEditors, if wave 6 gives them a mode setter |
| AnalyticEmailDestinationViewImpl.ui.xml:6 | css-class | AnalyticEmailDestinationViewImpl (components.css:1782-1795) renamed to analytic-email-destination, as the ViewImpl goes |

**Stays in Java:** editor options (AnalyticEmailDestinationPresenter.java:199-205) on the wave-6 settings model in the controller; to/cc/bcc values, test REST calls and confirm.

## `CommentEditPresenter`

composite, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.annotation.client.component.CommentEdit`
- **Controller:** `stroom.annotation.client.component.CommentEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/annotation/client/CommentEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/annotation/client/component/CommentEdit.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/annotation/client/CommentEditPresenter.java (nested CommentEditView)`; `stroom-core-client/src/main/java/stroom/annotation/client/CommentEditViewImpl.java`; `AnnotationModule.java:87 bindPresenterWidget(CommentEditPresenter.class, CommentEditView.class, CommentEditViewImpl.class)`
- **Names:** No clash. Its only user is AnnotationEditPresenter (:160,195). The controller just attaches the wave-6 editor controller; if the owner does that, the controller can go.

| Into the template | What | Detail |
| --- | --- | --- |
| CommentEditPresenter.java:38 / CommentEditViewImpl.ui.xml:4 | component-declaration | <e:CodeEditor ui:field="editor" styleName="max form-control-border"/> (wave 6) in place of <g:SimplePanel ui:field="content"> and setContent(View) |
| CommentEditViewImpl.ui.xml:4 | css-class | drop CommentEditViewImpl (no CSS rule uses it) |

**Stays in Java:** comment text (setText/getText).

## `XMLSchemaSettingsPresenter`

tab, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.xmlschema.client.component.XMLSchemaSettings`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/xmlschema/client/view/XMLSchemaSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/xmlschema/client/component/XMLSchemaSettings.ui.xml`
- **Goes:** `XMLSchemaSettingsPresenter.java (nested XMLSchemaSettingsView)`; `stroom-core-client/src/main/java/stroom/xmlschema/client/view/XMLSchemaSettingsViewImpl.java`; XMLSchemaModule.java:36-37 bindPresenterWidget(XMLSchemaSettingsPresenter.class, XMLSchemaSettingsView.class, XMLSchemaSettingsViewImpl.class)
- **Names:** No clash (XmlSchemaDoc is the DTO). Dumb document tab, so no controller.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/resources/stroom/xmlschema/client/view/XMLSchemaSettingsViewImpl.ui.xml:10 | `<g:TextBox ui:field="namespaceURI" width="400px"/>` | static | text-field--wide (text-field.css, new) |
| XMLSchemaSettingsViewImpl.ui.xml:15 | `<g:TextBox ui:field="systemId" width="400px"/>` | static | text-field--wide |
| XMLSchemaSettingsViewImpl.ui.xml:20 | `<g:TextBox ui:field="schemaGroup" width="400px"/>` | static | text-field--wide |

| Into the template | What | Detail |
| --- | --- | --- |
| XMLSchemaSettingsViewImpl.ui.xml:37 | component-declaration | TickBox and FormGroup already declared; the view stops exposing its TextBox/TickBox getters (XMLSchemaSettingsPresenter.java:49-59) |

**Stays in Java:** field values.

## `MarkdownPreviewPresenter`

composite, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.entity.client.component.MarkdownPreview`
- **Controller:** `stroom.entity.client.component.MarkdownPreviewController`
- **Template:** `stroom-core-client/src/main/resources/stroom/entity/client/view/MarkdownPreviewViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/entity/client/component/MarkdownPreview.ui.xml`
- **Goes:** `MarkdownPreviewPresenter.java (nested MarkdownPreviewView)`; `stroom-core-client/src/main/java/stroom/entity/client/view/MarkdownPreviewViewImpl.java`; `EntityModule.java:64-66 bindPresenterWidget(MarkdownPreviewPresenter.class, MarkdownPreviewView.class, MarkdownPreviewViewImpl.class)`
- **Names:** No clash. Its value/format handlers become setOnChange; the controller keeps the convert timer and the wave-6 editor controller.

| Into the template | What | Detail |
| --- | --- | --- |
| MarkdownPreviewPresenter.java:82 / MarkdownPreviewViewImpl.ui.xml:7 | component-declaration | <e:CodeEditor ui:field="editor" styleName="max dashboard-panel"/> (wave 6) in place of editorContainer and setViews |
| MarkdownPreviewPresenter.java:82 / MarkdownPreviewViewImpl.ui.xml:9 | component-declaration | <i:IFramePresenter ui:field="preview" styleName="max dashboard-panel"/> (wave 3) in place of previewContainer |
| MarkdownPreviewPresenter.java:78-79 | aria | id="markdown-preview-frame" and accessibleName="Preview" on the IFramePresenter |
| MarkdownPreviewPresenter.java:76 | structure | mode="MARKDOWN" on the CodeEditor, if wave 6 gives it a mode setter |
| MarkdownPreviewViewImpl.ui.xml:6,8 | css-class | drop QueryHelpViewImpl and QueryHelpViewImpl-details, borrowed from the query help and doing nothing here (no quick filter; -details has no rule) |
| MarkdownPreviewViewImpl.ui.xml:5 | component-declaration | SplitPane already declared; keep |

**Stays in Java:** line wrap on (MarkdownPreviewPresenter.java:75) on the editor settings model; sandbox ALLOW_POPUPS (:80) unless IFramePresenter gets a template setter; srcdoc HTML and scroll (:105-115).

## `WordsTab`

tab, smart class; complexity 1, effort S; used by 1.

- **Component:** `stroom.dictionary.client.component.WordsTab`
- **Controller:** `stroom.dictionary.client.component.WordsTabController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/dictionary/client/component/WordsTab.ui.xml`
- **Goes:** `the Controller half of WordsTab.java moves to WordsTabController (PresenterAdapter<EditorPresenter> at :32-47 goes)`
- **Names:** WordsTab is this class itself (no other clash). Today it is a Controller wrapping EditorPresenter; it becomes the component (template with the code editor) and WordsTabController attaches the wave-6 editor controller and keeps setOnChange/setWords/getWords. The wave-6 code editor is named CodeEditor here, as Editor is taken by stroom.editor.client.view.Editor.

| Into the template | What | Detail |
| --- | --- | --- |
| WordsTab.java:38,47 | component-declaration | <e:CodeEditor ui:field="editor"/> (wave 6) in place of PresenterAdapter.of(editorPresenterProvider) and initWidget(editor.asWidget()) |
| WordsTab.java:41 | structure | mode="TEXT" on the CodeEditor, if wave 6 gives it a setMode(AceEditorMode) setter |

**Stays in Java:** WordsTab.java:43-44 styles/format unavailable (editor settings model, set by the controller); setWords/getWords values.

## `FloorMapTimelineViewImpl`

widget, tidy; complexity 3, effort M; used by 1.

- **Component:** `stroom.floormap.client.component.FloorMapTimeline`
- **Controller:** `stroom.floormap.client.component.FloorMapTimelineController`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapTimelineViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapTimeline.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapTimelineViewImpl.java (binding listed under unit 166)`
- **Names:** The record's 'stays FloorMapTimelineViewImpl' goes against rule 1: it is a presenter's ViewImpl, so with unit 166 it becomes the component FloorMapTimeline (controller from unit 166). setScrubHandler/setCommitHandler/setNudgeHandler/set*Handler become setOnScrub/setOnCommit/setOnNudge/setOnPlayPause etc.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapTimelineViewImpl.java:269 | `innerBar.getElement().getStyle().setWidth(pct, Unit.PCT)` | dynamic | --timeline-progress (e.g. "42.5%") set once on the outer bar; .stroom-floormap-timeline-inner-bar { width: var(--timeline-progress, 0%); } in stroom-floormap.css:263 |
| FloorMapTimelineViewImpl.java:270 | `handle.getElement().getStyle().setLeft(pct, Unit.PCT)` | dynamic | --timeline-progress; .stroom-floormap-timeline-handle { left: var(--timeline-progress, 0%); } (stroom-floormap.css:272) |
| FloorMapTimelineViewImpl.java:271 | `scrubTooltip.getElement().getStyle().setLeft(pct, Unit.PCT)` | dynamic | --timeline-progress; .stroom-floormap-timeline-scrub-tooltip { left: var(--timeline-progress, 0%); } (stroom-floormap.css:287) |

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapTimelineViewImpl.java:137-162 | component-declaration | the four ToolbarButtons declared in the playback and settings Toolbars with svg and title (STEP_BACKWARD "Step Back", PLAY "Play", STEP_FORWARD "Step Forward", SETTINGS "Playback Settings") instead of playbackToolbar.button(...)/settingsToolbar.button(...) |
| FloorMapTimelineViewImpl.java:143,149,155,162 | css-class | stroom-floormap-timeline-step-button / -play-button / -step-button / -settings-button on those buttons |
| FloorMapTimelineViewImpl.java:166-169 | aria | speed badge: title="Playback Speed", role="button", tabindex="0" (as a plain <div ui:field="speedBadge"> in a g:HTMLPanel root, since UiBinder can't set role on a g:Label) |
| FloorMapTimelineViewImpl.java:191-196 | aria | outer bar: role="slider" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0" tabindex="0" (plain <div ui:field="outerBar"> in the HTMLPanel; its mouse/key/focus handlers added on the root and filtered to the bar) |
| FloorMapTimelineViewImpl.java:233-236 | text | «/» chevron text and title="Object out of range. Extend timeline range to view object." on both out-of-range labels |
| FloorMapTimelineViewImpl.java:241-242 | aria | aria-hidden="true" on both out-of-range chevrons |
| FloorMapTimelineViewImpl.java:246-249 | aria | timelineStatus: role="status" aria-live="polite" aria-atomic="true" |
| FloorMapTimelineViewImpl.java:224-231 / .ui.xml:28 | component-declaration | <h:Histogram ui:field="histogram" addStyleNames="stroom-floormap-timeline-histogram-container"/> (wave 2) in place of the SimplePanel filled with new Histogram() |
| FloorMapTimelineViewImpl.ui.xml:67 | css-class | stroom-floormap-visually-hidden duplicates sr-only (spinner.css:126); reuse sr-only once removals are allowed |

**Stays in Java:** aria-valuenow/aria-valuetext/aria-label of the bar built from position and dates (FloorMapTimelineViewImpl.java:274,281,306-314); speed badge text and its aria-label "Playback speed ×N, opens the speed menu" (:389-402); play/pause icon/title, scrub tooltip text, date labels, out-of-range visibility and live text (:322-453); scrub-tooltip--visible state class (:517-519); drag and keyboard behaviour.

## `MarkdownEditPresenter`

tab, presenter; complexity 3, effort M; used by 34.

- **Component:** `stroom.entity.client.component.MarkdownEdit`
- **Controller:** `stroom.entity.client.component.MarkdownEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/entity/client/view/MarkdownEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/entity/client/component/MarkdownEdit.ui.xml`
- **Goes:** `MarkdownEditPresenter.java (nested MarkdownEditView)`; `stroom-core-client/src/main/java/stroom/entity/client/view/MarkdownEditViewImpl.java`; `EntityModule.java:61-63 bindPresenterWidget(MarkdownEditPresenter.class, MarkdownEditView.class, MarkdownEditViewImpl.class)`
- **Names:** No clash (wave 14's Documentation is a different screen). The injected EditorPresenter (MarkdownEditPresenter.java:78,85) is never used: drop it. MarkdownTabProvider (29 callers) must host it as a TabbedComponent tab.

| Into the template | What | Detail |
| --- | --- | --- |
| MarkdownEditPresenter.java:142,146 / MarkdownEditViewImpl.ui.xml:4 | component-declaration | <c:MarkdownPreview ui:field="editor" styleName="max dashboard-panel" visible="false"/> (unit 772, same wave) and <i:IFramePresenter ui:field="preview" styleName="max dashboard-panel"/> (wave 3 widget) declared side by side, one shown, in place of the SimplePanel and setView(View) |
| MarkdownEditViewImpl.java:47 | css-class | dashboard-panel added in Java to whichever view is set: in the template on both declared children |
| MarkdownEditPresenter.java:92-94 | aria | id="markdown-frame" and accessibleName="Preview" on the IFramePresenter (sandbox with ALLOW_POPUPS stays a call unless IFramePresenter gets a template setter) |
| MarkdownEditPresenter.java:116-122 | structure | the document Toolbar ("Documentation actions") with the Edit Documentation toggle (svg EDIT) and Help button (svg HELP, "Help with Documentation (opens in new tab)"): optional small template MarkdownEditToolbar.ui.xml, as it is shown in the document's button bar, not in this widget |

**Stays in Java:** edit mode, which child is visible, toggle pressed state (MarkdownEditPresenter.java:140-152); srcdoc HTML from the markdown (:206-216); addButtonsFirst callback for the owner's buttons (:118).

## `EntityTreePresenter`

composite, presenter; complexity 2, effort S; used by 6.

- **Component:** `stroom.explorer.client.component.EntityTree`
- **Controller:** `stroom.explorer.client.component.EntityTreeController`
- **Template:** `stroom-core-client/src/main/resources/stroom/explorer/client/view/EntityTreeViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/explorer/client/component/EntityTree.ui.xml`
- **Goes:** `EntityTreePresenter.java (nested EntityTreeView)`; `stroom-core-client/src/main/java/stroom/explorer/client/presenter/EntityTreeUiHandlers.java`; `stroom-core-client/src/main/java/stroom/explorer/client/view/EntityTreeViewImpl.java`; `AppModule.java:139 bindPresenterWidget(EntityTreePresenter.class, EntityTreeView.class, EntityTreeViewImpl.class)`; `DashboardAppModule.java:125-128 bindPresenterWidget(EntityTreePresenter.class, EntityTreeView.class, EntityTreeViewImpl.class)`
- **Names:** No clash. Same shape as ExplorerPopup (QuickFilter over the explorer tree, without the live status); they could share one component, but the popup has the sr-only status and the scroll panel's stroom-control class, so kept separate.

| Into the template | What | Detail |
| --- | --- | --- |
| EntityTreeViewImpl.java:49 | aria | delete: accessibleName="Filter documents" is already set on the QuickFilter at EntityTreeViewImpl.ui.xml:6 |
| EntityTreePresenter.java:56-63 / EntityTreeViewImpl.ui.xml:7 | component-declaration | the wave-6 explorer tree declared inside the MaxScrollPanel in place of setCellTree(new ExplorerTree(...)) |
| EntityTreeViewImpl.ui.xml:6 | component-declaration | QuickFilter already declared; keep |

**Stays in Java:** filter error and quick filter context handler (runtime); quick filter tooltip from ui config (EntityTreePresenter.java:69-76).

## `ExplorerPopupPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.explorer.client.component.ExplorerPopup`
- **Controller:** `stroom.explorer.client.component.ExplorerPopupController`
- **Template:** `none (its view is unit 7's ExplorerPopupViewImpl.ui.xml)` → `stroom-core-client/src/main/java/stroom/explorer/client/component/ExplorerPopup.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/explorer/client/presenter/ExplorerPopupPresenter.java (no binding of its own; its view's bindings are listed under unit 7)
- **Names:** One pair with unit 7. The private ExtendedExplorerTree subclass (ExplorerPopupPresenter.java:329-354) goes: the wave-6 tree's selection callbacks drive setSelectedTreeItem. A dialog: the controller's show(...) uses ShowPopupEvent with the component; DocSelectionController, CreateDocumentPresenter, MoveDocumentPresenter, CopyDocumentPresenter and ExportConfigPresenter move to it in the same change.

| Into the template | What | Detail |
| --- | --- | --- |
| ExplorerPopupPresenter.java:77 | aria | accessibleName="Filter documents" on the QuickFilter (see unit 7) |
| ExplorerPopupPresenter.java:85 | aria | accessibleName="Documents" on the declared tree |
| ExplorerPopupPresenter.java:82,95 | component-declaration | the wave-6 explorer tree declared in the template rather than new ExtendedExplorerTree + setCellTree |

**Stays in Java:** caption (default "Choose item", :63), set by callers; alert texts (:143-144, :187); quick filter tooltip from ui config (:97-104); REST lookup of the chosen DocRef (:275-290).

## `EntityCheckTreePresenter`

composite, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.explorer.client.component.EntityCheckTree`
- **Controller:** `stroom.explorer.client.component.EntityCheckTreeController`
- **Template:** `stroom-core-client/src/main/resources/stroom/explorer/client/view/EntityCheckTreeViewImpl.ui.xml` → `none (built in Java because the wave-6 tick-box explorer tree is the whole view: the component is that tree widget; EntityCheckTreeViewImpl.ui.xml is only a SimplePanel and goes)`
- **Goes:** `EntityCheckTreePresenter.java (nested EntityCheckTreeView)`; `stroom-core-client/src/main/java/stroom/explorer/client/view/EntityCheckTreeViewImpl.java`; `stroom-core-client/src/main/resources/stroom/explorer/client/view/EntityCheckTreeViewImpl.ui.xml`; `AppModule.java:140 bindPresenterWidget(EntityCheckTreePresenter.class, EntityCheckTreeView.class, EntityCheckTreeViewImpl.class)`
- **Names:** No clash. If wave 6 names the dumb tick-box tree ExplorerTickBoxTree, EntityCheckTree may not be needed as a separate component: the controller attaches to that tree directly. It also stops making its own SimpleEventBus (EntityCheckTreePresenter.java:42).

| Into the template | What | Detail |
| --- | --- | --- |
| EntityCheckTreePresenter.java:44-45 | component-declaration | the wave-6 tick-box tree, declared by the owner's template (the users of EntityCheckTreePresenter) rather than made with new ExplorerTickBoxTree + setCellTree |

**Stays in Java:** model settings (types, tags, flags, permissions, filter) and selection set: controller.

## `AiConfigTableAnalysisPresenter`

tab, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.ai.client.component.AiConfigTableAnalysis`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/ai/client/AiConfigTableAnalysisViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/ai/client/component/AiConfigTableAnalysis.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/ai/client/AiConfigTableAnalysisPresenter.java (nested AiConfigTableAnalysisView)`; `stroom-core-client/src/main/java/stroom/ai/client/AiConfigTableAnalysisViewImpl.java`; AskStroomAIModule.java:58-61 bindPresenterWidget(AiConfigTableAnalysisPresenter.class, AiConfigTableAnalysisView.class, AiConfigTableAnalysisViewImpl.class)
- **Names:** No clash. Nothing smart (read/write of config with defaults), so a component only.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/resources/stroom/ai/client/AiConfigTableAnalysisViewImpl.ui.xml:10 | `<v:ValueSpinner ui:field="maxTotalRows" width="200px"/>` | static | value-spinner--wide (value-spinner.css, new) |
| AiConfigTableAnalysisViewImpl.ui.xml:15 | `<v:ValueSpinner ui:field="maxRowsPerBatch" width="200px"/>` | static | value-spinner--wide |
| AiConfigTableAnalysisViewImpl.ui.xml:20 | `<v:ValueSpinner ui:field="maxParallelBatches" width="200px"/>` | static | value-spinner--wide |
| AiConfigTableAnalysisViewImpl.ui.xml:25 | `<v:ValueSpinner ui:field="attachmentDownloadTimeoutMs" width="200px"/>` | static | value-spinner--wide |
| AiConfigTableAnalysisViewImpl.ui.xml:30 | `<g:TextArea ui:field="tableQuerySystemPrompt" width="100%"/>` | static | w-100 (stroom.css:843) |
| AiConfigTableAnalysisViewImpl.ui.xml:35 | `<g:TextArea ui:field="tableQueryUserPrompt" width="100%"/>` | static | w-100 (stroom.css:843) |
| AiConfigTableAnalysisViewImpl.ui.xml:40 | `<g:TextArea ui:field="multiSummaryMergePrompt" width="100%"/>` | static | w-100 (stroom.css:843) |

| Into the template | What | Detail |
| --- | --- | --- |
| AiConfigTableAnalysisViewImpl.ui.xml:10-40 | component-declaration | ValueSpinner x4 and FormGroup already declared; keep |

**Stays in Java:** spinner bounds (AiConfigTableAnalysisViewImpl.java:54-62); values and default prompt texts from the config.

## `ExplorerPopupViewImpl`

dialog, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.explorer.client.component.ExplorerPopup`
- **Controller:** `stroom.explorer.client.component.ExplorerPopupController`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/dropdowntree/client/view/ExplorerPopupViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/explorer/client/component/ExplorerPopup.ui.xml`
- **Goes:** `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/view/ExplorerPopupView.java`; `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/view/ExplorerPopupViewImpl.java`; `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/view/ExplorerPopupUiHandlers.java`; `stroom-core-client-widget/src/main/resources/stroom/widget/dropdowntree/client/view/ExplorerPopupViewImpl.ui.xml (moved)`; `StreamStoreModule.java:163 bindSharedView(ExplorerPopupView.class, ExplorerPopupViewImpl.class)`; `DashboardAppModule.java:122 bindSharedView(ExplorerPopupView.class, ExplorerPopupViewImpl.class)`
- **Names:** Same pair as unit 377 (its presenter); one ExplorerPopup + ExplorerPopupController, not two. The record's package (stroom.widget.dropdowntree.client.view) is dropped: the template must declare the wave-6 explorer tree, which lives in stroom-core-client, and the widget module cannot see it, so the component moves to stroom.explorer.client.component. Its only users are explorer classes (ExplorerPopupPresenter, DocSelectionPopup), not UserRefPopupPresenter. No clash in all_simple_names.json.

| Into the template | What | Detail |
| --- | --- | --- |
| ExplorerPopupViewImpl.java:48 | aria | aria-live="polite" on the status region: make the root a g:HTMLPanel and the status a plain <div ui:field="status" class="sr-only" aria-live="polite"/> (DivElement), as UiBinder can't set aria-live on a g:Label |
| ExplorerPopupPresenter.java:77 | aria | accessibleName="Filter documents" on <v:QuickFilter ui:field="nameFilter"> |
| ExplorerPopupPresenter.java:85 | aria | accessibleName="Documents" on the tree element |
| ExplorerPopupPresenter.java:82,95 / ExplorerPopupViewImpl.ui.xml:8 | component-declaration | the wave-6 explorer tree widget (from AbstractExplorerTree/ExplorerTree) declared in place of <g:SimplePanel ui:field="treeContainer">; its AbstractExplorerTreeController / ExplorerTreeModel attaches in ExplorerPopupController |
| ExplorerPopupViewImpl.ui.xml:6 | component-declaration | <v:QuickFilter ui:field="nameFilter" addStyleNames="dock-min"/> already declared; keep |

**Stays in Java:** ExplorerPopupViewImpl.java:47 sinkEvents(ONMOUSEUP \| FOCUSEVENTS) (behaviour); announce(text) at ExplorerPopupPresenter.java:215: text built from the chosen node's name; setFilterError, setQuickFilter, typeInFilter, setMoveDown: runtime values/behaviour passed as callbacks; the quick filter tooltip (ExplorerPopupPresenter.java:99-102): built from ui config's help URL.

## `DocSelectionPopup`

dialog, presenter; complexity 1, effort S; used by 11.

- **Component:** `stroom.explorer.client.component.ExplorerPopup`
- **Controller:** `stroom.explorer.client.component.ExplorerPopupController`
- **Template:** `none` → `none (built in Java because it is ExplorerPopup's, from unit 7)`
- **Goes:** `stroom-core-client/src/main/java/stroom/explorer/client/presenter/DocSelectionPopup.java (no GIN binding; injected directly)`
- **Names:** No DocSelectionPopup/DocSelectionPopupController: it only exists to call setIncludeNullSelection(false) (DocSelectionPopup.java:36) through a public injectable constructor. Its 11 callers inject Provider<ExplorerPopupController> and call a public setIncludeNullSelection(false) (ExplorerPopupPresenter.java:194 is protected today).

**Stays in Java:** setIncludeNullSelection(false) moves to the callers.

## `DocSelectionBoxPresenter`

widget, split; complexity 2, effort M; used by 35.

- **Component:** `stroom.widget.dropdowntree.client.DropDownBox`
- **Controller:** `stroom.explorer.client.component.DocSelectionController`
- **Template:** `none` → `none (built in Java because DropDownBox is a wave-2 widget declared in each user's template; this unit adds no template)`
- **Goes:** stroom-core-client/src/main/java/stroom/explorer/client/presenter/DocSelectionBoxPresenter.java, once its last caller has moved (it has no GIN binding); `stroom-core-client/src/main/java/stroom/explorer/client/presenter/DocSelectionController.java moves to stroom.explorer.client.component`
- **Names:** No new DocSelectionBox/DocSelectionBoxController: the pilot already split it into DropDownBox (component) and DocSelectionController (controller), so the record's names would duplicate them. This unit only moves DocSelectionController into stroom.explorer.client.component (no clash), switches it from ExplorerPopupPresenter to ExplorerPopupController, and retires the wrapper. Of its 35 callers, 12 units in this wave move to template DropDownBox + attach; the rest keep the wrapper until they convert (rule 5).

**Stays in Java:** DocSelectionBoxPresenter.java:42 own(new DocSelectionController(...)) while the wrapper lasts; focus/setReadOnly/setEnabled delegation for the callers not yet moved.

## `QueryHelpPresenter`

composite, presenter; complexity 3, effort M; used by 4.

**Since the merge.** Copy now uses the new stroom.widget.util.client.Clipboard.writeText in place of ClipboardUtil.copy. Nothing else material; no longer in flight.

- **Component:** `stroom.query.client.component.QueryHelp`
- **Controller:** `stroom.query.client.component.QueryHelpController`
- **Template:** `stroom-core-client/src/main/resources/stroom/query/client/view/QueryHelpViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/query/client/component/QueryHelp.ui.xml`
- **Goes:** `QueryHelpPresenter.java (nested QueryHelpView)`; `stroom-core-client/src/main/java/stroom/query/client/view/QueryHelpViewImpl.java`; `stroom-core-client/src/main/java/stroom/query/client/presenter/QueryHelpUiHandlers.java`; `QueryModule.java:69-72 bindPresenterWidget(QueryHelpPresenter.class, QueryHelpView.class, QueryHelpViewImpl.class)`
- **Names:** No clash. linkToEditor(EditorPresenter) (QueryHelpPresenter.java:187) becomes setOnInsert(BiConsumer<String, InsertType>) wired by the owner, plus a getter for the completion provider; copy_and_paste has landed, so Clipboard.writeText (:144) is the current API.

| Into the template | What | Detail |
| --- | --- | --- |
| QueryHelpViewImpl.java:55 | aria | accessibleName="Help contents" on <sl:SelectionList ui:field="selectionList"> |
| QueryHelpPresenter.java:83 | structure | keyboardSelectionPolicy="BOUND_TO_SELECTION" on the SelectionList (enum setter), if SelectionList keeps that setter after wave 5 |
| QueryHelpViewImpl.ui.xml:9,11,12 | css-class | QueryHelpViewImpl (quick-filter.css:97) renamed to query-help; QueryHelpViewImpl-details and QueryHelpViewImpl-buttons have no CSS rules, so drop them |
| QueryHelpViewImpl.ui.xml:8-16 | component-declaration | SplitPane, SelectionList, Toolbar and ToolbarButton x2 already declared; keep |

**Stays in Java:** details HTML (QueryHelpViewImpl.java:73) and buttons' enabled state (:77-80): runtime; quick filter tooltip built from the help URL (QueryHelpPresenter.java:87-90); REST-backed model, detail provider and completion provider in the controller.

## `DataGenSettingsPresenter`

tab, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.datagen.client.component.DataGenSettings`
- **Controller:** `stroom.datagen.client.component.DataGenSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/datagen/client/view/DataGenSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/datagen/client/component/DataGenSettings.ui.xml`
- **Goes:** `DataGenSettingsPresenter.java (nested DataGenSettingsView)`; `stroom-core-client/src/main/java/stroom/datagen/client/view/DataGenSettingsViewImpl.java`; `DataGenModule.java:40-42 bindPresenterWidget(DataGenSettingsPresenter.class, DataGenSettingsView.class, DataGenSettingsViewImpl.class)`
- **Names:** No clash (DataGenDoc is the DTO).

| Into the template | What | Detail |
| --- | --- | --- |
| DataGenSettingsPresenter.java:55 / DataGenSettingsViewImpl.ui.xml:11 | component-declaration | <d:DropDownBox ui:field="destinationFeed"/> in place of the SimplePanel and setDestinationFeed(View); DocSelectionController attached with FeedDoc.TYPE and VIEW (:54-55) |
| DataGenSettingsPresenter.java:50,52 / DataGenSettingsViewImpl.ui.xml:19 | component-declaration | <e:CodeEditor ui:field="template" styleName="max form-control-border"/> (wave 6) in place of the SimplePanel and setTemplateEditor(View) |

**Stays in Java:** read-only and format availability (DataGenSettingsPresenter.java:66-69); template text and feed values.

## `VisualisationSettingsPresenter`

tab, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.visualisation.client.component.VisualisationSettings`
- **Controller:** `stroom.visualisation.client.component.VisualisationSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/visualisation/client/view/VisualisationSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/visualisation/client/component/VisualisationSettings.ui.xml`
- **Goes:** `VisualisationSettingsPresenter.java (nested VisualisationSettingsView)`; `stroom-core-client/src/main/java/stroom/visualisation/client/view/VisualisationSettingsViewImpl.java`; VisualisationModule.java:31-32 bindPresenterWidget(VisualisationSettingsPresenter.class, VisualisationSettingsView.class, VisualisationSettingsViewImpl.class)
- **Names:** No clash (VisualisationDoc is the DTO).

| Into the template | What | Detail |
| --- | --- | --- |
| VisualisationSettingsPresenter.java:59 / VisualisationSettingsViewImpl.ui.xml:13 | component-declaration | <d:DropDownBox ui:field="script"/>; DocSelectionController attached with ScriptDoc.TYPE and USE (:56-57) |
| VisualisationSettingsPresenter.java:60 / VisualisationSettingsViewImpl.ui.xml:20 | component-declaration | <e:CodeEditor ui:field="settings" addStyleNames="dock-max form-control-border"/> (wave 6) in place of the SimplePanel and setSettingsView(View) |
| VisualisationSettingsPresenter.java:62 | structure | mode="JSON" on the CodeEditor, if wave 6 gives it a mode setter |
| VisualisationSettingsViewImpl.ui.xml:17 | css-class | Bootstrap flex-grow-1 replaced by dock-max (stroom.css), as Bootstrap is being retired |

**Stays in Java:** read-only and format availability (VisualisationSettingsPresenter.java:79-81); function name, script and settings values.

## `FloorMapEventStoreSettingsPresenter`

tab, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.floormap.client.component.FloorMapEventStoreSettings`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapEventStoreSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEventStoreSettings.ui.xml`
- **Goes:** `FloorMapEventStoreSettingsPresenter.java (nested FloorMapEventStoreSettingsView)`; FloorMapModule.java:101-103 bindPresenterWidget(FloorMapEventStoreSettingsPresenter.class, FloorMapEventStoreSettingsView.class, FloorMapEventStoreSettingsViewImpl.class)
- **Names:** Same component as unit 728 (its view half): the presenter and FloorMapEventStoreSettingsViewImpl become one FloorMapEventStoreSettings with read(doc)/write(doc)/setReadOnly/setOnChange. It is dumb (no REST), so no controller; it is hosted by the document-tab base until wave 14 and by DocumentEditor's addTab(tab, make, reader, writer) after. DirtyUiHandlers goes.

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapEventStoreSettingsPresenter.java:47 | structure | watch(READ_ONLY, view::onReadOnly) becomes setReadOnly, called by the host |

**Stays in Java:** onRead/onWrite logic (FloorMapEventStoreSettingsPresenter.java:57-73) moves to read/write on the component.

## `ElasticIndexSettingsPresenter`

tab, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.search.elastic.client.component.ElasticIndexSettings`
- **Controller:** `stroom.search.elastic.client.component.ElasticIndexSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/search/elastic/client/view/ElasticIndexSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/search/elastic/client/component/ElasticIndexSettings.ui.xml`
- **Goes:** `ElasticIndexSettingsPresenter.java (nested ElasticIndexSettingsView)`; `stroom-core-client/src/main/java/stroom/search/elastic/client/view/ElasticIndexSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/search/elastic/client/presenter/ElasticIndexSettingsUiHandlers.java`; ElasticIndexModule.java:34-36 bindPresenterWidget(ElasticIndexSettingsPresenter.class, ElasticIndexSettingsView.class, ElasticIndexSettingsViewImpl.class)
- **Names:** No clash (wave 12's SolrIndexSettings is separate).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/resources/stroom/search/elastic/client/view/ElasticIndexSettingsViewImpl.ui.xml:18 | `<g:TextBox ui:field="indexName" width="400px"/>` | static | new text-field--wide (width: 400px) in text-field.css |
| ElasticIndexSettingsViewImpl.ui.xml:23 | `<g:TextBox ui:field="timeField" width="200px"/>` | static | remove: .text-field already gives 200px (text-field.css:34) |
| ElasticIndexSettingsViewImpl.ui.xml:32 | `<ValueSpinner ui:field="searchSlices" width="80px"/>` | static | new value-spinner--narrow (width: 80px) in value-spinner.css |
| ElasticIndexSettingsViewImpl.ui.xml:39 | `<ValueSpinner ui:field="searchScrollSize" width="80px"/>` | static | value-spinner--narrow (value-spinner.css, new) |
| ElasticIndexSettingsViewImpl.ui.xml:60 | `<g:TextBox ui:field="rerankTextFieldSuffix" width="200px"/>` | static | remove: .text-field default 200px (text-field.css:34) |
| ElasticIndexSettingsViewImpl.ui.xml:67 | `<g:TextBox ui:field="rerankScoreFieldSuffix" width="200px"/>` | static | remove: .text-field default 200px |
| ElasticIndexSettingsViewImpl.ui.xml:75 | `<g:TextBox ui:field="rerankScoreMinimum" width="80px"/>` | static | new text-field--narrow (width: 80px) in text-field.css |
| ElasticIndexSettingsViewImpl.ui.xml:79 | `<b:Button ui:field="testConnection" width="200px"/>` | static | remove: .form > .Button already has min-width: 200px (form.css:69-72) |

| Into the template | What | Detail |
| --- | --- | --- |
| ElasticIndexSettingsPresenter.java:92-95 / ElasticIndexSettingsViewImpl.ui.xml:13,44,51,55 | component-declaration | four <d:DropDownBox ui:field="cluster\|defaultExtractionPipeline\|vectorGenerationModel\|rerankModel"/> in place of the SimplePanel slots and set*View(View); the controller does make(docSelectionControllerProvider).attach(view.getClusterBox()) etc. instead of the four DocSelectionBoxPresenters (:59-62) |
| ElasticIndexSettingsViewImpl.ui.xml:32,39,79 | component-declaration | ValueSpinner x2, Button and FormGroup already declared; keep |

**Stays in Java:** spinner bounds (ElasticIndexSettingsViewImpl.java:72-76): behaviour, no template attribute for long; picker types and permissions (ElasticIndexSettingsPresenter.java:82-89) on the attached controllers; onTestIndex REST and alerts in the controller.

## `FeedSettingsPresenter`

tab, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.feed.client.component.FeedSettings`
- **Controller:** `stroom.feed.client.component.FeedSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/feed/client/view/FeedSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/feed/client/component/FeedSettings.ui.xml`
- **Goes:** `FeedSettingsPresenter.java (nested FeedSettingsView)`; `stroom-core-client/src/main/java/stroom/feed/client/view/FeedSettingsViewImpl.java`; `FeedModule.java:37 bindSharedView(FeedSettingsView.class, FeedSettingsViewImpl.class)`; `FeedModule.java:38 bind(FeedSettingsPresenter.class)`
- **Names:** No clash (FeedDoc is the DTO).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/resources/stroom/feed/client/view/FeedSettingsViewImpl.ui.xml:12 | `<g:TextBox ui:field="classification" width="400px"/>` | static | text-field--wide (width: 400px), new in text-field.css |

| Into the template | What | Detail |
| --- | --- | --- |
| FeedSettingsViewImpl.ui.xml:16,22-68 | component-declaration | TickBox, SelectionBox x7 and FormGroup already declared; the view stops exposing them (getDataFormat() etc.) and gets read/write plus setters for the option lists |

**Stays in Java:** option lists: FORMATS (FeedSettingsPresenter.java:83-84), encodings (:116-117), volume groups (:138), stream types (:157), FeedStatus values (:170): data; feed status enabled by receipt check mode (:178).

## `EditorPreferencesPresenter`

tab, presenter; complexity 2, effort S; used by 3.

**Since the merge.** copy_and_paste added a fourth preference, Text Context Menu (Stroom or Browser): a new FormGroup and SelectionBox in EditorPreferencesViewImpl.ui.xml (still under src/main/resources), a getter/setter pair on the view interface, and read/write of UserPreferences.textContextMenu. Same pattern as the others, so slightly more to move but no new dependency.

- **Component:** `stroom.preferences.client.component.EditorPreferences`
- **Controller:** `stroom.preferences.client.component.EditorPreferencesController`
- **Template:** `stroom-core-client/src/main/resources/stroom/preferences/client/EditorPreferencesViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/preferences/client/component/EditorPreferences.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/preferences/client/EditorPreferencesPresenter.java (nested EditorPreferencesView)`; `stroom-core-client/src/main/java/stroom/preferences/client/EditorPreferencesViewImpl.java`; UserPreferencesModule.java:55-58 bindPresenterWidget(EditorPreferencesPresenter.class, EditorPreferencesView.class, EditorPreferencesViewImpl.class)
- **Names:** No clash. copy_and_paste added the Text Context Menu box (textContextMenu); it is part of this unit now. The controller only fetches editor theme lists from UserPreferencesManager; if the owner passes them in, it can go.

| Into the template | What | Detail |
| --- | --- | --- |
| EditorPreferencesViewImpl.ui.xml:12,18,24,30 | component-declaration | SelectionBox x4 and FormGroup already declared; keep |

**Stays in Java:** option lists: TextContextMenu values (EditorPreferencesViewImpl.java:64), editor themes (:103), key bindings (:132), Toggle ON/OFF (:149-150): data; theme type switch (EditorPreferencesPresenter.java:63-77).

## `ThemePreferencesPresenter`

tab, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.preferences.client.component.ThemePreferences`
- **Controller:** `stroom.preferences.client.component.ThemePreferencesController`
- **Template:** `stroom-core-client/src/main/resources/stroom/preferences/client/ThemePreferencesViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/preferences/client/component/ThemePreferences.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/preferences/client/ThemePreferencesPresenter.java (nested ThemePreferencesView)`; `stroom-core-client/src/main/java/stroom/preferences/client/ThemePreferencesViewImpl.java`; UserPreferencesModule.java:51-54 bindPresenterWidget(ThemePreferencesPresenter.class, ThemePreferencesView.class, ThemePreferencesViewImpl.class)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ThemePreferencesViewImpl.ui.xml:13-41 | component-declaration | SelectionBox x5, TickBox x2 and FormGroup already declared; keep |

**Stays in Java:** fixed item text "Comfortable", "Compact" (ThemePreferencesViewImpl.java:60-61) and "Small", "Medium", "Large" (:63-65): SelectionBox items cannot be declared in a template; VisPalette display values (:68), themes (:95), fonts (:125): data.

## `TimePreferencesPresenter`

tab, tidy; complexity 2, effort S; used by 3.

- **Component:** `stroom.preferences.client.component.TimePreferences`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/preferences/client/TimePreferencesViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/preferences/client/component/TimePreferences.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/preferences/client/TimePreferencesPresenter.java (nested TimePreferencesView)`; `stroom-core-client/src/main/java/stroom/preferences/client/TimePreferencesViewImpl.java`; UserPreferencesModule.java:59-62 bindPresenterWidget(TimePreferencesPresenter.class, TimePreferencesView.class, TimePreferencesViewImpl.class)
- **Names:** The record's 'stays TimePreferencesPresenter' goes against rule 1 (a presenter is renamed and moved); it becomes the dumb component TimePreferences with read/write/setOnChange. Nothing smart, so no controller. No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| TimePreferencesViewImpl.java:86-87 | aria | accessibleName="Hours" / accessibleName="Minutes" on the two ValueSpinners in place of AccessibleName.set (needs ValueSpinner.setAccessibleName from the wave-2 contract) |
| TimePreferencesViewImpl.java:113 | structure | visible="false" on <valuespinner:ValueSpinner ui:field="timeZoneOffsetMinutes"> |
| TimePreferencesViewImpl.ui.xml:14-41 | component-declaration | SelectionBox x3, TickBox, ValueSpinner x2 and FormGroup already declared; keep |

**Stays in Java:** Use values, standard formats and time zone ids (TimePreferencesViewImpl.java:88-97): data; spinner bounds and steps (:99-110): behaviour; Custom text enabled state and zone group visibility (:150-151, :212-218).

## `AiConfigGeneralPresenter`

tab, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.ai.client.component.AiConfigGeneral`
- **Controller:** `stroom.ai.client.component.AiConfigGeneralController`
- **Template:** `stroom-core-client/src/main/resources/stroom/ai/client/AiConfigGeneralViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/ai/client/component/AiConfigGeneral.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/ai/client/AiConfigGeneralPresenter.java (nested AiConfigGeneralView)`; `stroom-core-client/src/main/java/stroom/ai/client/AiConfigGeneralUiHandlers.java`; `stroom-core-client/src/main/java/stroom/ai/client/AiConfigGeneralViewImpl.java`; `AskStroomAIModule.java:54-57 bindPresenterWidget(AiConfigGeneralPresenter.class, AiConfigGeneralView.class, AiConfigGeneralViewImpl.class)`
- **Names:** No clash (AskStroomAiConfig is the DTO).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/resources/stroom/ai/client/AiConfigGeneralViewImpl.ui.xml:27 | `<g:TextArea ui:field="chatSystemPrompt" width="100%"/>` | static | w-100 (stroom.css:843) |
| AiConfigGeneralViewImpl.ui.xml:32 | `<g:TextArea ui:field="historySummaryPrompt" width="100%"/>` | static | w-100 (stroom.css:843) |
| AiConfigGeneralViewImpl.ui.xml:37 | `<v:ValueSpinner ui:field="maxHistorySafetyCapMessages" width="200px"/>` | static | value-spinner--wide (width: 200px), new in value-spinner.css |

| Into the template | What | Detail |
| --- | --- | --- |
| AiConfigGeneralPresenter.java:53 / AiConfigGeneralViewImpl.ui.xml:12 | component-declaration | <d:DropDownBox ui:field="modelRef"/> in place of the SimplePanel and setModelRefSelection(View); DocSelectionController attached with OpenAIModelDoc.TYPE and USE (:54-55) |
| AiConfigGeneralViewImpl.ui.xml:17-42 | component-declaration | SelectionBox x2, ValueSpinner, TickBox and FormGroup already declared; keep |

**Stays in Java:** DockType and DockLocation items (AiConfigGeneralViewImpl.java:63-70): data; spinner bounds (:73-74); prompt texts and values from the config.

## `ReportSettingsPresenter`

tab, presenter; complexity 2, effort M; used by 3.

- **Component:** `stroom.analytics.client.component.ReportSettingsTab`
- **Controller:** `stroom.analytics.client.component.ReportSettingsTabController`
- **Template:** `stroom-core-client/src/main/resources/stroom/analytics/client/view/ReportSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/analytics/client/component/ReportSettingsTab.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/analytics/client/presenter/ReportSettingsPresenter.java (nested ReportSettingsView)`; `stroom-core-client/src/main/java/stroom/analytics/client/view/ReportSettingsViewImpl.java`; `ReportModule.java:33 bindPresenterWidget(ReportSettingsPresenter.class, ReportSettingsView.class, ReportSettingsViewImpl.class)`
- **Names:** ReportSettings clashes with stroom.analytics.shared.ReportSettings (stroom-core-shared), the DTO this tab reads and writes (ReportSettingsPresenter.java:22,71-107). Chosen ReportSettingsTab / ReportSettingsTabController, as the pilot names its tabs (WordsTab, ImportsTab). Takes in the error-feed part of AbstractSettingsPresenter (unit 507).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/resources/stroom/analytics/client/view/ReportSettingsViewImpl.ui.xml:31 | `<g:TextArea ui:field="aiSummaryPrompt" width="100%"/>` | static | w-100 (stroom.css:843) |

| Into the template | What | Detail |
| --- | --- | --- |
| ReportSettingsViewImpl.java:65 | aria | title="Set as the default error feed for all users" on <b:Button ui:field="setDefaultErrorFeed"> |
| ReportSettingsPresenter.java:56 / ReportSettingsViewImpl.ui.xml:26 | component-declaration | <d:DropDownBox ui:field="aiSummaryModel"/>; DocSelectionController with OpenAIModelDoc.TYPE and USE (:57-59) |
| AbstractSettingsPresenter.java:44 / ReportSettingsViewImpl.ui.xml:37 | component-declaration | <d:DropDownBox ui:field="errorFeed"/>; DocSelectionController with FeedDoc.TYPE and VIEW |
| ReportSettingsViewImpl.ui.xml:12-40 | component-declaration | SelectionBox, TickBox x2, Button and FormGroup already declared; keep |

**Stays in Java:** file types (ReportSettingsViewImpl.java:62): data; Set Default visibility for administrators and read-only state; values read and written through the ReportSettings DTO.

## `IndexSettingsPresenter`

tab, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.index.client.component.IndexSettings`
- **Controller:** `stroom.index.client.component.IndexSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/index/client/view/IndexSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/index/client/component/IndexSettings.ui.xml`
- **Goes:** `IndexSettingsPresenter.java (nested IndexSettingsView)`; `stroom-core-client/src/main/java/stroom/index/client/presenter/IndexSettingsUiHandlers.java`; `stroom-core-client/src/main/java/stroom/index/client/view/IndexSettingsViewImpl.java`; `IndexModule.java:57 bindPresenterWidget(IndexSettingsPresenter.class, IndexSettingsView.class, IndexSettingsViewImpl.class)`
- **Names:** No clash (LuceneIndexDoc is the DTO; Solr and Elastic have their own *IndexSettings).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/resources/stroom/index/client/view/IndexSettingsViewImpl.ui.xml:12 | `<v:ValueSpinner ui:field="maxDocsPerShard" width="200px"/>` | static | value-spinner--wide (value-spinner.css, new) |
| IndexSettingsViewImpl.ui.xml:22 | `<v:ValueSpinner ui:field="partitionSize" width="200px"/>` | static | value-spinner--wide |
| IndexSettingsViewImpl.ui.xml:27 | `<v:ValueSpinner ui:field="shardsPerPartition" width="200px"/>` | static | value-spinner--wide |
| IndexSettingsViewImpl.ui.xml:32 | `<g:TextBox ui:field="timeField" width="200px"/>` | static | remove: .text-field default 200px (text-field.css:34) |

| Into the template | What | Detail |
| --- | --- | --- |
| IndexSettingsPresenter.java:80 / IndexSettingsViewImpl.ui.xml:46 | component-declaration | <d:DropDownBox ui:field="defaultExtractionPipeline"/>; DocSelectionController attached (pipeline type and permission as now) |
| IndexSettingsViewImpl.ui.xml:12-48 | component-declaration | ValueSpinner x3, SelectionBox x3, Button and FormGroup already declared; keep |

**Stays in Java:** PartitionBy items (IndexSettingsViewImpl.java:69-72), retention ages (IndexSettingsPresenter.java:159), volume group names (:176-177): data; spinner bounds (IndexSettingsViewImpl.java:75-85); reset REST and confirm in the controller.

## `DenseVectorFieldPresenter`

composite, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.index.client.component.DenseVectorField`
- **Controller:** `stroom.index.client.component.DenseVectorFieldController`
- **Template:** `stroom-core-client/src/main/resources/stroom/index/client/view/DenseVectorFieldViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/index/client/component/DenseVectorField.ui.xml`
- **Goes:** `DenseVectorFieldPresenter.java (nested DenseVectorFieldView)`; `stroom-core-client/src/main/java/stroom/index/client/view/DenseVectorFieldViewImpl.java`; `IndexModule.java:59-62 bindPresenterWidget(DenseVectorFieldPresenter.class, DenseVectorFieldView.class, DenseVectorFieldViewImpl.class)`
- **Names:** No clash (the shared DTO is DenseVectorFieldParams-style data on the field, not a DenseVectorField class).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/resources/stroom/index/client/view/DenseVectorFieldViewImpl.ui.xml:23 | `<v:ValueSpinner ui:field="segmentSize" width="200px"/>` | static | value-spinner--wide (value-spinner.css, new) |
| DenseVectorFieldViewImpl.ui.xml:29 | `<v:ValueSpinner ui:field="overlapSize" width="200px"/>` | static | value-spinner--wide |
| DenseVectorFieldViewImpl.ui.xml:35 | `<v:ValueSpinner ui:field="nearestNeighbourCount" width="200px"/>` | static | value-spinner--wide |
| DenseVectorFieldViewImpl.ui.xml:52 | `<v:ValueSpinner ui:field="rerankBatchSize" width="200px"/>` | static | value-spinner--wide |

| Into the template | What | Detail |
| --- | --- | --- |
| DenseVectorFieldPresenter.java:55 / DenseVectorFieldViewImpl.ui.xml:11 | component-declaration | <d:DropDownBox ui:field="embeddingModel"/>; DocSelectionController attached |
| DenseVectorFieldPresenter.java:59 / DenseVectorFieldViewImpl.ui.xml:40 | component-declaration | <d:DropDownBox ui:field="rerankModel"/>; DocSelectionController attached |
| DenseVectorFieldViewImpl.ui.xml:17-58 | component-declaration | SelectionBox x2, ValueSpinner x4 and FormGroup already declared; keep |

**Stays in Java:** enum items (DenseVectorFieldViewImpl.java:61,70) and spinner bounds (:62-69).

## `TextConverterSettingsPresenter`

tab, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.pipeline.client.component.TextConverterSettings`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/pipeline/client/view/TextConverterSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pipeline/client/component/TextConverterSettings.ui.xml`
- **Goes:** `TextConverterSettingsPresenter.java (nested TextConverterSettingsView)`; `stroom-core-client/src/main/java/stroom/pipeline/client/view/TextConverterSettingsViewImpl.java`; PipelineModule.java:106-107 bindPresenterWidget(TextConverterSettingsPresenter.class, TextConverterSettingsView.class, TextConverterSettingsViewImpl.class)
- **Names:** No clash. Dumb document tab (read/write/setReadOnly/setOnChange), hosted by the document-tab base, so no controller.

| Into the template | What | Detail |
| --- | --- | --- |
| TextConverterSettingsViewImpl.ui.xml:10 | component-declaration | SelectionBox and FormGroup already declared; the view stops exposing getConverterType() |

**Stays in Java:** converter type items (TextConverterSettingsPresenter.java:40-42): enum data.

## `AbstractSettingsPresenter`

tab, presenter; complexity 2, effort M; used by 2.

- **Component:** `stroom.analytics.client.component.AnalyticSettings`
- **Controller:** `stroom.analytics.client.component.AnalyticSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/analytics/client/view/AnalyticSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/analytics/client/component/AnalyticSettings.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/analytics/client/presenter/AbstractSettingsPresenter.java (nested SettingsView)`; `stroom-core-client/src/main/java/stroom/analytics/client/presenter/SettingsUiHandlers.java`; `stroom-core-client/src/main/java/stroom/analytics/client/presenter/AnalyticSettingsPresenter.java (nested AnalyticSettingsView)`; `stroom-core-client/src/main/java/stroom/analytics/client/view/AnalyticSettingsViewImpl.java`; `AnalyticsModule.java:67-69 bindPresenterWidget(AnalyticSettingsPresenter.class, AnalyticSettingsView.class, AnalyticSettingsViewImpl.class)`
- **Names:** AbstractSettingsPresenter is merged, not renamed (no AbstractSettings/AbstractSettingsController): its error-feed picker and Set Default logic (AbstractSettingsPresenter.java:50-100) go into AnalyticSettingsController and ReportSettingsTabController (unit 508), following AnalyticStreamDestinationController's feed + Set Default pattern; if the duplicate grows, a small ErrorFeedSelection helper both make. The unit's subclass AnalyticSettingsPresenter becomes AnalyticSettings + AnalyticSettingsController. No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| AnalyticSettingsViewImpl.java:65 | aria | title="Set as the default error feed for all users" on <b:Button ui:field="setDefaultErrorFeed"> |
| AbstractSettingsPresenter.java:44 / AnalyticSettingsViewImpl.ui.xml:33 | component-declaration | <d:DropDownBox ui:field="errorFeed"/> in place of the SimplePanel and setErrorFeedView(View); DocSelectionController attached with FeedDoc.TYPE and VIEW (:42-43) |
| AnalyticSettingsViewImpl.ui.xml:13-34 | component-declaration | SelectionBox x2, TickBox, Button and FormGroup already declared; keep |

**Stays in Java:** level and status items from enums (AnalyticSettingsViewImpl.java:61,63): data; Set Default visible only for administrators (AbstractSettingsPresenter.java:46), enabled by read-only; default error feed lookup from ui config (:55-76).

## `PathwaysSettingsPresenter`

tab, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.pathways.client.component.PathwaysSettings`
- **Controller:** `stroom.pathways.client.component.PathwaysSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pathways/client/view/PathwaysSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pathways/client/component/PathwaysSettings.ui.xml`
- **Goes:** `PathwaysSettingsPresenter.java (nested PathwaysSettingsView)`; `stroom-core-client/src/main/java/stroom/pathways/client/presenter/PathwaysSettingsUiHandlers.java`; `stroom-core-client/src/main/java/stroom/pathways/client/view/PathwaysSettingsViewImpl.java`; `PathwaysModule.java:55-57 bindPresenterWidget(PathwaysSettingsPresenter.class, PathwaysSettingsView.class, PathwaysSettingsViewImpl.class)`
- **Names:** No clash (PathwaysDoc is the DTO).

| Into the template | What | Detail |
| --- | --- | --- |
| PathwaysSettingsPresenter.java:57 / PathwaysSettingsViewImpl.ui.xml:9 | component-declaration | <d:DropDownBox ui:field="traceStore"/>; DocSelectionController attached |
| PathwaysSettingsPresenter.java:61 / PathwaysSettingsViewImpl.ui.xml:12 | component-declaration | <d:DropDownBox ui:field="infoFeed"/>; DocSelectionController attached |
| PathwaysSettingsViewImpl.ui.xml:16-29 | component-declaration | DurationPicker, TickBox x4 and FormGroup already declared; keep |

**Stays in Java:** default tolerance value (PathwaysSettingsViewImpl.java:64,90); read-only state (:137-138), values.

## `FloorMapEventStoreSettingsViewImpl`

composite, tidy; complexity 2, effort S; used by 1.

- **Component:** `stroom.floormap.client.component.FloorMapEventStoreSettings`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapEventStoreSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEventStoreSettings.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapEventStoreSettingsViewImpl.java (binding listed under unit 152)`
- **Names:** The record's 'stays FloorMapEventStoreSettingsViewImpl' goes against rule 1: it is a presenter's ViewImpl, so with unit 152 it becomes the component FloorMapEventStoreSettings. Its @Inject Binder constructor becomes a no-arg one with GWT.create.

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapEventStoreSettingsViewImpl.java:113-118 | structure | the condense panel's FlowPanel, built in Java, declared inside <sg:SettingsGroup label="Condense Data"> |
| FloorMapEventStoreSettingsViewImpl.java:57-58,114 | text | <g:Label text="This will cause repeating events to disappear from the map."/> |
| FloorMapEventStoreSettingsViewImpl.java:115 | css-class | floorMapEventStoreCondenseWarning (stroom-floormap.css:474) on that label |
| FloorMapEventStoreSettingsViewImpl.java:85-92,109-110,117 | component-declaration | <sg:GeneralSettingsWidget ui:field="generalSettings"/>, <sg:CondenseSettingsWidget ui:field="condenseSettings"/>, <sg:RetentionSettingsWidget ui:field="retentionSettings"/> (wave 7) declared in their SettingsGroups instead of injected and added with add(asWidget()) |
| FloorMapEventStoreSettingsViewImpl.java:107 | structure | none: initial unit HOURS stays with the items |
| FloorMapEventStoreSettingsViewImpl.ui.xml:32-47 | component-declaration | SettingsGroup x4, FormGroup, ValueSpinner and SelectionBox already declared; keep |

**Stays in Java:** TimeUnit items (FloorMapEventStoreSettingsViewImpl.java:100-107) and spinner bounds (:94-96); readSettings preservation and read/write (:135-178); read-only state (:181-187), now setReadOnly.

## `TableBuilderProcessingPresenter`

composite, presenter; complexity 3, effort M; used by 5.

- **Component:** `stroom.analytics.client.component.TableBuilderProcessing`
- **Controller:** `stroom.analytics.client.component.TableBuilderProcessingController`
- **Template:** `stroom-core-client/src/main/resources/stroom/analytics/client/view/TableBuilderProcessingViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/analytics/client/component/TableBuilderProcessing.ui.xml`
- **Goes:** `TableBuilderProcessingPresenter.java (nested TableBuilderProcessingView)`; `stroom-core-client/src/main/java/stroom/analytics/client/view/TableBuilderProcessingViewImpl.java`; AnalyticsModule.java:82-84 bindPresenterWidget(TableBuilderProcessingPresenter.class, TableBuilderProcessingView.class, TableBuilderProcessingViewImpl.class)
- **Names:** No clash. ProcessingStatusUiHandlers is shared with other processing views, so it stays until they convert; this view stops implementing it.

| Into the template | What | Detail |
| --- | --- | --- |
| TableBuilderProcessingViewImpl.java:73 | aria | icon="REFRESH" on <b:Button ui:field="refresh" text="Refresh"> |
| TableBuilderProcessingViewImpl.java:168 / .ui.xml:43 | structure | <g:HTML ui:field="info" styleName="default-min-sizes form-control-border overflow-auto"/> in place of the SimplePanel filled with new HTML(info); Java calls info.setHTML |
| TableBuilderProcessingViewImpl.ui.xml:13-44 | component-declaration | TickBox, SelectionBox, DateTimeBox x2 (wave 4, controllers attached), DurationPicker x2, Button and FormGroup already declared; keep |

**Stays in Java:** node list (TableBuilderProcessingViewImpl.java:96) and info HTML: data; read-only state (:172-178); node REST and info refresh in the controller.

## `CredentialSettingsPresenter`

tab, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.credentials.client.component.CredentialSettings`
- **Controller:** `stroom.credentials.client.component.CredentialSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/credentials/client/view/CredentialSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/credentials/client/component/CredentialSettings.ui.xml`
- **Goes:** `CredentialSettingsPresenter.java (nested CredentialSettingsView)`; `stroom-core-client/src/main/java/stroom/credentials/client/presenter/CredentialSettingsUiHandlers.java`; `stroom-core-client/src/main/java/stroom/credentials/client/view/CredentialSettingsViewImpl.java`; CredentialsModule.java:63-65 bindPresenterWidget(CredentialSettingsPresenter.class, CredentialSettingsView.class, CredentialSettingsViewImpl.class)
- **Names:** No clash (the shared DTOs are Credential, CredentialWithPerms, PutCredentialRequest).

| Into the template | What | Detail |
| --- | --- | --- |
| CredentialSettingsViewImpl.ui.xml:33-35 / CredentialSettingsPresenter.java:70 | component-declaration | the five wave-7 secret components (UsernamePasswordSecret, AccessTokenSecret, SshKeySecret, KeyStoreSecret, KeyStoreFileSecret) declared in the secret FormGroup, visible="false", in place of <g:SimplePanel ui:field="secret"> and setSecretView(View); the controller shows the one for the type and attaches each one's controller |
| CredentialSettingsPresenter.java:62 | component-declaration | <datetimebox:DateTimeBox ui:field="expiryTime"> stays declared; the wave-4 DateTimeBoxController is attached by the controller instead of setPopupProvider(Provider<DateTimePopup>) |
| CredentialSettingsViewImpl.ui.xml:13,18,31 | component-declaration | TickBox, SelectionBox and FormGroup already declared; keep |

**Stays in Java:** CredentialSettingsViewImpl.java:89-95 type list sorted by display value (data); setExistingCredential / setKeyStorePath / updateState visibility and enabled states (CredentialSettingsViewImpl.java:149-168); CredentialSettingsPresenter.java:124-128 rename warning text built from the names.

## `QueryToolbarPresenter`

composite, presenter; complexity 2, effort S; used by 6.

- **Component:** `stroom.query.client.component.QueryToolbar`
- **Controller:** `stroom.query.client.component.QueryToolbarController`
- **Template:** `stroom-core-client/src/main/resources/stroom/query/client/view/QueryToolbarViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/query/client/component/QueryToolbar.ui.xml`
- **Goes:** `QueryToolbarPresenter.java (nested QueryToolbarView)`; `stroom-core-client/src/main/java/stroom/query/client/view/QueryToolbarViewImpl.java`; `stroom-core-client/src/main/java/stroom/query/client/presenter/QueryToolbarUiHandlers.java`; `QueryModule.java:65-68 bindPresenterWidget(QueryToolbarPresenter.class, QueryToolbarView.class, QueryToolbarViewImpl.class)`
- **Names:** No clash. Keeps a small controller for the SearchStateListener/SearchErrorListener and the error alert (AlertEvent needs a node); if the owner takes the alert, the controller can go.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/query/client/view/QueryToolbarViewImpl.java:76 | `errors.getElement().getStyle().setVisibility(show ? VISIBLE : HIDDEN)` | static | state class query-toolbar__errors--hidden { visibility: hidden; } toggled on the errors button, new in dashboard.css beside .QueryToolbarViewImpl-errors (dashboard.css:80); no existing Stroom class sets visibility: hidden (Bootstrap's invisible is not to be used) |

| Into the template | What | Detail |
| --- | --- | --- |
| QueryToolbarViewImpl.java:54 | aria | svg="ERROR" on <b:ToolbarButton ui:field="errors" title="Show Errors"> (title already there) |
| QueryToolbarPresenter.java:63 | css-class | start hidden: addStyleNames="query-toolbar__errors--hidden" on the errors button |
| QueryToolbarViewImpl.ui.xml:6,7,9 | css-class | QueryToolbarViewImpl-outer/-inner/-errors (dashboard.css:66,72,80,142) renamed to the block query-toolbar, query-toolbar__inner, query-toolbar__errors, as the ViewImpl name goes |
| QueryToolbarViewImpl.ui.xml:8,9,12 | component-declaration | TimeRangeSelector (wave 4, controller attached), Toolbar, ToolbarButton and QueryButtons already declared; keep |

**Stays in Java:** setErrorSeverity icon and title swaps (QueryToolbarViewImpl.java:83-92): state; time range value and param values; alert message text built from error messages (QueryToolbarPresenter.java:90-114).

## `FloorMapTimelinePresenter`

composite, presenter; complexity 4, effort L; used by 4.

- **Component:** `stroom.floormap.client.component.FloorMapTimeline`
- **Controller:** `stroom.floormap.client.component.FloorMapTimelineController`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapTimelineViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapTimeline.ui.xml`
- **Goes:** `FloorMapTimelinePresenter.java (nested FloorMapTimelineView, :780-860)`; FloorMapModule.java:129-131 bindPresenterWidget(FloorMapTimelinePresenter.class, FloorMapTimelineView.class, FloorMapTimelineViewImpl.class)
- **Names:** Paired with unit 743: FloorMapTimelineViewImpl becomes the component FloorMapTimeline, this presenter becomes FloorMapTimelineController (playback state, settings popup, speed menu). No clash. FloorMapTimelineSettingsPresenter (wave 7, FloorMapTimelineSettings + controller) is a popup the controller makes, not declared.

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapTimelinePresenter.java:296-299 | aria | initial icons and names: <b:ToolbarButton ui:field="playPause" svg="PLAY" title="Play"/>, stepBack svg="STEP_BACKWARD" title="Step Back", stepForward svg="STEP_FORWARD" title="Step Forward", settings svg="SETTINGS" title="Playback Settings" (setStepBackIcon/setStepForwardIcon/setSettingsIcon go: they never change) |
| FloorMapTimelinePresenter.java:774-777 | component-declaration | <b:Toolbar ui:field="helpToolbar" name="Timeline help" visible="false"> with the help ToolbarButton in the right controls, shown by setHelpContent, in place of new Toolbar + addRightControl |
| FloorMapTimelinePresenter.java:323 | text | "Playback Speed" menu name stays in the controller (menu, not template) |

**Stays in Java:** play/pause icon and title swaps at :229,232,462,677 (state); date labels, scrub text, progress, speed badge text, out-of-range state, histogram data (all runtime); alert text at :220-224.

## `ContentStoreContentPackDetailsPresenter`

composite, smart class; complexity 3, effort M; used by 1.

- **Component:** `stroom.contentstore.client.component.ContentStoreContentPackDetails`
- **Controller:** `stroom.contentstore.client.component.ContentStoreContentPackDetailsController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/contentstore/client/component/ContentStoreContentPackDetails.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/contentstore/client/presenter/ContentStoreContentPackDetailsPresenter.java (no GIN binding; injected into ContentStorePresenter:57)
- **Names:** Kept the record's name, not ContentPackDetails, so it reads with the shared ContentStoreContentPack* DTOs it shows; the template is ContentStoreContentPackDetails.ui.xml, not the record's ContentPackDetails.ui.xml. No clash. The back-reference to ContentStorePresenter (setContentStorePresenter, :282) becomes a setOnInstalled(Runnable) callback on the controller.

| Into the template | What | Detail |
| --- | --- | --- |
| ContentStoreContentPackDetailsPresenter.java:181-237 | structure | whole layout: root FlowPanel, icon HTML, vertical FlowPanel, heading Label, details HTML, details list, button row (all built with new FlowPanel/Label/HTML/Button today) |
| ContentStoreContentPackDetailsPresenter.java:181 | css-class | contentstore-details (components.css:1927) |
| ContentStoreContentPackDetailsPresenter.java:184 | css-class | contentstore-details-icon |
| ContentStoreContentPackDetailsPresenter.java:189 | css-class | contentstore-details-vertical |
| ContentStoreContentPackDetailsPresenter.java:193 | css-class | contentstore-details-heading |
| ContentStoreContentPackDetailsPresenter.java:196-197 | css-class | contentstore-details-markdown form-control-border form-control-background, wordWrap="true" |
| ContentStoreContentPackDetailsPresenter.java:209 | css-class | contentstore-buttons |
| ContentStoreContentPackDetailsPresenter.java:215 | css-class | contentstore-details-list |
| ContentStoreContentPackDetailsPresenter.java:246 | css-class | contentstore-details-name on each detail name label |
| ContentStoreContentPackDetailsPresenter.java:253-256 | css-class | contentstore-details-url on the URL InlineLabels, link + URL pairs as FlowPanels |
| ContentStoreContentPackDetailsPresenter.java:200,204 | text | <b:Button ui:field="install" text="Install"/> and <b:Button ui:field="upgrade" text="Upgrade"/> |
| ContentStoreContentPackDetailsPresenter.java:218-231 | text | labels "Installed status:", "Licence:", "Licence details:", "Installed location:", "Git URL:", "Git branch:", "Git path:", "Git commit:" |
| ContentStoreContentPackDetailsPresenter.java:61-62 | text | link texts "View licence" and "View Git repository" on the two Anchors |
| ContentStoreContentPackDetailsPresenter.java:152,157 | component-declaration | two <b:Button> declared (wave 1) |

**Stays in Java:** setContentPack values (ContentStoreContentPackDetailsPresenter.java:303-332): icon <img> from ImageTagUtil (data-driven id; its width/height are attributes, not style), name, status, licence, path, git fields, markdown HTML; link href and visibility (setLink, :262-274); button enabled state (setState, :359-378); REST create/upgrade, credentials dialog and alerts (:383-519) go to the controller (dialog with makeDialog).

