/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.gitrepo.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.gitrepo.shared.GitRepoPushDto;
import stroom.gitrepo.shared.GitRepoResource;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

/**
 * Provides the main functionality on the client behind the GitRepo Settings tab.
 */
public class GitRepoSettingsPresenter
        extends DocumentEditPresenter<GitRepoSettingsPresenter.GitRepoSettingsView, GitRepoDoc>
        implements GitRepoSettingsUiHandlers {

    /**
     * Server REST API.
     */
    private static final GitRepoResource GIT_REPO_RESOURCE = GWT.create(GitRepoResource.class);

    /**
     * Provides REST connection to the server.
     */
    private final RestFactory restFactory;

    /**
     * Local copy of the gitRepoDoc, saved in the onRead() method.
     * Might be null if onRead() hasn't been called yet.
     */
    private GitRepoDoc gitRepoDoc = null;

    @Inject
    public GitRepoSettingsPresenter(final EventBus eventBus,
                                    final GitRepoSettingsView view,
                                    final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
        view.setUiHandlers(this);
    }

    @Override
    protected void onRead(final DocRef docRef, final GitRepoDoc doc, final boolean readOnly) {
        gitRepoDoc = doc;
        this.getView().setUrl(doc.getUrl());
        this.getView().setUsername(doc.getUsername());
        this.getView().setPassword(doc.getPassword());
        this.getView().setBranch(doc.getBranch());
        this.getView().setPath(doc.getPath());
        this.getView().setCommit(doc.getCommit());
        this.getView().setAutoPush(doc.isAutoPush());
    }

    @Override
    protected GitRepoDoc onWrite(final GitRepoDoc doc) {
        doc.setUrl(this.getView().getUrl());
        doc.setUsername(this.getView().getUsername());
        doc.setPassword(this.getView().getPassword());
        doc.setBranch(this.getView().getBranch());
        doc.setPath(this.getView().getPath());
        doc.setCommit(this.getView().getCommit());
        doc.setAutoPush(this.getView().isAutoPush());
        return doc;
    }

    @Override
    public void onDirty() {
        this.setDirty(true);
    }

    /**
     * Called when Git Push button is pressed.
     * @param taskMonitorFactory Where the wait icon is displayed.
     */
    @Override
    public void onGitRepoPush(final TaskMonitorFactory taskMonitorFactory) {
        // Use the gitRepoDoc saved in the onRead() method, if available
        if (gitRepoDoc != null) {
            final GitRepoDoc doc = onWrite(gitRepoDoc);
            final GitRepoPushDto dto = new GitRepoPushDto(doc, this.getView().getCommitMessage());
            restFactory
                    .create(GIT_REPO_RESOURCE)
                    .method(res -> res.pushToGit(dto))
                    .onSuccess(result -> {
                        // Wipe the commit message
                        this.getView().setCommitMessage("");

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
        } else {
            AlertEvent.fireWarn(this, "Git repository information not available", "", null);
        }
    }

    /**
     * Called when the Git Pull button is pressed.
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

    public interface GitRepoSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<GitRepoSettingsUiHandlers> {

        void setUrl(String url);

        String getUrl();

        String getUsername();

        void setUsername(final String username);

        String getPassword();

        void setPassword(final String password);

        String getBranch();

        void setBranch(final String branch);

        String getPath();

        void setPath(final String directory);

        String getCommit();

        void setCommit(String commit);

        String getCommitMessage();

        void setCommitMessage(final String commitMessage);

        Boolean isAutoPush();

        void setAutoPush(Boolean autoPush);
    }
}
