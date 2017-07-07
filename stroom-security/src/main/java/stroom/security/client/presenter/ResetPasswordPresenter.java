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
import stroom.security.client.event.ResetPasswordEvent;
import stroom.security.client.event.ResetPasswordEvent.ResetPasswordHandler;
import stroom.security.shared.ResetPasswordAction;
import stroom.security.shared.UserRef;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class ResetPasswordPresenter
        extends MyPresenter<ResetPasswordPresenter.ResetPasswordView, ResetPasswordPresenter.ResetPasswordProxy>
        implements ResetPasswordHandler, PopupUiHandlers {
    public interface ResetPasswordView extends View, HasUiHandlers<PopupUiHandlers> {
        String getUserName();

        void setUserName(String userName);

        String getPassword();

        void setPassword(String password);

        String getConfirmPassword();

        void setConfirmPassword(String confirmPassword);
    }

    @ProxyCodeSplit
    public interface ResetPasswordProxy extends Proxy<ResetPasswordPresenter> {
    }

    private final ClientDispatchAsync dispatcher;

    private UserRef userRef;

    @Inject
    public ResetPasswordPresenter(final EventBus eventBus, final ResetPasswordView view, final ResetPasswordProxy proxy,
                                  final ClientDispatchAsync clientDispatchAsync) {
        super(eventBus, view, proxy);
        this.dispatcher = clientDispatchAsync;
        view.setUiHandlers(this);
    }

    @ProxyEvent
    @Override
    public void onResetPassword(final ResetPasswordEvent event) {
        userRef = event.getUserRef();
        read();
        forceReveal();
    }

    @Override
    protected void revealInParent() {
        final PopupSize popupSize = new PopupSize(400, 126, 400, 126, 600, 126, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Reset Password", this);
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            save();
        } else {
            HidePopupEvent.fire(ResetPasswordPresenter.this, ResetPasswordPresenter.this);
        }
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
        // Do nothing.
    }

    private void read() {
        getView().setUserName(userRef.getName());
        getView().setPassword("");
        getView().setConfirmPassword("");
    }

    private void save() {
        final String password = getView().getPassword();
        final String confirmPassword = getView().getConfirmPassword();

        if (!password.equals(confirmPassword)) {
            AlertEvent.fireWarn(this, "New password and confirmation password do not match!", null);
        } else {
            if (!PasswordUtil.isOkPassword(password)) {
                AlertEvent.fireWarn(this,
                        "New password is not strong enough.\nIt must be mixed case, contain at least 1 non letter, and be at least 8 characters long",
                        null);

            } else {
                dispatcher.exec(new ResetPasswordAction(userRef, password)).onSuccess(result -> {
                    AlertEvent.fireInfo(ResetPasswordPresenter.this, "The password has been changed.",
                            () -> HidePopupEvent.fire(ResetPasswordPresenter.this, ResetPasswordPresenter.this));
                });
            }
        }
    }
}
