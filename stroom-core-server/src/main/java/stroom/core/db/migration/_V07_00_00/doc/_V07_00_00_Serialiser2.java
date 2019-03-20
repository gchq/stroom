package stroom.core.db.migration._V07_00_00.doc;

import java.io.IOException;
import java.util.Map;

public interface _V07_00_00_Serialiser2<D> {
    D read(Map<String, byte[]> data) throws IOException;

    Map<String, byte[]> write(D document) throws IOException;
}
