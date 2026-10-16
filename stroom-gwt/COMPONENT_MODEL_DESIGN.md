# Component and Callbacks Model for the Stroom UI

Draft, 9 October 2026. This file is the design of record. It began as an export of an earlier
draft (https://claude.ai/code/artifact/e93329dc-1546-4557-9591-79a6246f84a3), which is no longer
kept up to date.

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

- **Its own buttons.** A toolbar's buttons are `ToolbarButton` and `ToolbarToggleButton`: a `<button class="toolbar-button">` holding its icon (`toolbar-button__icon`), styled only by `toolbar.css`. They replaced the older icon buttons (`InlineSvgButton` and its toggle, whose `icon-button` and `face` markup and rules are gone), looking the same in every state (`Widgets/Buttons/ToolbarButtonStates`). They are real buttons: Enter and Space press them natively.
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
- Done: `PagerView` exposes its actions `Toolbar` once (`getToolbar()`), and the list presenters' and views' pass-through methods are `getToolbar()` too. `PagerView.addButton(ButtonView)` stays for the button widgets built on `ToolbarButton` (e.g. table expand and collapse) until they can be made in a toolbar.

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
- **Maximise.** A splitter's menu (a right click or long press on it, or the context menu key or Shift+F10 when it has the focus) offers to maximise the pane either side of it, or to restore them, so a pane can be resized without dragging (WCAG 2.5.7). Alt+M (`KeyBinding.Action.MAXIMISE_PANE`, hinted in the menu) maximises the pane holding the focus, in the split pane nearest it, and restores it. A maximised pane takes all the space and the others none (their splitters stay, to drag them back); moving a splitter carries on from the panes as they are shown. The menu is Stroom's, asked for with `MenuRequest`, which bubbles up the page to the one relay `MainPresenter` installs, as a widget has no event bus to fire `ShowMenuEvent` on; `ScreenHarness` and `StoryPopups.withMenuRequests()` relay it in the workbench. Key bindings are still fixed in `KeyBinding`, so Alt+M will become a user preference with the rest.
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

## CSS for new widgets

New widgets get their own stylesheet, named after the widget's block, in `stroom-app/src/main/resources/ui/css/` and imported by `app.css` (after `stroom-button.css`, whose styles they may build on), e.g. `toolbar.css`, `focus-tooltip.css`, `split-pane.css` and `tab-bar.css`. Existing stylesheets (e.g. the 3,000 lines of `components.css`) are left as they are, and a widget's rules move into a file of its own when it is next changed.

- **BEM names.** A block is a widget, in lower-case kebab case (`toolbar`, `toolbar-row`, `focus-tooltip`); a part of it is `block__element` (`toolbar__separator`); a variant is `block--modifier` (e.g. a colour scheme). `Button.css` already uses this form, with capitalised blocks (`Button__content`, `Button--contained-green`), which stay as they are.
- **States come from ARIA attributes, not classes.** Where an ARIA attribute says what state an element is in, the CSS reads it, so what is shown always matches what a screen reader is told: `[aria-pressed="true"]`, `[aria-disabled="true"]`, `[aria-expanded="true"]`, `[aria-orientation="vertical"]`. Modifiers are only for variants that have no ARIA meaning, and for the rare state ARIA has no attribute for (e.g. `split-pane--resizing`, while a splitter is dragged), which the stylesheet says is one.
- **One element per part, and no wrappers for effects.** Fills, borders and other decoration that only need a box are drawn with `::before` or `::after` (as `.icon-button::before` draws an icon button's hover, on and focus states), not with extra elements.
- **Motion respects `prefers-reduced-motion`.** Anything that animates continuously (e.g. a spinner) stops when the user asks for less motion.
- **Shared names stay.** Names used across many stylesheets or built into HTML by hand (`.icon-button`, its `.face`, `.button-container`) are not renamed; new code uses them as they are.

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
- [x] Does UiBinder set a button's icon from an `svg="..."` attribute? Yes (`Widgets/Buttons/Toolbar` `Template`).
- [ ] Do menus also use `SvgPresets`, and what replaces them there, before `SvgPresets` can be deleted?
