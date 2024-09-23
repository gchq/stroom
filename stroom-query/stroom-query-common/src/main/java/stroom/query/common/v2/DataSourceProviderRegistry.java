/*
 * Copyright 2024 Crown Copyright
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

import stroom.datasource.api.v2.DataSourceProvider;
import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@SuppressWarnings("unused")
public class DataSourceProviderRegistry {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataSourceProviderRegistry.class);
    private final Map<String, DataSourceProvider> dataSourceProviders = new ConcurrentHashMap<>();

    @Inject
    public DataSourceProviderRegistry(final Set<DataSourceProvider> providers) {
        for (final DataSourceProvider provider : providers) {
            dataSourceProviders.put(provider.getType(), provider);
        }
    }

    public Optional<DataSourceProvider> getDataSourceProvider(final String type) {
        return Optional.ofNullable(dataSourceProviders.get(type));
    }

    public DocRef fetchDefaultExtractionPipeline(final DocRef dataSourceRef) {
        return getDataSourceProvider(dataSourceRef.getType())
                .map(dsp -> dsp.fetchDefaultExtractionPipeline(dataSourceRef))
                .orElse(null);
    }

    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        try {
            return getDataSourceProvider(criteria.getDataSourceRef().getType())
                    .map(dsp -> dsp.getFieldInfo(criteria))
                    .orElseGet(() -> ResultPage.createCriterialBasedList(Collections.emptyList(), criteria));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            return ResultPage.createCriterialBasedList(Collections.emptyList(), criteria);
        }
    }

    public int getFieldCount(final DocRef docRef) {
        return getDataSourceProvider(docRef.getType())
                .map(dataSourceProvider -> dataSourceProvider.getFieldCount(docRef))
                .orElse(0);
    }

    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return getDataSourceProvider(docRef.getType())
                .flatMap(dsp -> dsp.fetchDocumentation(docRef));
    }

    public Set<String> getTypes() {
        return dataSourceProviders.keySet();
    }

    public List<DocRef> list() {
        return dataSourceProviders.values()
                .stream()
                .map(DataSourceProvider::list)
                .flatMap(List::stream)
                .toList();
    }
}
