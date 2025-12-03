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

package stroom.welcome.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.config.global.shared.SessionInfoResource;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.UserRef;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class WelcomePresenter extends ContentTabPresenter<WelcomePresenter.WelcomeView> {

    public static final String WELCOME = "Welcome";
    public static final String TAB_TYPE = WELCOME;

    private static final SessionInfoResource SESSION_INFO_RESOURCE = GWT.create(SessionInfoResource.class);

    @Inject
    public WelcomePresenter(final EventBus eventBus,
                            final WelcomeView view,
                            final RestFactory restFactory,
                            final UiConfigCache uiConfigCache,
                            final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);

        restFactory
                .create(SESSION_INFO_RESOURCE)
                .method(SessionInfoResource::get)
                .onSuccess(sessionInfo -> {
                    final UserRef userRef = sessionInfo.getUserRef();
                    view.getUserIdentity().setText(userRef.getSubjectId());
                    view.getDisplayName().setText(userRef.getDisplayName());
                    view.getFullName().setText(userRef.getFullName());

                    final BuildInfo buildInfo = sessionInfo.getBuildInfo();
                    view.getBuildVersion().setText(buildInfo.getBuildVersion());
                    view.getBuildDate().setText(dateTimeFormatter.format(buildInfo.getBuildTime()));
                    view.getUpDate().setText(dateTimeFormatter.format(buildInfo.getUpTime()));
                    view.getNodeName().setText(sessionInfo.getNodeName());
                })
                .onFailure(caught ->
                        AlertEvent.fireError(WelcomePresenter.this, caught.getMessage(), null))
                .taskMonitorFactory(this)
                .exec();

        uiConfigCache.get(result -> {
            if (result != null) {
                view.setHTML(result.getWelcomeHtml());
            }
        }, this);
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.OO;
    }

    @Override
    public String getLabel() {
        return WELCOME;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }


    // --------------------------------------------------------------------------------


    public interface WelcomeView extends View {

        void setHTML(String html);

        HasText getBuildVersion();

        HasText getBuildDate();

        HasText getUpDate();

        HasText getNodeName();

        HasText getUserIdentity();

        HasText getDisplayName();

        HasText getFullName();
    }
}
