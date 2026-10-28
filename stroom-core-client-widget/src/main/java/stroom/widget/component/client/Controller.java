/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.component.client;

import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.Objects;
import java.util.function.Consumer;

/// A part of the UI that owns the controllers it shows. Inputs come in through its constructor and
/// setters, outputs go back through callbacks its owner gives it, and values many layers need
/// (read-only, the task monitor) come from context (see [Contexts]).
///
/// Whoever shows a controller owns it ([#make(Provider)], or [#own(HasControllerNode)] for one
/// injected), and disposing a controller
/// disposes everything it owns, along with every registration made through it. Screen authors
/// never call [#dispose()]: only roots are disposed (an open document's tab, a dialog, a workbench
/// story).
///
/// A controller builds its widget once, in its constructor, and gives it to [#initWidget(Widget)].
/// [#onAttach()] and [#onDetach()] are called as the widget enters and leaves the page, e.g. as its
/// tab is shown and hidden; being hidden doesn't dispose it.
///
/// A controller made with the event bus can be the source of app-wide requests:
/// `AlertEvent.fireWarn(this, ...)`, `ConfirmEvent.fire(this, ...)`.
public abstract class Controller implements IsWidget, HasControllerNode, HasHandlers {

    private final ControllerNode node;
    private final EventBus eventBus;
    private Widget widget;

    /// Makes a controller that doesn't listen to the event bus.
    protected Controller() {
        this(null);
    }

    /// Makes a controller that listens to app-wide events (see [#listen(Event.Type, Object)]).
    ///
    /// @param eventBus The application's event bus.
    protected Controller(final EventBus eventBus) {
        this(null, eventBus);
    }

    /// Makes a controller with a name of its own, for diagnostics (e.g. the unowned controllers
    /// [ControllerChecks] lists), rather than its class's.
    ///
    /// @param name     The name, or null for the class's simple name.
    /// @param eventBus The application's event bus, or null if it doesn't listen to it.
    protected Controller(final String name, final EventBus eventBus) {
        this.node = new ControllerNode(name != null
                ? name
                : getClass().getSimpleName());
        this.eventBus = eventBus;
    }

    @Override
    public final ControllerNode getControllerNode() {
        return node;
    }

    @Override
    public Widget asWidget() {
        if (widget == null) {
            throw new IllegalStateException(node.getName() + " has no widget: call initWidget()");
        }
        return widget;
    }

    /// Fires an app-wide event, e.g. a request to show an alert, on the event bus, from this
    /// controller.
    ///
    /// @param event The event.
    /// @throws IllegalStateException If this controller was made without an event bus.
    @Override
    public void fireEvent(final GwtEvent<?> event) {
        if (eventBus == null) {
            throw new IllegalStateException(node.getName() + " was made without an event bus");
        }
        eventBus.fireEventFromSource(event, this);
    }

    /// Disposes this controller and everything it owns. Only the owners of roots call this; a
    /// controller owned by another is disposed with it.
    public final void dispose() {
        node.dispose();
    }

    /// @return Whether this controller is disposed.
    public final boolean isDisposed() {
        return node.isDisposed();
    }

    /// Sets the widget this controller shows. Called once, from the constructor.
    ///
    /// @param widget The widget.
    protected final void initWidget(final Widget widget) {
        Objects.requireNonNull(widget, "widget");
        if (this.widget != null) {
            throw new IllegalStateException(node.getName() + " already has a widget");
        }
        this.widget = widget;
        // A handler on its own widget, so it is collected with it and needs no registration
        widget.addAttachHandler(event -> {
            if (!node.isDisposed()) {
                if (event.isAttached()) {
                    onAttach();
                } else {
                    onDetach();
                }
            }
        });
    }

    /// Makes a controller and owns it until this one is disposed, e.g. a tab, a list
    /// or a settings panel: `final FieldList list = make(fieldListProvider);`.
    ///
    /// @param provider What makes it, e.g. a GIN provider.
    /// @param <C>      Its type.
    /// @return The controller, made and owned.
    protected final <C extends HasControllerNode> C make(final Provider<C> provider) {
        Objects.requireNonNull(provider, "provider");
        return own(provider.get());
    }

    /// Makes a dialog for one use, e.g. when a button is pressed, and owns it until it is hidden:
    /// it is then disposed (once the popup manager has hidden it), or with this one if this is
    /// disposed first, e.g. one set up before what it shows has been fetched, and so before it can
    /// be shown with [ControllerDialog#show(Controller)]. A dialog that shows itself takes its opener
    /// instead ([ControllerDialog#showOnce]), and one shown again is made with [#make(Provider)].
    ///
    /// ```java
    /// final CodeEditorController jsonEditor = makeDialog(jsonEditorProvider).attach(new CodeEditor());
    /// ...
    /// ControllerDialog.show(jsonEditor).caption("Pipeline Source").fire();
    /// ```
    ///
    /// @param provider What makes it, e.g. a GIN provider.
    /// @param <C>      Its type.
    /// @return The dialog, made and owned, ready to show.
    protected final <C extends Controller> C makeDialog(final Provider<C> provider) {
        Objects.requireNonNull(provider, "provider");
        return ControllerDialog.disposeWhenHidden(make(provider));
    }

