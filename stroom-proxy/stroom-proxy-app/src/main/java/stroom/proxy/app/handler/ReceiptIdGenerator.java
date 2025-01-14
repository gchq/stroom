package stroom.proxy.app.handler;

import stroom.util.concurrent.UniqueIdGenerator;
import stroom.util.concurrent.UniqueIdGenerator.UniqueId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.function.Supplier;

@Singleton
public class ReceiptIdGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReceiptIdGenerator.class);

    private final UniqueIdGenerator receiptIdGenerator;

    @Inject
    public ReceiptIdGenerator(final ProxyId proxyId) {
        LOGGER.info("Creating receiptIdGenerator for proxyId '{}'", proxyId);
        receiptIdGenerator = new UniqueIdGenerator(proxyId.getId());
    }

    public ReceiptIdGenerator(final Supplier<String> nodeIdSupplier) {
        final String nodeId = Objects.requireNonNull(nodeIdSupplier).get();
        LOGGER.info("Creating receiptIdGenerator for proxyId '{}'", nodeId);
        receiptIdGenerator = new UniqueIdGenerator(nodeId);
    }

    /**
     * @return A globally unique ID.
     */
    public UniqueId generateId() {
        return receiptIdGenerator.generateId();
    }
}
