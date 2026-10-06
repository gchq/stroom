# Porting React stories to the Stroom GWT Workbench

The aim is one Java story for each story in the React project (`stroom-ui-react`), with the same
id, so the two can be opened at the same URL, and with its play function ported so the workbench's
test runner checks the same behaviour.

See [test-runner/README.md](test-runner/README.md) for running the stories and the coverage
report.

## Rules

* **Same title and export name.** The story id comes from the component title and the story's
  export name, using Storybook's rules, e.g. `App/Main/RecentItemsDialog` + `Recent` →
  `app-main-recentitemsdialog--recent`. `TestReactStoryCoverage` fails for an id that isn't in
  `src/test/resources/react-stories.json`.
* **Register under the title's comment** in `AppStories`, `ScreensStories` or `WidgetsStories`,
  so that parallel ports rarely touch the same lines.
* **Assert what GWT does.** Where the GWT widget or screen behaves differently from the React
  port, the play asserts the GWT behaviour, and the difference is noted in a comment starting
  `// Differs from React:` so the differences can be found and reviewed later.
* **Not applicable or blocked** stories are recorded in `src/test/resources/react-story-status.json`
  with a reason, so that coverage accounts for every React story.
* **Every React check has a GWT check, or a `// Differs from React:` comment saying why not.**
  Don't silently drop an assertion, and don't replace one with a check that can't fail (e.g.
  `queryByText(x).toBeNull()` for text the screen never shows); check what the GWT screen shows
  instead (e.g. that its result list has no rows).
* **Match the React story's layout** (`parameters.layout` → `StoryLayout`), or note why not.
* **Screen stories: no alerts, unmatched requests or failing fixtures unless the React story
  expects them.** End the play with
  `play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled()` (and the same for
  `ScreenHarness.UNHANDLED_REQUEST_SPY`), unless an error is what the story shows.
* **Story code is not jakarta→javax transformed.** The stories (and the workbench framework) are
  given to the GWT compiler as they are, while Stroom's code is transformed. A story that imports
  `jakarta.*` (e.g. `jakarta.inject.Inject`) compiles for the JVM and the tests but fails
  `workbenchDraftCompile`; use `com.google.inject.*` (or `javax.*`) instead.

## Widget stories

A widget story creates the widget, applies the story's args and returns it. See
`client/widgets/buttons/ButtonStories.java`. Args and controls are declared with `ArgType`, and React's
`fn()` args become spies, `context.fn("onClick")`, that play functions check with
`play.spy("onClick")`.

## Screen stories

