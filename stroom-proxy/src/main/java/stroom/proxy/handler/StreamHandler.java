package stroom.proxy.handler;

import stroom.feed.AttributeMap;
import stroom.proxy.repo.StroomStreamHandler;

import java.io.IOException;

public interface StreamHandler extends StroomStreamHandler {
    void setAttributeMap(AttributeMap attributeMap);

    void handleHeader() throws IOException;

    void handleFooter() throws IOException;

    void handleError() throws IOException;

    void validate();
}
