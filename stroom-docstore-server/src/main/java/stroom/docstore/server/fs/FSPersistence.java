package stroom.docstore.server.fs;

import stroom.docstore.server.Persistence;
import stroom.docstore.server.RWLockFactory;
import stroom.node.server.StroomPropertyService;
import stroom.query.api.v2.DocRef;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;


public class FSPersistence implements Persistence {
    private static final String FILE_EXTENSION = ".json";

    private final RWLockFactory lockFactory = new StripedLockFactory();
    private final Path dir;

    @Inject
    FSPersistence(final StroomPropertyService stroomPropertyService) {
        try {
            final String path = stroomPropertyService.getProperty("stroom.config.dir");
            this.dir = Paths.get(path);
            Files.createDirectories(dir);
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public FSPersistence(final Path dir) {
        try {
            this.dir = dir;
            Files.createDirectories(dir);
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(final DocRef docRef) {
        final Path filePath = getPath(docRef);
        return Files.isRegularFile(filePath);
    }

    @Override
    public InputStream getInputStream(final DocRef docRef) {
        try {
            final Path path = getPath(docRef);
            return Files.newInputStream(path);
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public OutputStream getOutputStream(final DocRef docRef, final boolean update) {
        try {
            final Path filePath = getPath(docRef);
            if (update) {
                if (!Files.isRegularFile(filePath)) {
                    throw new RuntimeException("Document does not exist with uuid=" + docRef.getUuid());
                }
            } else if (Files.isRegularFile(filePath)) {
                throw new RuntimeException("Document already exists with uuid=" + docRef.getUuid());
            }
            return Files.newOutputStream(filePath);
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public void delete(final DocRef docRef) {
        try {
            final Path path = getPath(docRef);
            Files.delete(path);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Set<DocRef> list(final String type) {
        final Set<DocRef> set = Collections.newSetFromMap(new ConcurrentHashMap<>());
        try (final Stream<Path> stream = Files.list(getPathForType(type))) {
            stream.filter(p -> p.toString().endsWith(FILE_EXTENSION)).parallel().forEach(p -> {
                final String fileName = p.getFileName().toString();
                final int index = fileName.indexOf(".");
                final String uuid = fileName.substring(0, index);
                set.add(new DocRef(type, uuid));
            });
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return set;
    }

    @Override
    public RWLockFactory getLockFactory() {
        return lockFactory;
    }

    private Path getPath(final DocRef docRef) {
        return getPathForType(docRef.getType()).resolve(docRef.getUuid() + FILE_EXTENSION);
    }

    private Path getPathForType(final String type) {
        final Path path = dir.resolve(type);
        try {
            if (!Files.isDirectory(path)) {
                Files.createDirectories(path);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return path;
    }
}