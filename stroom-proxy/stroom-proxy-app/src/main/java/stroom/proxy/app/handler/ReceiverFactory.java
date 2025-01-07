package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;

public interface ReceiverFactory {

    Receiver get(AttributeMap attributeMap);
}
