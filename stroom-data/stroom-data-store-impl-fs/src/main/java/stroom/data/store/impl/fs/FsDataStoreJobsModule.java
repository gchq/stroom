package stroom.data.store.impl.fs;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskRunnable;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;

public class FsDataStoreJobsModule extends ScheduledJobsModule {
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
                .name("Meta Delete")
                .description("Physically delete streams that have been logically deleted " +
                        "based on age of delete (stroom.data.store.deletePurgeAge)")
                .schedule(CRON, "0 0 *")
                .to(MetaDelete.class);
    }

    private static class FileSystemClean extends TaskRunnable {
        @Inject
        FileSystemClean(final FsCleanExecutor fileSystemCleanExecutor) {
            super(fileSystemCleanExecutor::clean);
        }
    }

    private static class MetaDelete extends TaskRunnable {
        @Inject
        MetaDelete(final PhysicalDeleteExecutor physicalDeleteExecutor) {
            super(physicalDeleteExecutor::exec);
        }
    }
}
