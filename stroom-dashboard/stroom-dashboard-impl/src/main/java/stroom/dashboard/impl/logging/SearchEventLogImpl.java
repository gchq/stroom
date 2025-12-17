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

package stroom.dashboard.impl.logging;

import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.Search;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.query.api.Column;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.IncludeExcludeFilter;
import stroom.query.api.Param;
import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.SearchRequest;
import stroom.query.api.TableResult;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QuerySearchRequest;
import stroom.security.api.SecurityContext;
import stroom.util.shared.NullSafe;

import event.logging.Criteria;
import event.logging.Data;
import event.logging.Data.Builder;
import event.logging.DataSources;
import event.logging.ExportEventAction;
import event.logging.File;
import event.logging.MultiObject;
import event.logging.Purpose;
import event.logging.Query;
import event.logging.ResultPage;
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
    private final DocRefInfoService docRefInfoService;

    @Inject
    public SearchEventLogImpl(final StroomEventLoggingService eventLoggingService,
                              final SecurityContext securityContext,
                              final DocRefInfoService docRefInfoService) {
        this.eventLoggingService = eventLoggingService;
        this.securityContext = securityContext;
        this.docRefInfoService = docRefInfoService;
    }

    @Override
    public void search(final QueryKey queryKey,
                       final String queryComponentId,
                       final String type,
                       final String rawQuery,
                       final DocRef dataSourceRef,
                       final ExpressionOperator expression,
                       final String queryInfo,
                       final List<Param> params,
                       final List<Result> results,
                       final Exception e) {
        securityContext.insecure(() -> {
            try {
                final String dataSourceInfo = getDataSourceString(dataSourceRef);
                final String description = "Searching data source \"" + dataSourceInfo + "\"";
                final DataSources dataSources = DataSources.builder()
                        .addDataSource(dataSourceInfo)
                        .build();
                final Query query = getQuery(queryKey, expression, params);
                query.setRaw(rawQuery);

                final List<TableResult> tableResults = getTableResults(results);
                // One event per table result
                final int eventCount = tableResults.isEmpty()
                        ? 1
                        : tableResults.size();

                for (int i = 0; i < eventCount; i++) {
                    final TableResult result = !tableResults.isEmpty()
                            ? tableResults.get(i)
                            : null;
                    final SearchEventAction.Builder<Void> actionBuilder = SearchEventAction.builder()
                            .withDataSources(dataSources)
                            .withQuery(query)
                            .addData(buildDataFromParams(params))
                            .addData(Data.builder()
                                    .withName("queryComponent")
                                    .withValue(queryComponentId)
                                    .build())
                            .withOutcome(EventLoggingUtil.createOutcome(e));
                    if (result != null) {
                        actionBuilder
                                .withTotalResults(BigInteger.valueOf(result.getTotalResults()))
                                .withResultPage(NullSafe.get(
                                        result,
                                        TableResult::getResultRange,
                                        offsetRange -> ResultPage.builder()
                                                .withFrom(BigInteger.valueOf(offsetRange.getOffset()))
                                                .withTo(BigInteger.valueOf(
                                                        offsetRange.getOffset() + offsetRange.getLength()))
                                                .build()))
                                .addData(Data.builder()
                                        .withName("tableComponent")
                                        .withValue(result.getComponentId())
                                        .build())
                                .addData(buildResultColumnsData(result));
                    }

                    eventLoggingService.log(
                            type,
                            description,
                            getPurpose(queryInfo),
                            actionBuilder.build());
                }
            } catch (final RuntimeException e2) {
                LOGGER.error(e.getMessage(), e2);
            }
        });
    }

    private Data buildResultColumnsData(final TableResult tableResult) {
        final List<Column> columns = tableResult.getColumns();
        if (NullSafe.hasItems(columns)) {
            final Builder<Void> builder = Data.builder()
                    .withName("columns");
            for (int i = 0; i < columns.size(); i++) {
                final Column column = columns.get(i);
                if (column.isVisible()) {
                    final Builder<Void> colBuilder = Data.builder();
                    colBuilder
                            .withName("column-" + (i + 1))
                            .addData(Data.builder()
                                    .withName("name")
                                    .withValue(column.getDisplayValue())
                                    .build())
                            .addData(Data.builder()
                                    .withName("expression")
                                    .withValue(column.getExpression())
                                    .build())
                            .build();
                    NullSafe.consume(column, Column::getColumnFilter, columnFilter -> {
                        colBuilder.addData(Data.builder()
                                .withName("columnFilter")
                                .withValue(column.getColumnFilter().getFilter())
                                .build());
                    });
                    NullSafe.consume(column, Column::getFilter, IncludeExcludeFilter::getIncludes, includes -> {
                        colBuilder.addData(Data.builder()
                                .withName("includeFilter")
                                .withValue(includes)
                                .build());
                    });
                    NullSafe.consume(column, Column::getFilter, IncludeExcludeFilter::getExcludes, excludes -> {
                        colBuilder.addData(Data.builder()
                                .withName("includeFilter")
                                .withValue(excludes)
                                .build());
                    });
                    builder.addData(colBuilder.build());
                }
            }
            return builder.build();
        } else {
            return null;
        }
    }

    private List<TableResult> getTableResults(final List<Result> results) {
        return NullSafe.stream(results)
                .filter(result -> result instanceof TableResult)
                .map(result -> (TableResult) result)
                .toList();
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
                final Search search = NullSafe.get(searchRequest, DashboardSearchRequest::getSearch);
                final List<Param> params = NullSafe.get(search, Search::getParams);

                final String dataSourceInfo = getDataSourceString(dataSourceRef);
                final String description = "Downloading search results - data source \"" + dataSourceInfo + "\"";
                final DataSources dataSources = DataSources.builder().addDataSource(dataSourceInfo).build();
                final Query query = getQuery(searchRequest.getQueryKey(), search.getExpression(), params);

                final String fileType = NullSafe.get(request.getFileType(),
                        DownloadSearchResultFileType::getExtension);

                eventLoggingService.log(
                        "Download search results",
                        description,
                        getPurpose(search.getQueryInfo()),
                        ExportEventAction.builder()
                                .withSource(MultiObject.builder()
                                        .addCriteria(Criteria.builder()
                                                .withDataSources(dataSources)
                                                .withQuery(query)
                                                .withTotalResults(NullSafe.get(
                                                        resultCount,
                                                        BigInteger::valueOf))
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
                LOGGER.error(e2.getMessage(), e2);
            }
        });
    }

    @Override
    public void downloadResults(final DownloadQueryResultsRequest req,
                                final SearchRequest request,
                                final Long resultCount,
                                final Exception ex) {
        securityContext.insecure(() -> {
            try {
                final stroom.query.api.Query qry = NullSafe.get(
                        request,
                        SearchRequest::getQuery);
                final DocRef dataSourceRef = NullSafe.get(
                        qry,
                        stroom.query.api.Query::getDataSource);

                final String dataSourceInfo = getDataSourceString(dataSourceRef);
                final String description = "Downloading StroomQL search results - data source \"" +
                                           dataSourceInfo +
                                           "\"";
                final DataSources dataSources = DataSources.builder().addDataSource(dataSourceInfo).build();
                final Query query = getQuery(
                        request.getKey(),
                        NullSafe.get(qry, stroom.query.api.Query::getExpression),
                        NullSafe.get(qry, stroom.query.api.Query::getParams));
                query.setRaw(NullSafe.get(
                        req,
                        DownloadQueryResultsRequest::getSearchRequest,
                        QuerySearchRequest::getQuery));
                final List<Param> params = NullSafe.get(qry, stroom.query.api.Query::getParams);
                final String fileType = NullSafe.get(req.getFileType(),
                        DownloadSearchResultFileType::getExtension);

                eventLoggingService.log(
                        "Download search results",
                        description,
                        getPurpose(req.getSearchRequest().getQueryContext().getQueryInfo()),
                        ExportEventAction.builder()
                                .withSource(MultiObject.builder()
                                        .addCriteria(Criteria.builder()
                                                .withDataSources(dataSources)
                                                .withQuery(query)
                                                .withTotalResults(NullSafe.get(
                                                        resultCount,
                                                        BigInteger::valueOf))
                                                .addData(buildDataFromParams(params))
                                                .build())
                                        .build())
                                .withDestination(
                                        MultiObject.builder()
                                                .addFile(File.builder()
                                                        .withType(fileType)
                                                        .addData(Data.builder()
                                                                .withName("sample")
                                                                .withValue(Boolean.toString(req.isSample()))
                                                                .build())
                                                        .addData(Data.builder()
                                                                .withName("percent")
                                                                .withValue(String.valueOf(req.getPercent()))
                                                                .build())
                                                        .build())
                                                .build())
                                .withOutcome(EventLoggingUtil.createOutcome(ex))
                                .build());

            } catch (final RuntimeException e2) {
                LOGGER.error(e2.getMessage(), e2);
            }
        });
    }

    private String getDataSourceString(final DocRef dataSourceRef) {
        final StringBuilder sb = new StringBuilder();

        final String type = NullSafe.get(dataSourceRef, DocRef::getType);
        if (NullSafe.isNonBlankString(type)) {
            sb.append(type);
        }

        final String dataSourceName = getDataSourceName(dataSourceRef);
        if (NullSafe.isNonBlankString(dataSourceName)) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(dataSourceName);
        }

        final String uuid = NullSafe.get(dataSourceRef, DocRef::getUuid);
        if (NullSafe.isNonBlankString(uuid)) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append("{");
            sb.append(uuid);
            sb.append("}");
        }

        if (sb.isEmpty()) {
            sb.append("Unknown");
        }

        return sb.toString();
    }

    private Query getQuery(final QueryKey queryKey,
                           final ExpressionOperator expression,
                           final List<Param> params) {
        final ExpressionOperator deReferencedExpression = ExpressionUtil.replaceExpressionParameters(
                expression,
                params);
        return StroomEventLoggingUtil.convertExpression(queryKey, deReferencedExpression);
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
