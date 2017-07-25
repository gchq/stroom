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

package stroom.kafka.filter;

import org.apache.kafka.clients.producer.ProducerRecord;
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
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

/**
 * A generic kakfa producer filter for sending in XML content to a kafka topic.  Currently the whole XML document
 * is sent as a single kafka message with a key specified in the filter properties in the UI.
 *
 * TODO It would be quite good to be able to set the key as a substitution variable e.g. ${userid} such that it
 * finds an element in the document with that name and uses its value as the key.
 *
 * TODO we may also want a way of breaking up the data in individual atomic events rather than a single kafka message
 * containing a batch of events
 */
@SuppressWarnings("unused")
@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(
        type = "KafkaProducerFilter",
        category = PipelineElementType.Category.FILTER,
        roles = {PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE},
        icon = ElementIcons.KAFKA)
public class KafkaProducerFilter extends AbstractSamplingFilter {
    private final ErrorReceiverProxy errorReceiverProxy;
    private final StroomKafkaProducer stroomKafkaProducer;

    private String recordKey;
    private String topic;

    @Inject
    public KafkaProducerFilter(final ErrorReceiverProxy errorReceiverProxy,
                               final LocationFactoryProxy locationFactory,
                               final StroomKafkaProducer stroomKafkaProducer) {
        super(errorReceiverProxy, locationFactory);
        this.errorReceiverProxy = errorReceiverProxy;
        this.stroomKafkaProducer = stroomKafkaProducer;
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        ProducerRecord<String, String> newRecord = new ProducerRecord<>(topic, recordKey, getOutput());
        try {
            stroomKafkaProducer.send(newRecord, StroomKafkaProducer.FlushMode.FLUSH_ON_SEND, exception -> {
                errorReceiverProxy.log(Severity.ERROR, null, null, "Unable to send record to Kafka!", exception);
            });
        } catch (RuntimeException e) {
            errorReceiverProxy.log(Severity.ERROR, null, null, "Unable to send record to Kafka!", e);
        }
    }

    @PipelineProperty(description = "The key for the record. This determines the partition.")
    public void setRecordKey(final String recordKey) {
        this.recordKey = recordKey;
    }

    @PipelineProperty(description = "The topic to send the record to.")
    public void setTopic(final String topic) {
        this.topic = topic;
    }
}