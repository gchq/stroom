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

package stroom.gitrepo.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.CredentialClient;
import stroom.credentials.client.presenter.CredentialListModel;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.gitrepo.shared.GitRepoPushDto;
import stroom.gitrepo.shared.GitRepoResource;
import stroom.item.client.SelectionBox;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.Set;

/**
 * Provides the main functionality on the client behind the GitRepo Settings tab.
 */
public class GitRepoSettingsPresenter
        extends DocumentEditPresenter<GitRepoSettingsPresenter.GitRepoSettingsView, GitRepoDoc>
        implements GitRepoSettingsUiHandlers {

    /**
     * Provides REST connection to the server.
     */
    private final RestFactory restFactory;

    /**
     * Shows the commit message dialog box.
     */
    private final GitRepoCommitDialogPresenter commitDialog;

    /**
     * Server REST API for GitRepoDocs
     */
    private static final GitRepoResource GIT_REPO_RESOURCE = GWT.create(GitRepoResource.class);

    /**
     * Local copy of the gitRepoDoc, saved in the onRead() method.
     * Might be null if onRead() hasn't been called yet.
     */
    private GitRepoDoc gitRepoDoc = null;
    private final CredentialClient credentialClient;

    /**
     * Injected constructor.
     *
     * @param eventBus     For parent class
     * @param view         The View for showing stuff to users
     * @param restFactory  For talking to the server
     * @param commitDialog Injected dialog for commit message
     */
    @Inject
    public GitRepoSettingsPresenter(final EventBus eventBus,
                                    final GitRepoSettingsView view,
                                    final RestFactory restFactory,
                                    final GitRepoCommitDialogPresenter commitDialog,
                                    final CredentialClient credentialClient) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.credentialClient = credentialClient;
        view.setUiHandlers(this);
        this.commitDialog = commitDialog;
        final CredentialListModel credentialListModel = new CredentialListModel(eventBus, credentialClient,
                Set.of(CredentialType.SSH_KEY, CredentialType.USERNAME_PASSWORD, CredentialType.ACCESS_TOKEN));
        credentialListModel.setTaskMonitorFactory(this);
        view.getCredentialSelectionBox().setModel(credentialListModel);
    }

    /**
     * Called when the UI is created and values need to be set from
     * the GitRepoDoc.
     */
    @Override
    protected void onRead(final DocRef docRef, final GitRepoDoc doc, final boolean readOnly) {
        // Local copy of the initial value of the doc
        gitRepoDoc = doc;

        final GitRepoSettingsView view = this.getView();
        if (doc.getContentStoreMetadata() != null) {
            view.setContentStoreName(doc.getContentStoreMetadata().getOwnerName());
            view.setContentPackName(doc.getName());
        } else {
            view.setContentStoreName("");
            view.setContentPackName("");
        }
        view.setUrl(doc.getUrl());
        view.setBranch(doc.getBranch());
        view.setPath(doc.getPath());
        view.setCommitToPull(doc.getCommit());
        view.setAutoPush(doc.isAutoPush());

        // Credentials - store locally
        grabCredentialsList(doc.getCredentialName());

        // Set the initial state of the UI
        view.setState();
    }

    /**
     * Called when values are being saved, so the UI values need to be stored
     * into the GitRepoDoc.
     */
    @Override
    protected GitRepoDoc onWrite(final GitRepoDoc doc) {
        final GitRepoSettingsView view = this.getView();
        doc.setUrl(view.getUrl());
        doc.setBranch(view.getBranch());
        doc.setPath(view.getPath());
        doc.setCommit(view.getCommitToPull());

        // Only save autoPush = true if we can push i.e. no commit hash ref
        if (doc.getCommit().isEmpty()) {
            doc.setAutoPush(view.isAutoPush());
        } else {
            doc.setAutoPush(false);
            view.setAutoPush(false);
        }

        // Credentials - store from local values
        final Credential credential = view.getCredentialSelectionBox().getValue();
        doc.setCredentialName(credential == null
                ? null
                : credential.getName());

        return doc;
    }

    /**
     * Called when anything changes, so the UI is dirty.
     */
    @Override
    public void onDirty() {
        this.setDirty(true);
    }

    /**
     * Called when Git Push button is pressed.
     *
     * @param taskMonitorFactory Where the wait icon is displayed.
     */
    @Override
    public void onGitRepoPush(final TaskMonitorFactory taskMonitorFactory) {

        // Use the gitRepoDoc saved in the onRead() method, if available
        if (gitRepoDoc != null) {
            final ShowPopupEvent.Builder builder = ShowPopupEvent.builder(commitDialog);
            commitDialog.setupDialog(builder);
            builder
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            // OK pressed so check if the dialog is valid
                            if (commitDialog.isValid()) {
                                e.hide();
                                requestGitRepoPush(taskMonitorFactory, commitDialog.getView().getCommitMessage());
                            } else {
                                // Something is wrong - tell user what it is and reset the dialog
                                AlertEvent.fireWarn(commitDialog, commitDialog.getValidationMessage(), e::reset);
                            }
                        } else {
                            // Cancel pressed
                            e.hide();
                        }
                    })
                    .fire();
        } else {
            AlertEvent.fireWarn(this,
                    "Git repository information not available",
                    "",
                    null);
        }
    }

    /**
     * Does the push into Git, once the CommitDialog has been OK'd.
     *
     * @param taskMonitorFactory Where to display the wait icon
     * @param commitMessage      The commit message, from the dialog box.
     */
    private void requestGitRepoPush(final TaskMonitorFactory taskMonitorFactory,
                                    final String commitMessage) {

        final GitRepoDoc doc = onWrite(gitRepoDoc);
        final GitRepoPushDto dto = new GitRepoPushDto(doc, commitMessage);
        restFactory
                .create(GIT_REPO_RESOURCE)
                .method(res -> res.pushToGit(dto))
                .onSuccess(result -> {
                    // Pop up an alert to show what happened
                    if (result.isOk()) {
                        AlertEvent.fireInfo(GitRepoSettingsPresenter.this,
                                "Push Success",
                                result.getMessage(),
                                null);
                    } else {
                        AlertEvent.fireError(GitRepoSettingsPresenter.this,
                                "Push Failure",
                                result.getMessage(),
                                null);
                    }
                })
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /**
     * Called when the Git Pull button is pressed.
     *
     * @param taskMonitorFactory Where to display the wait icon.
     */
    @Override
    public void onGitRepoPull(final TaskMonitorFactory taskMonitorFactory) {
        if (gitRepoDoc != null) {
            final GitRepoDoc doc = onWrite(gitRepoDoc);
            restFactory
                    .create(GIT_REPO_RESOURCE)
                    .method(res -> res.pullFromGit(doc))
                    .onSuccess(result -> {
                        if (result.isOk()) {
                            AlertEvent.fireInfo(this,
                                    "Pull Success",
                                    result.getMessage(),
                                    () -> RefreshExplorerTreeEvent.fire(GitRepoSettingsPresenter.this));
                        } else {
                            AlertEvent.fireError(
                                    this,
                                    "Pull Failure",
                                    result.getMessage(),
                                    () -> RefreshExplorerTreeEvent.fire(GitRepoSettingsPresenter.this));
                        }
                    })
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        } else {
            AlertEvent.fireWarn(this, "Git repository information not available", "", null);
        }
    }

    /**
     * Called when the check for updates button is pressed.
     *
     * @param taskMonitorFactory Where to display the wait icon.
     */
    @Override
    public void onCheckForUpdates(final TaskMonitorFactory taskMonitorFactory) {
        if (gitRepoDoc != null) {

            final GitRepoDoc doc = onWrite(gitRepoDoc);
            restFactory
                    .create(GIT_REPO_RESOURCE)
                    .method(res -> res.areUpdatesAvailable(doc))
                    .onSuccess(result -> {
                        if (result.isOk()) {
                            AlertEvent.fireInfo(this,
                                    "Update Check Success",
                                    result.getMessage(),
                                    null);
                        } else {
                            AlertEvent.fireError(
                                    this,
                                    "Update Check Failure",
                                    result.getMessage(),
                                    null);
                        }
                    })
                    .onFailure(error -> {
                        AlertEvent.fireError(
                                this,
                                "Update Check Failure",
                                error.getMessage(),
                                null);
                    })
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();

        } else {
            AlertEvent.fireWarn(this, "Git repository information is not available",
                    "", null);
        }
    }

    /**
     * Gets the list of credentials and puts them into the selection list.
     *
     * @param credentialName The name of the currently selected credential, or
     *                       null if nothing is selected.
     */
    private void grabCredentialsList(final String credentialName) {
        if (credentialName == null) {
            getView().getCredentialSelectionBox().setValue(null);
        } else {
            credentialClient.getCredentialByName(credentialName, credential ->
                    getView().getCredentialSelectionBox().setValue(credential), this);
        }
    }

    public interface GitRepoSettingsView
            extends View, HasUiHandlers<GitRepoSettingsUiHandlers> {

        SelectionBox<Credential> getCredentialSelectionBox();

        void setContentStoreName(String contentStoreName);

        void setContentPackName(String contentPackName);

        void setUrl(String url);

        String getUrl();

        String getBranch();

        void setBranch(final String branch);

        String getPath();

        void setPath(final String directory);

        String getCommitToPull();

        void setCommitToPull(String commit);

        Boolean isAutoPush();

        void setAutoPush(Boolean autoPush);

        /**
         * Called by presenter on startup to set the state of the UI.
         */
        void setState();
    }
}
