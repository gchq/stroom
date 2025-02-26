package stroom.proxy.app.handler;

import stroom.proxy.app.DataDirProvider;
import stroom.proxy.repo.ProxyServices;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;

@Singleton
public class ForwardFileDestinationFactoryImpl implements ForwardFileDestinationFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardFileDestinationFactoryImpl.class);

    private final ProxyServices proxyServices;
    private final DirQueueFactory dirQueueFactory;
    private final DataDirProvider dataDirProvider;
    private final PathCreator pathCreator;

    @Inject
    public ForwardFileDestinationFactoryImpl(final ProxyServices proxyServices,
                                             final DirQueueFactory dirQueueFactory,
                                             final DataDirProvider dataDirProvider,
                                             final PathCreator pathCreator) {
        this.proxyServices = proxyServices;
        this.dirQueueFactory = dirQueueFactory;
        this.dataDirProvider = dataDirProvider;
        this.pathCreator = pathCreator;
    }

    @Override
    public ForwardDestination create(final ForwardFileConfig config) {
        // Create the store directory.
        final Path storeDir = pathCreator.toAppPath(config.getPath());
        DirUtil.ensureDirExists(storeDir);

        final ForwardFileDestinationImpl forwardFileDestination = new ForwardFileDestinationImpl(
                storeDir,
                config.getName(),
                config.getSubPathTemplate(),
                config.getTemplatingMode(),
                pathCreator);

        final ForwardDestination destination = getWrappedForwardDestination(config, forwardFileDestination);

        LOGGER.info("Created {} '{}' at {} with getSubPathTemplate '{}' (isInstant: {})",
                destination.getClass().getSimpleName(),
                config.getName(),
                config.getPath(),
                config.getSubPathTemplate(),
                config.isInstant());

        return destination;
    }

    private ForwardDestination getWrappedForwardDestination(final ForwardFileConfig config,
                                                            final ForwardFileDestinationImpl forwardFileDestination) {
        final ForwardQueueConfig forwardQueueConfig = config.getForwardQueueConfig();
        final ForwardDestination destination;
        if (forwardQueueConfig != null) {
            // We have queue config so wrap out ultimate destination with some queue/retry logic
            destination = new RetryingForwardDestination(
                    forwardQueueConfig,
                    forwardFileDestination,
                    dataDirProvider,
                    pathCreator,
                    dirQueueFactory,
                    proxyServices);
        } else {
            destination = forwardFileDestination;
        }
        return destination;
    }
}
