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

package stroom.gitrepo.impl;

import stroom.gitrepo.api.GitRepoStorageService;
import stroom.gitrepo.api.GitRepoStore;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.io.IOException;

/**
 * Runs the job to automatically push stuff to
 */
public class GitRepoPushExecutor {

    /**
     * Name of job as shown in the UI.
     */
    public static final String TASK_NAME = "Git Repo Push";

    /**
     * Name of cluster lock for this job.
     */
    public static final String LOCK_NAME = "GitRepoPush";

    /**
     * Commit message used from the job.
     */
    private static final String JOB_COMMIT_MESSAGE = "Anonymous commit by Git Repo Push job";

    /**
     * Where we get the GitRepos from. Injected into the constructor.
     */
    private final GitRepoStore gitRepoStore;

    /**
     * What we use to push the items to Git.
     */
    private final GitRepoStorageService gitRepoStorageService;

    /**
     * Logger
     */
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GitRepoPushExecutor.class);

    /**
     * Constructor, called from GitRepoJobsModule.
     */
    @Inject
    GitRepoPushExecutor(final GitRepoStore gitRepoStore,
                        final GitRepoStorageService gitRepoStorageService) {
        this.gitRepoStore = gitRepoStore;
        this.gitRepoStorageService = gitRepoStorageService;
    }

    /**
     * Runs the job.
     */
    public void exec() {
        LOGGER.debug("{} - Running", TASK_NAME);

        // Get all the GitRepoDoc instances and push them
        NullSafe.stream(gitRepoStore.list())
                .map(gitRepoStore::readDocument)
                .filter(GitRepoDoc::isAutoPush)
                .forEach(gitRepoDoc -> {
                    try {
                        final DurationTimer timer = DurationTimer.start();
                        gitRepoStorageService.exportDoc(
                                gitRepoDoc,
                                JOB_COMMIT_MESSAGE,
                                false);
                        LOGGER.info("{} - Pushed gitRepoDoc {} in {}", TASK_NAME, gitRepoDoc, timer);
                    } catch (final IOException | EntityServiceException e) {
                        LOGGER.error("{} - error: {}: {} ", TASK_NAME, e.getClass().getSimpleName(), e.getMessage());
                    }
                });
        LOGGER.debug("{} - Finished", TASK_NAME);
    }
}
