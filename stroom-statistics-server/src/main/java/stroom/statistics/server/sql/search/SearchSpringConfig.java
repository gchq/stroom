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

package stroom.statistics.server.sql.search;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stroom.node.server.StroomPropertyService;
import stroom.statistics.server.sql.SQLStatisticEventStore;
import stroom.statistics.server.sql.StatisticsQueryService;
import stroom.statistics.server.sql.datasource.StatisticStoreCache;
import stroom.statistics.server.sql.datasource.StatisticsDataSourceProvider;

@Configuration
public class SearchSpringConfig {
    @Bean
    public SqlStatisticsQueryResource sqlStatisticsQueryResource(final StatisticsQueryService statisticsQueryService) {
        return new SqlStatisticsQueryResource(statisticsQueryService);
    }

    @Bean
    public StatisticsQueryService statisticsQueryService(final StatisticsDataSourceProvider statisticsDataSourceProvider,
                                                         final StatisticStoreCache statisticStoreCache,
                                                         final SQLStatisticEventStore sqlStatisticEventStore,
                                                         final StroomPropertyService stroomPropertyService) {
        return new StatisticsQueryServiceImpl(statisticsDataSourceProvider, statisticStoreCache, sqlStatisticEventStore, stroomPropertyService);
    }
}