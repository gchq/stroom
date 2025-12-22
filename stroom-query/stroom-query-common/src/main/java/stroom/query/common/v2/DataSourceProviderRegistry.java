/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.query.api.datasource.QueryField;
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
            dataSourceProviders.put(provider.getDataSourceType(), provider);
        }
    }

    public Optional<DataSourceProvider> getDataSourceProvider(final String type) {
        return Optional.ofNullable(dataSourceProviders.get(type));
    }

    public Optional<DocRef> fetchDefaultExtractionPipeline(final DocRef dataSourceRef) {
        final DocRef docRef = LegacyDocRefConverter.convert(dataSourceRef);
        return getDataSourceProvider(docRef.getType())
                .flatMap(dsp -> dsp.fetchDefaultExtractionPipeline(docRef));
    }

    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        try {
            final DocRef docRef = LegacyDocRefConverter.convert(criteria.getDataSourceRef());
            final FindFieldCriteria findFieldCriteria = new FindFieldCriteria(
                    criteria.getPageRequest(),
                    criteria.getSortList(),
                    docRef,
                    criteria.getFilter(),
                    criteria.getQueryable());

            return getDataSourceProvider(docRef.getType())
                    .map(dsp -> dsp.getFieldInfo(findFieldCriteria))
                    .orElseGet(() -> ResultPage.createCriterialBasedList(Collections.emptyList(), findFieldCriteria));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            return ResultPage.createCriterialBasedList(Collections.emptyList(), criteria);
        }
    }

    public int getFieldCount(final DocRef dataSourceRef) {
        final DocRef docRef = LegacyDocRefConverter.convert(dataSourceRef);
        return getDataSourceProvider(docRef.getType())
                .map(dataSourceProvider -> dataSourceProvider.getFieldCount(docRef))
                .orElse(0);
    }

    public Optional<String> fetchDocumentation(final DocRef dataSourceRef) {
        final DocRef docRef = LegacyDocRefConverter.convert(dataSourceRef);
        return getDataSourceProvider(docRef.getType())
                .flatMap(dsp -> dsp.fetchDocumentation(docRef));
    }

    public List<DocRef> getDataSourceDocRefs() {
        return dataSourceProviders.values()
                .stream()
                .map(DataSourceProvider::getDataSourceDocRefs)
                .flatMap(List::stream)
                .toList();
    }
}
