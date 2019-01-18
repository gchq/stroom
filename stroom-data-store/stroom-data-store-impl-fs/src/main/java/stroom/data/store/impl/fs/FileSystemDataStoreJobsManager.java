package stroom.data.store.impl.fs;

import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;

class FileSystemDataStoreJobsManager extends ScheduledJobsModule {
    private final Provider<FileSystemCleanExecutor> fileSystemCleanExecutorProvider;
    private final Provider<StreamDeleteExecutor> streamDeleteExecutorProvider;

    @Inject
    FileSystemDataStoreJobsManager(final Provider<FileSystemCleanExecutor> fileSystemCleanExecutorProvider,
                                   final Provider<StreamDeleteExecutor> streamDeleteExecutorProvider) {
        this.fileSystemCleanExecutorProvider = fileSystemCleanExecutorProvider;
        this.streamDeleteExecutorProvider = streamDeleteExecutorProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("File System Clean")
                .description("Job to process a volume deleting files that are no " +
                        "longer indexed (maybe the retention period has past or they have been deleted)")
                .schedule(CRON, "0 0 *")
                .advanced(false)
                .to(() -> (task) -> fileSystemCleanExecutorProvider.get().exec(task));
        bindJob()
                .name("Stream Delete")
                .description("Physically delete streams that have been logically deleted " +
                        "based on age of delete (stroom.data.store.deletePurgeAge)")
                .schedule(CRON, "0 0 *")
                .to(() -> (task) -> streamDeleteExecutorProvider.get().exec());
    }
}
