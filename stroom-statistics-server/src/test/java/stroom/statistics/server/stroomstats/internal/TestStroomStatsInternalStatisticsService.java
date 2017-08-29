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
import stroom.node.server.MockStroomPropertyService;
import stroom.query.api.v2.DocRef;
import stroom.statistics.internal.InternalStatisticEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@RunWith(MockitoJUnitRunner.class)
public class TestStroomStatsInternalStatisticsService {

    public static final String DOC_REF_TYPE_1 = "myDocRefType1";
    public static final String DOC_REF_TYPE_2 = "myDocRefType2";
    private static final Logger LOGGER = LoggerFactory.getLogger(TestStroomStatsInternalStatisticsService.class);
    private final MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();
    @Captor
    ArgumentCaptor<Consumer<Exception>> exceptionHandlerCaptor;
    @Mock
    private StroomKafkaProducer stroomKafkaProducer;

    @Test
    public void putEvents_multipleEvents() {

        mockStroomPropertyService.setProperty(StroomStatsInternalStatisticsService.PROP_KEY_DOC_REF_TYPE, DOC_REF_TYPE_1);
        mockStroomPropertyService.setProperty(
                StroomStatsInternalStatisticsService.PROP_KEY_PREFIX_KAFKA_TOPICS +
                        InternalStatisticEvent.Type.COUNT.toString().toLowerCase(),
                "MyTopic");

        StroomStatsInternalStatisticsService stroomStatsInternalStatisticsService = new StroomStatsInternalStatisticsService(
                name -> stroomKafkaProducer,
                mockStroomPropertyService
        );
        stroomStatsInternalStatisticsService.postConstruct();

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
        Mockito.verify(stroomKafkaProducer, Mockito.times(2))
                .send(Mockito.any(), Mockito.any(), Mockito.any());

        stroomStatsInternalStatisticsService.preDestroy();
    }

    @Test(expected = RuntimeException.class)
    public void putEvents_exception() {

        mockStroomPropertyService.setProperty(StroomStatsInternalStatisticsService.PROP_KEY_DOC_REF_TYPE, DOC_REF_TYPE_1);
        mockStroomPropertyService.setProperty(
                StroomStatsInternalStatisticsService.PROP_KEY_PREFIX_KAFKA_TOPICS +
                        InternalStatisticEvent.Type.COUNT.toString().toLowerCase(),
                "MyTopic");

        //when flush is called on the producer capture the exceptionhandler passed to the send method and give an exception
        //to the handler to simulate a failure on the send that will only manifest itself on the flush
        Mockito.doAnswer(invocation -> {
            Mockito.verify(stroomKafkaProducer)
                    .send(Mockito.any(), Mockito.any(), exceptionHandlerCaptor.capture());
            exceptionHandlerCaptor.getValue()
                    .accept(new RuntimeException("Exception inside StroomKafkaProducer"));
            return null;
        }).when(stroomKafkaProducer).flush();

        StroomStatsInternalStatisticsService stroomStatsInternalStatisticsService = new StroomStatsInternalStatisticsService(
                name -> stroomKafkaProducer,
                mockStroomPropertyService
        );
        stroomStatsInternalStatisticsService.postConstruct();

        //assemble test data
        InternalStatisticEvent event = InternalStatisticEvent.createPlusOneCountStat("myKey", 0, Collections.emptyMap());
        DocRef docRef = new DocRef(DOC_REF_TYPE_1, UUID.randomUUID().toString(), "myStat");
        Map<DocRef, List<InternalStatisticEvent>> map = ImmutableMap.of(docRef, Collections.singletonList(event));

        //exercise the service
        try {
            stroomStatsInternalStatisticsService.putEvents(map);
            stroomStatsInternalStatisticsService.preDestroy();
        } catch (Exception e) {
            LOGGER.info("Caught expected exception: {} ", e.getMessage(), e);
            throw e;
        }
    }

}