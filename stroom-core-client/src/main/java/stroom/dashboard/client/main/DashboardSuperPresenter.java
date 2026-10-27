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

package stroom.dashboard.client.main;

import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.dashboard.client.component.DashboardBodyController;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.document.client.HasMultipleInstances;
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.ControllerTabProvider;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.HasToolbar;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.query.api.ResultStoreInfo;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import javax.inject.Provider;

public class DashboardSuperPresenter
        extends DocTabPresenter<LinkTabPanelView, DashboardDoc>
        implements HasToolbar, HasMultipleInstances {

    private static final TabData DASHBOARD = new TabDataImpl("Dashboard", DashboardDoc.TYPE);
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final DashboardBodyController dashboardBody;
    private int instance;

    @Inject
    public DashboardSuperPresenter(final EventBus eventBus,
                                   final LinkTabPanelView view,
                                   final DashboardBodyController dashboardBody,
                                   final Provider<MarkdownEditController> markdownEditProvider,
                                   final DocumentUserPermissionsTabProvider<DashboardDoc>
                                           documentUserPermissionsTabProvider) {
        super(eventBus, view);
        own(dashboardBody);
        this.dashboardBody = dashboardBody;

        addTab(DASHBOARD, ControllerTabProvider.ofOwned(eventBus, dashboardBody,
                DashboardBodyController::read,
                DashboardBodyController::write));
        addTab(DOCUMENTATION, new MarkdownTabProvider<DashboardDoc>(getEventBus(), markdownEditProvider::get) {
            @Override
            public void onRead(final MarkdownEditController markdownEdit,
                               final DocRef docRef,
                               final DashboardDoc document) {
                markdownEdit.setText(document.getDescription());
                markdownEdit.setReadOnly(isReadOnly());
            }

            @Override
            public DashboardDoc onWrite(final MarkdownEditController markdownEdit,
                                        final DashboardDoc document) {
                return document.copy().description(markdownEdit.getText()).build();
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(DASHBOARD);

        listen(ContentTabSelectionChangeEvent.getType(), e ->
                dashboardBody.onContentTabVisible(e.getTabData() == this));
    }

    public void setParentContext(final Object context) {
        if (context instanceof final DashboardContext dashboardContext) {
            dashboardBody.setParentContext(dashboardContext);
        }
    }

    @Override
    protected void onRead(final DocRef docRef,
                          final DashboardDoc document) {
        super.onRead(docRef, document);
        dashboardBody.read(docRef, document);
    }

    @Override
    public boolean handleKeyAction(final Action action) {
        if (Action.OK == action
            && Objects.equals(getSelectedTab().getType(), DASHBOARD.getType())) {
            dashboardBody.start();
            return true;
        } else if (Action.CLOSE == action
                   && Objects.equals(getSelectedTab().getType(), DASHBOARD.getType())) {
            dashboardBody.stop();
            return true;
//        } else if (Action.DOCUMENTATION == action
//                && Objects.equals(getSelectedTab().getType(), DASHBOARD.getType())) {
//            selectTab(DOCUMENTATION);
//            return true;
        } else {
            return false;
        }
    }

    @Override
    public List<Widget> getDocumentButtons() {
        return dashboardBody.getDocumentButtons();
    }

    @Override
    public List<Widget> getToolbars() {
        return dashboardBody.getToolbars();
    }

    /// @return The dashboard's body.
    public DashboardBodyController getDashboardBody() {
        return dashboardBody;
    }

    @Override
    public String getType() {
        return DashboardDoc.TYPE;
    }

    @Override
    public String getLabel() {
        final String label = dashboardBody.getTitle() + getInstanceString();
        return isDirty()
                ? "* " + label
                : label;
    }

    @Override
    public void onClose() {
        dashboardBody.onClose();
        super.onClose();
    }

    public void setInstance(final int instance) {
        this.instance = instance;
    }

    @Override
    public int getInstance() {
        return instance;
    }

    public void setParamsFromLink(final String params) {
        dashboardBody.setParamsFromLink(params);
    }

    public void setCustomTitle(final String customTitle) {
        dashboardBody.setCustomTitle(customTitle);
    }

    public void setQueryOnOpen(final boolean queryOnOpen) {
        dashboardBody.setQueryOnOpen(queryOnOpen);
    }

    public void setResultStoreInfo(final ResultStoreInfo resultStoreInfo) {
        dashboardBody.setResultStoreInfo(resultStoreInfo);
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
