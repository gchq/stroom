/*
 * Copyright 2016 Crown Copyright
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

package stroom.widget.presenter.client;

import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.HasTaskMonitorFactory;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.ComponentNode;
import stroom.widget.component.client.ContextKey;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.HasComponentNode;
import stroom.widget.popup.client.event.HidePopupEvent;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.Event.Type;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.PresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

/// Stroom's presenter, built on GWTP's [PresenterWidget], with a place in the ownership tree (see
/// [ComponentNode]): the presenters and components it owns ([#own(HasComponentNode)]), and the
/// registrations made through it ([#listen(Type, Object)], [#addHandlerToSource(Type, Object)]),
/// are released when it is disposed ([#dispose()]), as a component's are.
///
/// @param <V> The view's type.
public class MyPresenterWidget<V extends View>
        extends PresenterWidget<V>
        implements Layer, TaskMonitorFactory, HasTaskMonitorFactory, HasComponentNode, IsWidget {

    // Runs something once the event being handled is done (replaced in JVM tests, which have no
    // GWT scheduler)
    static Consumer<Runnable> deferred = runnable -> Scheduler.get().scheduleDeferred(runnable::run);

    // Set explicitly (e.g. a list's own spinner), or null to use the context's (see createTaskMonitor)
    private TaskMonitorFactory taskMonitorFactory;
    private final ComponentNode node = new ComponentNode(getClass().getSimpleName());

    public MyPresenterWidget(final EventBus eventBus, final V view) {
        super(eventBus, view);
        // Run after what it owns is disposed and its registrations are removed
        node.onDispose(this::release);
    }

    /// @return Its view's widget, so a presenter can go where a component can, e.g. a tab of a
    /// `TabbedComponent`.
    @Override
    public Widget asWidget() {
        return getWidget();
    }

    @Override
    public void setLayerVisible(final boolean fade, final boolean visible) {
        Layer.setLayerVisible(getWidget().getElement(), fade, visible);
    }

    @Override
    public void addLayer(final LayerContainer tabContentView) {
        tabContentView.add(getWidget());
    }

    @Override
    public boolean removeLayer() {
        getWidget().removeFromParent();
        return true;
    }

    @Override
    public void onResize() {
        if (getWidget() instanceof RequiresResize) {
            ((RequiresResize) getWidget()).onResize();
        }
    }

    protected final <H> HandlerRegistration addHandlerToSource(final Type<H> type, final H handler) {
        final HandlerRegistration registration = getEventBus().addHandlerToSource(type, this, handler);
        // Callers often throw the registration away, and the event bus would then keep the handler,
        // and what it refers to, for good; it is removed when this is disposed
        node.add(registration);
        return registration;
    }

    /// Owns a presenter or component, which is then disposed with this presenter and sees its
    /// context.
    ///
    /// @param child The presenter or component, e.g. one just made by a GIN provider.
    /// @param <C>   Its type.
    /// @return The child, so it can be owned as it is made: `own(editorProvider.get())`.
    protected final <C extends HasComponentNode> C own(final C child) {
        final ComponentNode childNode = child.getComponentNode();
        // Null only for a mock (e.g. Mockito's, in a test), which has no node to own
        if (childNode != null) {
            node.adopt(childNode);
        }
        return child;
    }

    /// Disposes a presenter or component this presenter owns early, e.g. one being replaced.
    ///
    /// @param child The child.
    protected final void release(final HasComponentNode child) {
        final ComponentNode childNode = child.getComponentNode();
        // Null only for a mock (e.g. Mockito's, in a test)
        if (childNode != null) {
            node.release(childNode);
        }
    }

    /// Owns a presenter made for one use, e.g. a dialog made when a button is pressed, until it is
    /// hidden: it is then disposed (once the popup manager has hidden it), rather than kept until
    /// this presenter is. A dialog this presenter shows again must be owned with
    /// [#own(HasComponentNode)] instead.
    ///
    /// @param dialog The presenter, just made, e.g. `ownUntilHidden(editPresenterProvider.get())`.
    /// @param <P>    Its type.
    /// @return The presenter.
    protected final <P extends MyPresenterWidget<?>> P ownUntilHidden(final P dialog) {
        return disposeWhenHidden(own(dialog));
    }

    /// Disposes a presenter made for one use as a popup, e.g. a dialog, once it is hidden (after
    /// the popup manager has hidden it), for code that makes dialogs but can't own them, such as
    /// an app-wide helper. A presenter can use [#ownUntilHidden(MyPresenterWidget)] instead, which
    /// also disposes the dialog if it is disposed first.
    ///
    /// @param dialog The presenter, just made, e.g. `disposeWhenHidden(dialogProvider.get())`.
    /// @param <P>    Its type.
    /// @return The presenter.
    public static <P extends MyPresenterWidget<?>> P disposeWhenHidden(final P dialog) {
        final ComponentNode dialogNode = dialog.getComponentNode();
        // Null only for a mock (e.g. Mockito's, in a test)
        if (dialogNode != null) {
            // A registration of the dialog's, so it goes when the dialog is disposed
            dialogNode.add(dialog.getEventBus().addHandlerToSource(HidePopupEvent.getType(), dialog, event ->
                    deferred.accept(dialogNode::dispose)));
        }
        return dialog;
    }

    /// Owns a registration, removed when this presenter is disposed (not when it is unbound, as
    /// [#registerHandler(HandlerRegistration)]'s are).
    ///
    /// @param registration The registration.
    protected final void own(final HandlerRegistration registration) {
        node.add(registration);
    }

    /// Listens to an app-wide event on the event bus until this presenter is disposed.
    ///
    /// @param type    The event's type.
    /// @param handler The handler.
    /// @param <H>     The handler's type.
    protected final <H> void listen(final Type<H> type, final H handler) {
        node.add(getEventBus().addHandler(type, handler));
    }

    @Override
    public final ComponentNode getComponentNode() {
        return node;
    }

    /// @return Whether this presenter is disposed.
    public final boolean isDisposed() {
        return node.isDisposed();
    }

    /// Releases this presenter for good, when what it belongs to is closed: disposes what it
    /// owns, removes the handlers it and others added through it, unbinds it (removing the
    /// handlers it registered) and lets a subclass release anything else in [#onDispose()]. Runs
    /// once, however often it is called.
    public final void dispose() {
        node.dispose();
    }

    private void release() {
        unbind();
        onDispose();
    }

    /// Releases anything else this presenter holds that would outlive it, e.g. a timer or a
    /// JavaScript object with global listeners. Called once, by [#dispose()].
    protected void onDispose() {
    }

    @Override
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
    }

    /// Shows a task's progress: on the task monitor set on this presenter, if one was, otherwise on
    /// the one its context provides ([Contexts#TASK_MONITOR], e.g. its tab's or dialog's),
    /// otherwise as an app-wide task (the main screen's spinner).
    ///
    /// @return A task monitor.
    @Override
    public TaskMonitor createTaskMonitor() {
        if (taskMonitorFactory != null) {
            return taskMonitorFactory.createTaskMonitor();
        }
        final TaskMonitorFactory provided = node.lookup(Contexts.TASK_MONITOR);
        if (provided != Contexts.TASK_MONITOR.getDefaultValue()) {
            return provided.createTaskMonitor();
        }
        return new DefaultTaskMonitorFactory(this).createTaskMonitor();
    }

    /// Looks up a context value now. To follow changes, use [#watch(ContextKey, Consumer)].
    ///
    /// @param key The key, e.g. [Contexts#READ_ONLY].
    /// @param <T> The type of the value.
    /// @return The value, or the key's default if nothing provides it.
    protected final <T> T context(final ContextKey<T> key) {
        return node.lookup(key);
    }

    /// Watches a context value: runs `onChange` with it once this presenter is owned (by a tab's
    /// root, or a presenter that is), and again whenever it changes, until it is disposed.
    ///
    /// @param key      The key, e.g. [Contexts#READ_ONLY].
    /// @param onChange What to run with the value, e.g. `getView()::onReadOnly`.
    /// @param <T>      The type of the value.
    protected final <T> void watch(final ContextKey<T> key, final Consumer<T> onChange) {
        node.watch(key, onChange);
    }

    /// Provides a context value to this presenter and everything it owns, however deep.
    ///
    /// @param key   The key.
    /// @param value The value, which may change.
    /// @param <T>   The type of the value.
    protected final <T> void provide(final ContextKey<T> key, final ContextValue<T> value) {
        node.provide(key, value);
    }
}
