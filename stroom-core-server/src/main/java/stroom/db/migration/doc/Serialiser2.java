package stroom.db.migration.doc;

import java.io.IOException;
import java.util.Map;

public interface Serialiser2<D> {
    D read(Map<String, byte[]> data) throws IOException;

    Map<String, byte[]> write(D document) throws IOException;
}
