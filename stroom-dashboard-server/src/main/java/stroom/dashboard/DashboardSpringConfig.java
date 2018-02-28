/*
 * Copyright 2018 Crown Copyright
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

package stroom.dashboard;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.ResourceLoader;
import stroom.dashboard.logging.SearchEventLog;
import stroom.datasource.DataSourceProviderRegistry;
import stroom.entity.StroomEntityManager;
import stroom.importexport.ImportExportHelper;
import stroom.properties.StroomPropertyService;
import stroom.resource.ResourceStore;
import stroom.security.SecurityContext;
import stroom.util.cache.CacheManager;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;
import stroom.visualisation.VisualisationService;

import javax.inject.Provider;

@Configuration
public class DashboardSpringConfig {
    @Bean
    public ActiveQueriesManager activeQueriesManager(final CacheManager cacheManager,
                                                     final DataSourceProviderRegistry dataSourceProviderRegistry) {
        return new ActiveQueriesManager(cacheManager, dataSourceProviderRegistry);
    }

    @Bean
    public DashboardService dashboardService(final StroomEntityManager entityManager,
                                             final ImportExportHelper importExportHelper,
                                             final SecurityContext securityContext,
                                             final ResourceLoader resourceLoader) {
        return new DashboardServiceImpl(entityManager, importExportHelper, securityContext, resourceLoader);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public DownloadQueryActionHandler downloadQueryActionHandler(final SearchRequestMapper searchRequestMapper,
                                                                 final ResourceStore resourceStore) {
        return new DownloadQueryActionHandler(searchRequestMapper, resourceStore);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public DownloadSearchResultsHandler downloadSearchResultsHandler(final ResourceStore resourceStore,
                                                                     final SearchEventLog searchEventLog,
                                                                     final ActiveQueriesManager activeQueriesManager,
                                                                     final DataSourceProviderRegistry searchDataSourceProviderRegistry,
                                                                     final SearchRequestMapper searchRequestMapper) {
        return new DownloadSearchResultsHandler(resourceStore, searchEventLog, activeQueriesManager, searchDataSourceProviderRegistry, searchRequestMapper);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public FetchExpressionFieldsHandler fetchExpressionFieldsHandler(final DataSourceProviderRegistry dataSourceProviderRegistry,
                                                                     final SecurityContext securityContext) {
        return new FetchExpressionFieldsHandler(dataSourceProviderRegistry, securityContext);
    }

    @Bean
    @Scope(value = StroomScope.PROTOTYPE)
    public FetchTimeZonesHandler fetchTimeZonesHandler() {
        return new FetchTimeZonesHandler();
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public FetchVisualisationHandler fetchVisualisationHandler(final VisualisationService visualisationService,
                                                               final SecurityContext securityContext) {
        return new FetchVisualisationHandler(visualisationService, securityContext);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public QueryHistoryCleanExecutor queryHistoryCleanExecutor(final TaskMonitor taskMonitor,
                                                               final QueryService queryService,
                                                               final StroomPropertyService propertyService) {
        return new QueryHistoryCleanExecutor(taskMonitor, queryService, propertyService);
    }

    @Bean("queryService")
//    @Profile(StroomSpringProfiles.PROD)
    public QueryService queryService(final StroomEntityManager entityManager,
                                     final ImportExportHelper importExportHelper,
                                     final SecurityContext securityContext) {
        return new QueryServiceImpl(entityManager, importExportHelper, securityContext);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public SearchBusPollActionHandler searchBusPollActionHandler(final QueryService queryService,
                                                                 final SearchEventLog searchEventLog,
                                                                 final DataSourceProviderRegistry searchDataSourceProviderRegistry,
                                                                 final ActiveQueriesManager activeQueriesManager,
                                                                 final SearchRequestMapper searchRequestMapper,
                                                                 final SecurityContext securityContext) {
        return new SearchBusPollActionHandler(queryService, searchEventLog, searchDataSourceProviderRegistry, activeQueriesManager, searchRequestMapper, securityContext);
    }

    @Bean
    public SearchBusPollResource searchBusPollResource(final Provider<SearchBusPollActionHandler> searchBusPollActionHandler) {
        return new SearchBusPollResource(searchBusPollActionHandler);
    }

    @Bean
    public SearchRequestMapper searchRequestMapper(final VisualisationService visualisationService) {
        return new SearchRequestMapper(visualisationService);
    }

    @Bean
    public SearchResponseMapper searchResponseMapper() {
        return new SearchResponseMapper();
    }

    @Bean
    @Scope(value = StroomScope.PROTOTYPE)
    public ValidateExpressionHandler validateExpressionHandler() {
        return new ValidateExpressionHandler();
    }
}