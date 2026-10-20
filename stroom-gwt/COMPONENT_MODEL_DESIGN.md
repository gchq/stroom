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
  through the shared views' `setAccessibleName` (`QuickFilterPageView`, `QuickFilterDialogView`,
  `ExplorerPopupView`). A selection box's list's filter is "Filter" and the box's name.
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
- **`LineColInput`** (`line-col-input`, `linecolinput.css`) is a text field for `line:column`; a
  value that is no such position is marked invalid (`FieldValidity`), rather than turned red.

Stories `Widgets/Inputs/TextInput` (`Basic`, `MultiLine`, `Invalid`, `DisabledAndReadonly`) and
`Widgets/Inputs/LineColInput` (`InvalidValueIsMarked`).

Not yet done: the bare element rules in `stroom.css` (to go once every input has a block); nine
fields whose template replaces the class with `styleName` (so they get those rules instead); it
hasn't been tried with a screen reader.

## Tick box

The tick box is the BEM block `tick-box` (`tick-box.css`), in two forms that look the same:

- **The widget** (`CustomCheckBox`): a `label` element wrapping a native check box
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
- **CSS.** `radio-button.css`: the BEM block `radio-group`, its `__option`, `__input` and `__label`.
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

Not yet done: its syntax help popup (`quickFilter-tooltip`, also the help manager's tooltip) and its
Advanced Query dialog (`advancedQuickFilter-*`), with the popups and dialogs (session 3); it hasn't
been tried with a screen reader.

## Pickers

The document and user pickers (`DocSelectionBoxPresenter`, `UserRefSelectionBoxPresenter`, drawn by
`DropDownView`) are buttons (`aria-haspopup="dialog"`) that open a modal dialog to choose from: a
quick filter over the explorer tree (`ExplorerPopupPresenter`) or a grid of users and groups
(`UserRefPopupPresenter`). Enter or Space on a picker opens it.

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
(`DropDownView.setProblem(short, full)`). The message is short ("'Deleted Feed' can't be found, or
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

`SvgIconBox` (`stroom.svg.client`, styled by `svg-icon-box.css`) is a field with an icon inside the end of its border that opens what the field chooses from: a selection box's drop-down arrow, a date box's calendar, a time or schedule box's clock. The icon is a picture, not a button: the field opens the same thing from the keyboard (Enter, and a click on a selection box that can't be typed in), so it is hidden from screen readers (`aria-hidden`) and is no tab stop, though a click on it opens it too. It has no hover or focus states.

- **Names.** The block is `svg-icon-box`; the field (the wrapped widget's first input) is `svg-icon-box__field`, padded clear of the icon (its 26px and 4px more, see Field ends); the icon is `svg-icon-box__icon` (a 26px strip over the end of the field) holding `svg-icon-box__image`.
- **Drop-down arrow.** `setDropDownWidget` gives the box the value spinner's arrow (`ARROW_DOWN`), 10px, in `--icon-colour__black`, as the date picker's month and year arrows' size (`svg-icon-box--drop-down`, a variant with no ARIA meaning). Every "opens a list" arrow in a field looks the same: the selection box's, QuickFilter's Recent Filters (a toolbar button, so a tab stop, with the same icon) and, as a pair, the value spinner's. `SvgImage.DROP_DOWN`, which drew a smaller, thinner arrow, is deleted.
- **Other icons** (the calendar and clock) are 16px (`--icon-width`) in `--icon-colour__grey`, as before.
- **Read only.** `setReadonly` dims the icon and stops it opening anything (`svg-icon-box--readonly`, a state with no ARIA attribute on the box, so a modifier): the selection, date, time and schedule boxes set it when their field is disabled or read only.

## Schedule box

`ScheduleBox` (`stroom.schedule.client`, styled by `schedule-box.css`) is a text field for a schedule's expression (a frequency such as `10m`, or a cron expression), with a clock at its end, and behaves as a date-time box: Enter or Alt+Down in the field, or a click on the clock, opens the Change Schedule dialog (`SchedulePopup`), never focus, and the field is `aria-haspopup="dialog"`. An expression the server rejects marks the field invalid when it is left; that check, and the dialog's, use the box's one dialog. In the dialog, the quick settings are a `QuickSettings`, and the Next Scheduled Time is a polite live region, as it changes when the expression is calculated.

`QuickSettings` (`stroom.widget.button.client`, styled by `quick-settings.css`) is presets that fill in a value, in titled groups side by side: each group a `group` named by its title, each preset a `LinkButton` named by its text, at least 24px high. The schedule dialog and the time range dialog use it (they had built the same markup each, with the time range's `timeRange-quick*` classes).

Stories `Widgets/Date & Time/ScheduleBox` (`ScheduleBoxStories`): `KeysOpenTheDialog`, `QuickSettingsAreGroupedButtons`, `CheckingReusesTheDialog`.

## Time range selector

`TimeRangeSelector` (`stroom.query.client.view`, styled by `time-range-selector.css`) shows the time range a query or dashboard applies, as a `LinkButton` whose text is the range ("Today", "After now()-1d"), named "Time range: " and its text, and `aria-haspopup="dialog"`. It opens the Time Range dialog (`TimeRangeDialog`, its content `TimeRangePopup`): quick settings (`QuickSettings`) and the After and Before times (date-time boxes that allow expressions), as a modal OK/Cancel dialog below the selector, made on the app's event bus (`StaticEventBus`), as the selector has none of its own. As a modal Stroom dialog, it is a `dialog` named by its title, the focus starts in After Time and is kept in it, Escape cancels it and the focus goes back to the selector. Disabled and read only are both `aria-disabled` (a button can't be read only), so it stays focusable and doesn't open; read only, greyed less, is the `time-range-selector--readonly` modifier.

Stories `App/Editors/QueryEditor` `TimeRangeRerun` (a quick setting chosen with the keys reruns the query) and `TimeRangeDialogKeys` (the dialog's role, name, focus and Escape).

## Field ends

A field with something drawn over its end (an icon box's icon, a password box's button and badge, a quick filter's buttons, a spinner's arrows) keeps its text 4px (`FieldEnd.GAP`) clear of it: its text never runs under what is over its end. Where that never changes width (an icon box's 26px icon, a selection box's arrow), the stylesheet pads the field's end by its width and 4px. Where it does (a quick filter's Clear comes and goes, a password badge's width depends on its text), `FieldEnd` (`stroom.widget.util.client`) watches it (`ResizeObserver`) while the widget is attached and sets the field's `--field__end-space`, which the stylesheet uses as its end padding, with a fallback for before it is shown. Story `Widgets/Inputs/FieldEnds` `TextStaysClearOfTheEnd` gives each such field a value too long for it and measures the space.

## Selection box

`BaseSelectionBox` (`stroom.item.client`, styled by `selection-box.css`) is a WAI-ARIA combobox: its text box (`role="combobox"`, `aria-haspopup="listbox"`, `aria-expanded`, `aria-controls`) sits over a picture of its value (`selection-box__value`, `aria-hidden`), with an icon box's drop-down arrow at its end. Its list is a `SelectionPopup` (`selection-popup.css`) holding a `SelectionList`: a quick filter over 10 items, a pager over 100, and a list box whose rows are options. Opening the list moves the focus into it (to its quick filter if it has one, else to the chosen option), rather than keeping it in the box with `aria-activedescendant`.

- **Keys on the box** (the list closed): Enter, Down, Up and Alt+Down open the list; in a box that can't be typed in (`selection-box--select-only`), Space does too, and typing a character opens the list and goes to it. Characters typed before the list has the focus are kept and passed on (`SelectionPopup.type`).
- **Keys in the list**: Up and Down move between options (and Up from the first option to the quick filter), only highlighting, as a combobox's list does; Enter or Alt+Up chooses the option with the focus and closes the list; Escape closes it, and the focus goes back to the box. Tab closes it, leaving the value, and goes on to what follows the box (`TabOrder.focusNext`); Shift+Tab closes it and goes back to the box. (It once sent the focus to the page, leaving the list open.) Typing goes to what is typed (`SelectionList.typeAhead`): into the quick filter, applied at once, with the caret after it (`QuickFilter.type`), or, in a list with no quick filter, to the next option that starts with it, characters typed within half a second being taken together, and the same character again going on to the next option starting with it. The list hands keys on after the browser has handled them (as the data grid does), so a key can't be cancelled there.
- **Value.** The picture of the value stays on one line and ends in an ellipsis, clear of the arrow (`padding-right: 26px`). It draws the box's border (`stroom-control`).
- **States.** Disabled and read only are modifiers (`selection-box--disabled`, `selection-box--readonly`), as the box has no ARIA attribute for them; they follow the text box's `disabled` and `aria-readonly`. A box that can't be typed in shows a pointer over it while it can be changed.

Stories `Widgets/Selectors/SelectionBox` (`SelectionBoxStories`): `KeysOpenTheList`, `AltUpChoosesTheOption`, `TypingJumpsToAnOption`, `TypingFiltersALongList` and `LongValue`, beside the earlier ones. Real input (`test-runner/lib/real-input/selection-box.mjs`): typing, Alt+Up, Space and Escape pressed for real, and typing into a long list's quick filter.

Not yet done: it hasn't been tried with a screen reader; the list's own classes (`selectionList-*`, in `components.css`) aren't BEM; and Home and End in the closed box don't open the list at its ends (optional in the pattern).

## Date and time

Every field for a moment in time is a `DateTimeBox` (`stroom.widget.datepicker.client`, styled by `date-time-box.css`): a text field for an ISO 8601 time with a calendar icon at its end. No value in Stroom is only a date, so there is no date-only box: GWT's `DateBox` and `DatePicker`, and Stroom's `MyDateBox` built on them, are deleted. A time of day is a `TimeBox`, which has the same look and keys.

- **Opening.** Enter or Alt+Down in the field, or a click on the icon, opens the Set Date And Time dialog (`DateTimePopup`) on the field's time; it never opens on focus. The field is `aria-haspopup="dialog"`; the icon is a picture hidden from screen readers (see Icon boxes). OK puts the time chosen in the field, formatted in the user's time zone (`ClientTimeZone`), Cancel or Escape leaves it, and the focus goes back to the field.
- **Expressions.** `setExpressionsAllowed(true)` (the expression term editor's dates, the query time range and a visualisation's date settings) allows any text, e.g. `now()-1d` or `day()`, which is never marked invalid; `getText()` and `setText()` read and write it. The dialog only edits a plain time, so it opens on now and replaces an expression. Such a box is typed in first, so Enter is its screen's (the term editor runs the query) and only Alt+Down or the icon opens the dialog.
- **Width.** 17 of the field's font size (`--font-size-13`), 245px at the medium font size, so a whole ISO time with an offset (`2024-12-28T23:58:58.888+0530`, 241px) fits beside the calendar at every font size; the same in an expression term (where a narrow field is 175px). A screen's width (`w-100`, or a class of its own) still wins. A `TimeBox` (the `date-time-box--time` variant) is 200px.
- **Format.** The field shows and takes ISO 8601, in the user's time zone (its offset, or `Z` for UTC), not the user's date and time format preference: that is a display format, some of whose choices drop the time (`yyyy-MM-dd`), the century (`dd/MM/yy`) or are ambiguous (`dd/MM` against `MM/dd`), the client has nothing to parse it, and expressions (`now()-1d`) are written in ISO. The dialog shows the date and time readably.
- **Validity.** A box that doesn't allow expressions marks text that isn't a time invalid (`FieldValidity`) when it is left; `setOptional(true)` allows it to be empty.
- **Its dialog.** A box makes its own dialog when first opened, shown on the app's event bus (`StaticEventBus`), unless given a provider (`setPopupProvider`, as the schedule screens do). The dialog's shortcuts (Today, Yesterday, Week Start; Now, Midnight, Midday) and each spinner's Reset are `LinkButton`s, at least 24px high (`date-time-view.css`).

The calendar (`CustomDatePicker`, the block `calendar`, `calendar.css`) is its year and month rows (`DefaultMonthSelector`: a selection box and a Previous/Next toolbar each, `calendar__header`, `calendar__row`) above the month's days (`CalendarGrid`), a WAI-ARIA grid:

- **Roles and names.** A `table` with `role="grid"`, named for the month it shows ("January 2024"); a header row of `columnheader`s (the short day name, its full name in `abbr`); each day a `gridcell` named by its full date in the browser's language ("Monday, 15 January 2024"). The chosen day is `aria-selected="true"`, today `aria-current="date"`; the days of the months before and after that fill the six weeks are `calendar__day--outside` (a modifier, as ARIA has no attribute for it). In a form group, the calendar is a `group` named by the label, as it has two form controls (Year and Month).
- **Keys.** The grid is one tab stop, the focus on a day (a roving `tabindex`): the chosen day if it is in the month shown, else today if it is, else the same day of the month. Left and Right move a day, Up and Down a week, Home and End to the start and end of its week, Page Up and Page Down a month, Shift+Page Up and Shift+Page Down a year (keeping the day of the month, or the month's last day if it is shorter); moving to a day of another month shows that month. Moving doesn't choose: Enter, Space or a click chooses the day (a click on a day outside the month shows its month). The Year and Month toolbars change the month without choosing, moving the tab stop to the same day of the new month.
- **Looks.** Each state comes from its ARIA attribute: the chosen day is filled in the selected row colour, today is bold with a border in it (the border alone is 2.6:1 dark and 1.3:1 light, so the bold is what shows it); a day's number is centred in its round fill (a table cell's content is at its top otherwise, `stroom.css`), hover the hovered row colour; keyboard focus is a 2px ring in `--focus-border` 1px outside the day, so it is against the page's colour on both sides (7.1:1 dark, 5.8:1 light; inside a chosen day's fill it would be 2.8:1 in the dark theme). In forced colours, the chosen day is `Highlight` and today's border and the focus outline `CanvasText`.

A duration is a `DurationPicker` (`stroom.widget.customdatebox.client`, styled by `duration-picker.css`): its amount (a value spinner, `duration-picker__amount`, "Amount") beside its unit (a selection box, `duration-picker__unit`, "Unit"), which a form group names together as a `group`. Each part is as wide as its longest value needs (1000000, "Milliseconds"), in units of the fields' font size, and neither shrinks. Stories `Widgets/Date & Time/DurationPicker`: `InAFormGroup`, `KeysChangeTheAmount`, `LargestValuesFit`, `SetValueFiresWhenAsked`.

Stories `Widgets/Date & Time/CalendarGrid` (`CalendarGridStories`): roles and names, the one tab stop, each key, crossing months, choosing with Enter, Space and a click, and the toolbars. Stories `Widgets/Date & Time/DateTimePicker` (`DateTimePickerStories`): opening with Enter and Alt+Down, choosing a day with the keys, the dialog's buttons, invalid text, expressions and disabled; `Widgets/Query/ExpressionBuilder` `DateTermBoxes`, a date term's From and To. Real input (`test-runner/lib/real-input/date-time.mjs`): the grid's keys, its focus ring and Tab pressed for real, and Alt+Down and Escape on the box.

Not yet done: it hasn't been tried with a screen reader; the dialogs' own markup (the Popups and Dialogs rows, session 3); the schedule box and time range selector (their own rows).

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
