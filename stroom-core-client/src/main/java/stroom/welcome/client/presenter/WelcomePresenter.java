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

package stroom.welcome.client.presenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.config.global.shared.SessionInfoResource;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgIcon;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.SessionInfo;

public class WelcomePresenter extends ContentTabPresenter<WelcomePresenter.WelcomeView> {
    private static final SessionInfoResource SESSION_INFO_RESOURCE = GWT.create(SessionInfoResource.class);

    public static final String WELCOME = "Welcome";

    @Inject
    public WelcomePresenter(final EventBus eventBus,
                            final WelcomeView view,
                            final RestFactory restFactory,
                            final UiConfigCache uiConfigCache) {
        super(eventBus, view);

        final Rest<SessionInfo> rest = restFactory.create();
        rest
                .onSuccess(sessionInfo -> {
                    final BuildInfo buildInfo = sessionInfo.getBuildInfo();
                    view.getBuildVersion().setText("Build Version: " + buildInfo.getBuildVersion());
                    view.getBuildDate().setText("Build Date: " + buildInfo.getBuildDate());
                    view.getUpDate().setText("Up Date: " + buildInfo.getUpDate());
                    view.getNodeName().setText("Node Name: " + sessionInfo.getNodeName());
                    view.getUserName().setText("User Name: " + sessionInfo.getUserName());
                })
                .onFailure(caught -> AlertEvent.fireError(WelcomePresenter.this, caught.getMessage(), null))
                .call(SESSION_INFO_RESOURCE)
                .get();

        uiConfigCache.get()
                .onSuccess(result -> view.setHTML(result.getWelcomeHtml()))
                .onFailure(caught -> AlertEvent.fireError(WelcomePresenter.this, caught.getMessage(), null));
    }

    @Override
    public Icon getIcon() {
        return new SvgIcon("images/oo.svg", 18, 18);
    }

    @Override
    public String getLabel() {
        return WELCOME;
    }

    public interface WelcomeView extends View {
        void setHTML(String html);

        HasText getBuildVersion();

        HasText getBuildDate();

        HasText getUpDate();

        HasText getNodeName();

        HasText getUserName();

        HasText getRoleName();
    }
}
