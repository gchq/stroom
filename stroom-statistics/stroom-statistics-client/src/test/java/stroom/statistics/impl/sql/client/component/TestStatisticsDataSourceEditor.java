/*
 * Copyright 2026 Crown Copyright
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

package stroom.statistics.impl.sql.client.component;

import stroom.statistics.impl.sql.shared.CustomRollUpMask;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestStatisticsDataSourceEditor {

    private static final CustomRollUpMask NO_ROLL_UP = new CustomRollUpMask(Collections.emptyList());
    private static final CustomRollUpMask FIRST_ROLLED_UP = new CustomRollUpMask(List.of(0));

    @Test
    void lookingAtTheRollUpsOfAStoreWithNoneChangesNothing() {
        // The tab adds its "no roll-up" row to a store with none. Before, that row was written as
        // the store's roll-up, so the store differed from the one read and stayed dirty
        assertThat(StatisticsDataSourceEditor.masksToWrite(Set.of(NO_ROLL_UP), null)).isNull();
        assertThat(StatisticsDataSourceEditor.masksToWrite(Set.of(NO_ROLL_UP), Set.of())).isEmpty();
    }

    @Test
    void theRollUpsShownAreWrittenOtherwise() {
        // A store read with roll-ups keeps its "no roll-up" row
        assertThat(StatisticsDataSourceEditor.masksToWrite(Set.of(NO_ROLL_UP), Set.of(NO_ROLL_UP)))
                .containsExactly(NO_ROLL_UP);
        // Roll-ups the user added are written
        assertThat(StatisticsDataSourceEditor.masksToWrite(Set.of(NO_ROLL_UP, FIRST_ROLLED_UP), null))
                .containsExactlyInAnyOrder(NO_ROLL_UP, FIRST_ROLLED_UP);
    }
}
