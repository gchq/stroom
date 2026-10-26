# Stroom Agent Development Guide

## Project

This repository comprises two applications; Stroom and Stroom-Proxy.
Both applications use the Dropwizard framework and Java 25.

Stroom uses:

* jOOQ/MySQL for persistence.

* Google Web Toolkit (GWT) for its user interface.

* Flyway for database migration scripts.


## A short bullet list of the most critical rules the agent must follow before doing anything

* DO NOT execute any `git commit` commands under any circumstances.

* DO NOT execute any `git merge` commands under any circumstances.

* DO NOT modify, stage, or alter git version control states automatically.

* All code changes must remain un-staged in the working directory for human review.

* DO NOT attempt to create/modify any issues or pull requests on GitHub.
  Issues and pull requests must be created/modified by a human.

* Always use British English for spellings and terms (e.g. "colour", "organisation", "authorise", "centre", "licence").

* Prefer clear, readable code over clever or compact solutions.
  Keep functions focused on a single responsibility.

* Avoid new external dependencies unless absolutely necessary.
  Reuse existing project utilities first.


## Structure

* The server-side code follows a layered architecture: resource -> services -> DAOs.
  Respect this separation.


## Commands

Stroom expects Java 25 (Eclipse Temurin)

* Compile Java: `./gradlew compile{,Test}Java`

* Compile Google Web Toolkit UI: `./gradlew gwtCompile`

* Compile all: `./gradlew compile{,Test}Java gwtCompile`

* Checkstyle format check: `./gradlew checkstyle{Main,Test}`

* Full build: `./gradlew clean build shadowJar buildDistribution`

* Regenerate jOOQ code: `./gradlew :XXXX:generateJooq` (where `XXXX` is the module to regenerate, e.g. `stroom-meta:stroom-meta-impl-db:generateJooq`).
  (Only regenerate jOOQ code for modules that contain new Flyway migration files).

