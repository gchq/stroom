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
