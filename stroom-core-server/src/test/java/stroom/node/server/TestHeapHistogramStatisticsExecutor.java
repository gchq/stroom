package stroom.node.server;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import stroom.node.shared.Node;
import stroom.node.shared.Rack;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.StatisticTag;
import stroom.statistics.common.Statistics;
import stroom.statistics.common.StatisticsFactory;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.util.List;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestHeapHistogramStatisticsExecutor extends StroomUnitTest {

    @Mock
    StatisticsFactory statisticsFactory;

    @Mock
    Statistics statistics;

    @Captor
    ArgumentCaptor<List<StatisticEvent>> eventsCaptor;

    NodeCache nodeCache;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final Rack rack1 = Rack.create("rack1");
        final Node node1a = Node.create(rack1, "1a");
        nodeCache = new NodeCache(node1a);
    }

    @Test
    public void test1() throws InterruptedException {

        //Given
        MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();
        mockStroomPropertyService.setProperty(HeapHistogramService.CLASS_NAME_MATCH_REGEX_PROP_KEY, "^stroom\\..*$");

        HeapHistogramService heapHistogramService = new HeapHistogramService(mockStroomPropertyService);

        HeapHistogramStatisticsExecutor executor = new HeapHistogramStatisticsExecutor(
                heapHistogramService, statisticsFactory, nodeCache);

        Mockito.when(statisticsFactory.instance())
                .thenReturn(statistics);

        //When
        executor.exec();

        //Then
        Mockito.verify(statistics, Mockito.timeout(5_000).times(1))
                .putEvents(eventsCaptor.capture());

        List<StatisticEvent> statisticEvents = eventsCaptor.getValue();

        //each histo entry should produce 2 stat events so we must have at least 2 events
        Assert.assertTrue(statisticEvents.size() > 2);

        //Ensure all class names start with stroom as that was the regex applied in the property
        Assert.assertTrue(statisticEvents.stream()
                .map(statisticEvent ->
                        //map event to class name tag value
                        statisticEvent.getTagList().stream()
                                .filter(statisticTag -> statisticTag.getTag().equals(HeapHistogramStatisticsExecutor.CLASS_NAME_TAG_NAME))
                                .map(StatisticTag::getValue)
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Tag not found"))
                )
                .allMatch(className -> className.startsWith("stroom")));
    }

    @Test
    public void test2() throws InterruptedException {

        //Given
        MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();
        //no regex so should get all classes back
        mockStroomPropertyService.setProperty(HeapHistogramService.CLASS_NAME_MATCH_REGEX_PROP_KEY, "");

        HeapHistogramService heapHistogramService = new HeapHistogramService(mockStroomPropertyService);

        HeapHistogramStatisticsExecutor executor = new HeapHistogramStatisticsExecutor(
                heapHistogramService, statisticsFactory, nodeCache);

        Mockito.when(statisticsFactory.instance())
                .thenReturn(statistics);

        //When
        executor.exec();

        //Then
        Mockito.verify(statistics, Mockito.timeout(5_000).times(1))
                .putEvents(eventsCaptor.capture());

        List<StatisticEvent> statisticEvents = eventsCaptor.getValue();

        //each histo entry should produce 2 stat events so we must have at least 2 events
        Assert.assertTrue(statisticEvents.size() > 2);

        //Ensure we have multiple starting letters of the class names to show a variety of classes are coming back
        Assert.assertTrue(statisticEvents.stream()
                .map(statisticEvent ->
                        //map event to class name tag value
                        statisticEvent.getTagList().stream()
                                .filter(statisticTag -> statisticTag.getTag().equals(HeapHistogramStatisticsExecutor.CLASS_NAME_TAG_NAME))
                                .map(StatisticTag::getValue)
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Tag not found"))
                )
                .map(className -> className.substring(0, 1))
                .distinct()
                .count() > 1);
    }
}


