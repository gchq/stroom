package stroom.statistics.stroomstats.internal;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.kafka.pipeline.KafkaProducer;
import stroom.kafka.pipeline.KafkaProducerFactory;
import stroom.kafka.pipeline.KafkaProducerRecord;
import stroom.statistics.internal.InternalStatisticEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RunWith(MockitoJUnitRunner.class)
public class TestStroomStatsInternalStatisticsService {
    private static final String DOC_REF_TYPE_1 = "myDocRefType1";
    private static final String DOC_REF_TYPE_2 = "myDocRefType2";
    private static final Logger LOGGER = LoggerFactory.getLogger(TestStroomStatsInternalStatisticsService.class);

    @Captor
    private ArgumentCaptor<Consumer<Throwable>> exceptionHandlerCaptor;
    @Mock
    private KafkaProducer mockStroomKafkaProducer;
    @Mock
    private KafkaProducerFactory mockStroomKafkaProducerFactory;

    @Test
    public void putEvents_multipleEvents() {
        final HBaseStatisticsConfig hBaseStatisticsConfig = new HBaseStatisticsConfig();
        hBaseStatisticsConfig.setDocRefType(DOC_REF_TYPE_1);
        hBaseStatisticsConfig.getKafkaTopicsConfig().setCount("MyTopic");

        Mockito.when(mockStroomKafkaProducerFactory.createProducer(Mockito.any())).thenReturn(Optional.of(mockStroomKafkaProducer));
        StroomStatsInternalStatisticsService stroomStatsInternalStatisticsService = new StroomStatsInternalStatisticsService(
                mockStroomKafkaProducerFactory,
                hBaseStatisticsConfig
        );

        //assemble test data
        InternalStatisticEvent event1 = InternalStatisticEvent.createPlusOneCountStat("myKeyA", 0, Collections.emptyMap());
        InternalStatisticEvent event2 = InternalStatisticEvent.createPlusOneCountStat("myKeyA", 1, Collections.emptyMap());
        InternalStatisticEvent event3 = InternalStatisticEvent.createPlusOneCountStat("myKeyB", 1, Collections.emptyMap());
        DocRef docRefA = new DocRef(DOC_REF_TYPE_1, UUID.randomUUID().toString(), "myStat1");
        DocRef docRefB = new DocRef(DOC_REF_TYPE_2, UUID.randomUUID().toString(), "myStat2");
        Map<DocRef, List<InternalStatisticEvent>> map = ImmutableMap.of(
                docRefA, Arrays.asList(event1, event2),
                docRefB, Collections.singletonList(event3));

        stroomStatsInternalStatisticsService.putEvents(map);

        //two different doc refs so two calls to producer
        Mockito.verify(mockStroomKafkaProducer, Mockito.times(2))
                .sendAsync(Mockito.any(KafkaProducerRecord.class), Mockito.any());
    }

    @Test
    public void putEvents_largeBatch() {
        final HBaseStatisticsConfig hBaseStatisticsConfig = new HBaseStatisticsConfig();
        hBaseStatisticsConfig.setDocRefType(DOC_REF_TYPE_1);
        hBaseStatisticsConfig.getKafkaTopicsConfig().setCount("MyTopic");
        hBaseStatisticsConfig.setEventsPerMessage(10);

        Mockito.when(mockStroomKafkaProducerFactory.createProducer(Mockito.any())).thenReturn(Optional.of(mockStroomKafkaProducer));
        StroomStatsInternalStatisticsService stroomStatsInternalStatisticsService = new StroomStatsInternalStatisticsService(
                mockStroomKafkaProducerFactory,
                hBaseStatisticsConfig
        );

        //assemble test data
        DocRef docRefA = new DocRef(DOC_REF_TYPE_1, UUID.randomUUID().toString(), "myStat1");
        DocRef docRefB = new DocRef(DOC_REF_TYPE_2, UUID.randomUUID().toString(), "myStat2");
        Map<DocRef, List<InternalStatisticEvent>> map = ImmutableMap.of(
                docRefA, createNEvents("myKeyA", 100),
                docRefB, createNEvents("myKeyB", 15));

        stroomStatsInternalStatisticsService.putEvents(map);

        //two different doc refs and batch size of 10,
        //so kafka msg count is 10 for A and 2 for B, thus 12
        Mockito.verify(mockStroomKafkaProducer, Mockito.times(12))
                .sendAsync(Mockito.any(KafkaProducerRecord.class), Mockito.any());
    }

    private List<InternalStatisticEvent> createNEvents(final String key, final int count) {

        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> InternalStatisticEvent.createPlusOneCountStat(key, i, Collections.emptyMap()))
                .collect(Collectors.toList());
    }

    @Test
    public void putEvents_exception() {
        final HBaseStatisticsConfig hBaseStatisticsConfig = new HBaseStatisticsConfig();
        hBaseStatisticsConfig.setDocRefType(DOC_REF_TYPE_1);
        hBaseStatisticsConfig.getKafkaTopicsConfig().setCount("MyTopic");

        Mockito.when(mockStroomKafkaProducerFactory.createProducer(Mockito.any())).thenReturn(Optional.of(mockStroomKafkaProducer));
        StroomStatsInternalStatisticsService stroomStatsInternalStatisticsService = new StroomStatsInternalStatisticsService(
                mockStroomKafkaProducerFactory,
                hBaseStatisticsConfig
        );

        //assemble test data
        InternalStatisticEvent event = InternalStatisticEvent.createPlusOneCountStat("myKey", 0, Collections.emptyMap());
        DocRef docRef = new DocRef(DOC_REF_TYPE_1, UUID.randomUUID().toString(), "myStat");
        Map<DocRef, List<InternalStatisticEvent>> map = ImmutableMap.of(docRef, Collections.singletonList(event));

        //exercise the service
        stroomStatsInternalStatisticsService.putEvents(map);

        //ensure sendAsync is called
        Mockito.verify(mockStroomKafkaProducer)
                .sendAsync(Mockito.any(KafkaProducerRecord.class), exceptionHandlerCaptor.capture());

        //create an exception in the handler
        exceptionHandlerCaptor.getValue()
                .accept(new RuntimeException("Test Exception inside mockStroomKafkaProducer"));

        //the exception handler should just  log so nothing needs to be asserted
    }
}