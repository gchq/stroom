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

package stroom.security.identity.client.view;

import stroom.security.identity.client.presenter.EmailResetPasswordPresenter.EmailResetPasswordView;
import stroom.security.identity.client.presenter.EmailValidator;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.popup.client.view.HideRequest;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class EmailResetPasswordViewImpl
        extends ViewWithUiHandlers<HideRequestUiHandlers>
        implements EmailResetPasswordView {

    private final Widget widget;
    @UiField
    TextBox email;
    @UiField
    Label emailFeedback;

    @Inject
    public EmailResetPasswordViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        email.getElement().setAttribute("placeholder", "Enter Your Email Address");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        email.setFocus(true);
    }

    @Override
    public String getEmail() {
        return email.getValue();
    }

    @Override
    public boolean validate() {
        boolean valid = true;

        email.removeStyleName("invalid");
        if (email.getValue().length() == 0) {
            emailFeedback.setText("Email is required");
            email.addStyleName("invalid");
            valid = false;
        } else if (!EmailValidator.validate(email.getValue())) {
            emailFeedback.setText("Invalid email address");
            email.addStyleName("invalid");
            valid = false;
        } else {
            emailFeedback.setText("");
        }

        return valid;
    }

    @UiHandler("email")
    public void onEmailKeyDown(final KeyDownEvent e) {
        onKeyDown(e);
    }

    private void onKeyDown(final KeyDownEvent e) {
        if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            getUiHandlers().hideRequest(new HideRequest(DialogAction.OK, () -> {
            }));
        } else if (e.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
            getUiHandlers().hideRequest(new HideRequest(DialogAction.CANCEL, () -> {
            }));
        }
    }

    public interface Binder extends UiBinder<Widget, EmailResetPasswordViewImpl> {

    }
}
