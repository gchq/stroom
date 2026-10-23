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

import stroom.docref.DocRef;
import stroom.widget.component.client.ComponentNode;
import stroom.widget.component.client.HasComponentNode;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.tab.client.presenter.TabData;

import com.google.web.bindery.event.shared.EventBus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TabContentProvider<E>
        implements
        HasDocumentRead<E>,
        HasDocumentWrite<E>,
        HasClose {

    private final Map<TabData, TabProvider<E>> tabProviders = new HashMap<>();
    private final Map<TabData, TabProvider<E>> presenterCache = new HashMap<>();

    private final Set<TabProvider<E>> usedProviders = new HashSet<>();
    private TabProvider<E> currentTabProvider;
    private DocRef docRef;
    private E entity;
    private int readCount;
    // What owns the tab presenters this makes (the document), so they are disposed with it, see
    // its context and tell it of changes (Contexts.DIRTY). The tab providers are released when it is
    private final ComponentNode owner;

    /// @param eventBus The application's event bus.
    /// @param owner    What owns the tab presenters this makes, e.g. the document's presenter.
    public TabContentProvider(final EventBus eventBus, final HasComponentNode owner) {
        this.owner = owner.getComponentNode();
        // Null only for a mock owner (e.g. Mockito's, in a test)
        if (this.owner != null) {
            this.owner.onDispose(this::release);
        }
    }

    // Releases the providers that made a presenter, with the document
    private void release() {
        for (final TabProvider<E> tabProvider : presenterCache.values()) {
            tabProvider.release();
        }
    }

    public interface TabProvider<E> extends
            HasDocumentRead<E>,
            HasDocumentWrite<E>,
            HasClose {

        MyPresenterWidget<?> getPresenter();

        /// Releases what the provider registered when it made its presenter, e.g. a handler on it,
        /// when the document is disposed.
        default void release() {
        }
    }

    public void add(final TabData tab, final TabProvider<E> provider) {
        tabProviders.put(tab, provider);
    }

    public void replace(final TabData tab, final TabProvider<E> provider) {
        tabProviders.replace(tab, provider);
        presenterCache.replace(tab, provider);
    }

    /// The presenter of a tab, made (and owned) when first asked for. It shows its tasks on the task
    /// monitor its context provides (e.g. its document's tab), as its owner's are.
    ///
    /// @param tab The tab.
    /// @return Its presenter.
    public MyPresenterWidget<?> getPresenter(final TabData tab) {
        return createPresenter(tab);
    }

    private MyPresenterWidget<?> createPresenter(final TabData tab) {
        currentTabProvider = presenterCache.get(tab);
        if (currentTabProvider == null) {
            final TabProvider<E> provider = tabProviders.get(tab);
            if (provider != null) {
                currentTabProvider = provider;
                presenterCache.put(tab, currentTabProvider);
            }
        }

        // Read entity if not read since entity set.
        if (readCount > 0 && !usedProviders.contains(currentTabProvider)) {
            read(currentTabProvider, docRef, entity);
        }

        return ownPresenter(currentTabProvider);
    }

    // The owner owns each tab presenter once it is made (a provider makes it when first asked)
    private MyPresenterWidget<?> ownPresenter(final TabProvider<E> tabProvider) {
        final MyPresenterWidget<?> presenter = tabProvider.getPresenter();
        if (presenter instanceof final HasComponentNode hasComponentNode) {
            final ComponentNode node = hasComponentNode.getComponentNode();
            if (node != null && node.parent() != owner && !node.isDisposed()) {
                owner.adopt(node);
            }
        }
        return presenter;
    }

    @Override
    public void read(final DocRef docRef, final E document) {
        // Tab providers make their presenters when first read, and the owner owns them
        this.docRef = docRef;
        this.entity = document;

        for (final TabProvider<E> tabProvider : usedProviders) {
            read(tabProvider, docRef, document);
        }
        if (currentTabProvider != null && !usedProviders.contains(currentTabProvider)) {
            read(currentTabProvider, docRef, document);
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
                      final E entity) {
        // Owned first, so that it reads the document as read only or not as its owner is
        ownPresenter(tabProvider);
        tabProvider.read(docRef, entity);
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
}
