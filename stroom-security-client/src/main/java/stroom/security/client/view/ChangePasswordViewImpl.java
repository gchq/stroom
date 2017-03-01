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

package stroom.security.client.view;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.logical.shared.AttachEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.security.client.presenter.ChangePasswordPresenter;
import stroom.widget.popup.client.presenter.PopupUiHandlers;

public class ChangePasswordViewImpl extends ViewWithUiHandlers<PopupUiHandlers>
        implements ChangePasswordPresenter.ChangePasswordView {
    private final Widget widget;
    @UiField
    Label userName;
    @UiField
    PasswordTextBox oldPassword;
    @UiField
    PasswordTextBox newPassword;
    @UiField
    PasswordTextBox confirmPassword;

    @Inject
    public ChangePasswordViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.addAttachHandler(new AttachEvent.Handler() {
            @Override
            public void onAttachOrDetach(final AttachEvent event) {
                Scheduler.get().scheduleDeferred(new ScheduledCommand() {
                    @Override
                    public void execute() {
                        oldPassword.setFocus(true);
                    }
                });
            }
        });
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getUserName() {
        return userName.getText();
    }

    @Override
    public void setUserName(final String userName) {
        this.userName.setText(userName);
    }

    @Override
    public String getNewPassword() {
        return newPassword.getText();
    }

    @Override
    public void setNewPassword(final String newPassword) {
        this.newPassword.setText(newPassword);
    }

    @Override
    public String getOldPassword() {
        return oldPassword.getText();
    }

    @Override
    public void setOldPassword(final String oldPassword) {
        this.oldPassword.setText(oldPassword);
    }

    @Override
    public String getConfirmPassword() {
        return confirmPassword.getText();
    }

    @Override
    public void setConfirmPassword(final String confirmPassword) {
        this.confirmPassword.setText(confirmPassword);
    }

    @UiHandler("newPassword")
    void onNewPasswordKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == '\r') {
            getUiHandlers().onHideRequest(false, true);
        }
    }

    @UiHandler("oldPassword")
    void onOldPasswordKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == '\r') {
            getUiHandlers().onHideRequest(false, true);
        }
    }

    @UiHandler("confirmPassword")
    void onConfirmPasswordKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == '\r') {
            getUiHandlers().onHideRequest(false, true);
        }
    }

    public interface Binder extends UiBinder<Widget, ChangePasswordViewImpl> {
    }
}
