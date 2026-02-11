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

package stroom.gitrepo.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.gitrepo.client.GitRepoPlugin;
import stroom.gitrepo.client.presenter.GitRepoCommitDialogPresenter;
import stroom.gitrepo.client.presenter.GitRepoCommitDialogPresenter.GitRepoCommitDialogView;
import stroom.gitrepo.client.presenter.GitRepoPresenter;
import stroom.gitrepo.client.presenter.GitRepoSettingsPresenter;
import stroom.gitrepo.client.presenter.GitRepoSettingsPresenter.GitRepoSettingsView;
import stroom.gitrepo.client.view.GitRepoCommitDialogViewImpl;
import stroom.gitrepo.client.view.GitRepoSettingsViewImpl;

public class GitRepoModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(GitRepoPlugin.class);
        bind(GitRepoPresenter.class);
        bindPresenterWidget(GitRepoSettingsPresenter.class, GitRepoSettingsView.class, GitRepoSettingsViewImpl.class);

        // Tie up the commit message dialog
        bindPresenterWidget(GitRepoCommitDialogPresenter.class,
                GitRepoCommitDialogView.class,
                GitRepoCommitDialogViewImpl.class);
    }
}
