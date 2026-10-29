# Writing Stroom's GWT UI

How to write a screen, dialog, tab or widget in Stroom's GWT user interface. It applies to the client
code in `stroom-core-client`, `stroom-core-client-widget`, `stroom-dashboard-gwt`, `stroom-app-gwt`,
`stroom-statistics/stroom-statistics-client` and the workbench (`stroom-gwt/`).

A screen, dialog or tab is a **component** and a **controller**; there are no presenters.
Why it is built this way, and how the code got here, is in [`COMPONENT_MODEL_DESIGN.md`](COMPONENT_MODEL_DESIGN.md)
("Components, controllers and models"). The receipt rules (`stroom.receive.rules.client.component`)
and the analytic notification's stream destination (`stroom.analytics.client.component`) are worked
examples. Stories are written as [`stroom-gwt-workbench/WRITING-STORIES.md`](stroom-gwt-workbench/WRITING-STORIES.md)
says.

## Components and controllers

* **A component is dumb.** `Foo.java`, extending `stroom.widget.component.client.Component`, and its UiBinder template `Foo.ui.xml` beside it (in `src/main/java`, in every module), with a `Binder` interface at the bottom of the class and a public no-arg constructor, so templates can use it and GIN can make it with no binding.
  Its template holds everything that doesn't change at runtime: the layout, Stroom's CSS class names, ARIA roles and labels, and the visible text, in plain English; components it uses are declared there, not made in Java.
  ARIA and similar attributes are set in the template through typed setters on Stroom's widgets (`ariaLabel="Filter"`, `placeholder="…"`, `opensDialog="true"`); add one to a widget when a template needs it.
  For a plain text box, text area or panel use `TextBox` (`type`, `placeholder`, `role`, `ariaLabel`, `ariaHasPopup`, `ariaExpanded`), `TextArea` (`placeholder`, `ariaLabel`) or `AriaPanel` (`role`, `tabIndex`, `ariaLabel`, `ariaHasPopup`, `ariaHidden`, `ariaLive`, `ariaValueMin`, `ariaValueMax`, `ariaValueNow`); an element that needs no widget is plain HTML in a `g:HTMLPanel`, where any attribute can be written.
  The only markup left in Java: a single-element widget's root (a `ButtonBase`, or a `FlowPanel` or `Widget` subclass such as `ToolbarButton`) sets its root block class and role in its constructor, a `PopupPanel`'s root keeps its class and role, and grid cells use `SafeHtmlTemplates`.
  Data comes in through setters named for what they do (`setRules(rules)`, `setName(name)`), events go out through one callback each (`setOnChange(Runnable)`), and a set of actions is one enum with one callback (`setOnAction(Consumer<RuleAction>)`) and `setAvailableActions(...)`.
  Repetition and conditions are in its Java (`setRules` adds a row per rule; `setVisible`).
  Its handlers are wired in Java, in its constructor (`button.setOnClick(...)`, `box.addKeyDownHandler(...)`), never with `@UiHandler` (checkstyle refuses it, `UiHandlerAnnotation`). Use the widget's callback where it has one and nothing else sets it, otherwise its `addXxxHandler`.
  It makes no REST calls, uses no bus or context and owns nothing; it applies read-only in `onReadOnlyChange`, when its controller calls `setReadOnly`.

