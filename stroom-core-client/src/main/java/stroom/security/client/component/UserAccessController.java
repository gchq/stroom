/*
 * Copyright 2026 Crown Copyright
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

package stroom.security.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.component.ContentTabController;
import stroom.data.table.client.HasRefresh;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.event.OpenUserEvent;
import stroom.security.shared.UserAccessResource;
import stroom.security.shared.UserAccessRow;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.IsButton;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows who currently holds access, and takes it away.
///
/// It is deliberately one screen covering sessions and tokens together. They stop different things -
/// a bearer token authenticates on its signature alone and ignores sessions entirely, while a
/// session-authenticated request never presents a token to check - so a screen offering only one
/// would let an administrator believe they had cut someone off when they had not.
public class UserAccessController extends ContentTabController
        implements HasRefresh {

    private static final UserAccessResource USER_ACCESS_RESOURCE = GWT.create(UserAccessResource.class);
    public static final String TAB_TYPE = "UserAccess";

    private final UserAccessListController userAccessList;
    private final UserSessionsListController userSessionsList;
    private final RestFactory restFactory;
    private final IsButton revokeButton;
    private final IsButton openUserButton;

    private boolean isExternalIdp = false;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus         The application's event bus.
    /// @param view             Its component.
    /// @param userAccessList   The user access list.
    /// @param userSessionsList The user sessions list.
    /// @param restFactory      Makes REST requests.
    /// @param uiConfigCache    The UI's configuration.
    @Inject
    public UserAccessController(final EventBus eventBus,
                                final UserAccess view,
                                final UserAccessListController userAccessList,
                                final UserSessionsListController userSessionsList,
                                final RestFactory restFactory,
                                final UiConfigCache uiConfigCache) {
        super(eventBus);
        initWidget(view);
        this.userAccessList = own(userAccessList).attach(view.getList());
        this.userSessionsList = own(userSessionsList).attach(view.getSessions());
        this.restFactory = restFactory;

        // Named for exactly what it does. "Revoke this user's access" was the earlier wording and claimed
        // more than the action delivers: nothing about the user's access is withdrawn, and they can sign
        // straight back in. The confirmation said so, but only once it had been clicked, which is too late
        // for an administrator scanning the toolbar for the containment action. "Revoke" is kept for the
        // tokens, where it is literally true - those never work again.
        userAccessList.getToolbar().setName("User access actions");
        revokeButton = userAccessList.getToolbar().button(
                SvgImage.DELETE,
                "End Sessions and Revoke Tokens for Selected User",
                this::revokeSelected);
        revokeButton.setEnabled(false);
        // Revoking does not stop someone coming back, and under an external identity provider it barely
        // slows them down. Disabling is what does, so it belongs next to it rather than on a screen an
        // administrator has to know to go and find.
        openUserButton = userAccessList.getToolbar().button(SvgImage.EDIT, "Open Selected User (to disable them)",
                this::openSelectedUser);
        openUserButton.setEnabled(false);

        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                isExternalIdp = extendedUiConfig.isExternalIdentityProvider();
            }
        }, this);

        own(getSelectionModel().addSelectionHandler(e -> {
            setButtonStates();
            showSessionsForSelectedUser();
        }));
        setButtonStates();
    }

    private void showSessionsForSelectedUser() {
        final UserAccessRow row = getSelectionModel().getSelected();
        if (row == null) {
            userSessionsList.setUser(null, null);
        } else {
            userSessionsList.setUser(row.getSubjectId(), row.getDisplayName());
        }
    }

    private void openSelectedUser() {
        final UserAccessRow row = getSelectionModel().getSelected();
        if (row != null && row.getUserRef() != null) {
            OpenUserEvent.fire(this, row.getUserRef());
        }
    }

    private void setButtonStates() {
        final UserAccessRow selected = getSelectionModel().getSelected();
        revokeButton.setEnabled(selected != null);
        openUserButton.setEnabled(selected != null && selected.getUserRef() != null);
    }

    private MultiSelectionModelImpl<UserAccessRow> getSelectionModel() {
        return userAccessList.getSelectionModel();
    }

    private void revokeSelected() {
        final UserAccessRow row = getSelectionModel().getSelected();
        if (row == null) {
            return;
        }
        ConfirmEvent.fire(this, buildConfirmMessage(row), ok -> {
            if (ok) {
                restFactory
                        .create(USER_ACCESS_RESOURCE)
                        .method(res -> res.revokeAccess(row.getSubjectId()))
                        .onSuccess(tokensRevoked -> {
                            getSelectionModel().clear();
                            refresh();
                            // The sessions we were showing have just been ended.
                            userSessionsList.setUser(null, null);
                        })
                        .onFailure(restError -> {
                            AlertEvent.fireError(this, restError.getMessage(), null);
                            refresh();
                        })
                        .taskMonitorFactory(this)
                        .exec();
            }
        });
    }

    // Spells out what revoking will and will not achieve. The wording differs by identity provider because
    // the guarantee genuinely differs. Against an external IdP only the sessions can be ended - the external
    // provider will happily issue fresh tokens on the next request - and an administrator who is not told
    // that would reasonably believe the user had been locked out.
    private String buildConfirmMessage(final UserAccessRow row) {
        final StringBuilder sb = new StringBuilder()
                .append("End all sessions and revoke all tokens for '")
                .append(row.getDisplayName())
                .append("'?")
                .append("\n\nThis will end all ")
                .append(row.getSessionCount())
                .append(" of their session(s) across the cluster");

        if (isExternalIdp) {
            sb.append(".")
                    .append("\n\nNote: this deployment uses an external identity provider, so their tokens ")
                    .append("cannot be revoked here. Their session at that provider is untouched, so their ")
                    .append("next request is likely to sign them straight back in automatically, without ")
                    .append("being asked for a password. Any access token they already hold also keeps ")
                    .append("working until it expires.")
                    .append("\n\nThis is therefore a forced re-authentication, not a way to lock ")
                    .append("someone out. To do that, disable the user here and disable or revoke them at ")
                    .append("the identity provider.");
        } else {
            sb.append(" and revoke the ")
                    .append(row.getTokenCount())
                    .append(" token(s) issued to them, so anything using those tokens stops working at once.")
                    .append("\n\nTheir account stays enabled and their password is unchanged - they will be ")
                    .append("able to sign in again. To prevent that, disable the user instead.");
        }
        return sb.toString();
    }

    /// Filters the list to one user.
    ///
    /// @param displayName The user's name.
    public void showUser(final String displayName) {
        if (!NullSafe.isBlankString(displayName)) {
            userAccessList.setQuickFilter(displayName);
        }
    }

    @Override
    public void refresh() {
        userAccessList.refresh();
        userSessionsList.refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.SHIELD;
    }

    @Override
    public String getLabel() {
        return "User Access";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
