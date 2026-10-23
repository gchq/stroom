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
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.Component;
import stroom.widget.component.client.ComponentNode;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.HasComponentNode;
import stroom.widget.popup.client.event.HidePopupEvent;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// Stroom's presenter: a [Component] that shows a view, and registers handlers while it is bound
/// ([#onBind()]). As for any component, the presenters and components it owns
/// ([#own(HasComponentNode)]), and the registrations made through it ([#listen(Event.Type, Object)],
/// [#own(HandlerRegistration)]), are released when it is disposed ([#dispose()]). One made by GIN is
/// bound once it is made; the popup manager binds one while it shows it as a popup.
///
/// @param <V> The view's type.
public class MyPresenterWidget<V extends View>
        extends Component
        implements HandlerContainer, Layer, TaskMonitorFactory {

    // Runs something once the event being handled is done (replaced in JVM tests, which have no
    // GWT scheduler)
    static Consumer<Runnable> deferred = runnable -> Scheduler.get().scheduleDeferred(runnable::run);

    // The registrations made while it is bound, removed when it is unbound
    private final List<HandlerRegistration> registrations = new ArrayList<>();
    // Set explicitly (e.g. a list's own spinner), or null to use the context's (see createTaskMonitor)
    private TaskMonitorFactory taskMonitorFactory;
    private final V view;
    private boolean bound;

    public MyPresenterWidget(final EventBus eventBus, final V view) {
        super(eventBus);
        this.view = view;
        // Run after what it owns is disposed and its registrations are removed
        onDispose(this::release);
    }

    @Override
    public final void bind() {
        if (!bound) {
            onBind();
            bound = true;
        }
    }

    @Override
    public final boolean isBound() {
        return bound;
    }

    @Override
    public final void unbind() {
        if (bound) {
            bound = false;
            for (final HandlerRegistration registration : registrations) {
                registration.removeHandler();
            }
            registrations.clear();
            onUnbind();
        }
    }

    /// Registers handlers ([#registerHandler(HandlerRegistration)]) when it is bound.
    protected void onBind() {
    }

    /// Lets go of anything else when it is unbound, after its registrations are removed.
    protected void onUnbind() {
    }

    /// Keeps a registration, removed when this is unbound.
    ///
    /// @param registration The registration.
    protected void registerHandler(final HandlerRegistration registration) {
        registrations.add(registration);
    }

    // GIN calls this once it has made and injected one
    @Inject
    final void automaticBind() {
        bind();
    }

    /// @return Its view.
    public V getView() {
        return view;
    }

    /// @return Its view's widget, or null if it has no view.
    public Widget getWidget() {
        return view == null
                ? null
                : view.asWidget();
    }

    /// Adds a handler to the event bus.
    ///
    /// @param type    The event's type.
    /// @param handler The handler.
    /// @param <H>     The handler's type.
    /// @return The registration, which the caller removes.
    protected final <H extends EventHandler> HandlerRegistration addHandler(final GwtEvent.Type<H> type,
                                                                            final H handler) {
        return getEventBus().addHandler(type, handler);
    }

    /// Adds a handler to the event bus until this presenter is unbound.
    ///
    /// @param type    The event's type.
    /// @param handler The handler.
    /// @param <H>     The handler's type.
    protected final <H extends EventHandler> void addRegisteredHandler(final GwtEvent.Type<H> type,
                                                                       final H handler) {
        registerHandler(addHandler(type, handler));
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

    private void release() {
        unbind();
        onDispose();
    }

    /// Releases anything else this presenter holds that would outlive it, e.g. a timer or a
    /// JavaScript object with global listeners. Called once, by [#dispose()].
    protected void onDispose() {
    }

    /// Gives this presenter a task monitor of its own, e.g. a list's pager spinner, rather than
    /// its context's (its tab's or dialog's). Don't pass on an owner's: its context gives it.
    ///
    /// @param taskMonitorFactory What shows the progress of its requests.
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
        final TaskMonitorFactory provided = getComponentNode().lookup(Contexts.TASK_MONITOR);
        if (provided != Contexts.TASK_MONITOR.getDefaultValue()) {
            return provided.createTaskMonitor();
        }
        return new DefaultTaskMonitorFactory(this).createTaskMonitor();
    }
}
