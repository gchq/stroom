/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.dao;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.stream.LmdbIterable.EntryConsumer;
import stroom.planb.impl.dao.MergeStatusDb.MergeTracker;
import stroom.util.io.ByteSize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the per source merge tracking that stops additive stores double counting when a merge is rerun.
 * The batch commit threshold comes from {@link LmdbWriter#shouldCommit()} (~10k changes), so an
 * "interrupted" merge is simulated by feeding enough entries for a batch commit to have happened and then
 * abandoning the merge without completing it. See docs/merge-idempotency-design.md.
 */
class TestMergeStatusDb {

    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());
    private static final String SOURCE_UUID = "17c04f39-6d63-40a1-a3f5-fbb1eb02b995";

    // One more than the LmdbWriter batch commit threshold, so feeding this many entries performs exactly
    // one batch commit, with the cursor at the last of them.
    private static final int COMMITTED_ENTRIES = 10_001;
    private static final int ABANDONED_ENTRIES = 500;
    private static final int TOTAL_ENTRIES = COMMITTED_ENTRIES + ABANDONED_ENTRIES;

    @Test
    void interruptedMergeResumesExactlyAfterLastCommit(@TempDir final Path tempDir) {
        try (final PlanBEnv env = createEnv(tempDir)) {
            final MergeStatusDb mergeStatusDb = new MergeStatusDb(env, BYTE_BUFFERS);

            // First attempt: one batch commit happens at the threshold, then the merge is interrupted, so
            // the entries after the commit are discarded.
            env.write(writer -> {
                final MergeTracker tracker = mergeStatusDb.startMerge(writer, SOURCE_UUID);
                assertThat(tracker.isAlreadyComplete()).isFalse();
                final EntryConsumer consumer = tracker.wrap((key, val) -> {
                });
                feedEntries(consumer, TOTAL_ENTRIES);
                writer.abort();
            });

            // Rerun of the same source: only the entries after the committed cursor are merged.
            final List<String> merged = new ArrayList<>();
            env.write(writer -> {
                final MergeTracker tracker = mergeStatusDb.startMerge(writer, SOURCE_UUID);
                assertThat(tracker.isAlreadyComplete()).isFalse();
                final EntryConsumer consumer = tracker.wrap((key, val) ->
                        merged.add(StandardCharsets.UTF_8.decode(key.duplicate()).toString()));
                feedEntries(consumer, TOTAL_ENTRIES);
                tracker.complete();
            });
            assertThat(merged).hasSize(ABANDONED_ENTRIES);
            assertThat(merged.getFirst()).isEqualTo(entryKey(COMMITTED_ENTRIES + 1));
            assertThat(merged.getLast()).isEqualTo(entryKey(TOTAL_ENTRIES));

            // Any further rerun of the source is skipped entirely.
            env.write(writer -> {
                final MergeTracker tracker = mergeStatusDb.startMerge(writer, SOURCE_UUID);
                assertThat(tracker.isAlreadyComplete()).isTrue();
            });
        }
    }

    @Test
    void legacySourceWithoutUuidIsNotTracked(@TempDir final Path tempDir) {
        try (final PlanBEnv env = createEnv(tempDir)) {
            final MergeStatusDb mergeStatusDb = new MergeStatusDb(env, BYTE_BUFFERS);

            final List<String> merged = new ArrayList<>();
            env.write(writer -> {
                final MergeTracker tracker = mergeStatusDb.startMerge(writer, null);
                assertThat(tracker.isAlreadyComplete()).isFalse();
                final EntryConsumer consumer = tracker.wrap((key, val) ->
                        merged.add(StandardCharsets.UTF_8.decode(key.duplicate()).toString()));
                feedEntries(consumer, 10);
                tracker.complete();
            });
            assertThat(merged).hasSize(10);

            // No status record is written so nothing is ever skipped.
            env.write(writer -> {
                final MergeTracker tracker = mergeStatusDb.startMerge(writer, null);
                assertThat(tracker.isAlreadyComplete()).isFalse();
            });
        }
    }

    /**
     * abort() must discard real uncommitted writes: a completion written to the transaction and then
     * aborted must not be visible afterwards. This is the mechanism that stops a failed merge publishing
     * partial work via the commit that {@link LmdbWriter#close()} performs.
     */
    @Test
    void abortDiscardsUncommittedWrites(@TempDir final Path tempDir) {
        try (final PlanBEnv env = createEnv(tempDir)) {
            final MergeStatusDb mergeStatusDb = new MergeStatusDb(env, BYTE_BUFFERS);

            env.write(writer -> {
                final MergeTracker tracker = mergeStatusDb.startMerge(writer, SOURCE_UUID);
                feedEntries(tracker.wrap((key, val) -> {
                }), 10);
                tracker.complete();
                writer.abort();
            });

            // The completion was aborted so the source has not been merged.
            env.write(writer -> {
                final MergeTracker tracker = mergeStatusDb.startMerge(writer, SOURCE_UUID);
                assertThat(tracker.isAlreadyComplete()).isFalse();
            });
        }
    }

    @Test
    void deleteOldStatusHonoursAge(@TempDir final Path tempDir) {
        try (final PlanBEnv env = createEnv(tempDir)) {
            final MergeStatusDb mergeStatusDb = new MergeStatusDb(env, BYTE_BUFFERS);
            env.write(writer -> {
                final MergeTracker tracker = mergeStatusDb.startMerge(writer, SOURCE_UUID);
                feedEntries(tracker.wrap((key, val) -> {
                }), 10);
                tracker.complete();
            });

            // Too new to prune.
            env.write(writer -> {
                assertThat(mergeStatusDb.deleteOldStatus(writer, Instant.now().minusSeconds(3600)))
                        .isZero();
            });
            env.write(writer -> {
                final MergeTracker tracker = mergeStatusDb.startMerge(writer, SOURCE_UUID);
                assertThat(tracker.isAlreadyComplete()).isTrue();
            });

            // Old enough to prune, after which the source would be merged again.
            env.write(writer -> {
                assertThat(mergeStatusDb.deleteOldStatus(writer, Instant.now().plusSeconds(3600)))
                        .isOne();
            });
            env.write(writer -> {
                final MergeTracker tracker = mergeStatusDb.startMerge(writer, SOURCE_UUID);
                assertThat(tracker.isAlreadyComplete()).isFalse();
            });
        }
    }

    private PlanBEnv createEnv(final Path path) {
        return new PlanBEnv(
                path,
                ByteSize.ofMebibytes(100).getBytes(),
                5,
                false,
                new HashClashCommitRunnable());
    }

    /**
     * Feed synthetic source entries in the fixed order iteration over an immutable source would produce.
     */
    private void feedEntries(final EntryConsumer consumer, final int count) {
        final ByteBuffer val = ByteBuffer.allocate(1);
        for (int i = 1; i <= count; i++) {
            final ByteBuffer key = ByteBuffer.wrap(entryKey(i).getBytes(StandardCharsets.UTF_8));
            consumer.accept(key, val.duplicate());
        }
    }

    private static String entryKey(final int i) {
        return String.format("key-%06d", i);
    }
}
