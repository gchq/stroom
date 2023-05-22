/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.entity.client.presenter;

import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.widget.tab.client.presenter.TabData;

import com.google.inject.Provider;
import com.gwtplatform.mvp.client.PresenterWidget;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TabContentProvider<E> implements HasDocumentRead<E>, HasDocumentWrite<E> {

    private final Map<TabData, Provider<?>> tabProviders = new HashMap<>();
    private final Map<TabData, PresenterWidget<?>> presenterCache = new HashMap<>();

    private Set<PresenterWidget<?>> usedPresenters;

    private DirtyHandler dirtyHandler;
    private PresenterWidget<?> currentPresenter;
    private DocRef docRef;
    private E entity;
    private boolean readOnly = true;

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
//                        if (event.isDirty()) {
//                            if (dirtyTabs == null) {
//                                dirtyTabs = new HashSet<>();
//                            }
//
//                            dirtyTabs.add(tab);
//                        }
                        dirtyHandler.onDirty(event);
                    });
                }
            }
        }

        // Read entity if not read since entity set.
        if (usedPresenters == null || !usedPresenters.contains(currentPresenter)) {
            read(currentPresenter, docRef, entity, readOnly);
        }

        return currentPresenter;
    }

    @Override
    public void read(final DocRef docRef, final E document, final boolean readOnly) {
        this.docRef = docRef;
        this.entity = document;
        this.readOnly = readOnly;

        if (usedPresenters != null) {
            for (final PresenterWidget<?> presenterWidget : usedPresenters) {
                read(presenterWidget, docRef, document, readOnly);
            }
            if (currentPresenter != null && !usedPresenters.contains(currentPresenter)) {
                read(currentPresenter, docRef, document, readOnly);
            }
        } else if (currentPresenter != null) {
            read(currentPresenter, docRef, document, readOnly);
        }

//        // Clear the dirty tab state as we are reading a new entity.
//        if (dirtyTabs != null) {
//            dirtyTabs.clear();
//        }
    }

    public E write(E document) {
        if (usedPresenters != null) {
            for (final PresenterWidget<?> presenter : usedPresenters) {
                document = write(presenter, document);
            }
        }
        return document;
    }


    @SuppressWarnings("unchecked")
    private void read(final PresenterWidget<?> presenter,
                      final DocRef docRef,
                      final E entity,
                      final boolean readOnly) {
        if (presenter instanceof HasDocumentRead<?>) {
            final HasDocumentRead<E> hasDocumentRead = (HasDocumentRead<E>) presenter;
            hasDocumentRead.read(docRef, entity, readOnly);

            if (usedPresenters == null) {
                usedPresenters = new HashSet<>();
            }
            usedPresenters.add(presenter);
        }
    }

    @SuppressWarnings("unchecked")
    private E write(final PresenterWidget<?> presenter, E entity) {
        if (entity != null && presenter instanceof HasDocumentWrite<?>) {
            final HasDocumentWrite<E> hasDocumentWrite = (HasDocumentWrite<E>) presenter;
            entity = hasDocumentWrite.write(entity);
        }
        return entity;
    }

    public void setDirtyHandler(final DirtyHandler handler) {
        this.dirtyHandler = handler;
    }
}
