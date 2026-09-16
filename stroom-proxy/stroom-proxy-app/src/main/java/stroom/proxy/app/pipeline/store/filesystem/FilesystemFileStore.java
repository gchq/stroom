/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.store.filesystem;

import stroom.proxy.app.handler.DirUtil;
import stroom.proxy.app.handler.Durability;
import stroom.proxy.app.pipeline.store.FileGroupNotFoundException;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreType;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.util.io.FileUtil;
import stroom.util.string.StringIdUtil;

import com.codahale.metrics.health.HealthCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link FileStore} on a filesystem: this node's own disk in local mode, or a mount every node
 * sees in shared mode. One class, because the storage is the same; what differs is who else is
 * writing, and that is decided by the writer root.
 * <p>
 * File groups are numbered sequentially and laid out by {@link DirUtil#createPath(Path, long)} so
 * that no directory holds more than 1000 entries:
 * </p>
 * <pre>
 * {@code <writerRoot>/0/001/                  <- id 1
 * <writerRoot>/1/001/001000/           <- id 1,000
 * <writerRoot>/.staging/001001/        <- a write in progress}
 * </pre>
 * <p>
 * In local mode the writer root is the store root itself, one numbered tree that survives restarts,
 * and the counter is re-established from the highest id present. In shared mode the writer root is
 * {@code <root>/<startId>/}, a fresh UUID per process start, so two nodes never share a tree and a
 * node restarting never reuses one; its counter starts at zero with nothing to scan.
 * </p>
 * <p>
 * Presence of a directory at a group's path is completeness: the only way one appears there is the
 * atomic rename in {@link FileStoreWrite#commit()}, from staging under the same writer root, so it
 * is always within one filesystem.
 * </p>
 */
public class FilesystemFileStore implements FileStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilesystemFileStore.class);

    static final String STAGING_DIR_NAME = ".staging";

    /**
     * How many times a commit will recreate the intermediate directories after a cascade-delete
     * removed them between the create and the rename. The race fires at most once per contended
     * intermediate, so this is generous.
     */
    private static final int COMMIT_ATTEMPTS = 10;

    private final String name;
    private final Path root;
    private final FileStoreType type;
    private final Durability durability;
    private final Duration orphanAge;
    private final Path writerRoot;
    private final Path stagingRoot;
    private final AtomicLong sequence = new AtomicLong();
    /**
     * What {@link #sweep()} has deleted, for a gauge. Groups are the one that matters: a sweep
     * cannot tell an orphan from live work that has waited longer than {@code orphanAge}, and R12
     * makes the loss silent, so a rate of swept groups that is not near zero under load is the only
     * signal there is. Staging is counted apart because deleting it is always safe.
     */
    private final LongAdder sweptGroups = new LongAdder();
    private final LongAdder sweptStagingDirs = new LongAdder();

    /**
     * A local-mode store forcing everything.
     */
    public FilesystemFileStore(final String name, final Path root) {
        this(name, root, FileStoreType.LOCAL_FILESYSTEM, Durability.FULL, null);
    }

    /**
     * @param type       {@link FileStoreType#LOCAL_FILESYSTEM} or {@link FileStoreType#SHARED_FILESYSTEM}.
     * @param durability What {@link FileStoreWrite#commit()} forces before returning.
     * @param orphanAge  Shared only, and required for it: how old residue must be before
     *                   {@link #sweep()} removes it.
     */
    public FilesystemFileStore(final String name,
                               final Path root,
                               final FileStoreType type,
                               final Durability durability,
                               final Duration orphanAge) {
        this.name = requireNonBlank(name, "name");
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.type = Objects.requireNonNull(type, "type");
        this.durability = Objects.requireNonNull(durability, "durability");
        if (!type.isFilesystem()) {
            throw new IllegalArgumentException("A filesystem store cannot be of type " + type);
        }
        if (type.isShared()) {
            this.orphanAge = Objects.requireNonNull(orphanAge, "orphanAge is required for a shared store");
            if (orphanAge.isNegative() || orphanAge.isZero()) {
                throw new IllegalArgumentException("orphanAge must be positive: " + orphanAge);
            }
            this.writerRoot = this.root.resolve(UUID.randomUUID().toString());
        } else {
            this.orphanAge = null;
            this.writerRoot = this.root;
        }
        this.stagingRoot = writerRoot.resolve(STAGING_DIR_NAME);

        try {
            if (type.isShared()) {
                // Other nodes are writing under this root right now, and a node that wrote here before
                // may never start again. Nothing shared is cleared here; residue ages out via sweep().
                Files.createDirectories(stagingRoot);
            } else {
                // One process is the only writer, so everything in staging belongs to a write that
                // never committed and the work it was for is still claimable on a queue.
                Files.createDirectories(root);
                FileUtil.deleteContents(stagingRoot);
                Files.createDirectories(stagingRoot);
                sequence.set(DirUtil.getMaxDirId(root));
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to initialise file store " + name + " at " + this.root, e);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    public Path getRoot() {
        return root;
    }

    public Path getWriterRoot() {
        return writerRoot;
    }

    /**
     * How many committed groups {@link #sweep()} has deleted since this store was built. Should be
     * near zero: orphans only come from a crash between commit and publish. A steady rate under load
     * means the sweep is reaching live work and {@code orphanAge} is too short.
     */
    public long getSweptGroupCount() {
        return sweptGroups.sum();
    }

    /**
     * How many stale staging directories {@link #sweep()} has deleted since this store was built.
     */
    public long getSweptStagingCount() {
        return sweptStagingDirs.sum();
    }

    public FileStoreType getType() {
        return type;
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
                .withDetail("type", type.toString())
                .withDetail("writable", true)
                .build();
    }

    @Override
    public FileStoreWrite newWrite() throws IOException {
        final long id = sequence.incrementAndGet();
        final Path staging = stagingRoot.resolve(StringIdUtil.idToString(id));
        Files.createDirectories(staging);
        return new Write(id, staging);
    }

    @Override
    public Path resolve(final FileStoreLocation location) throws IOException {
        final Path path = toGroupPath(location);
        if (!Files.isDirectory(path)) {
            throw new FileGroupNotFoundException(location,
                    "No file group at '" + FileUtil.getCanonicalPath(path) + "'. It was never written, or it "
                    + "has already been consumed and deleted.");
        }
        // Lent, not copied: a committed group has exactly one reader, the holder of the one leased
        // message that names it.
        return path;
    }

    @Override
    public void delete(final FileStoreLocation location) throws IOException {
        final Path path = toGroupPath(location);
        if (!Files.exists(path)) {
            // Already gone - usually because the consumer moved the lent directory out, which the
            // forwarder and the aggregators all do. The numbering directories it left may be empty.
            cascadeDeleteEmptyParents(path.getParent());
            return;
        }

        // The delete IS the operation asked for, so a failure is a throw. FileUtil.deleteDir reports
        // by return value and no-ops for a non-directory, so the throw is reconstructed and the shape
        // dispatched on: a stray regular file at a group path is corruption, and reporting its removal
        // as success while leaving it in place would be silent.
        final boolean deleted = Files.isDirectory(path)
                ? FileUtil.deleteDir(path)
                : FileUtil.delete(path);
        if (!deleted) {
            throw new IOException("Failed to fully delete file store location " + FileUtil.getCanonicalPath(path));
        }

        cascadeDeleteEmptyParents(path.getParent());
    }

    /**
     * Validate a location and convert it to a path without asserting anything is there, so that
     * {@link #delete} stays idempotent while {@link #resolve} reports absence.
     */
    private Path toGroupPath(final FileStoreLocation location) throws IOException {
        Objects.requireNonNull(location, "location");
        if (!name.equals(location.storeName())) {
            throw new IOException("File store location is for store '" + location.storeName()
                                  + "' but this store is '" + name + "'");
        }
        if (!location.isFilesystem()) {
            throw new IOException("Not a filesystem location: " + location.uri());
        }
        final Path path = location.toPath();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IOException("File store location '" + path + "' is outside store root '" + root + "'");
        }
        if (!DirUtil.isValidLeafPath(path)) {
            throw new IOException("File store location '" + path + "' is not a file group path");
        }
        return path;
    }

    /**
     * Remove the numbering directories a deleted group has left empty, from its parent up to but
     * never including the writer root. Best effort: it stops at the first directory that is not
     * empty or cannot be removed, and never throws.
     * <p>
     * This races with a commit creating the same directories on another thread or node, and needs
     * no lock: {@code rmdir} only succeeds on an empty directory, and a commit whose parent vanished
     * between its {@code mkdir} and its rename retries the pair.
     * </p>
     */
    private void cascadeDeleteEmptyParents(final Path start) {
        Path dir = start;
        while (dir != null
               && dir.startsWith(root)
               && !dir.equals(root)
               && DirUtil.isValidLeafOrBranchPath(dir)) {
            try {
                Files.delete(dir);
            } catch (final DirectoryNotEmptyException e) {
                return;
            } catch (final NoSuchFileException e) {
                // Someone else removed it; its parent may be empty now.
            } catch (final IOException e) {
                LOGGER.debug("Could not remove empty directory {}: {}", dir, e.toString());
                return;
            }
            dir = dir.getParent();
        }
    }

    /**
     * Local mode start-up: delete every committed group not named by a live queue message, remove
     * the numbering directories that leaves empty, and re-establish the counter from what is left.
     * <p>
     * Exact because nothing is running: the set of live groups is precisely the set some message
     * names. The counter is not persisted and must not be - a fully drained store restarts at 1,
     * which is safe because this sweep has just proved nothing references any id that is gone.
     * </p>
     *
     * @param live Every location a queue message names, for any store; those for other stores are
     *             ignored.
     * @return How many groups were deleted.
     */
    public int deleteAllExcept(final Set<FileStoreLocation> live) throws IOException {
        if (type.isShared()) {
            throw new IllegalStateException("A shared store is not swept against the queues; use sweep()");
        }
        final Set<Path> livePaths = live.stream()
                .filter(location -> name.equals(location.storeName()) && location.isFilesystem())
                .map(FileStoreLocation::toPath)
                .collect(Collectors.toSet());

        int deleted = 0;
        for (final Path group : listGroups(root)) {
            if (!livePaths.contains(group)) {
                if (FileUtil.deleteDir(group)) {
                    deleted++;
                    LOGGER.info("Deleted orphaned file group {} from store {}: no queue message names it",
                            FileUtil.getCanonicalPath(group), name);
                } else {
                    LOGGER.warn("Failed to delete orphaned file group {} from store {}",
                            FileUtil.getCanonicalPath(group), name);
                }
            }
        }
        deleteEmptyNumberingDirs(root, null);
        sequence.set(DirUtil.getMaxDirId(root));
        return deleted;
    }

    /**
     * Shared mode housekeeping, run on a schedule by every node: delete anything under the store
     * older than {@code orphanAge} - stale staging, groups left unreferenced, empty numbering
     * directories - in every writer root including those of nodes that no longer exist, and remove a
     * writer root left empty. Deletes are idempotent; two nodes sweeping at once is harmless.
     * <p>
     * A group older than {@code orphanAge} is deleted whether or not something still names it.
     * That bound is the whole safety argument, and it is the operator's.
     * </p>
     *
     * @return How many groups and staging directories were deleted.
     */
    public int sweep() throws IOException {
        if (!type.isShared()) {
            throw new IllegalStateException("A local store clears residue at start-up; use deleteAllExcept()");
        }
        final Instant cutoff = Instant.now().minus(orphanAge);
        int deleted = 0;
        for (final Path candidateWriterRoot : listDirectories(root)) {
            deleted += sweepWriterRoot(candidateWriterRoot, cutoff);
        }
        return deleted;
    }

    private int sweepWriterRoot(final Path aWriterRoot, final Instant cutoff) throws IOException {
        int deleted = 0;
        final Path staging = aWriterRoot.resolve(STAGING_DIR_NAME);
        if (Files.isDirectory(staging)) {
            for (final Path dir : listDirectories(staging)) {
                if (isOlderThan(dir, cutoff) && FileUtil.deleteDir(dir)) {
                    deleted++;
                    sweptStagingDirs.increment();
                    LOGGER.info("Deleted stale staging directory {} from store {}", dir, name);
                }
            }
        }
        for (final Path group : listGroups(aWriterRoot)) {
            if (isOlderThan(group, cutoff) && FileUtil.deleteDir(group)) {
                deleted++;
                sweptGroups.increment();
                LOGGER.info("Deleted file group {} from store {}: older than {}. An orphan if a crash left it "
                            + "unreferenced; live work if something still named it, which the sweep cannot tell",
                        group, name, orphanAge);
            }
        }
        deleteEmptyNumberingDirs(aWriterRoot, cutoff);
        if (!aWriterRoot.equals(writerRoot) && isOlderThan(aWriterRoot, cutoff)) {
            deleteIfEmpty(staging);
            deleteIfEmpty(aWriterRoot);
        }
        return deleted;
    }

    /**
     * Every committed group under a writer root, found by walking the numbering directories and
     * stopping at each leaf so a group's own contents are never entered.
     */
    private static List<Path> listGroups(final Path aWriterRoot) throws IOException {
        final List<Path> groups = new ArrayList<>();
        collectGroups(aWriterRoot, groups);
        return groups;
    }

    private static void collectGroups(final Path dir, final List<Path> groups) throws IOException {
        for (final Path child : listDirectories(dir)) {
            if (DirUtil.isValidLeafPath(child)) {
                groups.add(child);
            } else if (DirUtil.isValidLeafOrBranchPath(child)) {
                collectGroups(child, groups);
            }
        }
    }

    /**
     * Remove empty numbering directories under a writer root, deepest first, never the writer root
     * itself. With a cutoff, only those older than it; without, all of them.
     */
    private static void deleteEmptyNumberingDirs(final Path aWriterRoot, final Instant cutoff) throws IOException {
        for (final Path child : listDirectories(aWriterRoot)) {
            if (DirUtil.isValidLeafOrBranchPath(child) && !DirUtil.isValidLeafPath(child)) {
                deleteEmptyNumberingDirs(child, cutoff);
                if (cutoff == null || isOlderThan(child, cutoff)) {
                    deleteIfEmpty(child);
                }
            }
        }
    }

    private static void deleteIfEmpty(final Path dir) {
        try {
            Files.delete(dir);
        } catch (final IOException e) {
            // Not empty, already gone, or not ours to remove right now. All fine.
        }
    }

    private static boolean isOlderThan(final Path path, final Instant cutoff) {
        try {
            final FileTime modified = Files.getLastModifiedTime(path);
            return modified.toInstant().isBefore(cutoff);
        } catch (final IOException e) {
            return false;
        }
    }

    private static List<Path> listDirectories(final Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (final Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory).sorted().toList();
        } catch (final NoSuchFileException e) {
            return List.of();
        }
    }

    private static String requireNonBlank(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    @Override
    public String toString() {
        return "FilesystemFileStore{name='" + name + "', type=" + type + ", root=" + root + '}';
    }


    // --------------------------------------------------------------------------------


    private final class Write implements FileStoreWrite {

        private final long id;
        private final Path staging;
        private Path committed;

        private Write(final long id, final Path staging) {
            this.id = id;
            this.staging = staging;
        }

        @Override
        public Path getPath() {
            return staging;
        }

        @Override
        public FileStoreLocation commit() throws IOException {
            if (committed != null) {
                return FileStoreLocation.filesystem(name, committed);
            }

            // The group's own bytes before the rename, so a name is never published whose contents
            // are still only in the page cache.
            if (durability.forcesFileGroups()) {
                DirUtil.fsyncRecursively(staging);
            }

            final Path leaf = DirUtil.createPath(writerRoot, id);
            final Path oldestNewAncestor = moveIntoPlace(leaf);

            // And the directories the rename touched, so the rename itself survives: the parent, every
            // numbering directory this commit created above it, and the existing directory that
            // gained the entry naming the highest new one - that entry is what publishes the whole
            // new subtree, and forcing the new directories alone says nothing about it. Once per
            // thousand groups that is more than one fsync.
            if (durability.forcesFileGroups()) {
                final Path outermost = oldestNewAncestor == null
                        ? leaf.getParent()
                        : oldestNewAncestor.getParent();
                for (Path dir = leaf.getParent(); ; dir = dir.getParent()) {
                    DirUtil.fsyncDir(dir);
                    if (dir.equals(outermost)) {
                        break;
                    }
                }
            }

            committed = leaf;
            return FileStoreLocation.filesystem(name, leaf);
        }

        /**
         * The highest directory below the writer root that does not yet exist, or null if the parent
         * already does. Its creation is the one new entry in an existing directory that a durable
         * commit must force - by forcing that existing directory, its parent.
         */
        private Path oldestMissingAncestor(final Path parent) {
            Path oldest = null;
            for (Path dir = parent; !dir.equals(writerRoot) && !Files.isDirectory(dir); dir = dir.getParent()) {
                oldest = dir;
            }
            return oldest;
        }

        /**
         * Create the numbering directories and rename staging into place, retrying the pair if a
         * cascade-delete on another thread or node removed the parent in between. Every primitive is
         * atomic - mkdir is idempotent, rmdir succeeds only on an empty directory, rename fails
         * cleanly if the parent has gone - so the retry is all the coordination needed.
         *
         * @return The highest directory the successful attempt created, or null if the leaf's parent
         *         already existed. Observed on that attempt rather than before the first: a cascade
         *         between two attempts can remove a directory that was there, and it is then this
         *         commit's to create and force.
         */
        private Path moveIntoPlace(final Path leaf) throws IOException {
            IOException last = null;
            for (int attempt = 0; attempt < COMMIT_ATTEMPTS; attempt++) {
                try {
                    final Path oldestNewAncestor = oldestMissingAncestor(leaf.getParent());
                    // Not atomic in the JDK either: createDirectories is create-then-check, so a
                    // directory that a cascade removes between the two surfaces as "already exists".
                    Files.createDirectories(leaf.getParent());
                    Files.move(staging, leaf, StandardCopyOption.ATOMIC_MOVE);
                    return oldestNewAncestor;
                } catch (final NoSuchFileException | FileAlreadyExistsException e) {
                    if (!Files.isDirectory(staging)) {
                        throw e;
                    }
                    last = e;
                }
            }
            throw new IOException("Could not commit file group " + id + " to " + leaf + " after "
                                  + COMMIT_ATTEMPTS + " attempts; its parent kept being removed", last);
        }

        @Override
        public void close() {
            if (committed == null) {
                // Discarding an uncommitted write. A throw here would be suppressed behind whatever
                // really went wrong, or would invent an error for an abandonment; the directory is
                // unreferenced either way and the store's housekeeping reclaims it.
                if (!FileUtil.deleteDir(staging)) {
                    LOGGER.warn("Failed to fully delete the staging directory {} of an uncommitted write",
                            FileUtil.getCanonicalPath(staging));
                }
            }
        }
    }
}