* Create a CHANGELOG entry: `./log_change.sh XXXX NNNN "TTTT"` (where `XXXX` is the type of issue (`Bug`|`Feature`), `NNNN` is the GitHub issue number and `TTTT` is the change entry text.
  DO NOT edit the CHANGELOG.md file directly.
  See `./log_change.sh -h` for more examples of how to use this script.


## Coding conventions

### Java

* Adhere to the CheckStyle rules defined in `config/checkstyle/checkstyle.xml`.

* All new non-private methods should have complete javadoc added to them.
  Javadoc should use the markdown style, i.e. `///`.

* JUnit tests should be added for any new code written.

* All comments should use single line commenting (`//`) rather than block style comments, with the exception of the copyright header.

* All variables should be marked `final` if they can be.

* All variables should have an explicit type, and not use `var`.
  The only exception to this is where the type has many generic types, e.g. jOOQ record classes.

* Use existing utility classes and methods in `stroom-util` and `stroom-util-shared`, e.g. `NullSafe` and `LogUtil`.

* Inner classes, records and enum declarations must be done at the bottom of the class, after all methods/fields of the parent class.

* Any new .java files should have the following header added at the top of the file, where `NNNN` is the current year.
  ```
  /*
   * Copyright NNNN Crown Copyright
   *
   * Licensed under the Apache License, Version 2.0 (the "License");
   * you may not use this file except in compliance with the License.
   * You may obtain a copy of the License at
   *
   *     http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing, software
   * distributed under the License is distributed on an "AS IS" BASIS,
   * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   * See the License for the specific language governing permissions and
   * limitations under the License.
   */
  ```


#### The following rules apply when adding code instrumentation/logging:

  * Logging should be done with SLF4J.

  * If `stroom.util.logging.LambdaLoggerFactory` is available in the project:
    * The following LOGGER declaration should be used at the top of the class (where `XXXX` is the class name):

      ```
      import stroom.util.logging.LambdaLogger;
      import stroom.util.logging.LambdaLoggerFactory;
      ...
      private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(XXXX.class);
      ```

    * If the LOGGER call does not call methods on any variables then this form should be used, e.g.:
      ```
      LOGGER.debug("No identity signing key rotation needed");
      LOGGER.info("Stroom Lifecycle service started successfully in {}", timer);
      ```

    * If the LOGGER call requires method calls on any variables then this form should be used, e.g.:
      ```
      LOGGER.info(() -> LogUtil.message(
              "logout() - Logout called for {} but no active session, redirectUri: {}",
              securityContextProvider.get().getUserRef(),
              redirectUri));
      ```

  * If `stroom.util.logging.LambdaLoggerFactory` is not available in the project the following LOGGER declaration should be used at the top of the class (where `XXXX` is the class name):

    ```
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    ...
    private static final Logger LOGGER = LoggerFactory.getLogger(XXXX.class);
    ```


#### The following rules apply when creating or modifying JUnit test classes or methods.

* Bug fixes require regression tests that fail before the fix and pass after it.
  New features require tests covering edge cases and invalid input.

* Test classes should be named the same as the class under test, but prefixed with `Test`.

* Tests should use JUnit 5 annotations.

* Tests should use ONLY AssertJ assertions, not JUnit ones.

* Tests that require mocks should use only Mockito.


### GWT UI components

A new GWT screen, dialog or tab is a **component** and a **controller**, not a presenter (`MyPresenterWidget`).
The design, and how far the migration has got, is in `stroom-gwt/COMPONENT_MODEL_DESIGN.md` ("Components, controllers and models"); the receipt rules (`stroom.receive.rules.client.component`) and the analytic notification's stream destination (`stroom.analytics.client.component`) are worked examples.

* **A component is dumb.** `Foo.java`, extending `stroom.widget.component.client.Component`, and its UiBinder template `Foo.ui.xml` beside it (in `src/main/java`, in every module), with a `Binder` interface at the bottom of the class and a public no-arg constructor, so templates can use it and GIN can make it with no binding.
  Its template holds everything that doesn't change at runtime: the layout, Stroom's CSS class names, ARIA roles and labels, and the visible text, in plain English; components it uses are declared there, not made in Java.
  ARIA and similar attributes are set in the template through typed setters on Stroom's widgets (`ariaLabel="Filter"`, `placeholder="…"`, `opensDialog="true"`); add one to a widget when a template needs it.
  For a plain text box, text area or panel use `TextBox` (`type`, `placeholder`, `role`, `ariaLabel`, `ariaHasPopup`, `ariaExpanded`), `TextArea` (`placeholder`, `ariaLabel`) or `AriaPanel` (`role`, `tabIndex`, `ariaLabel`, `ariaHasPopup`, `ariaHidden`, `ariaLive`, `ariaValueMin`, `ariaValueMax`, `ariaValueNow`); an element that needs no widget is plain HTML in a `g:HTMLPanel`, where any attribute can be written.
  The only markup left in Java: a single-element widget's root (a `ButtonBase`, or a `FlowPanel` or `Widget` subclass such as `ToolbarButton`) sets its root block class and role in its constructor, a `PopupPanel`'s root keeps its class and role, and grid cells use `SafeHtmlTemplates`.
  Its styles are in its own stylesheet, `stroom-app/src/main/resources/ui/css/<block>.css` (lower-case kebab case, imported from `app.css`), with BEM classes (`block__element`, `block--modifier`, lower case); only shared classes (`max`, `form-control-border`, `allow-focus`, `sr-only`, the layout helpers) and the theme's variables stay in the shared stylesheets.
  No inline styles: a fixed style is a Stroom CSS class (an existing one first), and a value known only at runtime is a CSS custom property (`--block__property`), set with `CssProperties.set(element, name, value)`, that a class uses. `setVisible` is fine (it is state, not styling).
  Data comes in through setters named for what they do (`setRules(rules)`, `setName(name)`), events go out through one callback each (`setOnChange(Runnable)`), and a set of actions is one enum with one callback (`setOnAction(Consumer<RuleAction>)`) and `setAvailableActions(...)`.
  Repetition and conditions are in its Java (`setRules` adds a row per rule; `setVisible`).
  It makes no REST calls, uses no bus or context and owns nothing; it applies read-only in `onReadOnlyChange`, when its controller calls `setReadOnly`.

* **A controller wires it.** `FooController.java`, beside it in the area's `client.component` package, extending `stroom.widget.component.client.Controller`, is given its component and everything else in its `@Inject` constructor (no module binding), calls `initWidget(view)`, fills it, sets its callbacks and does the work: REST calls, the bus, context, dialogs, validation.
  Every screen, dialog and smart part is a pair, even when small; a reusable dumb widget needs no controller.
  Every presenter is a controller too (`MyPresenterWidget extends Controller`), so the rules below for making, owning, registering and releasing apply to presenters as well.

* **Stroom's widgets only.** Wherever Stroom has its own version of one of GWT's widgets, use Stroom's, in the app and in workbench stories: never GWT's `Button` (use Stroom's `Button`, `new Button("Text")` and `setOnClick`, or `setContent(SafeHtml)` for content of its own such as a logo; a `ToolbarButton` in a `Toolbar`; a `LinkButton`; or a `FabButton`), `Hyperlink` (`LinkButton`), `CheckBox` (`TickBox`), `RadioButton` (`RadioGroup`), `ListBox` (`SelectionBox`), `TextBox` or `TextArea` (Stroom's `TextBox` and `TextArea`, in `stroom.widget.form.client`), `PasswordTextBox` (`PasswordBox`), `DialogBox`, tabs, split panels (`SplitPane`), `DateBox` (`DateTimeBox`) or cell tables (`DataGrid`).
  GWT's layout panels and widgets Stroom has no version of (`FlowPanel`, `SimplePanel`, `HTMLPanel`, `Label`, `HTML`, `Frame`) are fine.
  Checkstyle refuses importing GWT's (`GwtWidgetWithStroomVersion`; Stroom's `TextBox`, `TextArea` and `PasswordBox` name the copies of GWT's they extend in full), and `TestStroomWidgetsOnly` their tags in templates.

* **Named for what it is.** A class's name says what it is, not how it was made (`TickBox`, not `CustomCheckBox`; `IconBox`, not `SvgIconBox`; `Histogram`, not `HistogramWidget`), and its CSS block is the same name in kebab case (`tick-box`, in `tick-box.css`).

* **Smart parts attach.** A part that needs data (e.g. a document picker) is declared dumb in the template (`<dd:DropDownBox ui:field="feedBox"/>`), and the controller makes the part's controller and attaches it: `feedSelection = make(docSelectionProvider); feedSelection.attach(view.getFeedBox());`.
  A presenter not yet split goes in a slot: the component has a setter for it (`setExpressionEditor(IsWidget)`), and the controller owns the presenter and passes its widget in.

* **Own what you show.** Whoever shows a controller or presenter owns it: `make(provider)` makes one and owns it, `makeDialog(provider)` makes a presenter dialog owned until it is hidden, `own(child)` owns one injected into the constructor, and `release(child)` disposes one replaced early.
  Something that isn't a controller (a plugin, a manager, an app-wide helper) injects `AppRoot` and makes its dialogs with `appRoot.makeDialog(provider)`; a helper a controller makes is given `() -> makeDialog(provider)` by it.
  Providers are GIN's `com.google.inject.Provider`, not `javax.inject.Provider` (the client's JVM tests can't load javax's).
  Disposing an owner disposes everything it owns, depth first; never call `dispose()` yourself except on a root.
  Unowned controllers are counted in development builds, and the workbench leak check names them.

