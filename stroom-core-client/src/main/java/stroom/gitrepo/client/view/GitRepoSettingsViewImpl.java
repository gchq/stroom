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

import stroom.credentials.shared.Credential;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.gitrepo.client.presenter.GitRepoSettingsPresenter.GitRepoSettingsView;
import stroom.gitrepo.client.presenter.GitRepoSettingsUiHandlers;
import stroom.item.client.SelectionBox;
import stroom.widget.button.client.Button;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

/**
 * Backs up GitRepoSettingsViewImpl.ui.xml for the GitRepo Settings tab.
 */
public class GitRepoSettingsViewImpl
        extends ViewWithUiHandlers<GitRepoSettingsUiHandlers>
        implements GitRepoSettingsView {

    /**
     * The widget that this represents
     */
    private final Widget widget;

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
    CustomCheckBox chkGitAutoPush;
    @UiField
    Button btnGitRepoPush;
    @UiField
    Button btnGitRepoPull;
    @UiField
    Button btnCheckForUpdates;
    @UiField
    Button setHttpClientConfig;

    /**
     * Whether the document is read only, so the push and pull actions must stay disabled.
     */
    private boolean readOnly;

    @Inject
    public GitRepoSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setContentStoreName(final String contentStoreName) {
        this.lblContentStore.setText(contentStoreName);
    }

    @Override
    public void setContentPackName(final String contentPackName) {
        this.lblContentPack.setText(contentPackName);
    }

    @Override
    public String getUrl() {
        return txtGitUrl.getText();
    }

    @Override
    public void setUrl(final String url) {
        this.txtGitUrl.setText(url);
    }

    @Override
    public String getBranch() {
        return txtGitBranch.getText();
    }

    @Override
    public void setBranch(final String branch) {
        this.txtGitBranch.setText(branch);
    }

    @Override
    public String getPath() {
        return txtGitPath.getText();
    }

    @Override
    public void setPath(final String path) {
        this.txtGitPath.setText(path);
    }

    @Override
    public String getCommitToPull() {
        return txtGitCommitToPull.getText();
    }

    @Override
    public void setCommitToPull(final String commit) {
        this.txtGitCommitToPull.setText(commit);
    }

    @Override
    public Boolean isAutoPush() {
        return this.chkGitAutoPush.getValue();
    }

    @Override
    public void setAutoPush(final Boolean autoPush) {
        // Objects.requireNonNullElse() not defined for GWT
        if (autoPush == null) {
            this.chkGitAutoPush.setValue(Boolean.FALSE);
        } else {
            this.chkGitAutoPush.setValue(autoPush);
        }
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        // Read only is independent of the enabled state that setState() manages, so a later
        // setState() can't make these fields editable
        txtGitUrl.setReadOnly(readOnly);
        txtGitBranch.setReadOnly(readOnly);
        txtGitPath.setReadOnly(readOnly);
        credentialSelectionBox.setReadOnly(readOnly);
        txtGitCommitToPull.setReadOnly(readOnly);
        chkGitAutoPush.setReadOnly(readOnly);
        setState();
    }

    /**
     * Sets the enabled/disabled state of widgets.
     * Called when the state of widgets changes.
     * Also called from the Presenter onBind().
     */
    @Override
    public void setState() {
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
            btnGitRepoPush.setEnabled(canPush && !readOnly);
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
            btnGitRepoPull.setEnabled(!readOnly);
            btnCheckForUpdates.setEnabled(true);
        } else {
            btnGitRepoPull.setEnabled(false);
            btnCheckForUpdates.setEnabled(false);
        }
    }

    @Override
    public SelectionBox<Credential> getCredentialSelectionBox() {
        return credentialSelectionBox;
    }

    /**
     * Sets the Dirty flag if any of the UI widget's content changes.
     *
     * @param e Event from the UI widget. Ignored. Can be null.
     */
    @SuppressWarnings("unused")
    @UiHandler({
            "txtGitUrl",
            "txtGitBranch",
            "txtGitPath",
            "txtGitCommitToPull"})
    public void onWidgetValueChange(@SuppressWarnings("unused") final ValueChangeEvent<String> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
        this.setState();
    }

    /**
     * Sets the Dirty flag if the selected credentials change.
     *
     * @param e Ignored. Can be null.
     */
    @SuppressWarnings("unused")
    @UiHandler("credentialSelectionBox")
    public void onSelectionValueChange(@SuppressWarnings("unused") final ValueChangeEvent<Credential> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    /**
     * Sets the dirty flag when the autoPush checkbox is changed.
     *
     * @param event Event from the UI widget. Ignored. Can be null.
     */
    @SuppressWarnings("unused")
    @UiHandler({"chkGitAutoPush"})
    public void onAutoPushClick(@SuppressWarnings("unused") final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
        this.setState();
    }

    /**
     * Handles 'Set Http Client Config' button clicks.
     *
     * @param event The button push event. Ignored. Can be null.
     */
    @SuppressWarnings("unused")
    @UiHandler("setHttpClientConfig")
    public void onSetHttpClientConfigClick(@SuppressWarnings("unused") final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onSetHttpClientConfiguration();
        }
    }

    /**
     * Handles 'Push to Git' button clicks.
     * Passes the button to display the wait icon.
     *
     * @param event The button push event.
     */
    @SuppressWarnings("unused")
    @UiHandler("btnGitRepoPush")
    public void onGitRepoPushClick(@SuppressWarnings("unused") final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onGitRepoPush(btnGitRepoPush);
        }
    }

    /**
     * Handles 'Pull from Git' button clicks.
     * Passes the button to display the wait icon.
     *
     * @param event The button push event. Ignored. Can be null.
     */
    @SuppressWarnings("unused")
    @UiHandler("btnGitRepoPull")
    public void onGitRepoPullClick(@SuppressWarnings("unused") final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onGitRepoPull(btnGitRepoPull);
        }
    }

    /**
     * Handles 'Check for updates' button clicks.
     *
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
