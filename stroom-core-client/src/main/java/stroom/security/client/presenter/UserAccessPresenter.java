/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.security.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.event.OpenUserEvent;
import stroom.security.client.presenter.UserAccessPresenter.UserAccessView;
import stroom.security.shared.UserAccessResource;
import stroom.security.shared.UserAccessRow;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

/**
 * Who currently holds access, and how to take it away.
 * <p>
 * Deliberately one screen covering sessions and tokens together. They stop different things - a bearer token
 * authenticates on its signature alone and ignores sessions entirely, while a session-authenticated request never
 * presents a token to check - so a screen offering only one would let an administrator believe they had cut
 * someone off when they had not.
 * </p>
 */
public class UserAccessPresenter
        extends ContentTabPresenter<UserAccessView>
        implements Refreshable {

    private static final UserAccessResource USER_ACCESS_RESOURCE = GWT.create(UserAccessResource.class);

    public static final String TAB_TYPE = "UserAccess";

    private final UserAccessListPresenter listPresenter;
    private final UserSessionsListPresenter sessionsListPresenter;
    private final RestFactory restFactory;
    private final ButtonView revokeButton;
    private final ButtonView openUserButton;

    private boolean isExternalIdp = false;

    @Inject
    public UserAccessPresenter(final EventBus eventBus,
                               final UserAccessView view,
                               final UserAccessListPresenter listPresenter,
                               final UserSessionsListPresenter sessionsListPresenter,
                               final RestFactory restFactory,
                               final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.sessionsListPresenter = sessionsListPresenter;
        this.restFactory = restFactory;

        view.setList(listPresenter.getView());
        view.setSessionList(sessionsListPresenter.getView());

        // Named for exactly what it does. "Revoke this user's access" was the earlier wording and claimed
        // more than the action delivers: nothing about the user's access is withdrawn, and they can sign
        // straight back in. The confirmation said so, but only once it had been clicked, which is too late
        // for an administrator scanning the toolbar for the containment action. "Revoke" is kept for the
        // tokens, where it is literally true - those never work again.
        revokeButton = listPresenter.addButton(
                SvgPresets.DELETE.title("End this user's sessions and revoke their tokens"));
        // Revoking does not stop someone coming back, and under an external identity provider it barely
        // slows them down. Disabling is what does, so it belongs next to it rather than on a screen an
        // administrator has to know to go and find.
        openUserButton = listPresenter.addButton(
                SvgPresets.EDIT.title("Open this user, where they can be disabled"));

        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                isExternalIdp = extendedUiConfig.isExternalIdentityProvider();
            }
        }, this);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(revokeButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                revokeSelected();
            }
        }));
        registerHandler(openUserButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                openSelectedUser();
            }
        }));
        registerHandler(getSelectionModel().addSelectionHandler(e -> {
            setButtonStates();
            showSessionsForSelectedUser();
        }));
        setButtonStates();
    }

    private void showSessionsForSelectedUser() {
        final UserAccessRow row = getSelectionModel().getSelected();
        if (row == null) {
            sessionsListPresenter.setUser(null, null);
        } else {
            sessionsListPresenter.setUser(row.getSubjectId(), row.getDisplayName());
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
        return listPresenter.getSelectionModel();
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
                            sessionsListPresenter.setUser(null, null);
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

    /**
     * Spell out what revoking will and will not achieve.
     * <p>
     * The wording differs by identity provider because the guarantee genuinely differs. Against an external IdP
     * only the sessions can be ended - the external provider will happily issue fresh tokens on the next request
     * - and an administrator who is not told that would reasonably believe the user had been locked out.
     * </p>
     */
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

    public void focus() {
        getView().focus();
    }

    public void showUser(final String displayName) {
        if (!NullSafe.isBlankString(displayName)) {
            listPresenter.setQuickFilter(displayName);
        }
    }

    @Override
    public void refresh() {
        listPresenter.refresh();
        sessionsListPresenter.refresh();
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


    // --------------------------------------------------------------------------------


    public interface UserAccessView extends View {

        void focus();

        void setList(View view);

        /**
         * The detail pane: the selected user's individual sessions.
         */
        void setSessionList(View view);
    }
}
