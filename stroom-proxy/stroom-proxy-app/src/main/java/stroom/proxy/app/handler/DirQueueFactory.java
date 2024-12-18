package stroom.proxy.app.handler;

import stroom.proxy.app.DataDirProvider;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;

import jakarta.inject.Inject;

import java.nio.file.Path;

public class DirQueueFactory {

    private final Path dataDir;
    private final QueueMonitors queueMonitors;
    private final FileStores fileStores;

    @Inject
    public DirQueueFactory(final DataDirProvider dataDirProvider,
                           final QueueMonitors queueMonitors,
                           final FileStores fileStores) {
        this.dataDir = dataDirProvider.get();
        this.queueMonitors = queueMonitors;
        this.fileStores = fileStores;
    }

    public DirQueue create(final String dirName,
                           final int order,
                           final String name) {
        final Path rootDir = dataDir.resolve(dirName);
        return create(rootDir, order, name);
    }

    public DirQueue create(final Path rootDir,
                           final int order,
                           final String name) {
        return new DirQueue(rootDir, queueMonitors, fileStores, order, name);
    }
}
