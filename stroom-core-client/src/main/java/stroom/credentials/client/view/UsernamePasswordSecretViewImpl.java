/*
 * Copyright 2025 Crown Copyright
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

package stroom.credentials.client.view;

import stroom.credentials.client.presenter.UsernamePasswordSecretPresenter.UsernamePasswordSecretView;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.widget.form.client.PasswordBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import javax.inject.Inject;

public class UsernamePasswordSecretViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements UsernamePasswordSecretView {

    private final Widget widget;

    @UiField
    TextBox username;
    @UiField
    PasswordBox password;

    @Inject
    @SuppressWarnings("unused")
    public UsernamePasswordSecretViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        password.setPlaceholder("Enter Password");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getUsername() {
        return username.getValue();
    }

    @Override
    public void setUsername(final String username) {
        this.username.setValue(username);
    }

    @Override
    public String getPassword() {
        return password.getValue();
    }

    @Override
    public void setPassword(final String password) {
        this.password.setValue(password);
    }

    @Override
    public void focus() {
        username.setFocus(true);
    }

    public interface Binder extends UiBinder<Widget, UsernamePasswordSecretViewImpl> {

    }
}
