# Writing stories for the Stroom GWT Workbench

The workbench shows Stroom's real GWT widgets and screens as stories, each optionally with a play
function that drives it as a user would and checks the result. Screens run for real against fake
REST replies, so a story is both a way to look at a screen and a regression test for it.

See [test-runner/README.md](test-runner/README.md) for running the stories, the test runner,
screenshots and the leak check.

## Rules

* **Use Stroom's real widgets, presenters and views**, not look-alikes, created as Stroom creates
  them (see [GIN, not `new`](#gin-not-new)). Where Stroom has no widget for something its views
  build themselves, build the same widgets the same way in the story, copying the view's code, and
  name the source view in the class comment.
* **Check what Stroom does.** A story is a regression test of Stroom's behaviour. Never write a
  check that can't fail (e.g. `queryByText(x).toBeNull()` for text the screen never shows, or
  selecting by a class and then asserting that class); check what the screen does show instead.
* **Record Stroom bugs in [`stroom-gwt/ISSUES.md`](../ISSUES.md).** When a story finds a bug, or
  missing accessibility, record it there with the cause and a suggested fix, port what still works
  (e.g. a fixture without the key that triggers it) and, once it is fixed, keep the story as its
  regression test.
* **Screen stories: no alerts, unmatched requests or failing fixtures** unless the story is about
  one. End the play with `play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled()`
  (and the same for `ScreenHarness.UNHANDLED_REQUEST_SPY`).
* **Registration runs on the JVM** (`TestAllStories`), so a stories class must not touch GWT at
  class-load time: no `static final` fields holding `GWT.create(...)` results (including REST
  resources such as `DictionaryResource`) or widgets. Create them in `render` (a lazily created
  static is fine).
* **Story code is not jakarta→javax transformed.** The stories (and the workbench framework) are
  given to the GWT compiler as they are, while Stroom's code is transformed. A story that imports
  `jakarta.*` (e.g. `jakarta.inject.Inject`) compiles for the JVM and the tests but fails
  `workbenchDraftCompile`; use `com.google.inject.*` (or `javax.*`) instead.

## Packages and registration

* Stories are grouped by title (`Widgets/Buttons/StepControls`, `App/Main/JobsScreen`). One
  `XxxStories` class per title, named after its last part, in a package named after the middle
  part in lower case without spaces or punctuation: `Widgets/Date & Time/*` →
  `client.widgets.dateandtime`, `App/Main/JobsScreen` → `client.app.main.JobsScreenStories`.
* A story's id, and so its URL, is made from its title and export name with Storybook's rules:
  `Widgets/Buttons/Button` + `WithIcons` → `widgets-buttons-button--with-icons`. A display name
  given with `.story(exportName, name, ...)` changes only the name shown, not the id.
* Register the class in `WidgetsStories`, `AppStories` or `ScreensStories`, on the line after the
  comment holding its title. For a new title, add a comment with the title next to the related
  ones (each title has its own line, so that parallel work rarely touches the same lines).
* A screen story's class comment names the Stroom presenter, and lists the REST endpoints the
  story answers (a small table for more than three, as `JobsScreenStories` has).
* Pure-Java arg conversions shared by widget stories are in `widgets/StoryArgs` (`toSvgImage`,
  `getBoolean(args, name, default)`, `getLong`), unit tested by `TestStoryArgs`. Widgets shared
  within a group go in a package-private class in the group's package (e.g.
  `widgets/inputs/InputWidgets`). Don't add to `StoryPanels` for one group's needs.

## Widget stories

A widget story creates the widget, applies the story's args and returns it. See
`client/widgets/buttons/ButtonStories.java`.

* Create views with `new XxxViewImpl(GWT.create(XxxViewImpl.Binder.class))`, presenters with `new`
  and a `SimpleEventBus` (use a `ScreenHarness` only for screens that make REST calls). Widgets with
  a private constructor that Stroom only creates in UiBinder (e.g. `CustomFileUpload`) are created
  with `GWT.create(Xxx.class)`. Implement small interfaces (e.g. `GlobalKeyHandler`) with a private
  no-op nested class.
* **Args.** Stories that only set args use `fromArgs(context)` with an `ArgType` for each option
  (`.args(...)` for the title's args, `.withArgs(...)` for the story's).
* **Callbacks.** Report what the widget does to a spy, `context.fn("onClick")`, called from the
  widget's handler with the value Stroom gives (e.g. a `Long` from `ValueSpinner`), so the Actions
  addon shows it and plays can check it with `play.spy("onClick")`. Use `context.fn` rather than
  `context.action`.
* **Echoes** of the widget's state (`Clicked: 3`, `Value: x`) are a `StoryPanels.note(...)` or a
  `Label` updated by the widget's own events.
