package stroom.core.db.migration._V07_00_00.docstore.shared;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface _V07_00_00_Serialiser<D> {
    D read(InputStream inputStream, Class<D> clazz) throws IOException;

    void write(OutputStream outputStream, D document) throws IOException;
}
