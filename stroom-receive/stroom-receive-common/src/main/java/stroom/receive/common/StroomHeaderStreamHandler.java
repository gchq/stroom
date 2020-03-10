package stroom.receive.common;

import stroom.meta.api.AttributeMap;

import java.io.IOException;

public interface StroomHeaderStreamHandler {
    void handleHeader(AttributeMap attributeMap) throws IOException;

}
