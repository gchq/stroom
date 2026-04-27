/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Local/shared filesystem implementation of {@link FileStore}.
 * <p>
 * Producers write to an isolated temporary directory. Calling
 * {@link FileStoreWrite#commit()} moves that directory into the stable store area and
 * returns a {@link FileStoreLocation} that can be published to a queue.
 * </p>
 */
public class LocalFileStore implements FileStore {

    private static final String TEMP_DIR_NAME = ".writing";
    private static final int ID_WIDTH = 10;

    private final String name;
    private final Path root;
    private final Path writerRoot;
    private final Path tempRoot;
    private final AtomicLong sequence = new AtomicLong();

    public LocalFileStore(final String name,
                          final Path root) {
        this(name, root, UUID.randomUUID().toString());
    }

    public LocalFileStore(final String name,
                          final Path root,
                          final String writerId) {
        this.name = requireNonBlank(name, "name");
        this.root = Objects.requireNonNull(root, "root")
                .toAbsolutePath()
                .normalize();
        final String safeWriterId = requireNonBlank(writerId, "writerId");
        this.writerRoot = this.root.resolve(safeWriterId);
        this.tempRoot = this.root.resolve(TEMP_DIR_NAME).resolve(safeWriterId);

        try {
            Files.createDirectories(this.writerRoot);
            Files.createDirectories(this.tempRoot);
            sequence.set(findMaxDirectChildId(this.writerRoot));
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to initialise local file store " + name + " at " + this.root, e);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    public Path getRoot() {
        return root;
    }

    @Override
    public FileStoreWrite newWrite() throws IOException {
        Files.createDirectories(tempRoot);
        final Path tempPath = Files.createTempDirectory(tempRoot, "write-");
        return new LocalFileStoreWrite(tempPath);
    }

    @Override
    public Path resolve(final FileStoreLocation location) throws IOException {
        Objects.requireNonNull(location, "location");

        if (!name.equals(location.storeName())) {
            throw new IOException("File store location is for store '" + location.storeName()
                                  + "' but this store is '" + name + "'");
        }
        if (!location.isLocalFileSystem()) {
            throw new IOException("Unsupported file store location type: " + location.locationType());
        }

        final Path path = Path.of(URI.create(location.uri()))
                .toAbsolutePath()
                .normalize();

        if (!path.startsWith(root)) {
            throw new IOException("File store location '" + path + "' is outside store root '" + root + "'");
        }

        return path;
    }

    private Path nextStablePath() throws IOException {
        while (true) {
            final long id = sequence.incrementAndGet();
            final Path path = writerRoot.resolve(formatId(id));

            if (!Files.exists(path)) {
                return path;
            }
        }
    }

    private static String formatId(final long id) {
        final String value = Long.toString(id);
        if (value.length() >= ID_WIDTH) {
            return value;
        }
        return "0".repeat(ID_WIDTH - value.length()) + value;
    }

    private static long findMaxDirectChildId(final Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return 0;
        }

        try (final Stream<Path> stream = Files.list(path)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .mapToLong(LocalFileStore::parseId)
                    .max()
                    .orElse(0);
        }
    }

    private static long parseId(final String value) {
        try {
            return OptionalLong.of(Long.parseLong(value))
                    .orElse(0);
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    private static String requireNonBlank(final String value,
                                          final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static void deleteRecursively(final Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }

        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file,
                                             final BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir,
                                                      final IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private final class LocalFileStoreWrite implements FileStoreWrite {

        private final Path tempPath;
        private boolean complete;
        private Path stablePath;

        private LocalFileStoreWrite(final Path tempPath) {
            this.tempPath = Objects.requireNonNull(tempPath, "tempPath");
        }

        @Override
        public Path getPath() {
            return tempPath;
        }

        @Override
        public FileStoreLocation commit() throws IOException {
            if (complete) {
                return FileStoreLocation.localFileSystem(name, stablePath);
            }

            stablePath = nextStablePath();
            Files.createDirectories(stablePath.getParent());

            try {
                Files.move(
                        tempPath,
                        stablePath,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (final AtomicMoveNotSupportedException e) {
                Files.move(tempPath, stablePath);
            }

            complete = true;
            return FileStoreLocation.localFileSystem(name, stablePath);
        }

        @Override
        public boolean isCommitted() {
            return complete;
        }

        @Override
        public void close() throws IOException {
            if (!complete) {
                deleteRecursively(tempPath);
            }
        }
    }
}
