package stroom.proxy.app.forwarder;

import stroom.util.io.PathCreator;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ForwardFileHandlersFactory {

    private final PathCreator pathCreator;

    @Inject
    public ForwardFileHandlersFactory(final PathCreator pathCreator) {
        this.pathCreator = pathCreator;
    }

    public ForwardFileHandlers create(final ForwardFileConfig config,
                                      final PathCreator pathCreator) {
        return new ForwardFileHandlers(config, pathCreator);
    }
}
