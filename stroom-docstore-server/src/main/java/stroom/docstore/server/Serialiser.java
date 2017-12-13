package stroom.docstore.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface Serialiser<D> {
    D read(InputStream inputStream, Class<D> clazz) throws IOException;

    void write(OutputStream outputStream, D document) throws IOException;
}
