package stroom.proxy.app.handler;

import java.io.IOException;
import java.io.InputStream;

public interface InputStreamSupplier {
    InputStream get() throws IOException;
}