    /// Owns a controller, which is then disposed with this one and sees this one's context.
    ///
    /// @param child The controller, e.g. one injected into the constructor (one made here is made
    ///              with [#make(Provider)]).
    /// @param <C>   Its type.
    /// @return The controller.
    protected final <C extends HasControllerNode> C own(final C child) {
        final ControllerNode childNode = child.getControllerNode();
        // Null only for a mock (e.g. Mockito's, in a test), which has no node to own
        if (childNode != null) {
            node.adopt(childNode);
        }
        return child;
    }

    /// Disposes a controller this one owns early, e.g. one that is being replaced.
    ///
    /// @param child The controller.
    protected final void release(final HasControllerNode child) {
        final ControllerNode childNode = child.getControllerNode();
        // Null only for a mock (e.g. Mockito's, in a test)
        if (childNode != null) {
            node.release(childNode);
        }
    }

    /// Owns a registration, removed when this controller is disposed, e.g. of a handler on a widget
    /// this controller doesn't own.
    ///
    /// @param registration The registration.
    protected final void own(final HandlerRegistration registration) {
        node.add(registration);
    }

    /// Listens to an app-wide event on the event bus until this controller is disposed. Events
    /// between a controller and its owner are callbacks, not bus events.
    ///
    /// @param type    The event's type.
    /// @param handler The handler.
    /// @param <H>     The handler's type.
    protected final <H> void listen(final Event.Type<H> type, final H handler) {
        if (eventBus == null) {
            throw new IllegalStateException(node.getName() + " was made without an event bus");
        }
        node.add(eventBus.addHandler(type, handler));
    }

    /// Adds a clean-up, run when this controller is disposed, e.g. to stop a timer.
    ///
    /// @param cleanUp What to run.
    protected final void onDispose(final Runnable cleanUp) {
        node.onDispose(cleanUp);
    }

    /// Wraps a callback, e.g. a REST result's, so that it does nothing once this controller is
    /// disposed.
    ///
    /// @param callback The callback.
    /// @param <T>      What it is given.
    /// @return The guarded callback.
    protected final <T> Consumer<T> guard(final Consumer<T> callback) {
        return node.guard(callback);
    }

    /// Tells the document this belongs to that the user changed something (its [Contexts#DIRTY]
    /// tracker, looked up from this one's owner), so it can work out whether it is dirty.
    protected final void markChanged() {
        node.lookupAbove(Contexts.DIRTY).onChange();
    }

    /// Looks up a context value now. To follow changes, use [#watch(ContextKey, Consumer)].
    ///
    /// @param key The key.
    /// @param <T> The type of the value.
    /// @return The value, or the key's default if nothing provides it.
    protected final <T> T context(final ContextKey<T> key) {
        return node.lookup(key);
    }

    /// What shows the progress of a request this controller makes through a client that takes a
    /// task monitor (rather than with `owner(this)`): the one its owners provide
    /// ([Contexts#TASK_MONITOR]), or null if none does, so that the request shows Stroom's own
    /// busy indicator. The context's default shows nothing, so passing it on would hide the
    /// request's progress altogether, e.g. one made before a dialog is shown.
    ///
    /// @return The task monitor its owners provide, or null.
    protected final TaskMonitorFactory getProvidedTaskMonitor() {
        final TaskMonitorFactory taskMonitorFactory = node.lookup(Contexts.TASK_MONITOR);
        return taskMonitorFactory == Contexts.TASK_MONITOR.getDefaultValue()
                ? null
                : taskMonitorFactory;
    }

    /// What shows the progress of this controller's requests: when each starts, the task monitor its
    /// owners provide then ([Contexts#TASK_MONITOR], e.g. its tab's or dialog's), or, if none does,
    /// Stroom's own busy indicator (an app-wide task). It can be given before this controller is
    /// owned, e.g. to a list made in its constructor.
    ///
    /// @return The task monitor.
    protected final TaskMonitorFactory taskMonitor() {
        return () -> {
            final TaskMonitorFactory provided = getProvidedTaskMonitor();
            return provided != null
                    ? provided.createTaskMonitor()
                    : new DefaultTaskMonitorFactory(this).createTaskMonitor();
        };
    }

    /// Watches a context value: runs `onChange` with it once this controller is owned, and again
    /// whenever it changes, until this controller is disposed.
    ///
    /// @param key      The key, e.g. [Contexts#READ_ONLY].
    /// @param onChange What to run with the value, e.g. `nameBox::setReadOnly`.
    /// @param <T>      The type of the value.
    protected final <T> void watch(final ContextKey<T> key, final Consumer<T> onChange) {
        node.watch(key, onChange);
    }

    /// Provides a context value to this controller and every controller it owns, however deep.
    ///
    /// @param key   The key.
    /// @param value The value, which may change.
    /// @param <T>   The type of the value.
    protected final <T> void provide(final ContextKey<T> key, final ContextValue<T> value) {
        node.provide(key, value);
    }

    /// @return The application's event bus, or null if this controller was made without one.
    protected final EventBus getEventBus() {
        return eventBus;
    }

    /// Called when this controller's widget enters the page, e.g. its tab is shown or its dialog
    /// opened: the time to focus, measure or start polling.
    protected void onAttach() {
        // Nothing by default
    }

    /// Called when this controller's widget leaves the page, e.g. its tab is hidden or its dialog
    /// closed: the time to stop polling. It keeps its state.
    protected void onDetach() {
        // Nothing by default
    }
}
