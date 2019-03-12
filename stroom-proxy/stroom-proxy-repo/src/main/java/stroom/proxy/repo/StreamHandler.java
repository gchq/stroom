package stroom.proxy.repo;

import stroom.meta.shared.AttributeMap;
import stroom.receive.common.StroomStreamHandler;

import java.io.IOException;

public interface StreamHandler extends StroomStreamHandler {
    void setAttributeMap(AttributeMap attributeMap);

    void handleHeader() throws IOException;

    void handleFooter() throws IOException;

    void handleError() throws IOException;

    void validate();
}