* **A controller wires it.** `FooController.java`, beside it in the area's `client.component` package, extending `stroom.widget.component.client.Controller`, is given its component and everything else in its `@Inject` constructor (no module binding), calls `initWidget(view)`, fills it, sets its callbacks and does the work: REST calls, the bus, context, dialogs, validation.
  Every screen, dialog and smart part is a pair, even when small; a reusable dumb widget needs no controller. A screen that is one smart part and nothing else (e.g. the node groups' list in a content tab) shows that part's widget as its own, rather than wrap it in a component with nothing in it; give it a component when it gains anything of its own.

* **Named for what it is.** A class's name says what it is, not how it was made (`TickBox`, not `CustomCheckBox`; `IconBox`, not `SvgIconBox`; `Histogram`, not `HistogramWidget`), and its CSS block is the same name in kebab case (`tick-box`, in `tick-box.css`).

## Widgets and styles

* **Stroom's widgets only.** Wherever Stroom has its own version of one of GWT's widgets, use Stroom's, in the app and in workbench stories: never GWT's `Button` (use Stroom's `Button`, `new Button("Text")` and `setOnClick`, or `setContent(SafeHtml)` for content of its own such as a logo; a `ToolbarButton` in a `Toolbar`; a `LinkButton`; or a `FabButton`), `Hyperlink` (`LinkButton`), `CheckBox` (`TickBox`), `RadioButton` (`RadioGroup`), `ListBox` (`SelectionBox`), `SuggestBox` (`SuggestionBox`, whose value is any text, with suggestions as hints), `Label`, `InlineLabel`, `HTML`, `Frame` or `Anchor` (Stroom's `Label`, `InlineLabel`, `Html`, `Frame` and `Link`, in `stroom.widget.text.client`), `TextBox` or `TextArea` (Stroom's `TextBox` and `TextArea`, in `stroom.widget.form.client`), `PasswordTextBox` (`PasswordBox`), `DialogBox`, tabs, split panels (`SplitPane`), `DateBox` (`DateTimeBox`) or cell tables (`DataGrid`).
  GWT's layout panels and widgets Stroom has no version of (`FlowPanel`, `SimplePanel`, `HTMLPanel`, `Label`, `HTML`, `Frame`) are fine.
  No raw HTML controls either: no `<input>`, `<select>`, `<textarea>` or `<button>` in a template's HTML, in `SafeHtml` or a cell's markup, or made with `DOM`/`Document` in Java. Use Stroom's widget, or in a grid Stroom's cell (`TickBoxCell`, `SelectionBoxCell`, `TextFieldCell`, `ValueSpinnerCell`), which give the control its keyboard behaviour, ARIA and looks. Only Stroom's own widgets and cells wrap native controls (e.g. `TickBox`, `TextBox`, `ColourBox`, `FileChooser`, `Button`, `TextFieldCell`); a control Stroom lacks is added as a new widget or cell like them, not built in place.
  Checkstyle refuses importing GWT's (`GwtWidgetWithStroomVersion`; Stroom's `TextBox`, `TextArea` and `PasswordBox` name the copies of GWT's they extend in full), and `TestStroomWidgetsOnly` their tags in templates.

* **Its own stylesheet, BEM.** A component's styles are in its own stylesheet, `stroom-app/src/main/resources/ui/css/<block>.css` (lower-case kebab case, imported from `app.css`), with BEM classes (`block__element`, `block--modifier`, lower case); only shared classes stay in the shared stylesheets: the element defaults (`base.css`, no classes), the layout helpers (`layout-helpers.css`: `max`, the dock containers, `w-100`, `sr-only` and the rest), the shared patterns (`control-box`, `form-control-border`, `allow-focus` …) and the theme's variables.

* **No inline styles.** A fixed style is a Stroom CSS class (an existing one first), and a value known only at runtime is a CSS custom property (`--block__property`), set with `CssProperties.set(element, name, value)`, that a class uses. `setVisible` is fine (it is state, not styling).

## Making, owning and releasing

* **Smart parts attach.** A part that needs data (e.g. a document picker) is declared dumb in the template (`<dd:DropDownBox ui:field="feedBox"/>`), and the controller makes the part's controller and attaches it: `feedSelection = make(docSelectionProvider); feedSelection.attach(view.getFeedBox());`.
  A part that has no component of its own to declare goes in a slot: the component has a setter for it (`setExpressionEditor(IsWidget)`), and the controller owns the part's controller and passes its widget in.

* **Own what you show.** Whoever shows a controller owns it: `make(provider)` makes one and owns it, `makeDialog(provider)` makes one owned until it is hidden (a dialog), `own(child)` owns one injected into the constructor, and `release(child)` disposes one replaced early.
  Something that isn't a controller (a plugin, a manager, an app-wide helper) injects `AppRoot`: `appRoot.make(provider)` makes one the app keeps (e.g. a dialog shown again and again), `appRoot.makeDialog(provider)` one owned until it is hidden, and `ControllerDialog.showOnce(appRoot, provider.get())` shows a dialog for one use; a helper a controller makes is given `() -> makeDialog(provider)` by it.
  Providers are GIN's `com.google.inject.Provider`, not `javax.inject.Provider` (the client's JVM tests can't load javax's).
  Disposing an owner disposes everything it owns, depth first; never call `dispose()` yourself except on a root.
  Unowned controllers are counted in development builds, and the workbench leak check names them.

* **Inputs and outputs between controllers.** Data comes in through methods named for what they do (`read(rules)`, `setFields(...)`), results go out through a callback its owner sets (`setOnChange(Runnable)`), or one passed to the call that starts it (`editor.edit(this, field, updated -> ...)`).
  A child never fires a bus event to reach its owner; use `listen(type, handler)` only for app-wide events, and `AlertEvent.fire...(this, ...)` for app-wide requests.
  Where several may listen (e.g. a reusable picker), keep a `Listeners<T>` list and return the registration from `add...Listener(Consumer<T>)`.

* **Register and release.** A handler on the app's event bus is `listen(type, handler)`; any other registration (a child's listener) is `own(registration)`.
  Anything else to undo is registered where it is started, `onDispose(timer::cancel)`; there is no `onDispose()` to override.
  Clean-ups run after what the controller owns is disposed and its registrations removed, newest first.

