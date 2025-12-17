/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.statistics.impl.hbase.internal;

import stroom.docref.DocRef;
import stroom.kafka.api.KafkaProducerFactory;
import stroom.kafka.api.SharedKafkaProducer;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.impl.InternalStatisticsService;
import stroom.stats.schema.v4.ObjectFactory;
import stroom.stats.schema.v4.Statistics;
import stroom.stats.schema.v4.TagType;
import stroom.util.collections.BatchingIterator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.common.base.Preconditions;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

@SuppressWarnings("unused")
class StroomStatsInternalStatisticsService implements InternalStatisticsService {

    private static final String STATISTICS_SCHEMA_VERSION = "4.0.0";
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(
            StroomStatsInternalStatisticsService.class);
    private static final Class<Statistics> STATISTICS_CLASS = Statistics.class;
    private static final TimeZone TIME_ZONE_UTC = TimeZone.getTimeZone(ZoneId.from(ZoneOffset.UTC));

    private final KafkaProducerFactory stroomKafkaProducerFactory;
    private final HBaseStatisticsConfig internalStatisticsConfig;
    private final String docRefType;
    private final DatatypeFactory datatypeFactory;

    private static JAXBContext jaxbContext;

    @Inject
    StroomStatsInternalStatisticsService(final KafkaProducerFactory stroomKafkaProducerFactory,
                                         final HBaseStatisticsConfig internalStatisticsConfig) {
        LOGGER.debug("Initialising StroomStatsInternalStatisticsService");

        this.stroomKafkaProducerFactory = stroomKafkaProducerFactory;
        this.internalStatisticsConfig = internalStatisticsConfig;
        this.docRefType = internalStatisticsConfig.getDocRefType();

        try {
            this.datatypeFactory = DatatypeFactory.newInstance();
        } catch (final DatatypeConfigurationException e) {
            throw new RuntimeException("Unable to create new DatatypeFactory instance", e);
        }
    }

    @Override
    public void putEvents(final Map<DocRef, List<InternalStatisticEvent>> eventsMap) {
        final DocRef kafkaConfigDocRef = new DocRef(
                KafkaConfigDoc.TYPE,
                internalStatisticsConfig.getKafkaConfigUuid());

        try (final SharedKafkaProducer sharedKafkaProducer =
                stroomKafkaProducerFactory.getSharedProducer(kafkaConfigDocRef)) {

            sharedKafkaProducer.getKafkaProducer().ifPresentOrElse(
                    kafkaProducer ->
                            sendMessages(eventsMap, kafkaProducer),
                    () -> {
                        throw new RuntimeException("The Kafka producer isn't initialised, unable to send any events");
                    }
            );
        }
    }

    private void sendMessages(final Map<DocRef, List<InternalStatisticEvent>> eventsMap,
                              final KafkaProducer<String, byte[]> kafkaProducer) {
        Preconditions.checkNotNull(eventsMap);
        final int batchSize = getBatchSize();

        //We work on the basis that a stat may or may not have a valid datasource (StatisticConfiguration) but we
        //will let stroom-stats worry about that and just fire what we have at kafka
        eventsMap.entrySet().stream()
                .filter(entry ->
                        !entry.getValue().isEmpty())
                .forEach(entry -> {
                    final DocRef docRef = entry.getKey();
                    final List<InternalStatisticEvent> events = entry.getValue();
                    //all have same name so all have same stat type, thus grab the first one's type
                    final String topic = getTopic(events.get(0).getType());
                    final String key = docRef.getUuid();

                    //The list of events may contain thousands of events, e.g for the
                    //heap histo stats, so break it down into batches for better scaling with kafka
                    BatchingIterator.batchedStreamOf(events.stream(), batchSize)
                            .forEach(eventsBatch ->
                                    sendMessage(kafkaProducer, topic, key, eventsBatch));
                });
    }

    private void sendMessage(final KafkaProducer<String, byte[]> kafkaProducer,
                             final String topic,
                             final String key,
                             final List<InternalStatisticEvent> events) {

        final byte[] message = buildMessage(events);

//        final KafkaProducerRecord<String, byte[]> producerRecord =
//                new KafkaProducerRecord.Builder<String, byte[]>()
//                        .topic(topic)
//                        .key(key)
//                        .value(message)
//                        .build();

        final ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic,
                key, message);

