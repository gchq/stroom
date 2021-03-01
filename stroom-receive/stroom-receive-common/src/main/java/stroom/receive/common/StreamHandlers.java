package stroom.receive.common;

import stroom.meta.api.AttributeMap;

import java.util.function.Consumer;

public interface StreamHandlers {

    void handle(AttributeMap attributeMap, Consumer<StreamHandler> consumer);
}
