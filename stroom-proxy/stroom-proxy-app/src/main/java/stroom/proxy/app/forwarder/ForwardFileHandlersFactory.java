package stroom.proxy.app.forwarder;

import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;
import stroom.util.io.PathCreator;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ForwardFileHandlersFactory {

    private final QueueMonitors queueMonitors;
    private final FileStores fileStores;

    @Inject
    public ForwardFileHandlersFactory(final QueueMonitors queueMonitors,
                                      final FileStores fileStores) {
        this.queueMonitors = queueMonitors;
        this.fileStores = fileStores;
    }

    public ForwardFileHandlers create(final ForwardFileConfig config,
                                      final PathCreator pathCreator,
                                      final int order,
                                      final String name) {
        return new ForwardFileHandlers(
                config,
                pathCreator,
                queueMonitors,
                fileStores,
                order,
                name);
    }
}
