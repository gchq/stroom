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
    public void test() throws InterruptedException {

        //Given
        MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();
        mockStroomPropertyService.setProperty(HeapHistogramService.CLASS_NAME_MATCH_REGEX_PROP_KEY, "^stroom\\..*$");

        HeapHistogramService heapHistogramService = new HeapHistogramService(mockStroomPropertyService);

        HeapHistogramStatisticsExecutor executor = new HeapHistogramStatisticsExecutor(
                heapHistogramService, statisticsFactory, nodeCache);

        Mockito.when(statisticsFactory.instance()).thenReturn(statistics);

        //When
        executor.exec();

        //TODO replace with something more clever to do the verify as soon as the mock has been called
        Thread.sleep(2_000);

        //Then
        
        Mockito.verify(statistics, Mockito.times(1))
                .putEvents(eventsCaptor.capture());

        Assert.assertTrue(eventsCaptor.getValue().size() > 2);
    }


}


