package stroom.proxy.app.handler;

import java.nio.file.Path;

public interface ForwardFileDestination extends ForwardDestination {

    Path getStoreDir();

    @Override
    default DestinationType getDestinationType() {
        return DestinationType.FILE;
    }
}
