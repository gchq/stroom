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

package stroom.dashboard.client.main;

import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.DocumentEditTabProvider;
import stroom.entity.client.presenter.HasToolbar;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
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
        extends DocumentEditTabPresenter<LinkTabPanelView, DashboardDoc>
        implements HasToolbar {

    private static final TabData DASHBOARD = new TabDataImpl("Dashboard", DashboardDoc.TYPE);
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final DashboardPresenter dashboardPresenter;

    @Inject
    public DashboardSuperPresenter(final EventBus eventBus,
                                   final LinkTabPanelView view,
                                   final DashboardPresenter dashboardPresenter,
                                   final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                                   final DocumentUserPermissionsTabProvider<DashboardDoc>
                                           documentUserPermissionsTabProvider) {
        super(eventBus, view);
        this.dashboardPresenter = dashboardPresenter;

        addTab(DASHBOARD, new DocumentEditTabProvider<>(() -> dashboardPresenter));
        addTab(DOCUMENTATION, new MarkdownTabProvider<DashboardDoc>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final DashboardDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public DashboardDoc onWrite(final MarkdownEditPresenter presenter,
                                        final DashboardDoc document) {
                document.setDescription(presenter.getText());
                return document;
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(DASHBOARD);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getEventBus().addHandler(ContentTabSelectionChangeEvent.getType(), e ->
                dashboardPresenter.onContentTabVisible(e.getTabData() == this)));
    }

    public void setParentContext(final Object context) {
        if (context instanceof final DashboardContext dashboardContext) {
            dashboardPresenter.setParentContext(dashboardContext);
        }
    }

    @Override
    protected void onRead(final DocRef docRef,
                          final DashboardDoc document,
                          final boolean readOnly) {
        super.onRead(docRef, document, readOnly);
        dashboardPresenter.onRead(docRef, document, readOnly);
    }

    @Override
    public boolean handleKeyAction(final Action action) {
        if (Action.OK == action
            && Objects.equals(getSelectedTab().getType(), DASHBOARD.getType())) {
            dashboardPresenter.start();
            return true;
        } else if (Action.CLOSE == action
                   && Objects.equals(getSelectedTab().getType(), DASHBOARD.getType())) {
            dashboardPresenter.stop();
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
    public List<Widget> getToolbars() {
        return dashboardPresenter.getToolbars();
    }

    public DashboardPresenter getDashboardPresenter() {
        return dashboardPresenter;
    }

    @Override
    public String getType() {
        return DashboardDoc.TYPE;
    }

    @Override
    public String getLabel() {
        return dashboardPresenter.getLabel();
    }

    public void setParamsFromLink(final String params) {
        dashboardPresenter.setParamsFromLink(params);
    }

    public void setCustomTitle(final String customTitle) {
        dashboardPresenter.setCustomTitle(customTitle);
    }

    public void setQueryOnOpen(final boolean queryOnOpen) {
        dashboardPresenter.setQueryOnOpen(queryOnOpen);
    }

    public void setResultStoreInfo(final ResultStoreInfo resultStoreInfo) {
        dashboardPresenter.setResultStoreInfo(resultStoreInfo);
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
