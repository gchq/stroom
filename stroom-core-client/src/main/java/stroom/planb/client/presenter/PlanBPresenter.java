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

package stroom.planb.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.ControllerTabProvider;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.planb.client.component.PlanBDataController;
import stroom.planb.client.component.PlanBSettingsController;
import stroom.planb.shared.PlanBDoc;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

// The Data tab (PlanBDataController) is second in the tab order after Settings. Settings stays the
// tab a PlanB document opens on - the Data tab is for inspecting what a store holds, not the first
// thing a user wants on opening one.
public class PlanBPresenter extends DocTabPresenter<LinkTabPanelView, PlanBDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData DATA = new TabDataImpl("Data");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    @Inject
    public PlanBPresenter(
            final EventBus eventBus,
            final LinkTabPanelView view,
            final Provider<PlanBDataController> planBDataPresenterProvider,
            final Provider<PlanBSettingsController> planBSettingsProvider,
            final Provider<MarkdownEditController> markdownEditProvider,
            final DocumentUserPermissionsTabProvider<PlanBDoc> documentUserPermissionsTabProvider) {
        super(eventBus, view);

        addTab(SETTINGS, new ControllerTabProvider<>(eventBus, planBSettingsProvider::get,
                PlanBSettingsController::read,
                PlanBSettingsController::write));
        addTab(DATA, new ControllerTabProvider<>(eventBus, planBDataPresenterProvider::get, PlanBDataController::read));
        addTab(DOCUMENTATION, new MarkdownTabProvider<>(getEventBus(), markdownEditProvider::get) {
            @Override
            public void onRead(final MarkdownEditController markdownEdit,
                               final DocRef docRef,
                               final PlanBDoc document) {
                markdownEdit.setText(document.getDescription());
                markdownEdit.setReadOnly(isReadOnly());
            }

            @Override
            public PlanBDoc onWrite(final MarkdownEditController markdownEdit,
                                    final PlanBDoc document) {
                return document.copy().description(markdownEdit.getText()).build();
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(SETTINGS);
    }

    @Override
    public String getType() {
        return PlanBDoc.TYPE;
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
