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

package stroom.query.common.v2;

import stroom.docref.DocRef;
import stroom.query.api.datasource.DataSourceProvider;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataSourceProviderRegistry {

    private static final DocRef SQL_SOURCE = new DocRef("DataConnection", "uuid-1", "HR");
    private static final DocRef INDEX = new DocRef("Index", "uuid-2", "Events");

    private final DataSourceProvider sqlProvider = provider("DataConnection", true);
    private final DataSourceProvider indexProvider = provider("Index", false);
    private final DataSourceProviderRegistry registry = new DataSourceProviderRegistry(
            Set.of(sqlProvider, indexProvider));

    private static DataSourceProvider provider(final String type, final boolean takesNativeQuery) {
        final DataSourceProvider provider = Mockito.mock(DataSourceProvider.class);
        Mockito.when(provider.getDataSourceType()).thenReturn(type);
        Mockito.when(provider.takesNativeQuery(Mockito.any())).thenReturn(takesNativeQuery);
        Mockito.when(provider.getFieldInfo(Mockito.any())).thenReturn(ResultPage.createUnboundedList(List.of()));
        return provider;
    }

    @Test
    void nativeQueryIsPassedToTheProvider() {
        // The fields of a Data Connection are the columns of the SQL, so the SQL must reach it.
        registry.getFieldInfo(new FindFieldCriteria(
                PageRequest.unlimited(), FindFieldCriteria.DEFAULT_SORT_LIST, SQL_SOURCE, "na", true,
                "select name from staff"));

        final ArgumentCaptor<FindFieldCriteria> captor = ArgumentCaptor.forClass(FindFieldCriteria.class);
        Mockito.verify(sqlProvider).getFieldInfo(captor.capture());
        assertThat(captor.getValue().getNativeQuery()).isEqualTo("select name from staff");
        assertThat(captor.getValue().getFilter()).isEqualTo("na");
        assertThat(captor.getValue().getQueryable()).isTrue();
    }

    @Test
    void takesNativeQueryAsksTheProviderAboutTheDocument() {
        // Some types of Data Connection take SQL and some do not, so the provider needs the document.
        assertThat(registry.takesNativeQuery(SQL_SOURCE)).isTrue();
        assertThat(registry.takesNativeQuery(INDEX)).isFalse();
        Mockito.verify(sqlProvider).takesNativeQuery(SQL_SOURCE);
    }

    @Test
    void minRefreshIntervalAsksTheProviderForTheType() {
        final SimpleDuration fiveMinutes = new SimpleDuration(5L, TimeUnit.MINUTES);
        Mockito.when(sqlProvider.fetchMinRefreshInterval(SQL_SOURCE)).thenReturn(Optional.of(fiveMinutes));

        assertThat(registry.fetchMinRefreshInterval(SQL_SOURCE)).hasValue(fiveMinutes);
        assertThat(registry.fetchMinRefreshInterval(INDEX)).isEmpty();
    }

    @Test
    void unknownTypeHasNoMinRefreshInterval() {
        assertThat(registry.fetchMinRefreshInterval(new DocRef("NoSuchType", "uuid-3", "Other"))).isEmpty();
    }

    @Test
    void unknownTypeTakesNoNativeQuery() {
        assertThat(registry.takesNativeQuery(new DocRef("NoSuchType", "uuid-3", "Other"))).isFalse();
    }
}
