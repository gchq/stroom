package stroom.docstore.memory;

import stroom.docstore.Persistence;
import stroom.docstore.RWLockFactory;
import stroom.query.api.v2.DocRef;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class MemoryPersistence implements Persistence {
    private static final RWLockFactory LOCK_FACTORY = new NoLockFactory();

    private final Map<DocRef, Map<String, byte[]>> map = new ConcurrentHashMap<>();

    @Override
    public boolean exists(final DocRef docRef) {
        return map.containsKey(docRef);
    }

    @Override
    public Map<String, byte[]> read(final DocRef docRef) throws IOException {
        return map.get(docRef);
    }

    @Override
    public void write(final DocRef docRef, final boolean update, final Map<String, byte[]> data) throws IOException {
        if (update) {
            if (!map.containsKey(docRef)) {
                throw new RuntimeException("Document does not exist with uuid=" + docRef.getUuid());
            }
        } else if (map.containsKey(docRef)) {
            throw new RuntimeException("Document already exists with uuid=" + docRef.getUuid());
        }

        map.put(docRef, data);
    }

    @Override
    public void delete(final DocRef docRef) {
        map.remove(docRef);
    }

    @Override
    public List<DocRef> list(final String type) {
        return map.keySet()
                .stream()
                .filter(docRef -> docRef.getType().equals(type))
                .collect(Collectors.toList());
    }

    @Override
    public RWLockFactory getLockFactory() {
        return LOCK_FACTORY;
    }

    public void clear() {
        map.clear();
    }
}