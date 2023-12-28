package stroom.proxy.app.handler;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.forwarder.ForwardConfig;
import stroom.proxy.app.forwarder.ForwardFileConfig;
import stroom.proxy.app.forwarder.ForwardHttpPostConfig;
import stroom.util.NullSafe;

import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;

public class ReceiverFactoryProvider implements Provider<ReceiverFactory> {

    private ReceiverFactory receiverFactory;

    @Inject
    public ReceiverFactoryProvider(final ProxyConfig proxyConfig,
                                   final Provider<DirectForwardHttpPost> directForwardHttpPostProvider,
                                   final Provider<DirectForwardFile> directForwardFileProvider,
                                   final Provider<StoringReceiverFactory> storingReceiverFactoryProvider,
                                   final Provider<Forwarder> forwarderProvider,
                                   final ManagedRegistry managedRegistry,
                                   final SequentialDirQueueFactory sequentialDirQueueFactory,
                                   final Provider<Aggregator> aggregatorProvider,
                                   final Provider<PreAggregator> preAggregatorProvider) {
        final long count = Stream
                .concat(NullSafe.list(proxyConfig.getForwardHttpDestinations()).stream(),
                        NullSafe.list(proxyConfig.getForwardFileDestinations()).stream())
                .filter(ForwardConfig::isEnabled)
                .count();

        if (count == 0) {
            throw new RuntimeException("No forward destinations are configured.");
        }

        if (!proxyConfig.getProxyRepositoryConfig().isStoringEnabled()) {
            if (count > 1) {
                throw new RuntimeException("Storing is not enabled but more than one forward destination is " +
                        "configured.");
            }

            // See if we can create a direct HTTP POST forwarding receiver.
            if (proxyConfig.getForwardHttpDestinations() != null) {
                final Optional<ForwardHttpPostConfig> optional = proxyConfig
                        .getForwardHttpDestinations()
                        .stream()
                        .filter(ForwardHttpPostConfig::isEnabled)
                        .findFirst();
                // Create a direct forwarding HTTP POST receiver.
                optional.ifPresent(forwardHttpPostConfig ->
                        receiverFactory = directForwardHttpPostProvider.get().get(forwardHttpPostConfig));
            }

            // See if we can create a direct file forwarding receiver.
            if (proxyConfig.getForwardFileDestinations() != null) {
                final Optional<ForwardFileConfig> optional = proxyConfig
                        .getForwardFileDestinations()
                        .stream()
                        .filter(ForwardFileConfig::isEnabled)
                        .findFirst();
                if (optional.isPresent()) {
                    // Create a direct forwarding file receiver.
                    optional.ifPresent(forwardFileConfig ->
                            receiverFactory = directForwardFileProvider.get().get(forwardFileConfig));
                }
            }
        } else {
            // Create forwarder.
            final Forwarder forwarder = forwarderProvider.get();
            // Create the forwarding queue.
            final SequentialDirQueue forwardQueue = sequentialDirQueueFactory.create(
                    "20_forwarding",
                    20,
                    "Forwarding");
            // Move items from the forwarding queue to the forwarder.
            final ManagedQueue forwardingQueueProcess =
                    new ManagedQueue(forwardQueue::next, forwarder::add, 1);
            managedRegistry.register(forwardingQueueProcess);

            if (proxyConfig.getAggregatorConfig().isEnabled()) {
                // If we are aggregating then create the aggregating moving parts.

                // Create the aggregator.
                final Aggregator aggregator = aggregatorProvider.get();
                aggregator.setDestination(forwardQueue::add);

                final SequentialDirQueue preAggregateQueue = sequentialDirQueueFactory.create(
                        "10_pre_aggregate",
                        10,
                        "Pre Aggregate");
                // Move items from the pre aggregate queue to the aggregator.
                final ManagedQueue preAggregateQueueProcess =
                        new ManagedQueue(preAggregateQueue::next, aggregator::addDir, 1);
                managedRegistry.register(preAggregateQueueProcess);

                // Create the pre aggregator.
                final PreAggregator preAggregator = preAggregatorProvider.get();
                preAggregator.setDestination(preAggregateQueue::add);

                final SequentialDirQueue fileStoreQueue = sequentialDirQueueFactory.create(
                        "03_store",
                        3,
                        "File store");
                // Move items from the file store to the pre aggregator.
                final ManagedQueue fileStoreQueueProcess =
                        new ManagedQueue(fileStoreQueue::next, preAggregator::addDir, 1);
                managedRegistry.register(fileStoreQueueProcess);

                // TODO : add items to the file store queue.

                dd
            }


            receiverFactory = storingReceiverFactoryProvider.get();
        }
    }

    @Override
    public ReceiverFactory get() {
        return receiverFactory;
    }
}
