package stroom.gitrepo.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.job.api.ScheduledJobsBinder;
import stroom.util.RunnableWrapper;
import stroom.util.shared.scheduler.CronExpressions;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

/**
 * Sets up the jobs for the GitRepo module.
 * Instantiated by stroom.app.guice.JobsModule.
 */
@SuppressWarnings("unused")
public class GitRepoJobsModule extends AbstractModule {

    /**
     * Called by the injection system to set up the job.
     */
    @Override
    protected void configure() {
        ScheduledJobsBinder.create(binder())
                .bindJobTo(GitRepoPush.class, builder -> builder
                        .name(GitRepoPushExecutor.TASK_NAME)
                        .description("Push changed objects to a remote Git repository")
                        .cronSchedule(CronExpressions.EVERY_MINUTE.getExpression())
                        .advanced(false));
    }

    /**
     * Class to wrap the job.
     */
    private static class GitRepoPush extends RunnableWrapper {

        @Inject
        GitRepoPush(final GitRepoPushExecutor gitRepoPushExecutor,
                    final ClusterLockService clusterLockService) {
            super(() -> clusterLockService.tryLock(
                    GitRepoPushExecutor.LOCK_NAME,
                    gitRepoPushExecutor::exec));
        }
    }
}
