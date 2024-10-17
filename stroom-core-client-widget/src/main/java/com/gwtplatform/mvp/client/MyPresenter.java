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

package com.gwtplatform.mvp.client;

import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.HasTaskMonitorFactory;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.Event.Type;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.proxy.Proxy;

public abstract class MyPresenter<T_VIEW extends View, T_PROXY extends Proxy<?>>
        extends Presenter<T_VIEW, T_PROXY>
        implements Layer, TaskMonitorFactory, HasTaskMonitorFactory {

    private TaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(this);
    private boolean firstReveal = true;

    public MyPresenter(final EventBus eventBus, final T_VIEW view, final T_PROXY proxy) {
        super(eventBus, view, proxy);
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

    protected final void fireEventFromSource(final Event<?> event) {
        getEventBus().fireEventFromSource(event, this);
    }

    protected final <H> HandlerRegistration addHandlerToSource(final Type<H> type, final H handler) {
        return getEventBus().addHandlerToSource(type, this, handler);
    }

    protected final <H extends EventHandler> void addRegisteredHandlerToSource(final Type<H> type, final H handler) {
        registerHandler(addHandlerToSource(type, handler));
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
