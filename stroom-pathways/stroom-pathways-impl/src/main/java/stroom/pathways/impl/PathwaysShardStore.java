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

package stroom.pathways.impl;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.pathways.shared.PathwaysDoc;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.dao.PlanBEnv;
import stroom.planb.impl.dao.trace.PathwaysDb;
import stroom.planb.impl.fs.SharedFileStorePublisher;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.PathSegmentUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Lends out one shard of a Pathways document's learnt model, as a local copy.
 *
 * <p>The authoritative copy lives on the shared filesystem and is never opened there — no LMDB
 * environment is ever opened on the shared mount, which is an invariant of the whole shared file
 * store. So a caller is handed a throwaway local directory, works in that, and the finished file is
 * copied back up.
 *
 * <p><b>Call only while holding the shard's cluster lock.</b> That lock is the whole of the mutual
 * exclusion: the push does not check that the shared copy is still the one it handed down, so it
 * would overwrite whatever is there. In normal running that cannot bite, because the lock is taken
 * by a conditional update of one database row and so admits one holder at a time, on one node or
 * across all of them. The one case it does not cover is a lease that expires while its holder still
 * believes it holds the lock — then two processes can both be in here, and the later push discards
 * the earlier one's work silently. Plan B's own two shared-store write paths have the same exposure,
 * and it is accepted here for the same reason: a hold bounded to seconds against a lease measured in
 * minutes makes it rare.
 */
