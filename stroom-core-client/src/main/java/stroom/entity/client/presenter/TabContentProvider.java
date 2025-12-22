/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.entity.client.presenter;

import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.task.client.HasTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.event.shared.GwtEvent;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HandlerContainer;
import com.gwtplatform.mvp.client.HandlerContainerImpl;
import com.gwtplatform.mvp.client.PresenterWidget;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TabContentProvider<E>
        extends HandlerContainerImpl
        implements
        HasDocumentRead<E>,
        HasDocumentWrite<E>,
        HasDirtyHandlers,
        HasClose {

    private final Map<TabData, TabProvider<E>> tabProviders = new HashMap<>();
    private final Map<TabData, TabProvider<E>> presenterCache = new HashMap<>();

    private final Set<TabProvider<E>> usedProviders = new HashSet<>();

    private final EventBus eventBus;
    private TabProvider<E> currentTabProvider;
    private DocRef docRef;
    private E entity;
    private boolean readOnly = true;
    private int readCount;

    public TabContentProvider(final EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    protected void onBind() {
        super.onBind();
        for (final TabProvider<E> tabProvider : usedProviders) {
            tabProvider.bind();
        }
    }

    @Override
    protected void onUnbind() {
        super.onUnbind();
        for (final TabProvider<E> tabProvider : usedProviders) {
            tabProvider.unbind();
        }
    }

    public interface TabProvider<E> extends
            HasDocumentRead<E>,
            HasDocumentWrite<E>,
            HasDirtyHandlers,
            HandlerContainer,
            HasClose {

        PresenterWidget<?> getPresenter();
    }

    public void add(final TabData tab, final TabProvider<E> provider) {
        tabProviders.put(tab, provider);
    }

    public PresenterWidget<?> getPresenter(final TabData tab, final TaskMonitorFactory taskMonitorFactory) {
        currentTabProvider = presenterCache.get(tab);
        if (currentTabProvider == null) {
            final TabProvider<E> provider = tabProviders.get(tab);
            if (provider != null) {
                provider.bind();
                currentTabProvider = provider;
                presenterCache.put(tab, currentTabProvider);

                // Handle dirty events.
                registerHandler(currentTabProvider.addDirtyHandler(this::fireEvent));

                if (currentTabProvider instanceof HasTaskMonitorFactory) {
                    ((HasTaskMonitorFactory) currentTabProvider)
                            .setTaskMonitorFactory(taskMonitorFactory);
                }
                if (currentTabProvider.getPresenter() instanceof HasTaskMonitorFactory) {
                    ((HasTaskMonitorFactory) currentTabProvider.getPresenter())
                            .setTaskMonitorFactory(taskMonitorFactory);
                }
            }
        }

        // Read entity if not read since entity set.
        if (readCount > 0 && !usedProviders.contains(currentTabProvider)) {
            read(currentTabProvider, docRef, entity, readOnly);
        }

        return currentTabProvider.getPresenter();
    }

    @Override
    public void read(final DocRef docRef, final E document, final boolean readOnly) {
        this.docRef = docRef;
        this.entity = document;
        this.readOnly = readOnly;

        for (final TabProvider<E> tabProvider : usedProviders) {
            read(tabProvider, docRef, document, readOnly);
        }
        if (currentTabProvider != null && !usedProviders.contains(currentTabProvider)) {
            read(currentTabProvider, docRef, document, readOnly);
        }

        readCount++;
    }

    public E write(E document) {
        for (final TabProvider<E> tabProvider : usedProviders) {
            document = write(tabProvider, document);
        }
        return document;
    }

    private void read(final TabProvider<E> tabProvider,
                      final DocRef docRef,
                      final E entity,
                      final boolean readOnly) {
        tabProvider.read(docRef, entity, readOnly);
        usedProviders.add(tabProvider);
    }

    private E write(final TabProvider<E> tabProvider, final E entity) {
        return tabProvider.write(entity);
    }

    @Override
    public void onClose() {
        for (final TabProvider<E> tabProvider : usedProviders) {
            tabProvider.onClose();
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return eventBus.addHandlerToSource(DirtyEvent.getType(), this, handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEventFromSource(event, this);
    }
}
