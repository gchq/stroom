package stroom.data.store.impl.fs;

import stroom.task.shared.Task;
import stroom.util.lifecycle.jobmanagement.ScheduledJob;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.util.lifecycle.jobmanagement.Schedule.ScheduleType.CRON;
import static stroom.util.lifecycle.jobmanagement.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
class FileSystemDataStoreJobs implements ScheduledJobs {
    private FileSystemCleanExecutor fileSystemCleanExecutor;
    private StreamDeleteExecutor streamDeleteExecutor;

    @Inject
    public FileSystemDataStoreJobs(FileSystemCleanExecutor fileSystemCleanExecutor,
                                   StreamDeleteExecutor streamDeleteExecutor) {
        this.fileSystemCleanExecutor = fileSystemCleanExecutor;
        this.streamDeleteExecutor = streamDeleteExecutor;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("File System Clean")
                        .description("Job to process a volume deleting files that are no " +
                                "longer indexed (maybe the retention period has past or they have been deleted)")
                        .schedule(CRON, "0 0 *")
                        .method((task) -> this.fileSystemCleanExecutor.exec((Task) task))
                        .advanced(false)
                        .build(),
                jobBuilder()
                        .name("Stream Delete")
                        .description("Physically delete streams that have been logically deleted " +
                                "based on age of delete (stroom.data.store.deletePurgeAge)")
                        .schedule(CRON, "0 0 *")
                        .method((task) -> this.streamDeleteExecutor.exec())
                        .build()
        );
    }
}
