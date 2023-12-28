package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;

import java.io.IOException;
import java.io.InputStream;

public interface StreamDestination {

    void send(AttributeMap attributeMap,
              InputStream inputStream) throws IOException;

}
