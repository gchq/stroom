package stroom.proxy.app;

import stroom.proxy.app.event.EventStore;
import stroom.proxy.app.event.EventStoreConfig;
import stroom.proxy.repo.ProxyServices;

import io.dropwizard.lifecycle.Managed;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

public class ProxyLifecycle implements Managed {

    private final ProxyServices proxyServices;

    @Inject
    public ProxyLifecycle(final ProxyConfig proxyConfig,
                          final EventStoreConfig eventStoreConfig,
                          final Provider<EventStore> eventStoreProvider,
                          final ProxyServices proxyServices) {
        this.proxyServices = proxyServices;

        // Add executor to roll event store.
        final EventStore eventStore = eventStoreProvider.get();
        proxyServices.addFrequencyExecutor("Event Store - roll",
                () -> eventStore::tryRoll,
                eventStoreConfig.getRollFrequency().toMillis());
        // Add executor to forward event store.
        proxyServices.addFrequencyExecutor("Event Store - forward",
                () -> eventStore::forwardAll,
                eventStoreConfig.getRollFrequency().toMillis());

        if (proxyConfig.getSqsConnectors() != null) {
            for (final SqsConnectorConfig sqsConnectorConfig : proxyConfig.getSqsConnectors()) {
                final SqsConnector sqsConnector = new SqsConnector(eventStore, sqsConnectorConfig);
                // Add executor to forward event store.
                proxyServices.addFrequencyExecutor("SQS - poll",
                        () -> sqsConnector::poll,
                        sqsConnectorConfig.getPollFrequency().toMillis());
            }
        }
    }

    @Override
    public void start() throws Exception {
        proxyServices.start();
    }

    @Override
    public void stop() {
        proxyServices.stop();
    }
}
