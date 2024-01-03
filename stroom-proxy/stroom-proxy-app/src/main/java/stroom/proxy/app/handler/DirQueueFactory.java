package stroom.proxy.app.handler;

import stroom.proxy.repo.RepoDirProvider;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;

import java.nio.file.Path;
import javax.inject.Inject;

public class DirQueueFactory {

    private final Path repoDir;
    private final QueueMonitors queueMonitors;
    private final FileStores fileStores;

    @Inject
    public DirQueueFactory(final RepoDirProvider repoDirProvider,
                           final QueueMonitors queueMonitors,
                           final FileStores fileStores) {
        this.repoDir = repoDirProvider.get();
        this.queueMonitors = queueMonitors;
        this.fileStores = fileStores;
    }

    public DirQueue create(String dirName,
                           final int order,
                           final String name) {
        final Path rootDir = repoDir.resolve(dirName);
        return new DirQueue(rootDir, queueMonitors, fileStores, order, name);
    }
}
