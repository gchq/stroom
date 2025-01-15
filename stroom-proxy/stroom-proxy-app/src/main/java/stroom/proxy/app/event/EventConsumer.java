package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.util.concurrent.UniqueId;

public interface EventConsumer {

    void consume(final AttributeMap attributeMap,
                 final UniqueId receiptId,
                 final String data);
}
