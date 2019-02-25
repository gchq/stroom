package stroom.docstore.api;

import stroom.docref.DocRef;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface Persistence {
    boolean exists(DocRef docRef);

    void delete(DocRef docRef);

    Map<String, byte[]> read(DocRef docRef) throws IOException;

    void write(DocRef docRef, boolean update, Map<String, byte[]> data) throws IOException;

    List<DocRef> list(String type);

    RWLockFactory getLockFactory();
}