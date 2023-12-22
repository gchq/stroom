package stroom.proxy.app.handler;

import java.io.IOException;
import java.nio.file.Path;

public interface DirQueue extends Destination {
    void add(Path sourceDir) throws IOException;

    SequentialDir next();
}
