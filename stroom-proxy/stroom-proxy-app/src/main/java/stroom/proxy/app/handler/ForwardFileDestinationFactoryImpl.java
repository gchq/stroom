package stroom.proxy.app.handler;

import stroom.util.io.PathCreator;

import java.nio.file.Path;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ForwardFileDestinationFactoryImpl implements ForwardFileDestinationFactory {

    private final PathCreator pathCreator;

    @Inject
    public ForwardFileDestinationFactoryImpl(final PathCreator pathCreator) {
        this.pathCreator = pathCreator;
    }

    @Override
    public ForwardFileDestination create(final ForwardFileConfig config) {
        // Create the store directory.
        final Path storeDir = pathCreator.toAppPath(config.getPath());
        DirUtil.ensureDirExists(storeDir);

        return new ForwardFileDestinationImpl(storeDir);
    }
}
