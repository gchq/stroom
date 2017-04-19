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

package stroom.entity.client.presenter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.inject.Provider;
import com.gwtplatform.mvp.client.PresenterWidget;

import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.widget.tab.client.presenter.TabData;

public class TabContentProvider<E> implements HasRead<E>, HasWrite<E> {
    private final Map<TabData, Provider<?>> tabProviders = new HashMap<TabData, Provider<?>>();
    private final Map<TabData, PresenterWidget<?>> presenterCache = new HashMap<TabData, PresenterWidget<?>>();

    private Set<PresenterWidget<?>> usedPresenters;
    private Set<TabData> dirtyTabs;

    private DirtyHandler dirtyHandler;
    private PresenterWidget<?> currentPresenter;
    private E entity;

    public <T extends PresenterWidget<?>> void add(final TabData tab, final Provider<T> provider) {
        tabProviders.put(tab, provider);
    }

    @SuppressWarnings("unchecked")
    public PresenterWidget<?> getPresenter(final TabData tab) {
        currentPresenter = presenterCache.get(tab);
        if (currentPresenter == null) {
            final Provider<PresenterWidget<?>> provider = (Provider<PresenterWidget<?>>) tabProviders.get(tab);
            if (provider != null) {
                currentPresenter = provider.get();
                presenterCache.put(tab, currentPresenter);

                // Handle dirty events.
                if (currentPresenter instanceof HasDirtyHandlers && dirtyHandler != null) {
                    final HasDirtyHandlers hasDirtyHandlers = (HasDirtyHandlers) currentPresenter;
                    hasDirtyHandlers.addDirtyHandler(event -> {
                        if (event.isDirty()) {
                            if (dirtyTabs == null) {
                                dirtyTabs = new HashSet<TabData>();
                            }

                            dirtyTabs.add(tab);
                        }
                        dirtyHandler.onDirty(event);
                    });
                }
            }
        }

        // Read entity if not read since entity set.
        if (usedPresenters == null || !usedPresenters.contains(currentPresenter)) {
            read(currentPresenter, entity);
        }

        return currentPresenter;
    }

    @Override
    public void read(final E entity) {
        this.entity = entity;

        // Clear the used presenter set as we are reading a new entity.
        if (usedPresenters != null) {
            usedPresenters.clear();
        }
        // Clear the dirty tab state as we are reading a new entity.
        if (dirtyTabs != null) {
            dirtyTabs.clear();
        }

        // If there is currently a presenter visible then let it read the
        // entity.
        if (currentPresenter != null) {
            read(currentPresenter, entity);
        }
    }

    @Override
    public void write(final E entity) {
        if (usedPresenters != null) {
            for (final PresenterWidget<?> presenter : usedPresenters) {
                write(presenter, entity);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void read(final PresenterWidget<?> presenter, final E entity) {
        if (presenter != null && presenter instanceof HasRead<?>) {
            final HasRead<E> hasRead = (HasRead<E>) presenter;
            hasRead.read(entity);

            if (usedPresenters == null) {
                usedPresenters = new HashSet<PresenterWidget<?>>();
            }
            usedPresenters.add(presenter);
        }
    }

    @SuppressWarnings("unchecked")
    private void write(final PresenterWidget<?> presenter, final E entity) {
        if (entity != null && presenter != null && presenter instanceof HasWrite<?>) {
            final HasWrite<E> hasWrite = (HasWrite<E>) presenter;
            hasWrite.write(entity);
        }
    }

    public void setDirtyHandler(final DirtyHandler handler) {
        this.dirtyHandler = handler;
    }

    public boolean isTabDirty(final TabData tab) {
        return dirtyTabs != null && dirtyTabs.contains(tab);
    }
}
