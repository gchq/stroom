package stroom.proxy.app.forwarder;

import stroom.util.io.PathCreator;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ForwardFileDestinationFactory {

    private final PathCreator pathCreator;

    @Inject
    public ForwardFileDestinationFactory(final PathCreator pathCreator) {
        this.pathCreator = pathCreator;
    }

    public ForwardFileDestination create(final ForwardFileConfig config) {
        return new ForwardFileDestination(
                config,
                pathCreator);
    }
}
