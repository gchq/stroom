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

## Widget stories

A widget story creates the widget, applies the story's args and returns it. See
`client/widgets/ButtonStories.java`. Args and controls are declared with `ArgType`, and React's
`fn()` args become spies, `context.fn("onClick")`, that play functions check with
`play.spy("onClick")`.

## Screen stories

Screens (presenters and views) run for real against fake REST replies. See
`client/app/main/RecentItemsDialogStories.java` and `AboutDialogStories.java`.

* `ScreenHarness.create(context, fixtures)` gives the story an event bus, Stroom's real
  `RestFactory`, a `PopupManager` and spies.
* `RestFixtures` answers requests by method and path (e.g. `post("/explorer/v2/find", ...)`) with
  JSON in the same shape as the `gwt-suite` corpus, so recorded replies can be pasted in. Every
  request is recorded by the `ScreenHarness.REQUEST_SPY` spy (`"METHOD /path body"`) and every
  alert by `ScreenHarness.ALERT_SPY`.
* Presenters and views are created with `new` (pass `null` for a GWTP proxy). A screen opened by
  an event is shown by registering the presenter as the event's handler and firing the event, as
  GWTP's proxy would.
* Dialogs are shown on the page's body, as in Stroom, so plays find them with `play.screen()`.
  Popups left over from a previous rendering are removed when the story renders again.
* A screen may need more Stroom GWT modules inherited in `StroomWorkbench.gwt.xml`: RestyGWT
  generates code for every method of a resource interface used, so the types of all of them
  must be available.

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
| `getByText('Name', {selector: 'label'})` | `getByText(TextMatch.exact("Name"), "label")` |
| `getAllByX(...)[i]` | `getAllByX(...).nth(i)` |
| `getAllByX(...).length` | `.count()` |
| `.map(e => e.textContent)` | `.textContents()` |
| `el.closest(sel)` | `q.closest(sel)` |
| `canvasElement.querySelector(sel)` | `play.querySelector(sel)` |
| `document.querySelector(sel)` | `play.screen().querySelector(sel)` |
| `el.children` | `play.within(q).querySelectorAll(":scope > *")` |
| `await findByText(x)` | `play.findByText(x)` |
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
| `toEqual({...})` / `toMatchObject({...})` | `toEqual(Map.of(...))` / `toMatchObject(Map.of(...))` |
| `expect.objectContaining({...})` | `ValueMatcher.objectContaining(Map.of(...))` |
| `expect.stringContaining(x)` | `ValueMatcher.stringContaining(x)` |
| `args.onX = fn()` | `context.fn("onX")` in the story, `play.spy("onX")` in the play |
| `expect(args.onX).toHaveBeenCalledWith(...)` | `play.expect(play.spy("onX")).toHaveBeenCalledWith(...)` |
| `spy.mock.calls.at(-1)` | `spy.lastCall()` |
| `userEvent.click/dblClick/hover/type/clear` | `play.click/dblClick/hover/type/clear(q, ...)` |
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
* `null` stands for both `null` and `undefined`; a single `null` spy argument is written
  `(Object) null`.
* `toMatchObject` and `objectContaining` work on `Map`s; use `toSatisfy` for Java objects.
