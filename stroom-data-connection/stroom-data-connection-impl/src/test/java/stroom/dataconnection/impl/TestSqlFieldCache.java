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

package stroom.dataconnection.impl;

import stroom.cache.impl.CacheManagerImpl;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.query.api.datasource.FieldType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestSqlFieldCache {

    private static final List<SqlColumn> COLUMNS = List.of(new SqlColumn("id", FieldType.INTEGER, Types.INTEGER));
    private static final String SQL = "select id from staff";

    private final CacheManagerImpl cacheManager = new CacheManagerImpl();
    private final SqlFieldCache fieldCache = new SqlFieldCache(cacheManager, DataConnectionConfig::new);
    private final AtomicInteger describeCount = new AtomicInteger();

    @AfterEach
    void tearDown() {
        cacheManager.close();
    }

    private static DataConnectionDoc doc(final String version) {
        return DataConnectionDoc.builder()
                .uuid("uuid-1")
                .version(version)
                .build();
    }

    private List<SqlColumn> describe(final String sql) {
        describeCount.incrementAndGet();
        return COLUMNS;
    }

    @Test
    void columnsAreDescribedOnceForTheSameSql() {
        assertThat(fieldCache.get(doc("v1"), SQL, this::describe)).isEqualTo(COLUMNS);
        assertThat(fieldCache.get(doc("v1"), SQL, this::describe)).isEqualTo(COLUMNS);

        assertThat(describeCount).hasValue(1);
    }

    @Test
    void differentSqlIsDescribedAgain() {
        fieldCache.get(doc("v1"), SQL, this::describe);
        fieldCache.get(doc("v1"), SQL + " where id > 1", this::describe);

        assertThat(describeCount).hasValue(2);
    }

    @Test
    void newDocumentVersionIsDescribedAgain() {
        // The connection may have changed, so columns found before the change are not used.
        fieldCache.get(doc("v1"), SQL, this::describe);
        fieldCache.get(doc("v2"), SQL, this::describe);

        assertThat(describeCount).hasValue(2);
    }

    @Test
    void columnsFromARunAreUsed() {
        fieldCache.put(doc("v1"), SQL, COLUMNS);

        assertThat(fieldCache.getIfPresent(doc("v1"), SQL)).hasValue(COLUMNS);
        assertThat(fieldCache.get(doc("v1"), SQL, this::describe)).isEqualTo(COLUMNS);
        assertThat(describeCount).hasValue(0);
    }

    @Test
    void clearForgetsEverything() {
        fieldCache.put(doc("v1"), SQL, COLUMNS);

        fieldCache.clear();

        assertThat(fieldCache.getIfPresent(doc("v1"), SQL)).isEmpty();
    }
}
