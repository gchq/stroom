package stroom.proxy.handler;

import stroom.proxy.repo.StroomStreamHandler;

import java.io.IOException;

public interface RequestHandler extends StroomStreamHandler {
    void validate();

    void handleHeader() throws IOException;

    void handleFooter() throws IOException;

    void handleError() throws IOException;
}