@Singleton
public class PathwaysShardStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PathwaysShardStore.class);

    // Holds the copy that is pushed. Sits inside the working copy so it is cleaned up along with it.
    private static final String COMPACTED_DIR_NAME = "compacted";

    private final SharedFileStorePublisher publisher;
    private final ByteBuffers byteBuffers;
    private final Path localRoot;
    private final Path readCacheRoot;

    /**
     * One per shard, so two queries do not replace a cached copy from under each other, and a query
     * does not read a copy half way through being refreshed.
     */
    private final Map<String, Object> readLocks = new ConcurrentHashMap<>();

    @Inject
    public PathwaysShardStore(final SharedFileStorePublisher publisher,
                              final ByteBuffers byteBuffers,
                              final PathCreator pathCreator) {
        this.publisher = publisher;
        this.byteBuffers = byteBuffers;
        final Path pathwaysHome = pathCreator.toAppPath("${stroom.home}/pathways");
        this.localRoot = pathwaysHome.resolve("shards");
        this.readCacheRoot = pathwaysHome.resolve("read");
    }

    /**
     * Where a document's shards live on the shared file store.
     *
     * <p>Public because not everything kept beside a shard is kept by this: a marker saying when some
     * once-a-day work was last done for a shard sits here too, read and written by whoever does that
     * work.
     *
     * @return the directory holding every shard of the document, or nothing where it names no shared
     * file store.
     */
    public Optional<Path> sharedDocDir(final PathwaysDoc doc) {
        final SharedFileStoreSettings settings = NullSafe.get(doc, PathwaysDoc::getSharedFileStore);
        return settings == null || NullSafe.isBlankString(settings.getSharedPath())
                ? Optional.empty()
                : Optional.of(sharedDocDir(settings, doc.getUuid()));
    }

    private static Path sharedDocDir(final SharedFileStoreSettings settings, final String docUuid) {
        return Path.of(settings.getSharedPath())
                .resolve(PlanBConstants.SHARDS_DIR_NAME)
                .resolve(PathSegmentUtil.requireSafeSegment(docUuid));
    }

    /**
     * Copies the shard down, runs {@code work} against the local copy, and copies it back up if
     * {@code work} says it changed anything.
     *
     * <p>Nothing is pushed for a shard that was only read, which matters because the push moves the
     * whole file: a cycle that applied nothing would otherwise rewrite the model to say so.
     *
     * <p>What goes up is a compacted copy of the working file rather than the working file itself, so
     * the shared store holds the model and not the free pages left behind by rewriting it.
     *
     * <p>It is published in place rather than by swapping the shard directory, because
     * {@link #readShard} reads that directory holding no lock and a swap makes it briefly absent.
     *
     * @param work given the local directory, returns whether it changed what is in it. Anything it
     *             throws propagates, and the shard is left as it was on the shared store.
     * @return whether the model was pushed back.
     */
    public boolean withShard(final PathwaysDoc doc,
                             final int shardIndex,
                             final Predicate<Path> work) throws IOException {
        final SharedFileStoreSettings settings = doc.getSharedFileStore();
        if (settings == null) {
            throw new IOException("Pathways document " + doc.getName() + " names no shared file store");
        }
        final Path sharedDocDir = sharedDocDir(settings, doc.getUuid());

        final Path localDir = localRoot
                .resolve(PathSegmentUtil.requireSafeSegment(doc.getUuid())
                         + "_" + PlanBConstants.formatShardIndex(shardIndex));
        FileUtil.deleteDir(localDir);
        Files.createDirectories(localDir);
        try {
            copyDown(sharedDocDir.resolve(PlanBConstants.formatShardIndex(shardIndex)), localDir);

            if (!work.test(localDir)) {
                LOGGER.debug(() -> LogUtil.message("Shard {} of {} was not changed, so not pushed",
                        shardIndex, doc.getName()));
                return false;
            }
            publisher.publishShard(compact(localDir), sharedDocDir, shardIndex);
            return true;

        } finally {
            try {
                FileUtil.deleteDir(localDir);
            } catch (final RuntimeException e) {
                // A leftover working copy costs disk, not correctness: the next cycle clears it before
                // copying down again.
                LOGGER.warn(() -> "Could not clean up " + localDir + ": " + e.getMessage());
            }
        }
    }

    /**
     * Reads one shard's model, without holding its cluster lock.
     *
     * <p>Queries cannot open the shared copy either, so a local one is kept and refreshed only when
     * the shard's {@code .version} says it has moved on. That makes a repeat query cost an open
     * rather than a copy, which matters because a screen reads every shard to answer once.
     *
     * <p>The environment is opened and closed inside this call rather than held. A cached copy can
     * therefore be replaced between queries without any reader having it mapped — which is what
     * {@code ArchiveStoreShard} needs its per-generation directories to arrange, and what this avoids
     * needing by not keeping one open.
     *
     * @return empty where the shard has no model yet, which is not an error — nothing has been
     * applied to it.
     */
    public <R> Optional<R> readShard(final PathwaysDoc doc,
                                     final int shardIndex,
                                     final Function<PathwaysDb, R> work) throws IOException {
        final SharedFileStoreSettings settings = doc.getSharedFileStore();
        if (settings == null) {
            return Optional.empty();
        }
        final Path sharedShardDir = sharedDocDir(settings, doc.getUuid())
                .resolve(PlanBConstants.formatShardIndex(shardIndex));
        final String shardKey = doc.getUuid() + "_" + PlanBConstants.formatShardIndex(shardIndex);

        synchronized (readLocks.computeIfAbsent(shardKey, k -> new Object())) {
            final Path localDir = readCacheRoot.resolve(shardKey);
            if (!refreshReadCopy(sharedShardDir, localDir)) {
                return Optional.empty();
            }
            final PathwaysDb db;
            try {
                db = PathwaysDb.create(localDir, byteBuffers, true);
            } catch (final RuntimeException e) {
                // The copy would not open, so it is a copy of nothing — a data file caught part way
                // through being replaced, on a mount with no atomic rename. Discard it rather than
                // serve the same failure every time: it is stamped with the shard's current version,
                // which would otherwise stop it being taken again until the shard is next published.
                discard(localDir);
                throw e;
            }
            try (db) {
                return Optional.ofNullable(work.apply(db));
            }
        }
    }

    // Brings the local copy up to the shared shard's current version, and says whether there is
    // anything to read. The marker is written after the data it describes, so re-reading it once the
    // copy is done tells us whether a publish landed mid-copy; one retry is enough for a publish that
    // takes far less time than a cycle. A copy taken between the data and its marker is kept, labelled
    // with the version it had: the marker that follows then reads as a change and it is taken again.
    private boolean refreshReadCopy(final Path sharedShardDir, final Path localDir) throws IOException {
        for (int attempt = 0; attempt < 2; attempt++) {
            final String sharedVersion = sharedVersion(sharedShardDir);
            if (sharedVersion == null) {
                return false;
            }
            if (sharedVersion.equals(readVersion(localDir))) {
                return true;
            }
            Files.createDirectories(localDir);
            Files.deleteIfExists(localDir.resolve(PlanBConstants.VERSION_FILE_NAME));
            try {
                copyDown(sharedShardDir, localDir);
            } catch (final NoSuchFileException e) {
                // Where the shared mount has no atomic rename, publishing replaces data.mdb by a plain
                // move, so it can go between being found and being read. The next attempt takes the
                // one that replaced it.
                LOGGER.debug(() -> "Shard data went while copying " + sharedShardDir + ", retrying");
                continue;
            }
            if (sharedVersion.equals(sharedVersion(sharedShardDir))) {
                Files.writeString(localDir.resolve(PlanBConstants.VERSION_FILE_NAME), sharedVersion);
                return true;
            }
        }
        LOGGER.warn(() -> "Gave up refreshing " + localDir + "; it is being published to repeatedly");
        return false;
    }

    // The shard's published version, or "" where it holds data that has no version yet — what a crash
    // between publishing the data and writing the marker leaves on a shard's first publish. Reading that
    // data beats reporting the shard empty, and recording "" means the first real version to appear
    // reads as a change. Null where there is nothing there to read at all.
    private static String sharedVersion(final Path sharedShardDir) {
        final String version = readVersion(sharedShardDir);
        if (version != null) {
            return version;
        }
        return Files.exists(sharedShardDir.resolve(PlanBConstants.DATA_FILE_NAME))
                ? ""
                : null;
    }

    private static void discard(final Path localDir) {
        try {
            FileUtil.deleteDir(localDir);
        } catch (final RuntimeException e) {
            LOGGER.warn(() -> "Could not discard the unreadable copy at " + localDir + ": " + e.getMessage());
        }
    }

    private static String readVersion(final Path dir) {
        try {
            final Path versionFile = dir.resolve(PlanBConstants.VERSION_FILE_NAME);
            return Files.exists(versionFile)
                    ? Files.readString(versionFile)
                    : null;
        } catch (final IOException e) {
            LOGGER.debug(() -> "Could not read the version of " + dir + ": " + e.getMessage());
            return null;
        }
    }

    // LMDB never shrinks a file. Rewriting a pathway takes fresh pages and leaves the old ones on the
    // free list, so a shard that is rewritten every cycle settles at several times the size of what it
    // actually holds. Compacting writes only the live pages into a new file, and it is that file which
    // is pushed, so the shared store keeps, sends and returns the smaller one. What it costs is a read
    // pass over the local copy; what it saves is on the shared mount, both on the way up and on every
    // query that copies back down.
    private static Path compact(final Path localDir) throws IOException {
        final Path dataFile = localDir.resolve(PlanBConstants.DATA_FILE_NAME);
        if (!Files.exists(dataFile)) {
            // Work claimed a change but wrote no environment, so there is nothing to compact and the
            // push has nothing to copy either. Hand back the directory it already expects.
            return localDir;
        }

        final long start = System.currentTimeMillis();
        try (final PlanBEnv env = PlanBEnv.openForMaintenance(localDir)) {
            final Path compactedDir = localDir.resolve(COMPACTED_DIR_NAME);
            FileUtil.deleteDir(compactedDir);
            Files.createDirectories(compactedDir);
            env.compactTo(compactedDir);
            LOGGER.debug(() -> LogUtil.message("Compacted {} from {} to {} bytes in {}ms",
                    localDir,
                    sizeOf(dataFile),
                    sizeOf(compactedDir.resolve(PlanBConstants.DATA_FILE_NAME)),
                    System.currentTimeMillis() - start));
            return compactedDir;
        }
    }

    private static long sizeOf(final Path file) {
        try {
            return Files.size(file);
        } catch (final IOException e) {
            return -1;
        }
    }

    // A shard nothing has written yet has no data file, and the caller then starts from an empty
    // directory. Replace rather than create: an interrupted cycle can leave a partial file behind, and
    // failing on it would stop this shard being worked until the process restarts.
    private static void copyDown(final Path sharedShardDir, final Path localDir) throws IOException {
        final Path sharedData = sharedShardDir.resolve(PlanBConstants.DATA_FILE_NAME);
        if (Files.exists(sharedData)) {
            Files.copy(sharedData, localDir.resolve(PlanBConstants.DATA_FILE_NAME),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
