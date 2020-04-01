package stroom.proxy.repo;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class ProxyLifecycle implements Managed {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyLifecycle.class);

    private final ProxyRepositoryManager proxyRepositoryManager;
    private final ProxyRepositoryReader proxyRepositoryReader;

    @Inject
    public ProxyLifecycle(final ProxyRepositoryManager proxyRepositoryManager, final ProxyRepositoryReader proxyRepositoryReader) {
        this.proxyRepositoryManager = proxyRepositoryManager;
        this.proxyRepositoryReader = proxyRepositoryReader;
    }

    @Override
    public void start() {
        LOGGER.info("Starting Stroom Proxy");

        proxyRepositoryManager.start();
        proxyRepositoryReader.start();

        LOGGER.info("Started Stroom Proxy");
    }

    @Override
    public void stop() {
        LOGGER.info("Stopping Stroom Proxy");

        try {
            proxyRepositoryReader.stop();
            proxyRepositoryManager.stop();
        } catch (Exception e) {
            LOGGER.error("error",e);
        }

        // This method is part of DW  Managed which is managed by Jersey so we need to ensure any interrupts
        // are cleared before it goes back to Jersey
        Thread.interrupted();

        LOGGER.info("Stopped Stroom Proxy");
    }
}
