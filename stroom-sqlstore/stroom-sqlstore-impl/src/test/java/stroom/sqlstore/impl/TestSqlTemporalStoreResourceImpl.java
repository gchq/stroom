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

package stroom.sqlstore.impl;

import stroom.docref.DocRef;
import stroom.sqlstore.shared.ApplyChangesRequest;
import stroom.sqlstore.shared.ApplyChangesResult;
import stroom.sqlstore.shared.ChangeOperation;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.sqlstore.shared.SqlTemporalStoreResource;
import stroom.sqlstore.shared.TemporalStoreTimeRange;
import stroom.test.common.util.test.AbstractResourceTest;
import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * REST-layer tests for {@link SqlTemporalStoreResourceImpl}.
 *
 * <p>Drives the resource over an in-memory Jersey client ({@link AbstractResourceTest})
 * with a mocked {@link UpdatableSqlTemporalStore} behind it, verifying that each
 * endpoint routes to the right store method and that the request/response bodies
 * serialise across the wire. No database is involved.</p>
 */
class TestSqlTemporalStoreResourceImpl extends AbstractResourceTest<SqlTemporalStoreResource> {

    private static final String MAP = "myMap";

    @Mock
    private UpdatableSqlTemporalStore mockStore;

    @Override
    public SqlTemporalStoreResource getRestResource() {
        return new SqlTemporalStoreResourceImpl(() -> mockStore);
    }

    @Override
    public String getResourceBasePath() {
        return SqlTemporalStoreResource.BASE_PATH;
    }

    @Test
    void testCreate() {
        final TemporalEntry entry = entry("gate", 1000L, "{\"v\":1}");
        when(mockStore.create(any())).thenReturn(entry);

        doPostTest("entry", entry, TemporalEntry.class, entry);
    }

    @Test
    void testUpdate() {
        final TemporalEntry entry = entry("gate", 1000L, "{\"v\":2}");
        when(mockStore.update(any())).thenReturn(entry);

        doPutTest("entry", entry, TemporalEntry.class, entry);
    }

    @Test
    void testDelete() {
        when(mockStore.delete(any())).thenReturn(true);

        doPostTest("entry/delete", id("gate", 1000L), Boolean.class, Boolean.TRUE);
    }

    @Test
    void testCount() {
        when(mockStore.count(any())).thenReturn(42L);

        doPostTest("count", docRef(), Long.class, 42L);
    }

    @Test
    void testGetTimeRange() {
        final TemporalStoreTimeRange range = new TemporalStoreTimeRange(1000L, 5000L);
        when(mockStore.getTimeRange(any())).thenReturn(range);

        doPostTest("timeRange", MAP, TemporalStoreTimeRange.class, range);
    }

    @Test
    void testApplyChanges() {
        final ApplyChangesRequest request = new ApplyChangesRequest(
                List.of(ChangeOperation.upsert(entry("gate", 1000L, "{}"))));
        final ApplyChangesResult result = new ApplyChangesResult(true, null);
        when(mockStore.applyChanges(any())).thenReturn(result);

        doPostTest("applyChanges", request, ApplyChangesResult.class, result);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static TemporalEntry entry(final String key, final long time, final String value) {
        return new TemporalEntry(MAP, key, time, value);
    }

    private static TemporalEntryId id(final String key, final long time) {
        return new TemporalEntryId(MAP, key, time);
    }

    private static DocRef docRef() {
        return DocRef.builder()
                .type(SqlTemporalStoreDoc.TYPE)
                .uuid("store-uuid")
                .name(MAP)
                .build();
    }
}
