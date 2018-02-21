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

package stroom.statistics.server.stroomstats.pipeline.filter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.connectors.kafka.StroomKafkaProducerFactoryService;
import stroom.node.server.StroomPropertyService;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.statistics.server.stroomstats.entity.StroomStatsStoreEntityService;
import stroom.statistics.server.stroomstats.kafka.TopicNameFactory;
import stroom.util.spring.StroomScope;

@Configuration
public class FilterSpringConfig {
    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public StroomStatsFilter stroomStatsFilter(final ErrorReceiverProxy errorReceiverProxy,
                                               final LocationFactoryProxy locationFactory,
                                               final StroomPropertyService stroomPropertyService,
                                               final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService,
                                               final TopicNameFactory topicNameFactory,
                                               final StroomStatsStoreEntityService stroomStatsStoreEntityService) {
        return new StroomStatsFilter(errorReceiverProxy, locationFactory, stroomPropertyService, stroomKafkaProducerFactoryService, topicNameFactory, stroomStatsStoreEntityService);
    }
}