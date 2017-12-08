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
import stroom.statistics.server.sql.StatisticEvent;
import stroom.statistics.server.sql.Statistics;
import stroom.util.test.StroomExpectedException;
import stroom.util.test.StroomJUnit4ClassRunner;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestHeapHistogramStatisticsExecutor {
    @Mock
    private
    Statistics statistics;

    @Captor
    private
    ArgumentCaptor<List<StatisticEvent>> eventsCaptor;

    private MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();
    private HeapHistogramService heapHistogramService = new HeapHistogramService(mockStroomPropertyService);
    private HeapHistogramStatisticsExecutor executor;

    @Before
    public void setup() {
        try {
            MockitoAnnotations.initMocks(this);
            final Rack rack1 = Rack.create("rack1");
            final Node node1a = Node.create(rack1, "1a");
            final NodeCache nodeCache = new NodeCache(node1a);

            mockStroomPropertyService.setProperty(HeapHistogramService.CLASS_NAME_MATCH_REGEX_PROP_KEY, "^stroom\\..*$");
            mockStroomPropertyService.setProperty(HeapHistogramService.JMAP_EXECUTABLE_PROP_KEY, "jmap");

            executor = new HeapHistogramStatisticsExecutor(heapHistogramService, statistics, nodeCache);
        } catch (Exception e) {
            throw new RuntimeException("Error during test setup", e);
        }
    }

    private static Function<StatisticEvent, String> STAT_TO_CLASS_NAME_MAPPER = statisticEvent ->
            statisticEvent.getTagValue(HeapHistogramStatisticsExecutor.CLASS_NAME_TAG_NAME);

    @Test
    public void testExec_stroomClasses() throws InterruptedException {

//        //Given
//        Mockito.when(statisticsFactory.instance())
//                .thenReturn(statistics);

        //When
        executor.exec();

        //Then
        Mockito.verify(statistics, Mockito.timeout(5_000).times(2))
                .putEvents(eventsCaptor.capture());

        List<List<StatisticEvent>> argValues = eventsCaptor.getAllValues();

        //We must have some stroom classes in the list
        Assert.assertTrue(argValues.get(0).size() > 0);

        //the histo is duplicated as two separate stats so two lists of equal size
        Assert.assertTrue(argValues.get(0).size() == argValues.get(1).size());


        //Ensure all class names start with stroom as that was the regex applied in the property
        for (List<StatisticEvent> statisticEvents : argValues) {
            Assert.assertTrue(statisticEvents.stream()
                    .map(STAT_TO_CLASS_NAME_MAPPER)
                    .allMatch(className -> className.startsWith("stroom")));

            //check this class features in the list
            Pattern thisClassPattern = Pattern.compile(this.getClass().getName());
            Assert.assertTrue(statisticEvents.stream()
                    .map(STAT_TO_CLASS_NAME_MAPPER)
                    .anyMatch(thisClassPattern.asPredicate()));
        }
    }

    @Test
    public void testExec_allClasses() throws InterruptedException {

        //Given
        //no regex so should get all classes back
        mockStroomPropertyService.setProperty(HeapHistogramService.CLASS_NAME_MATCH_REGEX_PROP_KEY, "");

//        Mockito.when(statisticsFactory.instance())
//                .thenReturn(statistics);

        //When
        executor.exec();

        //Then
        Mockito.verify(statistics, Mockito.timeout(5_000).times(2))
                .putEvents(eventsCaptor.capture());

        List<List<StatisticEvent>> argValues = eventsCaptor.getAllValues();

        //We must have some classes in the list
        Assert.assertTrue(argValues.get(0).size() > 0);

        //the histo is duplicated as two separate stats so two lists of equal size
        Assert.assertTrue(argValues.get(0).size() == argValues.get(1).size());

        //Ensure we have multiple starting letters of the class names to show a variety of classes are coming back
        for (List<StatisticEvent> statisticEvents : argValues) {
            Assert.assertTrue(statisticEvents.stream()
                    .map(STAT_TO_CLASS_NAME_MAPPER)
                    .map(className -> className.substring(0, 1))
                    .distinct()
                    .count() > 1);
        }
    }

    @Test
    @StroomExpectedException(exception = {RuntimeException.class, IOException.class})
    public void testExecBadExecutable() throws InterruptedException {
        //Given
        mockStroomPropertyService.setProperty(HeapHistogramService.JMAP_EXECUTABLE_PROP_KEY, "badNameForJmapExecutable");

        //When
        boolean thrownException = false;
        try {
            executor.exec();
        } catch (Exception e) {
            thrownException = true;
        }

        Assert.assertTrue(thrownException);
    }

}


