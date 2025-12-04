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
import stroom.core.client.ContentManager;
import stroom.data.client.AbstractTabPresenterPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.event.OpenUserEvent;
import stroom.security.client.presenter.UserTabPresenter;
import stroom.security.shared.AppPermission;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.UserRef;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class UserTabPlugin extends AbstractTabPresenterPlugin<UserRef, UserTabPresenter> {

    public static final Preset USER_ICON = SvgPresets.USER;
    public static final Preset GROUP_ICON = SvgPresets.USER_GROUP;

    private final ClientSecurityContext securityContext;

    @Inject
    public UserTabPlugin(final EventBus eventBus,
                         final ContentManager contentManager,
                         final Provider<UserTabPresenter> userTabPresenterProvider,
                         final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, userTabPresenterProvider);
        this.securityContext = securityContext;

        registerHandler(getEventBus().addHandler(OpenUserEvent.getType(), event -> {
            GWT.log("handling event");
            open(event.getUserRef(), true);
        }));


    }

    public void open(final UserRef userRef, final boolean forceOpen) {
        if (userRef != null) {
            if (hasPermissionToOpenUser(userRef)) {

                super.openTabPresenter(
                        forceOpen,
                        userRef,
                        userTabPresenter ->
                                userTabPresenter.setUserRef(userRef));
            } else {
                AlertEvent.fireError(this, "You do not have permission to open user '"
                                           + userRef.toDisplayString() + "'.", null);
            }
        }
    }

    private boolean hasPermissionToOpenUser(final UserRef userRef) {
        return securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)
               || securityContext.isCurrentUser(userRef);
    }

    @Override
    protected String getName() {
        return "User";
    }
}
