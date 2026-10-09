# Component and Callbacks Model for the Stroom UI

Draft, 9 October 2026. Exported from the design doc:
https://claude.ai/code/artifact/e93329dc-1546-4557-9591-79a6246f84a3

## Summary

Replace GWTP's presenter lifecycle with an in-house **component model** in which a component owns the components it shows, and a parent and its children talk through **callbacks** rather than the event bus. Ownership, clean-up and cross-cutting state (read-only, task monitor) become structural, so authors stop wiring them by hand. A pilot in a separate package first duplicates one screen (the Dictionary editor) to prove the framework against the original. The change is then delivered inside our own `MyPresenterWidget` in phases, so the ~250 existing presenters keep working throughout and GWTP is removed last.

**Goals**

- Nothing a component registers or creates outlives it; `PresenterScope` is retired.
- Inputs go in through constructors and setters; outputs come back through callbacks. The event bus carries only app-wide events.
- Read-only, the task monitor and the current document are set once and read by any descendant.
- A new screen needs fewer pieces: no GIN module entry, and a separate view interface only when it earns its keep.
- Every component can be shown in a workbench story without application scaffolding.
- Old presenters and new components embed each other freely during the migration.

**Non-goals**

- A virtual DOM, re-rendering or a reactive state library.
- A form-building DSL or replacing UiBinder.
- Replacing GIN; that is a separate decision (see Risks).
- The React port.

## Problems with the current model

GWTP ties a presenter's lifetime to bind and unbind, not to who created or shows it, and we use almost none of the slot and reveal machinery that was meant to manage it. Everything else (ownership, data flow, read-only, progress) is wired by hand on each screen.

### What we actually use of GWTP

Tab content is shown by swapping layers (`LayerContainerImpl.show`), not by `setInSlot`, so `onReveal`/`onHide` never fire for tab bodies. Only 2 presenters in `stroom-core-client` use slots and 24 override `onReveal`. Proxies (26 `bindPresenter` calls) exist only for the application shell and code-split plugins.

### Where the cost shows up

| Problem | How it happens today | Scale (approx.) |
| --- | --- | --- |
| No owner for most presenters | GIN `Provider.get()` makes a bound presenter that nothing releases. `PresenterScope` now captures presenters made while a document opens, but presenters made later need `inScope`/`captureIn`, and dialogs are only released if made inside a scope | 649 `bindPresenterWidget` bindings; ~46 scope references in 9 files |
| Dialogs are bound and unbound but never disposed | `PopupManager` binds on show and unbinds on hide; the caller keeps the instance | 161 `ShowPopupEvent` call sites |
| Parent and child talk over the global bus | `fireEvent(this)` and `addHandlerToSource` route child-to-parent messages through the shared bus, keyed by source, so a forgotten registration keeps both alive | 64 `addHandlerToSource`, ~424 `XxxEvent.fire(this, …)` |
| Dirty state is an event chain | Child `DocPresenter`s fire `DirtyEvent`; `TabContentProvider` re-fires it; `DocTabPresenter` listens | 20 `DirtyEvent.fire`, 32 `addDirtyHandler` |
| Read-only is passed by hand | `read(docRef, doc, readOnly)` → `onRead` → `onReadOnly` → each widget's `setReadOnly`; any missed step leaves a field editable | 109 `onRead` overrides, ~49 `onReadOnly`, 292 `setReadOnly` calls |
| Task monitors are passed by hand | `setTaskMonitorFactory` chains and `instanceof HasTaskMonitorFactory` checks | 80 `setTaskMonitorFactory` calls, 39 overrides |
| Async results arrive after close | `RestFactory` has no cancel, owner or disposed check, so callbacks run on unbound presenters | ~482 `.exec()` sites |
| A screen is many files | Presenter, view interface, `ViewImpl`, `ui.xml`, UiBinder `Binder`, GIN binding, `UiHandlers` interface | 243 view interfaces, 123 with `HasUiHandlers` |

