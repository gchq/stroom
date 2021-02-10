/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.impl;

import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.ExpressionParser;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.ParamFactory;
import stroom.dashboard.impl.datasource.DataSourceProvider;
import stroom.dashboard.impl.datasource.DataSourceProviderRegistry;
import stroom.dashboard.impl.download.DelimitedTarget;
import stroom.dashboard.impl.download.ExcelTarget;
import stroom.dashboard.impl.download.SearchResultWriter;
import stroom.dashboard.impl.logging.SearchEventLog;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardQueryKey;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.DownloadQueryRequest;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.FunctionSignature;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.SearchBusPollRequest;
import stroom.dashboard.shared.SearchRequest;
import stroom.dashboard.shared.SearchResponse;
import stroom.dashboard.shared.StoredQuery;
import stroom.dashboard.shared.TableResultRequest;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.dashboard.shared.VisResultRequest;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.ResultRequest.Fetch;
import stroom.query.api.v2.Row;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.storedquery.api.StoredQueryService;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.util.EntityServiceExceptionUtil;
import stroom.util.json.JsonUtil;
import stroom.util.servlet.HttpServletRequestHolder;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@AutoLogged
class DashboardResourceImpl implements DashboardResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardResourceImpl.class);

    private final Provider<DashboardService> dashboardServiceProvider;

    @Inject
    DashboardResourceImpl(final Provider<DashboardService> dashboardServiceProvider) {
        this.dashboardServiceProvider = dashboardServiceProvider;
    }

    @Override
    public DashboardDoc read(final DocRef docRef) {
        return dashboardServiceProvider.get().read(docRef);
    }

    @Override
    public DashboardDoc update(final DashboardDoc doc) {
        return dashboardServiceProvider.get().update(doc);
    }

    @Override
    public ValidateExpressionResult validateExpression(final String expressionString) {
       return dashboardServiceProvider.get().validateExpression(expressionString);
    }

    @Override
    public ResourceGeneration downloadQuery(final DownloadQueryRequest request) {
        return dashboardServiceProvider.get().downloadQuery(request);
    }

    @Override
    public ResourceGeneration downloadSearchResults(final DownloadSearchResultsRequest request) {
        return dashboardServiceProvider.get().downloadSearchResults(request);
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public Set<SearchResponse> poll(final SearchBusPollRequest request) {
        return dashboardServiceProvider.get().poll(request);
    }



    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public List<String> fetchTimeZones() {
        return dashboardServiceProvider.get().fetchTimeZones();
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public List<FunctionSignature> fetchFunctions() {
        return dashboardServiceProvider.get().fetchFunctions();
    }

}