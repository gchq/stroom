package stroom.proxy.app.handler;

import java.nio.file.Path;

public interface ForwardFileDestination extends ForwardDestination {

    Path getStoreDir();
}
