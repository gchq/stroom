package stroom.docstore.fs;

import stroom.docstore.Persistence;
import stroom.docstore.RWLockFactory;
import stroom.query.api.v2.DocRef;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class FSPersistence implements Persistence {
    private static final String META = "meta";

    private final RWLockFactory lockFactory = new StripedLockFactory();
    private final Path dir;

    @Inject
    FSPersistence(final FSPersistenceConfig config) {
        try {
            final String path = config.getPath();
            this.dir = Paths.get(path);
            Files.createDirectories(dir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public FSPersistence(final Path dir) {
        try {
            this.dir = dir;
            Files.createDirectories(dir);
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
    public Map<String, byte[]> read(final DocRef docRef) throws IOException {
        final Map<String, byte[]> data = new HashMap<>();
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(getPathForType(docRef.getType()), docRef.getUuid() + ".*")) {
            stream.forEach(file -> {
                try {
                    final String fileName = file.getFileName().toString();
                    final int index = fileName.indexOf(".");
//                    final String uuid = fileName.substring(0, index);
                    final String extension = fileName.substring(index + 1);

                    final byte[] bytes = Files.readAllBytes(file);
                    data.put(extension, bytes);

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
    public void write(final DocRef docRef, final boolean update, final Map<String, byte[]> data) throws IOException {
        final Path filePath = getPath(docRef, META);
        if (update) {
            if (!Files.isRegularFile(filePath)) {
                throw new RuntimeException("Document does not exist with uuid=" + docRef.getUuid());
            }
        } else if (Files.isRegularFile(filePath)) {
            throw new RuntimeException("Document already exists with uuid=" + docRef.getUuid());
        }

        data.forEach((extension, bytes) -> {
            try {
                Files.write(getPath(docRef, extension), bytes);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public void delete(final DocRef docRef) {
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(getPathForType(docRef.getType()), docRef.getUuid() + ".*")) {
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
                list.add(new DocRef(type, uuid));
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

    private Path getPath(final DocRef docRef, final String extension) {
        return getPathForType(docRef.getType()).resolve(docRef.getUuid() + "." + extension);
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
}