* **Story-only adapters.** Where Stroom has no callback and reads the value later (e.g.
  `LineColInput.getLocation()`, `CustomFileUpload.getFilename()`), read it in the story on the
  event a user would expect (blur/Enter, the input's change event).

## Screen stories

Screens (presenters and views) run for real against fake REST replies. Copy the story closest to
your screen:

* a dialog opened by an event, with its own fixtures: `client/app/main/RecentItemsDialogStories`;
* start-up fixtures only, customised: `AboutDialogStories`; a tab's content: `WelcomeScreenStories`;
* simple dialogs: `CredentialPickerDialogStories`, `DocInfoDialogStories`; chained requests:
  `EditTagsDialogStories`; lists with toolbars and permissions: `JobsScreenStories`,
  `NodesScreenStories`, `UsersScreenStories`; polling: `ServerTasksScreenStories`,
  `UserTaskManagerDialogStories`; a document editor: `DictionaryEditorStories`.

### The harness

`ScreenHarness.create(context, fixtures)`, or `ScreenHarness.builder(context, fixtures)...build()`
for options, gives the story everything a screen needs. Create it in `render`, each time the story
renders. Usually a story has one harness, shared by everything it creates, but a rendering may
have several, e.g. two screens with different fixtures or users: each harness has its own
injector, `RestFactory` (which sends its requests with that harness's own dispatcher), fixtures
(with their own sequences), popup manager, UI config cache and location manager, so each answers
only its own screen's requests. The spies are the rendering's, so the harnesses' `REQUEST_SPY`
etc. record into the same spies.

* an event bus, Stroom's real `RestFactory` (its requests are answered by the fixtures) and a
  `PopupManager`, so dialogs appear on the page's body as in Stroom;
* a GIN injector, `harness.getInjector()`, with the services screens commonly need, created as
  Stroom's GIN modules would: `getUiConfigCache()`, `getDateTimeFormatter()`,
  `getUserPreferencesManager()`, `getCurrentPreferences()`, `getClientSecurityContext()`,
  `getLocationManager()` (shortcuts for the common ones are on the harness). Ask for these rather
  than creating them with `new`, so that a presenter and the harness share them. Each is a
  singleton of the harness's injector. To add another singleton, add a getter to
  `ScreenGinjector` (GIN creates anything with an `@Inject` constructor; bind fakes in
  `ScreenGinModule`). Presenters and views are not added there, as each would compile its whole
  graph into every story;
* spies: `REQUEST_SPY` (`"METHOD /path?query body"`), `UNHANDLED_REQUEST_SPY`, `ALERT_SPY`
  (`"ERROR: message"`), `CONFIRM_SPY`, `DOWNLOAD_SPY` and `UPLOAD_SPY` (see [Uploads](#uploads)). A
  story's own spies are made with `harness.fn(name)` **as the story renders** (so a play can check
  they weren't called) and called with `harness.spy(name, detail)`.

```java
final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
        .user(ALICE)                                         // security context + session info
        .appPermissions(AppPermission.MANAGE_USERS_PERMISSION) // ADMINISTRATOR by default
        .uiConfig("{\"welcomeHtml\": \"<h1>Hi</h1>\"}")        // the uiConfig fixture
        .startup(startup -> startup.nodeName("node2a"))      // other start-up fixtures
        .realAlerts()                                        // show alerts as Stroom does
        .build();
harness.getSecurityContext().setDocumentPermission(DocumentPermission.VIEW);
```

Get the screen's presenter from **`AppScreenGinjector`** (`client/app/gin`), or the area's own
ginjector (see [Support by area](#support-by-area)), so the presenter and its graph of child
presenters and views are created (and bound) as in Stroom; give it to the harness with
`.injector(...)`. Small presenters with no graph may still be created with `new` (pass `null` for a
GWTP proxy), as `RecentItemsDialogStories` does.

```java
final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES).injector(injector).build();
harness.afterStartUp(() -> harness.addContent(injector.getJobPresenter()));
return harness.asWidget();
```

A screen opened by an event is shown by registering the presenter as the event's handler and
firing the event, as GWTP's proxy would; a tab's content (not a popup) is added with
`harness.addContent(presenter)`, which fills the canvas (the full page height, as Stroom's content
pane does) and unbinds the presenter on re-render.

Also on the harness, for what Stroom's app does around a screen:

* **`afterStartUp(action)`** runs the action once the harness has loaded the UI config into its
  `UiConfigCache` and the user's preferences into `CurrentPreferences`, as Stroom does at login
  before it shows any screen. Create the screen in it, as Stroom opens screens after login, and
  always when the screen (or anything it creates) reads the cached UI config at once (e.g. a
  `ClassificationLabel`'s colours) or the user's preferences (`EditorPresenter` reads the editor
  preferences, lists format dates with them). Without it, each `UiConfigCache.get` call made before
  the config arrives is answered, which a screen may not expect (`UserListPresenter` then sets up
  its columns twice). Most of the `UsersScreen` stories deliberately open the screen at once:
  they are the regression test for `UiConfigCache.get` calling its consumer with null.
* **Menus**: the harness installs Stroom's `Menu`, so `ShowMenuEvent`s (an `ActionMenuCell`'s
  'Actions...' menu, a grid's context menu) show Stroom's real menu on the page's body. Don't
  install another (menus would show twice).
* **`closeOnCleanUp(popup)`** asks a popup to close on re-render as its Close button would
  (`HidePopupRequestEvent` with `DialogAction.CLOSE`), before the harness hides the popups still
  open. Use it for a popup that stops something only when asked to close, e.g. a polling timer
  cancelled in its `onHideRequest` (`UserTaskManagerPresenter`): the harness's own hiding
  (`HidePopupEvent`) skips `onHideRequest`, and the timer would keep polling the disposed
  dispatcher (a `console.warn` per request).
* `GlobalKeyHandler` is bound to `StoryGlobalKeyHandler`, which ignores the app's keyboard
  shortcuts (e.g. `ctrl+s`), as there is no app shell (see `ShellScreen` for stories that need it).

**Document plugins.** Stroom finds a document's plugin (`DocumentPlugin`: load, save, open its
tab) by type in `DocumentPluginRegistry`, which the app fills as its plugins start; a plugin
registers itself when it is created. Screens that go through a plugin (`OpenDocumentEvent`, an
editor's Save via `DocumentPluginEventManager`, a stepping element's code in `ElementPresenter`, an
embedded document created from a pipeline property, `PipelinePlugin`'s 'Save Pipeline' picker) need
the plugins created from the story's ginjector (which must bind `ContentManager` as a singleton, as
`AppModule` does). `StoryDocumentPlugins` does the rest: `register(harness, eventManager,
plugins...)` unbinds them on re-render, `showOpenedTabs()` shows the tab a plugin opens
(`OpenContentTabEvent`) with `addContent`, and `documentRoutes(builder, path, docJson)` answers a
plugin's `GET` and `PUT /{resource}/v1/{uuid}`. Without a plugin for the type, Stroom fails with
"Cannot read properties of undefined (reading 'load')".

`SteppingScreen`'s `SteppingWithCode` registers `XsltPlugin`, so that a stepping element's code
loads; `PipelineEditor`'s `MultiDocumentSave` opens the pipeline with `OpenDocumentEvent` through
`PipelinePlugin`, whose Save shows the 'Save Pipeline' picker for code edited while stepping.
`PipelineEditor`'s `EmbeddedProperty` stops before OK, as creating the embedded XSLT needs
`XsltPlugin` registered too.

```java
StoryDocumentPlugins.register(harness, injector.getDocumentPluginEventManager(),
        injector.getPipelinePlugin(), injector.getXsltPlugin()).showOpenedTabs();
harness.afterStartUp(() -> OpenDocumentEvent.fire(harness.getHasHandlers(), docRef, true));
```

### Finding the screen and its endpoints

1. Read the screen's **plugin** (`XxxPlugin`, e.g. `UsersPlugin`, `TaskManagerPlugin`,
   `DictionaryPlugin`) to see how Stroom opens the screen, and do the same in `render`:
   `UsersPlugin.open` calls `refresh()`; `NodeMonitoringPlugin` calls `setSelected(node)` for an
   `OpenNodeEvent`; `TaskManagerPlugin` calls `changeNameFilter(...)`; a `DocumentPlugin` loads the
   document, checks `DocumentPermission.EDIT` and calls `read(docRef, doc, !allowUpdate)`. For a
   popup opened by an event (`ShowXxxEvent`, `OpenUserTaskManagerEvent`), register the presenter as
   the event's handler and fire the event; where Stroom fetches what the event carries first (e.g.
   the explorer's Info item fetches `POST /explorer/v2/info`), the story does that request too.
2. Read the presenters for `restFactory.create(XXX_RESOURCE).method(r -> r.xxx(...))` (and their
   `XxxClient` helpers, e.g. `NodeClient`, `CredentialClient`), then the resource interface in the
   `*-shared` module for the HTTP method and `@Path` (constants such as `BASE_PATH` are at its top).
   Paths are relative to `/api`.
3. JSON shapes: the `@JsonProperty` names of the shared classes
   (`grep -o 'JsonProperty("[a-zA-Z]*")'`). Enums are sent by name. Watch for required members: a
   `@JsonCreator` with `Objects.requireNonNull` fails the whole reply (e.g.
   `ExplorerNodeInfo.auditEntries`, `ContentStoreMetadata.ownerId`), and without an `onFailure` the
   screen silently never shows, or a list stays empty with no error.
4. The suite's recorded replies (see [Recorded replies](#recorded-replies)) show the shape of most
   list endpoints (`GET /api/job/v1`, `POST /api/node/v1/find`, `POST /api/users/v1/find`, ...) and
   the members Stroom fills in.
5. Include what Stroom's server always sends, even where the screen doesn't use it: Stroom's
   client reads some of it without null checks (e.g. a processor filter tracker's `status`, a meta
   row's `attributes`, a selection summary's `ageRange`, an execution schedule's `scheduleBounds`,
   the rule and report editors' `analyticUiDefaultConfig`/`reportUiDefaultConfig` UI config).

### Fixtures

`RestFixtures` answers requests by method and path, relative to the REST root (`/api` is not part
of the path). The first matching route replies; a story's routes come before the start-up
fixtures, so they override them.

```java
RestFixtures.builder()
        .get("/sessionInfo/v1", RestReply.json(SESSION_INFO))
        .post("/explorer/v2/find", request -> RestReply.json(find(request.getBody())))
        // A sequence, e.g. for polling: the last reply repeats
        .post("/search/v1", RestReply.json(PENDING), RestReply.json(COMPLETE))
        // Matching the query (exactly, in any order) or the body (JSON value, text or predicate)
        .route(RequestMatcher.get("/node/v1/info").withQuery("node=node1a"), RestReply.json(NODE))
        .route(RequestMatcher.post("/explorer/v2/find").withJsonBody(QUERY), RestReply.json(EVENTS))
        // Matching part of the body (objects need only the members given)
        .route(RequestMatcher.post("/jobNode/v1/find").withJsonBodyContaining(
                "{\"jobName\": {\"string\": \"Data Retention\"}}"), RestReply.json(DATA_RETENTION_NODES))
        .route(RequestMatcher.any("/permission/*"), RestReply.json("true"))
        .build();
```

* Write each reply as a Java text block in Stroom's JSON shape, trimmed to what the screen reads.
  Reuse a block with `.replace("JOB", ...)` (GWT has no `String.formatted`).
* The query string and body are ignored unless a `RequestMatcher` asks for them. `withQuery`
  compares the parameters as sent (URL encoded), in any order, ignoring empty ones; a query with
  no parameters (`withQuery("")`) is the same as `withoutQuery()`, matching `/a` and `/a?`.
  `withJsonBody` compares JSON values with a strict (RFC 8259) parser, so an invalid body never
  matches, and compares numbers exactly (`1` equals `1.0`, but long ids above 2^53 that a
  `double` can't tell apart are different).
* `withJsonBodyContaining(json)` matches a JSON body containing the JSON given, as Jest's
  `toMatchObject` compares (`JsonValues.contains`): an object needs only the members given, each
  containing the expected value; an array must have the same length, item by item. Use it to route
  by one criterion of a request (a job name, a name filter) whatever else the client sends: the
  specific route first, and a catch-all after it for the others (e.g. one job's nodes, and an empty
  list for any other job). This is also how fixtures stand in for what the server computes:
  `ServerTasksScreenStories` replies with the match states for a request whose `nameFilter` is
  'Processor A'.
* Write echoes (e.g. `updateNodeTags` returns the node sent) as a `RestHandler`
  (`request -> RestReply.json(request.getBody())`).
* **Checking requests**: `RequestMatcher.toSpyMatcher()` turns a matcher into a `ValueMatcher` of
  the request spy's calls, so a play checks the method, path, query and body of a request the
  screen made without depending on the JSON's spacing or member order:
  ```java
  play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
          RequestMatcher.put("/jobNode/v1/11/schedule")
                  .withJsonBodyContaining("{\"type\": \"CRON\", \"expression\": \"0 /5 * * * ?\"}")
                  .toSpyMatcher()));
  ```
  `not().toHaveBeenCalledWith(RequestMatcher.put(path).toSpyMatcher())` checks a request was not
  made. `RecordedRequest.parse` reads a spy call back into a request. For sets whose order isn't
  fixed (e.g. `jobNodeIds`) use `withBody(description, predicate)` with `JsonValues.parse`.
* `build()` refuses (`IllegalStateException`) a route that an earlier route makes unreachable,
  e.g. two routes for the same method, path and query without a body rule to tell them apart
  (use a sequence of replies instead), or the same recorded key twice. Routes added with
  `addAll(...)`, such as the start-up fixtures, aren't checked, as a story's route may override
  them. `RequestMatcher.ANY_METHOD` (`*` as a method) and a path ending with
  `RequestMatcher.PATH_WILDCARD` (`/explorer/*`) are separate things.
* Fixtures hold no state, so can be `static final`. A sequence's position belongs to the
  rendering, so it starts again when the story is rerun, rewound or re-rendered. A `RestHandler`
  that keeps state in a field would not reset; use a sequence or create the fixtures in `render`.
* `RestReply`: `json(body)`, `json(status, body)`, `text(body)`/`text(status, body)` (`text/plain`,
  which Stroom's `RestError` shows as the error's text rather than parsing as JSON),
  `error(status, message)` (Stroom's `{"code", "message"}` error JSON), `noContent()`,
  `of(status, contentType, body)`, `networkError(message)` and `timeout()` (both reach
  RestyGWT's `onError`, i.e. the screen's failure handler with no response), plus
  `.delayed(millis)` and `.withHeader(name, value)` (e.g. `Content-Disposition`, `Location`;
  names are case insensitive and replace a header already set, and `Content-Type` replaces the
  reply's content type).
* **`401` is refused** (`IllegalArgumentException`): Stroom's `RestFactoryImpl` reloads the page on
  any `401`, assuming the session has expired, which would reload the workbench forever. Use `403`
  for a request the user isn't allowed to make. An expired session can't be shown in a story.
* A fixture that throws or returns null replies `500`, and fails the story (see
  [Errors and unmatched requests](#errors-and-unmatched-requests)).
* App-wide lookups belong in `StartupFixtures` (e.g. `fetchDocumentTypes`); a screen's own data
  belongs in the story.

#### Start-up fixtures

The harness adds `StartupFixtures` after the story's routes (`fixtures.followedBy(startup)`, which
keeps the story fixtures' strictness): the session info (`GET /sessionInfo/v1`), the extended UI
config (`GET /config/v1/noauth/fetchExtendedUiConfig`), the user preferences
(`GET /preferences/v1`), the app permissions (`GET /permission/app/v1`), document permission checks
(`POST /permission/doc/v1/checkDocumentPermission`) and Stroom's document types
(`GET /explorer/v2/fetchDocumentTypes`, which Stroom caches app-wide). The defaults are the `admin`
user (`ADMINISTRATOR`) on node `node1a`, build `SNAPSHOT`, the usual "About Stroom"
`welcomeHtml`/`aboutHtml` and help URLs, and default preferences in UTC. Customise them with the
harness builder (`user`, `appPermissions`, `uiConfig`, `startup(...)`), or leave them out with
`withoutStartupFixtures()`. `uiConfig(json)` replaces the whole default `uiConfig`; top-level
`ExtendedUiConfig` members (e.g. `dependencyWarningsEnabled`) need
`startup(s -> s.extendedUiConfig(...))`.

#### Security context

The injector binds `ClientSecurityContext` to `StorySecurityContext` (in place of Stroom's
`CurrentUser`): the harness builder's `user`/`appPermissions` set both it and the start-up
fixtures, and `harness.getSecurityContext()` sets document permissions, either a default for all
documents or per document, e.g. `setDocumentPermission(DocumentPermission.VIEW)` for a read-only
screen (set it before the plugin's permission check). `ADMINISTRATOR` implies every app permission,
as in Stroom. Document permission checks are answered immediately, without a request (Stroom's
`CurrentUser` asks the server). It is not logged in by default, as `UiConfigCache` refreshes the UI
config every minute while the user is logged in.

#### Recorded replies

The GWT behaviour suite records a live Stroom's REST replies in its corpus
(`stroom-gwt-suite/corpus/<name>/api/manifest.json`, kept locally; see
[its README](../stroom-gwt-suite/README.md)). They can't simply be pasted:

* the keys are `METHOD /api/path?query #sha1-of-body`, e.g.
  `GET /api/sessionInfo/v1 #da39a3ee5e6b4b0d`;
* each key has an ordered list of replies (a poll's sequence; the last repeats), each with a
  status, content type (sometimes with a `;charset`), some headers and the *name* of a file in
  the same directory holding the body;
* requests that differ only in their body have different keys (e.g. one
  `checkDocumentPermission` key per document); the hash is the first 16 hex digits of the SHA-1
  of the body as UTF-8 (of an empty string for no body).

`RestFixtures.Builder.recorded(key, replies...)` takes a key as it is: the `/api` root is removed,
the query must match exactly and, if the key has a body hash, so must the hash of the request's
body, so several recordings of one endpoint each answer their own request. `CorpusKey.bodyHash`
computes a body's hash. A request whose body holds an id the client mints (e.g. a search's query
key) hashes differently every time; route it with
`route(CorpusKey.parse(key).toMatcherIgnoringBody(), replies...)`, or a body rule, instead. The
bodies can be printed with (from `corpus/default`):

```bash
node -e 'const m=require("./api/manifest.json"), fs=require("fs");
  for (const r of m.entries[process.argv[1]]) console.log(r.status, r.contentType,
    fs.readFileSync("api/" + r.body, "utf8"))' 'GET /api/sessionInfo/v1 #da39a3ee5e6b4b0d'
```

then pasted into `RestReply.of(status, contentType, body)` (use `RestReply.JSON`/`TEXT` when the
content type has a charset). Trim the bodies to what the screen uses. The corpus also rewrites
client-minted ids (e.g. search query keys) on replay; a fixture must use a handler for those.

### What happens on a re-render

When the story renders again (Rerun, Rewind, an args change or another story), the harness is
disposed through `StoryContext.addCleanUp`, before the preview removes what is left on the page:

1. the harness's dispatcher cancels the replies still pending and drops (with a `console.warn`)
   any request that still reaches it. Stroom's `RestFactory` sends each request with the
   dispatcher it was created with, so an old presenter's chained request or polling timer can
   only reach its own (now disposed) harness's dispatcher, never the new rendering's fixtures or
   spies;
2. `UiConfigCache.stopRefreshing()` stops its one-minute refresh timer, and
   `LocationManager.removeWindowClosingHandler()` removes the window-closing handler the harness's
   `StoryLocationManager` added;
3. the popups the harness's `PopupManager` opened are hidden with Stroom's `HidePopupEvent`, which
   unbinds their presenters and restores the focus;
4. the story's clean ups run: `harness.addTimer(timer)`, `harness.addRegistration(registration)`,
   `harness.unbindOnCleanUp(presenter)` and `harness.addCleanUp(runnable)`. Register anything the
   story starts that outlives the rendering, e.g. a presenter shown in the canvas;
5. the event bus stops firing events and the security context reports being logged out, so
   anything else left over does nothing.

The harness registers its clean up before it creates anything, so a harness whose construction
fails part way (e.g. a screen service that throws) is still disposed. Failures while cleaning up
are reported with `console.error`.

Nothing is left behind per rendering: rerunning a screen story 20 times leaves one live interval
timer and one window-closing handler per harness (those of the current rendering's
`UiConfigCache` and `StoryLocationManager`). Anything a story's presenter starts itself (its own
repeating timers or window handlers) must be registered with the harness as above. Check with the
story's Rerun: no "A request from a previous rendering of the story was dropped" warnings after it.

The harness never uses Stroom's network dispatcher: its injector creates Stroom's real
`RestFactoryImpl` with the harness's dispatcher in place of `RestDispatcher` (see
`StroomRestFactory`). `RestFactoryImpl`'s constructor also sets RestyGWT's static default
dispatcher; Stroom sends nothing through that (only `RestFactory` uses RestyGWT), but to be safe
the harness replaces it with one that drops every request with a `console.warn`, so a request
that bypassed `RestFactory` would reach neither the network nor another harness's fixtures.

### Errors and unmatched requests

* A request no route matches gets a `404` reply (`{"code":404,"message":"No fixture for ..."}`),
  is logged with `console.warn` (with its body) and recorded by `UNHANDLED_REQUEST_SPY`.
* Fixtures are **strict** by default: an unmatched request also shows the problem in red at the
  top of the story and reports an uncaught exception, which fails the play. For a story about a
  request that fails, use `RestFixtures.builder()...lenient()`, and check the `404` alert with
  `ALERT_SPY`.
* Errors the screen reports with `AlertEvent` (e.g. Stroom's `DefaultErrorHandler`) are recorded by
  `ALERT_SPY` as `LEVEL: message` (info alerts too, e.g.
  `INFO: Successfully changed permissions.`, which a story checks for rather than ending with
  `expectNoProblems`),
  and confirmations (`ConfirmEvent`) by `CONFIRM_SPY`. They are not shown, and a confirmation is
  never answered, unless the harness is built with `realAlerts()`, which shows Stroom's real alert
  dialog (`AlertPlugin`/`CommonAlertPresenter`). Use it for a story that answers a confirmation
  (e.g. 'Run Now', 'Remove Import', terminating a task) or closes an alert: alerts are shown in a
  `CLOSE_DIALOG` (caption 'Alert', closed with its 'Close' button); confirmations have OK and
  Cancel.
* `RestReply.delayed(millis)` shows a screen loading; a pending reply is cancelled on re-render.

### Downloads and streaming

* **Downloads**: the injector binds Stroom's `LocationManager` to `StoryLocationManager`, which
  records the URL that `ExportFileCompleteUtil` would navigate to in `DOWNLOAD_SPY` (relative to
  the host page, e.g. `resourcestore/my-notes.md?uuid=k1`) instead of navigating away. Each
  harness has its own; give the screen `harness.getInjector().getLocationManager()`. Check with
  `play.expect(play.spy(ScreenHarness.DOWNLOAD_SPY)).toHaveBeenCalledWith(ValueMatcher.stringContaining("resourcestore/"))`.
* **WebSocket/EventSource**: Stroom's GWT client uses neither; it polls with REST, which fixture
  sequences cover.

### Uploads

Stroom's `CustomFileUpload` (the import, data upload, visualisation asset and key store dialogs)
posts the chosen file to the import file servlet (`ImportUtil.getImportFileURL()`,
`importfile.rpc`) through a static `FileUploadTransport` (`CustomFileUpload.setUploadTransport`),
whose default is an `XMLHttpRequest`. The harness installs its own, `StoryUploadTransport`, when it
is built and restores Stroom's default when the story renders again, so uploads never reach the
network. It reads the chosen file (a `FileReader`, asynchronously) and answers the upload from the
story's fixtures:

```java
RestFixtures.builder()
        // Every upload: the servlet stored the file with this resource key and name
        .upload(UploadReply.success("res-1", "import.zip"))
        // Or by the chosen file's name (put these before a route for every upload)
        .upload("bad.zip", UploadReply.failure("Not a zip file"))
        // A sequence: the first upload gets the first reply, and so on; the last repeats
        .upload("a.txt", UploadReply.networkError("Network error during upload"),
                UploadReply.success("k1", "a.txt").delayed(500))
        .build();
```

* `UploadReply.success(key, name)` is the servlet's `PropertyMap` arg line
  (`#PM#success=true key=res-1 name=import.zip#PM#`), which Stroom's `FileUploadResultHandler`
  reads into the `ResourceKey` given to the screen's success handler (e.g. the key sent with
  `ImportConfigRequest`, `UploadDataRequest` or a key store secret). `failure(message)` is the
  servlet's `success=false` reply, whose exception message goes to the screen's failure handler
  (usually an error alert); `networkError(message)` and `httpError(status)` are failures before the
  servlet (Stroom's transport reports `Network error during upload` and
  `Upload failed (HTTP <status>)`). `.delayed(millis)` keeps the upload in flight.
* Replies are asynchronous, as REST replies are; a pending reply is cancelled on re-render, and an
  upload from an old rendering is dropped with a `console.warn`.
* `ScreenHarness.UPLOAD_SPY` records each upload with three arguments: the URL relative to the host
  page (always `importfile.rpc`), the chosen file's name and its content (null unless it is text of
  at most 64 KiB), e.g.
  `play.expect(play.spy(ScreenHarness.UPLOAD_SPY)).toHaveBeenCalledWith("importfile.rpc", "a.txt", "x")`
  (`ValueMatcher.anything()` for an argument that doesn't matter).
* **Strict**: an upload that no upload route matches fails the story, as an unmatched request does
  (it is also recorded by `UNHANDLED_REQUEST_SPY` and fails as Stroom reports a `404`); with
  `lenient()` it only fails the upload.
* Choose the file with `play.upload(input, name, content, mimeType)` on the hidden file input
  (`StroomDom.FILE_INPUT`, inside the dialog): it sets the input's files with a `DataTransfer` and
  fires `input` and `change`, so `CustomFileUpload` shows the name, whether or not the input is
  visible. Then press the dialog's OK, which submits it. Where a story works on what an upload
  returns (e.g. Import's 'Confirm Import'), it uploads the file too.
* Browsers give a file input's value as `C:\fakepath\<name>`. `CustomFileUpload.getFilename()`
  returns just the name (`FileUploadUtil.getFileName`).
* `CustomFileUpload`'s transport is static, so with several harnesses in one rendering the last
  one built answers every upload. Widget stories (no harness) keep Stroom's default transport, so
  they must not submit a `CustomFileUpload`.

### GIN, not `new`

* Get the presenter from `AppScreenGinjector` (or the area's ginjector): add a getter under its
  area's comment. Its modules mirror Stroom's GIN modules (`XxxScreenModule` for
  `stroom...gin.XxxModule`) with the `bindPresenterWidget`/`bindSharedView`/`bind(X)` lines
  copied, plugins (`bindPlugin`), eager singletons and app services (`ClientSecurityContext`,
  `LoginManager`) left out, and each `bindPresenter(P, V, VImpl, Proxy)` turned into
  `bindPresenterWidget(P, V, VImpl)` plus a `@Provides` method returning a null proxy (the story
  registers the presenter as its event's handler instead). Shared widgets (pager views, menus, tool
  tips, editors, expression trees, the explorer drop-downs, date pickers) are bound once, in
  `ScreenViewsModule`.
* GIN checks the whole graph of **every** binding of the injector's modules, used or not, and the
  compile fails if a key is bound twice. So when GIN reports `No @Inject or default constructor
  found for X`, read its "Path to required node", find the Stroom module that binds `X`
  (`grep -rn "X.class" stroom-core-client/src/main/java/stroom/*/client/gin stroom-app-gwt`), and
  either add the mirror of that module to `@GinModules` or, for a widget several areas share, add
  the binding to `ScreenViewsModule` (and leave it out of the area module).
* `Class X is used in Gin, but not available in GWT client code`, or the draft compile's "No source
  code is available for type ...", means a Stroom GWT module isn't inherited: add it to
  `StroomWorkbench.gwt.xml`, copied from `stroom-app-gwt/.../App.gwt.xml`. Widgets often need more
  than their own module (`FormGroup` needs `stroom.widget.help.Help` and
  `stroom.widget.tooltip.Tooltip`; stepping needs `stroom.pipeline.Pipeline` as well as
  `PipelineStepping`), and RestyGWT generates code for every method of a resource interface used,
  so the types of all of them must be available.
* Presenters from GIN are bound (`onBind`) when created, as in Stroom; one made with `new` is not
  bound until it is revealed (or `bind()` is called), so e.g. `CredentialsListPresenter`'s data
  provider would still be null when its dialog is set up.
* Create the injector in `render` (`GWT.create(AppScreenGinjector.class)`), a new one each time:
  the harness refuses an injector another harness has used.
* **Package-private or private Stroom methods** (e.g. `NodeGroupEditPresenter.show`, called only by
  `NodeGroupPresenter`; `LoginPresenter.isSameOrigin`) are called with a JSNI method in the story,
  which ignores Java's access rules, on a real presenter, rather than by opening the whole parent
  screen.
* **Plugins the story stands in for** (`PipelinePlugin.onBeginStepping`/`step`,
  `DataDisplaySupport`, `HyperlinkEventHandlerImpl.openData`, `QueryPresenter.setProcessorLimits`)
  are copied into the story's `render`, naming the source, when they only wire up presenters the
  injector can create.

### Polling and sequences

* Stroom's lists that "poll" (Server Tasks, Nodes, Jobs) refresh when the content pane calls
  `Refreshable.refresh()`, which nothing does in a story, so they make one round of requests.
* A screen with its own timer (`UserTaskManagerPresenter` polls every second) gets the same reply
  each poll (a route's single reply repeats); use a sequence (`get(path, first, second)`) when the
  play needs the data to change. Stop the timer on re-render with `closeOnCleanUp(popup)` (or
  `addTimer` if the story owns it).
* A polling chat (Ask Stroom AI) uses a stateless `RestHandler` that replies with the messages
  after the request's `lastSeenMessageId`, as the server does (a repeated reply would add the
  messages again).
* Debounced inputs (the quick filters, 300-400ms) need a `play.waitFor(3000, ...)` after typing.

## Support by area

Each area has its own ginjector in `client/app/gin/<area>` and helpers for its screens. Reuse them.

* **Document editors** (`client/app/editors/DocEditors`): `DocEditors.render(context, fixtures,
  readOnly, (harness, injector) -> DocEditors.open(harness, docRef, injector.getXxxPresenter(),
  DocResource.of(fetch, update)))` creates the `EditorsScreenGinjector` and a harness with
  `realAlerts()`, gives the user OWNER (or VIEW) on every document, sets up Stroom's
  `StaticEventBus` and `HelpManager` (form help buttons) and, after start-up, opens the document as
  `DocumentPlugin` does (fetch, permission check, `read`, the default tab). The toolbar's Save
  (`SaveDocumentEvent`) is answered as `DocumentPlugin.save`: write, `PUT` to `update`, then the
  editor's post save callback (`VisualisationPresenter` publishes its assets there), then `read`.
  So a story checks a change by editing, saving and checking the `PUT` body.
  `DocEditors.permissionRoutes(builder)` answers the Permissions tabs (admin is OWNER);
  `DocEditors.docSelectionRoutes(builder)` answers `explorer/v2/decorate` and `getFromDocRef`
  (document selection boxes) with the document asked about.
* **Query, rule, report, View, Plan B and statistic store editors**
  (`client/app/gin/query/QueryScreenGinjector`, `client/app/query`):
  `DocumentEditors.open(harness, presenter, docRef, resource, res -> res.fetch(uuid))` does what a
  `DocumentPlugin` does and then fires `ContentTabSelectionChangeEvent` for the editor, as the
  content pane does when it selects a tab (the query table only reacts to annotation changes while
  its tab is visible); `ownerPermissions(builder)` and `decorated(builder, docRefJson...)` answer the
  Permissions tab and doc pickers. The ginjector also gives `HyperlinkEventHandlerImpl` and
  `DataDisplaySupport` (Stroom creates both eagerly), `DateTimeSettingsFactory` and
  `ResultStoreModel`; its `QueryExtrasScreenModule` holds single bindings these screens need
  (`DataUploadPresenter`, `IFrameContentPresenter`).
* **Searches** (`client/app/query/QueryFixtures`, also for dashboards): a search is a poll of
  `POST /query/v1/search/{node}` (`/dashboard/v1/search/{node}` for a dashboard's `SearchModel`)
  until a reply is `complete` or `null`: route `QueryFixtures.SEARCH` with a reply sequence
  (`response(complete, results...)`, `tableResult(...)`, `visResult(...)`). A later reply can be
  `.delayed(...)` to keep the search running (e.g. for a pause button). `editorRoutes(builder)`
  adds, with `addAll` so that a story's own route for the same request wins, what a StroomQL
  editor asks for: help items, time zones, the query's data source, the current activity (Stroom
  checks it before each search), help details and the result store's `destroy`/`terminate`.
  `uiConfigWith(members)` adds members (e.g. `analyticUiDefaultConfig`) to the default UI config.
* **Processing** (pipelines, stepping, processors, data retention and receipt rules, execution
  schedules, data): `ProcessingScreenGinjector` gives `DocumentPluginEventManager`,
  `PipelinePlugin` and `XsltPlugin` for `StoryDocumentPlugins`; shared pipeline replies (element
  types, a stepper) are in `app/main/PipelineFixtures`.
* **Content screens** (content, monitoring, volume, activity and annotation screens):
  `ContentStorySupport` (`expectNoProblems`, `queryParam`, `decorate`, which answers the explorer's
  `decorate` with the document asked about), `ActivityFixtures`, `AnnotationFixtures`.
* **Security** (`client/app/gin/security/SecurityScreenGinjector`, whose `IdentityScreenModule`
  mirrors Stroom's `ChangePasswordModule` without the sign-in pages): `CurrentPasswordPresenter`
  needs Stroom's concrete `CurrentUser` (whose graph GIN can't build here): create it with `new`,
  giving it `new CurrentUser(eventBus, restFactory, null, null)` signed in with
  `setUserAndPermissions(aup, false)`. RestyGWT ignores `@JsonInclude(NON_NULL)`, so a change such
  as `AccountChange` sends the members it leaves alone as `null` (and an empty action set as `[]`):
  check "only these values" with `SecurityPlays.withOnlyValues(matcher, json)`, and read the first
  of several requests with `SecurityPlays.firstBody(spy, matcher)`. Lists that select their first
  row when they load (`UserAccessListPresenter`, `UserDependenciesListPresenter`) are cleared with a
  ctrl-mousedown on the selected row. A `UserListPresenter`'s whole block (label, quick filter,
  toolbar, grid and pager) is its label's `.closest(".dock-container-vertical")`. Rules that
  aren't screens (`DeleteConfirmation.isEmpty`,
  `CurrentUser.hasAppPermission`, `DocumentPluginEventManager` handling `DeleteDocumentEvent`) are
  checked by calling Stroom's code in a value's supplier, or with a real handler created with `new`
  and `bind()` driven by a button per case; create the Stroom objects inside the supplier, so each
  run of the play checks fresh ones.
* **Sign-in pages** (`client/app/gin/idp/IdpScreenGinjector` with `IdpScreenModule`,
  `client/app/idp/IdpPage`,
  `SignInPages`): Stroom's `App.onModuleLoad` reveals `LoginPresenter`,
  `AuthenticationErrorPresenter` and `ResetPasswordPresenter` with `forceReveal()`; a story adds
  the presenter with `addContent` and calls its protected `revealInParent()` with JSNI
  (`IdpPage.reveal`). They look up the host page's `#loading`/`#loadingText` elements when
  created, which `IdpPage.setUp` adds. A screen that reads `Window.Location.getParameter`
  (`error`, `redirect_uri`, `token`) gets them added to the story page's URL with
  `history.replaceState`, restored on clean up. `Window.Location.replace(url)` (which a
  `LocationManager` can't intercept) is cancelled with the browser's Navigation API (Chromium) and
  recorded by the `onRedirect` spy, relative to the origin (`""` for a reload).
* **The whole app shell** (`app/main/ShellScreen`, `client/app/gin/shell/ShellScreenGinjector`):
  `create(context, fixtures, options)` builds the harness (with `realAlerts()`);
  `start(initialDocRef)` then logs in as Stroom does: every main menu plugin, the
  Dictionary/Feed/Folder document plugins and `DocumentPluginEventManager` are created, the dialogs
  GWTP proxies would show are registered lazily (Find, Recent Items, New, Copy, Move, Rename, Info,
  Export, Edit Tags), then the splash screen, the initial activity chooser and `ShowMainEvent`,
  which shows the main view and the explorer and opens the initial document (a deep link).
  `startFullScreen(docRef)` is Stroom's embedded view instead. Reuse it for any story about the
  shell, the explorer, the main menu or opening documents from the explorer.
  `ContentTabPanePresenter.onOpen` calls `forceReveal()`, whose `revealInParent()` clears every
  tab, so a tab pane that isn't visible keeps only the last tab opened; the shell makes the main
  view visible as GWTP's root would (JSNI `PresenterWidget.internalReveal()`), with the tab pane
  already in its content slot, and puts the explorer in its slot after
  `NavigationPresenter.onShowMain`; stories that only need one tab can use
  `harness.addContent(tabPane)` instead. Its ginjector adds `ShellAppScreenModule` (the main view,
  `NavigationPresenter`, About, User Preferences, Welcome, current password and Feed), and it
  cancels `MainPresenter`'s 30 second refresh timer on re-render. `MainPresenter` adds `GlobalKeyHandler` DOM handlers to the
  page's body and never removes them, so the shell passes keys to Stroom's real
  `GlobalKeyHandlerImpl` only until the story renders again; `KeyBinding`'s commands are static, so
  the last shell rendered owns them. Fixture shapes: `fetchDocumentTypes` needs `visibleTypes` as
  well as `types`; the explorer's `create` reply needs the node's `rootNodeUuid`, `uniqueKey` and
  `nodeFlags`; the explorer's permissions are looked up by node, so the reply echoes the nodes
  asked about (`AppShellFixtures.explorerPermissions`); the System and Favourites roots must have
  Stroom's uuids (`0`, `1`).
* **Dashboards** (`app/dashboard/DashboardSupport`, `gin/dashboard/DashboardScreenGinjector`):
  `DashboardSupport.story(docJson, routes, options)` renders Stroom's real
  `DashboardSuperPresenter` (Dashboard, Documentation, Permissions) as `DashboardPlugin` opens it,
  with `realAlerts()`, OWNER permission, the dark theme's preferences
  (`DashboardSupport.DARK_PREFERENCES`) and Save answered as
  `DocumentPlugin.save`. The dashboard is a `DashboardDoc` JSON served from
  `GET /dashboard/v1/{uuid}` (and echoed on `PUT`); `DashboardSupport.fixtures` adds defaults (a
  dashboard search with `t1`'s rows `alpha`/`beta`, a StroomQL search, the referenced query
  `q-ref`, the editor routes and the Permissions tab) after the story's own routes. Options:
  `linkParams` (as `DashboardPlugin.openParameterisedDashboard`), `harness(...)`,
  `setup((harness, injector) -> ...)` and `afterOpen(...)`. `registerComponentTypes` gets every
  component plugin from the injector before the dashboard is read, as Stroom's eager singletons
  fill `ComponentRegistry` (the ginjector keeps `ComponentRegistry` and `VisFunctionCache` as
  singletons, so they belong to the rendering's harness). `app/dashboard/DashboardDocs` builds dashboards in Stroom's JSON
  (`component`, `query`, `table`, `tabs`, `split`, `sized`, `operator`, `term`), adding what Stroom
  needs: a preferred size on every layout, an `automate`, `queryTablePreferences` and full
  `embeddedQueryDoc` reference for every Embedded Query, and the model version (else Stroom treats
  it as legacy and adds a 'Params' input). `app/dashboard/DashboardPlays`: `opened(play)` waits for
  the dashboard; `tab`, `linkTab`, `selectTab`; a tab's menu opens on a click of the *selected* tab
  (`openTabMenu`); `runQuery(play, i)` clicks a Query component's Execute, `runAll` the toolbar's
  (an Embedded Query has none); `simpleMenuItem`; `drag(play, from, to, fx, fy, dx, dy)` drags tabs
  and splitters with mouse events (`FlexLayout` reads them with capture); `closeWindow(play)`
  (`beforeunload`); `at(json, path...)`, `param`, `componentIds` and `componentRequest` read
  request bodies. A search whose result handling throws ends with the error shown in the Query's
  errors, so a play that waits for nothing in particular should check no error is shown.
* **Ask Stroom AI opened by another screen** (a results table's 'Ask Stroom AI' button fires
  `AskStroomAiEvent`): add the ginjector's `AskStroomAIScreenModule` and a getter for
  `AskStroomAiPresenter`, route the chat's requests with `AiFixtures.chatRoutes(builder)` (a
  `DIALOG` config: Stroom's default, `DOCK`, needs the app's main layout) and register the chat
  with `AskStroomAiChat.register(harness, injector::getAskStroomAiPresenter)`. It creates the
  presenter on the first event, as its GWTP proxy does, i.e. after start-up.

## Play functions

A play function builds steps that run after it returns, against the story's rendering. Its API
follows Testing Library, user-event and jest-dom, so their documentation describes what each call
does:

| Java | Testing Library equivalent |
|---|---|
| `play` | `within(canvasElement)` |
| `play.screen()` | `within(document.body)`, `screen` (dialogs and menus are on the body) |
| `play.within(q)` | `within(el)` |
| `"Save"` | `'Save'` |
| `TextMatch.containing("Save")` | `/Save/` |
| `TextMatch.containingIgnoreCase("save")` | `/save/i`, `{exact: false}` |
| `TextMatch.exactIgnoreCase("ok")` | `/^ok$/i` |
| `TextMatch.startingWith("Locked")`, `TextMatch.endingWith("help")` | `/^Locked/`, `/help$/` |
| `TextMatch.regex("close\|ok", "i")` | any other regex |
| `getByRole("button", TextMatch.exact("OK"))` | `getByRole('button', {name: /^OK$/})` |
| `getByText("Name", "label")` | `getByText('Name', {selector: 'label'})` |
| `getAllByX(...).nth(i)`, `.count()`, `.textContents()` | `getAllByX(...)[i]`, `.length`, `.map(e => e.textContent)` |
| `q.closest(sel)` | `el.closest(sel)` |
| `play.querySelector(sel)` (the first match, never ambiguous) | `canvasElement.querySelector(sel)` |
| `play.screen().querySelector(sel)` | `document.querySelector(sel)` |
| `play.within(q).querySelectorAll(":scope > *")` | `el.children` |
| `play.findByText(x)`, `final Query el = play.findByText(x)` | `await findByText(x)` |
| `play.waitFor(5000, () -> play.expect(play.getByText(x)).toBeInTheDocument())` | `findByText(x, {}, {timeout: 5000})` |
| `play.waitFor(8000, () -> ...)` | `waitFor(fn, {timeout: 8000})` |
| `play.expect(q).toBeInTheDocument()` | `expect(el).toBeInTheDocument()` |
| `play.expect(play.queryBy...).toBeNull()`, `.not().toBeNull()` | `expect(queryBy...).toBeNull()`, `.not.toBeNull()` |
| `play.expect(play.getAllBy...).toHaveLength(n)` | `expect(getAllBy...).toHaveLength(n)` |
| `play.expect(q.textContent()).toBe(x)` | `expect(el.textContent).toBe(x)` |
| `play.expect(q.attribute(a)).toBe(x)` | `expect(el.getAttribute(a)).toBe(x)` |
| `play.expect(q.className()).toMatch("x")` | `expect(el.className).toMatch(/x/)` |
| `q.width()` | `el.getBoundingClientRect().width` |
| `play.expect(() -> computed).toBe(y)` | `expect(computed).toBe(y)` |
| `final Value<Integer> before = play.capture("before", spy.callCount())` | `const before = spy.mock.calls.length` (mid-play) |
| `play.expect("calls", () -> spy.getCallCount() - before.get()).toBe(1)` | `expect(spy.mock.calls.length).toBe(before + 1)` |
| `play.expect(q).toHaveValue(1.5)`, `toHaveValue("abc")` | `expect(input).toHaveValue(1.5)`, `('abc')` |
| `play.expect(q).toHaveStyle("fontWeight", "bold")` | `expect(el).toHaveStyle({fontWeight: 'bold'})` |
| `play.spy("onX").mockReturnValue(v)`, `.mockClear()` (a step) | `args.onX.mockReturnValue(v)`, `.mockClear()` |
| `toEqual(Map.of(...))`, `toMatchObject(Map.of(...))` | `toEqual({...})`, `toMatchObject({...})` |
| `ValueMatcher.objectContaining(Map.of(...))`, `ValueMatcher.stringContaining(x)` | `expect.objectContaining({...})`, `expect.stringContaining(x)` |
| `context.fn("onX")` in the story, `play.spy("onX")` in the play | `args.onX = fn()` |
| `play.expect(play.spy("onX")).toHaveBeenCalledWith(...)` | `expect(args.onX).toHaveBeenCalledWith(...)` |
| `spy.lastCall()` | `spy.mock.calls.at(-1)` |
| `spy.call((Object) new String[]{"a", "b"})` | `onPick(['a', 'b'])` (an array as one argument) |
| `play.click/dblClick/hover/unhover/type/clear(q, ...)` | `userEvent.click/dblClick/hover/unhover/type/clear` |
| `play.tab()`, `play.tab(true)` | `userEvent.tab()`, `userEvent.tab({shift: true})` |
| `play.keyboard("{Enter}")` | `userEvent.keyboard('{Enter}')` |
| `play.rightClick(q)` | `userEvent.pointer({keys: '[MouseRight]', target})` |
| `play.upload(q, "a.txt", "x", type)` | `userEvent.upload(input, new File(['x'], 'a.txt', {type}))` |
| `play.selectOptions(q, ...)` | `userEvent.selectOptions(el, ...)` |
| `play.fireEvent().contextMenu(q)` | `fireEvent.contextMenu(el)` |
| `play.fireEvent().mouseDown(q, EventInit.create().ctrlKey())` | `fireEvent.mouseDown(el, {ctrlKey: true})` |
| `play.fireEvent().mouseUp(play.body())` | `fireEvent.mouseUp(document)` |
| `play.fireEvent().keyDown(play.body(), EventInit.create().key("u"))` | `fireEvent.keyDown(document.body, {key: 'u'})` |
| `EventInit.create().atCentreOf(q)`, `.relativeTo(q, dx, dy)` | `{clientX: ..., clientY: ...}` |
| `play.fireEvent().change(q, value)` | `fireEvent.change(el, {target: {value}})` |
| `play.sleep(n)` | `await new Promise(r => setTimeout(r, n))` |

Notes:

* `find*` queries and `waitFor` retry for 1000ms by default, as Testing Library does.
* The play function only *builds* steps; they run after it returns. Every `play.*` call must be
  made while building: adding a step while the steps run (e.g. `play.click(...)` or
  `play.findBy...(...)` inside `play.run(...)` or a value's supplier) fails that step with a clear
  error. Use `play.capture(name, supplier)` for a value read mid-play; expected values (e.g. the
  argument of `toBe`) are fixed while building, so put the captured value in the supplier. In the
  same way, get a spy while building (`final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);`)
  and read `requests.getCalls()` in the supplier.
* A `findBy*`/`findAllBy*` call waits at the point it is called, like `await findBy...`; the query
  it returns finds the element again (without waiting) wherever it is used later.
* Accessible names (`getByRole(role, name)`) follow `dom-accessibility-api` exactly, as Testing
  Library does: `aria-labelledby`, then `aria-label`, then labels/`alt`/`<legend>`/SVG `<title>`,
  and the content only for roles that allow it (button, link, tab, option, menuitem, cell,
  heading, …, not dialog, group, listbox or textbox), ignoring hidden content and falling back
  to `title`.
* Roles follow Testing Library's table: e.g. `<section>` is a `region` only with a name, a
  password input has no role, `<output>` is `status`, `<meter>` is `meter`, a text input with an
  empty `list=""` has no role, and `<th scope="Row">` is a `rowheader` (the scope's case doesn't
  matter). Roles, names and accessibility are checked against Testing Library by the self-test
  (`test-runner/selftest`).
* User events follow user-event 14's "direct" API (`userEvent.click(el)` etc., where each call
  starts with a new pointer and keyboard), checked against it by the self-test. In particular:
  * `click`/`dblClick`/`type` move the pointer from the body onto the element (`pointerout` and
    `mouseout` on the body, then over, enter and move events on the element, entering it before
    its ancestors), so a second click fires them again; `play.rightClick(q)` doesn't move the
    pointer, so fires no over/enter/move events, and fires `contextmenu` and `auxclick`. `hover`
    moves from the body onto the element; `unhover` moves from the element to the body (out and
    leave events on the element and its ancestors).
  * A press sets the caret as user-event does: one click puts it at the end of a field's value
    (or of an element's text, in the document's selection), a double click selects the last word
    and a triple click the last line. A cancelled `pointerdown` or `mousedown` moves neither the
    caret nor the focus. Clicking a label focuses and clicks its control.
  * Tab goes as user-event's `getTabDestination`: positive `tabindex` first, then document order;
    the body is a stop (Tab from the last element, or Shift+Tab from the first, blurs to the
    body); only the checked (or focused) radio button of a group is a stop; elements in a disabled
    `<fieldset>` are skipped; a text field reached by Tab has all its text selected.
  * Keys: a `keypress` (and so typing) needs no Control or Alt (Meta doesn't stop it, so
    `{Meta>}a` types `a`); Control+A selects all (Meta+A doesn't); Enter clicks a button, link or
    button-like/colour/file input, or in a text input submits its form (clicking the default
    button, or firing `submit` for a form with a single input); Space clicks a button, check box,
    radio button etc. on its `keyup`, unless the `keydown` or `keyup` was cancelled; arrow keys
    walk a radio group; releasing a key that isn't held (e.g. `{/Shift}`) does nothing; `type`
    releases keys still held at the end. Key names and codes are user-event 14's US keyboard:
    names ignore case (`{enter}`), but user-event 13's other names (`{esc}`, `{del}`,
    `{selectall}`) are unknown keys, and punctuation such as `.` and `-` has the `code`
    `Unknown`.
  * Typing: a disabled field does nothing; a read only field only gets the key events; `clear` on
    a disabled or read only field fails; `maxlength` is honoured for text, email, password,
    search and url inputs and text areas but not `tel`; a number field only takes what a browser
    accepts and its `value` is the number typed so far (`1` after typing `1.`, `0.0015` after
    `1.5e-3`); a time field builds `HH:MM` from the digits (`0930` → `09:30`); typing into a
    content editable element inserts at the caret, keeping its markup. A field's value or
    selection set by the story's code while typing is followed, as user-event's interceptors do.
  * Unlike user-event, the pointer's coordinates are the element's centre (user-event uses 0, 0),
    and keyboard events have the legacy `keyCode`/`which` GWT reads (user-event leaves them 0).
* Errors thrown by the story's code (e.g. in an event handler or timer) are caught for the whole
  life of the story's renderings: while the steps run (or the story re-renders for Rerun/Rewind)
  they fail the running (or next) step; at any other time, e.g. after the play has completed,
  while it is paused by the debugger controls, after an args change, or for a story with no play
  function, they mark the run `ERRORED`, shown as an extra "Unhandled error in the story" entry in
  the Interactions addon (and as the failed step for the test runner). When the story renders
  again, the previous rendering's clean ups run first, then its widgets and popups are removed; a
  failure in any of these is logged with `console.error` and reported the same way.
* Register every spy the play checks with `context.fn(name)` while the story renders: checking a
  spy name the rendering didn't register fails (to catch typos), and calls made by an earlier
  rendering's left-over callbacks (timers, late replies) are ignored after a re-render. Once the
  story has rendered again, an old rendering's `context.fn(name)` returns a detached spy that
  records nothing, so a late callback can't create or call the new rendering's spy.
* `mockReturnValue`, `mockClear` and `logTo` on `play.spy(name)` called in the play body add a
  step (they run at that point of the play, and again on each rerun, against that rendering's
  spy); called inside `play.run(...)` they happen at once.
* `toBeDisabled`/`toBeEnabled` follow jest-dom, so `aria-disabled` doesn't count; check it with
  `toHaveAttribute("aria-disabled", "true")` (Stroom's icon buttons are disabled with
  `aria-disabled` and stay focusable). `toHaveAttribute(name, null)` checks presence only.
* Matchers follow jest-dom and Jest exactly: `toBe`/`toEqual` compare numbers with `Object.is`
  (so `0` isn't `-0`); `toHaveLength` fails for a `Map` (it has no `length`); `toHaveValue`
  compares the typed value, so a number field needs `toHaveValue(5)` and a text field
  `toHaveValue("5")`, and it fails for a check box or radio button even with `not()`;
  `toBeChecked` fails (so `not().toBeChecked()` passes) for an element that can't be checked;
  `toBeVisible` also honours the `hidden` attribute, `visibility: collapse` and a closed
  `<details>` (apart from its `<summary>`); `toHaveStyle` normalises the expected value as a
  style declaration does but not to a computed colour, so `color: "red"` doesn't match (use
  `"rgb(255, 0, 0)"`). `fireEvent` leaves `button` and `buttons` at 0, even for `contextMenu` and
  `mouseDown` (pass `EventInit.create().button(2)` for a right button).
* `toHaveTextContent("")` only passes for an element with no text (as jest-dom);
  `not().toHaveClass()` with no classes expects no classes.
* `TextMatch.regex` uses the browser's `RegExp`; in JVM unit tests only the `g`, `i` and `m` flags
  are supported.
* `null` stands for both `null` and `undefined`; a single `null` spy argument is written
  `(Object) null`. In `toEqual`, map entries with a null value are ignored (as `undefined`
  properties are in Jest) and sets compare regardless of order.
* `toMatchObject` and `objectContaining` work on `Map`s (including maps inside lists); use
  `toSatisfy` for Java objects, e.g. `toSatisfy("is a Long", v -> v instanceof Long)` on
  `spy.lastCall()`.
* Not supported: Testing Library's `hidden: true` option and other `*ByRole` filters (`checked`,
  `level`, `expanded`, …); use `play.querySelector(...)` or an expectation instead.

### Finding things in Stroom's markup

Query by role and name where Stroom provides them (buttons, links, inputs named by their labels or
titles, menus and menu items, tick boxes). Where it doesn't (see the accessibility entries in
`ISSUES.md`), use `StroomDom` rather than copying selectors:

* `StroomDom.button("OK")` for a Stroom `Button`'s name; `DIALOG` (a dialog has no
  `role="dialog"`) and `DIALOG_TITLE`;
* grid rows are `<tr>`s (`getByText(x).closest("tr")`, `GRID_ROW`), a selected row has
  `SELECTED_ROW`, sortable headers `SORTABLE_HEADER` (they have `role="button"`, so read their
  names from `.dataGridSortableHeaderNameHolder`);
* `ACTIONS_TITLE` ('Actions...'); `MENU_ITEM_TEXT`; `LINK_TAB_LABEL` (link tabs have no
  `role="tab"`); `QUICK_FILTER_PLACEHOLDER` (quick filters have no label); `SELECTION_BOX` (click
  its text box to open it); `COMMAND_LINK_OPEN`; `FILE_INPUT`.

Also:

* **Press a grid row's cell, not its `<tr>`.** A play's event is dispatched to the element it is
  given, and GWT's grids handle the events of their cells, so `play.dblClick(play.getByText(x))`
  opens a row where `play.dblClick(play.getByText(x).closest("tr"))` does nothing.
* **Command links** (`CommandLinkCell`: Jobs' Node/Schedule, the Imports grid) run on a mousedown on
  their 'open' icon, found inside the element titled with what it opens:
  `play.click(play.within(play.getByTitle("Edit schedule")).querySelector(StroomDom.COMMAND_LINK_OPEN))`.
  To keep a multi-selection, `play.fireEvent().mouseDown(...)` on the icon (the grid then leaves the
  selection alone).
* Grid tick boxes are `TickBoxCell` divs (`.tickBox`) with the `checkbox` role, not inputs, so
  `getAllByRole("checkbox")` finds them too; list boxes (`<select multiple>`) are driven with
  `play.selectOptions(...)`; context menus with `play.rightClick(cell)`.
* **Results table headers**: `MyDataGrid` only handles a header's mouse buttons after the mouse has
  moved over it (a deferred native preview handler), and a click moves the mouse in again from the
  body, so `QueryEditorStories.clickHeader(play, target)` fires a mousemove, waits 200ms, then
  mousedown/mouseup/click. A column's menu opens on a left click (not a context menu); the values
  filter on the header's `.column-valueFilterIcon`. Header labels are `.column-top .column-label`.
* **Form groups**: a `FormGroup`'s label names the one form control in it (an input, select or text
  area, even inside a panel or composite such as a tick box or password box), so find it with
  `getByLabelText(label)`. A group with several controls is `role="group"`, named by its label
  (`getByRole("group", label)`), and its controls are named by `controlNames` in the order shown
  (e.g. `getByRole("textbox", "Amount")`). A group's plain help text is also in the page, hidden,
  as its control's description (`aria-describedby`, with `aria-keyshortcuts="F1"`), so a help
  popup's text appears twice: find it in the popup with
  `screen.findByText(text, ".help-button-tooltip *")`, and check the control with
  `toHaveAccessibleDescription(...)`. Clicking a help button leaves the focus where it was.
  `screenReaderText` (if set) is the description instead.
  `required="true"` marks the control `aria-required`.
* **Text boxes report their change** (and so make a document dirty) when they lose the focus: type,
  then `play.tab()`.
* **Read-only text in a text area** (e.g. `DependenciesInfoViewImpl`) is checked with
  `querySelector("textarea...").value()`, not `getByText`.
* **Ace editors**: click `.ace_content` then `play.keyboard(text)`, and wait for the text in
  `.ace_content` before pressing OK.
* **Menus**: the explorer's menu opens on a secondary mousedown (`play.rightClick`), a tab's on a
  secondary mouseup (`fireEvent().mouseUp(tab, button(2))`); main menu groups render their items
  only while hovered. Disabled items have `menuItem-disabled`, shortcut hints are
  `.menuItem-shortcut`. Key sequences (`g` `u`, Shift Shift) complete on key release, so plays use
  `play.keyboard("gu")`.
* **App events a screen hands on** (create document, open document, annotations, stepping, show
  data, ask AI) are checked with spies on the event bus. The create dialog is stood in for by a
  probe panel whose 'run-seed' button runs the event's consumer (`QueryEditorStories`).
* End with the `ALERT_SPY`/`UNHANDLED_REQUEST_SPY` checks (a shared `expectNoProblems(play)` helper
  per class), and check the requests the screen should have made.

## Pitfalls

* An uncaught exception from Stroom's code (e.g. a `NullPointerException` while building a menu) is
  reported as "Uncaught error in the story", often with an obfuscated name (`reading 'PNc'`). To
  read it, temporarily add `'-style', 'PRETTY'` to `workbenchDraftCompile`'s args (don't keep it),
  or read `window.__workbenchPlay.error`. When it is a Stroom bug, record it in `ISSUES.md`.
* In compiled GWT, a `String` field that was never set can be `undefined` rather than `null`, and
  `Objects.equals(null, undefined)` is false (it compares two `String`s strictly), while
  `x == null` is true for both. Check with `== null` when a value may come from a builder that left
  it unset.
* Don't create a presenter that reads the user's preferences before start-up (e.g.
  `AskStroomAiPresenter`, whose constructor reads the AI config): create it in `afterStartUp`, as
  Stroom does.
* A leftover workbench server (e.g. after a Gradle daemon is killed for memory) keeps the test port;
  stop it by its PID (`ss -ltnp | grep <port>`) before the next run.
* While other work leaves the workbench's code broken, a private draft compile of only your own
  stories (an `AllStories` override ahead of the sources on the GWT classpath, its own war and
  work dirs and its own `WorkbenchServer`) keeps another ginjector's errors from stopping you. Keep
  the GWT unit cache small if the scratch space has a quota.

## Checking

* `workbenchTest -PworkbenchTestFilter=App/Main/JobsScreen` runs a title's stories (see
  [test-runner/README.md](test-runner/README.md) for the other options and the leak check).
  While iterating, a draft compile (`workbenchDraftCompile`, ~25s) and
  `node run.mjs App/Main/JobsScreen --url http://127.0.0.1:<port>` in `test-runner` against your
  own `workbenchServe` is quickest.
* To see the DOM before writing a play, register the story with a minimal play (the preview runs
  the play when it loads), then read the page (`iframe.html?id=<id>&viewMode=story`) with
  Playwright.
* Rerun a screen story from the debugger controls and check the console for dropped requests from
  an earlier rendering (see [What happens on a re-render](#what-happens-on-a-re-render)).
