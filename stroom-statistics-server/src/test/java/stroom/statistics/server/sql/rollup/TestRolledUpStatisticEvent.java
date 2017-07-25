/*
 * Copyright 2016 Crown Copyright
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

package stroom.statistics.server.sql.rollup;

import org.junit.Test;
import stroom.statistics.server.sql.StatisticEvent;
import stroom.statistics.server.sql.StatisticTag;
import stroom.statistics.server.sql.TimeAgnosticStatisticEvent;
import stroom.statistics.shared.StatisticType;
import stroom.util.test.StroomUnitTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestRolledUpStatisticEvent extends StroomUnitTest {
    private static final double JUNIT_DOUBLE_EQUALITY_DELTA = 0.001;

    private final List<StatisticTag> tags = new ArrayList<>();
    private StatisticEvent event;

    @Test
    public void testRolledUpStatisticEventStatisticEventListOfListOfStatisticTag() {
        buildEvent();
    }

    @Test
    public void testRolledUpStatisticEventStatisticEvent() {
        buildEvent();

        final RolledUpStatisticEvent rolledUpStatisticEvent = new RolledUpStatisticEvent(event);

        compareEvents(event, rolledUpStatisticEvent);

        assertTrue(rolledUpStatisticEvent.iterator().hasNext());

        int counter = 0;
        for (final TimeAgnosticStatisticEvent timeAgnosticStatisticEvent : rolledUpStatisticEvent) {
            assertEquals(event.getTimeAgnosticStatisticEvent(), timeAgnosticStatisticEvent);

            counter++;
        }

        assertEquals(1, counter);
    }

    private void buildEvent() {
        tags.add(new StatisticTag("Tag1", "Tag1Val"));
        tags.add(new StatisticTag("Tag2", "Tag2Val"));
        tags.add(new StatisticTag("Tag3", "Tag3Val"));

        event = StatisticEvent.createCount(1234L, "MtStat", tags, 1000L);
    }

    private void compareEvents(final StatisticEvent statisticEvent,
                               final RolledUpStatisticEvent rolledUpStatisticEvent) {
        assertEquals(statisticEvent.getTimeMs(), rolledUpStatisticEvent.getTimeMs());
        assertEquals(statisticEvent.getName(), rolledUpStatisticEvent.getName());
        assertEquals(statisticEvent.getType(), rolledUpStatisticEvent.getType());

        if (statisticEvent.getType().equals(StatisticType.COUNT)) {
            assertEquals(statisticEvent.getCount(), rolledUpStatisticEvent.getCount());
        } else {
            assertEquals(statisticEvent.getValue(), rolledUpStatisticEvent.getValue(), JUNIT_DOUBLE_EQUALITY_DELTA);
        }
    }
}
