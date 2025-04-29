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

package stroom.gitrepo.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.gitrepo.client.presenter.GitRepoSettingsPresenter.GitRepoSettingsView;
import stroom.gitrepo.client.presenter.GitRepoSettingsUiHandlers;
import stroom.widget.button.client.Button;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class GitRepoSettingsViewImpl
        extends ViewWithUiHandlers<GitRepoSettingsUiHandlers>
        implements GitRepoSettingsView, ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    TextBox url;

    @UiField
    TextBox username;

    @UiField
    PasswordTextBox password;

    @UiField
    TextBox branch;

    @UiField
    TextBox path;

    @UiField
    Button gitRepoPush;

    @Inject
    public GitRepoSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        // TODO Add validation for the TextBoxes
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getUrl() {
        return url.getText();
    }
    @Override
    public void setUrl(final String url) {
        this.url.setText(url);
    }
    @Override
    public String getUsername() {
        return username.getText();
    }
    @Override
    public void setUsername(final String username) {
        this.username.setText(username);
    }
    @Override
    public String getPassword() {
        return password.getText();
    }
    @Override
    public void setPassword(final String password) {
        this.password.setText(password);
    }
    @Override
    public String getBranch() {
        return branch.getText();
    }
    @Override
    public void setBranch(final String branch) {
        this.branch.setText(branch);
    }
    @Override
    public String getPath() {
        return path.getText();
    }
    @Override
    public void setPath(String path) {
        this.path.setText(path);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        url.setEnabled(!readOnly);
        username.setEnabled(!readOnly);
        password.setEnabled(!readOnly);
        branch.setEnabled(!readOnly);
        path.setEnabled(!readOnly);
    }

    /**
     * Sets the Dirty flag if any of the UI widget's content changes.
     * @param e Event from the UI widget
     */
    @SuppressWarnings("unused")
    @UiHandler({"url", "username", "password", "branch", "path"})
    public void onWidgetValueChange(@SuppressWarnings("unused") final KeyDownEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
    }

    /**
     * Handles 'Push to Git' button clicks.
     * Passes the button to display the wait timer.
     * @param event The button push event.
     */
    @SuppressWarnings("unused")
    @UiHandler("gitRepoPush")
    public void onGitRepoPushClick(@SuppressWarnings("unused") final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onGitRepoPush(gitRepoPush);
        }
    }

    public interface Binder extends UiBinder<Widget, GitRepoSettingsViewImpl> {

    }
}