`PresenterScope` (added on this branch) proves that releasing a closed document's presenters works, but it does so from outside: it relies on a static "current scope" at construction time rather than on the actual composition.

## Design principles

We take React's ownership and data-flow rules, not its rendering machinery. Each principle below removes a class of bug we fix repeatedly today.

| Principle | Borrowed from React | What it replaces here |
| --- | --- | --- |
| A component owns what it creates and shows; disposing it disposes them | The render tree is the ownership tree; unmount cascades | `PresenterScope`, manual `bind()`/`unbind()`, leaks via the event bus |
| Every registration belongs to a component and is undone with it | `useEffect` returns its clean-up | `registerHandler`, `addHandlerToSource`, forgotten timers and JS listeners |
| Inputs in, callbacks out | Props down, callbacks up | Bus events between parent and child (`DirtyEvent`, `addHandlerToSource`) |
| Cross-cutting values are provided once and looked up | Context | Threading `readOnly` and `TaskMonitorFactory` through every `read()` and setter |
| Small behaviours are composed, not inherited | Hooks and composition | Deep generic hierarchies and anonymous provider subclasses |
| A component's inputs are explicit, so it can be shown alone | Components are easy to put in Storybook | Story scaffolding that recreates GIN singletons and events |

**What we deliberately do not copy**

- **Virtual DOM and re-rendering.** GWT widgets are stateful and long-lived; diffing a virtual tree in compiled Java costs more than it saves. Components build their widgets once and update them directly.
- **The rules of hooks.** They exist because React re-runs a function on every render. Java classes keep state in fields and need no ordering rules.
- **JSX.** UiBinder or plain widget code stays.
- **A global store.** Data still lives in the component that loads it and is passed down.

## Component model

A component is a widget plus an ownership node: it owns child components, registrations and clean-ups, and disposing it releases all of them, depth first. Visibility (attached or detached) is separate from ownership, so a hidden tab keeps its state until its owner disposes it.

### The ownership node

Ownership lives in a small class, `ComponentNode`, rather than in a base class. Both the new `Component` and the existing `MyPresenterWidget` hold one, so old presenters and new components sit in the same tree from day one.

```java
public final class ComponentNode {
    ComponentNode parent();
    void adopt(ComponentNode child);        // child.parent = this; disposed with this
    void release(ComponentNode child);      // dispose one child early (e.g. a replaced tab)
    void add(HandlerRegistration registration);
    void add(Runnable cleanUp);
    <T> T lookup(ContextKey<T> key);        // nearest ancestor that provides the key
    <T> void provide(ContextKey<T> key, ContextValue<T> value);
    void dispose();                         // children (newest first), then registrations, then clean-ups
    boolean isDisposed();
}
```

### The Component base class

```java
public abstract class Component implements IsWidget, HasComponentNode {

    // Ownership
    protected final <C extends HasComponentNode> C own(C child);
    protected final void release(HasComponentNode child);

    // Registrations, all undone on dispose
    protected final <H> void listen(Type<H> type, H handler);        // app-wide event bus
    protected final void own(HandlerRegistration registration);      // e.g. a widget's click handler
    protected final void onDispose(Runnable cleanUp);                // timers, JS objects
    protected final <T> Consumer<T> guard(Consumer<T> callback);     // no-op once disposed (async results)

    // Context
    protected final <T> T context(ContextKey<T> key);
    protected final <T> void watch(ContextKey<T> key, Consumer<T> onChange);
    protected final <T> void provide(ContextKey<T> key, ContextValue<T> value);

    // Lifecycle hooks
    protected void onAttach() { }   // widget entered the page (tab shown, dialog opened)
    protected void onDetach() { }   // widget left the page (tab hidden, dialog closed)

    public final void dispose();
}
```

### Lifecycle

