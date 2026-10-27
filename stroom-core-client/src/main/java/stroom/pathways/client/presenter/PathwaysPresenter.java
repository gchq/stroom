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

package stroom.pathways.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.ControllerTabProvider;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.pathways.client.component.PathwaysSettingsController;
import stroom.pathways.client.component.PathwaysSplitController;
import stroom.pathways.shared.PathwaysDoc;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

public class PathwaysPresenter extends DocTabPresenter<LinkTabPanelView, PathwaysDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData PATHWAYS = new TabDataImpl("Pathways");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    @Inject
    public PathwaysPresenter(final EventBus eventBus,
                             final LinkTabPanelView view,
                             final Provider<PathwaysSettingsController> pathwaysSettingsProvider,
                             final Provider<PathwaysSplitController> pathwaysSplitProvider,
                             final Provider<MarkdownEditController> markdownEditProvider,
                             final DocumentUserPermissionsTabProvider<PathwaysDoc> documentUserPermissionsTabProvider,
                             final ClientSecurityContext securityContext) {
        super(eventBus, view);

        addTab(PATHWAYS, new ControllerTabProvider<>(eventBus, pathwaysSplitProvider::get,
                PathwaysSplitController::read));
        addTab(SETTINGS, new ControllerTabProvider<>(eventBus, pathwaysSettingsProvider::get,
                PathwaysSettingsController::read,
                PathwaysSettingsController::write));
        addTab(DOCUMENTATION, new MarkdownTabProvider<PathwaysDoc>(getEventBus(), markdownEditProvider::get) {
            @Override
            public void onRead(final MarkdownEditController markdownEdit,
                               final DocRef docRef,
                               final PathwaysDoc document) {
                markdownEdit.setText(document.getDescription());
                markdownEdit.setReadOnly(isReadOnly());
            }

            @Override
            public PathwaysDoc onWrite(final MarkdownEditController markdownEdit,
                                       final PathwaysDoc document) {
                return document.copy().description(markdownEdit.getText()).build();
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(PATHWAYS);
    }

    @Override
    public String getType() {
        return PathwaysDoc.TYPE;
    }

    @Override
    protected TabData getPermissionsTab() {
        return PERMISSIONS;
    }

    @Override
    protected TabData getDocumentationTab() {
        return DOCUMENTATION;
    }
}
