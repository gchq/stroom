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

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import stroom.app.client.ContentManager;
import stroom.app.client.ContentManager.CloseCallback;
import stroom.app.client.ContentManager.CloseHandler;
import stroom.app.client.presenter.Plugin;
import stroom.data.table.client.Refreshable;
import stroom.widget.tab.client.presenter.TabData;

public abstract class ContentPlugin<P extends MyPresenterWidget<?>> extends Plugin {
    private final ContentManager contentManager;
    private final Provider<P> presenterProvider;
    private P presenter;

    @Inject
    public ContentPlugin(final EventBus eventBus, final ContentManager contentManager,
            final Provider<P> presenterProvider) {
        super(eventBus);
        this.contentManager = contentManager;
        this.presenterProvider = presenterProvider;
    }

    public void open() {
        if (presenter == null) {
            // If the presenter is null then we haven't got this tab open.
            // Create a new presenter.
            presenter = presenterProvider.get();
        }

        final CloseHandler closeHandler = new CloseHandler() {
            @Override
            public void onCloseRequest(final CloseCallback callback) {
                // Give the content manager the ok to close the tab.
                callback.closeTab(true);

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
    }
}
