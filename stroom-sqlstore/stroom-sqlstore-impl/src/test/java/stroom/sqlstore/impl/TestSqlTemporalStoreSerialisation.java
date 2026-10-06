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

package stroom.sqlstore.impl;

import stroom.sqlstore.shared.ApplyChangesResult;
import stroom.sqlstore.shared.FetchAtTimeRequest;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies JSON serialisation and deserialisation of {@link SqlTemporalStoreDoc},
 * including round-trip fidelity and tolerance of unknown fields (forward/backward
 * compatibility). Mirrors {@code TestFloorMapSerialisation}.
 */
class TestSqlTemporalStoreSerialisation {

    @Test
    void testSerializationRoundTrip() {
        final SqlTemporalStoreDoc original = SqlTemporalStoreDoc.builder()
                .uuid("store-uuid-123")
                .name("StoreName")
                .version("v1")
                .createTimeMs(1_600_000_000_000L)
                .updateTimeMs(1_600_000_100_000L)
                .createUser("creator")
                .updateUser("updater")
                .description("A temporal store")
                .build();

        final String json = JsonUtil.writeValueAsString(original);
        assertThat(json).isNotNull();

        final SqlTemporalStoreDoc deserialized = JsonUtil.readValue(json, SqlTemporalStoreDoc.class);

        // The doc round-trips exactly (relies on the class's equals()).
        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.getName()).isEqualTo("StoreName");
        assertThat(deserialized.getDescription()).isEqualTo("A temporal store");
        assertThat(deserialized.getType()).isEqualTo(SqlTemporalStoreDoc.TYPE);
    }

    @Test
    void testSerializationRoundTrip_nullDescription() {
        final SqlTemporalStoreDoc original = SqlTemporalStoreDoc.builder()
                .uuid("store-uuid-123")
                .name("StoreName")
                .build();

        final String json = JsonUtil.writeValueAsString(original);
        final SqlTemporalStoreDoc deserialized = JsonUtil.readValue(json, SqlTemporalStoreDoc.class);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.getDescription()).isNull();
    }

    @Test
    void testUnknownFieldsIgnored() {
        // A future/foreign field must not break deserialisation.
        final String json = "{"
                + "\"type\":\"" + SqlTemporalStoreDoc.TYPE + "\","
                + "\"uuid\":\"store-uuid-123\","
                + "\"name\":\"StoreName\","
                + "\"description\":\"desc\","
                + "\"someRemovedField\":\"legacy\""
                + "}";

        final SqlTemporalStoreDoc deserialized = JsonUtil.readValue(json, SqlTemporalStoreDoc.class);

        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getName()).isEqualTo("StoreName");
        assertThat(deserialized.getDescription()).isEqualTo("desc");
    }

    @Test
    void testApplyChangesResultRoundTrip() {
        final ApplyChangesResult original = new ApplyChangesResult(false, "Rolled back");

        final ApplyChangesResult deserialized = JsonUtil.readValue(
                JsonUtil.writeValueAsString(original), ApplyChangesResult.class);

        assertThat(deserialized).isEqualTo(original);
    }

    /// A result with no success flag must not be mistaken for a success.
    @Test
    void testApplyChangesResultMissingSuccessIsFailure() {
        final ApplyChangesResult deserialized = JsonUtil.readValue(
                "{\"errorMessage\":\"boom\"}", ApplyChangesResult.class);

        assertThat(deserialized.isSuccess()).isFalse();
        assertThat(deserialized.getErrorMessage()).isEqualTo("boom");
        assertThat(new ApplyChangesResult(null, null).isSuccess()).isFalse();
    }

    @Test
    void testFetchAtTimeRequestRoundTrip() {
        final FetchAtTimeRequest original = new FetchAtTimeRequest("myMap", 1_600_000_000_000L);

        final FetchAtTimeRequest deserialized = JsonUtil.readValue(
                JsonUtil.writeValueAsString(original), FetchAtTimeRequest.class);

        assertThat(deserialized).isEqualTo(original);
    }

    /// A request with no timeTo is held as 0 (the epoch).
    @Test
    void testFetchAtTimeRequestMissingTimeToIsZero() {
        final FetchAtTimeRequest deserialized = JsonUtil.readValue(
                "{\"mapName\":\"myMap\"}", FetchAtTimeRequest.class);

        assertThat(deserialized.getMapName()).isEqualTo("myMap");
        assertThat(deserialized.getTimeTo()).isZero();
        assertThat(new FetchAtTimeRequest("myMap", null).getTimeTo()).isZero();
    }
}
