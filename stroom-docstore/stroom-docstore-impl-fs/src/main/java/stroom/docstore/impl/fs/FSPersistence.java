package stroom.docstore.impl.fs;

import stroom.docref.DocRef;
import stroom.docstore.api.RWLockFactory;
import stroom.docstore.impl.DocumentData;
import stroom.docstore.impl.Persistence;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.UniqueNameUtil;
import stroom.util.NullSafe;
import stroom.util.io.PathCreator;
import stroom.util.json.JsonUtil;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Singleton
public class FSPersistence implements Persistence, Clearable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FSPersistence.class);

    private static final String META = "meta";

    private final RWLockFactory lockFactory = new StripedLockFactory();
    private final Path dir;

    @Inject
    public FSPersistence(final FSPersistenceConfig config, final PathCreator pathCreator) {
        this(pathCreator.toAppPath(config.getPath()));
    }

    public FSPersistence(final Path absoluteDir) {
        try {
            this.dir = absoluteDir;
            LOGGER.debug("Using path {}", absoluteDir);
            Files.createDirectories(absoluteDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean exists(final DocRef docRef) {
        final Path filePath = getPath(docRef, META);
        return Files.isRegularFile(filePath);
    }

    @Override
    public DocumentData create(final DocumentData documentData) throws IOException {
        validate(documentData);

        final Path filePath = getPath(documentData.getDocRef(), META);
        if (Files.isRegularFile(filePath)) {
            throw new RuntimeException("Document already exists: " + documentData);
        }

        documentData.getData().forEach((ext, bytes) -> {
            try {
                Files.write(getPath(documentData.getDocRef(), ext), bytes);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        return documentData;
    }

    @Override
    public Optional<DocumentData> read(final DocRef docRef) throws IOException {
        final Path metaPath = getPath(docRef, META);
        final Optional<AbstractDoc> optional = getDoc(metaPath);
        return optional.map(doc -> {
            final Map<String, byte[]> data = new HashMap<>();
            try (final DirectoryStream<Path> stream = Files.newDirectoryStream(
                    getPathForType(docRef.getType()),
                    docRef.getUuid() + ".*")) {
                stream.forEach(file -> {
                    try {
                        final String fileName = file.getFileName().toString();
                        final int index = fileName.indexOf(".");
                        final String ext = fileName.substring(index + 1);

                        final byte[] bytes = Files.readAllBytes(file);
                        data.put(ext, bytes);

                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }

            return DocumentData
                    .builder()
                    .docRef(docRef)
                    .uniqueName(doc.getUniqueName() != null
                            ? doc.getUniqueName()
                            : UniqueNameUtil.createDefault(docRef))
                    .version(doc.getVersion())
                    .data(data)
                    .build();
        });
    }

    @Override
    public DocumentData update(final String expectedVersion, final DocumentData documentData) throws IOException {
        Objects.requireNonNull(expectedVersion, "Expected version is null");
        validate(documentData);

        // Read existing data.
        final Path metaPath = getPath(documentData.getDocRef(), META);
        final Optional<AbstractDoc> optional = getDoc(metaPath);
        final AbstractDoc doc = optional.orElseThrow(() ->
                new RuntimeException("Document does not exist: " + documentData));

        // Check version.
        if (!expectedVersion.equals(doc.getVersion())) {
            throw new RuntimeException("Unable to update document due to version mismatch: " + documentData);
        }

        documentData.getData().forEach((ext, bytes) -> {
            try {
                Files.write(getPath(documentData.getDocRef(), ext), bytes);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        return documentData;
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
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(getPathForType(docRef.getType()),
                docRef.getUuid() + ".*")) {
            stream.forEach(file -> {
                try {
                    Files.delete(file);
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<DocRef> list(final String type) {
        final List<DocRef> list = new ArrayList<>();
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(getPathForType(type), "*." + META)) {
            stream.forEach(metaPath -> {
                final Optional<AbstractDoc> optional = getDoc(metaPath);
                optional.ifPresent(doc -> {


//                    final String fileName = file.getFileName().toString();
//                    final int index = fileName.indexOf(".");
//                    final String uuid = fileName.substring(0, index);
//
//                    final Optional<String> name = getName(file);
//                    list.add(new DocRef(type, uuid, name.orElse(null)));

                    list.add(new DocRef(doc.getType(), doc.getUuid(), doc.getName()));

                });
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return list;
    }

    @Override
    public RWLockFactory getLockFactory() {
        return lockFactory;
    }

    private Path getPath(final DocRef docRef, final String ext) {
        return getPathForType(docRef.getType()).resolve(docRef.getUuid() + "." + ext);
    }

    private Path getPathForType(final String type) {
        final Path path = dir.resolve(type);
        try {
            if (!Files.isDirectory(path)) {
                Files.createDirectories(path);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return path;
    }

    @Override
    public void clear() {
        recursiveDelete(dir);
    }

    private void recursiveDelete(final Path path) {
        try {
            FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (!dir.equals(path)) {
                        Files.delete(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            };
            Files.walkFileTree(path, visitor);
        } catch (final NotDirectoryException e) {
            // Ignore.
        } catch (final IOException e) {
            LOGGER.debug(e.getMessage(), e);
        }
    }

    private Optional<AbstractDoc> getDoc(final Path metaFile) {
        try {
            final GenericDoc doc = JsonUtil.readValue(metaFile, GenericDoc.class);
            return Optional.ofNullable(doc);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return Optional.empty();
    }

    private static class GenericDoc extends AbstractDoc {

        @Override
        public String getType() {
            return "GenericDoc";
        }
    }
}
