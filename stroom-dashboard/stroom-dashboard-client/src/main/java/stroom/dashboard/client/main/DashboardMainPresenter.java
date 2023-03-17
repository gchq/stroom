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

package stroom.dashboard.client.main;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.presenter.CorePresenter;
import stroom.dashboard.client.main.DashboardMainPresenter.DashboardMainProxy;
import stroom.dashboard.client.main.DashboardMainPresenter.DashboardMainView;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.main.client.event.UrlQueryParameterChangeEvent;
import stroom.main.client.event.UrlQueryParameterChangeEvent.UrlQueryParameterChangeHandler;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ContentSlot;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.annotations.ProxyStandard;
import com.gwtplatform.mvp.client.proxy.Proxy;
import com.gwtplatform.mvp.client.proxy.RevealContentEvent;
import com.gwtplatform.mvp.client.proxy.RevealContentHandler;

import java.util.Map;
import javax.inject.Inject;

public class DashboardMainPresenter
        extends MyPresenter<DashboardMainView, DashboardMainProxy>
        implements UrlQueryParameterChangeHandler {

    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);
    private static final String SHOW_DASHBOARD_ACTION = "open-dashboard";

    @ContentSlot
    public static final GwtEvent.Type<RevealContentHandler<?>> CONTENT = new GwtEvent.Type<>();

    private final DashboardPresenter dashboardPresenter;
    private final RestFactory restFactory;

    @Inject
    public DashboardMainPresenter(final EventBus eventBus,
                                  final DashboardMainView view,
                                  final DashboardMainProxy proxy,
                                  final RestFactory restFactory,
                                  final DashboardPresenter dashboardPresenter) {
        super(eventBus, view, proxy);
        this.dashboardPresenter = dashboardPresenter;
        this.restFactory = restFactory;

        dashboardPresenter.addDirtyHandler(event -> Window.setTitle(dashboardPresenter.getLabel()));
        Window.addWindowClosingHandler(event -> {
            if (dashboardPresenter.isDirty()) {
                event.setMessage("Dashboard '" + dashboardPresenter.getTitle() + "' " +
                        "has unsaved changes. Are you sure you want to close it?");
            }
        });
    }

    private void onLoadSuccess(final DashboardDoc dashboard) {
        if (dashboard == null) {
            AlertEvent.fireError(this, "No dashboard uuid has been specified", null);

        } else {
            setInSlot(CONTENT, dashboardPresenter);
            forceReveal();

            dashboardPresenter.read(DocRefUtil.create(dashboard), dashboard);
            Window.setTitle(dashboardPresenter.getLabel());
        }
    }

    private void onLoadFailure(final Throwable throwable) {
        AlertEvent.fireError(this, throwable.getMessage(), null);
    }

    @Override
    protected void revealInParent() {
        RevealContentEvent.fire(this, CorePresenter.CORE, this);
        RootPanel.get("logo").setVisible(false);
    }

    @ProxyEvent
    @Override
    public void onChange(final UrlQueryParameterChangeEvent event) {
        if (SHOW_DASHBOARD_ACTION.equals(event.getAction())) {
            final Map<String, String> queryParams = event.getQueryParams();
            final String type = queryParams.get("type");
            final String uuid = queryParams.get("uuid");
            final String title = queryParams.get("title");
            final String params = queryParams.get("params");
            final boolean embedded = Boolean.TRUE.toString().equalsIgnoreCase(queryParams.get("embedded"));
            final boolean queryOnOpen = Boolean.TRUE.toString().equalsIgnoreCase(queryParams.get("queryOnOpen"));

            dashboardPresenter.setCustomTitle(title);
            dashboardPresenter.setParams(params);
            dashboardPresenter.setEmbedded(embedded);
            dashboardPresenter.setQueryOnOpen(queryOnOpen);

            if (title != null && title.trim().length() > 0) {
                Window.setTitle(title);
            }

            if (type == null || uuid == null) {
                AlertEvent.fireError(this, "No dashboard uuid has been specified", null);

            } else {
                final DocRef docRef = new DocRef(type, uuid);
                final Rest<DashboardDoc> rest = restFactory.create();
                rest
                        .onSuccess(this::onLoadSuccess)
                        .onFailure(this::onLoadFailure)
                        .call(DASHBOARD_RESOURCE)
                        .fetch(docRef.getUuid());
            }
        }
    }

    @ProxyStandard
    public interface DashboardMainProxy extends Proxy<DashboardMainPresenter> {

    }

    public interface DashboardMainView extends View {

    }
}
