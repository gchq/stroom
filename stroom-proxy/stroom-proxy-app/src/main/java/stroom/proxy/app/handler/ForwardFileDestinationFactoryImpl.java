package stroom.proxy.app.handler;

import stroom.proxy.app.DataDirProvider;
import stroom.proxy.repo.ProxyServices;
import stroom.proxy.repo.store.FileStores;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.Objects;

@Singleton
public class ForwardFileDestinationFactoryImpl implements ForwardFileDestinationFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardFileDestinationFactoryImpl.class);

    private final ProxyServices proxyServices;
    private final DirQueueFactory dirQueueFactory;
    private final DataDirProvider dataDirProvider;
    private final PathCreator pathCreator;
    private final FileStores fileStores;

    @Inject
    public ForwardFileDestinationFactoryImpl(final ProxyServices proxyServices,
                                             final DirQueueFactory dirQueueFactory,
                                             final DataDirProvider dataDirProvider,
                                             final PathCreator pathCreator,
                                             final FileStores fileStores) {
        this.proxyServices = proxyServices;
        this.dirQueueFactory = dirQueueFactory;
        this.dataDirProvider = dataDirProvider;
        this.pathCreator = pathCreator;
        this.fileStores = fileStores;
    }

    @Override
    public ForwardDestination create(final ForwardFileConfig config) {
        // Create the store directory.
        final Path storeDir = pathCreator.toAppPath(config.getPath());
        DirUtil.ensureDirExists(storeDir);

        final ForwardFileDestinationImpl forwardFileDestination = new ForwardFileDestinationImpl(
                storeDir,
                config,
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
        Objects.requireNonNull(forwardQueueConfig, () -> LogUtil.message(
                "No forwardQueueConfig set for destination '{}'", config.getName()));
        // We have queue config so wrap out ultimate destination with some queue/retry logic
        return new RetryingForwardDestination(
                forwardQueueConfig,
                forwardFileDestination,
                dataDirProvider,
                pathCreator,
                dirQueueFactory,
                proxyServices,
                fileStores);
    }
}
