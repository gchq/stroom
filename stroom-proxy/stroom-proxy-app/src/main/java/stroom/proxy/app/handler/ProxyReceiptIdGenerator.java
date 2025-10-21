package stroom.proxy.app.handler;

import stroom.receive.common.ReceiptIdGenerator;
import stroom.util.concurrent.UniqueId;
import stroom.util.concurrent.UniqueId.NodeType;
import stroom.util.concurrent.UniqueIdGenerator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link ReceiptIdGenerator} for a proxy node
 */
@Singleton
public class ProxyReceiptIdGenerator implements ReceiptIdGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyReceiptIdGenerator.class);

    private static final NodeType NODE_TYPE = NodeType.PROXY;

    private final UniqueIdGenerator receiptIdGenerator;

    @Inject
    public ProxyReceiptIdGenerator(final ProxyId proxyId) {
        LOGGER.info("Creating receiptIdGenerator for proxyId '{}'", proxyId);
        receiptIdGenerator = new UniqueIdGenerator(NODE_TYPE, proxyId.getId());
    }

    /**
     * For testing
     */
    public ProxyReceiptIdGenerator(final Supplier<String> nodeIdSupplier) {
        final String nodeId = Objects.requireNonNull(nodeIdSupplier).get();
        LOGGER.info("Creating receiptIdGenerator for proxyId '{}'", nodeId);
        receiptIdGenerator = new UniqueIdGenerator(NODE_TYPE, nodeId);
    }

    /**
     * @return A globally unique ID.
     */
    @Override
    public UniqueId generateId() {
        return receiptIdGenerator.generateId();
    }
}
