package stroom.statistics.stroomstats.internal;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.connectors.kafka.StroomKafkaProducer;
import stroom.kafka.StroomKafkaProducerFactoryService;
import stroom.connectors.kafka.StroomKafkaProducerRecord;
import stroom.properties.api.PropertyService;
import stroom.docref.DocRef;
import stroom.statistics.internal.InternalStatisticEvent;
import stroom.statistics.internal.InternalStatisticsService;
import stroom.stats.schema.v4.ObjectFactory;
import stroom.stats.schema.v4.Statistics;
import stroom.stats.schema.v4.TagType;
import stroom.util.collections.BatchingIterator;

import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Consumer;

@SuppressWarnings("unused")
class StroomStatsInternalStatisticsService implements InternalStatisticsService {

    static final String PROP_KEY_DOC_REF_TYPE = "stroom.services.stroomStats.docRefType";
    static final String PROP_KEY_PREFIX_KAFKA_TOPICS = "stroom.services.stroomStats.kafkaTopics.";
    static final String PROP_KEY_EVENTS_PER_MESSAGE = "stroom.services.stroomStats.internalStats.eventsPerMessage";

    public static final String STATISTICS_SCHEMA_VERSION = "4.0.0";
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomStatsInternalStatisticsService.class);
    private static final Class<Statistics> STATISTICS_CLASS = Statistics.class;
    private static final TimeZone TIME_ZONE_UTC = TimeZone.getTimeZone(ZoneId.from(ZoneOffset.UTC));

    private final StroomKafkaProducerFactoryService stroomKafkaProducerFactory;
    private final PropertyService stroomPropertyService;
    private final String docRefType;
    private final JAXBContext jaxbContext;
    private final DatatypeFactory datatypeFactory;

    @Inject
    StroomStatsInternalStatisticsService(final StroomKafkaProducerFactoryService stroomKafkaProducerFactory,
                                         final PropertyService stroomPropertyService) {
        this.stroomKafkaProducerFactory = stroomKafkaProducerFactory;
        this.stroomPropertyService = stroomPropertyService;
        this.docRefType = stroomPropertyService.getProperty(PROP_KEY_DOC_REF_TYPE);

        try {
            this.jaxbContext = JAXBContext.newInstance(Statistics.class);
        } catch (JAXBException e) {
            throw new RuntimeException(String.format("Unable to create JAXBContext for class %s",
                    STATISTICS_CLASS.getCanonicalName()), e);
        }

        try {
            this.datatypeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new RuntimeException("Unable to create new DatatypeFactory instance", e);
        }
    }

    @Override
    public void putEvents(final Map<DocRef, List<InternalStatisticEvent>> eventsMap) {

        StroomKafkaProducer stroomKafkaProducer = stroomKafkaProducerFactory.getConnector().orElse(null);
        if (stroomKafkaProducer == null) {
            throw new RuntimeException("The Kafka producer isn't initialised, unable to send any events");
        }

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
                                    sendMessage(stroomKafkaProducer, topic, key, eventsBatch));
                });
    }

    private void sendMessage(final StroomKafkaProducer stroomKafkaProducer,
                             final String topic,
                             final String key,
                             final List<InternalStatisticEvent> events) {

        final byte[] message = buildMessage(events);

        final StroomKafkaProducerRecord<String, byte[]> producerRecord =
                new StroomKafkaProducerRecord.Builder<String, byte[]>()
                        .topic(topic)
                        .key(key)
                        .value(message)
                        .build();

        final Consumer<Throwable> exceptionHandler = StroomKafkaProducer
                .createLogOnlyExceptionHandler(LOGGER, topic, key);

        //These are only internal stats so just send them async for performance
        stroomKafkaProducer.sendAsync(producerRecord, exceptionHandler);
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
        ObjectFactory objectFactory = new ObjectFactory();
        Statistics.Statistic statistic = objectFactory.createStatisticsStatistic();
        statistic.setTime(toXMLGregorianCalendar(internalStatisticEvent.getTimeMs()));
        InternalStatisticEvent.Type type = internalStatisticEvent.getType();

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
            Statistics.Statistic.Tags tags = new Statistics.Statistic.Tags();
            internalStatisticEvent.getTags().entrySet().stream()
                    .map(entry -> {
                        TagType tag = new TagType();
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
        GregorianCalendar gregorianCalendar = new GregorianCalendar(TIME_ZONE_UTC);
        gregorianCalendar.setTimeInMillis(timeMs);
        return datatypeFactory.newXMLGregorianCalendar(gregorianCalendar);
    }

    private byte[] marshall(final Statistics statistics) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8);
        try {
            getMarshaller().marshal(statistics, writer);
        } catch (JAXBException e) {
            throw new RuntimeException("Error marshalling Statistics object", e);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private Marshaller getMarshaller() {
        try {
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, StandardCharsets.UTF_8.name());
            return marshaller;
        } catch (JAXBException e) {
            throw new RuntimeException("Error creating marshaller for class " + STATISTICS_CLASS.getCanonicalName(), e);
        }
    }

    private String getTopic(final InternalStatisticEvent.Type type) {

        String propKey = PROP_KEY_PREFIX_KAFKA_TOPICS + type.toString().toLowerCase();
        String topic = stroomPropertyService.getProperty(propKey);

        if (topic == null || topic.isEmpty()) {
            throw new RuntimeException(
                    String.format("Missing value for property %s, unable to send internal statistics", topic));
        }
        return topic;
    }

    private int getBatchSize() {
        return stroomPropertyService.getIntProperty(PROP_KEY_EVENTS_PER_MESSAGE, 100);
    }

    @Override
    public String getDocRefType() {
        return docRefType;
    }
}
