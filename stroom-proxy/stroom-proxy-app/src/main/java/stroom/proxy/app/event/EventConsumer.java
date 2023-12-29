package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;

public interface EventConsumer {

    void consume(final AttributeMap attributeMap,
                 final String requestUuid,
                 final String data);
}