| State | Entered when | Allowed | Hooks |
| --- | --- | --- | --- |
| Created | Constructor runs | Build widgets, `own()` children, set callbacks | none |
| Owned | An owner calls `own()` | Context lookups and `watch()` resolve | none |
| Attached | Its widget is attached to the page | Focus, measure, start polling | `onAttach()` |
| Detached | Its widget leaves the page | Keep state; stop polling | `onDetach()` |
| Disposed | Its owner disposes it, or its owner is disposed | Nothing; callbacks wrapped by `guard()` are dropped | clean-ups run once |

Attached and Detached alternate any number of times. Attach and detach come from GWT's own attach events (`Widget.addAttachHandler`, i.e. `Widget.onLoad()`/`onUnload()`), replacing GWTP's `onReveal()`/`onHide()`, which we rarely drive through slots anyway.

### Rules

1. **Whoever shows it, owns it.** A component that puts another's widget on screen calls `own()` on it. A component made from a GIN provider is owned the moment it is made: `own(editorProvider.get())`.
2. **Roots are few and named.** The content area owns document tabs, the popup manager owns dialogs, and the application shell owns everything else. In a workbench story the story owns its components.
3. **Shared things are never owned.** GIN singletons (services, plugins, the shell) are not components. A singleton presenter must not be passed to `own()`; in development builds this is an error.
4. **Disposal is final.** A disposed component is never shown again; reopening a document makes new components.
5. **Unowned is a bug.** In development builds a registry counts live components with no owner, and the workbench shows the count, so leaks are found in stories rather than in production.

## Callbacks and data flow

A parent passes data down through constructors and setters and hears back through callbacks it supplies. A child never fires an event on the global bus to reach its parent, and a parent never listens on the bus for its own child.

### Inputs

- **Required collaborators** (services, the REST factory) come through the `@Inject` constructor.
- **Data** comes through plain methods named for what they do: `setFields(List<QueryField>)`, `edit(QueryField)`. There is no generic `read(docRef, doc, readOnly)`; read-only comes from context (next section).
- A component never reaches up to its parent; anything it needs is passed in or looked up in context.

### Outputs

- **One listener: a callback setter.** The owner is almost always the only listener, so a single field is enough:

```java
fieldList.setOnSelectionChange(this::showField);
fieldEditor.setOnChange(field -> {
    fields.replace(field);
    markDirty();
});
```

- **One-off results: a callback argument.** For dialogs and questions, the callback is passed to the call that starts it:

```java
own(fieldEditDialog.get()).edit(field, updated -> fieldList.replace(field, updated));
```

- **Several listeners: `Listeners<T>`.** A small owned list for the rare component with more than one listener. Each `add()` returns a registration that the listener's own component owns, so neither side can leak the other.
- Callback types are `Runnable`, `Consumer<T>` or a small named functional interface when a callback carries more than one value. No marker interfaces like `HasDirtyHandlers`.

### Dirty state

Dirty state stops being an event chain. Each editable child is given an `onChange` callback, and the document component turns changes into its own dirty flag. Deep trees that would otherwise pass the callback through several layers look up a `DirtyTracker` from context (see Context).

### What still uses the event bus

The bus is kept for messages that are genuinely app-wide, where the sender cannot know the receivers:

| Keep on the bus | Move to callbacks or context |
| --- | --- |
| A document was saved, renamed, moved or deleted | A child's value changed (`DirtyEvent`, `ValueChangeEvent` between presenters) |
| Explorer tree refresh, user preferences changed | A list's selection changed (to its owner) |
| Requests to app services: show an alert, open a document, show a popup | A dialog was confirmed or cancelled |
| Session and security changes | Handlers added with `addHandlerToSource` on a child presenter |

Bus handlers are always added through `listen()`, so they are removed with the component.

## Context

A value set on one component is visible to every component it owns, however deep, without being passed through each layer. A lookup walks up the ownership tree to the nearest provider; changeable values notify watchers, and each watch is a registration the watcher owns.

