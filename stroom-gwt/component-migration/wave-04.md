# Wave 4: Smart widgets not in flight: split into box plus controller

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 24 October 2026 from a review of every unit in the wave against the code after master (with `data_grid2` and `copy_and_paste`) was merged in. Line numbers are as of then. Each unit is done when it meets "Done means, for every unit".

Split the widgets that open their own presenter dialogs, or reach services or the bus themselves, into a dumb box and a controller or callback, following the DropDownBox and DocSelectionController pilot. The date, time and schedule popups become component dialogs. The navigators invert to callbacks. ClassificationLabel takes its colours from outside, and VisFrame and CustomFileUpload lose their bus and transport coupling.

## Audited (25 October 2026)

Six reviewers read the wave against this plan, "Done means" and AGENTS.md. No regression in
normal use. Fixed:

- **Progress.** Controllers passed their context's task monitor to clients that take one; where
  nothing provides one it shows nothing, and the request then shows no progress rather than
  Stroom's busy indicator (the schedule dialog asks for its times before it is shown). They pass
  `Controller.getProvidedTaskMonitor()`, null when nothing provides one; so do the two earlier
  controllers that did the same (`DocSelectionController`, `AnalyticStreamDestinationController`).
  Calls through `ScheduledTimeClient` keep `guard(...)` for late replies, rather than the plan's
  `owner(this)`, as the client takes no owner.
- **A leak.** A visualisation's settings kept the date controllers of every visualisation chosen
  while the dialog was open; they are released with their tabs.
- **Long values.** The item selection and source range dialogs read their spinners as `int`; they
  read `long`s (an older bug).
- **Claims that weren't so.** `TimeRangeSelector` now has the `setOnChange` the Done section
  claimed; the schedule and date-time boxes have the read-only stories it claimed, and Set
  Location's OK and Cancel are tested (`TestStepLocationLinkController`).
- **Smaller things.** The schedule dialog asked for its times twice as it opened; an `IconBox`
  told `opensDialog` false took away an `aria-haspopup` its field had set itself; every new file
  says 2026 (a rename keeps its year); the new controllers' constructors and the package-private
  methods have `///` javadoc; three view getters were below their classes' inner-type divider;
  unused imports; stale names in comments, docs, the later waves' plans and the suite's tools
  (`build-handler-inventory.mjs` looked for `ItemNavigatorViewImpl`); weak story checks (the item
  navigator's First, the file chooser's GWT classes, the source range's end, the settings pane's
  change and read-only checks).

Decided, and left:

- **`date-time-view`** stays the block both the time and date-time dialogs' content use
  (`date-time-view.css`), as the two share it; it isn't renamed for either.
- `FieldEndStories`' date-time box has no controller, as only its layout is checked.
- `BatchExecutionScheduleEditPresenter.validate` tells its caller `false` after `true` (older,
  harmless today; in `ISSUES.md`).
- The suite's crawl-made oracles (`reachability-graph.md`, `route-recipes.json`) still name the old
  classes until the next crawl.

## Done (25 October 2026)

All 17 units, in nine commits after one for the shared widgets' template support. What was decided
on the way, where it differs from the plans below:

