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

package stroom.db.util;

import org.assertj.core.api.Assertions;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import java.sql.SQLTransactionRollbackException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestJooqUtil {

    @Test
    void testWithDeadlockRetry_success() {
        final AtomicInteger attempt = new AtomicInteger(0);
        final AtomicBoolean success = new AtomicBoolean(false);
        JooqUtil.withDeadlockRetries(
                () -> {
                    attempt.incrementAndGet();
                    // Nothing to do
                    success.set(true);
                },
                () -> "Do stuff");
        assertThat(attempt)
                .hasValue(1);
        assertThat(success)
                .isTrue();
    }

    @Test
    void testWithDeadlockRetry_deadlock_success() {
        final AtomicInteger attempt = new AtomicInteger(0);
        final AtomicBoolean success = new AtomicBoolean(false);

        JooqUtil.withDeadlockRetries(
                () -> {
                    attempt.incrementAndGet();
                    if (attempt.get() <= 2) {
                        throwDeadlock();
                    }
                    success.set(true);
                },
                () -> "Do stuff");
        assertThat(attempt)
                .hasValue(3);
        assertThat(success)
                .isTrue();
    }

    @Test
    void testWithDeadlockRetry_deadlock_fail() {
        final AtomicInteger attempt = new AtomicInteger(0);
        final AtomicBoolean success = new AtomicBoolean(false);

        Assertions.assertThatThrownBy(
                        () -> JooqUtil.withDeadlockRetries(
                                () -> {
                                    attempt.incrementAndGet();
                                    throwDeadlock();
                                },
                                () -> "Do stuff"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("deadlock");
        assertThat(attempt)
                .hasValue(JooqUtil.MAX_DEADLOCK_RETRY_ATTEMPTS);
        assertThat(success)
                .isFalse();
    }

    private void throwDeadlock() {
        throw new DataAccessException(
                "foo",
                new SQLTransactionRollbackException("a deadlock"));
    }
}
