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

import stroom.job.impl.db.JobDbConnProvider;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static stroom.job.impl.db.jooq.Tables.JOB;

/**
 * More of a manual test, need DEBUG set on {@link SlowQueryExecuteListener}
 */
public class TestSlowQueryExecuteListener extends AbstractCoreIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSlowQueryExecuteListener.class);

    public static final String JOB_NAME = "Test job";

    @Inject
    private JobDbConnProvider jobDbConnProvider;

    @BeforeAll
    static void beforeAll() {
        SlowQueryExecuteListener.setSlowQueryDurationThreshold(Duration.ZERO);
    }


    @BeforeEach
    void beforeEach() {
        JooqUtil.context(jobDbConnProvider, context -> {
            context.deleteFrom(JOB)
                    .where(JOB.NAME.like(JOB_NAME + "%"));
        });

        final List<String> names = JooqUtil.contextResult(jobDbConnProvider, context -> context
                .select(JOB.NAME)
                .from(JOB)
                .fetch(JOB.NAME));

        Assertions.assertThat(names)
                .isEmpty();
    }

    @AfterAll
    static void afterAll() {
        SlowQueryExecuteListener.resetSlowQueryDurationThreshold();
    }

    @Test
    void testSelect() {

        final List<String> names = JooqUtil.contextResult(jobDbConnProvider, context -> context
                .select(JOB.NAME)
                .from(JOB)
                .fetch(JOB.NAME));

    }

    @Test
    void testInsert() {

        final int count = JooqUtil.contextResult(jobDbConnProvider, context -> context
                .insertInto(
                        JOB,
                        JOB.VERSION,
                        JOB.CREATE_TIME_MS,
                        JOB.CREATE_USER,
                        JOB.UPDATE_TIME_MS,
                        JOB.UPDATE_USER,
                        JOB.NAME)
                .values(1, 123L, "user1", 123L, "user1", JOB_NAME + "_1")
                .execute());
    }

    @Test
    void testMultiRowInsert() {

        final int count = JooqUtil.contextResult(jobDbConnProvider, context -> context
                .insertInto(
                        JOB,
                        JOB.VERSION,
                        JOB.CREATE_TIME_MS,
                        JOB.CREATE_USER,
                        JOB.UPDATE_TIME_MS,
                        JOB.UPDATE_USER,
                        JOB.NAME)
                .values(1, 123L, "user1", 123L, "user1", JOB_NAME + "_1")
                .values(1, 123L, "user1", 123L, "user1", JOB_NAME + "_2")
                .execute());
    }

    @Test
    void testBatchInsert_same() {

        JooqUtil.context(jobDbConnProvider, context -> context
                .batch(
                        context.insertInto(
                                        JOB,
                                        JOB.VERSION,
                                        JOB.CREATE_TIME_MS,
                                        JOB.CREATE_USER,
                                        JOB.UPDATE_TIME_MS,
                                        JOB.UPDATE_USER,
                                        JOB.NAME)
                                .values((Integer) null, null, null, null, null, null))
                .bind(1, 123L, "user1", 123L, "user1", JOB_NAME + "_1")
                .bind(1, 123L, "user1", 123L, "user1", JOB_NAME + "_2")
                .execute());

        final List<String> names = JooqUtil.contextResult(jobDbConnProvider, context -> context
                .select(JOB.NAME)
                .from(JOB)
                .fetch(JOB.NAME));

        Assertions.assertThat(names)
                        .containsExactly(JOB_NAME + "_1", JOB_NAME + "_2");

        LOGGER.info("\n{}", AsciiTable.fromCollection(names));
    }

    @Test
    void testBatchInsert_same2() {

        JooqUtil.context(jobDbConnProvider, context -> context
                .batch(
                        context.insertInto(
                                        JOB,
                                        JOB.VERSION,
                                        JOB.CREATE_TIME_MS,
                                        JOB.CREATE_USER,
                                        JOB.UPDATE_TIME_MS,
                                        JOB.UPDATE_USER,
                                        JOB.NAME)
                                .values(1, 123L, "user1", 123L, "user1", JOB_NAME + "_1"),

                        context.insertInto(
                                        JOB,
                                        JOB.VERSION,
                                        JOB.CREATE_TIME_MS,
                                        JOB.CREATE_USER,
                                        JOB.UPDATE_TIME_MS,
                                        JOB.UPDATE_USER,
                                        JOB.NAME)
                                .values(1, 123L, "user1", 123L, "user1", JOB_NAME + "_2")
                )
                .execute());

        final List<String> names = JooqUtil.contextResult(jobDbConnProvider, context -> context
                .select(JOB.NAME)
                .from(JOB)
                .fetch(JOB.NAME));

        Assertions.assertThat(names)
                .containsExactly(JOB_NAME + "_1", JOB_NAME + "_2");

        LOGGER.info("\n{}", AsciiTable.fromCollection(names));
    }

    @Test
    void testBatchInsert_different() {

        JooqUtil.context(jobDbConnProvider, context -> context
                .batch(
                        context.insertInto(
                                        JOB,
                                        JOB.VERSION,
                                        JOB.CREATE_TIME_MS,
                                        JOB.CREATE_USER,
                                        JOB.UPDATE_TIME_MS,
                                        JOB.UPDATE_USER,
                                        JOB.NAME)
                                .values(1, 123L, "user1", 123L, "user1", JOB_NAME + "_1"),

                        context.update(JOB)
                                .set(JOB.NAME, JOB_NAME + "_1_modified")
                                .where(JOB.NAME.eq(JOB_NAME + "_1"))
                )
                .execute());

        final List<String> names = JooqUtil.contextResult(jobDbConnProvider, context -> context
                .select(JOB.NAME)
                .from(JOB)
                .fetch(JOB.NAME));

        Assertions.assertThat(names)
                .containsExactly(JOB_NAME + "_1_modified");

        LOGGER.info("\n{}", AsciiTable.fromCollection(names));
    }
}
