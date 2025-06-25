package stroom.core.receive;

import stroom.node.api.NodeInfo;
import stroom.receive.common.ReceiptIdGenerator;
import stroom.util.concurrent.UniqueId;
import stroom.util.concurrent.UniqueId.NodeType;
import stroom.util.concurrent.UniqueIdGenerator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Objects;

@Singleton
public class StroomReceiptIdGenerator implements ReceiptIdGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomReceiptIdGenerator.class);
    private static final String UNSAFE_CHARS_REGEX = "[^A-Za-z0-9-]";
    private static final NodeType NODE_TYPE = NodeType.STROOM;

    private final UniqueIdGenerator uniqueIdGenerator;

    @Inject
    public StroomReceiptIdGenerator(final NodeInfo nodeInfo) {
        final String nodeName = nodeInfo.getThisNodeName();
        if (NullSafe.isBlankString(nodeName)) {
            throw new IllegalArgumentException("nodeName is blank");
        }
        final String nodeId = createSafeString(nodeName);
        if (!Objects.equals(nodeName, nodeId)) {
            LOGGER.info("Using nodeId '{}' for receiptId meta attribute as derived from nodeName '{}'",
                    nodeId, nodeName);
        }
        this.uniqueIdGenerator = new UniqueIdGenerator(NODE_TYPE, nodeId);
    }

    @Override
    public UniqueId generateId() {
        return uniqueIdGenerator.generateId();
    }

    static String createSafeString(final String in) {
        if (in == null) {
            return null;
        } else {
            return in.replaceAll(UNSAFE_CHARS_REGEX, "-");
        }
    }
}
