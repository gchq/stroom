/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.app.gin.editors;

import stroom.gitrepo.client.presenter.GitRepoCommitDialogPresenter;
import stroom.gitrepo.client.presenter.GitRepoCommitDialogPresenter.GitRepoCommitDialogView;
import stroom.gitrepo.client.presenter.GitRepoPresenter;
import stroom.gitrepo.client.presenter.GitRepoSettingsPresenter;
import stroom.gitrepo.client.presenter.GitRepoSettingsPresenter.GitRepoSettingsView;
import stroom.gitrepo.client.view.GitRepoCommitDialogViewImpl;
import stroom.gitrepo.client.view.GitRepoSettingsViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `GitRepoModule`
/// (`stroom/gitrepo/client/gin/GitRepoModule.java`),
/// without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class GitRepoScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(GitRepoPresenter.class);
        bindPresenterWidget(GitRepoSettingsPresenter.class,
                GitRepoSettingsView.class,
                GitRepoSettingsViewImpl.class);
        bindPresenterWidget(GitRepoCommitDialogPresenter.class,
                GitRepoCommitDialogView.class,
                GitRepoCommitDialogViewImpl.class);
    }
}

