/*
 * Copyright 2024 Crown Copyright
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

package stroom.state.impl.dao;

import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValDate;
import stroom.state.impl.InstantRange;
import stroom.state.impl.ScyllaDbUtil;
import stroom.util.shared.string.CIKeys;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestSessionDao {

    @Test
    void testDao() {
        ExpressionOperator expression = ExpressionOperator.builder()
                .addTextTerm(SessionFields.KEY_FIELD, Condition.EQUALS, "TEST")
                .build();
        final ExpressionCriteria criteria = new ExpressionCriteria(expression);

        ScyllaDbUtil.test((sessionProvider, tableName) -> {
            final SessionDao sessionDao = new SessionDao(sessionProvider, tableName);

            final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
            final InstantRange highRange = insertData(sessionDao, refTime, 100, 10);
            final InstantRange lowRange = insertData(sessionDao, refTime, 10, -10);
            final InstantRange outerRange = InstantRange.combine(highRange, lowRange);

            assertThat(sessionDao.count()).isEqualTo(109);

            final AtomicInteger count = new AtomicInteger();
            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create(CIKeys.KEY);
            fieldIndex.create(CIKeys.START);
            fieldIndex.create(CIKeys.END);
            fieldIndex.create(CIKeys.TERMINAL);

            final ValDate minTime = ValDate.create(outerRange.min());
            final ValDate maxTime = ValDate.create(outerRange.max());
            sessionDao.search(criteria, fieldIndex, null, values -> {
                count.incrementAndGet();
                assertThat(values[1]).isEqualTo(minTime);
                assertThat(values[2]).isEqualTo(maxTime);
            });
            assertThat(count.get()).isEqualTo(1);
            count.set(0);

            sessionDao.condense(Instant.now());
            assertThat(sessionDao.count()).isOne();

            sessionDao.search(new ExpressionCriteria(expression), fieldIndex, null, values -> {
                count.incrementAndGet();
                assertThat(values[1]).isEqualTo(minTime);
                assertThat(values[2]).isEqualTo(maxTime);
            });
            assertThat(count.get()).isEqualTo(1);
            count.set(0);

            // Test in session.
            assertThat(sessionDao.inSession(
                    new TemporalStateRequest(
                            "TEST",
                            "TEST",
                            Instant.parse("2000-01-01T00:00:00.000Z"))))
                    .isTrue();
            assertThat(sessionDao.inSession(
                    new TemporalStateRequest(
                            "TEST",
                            "TEST",
                            Instant.parse("1999-01-01T00:00:00.000Z"))))
                    .isFalse();
        });
    }

    @Test
    void testRemoveOldData() {
        ScyllaDbUtil.test((sessionProvider, tableName) -> {
            final SessionDao sessionDao = new SessionDao(sessionProvider, tableName);

            Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
            insertData(sessionDao, refTime, 100, 10);
            insertData(sessionDao, refTime, 10, -10);

            assertThat(sessionDao.count()).isEqualTo(109);

            sessionDao.removeOldData(refTime);
            assertThat(sessionDao.count()).isEqualTo(100);

            sessionDao.removeOldData(Instant.now());
            assertThat(sessionDao.count()).isEqualTo(0);
        });
    }

    private InstantRange insertData(final SessionDao sessionDao,
                                    final Instant refTime,
                                    final int rows,
                                    final long deltaSeconds) {
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

            final Session session = new Session("TEST", start, end, false);
            sessionDao.insert(Collections.singletonList(session));
        }
        return new InstantRange(min, max);
    }
}
