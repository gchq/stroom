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

package stroom.ai.impl.tools.query;

import stroom.docref.DocRef;
import stroom.query.api.datasource.DataSourceProvider;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataSourceAccess {

    private static final DocRef FIRST = new DocRef(ElasticIndexDoc.TYPE, "first-uuid", "First");
    private static final DocRef SECOND = new DocRef(ElasticIndexDoc.TYPE, "second-uuid", "Second");

    private final DataSourceProviderRegistry registry = Mockito.mock(DataSourceProviderRegistry.class);
    private final DataSourceProvider provider = Mockito.mock(DataSourceProvider.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final DataSourceAccess access = new DataSourceAccess(() -> registry, securityContext);

    @Test
    void canUseAnyIsTrueWhenTheUserCanUseOneOfTheTypesDocs() {
        Mockito.when(registry.getDataSourceProvider(ElasticIndexDoc.TYPE)).thenReturn(Optional.of(provider));
        Mockito.when(provider.getDataSourceDocRefs()).thenReturn(List.of(FIRST, SECOND));
        Mockito.when(securityContext.hasDocumentPermission(SECOND, DocumentPermission.USE)).thenReturn(true);

        assertThat(access.canUseAny(ElasticIndexDoc.TYPE)).isTrue();
    }

    @Test
    void canUseAnyIsFalseWhenTheUserCanUseNoneOfThem() {
        Mockito.when(registry.getDataSourceProvider(ElasticIndexDoc.TYPE)).thenReturn(Optional.of(provider));
        Mockito.when(provider.getDataSourceDocRefs()).thenReturn(List.of(FIRST, SECOND));

        assertThat(access.canUseAny(ElasticIndexDoc.TYPE)).isFalse();
    }

    @Test
    void canUseAnyIsFalseWithNoDocsNoProviderOrAFailure() {
        Mockito.when(registry.getDataSourceProvider(ElasticIndexDoc.TYPE)).thenReturn(Optional.of(provider));
        Mockito.when(provider.getDataSourceDocRefs()).thenReturn(null);
        assertThat(access.canUseAny(ElasticIndexDoc.TYPE)).isFalse();

        Mockito.when(registry.getDataSourceProvider(ElasticIndexDoc.TYPE)).thenReturn(Optional.empty());
        assertThat(access.canUseAny(ElasticIndexDoc.TYPE)).isFalse();

        Mockito.when(registry.getDataSourceProvider(ElasticIndexDoc.TYPE)).thenThrow(new IllegalStateException());
        assertThat(access.canUseAny(ElasticIndexDoc.TYPE)).isFalse();
    }

    @Test
    void canUseAnyStopsAtTheFirstDocTheUserCanUse() {
        Mockito.when(registry.getDataSourceProvider(ElasticIndexDoc.TYPE)).thenReturn(Optional.of(provider));
        Mockito.when(provider.getDataSourceDocRefs()).thenReturn(List.of(FIRST, SECOND));
        Mockito.when(securityContext.hasDocumentPermission(FIRST, DocumentPermission.USE)).thenReturn(true);

        assertThat(access.canUseAny(ElasticIndexDoc.TYPE)).isTrue();
        Mockito.verify(securityContext, Mockito.never()).hasDocumentPermission(SECOND, DocumentPermission.USE);
    }
}
