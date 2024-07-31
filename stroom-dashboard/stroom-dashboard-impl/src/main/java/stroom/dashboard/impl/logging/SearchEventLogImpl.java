/*
 * Copyright 2016 Crown Copyright
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

package stroom.dashboard.impl.logging;

import stroom.collection.api.CollectionService;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.Search;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Param;
import stroom.security.api.SecurityContext;
import stroom.util.NullSafe;

import event.logging.Criteria;
import event.logging.Data;
import event.logging.Data.Builder;
import event.logging.DataSources;
import event.logging.ExportEventAction;
import event.logging.File;
import event.logging.MultiObject;
import event.logging.Purpose;
import event.logging.Query;
import event.logging.SearchEventAction;
import event.logging.util.EventLoggingUtil;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

public class SearchEventLogImpl implements SearchEventLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchEventLogImpl.class);

    private final StroomEventLoggingService eventLoggingService;
    private final SecurityContext securityContext;
    private final WordListProvider wordListProvider;
    private final CollectionService collectionService;
    private final DocRefInfoService docRefInfoService;

    @Inject
    public SearchEventLogImpl(final StroomEventLoggingService eventLoggingService,
                              final SecurityContext securityContext,
                              final WordListProvider wordListProvider,
                              final CollectionService collectionService,
                              final DocRefInfoService docRefInfoService) {
        this.eventLoggingService = eventLoggingService;
        this.securityContext = securityContext;
        this.wordListProvider = wordListProvider;
        this.collectionService = collectionService;
        this.docRefInfoService = docRefInfoService;
    }

    @Override
    public void search(final DocRef dataSourceRef,
                       final ExpressionOperator expression,
                       final String queryInfo,
                       final List<Param> params) {
        securityContext.insecure(() -> search("Search",
                dataSourceRef,
                expression,
                queryInfo,
                params,
                null));
    }

    @Override
    public void search(final DocRef dataSourceRef,
                       final ExpressionOperator expression,
                       final String queryInfo,
                       final List<Param> params,
                       final Exception e) {
        securityContext.insecure(() -> search("Search",
                dataSourceRef,
                expression,
                queryInfo,
                params,
                e));
    }

    @Override
    public void search(final String query,
                       final String queryInfo,
                       final List<Param> params,
                       final Exception ex) {
        securityContext.insecure(() -> {
            try {
                search("Search",
                        getDescription("Search", null),
                        DataSources.builder().addDataSource("Unknown").build(),
                        Query.builder().withRaw(query).build(),
                        queryInfo,
                        params,
                        ex);
            } catch (final RuntimeException e2) {
                LOGGER.error(ex.getMessage(), e2);
            }
        });
    }

    @Override
    public void batchSearch(final DocRef dataSourceRef,
                            final ExpressionOperator expression,
                            final String queryInfo,
                            final List<Param> params) {
        securityContext.insecure(() -> search("Batch search",
                dataSourceRef,
                expression,
                queryInfo,
                params,
                null));
    }

    @Override
    public void batchSearch(final DocRef dataSourceRef,
                            final ExpressionOperator expression,
                            final String queryInfo,
                            final List<Param> params,
                            final Exception e) {
        securityContext.insecure(() -> search("Batch search",
                dataSourceRef,
                expression,
                queryInfo,
                params,
                e));
    }

    @Override
    public void downloadResults(final DownloadSearchResultsRequest request,
                                final Long resultCount,
                                final Exception e) {
        securityContext.insecure(() -> {
            try {
                final DashboardSearchRequest searchRequest = request.getSearchRequest();
                final DocRef dataSourceRef = NullSafe.get(
                        searchRequest,
                        DashboardSearchRequest::getSearch,
                        Search::getDataSourceRef);

                final String dataSourceName = getDataSourceName(dataSourceRef);
                final Search search = NullSafe.get(searchRequest, DashboardSearchRequest::getSearch);
                final List<Param> params = NullSafe.get(search, Search::getParams);
                final String fileType = NullSafe.get(request.getFileType(),
                        DownloadSearchResultFileType::getExtension);

                final ExpressionOperator deReferencedExpression = ExpressionUtil.replaceExpressionParameters(
                        search.getExpression(),
                        params);

                eventLoggingService.log(
                        "Download search results",
                        "Downloading search results - data source \"" + dataSourceRef.toInfoString(),
                        getPurpose(search.getQueryInfo()),
                        ExportEventAction.builder()
                                .withSource(MultiObject.builder()
                                        .addCriteria(Criteria.builder()
                                                .withDataSources(DataSources.builder()
                                                        .addDataSource(dataSourceName)
                                                        .build())
                                                .withQuery(StroomEventLoggingUtil.convertExpression(
                                                        deReferencedExpression))
                                                .withTotalResults(NullSafe.get(
                                                        resultCount,
                                                        cnt -> BigInteger.valueOf(cnt)))
                                                .addData(buildDataFromParams(params))
                                                .build())
                                        .build())
                                .withDestination(
                                        MultiObject.builder()
                                                .addFile(File.builder()
                                                        .withType(fileType)
                                                        .addData(Data.builder()
                                                                .withName("sample")
                                                                .withValue(Boolean.toString(request.isSample()))
                                                                .build())
                                                        .addData(Data.builder()
                                                                .withName("percent")
                                                                .withValue(String.valueOf(request.getPercent()))
                                                                .build())
                                                        .addData(Data.builder()
                                                                .withName("allTables")
                                                                .withValue(String.valueOf(
                                                                        request.isDownloadAllTables()))
                                                                .build())
                                                        .build())
                                                .build())
                                .withOutcome(EventLoggingUtil.createOutcome(e))
                                .build());

            } catch (final RuntimeException e2) {
                LOGGER.error(e.getMessage(), e2);
            }
        });
    }

    private void search(final String type,
                        final DocRef dataSourceRef,
                        final ExpressionOperator expression,
                        final String queryInfo,
                        final List<Param> params,
                        final Exception e) {
        securityContext.insecure(() -> {
            try {
                search(type,
                        getDescription(type, dataSourceRef),
                        getDataSources(dataSourceRef),
                        getQuery(expression, params),
                        queryInfo,
                        params,
                        e);
            } catch (final RuntimeException e2) {
                LOGGER.error(e.getMessage(), e2);
            }
        });
    }

    private void search(final String type,
                        final String description,
                        final DataSources dataSources,
                        final Query query,
                        final String queryInfo,
                        final List<Param> params,
                        final Exception e) {
        try {
            eventLoggingService.log(
                    type,
                    description,
                    getPurpose(queryInfo),
                    SearchEventAction.builder()
                            .withDataSources(dataSources)
                            .withQuery(query)
                            .addData(buildDataFromParams(params))
                            .withOutcome(EventLoggingUtil.createOutcome(e))
                            .build());
        } catch (final RuntimeException e2) {
            LOGGER.error(e.getMessage(), e2);
        }
    }

    private String getDescription(final String type, final DocRef dataSourceRef) {
        if (dataSourceRef != null) {
            return type + "ing data source \"" + dataSourceRef.toInfoString();
        }
        return type + "ing data source";
    }

    private DataSources getDataSources(final DocRef dataSourceRef) {
        String dataSourceName = getDataSourceName(dataSourceRef);
        if (dataSourceName == null || dataSourceName.isEmpty()) {
            dataSourceName = "NULL";
        }
        return DataSources.builder()
                .addDataSource(dataSourceName)
                .build();
    }

    private Query getQuery(final ExpressionOperator expression,
                           final List<Param> params) {
        final ExpressionOperator deReferencedExpression = ExpressionUtil.replaceExpressionParameters(
                expression,
                params);
        return StroomEventLoggingUtil.convertExpression(deReferencedExpression);
    }

    private Iterable<Data> buildDataFromParams(final List<Param> params) {
        if (NullSafe.hasItems(params)) {
            final Builder<Void> dataBuilder = Data.builder()
                    .withName("params");

            boolean addedData = false;
            for (final Param param : params) {
                if (NullSafe.isBlankString(param.getKey())) {
                    LOGGER.warn("Param with no key: {}", param);
                } else {
                    dataBuilder.addData(Data.builder()
                            .withName(param.getKey())
                            .withValue(param.getValue())
                            .build());
                    addedData = true;
                }
            }
            return addedData
                    ? List.of(dataBuilder.build())
                    : Collections.emptyList();
        } else {
            return Collections.emptyList();
        }
    }

    private String getDataSourceName(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }

        try {
            return docRefInfoService.name(docRef)
                    .orElse(docRef.getName());
        } catch (final RuntimeException e) {
            // We might not have an explorer handler capable of getting info.
            LOGGER.debug(e.getMessage(), e);
        }

        return docRef.getName();
    }

    private Purpose getPurpose(final String queryInfo) {
        return queryInfo != null
                ? Purpose.builder()
                .withJustification(queryInfo)
                .build()
                : null;
    }
}
