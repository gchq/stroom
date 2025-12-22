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

package stroom.proxy.app.handler;

import stroom.proxy.repo.queue.QueueMonitor;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.exception.ThrowingSupplier;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A concurrent directory based queue.
 * Directories are added to the queue by performing an atomic move of the source directory
 * into the queue directory structure.
 * <p>
 * Each item on the queue has a sequential ID and the queue maintains a the current position
 * for both a reader and a writer. See {@link DirUtil} for details of the directory structure.
 * </p>
 * <p>
 * On initialisation, the queue will scan the rootDir to establish the min and max IDs then
 * set the readId and writeId accordingly.
 * </p>
 */
public class DirQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DirQueue.class);
    private final Path rootDir;

    /**
     * ID last written to, i.e. 0 if never written to
     */
    private long writeId;
    /**
     * ID to read from next, i.e. 1 if not read yet
     */
    private long readId;

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final QueueMonitor queueMonitor;
    private final String name;

    DirQueue(final Path rootDir,
             final QueueMonitors queueMonitors,
             final FileStores fileStores,
             final int order,
             final String name) {
        this.rootDir = rootDir;
        this.queueMonitor = queueMonitors.create(order, name);
        this.name = name;

        // Create the root directory
        DirUtil.ensureDirExists(rootDir);

        // Create the store directory and initialise the store id.
        fileStores.add(order, name + " - store", rootDir);

        final long maxId = DirUtil.getMaxDirId(rootDir);
        final long minId = DirUtil.getMinDirId(rootDir);

        if (minId > maxId) {
            throw new IllegalStateException(LogUtil.message("minId {} is greater than maxId {}", minId, maxId));
        }

        writeId = maxId;
        readId = Math.max(1, minId);
        queueMonitor.setWritePos(maxId);
        queueMonitor.setReadPos(minId);
        LOGGER.info("Initialising queue '{}' in {} with readId {} and writeId {}",
                name, LogUtil.path(rootDir), readId, writeId);
    }

    /**
     * Get the next dir that is available in the queue as soon as one is available, block until then.
     *
     * @return A dir that is managed by this queue. The dir should be closed once used to ensure parent dirs are
     * deleted if empty.
     */
    public Dir next() {
        Dir dir = null;
        try {
            lock.lockInterruptibly();
            try {
                final long initialId = readId;
                while (dir == null) {
                    while (readId > writeId) {
                        condition.await();
                    }
                    dir = tryNext();
                }
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
        LOGGER.trace("next() - {} ({}) - next() dir: {}", name, rootDir, dir);
        return dir;
    }


    /**
     * Get the next dir that is available in the queue as soon as one is available, block until then or timeout.
     *
     * @return A dir that is managed by this queue. The dir should be closed once used to ensure parent dirs are
     * deleted if empty.
     */
    public Optional<Dir> next(final long time, final TimeUnit unit) {
        Dir dir = null;
        try {
            lock.lockInterruptibly();
            try {
//                final long initialId = readId;
                while (dir == null) {
                    while (readId > writeId) {
                        if (!condition.await(time, unit)) {
                            return Optional.empty();
                        }
                    }
                    dir = tryNext();
                }
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
        LOGGER.trace("next() - {} ({}) - next() time: {}, unit: {}, dir: {}", name, rootDir, time, unit, dir);
        return Optional.of(dir);
    }

    private Dir tryNext() {
        DurationTimer timer = null;
        Dir dir = null;
        // Make the assumption that the ids are all nicely sequential so create the path for the next id
        // and see if it is a dir
        Set<Path> allDeletedPaths = null;
        final long initialReadId = readId;
        long id = readId++;
        while (dir == null) {
            Path path = DirUtil.createPath(rootDir, id);

            boolean foundIdPath = false;
            if (Files.isDirectory(path)) {
                // We expect to come in here in 99.9999% of cases.
                foundIdPath = true;
            } else {
                // id wasn't a path so look for a sibling dir
                // Only start the time if we didn't find a sequential ID
                timer = Objects.requireNonNullElseGet(timer, DurationTimer::start);
                allDeletedPaths = Objects.requireNonNullElseGet(allDeletedPaths, HashSet::new);
                final Path parent = path.getParent();
                if (Files.isDirectory(parent)) {
                    final long lastId = id;
                    try (final Stream<Path> pathStream = DirUtil.findDirectories(parent)) {
                        final OptionalLong nextSiblingId = pathStream
                                .filter(DirUtil::isValidLeafPath)
                                .map(Path::getFileName)
                                .map(Objects::toString)
                                .mapToLong(Long::parseLong)
                                .filter(anId -> anId > lastId)
                                .sorted()
                                .findFirst();
                        if (nextSiblingId.isPresent()) {
                            foundIdPath = true;
                            id = nextSiblingId.getAsLong();
                            readId = id + 1;
                            path = DirUtil.createPath(rootDir, id);
                            if (LOGGER.isDebugEnabled()) { // Mutable id
                                LOGGER.debug("tryNext() - Finding next sibling in {}, nextSibling: {} (id: {})",
                                        parent,
                                        path,
                                        ModelStringUtil.formatCsv(id));
                            }
                        } else {
                            // No siblings so parent must be empty, so try to delete it
                            LOGGER.debug("tryNext() - No sibling found in {}", parent);
                        }
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                } else {
                    // No IDs in ths block to read from, so jump to the next block
                    if (LOGGER.isTraceEnabled()) { // Mutable id
                        LOGGER.trace("tryNext() - parent {} doesn't exist, so advancing to ID {}",
                                parent, ModelStringUtil.formatCsv(id));
                    }
                }
            }

            if (foundIdPath) {
                queueMonitor.setReadPos(id);
                dir = createDir(path);
                // Gaps should be unlikely but if we do get them it would be useful to know about it,
                // so we can investigate
                final long gapSize = id - initialReadId;
                if (gapSize > 5) {
                    LOGGER.warn("Large gap ({}) in dir IDs, initialReadId: {}, id: {}, path: {}",
                            ModelStringUtil.formatCsv(gapSize),
                            ModelStringUtil.formatCsv(initialReadId),
                            ModelStringUtil.formatCsv(id),
                            path);
                }
            } else {
                // No IDs in ths block of 1000, so jump to the next one
                final Path parent = path.getParent();
                pruneEmptyParts(parent, allDeletedPaths);
                id = DirUtil.getIdInNextBlock(id);
                readId = id;
                if (LOGGER.isTraceEnabled()) { // Mutable id
                    LOGGER.trace("tryNext() - parent {} doesn't exist, so advancing to ID {}",
                            parent, ModelStringUtil.formatCsv(id));
                }
            }
            if (dir == null && readId > writeId) {
                // Didn't find any valid paths after initialReadId and have reached the write position,
                // so we can move the write position back to
                final long newWriteId = initialReadId - 1;
                LOGGER.info("tryNext() - No more ID paths found in rootDir: {}, " +
                            "resetting position: readId: {} => {}, writeId: {} => {}, duration: {}",
                        rootDir,
                        ModelStringUtil.formatCsv(readId),
                        ModelStringUtil.formatCsv(initialReadId),
                        ModelStringUtil.formatCsv(writeId),
                        ModelStringUtil.formatCsv(newWriteId),
                        NullSafe.get(timer, DurationTimer::get));
                readId = initialReadId;
                writeId = newWriteId;
                queueMonitor.setReadPos(readId);
                queueMonitor.setWritePos(writeId);
                break;
            }
        }
        if (readId > initialReadId + 1) {
            LOGGER.info("tryNext() - initialId: {}, readId: {}, writeId: {}, gap size: {}, duration: {}",
                    initialReadId,
                    ModelStringUtil.formatCsv(readId),
                    ModelStringUtil.formatCsv(writeId),
                    ModelStringUtil.formatCsv(readId - initialReadId),
                    NullSafe.get(timer, DurationTimer::get));
        }
        return dir;
    }

    void deleteDirIfNotEmpty(final Path path) {
        LOGGER.debug("deleteDirIfNotEmpty() - path {}", path);
        try {
            Files.delete(path);
            LOGGER.info("Deleted path {}", path);
        } catch (final DirectoryNotEmptyException e) {
            // This is okk, so just swallow
        } catch (final IOException e) {
            // If we can't delete this then we can't delete the parent
            LOGGER.error("Error while trying to delete path '{}'", path);
        }
    }

    List<Path> pruneEmptyParts(final Path path, final Set<Path> allDeletedPaths) {
        Path relPath = rootDir.relativize(path);
        LOGGER.trace("pruneEmptyParts() - relPath: {}, path: {}", relPath, path);
        final List<Path> deletedPaths = new ArrayList<>();
        // We already know the leaf is not a dir else this method would not be called, so start with
        // its parent
        while (relPath != null) {
            final Path aPath = rootDir.resolve(relPath);
            if (Files.isDirectory(aPath) && !hasAlreadyBeenDeleted(aPath, allDeletedPaths)) {
                try {
                    Files.delete(aPath);
                    deletedPaths.add(aPath);
                    // Remove any children of our path as the fact we have deleted their parent means
                    // they have also been deleted. Reduces items held.
                    allDeletedPaths.removeIf(aDeletedPath ->
                            aDeletedPath.startsWith(aPath));
                    allDeletedPaths.add(aPath);
                } catch (final DirectoryNotEmptyException e) {
                    // Not empty so no point going higher in the path
                    break;
                } catch (final IOException e) {
                    // If we can't delete this then we can't delete the parent
                    LOGGER.error("Error while cleaning up path '{}' (parent: '{}', rootDir: '{}') - {}",
                            path, aPath, rootDir, LogUtil.exceptionMessage(e));
                    break;
                }
            } else {
                LOGGER.trace("pruneEmptyParts() - Skipping {}", aPath);
            }
            relPath = relPath.getParent();
        }

        if (LOGGER.isDebugEnabled() && !deletedPaths.isEmpty()) {
            LOGGER.debug("pruneEmptyParts() - Deleted empty directories:\n{}",
                    deletedPaths.stream()
                            .map(Path::toString)
                            .collect(Collectors.joining("\n")));
        }
        return deletedPaths;
    }

    private boolean hasAlreadyBeenDeleted(final Path path, final Set<Path> allDeletedPaths) {
        if (allDeletedPaths.contains(path)) {
            return true;
        } else {
            // e.g. path: foo/2/012/345/012345000, allDeletedPaths contains: foo/2/012/345
            return allDeletedPaths.stream()
                    .anyMatch(path::startsWith);
        }
    }

//    void pruneEmptyParts2(final Path path) {
//        Path relPath = rootDir.relativize(path);
//        LOGGER.trace("pruneEmptyParts() - relPath: {}, path: {}", relPath, path);
//        final List<Path> deletedPaths = new ArrayList<>();
//
//        FileUtil.deepListContents()
//
//        // We already know the leaf is not a dir else this method would not be called, so
//        while (true) {
//            relPath = relPath.getParent();
//            if (relPath == null) {
//                break;
//            } else {
//                final Path parent = rootDir.resolve(relPath);
//                if (Files.isDirectory(parent)) {
//                    try {
//                        Files.delete(parent);
//                        deletedPaths.add(parent);
//                    } catch (final DirectoryNotEmptyException e) {
//                        // Not empty so no point going higher in the path
//                        break;
//                    } catch (final IOException e) {
//                        // If we can't delete this then we can't delete the parent
//                        LOGGER.error("Error while cleaning up path '{}' (parent: '{}', rootDir: '{}') - {}",
//                                path, parent, rootDir, LogUtil.exceptionMessage(e));
//                        break;
//                    }
//                }
//            }
//        }
//        if (!deletedPaths.isEmpty()) {
//            LOGGER.info("pruneEmptyParts() - Deleted empty directories:\n{}",
//                    deletedPaths.stream()
//                            .map(Path::toString)
//                            .collect(Collectors.joining("\n")));
//        }
//    }

    /**
     * Add a dir to the queue. In the process this will move (atomically) the source dir to a dir managed
     * by the queue so that the process providing the dir will no longer be responsible
     * for managing the supplied dir.
     *
     * @param sourceDir The source dir to move to the queue.
     */
    public void add(final Path sourceDir) {
        try {
            lock.lockInterruptibly();
            try {
                // Increment the sequence id.
                final long id = ++writeId;
                queueMonitor.setWritePos(id);
                final Path targetDir = DirUtil.createPath(rootDir, id);
                final Path targetParent = targetDir.getParent();
                try {
                    DirUtil.ensureDirExists(targetParent);
                    Files.move(sourceDir, targetDir, StandardCopyOption.ATOMIC_MOVE);
                    LOGGER.trace("add() - {} ({}) - Added sourceDir {}", name, rootDir, sourceDir);
                } catch (final IOException e) {
                    final boolean targetParentExists = LogUtil.swallowExceptions(
                                    ThrowingSupplier.unchecked(() -> Files.exists(targetParent)))
                            .orElse(false);
                    final boolean sourceExists = LogUtil.swallowExceptions(
                                    ThrowingSupplier.unchecked(() -> Files.exists(sourceDir)))
                            .orElse(false);
                    LOGGER.error("Error moving {} -> {}, sourceExists: {}, targetParentExists: {}, msg: {}",
                            sourceDir, targetDir, sourceExists, targetParentExists, LogUtil.exceptionMessage(e), e);
                    throw new UncheckedIOException(e);
                }
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    /**
     * When we have finished with a dir we should be in a position where the dir has been
     * moved so can try to delete the parent directories. We never want to delete the dir
     * itself as it should have been moved and any failure to do so is an error.
     *
     * @param dir The dir to close.
     */
    public void close(final Dir dir) {
        try {
            lock.lockInterruptibly();
            try {
                // Try to delete parent directories.
                try {
                    boolean success = true;
                    Path path = dir.getPath().getParent();
                    while (!path.equals(rootDir) && success) {
                        success = Files.deleteIfExists(path);
                        path = path.getParent();
                    }
                } catch (final DirectoryNotEmptyException e) {
                    // Expected error.
                    LOGGER.trace(() -> LogUtil.message("close() - {} is not empty so can't be deleted",
                            e.getMessage()));
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                }
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    private Dir createDir(final Path path) {
        return new Dir(this, path);
    }

    /**
     * ID to read from next, i.e. 1 if not read yet
     */
    long getReadId() {
        return readId;
    }

    /**
     * ID last written to, i.e. 0 if never written to
     */
    long getWriteId() {
        return writeId;
    }

    @Override
    public String toString() {
        return "DirQueue{" +
               "rootDir=" + rootDir +
               ", writeId=" + writeId +
               ", readId=" + readId +
               '}';
    }
}
