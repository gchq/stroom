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

import stroom.widget.presenter.client.MyPresenterWidget;

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

/// A part of the UI that owns the components it shows. Inputs come in through its constructor and
/// setters, outputs go back through callbacks its owner gives it, and values many layers need
/// (read-only, the task monitor) come from context (see [Contexts]).
///
/// Whoever shows a component owns it ([#make(Provider)], or [#own(HasComponentNode)] for one
/// injected), and disposing a component
/// disposes everything it owns, along with every registration made through it. Screen authors
/// never call [#dispose()]: only roots are disposed (an open document's tab, a dialog, a workbench
/// story).
///
/// A component builds its widget once, in its constructor, and gives it to [#initWidget(Widget)].
/// [#onAttach()] and [#onDetach()] are called as the widget enters and leaves the page, e.g. as its
/// tab is shown and hidden; being hidden doesn't dispose it.
///
/// A component made with the event bus can be the source of app-wide requests, as a presenter
/// can: `AlertEvent.fireWarn(this, ...)`, `ConfirmEvent.fire(this, ...)`.
public abstract class Component implements IsWidget, HasComponentNode, HasHandlers {

    private final ComponentNode node;
    private final EventBus eventBus;
    private Widget widget;

    /// Makes a component that doesn't listen to the event bus.
    protected Component() {
        this(null);
    }

    /// Makes a component that listens to app-wide events (see [#listen(Event.Type, Object)]).
    ///
    /// @param eventBus The application's event bus.
    protected Component(final EventBus eventBus) {
        this(null, eventBus);
    }

    /// Makes a component with a name of its own, for diagnostics (e.g. the unowned components
    /// [ComponentChecks] lists), rather than its class's.
    ///
    /// @param name     The name, or null for the class's simple name.
    /// @param eventBus The application's event bus, or null if it doesn't listen to it.
    protected Component(final String name, final EventBus eventBus) {
        this.node = new ComponentNode(name != null
                ? name
                : getClass().getSimpleName());
        this.eventBus = eventBus;
    }

    @Override
    public final ComponentNode getComponentNode() {
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
    /// component.
    ///
    /// @param event The event.
    /// @throws IllegalStateException If this component was made without an event bus.
    @Override
    public void fireEvent(final GwtEvent<?> event) {
        if (eventBus == null) {
            throw new IllegalStateException(node.getName() + " was made without an event bus");
        }
        eventBus.fireEventFromSource(event, this);
    }

    /// Disposes this component and everything it owns. Only the owners of roots call this; a
    /// component owned by another is disposed with it.
    public final void dispose() {
        node.dispose();
    }

    /// @return Whether this component is disposed.
    public final boolean isDisposed() {
        return node.isDisposed();
    }

    /// Sets the widget this component shows. Called once, from the constructor.
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

    /// Makes a component (or presenter) and owns it until this one is disposed, e.g. a tab, a list
    /// or a settings panel: `final FieldList list = make(fieldListProvider);`.
    ///
    /// @param provider What makes it, e.g. a GIN provider.
    /// @param <C>      Its type.
    /// @return The component, made and owned.
    protected final <C extends HasComponentNode> C make(final Provider<C> provider) {
        Objects.requireNonNull(provider, "provider");
        return own(provider.get());
    }

    /// Makes a dialog for one use, e.g. when a button is pressed, and owns it until it is hidden:
    /// it is then disposed (once the popup manager has hidden it), or with this one if this is
    /// disposed first. A dialog shown again is made with [#make(Provider)] instead.
    ///
    /// ```java
    /// makeDialog(editPresenterProvider).show(field, updated -> ...);
    /// ```
    ///
    /// @param provider What makes it, e.g. a GIN provider.
    /// @param <P>      Its type.
    /// @return The dialog, made and owned, ready to show.
    protected final <P extends MyPresenterWidget<?>> P makeDialog(final Provider<P> provider) {
        Objects.requireNonNull(provider, "provider");
        return ComponentDialog.disposeWhenHidden(make(provider));
    }

    /// Owns a component, which is then disposed with this one and sees this one's context.
    ///
    /// @param child The component, e.g. one injected into the constructor (one made here is made
    ///              with [#make(Provider)]).
    /// @param <C>   Its type.
    /// @return The component.
    protected final <C extends HasComponentNode> C own(final C child) {
        final ComponentNode childNode = child.getComponentNode();
        // Null only for a mock (e.g. Mockito's, in a test), which has no node to own
        if (childNode != null) {
            node.adopt(childNode);
        }
        return child;
    }

    /// Disposes a component this one owns early, e.g. one that is being replaced.
    ///
    /// @param child The component.
    protected final void release(final HasComponentNode child) {
        final ComponentNode childNode = child.getComponentNode();
        // Null only for a mock (e.g. Mockito's, in a test)
        if (childNode != null) {
            node.release(childNode);
        }
    }

    /// Owns a registration, removed when this component is disposed, e.g. of a handler on a widget
    /// this component doesn't own.
    ///
    /// @param registration The registration.
    protected final void own(final HandlerRegistration registration) {
        node.add(registration);
    }

    /// Listens to an app-wide event on the event bus until this component is disposed. Events
    /// between a component and its owner are callbacks, not bus events.
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

    /// Adds a clean-up, run when this component is disposed, e.g. to stop a timer.
    ///
    /// @param cleanUp What to run.
    protected final void onDispose(final Runnable cleanUp) {
        node.onDispose(cleanUp);
    }

    /// Wraps a callback, e.g. a REST result's, so that it does nothing once this component is
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

    /// Watches a context value: runs `onChange` with it once this component is owned, and again
    /// whenever it changes, until this component is disposed.
    ///
    /// @param key      The key, e.g. [Contexts#READ_ONLY].
    /// @param onChange What to run with the value, e.g. `nameBox::setReadOnly`.
    /// @param <T>      The type of the value.
    protected final <T> void watch(final ContextKey<T> key, final Consumer<T> onChange) {
        node.watch(key, onChange);
    }

    /// Provides a context value to this component and every component it owns, however deep.
    ///
    /// @param key   The key.
    /// @param value The value, which may change.
    /// @param <T>   The type of the value.
    protected final <T> void provide(final ContextKey<T> key, final ContextValue<T> value) {
        node.provide(key, value);
    }

    /// @return The application's event bus, or null if this component was made without one.
    protected final EventBus getEventBus() {
        return eventBus;
    }

    /// Called when this component's widget enters the page, e.g. its tab is shown or its dialog
    /// opened: the time to focus, measure or start polling.
    protected void onAttach() {
        // Nothing by default
    }

    /// Called when this component's widget leaves the page, e.g. its tab is hidden or its dialog
    /// closed: the time to stop polling. It keeps its state.
    protected void onDetach() {
        // Nothing by default
    }
}
