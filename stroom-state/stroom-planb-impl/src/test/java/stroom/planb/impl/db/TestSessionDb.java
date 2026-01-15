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

package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.planb.impl.InstantRange;
import stroom.planb.impl.data.Session;
import stroom.planb.impl.db.StateKeyTestUtil.ValueFunction;
import stroom.planb.impl.db.session.SessionDb;
import stroom.planb.impl.db.session.SessionFields;
import stroom.planb.impl.db.session.SessionRequest;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SessionKeySchema;
import stroom.planb.shared.SessionSettings;
import stroom.planb.shared.TemporalPrecision;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;
import stroom.util.shared.PageRequest;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TestSessionDb {

    private static final int ITERATIONS = 100;
    private static final SessionSettings BASIC_SETTINGS = new SessionSettings
            .Builder()
            .maxStoreSize(ByteSize.ofGibibytes(100).getBytes())
            .build();
    private static final PlanBDoc DOC = getDoc(BASIC_SETTINGS);
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());

    @Test
    void test(@TempDir final Path tempDir) {
        final Ranges ranges = testWrite(tempDir);

        final KeyPrefix key = KeyPrefix.create("TEST");
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        final InstantRange highRange = ranges.highRange;
        final InstantRange lowRange = ranges.lowRange;

        try (final SessionDb db = SessionDb.create(
                tempDir,
                BYTE_BUFFERS,
                DOC,
                true)) {
            assertThat(db.count()).isEqualTo(109);
            testGet(db, key, refTime, 10);

            checkState(db, i -> key, highRange.max(), true);
            checkState(db, i -> key, highRange.min(), true);
            checkState(db, i -> key, lowRange.max(), true);
            checkState(db, i -> key, lowRange.min(), true);
            checkState(db, i -> key, highRange.max().plusMillis(1), false);
            checkState(db, i -> key, lowRange.min().minusMillis(1), false);

            final ExpressionOperator expression = ExpressionOperator.builder()
                    .addTextTerm(SessionFields.KEY_FIELD, Condition.EQUALS, "TEST")
                    .build();
            final ExpressionCriteria criteria = new ExpressionCriteria(expression);

            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create(SessionFields.KEY);
            fieldIndex.create(SessionFields.START);
            fieldIndex.create(SessionFields.END);

            final InstantRange outerRange = InstantRange.combine(highRange, lowRange);
            final ValDate minTime = ValDate.create(outerRange.min());
            final ValDate maxTime = ValDate.create(outerRange.max());
            final List<Val[]> results = new ArrayList<>();
            final ExpressionPredicateFactory expressionPredicateFactory = new ExpressionPredicateFactory();
            db.search(
                    criteria,
                    fieldIndex,
                    null,
                    expressionPredicateFactory,
                    results::add);
            assertThat(results.size()).isEqualTo(1);
            assertThat(results.getFirst()[0].toString()).isEqualTo("TEST");
            assertThat(results.getFirst()[1]).isEqualTo(minTime);
            assertThat(results.getFirst()[2]).isEqualTo(maxTime);
        }
    }

    @Test
    void testSessionQuery() throws IOException {
        final Path dbDir = Files.createTempDirectory("stroom");
        final KeyPrefix prefix = KeyPrefix.create("User1");
        final Instant refTime = Instant.parse("2025-12-01T00:00:00.000Z");
        try (final SessionDb db = SessionDb.create(dbDir, BYTE_BUFFERS, DOC, false)) {
            db.write(writer -> {
                Instant day = refTime;
                for (int i = 0; i < 9; i++) {
                    for (int j = 6; j < 15; j++) {
                        final Instant start = day.plus(j, ChronoUnit.HOURS);
                        final Instant end = day.plus(j + 1, ChronoUnit.HOURS);
                        final Session session = Session.builder().prefix(prefix).start(start).end(end).build();
                        db.insert(writer, session);
                    }
                    day = day.plus(1, ChronoUnit.DAYS);
                }
            });

            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create(SessionFields.KEY);
            fieldIndex.create(SessionFields.START);
            fieldIndex.create(SessionFields.END);
            final ExpressionCriteria criteria = new ExpressionCriteria(
                    PageRequest.unlimited(),
                    Collections.emptyList(),
                    ExpressionOperator.builder().build());
            final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().build();
            final List<Val[]> results = new ArrayList<>();
            final ExpressionPredicateFactory expressionPredicateFactory = new ExpressionPredicateFactory();
            db.search(
                    criteria,
                    fieldIndex,
                    dateTimeSettings,
                    expressionPredicateFactory,
                    results::add);

            // Assert the data is as expected.
            validateResults(results, refTime, 9);

            // Condense and delete.
            db.condense(Instant.parse("2025-12-07T00:10:00.000Z"));
            db.deleteOldData(Instant.parse("2025-12-04T00:10:00.000Z"), true);

            results.clear();
            db.search(
                    criteria,
                    fieldIndex,
                    dateTimeSettings,
                    expressionPredicateFactory,
                    results::add);

            // Assert the data is as expected.
            validateResults(results, refTime.plus(3, ChronoUnit.DAYS), 6);
        }
    }

    private void validateResults(final List<Val[]> results,
                                 final Instant refTime,
                                 final int expectedSize) {
        assertThat(results.size()).isEqualTo(expectedSize);
        Instant day = refTime;
        for (final Val[] vals : results) {
            final Instant start = day.plus(6, ChronoUnit.HOURS);
            final Instant end = day.plus(15, ChronoUnit.HOURS);
            assertThat(vals[1]).isEqualTo(ValDate.create(start));
            assertThat(vals[2]).isEqualTo(ValDate.create(end));
            day = day.plus(1, ChronoUnit.DAYS);
        }
    }

    @TestFactory
    Collection<DynamicTest> testMultiWrite() {
        return createMultiKeyTest(1, false);
    }

    @TestFactory
    Collection<DynamicTest> testMultiWritePerformance() {
        return createMultiKeyTest(ITERATIONS, false);
    }

    @TestFactory
    Collection<DynamicTest> testMultiWriteRead() {
        return createMultiKeyTest(1, true);
    }

    @TestFactory
    Collection<DynamicTest> testMultiWriteReadPerformance() {
        return createMultiKeyTest(ITERATIONS, true);
    }

    Collection<DynamicTest> createMultiKeyTest(final int iterations, final boolean read) {
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        final List<DynamicTest> tests = new ArrayList<>();
        for (final ValueFunction valueFunction : StateKeyTestUtil.getValueFunctions()) {
            for (final TemporalPrecision temporalPrecision : TemporalPrecision.values()) {

                tests.add(DynamicTest.dynamicTest("Value type = " + valueFunction +
                                                  ", Temporal precision = " + temporalPrecision,
                        () -> {
                            final SessionSettings settings = new SessionSettings
                                    .Builder()
                                    .keySchema(new SessionKeySchema.Builder()
                                            .keyType(valueFunction.stateValueType())
                                            .temporalPrecision(temporalPrecision)
                                            .build())
                                    .build();

                            Path path = null;
                            try {
                                path = Files.createTempDirectory("stroom");

                                testWrite(path, settings, iterations,
                                        valueFunction.function(), refTime);
                                if (read) {
                                    testSimpleRead(path, settings, iterations,
                                            valueFunction.function(), refTime);
                                }

                            } catch (final IOException e) {
                                throw new UncheckedIOException(e);
                            } finally {
                                if (path != null) {
                                    FileUtil.deleteDir(path);
                                }
                            }
                        }));
            }
        }
        return tests;
    }

    private void testWrite(final Path dbDir,
                           final SessionSettings settings,
                           final int insertRows,
                           final Function<Integer, KeyPrefix> valueFunction,
                           final Instant refTime) {
        try (final SessionDb db = SessionDb.create(dbDir, BYTE_BUFFERS, getDoc(settings), false)) {
            insertData(db, valueFunction, refTime, insertRows, 0);
        }
    }

    private void testSimpleRead(final Path dbDir,
                                final SessionSettings settings,
                                final int rows,
                                final Function<Integer, KeyPrefix> valueFunction,
                                final Instant time) {
        try (final SessionDb db = SessionDb.create(dbDir, BYTE_BUFFERS, getDoc(settings), true)) {
            for (int i = 0; i < rows; i++) {
                final KeyPrefix key = valueFunction.apply(i);
                final Session session = db.getState(new SessionRequest(key, time));
                assertThat(session).isNotNull();
                assertThat(session.getPrefix().getVal().type()).isEqualTo(key.getVal().type());
                assertThat(session.getPrefix().getVal()).isEqualTo(key.getVal());
            }
        }
    }

    @Test
    void testMerge(@TempDir final Path rootDir) throws IOException {
        final Path dbPath1 = rootDir.resolve("db1");
        final Path dbPath2 = rootDir.resolve("db2");
        Files.createDirectory(dbPath1);
        Files.createDirectory(dbPath2);

        testWrite(dbPath1);
        testWrite(dbPath2);

        try (final SessionDb db = SessionDb.create(dbPath1, BYTE_BUFFERS, DOC, false)) {
            db.merge(dbPath2);
        }
    }

    @Test
    void testCondenseAndDelete(@TempDir final Path rootDir) throws IOException {
        final Path dbPath = rootDir.resolve("db");
        Files.createDirectory(dbPath);

        testWrite(dbPath);

        try (final SessionDb db = SessionDb.create(dbPath, BYTE_BUFFERS, DOC, false)) {
            assertThat(db.count()).isEqualTo(109);
            db.condense(Instant.now());
            db.deleteOldData(Instant.MIN, true);
            assertThat(db.count()).isEqualTo(1);
            db.condense(Instant.now());
            db.deleteOldData(Instant.now(), true);
            assertThat(db.count()).isEqualTo(0);
        }
    }

    private Ranges testWrite(final Path dbDir) {
        final KeyPrefix key = KeyPrefix.create("TEST");
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        final InstantRange highRange;
        final InstantRange lowRange;
        try (final SessionDb db = SessionDb.create(dbDir, BYTE_BUFFERS, DOC, false)) {
            highRange = insertData(db, i -> key, refTime, 100, 10);
            lowRange = insertData(db, i -> key, refTime, 10, -10);
        }
        return new Ranges(highRange, lowRange);
    }

    private record Ranges(InstantRange highRange,
                          InstantRange lowRange) {

    }

    private void testGet(final SessionDb db,
                         final KeyPrefix key,
                         final Instant refTime,
                         final long deltaSeconds) {
        final Session k = Session.builder().start(refTime).end(refTime.plusSeconds(deltaSeconds)).prefix(key).build();
        final Session session = db.get(k);
        assertThat(session).isNotNull();
        assertThat(session.getPrefix()).isEqualTo(key);
    }

    private void checkState(final SessionDb db,
                            final Function<Integer, KeyPrefix> valueFunction,
                            final Instant time,
                            final boolean expected) {
        final SessionRequest request = new SessionRequest(valueFunction.apply(0), time);
        final Session session = db.getState(request);
        assertThat(session != null).isEqualTo(expected);
    }

    private InstantRange insertData(final SessionDb db,
                                    final Function<Integer, KeyPrefix> valueFunction,
                                    final Instant refTime,
                                    final int rows,
                                    final long deltaSeconds) {
        final AtomicReference<InstantRange> reference = new AtomicReference<>();
        db.write(writer -> {
            Instant min = refTime;
            Instant max = refTime;
            for (int i = 0; i < rows; i++) {
                final Instant start = refTime.plusSeconds(i * deltaSeconds);
                final Instant end = start.plusSeconds(Math.abs(deltaSeconds));
                if (start.isBefore(min)) {
                    min = start;
                }
                if (end.isAfter(max)) {
                    max = end;
                }

                final Session session = Session.builder().prefix(valueFunction.apply(i)).start(start).end(end).build();
                db.insert(writer, session);
            }
            reference.set(new InstantRange(min, max));
        });
        return reference.get();
    }

    private static PlanBDoc getDoc(final SessionSettings settings) {
        return PlanBDoc.builder().uuid(UUID.randomUUID().toString()).name("test").settings(settings).build();
    }
}
