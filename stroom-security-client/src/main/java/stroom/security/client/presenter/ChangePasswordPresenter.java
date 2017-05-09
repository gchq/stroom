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

package stroom.security.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.security.client.CurrentUser;
import stroom.security.client.event.ChangePasswordEvent;
import stroom.security.client.event.ChangePasswordEvent.ChangePasswordHandler;
import stroom.security.shared.ChangePasswordAction;
import stroom.security.shared.User;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class ChangePasswordPresenter
        extends MyPresenter<ChangePasswordPresenter.ChangePasswordView, ChangePasswordPresenter.ChangePasswordProxy>
        implements ChangePasswordHandler, PopupUiHandlers {
    private final CurrentUser currentUser;
    private final ClientDispatchAsync dispatcher;
    private User user;
    private boolean loginOnChange;

    @Inject
    public ChangePasswordPresenter(final EventBus eventBus, final ChangePasswordView view,
                                   final ChangePasswordProxy proxy, final CurrentUser currentUser, final ClientDispatchAsync dispatcher) {
        super(eventBus, view, proxy);
        this.currentUser = currentUser;
        this.dispatcher = dispatcher;
        view.setUiHandlers(this);
    }

    @ProxyEvent
    @Override
    public void onChangePassword(final ChangePasswordEvent event) {
        user = event.getUser();
        loginOnChange = event.isLogonChange();
        read();
        forceReveal();
    }

    @Override
    protected void revealInParent() {
        final PopupSize popupSize = new PopupSize(400, 149, 400, 149, 600, 149, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Change Password", this);
    }

    private void read() {
        getView().setUserName(user.getName());
        getView().setOldPassword("");
        getView().setNewPassword("");
        getView().setConfirmPassword("");
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            save();
        } else {
            HidePopupEvent.fire(this, this);
        }
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
        // Do nothing.
    }

    private void save() {
        final String oldPassword = getView().getOldPassword();
        final String newPassword = getView().getNewPassword();
        final String confirmPassword = getView().getConfirmPassword();

        if (!newPassword.equals(confirmPassword)) {
            AlertEvent.fireWarn(this, "New password and confirmation password do not match!", null);

        } else {
            if (!PasswordUtil.isOkPassword(newPassword)) {
                AlertEvent.fireWarn(this,
                        "New password is not strong enough.\nIt must be mixed case, contain at least 1 non letter, and be at least 8 characters long",
                        null);

            } else {
                dispatcher.exec(new ChangePasswordAction(user, oldPassword, newPassword)).onSuccess(result -> {
                    AlertEvent.fireInfo(ChangePasswordPresenter.this, "The password has been changed.",
                            () -> {
                                currentUser.setUserAndPermissions(result, loginOnChange);
                                HidePopupEvent.fire(ChangePasswordPresenter.this, ChangePasswordPresenter.this);
                            });
                });
            }
        }
    }

    public interface ChangePasswordView extends View, HasUiHandlers<PopupUiHandlers> {
        String getUserName();

        void setUserName(String userName);

        String getOldPassword();

        void setOldPassword(String oldPassword);

        String getNewPassword();

        void setNewPassword(String newPassword);

        String getConfirmPassword();

        void setConfirmPassword(String confirmPassword);
    }

    @ProxyCodeSplit
    public interface ChangePasswordProxy extends Proxy<ChangePasswordPresenter> {
    }
}
