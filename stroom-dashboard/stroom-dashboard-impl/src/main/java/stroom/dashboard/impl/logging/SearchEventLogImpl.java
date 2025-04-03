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

import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.Search;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.SearchRequest;
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
    public void search(final String type,
                       final String rawQuery,
                       final DocRef dataSourceRef,
                       final ExpressionOperator expression,
                       final String queryInfo,
                       final List<Param> params,
                       final Exception e) {
        securityContext.insecure(() -> {
            try {
                final String dataSourceInfo = getDataSourceString(dataSourceRef);
                final String description = "Searching data source \"" + dataSourceInfo + "\"";
                final DataSources dataSources = DataSources.builder().addDataSource(dataSourceInfo).build();
                final Query query = getQuery(expression, params);
                query.setRaw(rawQuery);

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
        });
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
                final Query query = getQuery(search.getExpression(), params);

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
                final stroom.query.api.v2.Query qry = NullSafe.get(
                        request,
                        SearchRequest::getQuery);
                final DocRef dataSourceRef = NullSafe.get(
                        qry,
                        stroom.query.api.v2.Query::getDataSource);

                final String dataSourceInfo = getDataSourceString(dataSourceRef);
                final String description = "Downloading StroomQL search results - data source \"" +
                                           dataSourceInfo +
                                           "\"";
                final DataSources dataSources = DataSources.builder().addDataSource(dataSourceInfo).build();
                final Query query = getQuery(NullSafe.get(qry, stroom.query.api.v2.Query::getExpression),
                        NullSafe.get(qry, stroom.query.api.v2.Query::getParams));
                query.setRaw(NullSafe.get(
                        req,
                        DownloadQueryResultsRequest::getSearchRequest,
                        QuerySearchRequest::getQuery));
                final List<Param> params = NullSafe.get(qry, stroom.query.api.v2.Query::getParams);
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
