package stroom.node;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.node.shared.Node;
import stroom.node.shared.Rack;
import stroom.statistics.internal.InternalStatisticEvent;
import stroom.statistics.internal.InternalStatisticsReceiver;
import stroom.util.logging.LambdaLogger;
import stroom.util.test.StroomExpectedException;
import stroom.util.test.StroomJUnit4ClassRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestHeapHistogramStatisticsExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestHeapHistogramStatisticsExecutor.class);

    @Mock
    private InternalStatisticsReceiver mockInternalStatisticsReceiver;

    @Captor
    private ArgumentCaptor<List<InternalStatisticEvent>> eventsCaptor;

    private HeapHistogramStatisticsExecutor executor;
    private HeapHistogramConfig heapHistogramConfig = new HeapHistogramConfig();

    @Before
    public void setup() {
        try {
            MockitoAnnotations.initMocks(this);
            final Rack rack1 = Rack.create("rack1");
            final Node node1a = Node.create(rack1, "1a");
            final NodeCache nodeCache = new NodeCache(node1a);

            final HeapHistogramService heapHistogramService = new HeapHistogramService(heapHistogramConfig);
            executor = new HeapHistogramStatisticsExecutor(heapHistogramService, mockInternalStatisticsReceiver, nodeCache);
        } catch (final RuntimeException e) {
            throw new RuntimeException("Error during test setup", e);
        }
    }

    private static Function<InternalStatisticEvent, String> STAT_TO_CLASS_NAME_MAPPER = event ->
            event.getTags().get(HeapHistogramStatisticsExecutor.TAG_NAME_CLASS_NAME);

    @Test
    public void testExec_stroomClasses() {

        // These are here to help diagnose problems finding the jmap executable on your environment.
        // Ensure jmap is on your PATH
        LOGGER.info("pwd={}", executeCmd("pwd"));
        LOGGER.info("PATH={}", executeCmd("/bin/bash", "-c", "echo $PATH"));
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
    public void testExec_allClasses() {

        //Given
        //no regex so should get all classes back
        heapHistogramConfig.setClassNameMatchRegex("");

//        mockStroomPropertyService.setProperty(HeapHistogramService.CLASS_NAME_MATCH_REGEX_PROP_KEY, "");

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
    public void testExecBadExecutable() {
        //Given
        heapHistogramConfig.setjMapExecutable("badNameForJmapExecutable");

        //When
        boolean thrownException = false;
        try {
            executor.exec();
        } catch (final RuntimeException e) {
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

    String executeCmd(final String... cmdPlusArgs) {
        try {
            CommandLine command = new CommandLine(cmdPlusArgs[0]);
            if (cmdPlusArgs.length > 1) {
                String[] args = Arrays.copyOfRange(cmdPlusArgs, 1, cmdPlusArgs.length);
                command.addArguments(args, false);
            }

            DefaultExecutor executor = new DefaultExecutor();
            executor.setExitValue(0);
            ExecuteWatchdog watchdog = new ExecuteWatchdog(120000);
            executor.setWatchdog(watchdog);
            //ensure the process is killed if stroom is shutting down
            executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());

            ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
            ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
            PumpStreamHandler pumpStreamHandler = new PumpStreamHandler(stdOut, stdErr);
            executor.setStreamHandler(pumpStreamHandler);
            int exitCode = executor.execute(command);
            if (exitCode != 0) {
                String error = stdErr.toString(StandardCharsets.UTF_8);
                throw new RuntimeException(
                        LambdaLogger.buildMessage("Non zero exit code: {}, error: {}", exitCode, error));
            }
            return stdOut.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error executing command {}", (Object[]) cmdPlusArgs), e);
        }
    }

}


