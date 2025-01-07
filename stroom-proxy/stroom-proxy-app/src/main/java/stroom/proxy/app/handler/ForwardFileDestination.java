package stroom.proxy.app.handler;

import java.nio.file.Path;

public interface ForwardFileDestination {

    void add(Path sourceDir);
}
