/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.node.impl;

import stroom.node.api.NodeInfo;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.task.api.SimpleTaskContextFactory;

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

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestHeapHistogramStatisticsExecutor {

    private static final Function<InternalStatisticEvent, String> STAT_TO_CLASS_NAME_MAPPER = event ->
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
            final HeapHistogramService heapHistogramService = new HeapHistogramService(this::getHeapHistogramConfig);
            executor = new HeapHistogramStatisticsExecutor(heapHistogramService,
                    mockInternalStatisticsReceiver,
                    nodeInfo, new SimpleTaskContextFactory());
        } catch (final RuntimeException e) {
            throw new RuntimeException("Error during test setup", e);
        }
    }

    @Test
    void testExec_stroomClasses() {
        //When
        executor.exec();

        //Then
        Mockito.verify(mockInternalStatisticsReceiver, Mockito.timeout(5_000).times(2))
                .putEvents(eventsCaptor.capture());

        final List<List<InternalStatisticEvent>> argValues = eventsCaptor.getAllValues();

        //We must have some stroom classes in the list
        assertThat(argValues.get(0).size() > 0).isTrue();

        //the histo is duplicated as two separate stats so two lists of equal size
        assertThat(argValues.get(0).size() == argValues.get(1).size()).isTrue();


        //Ensure all class names start with stroom as that was the regex applied in the property
        for (final List<InternalStatisticEvent> statisticEvents : argValues) {
            assertThat(statisticEvents.stream()
                    .map(STAT_TO_CLASS_NAME_MAPPER)
                    .allMatch(className -> className.startsWith("stroom"))).isTrue();

            //check this class features in the list
            final Pattern thisClassPattern = Pattern.compile(this.getClass().getName());
            assertThat(statisticEvents.stream()
                    .map(STAT_TO_CLASS_NAME_MAPPER)
                    .anyMatch(thisClassPattern.asPredicate())).isTrue();
        }
    }

    @Test
    void testExec_allClasses() {

        //Given
        //no regex so should get all classes back
        heapHistogramConfig = heapHistogramConfig.withClassNameMatchRegex("");

//        mockStroomPropertyService.setProperty(HeapHistogramService.CLASS_NAME_MATCH_REGEX_PROP_KEY, "");

//        Mockito.when(statisticsFactory.instance())
//                .thenReturn(statistics);

        //When
        executor.exec();

        //Then
        Mockito.verify(mockInternalStatisticsReceiver, Mockito.timeout(5_000).times(2))
                .putEvents(eventsCaptor.capture());

        final List<List<InternalStatisticEvent>> argValues = eventsCaptor.getAllValues();

        //We must have some classes in the list
        assertThat(argValues.get(0).size() > 0).isTrue();

        //the histo is duplicated as two separate stats so two lists of equal size
        assertThat(argValues.get(0).size() == argValues.get(1).size()).isTrue();

        //Ensure we have multiple starting letters of the class names to show a variety of classes are coming back
        for (final List<InternalStatisticEvent> statisticEvents : argValues) {
            assertThat(statisticEvents.stream()
                    .map(STAT_TO_CLASS_NAME_MAPPER)
                    .map(className -> className.substring(0, 1))
                    .distinct()
                    .count() > 1).isTrue();
        }
    }

    @Test
    void testRegex() {
        final String regex = "((?<=\\$\\$)[0-9a-f]+|(?<=\\$\\$Lambda\\$)[0-9]+\\/[0-9]+)";

        final Pattern pattern = Pattern.compile(regex);

        final String input = "stroom.query.audit.client.DocRefResourceHttpClient$$Lambda$46/1402766141";

        final String output = pattern.matcher(input).replaceAll("--");

        assertThat(output).isEqualTo("stroom.query.audit.client.DocRefResourceHttpClient$$Lambda$--");
    }

    private HeapHistogramConfig getHeapHistogramConfig() {
        return heapHistogramConfig;
    }
}


