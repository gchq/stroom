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
import stroom.alert.client.event.AlertEvent;
import stroom.core.client.UrlParameters;
import stroom.core.client.presenter.CorePresenter;
import stroom.dashboard.client.main.DashboardMainPresenter.DashboardMainProxy;
import stroom.dashboard.client.main.DashboardMainPresenter.DashboardMainView;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.entity.shared.DocumentServiceReadAction;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.api.event.CurrentUserChangedEvent;
import stroom.security.client.api.event.CurrentUserChangedEvent.CurrentUserChangedHandler;

import javax.inject.Inject;

public class DashboardMainPresenter
        extends MyPresenter<DashboardMainView, DashboardMainProxy>
        implements CurrentUserChangedHandler {
    @ContentSlot
    public static final GwtEvent.Type<RevealContentHandler<?>> CONTENT = new GwtEvent.Type<>();

    private final DashboardPresenter dashboardPresenter;
    private final ClientDispatchAsync dispatcher;

    private String type;
    private String uuid;

    @Inject
    public DashboardMainPresenter(final EventBus eventBus,
                                  final DashboardMainView view,
                                  final DashboardMainProxy proxy,
                                  final ClientSecurityContext securityContext,
                                  final ClientDispatchAsync dispatcher,
                                  final DashboardPresenter dashboardPresenter,
                                  final UrlParameters urlParameters) {
        super(eventBus, view, proxy);
        this.dashboardPresenter = dashboardPresenter;
        this.dispatcher = dispatcher;

        type = urlParameters.getType();
        uuid = urlParameters.getUuid();
        final String title = urlParameters.getTitle();
        final String params = urlParameters.getParams();
        final boolean embedded = urlParameters.isEmbedded();

        dashboardPresenter.setCustomTitle(title);
        dashboardPresenter.setParams(params);
        dashboardPresenter.setEmbedded(embedded);

        if (title != null && title.trim().length() > 0) {
            Window.setTitle(title);
        }

        dashboardPresenter.addDirtyHandler(event -> Window.setTitle(dashboardPresenter.getLabel()));
        Window.addWindowClosingHandler(event -> {
            if (dashboardPresenter.isDirty()) {
                event.setMessage("Dashboard '" + dashboardPresenter.getTitle() + "' has unsaved changes. Are you sure you want to close it?");
            }
        });
    }

    @ProxyEvent
    @Override
    public void onCurrentUserChanged(final CurrentUserChangedEvent event) {
        if (type == null || uuid == null) {
            AlertEvent.fireError(this, "No dashboard uuid has been specified", null);

        } else {
            final DocRef docRef = new DocRef(type, uuid);
            dispatcher.exec(new DocumentServiceReadAction<DashboardDoc>(docRef))
                    .onSuccess(this::onLoadSuccess)
                    .onFailure(this::onLoadFailure);
        }
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

    @ProxyStandard
    public interface DashboardMainProxy extends Proxy<DashboardMainPresenter> {
    }

    public interface DashboardMainView extends View {
    }
}