        //These are only internal stats so just send them async for performance
        kafkaProducer.send(record);
    }

    private byte[] buildMessage(final List<InternalStatisticEvent> events) {

        final Statistics statistics = new ObjectFactory().createStatistics();
        statistics.setVersion(STATISTICS_SCHEMA_VERSION);
        Preconditions.checkNotNull(events).stream()
                .map(this::internalStatisticMapper)
                .forEach(statistic -> statistics.getStatistic().add(statistic));

        return marshall(statistics);
    }

    private Statistics.Statistic internalStatisticMapper(final InternalStatisticEvent internalStatisticEvent) {
        Preconditions.checkNotNull(internalStatisticEvent);
        final ObjectFactory objectFactory = new ObjectFactory();
        final Statistics.Statistic statistic = objectFactory.createStatisticsStatistic();
        statistic.setTime(toXMLGregorianCalendar(internalStatisticEvent.getTimeMs()));
        final InternalStatisticEvent.Type type = internalStatisticEvent.getType();

        switch (internalStatisticEvent.getType()) {
            case COUNT:
                statistic.setCount(getValueAsType(type,
                        Long.class,
                        internalStatisticEvent.getValue()));
                break;
            case VALUE:
                statistic.setValue(getValueAsType(type,
                        Double.class,
                        internalStatisticEvent.getValue()));
                break;
            default:
                throw new IllegalArgumentException("Unexpected type " + type.toString());
        }

        if (!internalStatisticEvent.getTags().isEmpty()) {
            final Statistics.Statistic.Tags tags = new Statistics.Statistic.Tags();
            internalStatisticEvent.getTags().entrySet().stream()
                    .map(entry -> {
                        final TagType tag = new TagType();
                        tag.setName(entry.getKey());
                        tag.setValue(entry.getValue());
                        return tag;
                    })
                    .forEach(tag -> tags.getTag().add(tag));
            statistic.setTags(tags);
        }
        return statistic;
    }

    private <T> T getValueAsType(final InternalStatisticEvent.Type type, final Class<T> clazz, final Object value) {
        try {
            return clazz.cast(value);
        } catch (final RuntimeException e) {
            throw new RuntimeException(String.format("Statistic of type %s has value of wrong type %s",
                    type, clazz.getCanonicalName()));
        }
    }

    private XMLGregorianCalendar toXMLGregorianCalendar(final long timeMs) {
        final GregorianCalendar gregorianCalendar = new GregorianCalendar(TIME_ZONE_UTC);
        gregorianCalendar.setTimeInMillis(timeMs);
        return datatypeFactory.newXMLGregorianCalendar(gregorianCalendar);
    }

    private byte[] marshall(final Statistics statistics) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final OutputStreamWriter writer = new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8);
        try {
            getMarshaller().marshal(statistics, writer);
        } catch (final JAXBException e) {
            throw new RuntimeException("Error marshalling Statistics object", e);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private Marshaller getMarshaller() {
        try {
            if (jaxbContext == null) {
                try {
                    jaxbContext = JAXBContext.newInstance(Statistics.class);
                } catch (final JAXBException e) {
                    throw new RuntimeException(String.format("Unable to create JAXBContext for class %s",
                            STATISTICS_CLASS.getCanonicalName()), e);
                }
            }

            final Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, StandardCharsets.UTF_8.name());
            return marshaller;
        } catch (final JAXBException e) {
            throw new RuntimeException("Error creating marshaller for class " + STATISTICS_CLASS.getCanonicalName(), e);
        }
    }

    private String getTopic(final InternalStatisticEvent.Type type) {
        final String typeName = type.toString().toLowerCase();
        if (typeName.equals("count")) {
            return internalStatisticsConfig.getKafkaTopicsConfig().getCount();
        }
        if (typeName.equals("value")) {
            return internalStatisticsConfig.getKafkaTopicsConfig().getValue();
        }

        throw new RuntimeException(String.format(
                "Missing value for property %s, unable to send internal statistics", typeName));
    }

    private int getBatchSize() {
        return internalStatisticsConfig.getEventsPerMessage();
    }

    @Override
    public String getDocRefType() {
        return docRefType;
    }
}
