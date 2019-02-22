package stroom.data.store.impl.fs;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskConsumer;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;

public class FileSystemDataStoreJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("File System Clean")
                .description("Job to process a volume deleting files that are no " +
                        "longer indexed (maybe the retention period has past or they have been deleted)")
                .schedule(CRON, "0 0 *")
                .advanced(false)
                .to(FileSystemClean.class);
        bindJob()
                .name("Stream Delete")
                .description("Physically delete streams that have been logically deleted " +
                        "based on age of delete (stroom.data.store.deletePurgeAge)")
                .schedule(CRON, "0 0 *")
                .to(StreamDelete.class);
    }

    private static class FileSystemClean extends TaskConsumer {
        @Inject
        FileSystemClean(final FileSystemCleanExecutor fileSystemCleanExecutor) {
            super(fileSystemCleanExecutor::exec);
        }
    }

    private static class StreamDelete extends TaskConsumer {
        @Inject
        StreamDelete(final StreamDeleteExecutor streamDeleteExecutor) {
            super(task -> streamDeleteExecutor.exec());
        }
    }
}