## State, context and requests

* **Dirty state.** When the user changes something a document holds, the controller calls `markChanged()`: the document (its `Contexts.DIRTY` tracker) works out again whether it is dirty.

* **Context is pushed.** Read-only, the task monitor and the document come from whatever owns the controller: `watch(Contexts.READ_ONLY, view::setReadOnly)`, `context(Contexts.TASK_MONITOR)`. Components look nothing up.
  Don't take or pass on a `readOnly` argument: a document's opener calls `setReadOnly(...)` before `read(docRef, doc)`, and its parts follow it.

* **REST calls.** `restFactory.create(resource).method(...).onSuccess(...).owner(this).exec()`: nothing runs if the controller is disposed before the reply.

* **Input that can't be kept is refused, never changed.** A field whose text must be a value of some kind is a `DateTimeBox`, or a `TextBox` given a check (`setInvalidReason(InputChecks.numberFrom(0, 1))`, or a function of its own giving the reason). While a field shown in a dialog or a document holds such text, OK and Save refuse: the field says why under it (in its `FormGroup`) and takes the focus, on whichever tab it is (`InvalidInputs`; content with tabs implements `RefusesInvalidInput`). Never drop, replace or default what was typed; keep the last value read until the user puts the field right. A duration whose meaning includes 0 says `zeroAllowed="true"`, and one that may be none names it (`noneText="Never"`).
  A client that takes a task monitor is given `taskMonitor()`: the one the controller's owners provide when the request starts (its tab's or dialog's), else Stroom's own busy indicator.

## Kinds of screen

* **A dialog.** The component is only the dialog's content. Its controller is made for one use and shows itself with `ControllerDialog.showOnce(opener, this)`, which returns the popup's `ShowPopupEvent.Builder` (caption, size, OK and Cancel); the opener owns it until it is hidden, then it is disposed. While it is shown, its requests show on the dialog's spinner.
  Give the controller a method that shows it and takes a callback for its result (see `FieldEditorController.edit`).
  A popup shown again and again (e.g. settings kept between showings) is owned by its owner and shown with `ControllerDialog.show(this)`; showing it while it is shown hides it.

* **A tab body.** Show a controller in a `LayerContainer` with `new ControllerLayer(controller)`, or add it to a `TabbedController` with `addTab(tab, make, reader[, writer])`.

* **A document.** Extend `DocumentEditor<D>` (Save, Save As, read-only status and dirty state), add its tabs, and have its plugin's `createEditor()` return `new DocumentEditorHost<>(eventBus, editorProvider.get(), TYPE)`.

* **A content tab** (e.g. Jobs, Users): a controller extending `ContentTabController`, opened by its area's content plugin. Its `TAB_TYPE` is its own (a tab session saves it, and its plugin registers under it to open it again; `TestContentTabController` checks that no two share one).

* **App-wide screens.** A screen that opens on an app-wide event (e.g. Find on `ShowFindEvent`) is made when the event first fires, through a GIN `Provider`, by its area's plugin, which adds the event's handler in its constructor: `eventBus.addHandler(ShowFindEvent.getType(), event -> { if (find == null) { find = appRoot.make(findProvider); } find.show(); })`.
  An area with no plugin in its GIN module has one small eager class for this (`DocumentDialogScreens`, `AskStroomAiScreens`, `CoreScreens`, `MainScreens`).
  There is no code splitting: everything is in the initial download.

## Testing

* **Stories.** A `Composite` can't be made on the JVM, so components and controllers are tested with workbench stories: give each screen stories for its default, read-only and dialog OK and Cancel paths, and run them with the leak check (`-PworkbenchLeakCheck`); when styles move into classes, compare the screenshots before and after (`-PworkbenchTestScreenshots`).
  In a story, the story's root owns whatever the story shows: `harness.show(screen)` fills the canvas with it, `harness.own(screen)` owns one the story shows itself (in a frame, or as a dialog), and `harness.ownPopup(popup)` also asks a popup to close, for one that stops polling only when asked (see [`WRITING-STORIES.md`](stroom-gwt-workbench/WRITING-STORIES.md)).
  `./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchDialogCoverage` lists the screens and dialogs no story covers.

* **JUnit.** Unit test logic that runs without a browser with JUnit (see `TestController`), e.g. as a static method or a plain class beside the controller.
