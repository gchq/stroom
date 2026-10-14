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

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.node.api.NodeInfo;
import stroom.pathways.shared.PathwaysDoc;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.fs.MergeCompletionStrategy;
import stroom.planb.impl.fs.SharedFileStorePublisher;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.StateType;
import stroom.util.io.PathCreator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Whether a shard's model survives the round trip to the shared filesystem and back.
 *
 * <p>Exercised against a counter in an LMDB environment rather than a real model. That is the point:
 * the moving of the file has nothing to do with what is in it, and it can be settled before the model
 * exists. The counter is a fixture and is not, and should not become, shipped code.
 */
class TestPathwaysShardStore {

    private static final ByteBufferFactoryImpl BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);

    private static final int SHARD = 3;

    @TempDir
    Path tempDir;

    @Mock
    private PathCreator pathCreator;
    @Mock
    private NodeInfo nodeInfo;

    private Path shared;
    private PathwaysDoc doc;
    private PathwaysShardStore store;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        shared = Files.createDirectories(tempDir.resolve("pathways_shared"));
        doc = PathwaysDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Test Pathways")
                .sharedFileStore(new SharedFileStoreSettings(8, shared.toString()))
                .build();

        Mockito.when(pathCreator.toAppPath(Mockito.anyString()))
                .thenReturn(tempDir.resolve("local"));
        // publishShard stamps .version with the publishing node's name.
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn("test-node");
        store = new PathwaysShardStore(
                new SharedFileStorePublisher(
                        nodeInfo,
                        BYTE_BUFFERS,
                        BYTE_BUFFER_FACTORY,
                        new PlanBPaths(tempDir.resolve("local_state")),
                        Map.<StateType, MergeCompletionStrategy>of()),
                BYTE_BUFFERS,
                pathCreator);
    }

    @Test
    void aChangeSurvivesBeingPushedAndTakenBackDown() throws IOException {
        assertThat(store.withShard(doc, SHARD, localDir -> {
            setCounter(localDir, 7);
            return true;
        })).as("a changed shard is pushed").isTrue();

        final long[] seen = {-1};
        store.withShard(doc, SHARD, localDir -> {
            seen[0] = readCounter(localDir);
            return false;
        });
        assertThat(seen[0]).isEqualTo(7);
    }

    @Test
    void changesAccumulateAcrossHolds() throws IOException {
        for (int i = 0; i < 3; i++) {
            store.withShard(doc, SHARD, localDir -> {
                setCounter(localDir, readCounter(localDir) + 1);
                return true;
            });
        }

        final long[] seen = {-1};
        store.withShard(doc, SHARD, localDir -> {
            seen[0] = readCounter(localDir);
            return false;
        });
        assertThat(seen[0]).as("each hold started from what the last one left").isEqualTo(3);
    }

    @Test
    void aShardNobodyHasWrittenStartsEmpty() throws IOException {
        final long[] seen = {-1};
        store.withShard(doc, SHARD, localDir -> {
            assertThat(localDir.resolve(PlanBConstants.DATA_FILE_NAME))
                    .as("nothing to copy down yet").doesNotExist();
            seen[0] = readCounter(localDir);
            return false;
        });
        assertThat(seen[0]).isZero();
    }

    @Test
    void anUnchangedShardIsNotPushed() throws IOException {
        store.withShard(doc, SHARD, localDir -> {
            setCounter(localDir, 5);
            return true;
        });
        final long pushedAt = lastModified(sharedShardDir().resolve(PlanBConstants.DATA_FILE_NAME));

        assertThat(store.withShard(doc, SHARD, localDir -> false))
                .as("a shard that was only read is left alone").isFalse();
        assertThat(lastModified(sharedShardDir().resolve(PlanBConstants.DATA_FILE_NAME)))
                .isEqualTo(pushedAt);
    }

    @Test
    void workThatThrowsLeavesTheSharedCopyAsItWas() throws IOException {
        store.withShard(doc, SHARD, localDir -> {
            setCounter(localDir, 4);
            return true;
        });

        assertThatThrownBy(() -> store.withShard(doc, SHARD, localDir -> {
            setCounter(localDir, 99);
            throw new IllegalStateException("applying blew up");
        })).isInstanceOf(IllegalStateException.class);

        final long[] seen = {-1};
        store.withShard(doc, SHARD, localDir -> {
            seen[0] = readCounter(localDir);
            return false;
        });
        assertThat(seen[0]).as("the half-done work never reached the shared store").isEqualTo(4);
    }

    @Test
    void noLmdbEnvironmentIsOpenedOnTheSharedStore() throws IOException {
        store.withShard(doc, SHARD, localDir -> {
            setCounter(localDir, 1);
            return true;
        });

        // A lock.mdb anywhere under the shared tree means an env was opened there, which is the one
        // thing the shared file store does not allow.
        try (final Stream<Path> files = Files.walk(shared)) {
            assertThat(files.filter(p -> p.getFileName().toString()
                            .equals(PlanBConstants.LOCK_FILE_NAME)).toList())
                    .isEmpty();
        }
    }

    @Test
    void theLocalWorkingCopyIsNotLeftBehind() throws IOException {
        final Path[] used = {null};
        store.withShard(doc, SHARD, localDir -> {
            used[0] = localDir;
            setCounter(localDir, 1);
            return true;
        });
        assertThat(used[0]).doesNotExist();
    }

    @Test
    void aBloatedShardIsPushedCompacted() throws IOException {
        final long[] localSize = {0};
        store.withShard(doc, SHARD, localDir -> {
            fill(localDir, 0, 300);
            deleteRange(localDir, 0, 290);
            localSize[0] = fileSize(localDir.resolve(PlanBConstants.DATA_FILE_NAME));
            return true;
        });

        assertThat(localSize[0])
                .as("the fixture has to leave a bloated file behind or this proves nothing")
                .isGreaterThan(8L * 1024 * 1024);
        assertThat(fileSize(sharedShardDir().resolve(PlanBConstants.DATA_FILE_NAME)))
                .as("what was pushed holds the ten surviving entries, not the pages the other 290 used")
                .isLessThan(localSize[0] / 4);
    }

    @Test
    void compactingDoesNotChangeWhatIsStored() throws IOException {
        store.withShard(doc, SHARD, localDir -> {
            fill(localDir, 0, 50);
            deleteRange(localDir, 0, 40);
            setCounter(localDir, 11);
            return true;
        });

        final List<String> keys = new ArrayList<>();
        final long[] counter = {-1};
        store.withShard(doc, SHARD, localDir -> {
            keys.addAll(keysIn(localDir));
            counter[0] = readCounter(localDir);
            return false;
        });

        assertThat(keys).as("compacting drops free pages, not entries")
                .containsExactly("c", "k040", "k041", "k042", "k043", "k044",
                        "k045", "k046", "k047", "k048", "k049");
        assertThat(counter[0]).isEqualTo(11);
    }

    /**
     * Queries read this directory holding no lock, so a publish must replace what is inside it rather
     * than swap the directory itself — a shard that is briefly absent reads as a shard with no model.
     */
    @Test
    void theSharedShardDirIsNeverReplaced() throws IOException {
        store.withShard(doc, SHARD, localDir -> {
            setCounter(localDir, 1);
            return true;
        });
        final Object firstKey = Files.readAttributes(
                sharedShardDir(), BasicFileAttributes.class).fileKey();

        store.withShard(doc, SHARD, localDir -> {
            setCounter(localDir, 2);
            return true;
        });

        assertThat(Files.readAttributes(sharedShardDir(), BasicFileAttributes.class).fileKey())
                .as("the shard directory must survive a push, not be swapped for a new one")
                .isEqualTo(firstKey);
    }

    @Test
    void aTempFileLeftByAnInterruptedPushIsSweptByTheNext() throws IOException {
        store.withShard(doc, SHARD, localDir -> {
            setCounter(localDir, 1);
            return true;
        });
        final Path orphan = sharedShardDir().resolve(PlanBConstants.DATA_TMP_FILE_NAME + "_stale");
        Files.writeString(orphan, "half a data file");

        store.withShard(doc, SHARD, localDir -> {
            setCounter(localDir, 2);
            return true;
        });

        assertThat(orphan).doesNotExist();

        final long[] seen = {-1};
        store.withShard(doc, SHARD, localDir -> {
            seen[0] = readCounter(localDir);
            return false;
        });
        assertThat(seen[0]).isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // Fixture — a counter in an LMDB env, standing in for the model
    // -----------------------------------------------------------------------

    private Path sharedShardDir() {
        return shared.resolve(PlanBConstants.SHARDS_DIR_NAME)
                .resolve(doc.getUuid())
                .resolve(PlanBConstants.formatShardIndex(SHARD));
    }

    private static void setCounter(final Path dir, final long value) {
        inEnv(dir, true, (dbi, txn) -> {
            dbi.put(txn, key("c"), value(value));
            return null;
        });
    }

    private static long readCounter(final Path dir) {
        return inEnv(dir, false, (dbi, txn) -> {
            final ByteBuffer found = dbi.get(txn, key("c"));
            return found == null
                    ? 0L
                    : found.getLong();
        });
    }

    // Entries big enough that the environment has to grow real pages for them, so deleting them again
    // leaves a file far larger than what it holds. That is the state a shard rewritten every cycle ends
    // up in, and the one compaction is there to undo.
    private static void fill(final Path dir, final int from, final int to) {
        inEnv(dir, true, (dbi, txn) -> {
            final ByteBuffer big = ByteBuffer.allocateDirect(64 * 1024);
            while (big.hasRemaining()) {
                big.put((byte) 'x');
            }
            big.flip();
            for (int i = from; i < to; i++) {
                dbi.put(txn, key(entryKey(i)), big.duplicate());
            }
            return null;
        });
    }

    private static void deleteRange(final Path dir, final int from, final int to) {
        inEnv(dir, true, (dbi, txn) -> {
            for (int i = from; i < to; i++) {
                dbi.delete(txn, key(entryKey(i)));
            }
            return null;
        });
    }

    private static List<String> keysIn(final Path dir) {
        return inEnv(dir, false, (dbi, txn) -> {
            final List<String> keys = new ArrayList<>();
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn)) {
                for (final KeyVal<ByteBuffer> keyVal : cursor) {
                    keys.add(StandardCharsets.UTF_8.decode(keyVal.key()).toString());
                }
            }
            return keys;
        });
    }

    private static String entryKey(final int i) {
        return String.format("k%03d", i);
    }

    private static <R> R inEnv(final Path dir,
                               final boolean write,
                               final BiFunction<Dbi<ByteBuffer>, Txn<ByteBuffer>, R> work) {
        try (final Env<ByteBuffer> env = Env.create()
                .setMapSize(256L * 1024L * 1024L)
                .setMaxDbs(1)
                .open(dir.toFile())) {
            final Dbi<ByteBuffer> dbi = env.openDbi("counter", DbiFlags.MDB_CREATE);
            try (final Txn<ByteBuffer> txn = write
                    ? env.txnWrite()
                    : env.txnRead()) {
                final R result = work.apply(dbi, txn);
                if (!txn.isReadOnly()) {
                    txn.commit();
                }
                return result;
            }
        }
    }

    private static ByteBuffer key(final String name) {
        final byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        final ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length);
        buf.put(bytes).flip();
        return buf;
    }

    private static ByteBuffer value(final long value) {
        final ByteBuffer buf = ByteBuffer.allocateDirect(Long.BYTES);
        buf.putLong(value).flip();
        return buf;
    }

    private static long fileSize(final Path path) {
        try {
            return Files.size(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long lastModified(final Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
