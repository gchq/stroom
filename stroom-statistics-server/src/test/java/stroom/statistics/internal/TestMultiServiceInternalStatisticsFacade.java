package stroom.statistics.internal;

import com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.node.server.MockStroomPropertyService;
import stroom.query.api.v1.DocRef;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class TestMultiServiceInternalStatisticsFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestMultiServiceInternalStatisticsFacade.class);

    private static final String STAT_KEY_A = "MyKeyA";
    private static final String STAT_KEY_B = "MyKeyB";
    private static final String DOC_REF_TYPE_1 = "DocRefType1";
    private static final String DOC_REF_TYPE_2 = "DocRefType2";
    private static final String STAT_NAME_A1 = "MyStatA1";
    private static final String STAT_NAME_A2 = "MyStatA2";
    private static final String STAT_NAME_B1 = "MyStatB1";
    private static final DocRef DOC_REF_A1 = new DocRef(DOC_REF_TYPE_1, UUID.randomUUID().toString(), STAT_NAME_A1);
    private static final DocRef DOC_REF_A2 = new DocRef(DOC_REF_TYPE_2, UUID.randomUUID().toString(), STAT_NAME_A2);
    private static final DocRef DOC_REF_B1 = new DocRef(DOC_REF_TYPE_1, UUID.randomUUID().toString(), STAT_NAME_B1);
    private static final InternalStatisticEvent EVENT_A601 = createEvent(STAT_KEY_A, 601L);
    private static final InternalStatisticEvent EVENT_A602 = createEvent(STAT_KEY_A, 602L);
    private static final InternalStatisticEvent EVENT_A603 = createEvent(STAT_KEY_A, 603L);
    private static final InternalStatisticEvent EVENT_B701 = createEvent(STAT_KEY_B, 701L);
    private static final InternalStatisticEvent EVENT_B702 = createEvent(STAT_KEY_B, 702L);
    private static final InternalStatisticEvent EVENT_B703 = createEvent(STAT_KEY_B, 703L);

    private final MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();

    @Mock
    private InternalStatisticDocRefCache internalStatisticDocRefCache;
    @Mock
    private InternalStatisticsService internalStatisticsService1;
    @Mock
    private InternalStatisticsService internalStatisticsService2;
    @Captor
    private ArgumentCaptor<Map<DocRef, List<InternalStatisticEvent>>> argCaptor1;
    @Captor
    private ArgumentCaptor<Map<DocRef, List<InternalStatisticEvent>>> argCaptor2;


    @Test
    public void putEvents() throws Exception {

        //keyA has a docRef for both services
        Mockito.when(internalStatisticDocRefCache.getDocRefs(Mockito.eq(STAT_KEY_A)))
                .thenReturn(Arrays.asList(DOC_REF_A1, DOC_REF_A2));
        Assertions.assertThat(internalStatisticDocRefCache.getDocRefs(STAT_KEY_A))
                .containsExactly(DOC_REF_A1, DOC_REF_A2);

        //keyB only has a docref for service1
        Mockito.when(internalStatisticDocRefCache.getDocRefs(Mockito.eq(STAT_KEY_B)))
                .thenReturn(Arrays.asList(DOC_REF_B1));
        Assertions.assertThat(internalStatisticDocRefCache.getDocRefs(STAT_KEY_B))
                .containsExactly(DOC_REF_B1);

        //service1 supports docRefType1, etc.
        Map<String, InternalStatisticsService> docRefTypeToServiceMap = ImmutableMap.of(
                DOC_REF_TYPE_1, internalStatisticsService1, //i.e. docRefA1 and B1
                DOC_REF_TYPE_2, internalStatisticsService2); //i.e. docRefA2

        MultiServiceInternalStatisticsFacade facade = new MultiServiceInternalStatisticsFacade(
                internalStatisticDocRefCache,
                docRefTypeToServiceMap);


        //fire 6 events at the facade, 3 for each key
        facade.putEvents(Arrays.asList(
                EVENT_A601,
                EVENT_A602,
                EVENT_A603,
                EVENT_B701,
                EVENT_B702,
                EVENT_B703));

        Mockito.verify(internalStatisticsService1, Mockito.times(1))
                .putEvents(argCaptor1.capture());
        Assertions.assertThat(argCaptor1.getValue().keySet())
                .containsExactlyInAnyOrder(DOC_REF_A1, DOC_REF_B1);
        Assertions.assertThat(argCaptor1.getValue().get(DOC_REF_A1))
                .containsExactly(EVENT_A601, EVENT_A602, EVENT_A603);
        Assertions.assertThat(argCaptor1.getValue().get(DOC_REF_B1))
                .containsExactly(EVENT_B701, EVENT_B702, EVENT_B703);

        Mockito.verify(internalStatisticsService2, Mockito.times(1))
                .putEvents(argCaptor2.capture());
        Assertions.assertThat(argCaptor2.getValue().keySet())
                .containsExactly(DOC_REF_A2);
        Assertions.assertThat(argCaptor2.getValue().get(DOC_REF_A2))
                .containsExactly(EVENT_A601, EVENT_A602, EVENT_A603);
    }

    private static InternalStatisticEvent createEvent(final String key, final Long timeMs) {
        return InternalStatisticEvent.createPlusOneCountStat(key, timeMs, null);
    }

}