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

package stroom.gitrepo.client.component;

import stroom.credentials.shared.Credential;
import stroom.item.client.SelectionBox;
import stroom.item.client.SelectionListModel;
import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.view.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// A Git repository's Settings tab: its URL, branch, path, commit to pull, whether changes are
/// pushed automatically and its credential, with buttons to set the HTTP client configuration, push,
/// check for updates and pull. A content pack's repository shows its store and pack, and only its
/// credential can be changed.
public class GitRepoSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final String AUTO_PUSH_TITLE = "Automatically push changes to the Git repository";
    private static final String PUSH_TITLE = "Push any changes into the Git repository";

    @UiField
    FormGroup fgContentStore;
    @UiField
    Label lblContentStore;
    @UiField
    FormGroup fgContentPack;
    @UiField
    Label lblContentPack;
    @UiField
    TextBox txtGitUrl;
    @UiField
    TextBox txtGitBranch;
    @UiField
    TextBox txtGitPath;
    @UiField
    SelectionBox<Credential> credentialSelectionBox;
    @UiField
    TextBox txtGitCommitToPull;
    @UiField
    FormGroup fgGitAutoPush;
    @UiField
    TickBox chkGitAutoPush;
    @UiField
    Button btnGitRepoPush;
    @UiField
    Button btnGitRepoPull;
    @UiField
    Button btnCheckForUpdates;
    @UiField
    Button setHttpClientConfig;

    private Runnable onChange = () -> {
    };

    /// Makes the tab, for a repository that isn't a content pack's.
    public GitRepoSettings() {
        initWidget(BINDER.createAndBindUi(this));
        // Any change makes the document dirty, and may change what can be pushed or pulled
        txtGitUrl.addValueChangeHandler(e -> onFieldChange());
        txtGitBranch.addValueChangeHandler(e -> onFieldChange());
        txtGitPath.addValueChangeHandler(e -> onFieldChange());
        txtGitCommitToPull.addValueChangeHandler(e -> onFieldChange());
        chkGitAutoPush.addValueChangeHandler(e -> onFieldChange());
        credentialSelectionBox.addValueChangeHandler(e -> onChange.run());
        updateState();
    }

    private void onFieldChange() {
        onChange.run();
        updateState();
    }

    /// Shows a content pack's store and pack, making the repository's settings (except its
    /// credential) read only, or for a repository that isn't a content pack's, hides them.
    ///
    /// @param contentStoreName The name of the content store, or empty if it isn't a content pack.
    /// @param contentPackName  The name of the content pack, or empty if it isn't one.
    public void setContentPack(final String contentStoreName, final String contentPackName) {
        lblContentStore.setText(contentStoreName);
        lblContentPack.setText(contentPackName);
        updateState();
    }

    /// @return The repository's URL.
    public String getUrl() {
        return txtGitUrl.getText();
    }

    /// @param url The repository's URL.
    public void setUrl(final String url) {
        txtGitUrl.setText(url);
        updateState();
    }

    /// @return The branch.
    public String getBranch() {
        return txtGitBranch.getText();
    }

    /// @param branch The branch.
    public void setBranch(final String branch) {
        txtGitBranch.setText(branch);
    }

    /// @return The path in the repository.
    public String getPath() {
        return txtGitPath.getText();
    }

    /// @param path The path in the repository.
    public void setPath(final String path) {
        txtGitPath.setText(path);
    }

    /// @return The commit to pull, empty for the latest.
    public String getCommitToPull() {
        return txtGitCommitToPull.getText();
    }

    /// @param commit The commit to pull, or empty for the latest.
    public void setCommitToPull(final String commit) {
        txtGitCommitToPull.setText(commit);
        updateState();
    }

    /// @return Whether changes are pushed automatically.
    public boolean isAutoPush() {
        return chkGitAutoPush.getValue();
    }

    /// @param autoPush Whether changes are pushed automatically; `null` means not.
    public void setAutoPush(final Boolean autoPush) {
        // Objects.requireNonNullElse() not defined for GWT
        chkGitAutoPush.setValue(autoPush != null && autoPush);
    }

    /// @param model The credentials the repository's can be chosen from.
    public void setCredentialModel(
            final SelectionListModel<Credential, SimpleSelectionItemWrapper<Credential>> model) {
        credentialSelectionBox.setModel(model);
    }

    /// @param credential The repository's credential, or `null` for none.
    public void setCredential(final Credential credential) {
        credentialSelectionBox.setValue(credential);
    }

    /// @return The credential chosen, or `null` for none.
    public Credential getCredential() {
        return credentialSelectionBox.getValue();
    }

    /// @return The Push to Git button, which spins while it pushes.
    public TaskMonitorFactory getPushTaskMonitor() {
        return btnGitRepoPush;
    }

    /// @return The Pull from Git button, which spins while it pulls.
    public TaskMonitorFactory getPullTaskMonitor() {
        return btnGitRepoPull;
    }

    /// @return The Check for updates button, which spins while it checks.
    public TaskMonitorFactory getCheckForUpdatesTaskMonitor() {
        return btnCheckForUpdates;
    }

    /// @param onChange Run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param onSetHttpClientConfig Run when Set HTTP Client Config is pressed.
    public void setOnSetHttpClientConfig(final Runnable onSetHttpClientConfig) {
        setHttpClientConfig.setOnClick(onSetHttpClientConfig);
    }

    /// @param onPush Run when Push to Git is pressed.
    public void setOnPush(final Runnable onPush) {
        btnGitRepoPush.setOnClick(onPush);
    }

    /// @param onPull Run when Pull from Git is pressed.
    public void setOnPull(final Runnable onPull) {
        btnGitRepoPull.setOnClick(onPull);
    }

    /// @param onCheckForUpdates Run when Check for updates is pressed.
    public void setOnCheckForUpdates(final Runnable onCheckForUpdates) {
        btnCheckForUpdates.setOnClick(onCheckForUpdates);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        // Read only is independent of the enabled state that updateState() manages, so a later
        // updateState() can't make these fields editable
        txtGitUrl.setReadOnly(readOnly);
        txtGitBranch.setReadOnly(readOnly);
        txtGitPath.setReadOnly(readOnly);
        credentialSelectionBox.setReadOnly(readOnly);
        txtGitCommitToPull.setReadOnly(readOnly);
        chkGitAutoPush.setReadOnly(readOnly);
        updateState();
    }

    // Sets which fields are shown and enabled, from the values shown and whether it is read only
    private void updateState() {
        if (!lblContentStore.getText().isEmpty()) {
            // We've got a Content Pack so everything (except credentials and buttons) is readonly
            fgContentStore.setVisible(true);
            fgContentPack.setVisible(true);
            txtGitUrl.setVisible(true);
            txtGitUrl.setEnabled(false);
            txtGitBranch.setVisible(true);
            txtGitBranch.setEnabled(false);
            txtGitPath.setVisible(true);
            txtGitPath.setEnabled(false);
            credentialSelectionBox.setVisible(true);
            txtGitCommitToPull.setVisible(true);
            txtGitCommitToPull.setEnabled(false);
            fgGitAutoPush.setVisible(false);
            btnGitRepoPush.setVisible(false);

        } else {
            // Not a Content Pack so allow editing
            fgContentStore.setVisible(false);
            fgContentPack.setVisible(false);
            txtGitUrl.setVisible(true);
            txtGitUrl.setEnabled(true);
            txtGitBranch.setVisible(true);
            txtGitBranch.setEnabled(true);
            txtGitPath.setVisible(true);
            txtGitPath.setEnabled(true);
            credentialSelectionBox.setVisible(true);
            txtGitCommitToPull.setVisible(true);
            txtGitCommitToPull.setEnabled(true);

            // Pushing needs a URL, and isn't possible while pinned to a commit. The controls stay
            // shown, disabled with the reason, so it is clear that pushing exists
            final String pushUnavailableReason;
            if (!txtGitCommitToPull.getText().isEmpty()) {
                pushUnavailableReason = "Not available while a commit hash is set";
            } else if (txtGitUrl.getText().isEmpty()) {
                pushUnavailableReason = "Set the Git URL first";
            } else {
                pushUnavailableReason = null;
            }
            fgGitAutoPush.setVisible(true);
            btnGitRepoPush.setVisible(true);
            final boolean canPush = pushUnavailableReason == null;
            chkGitAutoPush.setEnabled(canPush);
            // Pushing changes the repository, so not allowed for a read-only document
            btnGitRepoPush.setEnabled(canPush && !isReadOnly());
            chkGitAutoPush.setTitle(canPush
                    ? AUTO_PUSH_TITLE
                    : pushUnavailableReason);
            btnGitRepoPush.setTitle(canPush
                    ? PUSH_TITLE
                    : pushUnavailableReason);
        }

        // Can pull and check for updates if URL is set
        if (!txtGitUrl.getText().isEmpty()) {
            // Pulling changes the document's content, so not allowed for a read-only document
            btnGitRepoPull.setEnabled(!isReadOnly());
            btnCheckForUpdates.setEnabled(true);
        } else {
            btnGitRepoPull.setEnabled(false);
            btnCheckForUpdates.setEnabled(false);
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, GitRepoSettings> {

    }
}
