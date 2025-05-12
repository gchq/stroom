package stroom.gitrepo.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

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
     * Logger
     */
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GitRepoPushExecutor.class);

    /**
     * Constructor, called from GitRepoJobsModule.
     */
    @Inject
    GitRepoPushExecutor() {
        // No code
    }

    /**
     * Runs the job.
     */
    public void exec() {
        LOGGER.info(() -> TASK_NAME + " Running");
    }
    
}
