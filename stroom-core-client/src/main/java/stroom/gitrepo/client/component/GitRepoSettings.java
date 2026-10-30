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
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.selection.client.SelectionListModel;
import stroom.widget.selection.client.SimpleSelectionItemWrapper;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.text.client.Label;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
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
    FormGroup contentStoreGroup;
    @UiField
    Label contentStore;
    @UiField
    FormGroup contentPackGroup;
    @UiField
    Label contentPack;
    @UiField
    TextBox urlBox;
    @UiField
    TextBox branchBox;
    @UiField
    TextBox pathBox;
    @UiField
    SelectionBox<Credential> credentialSelectionBox;
    @UiField
    TextBox commitToPullBox;
    @UiField
    FormGroup autoPushGroup;
    @UiField
    TickBox autoPushBox;
    @UiField
    Button pushButton;
    @UiField
    Button pullButton;
    @UiField
    Button checkForUpdatesButton;
    @UiField
    Button setHttpClientConfig;

    private Runnable onChange = () -> {
    };

    /// Makes the tab, for a repository that isn't a content pack's.
    public GitRepoSettings() {
        initWidget(BINDER.createAndBindUi(this));
        // Any change makes the document dirty, and may change what can be pushed or pulled
        urlBox.addValueChangeHandler(e -> onFieldChange());
        branchBox.addValueChangeHandler(e -> onFieldChange());
        pathBox.addValueChangeHandler(e -> onFieldChange());
        commitToPullBox.addValueChangeHandler(e -> onFieldChange());
        autoPushBox.addValueChangeHandler(e -> onFieldChange());
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
        contentStore.setText(contentStoreName);
        contentPack.setText(contentPackName);
        updateState();
    }

    /// @return The repository's URL.
    public String getUrl() {
        return urlBox.getText();
    }

    /// @param url The repository's URL.
    public void setUrl(final String url) {
        urlBox.setText(url);
        updateState();
    }

    /// @return The branch.
    public String getBranch() {
        return branchBox.getText();
    }

    /// @param branch The branch.
    public void setBranch(final String branch) {
        branchBox.setText(branch);
    }

    /// @return The path in the repository.
    public String getPath() {
        return pathBox.getText();
    }

    /// @param path The path in the repository.
    public void setPath(final String path) {
        pathBox.setText(path);
    }

    /// @return The commit to pull, empty for the latest.
    public String getCommitToPull() {
        return commitToPullBox.getText();
    }

    /// @param commit The commit to pull, or empty for the latest.
    public void setCommitToPull(final String commit) {
        commitToPullBox.setText(commit);
        updateState();
    }

    /// @return Whether changes are pushed automatically.
    public boolean isAutoPush() {
        return autoPushBox.getValue();
    }

    /// @param autoPush Whether changes are pushed automatically; `null` means not.
    public void setAutoPush(final Boolean autoPush) {
        // Objects.requireNonNullElse() not defined for GWT
        autoPushBox.setValue(autoPush != null && autoPush);
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
        return pushButton;
    }

    /// @return The Pull from Git button, which spins while it pulls.
    public TaskMonitorFactory getPullTaskMonitor() {
        return pullButton;
    }

    /// @return The Check for updates button, which spins while it checks.
    public TaskMonitorFactory getCheckForUpdatesTaskMonitor() {
        return checkForUpdatesButton;
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
        pushButton.setOnClick(onPush);
    }

    /// @param onPull Run when Pull from Git is pressed.
    public void setOnPull(final Runnable onPull) {
        pullButton.setOnClick(onPull);
    }

    /// @param onCheckForUpdates Run when Check for updates is pressed.
    public void setOnCheckForUpdates(final Runnable onCheckForUpdates) {
        checkForUpdatesButton.setOnClick(onCheckForUpdates);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        // Read only is independent of the enabled state that updateState() manages, so a later
        // updateState() can't make these fields editable
        urlBox.setReadOnly(readOnly);
        branchBox.setReadOnly(readOnly);
        pathBox.setReadOnly(readOnly);
        credentialSelectionBox.setReadOnly(readOnly);
        commitToPullBox.setReadOnly(readOnly);
        autoPushBox.setReadOnly(readOnly);
        updateState();
    }

    // Sets which fields are shown and enabled, from the values shown and whether it is read only
    private void updateState() {
        if (!contentStore.getText().isEmpty()) {
            // We've got a Content Pack so everything (except credentials and buttons) is readonly
            contentStoreGroup.setVisible(true);
            contentPackGroup.setVisible(true);
            urlBox.setVisible(true);
            urlBox.setEnabled(false);
            branchBox.setVisible(true);
            branchBox.setEnabled(false);
            pathBox.setVisible(true);
            pathBox.setEnabled(false);
            credentialSelectionBox.setVisible(true);
            commitToPullBox.setVisible(true);
            commitToPullBox.setEnabled(false);
            autoPushGroup.setVisible(false);
            pushButton.setVisible(false);

        } else {
            // Not a Content Pack so allow editing
            contentStoreGroup.setVisible(false);
            contentPackGroup.setVisible(false);
            urlBox.setVisible(true);
            urlBox.setEnabled(true);
            branchBox.setVisible(true);
            branchBox.setEnabled(true);
            pathBox.setVisible(true);
            pathBox.setEnabled(true);
            credentialSelectionBox.setVisible(true);
            commitToPullBox.setVisible(true);
            commitToPullBox.setEnabled(true);

            // Pushing needs a URL, and isn't possible while pinned to a commit. The controls stay
            // shown, disabled with the reason, so it is clear that pushing exists
            final String pushUnavailableReason;
            if (!commitToPullBox.getText().isEmpty()) {
                pushUnavailableReason = "Not available while a commit hash is set";
            } else if (urlBox.getText().isEmpty()) {
                pushUnavailableReason = "Set the Git URL first";
            } else {
                pushUnavailableReason = null;
            }
            autoPushGroup.setVisible(true);
            pushButton.setVisible(true);
            final boolean canPush = pushUnavailableReason == null;
            autoPushBox.setEnabled(canPush);
            // Pushing changes the repository, so not allowed for a read-only document
            pushButton.setEnabled(canPush && !isReadOnly());
            autoPushBox.setTitle(canPush
                    ? AUTO_PUSH_TITLE
                    : pushUnavailableReason);
            pushButton.setTitle(canPush
                    ? PUSH_TITLE
                    : pushUnavailableReason);
        }

        // Can pull and check for updates if URL is set
        if (!urlBox.getText().isEmpty()) {
            // Pulling changes the document's content, so not allowed for a read-only document
            pullButton.setEnabled(!isReadOnly());
            checkForUpdatesButton.setEnabled(true);
        } else {
            pullButton.setEnabled(false);
            checkForUpdatesButton.setEnabled(false);
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, GitRepoSettings> {

    }
}
