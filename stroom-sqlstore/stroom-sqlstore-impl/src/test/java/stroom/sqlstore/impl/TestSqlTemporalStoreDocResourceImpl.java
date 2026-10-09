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

import stroom.docstore.api.DocumentResourceHelper;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.sqlstore.shared.SqlTemporalStoreDocResource;
import stroom.test.common.util.test.AbstractResourceTest;
import stroom.util.shared.ResourcePaths;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REST-layer tests for {@link SqlTemporalStoreDocResourceImpl} — the document
 * fetch/update endpoints for the {@link SqlTemporalStoreDoc}.
 *
 * <p>Backed by a mocked {@link SqlTemporalStoreDocStore} and
 * {@link DocumentResourceHelper}; no docstore or database is involved.</p>
 */
class TestSqlTemporalStoreDocResourceImpl extends AbstractResourceTest<SqlTemporalStoreDocResource> {

    private static final String UUID = "store-uuid-123";

    @Mock
    private SqlTemporalStoreDocStore mockDocStore;
    @Mock
    private DocumentResourceHelper mockDocumentResourceHelper;

    @Override
    public SqlTemporalStoreDocResource getRestResource() {
        return new SqlTemporalStoreDocResourceImpl(() -> mockDocStore, () -> mockDocumentResourceHelper);
    }

    @Override
    public String getResourceBasePath() {
        return "/sqlTemporalStoreDoc" + ResourcePaths.V1;
    }

    @Test
    void testFetch() {
        final SqlTemporalStoreDoc doc = doc(UUID, "StoreName");
        when(mockDocumentResourceHelper.read(any(), any())).thenReturn(doc);

        doGetTest(UUID, SqlTemporalStoreDoc.class, doc);
    }

    @Test
    void testUpdate_uuidMatches_delegates() {
        final SqlTemporalStoreDoc doc = doc(UUID, "StoreName");
        when(mockDocumentResourceHelper.update(any(), any())).thenReturn(doc);

        doPutTest(UUID, doc, SqlTemporalStoreDoc.class, doc);
    }

    /**
     * The resource rejects an update whose body UUID does not match the path
     * UUID, and must not delegate to the store.
     */
    @Test
    void testUpdate_uuidMismatch_isRejected() {
        final SqlTemporalStoreDoc doc = doc("a-different-uuid", "StoreName");

        assertThatThrownBy(() -> doPutTest(UUID, doc, SqlTemporalStoreDoc.class, doc))
                .isInstanceOf(RuntimeException.class);

        verify(mockDocumentResourceHelper, never()).update(any(), any());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static SqlTemporalStoreDoc doc(final String uuid, final String name) {
        return SqlTemporalStoreDoc.builder()
                .uuid(uuid)
                .name(name)
                .build();
    }
}
