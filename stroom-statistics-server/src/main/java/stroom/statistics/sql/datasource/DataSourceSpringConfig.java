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

package stroom.statistics.sql.datasource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stroom.entity.util.StroomEntityManager;
import stroom.importexport.ImportExportHelper;
import stroom.security.SecurityContext;
import stroom.statistics.sql.Statistics;
import stroom.util.cache.CacheManager;

@Configuration
public class DataSourceSpringConfig {
    @Bean
    public StatisticStoreEntityService statisticStoreEntityService(final StroomEntityManager entityManager,
                                                                   final ImportExportHelper importExportHelper,
                                                                   final SecurityContext securityContext) {
        return new StatisticStoreEntityServiceImpl(entityManager, importExportHelper, securityContext);
    }

    @Bean("statisticsDataSourceCache")
    public StatisticStoreCache statisticsDataSourceCache(final StatisticStoreEntityService statisticsDataSourceService,
                                                         final CacheManager cacheManager) {
        return new StatisticsDataSourceCacheImpl(statisticsDataSourceService, cacheManager);
    }

    @Bean
    public StatisticsDataSourceProvider statisticsDataSourceProvider(final StatisticStoreCache statisticStoreCache,
                                                                     final Statistics statistics,
                                                                     final SecurityContext securityContext) {
        return new StatisticsDataSourceProviderImpl(statisticStoreCache, statistics, securityContext);
    }

    @Bean
    public StatisticStoreValidator statisticsDataSourceValidator() {
        return new StatisticsDataSourceValidatorImpl();
    }
}