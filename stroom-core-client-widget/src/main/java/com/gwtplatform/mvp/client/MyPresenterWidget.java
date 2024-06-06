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

import stroom.task.client.HasTaskListener;
import stroom.task.client.TaskListener;
import stroom.task.client.TaskListenerImpl;

import com.google.gwt.user.client.ui.RequiresResize;
import com.google.web.bindery.event.shared.Event.Type;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

public class MyPresenterWidget<V extends View>
        extends PresenterWidget<V>
        implements Layer, TaskListener, HasTaskListener {

    private final TaskListenerImpl taskListener = new TaskListenerImpl(this);

    public MyPresenterWidget(final EventBus eventBus, final V view) {
        super(eventBus, view);
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
        return getEventBus().addHandlerToSource(type, this, handler);
    }

    @Override
    public synchronized void setTaskListener(final TaskListener taskListener) {
        this.taskListener.setTaskListener(taskListener);
    }

    @Override
    public synchronized void incrementTaskCount() {
        taskListener.incrementTaskCount();
    }

    @Override
    public synchronized void decrementTaskCount() {
        taskListener.decrementTaskCount();
    }
}
