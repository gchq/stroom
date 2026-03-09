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

import stroom.security.identity.client.presenter.CurrentPasswordPresenter.CurrentPasswordView;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.popup.client.view.HideRequest;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class CurrentPasswordViewImpl extends ViewWithUiHandlers<HideRequestUiHandlers> implements CurrentPasswordView {

    private final Widget widget;
    @UiField
    PasswordTextBox password;
    @UiField
    InlineSvgButton showPassword;
    @UiField
    Label passwordFeedback;

    @Inject
    public CurrentPasswordViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        showPassword.setSvg(SvgImage.EYE);
        showPassword.setTitle("Show Password");
        showPassword.setEnabled(true);

        password.getElement().setAttribute("placeholder", "Enter Your Current Password");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        password.setFocus(true);
    }

    @Override
    public String getPassword() {
        return password.getValue();
    }

    @Override
    public boolean validate() {
        boolean valid = true;

        password.removeStyleName("invalid");
        if (password.getValue().length() == 0) {
            passwordFeedback.setText("Password is required");
            password.addStyleName("invalid");
            valid = false;
        } else {
            passwordFeedback.setText("");
        }

        return valid;
    }

    @UiHandler("password")
    public void onPasswordKeyDown(final KeyDownEvent e) {
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

    @UiHandler("showPassword")
    public void onShowPassword(final ClickEvent e) {
        final String type = password.getElement().getAttribute("type");
        if (type == null || "password".equals(type)) {
            password.getElement().setAttribute("type", "text");
            showPassword.setSvg(SvgImage.EYE_OFF);
            showPassword.setTitle("Hide Password");
        } else {
            password.getElement().setAttribute("type", "password");
            showPassword.setSvg(SvgImage.EYE);
            showPassword.setTitle("Show Password");
        }
    }

    public interface Binder extends UiBinder<Widget, CurrentPasswordViewImpl> {

    }
}
