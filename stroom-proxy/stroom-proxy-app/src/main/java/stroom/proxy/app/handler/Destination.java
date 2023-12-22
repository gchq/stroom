package stroom.proxy.app.handler;

import java.io.IOException;
import java.nio.file.Path;

public interface Destination {


    void add(Path sourceDir) throws IOException;
}
