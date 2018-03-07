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

package stroom.statistics.sql;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.entity.StroomDatabaseInfo;
import stroom.jobsystem.ClusterLockService;
import stroom.properties.StroomPropertyService;
import stroom.statistics.sql.datasource.StatisticStoreCache;
import stroom.statistics.sql.datasource.StatisticStoreValidator;
import stroom.task.TaskManager;
import stroom.util.spring.StroomScope;
import stroom.task.TaskContext;

import javax.inject.Named;
import javax.sql.DataSource;

@Configuration
public class SQLStatisticSpringConfig {
    @Bean
    @Scope(value = StroomScope.TASK)
    public SQLStatisticAggregationManager sQLStatisticAggregationManager(final ClusterLockService clusterLockService,
                                                                         final SQLStatisticAggregationTransactionHelper helper,
                                                                         final TaskContext taskContext,
                                                                         final StroomDatabaseInfo stroomDatabaseInfo,
                                                                         final StroomPropertyService propertyService) {
        return new SQLStatisticAggregationManager(clusterLockService, helper, taskContext, stroomDatabaseInfo, propertyService);
    }

    @Bean
    public SQLStatisticAggregationTransactionHelper sQLStatisticAggregationTransactionHelper(@Named("statisticsDataSource") final DataSource statisticsDataSource,
                                                                                             final StroomDatabaseInfo stroomDatabaseInfo,
                                                                                             final StroomPropertyService stroomPropertyService) {
        return new SQLStatisticAggregationTransactionHelper(statisticsDataSource, stroomDatabaseInfo, stroomPropertyService);
    }

    @Bean
    public SQLStatisticCache sQLStatisticCache(final TaskManager taskManager) {
        return new SQLStatisticCacheImpl(taskManager);
    }

    @Bean
    public SQLStatisticEventStore sQLStatisticEventStore(final StatisticStoreValidator statisticsDataSourceValidator,
                                                         final StatisticStoreCache statisticsDataSourceCache,
                                                         final SQLStatisticCache statisticCache,
                                                         @Named("statisticsDataSource") final DataSource statisticsDataSource,
                                                         final StroomPropertyService propertyService) {
        return new SQLStatisticEventStore(statisticsDataSourceValidator, statisticsDataSourceCache, statisticCache, statisticsDataSource, propertyService);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public SQLStatisticFlushTaskHandler sqlStatisticFlushTaskHandler(final SQLStatisticValueBatchSaveService sqlStatisticValueBatchSaveService,
                                                                     final TaskContext taskContext) {
        return new SQLStatisticFlushTaskHandler(sqlStatisticValueBatchSaveService, taskContext);
    }

    @Bean
    public SQLStatisticValueBatchSaveService sQLStatisticValueBatchSaveService(@Named("statisticsDataSource") final DataSource statisticsDataSource) {
        return new SQLStatisticValueBatchSaveService(statisticsDataSource);
    }
}