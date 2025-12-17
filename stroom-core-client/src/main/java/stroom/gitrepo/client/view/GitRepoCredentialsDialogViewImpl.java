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

package stroom.gitrepo.client.view;

import stroom.gitrepo.client.presenter.GitRepoCredentialsDialogPresenter;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import javax.inject.Inject;

/**
 * Provides backing for CredentialsDetailsDialogView.ui.xml,
 * to provide the UI for the Dialog that allows users to enter authentication
 * information to obtain a Content Pack.
 */
public class GitRepoCredentialsDialogViewImpl
        extends ViewWithUiHandlers<GitRepoCredentialsDialogPresenter.GitRepoCredentialsDialogUiHandlers>
        implements GitRepoCredentialsDialogPresenter.GitRepoCredentialsDialogView {

    /** Underlying Widget created by UiBinder */
    private final Widget widget;

    /** Accepts username for the Content Pack auth */
    @UiField
    TextBox txtUsername;

    /** Accepts password for the Content Pack auth */
    @UiField
    PasswordTextBox pwdPassword;

    /**
     * Injected constructor.
     */
    @Inject
    @SuppressWarnings("unused")
    public GitRepoCredentialsDialogViewImpl(final GitRepoCredentialsDialogViewImpl.Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    /**
     * @return The underlying widget.
     */
    @Override
    public Widget asWidget() {
        return widget;
    }

    /**
     * Sets the username in the UI.
     */
    @Override
    public void setUsername(final String username) {
        txtUsername.setText(username);
    }

    /**
     * @return The username as entered into the Text Box.
     */
    @Override
    public String getUsername() {
        return txtUsername.getText();
    }

    /**
     * Sets the password in the UI.
     */
    @Override
    public void setPassword(final String password) {
        pwdPassword.setText(password);
    }

    /**
     * @return The password as entered into the Password Box.
     */
    @Override
    public String getPassword() {
        return pwdPassword.getText();
    }

    /**
     * Resets all dialog information for the next use.
     */
    @Override
    public void resetData() {
        txtUsername.setText("");
        pwdPassword.setText("");
    }

    /**
     * Interface to keep GWT UiBinder happy.
     */
    public interface Binder extends
            UiBinder<Widget, GitRepoCredentialsDialogViewImpl> {
        // No code
    }

}

