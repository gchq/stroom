package stroom.docstore.impl.memory;

import stroom.docref.DocRef;
import stroom.docstore.api.RWLockFactory;
import stroom.docstore.impl.DocumentData;
import stroom.docstore.impl.Persistence;
import stroom.util.NullSafe;
import stroom.util.shared.Clearable;

import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class MemoryPersistence implements Persistence, Clearable {

    private static final RWLockFactory LOCK_FACTORY = new NoLockFactory();

    private final Map<DocRef, DocumentData> map = new ConcurrentHashMap<>();

    @Override
    public boolean exists(final DocRef docRef) {
        return map.containsKey(docRef);
    }

    @Override
    public DocumentData create(final DocumentData documentData) throws IOException {
        validate(documentData);
        return map.compute(documentData.getDocRef(), (k, v) -> {
            if (v != null) {
                throw new RuntimeException("Document already exists: " + documentData);
            }
            return documentData;
        });
    }

    @Override
    public Optional<DocumentData> read(final DocRef docRef) throws IOException {
        return Optional.ofNullable(map.get(docRef));
    }

    @Override
    public DocumentData update(final String expectedVersion, final DocumentData documentData) throws IOException {
        Objects.requireNonNull(expectedVersion, "Expected version is null");
        validate(documentData);

        return map.compute(documentData.getDocRef(), (k, v) -> {
            if (v == null) {
                throw new RuntimeException("Document does not exist: " + documentData);
            }
            if (!v.getVersion().equals(expectedVersion)) {
                throw new RuntimeException("Unexpected version: " + documentData);
            }
            return documentData;
        });
    }

    private void validate(final DocumentData documentData) {
        Objects.requireNonNull(documentData, "Document data is null");
        Objects.requireNonNull(documentData.getDocRef(), "DocRef is null: " + documentData);
        NullSafe.requireNonBlank(documentData.getDocRef().getType(), () ->
                "Type not set on document: " + documentData);
        NullSafe.requireNonBlank(documentData.getDocRef().getUuid(), () ->
                "UUID not set on document: " + documentData);
        NullSafe.requireNonBlank(documentData.getDocRef().getName(), () ->
                "Name not set on document: " + documentData);
        NullSafe.requireNonBlank(documentData.getVersion(), () ->
                "Version not set on document: " + documentData);
        NullSafe.requireNonBlank(documentData.getUniqueName(), () ->
                "Unique name not set on document: " + documentData);
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

    @Override
    public void clear() {
        map.clear();
    }
}
