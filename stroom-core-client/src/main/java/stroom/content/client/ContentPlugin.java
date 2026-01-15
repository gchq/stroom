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

package stroom.content.client;

import stroom.core.client.ContentManager;
import stroom.core.client.event.CloseContentEvent;
import stroom.core.client.event.CloseContentEvent.Callback;
import stroom.core.client.presenter.Plugin;
import stroom.data.table.client.Refreshable;
import stroom.widget.tab.client.presenter.TabData;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public abstract class ContentPlugin<P extends MyPresenterWidget<?>> extends Plugin {

    private final ContentManager contentManager;
    private final Provider<P> presenterProvider;
    private P presenter;

    @Inject
    public ContentPlugin(final EventBus eventBus,
                         final ContentManager contentManager,
                         final Provider<P> presenterProvider) {
        super(eventBus);
        this.contentManager = contentManager;
        this.presenterProvider = presenterProvider;
    }

    public void open() {
        open(presenter -> {
        });
    }

    public void open(final Consumer<P> consumer) {
        if (presenter == null) {
            // If the presenter is null then we haven't got this tab open.
            // Create a new presenter.
            presenter = presenterProvider.get();
        }

        final CloseContentEvent.Handler closeHandler = (event) -> {
            if (presenter instanceof CloseContentEvent.Handler) {
                final Callback callback = ok -> {
                    event.getCallback().closeTab(ok);
                    // After we close the tab set the presenter back to null so
                    // that we can open it again.
                    if (ok) {
                        presenter = null;
                    }
                };

                ((CloseContentEvent.Handler) presenter)
                        .onCloseRequest(new CloseContentEvent(event.getDirtyMode(), callback));
                // Give the content manager the ok to close the tab.
            } else {
                event.getCallback().closeTab(true);
                // After we close the tab set the presenter back to null so
                // that we can open it again.
                presenter = null;
            }
        };

        // Tell the content manager to open the tab.
        final TabData tabData = (TabData) presenter;
        contentManager.open(closeHandler, tabData, presenter);

        // If the presenter is refreshable then refresh it.
        if (presenter instanceof Refreshable) {
            final Refreshable refreshable = (Refreshable) presenter;
            refreshable.refresh();
        }
        consumer.accept(presenter);
    }
}
