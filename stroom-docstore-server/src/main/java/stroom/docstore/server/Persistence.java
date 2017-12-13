package stroom.docstore.server;

import stroom.query.api.v2.DocRef;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public interface Persistence {
    boolean exists(DocRef docRef);

    void delete(DocRef docRef);

    InputStream getInputStream(DocRef docRef);

    OutputStream getOutputStream(DocRef docRef, boolean update);

    List<DocRef> list(String type);

    RWLockFactory getLockFactory();
}