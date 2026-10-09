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
import stroom.widget.util.client.PresenterScope;

import com.google.gwt.user.client.ui.RequiresResize;
import com.google.web.bindery.event.shared.Event.Type;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class MyPresenterWidget<V extends View>
        extends PresenterWidget<V>
        implements Layer, TaskMonitorFactory, HasTaskMonitorFactory {

    private TaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(this);
    // What this presenter belongs to (e.g. an open document), if it was made in a scope
    private final PresenterScope presenterScope;
    // Handlers others added with this presenter as their source, removed when it is disposed
    private List<HandlerRegistration> sourceRegistrations;

    public MyPresenterWidget(final EventBus eventBus, final V view) {
        super(eventBus, view);
        presenterScope = PresenterScope.register(this);
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
        // and what it refers to, for good; within a scope it is removed when this is disposed
        if (presenterScope != null) {
            if (sourceRegistrations == null) {
                sourceRegistrations = new ArrayList<>();
            }
            sourceRegistrations.add(registration);
        }
        return registration;
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

    /// Releases this presenter for good, when what it belongs to is closed: unbinds it (removing
    /// the handlers it registered), removes the handlers others added with it as their source, and
    /// lets a subclass release anything else in [#onDispose()]. Called by [PresenterScope#dispose()].
    public final void dispose() {
        unbind();
        if (sourceRegistrations != null) {
            for (final HandlerRegistration registration : sourceRegistrations) {
                registration.removeHandler();
            }
            sourceRegistrations = null;
        }
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
