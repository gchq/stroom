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

package stroom.security.client;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.dispatch.client.RestFactory;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.shared.SessionResource;
import stroom.svg.shared.SvgImage;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.widget.menu.client.presenter.IconMenuItem;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

/**
 * Adds a "Sign Out Other Sessions" action to the user menu. This lets a user end all of their
 * own sessions across the cluster except the one they are using, e.g. after signing in on a
 * shared or lost device. It is deliberately a self-service, opt-in action.
 */
@Singleton
public class SignOutOtherSessionsPlugin extends Plugin {

    private static final SessionResource SESSION_RESOURCE = GWT.create(SessionResource.class);

    private final RestFactory restFactory;

    @Inject
    public SignOutOtherSessionsPlugin(final EventBus eventBus,
                                      final RestFactory restFactory) {
        super(eventBus);
        this.restFactory = restFactory;
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        super.onReveal(event);

        MenuKeys.addUserMenu(event.getMenuItems());
        event.getMenuItems().addMenuItem(MenuKeys.USER_MENU,
                new IconMenuItem.Builder()
                        .priority(45)
                        .icon(SvgImage.LOGOUT)
                        .text("Sign Out Other Sessions")
                        .command(() ->
                                ConfirmEvent.fire(SignOutOtherSessionsPlugin.this,
                                        "Sign out of all your other sessions "
                                                + "(on other browsers and devices)?",
                                        result -> {
                                            if (result) {
                                                terminateOtherSessions();
                                            }
                                        }))
                        .build());
    }

    private void terminateOtherSessions() {
        restFactory
                .create(SESSION_RESOURCE)
                .method(SessionResource::terminateOtherSessions)
                .onSuccess(ok -> AlertEvent.fireInfo(SignOutOtherSessionsPlugin.this,
                        "Signed out of your other sessions.", null))
                .onFailure(restError -> AlertEvent.fireErrorFromException(
                        SignOutOtherSessionsPlugin.this, restError.getException(), null))
                .taskMonitorFactory(new DefaultTaskMonitorFactory(this))
                .exec();
    }
}
