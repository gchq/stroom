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
import stroom.gitrepo.shared.GitRepoPushDto;
import stroom.gitrepo.shared.GitRepoResource;
import stroom.gitrepo.shared.GitRepoResponse;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.Message;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Objects;

/**
 * Server-side code to respond to REST requests for the GitRepo.
 */
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

    /**
     * Called on the server when a REST request is received from the
     * UI for a Git Push.
     * @param gitRepoPushDto The DTO holding the info.
     * @return a GitRepoResponse with all the messages about
     *         whether it worked.
     */
    @Override
    public GitRepoResponse pushToGit(final GitRepoPushDto gitRepoPushDto) {
        Objects.requireNonNull(gitRepoPushDto);
        GitRepoResponse response;
        try {
            LOGGER.info("Pushing to Git repo: '{}'", gitRepoPushDto.getGitRepoDoc().getUrl());
            final List<Message> messages = gitRepoStorageServiceProvider.get()
                            .exportDoc(gitRepoPushDto.getGitRepoDoc(),
                                       gitRepoPushDto.getCommitMessage(),
                                       true);
            response = this.createResponse(messages);
        } catch (final Exception e) {
            response = new GitRepoResponse(false, e.getMessage());
        }
        return response;
    }

    /**
     * Called on the server when a REST request is received from the
     * UI for a Git Pull.
     * @param gitRepoDoc The doc holding git repo info.
     * @return a GitRepoRespose with all the messages about
     * whether it worked.
     */
    @Override
    public GitRepoResponse pullFromGit(final GitRepoDoc gitRepoDoc) {
        Objects.requireNonNull(gitRepoDoc);
        GitRepoResponse response;
        try {
            LOGGER.info("Pulling from Git repo: {}", gitRepoDoc.getUrl());
            final List<Message> messages = gitRepoStorageServiceProvider.get()
                    .importDoc(gitRepoDoc);
            response = this.createResponse(messages);
        } catch (final Exception e) {
            response = new GitRepoResponse(false, e.getMessage());
        }
        return response;
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(GitRepoDoc.TYPE)
                .build();
    }

    /**
     * Converts the response from the exportDoc method into something we can
     * send back to the UI and show to the user.
     * @param messages The collection of messages for the export process.
     * @return the response for the UI. Never returns null.
     */
    private GitRepoResponse createResponse(final List<Message> messages) {
        Objects.requireNonNull(messages);
        final var buf = new StringBuilder("Success:\n");
        for (final var m : messages) {
            buf.append(m);
            buf.append("\n");
        }

        return new GitRepoResponse(true, buf.toString());
    }
}
