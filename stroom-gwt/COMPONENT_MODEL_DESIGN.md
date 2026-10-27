# Component and Callbacks Model for the Stroom UI

Draft, 9 October 2026. This file is the design of record. It began as an export of an earlier
draft (https://claude.ai/code/artifact/e93329dc-1546-4557-9591-79a6246f84a3), which is no longer
kept up to date.

## Summary

Replace GWTP's presenter lifecycle with an in-house model of dumb **components** (a UiBinder template and its Java) wired by **controllers**, in which a controller owns the controllers it shows, and a parent and its children talk through **callbacks** rather than the event bus (see [Components, controllers and models](#components-controllers-and-models)). Ownership, clean-up and cross-cutting state (read-only, task monitor) become structural, so authors stop wiring them by hand. A pilot in a separate package first duplicates one screen (the Dictionary editor) to prove the framework against the original. The change is then delivered inside our own `MyPresenterWidget` in phases, so the ~250 existing presenters keep working throughout and GWTP is removed last.

**Goals**

- Nothing a controller registers or creates outlives it; `PresenterScope` is retired.
- Inputs go in through constructors and setters; outputs come back through callbacks. The event bus carries only app-wide events.
- Read-only, the task monitor and the current document are set once and read by any descendant.
- A new screen needs fewer pieces: no GIN module entry, and a separate view interface only when it earns its keep.
- Every screen can be shown in a workbench story without application scaffolding.
- Old presenters and new components and controllers embed each other freely during the migration.

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

## Widget rules

Written 24 October 2026, at the start of wave 1 (`COMPONENT_MIGRATION_ORDER.md`). Every dumb widget, from the smallest leaf up, meets these, so that any template can declare it and any controller can drive it the same way:

1. **Template-usable.** A public no-arg constructor (or a `@UiConstructor` whose arguments are template attributes), and its own `Binder` made with `GWT.create`, never injected. Settings a template needs are setters UiBinder can call: `boolean`, `int`, `double`, `String` or an enum (UiBinder has no `long` parser, so a `long` setting also gets an `int` setter of its own name).
2. **Outputs are callbacks.** One per event, set by its owner: `setOnClick(Runnable)`, `setOnChange(Consumer<T>)`. Where several may listen, a `Listeners<T>` list and `add...Listener(Consumer<T>)` returning a registration. A widely used widget keeps its GWT event (`ClickHandler`, `ValueChangeEvent`) beside the callback until its callers have moved, then it goes.
3. **One read-only setter.** `setReadOnly(boolean)` means "shown, but can't be changed" (a dumb `Component` gets it from the base class, applied in `onReadOnlyChange`). `setEnabled(boolean)` stays only where it means something else (e.g. a mode not in use).
4. **Focus** is `focus()`, not `setFocus(boolean)`.
5. **Attributes through typed setters.** A template sets ARIA and the like with setters on the widget (`ariaLabel`, `placeholder`, `opensDialog`), so nothing is set by hand after the template is made. A plain text box is a `TextBox` (`stroom.widget.form.client`: `type`, `placeholder`, `role`, `ariaLabel`, `ariaHasPopup`, `ariaExpanded`) and a plain panel an `AriaPanel` (`stroom.widget.component.client`: `role`, `tabIndex`, `ariaLabel`, `ariaHasPopup`, `ariaHidden`, `ariaLive`), both added in wave 2 (a plain text area is a `TextArea`, `placeholder` and `ariaLabel`, added in wave 9); an element that needs no widget is plain HTML in a `g:HTMLPanel`, where any attribute can be written.
6. **Markup in the template**, except the root of a single-element widget (a `ButtonBase`, or a `FlowPanel` or `Widget` subclass), whose block class and role are set in its constructor; a `PopupPanel`'s root; and cells, which use `SafeHtmlTemplates`.
7. **No inline styles.** Fixed styles are Stroom CSS classes (BEM, in the block's own stylesheet under `stroom-app/src/main/resources/ui/css`); a runtime value is a custom property set with `CssProperties.set(element, "--block__property", value)` that a class uses. `setVisible` is fine.
8. **Nothing smart.** No REST, event bus, context lookups or GIN-injected services, and nothing it owns: a widget that needs any of these is split into a box and a controller (as `DropDownBox` and `DocSelectionController`).
9. **Stroom's widgets only.** No GWT `Button` (`com.google.gwt.user.client.ui.Button`), in the app or the workbench's stories: a button is Stroom's `Button`, `ToolbarButton` (in a `Toolbar`), `LinkButton` or `FabButton`. The theme still styles GWT's (`standard_mod.css`, the `--button__*` theme colours) for the three units that use one until their waves (UserTask, wave 3; the floor map's layers, wave 7; the explorer's logo and activity button, wave 15). (Added 24 October 2026.)
10. **State as setters, not task monitors.** A widget that shows work in progress (a refresh button's spin) has `setRefreshing(boolean)`; whatever owns it turns its task monitor into those calls.
11. **Its own stylesheet, BEM.** Each component (or widget) has its own stylesheet under `stroom-app/src/main/resources/ui/css/`, named for its block in lower-case kebab case (`selection-box.css` for the `selection-box` block, imported from `app.css`), holding only that block's rules. Its classes are BEM: `block`, `block__element`, `block--modifier`, all lower-case kebab case (`selection-box__value`, `selection-box--read-only`), and its custom properties `--block__property`. What is shared stays in the shared stylesheets: the common classes many blocks use (`max`, `form-control-border`, `allow-focus`, `sr-only`, the `dock-*` and `w-*` layout helpers), the theme's variables (`--control__border-color` and the palettes) and the base element rules. A block's rules move out of `components.css`, `stroom.css` and other shared files into its own file when it converts, and its old class names (`expanderCell`, `mainViewImpl-*`, `Button--base`) are renamed to BEM with every use (Java, templates, `SafeHtmlTemplates`, other stylesheets and the workbench's stories) in the same commit. (Added 24 October 2026, after wave 1.)
12. **Named for what it is.** A widget's name says what it is, not how it was made (`TickBox`, not `CustomCheckBox`; `IconBox`, not `SvgIconBox`; `Histogram`, not `HistogramWidget`), and its BEM block is the same name in kebab case. Renamed after wave 2: `CustomCheckBox` to `TickBox`, `CustomDatePicker` to `Calendar`, `SvgIconBox` to `IconBox`, `HistogramWidget` to `Histogram`, `DaysWidget` to `DaysOfWeek`, `TimeZoneWidget` to `TimeZoneBox` and `TraceHistogramWidget` to `TraceHistogramChart` (`TraceHistogram` is its data). Later waves rename theirs: `SvgCell`, which wave 5 made `IconCell`, and `CustomFileUpload`, which wave 4 made `FileChooser` (GWT's `FileUpload` is taken); `MySingleSelectionModel` when its users convert; `SvgIconCell`, `SvgStringCell` and `MyTreeLayout` look unused, so are deleted once removals are allowed. (Added 24 October 2026.)

## Controllers and ownership

A controller (`stroom.widget.component.client.Controller`) is the smart half of a screen: it is given its component, wires it to data and behaviour, and holds an ownership node: it owns child controllers, registrations and clean-ups, and disposing it releases all of them, depth first. Visibility (attached or detached) is separate from ownership, so a hidden tab keeps its state until its owner disposes it. Every presenter is a controller too (`MyPresenterWidget extends Controller`) until the presenters go. A component (`stroom.widget.component.client.Component`, a `Composite`) has none of this: its handlers go with its widgets.

### The ownership node

Ownership lives in a small class, `ControllerNode`, rather than in the base class, so presenters and controllers sit in the same tree.

```java
public final class ControllerNode {
    ControllerNode parent();
    void adopt(ControllerNode child);       // child.parent = this; disposed with this
    void release(ControllerNode child);     // dispose one child early (e.g. a replaced tab)
    void add(HandlerRegistration registration);
    void onDispose(Runnable cleanUp);       // not add(Runnable): a lambda would match both
    <T> T lookup(ContextKey<T> key);        // nearest ancestor that provides the key
    <T> void provide(ContextKey<T> key, ContextValue<T> value);
    <T> HandlerRegistration watch(ContextKey<T> key, Consumer<T> onChange);
    <T> Consumer<T> guard(Consumer<T> callback);
    void dispose();                         // children (newest first), then watches, registrations, clean-ups
    boolean isDisposed();
}
```

`ControllerNode.root(name)` makes a root. A node is *connected* when it is a root or its owner is connected; watches report only once their node is connected, and are resolved again when a provider is added above them, so a controller can watch in its constructor and a document can provide after owning its children.

### The Controller base class

```java
public abstract class Controller implements IsWidget, HasControllerNode, HasHandlers {

    // Its widget: its component (initWidget(view)); a controller attached to a part has none
    protected final void initWidget(Widget widget);

    // Making and owning
    protected final <C extends HasControllerNode> C make(Provider<C> provider);         // made now, owned until this is disposed
    protected final <P extends MyPresenterWidget<?>> P makeDialog(Provider<P> provider); // owned until hidden
    protected final <C extends HasControllerNode> C own(C child);                        // one injected into the constructor
    protected final void release(HasControllerNode child);                               // one replaced early

    // Registrations, all undone on dispose
    protected final <H> void listen(Type<H> type, H handler);        // app-wide event bus
    protected final void own(HandlerRegistration registration);      // e.g. a child's listener
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

The plugins, managers and app-wide helpers, which are GIN singletons and not controllers, make their dialogs with `AppRoot.makeDialog(provider)`, a root that lasts as long as the page.

### Lifecycle

| State | Entered when | Allowed | Hooks |
| --- | --- | --- | --- |
| Created | Constructor runs | Fill its component, `make()` and `own()` children, set callbacks | none |
| Owned | An owner calls `make()` or `own()` | Context lookups and `watch()` resolve | none |
| Attached | Its widget is attached to the page | Focus, measure, start polling | `onAttach()` |
| Detached | Its widget leaves the page | Keep state; stop polling | `onDetach()` |
| Disposed | Its owner disposes it, or its owner is disposed | Nothing; callbacks wrapped by `guard()`, and REST replies for it (`RestFactory.owner`), are dropped | clean-ups run once |

Attached and Detached alternate any number of times. Attach and detach come from GWT's own attach events (`Widget.addAttachHandler`), replacing GWTP's `onReveal()`/`onHide()`.

### Rules

1. **Whoever shows it, owns it.** A controller that shows another makes it with `make(provider)` (or `makeDialog(provider)` for a dialog), or owns one injected into its constructor with `own()`.
2. **Roots are few and named.** The content area owns document tabs (a root per tab), `AppRoot` owns the app-wide helpers' dialogs, and in a workbench story the story's root owns what it shows.
3. **Shared things are never owned.** GIN singletons (services, plugins, the shell) are not controllers.
4. **Disposal is final.** A disposed controller is never shown again; reopening a document makes new ones.
5. **Unowned is a bug.** In development builds a registry counts live controllers with no owner (`ControllerChecks`), and the workbench leak check names them, so leaks are found in stories rather than in production.

## Callbacks and data flow

A parent passes data down through constructors and setters and hears back through callbacks it supplies. A child never fires an event on the global bus to reach its parent, and a parent never listens on the bus for its own child. The same holds between a controller and its component, and between a controller and the controllers it owns.

### Inputs

- **Required collaborators** (services, the REST factory) come through the `@Inject` constructor.
- **Data** comes through plain methods named for what they do: `setFields(List<QueryField>)`, `edit(QueryField)`. There is no generic `read(docRef, doc, readOnly)`; read-only comes from context (next section).
- A component never reaches up to anything; a controller looks up what it needs in context and passes it on.

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
makeDialog(fieldEditorProvider).edit(this, "Edit Field", field, otherNames, updated -> fieldList.replace(field, updated));
```

- **Several listeners: `Listeners<T>`.** A small list for the rare component or controller with more than one listener. Each `add()` returns a registration that the listener's own controller owns, so neither side can leak the other.
- Callback types are `Runnable`, `Consumer<T>` or a small named functional interface when a callback carries more than one value. No marker interfaces like `HasDirtyHandlers`.

### Dirty state

Dirty state stops being an event chain. Each editable child is given an `onChange` callback, and the document's controller turns changes into its own dirty flag. Deep trees that would otherwise pass the callback through several layers look up a `DirtyTracker` from context (see Context).

### What still uses the event bus

The bus is kept for messages that are genuinely app-wide, where the sender cannot know the receivers:

| Keep on the bus | Move to callbacks or context |
| --- | --- |
| A document was saved, renamed, moved or deleted | A child's value changed (`DirtyEvent`, `ValueChangeEvent` between presenters) |
| Explorer tree refresh, user preferences changed | A list's selection changed (to its owner) |
| Requests to app services: show an alert, open a document, show a popup | A dialog was confirmed or cancelled |
| Session and security changes | Handlers added with `addHandlerToSource` on a child presenter |

Bus handlers are always added through `listen()`, so they are removed with the controller.

## Context

A value set on one controller is visible to every controller it owns, however deep, without being passed through each layer; a controller pushes what its component needs into it (`view.setReadOnly(readOnly)`). A lookup walks up the ownership tree to the nearest provider; changeable values notify watchers, and each watch is a registration the watcher owns.

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

// Any descendant controller reacts, now and whenever it changes, and tells its component
watch(Contexts.READ_ONLY, view::setReadOnly);
```

### Standard keys

| Key | Type | Provided by | Replaces |
| --- | --- | --- | --- |
| `READ_ONLY` | `Boolean`, changeable | The document, from its permissions and lock state; a nested editor may force `true` for its subtree | The `readOnly` argument of `read()` and per-view `setReadOnly` calls |
| `TASK_MONITOR` | `TaskMonitorFactory` | The document tab or dialog that shows progress | `HasTaskMonitorFactory`, `setTaskMonitorFactory` chains, `instanceof` checks |
| `DOCUMENT` | `DocRef` | The document's controller | Passing `docRef` to children that only need it for REST calls or links |
| `DIRTY` | `DirtyTracker` | The document's controller | `DirtyEvent` chains from deep children |

Keys are few and documented in one class, `Contexts`. A feature may define its own key (for example the dashboard's current query) but must not use context for data a direct child could be given as an argument.

### Rules

1. **Read-only only narrows.** A provider below the document may set `READ_ONLY` to `true` for its subtree but cannot make a read-only document editable.
2. **Watch, don't poll.** Controllers react through `watch()`, which runs once immediately and again on every change, so a document locked or unlocked while open updates every field.
3. **Lookups need an owner.** Context resolves once a controller is owned. Watches registered in the constructor are held until then and applied on adoption.
4. **Default values are safe.** `READ_ONLY` defaults to `true` and `TASK_MONITOR` to a no-op factory, so an unowned controller in a test fails closed rather than editable.

## Lifetimes

Most of what a screen makes needs no clean-up at all, and what does should be released by something that always happens anyway (a widget leaving the page, a tab closing) rather than by a `dispose()` call an author has to remember. Clean-up is only needed when something long-lived (the event bus, `window`, `document`, a timer, a singleton) holds on to an object, so each thing a screen makes falls into one of three cases.

| Lifetime | Mechanism | Who has to remember |
| --- | --- | --- |
| Lives and dies with its widget | Listeners only on its own elements; garbage collected with its owner | Nobody |
| Needs something long-lived while shown | `AttachScope`: registered on attach, removed on detach | Nobody: GWT always attaches and detaches |
| Must react while hidden | `listen()` and `own()` on the component; only roots are disposed | Only the roots (tab, dialog, story) |

### 1. Self-contained: no clean-up

**Rule: a widget only listens to itself and its own children.** Its handlers, DOM listeners and observers are then a closed group of references (widget → handler → owner → widget), which the garbage collector takes together once nothing outside refers to any of them.

- Click handlers live in the widget's own handler manager, not on the event bus. GWT relies on this: UiBinder's generated code for `@UiHandler` (`HandlerEvaluator`) calls `addClickHandler` and discards the registration, and nothing ever removes it.
- `Widget.onDetach()` unhooks the element from the widget (`DOM.setEventListener(element, null)`) but keeps the handlers, so a hidden tab or a reopened dialog works again without rebinding.
- DOM listeners and a `MutationObserver` on the widget's own elements are collected with them (a node holds its observer; the observer holds its callback).
- A widget in this case has no `dispose()`. Clicks after its owner is disposed can't happen, as a disposed tab or dialog has left the page.

### 2. Attach-scoped: `AttachScope`

A widget that needs something long-lived (a `window` resize listener, a global key handler, a polling timer, an app-wide event that only matters while it is shown) registers it while it is on the page:

```java
public final class AttachScope {
    public AttachScope(Widget widget);
    public void listen(Supplier<HandlerRegistration> register);   // e.g. () -> Window.addResizeHandler(...)
    public void every(int periodMillis, Runnable task);           // a timer
    public void whileAttached(Supplier<Runnable> start);          // start returns its own stop
}

// In the widget or component
private final AttachScope attached = new AttachScope(this);
...
attached.listen(() -> Window.addResizeHandler(event -> layout()));
attached.every(5000, this::poll);
```

- Each registration is made when the widget is attached (at once if it already is), removed when it is detached and made again if it returns.
- The scope watches attach and detach with `Widget.addAttachHandler`, a handler on the widget itself, so the scope is case 1 and needs no clean-up either.
- Detached widgets stop listening and polling, as the lifecycle above wants ("Detached: keep state; stop polling"). A widget that may miss changes while hidden refreshes when it is attached again.

### 3. Owned: must react while hidden

A few things must keep reacting while their widget is detached, for example a hidden document tab hearing that its document was renamed. These are registered on the component, with `listen()`, `own()` and `onDispose()`, so a registration cannot be made without an owner, and only the roots are ever disposed: the content area when a tab closes, the popup manager when a dialog is finished and the story when it ends. Disposal cascades down the ownership tree, so screen authors never call `dispose()`.

Until Phase 1 these are `registerHandler()` and `addHandlerToSource()` on `MyPresenterWidget`, released by `PresenterScope`.

### Choosing

Use the first case that fits: self-contained if the widget can be; attach-scoped if it only needs the outside world while shown; owned only if it must react while hidden. A leak story can check the first two by showing and closing a screen and finding no listeners left on `window`, `document` or the story's event bus.

## Toolbars

Toolbars are the first part of the UI to be rebuilt on these principles. They are self-contained (lifetime case 1), take inputs and give callbacks, and need nothing from `ComponentNode`, so they can be done before the pilot.

### Keyboard behaviour

Buttons that sit next to each other are one group and one tab stop, the WAI-ARIA toolbar pattern; buttons apart on a row (e.g. at either end) are separate groups. `ButtonGroupFocus` (in `stroom-core-client-widget`, used by `Toolbar`) gives a group:

- a roving tab index: one button has `tabindex="0"`, the others `-1`, kept as buttons are added, shown, hidden or attached;
- Left and Right (Up and Down for a vertical group), wrapping at the ends, and Home and End; Tab returns to the button last used;
- disabled (`aria-disabled`) buttons that can still be reached; hidden buttons skipped;
- `role="toolbar"`, named, when it has two or more buttons; a single button is left as a plain button;
- if the focused button is hidden or removed, the focus moves to the button now in its place (unless code has moved it on purpose).

`FocusTooltip` shows an icon button's name beside it when it has the keyboard focus, as browsers only show a `title` tooltip on hover; one tooltip serves the page, installed by the button classes, and is hidden on blur, Escape, scrolling and mouse presses.

The workbench measures this: `ButtonRuns` reports the runs of adjacent image buttons and the tab stops each costs, and `TabStops` the tab stops of a region in order (stories `Widgets/Buttons/ButtonGroup` and the `ToolbarTabStops` stories). See "Toolbars are a tab stop per button" in `ISSUES.md`.

### Problems with building toolbars today

| Problem | How it happens | Scale (approx.) |
| --- | --- | --- |
| Pass-through methods | `addButton(Preset)`, `addButton(ButtonView)` and `addToggleButton(...)` re-declared on view interfaces and presenters so callers can reach a `ButtonPanel` (`PagerView`, `PagerViewWithHeading`, `QueryView.addButtonLeft/Right`, `EditExpressionView`, ...) | ~265 `addButton` calls in 99 files |
| Presets | `SvgPresets` bundles an icon, a name and a default enabled state (`ADD` enabled, `EDIT` and `DELETE` disabled); names are overridden with `.title(...)`; icons reused under other names | 299 references; 69 `.title(...)` overrides |
| Clicks wired separately | Buttons kept in fields and wired with `registerHandler(button.addClickHandler(...))` | Most toolbars |
| Toolbars merged by hand | `HasToolbar.getToolbars()` returns widgets that `DocTabPresenter` adds beside its Save buttons, so adjacent buttons are separate groups | 11 implementers |
| Two toggle types | `SvgToggleButton` swaps between two preset icons and has no accessible name; `InlineSvgToggleButton` has `aria-pressed` | 8 `addToggleButton` calls |
| Groups not built as groups | The explorer header, pagers, main toolbar and data navigators are plain `FlowPanel`s | ~10 places |

### API

A `Toolbar` is one named group. Buttons are made from an `SvgImage` and a name, both always given, and start enabled.

```java
final Toolbar toolbar = new Toolbar("Dictionary actions");
final ToolbarButton save = toolbar.button(SvgImage.SAVE, "Save", this::save);
toolbar.button(SvgImage.SAVEAS, "Save As", this::saveAs);
final ToolbarToggleButton filters = toolbar.toggle(SvgImage.FILTER, "Show Column Filters", this::setFiltering);
toolbar.separator();                  // a visual divider, still the same group
save.setEnabled(dirty);
```

- **Its own buttons.** A toolbar's buttons are `ToolbarButton` and `ToolbarToggleButton`: a `<button class="toolbar-button">` holding its icon (`toolbar-button__icon`), styled only by `toolbar-button.css`. They replaced the older icon buttons (`InlineSvgButton` and its toggle, whose `icon-button` and `face` markup and rules are gone), looking the same in every state (`Widgets/Buttons/ToolbarButtonStates`). They are real buttons: Enter and Space press them natively.
- **One callback per button.** `ToolbarButton.setOnClick(Runnable)` and `ToolbarToggleButton.setOnToggle(Consumer<Boolean>)`: a single field, set when the button is made, replaced rather than added to, so there is no registration to keep or remove.
- **One toggle type.** A toggle is one icon with a pressed state (`aria-pressed`, its only record of the state), named like any other button. `SvgToggleButton` is retired.
- **No presets.** `Toolbar` takes no `Preset`, so the icon and the name a screen reader announces are visible where the button is made.
- **A name for every group.** `Toolbar`'s constructor requires the name that is announced with `role="toolbar"`.
- **Groups apart on a row are a `ToolbarRow`.** A row lays out named toolbars with space between them, e.g. a list's actions at the start and its pager at the end, each its own tab stop.

### Display preference (later)

Toolbars are icon only for now: every button shows its icon, and its name is its tooltip (shown on hover and, by `FocusTooltip`, on keyboard focus) and what a screen reader announces. Some users would find text easier, so a user preference should later choose how toolbars are shown: icons only, text only, or icons and text. Each `ToolbarButton` already has the text (its name), so this needs only:

- a label element in the button (`toolbar-button__label`), holding its name, hidden while icons only;
- the preference applied as a class on the page, as the theme and density preferences already are (`ThemeCssUtil.getCurrentPreferenceClasses`), e.g. `toolbar-display--text`, which `toolbar.css` reads to show the label, the icon or both;
- wider buttons when they show text, so `target-size` and the arrow keys are unaffected.

Buttons that act on one piece of content and are labelled (e.g. the Copy button under each chat reply) are not toolbars: they stay ordinary buttons, a tab stop each.

### UiBinder

A toolbar can be declared in a template. `@UiConstructor Toolbar(String name)` makes `name` a required attribute, so an unnamed toolbar fails the GWT compile.

```xml
<b:ToolbarRow>
    <b:Toolbar ui:field="actions" name="Dictionary actions">
        <b:ToolbarButton ui:field="save" svg="SAVE" title="Save"/>
        <b:ToolbarButton ui:field="saveAs" svg="SAVEAS" title="Save As"/>
    </b:Toolbar>
    <b:Toolbar name="Paging">
        <b:ToolbarButton ui:field="first" svg="FAST_BACKWARD" title="First"/>
    </b:Toolbar>
</b:ToolbarRow>
```

- `svg="SAVE"` sets the icon: UiBinder sets the enum through `ToolbarButton.setSvg(SvgImage)` (story `Widgets/Buttons/Toolbar` `Template`). Until now icons were set in Java even when the title was in the template.
- The template declares the layout; behaviour is wired in Java with `save.setOnClick(this::save)`, so toolbars made in templates and in code read the same. `@UiHandler` also works (its lifetime is the widget's, case 1), but cannot be replaced or cleared.
- Toolbars whose buttons depend on permissions, the document type or contributions are made in Java; both end up as the same `Toolbar`.

### Contributions

- Done: `HasToolbar.getDocumentButtons()` gives the groups of buttons that join the document tab's toolbar after Save and Save As (nested in it, so they are one group and one tab stop), and `HasToolbar.getToolbars()` the controls apart from them on the row (e.g. a query's time range and Execute button at its end). They are `Toolbar`s; when their buttons are made in the document's toolbar itself (`addTo(Toolbar)`), the transitional `.toolbar > .toolbar` rule in `toolbar.css` can be removed.
- Done: `PagerView` (now `PagerPanel`) exposes its actions `Toolbar` once (`getToolbar()`), and the list presenters' and views' pass-through methods are `getToolbar()` too. `PagerView.addButton(ButtonView)` stayed for the button widgets built on `ToolbarButton` (e.g. table expand and collapse) until they can be made in a toolbar.

### Lifetime

A toolbar is self-contained (case 1): its callbacks are on its buttons and `ButtonGroupFocus`'s listeners and observer are on its own element, so it has no `dispose()` and needs no owner. A story checks that a closed toolbar leaves nothing on `window`, `document` or the event bus.

### Migration

1. Done: `Toolbar`, `ToolbarRow`, `ToolbarSeparator`, `ToolbarButton` and `ToolbarToggleButton` (with `setOnClick` and `setOnToggle`), styled by `toolbar.css` alone, with stories for the Java and UiBinder forms (`Widgets/Buttons/Toolbar`). `ButtonPanel`'s `Preset` methods and `SvgToggleButton` are deprecated; existing toolbars keep working, already with the keyboard behaviour. `InlineSvgToggleButton` now says it is a toggle (`aria-pressed="false"`) before it is first pressed.
2. Convert the Dictionary editor first, the pilot's screen, using its `ToolbarTabStops` numbers as the before and after.
3. Done: convert by area: the plain `FlowPanel` groups (explorer header, pagers, main toolbar, data navigators), `HasToolbar` and the document tab, then the remaining `addButton` callers, removing pass-throughs as they go. `SvgButton` and `ButtonPanel` are deleted (`Toolbar.setVertical` replaces `ButtonPanel.setVertical`). A form group's help button is a form control of its own (`FormGroupHelpButton`), not a toolbar button.
4. Done: make the button widgets built on `ToolbarButton` in a toolbar: the quick filter's, the date picker's, `RefreshButton` (a `ToolbarButton`, made by `Toolbar.refresh`) and table expand and collapse (regular buttons, made by `GroupLevelButtons`; `PagerView.addButton(ButtonView)` is gone).
5. Done: replace `Preset` where cells and menus use it, then delete `Preset` and `SvgPresets`. Cells take a `CellIcon` (icon, name, enabled; `stroom.cell.info.client`), made where it is used (`CellIcon.of(SvgImage.LOCKED, "Locked Stream")`); menus and the plugins' icons take an `SvgImage`.

## Split panes

`SplitPane` (`stroom.widget.layout.client`, styled by `split-pane.css`) replaced GWT's split layout panels, Stroom's `ThinSplitLayoutPanel` and `MySplitLayoutPanel` (copies of one widget, with 1px and 7px splitters, used by about 40 views; now deleted). Like a toolbar it is self-contained (lifetime case 1): its listeners are on its own splitters, and its panes' resize observers are removed when it is detached.

- **A CSS grid, in reading order.** Its panes and splitters are its children in the order they are shown, so a screen reader and the Tab key meet the panes as they are laid out; a `DockLayoutPanel` adds its centre last, which needed `ReadingOrder` to put right. Panes split both ways nest one split pane in another.
- **Sizes.** Each pane is a share (`30%`, kept as the split pane is resized), a fixed size (`400px`) or the rest (`*`). One pane takes the rest (the last, if none is given `*`); every other pane must be given a size, and a split pane that lacks one, or has two taking the rest, fails when it is shown, naming the pane. A splitter resizes the pane on its side away from the one taking the rest, as a dock layout's did.
- **When the split pane is resized** (e.g. with the window), the pane taking the rest gives or takes the space until it is at its minimum; then the sized panes give up space, down to their minimums; and each grows back to its own size as the split pane widens (each sized pane's grid track is `minmax(min, size)`, the rest's `minmax(min, 1fr)`). Which pane absorbs a resize is chosen by which is `*`: the first (e.g. `*, 400px`, as `<g:east>`) or the second (`400px, *`, as `<g:north>`/`<g:west>`, most of Stroom's screens); a share (`30%`) changes with the split pane. The old panels kept a fixed pane at its size however small the panel became, squeezing the centre to nothing, and only kept to a minimum in a drag (the centre could have none): stories `Widgets/Layout/SplitPaneResizing` measure both at several sizes.
- **Keyboard and screen readers.** Each splitter is a focusable `role="separator"` (the WAI-ARIA window splitter pattern), named by the pane it resizes (so every pane a splitter resizes must be given a name, or the split pane fails when shown), with that pane's size as its value (a percentage of the split pane, said as e.g. "30% of the width") and `aria-controls` pointing at it. Arrow keys move it 10px (50px with Shift), Page Up and Page Down grow and shrink its pane by 50px, Home and End move it to its limits, and Enter collapses its pane and restores it. Movement follows the screen on a right-to-left page.
- **Pointer.** A mouse, finger or pen drags a splitter, with pointer capture, so a frame in a pane can't take the drag; the panes are laid out at most once a frame however fast the pointer moves. Double clicking a splitter collapses and restores its pane, as Enter does. A thin splitter is drawn as a 1px line but can be grabbed by 7px.
- **Maximise.** A splitter's menu (a right click or long press on it, or the context menu key or Shift+F10 when it has the focus) offers to maximise the pane either side of it, or to restore them, so a pane can be resized without dragging (WCAG 2.5.7). Alt+M (`KeyBinding.Action.MAXIMISE_PANE`, hinted in the menu) maximises the pane holding the focus, in the split pane nearest it, and restores it. A maximised pane takes all the space and the others none (their splitters stay, to drag them back); moving a splitter carries on from the panes as they are shown. The menu is Stroom's, asked for with `MenuRequest`, which bubbles up the page to the one relay `CorePresenter` installs (in Stroom and the standalone dashboard), as a widget has no event bus to fire `ShowMenuEvent` on; `ScreenHarness` and `StoryPopups.withPageRequests()` relay it in the workbench. Key bindings are still fixed in `KeyBinding`, so Alt+M will become a user preference with the rest.
- **Seen.** A splitter's keyboard focus (`--splitter__focus-color`, the splitter's full 7px, with the app's `--focus-shadow`) is at least 3:1 against the panes either side in both themes (WCAG 1.4.11), as the workbench measures beside Stroom's table and code editor. A thin splitter's line (`--splitter__line-color`) is drawn in the border colour (`--border-color`), as other borders are, as the old splitters' was (1.3:1 dark, 1:1 light); its contrast is to be revisited with the theme colours. Each split pane sets `--split-pane__splitter-colour`, so a nested one doesn't take its parent's, and `split-pane--navigation` draws it in the explorer's colours. In forced colours (Windows high contrast) splitters are drawn in system colours, where their backgrounds would otherwise be replaced by the page's.
- **Target size (WCAG 2.5.8).** A splitter is 7px wide, which passes when nothing else clickable is within 12px of its centre (`TargetSpacing` measures it). It doesn't where two splitters meet in a T at the centre of the one across (e.g. User Groups' 50/50 split): an accepted deviation (see `COMPONENTS.md`), pinned in the `Template` story.
- **Collapsed panes.** A pane collapsed to nothing is `inert`, so the focus can't go into it unseen (WCAG 2.4.7); restored, it can again.
- **Told of resizes.** `setOnResized` is told when the user has resized a pane (on letting go of a drag, a key, a double click, or maximising or restoring), not when the split pane itself is resized or sizes are set in code, e.g. for the main screen to save the explorer's width (which it now finds by overriding `onResize`).
- **Resizing.** A pane's widget fills it, and is told when its pane changes size (if it `RequiresResize`), by a resize observer on the pane; the split pane's own `onResize` doesn't pass the call on, so a widget isn't laid out twice.
- **Templates.** `sizes`, `names`, `minSizes`, `vertical` and `thin` are attributes, and the panes are the children, in order.
- **Thin or regular.** A thin splitter (a 1px line) is for panes that run to their edges with no frame of their own (grids, editors, `dashboard-panel`s), where the line is the only boundary between them. A regular splitter (a 7px gap) is for panes that are framed controls, each with its own border, in a padded form (e.g. dictionary settings' Imports and Effective Words, query history and favourites, Find in Content): a thin line there sits against the frame above it and reads as a doubled border.

A visible collapse button on each splitter was tried for 2.5.7 and 2.5.8 and dropped: wherever it went on a splitter, it sat over pane content (toolbars and pagers at pane corners, filters along their tops).

Stories `Widgets/Layout/SplitPaneContent` hold what Stroom's screens do: Stroom's data grid beside its code editor (each lays itself out again as the splitter moves), the stepping screen's four pane layout as nested split panes, forms in a pane that collapses, Alt+M (alone and in nested split panes), the splitter's menu (and from the keyboard, in the runner), double clicks and the resize call. Stories `Widgets/Layout/SplitPane`: the `Drag` stories set split panes up as Stroom's screens do, and the workbench runner drags them with the real mouse (a pane must follow the mouse to the pixel, and stop at its minimum); the others cover the keyboard, names, an end-anchored pane, a hidden pane, a right-to-left page, a drag across a frame, the navigation colour, resize notices, a missing size or name and a nested template; the runner checks the line and focus contrast in both themes, and forced colours.

Migration (done): User Groups first (which nests splits), with screenshot diffs before and after, then the rest by area, ending with the main screen. The old panels, their CSS (`thinSplitLayoutPanel-*`, `gwt-SplitLayoutPanel-*`), their stories and the comparison stories are deleted, as is `ReadingOrder`.

## Dashboard splitters

A dashboard's panels are laid out by `FlexLayout` (`stroom.dashboard.client.flexlayout`, styled by `flex-layout.css`), not `SplitPane`, and it keeps its own splitters (`Splitter`). Its panels and splitters are placed absolutely, from sizes `LayoutSizes` works out, and a drag shows a marker on a glass over the page and resizes the panels only when it is let go, as laying a dashboard out again (its tables and visualisations) is too slow to follow the pointer. A visualisation is drawn in a frame that floats over the layout (so it can be moved off screen and keep its state), and a splitter beside one must stay above it.

- **Tab order.** The panels and splitters are added to the page in the order they are laid out, depth first, each splitter after the last panel before it and before the first after it; the splitters that resize the whole dashboard (its width and height, at its right and bottom edges) come last. Splitters are tab stops in both modes, as resizing isn't a design-only change.
- **Keyboard and screen readers.** Each splitter is a focusable `role="separator"` (the WAI-ARIA window splitter pattern), with `aria-orientation` (`vertical` between panels side by side). It is named for the selected tabs of the panels either side of it ("Resize Query and Table"; "Resize the dashboard's width" or "height" for the outer ones), named again when it takes the focus, as tabs change. Its value is where it is between its limits, as a percentage of the split it divides ("40% of the width"). The arrow keys along it move it 10px (50px with Shift), and Home and End to its limits, each press resizing as a drag let go there does. Page Up and Down, Enter (collapse) and a menu, which `SplitPane` has, aren't offered: a panel's own menu maximises and restores it.
- **Focus kept.** Maximising, restoring and other changes rebuild the layout, which replaces its panels and splitters, and the focus went with them. `FocusMemory` notes the panel the focus was last in (by its selected tab), and whether it was on the splitter after it, and puts it back there after a rebuild if it was lost (not if something else, such as the design mode button, has it). A panel's menu that rebuilt the layout gives the focus back to the removed tab as it closes, so this waits while the focus is in a popup, trying every 25ms for up to half a second.
- **Seen.** A splitter is a 1px line in the border colour with a 7px grab area (`flex-layout__dragger`, `--split__size`). On hover and keyboard focus (`:focus-visible`) it shows a handle, a short rounded bar 5px across; focused, the line and handle are drawn in `--splitter__focus-color` with the app's `--focus-shadow`, as a split pane's are. While dragged, the marker is the line in `--splitter__line-color` with the handle in its active colour. States come from the splitter's ARIA attributes and pseudo-classes; only the marker, which isn't a control, has modifiers (`--splitter`, `--across`, `--down`, `--tab`). In forced colours the handle is drawn in system colours.
- **Above visualisations.** Checked by `DashboardComponent` `SplitterAboveVisualisation`: 3px into the visualisation's frame, the splitter (its grab area, handle and focus) is on top; further in, the visualisation is.

Stories `App/Editors/DashboardEditor` (`DashboardKeyboardStories`): `TabOrder*` check the order of panels and splitters side by side, stacked, nested both ways, deeply nested, tabbed and after a panel is docked to the left; `SplitterKeyboardResize` and `SplitterKeyboardResizeStacked` move splitters with the keys; `FocusKeptWhenMaximised` maximises and restores a panel from its menu with the keyboard and checks the focus returns to its tab; `SplitterFocused` and `SplitterDragging` show the focused and dragged looks.

Not yet done: drags read mouse events only (no touch or pen, and no pointer capture); Tab from inside a visualisation's frame goes to the end of the page, as the frame isn't in the layout; and the name isn't refreshed when a tab is renamed while its splitter has the focus.

## Text fields

Every text box and text area is the BEM block `text-field` (`text-field.css`), a text area with the
`text-field--multiline` modifier. Stroom builds them from its own copies of GWT's `TextBox` and
`TextArea` (`stroom-core-client-widget/src/main/java/com/google/gwt/user/client/ui`), and those give
the class in place of GWT's `gwt-TextBox` and `gwt-TextArea` (a one-line change in each, approved
for that directory), so no screen needed editing.

- **States** come from the element: `:disabled` (the filled field with no border that every
  disabled control has), `[readonly]` (the normal field, its value greyed), `:focus-visible`, and,
  for a rejected value, `aria-invalid` (`FieldValidity`: a red border, and a red ring with the
  focus). Its placeholder and the browser's autofill colours are there too. Each state is the
  attribute alone: Stroom's copy of GWT's `ValueBoxBase` no longer adds a `<primary>-readonly`
  class, and `FieldValidity` no longer adds an `invalid` class (the fallback for inputs with no
  block uses `[aria-invalid="true"]` too). The password box's input is a text field, given by
  Stroom's copy of `PasswordTextBox`.
- **One stylesheet.** `text-field.css` has every look a text field has. `stroom.css`'s rules for
  bare `input` and `textarea` elements (for inputs with no block of their own yet) leave text
  fields out, with `:where(:not(.text-field))`, which adds nothing to their weight. The password
  box's input is a text field too.
- **Quick filters** are named for what they filter ("Filter users and groups"), as a placeholder is
  only a hint: by a form group's label, in their template (`accessibleName`), or by their presenter
  through the shared views' `setAccessibleName` (`QuickFilterPageView`, `QuickFilterDialogView`;
  the explorer's chooser, `ExplorerPopup`, names its own in its template). A selection box's list's filter is "Filter" and the box's name.
- **Cascade.** `text-field.css` is imported straight after `stroom.css`, where the rules it replaces
  were, and each of its rules weighs what the rule it replaced did: its size at a class's weight
  (0,1,0), so a screen's `width: 100%` still wins; its look at an element and a class's (0,1,1) for a
  text box; its states in `:where()`. So a later stylesheet's rules for a particular field win or
  lose as they did, and screenshots of every text field's states were unchanged by the move.
  Radio buttons' looks moved from `stroom.css` to `radio-button.css`.
- **Names.** A field in a form group is named by its label. Where nothing on screen names a field,
  it is named with `AccessibleName` (the annotation editor's Title and Subject, whose placeholders
  are only hints; Query Info; Dependency Information; a pipeline property's Value, whose control is
  set after its form group's label), or for its component (the dashboard's Text Input and Key Value
  Input, renamed with it). Fields in configured HTML (the activity form) are named by wrapping them
  in their `<label>`: a field's `id` there is the id of the property it sets, so a label can't point
  at one.
- **`LineColInput`** (`line-col-input`, `line-col-input.css`) is a text field for `line:column`; a
  value that is no such position is marked invalid (`FieldValidity`), rather than turned red.

Stories `Widgets/Inputs/TextInput` (`Basic`, `MultiLine`, `Invalid`, `DisabledAndReadonly`) and
`Widgets/Inputs/LineColInput` (`InvalidValueIsMarked`).

A template adds a field's own classes with `addStyleNames`, never `styleName`, which would replace
`text-field` and leave the field to `stroom.css`'s bare element rules. Nine fields did (the pager's
page boxes, the query data box, the annotation editor's Title, Subject and comment, the include and
exclude filter areas and Create Multiple Users' batch); they are text fields now, each looking as it
did (a screen's own rules for one, e.g. `.annotationComment`, win as they did), with `w-100` where it
filled its place (a text field is 200px wide unless the screen sizes it).

`TextFieldCell` (`stroom.cell.textfield.client`) is a text field in a grid's cell: the same
`text-field` input, filling its column (`text-field--cell`), each row's named for its column and
row (e.g. "Path for $.type"), read only with `readonly` (not `disabled`, so it can still be reached
and read). `SelectionBoxCell` (`stroom.item.client`) is the selection box in a cell: the same markup
and classes as `BaseSelectionBox` (`selection-box--cell` fills the column), the same keys (Enter,
Space, Down, Up and Alt+Down open the list, typing goes to it) and the same `SelectionPopup`;
choosing gives the value to the column's field updater, and the focus comes back to the row's box
once the grid has drawn it again. Every row's controls are tab stops, and while one has the focus
the grid leaves its keys to it, as the cell is editing (the selection box cell takes focus and blur
so that the grid knows before a key arrives). They replace the floor map's `AccessibleTextInputCell`
and `AccessibleSelectionCell` (bare `input` and `select` elements). Stories
`App/Editors/FloorMapSettings`; real input: `selection-box.mjs`. The grid's own keyboard cell was a
stray tab stop between the controls, and a grid with Stroom's own selection handling didn't ask
whether a cell was editing; both went with the new grid (session 4), whose keyboard the floor map's
schema grid now follows (see Data grid).

Not yet done: the bare element rules in `stroom.css` (to go once every input has a block); it
hasn't been tried with a screen reader.

## Tick box

The tick box is the BEM block `tick-box` (`tick-box.css`), in two forms that look the same:

- **The widget** (`TickBox`): a `label` element wrapping a native check box
  (`tick-box__input`, unseen and over the box, so it has the focus, the clicks and the state), the
  box drawn for it (`tick-box__mark`) and its text (`tick-box__label`). The label wraps the check
  box, so its text names it and clicking anywhere on it ticks it, with no `for` and no id needed.
  Its states are the check box's: `:checked`, `:disabled` and `aria-readonly` (a read-only tick box
  can be focused and read, but a click is cancelled). It has no part-ticked state.
- **The grid's cell** (`TickBoxCell`, `tick-box--cell`, an icon's size): an element with
  `role="checkbox"`, `aria-checked` (`true`, `false` or `mixed`, part ticked, e.g. a header's
  select-all) and `aria-readonly`, holding the drawn box. The part-ticked square is drawn by the
  stylesheet, so the box has nothing inside it but the tick. A cell that can't be clicked is
  `aria-readonly`, which gives it the read-only look (`NoBorderAppearance` no longer adds a class).
  `ColumnValueCell` draws the same markup for its read-only ticks.

Ticked is a white tick on the widget's blue (`--blue-500`); read only, a grey tick on the normal
box; disabled, a grey tick on the filled box with no border every disabled control has.

Stories `Widgets/Inputs/TickBox` (`Basic`, `ReadOnly`, `Disabled`, `NoLabel`) and
`Widgets/Cell Renderers/TickBoxCell` (`InGrid`, `ReadOnly`).

Not yet done: the grid cell can't be reached from the keyboard (the data grid's keyboard pattern,
session 4); it hasn't been tried with a screen reader.

## Radio group

`RadioGroup<T>` (`stroom.widget.form.client`) is a choice of one of a few options, all shown. It
replaces GWT's `RadioButton`, of which the floor map's layer style had three.

- **Native radio buttons.** Its options are native radio inputs sharing a name, so the browser
  makes the group one tab stop, and the arrow keys move between the options, choosing as they go.
  Each option is a label holding its radio button and text, so its text names it, and clicking the
  text chooses it.
- **A named radio group.** The block is `role="radiogroup"`, named by its form group's label, or by
  `setAccessibleName` where there is none. A form group marks a child holding several controls as a
  `group`; it now leaves a child that has a role of its own (a radio group's) as it is.
- **Value.** `addItem(value, label[, title])`, then `getValue`, `setValue(value[, fireEvents])`
  and a value change handler, which a choice fires once (the browser fires `change` only on the
  radio button chosen).
- **CSS.** `radio-group.css`: the BEM block `radio-group`, its `__option`, `__input` and `__label`.
  Every radio input looks the same, including those in an activity's form, which have no classes
  of their own.

Stories `Widgets/Inputs/RadioGroup` (`Basic`, `Disabled`, `AccessibleName`), and
`App/Editors/FloorMapForms` `LayerStyle`.

The floor map's icon picker (`FloorMapIconPicker`) is a radio group in the same way: each icon a
label holding an unseen native radio button, so the grid is one tab stop with the arrow keys
choosing, its chosen and focused looks drawn from the radio button's state (`:has(:checked)`,
`:has(:focus-visible)`).

## Quick filter

`QuickFilter` (`stroom.widget.dropdowntree.client.view`) is the BEM block `quick-filter`
(`quick-filter.css`):

- **A search field.** Its input (`quick-filter__input`) is `type="search"`, so assistive technology
  meets a `searchbox`, not a text box. Chrome's own clear button and decoration are hidden, as the
  filter has its own Clear button; its look, and its disabled, focused and invalid looks, are the
  text field's (`text-field.css`). Escape clears it, as it did before (and as a search field does).
- **Its buttons** are a toolbar (`quick-filter__toolbar`, one tab stop, a toolbar only while two
  buttons show) over the end of the box: Clear, the recent filters (`quick-filter__recent`, shown
  with the block's `quick-filter--recent` modifier, which leaves room for it in the box) and its
  syntax help, each 24px.
- **One stylesheet.** The rules other stylesheets had for it are in `quick-filter.css`: the column
  function editor's and query help's flat filter (a bottom border only), and the gap under it in a
  selection box's list. `quick-filter-layout` is a panel holding a quick filter above what it
  filters (it was `quickFilter-container`). The state classes it no longer needs are gone
  (`disabled`, which the input's own `:disabled` carries).

Stories `Widgets/Inputs/QuickFilter` (`FiltersAList` checks the search role and the classes).

Its syntax help is the shared help popup and its Advanced Query dialog's content is BEM
(`advanced-query`), both done with the overlays (see [Overlays](#overlays)). Not yet done: it hasn't
been tried with a screen reader.

## Pickers

The document and user pickers (`DocSelectionController` and `UserRefSelectionController`, drawn by
`DropDownBox`) are buttons (`aria-haspopup="dialog"`) that open a modal dialog to choose from: a
quick filter over the explorer tree (`ExplorerPopupController`) or a grid of users and groups
(`UserRefPopupController`). Enter or Space on a picker opens it.

- **Modal.** The dialogs are shown `.modal(true)`: the glass covers the page, they are `aria-modal`,
  and `DialogFocusTrap` keeps the focus in them (Tab from the last tab stop goes to the first, and
  Shift+Tab back), as it does in every modal dialog. Escape cancels, and the focus goes back to the
  picker (`CurrentFocus`).
- **Filter and list.** The focus starts in the quick filter. Down goes to the chosen item (or the
  first), Up from the first item back to the filter, and typing in the list goes to the filter
  (`QuickFilter.setMoveDown`, and `AbstractSelectionEventManager.setMoveUpFromFirst` and
  `setTypedHandler`, which other lists with a filter above them can use too).
- **Choosing.** Moving chooses (selection follows focus,
  `AbstractSelectionEventManager.setSelectionFollowsFocus`), as in a list that chooses one item;
  what is chosen is announced in a live region ("Normalise chosen"). Tabbing into the list lands on
  the chosen item. Enter on an item chooses it and closes the dialog, as OK does; on the grid it is
  the row with the focus, even one only highlighted after filtering hid what was chosen. Cancel puts
  back what was chosen when the dialog opened.
- **The filter's focus.** `QuickFilter.forceFocus` tries again for half a second while a popup
  settles, but no longer once the filter has had the focus, so it doesn't pull the focus back from
  the list.

The lists are named ("Documents", "Users and groups"), but the tree and grid have no roles or
states yet; those come with sessions 5 (trees) and 4 (grids).

The picker itself is the BEM block `drop-down` (`drop-down.css`): its value (`drop-down__value`,
filling the box and ending in an ellipsis clear of the icon) and the "…" icon (`drop-down__icon`).
Its field look, and its read-only and disabled looks, are the shared `stroom-control` classes'.

A document that can't be found (deleted, or not visible to the user) is a problem with the field's
value, so it is shown as #5409's form feedback is, not behind a button: the picker is
`aria-invalid`, with the red border of an invalid field, and described by a message under it
(`DropDownBox.setProblem(short, full)`). The message is short ("'Deleted Feed' can't be found, or
you don't have permission to see it."), with the full one (the id, and what to do) as its tooltip
and what screen readers read; in a form group it is the group's feedback
(`FormGroup.setFeedback(short, full)`), and outside one a line of the picker's own
(`drop-down__feedback`), added while there is a problem. It replaced a 17px warning button that only
a mouse could use, which sat inside the picker's own button, and opened an alert.

Stories `Widgets/Selectors/DocSelectionBox` `KeysInTheDialog` and
`Widgets/Selectors/UserRefSelectionBox` `KeysInTheDialog`.

## Value spinner

`ValueSpinner` (`stroom.widget.valuespinner.client`, styled by `value-spinner.css`) is a field for a whole number with arrows that step it down and up. It is retrofitted rather than replaced, as its API (`setMin`, `setMax`, `setMinStep`, `setValue`, `formatValue`/`parseValue` for subclasses) is used by about 58 views; `Spinner` holds its value, bounds and the stepping while an arrow is held, and no longer owns any widgets.

- **Keyboard and screen readers.** The field is a WAI-ARIA spin button (`role="spinbutton"` on the text input), with `aria-valuenow`, `aria-valuemin` and `aria-valuemax`, and `aria-valuetext` when `formatValue` shows the value other than as the number (e.g. "25%"). Up and Down step it, Page Up and Page Down take ten steps (stopping at the bounds rather than wrapping), Home and End go to its bounds, and Enter or leaving the field commits what was typed. Every step first commits what was typed, so it steps from that rather than from the value before it. The spinner is one tab stop.
- **Arrows.** The arrows (down, then up) aren't buttons: the keys do what they do, so, as the pattern allows, they are no tab stop and are hidden from screen readers (`aria-hidden`), their titles naming them for the mouse. They are not a toolbar, the exception to "every icon button is made in a toolbar". Each is a 24px target (WCAG 2.5.8) inside the end of the field's border, as a drop-down's arrow is, with a 10px icon in the drop-down arrow's colour (`--icon-colour__black`, as the date picker's month and year arrows' size) and a toolbar button's hover and pressed fills. They read pointer events, so a mouse, a finger or a pen steps at once and keeps stepping, faster and faster, while held; a long press doesn't open the browser's menu. A mouse press puts the focus in the field, so typing can carry on; a finger's doesn't, so no on-screen keyboard comes up. They are dimmed and do nothing when the field is disabled or read only, which the CSS reads from the field's own state (`:read-only`, which a disabled field also matches), not a modifier.
- **Rejected values.** A typed value that isn't a whole number within the bounds is rejected (WCAG 3.3.1): the value before it is put back, so what is shown is always what the screen will save, and the field is marked invalid and described by why, e.g. `"500" isn't allowed. Enter a whole number from 0 to 100.` (a bound that is the largest or smallest `long` isn't said). In a `FormGroup` (most of Stroom's spinners) the message is the group's feedback (`FormGroup.setFeedback`), as #5409's dialogs show theirs; otherwise the spinner shows it under the field (`value-spinner__feedback`). Either way it is also announced by a polite live region (`value-spinner__status`), as the focus has usually left the field, and read again when the field is focused. It stays until a value is accepted (a commit, an arrow or a step key); focusing and leaving the field unchanged doesn't clear it. A spinner that hasn't been given a value is empty, which isn't an error.
- **Layout.** The block (`value-spinner`) holds the field and its arrows (`value-spinner__field`, which the arrows are placed on, so that feedback under it doesn't move them), then the feedback and the live region. A width given to the spinner (`width="200px"`, `w-100`, `dock-max`) is the field's; its own is 96px, leaving room for the value beside the arrows. The field's padding is set under `value-spinner__field`, to outrank `stroom.css`'s `input[type="text"]` padding.

The data grid's spinner, `ValueSpinnerCell`, renders the widget's markup (`value-spinner`, `__field`, `__input`, `__steps`, `__step`, `__icon`) with the `value-spinner--cell` modifier: as wide as its cell, with a 24px-high field whose 1px negative margin keeps its row as high as the grid's others (28px). Spinner columns are `ColumnSizeConstants.VALUE_SPINNER_COL` (110px), room for a value and its two arrows; a disabled row's value is greyed by a rule in `value-spinner.css` that reads the grid's `dataGridDisabledCell`. Its field is a spin button named by its column (the name is required: `new ValueSpinnerCell(name, min, max)`, `DataGridUtil.valueSpinnerColumnBuilder(name, …)`), with its value and bounds, and the widget's keys while it has the focus, each committing the value and leaving the focus there. Its arrows step it while pressed with the mouse and commit it when let go. The grid hands a cell its events after the browser has handled them (`AbstractHasData.onBrowserEvent` defers `onBrowserEvent2`), so a cell can't `preventDefault()` an event and doesn't receive touch events: a finger's tap steps once, by the mouse events the browser makes for it, but a held finger doesn't repeat, and Page Up and Page Down may also scroll.

Stories `Widgets/Inputs/ValueSpinner` (`ValueSpinnerStories`): the role, value and bounds as they change (`SpinButtonRoleAndValue`, `FormattedValueText`); the keys (`PageHomeAndEndKeys`, `KeysStepFromTypedText`, `ArrowsClampAtTheBoundary`); the arrows (`ArrowsStepTheValue`, `ArrowsStepFromTypedText`, `ArrowsAreNotTabStops`); disabled and read only; rejected values under the field, in a form group, empty and unset (`RejectedValueIsExplained`, `RejectedValueInAFormGroup`, `EmptyValueIsExplained`, `UnsetIsNotAnError`, and the older contract stories); and the widths screens give it (`Widths`). Stories `Widgets/Cell Renderers/ValueSpinnerCell` (`ValueSpinnerCellStories`): its role, name, value and bounds in a grid (`InGrid`), the arrows (`ArrowsStepAndCommit`), the keys (`KeysWhileFocused`), a typed value kept within its bounds and a read-only row. Real input (`test-runner/lib/real-input/value-spinner.mjs`): the real mouse clicks and holds an arrow, a real finger (touch events from Chromium's DevTools protocol) taps and holds one without focusing the field, the real keys step it, and a disabled spinner's arrows do nothing; in the grid, the real mouse clicks and holds an arrow, a real finger taps one, and the real keys step the field.

Not yet done: it hasn't been tried with a screen reader, and VoiceOver on iOS may not step a custom spin button by swiping (the value can still be typed); screens are to be checked for the wider default width when it is rolled out; and the grid's spinner can't be reached from the keyboard (its field is `tabindex="-1"`), is named by its column but not its row, changes a typed value silently (non-digits dropped, then kept within its bounds), and doesn't repeat while a finger is held. Those wait on the data grid's keyboard pattern and event handling.

## Icon boxes

`IconBox` (`stroom.svg.client`, styled by `icon-box.css`) is a field with an icon inside the end of its border that opens what the field chooses from: a selection box's drop-down arrow, a date box's calendar, a time or schedule box's clock. The icon is a picture, not a button: the field opens the same thing from the keyboard (Enter, and a click on a selection box that can't be typed in), so it is hidden from screen readers (`aria-hidden`) and is no tab stop, though a click on it opens it too. It has no hover or focus states.

- **Names.** The block is `icon-box`; the field (the wrapped widget's first input) is `icon-box__field`, padded clear of the icon (its 26px and 4px more, see Field ends); the icon is `icon-box__icon` (a 26px strip over the end of the field) holding `icon-box__image`.
- **Drop-down arrow.** `setDropDownWidget` gives the box the value spinner's arrow (`ARROW_DOWN`), 10px, in `--icon-colour__black`, as the date picker's month and year arrows' size (`icon-box--drop-down`, a variant with no ARIA meaning). Every "opens a list" arrow in a field looks the same: the selection box's, QuickFilter's Recent Filters (a toolbar button, so a tab stop, with the same icon) and, as a pair, the value spinner's. `SvgImage.DROP_DOWN`, which drew a smaller, thinner arrow, is deleted.
- **Other icons** (the calendar and clock) are 16px (`--icon-width`) in `--icon-colour__grey`, as before.
- **Read only.** `setReadOnly` dims the icon and stops it opening anything (`icon-box--read-only`, a state with no ARIA attribute on the box, so a modifier): the selection, date, time and schedule boxes set it when their field is disabled or read only.

## Schedule box

`ScheduleBox` (`stroom.schedule.client`, styled by `schedule-box.css`) is a text field for a schedule's expression (a frequency such as `10m`, or a cron expression), with a clock at its end, and behaves as a date-time box: Enter or Alt+Down in the field, or a click on the clock, opens the Change Schedule dialog (`SchedulePicker`, shown by `SchedulePickerController`), never focus, and the field is `aria-haspopup="dialog"`. An expression the server rejects marks the field invalid when it is left. The box only asks: a `ScheduleBoxController` attached to it by its screen's controller checks the expression with the server (making no dialog) and opens a new dialog each time it is asked. In the dialog, the quick settings are a `QuickSettings`, and the Next Scheduled Time is a polite live region, as it changes when the expression is calculated.

`QuickSettings` (`stroom.widget.button.client`, styled by `quick-settings.css`) is presets that fill in a value, in titled groups side by side: each group a `group` named by its title, each preset a `LinkButton` named by its text, at least 24px high. The schedule dialog and the time range dialog use it (they had built the same markup each, with the time range's `timeRange-quick*` classes).

Stories `Widgets/Date & Time/ScheduleBox` (`ScheduleBoxStories`): `KeysOpenTheDialog`, `QuickSettingsAreGroupedButtons`, `CheckingMakesNoDialog`, `ChooseSchedule`, `CancelKeepsSchedule`, `ReadOnly`.

## Time range selector

`TimeRangeSelector` (`stroom.query.client.view`, styled by `time-range-selector.css`) shows the time range a query or dashboard applies, as a `LinkButton` whose text is the range ("Today", "After now()-1d"), named "Time range: " and its text, and `aria-haspopup="dialog"`. It asks for the Time Range dialog (`TimeRangePickerController`, its content `TimeRangePicker`): quick settings (`QuickSettings`) and the After and Before times (date-time boxes that allow expressions), shown as a modal OK/Cancel dialog below the selector by the `TimeRangeSelectorController` its screen's controller attaches. As a modal Stroom dialog, it is a `dialog` named by its title, the focus starts in After Time and is kept in it, Escape cancels it and the focus goes back to the selector. Disabled and read only are both `aria-disabled` (a button can't be read only), so it stays focusable and doesn't open; read only, greyed less, is the `time-range-selector--readonly` modifier.

Stories `App/Editors/QueryEditor` `TimeRangeRerun` (a quick setting chosen with the keys reruns the query) and `TimeRangeDialogKeys` (the dialog's role, name, focus and Escape).

## Field ends

A field with something drawn over its end (an icon box's icon, a password box's button and badge, a quick filter's buttons, a spinner's arrows) keeps its text 4px (`FieldEnd.GAP`) clear of it: its text never runs under what is over its end. Where that never changes width (an icon box's 26px icon, a selection box's arrow), the stylesheet pads the field's end by its width and 4px. Where it does (a quick filter's Clear comes and goes, a password badge's width depends on its text), `FieldEnd` (`stroom.widget.util.client`) watches it (`ResizeObserver`) while the widget is attached and sets the field's `--field__end-space`, which the stylesheet uses as its end padding, with a fallback for before it is shown. Story `Widgets/Inputs/FieldEnds` `TextStaysClearOfTheEnd` gives each such field a value too long for it and measures the space.

## Selection box

`BaseSelectionBox` (`stroom.item.client`, styled by `selection-box.css`) is a WAI-ARIA combobox: its text box (`role="combobox"`, `aria-haspopup="listbox"`, `aria-expanded`, `aria-controls`) sits over a picture of its value (`selection-box__value`, `aria-hidden`), with an icon box's drop-down arrow at its end. Its list is a `SelectionPopup` (`selection-popup.css`) holding a `SelectionList`: a quick filter over 10 items, a pager over 100, and a list box whose rows are options. Opening the list moves the focus into it (to its quick filter if it has one, else to the chosen option), rather than keeping it in the box with `aria-activedescendant`.

- **Keys on the box** (the list closed): Enter, Down, Up and Alt+Down open the list; in a box that can't be typed in (`selection-box--select-only`), Space does too, Home and End open it at its first and last option, and typing a character opens the list and goes to it. Characters typed before the list has the focus are kept and passed on (`SelectionPopup.type`).
- **Keys in the list**: Up and Down move between options (and Up from the first option to the quick filter), only highlighting, as a combobox's list does; Enter or Alt+Up chooses the option with the focus and closes the list; Escape closes it, and the focus goes back to the box. Tab closes it, leaving the value, and goes on to what follows the box (`TabOrder.focusNext`); Shift+Tab closes it and goes back to the box. (It once sent the focus to the page, leaving the list open.) Typing goes to what is typed (`SelectionList.typeAhead`): into the quick filter, applied at once, with the caret after it (`QuickFilter.type`), or, in a list with no quick filter, to the next option that starts with it, characters typed within half a second being taken together, and the same character again going on to the next option starting with it. The list hands keys on after the browser has handled them (as the data grid does), so a key can't be cancelled there.
- **Value.** The picture of the value stays on one line and ends in an ellipsis, clear of the arrow (`padding-right: 26px`). It draws the box's border (`stroom-control`).
- **States.** Disabled and read only are modifiers (`selection-box--disabled`, `selection-box--read-only`), as the box has no ARIA attribute for them; they follow the text box's `disabled` and `aria-readonly`. A box that can't be typed in shows a pointer over it while it can be changed.

Stories `Widgets/Selectors/SelectionBox` (`SelectionBoxStories`): `KeysOpenTheList`, `AltUpChoosesTheOption`, `TypingJumpsToAnOption`, `TypingFiltersALongList` and `LongValue`, beside the earlier ones. Real input (`test-runner/lib/real-input/selection-box.mjs`): typing, Alt+Up, Space and Escape pressed for real, and typing into a long list's quick filter.

- **The list's names.** `SelectionList` is styled by `selection-list.css`: the block `selection-list`, its quick filter `selection-list__filter`, its path `selection-list__path` (a navigation landmark, an `AriaPanel` with role `navigation` named "Breadcrumb", holding a `selection-list__path-link` button for each level above, `selection-list__path-separator`s hidden from screen readers, and the level shown, `selection-list__path-current`, `aria-current="page"`), and its options with their pager, `selection-list__options`. The rows are still the cell table's (`cellTable*`), which the grid session renames. The option with the focus has the focus border and ring every other control has (`--focus-border`, `--focus-ring__color`), drawn inside the row, as the list's scrolling box would cut off one drawn round it; a chosen one with the focus takes `--row__background-color--selected--focussed`, as the expression tree's and the explorer's rows do, so that the border is 3:1 against it in both themes (the row tokens' pale ring it had was 1.7:1, and 1.05:1 on a chosen row, in the light theme; the grids still use it). A chosen row looks the same under the mouse, as the expression tree's does; only an unchosen row shows the hover colour. Story `Widgets/Selectors/SelectionListStates` `RowStates` shows a row in each state (at rest, mouse over, mouse down, selected, selected and mouse over, focused, selected and focused), with the pseudo-classes copied into classes as `Widgets/Layout/SplitPaneStates` does.
- **Several choices.** `setMultiSelect(true)` makes the list WAI-ARIA's list box with multiple selection (`aria-multiselectable`), for a list on a form rather than a selection box's: moving only moves the focus; Space adds the option with the focus to those chosen or takes it out (it is never typed into the quick filter), Shift+Down and Shift+Up move to the next option and do the same there, and Shift+Space chooses the run from the last one chosen; a click chooses only that option, Ctrl+click adds or takes it, Shift+click chooses the run from the last one chosen; Ctrl+A chooses every option; Enter or a double click acts on those chosen (first choosing only the option with the focus, if it isn't one), which the selection model announces as a double select. Alt+Up doesn't choose, and Left at the top level doesn't go back to the first page. The choices are kept when the list is refreshed or paged; a screen that changes the options keeps only the choices still shown (the explorer's `TagListModel.setTags`), so it never acts on a choice that can't be seen. Such a list on a form is drawn as a field (`selection-list--field`: a variant, so a modifier) and named by its form group's label, which names a `listbox` with `aria-labelledby` (its own text, every option's, isn't part of its name). `setMoveUpFromFirst` sends Up on the first option elsewhere, e.g. to a field above that filters the list. The explorer's Edit Tags and Remove Tags lists are such lists.

Stories `App/Main/EditTagsDialog`: `KeysChooseAndAddTags`, `DeleteRemovesTags`, `FilteringDropsHiddenChoices`, `ManyKnownTags` and `RemoveByKeyboard`, beside the earlier ones. Real input (`test-runner/lib/real-input/selection-list.mjs`): Ctrl+click, Shift+click, Ctrl+A, Delete, Enter and a double click, done for real.

Not yet done: it hasn't been tried with a screen reader; a key or click sent within a millisecond of a choice is lost, as the rows are redrawn and the cell table hands its events on in a deferred command (no person's hand is that quick, but a play must wait for the focus to come back between keys; the grid session's to change).

## Code editor

Stroom's code editor (`Editor`, in `CodeEditor` and `SingleLineCodeEditor`) is Ace, retrofitted rather than replaced: Ace's sources (`edu/ycp/cs/dh/acegwt`) change only with approval, so the editor uses what Ace 1.44 offers.

- **Keyboard.** Ace's keyboard accessibility mode is on for every editor (`AceEditor.setKeyboardAccessibility`). An editor is one tab stop, its content (`role="group"`, `aria-roledescription` "editor"); Ace's text input isn't one. Enter on the content starts editing (the caret where it was, scrolled into view); while editing, Tab indents, as a code editor's should. Escape stops editing, back to the content, and Tab then moves on. A click goes straight into the text, as before. A read-only editor works the same way, Enter putting the caret in its text to move through it and select.
- **The gutter.** The mode also makes the gutter a tab stop, with the arrow keys through its fold widgets and annotations; `Editor` puts it back as Ace makes it (no `tabindex`, `aria-hidden`), as agreed, so an editor is one tab stop and a thin splitter beside one keeps the room WCAG 2.5.8 asks for. Its errors and folds have Ace's keys while editing instead: Alt+E and Alt+Shift+E (F4 and Shift+F4 on a Mac) go to the next and previous annotation and show its message under its line; F2 folds or unfolds the line's element, Alt+L and Alt+Shift+L fold and unfold at the cursor.
- **Escape.** The editor handles Escape first: Ace's popups (autocompletion, the search box) close on it, Vim keys leave insert or visual mode on it, and only then does it stop editing. Dialogs leave keys from Ace's text input to Ace (`AbstractPopupPanel` checks for `ace_text-input`), so in a dialog the first Escape stops editing and the next one, from the content, cancels the dialog, as Escape reaches an inner widget first everywhere else.
- **Names.** Ace names the content ("Editor content, press Enter to start editing, press Escape to exit"), and labels its text input with the cursor's row, after Stroom's name for it ("Code editor, Cursor at row 3", through Ace's `textInputAriaLabel`). The text input is a `textbox`, `aria-readonly` when the editor is read only. Ace hides its drawn lines from screen readers (`aria-hidden`), which read the text through its input.
- **Focus.** Ace's own ring (a box shadow in a fixed blue, on any focus) is replaced in `code-editor.css` by a 2px outline inside the content's edge, for keyboard focus only (`:focus-visible`), so it stays in forced colours. Its colour follows the editor's theme (`ace_dark`), not Stroom's, as a user can pair any editor theme with either Stroom theme: `--blue-800` on light editor themes (4.2:1 or more, measured on every theme's gutter), `--blue-300` on dark ones (4.9:1 or more).

Stories `Widgets/Editors & Viewers/AceEditor` (`AceEditorStories`): `KeyboardWayOut` (the one tab stop, Enter, Tab indenting, Escape, Tab out and Shift+Tab back), `KeyboardErrorsAndFolds` (Alt+E and F2 while editing) and `KeyboardReadOnly`; `AceEditorFeatures` `VimKeys` (Escape out of insert mode, then out of editing); `App/Editors/QueryEditor` `ColumnFilterMenu` (Escape in a dialog's editor leaves the dialog open). Real input (`test-runner/lib/real-input/ace-editor.mjs`): real Tab, Enter and Escape through `KeyboardWayOut`, with the focus ring shown.

Not yet done: it hasn't been tried with a screen reader; an annotation's message, shown by Alt+E inside the content, which Ace hides from screen readers, isn't read out (no worse than before, when the gutter couldn't be reached at all; a live region could say it); Ace's names are its own English strings; and Ace's search box, autocompletion popup and Stroom's editor context menu haven't been audited.

## Date and time

Every field for a moment in time is a `DateTimeBox` (`stroom.widget.datepicker.client`, styled by `date-time-box.css`): a text field for an ISO 8601 time with a calendar icon at its end. No value in Stroom is only a date, so there is no date-only box: GWT's `DateBox` and `DatePicker`, and Stroom's `MyDateBox` built on them, are deleted. A time of day is a `TimeBox`, which has the same look and keys.

- **Opening.** Enter or Alt+Down in the field, or a click on the icon, opens the Set Date And Time dialog (`DateTimePicker`, shown by `DateTimePickerController`) on the field's time; it never opens on focus. The field is `aria-haspopup="dialog"`; the icon is a picture hidden from screen readers (see Icon boxes). OK puts the time chosen in the field, formatted in the user's time zone (`ClientTimeZone`), Cancel or Escape leaves it, and the focus goes back to the field.
- **Expressions.** `setExpressionsAllowed(true)` (the expression term editor's dates, the query time range and a visualisation's date settings) allows any text, e.g. `now()-1d` or `day()`, which is never marked invalid; `getText()` and `setText()` read and write it. The dialog only edits a plain time, so it opens on now and replaces an expression. Such a box is typed in first, so Enter is its screen's (the term editor runs the query) and only Alt+Down or the icon opens the dialog.
- **Width.** 17 of the field's font size (`--font-size-13`), 245px at the medium font size, so a whole ISO time with an offset (`2024-12-28T23:58:58.888+0530`, 241px) fits beside the calendar at every font size; the same in an expression term (where a narrow field is 175px). A screen's width (`w-100`, or a class of its own) still wins. A `TimeBox` (the `date-time-box--time` variant) is 200px.
- **Format.** The field shows and takes ISO 8601, in the user's time zone (its offset, or `Z` for UTC), not the user's date and time format preference: that is a display format, some of whose choices drop the time (`yyyy-MM-dd`), the century (`dd/MM/yy`) or are ambiguous (`dd/MM` against `MM/dd`), the client has nothing to parse it, and expressions (`now()-1d`) are written in ISO. The dialog shows the date and time readably.
- **Validity.** A box that doesn't allow expressions marks text that isn't a time invalid (`FieldValidity`) when it is left; `setOptional(true)` allows it to be empty.
- **Its dialog.** The box only asks for its dialog (`setOnOpenPicker`); the controller of the screen that shows it attaches a `DateTimeBoxController` to it (`make(dateTimeBoxControllerProvider).attach(view.getStartTime())`), which opens the dialog, owned by the screen, and puts the time chosen in the box. A `TimeBox` has a `TimeBoxController` in the same way, for the Set Time dialog (`TimePicker`). The dialog's shortcuts (Today, Yesterday, Week Start; Now, Midnight, Midday) and each spinner's Reset are `LinkButton`s, at least 24px high (`date-time-view.css`).

The calendar (`Calendar`, the block `calendar`, `calendar.css`) is its year and month rows (in `Calendar.ui.xml`: a selection box and a Previous/Next toolbar each, `calendar__header`, `calendar__row`) above the month's days (`CalendarGrid`), a WAI-ARIA grid:

- **Roles and names.** A `table` with `role="grid"`, named for the month it shows ("January 2024"); a header row of `columnheader`s (the short day name, its full name in `abbr`); each day a `gridcell` named by its full date in the browser's language ("Monday, 15 January 2024"). The chosen day is `aria-selected="true"`, today `aria-current="date"`; the days of the months before and after that fill the six weeks are `calendar__day--outside` (a modifier, as ARIA has no attribute for it). In a form group, the calendar is a `group` named by the label, as it has two form controls (Year and Month).
- **Keys.** The grid is one tab stop, the focus on a day (a roving `tabindex`): the chosen day if it is in the month shown, else today if it is, else the same day of the month. Left and Right move a day, Up and Down a week, Home and End to the start and end of its week, Page Up and Page Down a month, Shift+Page Up and Shift+Page Down a year (keeping the day of the month, or the month's last day if it is shorter); moving to a day of another month shows that month. Moving doesn't choose: Enter, Space or a click chooses the day (a click on a day outside the month shows its month). The Year and Month toolbars change the month without choosing, moving the tab stop to the same day of the new month.
- **Looks.** Each state comes from its ARIA attribute: the chosen day is filled in the selected row colour, today is bold with a border in it (the border alone is 2.6:1 dark and 1.3:1 light, so the bold is what shows it); a day's number is centred in its round fill (a table cell's content is at its top otherwise, `stroom.css`), hover the hovered row colour; keyboard focus is a 2px ring in `--focus-border` 1px outside the day, so it is against the page's colour on both sides (7.1:1 dark, 5.8:1 light; inside a chosen day's fill it would be 2.8:1 in the dark theme). In forced colours, the chosen day is `Highlight` and today's border and the focus outline `CanvasText`.

A duration is a `DurationPicker` (`stroom.widget.customdatebox.client`, styled by `duration-picker.css`): its amount (a value spinner, `duration-picker__amount`, "Amount") beside its unit (a selection box, `duration-picker__unit`, "Unit"), which a form group names together as a `group`. Each part is as wide as its longest value needs (1000000, "Milliseconds"), in units of the fields' font size, and neither shrinks. Stories `Widgets/Date & Time/DurationPicker`: `InAFormGroup`, `KeysChangeTheAmount`, `LargestValuesFit`, `SetValueFiresWhenAsked`.

Stories `Widgets/Date & Time/CalendarGrid` (`CalendarGridStories`): roles and names, the one tab stop, each key, crossing months, choosing with Enter, Space and a click, and the toolbars. Stories `Widgets/Date & Time/DateTimePicker` (`DateTimePickerStories`): opening with Enter and Alt+Down, choosing a day with the keys, the dialog's buttons, invalid text, expressions and disabled; `Widgets/Query/ExpressionBuilder` `DateTermBoxes`, a date term's From and To. Real input (`test-runner/lib/real-input/date-time.mjs`): the grid's keys, its focus ring and Tab pressed for real, and Alt+Down and Escape on the box.

Not yet done: it hasn't been tried with a screen reader; the dialogs' own markup (the Popups and Dialogs rows, session 3); the schedule box and time range selector (their own rows).

## Overlays

Dialogs, menus and help popups (session 3), each a popup shown by `PopupUtil.showPopup` (directly, or through `ShowPopupEvent` and `PopupManager`), which remembers where the focus was and puts it back on close (`CurrentFocus`).

- **Escape.** A popup (`AbstractPopupPanel`) previews every key, but Escape pressed inside it isn't handled there: it goes to the widget with the focus first, which uses it if it has something to undo or close (a quick filter with text clears, a date box's picker closes, a tree puts back a move, a grid's list closes its popup, a chart's tooltip goes) and calls `preventDefault()`. The popup's own keydown handler then closes it (through its Close or Cancel button, so a disabled one keeps it open) only if no widget did, and stops it there, so the page behind (whose key handler stops a dashboard's query on Escape) never sees it. Escape with the focus outside every popup closes the top one, as before. A widget in a popup that handles Escape must call `preventDefault()` only when it used it; `AbstractSelectionEventManager.onClose` returns whether it did. Escape in Ace is Ace's (session 6). Stories `Widgets/Dialogs/Escape`.
- **Modal or not.** A dialog is modal (`ShowPopupEvent.Builder.modal()`: glass over the page, `aria-modal`, the focus kept in it by `DialogFocusTrap`) when what it does must not change under it: a picker whose value goes back into a field; a confirmation, or an action on the page's selection or filter; create, copy, move, rename, import and upload; accounts, credentials and permissions; alerts. The rest aren't, so the page behind can be read, and text copied from it, while the dialog is open (an editor of an item in a list, a filter or property whose value comes from the page). A dialog that isn't modal must cope with its subject changing behind it: on OK, apply to the item as it is now (found by its id), and say if it was removed rather than throw (`AlertEvent.fireWarn`).
- **Dialogs.** `Dialog` and `ResizableDialog` are the block `dialog` (`dialog.css`; `dialog--resizable`), named by their title (`aria-labelledby`); an alert or confirmation is an `alertdialog`, and any dialog can be described by a message in it (`PopupUtil.setDialogDescription`, as Advanced Query's is). Elements: `__container`, `__frame` (border, shadow and background, behind the rest), `__layout`, `__title-bar` holding `__icon`, `__title` and `__spinner`, `__body` holding the content template's `__content` and `__buttons` (`--end` for OK and Cancel, `--start` with a `__button-group` at the end), and `__resize--n` to `--sw`.
- **Menus.** The block `menu` (`menu.css`) on the menu's table (`role="menu"`), whose rows' focusable elements are `menuitem` or `menuitemcheckbox`, with `aria-disabled` (which gives the disabled look), and `aria-haspopup` and `aria-expanded` on an item with a sub menu. Up, Down, Home, End, Page Up and Page Down move; Right or Enter opens a sub menu, Left or Escape closes it and goes back to its parent item; Escape in the root menu closes the menu; Enter or Space chooses. A button that opens a menu is marked (`ToolbarButton.setOpensMenu` or `MenuOpener.mark`: `aria-haspopup="menu"`) and passed as the menu's opener (`ShowMenuEvent.Builder.opener`), which keeps its `aria-expanded` up to date. Look: each item draws its own look with rounded corners (5px), not the table's row; the highlighted item (the one the keyboard or mouse is on) is filled, and the mouse over it changes nothing more; keyboard focus is the focus border and ring used elsewhere (`0 0 0 1px --focus-border, 0 0 0 5px --focus-ring__color`), shown only while the keyboard is in use (`menu--keyboard`, set from `InputModality` when the menu opens, on a key press in it, and cleared by the mouse moving or pressing in it), as the menu moves the focus itself as the mouse moves, so the browser's `:focus-visible` can't tell; the menu's viewport (`menu__viewport`) has 5px of room for the ring, the popup no padding of its own. A disabled item greys its content, not its highlight or ring. Stories `Widgets/Menu/MenuPanel`, `PopupContract`'s `EscapeClosesOneLevelAtATime`.
- **Popups.** `SimplePopup` (`PopupType.POPUP`: a menu, a chooser, a filter's list, a tooltip) is the block `popup` (`popup.css`), its look (border, shadow, background, a 4px inset) on the popup itself, with its content in GWT's `popupContent`; `popup--floating` marks a popup panel of its own, hidden until `PopupUtil` has placed it. `SimplePopupLayout` gives another popup panel the same look (one element). A popup's caption isn't shown: it names the popup, making it a dialog that isn't modal; a popup with none has no role (a menu's content has its own).
- **Help popups.** `HelpPopup` (`stroom.widget.help.client`, `help-popup.css`) shows help beside the button that opens it: a form group's (through `ShowHelpEvent` and `HelpManager`, as markdown, `help-popup--markdown`), the floor map's, and a quick filter's syntax help. It is a dialog that isn't modal, named for what it helps with, and doesn't take the focus; Escape, a click outside it or its button again closes it. Its button is marked (`HelpPopup.markOpener`: `aria-haspopup="dialog"`, `aria-expanded`) and passed to it (`setOpener`).

Not yet done: menus have no type-ahead (letters move the highlight, as `KeyBinding`'s movement keys); a dialog's content is seven elements in (GWT's `popupContent` and the content template's wrapper among them); none of it has been tried with a screen reader.

## Data grid

Stroom's grids move to a new widget, `DataGrid` (`stroom.widget.datagrid.client`), built from plain elements rather than GWT's cell table (see `stroom-core-client-widget/DATA_GRID_DESIGN.md` for its design, its parity with `MyDataGrid` and the plan for moving screens to it). It is one `grid` (its scrolling box), with a sticky header row of `columnheader`s and rows of `gridcell`s, so cells are tied to their headers; its events are handled as they happen, so a key can be cancelled. Every screen uses it, and `MyDataGrid` and its classes are deleted (2026-10-21, the design's phases 3 and 4). Its classes are the BEM block `data-grid` (`__row`, `__cell`, `__header-cell` and so on); its row looks, forced colours included, are `data-grid.css` (with its parts and layout), and its keyboard `DataGridKeyboard`.

- **Row looks.** Tight rows don't take the menu's looks (rounded items, a ring outside them). A row is full width with square corners, so a run of selected rows is one block; the grid's rows are striped. Mouse over: the hovered colour, fading in (`--hover__transition`, 120ms on `background-color`, set only in `:hover` rules so that leaving, selecting and focus change at once, and none under `prefers-reduced-motion`). A selected row (`aria-selected="true"`) shows no mouse-over, as there is nothing more to choose. There is no pressed look, as a press selects the row there and then. The light theme's selected colours are solid, so the stripes don't show through; the dark theme's mouse-over is a neutral grey (`#2f343e`, 1.22:1 on a stripe, where the old `#22262e` was 1.00:1). Keyboard focus only (`:focus-visible`; a clicked row shows its selection, not a ring) is drawn inside the row, cell or header, as one outside would cover the rows next to it and be cut off by the scrolling box: a 1px focus border and a 2px band of the focus ring's colour. A selected row with the focus, on it or a cell, is in the selected-and-focused colour, from which the focus border stands out by 3:1 in both themes (the plain selected colour gives only 2.8:1 in the dark theme).
- **Keys.** The grid is one tab stop (a roving `tabindex`): the row, cell, control or header with the focus, as in WAI-ARIA's treegrid example, since most of Stroom's grids are lists whose rows are chosen. On a row, Up and Down, Page Up and Page Down (ten rows), Home and End move between rows, and Right moves into its first cell. On a cell, Left and Right move along the row (Left from the first cell goes back to the row), Up and Down to the same column of the row above or below, Home and End to the first or last cell. While the keyboard row isn't drawn (scrolled away in a grid that draws only the rows in view), the scrolling box is the tab stop, and the row keys there act on the keyboard row.
- **Tree grids.** A grid with an expander column (an `ExpanderCell`'s: processors, server tasks, markers, retention impact, a property's cluster values, a grouped dashboard or query table) is a `treegrid`, its rows saying their level (`aria-level`) and, if they have children, whether they are expanded (`aria-expanded`). On a row, Right expands a collapsed row, and otherwise moves into its cells as in any grid; Left collapses an expanded row, and otherwise goes up to its parent row. The expander column looks and works as it did (a click on it, or Enter on its cell, still toggles); the grid acts through the column's own field updater, so a screen needs nothing more. Story `Widgets/Data Grid/DataGrid` `TreeKeys`.
- **Headers.** Up from a cell in the first row goes to its column's header (not from a row, so Up and Down on rows only ever choose rows); Left and Right move along the headers, Home and End to the first and last, Down back to the first row's cell. Enter or Space does what a click does: sorts a sortable column (`aria-sort`), or else opens the column's menu where the grid has one; the context menu key or Shift+F10 opens the menu too. A header's own controls (the dashboard's filter box) keep their keys.
- **Selection.** It follows the row focus: in a grid that chooses one row the row moved to is chosen; in one that chooses several, Up and Down choose only it, Shift extends the choice to it, Ctrl moves without choosing, and Space toggles the focused row (Shift+Space chooses the run to it). Enter chooses the row, and Ctrl+A fires the grid's Select All. The focus on a cell or header never changes the selection.
- **Cells.** A cell's control that doesn't use the arrow keys takes the focus itself: a tick box (Space ticks it, keeping the focus), a button or link (a row's "Actions..." button among them: Enter or Space opens its menu below it), and a selection box (Enter, Space and Alt+Down open its list). A text field or value spinner uses the arrows, so its cell takes the focus: Enter or F2 starts editing (typing doesn't), Enter commits and Escape cancels (putting the value back), both returning to the cell and neither reaching a dialog. Tab while editing commits and edits the next cell holding a control (the next row's first at the end of a row), Shift+Tab the one before, going on from a tick box or selection box reached that way, until an arrow key, Escape or a click ends it; past the last control the focus leaves the grid. A click in a field edits it at once.
- **Focus kept.** The keyboard's target (row, cell, control or header) is what the grid gives the focus back to when it draws rows or the header again. A press redraws the pressed row's cells (it is now selected) before the browser's own focusing, which drops the focus; the grid puts it back as keyboard focus only if the last input was a key, so a click shows no ring.

The selection manager (`DataGridSelectionEventManager`) handles the mouse, and is the keyboard's `Selector`. `TickBoxCell` keeps the focus on a tick box it redraws.

Stories `Widgets/Data Grid/DataGridRowStates` (`DataGridRowStateStories`): a row of `DataGrid` in each state (it was set beside `MyDataGrid`'s until that was deleted), a story for each theme and density, with a tick box, value spinner, text field and selection box in each row. `Widgets/Data Grid/DataGridKeyboard` (`DataGridKeyboardStories`): `Rows`, `SingleSelection`, `Cells`, `Editing` and `Headers`. Real input (`test-runner/lib/real-input/data-grid.mjs`): Tab in and out, the keys cancelled as they are pressed, the focus ring for the keyboard and not a click, and Tab from control to control while editing. `Widgets/Data Grid/DataGrid`, `Behaviour` and `Benchmark` are the new grid's own (see its design).

Not done yet: the column menu's Move and Resize items for keyboard users (agreed: items in the menu, not shortcuts); a cell with more than one control gives the focus to the first; it hasn't been tried with a screen reader. The density setting doesn't make a row with controls in it tighter, as the controls keep their own padding (left to the whole-app text size pass). The follow-ups agreed at the end of session 4 are in `ISSUES.md`, The data grid's follow-ups: keeping a focused control during a row update, measuring the row update's cost and splitting `DataGrid.java` (its event dispatch is now `GridEvents`) were done on 23 October 2026, and key providers are left.

## CSS for new widgets

New widgets get their own stylesheet, named after the widget's block, in `stroom-app/src/main/resources/ui/css/` and imported by `app.css` (after `stroom-button.css`, whose styles they may build on), e.g. `toolbar.css`, `focus-tooltip.css`, `split-pane.css` and `tab-bar.css`. Existing stylesheets (e.g. the 3,000 lines of `components.css`) are left as they are, and a widget's rules move into a file of its own when it is next changed.

- **BEM names.** A block is a widget, in lower-case kebab case (`toolbar`, `toolbar-row`, `focus-tooltip`); a part of it is `block__element` (`toolbar__separator`); a variant is `block--modifier` (e.g. a colour scheme). The button was renamed to this form in wave 1 of the component migration (24 October 2026): `button`, `button__content`, `button--contained-green`, in `button.css`.
- **States come from ARIA attributes, not classes.** Where an ARIA attribute says what state an element is in, the CSS reads it, so what is shown always matches what a screen reader is told: `[aria-pressed="true"]`, `[aria-disabled="true"]`, `[aria-expanded="true"]`, `[aria-orientation="vertical"]`. Modifiers are only for variants that have no ARIA meaning, and for the rare state ARIA has no attribute for (e.g. `split-pane--resizing`, while a splitter is dragged), which the stylesheet says is one.
- **One element per part, and no wrappers for effects.** Fills, borders and other decoration that only need a box are drawn with `::before` or `::after` (as `.icon-button::before` draws an icon button's hover, on and focus states), not with extra elements.
- **Motion respects `prefers-reduced-motion`.** Anything that animates continuously (e.g. a spinner) stops when the user asks for less motion.
- **Shared names stay.** Utility classes many blocks use (`max`, `form-control-border`, `allow-focus`, `sr-only`, the layout helpers) are not renamed; a block's own classes are renamed when its component converts (rule 11 of [Widget rules](#widget-rules)). The FAB's old `.face` is now `fab-button__face`.

## Interop during migration

Old presenters and new components share one ownership tree, because `MyPresenterWidget` gets a `ComponentNode` in Phase 1. Either can own the other, and every existing entry point (tabs, popups, GIN) keeps working while it is converted.

| Area | Today | During migration | End state |
| --- | --- | --- | --- |
| `MyPresenterWidget` | `PresenterWidget` subclass; `PresenterScope.register(this)` in the constructor; `dispose()` unbinds | Holds a `ComponentNode`; `own()`, `listen()`, `context()` available; `dispose()` delegates to the node, which also unbinds | Rebased on `Component`; GWTP superclass removed |
| Document tabs | `DocumentPlugin` makes the editor in `new PresenterScope().capture(...)`; `EntityCloseHandler` calls `disposeScopeOf` | `ContentManager` holds a root node per open tab and adopts the editor; closing the tab disposes it | Same, without `PresenterScope` |
| Tab bodies | `TabContentProvider` makes presenters in the captured scope; `LayerContainer` swaps them | Providers `own()` the presenters they create; `Component` implements `Layer`, so it can be a tab body | Tab providers become small components or plain factories |
| Dialogs | `PopupManager` binds on show and unbinds on hide; nobody disposes | The opener owns the dialog. `ComponentDialog.showOnce(owner, component)` owns it until hidden, then disposes it; reusable dialogs stay owned by the opener | Same |
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

## Simplifying the lifecycle

With GWTP gone, every presenter is a `Component` (`MyPresenterWidget extends Component`), but the ways to make, own, register and release have piled up: a child is made in two steps (`own(provider.get())`, 67 sites), a one-use dialog in three different ways (`ownUntilHidden` 82, `disposeWhenHidden` 30, `ComponentDialog.showOnce` 4), a bus handler in two (`listen` 15, `own(getEventBus().addHandler(...))` 35), and a clean-up in two (`onDispose()` overridden 11 times, and `onDispose(Runnable)`). Most leaks found in Phases 1 to 6 were a missed `own`. Phase 7 makes each of these one call, before the screens move to components (Phases 8 to 11), so that every conversion is simpler. Decided 23 October 2026:

### Making children

`Component` (and so every presenter) gets two methods, which replace `own(provider.get())`, `ownUntilHidden(...)` and `disposeWhenHidden(...)`:

```java
// A child made now and owned until this is disposed (a tab, a list, a settings panel)
final FieldList list = make(fieldListProvider);
// A dialog made for one use: owned until it is hidden, then disposed
makeDialog(fieldEditorProvider).edit(field, updated -> ...);
```

`own(child)` stays for a child injected into the constructor (GIN made it, so there is nothing to make). An injectable `Children<T>` (a provider that takes its owner) was considered and not chosen: GIN can't inject the object being made, so the owner would still be passed at each call, for a binding pattern of its own. The leak check and the unowned counter stay as the safety net.

### The app root

The things outside the ownership tree, the plugins, `AlertPlugin`, the managers (`DocumentPluginEventManager`, `TabSessionManager`) and `HyperlinkEventHandlerImpl`, which are app-wide singletons, get an `AppRoot` to inject: a `ComponentNode` that lasts as long as the page. Their dialogs are made with `appRoot.makeDialog(provider)`, so `disposeWhenHidden` (a static helper for exactly this case) goes, and a dialog a plugin leaves behind shows in the leak counts. Making the plugins components themselves was considered: cleaner, but it touches all 92 plugins' construction for little gain while they stay app-wide singletons.

### Registering and releasing

* An app-wide (bus) handler is `listen(type, handler)`; a handler on a widget or another object is `own(registration)`. `MyPresenterWidget.addHandler(...)` and the `own(getEventBus().addHandler(...))` form go.
* A clean-up is registered where its thing is made, `onDispose(timer::cancel)`, as components already do; the overridable `onDispose()` goes (its 11 overrides become registrations).

### App-wide screens

The 22 listener classes (`FindPresenter.FindListener` and the like) and their `bindPresenter(presenter, view, viewImpl, listener)` lines go. Each area's plugin, already an eager singleton, adds the handlers of the events that open its screens, and makes each screen through its GIN `Provider` when its event first comes, as the listeners did:

```java
// NavigationPlugin's constructor: Find opens on ShowFindEvent, made when first needed
eventBus.addHandler(ShowFindEvent.getType(), event -> findPresenterProvider.get().onShow(event));
```

An area with no plugin in its GIN module (each app installs a different set) has one small eager class instead: `DocumentDialogScreens` (the five document dialogs), `AskStroomAiScreens`, `CoreScreens` (the page's core and full screen view, in both apps) and `MainScreens` (the main screen and its content tabs).

A registry, `AppScreens` (`screens.on(type, provider, Screen::onShow)`), was planned first (decided 23 October 2026) and dropped on writing it (the same day): each event has a handler interface of its own, which a provider and a method can't be adapted to without code for each event, so `on` came down to `eventBus.addHandler` by another name; and one registry would have needed a list of each app's screens. Screens that listen themselves (each an eager singleton) were considered and not chosen: every screen would be made at start-up.

### Stories

The workbench's three overlapping calls for what a story shows (`addContent`, `disposeOnCleanUp`, `closeOnCleanUp`), each owning or closing differently (two of the leak check's failures came from that), become one rule: the story's root owns whatever the story shows, and the leak check closes it. There is one call to show a screen (`harness.show(screen)`, or `harness.showPage(page)` for a page shown on its own, which the focus check skips), one for what the story shows itself, in a frame or as a dialog (`harness.own(screen)`), and one for a popup that must also be asked to close, e.g. to stop polling (`harness.ownPopup(popup)`).

## Components, controllers and models

Decided 24 October 2026, after Phase 7. With GWTP gone, the UI is split by concern into three layers, named as a React developer would know them. The pattern is MVP with a passive view (presentational components and their containers, in React's terms).

| Layer | What it is | GIN |
|---|---|---|
| **Component** | `Foo.java` and its template `Foo.ui.xml`, both dumb: widgets, layout, Stroom CSS class names, ARIA roles and labels, and visible text. Data comes in through setters (`setRules(list)`, `setReadOnly(true)`); events go out through one callback each, set by its controller (`setOnChange(Runnable)`, `setOnSelect(Consumer<T>)`); a reusable widget that several may watch keeps a `Listeners<T>` list (`addSelectionListener` returns a registration). It has a no-arg constructor, so templates can use it, and no REST, bus, context or ownership. | Made by GIN (no binding needed) or a template |
| **Controller** | Wires a component to data and behaviour: REST calls, the bus, context (read-only, task monitor, document), dialogs, and other controllers. It pushes state into its component with setters and handles its callbacks. Controllers own controllers (the ownership tree, `make`, `makeDialog`, `listen`, `onDispose`, the REST `owner`), and the leak check counts them. | Yes |
| **Model** | The data: the shared DTOs and documents, and a state class only where several controllers share state (e.g. a dashboard's query). No observable store. | — |

Decisions:

* **Templates, not HTML in Java.** GWT has no virtual DOM: a string re-rendered replaces the DOM, losing focus, selection, scroll and editor state. UiBinder is checked when GWT compiles (fields, widget types, handlers). Repetition and conditions are in the component's Java (`setRules(list)` adds a row per rule; `setVisible`). `SafeHtmlTemplates` stay for grid cells and rows. Templates use Stroom's global class names, not `ui:style`.
* **Text in the template, in plain English.** Labels, headings, button text and ARIA labels are written in the `.ui.xml`; when i18n lands, the English is looked up in the language's JSON, as `msg("English")` is (see the i18n design). Not UiBinder's `ui:msg` (a compile per locale).
* **Always a pair.** Every screen, dialog and smart part is a component (`Foo.java`, `Foo.ui.xml`) and a controller (`FooController.java`), even when small; a reusable dumb widget needs no controller. The three files sit side by side in the area's client package.
* **A controller is given its component** in its `@Inject` constructor, with its other dependencies.
* **Smart parts attach.** A part that needs data (e.g. a feed picker that fetches) is declared dumb in its screen's template (`<s:DocSelectionBox ui:field="feedBox"/>`); the screen's controller makes the part's controller and attaches it: `feedSelector = make(docSelectionControllerProvider); feedSelector.attach(view.getFeedBox());`. Templates stay dumb; the controllers form a tree beside the components'. Not `@UiField(provided = true)`, which would put a smart part in a template.
* **Context is pushed.** Controllers watch context and call the component's setters (`setReadOnly`); components look nothing up.
* **Dialogs.** The component is the dialog's content only. Its controller shows it through the popup manager with caption, size and buttons, and handles OK and Cancel (validating, saving, closing); a dialog's controller is owned until hidden (`makeDialog`).
* **Base classes.** Today's smart `Component` (ownership, `listen`, REST owner, context) is renamed `Controller`, and `MyPresenterWidget` extends `Controller` until it goes. A new thin `Component` (a `Composite`, with no lifecycle) is the base of dumb components, and the home of shared helpers.
* **Testing.** No view interfaces (as decided in Phase 0): controllers are tested with workbench stories, pure logic with JUnit.

Added by the pilot (Phase 7a, 24 October 2026):

* **A controller's widget is its component.** It calls `initWidget(view)`, so `ControllerDialog`, `ControllerLayer` and a presenter's view setter show it unchanged. A controller attached to a part (e.g. `DocSelectionController`) has no widget; it is made with `make(provider)` and only owned.
* **A part not yet split goes in a slot.** Until a presenter is split, the component has a setter for it (`setExpressionEditor(IsWidget)`) and the controller owns the presenter and passes its widget in. A presenter used in many places can keep its API as a thin wrapper over its new controller (`DocSelectionBoxPresenter`) until its users move.
* **A set of actions is one enum.** A component with several actions has one callback, `setOnAction(Consumer<RuleAction>)`, and one setter for those that apply now, `setAvailableActions(Set<RuleAction>)`; it disables them all while read only. Where the buttons go on another part's toolbar (a list's), the component is given that toolbar and adds them, with their icons, names and toolbar name.
* **Templates live beside their Java, in every module** (changed 24 October 2026; the pilot first kept the widget module's under `src/main/resources`). The GWT build copies each project's `src/main/java` and `src/main/resources` whole, so a template beside its Java is found by every compile; `DropDownBox.ui.xml` was moved to prove it in the widget module (its stories, and both apps' compiles, pass).

Agreed 24 October 2026, after a second review of every unit against the code with master merged in (`COMPONENT_MIGRATION_ORDER.md`, "Second review, after merging master", and each unit's plan in `component-migration/wave-NN.md`): `setVisible` stays (state, not styling); templates set ARIA and the like through typed setters on Stroom's widgets; a single-element widget's root, a `PopupPanel`'s root and grid cells (`SafeHtmlTemplates`) are the only markup left in Java; and runtime values are set as custom properties with one helper, `CssProperties`, added before wave 1.

Agreed 24 October 2026, for every unit converted from here on (the full list is "Done means, for every unit" in `COMPONENT_MIGRATION_ORDER.md`): it is renamed and moved in its wave (`FooPresenter`, `FooView`, `FooViewImpl` and `FooUiHandlers` become `Foo` and `FooController` in the area's `client.component` package, `FooViewImpl.ui.xml` becomes `Foo.ui.xml` beside `Foo.java`); its template takes everything that doesn't change at runtime (structure, CSS classes, ARIA, text); it has no inline styles (fixed styles become Stroom CSS classes, values known only at runtime become CSS custom properties a class uses); every component from an earlier wave it uses is declared in its template rather than built in Java; and its stories pass with the leak check and match their screenshots before and after.

## Phased implementation

A pilot, then eleven phases, each shippable on its own and each ending by deleting an old mechanism. The pilot proves the framework on a duplicated screen without changing existing code. Phases 1 and 2 then fix today's leak and read-only bugs before any screen is rewritten; Phase 3 is where the component and callback model becomes the way new screens are written. Phases 0 to 6 (done 23 October 2026) put every presenter on the component model and removed GWTP; Phase 7 simplifies the lifecycle API, and Phases 8 to 11 move the screens themselves to components, area by area, ending with no presenters at all.

### Roadmap

| Phase | Main work | Gate |
| --- | --- | --- |
| 0 · Pilot | Dictionary editor duplicated in a new package; framework classes and tests in `stroom.widget.component.client`; workbench stories mirror the original; old presenters embedded through an adapter; no existing code changed | Comparison recorded; go ahead with Phase 1 or revise the API |
| 1 · Ownership | `ComponentNode` in presenters; roots for tabs, popups and stories; `own()` replaces `PresenterScope` | `PresenterScope` deleted |
| 2 · Context | Read-only, task monitor and document context; documents provide, fields watch; folders and one doc type first | Read-only stories pass |
| 3 · Components and callbacks | `ComponentDialog.showOnce`, REST owner; receive rules dialogs converted; components as document tabs; authoring guide published | New screens use components |
| 4 · Dirty state and the bus | `DirtyEvent` chains to callbacks; `addHandlerToSource` retired; bus kept for app-wide events | No parent-child bus use |
| 5 · Migrate by area | Documents, dashboards, admin; `HasTaskMonitorFactory` retired; `onReveal`/`onHide` audited | Every presenter owned |
| 6 · Remove GWTP | Plugin registry for proxies; `PresenterWidget` base dropped; GIN kept | No GWTP dependency |
| 7 · Lifecycle API | `make`, `makeDialog`, `AppRoot`, `listen`/`own`, `onDispose(Runnable)`, screens wired by their area's plugin; one rule for stories | One way to make, own, register and release |
| 7a · Components and controllers | `Component` renamed `Controller`; a thin dumb `Component`; pilot: receive rules (screen and `FieldEditor` dialog) and `DocSelectionBox` with its controller (attach) | The split proven; design and `AGENTS.md` updated |
| 8 · Dialogs | The 115 presenters that show themselves as a popup become component dialogs; the popup manager shows components | No dialog presenter; no listener classes |
| 9 · Documents | `DocPresenter` (51) and `DocTabPresenter` (30) become `DocumentEditor`s; tab bodies become components; tab providers go | `DocPresenter`, `DocTabPresenter` and the tab providers deleted |
| 10 · Dashboards | The dashboard, its components and their settings become components | `AbstractComponentPresenter` deleted |
| 11 · The rest, and removal | Admin and content screens, lists, the main shell; then the presenter framework | No presenters: `MyPresenterWidget` and the view types deleted |

### Phase 0: Pilot

Prove the framework in a separate package by duplicating the Dictionary editor, before any existing code changes. The original stays untouched, so the two can be compared side by side.

The Dictionary editor is chosen because it is a real document tab with three of its own tabs (Words, Imports, Effective words) and two shared ones (Documentation, Permissions). Its dirty state flows from every tab, read-only must reach every tab, it makes REST calls with a task monitor, and it embeds old presenters we would not rewrite (the Ace `EditorPresenter`, the markdown editor, the permissions tab). It already has 7 stories in `DictionaryEditorStories`.

1. Done: the framework, in `stroom.widget.component.client` (`stroom-core-client-widget`, GWT module `stroom.widget.component.Component`, inherited by the workbench): `ComponentNode`, `Component`, `ContextKey`, `ContextValue`, `Contexts` (`READ_ONLY`, `TASK_MONITOR`, `DOCUMENT`; `DIRTY` waits for Phase 4), `Listeners`, `guard()` and `ComponentChecks`, with JUnit tests (`TestComponentNode`, `TestComponent`, `TestListeners`, `TestContextValue`). `READ_ONLY` is a *narrowing* key (`ContextKey.narrowing`): every provider on the way up counts, combined with `||`. The development checks are the binding property `stroom.componentChecks` (`ENABLED` in the workbench, `DISABLED` by default), read with `System.getProperty`, so the application compiles them out; on the JVM they are on. With them off, misuse is ignored as safely as it can be (a node owned by another is moved; one given to a disposed owner is disposed).
2. Done: `PresenterAdapter`, a component that puts an existing `MyPresenterWidget` into the ownership tree: `own(PresenterAdapter.of(editorProvider::get).onReadOnly(EditorPresenter::setReadOnly))`. It takes something that makes the presenter, not a presenter, and makes it in a `PresenterScope` of its own, which it disposes when it is disposed: GWTP doesn't unbind a presenter's own presenters with it, so `dispose()` on the one presenter would leave them, and a presenter made in the workbench leak probe's scope would be disposed twice. Read-only goes to the setter given to `onReadOnly`; a provided `TASK_MONITOR` goes to `setTaskMonitorFactory` (with none provided, the presenter keeps its own). No file in the protected `com/` directory is edited (`TestPresenterAdapter`).
3. Done: the editor duplicated in `stroom.dictionary.client.component`: `DictionaryEditor` (the document), `WordsTab` (the Ace editor through the adapter), `ImportsTab` (its own UiBinder template, no view interface; the imports grid and the dictionary chooser through the adapter) and `EffectiveWordsList` (the Effective Words tab, and the words of the selected import), with Documentation and Permissions through the adapter. No GIN module entry: GIN makes them just in time. `DictionaryEditor` provides `READ_ONLY` (`setReadOnly`), which every tab watches; reads with `read(docRef, doc)`; works out dirty state by comparing the document read with `write()` on every tab's `onChange` callback; and tells its owner through `setOnDirtyChange`, `setOnSave` and `setOnSaveAs` (saving is the owner's). Tabs are made, and owned, when first shown. `ComponentLayer` (framework) shows a component in a `LayerContainer`. Differences from the original: tab content is shown at once rather than deferred; `write()` doesn't select the first import as a side effect; Effective Words breaks sorting ties as intended (see `ISSUES.md`). Not yet: a REST error handler isn't a `Consumer`, so `guard()` can't wrap it and the screen checks `isDisposed()` (Phase 3's `RestFactory.owner`); the editor holds a copy of `DocTabPresenter`'s toolbar, tab and read-only status code, which a shared document component would take over.
4. Done: `App/Dictionary/DictionaryEditorComponent` runs the 7 `App/Dictionary/DictionaryEditor` stories' own plays (shared, so both are checked alike) against the copy, and adds `LockChangeWhileOpen`, `SaveGoesToTheOwner` and `CloseWhileLoading` (the effective words fail 500ms late, after the story's root is disposed: no alert, and nothing left unowned; with the disposed check removed, the story fails on the alert). `ScreenHarness.addComponent` shows a component owned by the story's root (`getComponentRoot()`, which provides `TASK_MONITOR` and is disposed with the story's clean ups, so also when the leak probe closes it), and `LeakProbe` exposes `unowned()`. All 17 stories pass, with axe-core, and the leak check finds 0 DOM nodes and 0 listeners kept per close for both editors (heap per close 99-123 KB for the original, 100-125 KB for the copy).
5. Done: the comparison, below.
6. Optionally, open the pilot in the real app behind a development-only switch to test the `ContentManager` and `PopupManager` roots, which the workbench does not exercise.

| Measure | How it is measured | Pass if |
| --- | --- | --- |
| Size | Files and lines for the screen, excluding shared framework classes | Fewer than the original |
| Manual wiring | Hand-written read-only, task monitor and dirty plumbing in the screen | None for read-only and task monitor; dirty only through callbacks |
| Behaviour | Old and new stories run side by side with screenshot diffs and axe-core | Same results as the original |
| Leaks | Live components and story bus handlers after the story root is disposed | Zero |
| Late results | REST fixture answers after close | No callback runs |
| Story effort | Lines needed to write a new story for the screen | Fewer than the original |

#### Pilot comparison (22 October 2026)

Measured on this branch: the original screen (`DictionaryPresenter`, `DictionarySettingsPresenter`, `DictionaryListPresenter`, `WordListPresenter`, `DictionarySettingsViewImpl` and its template) against the copy (`DictionaryEditor`, `WordsTab`, `ImportsTab` and its template, `EffectiveWordsList`). Code lines leave out blank lines, comments, imports and the package line. Shared classes are left out of both: the framework (about 1,300 lines) and, for the original, `DocTabPresenter`, `DocPresenter` and the tab providers.

| Measure | Original | Copy | Result |
| --- | --- | --- | --- |
| Size | 6 files, 850 lines (546 code), and a GIN binding with a view interface | 5 files, 911 lines (606 code); no GIN entry, no view interface | **Not met.** About 80 lines of `DictionaryEditor` (Save and Save As, merging a tab's buttons into the toolbar, the read-only status) are copied from `DocTabPresenter`, which the original inherits. Without them the copy is about 830 lines, much the same as the original |
| Manual wiring | Read-only passed through 5 `onRead(..., readOnly)` overrides, 2 `setReadOnly` calls and `DictionaryListPresenter.read`; task monitor `taskMonitorFactory(this)`; dirty through `ChangeEvent` on the bus (`addHandlerToSource`) and the `DirtyEvent` chain of the tab providers | Read-only: one `provide`, 2 `watch`es, and `onReadOnly` on the 2 embedded presenters; task monitor: one `context(TASK_MONITOR)`; dirty: `setOnChange` callbacks, and `addDirtyHandler` on the embedded markdown editor (owned, so removed with it) | **Met**, but for the embedded markdown editor's dirty event, which goes when it is converted |
| Behaviour | 7 stories | The same 7 plays, shared | **Met.** All pass, with axe-core; the 7 screenshot pairs are identical (`screenshot-diff.mjs`, default fuzz, fixed clock) |
| Leaks | 0 DOM nodes, 0 listeners per close (heap 99-123 KB, the browser's records) | 0 DOM nodes, 0 listeners per close (heap 100-125 KB); 0 unowned components after close | **Met.** Both are leak free: the original through `PresenterScope`, the copy through ownership alone |
| Late results | Not checked; a late error would be shown after close | `CloseWhileLoading`: no alert after close; the story fails if the check is removed | **Met** |
| Story effort | `open()`: 18 lines | `openComponent()`: 29 lines (it is the owner: it says read-only and handles dirty and save); plays reach the editor through a static field | **Not met.** The copy's stories can do more (lock change while open isn't possible with the original without reading it again), but take more lines |

What the pilot found for the API:

- **A document component.** Every document repeats Save, Save As, the tab bar and toolbar and the read-only status, which `DocTabPresenter` gives today. A shared `DocumentComponent` (or a composed `DocumentTabs` helper) would remove about 80 lines from each document and meet the size goal.
- **`RestFactory.owner(...)` sooner.** `guard()` can't wrap a REST error handler (a `RestErrorHandler`, not a `Consumer`), so the screen checks `isDisposed()` by hand. It belongs in Phase 1 rather than Phase 3.
- **Stories reach components through statics.** A harness method that hands the play the component it shows would replace them.
- **Adapters are all named `PresenterAdapter`** in the unowned list; naming them after their presenter would make leaks easier to find.
- **`getFirst()`** and other newer collection methods aren't emulated by GWT: a JVM test passes, but the strict GWT compile fails.

**Exit:** the comparison is recorded and we decide to go ahead with Phase 1 or revise the API first.

#### After the revision (22 October 2026)

The revision is done:

- **`DocumentEditor<D>`** (`stroom.entity.client.component`), a base class for documents: Save and Save As, the tab bar and toolbar (a tab's `HasToolbar` buttons join the document's), the read-only status and `READ_ONLY`, dirty state from comparing the document with what the tabs write, and the owner's callbacks. A document adds its tabs with `addTab(tab, make, reader[, writer])`, each made and owned when first shown, and its own buttons with `getToolbar()`. It is inherited, not composed: a composed helper would need every document to pass `read`, `write`, `setReadOnly` and the callbacks through (about 30 lines each).
- **`RestFactory.MethodExecutor.owner(component)`**: neither handler (nor Stroom's default failure alert) runs once the owner is disposed, the task monitor is still ended, and with no task monitor given the owner's `TASK_MONITOR` is used. `FakeRestFactory` does the same. The copy has no `guard()` or `isDisposed()` checks left.
- **`ScreenHarness.shownComponent()` and `closeShownComponents()`** give plays the component the story shows; the stories' static fields are gone.
- **`PresenterAdapter`s are named after their presenter** (`PresenterAdapter(EditorPresenter)`), through a new `Component(String name, EventBus)` constructor.

Measured again: the copy is 5 files, 677 lines (437 code), against the original's 850 (546): **size met**. The shared part is `DocumentEditor`, 347 lines, against `DocTabPresenter`, `DocPresenter` and the tab providers' 920. Story effort: `openComponent()` is 24 lines against `open()`'s 18; the 6 more are the owner's dirty and save spies, which the original can't offer, so it is **about the same**. Behaviour, leaks and late results are unchanged: all 17 stories pass with axe-core, the 7 screenshot pairs are identical, each close keeps 0 nodes and 0 listeners, and `CloseWhileLoading` passes through `owner()` alone.

**Decided (22 October 2026):** revise, then go ahead with Phase 1. The revision adds a shared document component (Save, Save As, the tabs and toolbar, the read-only status), `RestFactory.owner(...)` (moved from Phase 3), and a harness method that gives plays the component a story shows; the copy is then measured again. The copy replaces the original after Phase 1, once `ContentManager` gives each open document tab a root: `DictionaryPlugin` then opens the copy and the original is deleted. Until then only the copy changes. The duplicate then either replaces the original or is deleted; both are never maintained.

### Phase 1: Ownership

Give every presenter an owner without changing how screens are written.

1. Done: `MyPresenterWidget`, with Stroom's other classes in the protected `com/gwtplatform/mvp/client` directory (`MyPresenter`, `Layer`, `LayerContainer`, `HandlerRegistrations`), moved to `stroom.widget.presenter.client` (GWT module `stroom.widget.presenter.Presenter`, inherited by `stroom.widget.util.Util` and the component module), decided 22 October 2026. Only imports changed (340 files); the protected directory now holds none of Stroom's code.
   Decided too: the child presenters injected into a presenter's constructor (307 in 198 classes, measured 22 October 2026) are owned explicitly, by a script that adds `own(child)` to each constructor (skipping shared ones), so that ownership nests as Phase 2's context needs; and each presenter made on demand (`provider.get()`, about 251) is owned where it is made, `own(provider.get())`, area by area, shared ones marked shared.
2. Adopt the pilot's framework classes, revised by what the pilot found.
3. Done: `MyPresenterWidget` has a node (it is a `HasComponentNode`); `dispose()` disposes the node, once however often it is called, then unbinds and calls `onDispose()`; `addHandlerToSource` registrations always belong to the node (not only within a scope); `own(child)`, `own(registration)`, `listen(type, handler)` and `isDisposed()` are added (`TestMyPresenterWidget`). `PresenterAdapter` owns its presenter's node, so context will reach it. `ComponentChecks` counts unowned components by name rather than holding them, as holding every presenter nothing owns yet kept closed screens alive in development builds.
4. Done: roots made explicit. `ContentManager` holds a root for each open tab: it owns the tab's content when the tab is opened (unless something else owns it, as the embedded query's editor is owned by its query) and is disposed when the tab is closed, so every content tab (documents, dashboards, annotations, screens such as Jobs, and those no scope covered, such as Traces, Annotation Browse, data tabs, link tabs and Document Permissions) is released when closed. The workbench story has a root (`ScreenHarness.getComponentRoot()`), which owns what `addContent` shows. Not a root per dialog in `PopupManager`: disposal is final, and many dialogs are kept by their opener and shown again, so the opener owns a dialog (step 4c).
   - Done: the child presenters injected into presenters' constructors are owned: a script added `own(child)` after `super(...)` in 178 classes (281 children), in every constructor of a presenter class, so that a child passed on to `super(...)` is owned by the base class that gets it. No presenter is shared: the 23 `asEagerSingleton` and the `in(Singleton.class)` bindings are plugins and services, and no presenter is `@Singleton`. `own()` skips a child with no node, which only a mock in a test has. All 1,197 workbench stories but the known `CredentialsScreen` `KeyStore` pass, so nothing is owned twice.
   - Decided (22 October 2026): a presenter made for each use (about 74 `provider.get()` sites, mostly a dialog per click) is owned only while it is shown: a `showOnce` helper (Phase 3's `Dialog.showOnce`, brought forward) owns it and releases it when it is hidden, as owning it for good would keep every dialog until its owner closed. Tab bodies (33) are owned by their document: `DocTabPresenter` passes itself to its `TabContentProvider` as the owner.
   - Done: presenters made on demand are owned. `TabContentProvider` takes an owner (its document, or folder) and owns each tab body it makes (the 33 `addTab(..., provider::get)` sites, and those made in a tab provider's `createPresenter`). Of the `provider.get()` calls in presenters, classified by a script and the unclear ones read: 38 in constructors are owned; 81 that make a dialog for one use are owned until hidden, with `MyPresenterWidget.ownUntilHidden` (it releases the dialog, deferred, once its `HidePopupEvent` has fired); 23 kept and reused (lazily made, cached in a map, or swapped) are owned, and released where they are replaced or closed with the new `release(child)`: Table Filter's column filters on `reset()`, the query and embedded query visualisations in `destroyCurrentVis()`, Plan B's settings when the state type changes, and the embedded query's editor when its tab is closed. All 1,197 workbench stories but the known `KeyStore` pass.
5. Done: `PresenterScope` deleted. Its call sites now own explicitly: `DocumentPlugin`, `ContentPlugin`, `DashboardPlugin` and `AnnotationEditSupport` leave the tab's root to `ContentManager` (an annotation that is never shown is disposed at once); `TabContentProvider` owns tab bodies through its owner; dashboard `Components` are owned by the dashboard (`setOwner`) and released when removed; `inScope(provider::get)` became `own(provider.get())`; `PresenterAdapter` owns its presenter, which owns its own; the workbench leak probe disposes the story's root rather than a scope. Without the scope catching them, presenters made by classes that aren't presenters were left without an owner, which the leak check found (it now names the components each close leaves unowned, from `LeakProbe.unownedNames()`): the expression tree's term editors' selection boxes (`TermEditor`, `ExpressionTreePanel` and `ExpressionTreeViewImpl` now have nodes, and `ExpressionTreePresenter` owns its view); children the constructor script had missed (classes sharing a simple name, and children passed to `super(...)` only as `child.getView()`); the children of proxy presenters (`MyPresenter` now has a node, `own()` and `dispose()`); dialogs that app-wide helpers make for one use (`MyPresenterWidget.disposeWhenHidden`, for `DataDisplaySupport`, `Menu`, `ColumnsManager`, `QueryInfo` and others, and, as they may not be shown at all, `CurrentUser`'s splash screen and `CurrentActivity`'s chooser are also disposed when their callback runs); and an unused `DataPresenter` injected into `DataPreviewTabViewImpl` (removed). The dashboard and query `TextPresenter`s drop a data fetch that is answered, or due, once they are disposed (`RestFactory.owner`). Measured with the full leak check (22 October 2026): 50 stories fail, against 71 before `PresenterScope` was removed; none fails that didn't before, and the annotation editor's leak (about 1,270 DOM nodes and 189 listeners per close, 23 stories) is gone. `ScreenHarness.disposeOnCleanUp` now has the story's root own a presenter, so a story's own dialogs are disposed.
6. Done: no presenter is shared (see step 4), so none needs `markShared()`.

**Exit:** `PresenterScope` deleted; open-close leak stories for documents, dashboards and annotations report zero live components.

### Phase 2: Context

Fix read-only and task-monitor threading once, centrally.

Decided (22 October 2026): a presenter in a tab or dialog that isn't given a task monitor shows its tasks on its tab's or dialog's, from context, rather than as an app-wide task (the main screen's spinner); Plan B is the first document type converted.

1. Done (the pilot): `Contexts.READ_ONLY`, `TASK_MONITOR` and `DOCUMENT`.
2. Done: `DocPresenter` provides `READ_ONLY` and `DOCUMENT` to everything it owns, set by `read` (and by `setDocumentReadOnly`, for a document presenter embedded in another screen rather than read, e.g. Plan B's settings in a traces document); `ContentTabPanePresenter` provides the tab's `TASK_MONITOR` on the tab's content (and still sets it on the content, replacing the one it was given while it opened), and `PopupSupportImpl` the dialog's on the dialog's presenter (`ComponentNode.setProvided`, as a dialog may be shown again). `MyPresenterWidget` has `context`, `watch` and `provide` (`TestDocPresenter`).
3. Done: `MyPresenterWidget.createTaskMonitor()` uses the task monitor set on it, else its context's, else an app-wide one (`TestMyPresenterWidget`).
4. Done for Plan B: its settings presenters and `PlanBSettingsPresenter` `watch(READ_ONLY)` (their views' `onReadOnly`), so `read(settings, readOnly)` lost its `readOnly` and no presenter passes read-only on; story `PlanBEditor` `LockChangeWhileOpen` reads the document again locked and unlocked, and every settings field follows. Done for every tabbed document and folder: `TabContentProvider.getPresenter(tab)` no longer sets each tab presenter's task monitor, as its context gives it its tab's. Not yet: the folders' tab presenters (`MetaPresenter`, `ProcessorPresenter`, `ProcessorTaskPresenter`) still take `readOnly` in `read(docRef, doc, readOnly)`, part of `HasDocumentRead`, which Phase 5 removes with the other `onRead` overrides.

**Exit:** read-only and lock-change stories pass for those screens; the pattern is documented for Phase 5.

#### Converting a screen to context (for Phase 5)

As Plan B was converted:

- **Read-only.** A presenter that shows something the user may change calls `watch(Contexts.READ_ONLY, getView()::onReadOnly)` (or its own method) in its constructor, and stops taking `readOnly` as an argument: `read(settings, readOnly)` becomes `read(settings)`, and the presenters between the document and it stop passing it on. The document provides it (`DocPresenter.read`); a document presenter used without being read (embedded in another screen) is told with `setDocumentReadOnly`. A presenter is told once it is owned, and again whenever the document is read with a different value, e.g. when its lock changes.
- **Task monitor.** Delete `setTaskMonitorFactory(...)` calls that pass on an owner's task monitor; the presenter uses its context's (its tab's or dialog's). Keep those that give a presenter its own (e.g. a list's pager spinner).
- **Check** with the screen's read-only story, and a lock change story: read the shown document again read only and then not (`ScreenHarness.shownContent()`), and expect its fields to follow.

### Phase 3: Components and callbacks

The new authoring model goes live.

1. Done: `ComponentDialog.showOnce(opener, component)` (`stroom.widget.component.client`; not `Dialog.showOnce`, as `Dialog` is already the popup panel's class). The popup manager shows presenters, so the component is shown through a small presenter (`ComponentView` is its view) that owns it; the opener owns that presenter until the dialog is hidden (`MyPresenterWidget.disposeWhenHidden`), and it returns the popup manager's `ShowPopupEvent.Builder`, so a dialog's type, caption and handlers are set as for any other (`TestComponentDialog`). A component made with the event bus is now a `HasHandlers`, so it fires app-wide requests itself (`AlertEvent.fireWarn(this, ...)`), as a presenter does; the pilot's `bus` fields are gone. `Component`, `Listeners`, `guard()` and `RestFactory.owner(...)` already existed.
2. Done: the receive rules' dialogs and Rules tab are components, in `stroom.receive.rules.client.component`, each with its own template and no view interface or GIN binding: `FieldEditor` and `RuleEditor` are dialogs shown for one use, with a callback argument for the result (`fieldEditor.edit(this, "New Field", field, otherNames, newField -> ...)`); `RuleEditor` checks the obfuscated fields itself, so its opener only gets a rule to keep. `RuleSetSettings` is the Rules tab: it watches `READ_ONLY`, gets the fields through a `Supplier` and tells its owner of changes through `setOnChange`, and `RuleSetPresenter` shows it in a `ComponentLayer` and provides `READ_ONLY` (false, as before). Converting them fixed four bugs, each with a story that fails without its fix (`ISSUES.md`, Data Receipt Rules' Rules tab). New stories: `AddFieldChecksTheName`, `EditRuleCancel`, `DuplicateRule`, `DeleteRule` and `ReadOnly` (the story's root provides `READ_ONLY`, so no read-only fixture is needed); all 9 pass, with the leak check.
3. Done, decided 22 October 2026: components open as Stroom's tabs. `DocumentEditorHost<D>` (`stroom.entity.client.component`) is the `DocPresenter` that `DocumentPlugin` opens, saves and closes: it owns a component, reads the document into it (telling a `DocumentEditor` whether it is read only), marks the tab's label while it is dirty, and turns Save and Save As into `SaveDocumentEvent` and `SaveAsDocumentEvent`. `DocumentEditor`'s tabs moved into a base class, `TabbedComponent<D>` (tabs made and owned when first shown, read into and written from, the toolbar row, and the tabs a link opens, `setCommonTab`), whose tabs may be presenters as well as components (`MyPresenterWidget` is now an `IsWidget`). `HasCommonTabs` replaces `DocumentPlugin`'s checks for `DocTabPresenter` and `LinkTabPanelPresenter`. Then:
   - The Dictionary copy replaced the original: `DictionaryPlugin` opens `DictionaryEditor` in a host, and `DictionaryPresenter`, `DictionarySettingsPresenter`, `DictionaryListPresenter`, `WordListPresenter` and `DictionarySettingsViewImpl` are deleted, with their GIN bindings. `App/Dictionary/DictionaryEditor` now shows the host, as Stroom opens it, and adds `LockChangeWhileOpen` (read again locked and unlocked, as `DocumentPlugin` does) and `SaveAsksStroomToSave`; `App/Dictionary/DictionaryEditorComponent` runs the same plays against the editor alone. All 19 pass, and each close keeps 0 nodes.
   - The folder tabs: `FolderPresenter` and `FolderRootPresenter`, near copies, became one component, `FolderTabs`, which both plugins open in a host. Its tabs (Data, Processors, Active Tasks, Permissions) are still presenters, owned when first shown; it adds them when first read, as which it has depends on whether it shows the System root. New stories: `ViewDataOnly`, `SystemRootViewDataOnly` and `PermissionsLink`.
4. Done: compared, below.
5. Done: the authoring guide is in `AGENTS.md` (GWT UI components).

| Screen | Before | After | Notes |
| --- | --- | --- | --- |
| Receive rules dialogs and Rules tab | 11 files, 1,774 lines (1,131 code): 3 presenters, 3 views, 3 view interfaces, 3 templates, and their 2 owners; 6 GIN bindings | 8 files, 1,503 lines (965 code): 3 components with their templates, and their 2 owners; no GIN bindings | Same behaviour but the 4 bugs fixed; 9 stories, leak free |
| Dictionary | 6 files, 853 lines (549 code), with a GIN binding and a view interface | 5 files, 672 lines (435 code) | Shared base: `DocumentEditor`, `TabbedComponent` and `DocumentEditorHost`, 609 lines (321 code), against `DocTabPresenter`, `DocPresenter` and the tab providers' 920 |
| Folders | 2 presenters, 362 lines (240 code) | 1 component, 128 lines (75 code) | The plugins are 5 lines longer |

Code lines leave out blank lines, comments, imports and the package line. Every story of the three screens passes (34, with the leak check). The full workbench run found one regression, fixed: `App/Main/AppShell` `SaveAsDocument` saved a Dictionary's copy over the original, as the host wrote the editor into the document it had read rather than the new one `DocumentPlugin.saveAs` gives it (`TabbedComponent.write(document)` now writes into the one given). The run then passes but for the known `CredentialsScreen` `KeyStore` story (1,206 of 1,208 before the fix), and the strict app and dashboard GWT compiles pass.

**Exit:** first conversions shipped; from here, new screens are written as components.

### Phase 4: Dirty state and the bus

Remove parent-child bus traffic.

Decided (22 October 2026): dirty state reaches the document through context, with a `Listeners` list for the few outside observers; every other `addHandlerToSource` is converted in this phase, not left for Phase 5.

1. Done: `DirtyTracker` and `Contexts.DIRTY`. Every `DocPresenter` provides it (its `onChange`, which works out again whether the document is dirty by comparing it with what its parts write), as do `DocumentEditor` and `RuleSetPresenter`. A part tells its document with `markChanged()` (on `MyPresenterWidget` and `Component`), which looks the tracker up from its owner (`ComponentNode.lookupAbove`), so a part that is itself a document, e.g. one of a document's tabs, tells the one above it. `DocPresenter` tells its owner whenever it becomes dirty or clean, and `MarkdownEditController` (with `markChanged()`) whenever its text does, so a document's Documentation tab needs no wiring. Outside observers (the full-screen window's title, the annotation's description, stories) use `addDirtyListener(Consumer<Boolean>)`, a `Listeners` list (`TestDocPresenter`, `TestComponentNode`).
2. Done: `TabContentProvider`, `DocTabProvider`, `AbstractTabProvider` and `MarkdownTabProvider` no longer re-fire dirty events (`MarkdownTabProvider` takes the event bus again since wave 8, for the `DocPresenter` that hosts its controller); `DocTabPresenter` no longer listens to them.
3. Done: every `addHandlerToSource` in a presenter is gone, with the event classes that carried them (23 deleted: `DirtyEvent`, `ChangeEvent`, `ChangeDataEvent`, `StepControlEvent`, `ChangeFilterEvent`, `DataLoadedEvent`, `StartQueryEvent`, `TimeRangeChangeEvent`, `InsertEditorTextEvent`, `ErrorEvent`, `PropertyChangeEvent`, `ComponentChangeEvent`, `DashboardContextChangeEvent`, `TableUpdateEvent`, the four floor map events, and the `Has...Handlers` interfaces of six of them). As decided in the design, a child with one listener (its owner) has a callback setter, e.g. `setOnChange(Runnable)`, `setOnStep(Consumer<StepType>)`, `setOnErrors(Consumer<String>)`; one that several may listen to has a `Listeners` list and an `add...Listener` that returns a registration for the listener to own: the selection boxes and lists (`addSelectionListener(Consumer<T>)`, told the item rather than an event), the dashboard context's component and context changes, the tables' updates, the pipeline model, the index loader and the UI config cache. Where two parties listened to one child (the floor map's query editor, the step controls in a story), the owner's one callback does both. Events between a document's tabs (the floor map's Events Query, Map and Editor tabs) go through the document, which owns all of them. A `ComponentChangeEvent` that the dashboard's input components fired from themselves reached nothing (the context only heard its own), so it is removed. Two of the conversions fixed listeners that heard the wrong sources (`ISSUES.md`, "Events between screens and their parts that reached the wrong listeners").
4. Kept on the bus, as app-wide: requests to app services (alerts, popups, opening documents), document saved/renamed/deleted, session, preference and explorer events, and the job and node lists telling each other of changes. `MyPresenterWidget.disposeWhenHidden` still listens for the popup manager's `HidePopupEvent` for its dialog (`addHandlerToSource` on the event bus itself), as the popup manager is an app service; it goes when the popup manager (Stroom's own, so not part of Phase 6) is replaced.

**Exit:** no `addHandlerToSource` in a presenter (the one above aside), and no `DirtyEvent`; `HasDirtyHandlers` deleted. Measured: 189 files changed, 1,043 lines added and 2,491 removed.

### Phase 5: Migrate by area

Convert the remaining presenters area by area, each a separate pull request with stories.

1. Order by value: document editors (27 `DocTabPresenter`, 42 `DocPresenter`), then dialogs (161 popup sites), then dashboards, then admin screens.
2. Within each area, retire `onReadOnly`, `setTaskMonitorFactory` and `HasTaskMonitorFactory` as their last users go.
3. Review each `onReveal`/`onHide` override (24) against attach and detach as its presenter is converted.

**Exit:** every presenter is owned; `HasTaskMonitorFactory`, `ReadOnlyChangeHandler` and the `readOnly` argument are gone.

Progress:

- Done (22 October 2026), documents: the `readOnly` argument is gone from `HasDocumentRead.read`, `DocPresenter.onRead`, the tab providers' `onRead` and the parts that were passed it (processing, duplicate management, query editing, pathways, Plan B settings in a traces document, script dependencies, processors and data).
  A document's opener says whether it may be changed with `DocPresenter.setReadOnly` before it reads it (`DocumentPlugin`, from the user's permission; Save As makes it editable), and that goes into its context; a document that is part of another (e.g. a settings tab) is never told, so it follows the one above it, and one nobody tells is read only.
  `isReadOnly()` is now the context's value, and parts read `context(Contexts.READ_ONLY)` when they are read, as a lock change reads the document again.
  `TabContentProvider` owns a tab's presenter before it reads into it, so that it reads as its document is (`TestDocPresenter`).
  Two parts that were told "editable" whatever their document was now follow it: the query editor in a floor map, and the query document a dashboard's embedded query opens.
  A document's settings presenter still sets its own fields and reusable editors (`editor.setReadOnly(isReadOnly())`), as an owner gives a widget its input.
- Done (22 October 2026), task monitors: `HasTaskMonitorFactory` is gone. The tab pane and the popup manager only provide the tab's or dialog's `TASK_MONITOR` (no longer setting it on the content, nor resetting it when a tab closes), and `DocumentPlugin` no longer gives an opening document the opener's.
  Presenters stopped passing their task monitor on to the presenters they own (ten overrides and twelve `child.setTaskMonitorFactory(this)` calls); plain models (query, search and selection list models, completion providers) are given their presenter once, in its constructor, so they show what it would.
  `setTaskMonitorFactory` stays on `MyPresenterWidget` and `MyPresenter` for a presenter given one of its own (e.g. a refresh button's spinner), and `MyPresenter` now falls back to its context's, as `MyPresenterWidget` does (`TestMyPresenter`).
  A dashboard component's settings dialog is now owned by its component (it was never released).
- Done (22 October 2026), `ReadOnlyChangeHandler` is gone. `DocPresenter.read` no longer calls a view's `onReadOnly` behind its presenter's back: each view that shows read-only declares `onReadOnly` itself, and its presenter calls `watch(Contexts.READ_ONLY, getView()::onReadOnly)` (19 settings presenters, and the two HTTP dialogs, which lost their `readOnly` argument and follow the document that opens them). Four views whose `onReadOnly` did nothing no longer have one. Every `onReadOnly` only set its fields' state from the flag, so being told when the presenter is connected, and on a change, rather than before each read, changes nothing.
- Done (22 October 2026), the `onReveal`/`onHide` audit: of the 24 counted, only five were `PresenterWidget` overrides left (the rest were event handlers or already gone), and none of them ever ran. GWTP reveals a presenter only through a revealed parent's slot, and Stroom shows tab content and dialogs in a `LayerContainer` or a popup, outside that chain; a probe that threw from each override failed none of the 48 stories that show those screens. Deleted: the refresh-on-reveal of the data retention impact list and the two global property lists (their data loads elsewhere), `QueryEditPresenter.onHide`'s completion clean-up (its editor unregisters its completions when it is disposed) and an empty `ExplorerPopupPresenter.onHide`. `AttachScope` isn't needed yet; it is made when a component first needs to act on attach. `MyPresenter.onReveal` (`onFirstReveal`) and `forceReveal` belong to the proxy presenters, which Phase 6 replaces.
- Done (22 October 2026), ownership: every presenter got from a GIN provider is owned. A search for `provider.get()` with nothing owning the result found eleven that weren't (listed in `ISSUES.md`, "Presenters nothing owned"); tab bodies, dashboard components, plugin editors and `TabbedComponent` tabs already were. `MyPresenter` gained `release`. The exception is a popup made by a plain widget (the date, time and schedule boxes): a widget isn't in the ownership tree, and its popup is unbound whenever hidden, so it holds nothing app-wide and goes with the widget.

### Phase 6: Remove GWTP

1. Replace the proxy presenters with a plain plugin and content registry, and drop code splitting (measuring load time before and after).
2. Rebase `MyPresenterWidget` on `Component` and drop `PresenterWidget`, `HandlerContainerImpl` and slots.
3. Keep GIN.

**Exit:** no GWTP dependency in the build.

Progress:

- Done (22 October 2026), GWTP is out of the build: no `build.gradle`, the version catalogue, `NOTICE.md` or a GWT module names it, and nothing imports `com.gwtplatform`.
  The few types Stroom used keep their simple names in `stroom.widget.presenter.client`: `View`, `ViewImpl`, `UiHandlers`, `HasUiHandlers`, `ViewWithUiHandlers`, `HandlerContainer(Impl)` (bound once GIN has made it, as GWTP's was), `RevealContentEvent`, `RevealRootContentEvent`, `RootPresenter` and `gin.AbstractPresenterModule`; about 860 files changed only their imports.
- Done, proxies: the 23 GWTP proxies that handled events or slots are each a small `public static final class ...Proxy` at the bottom of their screen, bound as an eager singleton by `bindPresenter(presenter, view, viewImpl, proxy)`. It adds the screen's event handlers and makes the screen through a GIN `Provider` when an event first comes; a layout screen's slots are `GwtEvent.Type<RevealContentEvent.Handler>` constants, filled through `RevealContentEvent.showIn(eventBus, slot, presenter::get)`, deferred as GWTP did (`forceReveal()`, then `setInSlot`). The four with neither (About, Login, Reset Password, Authentication Error) lost their proxy. The inactive place manager, the token formatter and `DelayedBindRegistry` went with them.
- Done, code splitting dropped: GWTP's code-split proxies, the one GIN `AsyncProvider` (the document permissions dialog) and the 132 `AsyncProvider` getters of 46 ginjectors, which only GWTP's generator used. The 44 ginjector interfaces this left empty are deleted. Measured on the production compile (one permutation): the first download grows from 5.62 MB (1.52 MB gzipped) to 5.88 MB (1.58 MB gzipped), +4.4%, and the 17 deferred fragments (0.30 MB, 82 KB gzipped) are gone; all the code together is 38 KB smaller, as GWTP's went with it. Page load wasn't timed in a browser; the difference is 67 KB more gzipped before the first screen, and none later when a screen first opens.
- Done, `MyPresenterWidget` no longer extends `PresenterWidget`: it is a `HandlerContainerImpl` with its view, the event bus and its node (and, until 23 October 2026, its slots). GWTP's reveal tree went, as none of its hooks ran in Stroom (Phase 5); `isRevealed()` is now whether it is the page (`RootPresenter`) or in a slot of one that is, which `MyPresenter.forceReveal()` checks, so the tab pane isn't cleared when a tab opens (`TestMyPresenterWidget`, `TestMyPresenter`, `TestHandlerContainerImpl`). Every story gives the results it gave before (the 49 environment failures are unchanged).
- Done (23 October 2026), slots are gone. The 16 presenters with fixed child slots set their children's views through typed view setters (`getView().setJobListView(jobListPresenter.getView())`), as the rest already did. The page is put together with plain calls: `MainPresenter` is made with the explorer and the document tabs in its view, and its proxy puts it in the core screen (`CorePresenter.setContent`) for `ShowMainEvent` and for a tab opened while it isn't shown; the full screen view's proxy does the same. `RevealContentEvent`, the slot constants, `MyPresenterWidget`'s slot methods and `isRevealed()` are deleted; the main screen, the explorer, the tabs and the full screen view are plain presenters, with no reveal methods.
- Done (23 October 2026), revealing is gone. A dialog screen's `revealInParent()` became its own `show()`, called where it called `forceReveal()`, and the overrides that did nothing were deleted. The sign in, password reset and authentication error pages extend `SignInPagePresenter`, whose `show()` makes the page the whole browser page (`Page.show`) and then calls the page's `onShown()` (hiding the loading message, focusing); a workbench story adds the page to itself and calls `onShown()`, so `IdpPage`'s JSNI went. The apps put the core screen in the page with `Page.show(core)`. `MyPresenter`, `RootPresenter` and `RevealRootContentEvent` are deleted: every presenter is a `MyPresenterWidget`.
- Done (23 October 2026), no GWTP names are left: the proxy classes are listeners (`FindPresenter.FindListener`, bound by `bindPresenter(presenter, view, viewImpl, listener)`), and the apps' `gin.ginjector` configuration property, which only GWTP's proxy generator read, is gone.
- Done (23 October 2026), `MyPresenterWidget` is a `Component`, and presenters have no bind lifecycle: a presenter registers its handlers when it is made (`own(registration)`, `listen(type, handler)`) and they last until it is disposed, as a component's do. The 193 `onBind()` overrides moved into their constructors (base classes' too: what they use is set in their own constructors) and the `onUnbind()` work into `onDispose()`. A dialog's handlers used to be registered only while it was shown, as the popup manager bound it to show it and unbound it when hidden: they now last while it is hidden too, which matters only for app-wide events, and the few dialogs that listen to them do nothing while hidden (checked one by one); what a dialog did each time it was bound (wiring callbacks and view handlers) now runs once, so re-showing one no longer adds its handlers again. Dashboard components and rule tabs no longer unbind themselves when closed or removed: their owner (the dashboard, or the tab's root) disposes them.
- Done (23 October 2026), `HandlerContainer` and `HandlerContainerImpl` are deleted. The plugins, `AlertPlugin` and `HyperlinkEventHandlerImpl` register their handlers when GIN makes them, for the application's life (their `onBind()` moved into their constructors); a tab provider keeps what it registers on the presenter it makes (`own(registration)`) until its document is disposed, when `TabContentProvider` releases it. Widgets keep their own `EventBinder`, which binds on attach and unbinds on detach. Nothing in Stroom is bound or unbound any more.

### Phase 7: Lifecycle API

Make each lifecycle step one call, everywhere, before any screen is rewritten (see [Simplifying the lifecycle](#simplifying-the-lifecycle)).

1. Done (23 October 2026): `make(provider)` and `makeDialog(provider)` on `Component`, with tests (`TestComponent`, `TestMyPresenterWidget`). 69 `own(provider.get())` became `make(...)`, 79 `ownUntilHidden(...)` and the 7 presenter uses of `disposeWhenHidden(...)` became `makeDialog(...)`, and `ownUntilHidden` is deleted. Both take GIN's `com.google.inject.Provider`, not `javax.inject.Provider`: the JVM tests of `stroom-core-client` run with Guice 7, whose `Provider` doesn't extend javax's (and javax's isn't on that classpath), so the 19 files that injected javax's now inject GIN's. `own(make.get())` in `TabbedComponent` stays, as its `make` is a `Supplier` given by the caller. Until Phase 8 (when the popup manager shows components), `makeDialog` takes a presenter; a component dialog is still shown with `ComponentDialog.showOnce`.
2. Done (23 October 2026): `AppRoot` (`stroom.widget.component.client`, a GIN singleton holding a root `ComponentNode`), with `makeDialog(provider)`, tested in `TestAppRoot`. The 11 injected classes that aren't components and made one-use dialogs (`Menu`, `CurrentUser`, `CurrentActivity`, `DataDisplaySupport`, `QueryInfo`, `AnnotationManager`, `QuickFilterContextHandlerFactory`, `DocumentAssetQuickUploadPresenter`, `TabSessionManager`, `HyperlinkEventHandlerImpl`, `UserPreferencesPlugin`) inject it. The helpers a presenter makes (`TabManager`, `ColumnsManager`, `QueryTableColumnsManager`, and the static `UserAndGroupHelper`) are given `() -> makeDialog(provider)` by it instead, so their dialogs are the presenter's rather than the app's. `disposeWhenHidden` is now package-private in `ComponentDialog`, shared by `Component.makeDialog`, `ComponentDialog.showOnce` and `AppRoot`. In the workbench, `ScreenHarness.getAppRoot()` stands for Stroom's, lasting as long as the harness (not disposed with a story's clean ups, as `Menu` keeps it across the leak check's reopen). Not done: the date, time and schedule boxes, which keep one popup and show it again (so it isn't a one-use dialog) and are made by UiBinder (so can't be injected); their popups move with Phase 8, when dialogs become components.
3. Done (23 October 2026): `listen(type, handler)` for every bus handler (the 35 `own(getEventBus().addHandler(...))`), `own(registration)` for the rest; `MyPresenterWidget.addHandler` deleted. Its two other callers were child-to-owner notifications sent over the bus and heard from every source, both bugs (see `ISSUES.md`, "Events between screens and their parts that reached the wrong listeners"): `AppPermissionsEditPresenter` now has `setOnChange` and `QueryResultTablePresenter` `setOnRefreshRequest`, `RefreshRequestEvent` is deleted, and `ColumnSelectionPresenter` keeps its value changes off the bus. The registrations left on the bus outside `listen` are the plugins', the managers' and the listener classes' (for the app's life; the listener classes go in step 5).
4. Done (23 October 2026): `onDispose(Runnable)` for every clean-up; the overridable `onDispose()` deleted. Its 9 overrides are registrations at the end of their constructors (a longer body became a private method, e.g. `VisPresenter.releaseVis`), and `RuleSetPresenter` removes itself from the save registry where it adds itself. `AbstractEditorPresenter`'s own `disposed` flag went too: `isDisposed()` is true from the start of disposal. The clean-ups still run after what a presenter owns is disposed and its registrations are removed (`ComponentNode.dispose`), as the override did.
5. Done (23 October 2026): the 22 listener classes and `bindPresenter(..., listener)` deleted. Their handlers moved into their area's plugin (`AlertPlugin`: Prompt; `AnnotationEditSupport`: Find Annotation; `NavigationPlugin`: Find, Recent Items, Find in Content, Edit Tags, Remove Tags; `ImportConfigPlugin`, `ExportConfigPlugin`, `DependenciesPlugin`: Import, Import Confirm, Export, Dependencies Info; `TaskManagerPlugin`: User Task Manager), or, where the area has no plugin in its GIN module, into one class for the area (`DocumentDialogScreens`, `AskStroomAiScreens`, `CoreScreens`, `MainScreens`), which keep the order the listeners were made in (the main screen is shown before a tab opens in it). `AbstractPresenterModule.bindPresenter(presenter)` binds a screen whose view is bound elsewhere. No `AppScreens` (see [App-wide screens](#app-wide-screens)). In the workbench, the shell story (`ShellScreen`) no longer wires the dialogs itself, as the listeners did: with the plugins it makes now wiring theirs, Find and Export opened twice; it gets `DocumentDialogScreens` from its injector instead.
6. Done (24 October 2026): the workbench harness's one rule: the story's root owns what the story shows. `harness.show(screen)` (a component or a presenter; it was `addContent` and `addComponent`) and `harness.showPage(page)` (was `addPage`) fill the canvas, `harness.own(screen)` (was `disposeOnCleanUp`) owns what the story shows itself, and `harness.ownPopup(popup)` (was `closeOnCleanUp`, which didn't own) owns a popup and asks it to close; the stories moved onto them, and the old calls deleted. Not `showPopup`, as planned: the stories show their popups through Stroom's events, as Stroom does, so the harness only owns them. `StoryDocumentPlugins.closeOnCleanUp`, which closes a tab, is now `closeTabOnCleanUp`.
7. Done (24 October 2026): the authoring guide in `AGENTS.md` updated to the new calls: presenters follow the component rules, GIN's `Provider`, a "Register and release" rule (`listen`, `own(registration)`, `onDispose(Runnable)`) and the stories' one rule (`show`, `own`, `ownPopup`). The workbench's comments that still named the listener classes (the story modules' and stories') name the plugin or area class that replaced them.

**Exit:** one way each to make a child, show a dialog, register a handler and release something; `ownUntilHidden`, `disposeWhenHidden`, `own(provider.get())`, the listener classes and the overridable `onDispose()` are gone.

### Phase 7a: Components and controllers

Prove the split (see [Components, controllers and models](#components-controllers-and-models)) before any screen is converted, so that Phases 8 to 11 convert straight into it.

1. Done (24 October 2026): `Component` renamed `Controller`, one mechanical commit; `MyPresenterWidget extends Controller`. With it `ComponentNode`, `HasComponentNode` (`getComponentNode`), `ComponentDialog`, `ComponentLayer`, `ComponentView`, `ComponentChecks` (the `stroom.componentChecks` property) and `TabbedComponent` became `Controller...`, and the framework's comments say controller. Kept: the package and GWT module (`stroom.widget.component`, which the dumb `Component` joins), the dashboard's unrelated `Component`, the pilot classes, story titles and the harness's method names. All 1,570 stories pass with the leak check.
2. Done (24 October 2026): the thin `Component` base, a `Composite` with no lifecycle, in `stroom.widget.component.client`. Its one shared helper is `setReadOnly(boolean)` (final; `onReadOnlyChange` applies a change, called only when the value changes), so that a controller can push read-only to any component. No JUnit test: a `Composite` can't be made on the JVM (GWT's widget classes call `GWT.create` as they load), and implementing `IsWidget` instead, as `Controller` does, would lose what templates set on a widget (`styleName`, `visible`, DOM handlers); the pilot's stories test it.
3. Done (24 October 2026), one commit each, every one with its stories passing with the leak check (the last with all 1,571):
   - The receipt rules' dialogs and Rules tab: `FieldEditor`, `RuleEditor` and `RuleSetSettings` (until then smart, one class each) are now dumb components, with `FieldEditorController`, `RuleEditorController` and `RuleSetSettingsController`. Validation, the obfuscation warning, the dialogs, the expression editor and the rule list (presenters, in slots) and read-only are the controllers'; the Rules tab's seven buttons are the component's (`RuleAction`, one callback), and are disabled by its `setReadOnly`, which the ReadOnly story checks.
   - The document picker: `DropDownViewImpl`'s widget code became a dumb, template-usable `DropDownBox` (which the view now wraps, so the user and asset pickers are unchanged), and `DocSelectionBoxPresenter`'s logic became `DocSelectionController`, with `attach(box)` (its REST call now also has the controller as its owner). `DocSelectionBoxPresenter` is a thin wrapper over the controller, so its 46 users are unchanged.
   - The attach pattern in a template: the analytic notification's stream destination (`AnalyticStreamDestinationPresenter`, its view interface, `ViewImpl` and `UiHandlers`, and its two GIN bindings) is now `AnalyticStreamDestination` (its template declares `<dd:DropDownBox ui:field="destinationFeed"/>`, and the Set Default button's title moved into it) and `AnalyticStreamDestinationController`, which makes a `DocSelectionController` and attaches it. A new story, `StreamDestinationFeedPicker`, checks the picker is attached.
4. Done (24 October 2026): compared, the rules revised (the four added at the end of [Components, controllers and models](#components-controllers-and-models)), this design's model sections rewritten in its terms ([Controllers and ownership](#controllers-and-ownership)), and the authoring guide in `AGENTS.md` rewritten.
   - **Files.** A screen that was a presenter (presenter, view interface, `ViewImpl`, template, `UiHandlers`, two GIN bindings) has fewer: the stream destination went from four Java files (258 lines) and a template to two (223 lines) and a template, and needs no binding. A screen that was already one smart class gains a file: each receipt rules part grew by its controller's header and fields, about 40 lines (the three went from 714 lines in three files to 953 in six, with the Rules tab's action enum).
   - **Wiring.** The controller's constructor reads as a list of what the screen does (`view.setOnAction(this::onAction)`, `feedSelection.attach(view.getDestinationFeedBox())`); the component reads as its template's API. Nothing reaches across: no component looks anything up.
   - **Stories.** None needed changing apart from the picker's construction (`new DropDownViewImpl()`); the template's text (titles) is checked where it was set in Java before.
   - **Tests.** A `Composite` can't be made on the JVM, so components and controllers are tested in stories; logic worth a JUnit test (e.g. which rule actions apply) can be a static method or a plain class beside the controller.

**Exit:** the pilot's screens pass their stories with the leak check, and the rules are written down. Phases 8 to 11 convert each screen into a component and a controller.

> **Proposed 24 October 2026, for agreement:** Phases 8 to 11 below were planned top down (dialogs, documents, dashboards, the rest). A review of every client class (`COMPONENT_MIGRATION_ORDER.md`: 608 units to convert, measured and ordered bottom up by what each embeds) proposes 15 waves instead: widgets first (waves 1 to 6), then composites (7 and 8), small dialogs (9), lists (10), larger dialogs (11), tabs (12), the query and dashboard stack (13), document editors (14), and the screens and shell (15). Once agreed, it replaces the phases below.

### Phase 8: Dialogs

The 115 presenters that show themselves as a popup (`ShowPopupEvent.builder(this)`) are small, self-contained, and the ones most often made for one use, so they go first and prove `makeDialog`.

1. Each dialog becomes a `Component` that owns its UiBinder template (no view interface), with a method that shows it and takes a callback for its result (as `FieldEditor.edit` does), made with `makeDialog(...)` by its opener.
2. Area by area (documents' dialogs, explorer, security, processing, dashboards, admin), each with workbench stories for its default, read-only, OK and Cancel paths, and the leak check.
3. App-wide dialogs (Find, Recent Items, Copy, Move, Rename, Info, Export, Import, Edit Tags, About, the AI chat) made by their area's plugin (or area class) when their event comes, as the presenters are now.
4. The popup manager shows components; `ComponentView` (the presenter that carried a component into a popup) goes.

**Exit:** no presenter shows itself as a popup; `ShowPopupEvent` takes a component; `ComponentView` deleted.

### Phase 9: Documents

1. Each `DocPresenter` (51) and `DocTabPresenter` (30) becomes a `DocumentEditor` (Save, Save As, read-only and dirty state), opened by its plugin through `DocumentEditorHost`, as the Dictionary and folders are.
2. Tab bodies become components added with `TabbedComponent.addTab(tab, make, reader[, writer])`; the 68 tab providers (`AbstractTabProvider`, `DocTabProvider`) become those `make`/`read`/`write` functions.
3. The settings presenters shared between documents (processing, notifications, duplicate management, query editing) become components their documents own.
4. Document by document, the most used first (pipelines, feeds, XSLT, dictionaries, queries, analytics), each with its stories and the leak check.

**Exit:** `DocPresenter`, `DocTabPresenter`, `TabContentProvider`, `AbstractTabProvider` and `DocTabProvider` deleted; `DocumentPlugin` opens only `DocumentEditor`s.

### Phase 10: Dashboards

1. The dashboard document, its layout (`FlexLayout`, `TabLayout`) and the 9 dashboard components (table, query, vis, text, inputs, embedded query, key-value, table filter, list input) become components; their settings dialogs follow Phase 8's pattern.
2. `Components` makes and owns them with `make(...)`; the dashboard context stays a context value.

**Exit:** `AbstractComponentPresenter` and the dashboard presenters deleted.

### Phase 11: The rest, and removal

1. The content tabs (`ContentTabPresenter`: jobs, nodes, caches, properties, users, tasks, data and processing screens), the lists inside them, and the main shell (`CorePresenter`, `MainPresenter`, `NavigationPresenter`, `ContentTabPanePresenter`, `FullScreenPresenter`) become components.
2. The sign in pages (`SignInPagePresenter`) become components (done in wave 11: `SignInPageController`).
3. The presenter framework is deleted: `MyPresenterWidget`, `View`, `ViewImpl`, `ViewWithUiHandlers`, `UiHandlers`, `HasUiHandlers`, `PresenterAdapter`, `DocumentEditorHost`'s presenter side, and `AbstractPresenterModule`'s presenter bindings (the 717 `bindPresenterWidget` lines in the app and workbench modules).

**Exit:** no presenters; GIN modules bind services and the few singletons, and components are made just in time.

Each of Phases 8 to 11 converts area by area, one pull request each, with its workbench stories, the leak check, and the full story run; nothing is converted without stories.

## Risks and open questions

The main risk is a long period with two styles side by side; each phase therefore ends with something removed, not only something added.

### Risks

| Risk | Effect | Mitigation |
| --- | --- | --- |
| Lifecycle behaviour moves during migration | Handlers fire after close, data reloads on tab switch, focus lands in the wrong place | Convert one area at a time; workbench stories and screenshot baselines for every converted screen; leak counter in development builds |
| A shared (singleton) presenter gets owned | Closing one document breaks it for every other | Development-build check in `own()`; list of known singletons reviewed in Phase 1 |
| Two styles for a long time | Authors unsure which to use; reviewers check both | New screens must use components from Phase 3; a short authoring guide; each phase retires an old API |
| In-house framework to maintain | Knowledge held by few people | Keep the API to the classes in this document; full Javadoc and tests; no general-purpose features |
| GIN is also unmaintained | Generator-based injection blocks any future J2CL move | GIN is kept (decided 23 October 2026); components use only constructor injection |
| Attach-based lifecycle differs from reveal | Code relying on `onReveal()` ordering behaves differently | Audit `onReveal`/`onHide` overrides when their presenter is converted, not in bulk |
| A reused dialog made with `makeDialog` | Disposed when first hidden, so showing it again fails | `makeDialog` only for one use; a dialog shown again is owned with `own` (or `make`); a development-build check throws on showing a disposed component |
| Moving 465 presenters takes a long time | Two styles for months; conversions stall | Phases 8 to 11 each end by deleting a framework class, so progress is visible and the old style can't creep back; new screens are components already (Phase 3) |

### Open questions

- [x] Package and module for `ComponentNode`, `Component` and `Contexts`: `stroom.widget.component.client` in `stroom-core-client-widget` (decided 22 October 2026).
- [x] Does `Component` extend `Composite`? No: it implements `IsWidget`, holds its root widget and hears attach and detach through `addAttachHandler`, so ownership and context are tested on the JVM (decided 22 October 2026).
- [x] Does the pilot open in the real app (Phase 0 step 6)? No: workbench only; the tab and popup roots are tested in Phase 1 (decided 22 October 2026).
- [x] How strict are development checks? Misuse in `own()` (a disposed child, owning twice, a singleton) throws in development builds, and a registry counts unowned components, shown in the workbench and checked by `LeakProbe`; production builds leave both out (decided 22 October 2026).
- [x] Do we keep a view interface for components with complex UiBinder layouts, or let the component own its UiBinder template directly? The component owns its template; a view interface is added only when a second implementation or a test needs one (decided 22 October 2026).
- [x] Should `RestFactory` take an owner so in-flight requests are cancelled on dispose, or is dropping late callbacks with `guard()` enough? `RestFactory.owner(component)` drops the callback and ends the task monitor once the owner is disposed; requests are not cancelled (decided 22 October 2026).
- [x] Where do proxy presenters (code-split plugins) go once GWTP is removed: a plain plugin registry, and do we still need code splitting? A plain plugin registry: each plugin, an eager singleton, makes its presenter through a GIN `Provider` when first needed. Code splitting is dropped: in the 7.13 build it defers about 330 KB of a 4.8 MB permutation (~7%); load time is measured before and after (decided 22 October 2026).
- [x] Is GIN replaced by Dagger 2 as part of Phase 6 or left in place? Left in place: GWTP is removed with GIN kept (decided 22 October 2026), and GIN stays: it is not to be replaced (decided 23 October 2026).
- [x] How does a component make a child it owns, in one step? `make(provider)` and `makeDialog(provider)` on `Component`; not an injectable `Children<T>` (decided 23 October 2026).
- [x] What owns the plugins' and managers' dialogs? An injectable `AppRoot` for the page's life; the plugins stay singletons rather than components (decided 23 October 2026).
- [x] How do app-wide screens hear the events that open them? Their area's plugin adds the handlers and makes each screen through its `Provider`; an area with no plugin has one small class for it. The listener classes go. (Decided 23 October 2026: first one registry, `AppScreens`, then, on writing it, this; see [App-wide screens](#app-wide-screens).)
- [x] Which clean-up form stays? `onDispose(Runnable)`; the overridable `onDispose()` goes (decided 23 October 2026).
- [x] Is the story harness simplified? Yes: one rule, the story's root owns what it shows (decided 23 October 2026).
- [x] In what order do the presenters move to components? Dialogs, then documents, then dashboards, then the rest and the framework's removal (decided 23 October 2026).
- [x] Does UiBinder set a button's icon from an `svg="..."` attribute? Yes (`Widgets/Buttons/Toolbar` `Template`).
- [x] Do menus also use `SvgPresets`, and what replaces them there, before `SvgPresets` can be deleted? They did; menus now take an `SvgImage`, and `SvgPresets` is deleted (Toolbars, Migration step 5).
