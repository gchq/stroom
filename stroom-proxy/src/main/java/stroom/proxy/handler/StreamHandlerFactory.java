package stroom.proxy.handler;

import java.util.List;

public interface StreamHandlerFactory {
    List<StreamHandler> addReceiveHandlers(final List<StreamHandler> handlers);

    List<StreamHandler> addSendHandlers(final List<StreamHandler> handlers);
}
