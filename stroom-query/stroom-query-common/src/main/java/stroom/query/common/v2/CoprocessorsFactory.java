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
import stroom.query.api.DateTimeSettings;
import stroom.query.api.Param;
import stroom.query.api.ParamUtil;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.TableSettings;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class CoprocessorsFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CoprocessorsFactory.class);

    private final DataStoreFactory dataStoreFactory;
    private final ExpressionContextFactory expressionContextFactory;
    private final SizesProvider sizesProvider;

    @Inject
    public CoprocessorsFactory(final DataStoreFactory dataStoreFactory,
                               final ExpressionContextFactory expressionContextFactory,
                               final SizesProvider sizesProvider) {
        this.dataStoreFactory = dataStoreFactory;
        this.expressionContextFactory = expressionContextFactory;
        this.sizesProvider = sizesProvider;
    }

    public List<CoprocessorSettings> createSettings(final SearchRequest searchRequest) {
        return createSettings(searchRequest, null);
    }

    public List<CoprocessorSettings> createSettings(final SearchRequest searchRequest,
                                                    final DocRef defaultExtractionPipeline) {
        // Group common settings.
        final Map<TableSettings, Set<String>> groupMap = new HashMap<>();
        for (final ResultRequest resultRequest : searchRequest.getResultRequests()) {
            if (!NullSafe.isEmptyCollection(resultRequest.getMappings())) {
                final String componentId = resultRequest.getComponentId();
                TableSettings tableSettings = resultRequest.getMappings().get(0);
                if (tableSettings != null) {
                    if (tableSettings.getExtractionPipeline() == null
                        && defaultExtractionPipeline != null) {
                        LOGGER.debug("Using defaultExtractionPipeline {} on tableSettings {}",
                                defaultExtractionPipeline, tableSettings);
                        tableSettings = tableSettings.copy()
                                .extractionPipeline(defaultExtractionPipeline)
                                .build();
                    }
                    final Set<String> set = groupMap.computeIfAbsent(tableSettings, k -> new HashSet<>());
                    set.add(componentId);
                }
            }
        }

        final List<CoprocessorSettings> coprocessorSettings = new ArrayList<>(groupMap.size());
        int i = 0;
        for (final Entry<TableSettings, Set<String>> entry : groupMap.entrySet()) {
            final TableSettings tableSettings = entry.getKey();
            final Set<String> componentIds = entry.getValue();
            final String[] componentIdArray = componentIds.stream().sorted().toArray(String[]::new);
            coprocessorSettings.add(new TableCoprocessorSettings(i++, componentIdArray, tableSettings));
        }

        return coprocessorSettings;
    }

    public CoprocessorsImpl create(final SearchRequest searchRequest,
                                   final DataStoreSettings dataStoreSettings) {
        final List<CoprocessorSettings> coprocessorSettingsList = createSettings(searchRequest);
        return create(
                searchRequest.getSearchRequestSource(),
                searchRequest.getDateTimeSettings(),
                searchRequest.getKey(),
                coprocessorSettingsList,
                searchRequest.getQuery().getParams(),
                dataStoreSettings);
    }

    public CoprocessorsImpl create(final SearchRequestSource searchRequestSource,
                                   final DateTimeSettings dateTimeSettings,
                                   final QueryKey queryKey,
                                   final List<CoprocessorSettings> coprocessorSettingsList,
                                   final List<Param> params,
                                   final DataStoreSettings dataStoreSettings) {
        // Create a field index map.
        final FieldIndex fieldIndex = new FieldIndex();

        // Create a parameter map.
        final Map<String, String> paramMap = ParamUtil.createParamMap(params);

        // Create error consumer.
        final ErrorConsumer errorConsumer = new ErrorConsumerImpl();
        final ExpressionContext expressionContext = expressionContextFactory
                .createContext(searchRequestSource, dateTimeSettings);
        final Map<Integer, Coprocessor> coprocessorMap = new HashMap<>();
        final Map<String, TableCoprocessor> componentIdCoprocessorMap = new HashMap<>();
        if (coprocessorSettingsList != null) {
            for (final CoprocessorSettings coprocessorSettings : coprocessorSettingsList) {
                final Coprocessor coprocessor = create(
                        expressionContext,
                        searchRequestSource,
                        queryKey,
                        coprocessorSettings,
                        fieldIndex,
                        paramMap,
                        errorConsumer,
                        dataStoreSettings);

                if (coprocessor != null) {
                    coprocessorMap.put(coprocessorSettings.getCoprocessorId(), coprocessor);

                    if (coprocessor instanceof final TableCoprocessor tableCoprocessor) {
                        final TableCoprocessorSettings tableCoprocessorSettings =
                                (TableCoprocessorSettings) coprocessorSettings;
                        for (final String componentId : tableCoprocessorSettings.getComponentIds()) {
                            componentIdCoprocessorMap.put(componentId, tableCoprocessor);
                        }
                    }
                }
            }
        }

        // Group coprocessors by extraction pipeline.
        final Map<DocRef, Set<Coprocessor>> extractionPipelineCoprocessorMap = new HashMap<>();
        coprocessorMap.values().forEach(coprocessor -> {
            DocRef extractionPipeline = null;

            if (coprocessor instanceof final TableCoprocessor tableCoprocessor) {
                if (tableCoprocessor.getTableSettings().extractValues()) {
                    extractionPipeline = tableCoprocessor.getTableSettings().getExtractionPipeline();
                }
            }

            extractionPipelineCoprocessorMap.computeIfAbsent(extractionPipeline, k ->
                    new HashSet<>()).add(coprocessor);
        });

        return new CoprocessorsImpl(
                Collections.unmodifiableMap(coprocessorMap),
                Collections.unmodifiableMap(componentIdCoprocessorMap),
                Collections.unmodifiableMap(extractionPipelineCoprocessorMap),
                fieldIndex,
                errorConsumer,
                expressionContext);
    }

    private Coprocessor create(final ExpressionContext expressionContext,
                               final SearchRequestSource searchRequestSource,
                               final QueryKey queryKey,
                               final CoprocessorSettings settings,
                               final FieldIndex fieldIndex,
                               final Map<String, String> paramMap,
                               final ErrorConsumer errorConsumer,
                               final DataStoreSettings dataStoreSettings) {
        if (settings instanceof final TableCoprocessorSettings tableCoprocessorSettings) {
            final TableSettings tableSettings = tableCoprocessorSettings.getTableSettings();
            final DataStore dataStore = create(
                    expressionContext,
                    searchRequestSource,
                    queryKey,
                    String.valueOf(tableCoprocessorSettings.getCoprocessorId()),
                    tableSettings,
                    fieldIndex,
                    paramMap,
                    dataStoreSettings,
                    errorConsumer);
            return new TableCoprocessor(tableSettings, dataStore, errorConsumer);
        } else if (settings instanceof final EventCoprocessorSettings eventCoprocessorSettings) {
            return new EventCoprocessor(eventCoprocessorSettings, fieldIndex, errorConsumer);
        }

        return null;
    }

    private DataStore create(final ExpressionContext expressionContext,
                             final SearchRequestSource searchRequestSource,
                             final QueryKey queryKey,
                             final String componentId,
                             final TableSettings tableSettings,
                             final FieldIndex fieldIndex,
                             final Map<String, String> paramMap,
                             final DataStoreSettings dataStoreSettings,
                             final ErrorConsumer errorConsumer) {

        // Create a set of sizes that are the minimum values for the combination of user provided sizes for the table
        // and the default maximum sizes.
        final Sizes maxResults;
        if (sizesProvider != null && tableSettings.getMaxResults() == null) {
            maxResults = sizesProvider.getDefaultMaxResultsSizes();
        } else {
            maxResults = Sizes.create(tableSettings.getMaxResults());
        }

        final DataStoreSettings modifiedSettings =
                dataStoreSettings.copy()
                        .maxResults(maxResults)
                        .build();

        return dataStoreFactory.create(
                expressionContext,
                searchRequestSource,
                queryKey,
                componentId,
                tableSettings,
                fieldIndex,
                paramMap,
                modifiedSettings,
                errorConsumer);
    }
}
