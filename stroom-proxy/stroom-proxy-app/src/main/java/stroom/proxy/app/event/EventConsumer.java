package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.proxy.app.handler.ReceiptId;

public interface EventConsumer {

    void consume(final AttributeMap attributeMap,
                 final ReceiptId receiptId,
                 final String data);
}
