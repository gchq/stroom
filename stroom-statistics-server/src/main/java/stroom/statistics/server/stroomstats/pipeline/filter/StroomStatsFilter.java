/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.statistics.server.stroomstats.pipeline.filter;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import stroom.kafka.StroomKafkaProducer;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.server.filter.AbstractSamplingFilter;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.statistics.server.stroomstats.entity.StroomStatsStoreEntityService;
import stroom.statistics.server.stroomstats.kafka.TopicNameFactory;
import stroom.stats.shared.StroomStatsStoreEntity;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@SuppressWarnings("unused")
@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(
        type = "StroomStatsFilter",
        category = PipelineElementType.Category.FILTER,
        roles = {PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE},
        icon = ElementIcons.STROOM_STATS)
public class StroomStatsFilter extends AbstractSamplingFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomStatsFilter.class);

    private final ErrorReceiverProxy errorReceiverProxy;
    private final StroomKafkaProducer stroomKafkaProducer;
    private final StroomStatsStoreEntityService stroomStatsStoreEntityService;
    private final TopicNameFactory topicNameFactory;

    private StroomStatsStoreEntity stroomStatsStoreEntity;

    @Inject
    public StroomStatsFilter(final ErrorReceiverProxy errorReceiverProxy,
                             final LocationFactoryProxy locationFactory,
                             final StroomKafkaProducer stroomKafkaProducer,
                             final StroomStatsStoreEntityService stroomStatsStoreEntityService,
                             final TopicNameFactory topicNameFactory) {
        super(errorReceiverProxy, locationFactory);
        this.errorReceiverProxy = errorReceiverProxy;
        this.stroomKafkaProducer = stroomKafkaProducer;
        this.stroomStatsStoreEntityService = stroomStatsStoreEntityService;
        this.topicNameFactory = topicNameFactory;
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        //TODO here we just grab the whole xml document as a string and put that into a single producer record
        //If the xml doc contains many individual events then we could parse the xml to look for each event element
        //and send the xml of each event as a producerRecord async and call a flush or get futures here.
        try {
            String topic = topicNameFactory.getTopic(stroomStatsStoreEntity.getStatisticType());
            String recordKey = stroomStatsStoreEntity.getName();
            ProducerRecord<String, String> newRecord = new ProducerRecord<>(topic, recordKey, getOutput());
            if (stroomStatsStoreEntity == null) {
                throw new RuntimeException("No Stroom-Stats data source selected");
            }
            LOGGER.trace("Sending statistics to topic {} with key {}", topic, recordKey);
            stroomKafkaProducer.send(newRecord, StroomKafkaProducer.FlushMode.FLUSH_ON_SEND, exception -> {
                errorReceiverProxy.log(Severity.ERROR, null, null, "Unable to send record to Kafka!", exception);
            });
        } catch (RuntimeException e) {
            errorReceiverProxy.log(Severity.ERROR, null, null, "Unable to send record to Kafka!", e);
        }
    }

    @PipelineProperty(description = "The Stroom-Stats data source to send statistics to")
    public void setStroomStatsStoreEntity(final StroomStatsStoreEntity stroomStatsStoreEntity) {
        this.stroomStatsStoreEntity = stroomStatsStoreEntity;
    }

}