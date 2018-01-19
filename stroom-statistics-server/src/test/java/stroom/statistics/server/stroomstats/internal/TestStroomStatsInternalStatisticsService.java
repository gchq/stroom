package stroom.statistics.server.stroomstats.internal;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.connectors.kafka.StroomKafkaProducer;
import stroom.connectors.kafka.StroomKafkaProducerRecord;
import stroom.node.server.MockStroomPropertyService;
import stroom.query.api.v2.DocRef;
import stroom.statistics.internal.InternalStatisticEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@RunWith(MockitoJUnitRunner.class)
public class TestStroomStatsInternalStatisticsService {

    public static final String DOC_REF_TYPE_1 = "myDocRefType1";
    public static final String DOC_REF_TYPE_2 = "myDocRefType2";
    private static final Logger LOGGER = LoggerFactory.getLogger(TestStroomStatsInternalStatisticsService.class);
    private final MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();
    @Captor
    ArgumentCaptor<Consumer<Throwable>> exceptionHandlerCaptor;
    @Mock
    private StroomKafkaProducer mockStroomKafkaProducer;

    @Test
    public void putEvents_multipleEvents() {

        mockStroomPropertyService.setProperty(StroomStatsInternalStatisticsService.PROP_KEY_DOC_REF_TYPE, DOC_REF_TYPE_1);
        mockStroomPropertyService.setProperty(
                StroomStatsInternalStatisticsService.PROP_KEY_PREFIX_KAFKA_TOPICS +
                        InternalStatisticEvent.Type.COUNT.toString().toLowerCase(),
                "MyTopic");

        StroomStatsInternalStatisticsService stroomStatsInternalStatisticsService = new StroomStatsInternalStatisticsService(
                Optional.of(mockStroomKafkaProducer),
                mockStroomPropertyService
        );

        //assemble test data
        InternalStatisticEvent event1 = InternalStatisticEvent.createPlusOneCountStat("myKey", 0, Collections.emptyMap());
        InternalStatisticEvent event2 = InternalStatisticEvent.createPlusOneCountStat("myKey", 1, Collections.emptyMap());
        InternalStatisticEvent event3 = InternalStatisticEvent.createPlusOneCountStat("myKey", 1, Collections.emptyMap());
        DocRef docRefA = new DocRef(DOC_REF_TYPE_1, UUID.randomUUID().toString(), "myStat1");
        DocRef docRefB = new DocRef(DOC_REF_TYPE_2, UUID.randomUUID().toString(), "myStat2");
        Map<DocRef, List<InternalStatisticEvent>> map = ImmutableMap.of(
                docRefA, Arrays.asList(event1, event2),
                docRefB, Collections.singletonList(event3));

        stroomStatsInternalStatisticsService.putEvents(map);

        //two different doc refs so two calls to producer
        Mockito.verify(mockStroomKafkaProducer, Mockito.times(2))
                .sendAsync(Mockito.any(StroomKafkaProducerRecord.class), Mockito.any());
    }

    @Test
    public void putEvents_exception() {

        mockStroomPropertyService.setProperty(StroomStatsInternalStatisticsService.PROP_KEY_DOC_REF_TYPE, DOC_REF_TYPE_1);
        mockStroomPropertyService.setProperty(
                StroomStatsInternalStatisticsService.PROP_KEY_PREFIX_KAFKA_TOPICS +
                        InternalStatisticEvent.Type.COUNT.toString().toLowerCase(),
                "MyTopic");

        StroomStatsInternalStatisticsService stroomStatsInternalStatisticsService = new StroomStatsInternalStatisticsService(
                Optional.of(mockStroomKafkaProducer),
                mockStroomPropertyService
        );

        //assemble test data
        InternalStatisticEvent event = InternalStatisticEvent.createPlusOneCountStat("myKey", 0, Collections.emptyMap());
        DocRef docRef = new DocRef(DOC_REF_TYPE_1, UUID.randomUUID().toString(), "myStat");
        Map<DocRef, List<InternalStatisticEvent>> map = ImmutableMap.of(docRef, Collections.singletonList(event));

        //exercise the service
        stroomStatsInternalStatisticsService.putEvents(map);

        //ensure sendAsync is called
        Mockito.verify(mockStroomKafkaProducer)
                .sendAsync(Mockito.any(StroomKafkaProducerRecord.class), exceptionHandlerCaptor.capture());

        //create an exception in the handler
        exceptionHandlerCaptor.getValue()
                .accept(new RuntimeException("Test Exception inside mockStroomKafkaProducer"));

        //the exception handler should just  log so nothing needs to be asserted
    }
}