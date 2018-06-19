package stroom.proxy.repo;

import stroom.feed.AttributeMap;

import java.io.IOException;

public interface StroomHeaderStreamHandler {
    void handleHeader(AttributeMap attributeMap) throws IOException;

}
