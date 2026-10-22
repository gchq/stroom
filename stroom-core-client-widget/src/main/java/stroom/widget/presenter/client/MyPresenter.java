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
import stroom.widget.component.client.ComponentNode;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.HasComponentNode;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.Event.Type;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.Presenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.proxy.Proxy;

/// Stroom's proxy presenter (an application screen or a code-split plugin's), built on GWTP's
/// [Presenter]. Like [MyPresenterWidget], it has a place in the ownership tree: the presenters it
/// owns ([#own(HasComponentNode)]) are disposed with it ([#dispose()]). The application makes each
/// once and keeps it, so it is disposed only where it is made again, e.g. by a workbench story.
///
/// @param <T_VIEW>  The view's type.
/// @param <T_PROXY> The proxy's type.
public abstract class MyPresenter<T_VIEW extends View, T_PROXY extends Proxy<?>>
        extends Presenter<T_VIEW, T_PROXY>
        implements Layer, TaskMonitorFactory, HasComponentNode {

    private TaskMonitorFactory taskMonitorFactory;
    private final ComponentNode node = new ComponentNode(getClass().getSimpleName());
    private boolean firstReveal = true;

    public MyPresenter(final EventBus eventBus, final T_VIEW view, final T_PROXY proxy) {
        super(eventBus, view, proxy);
        // Run after what it owns is disposed
        node.onDispose(this::unbind);
    }

    /// Owns a presenter, which is then disposed with this one.
    ///
    /// @param child The presenter, e.g. one injected into the constructor.
    /// @param <C>   Its type.
    /// @return The child.
    protected final <C extends HasComponentNode> C own(final C child) {
        final ComponentNode childNode = child.getComponentNode();
        // Null only for a mock (e.g. Mockito's, in a test), which has no node to own
        if (childNode != null) {
            node.adopt(childNode);
        }
        return child;
    }

    /// Releases this presenter and what it owns for good. Runs once, however often it is called.
    public final void dispose() {
        node.dispose();
    }

    @Override
    public final ComponentNode getComponentNode() {
        return node;
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

    /**
     * Only called the first time the widget is revealed.
     */
    protected void onFirstReveal() {
    }

    @Override
    protected void onReveal() {
        if (firstReveal) {
            firstReveal = false;
            onFirstReveal();
        }
    }

    /// Gives this presenter a task monitor of its own, rather than its context's (its tab's).
    ///
    /// @param taskMonitorFactory What shows the progress of its requests.
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
    }

    /// Shows a task's progress: on the task monitor set on this presenter, if one was, otherwise on
    /// the one its context provides ([Contexts#TASK_MONITOR], e.g. its tab's), otherwise as an
    /// app-wide task (the main screen's spinner).
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
}
