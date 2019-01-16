package stroom.docstore;

import java.io.IOException;
import java.io.Writer;

public interface Serialiser2<D> extends DocumentSerialiser2<D> {
//    D read(Map<String, byte[]> data) throws IOException;

    D read(byte[] data) throws IOException;

    void write(final Writer writer, D document) throws IOException;

//    Map<String, byte[]> write(D document) throws IOException;
}
