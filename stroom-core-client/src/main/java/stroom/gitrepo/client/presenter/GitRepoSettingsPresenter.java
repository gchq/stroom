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
import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.gitrepo.shared.GitRepoDoc;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class GitRepoSettingsPresenter
        extends DocumentEditPresenter<GitRepoSettingsPresenter.GitRepoSettingsView, GitRepoDoc>
        implements DirtyUiHandlers {

    @Inject
    public GitRepoSettingsPresenter(final EventBus eventBus,
                                    final GitRepoSettingsView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        //final DirtyHandler dirtyHandler = event -> setDirty(true);
        //registerHandler(gitRepoSettingsListPresenter.addDirtyHandler(dirtyHandler));
    }

    @Override
    protected void onRead(final DocRef docRef, final GitRepoDoc doc, final boolean readOnly) {
        //gitRepoDependencyListPresenter.read(docRef, doc, readOnly);
        this.getView().setUrl(doc.getUrl());
        this.getView().setUsername(doc.getUsername());
        this.getView().setPassword(doc.getPassword());
        this.getView().setBranch(doc.getBranch());
        this.getView().setPath(doc.getPath());
    }

    @Override
    protected GitRepoDoc onWrite(GitRepoDoc doc) {
        //gitRepo = gitRepoDependencyListPresenter.write(gitRepo);
        doc.setUrl(this.getView().getUrl());
        doc.setUsername(this.getView().getUsername());
        doc.setPassword(this.getView().getPassword());
        doc.setBranch(this.getView().getBranch());
        doc.setPath(this.getView().getPath());
        return doc;
    }

    @Override
    public void onDirty() {
        this.setDirty(true);
    }

    public interface GitRepoSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<DirtyUiHandlers> {

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
    }
}
