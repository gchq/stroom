package stroom.proxy.app.handler;

import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;

@Singleton
public class ForwardFileDestinationFactoryImpl implements ForwardFileDestinationFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardFileDestinationFactoryImpl.class);

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

        final ForwardFileDestinationImpl forwardFileDestination = new ForwardFileDestinationImpl(
                storeDir,
                config.getName(),
                config.getSubPathTemplate(),
                config.getTemplatingMode(),
                pathCreator);

        LOGGER.info("Created forward file destination {} at {} with getSubPathTemplate '{}' (isInstant: {})",
                config.getName(),
                config.getPath(),
                config.getSubPathTemplate(),
                config.isInstant());

        return forwardFileDestination;
    }
}
