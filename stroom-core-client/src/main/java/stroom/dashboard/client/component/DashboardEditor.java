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

package stroom.dashboard.client.component;

import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.shared.DashboardDoc;
import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.LinkTabPanel;
import stroom.entity.client.component.MarkdownEditController;
import stroom.query.api.ResultStoreInfo;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A dashboard's editor: its Dashboard (the dashboard itself), Documentation and Permissions tabs
/// (see [DocumentEditor]). Its tab is labelled with the dashboard's title. With the Dashboard tab
/// showing, OK runs its queries and Close stops them.
public class DashboardEditor extends DocumentEditor<DashboardDoc> {

    private static final TabData DASHBOARD = new TabDataImpl("Dashboard", DashboardDoc.TYPE);
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final DashboardBodyController dashboardBody;

    /// Makes the editor, with its Dashboard, Documentation and Permissions tabs.
    @Inject
    public DashboardEditor(final EventBus eventBus,
                           final LinkTabPanel view,
                           final DashboardBodyController dashboardBody,
                           final Provider<MarkdownEditController> markdownEditProvider,
                           final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);
        this.dashboardBody = own(dashboardBody);
        addOwnedTab(DASHBOARD, dashboardBody, DashboardBodyController::read, DashboardBodyController::write);
        addDocumentationTab(DOCUMENTATION,
                markdownEditProvider,
                DashboardDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        selectTab(DASHBOARD);
    }

    /// @return The dashboard itself, its Dashboard tab.
    public DashboardBodyController getDashboardBody() {
        return dashboardBody;
    }

    /// @param context The context of the dashboard this one is opened from, if any.
    public void setParentContext(final Object context) {
        if (context instanceof final DashboardContext dashboardContext) {
            dashboardBody.setParentContext(dashboardContext);
        }
    }

    /// @param params The parameters of the link the dashboard is opened from.
    public void setParamsFromLink(final String params) {
        dashboardBody.setParamsFromLink(params);
    }

    /// @param customTitle The title to show in place of the dashboard's own.
    public void setCustomTitle(final String customTitle) {
        dashboardBody.setCustomTitle(customTitle);
    }

    /// @param queryOnOpen Whether to run the dashboard's queries once it is opened.
    public void setQueryOnOpen(final boolean queryOnOpen) {
        dashboardBody.setQueryOnOpen(queryOnOpen);
    }

    /// @param resultStoreInfo The results of an earlier search to show, reopening it.
    public void setResultStoreInfo(final ResultStoreInfo resultStoreInfo) {
        dashboardBody.setResultStoreInfo(resultStoreInfo);
    }

    @Override
    public String getTabLabel() {
        return dashboardBody.getTitle();
    }

    @Override
    public boolean handleKeyAction(final Action action) {
        if (Action.OK == action && DASHBOARD.equals(getSelectedTab())) {
            dashboardBody.start();
            return true;
        } else if (Action.CLOSE == action && DASHBOARD.equals(getSelectedTab())) {
            dashboardBody.stop();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onContentTabVisible(final boolean visible) {
        dashboardBody.onContentTabVisible(visible);
    }

    @Override
    public void onClose() {
        dashboardBody.onClose();
    }
}
