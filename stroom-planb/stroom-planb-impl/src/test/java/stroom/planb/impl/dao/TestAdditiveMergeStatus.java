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
import stroom.entity.shared.ExpressionCriteria;
import stroom.planb.impl.dao.histogram.HistogramDb;
import stroom.planb.impl.dao.metric.MetricDb;
import stroom.planb.impl.dao.metric.MetricFields;
import stroom.planb.impl.data.TemporalValue;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.keyprefix.Tag;
import stroom.planb.impl.serde.temporalkey.TemporalKey;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.HistogramSettings;
import stroom.planb.shared.MetricSettings;
import stroom.planb.shared.MetricValueSchema;
import stroom.planb.shared.PlanBDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.util.io.ByteSize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Additive store merges (histogram, and metric count/sum) must not double count when the same source is
 * merged more than once, e.g. when a merge is rerun after an interrupted shutdown or a duplicate copy of a
 * part arrives. See docs/merge-idempotency-design.md.
 */
class TestAdditiveMergeStatus {

    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());

    private static final HistogramSettings HISTOGRAM_SETTINGS = new HistogramSettings
            .Builder()
            .maxStoreSize(ByteSize.ofGibibytes(1).getBytes())
            .build();

    private static final MetricSettings METRIC_SETTINGS = new MetricSettings
            .Builder()
            .maxStoreSize(ByteSize.ofGibibytes(1).getBytes())
            .valueSchema(new MetricValueSchema
                    .Builder()
                    .storeLatestValue(true)
                    .storeMin(true)
                    .storeMax(true)
                    .storeCount(true)
                    .storeSum(true)
                    .build())
            .build();

    private final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");

    @Test
    void histogramReMergeDoesNotDoubleCount(@TempDir final Path tempDir) throws IOException {
        final PlanBDoc doc = getDoc(HISTOGRAM_SETTINGS);

        // Create a source part holding a value of 10 against each of three keys.
        final Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        try (final HistogramDb source = HistogramDb.create(sourceDir, BYTE_BUFFERS, doc, false)) {
            source.write(writer -> {
                for (int i = 0; i < 3; i++) {
                    source.insert(writer, new TemporalValue(key(i), 10L));
                }
            });
        }

        // Copies of the same part, as staging zip replay or a sender retry would produce. They carry the
        // same instance UUID as the source they were copied from.
        final Path copy1 = copyDir(tempDir, sourceDir, "copy1");
        final Path copy2 = copyDir(tempDir, sourceDir, "copy2");
        final Path copy3 = copyDir(tempDir, sourceDir, "copy3");

        final Path targetDir = tempDir.resolve("target");
        Files.createDirectories(targetDir);
        try (final HistogramDb target = HistogramDb.create(targetDir, BYTE_BUFFERS, doc, false)) {
            target.merge(copy1);
            assertThat(copy1).doesNotExist();
            for (int i = 0; i < 3; i++) {
                assertThat(target.get(key(i))).isEqualTo(10L);
            }

            // Merging a duplicate of the same source is skipped, and the duplicate is still consumed.
            target.merge(copy2);
            assertThat(copy2).doesNotExist();
            for (int i = 0; i < 3; i++) {
                assertThat(target.get(key(i))).isEqualTo(10L);
            }

            // The status record is what prevents the double count: too new to prune...
            assertThat(target.deleteOldMergeStatus(Instant.now().minusSeconds(3600))).isZero();
            // ...but once pruned, a further copy of the source is fully re-merged and counts double.
            assertThat(target.deleteOldMergeStatus(Instant.now().plusSeconds(3600))).isOne();
            target.merge(copy3);
            for (int i = 0; i < 3; i++) {
                assertThat(target.get(key(i))).isEqualTo(20L);
            }
        }
    }

    @Test
    void metricReMergeDoesNotDoubleCountOrSum(@TempDir final Path tempDir) throws IOException {
        final PlanBDoc doc = getDoc(METRIC_SETTINGS);

        // Create a source part holding three values against one key, so count = 3 and sum = 30.
        final Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        try (final MetricDb source = MetricDb.create(sourceDir, BYTE_BUFFERS, doc, false)) {
            source.write(writer -> {
                source.insert(writer, new TemporalValue(key(0), 5L));
                source.insert(writer, new TemporalValue(key(0), 10L));
                source.insert(writer, new TemporalValue(key(0), 15L));
            });
        }

        final Path copy1 = copyDir(tempDir, sourceDir, "copy1");
        final Path copy2 = copyDir(tempDir, sourceDir, "copy2");

        final Path targetDir = tempDir.resolve("target");
        Files.createDirectories(targetDir);
        try (final MetricDb target = MetricDb.create(targetDir, BYTE_BUFFERS, doc, false)) {
            target.merge(copy1);
            checkCountAndSum(target, 3L, 30L);

            // Merging a duplicate of the same source must not add to count or sum.
            target.merge(copy2);
            checkCountAndSum(target, 3L, 30L);
        }
    }

    private void checkCountAndSum(final MetricDb db, final long expectedCount, final long expectedSum) {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create(MetricFields.TIME);
        fieldIndex.create(MetricFields.COUNT);
        fieldIndex.create(MetricFields.SUM);
        final List<Val[]> results = new ArrayList<>();
        db.search(
                new ExpressionCriteria(ExpressionOperator.builder().build()),
                fieldIndex,
                null,
                new ExpressionPredicateFactory(),
                results::add);
        assertThat(results).isNotEmpty();
        // All values were written against refTime, which is the first second of the bucket.
        assertThat(results.getFirst()[1].toLong()).isEqualTo(expectedCount);
        assertThat(results.getFirst()[2].toLong()).isEqualTo(expectedSum);
    }

    private TemporalKey key(final int i) {
        final KeyPrefix prefix = KeyPrefix.create(List.of(new Tag("host", ValString.create("host-" + i))));
        return new TemporalKey(prefix, refTime);
    }

    /**
     * Copy a part shard as the staging replay and sender retry paths would, i.e. bit for bit including its
     * instance UUID.
     */
    private Path copyDir(final Path tempDir, final Path sourceDir, final String name) throws IOException {
        final Path targetDir = tempDir.resolve(name);
        try (final Stream<Path> stream = Files.walk(sourceDir)) {
            stream.forEach(path -> {
                try {
                    final Path dest = targetDir.resolve(sourceDir.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(path, dest);
                    }
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return targetDir;
    }

    private static PlanBDoc getDoc(final AbstractPlanBSettings settings) {
        return PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test")
                .settings(settings)
                .build();
    }
}
