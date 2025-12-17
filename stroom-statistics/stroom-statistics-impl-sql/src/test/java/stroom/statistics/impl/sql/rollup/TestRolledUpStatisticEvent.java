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

package stroom.statistics.impl.sql.rollup;


import stroom.statistics.impl.sql.StatisticEvent;
import stroom.statistics.impl.sql.StatisticTag;
import stroom.statistics.impl.sql.TimeAgnosticStatisticEvent;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TestRolledUpStatisticEvent extends StroomUnitTest {

    private static final double JUNIT_DOUBLE_EQUALITY_DELTA = 0.001;

    private final List<StatisticTag> tags = new ArrayList<>();
    private StatisticEvent event;

    @Test
    void testRolledUpStatisticEventStatisticEventListOfListOfStatisticTag() {
        buildEvent();
    }

    @Test
    void testRolledUpStatisticEventStatisticEvent() {
        buildEvent();

        final RolledUpStatisticEvent rolledUpStatisticEvent = new RolledUpStatisticEvent(event);

        compareEvents(event, rolledUpStatisticEvent);

        assertThat(rolledUpStatisticEvent.iterator().hasNext()).isTrue();

        int counter = 0;
        for (final TimeAgnosticStatisticEvent timeAgnosticStatisticEvent : rolledUpStatisticEvent) {
            assertThat(timeAgnosticStatisticEvent).isEqualTo(event.getTimeAgnosticStatisticEvent());

            counter++;
        }

        assertThat(counter).isEqualTo(1);
    }

    private void buildEvent() {
        tags.add(new StatisticTag("Tag1", "Tag1Val"));
        tags.add(new StatisticTag("Tag2", "Tag2Val"));
        tags.add(new StatisticTag("Tag3", "Tag3Val"));

        event = StatisticEvent.createCount(1234L, "MtStat", tags, 1000L);
    }

    private void compareEvents(final StatisticEvent statisticEvent,
                               final RolledUpStatisticEvent rolledUpStatisticEvent) {
        assertThat(rolledUpStatisticEvent.getTimeMs()).isEqualTo(statisticEvent.getTimeMs());
        assertThat(rolledUpStatisticEvent.getName()).isEqualTo(statisticEvent.getName());
        assertThat(rolledUpStatisticEvent.getType()).isEqualTo(statisticEvent.getType());

        if (statisticEvent.getType().equals(StatisticType.COUNT)) {
            assertThat(rolledUpStatisticEvent.getCount()).isEqualTo(statisticEvent.getCount());
        } else {
            assertThat(rolledUpStatisticEvent.getValue()).isCloseTo(statisticEvent.getValue(),
                    within(JUNIT_DOUBLE_EQUALITY_DELTA));
        }
    }
}
