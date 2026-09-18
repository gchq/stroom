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

package stroom.config.global.impl.dao;

import stroom.config.global.impl.db.GlobalConfigDbConnProvider;
import stroom.config.impl.db.jooq.tables.QuickFilterHistory;
import stroom.quickfilter.shared.QuickFilterHistoryKey;
import stroom.test.common.util.db.DbTestUtil;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(GlobalConfigTestModule.class)
class TestQuickFilterHistoryDaoImpl {

    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";
    private static final QuickFilterHistoryKey DEPENDENCIES = new QuickFilterHistoryKey("dependencies", null);
    private static final QuickFilterHistoryKey PROPERTIES = new QuickFilterHistoryKey("globalProperties", null);
    private static final QuickFilterHistoryKey TRACES_DOC_1 = new QuickFilterHistoryKey("traces", "doc-1");
    private static final QuickFilterHistoryKey TRACES_DOC_2 = new QuickFilterHistoryKey("traces", "doc-2");
    private static final int LIMIT = 3;

    @Inject
    QuickFilterHistoryDaoImpl dao;
    @Inject
    GlobalConfigDbConnProvider connProvider;

    private long clock = 1_000;

    @BeforeEach
    void setUp() throws SQLException {
        try (final Connection connection = connProvider.getConnection()) {
            DbTestUtil.clearTables(connection, List.of(QuickFilterHistory.QUICK_FILTER_HISTORY.getName()));
        }
    }

    @Test
    void testMostRecentFirst() {
        use(USER_A, DEPENDENCIES, "one");
        use(USER_A, DEPENDENCIES, "two");
        use(USER_A, DEPENDENCIES, "three");

        assertThat(dao.fetch(USER_A, DEPENDENCIES, LIMIT))
                .containsExactly("three", "two", "one");
    }

    @Test
    void testRepeatUseMovesToTopRatherThanDuplicating() {
        use(USER_A, DEPENDENCIES, "one");
        use(USER_A, DEPENDENCIES, "two");
        use(USER_A, DEPENDENCIES, "one");

        assertThat(dao.fetch(USER_A, DEPENDENCIES, LIMIT))
                .containsExactly("one", "two");
    }

    /**
     * The table collation is case-insensitive, matching the parser, so a different casing of the
     * same filter is the same entry and keeps the spelling it was first recorded with.
     */
    @Test
    void testDeduplicationIgnoresCase() {
        use(USER_A, DEPENDENCIES, "Status:Missing");
        use(USER_A, DEPENDENCIES, "status:missing");

        assertThat(dao.fetch(USER_A, DEPENDENCIES, LIMIT))
                .containsExactly("Status:Missing");
    }

    @Test
    void testTrimmedToLimitOnWrite() {
        use(USER_A, DEPENDENCIES, "one");
        use(USER_A, DEPENDENCIES, "two");
        use(USER_A, DEPENDENCIES, "three");
        use(USER_A, DEPENDENCIES, "four");

        // Asked for more than the limit to prove the trim happened in the table, not the read.
        assertThat(dao.fetch(USER_A, DEPENDENCIES, LIMIT * 10))
                .containsExactly("four", "three", "two");
    }

    @Test
    void testContextsAreIndependent() {
        use(USER_A, DEPENDENCIES, "one");
        use(USER_A, PROPERTIES, "two");

        assertThat(dao.fetch(USER_A, DEPENDENCIES, LIMIT)).containsExactly("one");
        assertThat(dao.fetch(USER_A, PROPERTIES, LIMIT)).containsExactly("two");
    }

    /**
     * Same field set, different document: the operations in one Plan B document mean nothing in
     * another, so they do not share.
     */
    @Test
    void testDataSourcesAreIndependent() {
        use(USER_A, TRACES_DOC_1, "operation:login");
        use(USER_A, TRACES_DOC_2, "operation:checkout");

        assertThat(dao.fetch(USER_A, TRACES_DOC_1, LIMIT)).containsExactly("operation:login");
        assertThat(dao.fetch(USER_A, TRACES_DOC_2, LIMIT)).containsExactly("operation:checkout");
    }

    @Test
    void testUsersAreIndependent() {
        use(USER_A, DEPENDENCIES, "mine");
        use(USER_B, DEPENDENCIES, "theirs");

        assertThat(dao.fetch(USER_A, DEPENDENCIES, LIMIT)).containsExactly("mine");
        assertThat(dao.fetch(USER_B, DEPENDENCIES, LIMIT)).containsExactly("theirs");
    }

    @Test
    void testClearIsPerContext() {
        use(USER_A, DEPENDENCIES, "one");
        use(USER_A, PROPERTIES, "two");

        assertThat(dao.clear(USER_A, DEPENDENCIES)).isEqualTo(1);

        assertThat(dao.fetch(USER_A, DEPENDENCIES, LIMIT)).isEmpty();
        assertThat(dao.fetch(USER_A, PROPERTIES, LIMIT)).containsExactly("two");
    }

    @Test
    void testDeleteAllIsPerUser() {
        use(USER_A, DEPENDENCIES, "one");
        use(USER_A, PROPERTIES, "two");
        use(USER_B, DEPENDENCIES, "theirs");

        assertThat(dao.deleteAll(USER_A)).isEqualTo(2);

        assertThat(dao.fetch(USER_A, DEPENDENCIES, LIMIT)).isEmpty();
        assertThat(dao.fetch(USER_A, PROPERTIES, LIMIT)).isEmpty();
        assertThat(dao.fetch(USER_B, DEPENDENCIES, LIMIT)).containsExactly("theirs");
    }

    private void use(final String userUuid, final QuickFilterHistoryKey key, final String filterText) {
        dao.recordUse(userUuid, key, filterText, clock++, LIMIT);
    }
}
