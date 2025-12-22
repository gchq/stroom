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

package stroom.statistics.impl.sql;


import stroom.statistics.impl.sql.shared.CustomRollUpMask;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TestCustomRollUpMask {

    @Test
    void testIsTagRolledUp() {
        final CustomRollUpMask mask = new CustomRollUpMask(Arrays.asList(3, 1, 0));

        assertThat(mask.isTagRolledUp(3)).isTrue();
        assertThat(mask.isTagRolledUp(2)).isFalse();
        assertThat(mask.isTagRolledUp(1)).isTrue();
        assertThat(mask.isTagRolledUp(0)).isTrue();
    }

    @Test
    void testSetRollUpState() {
        final CustomRollUpMask mask = new CustomRollUpMask(Arrays.asList(3, 1, 0));

        assertThat(mask.isTagRolledUp(2)).isFalse();
        assertThat(mask.isTagRolledUp(3)).isTrue();

        mask.setRollUpState(2, false);
        assertThat(mask.isTagRolledUp(2)).isFalse();

        mask.setRollUpState(2, true);
        assertThat(mask.isTagRolledUp(2)).isTrue();

        mask.setRollUpState(2, false);
        assertThat(mask.isTagRolledUp(2)).isFalse();

        mask.setRollUpState(3, true);
        assertThat(mask.isTagRolledUp(3)).isTrue();

        mask.setRollUpState(3, false);
        assertThat(mask.isTagRolledUp(3)).isFalse();
    }
}
