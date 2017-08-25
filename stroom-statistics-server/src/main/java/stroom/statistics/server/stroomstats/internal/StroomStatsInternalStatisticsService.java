package stroom.statistics.server.stroomstats.internal;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.kafka.FlushMode;
import stroom.kafka.StroomKafkaProducer;
import stroom.kafka.StroomKafkaProducerRecord;
import stroom.node.server.StroomPropertyService;
import stroom.query.api.v2.DocRef;
import stroom.statistics.internal.InternalStatisticEvent;
import stroom.statistics.internal.InternalStatisticsService;
import stroom.stats.schema.Statistics;
import stroom.stats.schema.TagType;

import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

@SuppressWarnings("unused")
@Component
class StroomStatsInternalStatisticsService implements InternalStatisticsService {

    static final String PROP_KEY_DOC_REF_TYPE = "stroom.services.stroomStats.docRefType";
    static final String PROP_KEY_PREFIX_KAFKA_TOPICS = "stroom.services.stroomStats.kafkaTopics.";
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomStatsInternalStatisticsService.class);
    private static final Class<Statistics> STATISTICS_CLASS = Statistics.class;
    private static final TimeZone TIME_ZONE_UTC = TimeZone.getTimeZone(ZoneId.from(ZoneOffset.UTC));

    private final StroomKafkaProducer stroomKafkaProducer;
    private final StroomPropertyService stroomPropertyService;
    private final String docRefType;
    private final JAXBContext jaxbContext;
    private final DatatypeFactory datatypeFactory;

    @Inject
    StroomStatsInternalStatisticsService(final StroomKafkaProducer stroomKafkaProducer,
                                         final StroomPropertyService stroomPropertyService) {

        this.stroomKafkaProducer = stroomKafkaProducer;
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

        Preconditions.checkNotNull(eventsMap);

        try {
            //We work on the basis that a stat may or may not have a valid datasource (StatisticConfiguration) but we
            //will let stroom-stats worry about that and just fire what we have at kafka
            eventsMap.entrySet().stream()
                    .filter(entry ->
                            !entry.getValue().isEmpty())
                    .forEach(entry -> {
                        DocRef docRef = entry.getKey();
                        List<InternalStatisticEvent> events = entry.getValue();
                        String statName = docRef.getName();
                        //all have same name so have same type
                        String topic = getTopic(events.get(0).getType());
                        String message = buildMessage(docRef, events);
                        StroomKafkaProducerRecord<String, String> producerRecord =
                                new StroomKafkaProducerRecord.Builder<String, String>()
                                        .topic(topic)
                                        .key(statName)
                                        .value(message)
                                        .build();
                        stroomKafkaProducer.send(producerRecord, FlushMode.NO_FLUSH, exception -> {
                            throw new RuntimeException(String.format(
                                    "Error sending %s internal stats with name %s to kafka on topic %s, due to (%s)",
                                    events.size(), statName, topic, exception.getMessage()), exception);
                        });
                    });
        } finally {
            //any problems with the send will call the exceptionhandler above to be triggered at this point
            stroomKafkaProducer.flush();
        }
    }

    private String buildMessage(final DocRef docRef, final List<InternalStatisticEvent> events) {

        Statistics statistics = new Statistics();
        Preconditions.checkNotNull(events).stream()
                .map(event -> internalStatisticMapper(docRef, event))
                .forEach(statistic -> statistics.getStatistic().add(statistic));

        return marshall(statistics);
    }

    private Statistics.Statistic internalStatisticMapper(final DocRef docRef,
                                                         final InternalStatisticEvent internalStatisticEvent) {
        Preconditions.checkNotNull(internalStatisticEvent);
        Statistics.Statistic statistic = new Statistics.Statistic();
        statistic.setName(docRef.getName());
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
        } catch (Exception e) {
            throw new RuntimeException(String.format("Statistic of type %s has value of wrong type %s",
                    type, clazz.getCanonicalName()));
        }
    }

    private XMLGregorianCalendar toXMLGregorianCalendar(final long timeMs) {
        GregorianCalendar gregorianCalendar = new GregorianCalendar(TIME_ZONE_UTC);
        gregorianCalendar.setTimeInMillis(timeMs);
        return datatypeFactory.newXMLGregorianCalendar(gregorianCalendar);

    }

    private String marshall(final Statistics statistics) {
        StringWriter stringWriter = new StringWriter();
        try {
            getMarshaller().marshal(statistics, stringWriter);
        } catch (JAXBException e) {
            throw new RuntimeException("Error marshalling Statistics object", e);
        }
        return stringWriter.toString();
    }

    private Marshaller getMarshaller() {
        try {
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
            return marshaller;
        } catch (JAXBException e) {
            throw new RuntimeException("Error creating marshaller for class " + STATISTICS_CLASS.getCanonicalName());
        }
    }

    private String getTopic(final InternalStatisticEvent.Type type) {

        String propKey = PROP_KEY_PREFIX_KAFKA_TOPICS + type.toString().toLowerCase();
        String topic = stroomPropertyService.getProperty(propKey);

        if (Strings.isNullOrEmpty(topic)) {
            throw new RuntimeException(
                    String.format("Missing value for property %s, unable to send internal statistics", topic));
        }
        return topic;
    }

    @Override
    public String getDocRefType() {
        return docRefType;
    }
}
