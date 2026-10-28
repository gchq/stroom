/*
 * Copyright 2020 Crown Copyright
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
import stroom.core.client.ContentManager;
import stroom.core.client.Plugin;
import stroom.core.client.event.CloseContentEvent;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.widget.component.client.Layer;
import stroom.widget.tab.client.TabData;

import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class AbstractTabPlugin<K, T extends Layer & TabData>
        extends Plugin {

    // Bi-directional maps to hold the open presenters
    private final Map<K, T> keyToControllerMap = new HashMap<>();
    private final Map<T, K> controllerToKeyMap = new HashMap<>();

    private final ContentManager contentManager;
    private final Provider<T> tabControllerProvider;

    public AbstractTabPlugin(final EventBus eventBus,
                                      final ContentManager contentManager,
                                      final Provider<T> tabControllerProvider) {
        super(eventBus);
        this.contentManager = contentManager;
        this.tabControllerProvider = tabControllerProvider;
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
    protected Optional<T> openTabController(final boolean forceOpen,
                                           final K controllerKey,
                                           final Consumer<T> tabControllerConsumer) {

        T tabController = null;
        if (controllerKey == null) {
            AlertEvent.fireError(
                    AbstractTabPlugin.this,
                    "Missing key for " + getName(),
                    null);
        } else {
            tabController = keyToControllerMap.get(controllerKey);
            final TaskMonitor taskMonitor = new DefaultTaskMonitorFactory(this).createTaskMonitor();
            final Task task = new SimpleTask("Opening " + getName() + " (" + controllerKey + ")");

            if (tabController != null) {
                // If we already have a tab item for this key then make sure it is visible.

                // Start spinning.
                taskMonitor.onStart(task);

                // Let the caller set what it wants on the presenter
                tabControllerConsumer.accept(tabController);

                // Tell the content presenter to select this existing tab.
                SelectContentTabEvent.fire(this, tabController);

                // Stop spinning.
                taskMonitor.onEnd(task);

            } else if (forceOpen) {
                // Start spinning.
                taskMonitor.onStart(task);

                // If the item isn't already open but we are forcing it open then,
                // create a new presenter and register it as open.
                tabController = tabControllerProvider.get();

                // Let the caller set what it wants on the presenter
                tabControllerConsumer.accept(tabController);

                // Register the tab as being open.
                keyToControllerMap.put(controllerKey, tabController);
                controllerToKeyMap.put(tabController, controllerKey);

                final CloseContentEvent.Handler closeHandler = createCloseHandler(tabController);

                // Load the document and show the tab.
                showTab(controllerKey, tabController, closeHandler, taskMonitor, task);
            }
        }

        return Optional.ofNullable(tabController);
    }

    private void showTab(final K controllerKey,
                         final T tabController,
                         final CloseContentEvent.Handler closeHandler,
                         final TaskMonitor taskMonitor,
                         final Task task) {
        try {
            if (controllerKey == null) {
                AlertEvent.fireError(
                        AbstractTabPlugin.this,
                        "Unable to load " + getName() + " (null)",
                        null);
            } else {
                // Open the tab.
                contentManager.open(
                        closeHandler,
                        tabController,
                        tabController);
            }
        } finally {
            // Stop spinning.
            taskMonitor.onEnd(task);
        }
    }

    private void removeTabData(final T tabController) {
        final K controllerKey = controllerToKeyMap.remove(tabController);
        keyToControllerMap.remove(controllerKey);
    }

    private CloseContentEvent.Handler createCloseHandler(final T tabController) {
        return event -> {
            if (tabController != null) {
                // Tell the presenter we are closing.
//                    sourceTabPresenter.onClose();
                // Cleanup reference to this tab data.
                removeTabData(tabController);
                // Tell the callback to close the tab if ok.
                event.getCallback().closeTab(true);
            }
        };
    }
}
