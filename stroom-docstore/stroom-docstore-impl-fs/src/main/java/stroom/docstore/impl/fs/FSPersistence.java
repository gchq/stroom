package stroom.docstore.impl.fs;

import stroom.docref.DocRef;
import stroom.docstore.api.RWLockFactory;
import stroom.docstore.impl.Persistence;
import stroom.docstore.shared.AbstractDoc;
import stroom.util.io.PathCreator;
import stroom.util.json.JsonUtil;
import stroom.util.shared.Clearable;
import stroom.util.string.EncodingUtil;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
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
import java.util.Optional;

@Singleton
public class FSPersistence implements Persistence, Clearable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FSPersistence.class);

    private static final String META = "meta";

    private final RWLockFactory lockFactory = new StripedLockFactory();
    private final Path dir;
    private final ObjectMapper objectMapper;

    @SuppressWarnings("unused")
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

        objectMapper = JsonUtil.getNoIndentMapper();
    }


    @Override
    public boolean exists(final DocRef docRef) {
        final Path filePath = getPath(docRef, META);
        return Files.isRegularFile(filePath);
    }

    @Override
    public Map<String, byte[]> read(final DocRef docRef) throws IOException {
        final Map<String, byte[]> data = new HashMap<>();
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(getPathForType(docRef.getType()),
                docRef.getUuid() + ".*")) {
            stream.forEach(file -> {
                try {
                    final String fileName = file.getFileName().toString();
                    final int index = fileName.indexOf(".");
//                    final String uuid = fileName.substring(0, index);
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

        if (data.size() == 0) {
            return null;
        }

        return data;
    }

    @Override
    public void write(final DocRef docRef, final boolean update, final Map<String, byte[]> data) {
        final Path filePath = getPath(docRef, META);
        if (update) {
            if (!Files.isRegularFile(filePath)) {
                throw new RuntimeException("Document does not exist with uuid=" + docRef.getUuid());
            }
        } else if (Files.isRegularFile(filePath)) {
            throw new RuntimeException("Document already exists with uuid=" + docRef.getUuid());
        }

        data.forEach((ext, bytes) -> {
            try {
                Files.write(getPath(docRef, ext), bytes);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
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
            stream.forEach(file -> {
                final String fileName = file.getFileName().toString();
                final int index = fileName.indexOf(".");
                final String uuid = fileName.substring(0, index);
                final Optional<String> name = getName(file);
                list.add(new DocRef(type, uuid, name.orElse(null)));
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
            final FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
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

    private Optional<String> getName(final Path metaFile) {
        try {
            final byte[] data = Files.readAllBytes(metaFile);
            final GenericDoc genericDoc = objectMapper.readValue(new StringReader(EncodingUtil.asString(data)),
                    GenericDoc.class);
            return Optional.ofNullable(genericDoc.getName());

        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return Optional.empty();
    }

    private static class GenericDoc extends AbstractDoc {

        public GenericDoc(@JsonProperty("uuid") final String uuid,
                          @JsonProperty("name") final String name,
                          @JsonProperty("version") final String version,
                          @JsonProperty("createTimeMs") final Long createTimeMs,
                          @JsonProperty("updateTimeMs") final Long updateTimeMs,
                          @JsonProperty("createUser") final String createUser,
                          @JsonProperty("updateUser") final String updateUser) {
            super("GenericDoc", uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        }
    }
}
