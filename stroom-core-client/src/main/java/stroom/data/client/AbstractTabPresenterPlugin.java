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

package stroom.data.client;

import stroom.alert.client.event.AlertEvent;
import stroom.content.client.event.SelectContentTabEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.core.client.ContentManager;
import stroom.core.client.event.CloseContentEvent;
import stroom.core.client.presenter.Plugin;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;

import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class AbstractTabPresenterPlugin<K, T extends ContentTabPresenter<?>>
        extends Plugin {

    // Bi-directional maps to hold the open presenters
    private final Map<K, T> keyToPresenterMap = new HashMap<>();
    private final Map<T, K> presenterToKeyMap = new HashMap<>();

    private final ContentManager contentManager;
    private final Provider<T> tabPresenterProvider;

    public AbstractTabPresenterPlugin(final EventBus eventBus,
                                      final ContentManager contentManager,
                                      final Provider<T> tabPresenterProvider) {
        super(eventBus);
        this.contentManager = contentManager;
        this.tabPresenterProvider = tabPresenterProvider;
    }

    /**
     * @return The name of the presenter being opened
     */
    protected abstract String getName();

    /**
     * @param forceOpen            If false will not open the presenter if it is not already open
     * @param presenterKey         The unique key that identifies the presenter
     * @param tabPresenterConsumer Method to modify the presenter before it is opened, i.e. telling
     *                             it what content to display.
     * @return The
     */
    protected Optional<T> openTabPresenter(final boolean forceOpen,
                                           final K presenterKey,
                                           final Consumer<T> tabPresenterConsumer) {

        T tabPresenter = null;
        if (presenterKey == null) {
            AlertEvent.fireError(
                    AbstractTabPresenterPlugin.this,
                    "Missing key for " + getName(),
                    null);
        } else {
            tabPresenter = keyToPresenterMap.get(presenterKey);
            final TaskMonitor taskMonitor = new DefaultTaskMonitorFactory(this).createTaskMonitor();
            final Task task = new SimpleTask("Opening " + getName() + " (" + presenterKey + ")");

            if (tabPresenter != null) {
                // If we already have a tab item for this key then make sure it is visible.

                // Start spinning.
                taskMonitor.onStart(task);

                // Let the caller set what it wants on the presenter
                tabPresenterConsumer.accept(tabPresenter);

                // Tell the content presenter to select this existing tab.
                SelectContentTabEvent.fire(this, tabPresenter);

                // Stop spinning.
                taskMonitor.onEnd(task);

            } else if (forceOpen) {
                // Start spinning.
                taskMonitor.onStart(task);

                // If the item isn't already open but we are forcing it open then,
                // create a new presenter and register it as open.
                tabPresenter = tabPresenterProvider.get();

                // Let the caller set what it wants on the presenter
                tabPresenterConsumer.accept(tabPresenter);

                // Register the tab as being open.
                keyToPresenterMap.put(presenterKey, tabPresenter);
                presenterToKeyMap.put(tabPresenter, presenterKey);

                final CloseContentEvent.Handler closeHandler = createCloseHandler(tabPresenter);

                // Load the document and show the tab.
                showTab(presenterKey, tabPresenter, closeHandler, taskMonitor, task);
            }
        }

        return Optional.ofNullable(tabPresenter);
    }

    private void showTab(final K presenterKey,
                         final T tabPresenter,
                         final CloseContentEvent.Handler closeHandler,
                         final TaskMonitor taskMonitor,
                         final Task task) {
        try {
            if (presenterKey == null) {
                AlertEvent.fireError(
                        AbstractTabPresenterPlugin.this,
                        "Unable to load " + getName() + " (null)",
                        null);
            } else {
                // Open the tab.
                contentManager.open(
                        closeHandler,
                        tabPresenter,
                        tabPresenter);
            }
        } finally {
            // Stop spinning.
            taskMonitor.onEnd(task);
        }
    }

    private void removeTabData(final T tabPresenter) {
        final K presenterKey = presenterToKeyMap.remove(tabPresenter);
        keyToPresenterMap.remove(presenterKey);
    }

    private CloseContentEvent.Handler createCloseHandler(final T tabPresenter) {
        return event -> {
            if (tabPresenter != null) {
                // Tell the presenter we are closing.
//                    sourceTabPresenter.onClose();
                // Cleanup reference to this tab data.
                removeTabData(tabPresenter);
                // Tell the callback to close the tab if ok.
                event.getCallback().closeTab(true);
            }
        };
    }
}
