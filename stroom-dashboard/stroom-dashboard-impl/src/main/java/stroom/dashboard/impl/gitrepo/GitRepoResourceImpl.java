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
 */

package stroom.dashboard.impl.gitrepo;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.gitrepo.shared.FetchLinkedGitRepoRequest;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.gitrepo.shared.GitRepoResource;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

@AutoLogged
class GitRepoResourceImpl implements GitRepoResource {

    private final Provider<GitRepoStore> gitRepoStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    GitRepoResourceImpl(final Provider<GitRepoStore> gitRepoStoreProvider,
                        final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.gitRepoStoreProvider = gitRepoStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public GitRepoDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(gitRepoStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public GitRepoDoc update(final String uuid, final GitRepoDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(gitRepoStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(GitRepoDoc.TYPE)
                .build();
    }

    @Override
    public List<GitRepoDoc> fetchLinkedGitRepos(final FetchLinkedGitRepoRequest request) {
        return gitRepoStoreProvider.get().fetchLinkedGitRepos(request.getGitRepo(), request.getLoadedGitRepos());
    }
}
