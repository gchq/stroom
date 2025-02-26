package stroom.proxy.app.handler;

import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.repo.ProxyServices;
import stroom.util.io.SimplePathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

public class ForwardHttpPostDestinationFactoryImpl implements ForwardHttpPostDestinationFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(
            ForwardHttpPostDestinationFactoryImpl.class);

    private final CleanupDirQueue cleanupDirQueue;
    private final ProxyServices proxyServices;
    private final DirQueueFactory dirQueueFactory;
    private final Provider<ProxyConfig> proxyConfigProvider;
    private final DataDirProvider dataDirProvider;
    private final SimplePathCreator simplePathCreator;
    private final HttpSenderFactory httpSenderFactory;

    @Inject
    public ForwardHttpPostDestinationFactoryImpl(final CleanupDirQueue cleanupDirQueue,
                                                 final ProxyServices proxyServices,
                                                 final DirQueueFactory dirQueueFactory,
                                                 final Provider<ProxyConfig> proxyConfigProvider,
                                                 final DataDirProvider dataDirProvider,
                                                 final SimplePathCreator simplePathCreator,
                                                 final HttpSenderFactory httpSenderFactory) {
        this.cleanupDirQueue = cleanupDirQueue;
        this.proxyServices = proxyServices;
        this.dirQueueFactory = dirQueueFactory;
        this.proxyConfigProvider = proxyConfigProvider;
        this.dataDirProvider = dataDirProvider;
        this.simplePathCreator = simplePathCreator;
        this.httpSenderFactory = httpSenderFactory;
    }

    @Override
    public ForwardDestination create(final ForwardHttpPostConfig forwardHttpPostConfig) {
        final ThreadConfig threadConfig = proxyConfigProvider.get().getThreadConfig();
        final StreamDestination streamDestination = httpSenderFactory.create(forwardHttpPostConfig);
        final String name = forwardHttpPostConfig.getName();
        final ForwardHttpPostDestination forwardHttpDestination = new ForwardHttpPostDestination(
                name,
                streamDestination,
                cleanupDirQueue,
                forwardHttpPostConfig);

        final ForwardDestination destination = getWrappedForwardDestination(
                forwardHttpPostConfig, forwardHttpDestination);

        LOGGER.info("Created {} '{}' with url '{}', threadCount: {}, " +
                    "retryCount: {}",
                destination.getClass().getSimpleName(),
                name,
                forwardHttpPostConfig.getForwardUrl(),
                threadConfig.getForwardThreadCount(),
                threadConfig.getForwardRetryThreadCount());

        return destination;
    }

    private ForwardDestination getWrappedForwardDestination(
            final ForwardHttpPostConfig config,
            final ForwardHttpPostDestination forwardHttpPostDestination) {

        final ForwardQueueConfig forwardQueueConfig = config.getForwardQueueConfig();
        final ForwardDestination destination;
        if (forwardQueueConfig != null) {
            // We have queue config so wrap out ultimate destination with some queue/retry logic
            destination = new RetryingForwardDestination(
                    forwardQueueConfig,
                    forwardHttpPostDestination,
                    dataDirProvider,
                    simplePathCreator,
                    dirQueueFactory,
                    proxyServices);
        } else {
            destination = forwardHttpPostDestination;
        }
        return destination;
    }
}
