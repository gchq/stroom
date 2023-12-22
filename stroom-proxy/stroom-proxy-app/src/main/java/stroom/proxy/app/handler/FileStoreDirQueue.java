package stroom.proxy.app.handler;

import stroom.proxy.repo.RepoDirProvider;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;

import java.io.IOException;
import java.nio.file.Path;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FileStoreDirQueue implements DirQueue {

    private final SequentialDirQueue sequentialDirQueue;

    @Inject
    FileStoreDirQueue(final RepoDirProvider repoDirProvider,
                      final QueueMonitors queueMonitors,
                      final FileStores fileStores) {
        sequentialDirQueue = new SequentialDirQueue(repoDirProvider.get().resolve("03_store"),
                queueMonitors,
                fileStores,
                1,
                "File store",
                true);
    }

    @Override
    public void add(final Path sourceDir) throws IOException {
        sequentialDirQueue.add(sourceDir);
    }

    @Override
    public SequentialDir next() {
        return sequentialDirQueue.next();
    }
}