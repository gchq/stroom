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

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootLayoutPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ContentSlot;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.annotations.ProxyStandard;
import com.gwtplatform.mvp.client.proxy.Proxy;
import com.gwtplatform.mvp.client.proxy.RevealContentHandler;
import com.gwtplatform.mvp.client.proxy.RevealRootContentEvent;
import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.shared.Dashboard;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocumentServiceReadAction;
import stroom.query.api.v1.DocRef;
import stroom.security.client.ClientSecurityContext;
import stroom.security.client.event.CurrentUserChangedEvent;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;

import javax.inject.Inject;

public class DashboardAppPresenter
        extends MyPresenter<DashboardAppPresenter.DashboardAppView, DashboardAppPresenter.DashboardAppProxy>
        implements TaskStartEvent.TaskStartHandler, TaskEndEvent.TaskEndHandler {
    @ContentSlot
    public static final GwtEvent.Type<RevealContentHandler<?>> CONTENT = new GwtEvent.Type<>();

    private final ClientSecurityContext securityContext;
    private final DashboardPresenter dashboardPresenter;

    private Dashboard dashboard;
    private String params;

    @Inject
    public DashboardAppPresenter(final EventBus eventBus, final DashboardAppView view, final DashboardAppProxy proxy,
                                 final ClientSecurityContext securityContext, final ClientDispatchAsync dispatcher,
                                 final DashboardPresenter dashboardPresenter) {
        super(eventBus, view, proxy);
        this.securityContext = securityContext;
        this.dashboardPresenter = dashboardPresenter;

        eventBus.addHandler(CurrentUserChangedEvent.getType(), event -> {
            RootLayoutPanel.get().clear();
            RootPanel.get().clear();

            final String type = Window.Location.getParameter("type");
            final String uuid = Window.Location.getParameter("uuid");
            params = Window.Location.getParameter("params");

            if (type == null || uuid == null) {
                AlertEvent.fireError(this, "No dashboard uuid has been specified", null);

            } else {
                final DocRef docRef = new DocRef(type, uuid);
                dispatcher.exec(new DocumentServiceReadAction<Dashboard>(docRef))
                        .onSuccess(this::onLoadSuccess)
                        .onFailure(this::onLoadFailure);
            }
        });

        dashboardPresenter.addDirtyHandler(event -> {
            if (dashboard != null) {
                if (event.isDirty()) {
                    Window.setTitle("* " + dashboard.getName());
                } else {
                    Window.setTitle(dashboard.getName());
                }
            }
        });

        Window.addWindowClosingHandler(event -> {
            if (dashboardPresenter.isDirty()) {
                String name = "";
                if (dashboard != null) {
                    name = "'" + dashboard.getName() + "'";
                }

                event.setMessage("Dashboard " + name + " has unsaved changes. Are you sure you want to close it?");
            }
        });
    }

    private void onLoadSuccess(final Dashboard dashboard) {
        if (dashboard == null) {
            AlertEvent.fireError(this, "No dashboard uuid has been specified", null);

        } else {
            this.dashboard = dashboard;

            setInSlot(CONTENT, dashboardPresenter);
            RevealRootContentEvent.fire(DashboardAppPresenter.this, DashboardAppPresenter.this);
            RootPanel.get("logo").setVisible(false);

            dashboardPresenter.setParams(params);
            dashboardPresenter.read(DocRefUtil.create(dashboard), dashboard);
            Window.setTitle(dashboard.getName());
        }
    }

    private void onLoadFailure(final Throwable throwable) {
        AlertEvent.fireError(this, throwable.getMessage(), null);
    }

    @Override
    protected void revealInParent() {
        RevealRootContentEvent.fire(this, this);
        RootPanel.get("logo").setVisible(false);
    }

    @ProxyEvent
    @Override
    public void onTaskStart(final TaskStartEvent event) {
        if (!securityContext.isLoggedIn()) {
            getView().showWorking(event.getMessage());
        }
    }

    @ProxyEvent
    @Override
    public void onTaskEnd(final TaskEndEvent event) {
        if (event.getTaskCount() == 0) {
            getView().hideWorking();
        }
    }

    @ProxyStandard
    public interface DashboardAppProxy extends Proxy<DashboardAppPresenter> {
    }

    public interface DashboardAppView extends View {
        void showWorking(final String message);

        void hideWorking();
    }
}
