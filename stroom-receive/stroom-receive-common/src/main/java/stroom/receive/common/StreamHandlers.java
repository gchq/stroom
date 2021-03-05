package stroom.receive.common;

import stroom.meta.api.AttributeMap;

import java.util.function.Consumer;

public interface StreamHandlers {

    void handle(String feedName,
                String typeName,
                AttributeMap attributeMap,
                Consumer<StreamHandler> consumer);
}