- **Shared widgets.** `IconBox` takes `icon`, its field as a child (`<svg:field>`) and
  `opensDialog`; `Button` takes `opensDialog` and `role`; `ValueSpinner` takes `minValue` and
  `maxValue` (`int`, as UiBinder can't parse `long`); `ValueSpinner`, `LineColInput` and
  `BaseSelectionBox` take `accessibleName`. A field that says it opens a dialog itself keeps saying
  so when it goes in an `IconBox`.
- **Boxes ask, controllers answer.** `TimeBox`, `DateTimeBox`, `ScheduleBox` and
  `TimeRangeSelector` only ask for their dialog (`setOnOpenPicker`); a `TimeBoxController`,
  `DateTimeBoxController`, `ScheduleBoxController` or `TimeRangeSelectorController` attached by the
  screen's controller opens a one-use `TimePickerController`, `DateTimePickerController`,
  `SchedulePickerController` or `TimeRangePickerController`. Every one of the 19 date-time box
  users attaches one, so `DateTimeBox`'s `StaticEventBus` fallback is gone, as is the time range
  selector's: the query expression editor's term editor gets a `Provider<DateTimeBoxController>`
  through its view, and `DynamicSettingsPane` hands its date boxes to its owner
  (`setOnDateTimeBoxAdded`). Their `ValueChangeEvent`s stay beside the new `setOnChange` until their
  callers move (rule 5), `TimeRangeSelector`'s since the audit.
- **The schedule box's checks make no dialog.** `ScheduleBoxController` checks the schedule with
  the server itself (`validate`), and makes a dialog only when one is opened; the job screens'
  `JobNodeListHelper` opens one per change through a provider.
- **`CustomFileUpload` is `FileChooser`** (block `file-chooser`), and its upload, task monitor and
  reply parsing are `FileChooserController`'s. The upload transport stays a static hook on the
  controller, set only by the workbench. The file input is a plain `<input type="file" hidden>`,
  and `FormGroup` now skips an element with the `hidden` attribute, as it skipped one hidden inline.
- **The navigators share `NavigatorAction`** (first, previous, next, last, refresh, select). Each
  component shows what it is given (`show(HasItems)`, `show(HasCharacterData)`, which it only
  reads); its controller acts on it and opens the selection dialog.
- **One `classification-label` block**, with `--one-line` for the text panes' banners;
  `ClassificationLabelController` gives a label its colours. `ClassificationWrapper` is a `View`
  too, as `DataPresenter` shows it as its view until it converts.
- **Tests on the JVM** cover the pure parts: the label colours, the upload's reply, the character
  range's checks (with OK and Cancel), and the location link's guard and text. New stories:
  `Widgets/Editors & Viewers/ItemNavigator`, `Widgets/Inputs/DynamicSettingsPane`, the time and
  schedule boxes' OK, Cancel and read-only paths, `SourcePresenter` `SetRange`,
  `ExecutionSchedulesScreen` `EditStartTimeWithDialog` and `QueryEditor` `TimeRangeQuickSetting`.

Found and fixed on the way (in `ISSUES.md`): the Select Record dialog's spinner had no name; the
time dialog's hour hid without its label. Left: `DynamicSettingsPane.setReadOnly` and `setOnChange`
are for its owner (`BasicVisSettingsPresenter`) to use when it converts; the unused CSS is listed in
`ISSUES.md`; `VisFrame` stays in wave 5, where the order puts it.

## CSS and template notes for the wave

- Custom-property helper: ClassificationLabel (JSNI setStyleProperty), FieldEnd, DataGridDom and SplitPane each set CSS custom properties with their own element.style.setProperty JSNI; one shared helper in stroom.widget.util.client (e.g. CssProperties.set(element, name, value) / remove) would serve every dynamic style in later waves too.
- IconBox (wave 1) needs template support before TimeBox, DateTimeBox and ScheduleBox can declare it: an icon attribute (SvgImage enum, which UiBinder parses), its field as a child (it extends FlowPanel, so add() must apply icon-box__field) and an opensDialog attribute that puts aria-haspopup="dialog" on its field. Their classes are already in date-time-box.css, schedule-box.css and icon-box.css.
- ValueSpinner min/max can't be set in templates: UiBinder has no long parser and setMin/setMax take long. Int-taking setters with distinct names (overloads of the same name are ambiguous to UiBinder) would let TimePicker, DateTimePicker, ItemSelection, CharacterRangeSelection and StepLocationPicker move their fixed limits into templates; minStep/maxStep (int) already can.
- Accessible names in templates: SelectionBox, ValueSpinner and LineColInput need setAccessibleName(String) (wrapping AccessibleName.set) for CharacterRangeSelection's AccessibleName.set calls to become attributes; Button needs setOpensDialog like LinkButton for CustomFileUpload.
- date-time-view.css (block date-time-view) is shared by TimePicker and DateTimePicker; quick-settings.css by TimeRangePicker and SchedulePicker, which both declare QuickSettings.
- components.css rules to move into their own BEM stylesheets as these units change: itemSelectionViewImpl-* (item-selection.css), itemNavigator* (item-navigator.css), characterNavigator* (character-navigator.css), characterRangeSelectionViewImpl-* (character-range-selection.css), stepLocationLink* (step-location-link into stepping-screen.css, disabled read from aria-disabled). itemNavigator-button, characterNavigator-button and stepLocationViewImpl-layout are unused, but removals wait for other in-flight work.
- Hidden file input: file-upload.css has no rule for file-upload__input; add display: none there in place of fileUpload.setVisible(false).
- Classification colours: .classificationLabel (components.css:398) and .classification (stroom.css:272) duplicate the same colour rules on --classification-label__background-color; one classification-label block with a variant would do.

## `ClassificationLabel`

widget, smart widget; complexity 1, effort S; used by 3.

- **Component:** `stroom.data.client.view.ClassificationLabel`
- **Controller:** `stroom.data.client.view.ClassificationLabelController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/ClassificationLabel.ui.xml` → `stroom-core-client/src/main/java/stroom/data/client/view/ClassificationLabel.ui.xml`
- **Goes:** ClassificationLabel.java:42-61 @Inject constructor reading UiConfigCache (becomes a no-arg constructor; the colours are pushed with setLabelColours(...) by ClassificationLabelController, which reads UiConfigCache with owner(this)); @UiField(provided = true) and constructor injection of the label in ClassificationWrapperViewImpl.java:34-39, dashboard TextViewImpl.java:49 and query TextViewImpl.java:54; `no GIN binding (constructor-injected)`
- **Names:** Widget keeps name and package (rule 1), not stroom.data.client.component. ClassificationLabelController is free. Workbench stories that call new ClassificationLabel(harness.getUiConfigCache()) (ClassificationLabelStories.java:95, ClassificationWrapperStories.java:69, SourcePresenterStories.java:197, SourceViewerStories.java:258) move with it.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/data/client/view/ClassificationLabel.java:81,84-86 | `element.style.setProperty("--classification-label__background-color", colour) via JSNI` | dynamic | Already a custom property, used by .classificationLabel (components.css:398-406) and .classification (stroom.css:272-277); keep it, through a shared custom-property helper rather than the class's own JSNI (see cssNotes) |
| stroom-core-client/src/main/java/stroom/data/client/view/ClassificationLabel.java:72 | `String colour = "#888888"` | static | Drop the default: when no label colour matches, remove the property so the stylesheet's fallback var(--classification-label__background-color, #888888) applies |

| Into the template | What | Detail |
| --- | --- | --- |
| ClassificationLabel.ui.xml:3 | css-class | styleName="max classificationLabel" stays in the template; its users replace it with styleName ("dock-min classificationLabel", "dock-min classification"), so the two rule sets could become one BEM block (classification-label, with a --banner variant for stroom.css's .classification) when it moves to its own stylesheet |

**Stays in Java:** ClassificationLabel.java:63-82 upper-cased text and the colour match (data).

## `CustomFileUpload`

widget, smart widget; complexity 3, effort M; used by 9.

**Name (rule 12, agreed after wave 2).** "Custom" only says it was made from GWT's, and GWT's own `FileUpload` is the obvious name, so choose another for what it is (e.g. `FileChooser`), with its block.

- **Component:** `stroom.widget.form.client.CustomFileUpload`
- **Controller:** `stroom.widget.form.client.CustomFileUploadController`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/form/client/CustomFileUpload.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/widget/form/client/CustomFileUpload.ui.xml`
- **Goes:** stroom-core-client-widget/src/main/java/stroom/widget/form/client/FileUploadResultHandler.java (task monitor and reply parsing merge into CustomFileUploadController); CustomFileUpload.java:52-53 and :136-144 static uploadTransport/setUploadTransport (the transport becomes the controller's, injected or set per controller; StoryUploadTransport in the workbench sets it on the controller); CustomFileUpload.java:98-134 onSuccess/onFailure/taskMonitorFactory/setAction/submit (move to the controller: upload(url, onSuccess, onFailure) using context(Contexts.TASK_MONITOR) and dropping late replies once disposed); no GIN binding (widget made by templates: ImportConfigViewImpl, DocumentAssetUploadFileDialogViewImpl, DataUploadViewImpl, KeyStoreSecretViewImpl)
- **Names:** Widget keeps its name and package (rule 1). CustomFileUploadController is free in all_simple_names.json and unique in this wave. FileUploadSubmitter (JSNI XHR), FileUploadTransport and FileUploadCallback stay as the controller's transport helpers; FileUploadUtil stays (pure). Form.gwt.xml must inherit stroom.widget.component.Component for the controller.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/widget/form/client/CustomFileUpload.java:68 | `fileUpload.setVisible(false)` | static | New rule `.file-upload__input { display: none; }` in file-upload.css (the class is already on the input in the template, but file-upload.css has no rule for it) |

| Into the template | What | Detail |
| --- | --- | --- |
| CustomFileUpload.java:70 | aria | role="button" on chooseFile: needs a template attribute on Button (e.g. setRole) or drop it (a Button is already a button) |
| CustomFileUpload.java:71 | aria | aria-haspopup="dialog" on chooseFile: Button gains setOpensDialog(boolean) as LinkButton has, template <b:Button ... opensDialog="true"/> |
| CustomFileUpload.java:75 | aria | aria-live="polite" on fileName: make it an HTMLPanel span (<span ui:field="fileName" class="file-upload__name" aria-live="polite"/>) or a Label with a live-region setter |
| CustomFileUpload.java:49,163-165 | text | initial text "No file chosen" and the file-upload__name--empty modifier can be the template's starting state (text="No file chosen", addStyleNames="file-upload__name--empty") |
| CustomFileUpload.ui.xml:7 | component-declaration | <b:Button ui:field="chooseFile"> already declared (Button, wave 1): keep; switch @UiHandler(ClickEvent) to Button.setOnClick once wave 1 adds it |

**Stays in Java:** CustomFileUpload.java:73-76 unique id and aria-describedby link (FieldValidity.addDescribedBy needs a generated id); CustomFileUpload.java:160-167 file name text and --empty modifier after a file is chosen (state); getFilename(), focus(), and a new setOnChange(Runnable) for the chosen file.

## `ClassificationWrapperView`

composite, tidy; complexity 1, effort S; used by 6.

- **Component:** `stroom.data.client.component.ClassificationWrapper`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/ClassificationWrapperViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/client/component/ClassificationWrapper.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/ClassificationWrapperView.java`; `stroom-core-client/src/main/java/stroom/data/client/view/ClassificationWrapperViewImpl.java (becomes ClassificationWrapper)`; stroom-core-client/src/main/java/stroom/data/client/presenter/ClassificationUiHandlers.java (implemented by ElementPresenter.java:67, SteppingPresenter.java:107, SourcePresenter.java:68; SourcePresenter.setClassificationUiHandlers :713 becomes setOnClassification(Consumer<String>)); stroom-core-client/src/main/java/stroom/data/client/gin/StreamStoreModule.java:164 bindSharedView(ClassificationWrapperView, ClassificationWrapperViewImpl); `stroom-dashboard-gwt/src/main/java/stroom/dashboard/client/gin/DashboardAppModule.java:192 bindSharedView(...)`; stroom-gwt/stroom-gwt-workbench/.../gin/processing/StreamStoreScreenModule.java:116-117, gin/query/StreamStoreScreenModule.java:116-117, gin/content/StreamStoreScreenModule.java:116-117, gin/editors/StreamStoreScreenModule.java:117-118 bindSharedView(...)
- **Names:** ClassificationWrapper is free. A dumb component with a content slot (setContent(IsWidget)) and setClassification(String), made by GIN with no binding; it needs no controller of its own: its owners (DataPresenter, SourcePresenter, ElementPresenter via Provider<ClassificationWrapperView>) attach a ClassificationLabelController to getLabel(). DataPresenter extends MyPresenterWidget<ClassificationWrapperView> (DataPresenter.java:88), so it takes the component as its view until it is converted.

| Into the template | What | Detail |
| --- | --- | --- |
| ClassificationWrapperViewImpl.ui.xml:7; ClassificationWrapperViewImpl.java:34-39 | component-declaration | <l:ClassificationLabel ui:field="classification" styleName="dock-min classification"/> becomes a plain declaration (no provided=true) once ClassificationLabel has a no-arg constructor |

**Stays in Java:** ClassificationWrapperViewImpl.java:49-56 setContent and setClassification (the content and text change).

## `TimePopup`

dialog, presenter; complexity 2, effort S; used by 4.

- **Component:** `stroom.widget.datepicker.client.component.TimePicker`
- **Controller:** `stroom.widget.datepicker.client.component.TimePickerController`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/datepicker/client/TimeViewImpl.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/component/TimePicker.ui.xml`
- **Goes:** `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/TimePopup.java (with its TimeView interface, :68-79)`; `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/TimeViewImpl.java (becomes TimePicker)`; stroom-core-client/src/main/java/stroom/monitoring/client/gin/MonitoringModule.java:83-86 bindPresenterWidget(TimePopup, TimeView, TimeViewImpl); stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/ScreenViewsModule.java:113 bindPresenterWidget(TimePopup, TimeView, TimeViewImpl)
- **Names:** Rule 1 would give TimePopup + TimePopupController, but the component is the dialog's content, not the popup; TimePicker (free in all_simple_names.json; the workbench already calls its stories TimePickerStories) + TimePickerController, which shows it with caption "Set Time" and OK/Cancel and has edit(Time, Consumer<Time>) as FieldEditor.edit. Same Picker naming for DateTimePicker, TimeRangePicker, SchedulePicker, StepLocationPicker in this wave. Package stroom.widget.datepicker.client.component (the area's client.component); DatePicker.gwt.xml must then inherit stroom.widget.component.Component.

| Into the template | What | Detail |
| --- | --- | --- |
| TimeViewImpl.java:57-58,61-62,65-66 | structure | minStep="1" maxStep="1" on hour, minute and second (setMinStep/setMaxStep take int, which UiBinder parses) |
| TimeViewImpl.java:55-56,59-60,63-64 | structure | min/max (0-23, 0-59): UiBinder has no long parser and ValueSpinner.setMin/setMax take long; move once ValueSpinner gains int-taking setters with distinct names (see cssNotes), else they stay |
| TimeViewImpl.ui.xml:8 | structure | give the hour FormGroup a ui:field (hourGroup) so setHourVisible hides the group, as minute/second do (TimeViewImpl.java:89 hides only the spinner, leaving its label) |
| TimeViewImpl.ui.xml:12-32 | component-declaration | FormGroup, ValueSpinner and LinkButton (waves 1-2) already declared; switch @UiHandler(ClickEvent/ValueChangeEvent) to setOnClick/setOnChange as those widgets gain them |

**Stays in Java:** TimeViewImpl.java:70-85 getTime/setTime (values); TimeViewImpl.java:88-100 setHourVisible/setMinuteVisible/setSecondVisible (state set by TimeBox's owner); TimeViewImpl.java:118-146 Reset handlers (behaviour); TimePopup.java:41-57 caption "Set Time" and OK/Cancel move to TimePickerController (a dialog's caption is the controller's).

## `ItemSelectionPresenter`

dialog, presenter; complexity 1, effort S; used by 4.

- **Component:** `stroom.data.client.component.ItemSelection`
- **Controller:** `stroom.data.client.component.ItemSelectionController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/ItemSelectionViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/client/component/ItemSelection.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/ItemSelectionPresenter.java (and its ItemSelectionView :73-88)`; `stroom-core-client/src/main/java/stroom/data/client/view/ItemSelectionViewImpl.java (the unused EventBus parameter :51 goes)`; StreamStoreModule.java:149-152 and DashboardAppModule.java:185-188 bindPresenterWidget(ItemSelectionPresenter, ItemSelectionView, ItemSelectionViewImpl); `workbench gin/processing, query, content StreamStoreScreenModule.java:107-109 and gin/editors/StreamStoreScreenModule.java:108-110`
- **Names:** ItemSelection and ItemSelectionController are free. The controller shows it (caption "Select " + display name, OK/Cancel) and returns the item with a callback, edit(offset, count, Consumer<Long>), rather than writing to HasItems itself (ItemSelectionPresenter.java:45-50).

| Into the template | What | Detail |
| --- | --- | --- |
| ItemSelectionViewImpl.java:97 | text | "of " moves to the template as its own label (<g:Label styleName="item-selection__of">of</g:Label>) beside the count, leaving only the number in Java |
| ItemSelectionViewImpl.java:56 | structure | min (1) once ValueSpinner has an int-taking setter; :57 max stays (Long.MAX_VALUE, and set from data at :98-102) |
| ItemSelectionViewImpl.ui.xml:5-9 | css-class | itemSelectionViewImpl-flowPanel/-panelChild/-countLabel (components.css:6-16; -panelChild is an empty rule) move to BEM item-selection, item-selection__count in a new item-selection.css |
| ItemSelectionViewImpl.ui.xml:7 | aria | the spinner has no name: nameLbl is never given text (ItemSelectionView.setName :75 is never called), so name the spinner in the template (e.g. a FormGroup label="Item", or accessibleName) and drop nameLbl; record in ISSUES.md |
| ItemSelectionViewImpl.ui.xml:7 | component-declaration | ValueSpinner (wave 2) already declared |

**Stays in Java:** ItemSelectionViewImpl.java:85-103 item number and count text and max (values from data); ItemSelectionPresenter.java:62 caption built from display.getName() (data) in the controller.

## `StepLocationPresenter`

composite, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.pipeline.stepping.client.component.StepLocationPicker`
- **Controller:** `stroom.pipeline.stepping.client.component.StepLocationPickerController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pipeline/stepping/client/view/StepLocationViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/StepLocationPicker.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/presenter/StepLocationPresenter.java (and its StepLocationView :49-54)`; `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/view/StepLocationViewImpl.java`; stroom-core-client/src/main/java/stroom/pipeline/client/gin/PipelineModule.java:118 bindPresenterWidget(StepLocationPresenter, StepLocationView, StepLocationViewImpl); `workbench gin/processing, query, content PipelineScreenModule.java:111-113`
- **Names:** StepLocation clashes with the shared DTO stroom.pipeline.shared.stepping.StepLocation, which this component reads and returns, so it is StepLocationPicker (free) with StepLocationPickerController, which shows it ("Set Location", OK/Cancel) with edit(StepLocation, Consumer<StepLocation>), taking over StepLocationLinkPresenter.java:56-68.

| Into the template | What | Detail |
| --- | --- | --- |
| StepLocationViewImpl.ui.xml:6 | structure | <g:FormPanel ui:field="form" styleName="max"> has no Java field and never submits: drop it, the inner FlowPanel ("max form") becomes the root |
| StepLocationViewImpl.java:43,45,47 | structure | min (0, 1, 1) once ValueSpinner has an int-taking setter; max (Long.MAX_VALUE, :42,44,46) stays in Java (no long parser) |
| StepLocationViewImpl.ui.xml:8-21 | component-declaration | FormGroup and ValueSpinner (waves 1-2) already declared |

**Stays in Java:** StepLocationViewImpl.java:61-70 the location's values (1-based in the spinners).

## `ItemNavigatorPresenter`

widget, presenter; complexity 2, effort S; used by 5.

- **Component:** `stroom.data.client.component.ItemNavigator`
- **Controller:** `stroom.data.client.component.ItemNavigatorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/ItemNavigatorViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/client/component/ItemNavigator.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/ItemNavigatorPresenter.java (and its ItemNavigatorView :76-85)`; stroom-core-client/src/main/java/stroom/data/client/view/ItemNavigatorViewImpl.java (the unused EventBus :74 and static Binder field :47 go); StreamStoreModule.java:145-148 and DashboardAppModule.java:181-184 bindPresenterWidget(ItemNavigatorPresenter, ItemNavigatorView, ItemNavigatorViewImpl); `workbench gin/processing, query, content StreamStoreScreenModule.java:104-106 and gin/editors/StreamStoreScreenModule.java:105-107`
- **Names:** ItemNavigator and ItemNavigatorController are free. The component gets callbacks (setOnFirst/Previous/Next/Last/Refresh, or one setOnAction(Consumer<NavigatorAction>) as the pilot's "set of actions is one enum" rule says, shared with CharacterNavigator) and setState(...) pushed from HasItems by the controller, which also opens ItemSelectionController with makeDialog (replacing setLabelClickHandler).

| Into the template | What | Detail |
| --- | --- | --- |
| ItemNavigatorViewImpl.java:78 | aria | opensDialog="true" on <btn:LinkButton ui:field="lblDetail"> |
| ItemNavigatorViewImpl.java:88-94,108-113 | structure | initial visible="false" on the four ToolbarButtons (they are enabled by default), and drop initButtons/setupButton |
| ItemNavigatorViewImpl.ui.xml:6-7 | css-class | itemNavigator, itemNavigator-label (components.css:650-663) to BEM item-navigator, item-navigator__label in a new item-navigator.css; itemNavigator-button (components.css:665-670) is unused (remove later, as removals wait for other work) |
| ItemNavigatorViewImpl.ui.xml:8-13 | component-declaration | Toolbar, ToolbarButton and LinkButton (wave 1) already declared, and RefreshButton; switch @UiHandler to setOnClick |

**Stays in Java:** ItemNavigatorViewImpl.java:126-137 label text, title and aria-label built from the display's name and offsets; ItemNavigatorViewImpl.java:96-106 button titles built from the display's name (data); ItemNavigatorViewImpl.java:139-160 enabled and visible state, :250 refreshing state.

## `StepLocationLinkPresenter`

widget, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.pipeline.stepping.client.component.StepLocationLink`
- **Controller:** `stroom.pipeline.stepping.client.component.StepLocationLinkController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/StepLocationLink.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/pipeline/stepping/client/presenter/StepLocationLinkPresenter.java (and its StepLocationLinkView :110-119); `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/view/StepLocationLinkViewImpl.java (its unused Binder :58-60 too)`; stroom-core-client/src/main/java/stroom/pipeline/client/gin/PipelineModule.java:114-117 bindPresenterWidget(StepLocationLinkPresenter, StepLocationLinkView, StepLocationLinkViewImpl); `workbench gin/processing, query, content PipelineScreenModule.java:108-110`; stroom-core-client/src/test/java/stroom/pipeline/stepping/client/presenter/TestStepLocationLinkPresenter.java (becomes TestStepLocationLinkController)
- **Names:** StepLocationLink and StepLocationLinkController are free. Used by SteppingPresenter.java:120,162. The controller owns the StepLocationPickerController dialog (makeDialog) and keeps setOnStepLocation(Consumer<StepLocation>) and setEnabled.

| Into the template | What | Detail |
| --- | --- | --- |
| StepLocationLinkViewImpl.java:35-36 | css-class | <b:LinkButton ui:field="button" addStyleNames="step-location-link"/>: stepLocationLink (components.css:1026-1041) to BEM step-location-link in stepping-screen.css |
| StepLocationLinkViewImpl.java:37 | text | title="Set Location" |
| StepLocationLinkViewImpl.java:38 | aria | opensDialog="true" |
| StepLocationLinkViewImpl.java:53 | css-class | stepLocationLink--disabled: style from [aria-disabled="true"], which DisabledState.set already sets (:55), so the modifier goes |
| StepLocationLinkPresenter.java:89 | text | initial text "[??:??:??]" can be the template's text, as the link starts with no location (:50) |
| new template | component-declaration | LinkButton (wave 1) declared rather than built with new (StepLocationLinkViewImpl.java:35) |

**Stays in Java:** StepLocationLinkPresenter.java:86-103 text and aria-label built from the location; StepLocationLinkViewImpl.java:52-56 enabled state (DisabledState).

## `TimeBox`

widget, smart widget; complexity 2, effort S; used by 2.

- **Component:** `stroom.widget.datepicker.client.TimeBox`
- **Controller:** `stroom.widget.datepicker.client.TimeBoxController`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/TimeBox.ui.xml`
- **Goes:** TimeBox.java:40,45,69-75,197-199 Provider<TimePopup> popupProvider, getPopup() and setPopupProvider (after its callers move: ProfilePeriodEditPresenter.java:50-51 and TimePickerStories.java:81); TimeBox.java:179-189 setHourVisible/setMinuteVisible/setSecondVisible reaching into the popup's view (move to TimeBoxController, which passes them to TimePicker); `no GIN binding of its own`
- **Names:** Widget keeps name and package. TimeBoxController (free) attaches to a declared box as DocSelectionController does (attach(box)), opens TimePickerController with makeDialog, and pushes the chosen time back. Kept beside the widget rather than in client.component as it belongs to a widget, not a presenter unit.

| Into the template | What | Detail |
| --- | --- | --- |
| TimeBox.java:78-80 | css-class | <g:TextBox ui:field="textBox" styleName="date-time-box__input stroom-control allow-focus"/> |
| TimeBox.java:81 | aria | aria-haspopup="dialog": UiBinder can't set it on g:TextBox; give IconBox an opensDialog attribute that marks its field (wave 1 IconBox change), or keep in Java |
| TimeBox.java:83-85 | component-declaration | <svg:IconBox styleName="icon-box date-time-box date-time-box--time" icon="HISTORY"><g:TextBox .../></svg:IconBox>: needs IconBox (wave 1) to take an icon attribute (SvgImage enum) and its field as a child instead of setWidget(widget, image) |

**Stays in Java:** TimeBox.java:47-67 key, click, blur and focus handlers (behaviour); TimeBox.java:125-140 setEnabled/setReadOnly and svgIconBox.setReadonly (state); TimeBox.java:150-177 value, FieldValidity invalid state; ValueChangeEvent stays beside a new setOnChange until its callers move (rule 5); new setOnOpenPicker(Runnable) for the controller.

## `SchedulePopup`

dialog, presenter; complexity 3, effort M; used by 8.

- **Component:** `stroom.schedule.client.component.SchedulePicker`
- **Controller:** `stroom.schedule.client.component.SchedulePickerController`
- **Template:** `stroom-core-client/src/main/resources/stroom/schedule/client/ScheduleViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/schedule/client/component/SchedulePicker.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/schedule/client/SchedulePopup.java (and its ScheduleView :205-218)`; `stroom-core-client/src/main/java/stroom/schedule/client/ScheduleViewImpl.java (becomes SchedulePicker)`; stroom-core-client/src/main/java/stroom/schedule/client/ScheduleUiHandlers.java (becomes SchedulePicker.setOnTypeChange(Consumer<ScheduleType>)); stroom-core-client/src/main/java/stroom/monitoring/client/gin/MonitoringModule.java:75-78 bindPresenterWidget(SchedulePopup, ScheduleView, ScheduleViewImpl); `stroom-gwt/stroom-gwt-workbench/.../gin/MonitoringScreenModule.java:53-55 bindPresenterWidget(SchedulePopup, ...)`
- **Names:** Schedule clashes with stroom.util.shared.scheduler.Schedule (the value it edits), so the component is SchedulePicker (free) and SchedulePickerController (free), consistent with this wave's other pickers. ScheduledTimeClient stays as a REST helper (also used by JobNodeListHelper, JobNodeListPresenter, NodeJobListPresenter), but the controller calls it with owner(this) so late replies are dropped. Other injectors of Provider<SchedulePopup> (BatchExecutionScheduleEditPresenter, ScheduledProcessEditPresenter, JobNodeListPresenter, NodeJobListPresenter, JobNodeListHelper) move to SchedulePickerController/ScheduleBoxController in the same commit.

| Into the template | What | Detail |
| --- | --- | --- |
| ScheduleViewImpl.java:75 | structure | icon="HISTORY" on <b:Button ui:field="calculate"> (Button.setIcon(SvgImage), enum attribute) |
| ScheduleViewImpl.java:78 | aria | aria-live="polite" on nextScheduledTime: a span in an HTMLPanel or a Label with a live-region attribute in the template |
| ScheduleViewImpl.java:89,92,98,103; ScheduleViewImpl.ui.xml:15 | component-declaration | <b:QuickSettings ui:field="quickSettings" addStyleNames="w-100"/> declared once, refilled with clear() and addGroup, instead of a SimplePanel and a new QuickSettings for each type |
| ScheduleViewImpl.java:79 | structure | the starting cron mode can be the template's (Quick Settings filled by the first setScheduleType), so the constructor's enterCronMode() goes |
| ScheduleViewImpl.ui.xml:8-30 | component-declaration | FormGroup, SelectionBox and Button (waves 1-2) already declared; @UiHandler("scheduleType") becomes setOnChange |

**Stays in Java:** ScheduleViewImpl.java:72-74 schedule type items (enum values); ScheduleViewImpl.java:90-91,99-102,107-119 quick-setting groups and presets (from FrequencyExpressions/CronExpressions data); ScheduleViewImpl.java:121-131 visibility for the instant type (state); SchedulePopup.java:154-165 Last Executed and Next Scheduled Time text ("Never", formatted times, errors from data); SchedulePopup.java:176 caption "Change Schedule" and the OK validation with AlertEvent.fireWarn move to SchedulePickerController.

## `CharacterRangeSelectionPresenter`

dialog, presenter; complexity 2, effort S; used by 4.

- **Component:** `stroom.data.client.component.CharacterRangeSelection`
- **Controller:** `stroom.data.client.component.CharacterRangeSelectionController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/CharacterRangeSelectionViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/client/component/CharacterRangeSelection.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/data/client/presenter/CharacterRangeSelectionPresenter.java (and its CharacterRangeSelectionView :119-126); stroom-core-client/src/main/java/stroom/data/client/view/CharacterRangeSelectionViewImpl.java (becomes CharacterRangeSelection; its From/To enums :269-300 go with it, at the bottom of the class); StreamStoreModule.java:137-140 and DashboardAppModule.java:173-176 bindPresenterWidget(CharacterRangeSelectionPresenter, CharacterRangeSelectionView, CharacterRangeSelectionViewImpl); `workbench gin/processing, query, content StreamStoreScreenModule.java:98-100 and gin/editors/StreamStoreScreenModule.java:99-101`
- **Names:** CharacterRangeSelection and its controller are free (DataRange, the value, is stroom.util.shared.DataRange and is not reused as a name). The controller shows it ("Set Source Range"), validates with getDataRangeProblem (CharacterRangeSelectionPresenter.java:89-117, pure, can be JUnit-tested) and raises AlertEvent.fireError(this, ...) as a controller may for app-wide requests.

| Into the template | What | Detail |
| --- | --- | --- |
| CharacterRangeSelectionViewImpl.java:72-78 | aria | AccessibleName.set(...) names become template attributes, e.g. <l:SelectionBox ui:field="fromType" accessibleName="Type"/>, <lci:LineColInput accessibleName="Line and column"/>, <v:ValueSpinner accessibleName="Character offset"/>, "Character count": needs setAccessibleName on SelectionBox, LineColInput and ValueSpinner (see cssNotes) |
| CharacterRangeSelectionViewImpl.java:93,96,99 | structure | min (1) once ValueSpinner has an int-taking setter; max stays (Long.MAX_VALUE and set from data at :236-242) |
| CharacterRangeSelectionViewImpl.ui.xml:9,16,25 | css-class | characterRangeSelectionViewImpl-infoLine/-options (components.css:573-586) to BEM character-range-selection__info, character-range-selection__options in a new character-range-selection.css |
| CharacterRangeSelectionViewImpl.ui.xml:13-31 | component-declaration | FormGroup, SelectionBox, LineColInput and ValueSpinner (waves 1-2) already declared |

**Stays in Java:** CharacterRangeSelectionViewImpl.java:82-89,193-213 the From/To items (depend on the From choice); CharacterRangeSelectionViewImpl.java:189-190,219-221 which control is visible (state); CharacterRangeSelectionViewImpl.java:229 total count text (data); CharacterRangeSelectionPresenter.java:97-113 problem messages built from the range.

## `DateTimePopup`

dialog, presenter; complexity 2, effort S; used by 10.

- **Component:** `stroom.widget.datepicker.client.component.DateTimePicker`
- **Controller:** `stroom.widget.datepicker.client.component.DateTimePickerController`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/datepicker/client/DateTimeViewImpl.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/component/DateTimePicker.ui.xml`
- **Goes:** stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/DateTimePopup.java (with DateTimeView, :75-82; getDateTimeModel() :71-73 has no callers); `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/DateTimeViewImpl.java (becomes DateTimePicker)`; stroom-core-client/src/main/java/stroom/monitoring/client/gin/MonitoringModule.java:79-82 bindPresenterWidget(DateTimePopup, DateTimeView, DateTimeViewImpl); stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/ScreenViewsModule.java:112 bindPresenterWidget(DateTimePopup, DateTimeView, DateTimeViewImpl)
- **Names:** DateTimePicker (free; matches the existing story group Widgets/Date & Time/DateTimePicker) rather than DateTimePopup, as the component is the dialog's content; DateTimePickerController shows it ("Set Date And Time", OK/Cancel) with edit(long, Consumer<Long>). Injected Provider<DateTimePopup> users (BatchExecutionScheduleEditPresenter.java:76-77, ScheduledProcessEditPresenter.java:95-96, FloorMapTimelineSettingsViewImpl.java:63-64, FloorMapObjectEditViewImpl.java:180, CredentialSettingsPresenter.java:62, ReplaceSecretPresenter.java:61, ViewStateStories.java:481, DateTimePickerStories.java:208) move to DateTimeBoxController in the same commit. DateTimeModel is a pure model with a no-arg constructor (DateTimeModel.java:75): the component makes its own rather than having GIN inject it.

| Into the template | What | Detail |
| --- | --- | --- |
| DateTimeViewImpl.java:90-91,94-95,98-99,102-103 | structure | minStep="1" maxStep="1" on hour, minute, second; minStep="1" maxStep="10" on millisecond |
| DateTimeViewImpl.java:88-89,92-93,96-97,100-101 | structure | min/max (0-23, 0-59, 0-999) once ValueSpinner has int-taking, distinctly named setters (no long parser in UiBinder); until then they stay |
| DateTimeViewImpl.ui.xml:13-64 | component-declaration | Calendar, FormGroup, FormLabel, LinkButton and ValueSpinner already declared; switch @UiHandler to setOnClick/setOnChange as waves 1-2 add them |

**Stays in Java:** DateTimeViewImpl.java:106-383 date/time resolution, DST fixes and labels (values built from data); DateTimeViewImpl.java:345 and :382 date and time label text (formatted from the value); DateTimePopup.java:44-61 caption and OK/Cancel move to DateTimePickerController.

## `CharacterNavigatorPresenter`

widget, presenter; complexity 2, effort S; used by 4.

- **Component:** `stroom.data.client.component.CharacterNavigator`
- **Controller:** `stroom.data.client.component.CharacterNavigatorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/CharacterNavigatorViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/client/component/CharacterNavigator.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/CharacterNavigatorPresenter.java (and its CharacterNavigatorView :102-113)`; stroom-core-client/src/main/java/stroom/data/client/view/CharacterNavigatorViewImpl.java (the unused EventBus :71 and the commented-out code :293-304 go); `CharacterNavigatorPresenter.java:34,41,46-47,51-53,64-85 the owned but never shown ProgressPresenter and its commented-out progress code`; `CharacterNavigatorViewImpl.ui.xml:11-12 commented-out progress panel`; StreamStoreModule.java:141-144 and DashboardAppModule.java:177-180 bindPresenterWidget(CharacterNavigatorPresenter, CharacterNavigatorView, CharacterNavigatorViewImpl); `workbench gin/processing, query, content StreamStoreScreenModule.java:101-103 and gin/editors/StreamStoreScreenModule.java:102-104`
- **Names:** CharacterNavigator and its controller are free. The declares list's ProgressPresenter (wave 3, Progress) is not declared: it was never shown, so it is dropped rather than declared. Shares the navigator action enum and callbacks with ItemNavigator; the controller opens CharacterRangeSelectionController with makeDialog.

| Into the template | What | Detail |
| --- | --- | --- |
| CharacterNavigatorViewImpl.java:75-78 | aria | opensDialog="true" and title="Set Source Range" on both lblLines and lblCharacters |
| CharacterNavigatorViewImpl.java:80 | structure | enabled="false" on <refresh-button:RefreshButton ui:field="refresh"/> |
| CharacterNavigatorViewImpl.java:83-88,94-99 | structure | initial visible="false" on the three ToolbarButtons; drop initButtons/setupButton |
| CharacterNavigatorViewImpl.ui.xml:6-9 | css-class | characterNavigator, characterNavigator-labels, characterNavigator-label (components.css:783-801) to BEM character-navigator, __labels, __label in a new character-navigator.css; characterNavigator-button (components.css:803-806) is unused (remove later) |
| CharacterNavigatorViewImpl.ui.xml:13-19 | component-declaration | Toolbar, ToolbarButton and LinkButton (wave 1) already declared, and RefreshButton |

**Stays in Java:** CharacterNavigatorViewImpl.java:42-43,111-129,149,163-166 range text ("Lines", "Chars" and numbers built from data) and its aria-label :190-195; CharacterNavigatorViewImpl.java:134-170 enabled state, :178-187 visibility (state).

## `ScheduleBox`

widget, smart widget; complexity 3, effort M; used by 4.

- **Component:** `stroom.schedule.client.ScheduleBox`
- **Controller:** `stroom.schedule.client.ScheduleBoxController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/schedule/client/ScheduleBox.ui.xml`
- **Goes:** ScheduleBox.java:52,59,138-140,168-173 Provider<SchedulePopup> and setSchedulePresenterProvider (callers: BatchExecutionScheduleEditPresenter.java:79, ScheduledProcessEditPresenter.java:99); ScheduleBox.java:64-65,257-260 scheduleReferenceTimeConsumer (the controller fetches the reference time; ScheduledProcessEditPresenter.java:101 passes its REST call); ScheduleBox.java:86-114,142-166 validate/showPopup REST through the popup move to ScheduleBoxController (validate(Consumer<ScheduledTimes>) is called by BatchExecutionScheduleEditPresenter.java:123 and ScheduledProcessEditPresenter.java:186); `no GIN binding of its own`
- **Names:** Widget keeps name and package (rule 1), not stroom.schedule.client.component. ScheduleBoxController (free) attaches to the declared box, owns SchedulePickerController (makeDialog) and the validate REST call, and marks the box invalid through a setter (setInvalid). Same shape as DocSelectionController.

| Into the template | What | Detail |
| --- | --- | --- |
| ScheduleBox.java:117-118 | css-class | <g:TextBox ui:field="textBox" styleName="schedule-box__input stroom-control allow-focus"/> |
| ScheduleBox.java:119 | aria | aria-haspopup="dialog" through IconBox's opensDialog attribute (as TimeBox, DateTimeBox), else stays |
| ScheduleBox.java:121-123 | component-declaration | <svg:IconBox styleName="icon-box schedule-box" icon="HISTORY"><g:TextBox .../></svg:IconBox> (wave 1 IconBox with icon attribute and child slot) |

**Stays in Java:** ScheduleBox.java:67-84 key, click, blur and focus handlers; ScheduleBox.java:187-218 enabled, read-only and instant read-only state; ScheduleBox.java:228-249 value and restriction; ScheduleBox.java:251-255 ValueChangeEvent beside a new setOnChange until callers move (rule 5); add setOnOpenPicker(Runnable) and setInvalid(boolean).

## `DateTimeBox`

widget, smart widget; complexity 3, effort M; used by 19.

- **Component:** `stroom.widget.datepicker.client.DateTimeBox`
- **Controller:** `stroom.widget.datepicker.client.DateTimeBoxController`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/DateTimeBox.ui.xml`
- **Goes:** DateTimeBox.java:62,68,123-133,318-324 Provider<DateTimePopup>, getPopup() with its StaticEventBus fallback (:127-129) and setPopupProvider, once all 19 users attach a DateTimeBoxController (rule 5: the fallback stays until then); `no GIN binding of its own`
- **Names:** Widget keeps name and package. DateTimeBoxController (free) attaches to a declared box (attach(box)), opens DateTimePickerController with makeDialog, and replaces both the StaticEventBus fallback and the injected Provider<DateTimePopup>. DynamicSettingsPane (712) and TimeRangePicker (195) make or declare DateTimeBoxes that need one attached by their owners' controllers.

| Into the template | What | Detail |
| --- | --- | --- |
| DateTimeBox.java:90-91 | css-class | <g:TextBox ui:field="textBox" styleName="date-time-box__input stroom-control allow-focus"/> |
| DateTimeBox.java:92 | aria | aria-haspopup="dialog": through an IconBox opensDialog attribute (shared with TimeBox and ScheduleBox), else stays in Java |
| DateTimeBox.java:95-97 | component-declaration | <svg:IconBox styleName="icon-box date-time-box" icon="CALENDAR"><g:TextBox .../></svg:IconBox> (needs IconBox's icon attribute and child slot from wave 1) |

**Stays in Java:** DateTimeBox.java:74-86,112-121 key, click, blur and focus handlers; DateTimeBox.java:170-185 setEnabled/setReadOnly and icon read-only state; DateTimeBox.java:212-302 value, text, optional, expressionsAllowed and FieldValidity state; DateTimeBox.java:304-316 ValueChangeEvent and addKeyDownHandler stay beside a new setOnChange until callers move (rule 5); add setOnOpenPicker(Runnable).

## `TimeRangeSelector`

widget, smart widget; complexity 2, effort S; used by 3.

- **Component:** `stroom.query.client.view.TimeRangeSelector`
- **Controller:** `stroom.query.client.view.TimeRangeSelectorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/query/client/view/TimeRangePopup.ui.xml (the selector itself has none)` → `stroom-core-client/src/main/java/stroom/query/client/view/TimeRangeSelector.ui.xml (the dialog content: stroom-core-client/src/main/java/stroom/query/client/component/TimeRangePicker.ui.xml)`
- **Goes:** stroom-core-client/src/main/java/stroom/query/client/view/TimeRangeDialog.java (presenter and its TimeRangeView interface :77-79; becomes stroom.query.client.component.TimeRangePickerController); stroom-core-client/src/main/java/stroom/query/client/view/TimeRangePopup.java (ViewImpl; becomes stroom.query.client.component.TimeRangePicker); `TimeRangeSelector.java:19,46,66-73 StaticEventBus and the hand-made dialog`; `no GIN bindings (the dialog was built with new)`
- **Names:** The selector is a widget declared in QueryToolbarViewImpl's template, so it keeps its name and package (rule 1), not stroom.query.client.component as the record suggested. TimeRangeDialog cannot be reused for the content: TimeRangeDialog is today's presenter (deleted here). TimeRange clashes with stroom.query.api.TimeRange (used by this code), so the content is TimeRangePicker and its dialog controller TimeRangePickerController (both free), in stroom.query.client.component. TimeRangeSelectorController (free) attaches to the selector and opens TimeRangePickerController, anchored below it (PopupLocation.BELOW).

| Into the template | What | Detail |
| --- | --- | --- |
| TimeRangeSelector.java:54-55 | css-class | <b:LinkButton ui:field="button" addStyleNames="time-range-selector"/> |
| TimeRangeSelector.java:56 | aria | opensDialog="true" on the LinkButton (LinkButton.setOpensDialog) |
| TimeRangeSelector.java:57-59 | text | title="Time range to apply to all queries in this dashboard.&#10;The selected time range applies to the time field applicable to each query's data source." |
| TimeRangePopup.java:52-57; TimeRangePopup.ui.xml:9 | component-declaration | <b:QuickSettings ui:field="quickSettings" addStyleNames="w-100"/> declared in TimeRangePicker.ui.xml instead of a SimplePanel and new QuickSettings() |
| TimeRangePopup.ui.xml:7-20 | component-declaration | FormGroup (wave 1) and DateTimeBox (this wave) already declared; LinkButton (wave 1) declared in TimeRangeSelector.ui.xml |

**Stays in Java:** TimeRangePopup.java:53-56,112-116 the quick-setting groups and presets (built from TimeRanges constants, data); TimeRangeSelector.java:94-102 aria-disabled and time-range-selector--readonly (state); TimeRangeSelector.java:124,133 button text and aria-label "Time range: " + text (built from data); TimeRangeSelector.java:154-157 HasValue/ValueChangeEvent beside a new setOnChange until callers move (rule 5); TimeRangeDialog.java:42 caption "Time Range" moves to TimeRangePickerController.

## `DynamicSettingsPane`

widget, tidy; complexity 3, effort M; used by 1.

- **Component:** `stroom.dashboard.client.vis.DynamicSettingsPane`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/dashboard/client/vis/DynamicSettingsPane.ui.xml`
- **Goes:** `DynamicSettingsPane.java:51,381-393,402,414-416 commented-out opacity and resize code`; `no GIN binding (made with new by its owner)`
- **Names:** Widget keeps name and package. Dumb (no REST, bus or context), so no controller; it gains setReadOnly and setOnChange. Its date boxes (createDateBox, :244-272) use DateTimeBox's StaticEventBus fallback today; once DateTimeBox loses it the pane must hand its date boxes to its owner's controller (e.g. getDateBoxes() or a setOnDateBoxAdded callback) to attach DateTimeBoxControllers. VisInputChoices and VisFieldOption stay (pure helpers).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/dashboard/client/vis/DynamicSettingsPane.java:392 | `// getElement().getStyle().setOpacity(opacity) (commented out)` | static | Dead code: delete; fading is Layer.setLayerVisible's (:397) |

| Into the template | What | Detail |
| --- | --- | --- |
| DynamicSettingsPane.java:54-56 | structure | <g:FlowPanel ui:field="outer" styleName="max form"/> as the template's root |

**Stays in Java:** DynamicSettingsPane.java:73-97 one FormGroup per control, with label, required, help text and feedback from the visualisation's JSON (data); DynamicSettingsPane.java:100-329 the controls (SelectionBox, TextBox, DateTimeBox, ValueSpinner, TickBox) and their w-100 classes (:184,215,248,276) and min/max/steps, made per JSON control, so they can't be declared; DynamicSettingsPane.java:428-458 FieldControl choices and feedback.