* **Inputs and outputs between controllers.** Data comes in through methods named for what they do (`read(rules)`, `setFields(...)`), results go out through a callback its owner sets (`setOnChange(Runnable)`), or one passed to the call that starts it (`editor.edit(this, field, updated -> ...)`).
  A child never fires a bus event to reach its owner; use `listen(type, handler)` only for app-wide events, and `AlertEvent.fire...(this, ...)` for app-wide requests.
  Where several may listen (e.g. a reusable picker), keep a `Listeners<T>` list and return the registration from `add...Listener(Consumer<T>)`.

* **Register and release.** A handler on the app's event bus is `listen(type, handler)`; any other registration (a child's listener) is `own(registration)`.
  Anything else to undo is registered where it is started, `onDispose(timer::cancel)`; there is no `onDispose()` to override.
  Clean-ups run after what the controller owns is disposed and its registrations removed, newest first.

* **Dirty state.** When the user changes something a document holds, the controller calls `markChanged()`: the document (its `Contexts.DIRTY` tracker) works out again whether it is dirty.

* **Context is pushed.** Read-only, the task monitor and the document come from whatever owns the controller: `watch(Contexts.READ_ONLY, view::setReadOnly)`, `context(Contexts.TASK_MONITOR)`. Components look nothing up.
  Don't take or pass on a `readOnly` argument: a document's opener calls `setReadOnly(...)` before `read(docRef, doc)`, and its parts follow it.

* **REST calls.** `restFactory.create(resource).method(...).onSuccess(...).owner(this).exec()`: nothing runs if the controller is disposed before the reply.

* **A dialog.** The component is only the dialog's content. Its controller is made for one use and shows itself with `ControllerDialog.showOnce(opener, this)`, which returns the popup's `ShowPopupEvent.Builder` (caption, size, OK and Cancel); the opener owns it until it is hidden, then it is disposed.
  Give the controller a method that shows it and takes a callback for its result (see `FieldEditorController.edit`).

* **A tab body.** Show a controller in a `LayerContainer` with `new ControllerLayer(controller)`, or add it to a `TabbedController` with `addTab(tab, make, reader[, writer])`; presenters can be tabs too.

