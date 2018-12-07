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

package stroom.dashboard.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.core.client.ContentManager.CloseHandler;
import stroom.dashboard.client.event.ShowDashboardEvent;
import stroom.dashboard.client.main.DashboardPresenter;
import stroom.dashboard.shared.Dashboard;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.document.client.DocumentPluginEventManager;
import stroom.document.client.DocumentTabData;
import stroom.entity.client.EntityPlugin;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.query.api.v2.DocRef;
import stroom.svg.client.Icon;
import stroom.task.client.TaskStartEvent;

public class DashboardPlugin extends EntityPlugin<Dashboard> {
    private final Provider<DashboardPresenter> editorProvider;

    @Inject
    public DashboardPlugin(final EventBus eventBus,
                           final Provider<DashboardPresenter> editorProvider,
                           final ClientDispatchAsync dispatcher,
                           final ContentManager contentManager,
                           final DocumentPluginEventManager entityPluginEventManager) {
        super(eventBus, dispatcher, contentManager, entityPluginEventManager);
        this.editorProvider = editorProvider;

        registerHandler(eventBus.addHandler(ShowDashboardEvent.getType(), event -> openParameterisedDashboard(event.getTitle(), event.getDocRef(), event.getParams())));
    }

    private void openParameterisedDashboard(final String title, final DocRef docRef, final String params) {
        // Start spinning.
        TaskStartEvent.fire(this, "Opening document");

        // If the item isn't already open but we are forcing it open then,
        // create a new presenter and register it as open.
        final DashboardPresenter presenter = (DashboardPresenter) createEditor();
        presenter.setParams(params);

//        // Register the tab as being open.
//        documentToTabDataMap.put(docRef, tabData);
//        tabDataToDocumentMap.put(tabData, docRef);

        final DocumentTabData tabData = new DocumentTabData() {
            @Override
            public String getType() {
                return presenter.getType();
            }

            @Override
            public Icon getIcon() {
                return presenter.getIcon();
            }

            @Override
            public String getLabel() {
                return title;
            }

            @Override
            public boolean isCloseable() {
                return presenter.isCloseable();
            }
        };

        // Load the document and show the tab.
        final CloseHandler closeHandler = callback -> callback.closeTab(true);
        showTab(docRef, presenter, closeHandler, tabData);
    }

    @Override
    protected DocumentEditPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    public String getType() {
        return Dashboard.ENTITY_TYPE;
    }
}
