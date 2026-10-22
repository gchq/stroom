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
import stroom.widget.component.client.HasComponentNode;
import stroom.widget.util.client.PresenterScope;

import com.google.gwt.user.client.ui.RequiresResize;
import com.google.web.bindery.event.shared.Event.Type;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.PresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Supplier;

/// Stroom's presenter, built on GWTP's [PresenterWidget], with a place in the ownership tree (see
/// [ComponentNode]): the presenters and components it owns ([#own(HasComponentNode)]), and the
/// registrations made through it ([#listen(Type, Object)], [#addHandlerToSource(Type, Object)]),
/// are released when it is disposed ([#dispose()]), as a component's are.
///
/// @param <V> The view's type.
public class MyPresenterWidget<V extends View>
        extends PresenterWidget<V>
        implements Layer, TaskMonitorFactory, HasTaskMonitorFactory, HasComponentNode {

    private TaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(this);
    private final ComponentNode node = new ComponentNode(getClass().getSimpleName());
    // What this presenter belongs to (e.g. an open document), if it was made in a scope
    private final PresenterScope presenterScope;

    public MyPresenterWidget(final EventBus eventBus, final V view) {
        super(eventBus, view);
        presenterScope = PresenterScope.register(this);
        // Run after what it owns is disposed and its registrations are removed
        node.onDispose(this::release);
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

    /// Makes something (usually a presenter, from a provider) in this presenter's scope, so that a
    /// presenter made on demand, after this was opened (e.g. when data arrives or a button is
    /// pressed), is released along with it.
    ///
    /// @param supplier What makes it, e.g. `presenterProvider::get`.
    /// @param <T>      What is made.
    /// @return What was made.
    protected final <T> T inScope(final Supplier<T> supplier) {
        return PresenterScope.captureIn(presenterScope, supplier);
    }

    /// @return What this presenter belongs to (e.g. an open document), or null if it was made
    /// outside a [PresenterScope].
    public final PresenterScope getPresenterScope() {
        return presenterScope;
    }

    /// Releases this presenter for good, when what it belongs to is closed: disposes what it
    /// owns, removes the handlers it and others added through it, unbinds it (removing the
    /// handlers it registered) and lets a subclass release anything else in [#onDispose()]. Runs
    /// once, however often it is called (e.g. by its owner and by a [PresenterScope]).
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

    @Override
    public TaskMonitor createTaskMonitor() {
        return taskMonitorFactory.createTaskMonitor();
    }
}
