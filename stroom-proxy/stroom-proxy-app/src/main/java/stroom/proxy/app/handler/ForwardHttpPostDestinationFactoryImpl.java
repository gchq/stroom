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
    public ForwardHttpPostDestination create(final ForwardHttpPostConfig forwardHttpPostConfig) {
        final ThreadConfig threadConfig = proxyConfigProvider.get().getThreadConfig();
        final StreamDestination streamDestination = httpSenderFactory.create(forwardHttpPostConfig);
        final String name = forwardHttpPostConfig.getName();
        final ForwardHttpPostDestination forwardDestination = new ForwardHttpPostDestination(
                name,
                streamDestination,
                cleanupDirQueue,
                forwardHttpPostConfig,
                proxyServices,
                dirQueueFactory,
                threadConfig,
                dataDirProvider,
                simplePathCreator);

        LOGGER.info("Created ForwardHTTP destination '{}' with url '{}', threadCount: {}, " +
                    "retryCount: {}",
                name,
                forwardHttpPostConfig.getForwardUrl(),
                threadConfig.getForwardThreadCount(),
                threadConfig.getForwardRetryThreadCount());

        return forwardDestination;
    }
}
