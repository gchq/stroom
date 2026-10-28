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

package stroom.content.client;

import stroom.core.client.ContentManager;
import stroom.core.client.TabPlugin;
import stroom.core.client.event.CloseContentEvent;
import stroom.core.client.event.CloseContentEvent.Callback;
import stroom.data.table.client.Refreshable;
import stroom.document.client.DocumentPluginRegistry;
import stroom.widget.component.client.Layer;
import stroom.widget.tab.client.TabData;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

public abstract class ContentPlugin<P extends Layer & TabData> extends TabPlugin {

    private final ContentManager contentManager;
    private final Provider<P> controllerProvider;
    private P controller;

    @Inject
    public ContentPlugin(final EventBus eventBus,
                         final ContentManager contentManager,
                         final Provider<P> controllerProvider,
                         final DocumentPluginRegistry documentPluginRegistry) {
        super(eventBus);
        this.contentManager = contentManager;
        this.controllerProvider = controllerProvider;

        register(documentPluginRegistry);
    }

    private void register(final DocumentPluginRegistry documentPluginRegistry) {
        final String type = getType();
        if (type != null) {
            documentPluginRegistry.register(getType(), this);
        }
    }

    public void open() {
        open(controller -> {
        });
    }

    public void open(final Consumer<P> consumer) {
        if (controller == null) {
            // If the presenter is null then we haven't got this tab open.
            // Create a new presenter, which the content manager releases, with every presenter it
            // owns, when the tab is closed
            controller = controllerProvider.get();
        }

        final CloseContentEvent.Handler closeHandler = (event) -> {
            if (controller instanceof CloseContentEvent.Handler) {
                final Callback callback = ok -> {
                    event.getCallback().closeTab(ok);
                    // After we close the tab set the presenter back to null so
                    // that we can open it again.
                    if (ok) {
                        controller = null;
                    }
                };

                ((CloseContentEvent.Handler) controller)
                        .onCloseRequest(new CloseContentEvent(event.getDirtyMode(), callback));
                // Give the content manager the ok to close the tab.
            } else {
                event.getCallback().closeTab(true);
                // After we close the tab set the presenter back to null so
                // that we can open it again.
                controller = null;
            }
        };

        // Tell the content manager to open the tab.
        contentManager.open(closeHandler, controller, controller);

        // If the presenter is refreshable then refresh it.
        if (controller instanceof final Refreshable refreshable) {
            refreshable.refresh();
        }
        consumer.accept(controller);
    }
}
