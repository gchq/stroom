package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;

import java.io.OutputStream;
import java.util.function.Consumer;

public interface EventConsumer {

    void consume(final AttributeMap attributeMap,
                 final String requestUuid,
                 final String data);
}
