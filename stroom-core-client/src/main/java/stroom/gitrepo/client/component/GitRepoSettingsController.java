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

import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.CredentialClient;
import stroom.credentials.client.presenter.CredentialListModel;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.gitrepo.shared.GitRepoPushDto;
import stroom.gitrepo.shared.GitRepoResource;
import stroom.http.client.component.HttpClientConfigEditorController;
import stroom.util.shared.NullSafe;
import stroom.util.shared.http.HttpClientConfig;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.Set;

/// A Git repository's Settings tab ([GitRepoSettings]): its settings, and pushing to, pulling from
/// and checking the repository for updates with them. Read only as its document is.
public class GitRepoSettingsController extends Controller {

    // Server REST API for GitRepoDocs
    private static final GitRepoResource GIT_REPO_RESOURCE = GWT.create(GitRepoResource.class);

    private final GitRepoSettings view;
    private final RestFactory restFactory;
    // Makes the commit message dialog box
    private final Provider<GitRepoCommitDialogController> commitDialogProvider;
    private final CredentialClient credentialClient;
    private final Provider<HttpClientConfigEditorController> httpClientConfigProvider;

    // The document read; null until it is
    private GitRepoDoc gitRepoDoc = null;

    // Held here rather than read back off the view, because the editor is a popup: it is only shown on
    // demand and the value has to survive between showings and into write()
    private HttpClientConfig httpClientConfiguration;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param view                     The tab.
    /// @param restFactory              Pushes, pulls and checks for updates.
    /// @param commitDialogProvider     Makes the dialog for the commit message.
    /// @param credentialClient         Finds the credentials.
    /// @param httpClientConfigProvider Makes the dialog that edits the HTTP client configuration.
    @Inject
    public GitRepoSettingsController(final EventBus eventBus,
                                     final GitRepoSettings view,
                                     final RestFactory restFactory,
                                     final Provider<GitRepoCommitDialogController> commitDialogProvider,
                                     final CredentialClient credentialClient,
                                     final Provider<HttpClientConfigEditorController> httpClientConfigProvider) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.commitDialogProvider = commitDialogProvider;
        this.credentialClient = credentialClient;
        this.httpClientConfigProvider = httpClientConfigProvider;
        initWidget(view);

        final CredentialListModel credentialListModel = new CredentialListModel(eventBus, credentialClient,
                Set.of(CredentialType.SSH_KEY, CredentialType.USERNAME_PASSWORD, CredentialType.ACCESS_TOKEN));
        credentialListModel.setTaskMonitorFactory(taskMonitor());
        view.setCredentialModel(credentialListModel);

        view.setOnChange(this::markChanged);
        view.setOnSetHttpClientConfig(this::onSetHttpClientConfiguration);
        view.setOnPush(this::onGitRepoPush);
        view.setOnPull(this::onGitRepoPull);
        view.setOnCheckForUpdates(this::onCheckForUpdates);
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    /// Shows a repository's settings.
    ///
    /// @param docRef The repository.
    /// @param doc    The repository.
    public void read(final DocRef docRef, final GitRepoDoc doc) {
        // Local copy of the initial value of the doc
        gitRepoDoc = doc;

        if (doc.getContentStoreMetadata() != null) {
            view.setContentPack(doc.getContentStoreMetadata().getOwnerName(), doc.getName());
        } else {
            view.setContentPack("", "");
        }
        view.setUrl(doc.getUrl());
        view.setBranch(doc.getBranch());
        view.setPath(doc.getPath());
        view.setCommitToPull(doc.getCommit());
        view.setAutoPush(doc.isAutoPush());

        httpClientConfiguration = doc.getHttpClientConfiguration();
        if (httpClientConfiguration == null) {
            // Nothing saved, so fetch a starting point rather than opening an empty editor. Note this does
            // not make the document dirty - it is only written back if the user changes something.
            restFactory
                    .create(GIT_REPO_RESOURCE)
                    .method(GitRepoResource::getDefaultHttpClientConfig)
                    .onSuccess(result -> httpClientConfiguration = result)
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }

        // Credentials - store locally
        grabCredentialsList(doc.getCredentialName());
    }

