package stroom.proxy.app.handler;

import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;

import java.nio.file.Path;
import javax.inject.Inject;

public class SequentialDirQueueFactory {

    private final Path repoDir;
    private final QueueMonitors queueMonitors;
    private final FileStores fileStores;

    @Inject
    public SequentialDirQueueFactory(final Path repoDir,
                                     final QueueMonitors queueMonitors,
                                     final FileStores fileStores) {
        this.repoDir = repoDir;
        this.queueMonitors = queueMonitors;
        this.fileStores = fileStores;
    }

    public SequentialDirQueue create(String dirName,
                                     final int order,
                                     final String name) {
        dirName = dirName.replaceAll("[^a-zA-Z0-9-_]", "_");
        final Path rootDir = repoDir.resolve(dirName);
        return new SequentialDirQueue(rootDir, queueMonitors, fileStores, order, name, true);
    }
}
