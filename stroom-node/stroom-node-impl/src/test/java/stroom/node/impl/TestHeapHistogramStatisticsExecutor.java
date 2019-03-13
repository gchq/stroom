package stroom.node.impl;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.node.api.NodeInfo;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.util.logging.LogUtil;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestHeapHistogramStatisticsExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestHeapHistogramStatisticsExecutor.class);
    private static Function<InternalStatisticEvent, String> STAT_TO_CLASS_NAME_MAPPER = event ->
            event.getTags().get(HeapHistogramStatisticsExecutor.TAG_NAME_CLASS_NAME);
    @Mock
    private InternalStatisticsReceiver mockInternalStatisticsReceiver;
    @Mock
    private NodeInfo nodeInfo;
    @Captor
    private ArgumentCaptor<List<InternalStatisticEvent>> eventsCaptor;
    private HeapHistogramStatisticsExecutor executor;
    private HeapHistogramConfig heapHistogramConfig = new HeapHistogramConfig();

    @BeforeEach
    void setup() {
        try {
            Mockito.when(nodeInfo.getThisNodeName()).thenReturn("1a");
            final HeapHistogramService heapHistogramService = new HeapHistogramService(heapHistogramConfig);
            executor = new HeapHistogramStatisticsExecutor(heapHistogramService, mockInternalStatisticsReceiver, nodeInfo);
        } catch (final RuntimeException e) {
            throw new RuntimeException("Error during test setup", e);
        }
    }

    @Test
    void testExec_stroomClasses() {

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
        assertThat(argValues.get(0).size() > 0).isTrue();

        //the histo is duplicated as two separate stats so two lists of equal size
        assertThat(argValues.get(0).size() == argValues.get(1).size()).isTrue();


        //Ensure all class names start with stroom as that was the regex applied in the property
        for (List<InternalStatisticEvent> statisticEvents : argValues) {
            assertThat(statisticEvents.stream()
                    .map(STAT_TO_CLASS_NAME_MAPPER)
                    .allMatch(className -> className.startsWith("stroom"))).isTrue();

            //check this class features in the list
            Pattern thisClassPattern = Pattern.compile(this.getClass().getName());
            assertThat(statisticEvents.stream()
                    .map(STAT_TO_CLASS_NAME_MAPPER)
                    .anyMatch(thisClassPattern.asPredicate())).isTrue();
        }
    }

    @Test
    void testExec_allClasses() {

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
        assertThat(argValues.get(0).size() > 0).isTrue();

        //the histo is duplicated as two separate stats so two lists of equal size
        assertThat(argValues.get(0).size() == argValues.get(1).size()).isTrue();

        //Ensure we have multiple starting letters of the class names to show a variety of classes are coming back
        for (List<InternalStatisticEvent> statisticEvents : argValues) {
            assertThat(statisticEvents.stream()
                    .map(STAT_TO_CLASS_NAME_MAPPER)
                    .map(className -> className.substring(0, 1))
                    .distinct()
                    .count() > 1).isTrue();
        }
    }

    @Test
    void testExecBadExecutable() {
        assertThatThrownBy(() -> {
            //Given
            heapHistogramConfig.setjMapExecutable("badNameForJmapExecutable");
            executor.exec();

        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testRegex() {
        String regex = "((?<=\\$\\$)[0-9a-f]+|(?<=\\$\\$Lambda\\$)[0-9]+\\/[0-9]+)";

        Pattern pattern = Pattern.compile(regex);

        String input = "stroom.query.audit.client.DocRefResourceHttpClient$$Lambda$46/1402766141";

        String output = pattern.matcher(input).replaceAll("--");

        assertThat(output).isEqualTo("stroom.query.audit.client.DocRefResourceHttpClient$$Lambda$--");
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
                        LogUtil.message("Non zero exit code: {}, error: {}", exitCode, error));
            }
            return stdOut.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error executing command {}", (Object[]) cmdPlusArgs), e);
        }
    }

}