```java
public final class ContextKey<T> {
    public static <T> ContextKey<T> of(String name, T defaultValue);
}

public final class ContextValue<T> {
    T get();
    void set(T value);                       // notifies watchers if it changed
}

// The document provides
provide(Contexts.READ_ONLY, readOnly);       // readOnly is a ContextValue<Boolean>

// Any descendant reacts, now and whenever it changes
watch(Contexts.READ_ONLY, nameBox::setReadOnly);
```

### Standard keys

| Key | Type | Provided by | Replaces |
| --- | --- | --- | --- |
| `READ_ONLY` | `Boolean`, changeable | The document, from its permissions and lock state; a nested editor may force `true` for its subtree | The `readOnly` argument of `read()` and per-view `setReadOnly` calls |
| `TASK_MONITOR` | `TaskMonitorFactory` | The document tab or dialog that shows progress | `HasTaskMonitorFactory`, `setTaskMonitorFactory` chains, `instanceof` checks |
| `DOCUMENT` | `DocRef` | The document component | Passing `docRef` to children that only need it for REST calls or links |
| `DIRTY` | `DirtyTracker` | The document component | `DirtyEvent` chains from deep children |

Keys are few and documented in one class, `Contexts`. A feature may define its own key (for example the dashboard's current query) but must not use context for data a direct child could be given as an argument.

### Rules

1. **Read-only only narrows.** A provider below the document may set `READ_ONLY` to `true` for its subtree but cannot make a read-only document editable.
2. **Watch, don't poll.** Components react through `watch()`, which runs once immediately and again on every change, so a document locked or unlocked while open updates every field.
3. **Lookups need an owner.** Context resolves once a component is owned. Watches registered in the constructor are held until then and applied on adoption.
4. **Default values are safe.** `READ_ONLY` defaults to `true` and `TASK_MONITOR` to a no-op factory, so an unowned component in a test fails closed rather than editable.

## Interop during migration

Old presenters and new components share one ownership tree, because `MyPresenterWidget` gets a `ComponentNode` in Phase 1. Either can own the other, and every existing entry point (tabs, popups, GIN) keeps working while it is converted.

| Area | Today | During migration | End state |
| --- | --- | --- | --- |
| `MyPresenterWidget` | `PresenterWidget` subclass; `PresenterScope.register(this)` in the constructor; `dispose()` unbinds | Holds a `ComponentNode`; `own()`, `listen()`, `context()` available; `dispose()` delegates to the node, which also unbinds | Rebased on `Component`; GWTP superclass removed |
| Document tabs | `DocumentPlugin` makes the editor in `new PresenterScope().capture(...)`; `EntityCloseHandler` calls `disposeScopeOf` | `ContentManager` holds a root node per open tab and adopts the editor; closing the tab disposes it | Same, without `PresenterScope` |
| Tab bodies | `TabContentProvider` makes presenters in the captured scope; `LayerContainer` swaps them | Providers `own()` the presenters they create; `Component` implements `Layer`, so it can be a tab body | Tab providers become small components or plain factories |
| Dialogs | `PopupManager` binds on show and unbinds on hide; nobody disposes | The opener owns the dialog. `Dialog.showOnce(owner, component, ...)` owns it until hidden, then disposes it; reusable dialogs stay owned by the opener | Same |
| Read-only | `read(docRef, doc, readOnly)` → `onReadOnly` | `DocPresenter.read` also sets the document's `READ_ONLY` context value, so new components watch it while old views still get `onReadOnly` | `readOnly` argument removed |
| Task monitor | `setTaskMonitorFactory` chains | `ContentTabPanePresenter` and `PopupSupportImpl` provide `TASK_MONITOR` on the tab or dialog node; `MyPresenterWidget.createTaskMonitor()` falls back to context when nothing was set | `HasTaskMonitorFactory` removed |
| REST calls | `.taskMonitorFactory(this).exec()`; no lifecycle | `RestFactory` gains `.owner(component)`: the callback is dropped and the task monitor ended if the owner was disposed | Owner required for calls made by components |
| GIN | `bindPresenterWidget` for every presenter, plus a view binding | Components with no view interface are injected just in time (already done for ~45 presenters); `Provider<C>` plus `own()` | No module entry for components |
| Workbench | `ScreenHarness` builds a per-story ginjector; `addContent` unbinds on clean-up; `LeakProbe` disposes a `PresenterScope` | The story context is a root node; `LeakProbe` counts unowned and undisposed components | Same |

`MyPresenterWidget` lives in `stroom-core-client-widget/src/main/java/com/`, which needs human approval to edit. Phase 1 therefore starts by deciding whether to edit it in place or move it to a `stroom` package (a mechanical change to ~264 imports).

## Testing and workbench stories

The framework classes are tested with plain JUnit; components are tested as workbench stories, which already run in headless Chromium through Playwright with axe-core, screenshot diffs and a leak check.

### Framework tests (JUnit 5, AssertJ, Mockito)

`ComponentNode`, `ContextValue`, `Listeners` and `guard()` have no GWT dependencies, so they run as ordinary JVM tests, like `TestPresenterScope` today. Cases to cover:

- Disposal order: children newest first, then registrations, then clean-ups; each runs once.
- One failing clean-up does not stop the others; the first failure is rethrown.
- Context: nearest provider wins, defaults apply when unowned, watches made before adoption are applied on adoption, and `READ_ONLY` cannot be relaxed below a read-only provider.
- Adopting a disposed child, owning twice, and owning a singleton in development mode all fail loudly.
- `guard()` drops callbacks after disposal.

### Component stories

- **Root per story.** The story context becomes a root `ComponentNode`; `addContent` adopts the component and the story's clean-up disposes the root.
- **Inputs are arguments.** Because a component's inputs are constructor arguments, setters and context, a story sets them directly: `provide(READ_ONLY, true)` on the story root shows the read-only state with no fixture document.
- **Callbacks are spies.** Stories pass `context.fn("onChange")` as callbacks and assert on calls, replacing bus listeners in tests.
- **Every converted screen gets stories for:** default, read-only, disabled where relevant, a dialog's OK and Cancel paths, and close-while-loading (the REST fixture responds after disposal and nothing should run).

### Leak checks

`LeakProbe` changes from disposing a `PresenterScope` to asserting that, after the story root is disposed, the development registry reports no live components from that story and no handlers left on the story event bus. The full-app suite in `stroom-gwt-suite` keeps its open-close memory checks as the end-to-end guard.

## Phased implementation

A pilot, then six phases, each shippable on its own and each ending by deleting an old mechanism. The pilot proves the framework on a duplicated screen without changing existing code. Phases 1 and 2 then fix today's leak and read-only bugs before any screen is rewritten; Phase 3 is where the component and callback model becomes the way new screens are written.

### Roadmap

| Phase | Main work | Gate |
| --- | --- | --- |
| 0 · Pilot | Dictionary editor duplicated in a new package; framework classes and tests in `stroom.widget.component.client`; workbench stories mirror the original; old presenters embedded through an adapter; no existing code changed | Comparison recorded; go ahead with Phase 1 or revise the API |
| 1 · Ownership | `ComponentNode` in presenters; roots for tabs, popups and stories; `own()` replaces `PresenterScope` | `PresenterScope` deleted |
| 2 · Context | Read-only, task monitor and document context; documents provide, fields watch; folders and one doc type first | Read-only stories pass |
| 3 · Components and callbacks | `Dialog.showOnce`, REST owner; receive rules dialogs converted; authoring guide published | New screens use components |
| 4 · Dirty state and the bus | `DirtyEvent` chains to callbacks; `addHandlerToSource` retired; bus kept for app-wide events | No parent-child bus use |
| 5 · Migrate by area | Documents, dashboards, admin; `HasTaskMonitorFactory` retired; `onReveal`/`onHide` audited | Every presenter owned |
| 6 · Remove GWTP | Plugin registry for proxies; `PresenterWidget` base dropped; GIN or Dagger decision | No GWTP dependency |

### Phase 0: Pilot

Prove the framework in a separate package by duplicating the Dictionary editor, before any existing code changes. The original stays untouched, so the two can be compared side by side.

The Dictionary editor is chosen because it is a real document tab with three of its own tabs (Words, Imports, Effective words) and two shared ones (Documentation, Permissions). Its dirty state flows from every tab, read-only must reach every tab, it makes REST calls with a task monitor, and it embeds old presenters we would not rewrite (the Ace `EditorPresenter`, the markdown editor, the permissions tab). It already has 7 stories in `DictionaryEditorStories`.

1. Build the framework in a new package, `stroom.widget.component.client` in `stroom-core-client-widget`: `ComponentNode`, `Component`, `ContextKey`, `ContextValue`, `Contexts`, `Listeners` and `guard()`, with JUnit tests.
2. Add `PresenterAdapter`, which puts an existing `MyPresenterWidget` into the ownership tree through its public `dispose()` and passes it read-only and the task monitor the old way. No file in the protected `com/` directory is edited.
3. Duplicate the editor in `stroom.dictionary.client.component`: Words, Imports and Effective words as components; Documentation, Permissions and the Ace editor embedded through the adapter.
4. Wire it into the workbench only, with each story as the root. Mirror the 7 existing stories and add read-only, lock change while open, and close while loading.
5. Record the comparison below in this document.
6. Optionally, open the pilot in the real app behind a development-only switch to test the `ContentManager` and `PopupManager` roots, which the workbench does not exercise.

| Measure | How it is measured | Pass if |
| --- | --- | --- |
| Size | Files and lines for the screen, excluding shared framework classes | Fewer than the original |
| Manual wiring | Hand-written read-only, task monitor and dirty plumbing in the screen | None for read-only and task monitor; dirty only through callbacks |
| Behaviour | Old and new stories run side by side with screenshot diffs and axe-core | Same results as the original |
| Leaks | Live components and story bus handlers after the story root is disposed | Zero |
| Late results | REST fixture answers after close | No callback runs |
| Story effort | Lines needed to write a new story for the screen | Fewer than the original |

**Exit:** the comparison is recorded and we decide to go ahead with Phase 1 or revise the API first. The duplicate then either replaces the original or is deleted; both are never maintained.

### Phase 1: Ownership

Give every presenter an owner without changing how screens are written.

1. Decide where `MyPresenterWidget` lives (edit in the protected `com/` directory, or move it to a `stroom` package).
2. Adopt the pilot's framework classes, revised by what the pilot found.
3. Give `MyPresenterWidget` a node; route `dispose()`, `addHandlerToSource` registrations and `onDispose()` through it; add `own()` and `listen()`.
4. Make roots explicit: a node per open tab in `ContentManager`, per dialog in `PopupManager`, per story in the workbench.
5. Reimplement `PresenterScope` as a thin wrapper over a root node, then replace its call sites (`inScope`, `captureIn`, `TabContentProvider`, `DashboardPlugin`, `AnnotationEditSupport`) with explicit `own()`.
6. Audit GIN singletons and eager singletons (23 `asEagerSingleton`) so none is ever owned.

**Exit:** `PresenterScope` deleted; open-close leak stories for documents, dashboards and annotations report zero live components.

### Phase 2: Context

Fix read-only and task-monitor threading once, centrally.

1. Add `Contexts.READ_ONLY`, `TASK_MONITOR` and `DOCUMENT`.
2. `DocPresenter.read` sets `READ_ONLY` on the document's node; `ContentTabPanePresenter` and `PopupSupportImpl` provide `TASK_MONITOR`.
3. `MyPresenterWidget.createTaskMonitor()` falls back to context.
4. Start with the folder presenters and one `DocTabPresenter` type: views `watch(READ_ONLY)` instead of implementing `onReadOnly`, and `setTaskMonitorFactory` calls are deleted.

**Exit:** read-only and lock-change stories pass for those screens; the pattern is documented for Phase 5.

### Phase 3: Components and callbacks

The new authoring model goes live.

1. Add `Dialog.showOnce` and `RestFactory.owner(...)`; `Component`, `Listeners` and `guard()` already exist from the pilot.
2. Convert the receive-rules dialogs (`FieldEditPresenter`, `RulePresenter`, `RuleSetSettingsPresenter`) and the folder tabs to components, with full stories.
3. Compare before and after: files, lines and behaviour per screen, recorded in this document.
4. Write a one-page authoring guide (how to make a screen, a dialog, a tab body) and add it to `AGENTS.md`.

**Exit:** first conversions shipped; from here, new screens are written as components.

### Phase 4: Dirty state and the bus

Remove parent-child bus traffic.

1. Add `DirtyTracker` and the `DIRTY` context key; `DocTabPresenter` provides it.
2. Replace `DirtyEvent` re-firing in `TabContentProvider`, `DocTabProvider` and `AbstractTabProvider` with callbacks or `DIRTY`.
3. Replace each `addHandlerToSource` (64 sites) with a callback setter on the child; keep `listen()` for genuinely app-wide events.

**Exit:** no `addHandlerToSource` or `DirtyEvent`; `HasDirtyHandlers` deleted.

### Phase 5: Migrate by area

Convert the remaining presenters area by area, each a separate pull request with stories.

1. Order by value: document editors (27 `DocTabPresenter`, 42 `DocPresenter`), then dialogs (161 popup sites), then dashboards, then admin screens.
2. Within each area, retire `onReadOnly`, `setTaskMonitorFactory` and `HasTaskMonitorFactory` as their last users go.
3. Review each `onReveal`/`onHide` override (24) against attach and detach as its presenter is converted.

**Exit:** every presenter is owned; `HasTaskMonitorFactory`, `ReadOnlyChangeHandler` and the `readOnly` argument are gone.

### Phase 6: Remove GWTP

1. Replace the 26 proxy presenters with a plain plugin and content registry; decide whether code splitting is still needed.
2. Rebase `MyPresenterWidget` on `Component` and drop `PresenterWidget`, `HandlerContainerImpl` and slots.
3. Decide whether GIN stays or Dagger 2 replaces it.

**Exit:** no GWTP dependency in the build.

## Risks and open questions

The main risk is a long period with two styles side by side; each phase therefore ends with something removed, not only something added.

### Risks

| Risk | Effect | Mitigation |
| --- | --- | --- |
| Lifecycle behaviour moves during migration | Handlers fire after close, data reloads on tab switch, focus lands in the wrong place | Convert one area at a time; workbench stories and screenshot baselines for every converted screen; leak counter in development builds |
| A shared (singleton) presenter gets owned | Closing one document breaks it for every other | Development-build check in `own()`; list of known singletons reviewed in Phase 1 |
| Two styles for a long time | Authors unsure which to use; reviewers check both | New screens must use components from Phase 3; a short authoring guide; each phase retires an old API |
| In-house framework to maintain | Knowledge held by few people | Keep the API to the classes in this document; full Javadoc and tests; no general-purpose features |
| GIN is also unmaintained | Generator-based injection blocks any future J2CL move | Components use only constructor injection, which Dagger 2 also supports; the GIN decision stays separate |
| Attach-based lifecycle differs from reveal | Code relying on `onReveal()` ordering behaves differently | Audit `onReveal`/`onHide` overrides when their presenter is converted, not in bulk |

### Open questions

- [ ] Package and module for `ComponentNode`, `Component` and `Contexts`: `stroom-core-client-widget` (`stroom.widget.component.client`) is the proposal.
- [ ] Do we keep a view interface for components with complex UiBinder layouts, or let the component own its UiBinder template directly?
- [ ] Should `RestFactory` take an owner so in-flight requests are cancelled on dispose, or is dropping late callbacks with `guard()` enough?
- [ ] Where do proxy presenters (code-split plugins) go once GWTP is removed: a plain plugin registry, and do we still need code splitting?
- [ ] Is GIN replaced by Dagger 2 as part of Phase 6 or left in place?
