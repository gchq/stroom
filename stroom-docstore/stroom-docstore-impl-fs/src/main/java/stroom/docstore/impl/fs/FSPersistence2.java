package stroom.docstore.impl.fs;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.RWLockFactory;
import stroom.docstore.impl.DocumentData;
import stroom.docstore.impl.Persistence;
import stroom.util.NullSafe;
import stroom.util.io.FileUtil;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class FSPersistence2 implements Persistence, Clearable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FSPersistence2.class);

    private static final String MANIFEST_FILE_NAME = "manifest.json";

    private final RWLockFactory lockFactory = new StripedLockFactory();
    private final Path dir;

    @Inject
    public FSPersistence2(final FSPersistenceConfig config, final PathCreator pathCreator) {
        this(pathCreator.toAppPath(config.getPath()));
    }

    public FSPersistence2(final Path absoluteDir) {
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
        final Path parentPath = getPath(docRef);
        return Files.exists(parentPath);
    }

    @Override
    public DocumentData create(final DocumentData documentData) throws IOException {
        validate(documentData);

        final Path parentPath = getPath(documentData.getDocRef());
        if (Files.exists(parentPath)) {
            throw new RuntimeException("Document already exists: " + documentData);
        }

        return write(documentData);
    }

    @Override
    public Optional<DocumentData> read(final DocRef docRef) throws IOException {
        final Path parentPath = getPath(docRef);
        if (!Files.exists(parentPath)) {
            throw new RuntimeException("Document not found: " + docRef);
        }

        // Read manifest.
        final Manifest manifest = readManifest(parentPath);

        final Map<String, byte[]> data = manifest
                .getEntries()
                .stream()
                .collect(Collectors.toMap(
                        entry -> entry,
                        entry -> {
                            try {
                                final Path path = parentPath.resolve(entry);
                                return Files.readAllBytes(path);
                            } catch (final IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }));

        return Optional.of(DocumentData
                .builder()
                .docRef(manifest.getDocRef())
                .uniqueName(manifest.getUniqueName())
                .version(manifest.getVersion())
                .data(data)
                .build());
    }

    @Override
    public DocumentData update(final String expectedVersion, final DocumentData documentData) throws IOException {
        Objects.requireNonNull(expectedVersion, "Expected version is null");
        validate(documentData);

        final Path parentPath = getPath(documentData.getDocRef());
        if (!Files.exists(parentPath)) {
            throw new DocumentNotFoundException(documentData.getDocRef());
        }

        // Read existing manifest.
        final Manifest existingManifest = readManifest(parentPath);

        // Check version.
        if (!expectedVersion.equals(existingManifest.getVersion())) {
            throw new RuntimeException("Unable to update document due to version mismatch: " + documentData);
        }

        return write(documentData);
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

    public DocumentData write(final DocumentData documentData) throws IOException {
        final Path parentPath = getPath(documentData.getDocRef());

        // Create parent dirs.
        try {
            if (!Files.isDirectory(parentPath)) {
                Files.createDirectories(parentPath);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        // Delete all existing files.
        FileUtil.deleteContents(parentPath);

        // Write manifest.
        final Manifest manifest = new Manifest(
                documentData.getDocRef(),
                documentData.getVersion(),
                documentData.getUniqueName(),
                documentData.getEntries());
        writeManifest(parentPath, manifest);

        // Write data.
        documentData.getData().forEach((entry, bytes) -> {
            try {
                final Path path = parentPath.resolve(entry);
                Files.write(path, bytes);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        return documentData;
    }

    private Manifest readManifest(final Path parentPath) {
        final Path manifestPath = parentPath.resolve(MANIFEST_FILE_NAME);
        return JsonUtil.readValue(manifestPath, Manifest.class);
    }

    private void writeManifest(final Path parentPath,
                               final Manifest manifest) {
        final Path manifestPath = parentPath.resolve(MANIFEST_FILE_NAME);
        JsonUtil.writeValue(manifestPath, manifest);
    }

    @Override
    public void delete(final DocRef docRef) {
        final Path parentPath = getPath(docRef);
        FileUtil.deleteDir(parentPath);
    }

    @Override
    public List<DocRef> list(final String type) {
        final List<DocRef> list = new ArrayList<>();
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(getPathForType(type))) {
            stream.forEach(dir -> {
                final Manifest manifest = readManifest(dir);
                list.add(manifest.getDocRef());
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

    private Path getPath(final DocRef docRef) {
        return getPathForType(docRef.getType()).resolve(docRef.getUuid());
    }

    private Path getPathForType(final String type) {
        return dir.resolve(type);
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
}