    /// Writes the settings shown into a copy of a repository.
    ///
    /// @param doc The repository.
    /// @return A copy of it with the settings shown.
    public GitRepoDoc write(final GitRepoDoc doc) {
        final GitRepoDoc.Builder builder = doc
                .copy()
                .url(view.getUrl())
                .branch(view.getBranch())
                .path(view.getPath())
                .commit(view.getCommitToPull());

        // Only enable auto-push when the repo is not pinned to a specific commit (a commit hash implies
        // a detached, non-pushable state). Derive this from the LIVE commit field, not the last-saved
        // doc, and do NOT change the view here - write runs on every dirty check, and the visibility
        // of the auto-push option is already handled by the view.
        final String commit = view.getCommitToPull();
        final boolean pinnedToCommit = commit != null && !commit.isEmpty();
        builder.autoPush(!pinnedToCommit && view.isAutoPush());

        // Credentials - store from local values
        final Credential credential = view.getCredential();
        builder.credentialName(credential == null
                ? null
                : credential.getName());

        builder.httpClientConfiguration(httpClientConfiguration);

        return builder.build();
    }

    private void onSetHttpClientConfiguration() {
        httpClientConfigProvider.get().show(this, httpClientConfiguration, updated -> {
            if (!Objects.equals(httpClientConfiguration, updated)) {
                httpClientConfiguration = updated;
                markChanged();
            }
        });
    }

    // Called when Git Push button is pressed
    private void onGitRepoPush() {
        // Use the gitRepoDoc saved in read(), if available
        if (gitRepoDoc != null) {
            commitDialogProvider.get().show(this, this::requestGitRepoPush);
        } else {
            AlertEvent.fireWarn(this,
                    "Git repository information not available",
                    "",
                    null);
        }
    }

    // Does the push into Git, once the commit dialog has been OK'd with a commit message
    private void requestGitRepoPush(final String commitMessage) {
        final GitRepoDoc doc = write(gitRepoDoc);
        final GitRepoPushDto dto = new GitRepoPushDto(doc, commitMessage);
        restFactory
                .create(GIT_REPO_RESOURCE)
                .method(res -> res.pushToGit(dto))
                .onSuccess(result -> {
                    // Pop up an alert to show what happened
                    if (result.isOk()) {
                        AlertEvent.fireInfo(this,
                                "Push Success",
                                result.getMessage(),
                                null);
                    } else {
                        AlertEvent.fireError(this,
                                "Push Failure",
                                result.getMessage(),
                                null);
                    }
                })
                .owner(this)
                .taskMonitorFactory(view.getPushTaskMonitor())
                .exec();
    }

    // Called when the Git Pull button is pressed
    private void onGitRepoPull() {
        if (gitRepoDoc != null) {
            final GitRepoDoc doc = write(gitRepoDoc);
            restFactory
                    .create(GIT_REPO_RESOURCE)
                    .method(res -> res.pullFromGit(doc))
                    .onSuccess(result -> {
                        if (result.isOk()) {
                            AlertEvent.fireInfo(this,
                                    "Pull Success",
                                    result.getMessage(),
                                    () -> RefreshExplorerTreeEvent.fire(this));
                        } else {
                            AlertEvent.fireError(
                                    this,
                                    "Pull Failure",
                                    result.getMessage(),
                                    () -> RefreshExplorerTreeEvent.fire(this));
                        }
                    })
                    .owner(this)
                    .taskMonitorFactory(view.getPullTaskMonitor())
                    .exec();
        } else {
            AlertEvent.fireWarn(this, "Git repository information not available", "", null);
        }
    }

    // Called when the check for updates button is pressed
    private void onCheckForUpdates() {
        if (gitRepoDoc != null) {

            final GitRepoDoc doc = write(gitRepoDoc);
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
                    .onFailure(error -> AlertEvent.fireError(
                            this,
                            "Update Check Failure",
                            error.getMessage(),
                            null))
                    .owner(this)
                    .taskMonitorFactory(view.getCheckForUpdatesTaskMonitor())
                    .exec();

        } else {
            AlertEvent.fireWarn(this, "Git repository information is not available",
                    "", null);
        }
    }

    // Shows the credential of the name given, or none for a blank name
    private void grabCredentialsList(final String credentialName) {
        if (NullSafe.isBlankString(credentialName)) {
            view.setCredential(null);
        } else {
            credentialClient.getCredentialByName(credentialName, guard(view::setCredential), taskMonitor());
        }
    }

}