* **A document.** Extend `DocumentEditor<D>` (Save, Save As, read-only status and dirty state), add its tabs, and have its plugin's `createEditor()` return `new DocumentEditorHost<>(eventBus, editorProvider.get(), TYPE)`.

* **App-wide screens.** A screen that opens on an app-wide event (e.g. Find on `ShowFindEvent`) is made when the event first fires, through a GIN `Provider`, by its area's plugin, which adds the event's handler in its constructor: `eventBus.addHandler(ShowFindEvent.getType(), event -> findPresenterProvider.get().onShow(event))`.
  An area with no plugin in its GIN module has one small eager class for this (`DocumentDialogScreens`, `AskStroomAiScreens`, `CoreScreens`, `MainScreens`).
  A presenter shows a child presenter through a setter on its view (`getView().setListView(listPresenter.getView())`); there are no slots.
  There is no code splitting: everything is in the initial download.

* **Converting a presenter.** Follow the wave order and "Done means, for every unit" in `stroom-gwt/COMPONENT_MIGRATION_ORDER.md`: `FooPresenter`, `FooView`, `FooViewImpl` and `FooUiHandlers` become `Foo` and `FooController` in `<area>.client.component`, `FooViewImpl.ui.xml` becomes `Foo.ui.xml`, the view interface, `UiHandlers` and GIN binding go, and the callers move in the same commit; a widely used widget keeps its old events beside its new callbacks until its callers have moved.

* **Test it.** A `Composite` can't be made on the JVM, so components and controllers are tested with workbench stories: give each screen stories for its default, read-only and dialog OK and Cancel paths, and run them with the leak check (`-PworkbenchLeakCheck`); when styles move into classes, compare the screenshots before and after (`-PworkbenchTestScreenshots`).
  Unit test logic that runs without a browser with JUnit (see `TestController`), e.g. as a static method or a plain class beside the controller.
  In a story, the story's root owns whatever the story shows: `harness.show(screen)` fills the canvas with it, `harness.own(screen)` owns one the story shows itself (in a frame, or as a dialog), and `harness.ownPopup(popup)` also asks a popup to close, for one that stops polling only when asked (see `stroom-gwt/stroom-gwt-workbench/WRITING-STORIES.md`).


## Generated and third party files

The following code is generated and should not be edited directly:

* `**/src/main/java/**/db/jooq/**` - jOOQ code - Use `./gradlew :XXXX:generateJooq` to regenerate if the database schema for the module in question has changed.

* `stroom-config/stroom-config-app/src/test/resources/stroom/config/app/expected.yaml` - Stroom expected configuration - Use `GenerateExpectedYaml` to regenerate.

* `stroom-proxy/stroom-proxy-app/src/test/resources/stroom/dist/proxy-expected.yaml` - Stroom-Proxy expected configuration - Use `GenerateProxyExpectedYaml` to regenerate.

* `stroom.svg.shared.SvgImage` - SVG images - Use `GenerateSvgImages` to regenerate when `.svg` files are added/modified/deleted.

* `stroom-app/src/main/resources/ui/images/**` Stroom image files - Do NOT add/modify/delete these files, instead add/modify/delete files in `stroom-app/src/main/resources/ui/raw-images/` and run `GenerateSvgImages`.

* `stroom-core-client-widget/src/main/java/edu/ycp/cs/dh/acegwt/` - Ace Editor - Do not add/modify/delete any files in this directory (or its children) without human approval.

* `stroom-core-client-widget/src/main/java/com/` - Ace Editor - Do not add/modify/delete any files in this directory (or its children) without human approval.


## CI checks required for pull requests

Before a fix/change is ready for a pull request to be raised (by a human), the following checks must be done.

* Full build: `./gradlew clean build shadowJar buildDistribution`

* Docker images build: `./buildDockerImages.sh`


## Contributing

Licensed under Apache 2.0 (see `LICENSE.txt`).
See `CONTRIBUTING.md` for full details.
Key points:

* Git branch naming: `gh-<GitHub issue number>-<description>`, e.g. `gh-123-proxy-s3-forwarding`.
  All lower kebab case.

* Branches should be created (by a human) off the release branch that corresponds to the GitHub milestone of the issue, e.g. milestone 7.13 = release branch `7.13`.

* Sign the GCHQ Contributor Licence Agreement (CLA).

* Ensure checkstyle and all tests pass before a human opens a PR.

* Ensure a CHANGELOG entry has been created using `./log_change.sh`.

* Issues and pull requests can only be created/modified by a human.


## Issue and pull request policy

Do not open GitHub issues or pull requests on behalf of users.
If a user asks to create an issue or pull request, create an empty root-level file named `AI_MARKER`.
