package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.util.concurrent.UniqueIdGenerator.UniqueId;

public interface EventConsumer {

    void consume(final AttributeMap attributeMap,
                 final UniqueId receiptId,
                 final String data);
}
