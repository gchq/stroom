package stroom.node;

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
import stroom.properties.MockStroomPropertyService;
import stroom.statistics.internal.InternalStatisticEvent;
import stroom.statistics.internal.InternalStatisticsReceiver;
import stroom.util.test.StroomExpectedException;
import stroom.util.test.StroomJUnit4ClassRunner;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestHeapHistogramStatisticsExecutor {

    @Mock
    private InternalStatisticsReceiver mockInternalStatisticsReceiver;

    @Captor
    private ArgumentCaptor<List<InternalStatisticEvent>> eventsCaptor;

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

            executor = new HeapHistogramStatisticsExecutor(heapHistogramService, mockInternalStatisticsReceiver, nodeCache);
        } catch (Exception e) {
            throw new RuntimeException("Error during test setup", e);
        }
    }

    private static Function<InternalStatisticEvent, String> STAT_TO_CLASS_NAME_MAPPER = event ->
            event.getTags().get(HeapHistogramStatisticsExecutor.TAG_NAME_CLASS_NAME);

    @Test
    public void testExec_stroomClasses() throws InterruptedException {

        //When
        executor.exec();

        //Then
        Mockito.verify(mockInternalStatisticsReceiver, Mockito.timeout(5_000).times(2))
                .putEvents(eventsCaptor.capture());

        List<List<InternalStatisticEvent>> argValues = eventsCaptor.getAllValues();

        //We must have some stroom classes in the list
        Assert.assertTrue(argValues.get(0).size() > 0);

        //the histo is duplicated as two separate stats so two lists of equal size
        Assert.assertTrue(argValues.get(0).size() == argValues.get(1).size());


        //Ensure all class names start with stroom as that was the regex applied in the property
        for (List<InternalStatisticEvent> statisticEvents : argValues) {
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
        Mockito.verify(mockInternalStatisticsReceiver, Mockito.timeout(5_000).times(2))
                .putEvents(eventsCaptor.capture());

        List<List<InternalStatisticEvent>> argValues = eventsCaptor.getAllValues();

        //We must have some classes in the list
        Assert.assertTrue(argValues.get(0).size() > 0);

        //the histo is duplicated as two separate stats so two lists of equal size
        Assert.assertTrue(argValues.get(0).size() == argValues.get(1).size());

        //Ensure we have multiple starting letters of the class names to show a variety of classes are coming back
        for (List<InternalStatisticEvent> statisticEvents : argValues) {
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

    @Test
    public void testRegex() {
        String regex = "((?<=\\$\\$)[0-9a-f]+|(?<=\\$\\$Lambda\\$)[0-9]+\\/[0-9]+)";

        Pattern pattern = Pattern.compile(regex);

        String input = "stroom.query.audit.client.DocRefResourceHttpClient$$Lambda$46/1402766141";

        String output = pattern.matcher(input).replaceAll("--");

        Assert.assertEquals("stroom.query.audit.client.DocRefResourceHttpClient$$Lambda$--", output);
    }

}


