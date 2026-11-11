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

package stroom.view.impl;

import stroom.docref.DocRef;
import stroom.query.api.Query;
import stroom.query.api.SearchRequest;
import stroom.query.common.v2.SearchProvider;
import stroom.query.common.v2.SearchProviderRegistry;
import stroom.view.api.ViewStore;
import stroom.view.shared.ViewDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestViewSearchProvider {

    private static final DocRef VIEW = new DocRef(ViewDoc.TYPE, "view-uuid", "HR view");
    private static final DocRef EXTERNAL_SOURCE = new DocRef("ExternalDataSource", "external-uuid", "HR");
    private static final DocRef OTHER_SQL_SOURCE = new DocRef("OtherSqlSource", "other-uuid", "Sales");

    private static void assertViewOfSourceIsRefused(final DocRef dataSource, final boolean takesSql) {
        final ViewStore viewStore = Mockito.mock(ViewStore.class);
        Mockito.when(viewStore.readDocument(VIEW)).thenReturn(ViewDoc.builder()
                .uuid(VIEW.getUuid())
                .name(VIEW.getName())
                .dataSource(dataSource)
                .build());
        final SearchProvider delegate = Mockito.mock(SearchProvider.class);
        Mockito.when(delegate.takesNativeQuery(Mockito.any())).thenReturn(takesSql);
        final SearchProviderRegistry registry = Mockito.mock(SearchProviderRegistry.class);
        Mockito.when(registry.getSearchProvider(dataSource)).thenReturn(Optional.of(delegate));
        final ViewSearchProvider provider = new ViewSearchProvider(
                viewStore, () -> registry, null, null, null, null);

        assertThatThrownBy(() -> provider.createResultStore(SearchRequest.builder()
                .query(Query.builder()
                        .dataSource(VIEW)
                        .build())
                .build()))
                .hasMessageContaining("cannot be used through a view")
                .hasMessageContaining("'" + dataSource.getName() + "'");
        Mockito.verify(delegate, Mockito.never()).createResultStore(Mockito.any());
    }

    @Test
    void viewOfADataSourceThatRunsSqlIsRefused() {
        // A view adds extraction that SQL rows do not use, and cannot pass the SQL on.
        assertViewOfSourceIsRefused(OTHER_SQL_SOURCE, true);
    }

    @Test
    void viewOfAnExternalDataSourceIsRefusedEvenWithoutSql() {
        // A CSV External Data Source takes no SQL, but its rows have no use for a view's extraction.
        assertViewOfSourceIsRefused(EXTERNAL_SOURCE, false);
    }
}
