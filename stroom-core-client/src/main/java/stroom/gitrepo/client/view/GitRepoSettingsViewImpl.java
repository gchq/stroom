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
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

/**
 * Backs up GitRepoSettingsViewImpl.ui.xml for the GitRepo Settings tab.
 */
public class GitRepoSettingsViewImpl
        extends ViewWithUiHandlers<GitRepoSettingsUiHandlers>
        implements GitRepoSettingsView, ReadOnlyChangeHandler {

    /** The widget that this represents */
    private final Widget widget;

    /** Whether this is readonly */
    private boolean readOnly = false;

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
    TextBox commit;

    @UiField
    Label gitRemoteCommitName;

    @UiField
    CustomCheckBox autoPush;

    @UiField
    TextArea commitMessage;

    @UiField
    Button gitRepoPush;

    @UiField
    Button gitRepoPull;

    @UiField
    Button btnCheckForUpdates;

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
    public String getCommit() {
        return commit.getText();
    }

    @Override
    public void setCommit(String commit) {
        this.commit.setText(commit);
    }

    @Override
    public void setGitRemoteCommitName(String commitName) {
        this.gitRemoteCommitName.setText(commitName);
    }

    @Override
    public Boolean isAutoPush() {
        return this.autoPush.getValue();
    }

    @Override
    public void setAutoPush(Boolean autoPush) {
        // Objects.requireNonNullElse() not defined for GWT
        if (autoPush == null) {
            this.autoPush.setValue(Boolean.FALSE);
        } else {
            this.autoPush.setValue(autoPush);
        }
    }

    @Override
    public String getCommitMessage() {
        return this.commitMessage.getText();
    }

    @Override
    public void setCommitMessage(String commitMessage) {
        this.commitMessage.setText(commitMessage);
        this.onCommitMessageValueChange(null);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        this.setState();
    }

    /**
     * Sets the enabled/disabled state of widgets.
     * Called when the state of widgets changes.
     * Also called from the Presenter onBind().
     */
    @Override
    public void setState() {

        if (this.readOnly) {
            // Everything is disabled
            url.setEnabled(false);
            username.setEnabled(false);
            password.setEnabled(false);
            branch.setEnabled(false);
            path.setEnabled(false);
            commit.setEnabled(false);
            autoPush.setEnabled(false);
            commitMessage.setEnabled(false);
            gitRepoPush.setEnabled(false);
            gitRepoPull.setEnabled(false);

        } else {
            // Not readonly so enable most stuff
            url.setEnabled(true);
            username.setEnabled(true);
            password.setEnabled(true);
            branch.setEnabled(true);
            path.setEnabled(true);
            commit.setEnabled(true);
            gitRepoPull.setEnabled(true);

            // Commit hash => Git cannot accept pushes
            if (commit.getText().isEmpty()) {
                // Can push manually or automatically
                autoPush.setEnabled(true);
                commitMessage.setEnabled(true);

                if (commitMessage.getText().isEmpty()) {
                    // Cannot push until message isn't empty
                    gitRepoPush.setEnabled(false);

                } else {
                    // Can push as we have a commit message
                    gitRepoPush.setEnabled(true);
                }

            } else {
                // Commit hash is specified so cannot push manually or automatically
                commitMessage.setEnabled(false);
                autoPush.setEnabled(false);
                gitRepoPush.setEnabled(false);
            }
        }
    }

    /**
     * Sets the Dirty flag if any of the UI widget's content changes.
     * @param e Event from the UI widget. Ignored. Can be null.
     */
    @SuppressWarnings("unused")
    @UiHandler({"url", "username", "password", "branch", "path", "commit"})
    public void onWidgetValueChange(@SuppressWarnings("unused") final ValueChangeEvent<String> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
        this.setState();
    }

    /**
     * Sets the dirty flag when the autoPush checkbox is changed.
     * @param event Event from the UI widget. Ignored. Can be null.
     */
    @SuppressWarnings("unused")
    @UiHandler({"autoPush"})
    public void onAutoPushClick(@SuppressWarnings("unused") final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
        this.setState();
    }

    /**
     * Enables/disables the Push button depending on whether there is anything
     * in the Commit Message text box.
     * @param e Event. Ignored. Can be null.
     */
    @UiHandler({"commitMessage"})
    public void onCommitMessageValueChange(@SuppressWarnings("unused") final ValueChangeEvent<String> e) {
        this.setState();
    }

    /**
     * Handles 'Push to Git' button clicks.
     * Passes the button to display the wait icon.
     * @param event The button push event.
     */
    @SuppressWarnings("unused")
    @UiHandler("gitRepoPush")
    public void onGitRepoPushClick(@SuppressWarnings("unused") final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onGitRepoPush(gitRepoPush);
        }
    }

    /**
     * Handles 'Pull from Git' button clicks.
     * Passes the button to display the wait icon.
     * @param event The button push event. Ignored. Can be null.
     */
    @SuppressWarnings("unused")
    @UiHandler("gitRepoPull")
    public void onGitRepoPullClick(@SuppressWarnings("unused") final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onGitRepoPull(gitRepoPull);
        }
    }

    /**
     * Handles 'Check for updates' button clicks.
     * @param event The button push event. Ignored. Can be null.
     */
    @SuppressWarnings("unused")
    @UiHandler("btnCheckForUpdates")
    public void onBtnCheckForUpdatesClick(@SuppressWarnings("unused") final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onCheckForUpdates(btnCheckForUpdates);
        }
    }

    public interface Binder extends UiBinder<Widget, GitRepoSettingsViewImpl> {

    }
}
