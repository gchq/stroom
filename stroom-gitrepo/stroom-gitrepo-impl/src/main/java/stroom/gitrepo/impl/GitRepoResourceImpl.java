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

package stroom.gitrepo.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.gitrepo.api.GitRepoStore;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.gitrepo.shared.GitRepoPushResponse;
import stroom.gitrepo.shared.GitRepoResource;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;

@AutoLogged
class GitRepoResourceImpl implements GitRepoResource {

    private final Provider<GitRepoStore> gitRepoStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<GitRepoStorageService> gitRepoStorageServiceProvider;

    /**
     * Logger so we can follow what is going on.
     */
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GitRepoResourceImpl.class);

    @Inject
    GitRepoResourceImpl(final Provider<GitRepoStore> gitRepoStoreProvider,
                        final Provider<DocumentResourceHelper> documentResourceHelperProvider,
                        final Provider<GitRepoStorageService> gitRepoStorageServiceProvider) {
        this.gitRepoStoreProvider = gitRepoStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.gitRepoStorageServiceProvider = gitRepoStorageServiceProvider;
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

    @Override
    public GitRepoPushResponse pushToGit(final GitRepoDoc gitRepoDoc) {
        GitRepoPushResponse response;
        try {
            LOGGER.error("Pushing Git repo: '{}'", gitRepoDoc);
            gitRepoStorageServiceProvider.get().exportDoc(gitRepoDoc);
            response = new GitRepoPushResponse(true, "Pushed ok");
        }
        catch (IOException e) {
            response = new GitRepoPushResponse(false, e.getMessage());
        }
        return response;
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(GitRepoDoc.TYPE)
                .build();
    }
}
