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

package stroom.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.connectors.ExternalLibService;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.destination.RollingDestinations;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.writer.PathCreator;
import stroom.properties.StroomPropertyService;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

@Configuration
public class KafkaSpringConfig {
    @Bean
    @Scope(StroomScope.SINGLETON)
    public StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService(final StroomPropertyService propertyService,
                                                                               final ExternalLibService externalLibService) {
        return new StroomKafkaProducerFactoryService(propertyService, externalLibService);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public KafkaAppender kafkaAppender(final ErrorReceiverProxy errorReceiverProxy,
                                       final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService,
                                       final PathCreator pathCreator) {
        return new KafkaAppender(errorReceiverProxy, stroomKafkaProducerFactoryService, pathCreator);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public RollingKafkaAppender rollingKafkaAppender(final RollingDestinations destinations,
                                                     final TaskMonitor taskMonitor,
                                                     final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService,
                                                     final PathCreator pathCreator,
                                                     final ErrorReceiverProxy errorReceiverProxy) {
        return new RollingKafkaAppender(destinations, taskMonitor, stroomKafkaProducerFactoryService, pathCreator, errorReceiverProxy);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public GenericKafkaProducerFilter genericKafkaProducerFilter(final ErrorReceiverProxy errorReceiverProxy,
                                                                 final LocationFactoryProxy locationFactory,
                                                                 final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService,
                                                                 final PathCreator pathCreator) {
        return new GenericKafkaProducerFilter(errorReceiverProxy, locationFactory, stroomKafkaProducerFactoryService, pathCreator);
    }
}