Screens (presenters and views) run for real against fake REST replies. See
`client/app/main/RecentItemsDialogStories.java` (a dialog opened by an event, with its own
fixtures), `AboutDialogStories.java` (start-up fixtures only, customised) and
`WelcomeScreenStories.java` (a tab's content, defaults only).

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
  singleton of the harness's injector. To add another
  singleton, add a getter to `ScreenGinjector` (GIN creates anything with an `@Inject`
  constructor; bind fakes in `ScreenGinModule`). Presenters and views are not added there, as each
  would compile its whole graph into every story;
* spies: `REQUEST_SPY` (`"METHOD /path?query body"`), `UNHANDLED_REQUEST_SPY`, `ALERT_SPY`
  (`"ERROR: message"`), `CONFIRM_SPY`, `DOWNLOAD_SPY` and `UPLOAD_SPY` (see "Uploads" below). A story's own spies, e.g. for React's
  `onOpenDoc: fn()`, are made with `harness.fn(name)` **as the story renders** (so a play can
  check they weren't called) and called with `harness.spy(name, detail)`.

```java
final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
        .user(ALICE)                                         // security context + session info
        .appPermissions(AppPermission.MANAGE_USERS_PERMISSION) // ADMINISTRATOR by default
        .uiConfig("{\"welcomeHtml\": \"<h1>Hi</h1>\"}")        // the uiConfig fixture
        .startup(startup -> startup.nodeName("node2a"))      // other start-up fixtures
        .realAlerts()                                        // show alerts as Stroom does
        .build();
harness.getSecurityContext().setDocumentPermission(DocumentPermission.VIEW);
final MyPresenter presenter = new MyPresenter(harness.getEventBus(), view, null,
        harness.getRestFactory(), harness.getUiConfigCache());
```

Get the screen's presenter from **`AppScreenGinjector`** (`client/app/gin`), which extends
`ScreenGinjector` with Stroom's presenter bindings, so the presenter and its graph of child
presenters and views are created (and bound) as in Stroom; give it to the harness with
`.injector(...)`. Small presenters with no graph may still be created with `new` (pass `null` for a
GWTP proxy), as `RecentItemsDialogStories` does. See "Conventions from the screen pilot" below.

```java
final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES).injector(injector).build();
harness.afterStartUp(() -> harness.addContent(injector.getJobPresenter()));
return harness.asWidget();
```

A screen opened by an event is shown by registering the presenter as the event's handler and
firing the event, as GWTP's proxy would; a tab's content (not a popup) is added with
`harness.addContent(presenter)`, which fills the canvas (the full page height, as Stroom's content
pane does; React wraps such screens in a `100vh` box) and unbinds the presenter on re-render.

Also on the harness, for what Stroom's app does around a screen:

* **`afterStartUp(action)`** runs the action once the harness has loaded the UI config into its
  `UiConfigCache` and the user's preferences into `CurrentPreferences`, as Stroom does at login
  before it shows any screen. Create the screen in it when the screen (or anything it creates)
  reads the cached UI config at once (e.g. a `ClassificationLabel`'s colours) or the user's
  preferences (`EditorPresenter` reads the editor preferences, lists format dates with them).
  `UiConfigCache.get(consumer)` fetches the config when nothing is cached (it once also called the
  consumer with null at once, now fixed), so a screen that only uses that may work without it (most
  of the Users screen's stories open it at once), but each call made before the config arrives is
  answered, which a screen may not expect (`UserListPresenter` then sets up its columns twice). If
  in doubt, use it: it costs two fixture replies and is the order Stroom opens screens in.
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
  shortcuts (e.g. `ctrl+s`), as there is no app shell.

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

```java
StoryDocumentPlugins.register(harness, injector.getDocumentPluginEventManager(),
        injector.getPipelinePlugin(), injector.getXsltPlugin()).showOpenedTabs();
harness.afterStartUp(() -> OpenDocumentEvent.fire(harness.getHasHandlers(), docRef, true));
```

A screen may need more Stroom GWT modules inherited in `StroomWorkbench.gwt.xml`: RestyGWT
generates code for every method of a resource interface used, so the types of all of them must be
available.

### Fixtures

`RestFixtures` answers requests by method and path, relative to the REST root (`/api` is not part
of the path). The first matching route replies; a story's routes come before the start-up
fixtures, so they override them.

```java
RestFixtures.builder()
        .get("/sessionInfo/v1", RestReply.json(SESSION_INFO))
        .post("/explorer/v2/find", request -> RestReply.json(find(request.getBody())))
        // A sequence, e.g. for polling: the last reply repeats, as in the corpus
        .post("/search/v1", RestReply.json(PENDING), RestReply.json(COMPLETE))
        // Matching the query (exactly, in any order) or the body (JSON value, text or predicate)
        .route(RequestMatcher.get("/node/v1/info").withQuery("node=node1a"), RestReply.json(NODE))
        .route(RequestMatcher.post("/explorer/v2/find").withJsonBody(QUERY), RestReply.json(EVENTS))
        // Matching part of the body (as Jest's toMatchObject: objects need only the members given)
        .route(RequestMatcher.post("/jobNode/v1/find").withJsonBodyContaining(
                "{\"jobName\": {\"string\": \"Data Retention\"}}"), RestReply.json(DATA_RETENTION_NODES))
        .route(RequestMatcher.any("/permission/*"), RestReply.json("true"))
        .build();
```

* The query string and body are ignored unless a `RequestMatcher` asks for them. `withQuery`
  compares the parameters as sent (URL encoded), in any order, ignoring empty ones; a query with
  no parameters (`withQuery("")`) is the same as `withoutQuery()`, matching `/a` and `/a?`.
  `withJsonBody` compares JSON values with a strict (RFC 8259) parser, so an invalid body never
  matches, and compares numbers exactly (`1` equals `1.0`, but long ids above 2^53 that a
  `double` can't tell apart are different).
* `withJsonBodyContaining(json)` matches a JSON body containing the JSON given, as Jest's
  `toMatchObject` compares (`JsonValues.contains`): an object needs only the members given, each
  containing the expected value; an array must have the same length, item by item. Use it to route
  by one criterion of a request (a job name, a name filter) whatever else the client sends.
* **Checking requests**: `RequestMatcher.toSpyMatcher()` turns a matcher into a `ValueMatcher` of
  the request spy's calls, so a play checks the method, path, query and body of a request the
  screen made (React's recorder checks) without depending on the JSON's spacing or member order:
  ```java
  play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
          RequestMatcher.put("/jobNode/v1/11/schedule")
                  .withJsonBodyContaining("{\"type\": \"CRON\", \"expression\": \"0 /5 * * * ?\"}")
                  .toSpyMatcher()));
  ```
  `not().toHaveBeenCalledWith(RequestMatcher.put(path).toSpyMatcher())` checks a request was not
  made. `RecordedRequest.parse` reads a spy call back into a request.
* `build()` refuses (`IllegalStateException`) a route that an earlier route makes unreachable,
  e.g. two routes for the same method, path and query without a body rule to tell them apart
  (use a sequence of replies instead), or the same corpus key recorded twice. Routes added with
  `addAll(...)`, such as the start-up fixtures, aren't checked, as a story's route may override
  them.
  `RequestMatcher.ANY_METHOD` (`*` as a method) and a path ending with `RequestMatcher.PATH_WILDCARD`
  (`/explorer/*`) are separate things.
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
* A fixture that throws or returns null replies `500`, and fails the story (see below).

#### Start-up fixtures

The harness adds `StartupFixtures` after the story's routes (`fixtures.followedBy(startup)`, which
keeps the story fixtures' strictness): the session info
(`GET /sessionInfo/v1`), the extended UI config (`GET /config/v1/noauth/fetchExtendedUiConfig`), the
user preferences (`GET /preferences/v1`), the app permissions (`GET /permission/app/v1`),
document permission checks (`POST /permission/doc/v1/checkDocumentPermission`) and Stroom's
document types (`GET /explorer/v2/fetchDocumentTypes`, which Stroom caches app-wide; the 27 types
recorded in the corpus). They are the
equivalent of React's `appApiFixture` (`fetchSessionInfo`, `fetchUiConfig`,
`fetchUserPreferences`, `fetchEffectiveAppPermissions`). The defaults, trimmed from the gwt-suite
corpus, are the `admin` user (`ADMINISTRATOR`) on node `node1a`, build `SNAPSHOT`, the usual
"About Stroom" `welcomeHtml`/`aboutHtml` and help URLs, and default preferences in UTC. Customise
them with the harness builder (`user`, `appPermissions`, `uiConfig`, `startup(...)`), or leave
them out with `withoutStartupFixtures()`.

#### Security context

The injector binds `ClientSecurityContext` to `StorySecurityContext` (in place of Stroom's
`CurrentUser`): the harness builder's `user`/`appPermissions` set both it and the start-up
fixtures, and `harness.getSecurityContext()` sets document permissions, either a default for all
documents or per document, e.g. `setDocumentPermission(DocumentPermission.VIEW)` for a read-only
screen. `ADMINISTRATOR` implies every app permission, as in Stroom. Document permission checks are
answered immediately, without a request (Stroom's `CurrentUser` asks the server). It is not logged
in by default, as `UiConfigCache` refreshes the UI config every minute while the user is logged in.

#### Replies recorded in the gwt-suite corpus

The corpus (`stroom-ui-react/gwt-suite/corpus/<name>/api/manifest.json`) can't simply be pasted:

* its keys are `METHOD /api/path?query #sha1-of-body`, e.g.
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
bodies can be printed with (from `gwt-suite/corpus/default`):

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
repeating timers or window handlers) must be registered with the harness as above.

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
  top of the story and reports an uncaught exception, which fails the play. For a story whose
  React original expects a request to fail, use `RestFixtures.builder()...lenient()`, and check
  the `404` alert with `ALERT_SPY`.
* Errors the screen reports with `AlertEvent` (e.g. Stroom's `DefaultErrorHandler`) are recorded by
  `ALERT_SPY` as `LEVEL: message`, and confirmations (`ConfirmEvent`) by `CONFIRM_SPY`. They are not
  shown, and a confirmation is never answered, unless the harness is built with `realAlerts()`,
  which shows Stroom's real alert dialog (`AlertPlugin`/`CommonAlertPresenter`), e.g. for stories
  ported from ones wrapped in `ApiErrorAlertHost`, or that click OK in a confirmation.
* `RestReply.delayed(millis)` shows a screen loading; a pending reply is cancelled on re-render.

### Downloads, uploads and streaming

* **Downloads**: the injector binds Stroom's `LocationManager` to `StoryLocationManager`, which
  records the URL that `ExportFileCompleteUtil` would navigate to in `DOWNLOAD_SPY` (relative to
  the host page, e.g. `resourcestore/my-notes.md?uuid=k1`) instead of navigating away. Each
  harness has its own; give the screen `harness.getInjector().getLocationManager()`. Check with
  `play.expect(play.spy(ScreenHarness.DOWNLOAD_SPY)).toHaveBeenCalledWith(ValueMatcher.stringContaining("resourcestore/"))`.
* **Uploads** are answered by the fixtures' upload replies: see "Uploads" below.
* **WebSocket/EventSource**: Stroom's GWT client uses neither (it polls with REST, which fixture
  sequences cover). React stories that stream (e.g. AI chat) have no GWT equivalent of the
  streaming; record them as not applicable, or port what GWT does instead.

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
  at most 64 KiB). React's `uploaded` recorders become
  `play.expect(play.spy(ScreenHarness.UPLOAD_SPY)).toHaveBeenCalledWith("importfile.rpc", "a.txt", "x")`
  (`ValueMatcher.anything()` for an argument that doesn't matter).
* **Strict**: an upload that no upload route matches fails the story, as an unmatched request does
  (it is also recorded by `UNHANDLED_REQUEST_SPY` and fails as Stroom reports a `404`); with
  `lenient()` it only fails the upload.
* Choose the file with `play.upload(input, name, content, mimeType)` on the hidden file input
  (`StroomDom.FILE_INPUT`, inside the dialog): it sets the input's files with a `DataTransfer` and
  fires `input` and `change`, so `CustomFileUpload` shows the name, whether or not the input is
  visible. Then press the dialog's OK, which submits it.
* Browsers give a file input's value as `C:\fakepath\<name>`. `CustomFileUpload.getFilename()`
  returns just the name (`FileUploadUtil.getFileName`); `DataUploadPresenter` once sent the fake
  path as the `UploadDataRequest`'s `fileName` (`DataUploadDialog`'s `Upload` is its regression test).
* `CustomFileUpload`'s transport is static, so with several harnesses in one rendering the last
  one built answers every upload. Widget stories (no harness) keep Stroom's default transport, so
  they must not submit a `CustomFileUpload`.

## Play functions: Testing Library → Java

| React / Testing Library | Java |
|---|---|
| `within(canvasElement)` | `play` |
| `within(document.body)`, `screen` | `play.screen()` |
| `within(el)` | `play.within(q)` |
| `'Save'` | `"Save"` |
| `/Save/` | `TextMatch.containing("Save")` |
| `/save/i`, `{exact: false}` | `TextMatch.containingIgnoreCase("save")` |
| `/^ok$/i` | `TextMatch.exactIgnoreCase("ok")` |
| `/^Locked/` | `TextMatch.startingWith("Locked")` |
| `/help$/` | `TextMatch.endingWith("help")` |
| any other regex | `TextMatch.regex("close\|ok", "i")` |
| `getByRole('button', {name: /^OK$/})` | `getByRole("button", TextMatch.exact("OK"))` |
| `getByText('Name', {selector: 'label'})` | `getByText("Name", "label")` (or a `TextMatch` and `"label"`) |
| `getAllByX(...)[i]` | `getAllByX(...).nth(i)` |
| `getAllByX(...).length` | `.count()` |
| `.map(e => e.textContent)` | `.textContents()` |
| `el.closest(sel)` | `q.closest(sel)` |
| `canvasElement.querySelector(sel)` | `play.querySelector(sel)` (the first match, never ambiguous) |
| `document.querySelector(sel)` | `play.screen().querySelector(sel)` |
| `el.children` | `play.within(q).querySelectorAll(":scope > *")` |
| `await findByText(x)`, `const el = await findByText(x)` | `play.findByText(x)`, `final Query el = play.findByText(x)` |
| `findByText(x, {}, {timeout: 5000})` | `play.waitFor(5000, () -> play.expect(play.getByText(x)).toBeInTheDocument())` |
| `waitFor(fn, {timeout: 8000})` | `play.waitFor(8000, () -> ...)` |
| `expect(el).toBeInTheDocument()` | `play.expect(q).toBeInTheDocument()` |
| `expect(queryBy...).toBeNull()` | `play.expect(play.queryBy...).toBeNull()` |
| `.not.toBeNull()`, `.toBeTruthy()` on an element | `.not().toBeNull()` |
| `expect(getAllBy...).toHaveLength(n)` | `play.expect(play.getAllBy...).toHaveLength(n)` |
| `expect(el.textContent).toBe(x)` | `play.expect(q.textContent()).toBe(x)` |
| `expect(el.getAttribute(a)).toBe(x)` | `play.expect(q.attribute(a)).toBe(x)` |
| `expect(el.className).toMatch(/x/)` | `play.expect(q.className()).toMatch("x")` |
| `el.getBoundingClientRect().width` | `q.width()` |
| `expect(computed).toBe(y)` | `play.expect(() -> computed).toBe(y)` |
| `const before = spy.mock.calls.length` (mid-play) | `final Value<Integer> before = play.capture("before", spy.callCount())` |
| `expect(spy.mock.calls.length).toBe(before + 1)` | `play.expect("calls", () -> spy.getCallCount() - before.get()).toBe(1)` |
| `expect(input).toHaveValue(1.5)` (number field) | `play.expect(q).toHaveValue(1.5)` |
| `expect(input).toHaveValue('abc')` (text field) | `play.expect(q).toHaveValue("abc")` |
| `expect(el).toHaveStyle({fontWeight: 'bold'})` | `play.expect(q).toHaveStyle("fontWeight", "bold")` |
| `args.onX.mockReturnValue(v)`, `.mockClear()` (in the play) | `play.spy("onX").mockReturnValue(v)`, `.mockClear()` (a step) |
| `toEqual({...})` / `toMatchObject({...})` | `toEqual(Map.of(...))` / `toMatchObject(Map.of(...))` |
| `expect.objectContaining({...})` | `ValueMatcher.objectContaining(Map.of(...))` |
| `expect.stringContaining(x)` | `ValueMatcher.stringContaining(x)` |
| `args.onX = fn()` | `context.fn("onX")` in the story, `play.spy("onX")` in the play |
| `expect(args.onX).toHaveBeenCalledWith(...)` | `play.expect(play.spy("onX")).toHaveBeenCalledWith(...)` |
| `spy.mock.calls.at(-1)` | `spy.lastCall()` |
| `onPick(['a', 'b'])` (an array as one argument) | `spy.call((Object) new String[]{"a", "b"})` |
| `userEvent.click/dblClick/hover/unhover/type/clear` | `play.click/dblClick/hover/unhover/type/clear(q, ...)` |
| `userEvent.tab()`, `userEvent.tab({shift: true})` | `play.tab()`, `play.tab(true)` |
| `userEvent.keyboard('{Enter}')` | `play.keyboard("{Enter}")` |
| `userEvent.pointer({keys: '[MouseRight]', target})` | `play.rightClick(q)` |
| `userEvent.upload(input, new File(['x'], 'a.txt', {type}))` | `play.upload(q, "a.txt", "x", type)` |
| `fireEvent.contextMenu(el)` | `play.fireEvent().contextMenu(q)` |
| `fireEvent.mouseDown(el, {ctrlKey: true})` | `play.fireEvent().mouseDown(q, EventInit.create().ctrlKey())` |
| `fireEvent.mouseUp(document)` | `play.fireEvent().mouseUp(play.body())` |
| `fireEvent.keyDown(document.body, {key: 'u'})` | `play.fireEvent().keyDown(play.body(), EventInit.create().key("u"))` |
| `fireEvent.pointerDown(el, {clientX: ..., clientY: ...})` | `EventInit.create().atCentreOf(q)` or `.relativeTo(q, dx, dy)` |
| `fireEvent.change(el, {target: {value}})` | `play.fireEvent().change(q, value)` |
| `await new Promise(r => setTimeout(r, n))` | `play.sleep(n)` |

Notes:

* `find*` queries and `waitFor` retry for 1000ms by default, as Testing Library does.
* The play function only *builds* steps; they run after it returns. Every `play.*` call must be
  made while building: adding a step while the steps run (e.g. `play.click(...)` or
  `play.findBy...(...)` inside `play.run(...)` or a value's supplier) fails that step with a clear
  error. Use `play.capture(name, supplier)` for a value read mid-play; expected values (e.g. the
  argument of `toBe`) are fixed while building, so put the captured value in the supplier.
* A `findBy*`/`findAllBy*` call waits at the point it is called, like `await findBy...`; the query
  it returns finds the element again (without waiting) wherever it is used later. A bare
  `screen.findByText("Saved");` is `await screen.findByText('Saved')`.
* Accessible names (`getByRole(role, name)`) follow `dom-accessibility-api` exactly, as Testing
  Library does: `aria-labelledby`, then `aria-label`, then labels/`alt`/`<legend>`/SVG `<title>`,
  and the content only for roles that allow it (button, link, tab, option, menuitem, cell,
  heading, …, not dialog, group, listbox or textbox), ignoring hidden content and falling back
  to `title`. A Stroom `Button`'s name is its text twice (e.g. `Save Save`), as its background
  text isn't hidden, so query it by role alone or with `TextMatch.startingWith`.
* Roles follow Testing Library's table: e.g. `<section>` is a `region` only with a name, a
  password input has no role, `<output>` is `status`, `<meter>` is `meter`, a text input with an
  empty `list=""` has no role, and `<th scope="Row">` is a `rowheader` (the scope's case doesn't
  matter). Roles, names and accessibility are checked against Testing Library by the self-test.
* User events are ported from user-event 14 as React Storybook plays call it (`userEvent.click(el)`
  etc., its "direct" API, where each call starts with a new pointer and keyboard), and checked
  against it by the self-test (`test-runner/selftest`). In particular:
  * `click`/`dblClick`/`type` move the pointer from the body onto the element (`pointerout` and
    `mouseout` on the body, then over, enter and move events on the element, entering it before
    its ancestors), so a second click fires them again; `play.rightClick(q)` (`pointer({keys:
    '[MouseRight]', target})`) doesn't move the pointer, so fires no over/enter/move events, and
    fires `contextmenu` and `auxclick`. `hover` moves from the body onto the element; `unhover`
    moves from the element to the body (out and leave events on the element and its ancestors).
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
    `Unknown`, as in React.
  * Typing: a disabled field does nothing; a read only field only gets the key events; `clear` on
    a disabled or read only field fails; `maxlength` is honoured for text, email, password,
    search and url inputs and text areas but not `tel` (user-event lists `telephone`); a number
    field only takes what a browser accepts and, as user-event sets it, its `value` is the number
    typed so far (`1` after typing `1.`, `0.0015` after `1.5e-3`); a time field builds `HH:MM`
    from the digits (`0930` → `09:30`); typing into a content editable element inserts at the
    caret, keeping its markup. A field's value or selection set by the story's code while typing
    is followed, as user-event's interceptors do.
  * The pointer's coordinates are the element's centre (user-event uses 0, 0), and keyboard
    events have the legacy `keyCode`/`which` GWT reads (user-event leaves them 0).
* Errors thrown by the story's code (e.g. in an event handler or timer) are caught for the whole
  life of the story's renderings, as an unhandled error fails a React play: while the steps run
  (or the story re-renders for Rerun/Rewind) they fail the running (or next) step; at any other
  time, e.g. after the play has completed, while it is paused by the debugger controls, after
  an args change, or for a story with no play function, they mark the run `ERRORED`, shown as an
  extra "Unhandled error in the story" entry in the Interactions addon (and as the failed step
  for the test runner). When the story renders again, the previous rendering's clean ups run
  first, then its widgets and popups are removed; a failure in any of these is logged with
  `console.error` and reported the same way.
* Register every spy the play checks with `context.fn(name)` while the story renders: checking a
  spy name the rendering didn't register fails (to catch typos), and calls made by an earlier
  rendering's left-over callbacks (timers, late replies) are ignored after a re-render. Once the
  story has rendered again, an old rendering's `context.fn(name)` returns a detached spy that
  records nothing, so a late callback can't create or call the new rendering's spy.
* `mockReturnValue`, `mockClear` and `logTo` on `play.spy(name)` called in the play body add a
  step (they run at that point of the play, and again on each rerun, against that rendering's
  spy); called inside `play.run(...)` they happen at once.
* `toBeDisabled`/`toBeEnabled` follow jest-dom, so `aria-disabled` doesn't count; check it with
  `toHaveAttribute("aria-disabled", "true")`. `toHaveAttribute(name, null)` checks presence only.
* Matchers follow jest-dom and Jest exactly: `toBe`/`toEqual` compare numbers with `Object.is`
  (so `0` isn't `-0`); `toHaveLength` fails for a `Map` (it has no `length`); `toHaveValue`
  compares the typed value, so a number field needs `toHaveValue(5)` and a text field
  `toHaveValue("5")`, and it fails for a check box or radio button even with `not()`;
  `toBeChecked` fails (so `not().toBeChecked()` passes) for an element that can't be checked;
  `toBeVisible` also honours the `hidden` attribute, `visibility: collapse` and a closed
  `<details>` (apart from its `<summary>`); `toHaveStyle` normalises the expected value as a
  style declaration does but not to a computed colour, so, as in React, `color: "red"` doesn't
  match (use `"rgb(255, 0, 0)"`). `fireEvent` leaves `button` and `buttons` at 0, even for
  `contextMenu` and `mouseDown` (pass `EventInit.create().button(2)` where the React play does).
* `toHaveTextContent("")` only passes for an element with no text (as jest-dom);
  `not().toHaveClass()` with no classes expects no classes.
* `TextMatch.regex` uses the browser's `RegExp`; in JVM unit tests only the `g`, `i` and `m` flags
  are supported.
* `null` stands for both `null` and `undefined`; a single `null` spy argument is written
  `(Object) null`. In `toEqual`, map entries with a null value are ignored (as `undefined`
  properties are in Jest) and sets compare regardless of order.
* `toMatchObject` and `objectContaining` work on `Map`s (including maps inside lists); use
  `toSatisfy` for Java objects.
* Not supported: Testing Library's `hidden: true` option and other `*ByRole` filters (`checked`,
  `level`, `expanded`, …) — use `play.querySelector(...)` or an expectation instead.

## Conventions from the pilot (`Widgets/Buttons`, `Widgets/Inputs`)

* **Packages.** One sub-package of `client.widgets` per React group, lower case without spaces
  or punctuation: `Widgets/Buttons/*` → `widgets.buttons`, `Widgets/Date & Time/*` →
  `widgets.dateandtime`. One `XxxStories` class per React title, named after its last part
  (`Widgets/Inputs/ValueSpinner` → `widgets.inputs.ValueSpinnerStories`).
* **Shared helpers.** Pure-Java arg conversions shared by all widget stories are in
  `widgets/StoryArgs` (`toSvgImage`, `getBoolean(args, name, default)` for React props that
  default to true, `getLong`), unit tested by `TestStoryArgs`. Widgets shared within a group go
  in a package-private class in the group's package (e.g. `widgets/inputs/InputWidgets`:
  `textBox(...)`, `maxWidth(...)`, the `ON_CHANGE` spy name). Don't add to `StoryPanels` for one
  group's needs.
* **Use the real Stroom widget, presenter or view**, not a look-alike: e.g. `StepControlPresenter`
  with `StepControlViewImpl`, `QuickFilterPageViewImpl`, `SingleLineEditorPresenter` (a real Ace
  editor). Create views with `new XxxViewImpl(GWT.create(XxxViewImpl.Binder.class))`, presenters
  with `new` and a `SimpleEventBus` (use the `ScreenHarness` only for screens that make REST
  calls). Widgets with a private constructor that Stroom only creates in UiBinder (e.g.
  `CustomFileUpload`) are created with `GWT.create(Xxx.class)`. Implement small interfaces (e.g.
  `GlobalKeyHandler`) with a private no-op nested class.
* **No GWT widget, but a GWT pattern.** Where the React component ports markup that Stroom's
  views build themselves (e.g. `PasswordInput` = `ChangePasswordViewImpl`'s password box and
  show/hide button), build the same widgets the same way in the story, copying the view's code,
  name the source view in the class comment, and record it as `intentional` in
  `REACT-DIFFERENCES.md`. Only use `n/a` when there is nothing in Stroom to show.
* **Args.** Port `Default`-style stories that only set `args` as `fromArgs(context)` with an
  `ArgType` for each React prop the GWT widget supports (`.args(...)` for the meta's args,
  `.withArgs(...)` for the story's). A React prop with no GWT equivalent is left out, with a `//`
  comment by the arg types saying so.
* **Callbacks.** Every React callback prop (`onClick`, `onChange`, `onStep`, ...) becomes
  `context.fn("<prop name>")`, called from the GWT widget's handler with the value GWT gives
  (e.g. a `Long` from `ValueSpinner`), whether or not a play checks it, so the Actions addon shows
  it. Use `context.fn` rather than `context.action`.
* **React state echoes** (`Clicked: {count}`, `Value: {v}`) are a `StoryPanels.note(...)` or
  `Label` updated by the widget's own events, with the React inline styles copied.
* **Story-only adapters.** Where GWT has no callback and Stroom reads the value later (e.g.
  `LineColInput.getLocation()`, `CustomFileUpload.getFilename()`), add the handler the React port
  implies (blur/Enter, the change event) in the story, with a `// Differs from React:` comment.
* **Plays.** Query GWT's actual roles and compare values as GWT holds them (e.g. a `TextBox`
  instead of a number input: `getByRole("textbox")` and `toHaveValue("10")`). Where React uses
  a port-only class name, select by the GWT view's own classes or structure (a constant with a
  `// Differs from React:` comment), and never by the class you then assert (that check couldn't
  fail). `typeof x === 'number'` becomes `toSatisfy("is a Long", v -> v instanceof Long)` on
  `spy.lastCall()`.
* **Module inherits.** Widgets often need more than their own module: `FormGroup` needs
  `stroom.widget.help.Help` and `stroom.widget.tooltip.Tooltip`; stepping needs
  `stroom.pipeline.Pipeline` (for `stroom.pipeline.shared`) as well as `PipelineStepping`. Copy
  the inherit from `stroom-app-gwt/.../App.gwt.xml` when the draft compile says
  "No source code is available for type ...".
* **Registration runs on the JVM** (`TestAllStories`, `TestReactStoryCoverage`), so a stories
  class must not touch GWT at class-load time: no `static final` fields holding `GWT.create(...)`
  results or widgets; create them in `render` (a lazily created static is fine).
* **Differences.** Each `// Differs from React:` gets a row in `REACT-DIFFERENCES.md` (story id
  without `widgets-`, `--*` for all of a component's stories), with one of the kinds listed there.
* **Checking.** `workbenchTest -PworkbenchTestFilter=Widgets/<Group>` must pass, and
  `workbenchCoverage -PworkbenchCoverageList=Widgets/<Group>` must show 100%. Compare a few
  stories by screenshot with React (`iframe.html?id=<id>&viewMode=story` on both).

## Conventions from the screen pilot (`App/Main/*`, `App/Dictionary/DictionaryEditor`)

The pilot ported 34 stories of 9 components, one or more of each kind of screen: simple dialogs
(`CredentialPickerDialog`, `DocInfoDialog`), chained requests (`EditTagsDialog`), list screens with
toolbars and permissions (`JobsScreen`, `NodesScreen`, `UsersScreen`), polling
(`ServerTasksScreen`, `UserTaskManagerDialog`) and a document editor (`DictionaryEditor`). Copy the
one closest to your screen.

### Packages and registration

* One `XxxStories` class per React title, in `client.app.<area>`, the title's middle part in lower
  case: `App/Main/JobsScreen` → `app.main.JobsScreenStories`, `App/Dictionary/DictionaryEditor` →
  `app.dictionary.DictionaryEditorStories`. Register it under its title's comment in `AppStories`.
* The class comment names the Stroom presenter, and maps each React fixture/seam to the Stroom REST
  endpoint that replaces it (a small table for more than three, as `JobsScreenStories` has).
* Spy names are React's callback names (`onOpenDoc`, `onShowTasks`, `openScreen`), as
  package-private constants, registered with `harness.fn(name)` in `render`.

### Finding the screen and its endpoints

1. `stroom-ui-react/porting/gwt-inventory.csv`: `grep -i <ReactName>` lists the GWT presenters,
   views and plugin behind the React component.
2. Read the **plugin** (`XxxPlugin`, e.g. `UsersPlugin`, `TaskManagerPlugin`, `DictionaryPlugin`) to
   see how Stroom opens the screen, and do the same in `render`: `UsersPlugin.open` calls
   `refresh()`; `NodeMonitoringPlugin` calls `setSelected(node)` for an `OpenNodeEvent`;
   `TaskManagerPlugin` calls `changeNameFilter(...)`; a `DocumentPlugin` loads the document, checks
   `DocumentPermission.EDIT` and calls `read(docRef, doc, !allowUpdate)`. For a popup opened by an
   event (`ShowXxxEvent`, `OpenUserTaskManagerEvent`), register the presenter as the event's handler
   and fire the event; where Stroom fetches what the event carries first (e.g. the explorer's Info
   item fetches `POST /explorer/v2/info`), the story does that request too, so its fixture is JSON
   (`DocInfoDialogStories`).
3. Read the presenters for `restFactory.create(XXX_RESOURCE).method(r -> r.xxx(...))` (and their
   `XxxClient` helpers, e.g. `NodeClient`, `CredentialClient`), then the resource interface in the
   `*-shared` module for the HTTP method and `@Path` (constants such as `BASE_PATH` are at its top).
   Paths are relative to `/api`.
4. JSON shapes: the `@JsonProperty` names of the shared classes (`grep -o 'JsonProperty("[a-zA-Z]*")'`).
   Enums are sent by name. Watch for required members: a `@JsonCreator` with
   `Objects.requireNonNull` fails the whole reply (e.g. `ExplorerNodeInfo.auditEntries`), and
   without an `onFailure` the screen silently never shows.
5. The gwt-suite corpus (`gwt-suite/corpus/default/api/manifest.json`) has real replies for most
   list endpoints (`GET /api/job/v1`, `POST /api/node/v1/find`, `POST /api/users/v1/find`, ...); use
   them for the shape and the members Stroom fills in, but put the React fixture's *values* in the
   story's fixture, as the plays check them.

### Fixtures

* Write each reply as a Java text block in Stroom's JSON shape with React's values, trimmed to what
  the screen reads. Reuse a block with `.replace("JOB", ...)` (GWT has no `String.formatted`).
* Route requests that differ by their body with `withJsonBodyContaining`, the specific route first
  and a catch-all after it for the others, e.g. one job's nodes and an empty list for any other job
  (strict fixtures fail the story on an unmatched request). This is also how "the server computes
  it" fixtures work: `ServerTasksScreenStories` replies with the match states for a request whose
  `nameFilter` is 'Processor A'.
* Write echoes (`updateNodeTags` returns the node sent) as a `RestHandler`
  (`request -> RestReply.json(request.getBody())`); keep state out of handlers.
* React's recorder checks (`expect(rec.executed).toContain(11)`) become
  `RequestMatcher...toSpyMatcher()` checks on `REQUEST_SPY`. For sets (order not fixed, e.g.
  `jobNodeIds`) use `withBody(description, predicate)` with `JsonValues.parse`.
* App-wide lookups belong in `StartupFixtures` (they were added for `fetchDocumentTypes`); a
  screen's own data belongs in the story.

### GIN, not `new`

* Get the presenter from `AppScreenGinjector`: add a getter under its area's comment. Its modules
  mirror Stroom's GIN modules (`XxxScreenModule` for `stroom...gin.XxxModule`) with the
  `bindPresenterWidget`/`bindSharedView`/`bind(X)` lines copied, plugins
  (`bindPlugin`), eager singletons and app services (`ClientSecurityContext`, `LoginManager`) left
  out, and each `bindPresenter(P, V, VImpl, Proxy)` turned into `bindPresenterWidget(P, V, VImpl)`
  plus a `@Provides` method returning a null proxy (the story registers the presenter as its event's
  handler instead). Shared widgets (pager views, menus, tool tips, editors, expression trees, the
  explorer drop-downs, date pickers) are bound once, in `ScreenViewsModule`.
* GIN checks the whole graph of **every** binding of the injector's modules, used or not, and the
  compile fails if a key is bound twice. So when GIN reports `No @Inject or default constructor
  found for X`, read its "Path to required node", find the Stroom module that binds `X`
  (`grep -rn "X.class" stroom-core-client/src/main/java/stroom/*/client/gin stroom-app-gwt`), and
  either add the mirror of that module to `@GinModules` or, for a widget several areas share, add
  the binding to `ScreenViewsModule` (and leave it out of the area module).
* `Class X is used in Gin, but not available in GWT client code` means a Stroom GWT module isn't
  inherited: add it to `StroomWorkbench.gwt.xml` (copy from `stroom-app-gwt/.../App.gwt.xml`).
* Presenters from GIN are bound (`onBind`) when created, as in Stroom; one made with `new` is not
  bound until it is revealed, so e.g. `CredentialsListPresenter`'s data provider would still be
  null when its dialog is set up.
* Create the injector in `render` (`GWT.create(AppScreenGinjector.class)`), a new one each time:
  the harness refuses an injector another harness has used.

### Start-up, permissions and users

* Create screens inside `harness.afterStartUp(...)` as Stroom opens them after login, and always
  when they read the cached UI config or the user's preferences (lists that format dates, document
  editors). It does nothing harmful for others. (Most of the Users screen's stories open it at once,
  as the regression test of `UiConfigCache.get` calling its consumer with null.)
* React's `fetchEffectiveAppPermissions` → `.appPermissions(...)` (default `ADMINISTRATOR`, which
  implies every permission); a React read-only `ctx`/`readOnly` →
  `harness.getSecurityContext().setDocumentPermission(DocumentPermission.VIEW)` before the plugin's
  permission check; React's `nodeMonitoring` etc. UI config → `.uiConfig(json)` (it replaces the
  whole default `uiConfig`).
* Answer confirmations (`ConfirmEvent`, e.g. 'Run Now', 'Remove Import', terminating a task) with
  `.realAlerts()` and a click on the real dialog's OK.

### Polling and sequences

* Stroom's lists that "poll" (Server Tasks, Nodes, Jobs) refresh when the content pane calls
  `Refreshable.refresh()`, which nothing does in a story, so they make one round of requests.
* A screen with its own timer (`UserTaskManagerPresenter` polls every second) gets the same reply
  each poll (a route's single reply repeats); use a sequence (`get(path, first, second)`) when the
  play needs the data to change. Stop the timer on re-render with `closeOnCleanUp(popup)` (or
  `addTimer` if the story owns it), and check it with the manager's Rerun: no
  "A request from a previous rendering of the story was dropped" warnings after it.
* Debounced inputs (the quick filters, 300-400ms) need a `play.waitFor(3000, ...)` after typing.

### Plays

* Most GWT-specific markup is in `StroomDom` (use it rather than copying selectors):
  `StroomDom.button("OK")` for a Stroom `Button` (its name is its text twice); `DIALOG` (no
  `role="dialog"`), `DIALOG_TITLE`; grid rows are `<tr>` (`getByText(x).closest("tr")`,
  `GRID_ROW`), a selected row has `SELECTED_ROW`, sortable headers `SORTABLE_HEADER`;
  `ACTIONS_TITLE` ('Actions...'); `MENU_ITEM_TEXT` (menu items have no `menuitem` role);
  `LINK_TAB_LABEL` (a document's sub-tabs have no `tab` role); `QUICK_FILTER_PLACEHOLDER` (no
  label); `SELECTION_BOX` (click its text box to open it); `COMMAND_LINK_OPEN`.
* **Command links** (`CommandLinkCell`: Jobs' Node/Schedule, the Imports grid) run on a mousedown on
  their 'open' icon, found inside the element titled with what it opens:
  `play.click(play.within(play.getByTitle("Edit schedule")).querySelector(StroomDom.COMMAND_LINK_OPEN))`.
  To keep a multi-selection, `play.fireEvent().mouseDown(...)` on the icon (the grid then leaves the
  selection alone).
* Grid tick boxes are `TickBoxCell` divs (`.tickBox`), not inputs; list boxes (`<select multiple>`)
  are driven with `play.selectOptions(...)`; context menus with `play.rightClick(cell)`.
* Dialogs and menus are on the body: `play.screen()`. A React `findByText` in the canvas for a
  dialog becomes a `screen` query with a `// Differs from React:` comment.
* End with the `ALERT_SPY`/`UNHANDLED_REQUEST_SPY` checks (a shared `expectNoProblems(play)` helper
  per class), and check the requests React's recorder checked.
* A React check that GWT can't fail (e.g. the absence of text GWT never shows) becomes a check of
  what GWT shows instead, with a `// Differs from React:` comment (see `NodeJobs`,
  `InfoActionsGatedByKeys`).

### Pitfalls

* `GWT.create(...)` of a resource (e.g. `DictionaryResource`, `ExplorerResource`) in `render`, not in
  a `static final` field (registration runs on the JVM).
* A `play.spy(...)` inside a supplier fails at run time; get it while building
  (`final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);`) and read `requests.getCalls()` in the
  supplier.
* An uncaught exception from Stroom's code (e.g. a `NullPointerException` while building a menu) is
  reported as "Uncaught error in the story", often with an obfuscated name (`reading 'PNc'`). To
  read it, temporarily add `'-style', 'PRETTY'` to `workbenchDraftCompile`'s args (don't keep it),
  or read `window.__workbenchPlay.error`. When it is a Stroom bug, record it (`GWT bug` in
  `REACT-DIFFERENCES.md`), port what still works (e.g. a fixture without the key that triggers it)
  and suggest the fix in your report.
* Stroom bugs found by the pilot, which other screens may hit: unquoted quick filter terms built
  from names with spaces (`UserAndGroupHelper.buildDisplayNameFilterInput`). Fixed since, with
  stories as their regression tests: `UiConfigCache.get` calling its consumer with null
  (`UsersScreen`); `new DocRef(type, null, name)` for the Server Tasks screen's 'Open Feed' (null
  UUIDs are refused; it now looks the feed up by name: `ServerTasksScreen`'s `InfoActions`);
  confirmation callbacks that ignored `ok`, so a cancel still executed the job or terminated the
  task (`JobNodeListHelper.executeJobNow`, `UserTaskManagerPresenter.onTerminate`: `JobsScreen`'s
  `JobSchedule`, `UserTaskManagerDialog`'s `Tasks`); a cancelled delete that never called its
  `ResultCallback` (`DocumentPluginEventManager`: `DeleteConfirmation`'s `CancelIsReported`); and
  the credential picker returning the credential's UUID as its name
  (`CredentialsManagerViewImpl.getCredentialName`: `CredentialPickerDialog`'s `Pick`).

### Module inherits added by the pilot

`stroom.credentials.Credentials`, `stroom.importexport.ImportExport` (the credentials' key store
upload), `stroom.dictionary.Dictionary`, `stroom.job.Job`, `stroom.node.Node`,
`stroom.schedule.Schedule`, `stroom.processor.Processor` and `stroom.monitoring.Monitoring`. Add
yours (from `App.gwt.xml`) when GIN or the compiler says a class isn't available.

### Checking

* `workbenchTest -PworkbenchTestFilter=App/Main/JobsScreen` (and `workbenchCoverage
  -PworkbenchCoverageList=App/Main/JobsScreen` for 100%). While iterating, a draft compile (~25s)
  and `node run.mjs App/Main/JobsScreen --url http://127.0.0.1:<port>` in `test-runner` against
  your own `workbenchServe` is quickest.
* To see the DOM before writing a play, register the story with a minimal play (the preview runs
  the play when it loads), then read the page (`iframe.html?id=<id>&viewMode=story`) with
  Playwright.

## Conventions from the editors batch (`App/Editors/*`, `App/Index/*`, `App/Feed/*`, `App/AI/*`)

* **Document editors** use `client/app/editors/DocEditors`: `DocEditors.render(context, fixtures,
  readOnly, (harness, injector) -> DocEditors.open(harness, docRef, injector.getXxxPresenter(),
  DocResource.of(fetch, update)))` creates the batch's `EditorsScreenGinjector` and a harness with
  `realAlerts()`, gives the user OWNER (or VIEW) on every document, sets up Stroom's
  `StaticEventBus` and `HelpManager` (form help buttons) and, after start-up, opens the document as
  `DocumentPlugin` does (fetch, permission check, `read`, the default tab). The toolbar's Save
  (`SaveDocumentEvent`) is answered as `DocumentPlugin.save`: write, `PUT` to `update`, then the
  editor's post save callback (`VisualisationPresenter` publishes its assets there), then `read`.
  So a React check of the document recorded on each change becomes: edit, Save, and check the
  `PUT` body with `RequestMatcher.put(...).withJsonBodyContaining(...)` or `withBody(...)`.
* `DocEditors.permissionRoutes(builder)` adds React's shared `docPermissionFixture` (admin is
  OWNER) for the Permissions tabs; `DocEditors.docSelectionRoutes(builder)` answers
  `explorer/v2/decorate` and `getFromDocRef` (document selection boxes, e.g. a feed or cluster) with
  the document asked about.
* Text boxes report their change (and so make the document dirty) on blur: type, then
  `play.tab()`. FormGroup controls have the group's `identity` as their id (query those, with a
  `// Differs from React:` constant).
* Stories that open screens using `UiConfigCache` defaults Stroom always sends (e.g. the execution
  schedule dialog's `analyticUiDefaultConfig`) set them with `.uiConfig(...)`.
* **The 'Ask Stroom AI' chat opened by another screen** (a results table's 'Ask Stroom AI' button
  fires `AskStroomAiEvent`): add the ginjector's `AskStroomAIScreenModule` and a getter for
  `AskStroomAiPresenter`, route the chat's requests with `AiFixtures.chatRoutes(builder)` (a
  `DIALOG` config: Stroom's default, `DOCK`, needs the app's main layout) and register the chat with
  `AskStroomAiChat.register(harness, injector::getAskStroomAiPresenter)`
  (`DashboardEditor`'s `TableAskAiButton`, `QueryEditor`'s `AskAiButton`). It creates the presenter
  on the first event, as its GWTP proxy does, i.e. after start-up. Don't create the presenter
  before start-up (e.g. in a `DashboardSupport` `setup`): its constructor reads the AI config from
  the user's preferences, and as Stroom's default preferences have no `askStroomAiConfig` it
  fetches the default and stores it in a copy of the user's preferences, which aren't loaded until
  `afterStartUp`. That once failed ("Cannot read properties of undefined (reading 'copy')", from
  `AskStroomAiClient.setConfig`); `UserPreferencesManager.getCurrentUserPreferences()` now gives
  defaults until the preferences load, and `AskStroomAiClient` waits for the user's preferences
  (`UserPreferencesManager.whenLoaded`) before reading or storing its config, so creating it after
  start-up is for fidelity (the default is then stored, as in Stroom).
* A polling chat (AI) uses a stateless `RestHandler` that replies with the messages after the
  request's `lastSeenMessageId`, as the server does (a repeated reply would add the messages again);
  `RestReply.delayed(...)` keeps a request in flight (e.g. to show a Stop button).
* Iterating while other batches' code is broken: the batch kept a private draft compile of only its
  own stories (an `AllStories` override ahead of the sources on the GWT classpath, its own war and
  work dirs and its own `WorkbenchServer`), so another batch's broken ginjector couldn't stop it.
  Keep the GWT unit cache small if the scratch space has a quota.

### Module inherits added by the editors batch

`stroom.documentation.Documentation`, `stroom.http.Http`, `stroom.contentstore.ContentStore`,
`stroom.gitrepo.GitRepo`, `stroom.openai.OpenAIModel`, `stroom.search.elastic.ElasticCluster`,
`stroom.search.elastic.ElasticIndex`, `stroom.search.solr.SolrIndex`, `stroom.index.Index`,
`stroom.pathways.Pathways`, `stroom.analytics.Analytics`, `stroom.datagen.DataGen`,
`stroom.folder.Folder`, `stroom.visualisation.Visualisation`, `stroom.script.Script`,
`stroom.xmlschema.XMLSchema`, `stroom.kafka.KafkaConfig` and `stroom.aws.s3.S3Config`.

### Conventions from the processing batch (`App/Main/NodeGroups*`, `Processor*`, `Pipeline*`, `Stepping*`, `DataRetention*`, `DataReceiptRules*`, `ExecutionSchedules*`, `App/Data/*`)

* **Package-private Stroom methods** (e.g. `NodeGroupEditPresenter.show`, called only by
  `NodeGroupPresenter`) are called with a JSNI method in the story, which ignores Java's access
  rules, rather than by opening the whole parent screen.
* **Plugins the story stands in for** (`PipelinePlugin.onBeginStepping`/`step`, `DataDisplaySupport`,
  `HyperlinkEventHandlerImpl.openData`, `QueryPresenter.setProcessorLimits`) are copied into the
  story's `render`, naming the source, when they only wire up presenters the injector can create.
* **Document plugins** (`DocumentPluginRegistry` is filled as the app's plugins start) are
  registered with `StoryDocumentPlugins` (see "Document plugins" under the harness): the
  `ProcessingScreenGinjector` gives `DocumentPluginEventManager`, `PipelinePlugin` and `XsltPlugin`
  (and binds `ContentManager` as a singleton). `SteppingScreen`'s `SteppingWithCode` registers
  `XsltPlugin`, so a stepping element's code loads; `PipelineEditor`'s `MultiDocumentSave` opens the
  pipeline with `OpenDocumentEvent` through `PipelinePlugin`, whose Save shows the 'Save Pipeline'
  picker for the code edited while stepping. (`EmbeddedProperty` still stops before OK; registering
  `XsltPlugin` would let it create the embedded XSLT.)
* **Null checks the server makes unnecessary**: fixtures must include what the server always sends
  and GWT reads without a null check, e.g. a processor filter tracker's `status`, a meta row's
  `attributes`, a selection summary's `ageRange`, an execution schedule's `scheduleBounds`.
* Shared pipeline replies (element types, a stepper) are in the package-private
  `app/main/PipelineFixtures`.

### Module inherits added by the processing batch

`stroom.data.retention.DataRetention`, `stroom.data.store.impl.fs.FsVolume` and
`stroom.dashboard.Dashboard` (and `stroom.analytics.Analytics`, also added by the editors batch).

### Conventions from the content batch (`App/Main/*` content, monitoring, volume, activity and annotation screens, `App/Annotations/*`)

* **Press a grid row's cell, not its `<tr>`.** A play's event is dispatched to the element it is
  given, and GWT's grids handle the events of their cells, so `play.dblClick(play.getByText(x))`
  opens a row where `play.dblClick(play.getByText(x).closest("tr"))` does nothing.
* **Alerts are closed with 'Close'.** `AlertEvent`s are shown in a `CLOSE_DIALOG` (caption
  'Alert'), so a play dismisses one with `getByRole("button", StroomDom.button("Close"))`;
  confirmations (`ConfirmEvent`) have OK and Cancel.
* **Read-only text in a text area** (e.g. `DependenciesInfoViewImpl`) is checked with
  `querySelector("textarea...").value()`, not `getByText`.
* **Labels for panels**: a `FormGroup` whose child is a panel (e.g. a text area beside a password
  box) labels the panel, so `getByLabelText` finds nothing; find the control inside the group:
  `within(getByText(label, "label").closest(".form-group")).querySelector("textarea")`.
* **Silent decode failures**: a reply that Stroom's JSON classes refuse (e.g. a
  `ContentStoreMetadata` without its required `ownerId`) can leave a list empty with no error
  shown; check the shared class's `@JsonCreator` for `requireNonNull` when a list stays empty.
* **Document plugins** can be registered (now with `StoryDocumentPlugins`, see "Document plugins"
  under the harness): getting a plugin (e.g. `XMLSchemaPlugin`) and
  `DocumentPluginEventManager` from the batch's injector registers the plugin, so
  `OpenDocumentEvent` loads and opens the document as in Stroom, and the editor's Save
  (`SaveDocumentEvent`) goes through `DocumentPlugin.save` (`App/Main/docPlugin`). Answer the
  explorer's `decorate` with `ContentStorySupport.decorate` (it replies with the document asked
  about), and show the opened tab by handling `OpenContentTabEvent` with `harness.addContent`.
  Unbind both on clean up (`harness.addCleanUp(plugin::unbind)`).
* **Uploads before a step**: where a React story uploads a file and then works on what the upload
  returns (e.g. Import's 'Confirm Import'), the story uploads it too, answered by an upload reply
  (see "Uploads" under the harness); the Import stories start at the 'Import' dialog.
* **Batch helpers** (in `app.main`): `ContentStorySupport` (`expectNoProblems`, `queryParam`, `decorate`)
  and the batch's fixtures (`ActivityFixtures`, `AnnotationFixtures`).
* A leftover workbench server (e.g. after a Gradle daemon is killed for memory) keeps the test port;
  stop it by its PID (`ss -ltnp | grep <port>`) before the next run.

### Module inherits added by the content batch

`stroom.cache.Cache`, `stroom.receive.content.ContentTemplate` and `stroom.aws.common.AwsCommon`
(the data volumes' `FsVolume` needs the S3 config's AWS classes).

## Conventions from the query batch (`App/Editors/QueryEditor`, rule, report, View, Plan B, statistic store)

* **Ginjector**: `client/app/gin/query/QueryScreenGinjector` (mirrors of Stroom's View, Plan B,
  Query, dashboard Table/Query, Annotation, StreamStore, Activity, Alert, Pipeline, Analytics,
  Report and Statistics modules, plus `QueryExtrasScreenModule` for single bindings:
  `DataUploadPresenter`, `IFrameContentPresenter`). It also gives `HyperlinkEventHandlerImpl` and
  `DataDisplaySupport` (Stroom creates both eagerly: a story that needs a table's links handled
  gets them from the injector), `DateTimeSettingsFactory` and `ResultStoreModel`.
* **Opening a document editor**: `app/query/DocumentEditors.open(harness, presenter, docRef,
  resource, res -> res.fetch(uuid))` does what a `DocumentPlugin` does (fetch, EDIT check, read,
  `addContent`) and then fires `ContentTabSelectionChangeEvent` for the editor, as the content pane
  does when it selects a tab (the query table only reacts to annotation changes while its tab is
  visible). `ownerPermissions(builder)` adds the Permissions tab's routes; `decorated(builder,
  docRefJson...)` answers a doc picker's `explorer/v2/decorate` and `getFromDocRef`.
* **Searches** (reusable for dashboards): `app/query/QueryFixtures`. A search is a poll of
  `POST /query/v1/search/{node}` (`/dashboard/v1/search/{node}` for a dashboard's `SearchModel`)
  until a reply is `complete` or `null`: route `QueryFixtures.SEARCH` with a reply sequence
  (`response(complete, results...)`, `tableResult(...)`, `visResult(...)`). A later reply can be
  `.delayed(...)` to keep the search running (e.g. for a pause button). `editorRoutes(builder)`
  adds, with `addAll` so that a story's own route for the same request wins, what a StroomQL
  editor asks for: help items, time zones, the query's data source, the current activity (Stroom
  checks it before each search), help details and the result store's `destroy`/`terminate`
  (`/result-store/v1/...`). `uiConfigWith(members)` adds members (e.g.
  `analyticUiDefaultConfig`) to the default UI config: rule and report editors need
  `analyticUiDefaultConfig`/`reportUiDefaultConfig` present (Stroom's server always sends them;
  `AbstractSettingsPresenter` NPEs without them).
* **Results table headers**: `MyDataGrid` only handles a header's mouse buttons after the mouse has
  moved over it (a deferred native preview handler), and user-event's click moves the mouse in
  again from the body, so `QueryEditorStories.clickHeader(play, target)` fires a mousemove, waits
  200ms, then mousedown/mouseup/click. A column's menu opens on a left click (not a context menu);
  the values filter on the header's `.column-valueFilterIcon`. Header labels are
  `.column-top .column-label`.
* **App events the screen hands on** (create document, open document, annotations, stepping,
  show data, ask AI) are checked with spies on the event bus; the create dialog is stood in for by
  a probe panel (React's `CreatePendingProbe`) whose 'run-seed' button runs the event's consumer.
* **Ace editors in dialogs**: click `.ace_content` then `play.keyboard(text)`, and wait for the
  text in `.ace_content` before pressing OK.
* **New project dependency**: `stroom-statistics-client` is a dependency (and GWT client project)
  of the workbench, for the statistic store editor.
* **Module inherits added**: `stroom.view.View`, `stroom.planb.PlanB`, `stroom.main.Main` (the
  query table's `ConditionalFormattingDynamicStyles`) and `stroom.statistics.impl.sql.SqlStatistics`
  (`stroom.analytics.Analytics` was added by another batch at the same time).

## Conventions from the security batch (accounts, users, groups, permissions, signing keys, `App/Core/*`)

* **Ginjector.** `client/app/gin/security/SecurityScreenGinjector` lists the shared base modules plus
  `IdentityScreenModule` (the mirror of Stroom's `ChangePasswordModule`, without the login, reset
  and current password presenters). `CurrentPasswordPresenter` needs Stroom's concrete `CurrentUser`
  (whose graph, the splash screen and current activity, GIN can't build here): create it with
  `new`, giving it `new CurrentUser(eventBus, restFactory, null, null)` signed in with
  `setUserAndPermissions(aup, false)` (needs the `stroom.activity.Activity` inherit).
* **Changes sent with nulls.** RestyGWT ignores `@JsonInclude(NON_NULL)`, so a change such as
  `AccountChange` sends the members it leaves alone as `null` (and an empty action set as `[]`).
  Check "only these values" with `SecurityPlays.withOnlyValues(matcher, json)`; read the first of
  several requests (React's `rec.finds[0]`) with `SecurityPlays.firstBody(spy, matcher)`.
* **Labels that don't label.** A `FormGroup` around a composite (a `CustomCheckBox`, or a
  `FlowPanel` holding a password box) points its label at the composite's `div`, so
  `getByLabelText` finds nothing: find the control in the label's form group
  (`getByText(label, "label").closest(".form-group")`) or by its class (`.passwordTextBox`,
  `.confirmPasswordTextBox`). Sortable column headers have `role="button"`, not `columnheader`:
  read their names from `.dataGridSortableHeaderNameHolder`. A `UserListPresenter`'s grid
  (label, quick filter, toolbar, pager) is its label's `.closest(".dock-container-vertical")`.
* **Lists that select their first row** when they load (`UserAccessListPresenter`,
  `UserDependenciesListPresenter`): a 'no selection' check first clears it with a ctrl-mousedown
  on the selected row (`play.fireEvent().mouseDown(q, EventInit.create().ctrlKey())`).
* **Info alerts** (e.g. 'Successfully changed permissions.') are recorded by `ALERT_SPY` as
  `INFO: ...`: check for them instead of ending with `expectNoProblems`.
* **Rules that aren't screens** (React's `App/Core/*` pure functions) are ported as checks of the GWT
  code that applies them: shared classes called in a value's supplier (`DeleteConfirmation.isEmpty`,
  a `CurrentUser`'s `hasAppPermission`), or a real handler created with `new` and `bind()`
  (`DocumentPluginEventManager` for `DeleteDocumentEvent`) driven by a button per case in the canvas,
  with Stroom's real dialogs (`realAlerts()`). Create the Stroom objects a check uses inside its
  supplier, so that each run of the play checks fresh ones.

### Module inherits added by the security batch

`stroom.activity.Activity` (for `CurrentUser`).

## Conventions from the idp batch (`App/IdP/*`, `Screens/SignIn/redirectUrl`)

* **Ginjector**: `client/app/gin/idp/IdpScreenGinjector` (`ScreenViewsModule` plus `IdpScreenModule`,
  the sign in, authentication error and password reset pages with null proxies, and the change
  password and 'Reset Your Password' dialogs).
* **Pages outside the app shell** (`client/app/idp/IdpPage`, `SignInPages`): Stroom's
  `App.onModuleLoad` reveals `LoginPresenter`, `AuthenticationErrorPresenter` and
  `ResetPasswordPresenter` with `forceReveal()`; a story adds the presenter with `addContent` and
  calls its protected `revealInParent()` with JSNI (`IdpPage.reveal`). They look up the host page's
  `#loading`/`#loadingText` elements when created, which `IdpPage.setUp` adds (outside any widget,
  as `RootPanel.get(id)` requires).
* **URL parameters**: a screen that reads `Window.Location.getParameter` (`error`, `redirect_uri`,
  `token`) gets them added to the story page's URL with `history.replaceState`, restored on clean
  up; GWT reads the parameters again whenever the query string changes.
* **Navigation**: `Window.Location.replace(url)` (which a `LocationManager` can't intercept) is
  cancelled with the browser's Navigation API (`navigation`'s `navigate` event, Chromium) and
  recorded by the `onRedirect` spy, relative to the origin (`""` for a reload). A story that needs it
  fails clearly in a browser without the API, rather than navigating away.
* **Private Stroom methods** checked as pure functions (`LoginPresenter.isSameOrigin`) are called with
  JSNI on a real presenter created by the story's rendering.

## Conventions from the shell batch (`App/Main/AppShell`)

* **The whole app shell**: `app/main/ShellScreen` puts Stroom's shell together from its real
  presenters and plugins with `client/app/gin/shell/ShellScreenGinjector` (other batches' mirrors
  plus `ShellAppScreenModule`: the main view, `NavigationPresenter`, About, User Preferences,
  Welcome, current password and Feed bindings). `create(context, fixtures, options)` builds the
  harness (with `realAlerts()`); `start(initialDocRef)` then logs in as Stroom does once start-up
  is done: every main menu plugin, the Dictionary/Feed/Folder document plugins and
  `DocumentPluginEventManager` are created (getting a plugin from the injector registers it), the
  dialogs GWTP proxies would show for their events are registered lazily (Find, Recent Items, New,
  Copy, Move, Rename, Info, Export, Edit Tags), then the splash screen, the initial activity
  chooser and `ShowMainEvent`, which shows the main view and the explorer and opens the initial
  document (a deep link). `startFullScreen(docRef)` is Stroom's embedded view instead. Reuse it for
  any story about the shell, the explorer, the main menu or opening documents from the explorer.
* **Presenter visibility matters**: `ContentTabPanePresenter.onOpen` calls `forceReveal()`, whose
  `revealInParent()` clears every tab, so a tab pane that isn't visible keeps only the last tab
  opened. The shell makes the main view visible as GWTP's root would (JSNI
  `PresenterWidget.internalReveal()`) with the tab pane already in its content slot, and puts the
  explorer in its slot after `NavigationPresenter.onShowMain` (which sets its tree only when it
  reveals itself). Stories that only need one tab can keep using `harness.addContent(tabPane)`.
* **Keyboard shortcuts**: `MainPresenter` adds `GlobalKeyHandler` DOM handlers to the page's body
  and never removes them, so the shell gives it a key handler that passes keys to Stroom's real
  `GlobalKeyHandlerImpl` only until the story renders again (and cancels its 30 second refresh
  timer). `KeyBinding`'s commands are static: the last shell rendered owns them. Key sequences
  (`g` `u`, Shift Shift) complete on key release, so plays use `play.keyboard("gu")`.
* **Menus**: the explorer's menu opens on a secondary mousedown (`play.rightClick`), a tab's on a
  secondary mouseup (`fireEvent().mouseUp(tab, button(2))`); main menu groups render their items
  only while hovered. Items are `.menuItem-outer[title="..."]`, disabled ones `menuItem-disabled`,
  shortcut hints `.menuItem-shortcut`.
* **Fixture shapes**: `fetchDocumentTypes` needs `visibleTypes` as well as `types` (the type filter
  streams it); the explorer's `create` reply needs the node's `rootNodeUuid`, `uniqueKey` and
  `nodeFlags` (the explorer highlights it); the explorer's permissions are looked up by node, so
  the reply echoes the nodes asked about (`AppShellFixtures.explorerPermissions`); the System and
  Favourites roots must have Stroom's uuids (`0`, `1`); top-level `ExtendedUiConfig` members (e.g.
  `dependencyWarningsEnabled`) need `startup(s -> s.extendedUiConfig(...))`, not `uiConfig(...)`.

### Module inherits added by the shell batch

`stroom.help.Help` (`HelpPlugin`).

## Conventions from the dashboard batch (`App/Editors/DashboardEditor`, `App/Dashboard/TableFilterSettings`, `App/Query/QueryResultsTable` › `DashboardExpressionEditor`)

* **Opening a dashboard**: `app/dashboard/DashboardSupport.story(docJson, routes, options)` renders
  Stroom's real `DashboardSuperPresenter` (Dashboard, Documentation, Permissions) from the batch's
  `gin/dashboard/DashboardScreenGinjector`, as `DashboardPlugin` opens it (fetch, EDIT check,
  `read`, `ContentTabSelectionChangeEvent`), with `realAlerts()`, OWNER permission, the dark theme's
  preferences (`DashboardSupport.DARK_PREFERENCES`, so Ace editors are dark as in React) and Save
  answered as `DocumentPlugin.save`. The dashboard is a `DashboardDoc` JSON served from
  `GET /dashboard/v1/{uuid}` (and echoed on `PUT`); `DashboardSupport.fixtures` adds defaults (a
  dashboard search with `t1`'s rows `alpha`/`beta`, a StroomQL search, the referenced query `q-ref`,
  the editor routes and the Permissions tab) after the story's own routes. Options: `linkParams`
  (as `DashboardPlugin.openParameterisedDashboard`), `harness(...)`, `setup((harness, injector) -> ...)`
  (app event handlers, spies) and `afterOpen(...)`.
* **Component types**: a dashboard creates its components through `ComponentRegistry`, which
  Stroom's component plugins (eager singletons) fill; `DashboardSupport.registerComponentTypes`
  gets every plugin from the injector before the dashboard is read (the ginjector keeps
  `ComponentRegistry` and `VisFunctionCache` singletons).
* **Documents**: `app/dashboard/DashboardDocs` builds React's dashboards in Stroom's JSON
  (`component`, `query`, `table`, `tabs`, `split`, `sized`, `operator`, `term`). Every layout needs
  a preferred size and every Embedded Query an `automate`, `queryTablePreferences` and a full
  `embeddedQueryDoc` reference (Stroom throws without them), and a dashboard needs its model version
  (else Stroom adds a legacy 'Params' input); the builders add them.
* **Plays** (`app/dashboard/DashboardPlays`): wait for the dashboard with `opened(play)` (it opens
  after the story renders); component tabs are link tabs (`tab`, `linkTab`, `selectTab`), and a
  tab's menu opens on a click of the *selected* tab (`openTabMenu`); `runQuery(play, i)` clicks a
  Query component's own Execute, `runAll` the toolbar's (an Embedded Query has none); Add
  Component's menu items are `.menuItem-simpleText` (`simpleMenuItem`). `FlexLayout` reads mouse
  events (with capture), so tabs and splitters are dragged with `drag(play, from, to, fx, fy, dx,
  dy)`, which computes the drop point from the target's rectangle when the step runs. A window close
  is `closeWindow(play)` (`beforeunload`). Request bodies are read with `at(json, path...)`,
  `param`, `componentIds` and `componentRequest`.
* **Stroom bugs that shaped the plays**, now fixed: a dashboard search whose `update` throws polls
  forever, and `QueryPresenter.getCurrentErrors` threw for a Query that hadn't searched (the
  selection-driven stories run only the master query and check every search completes: no Query
  button left as 'Stop Query'); `MySingleSelectionModel` counted two quick list refreshes as a
  double select, which closed the Query Favourites dialog (`QueryHistoryAndFavourites` changes the
  list quickly and checks the dialog stays open). Any other exception in a search's `update` would
  still poll forever, so check for a Query still searching when a play waits for nothing.
* **Private iteration**: as the editors batch, the dashboard batch compiled only its own classes
  (`javac` into a private classes dir ahead of the workbench's on the GWT classpath, and an
  `AllStories` override) so other batches' broken code couldn't stop it.
