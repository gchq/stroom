package stroom.gitrepo.impl;

import stroom.gitrepo.api.GitRepoStorageService;
import stroom.gitrepo.api.GitRepoStore;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.EntityServiceException;

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
    GitRepoPushExecutor(GitRepoStore gitRepoStore,
                        GitRepoStorageService gitRepoStorageService) {
        this.gitRepoStore = gitRepoStore;
        this.gitRepoStorageService = gitRepoStorageService;
    }

    /**
     * Runs the job.
     */
    public void exec() {
        LOGGER.info(() -> TASK_NAME + " Running");

        // Get all the GitRepoDoc instances and push them
        var docRefs = gitRepoStore.list();
        for (var docRef : docRefs) {
            GitRepoDoc gitRepoDoc = gitRepoStore.readDocument(docRef);

            if (gitRepoDoc.isAutoPush()) {
                try {
                    gitRepoStorageService.exportDoc(
                            gitRepoDoc,
                            JOB_COMMIT_MESSAGE,
                            false);
                } catch (IOException | EntityServiceException e) {
                    LOGGER.error(TASK_NAME + " error: {}: {} ", e.getClass().getSimpleName(), e.getMessage());
                }
            }
        }
    }

}
