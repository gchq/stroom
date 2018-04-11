package stroom.docstore.memory;

import stroom.docstore.Persistence;
import stroom.docstore.RWLockFactory;
import stroom.query.api.v2.DocRef;

import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class MemoryPersistence implements Persistence {
    private static final RWLockFactory LOCK_FACTORY = new NoLockFactory();

    private final Map<DocRef, byte[]> data = new ConcurrentHashMap<>();

    @Override
    public boolean exists(final DocRef docRef) {
        return data.containsKey(docRef);
    }

    @Override
    public InputStream getInputStream(final DocRef docRef) {
        final byte[] bytes = data.get(docRef);
        if (bytes == null) {
            return null;
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public OutputStream getOutputStream(final DocRef docRef, final boolean update) {
        if (update) {
            if (!data.containsKey(docRef)) {
                throw new RuntimeException("Document does not exist with uuid=" + docRef.getUuid());
            }
        } else if (data.containsKey(docRef)) {
            throw new RuntimeException("Document already exists with uuid=" + docRef.getUuid());
        }
        return new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                super.close();
                data.put(docRef, this.toByteArray());
            }
        };
    }

    @Override
    public void delete(final DocRef docRef) {
        data.remove(docRef);
    }

    @Override
    public List<DocRef> list(final String type) {
        return data.keySet()
                .stream()
                .filter(docRef -> docRef.getType().equals(type))
                .collect(Collectors.toList());
    }

    @Override
    public RWLockFactory getLockFactory() {
        return LOCK_FACTORY;
    }
}