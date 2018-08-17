package stroom.proxy.repo;

import stroom.data.meta.api.AttributeMap;

import java.io.IOException;

public interface StroomHeaderStreamHandler {
    void handleHeader(AttributeMap attributeMap) throws IOException;

}
