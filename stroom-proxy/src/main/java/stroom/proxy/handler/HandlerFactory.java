package stroom.proxy.handler;

import java.util.List;

public interface HandlerFactory {
    List<RequestHandler> create();
}
