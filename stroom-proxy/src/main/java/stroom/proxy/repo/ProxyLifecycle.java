package stroom.proxy.repo;

import io.dropwizard.lifecycle.Managed;

import javax.inject.Inject;

public class ProxyLifecycle implements Managed {
    private final ProxyRepositoryManager proxyRepositoryManager;
    private final ProxyRepositoryReader proxyRepositoryReader;

    @Inject
    public ProxyLifecycle(final ProxyRepositoryManager proxyRepositoryManager, final ProxyRepositoryReader proxyRepositoryReader) {
        this.proxyRepositoryManager = proxyRepositoryManager;
        this.proxyRepositoryReader = proxyRepositoryReader;
    }

    @Override
    public void start() {
        proxyRepositoryManager.start();
        proxyRepositoryReader.start();
    }

    @Override
    public void stop() {
        proxyRepositoryReader.stop();
        proxyRepositoryManager.stop();
    }
}
