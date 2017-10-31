package stroom.proxy.handler;

import java.util.List;

public interface StreamHandlerFactory {
    List<StreamHandler> create();
}
