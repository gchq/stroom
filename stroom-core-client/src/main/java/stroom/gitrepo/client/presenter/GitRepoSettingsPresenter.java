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

import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.gitrepo.shared.GitRepoDoc;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class GitRepoSettingsPresenter extends DocumentEditPresenter<GitRepoSettingsPresenter.GitRepoSettingsView, GitRepoDoc> {

    @Inject
    public GitRepoSettingsPresenter(final EventBus eventBus,
                                   final GitRepoSettingsView view) {
        super(eventBus, view);
    }

    @Override
    protected void onBind() {
        final DirtyHandler dirtyHandler = event -> setDirty(true);
        //registerHandler(gitRepoDependencyListPresenter.addDirtyHandler(dirtyHandler));
    }

    @Override
    protected void onRead(final DocRef docRef, final GitRepoDoc doc, final boolean readOnly) {
        //gitRepoDependencyListPresenter.read(docRef, doc, readOnly);
    }

    @Override
    protected GitRepoDoc onWrite(GitRepoDoc gitRepo) {
        //gitRepo = gitRepoDependencyListPresenter.write(gitRepo);
        return gitRepo;
    }

    public interface GitRepoSettingsView extends View {

        /**
         * Puts the GIT repository URL into the UI.
         * @param url The GIT repository URL.
         */
        public void setUrl(String url);

        /**
         * Returns the GIT repository URL from the UI.
         * @return The GIT repository URL.
         */
        public String getUrl();
    }
}
