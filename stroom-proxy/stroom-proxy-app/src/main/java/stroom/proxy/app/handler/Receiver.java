package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;

import java.io.InputStream;
import java.time.Instant;
import java.util.function.Supplier;

public interface Receiver {

    void receive(Instant startTime,
                 AttributeMap attributeMap,
                 String requestUri,
                 InputStreamSupplier inputStreamSupplier);
}
