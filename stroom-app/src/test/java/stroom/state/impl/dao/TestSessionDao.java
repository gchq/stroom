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
import stroom.state.impl.ScyllaDbUtil;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.shared.string.CIKeys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestSessionDao extends AbstractCoreIntegrationTest {

    @Test
    void testDao() {
        ScyllaDbUtil.test((sessionProvider, tableName) -> {
            ExpressionOperator expression = ExpressionOperator.builder()
                    .addTextTerm(SessionFields.KEY_FIELD, Condition.EQUALS, "TEST")
                    .build();
            final ExpressionCriteria criteria = new ExpressionCriteria(expression);

            final SessionDao sessionDao = new SessionDao(sessionProvider, tableName);

            Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
            Instant min = refTime;
            Instant max = refTime;
            for (int i = 0; i < 100; i++) {
                final Instant start = refTime.plusSeconds(i * 10);
                final Instant end = start.plusSeconds(10);
                if (start.isBefore(min)) {
                    min = start;
                }
                if (end.isAfter(max)) {
                    max = end;
                }

                final Session session = new Session("TEST", start, end, false);
                sessionDao.insert(Collections.singletonList(session));
            }
            for (int i = 0; i >= -10; i--) {
                final Instant start = refTime.plusSeconds(i * 10);
                final Instant end = start.plusSeconds(10);
                if (start.isBefore(min)) {
                    min = start;
                }
                if (end.isAfter(max)) {
                    max = end;
                }

                final Session session = new Session("TEST", start, end, false);
                sessionDao.insert(Collections.singletonList(session));
            }
            assertThat(sessionDao.count()).isEqualTo(110);

            final AtomicInteger count = new AtomicInteger();
            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create(CIKeys.KEY);
            fieldIndex.create(CIKeys.START);
            fieldIndex.create(CIKeys.END);
            fieldIndex.create(CIKeys.TERMINAL);

            final ValDate minTime = ValDate.create(min);
            final ValDate maxTime = ValDate.create(max);
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
}
