/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.planb.impl.InstantRange;
import stroom.planb.impl.db.session.Session;
import stroom.planb.impl.db.session.SessionDb;
import stroom.planb.impl.db.session.SessionFields;
import stroom.planb.impl.db.session.SessionRequest;
import stroom.planb.shared.SessionSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValString;
import stroom.util.io.ByteSize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TestSessionDb {

    private static final SessionSettings BASIC_SETTINGS = SessionSettings
            .builder()
            .maxStoreSize(ByteSize.ofGibibytes(100).getBytes())
            .build();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());

    @Test
    void test(@TempDir Path tempDir) {
        final Ranges ranges = testWrite(tempDir);

        final Val key = ValString.create("TEST");
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        final InstantRange highRange = ranges.highRange;
        final InstantRange lowRange = ranges.lowRange;

        try (final SessionDb db = SessionDb.create(
                tempDir,
                BYTE_BUFFERS,
                BASIC_SETTINGS,
                true)) {
            assertThat(db.count()).isEqualTo(109);
            testGet(db, key, refTime, 10);

            checkState(db, key, highRange.max(), true);
            checkState(db, key, highRange.min(), true);
            checkState(db, key, lowRange.max(), true);
            checkState(db, key, lowRange.min(), true);
            checkState(db, key, highRange.max().plusMillis(1), false);
            checkState(db, key, lowRange.min().minusMillis(1), false);

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
    void testMerge(@TempDir final Path rootDir) throws IOException {
        final Path dbPath1 = rootDir.resolve("db1");
        final Path dbPath2 = rootDir.resolve("db2");
        Files.createDirectory(dbPath1);
        Files.createDirectory(dbPath2);

        testWrite(dbPath1);
        testWrite(dbPath2);

        try (final SessionDb db = SessionDb.create(dbPath1, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            db.merge(dbPath2);
        }
    }

    @Test
    void testCondenseAndDelete(@TempDir final Path rootDir) throws IOException {
        final Path dbPath = rootDir.resolve("db");
        Files.createDirectory(dbPath);

        testWrite(dbPath);

        try (final SessionDb db = SessionDb.create(dbPath, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            assertThat(db.count()).isEqualTo(109);
            db.condense(System.currentTimeMillis(), 0);
            assertThat(db.count()).isEqualTo(1);
            db.condense(System.currentTimeMillis(), System.currentTimeMillis());
            assertThat(db.count()).isEqualTo(0);
        }
    }

    private Ranges testWrite(final Path dbDir) {
        final Val key = ValString.create("TEST");
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        final InstantRange highRange;
        final InstantRange lowRange;
        try (final SessionDb db = SessionDb.create(dbDir, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            highRange = insertData(db, key, refTime, 100, 10);
            lowRange = insertData(db, key, refTime, 10, -10);
        }
        return new Ranges(highRange, lowRange);
    }

    private record Ranges(InstantRange highRange,
                          InstantRange lowRange) {

    }

    private void testGet(final SessionDb db,
                         final Val key,
                         final Instant refTime,
                         final long deltaSeconds) {
        final Session k = Session.builder().start(refTime).end(refTime.plusSeconds(deltaSeconds)).key(key).build();
        final Session session = db.get(k);
        assertThat(session).isNotNull();
        assertThat(session.getKey()).isEqualTo(key);
    }

    private void checkState(final SessionDb db,
                            final Val key,
                            final Instant time,
                            final boolean expected) {
        final SessionRequest request = new SessionRequest(key, time);
        final Session session = db.getState(request);
        assertThat(session != null).isEqualTo(expected);
    }

    private InstantRange insertData(final SessionDb db,
                                    final Val key,
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

                final Session session = Session.builder().key(key).start(start).end(end).build();
                db.insert(writer, session);
            }
            reference.set(new InstantRange(min, max));
        });
        return reference.get();
    }
}
