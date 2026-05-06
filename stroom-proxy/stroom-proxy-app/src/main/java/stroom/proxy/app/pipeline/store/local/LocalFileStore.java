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

package stroom.proxy.app.pipeline.store.local;

import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;

import com.codahale.metrics.health.HealthCheck;

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

    private static final String TEMP_DIR_NAME = "writing";

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
    public HealthCheck.Result healthCheck() {
        final boolean rootOk = Files.isDirectory(root) && Files.isWritable(root);
        final boolean writerOk = Files.isDirectory(writerRoot) && Files.isWritable(writerRoot);

        if (!rootOk || !writerOk) {
            return HealthCheck.Result.builder()
                    .unhealthy()
                    .withMessage("Directory check failed: root=%s, writerRoot=%s", rootOk, writerOk)
                    .withDetail("root", root.toString())
                    .withDetail("writerRoot", writerRoot.toString())
                    .build();
        }

        return HealthCheck.Result.builder()
                .healthy()
                .withDetail("root", root.toString())
                .withDetail("writable", true)
                .build();
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

    @Override
    public void delete(final FileStoreLocation location) throws IOException {
        // resolve() validates store name, location type, and that the
        // path is inside the store root.
        final Path path = resolve(location);

        // Defensive guard: never delete the store root or writer root.
        if (path.equals(root) || path.equals(writerRoot)) {
            throw new IOException("Refusing to delete store-level directory: " + path);
        }

        // Idempotent: treat already-deleted (or never-existed) as success.
        if (!Files.exists(path)) {
            return;
        }

        deleteRecursively(path);
    }



    @Override
    public FileStoreWrite newDeterministicWrite(final String fileGroupId) throws IOException {
        Objects.requireNonNull(fileGroupId, "fileGroupId");
        if (fileGroupId.isBlank()) {
            throw new IllegalArgumentException("fileGroupId must not be blank");
        }

        final Path stablePath = writerRoot.resolve(fileGroupId);

        // If the output already exists, return a pre-committed write.
        if (Files.isDirectory(stablePath)) {
            return new PreCommittedFileStoreWrite(stablePath);
        }

        // If something exists at the path but is not a directory, clean it up.
        if (Files.exists(stablePath)) {
            deleteRecursively(stablePath);
        }

        // Create the temp write directory.
        Files.createDirectories(tempRoot);
        final Path tempPath = Files.createTempDirectory(tempRoot, "write-");
        return new DeterministicFileStoreWrite(tempPath, stablePath);
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

    /**
     * A write handle targeting a deterministic (content-addressed) path.
     * <p>
     * Unlike {@link LocalFileStoreWrite} which resolves its stable path at
     * commit time via the monotonic sequence, this write targets a
     * pre-determined stable path derived from a file-group ID.
     * </p>
     */
    private final class DeterministicFileStoreWrite implements FileStoreWrite {

        private final Path tempPath;
        private final Path stablePath;
        private boolean complete;

        private DeterministicFileStoreWrite(final Path tempPath, final Path stablePath) {
            this.tempPath = Objects.requireNonNull(tempPath, "tempPath");
            this.stablePath = Objects.requireNonNull(stablePath, "stablePath");
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

            Files.createDirectories(stablePath.getParent());

            try {
                Files.move(tempPath, stablePath, StandardCopyOption.ATOMIC_MOVE);
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

    /**
     * A pre-committed write handle returned when a deterministic write
     * target already exists and is complete. This is a no-op handle that
     * simply returns the existing stable location.
     */
    private final class PreCommittedFileStoreWrite implements FileStoreWrite {

        private final Path stablePath;

        private PreCommittedFileStoreWrite(final Path stablePath) {
            this.stablePath = Objects.requireNonNull(stablePath, "stablePath");
        }

        @Override
        public Path getPath() {
            return stablePath;
        }

        @Override
        public FileStoreLocation commit() {
            return FileStoreLocation.localFileSystem(name, stablePath);
        }

        @Override
        public boolean isCommitted() {
            return true;
        }

        @Override
        public void close() {
            // Nothing to clean up — the data is already committed.
        }
    }
}
