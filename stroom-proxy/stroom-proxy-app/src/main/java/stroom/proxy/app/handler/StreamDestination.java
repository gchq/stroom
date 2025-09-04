package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;

import java.io.InputStream;

public interface StreamDestination {

    void send(AttributeMap attributeMap,
              InputStream inputStream) throws ForwardException;

    boolean performLivenessCheck() throws Exception;

    boolean hasLivenessCheck();
}
