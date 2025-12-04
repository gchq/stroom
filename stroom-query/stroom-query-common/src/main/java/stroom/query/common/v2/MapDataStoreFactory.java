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

import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.TableSettings;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ref.ErrorConsumer;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Map;

public class MapDataStoreFactory implements DataStoreFactory {

    private final Provider<SearchResultStoreConfig> resultStoreConfigProvider;

    @Inject
    public MapDataStoreFactory(final Provider<SearchResultStoreConfig> resultStoreConfigProvider) {
        this.resultStoreConfigProvider = resultStoreConfigProvider;
    }

    @Override
    public DataStore create(final ExpressionContext expressionContext,
                            final SearchRequestSource searchRequestSource,
                            final QueryKey queryKey,
                            final String componentId,
                            final TableSettings tableSettings,
                            final FieldIndex fieldIndex,
                            final Map<String, String> paramMap,
                            final DataStoreSettings dataStoreSettings,
                            final ErrorConsumer errorConsumer) {
        final SearchResultStoreConfig resultStoreConfig = resultStoreConfigProvider.get();
        return new MapDataStore(
                componentId,
                tableSettings,
                expressionContext,
                fieldIndex,
                paramMap,
                dataStoreSettings,
                errorConsumer,
                resultStoreConfig.getMapConfig());
    }

    @Override
    public StoreSizeSummary getTotalSizeOnDisk() {
        // Heap based so no disk used
        // No way to get the number of stores
        return new StoreSizeSummary(0, -1);
    }
}
