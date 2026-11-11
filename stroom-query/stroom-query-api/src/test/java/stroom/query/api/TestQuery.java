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

package stroom.query.api;

import stroom.docref.DocRef;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestQuery {

    private static final DocRef DATA_SOURCE = new DocRef("ExternalDataSource", "uuid-1", "HR Database");
    private static final String SQL = "select id, name\nfrom staff\nwhere dept = 'Sales'";

    @Test
    void nativeQueryIsNullWhenNotSet() {
        final Query query = Query.builder()
                .dataSource(DATA_SOURCE)
                .build();

        assertThat(query.getNativeQuery()).isNull();
    }

    @Test
    void builderSetsNativeQuery() {
        final Query query = Query.builder()
                .dataSource(DATA_SOURCE)
                .nativeQuery(SQL)
                .build();

        assertThat(query.getNativeQuery()).isEqualTo(SQL);
    }

    @Test
    void copyKeepsNativeQuery() {
        final Query query = Query.builder()
                .dataSource(DATA_SOURCE)
                .nativeQuery(SQL)
                .build();

        final Query copy = query.copy()
                .timeRange(new TimeRange("Today", "day()", "day() + 1d"))
                .build();

        assertThat(copy.getNativeQuery()).isEqualTo(SQL);
    }

    @Test
    void nativeQueryTakesPartInEquality() {
        final Query withSql = Query.builder()
                .dataSource(DATA_SOURCE)
                .nativeQuery(SQL)
                .build();
        final Query withOtherSql = withSql.copy()
                .nativeQuery("select 1")
                .build();
        final Query sameSql = withSql.copy().build();

        assertThat(withSql).isNotEqualTo(withOtherSql);
        assertThat(withSql).isEqualTo(sameSql);
        assertThat(withSql.hashCode()).isEqualTo(sameSql.hashCode());
    }

    @Test
    void nativeQuerySurvivesJson() {
        final Query query = Query.builder()
                .dataSource(DATA_SOURCE)
                .nativeQuery(SQL)
                .build();

        final String json = JsonUtil.writeValueAsString(query);
        final Query fromJson = JsonUtil.readValue(json, Query.class);

        assertThat(fromJson).isEqualTo(query);
        assertThat(fromJson.getNativeQuery()).isEqualTo(SQL);
    }

    @Test
    void jsonOmitsNativeQueryWhenNotSet() {
        // Keeps the JSON of every existing query, stored or sent, unchanged.
        final Query query = Query.builder()
                .dataSource(DATA_SOURCE)
                .build();

        final String json = JsonUtil.writeValueAsString(query);

        assertThat(json).doesNotContain("nativeQuery");
    }

    @Test
    void jsonWithoutNativeQueryStillReads() {
        final String json = """
                {"dataSource":{"type":"ExternalDataSource","uuid":"uuid-1","name":"HR Database"}}""";

        final Query query = JsonUtil.readValue(json, Query.class);

        assertThat(query.getDataSource()).isEqualTo(DATA_SOURCE);
        assertThat(query.getNativeQuery()).isNull();
    }
}
