package stroom.proxy.app.handler;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.repo.ProxyServices;
import stroom.util.NullSafe;

import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;

public class ReceiverFactoryProvider implements Provider<ReceiverFactory> {

    private ReceiverFactory receiverFactory;

    @Inject
    public ReceiverFactoryProvider(final ProxyConfig proxyConfig,
                                   final Provider<InstantForwardHttpPost> directForwardHttpPostProvider,
                                   final Provider<InstantForwardFile> directForwardFileProvider,
                                   final Provider<Forwarder> forwarderProvider,
                                   final DirQueueFactory sequentialDirQueueFactory,
                                   final Provider<Aggregator> aggregatorProvider,
                                   final Provider<PreAggregator> preAggregatorProvider,
                                   final Provider<ZipReceiver> zipReceiverProvider,
                                   final Provider<SimpleReceiver> simpleReceiverProvider,
                                   final ProxyServices proxyServices) {
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
            final DirQueue forwardQueue = sequentialDirQueueFactory.create(
                    "20_forwarding",
                    20,
                    "Forwarding");
            // Move items from the forwarding queue to the forwarder.
            final DirQueueTransfer forwardingQueueTransfer = new DirQueueTransfer(forwardQueue::next, forwarder::add);
            proxyServices.addParallelExecutor("Forwarding queue transfer", () -> forwardingQueueTransfer, 1);

            if (proxyConfig.getAggregatorConfig() != null && proxyConfig.getAggregatorConfig().isEnabled()) {
                // If we are aggregating then create the aggregating moving parts.

                // Create the aggregator.
                final Aggregator aggregator = aggregatorProvider.get();
                aggregator.setDestination(forwardQueue::add);

                final DirQueue preAggregateQueue = sequentialDirQueueFactory.create(
                        "10_pre_aggregate",
                        10,
                        "Pre Aggregate");
                // Move items from the pre aggregate queue to the aggregator.
                // TODO : Could use more than one thread here.
                final DirQueueTransfer preAggregateQueueTransfer =
                        new DirQueueTransfer(preAggregateQueue::next, aggregator::addDir);
                proxyServices.addParallelExecutor("Pre aggregate queue transfer", () -> preAggregateQueueTransfer, 1);

                // Create the pre aggregator.
                final PreAggregator preAggregator = preAggregatorProvider.get();
                preAggregator.setDestination(preAggregateQueue::add);

                final DirQueue fileStoreQueue = sequentialDirQueueFactory.create(
                        "03_store",
                        3,
                        "File store");
                // Move items from the file store to the pre aggregator.
                final DirQueueTransfer fileStoreQueueTransfer =
                        new DirQueueTransfer(fileStoreQueue::next, preAggregator::addDir);
                proxyServices.addParallelExecutor("File store queue transfer", () -> fileStoreQueueTransfer, 1);

                // Create the receivers that will add data to the file store queue on receipt.
                final SimpleReceiver simpleReceiver = simpleReceiverProvider.get();
                simpleReceiver.setDestination(fileStoreQueue::add);
                final ZipReceiver zipReceiver = zipReceiverProvider.get();
                zipReceiver.setDestination(fileStoreQueue::add);

                receiverFactory = new StoringReceiverFactory(simpleReceiver, zipReceiver);

            } else {
                // If we aren't aggregating then we just need to queue items for forwarding.

                // Create the receivers that will add data to the forward queue on receipt.
                final SimpleReceiver simpleReceiver = simpleReceiverProvider.get();
                simpleReceiver.setDestination(forwardQueue::add);
                final ZipReceiver zipReceiver = zipReceiverProvider.get();
                zipReceiver.setDestination(forwardQueue::add);

                receiverFactory = new StoringReceiverFactory(simpleReceiver, zipReceiver);
            }
        }
    }

    @Override
    public ReceiverFactory get() {
        return receiverFactory;
    }
}
