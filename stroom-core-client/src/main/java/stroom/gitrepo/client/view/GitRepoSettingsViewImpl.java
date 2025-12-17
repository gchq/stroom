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

import stroom.credentials.shared.Credentials;
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

import java.util.List;

/**
 * Backs up GitRepoSettingsViewImpl.ui.xml for the GitRepo Settings tab.
 */
public class GitRepoSettingsViewImpl
        extends ViewWithUiHandlers<GitRepoSettingsUiHandlers>
        implements GitRepoSettingsView {

    /** The widget that this represents */
    private final Widget widget;

    /** Checks App Permissions */
    //private ClientSecurityContext securityContext = null;
    private boolean hasCredentialsAppPermission = false;

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
    SelectionBox<Credentials> selCredentials;

    @UiField
    Button btnSetCredentials;

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

    /**
     * Credentials to use when credentials are selected but we don't have permission to see them.
     */
    private final Credentials noPermissionCredentials = new Credentials();

    /**
     * Credentials to use when no credentials are selected.
     */
    private static final Credentials NULL_CREDENTIALS = new Credentials();

    @Inject
    public GitRepoSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        // Selection list should display the name of the credentials
        selCredentials.setDisplayValueFunction(Credentials::getName);

        // Set up the credentials we use when credentials are selected but we don't have permission
        // to see them
        noPermissionCredentials.setName("HIDDEN");
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
            selCredentials.setVisible(true);
            selCredentials.setEnabled(hasCredentialsAppPermission);
            btnSetCredentials.setVisible(true);
            btnSetCredentials.setEnabled(hasCredentialsAppPermission);
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
            selCredentials.setVisible(true);
            selCredentials.setEnabled(hasCredentialsAppPermission);
            btnSetCredentials.setVisible(true);
            btnSetCredentials.setEnabled(hasCredentialsAppPermission);
            txtGitCommitToPull.setVisible(true);
            txtGitCommitToPull.setEnabled(true);

            // Is commit specified? If not can push to Git
            // as long as the URL is specified
            if (txtGitCommitToPull.getText().isEmpty()) {
                fgGitAutoPush.setVisible(true);
                btnGitRepoPush.setVisible(true);

                if (!txtGitUrl.getText().isEmpty()) {
                    chkGitAutoPush.setEnabled(true);
                    btnGitRepoPush.setEnabled(true);
                } else {
                    chkGitAutoPush.setEnabled(false);
                    btnGitRepoPush.setEnabled(false);
                }

            } else {
                fgGitAutoPush.setVisible(false);
                btnGitRepoPush.setVisible(false);
            }
        }

        // Can pull and check for updates if URL is set
        if (!txtGitUrl.getText().isEmpty()) {
            btnGitRepoPull.setEnabled(true);
            btnCheckForUpdates.setEnabled(true);
        } else {
            btnGitRepoPull.setEnabled(false);
            btnCheckForUpdates.setEnabled(false);
        }

    }

    /**
     * Called from Presenter to set whether the user has App Permissions.
     */
    @Override
    public void setHasCredentialsAppPermission(final boolean hasCredentialsAppPermission) {
        this.hasCredentialsAppPermission = hasCredentialsAppPermission;
    }

    /**
     * Sets the list of credentials in the list that is displayed.
     * Due to permissions, the credentialsList might not contain the selected credentials as that
     * user might not have permission to see those credentials. However, we still need to return
     * that credentials ID when saving these creds without changing it.
     * @param credentialsList The list of credentials to display in the list.
     * @param selectedCredentialsId The ID of the selected credentials. Can
     *                              be null if nothing is selected.
     */
    @Override
    public void setCredentialsList(final List<Credentials> credentialsList,
                                   final String selectedCredentialsId) {
        selCredentials.clear();

        selCredentials.addItem(NULL_CREDENTIALS);
        selCredentials.addItems(credentialsList);

        // Find the selected credentials
        boolean matchedCredentials = false;
        if (selectedCredentialsId != null && !selectedCredentialsId.isBlank()) {
            for (final Credentials credentials : credentialsList) {
                if (credentials.getUuid().equals(selectedCredentialsId)) {
                    selCredentials.setValue(credentials);
                    matchedCredentials = true;
                    break;
                }
            }
            if (!matchedCredentials) {
                // Fudge credentials to match the ID of our non-matching ID
                noPermissionCredentials.setUuid(selectedCredentialsId);
                selCredentials.addItem(noPermissionCredentials);
                selCredentials.setValue(noPermissionCredentials);
            }
        } else {
            // Nothing selected so select the null item
            selCredentials.setValue(NULL_CREDENTIALS);
        }
    }

    /**
     * @return The currently selected credentials, or null if nothing is selected.
     */
    @Override
    public String getCredentialsId() {
        final String selectedCredentialsId;
        final Credentials selectedCredentials = selCredentials.getValue();
        if (selectedCredentials != null) {
            if (!selectedCredentials.getUuid().equals(NULL_CREDENTIALS.getUuid())) {
                selectedCredentialsId = selectedCredentials.getUuid();
            } else {
                selectedCredentialsId = null;
            }
        } else {
            selectedCredentialsId = null;
        }

        return selectedCredentialsId;
    }

    /**
     * Sets the Dirty flag if any of the UI widget's content changes.
     * @param e Event from the UI widget. Ignored. Can be null.
     */
    @SuppressWarnings("unused")
    @UiHandler({"txtGitUrl",
            "txtGitBranch",
            "txtGitPath",
            "txtGitCommitToPull"})
    public void onWidgetValueChange(@SuppressWarnings("unused") final ValueChangeEvent<String> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
        this.setState();
    }

    /**
     * Sets the Dirty flag if the selected credentials change.
     * @param e Ignored. Can be null.
     */
    @SuppressWarnings("unused")
    @UiHandler({"selCredentials"})
    public void onSelectionValueChange(@SuppressWarnings("unused") final ValueChangeEvent<Credentials> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
    }

    /**
     * Sets the dirty flag when the autoPush checkbox is changed.
     * @param event Event from the UI widget. Ignored. Can be null.
     */
    @SuppressWarnings("unused")
    @UiHandler({"chkGitAutoPush"})
    public void onAutoPushClick(@SuppressWarnings("unused") final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
        this.setState();
    }

    /**
     * Handles 'Set Credentials' button clicks.
     * Passes the button to display the wait icon.
     * @param event The button push event.
     */
    @SuppressWarnings("unused")
    @UiHandler("btnSetCredentials")
    public void onBtnSetCredentialsClick(@SuppressWarnings("unused") final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onShowCredentialsDialog(btnSetCredentials);
        }
    }

    /**
     * Handles 'Push to Git' button clicks.
     * Passes the button to display the wait icon.
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
