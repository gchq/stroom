/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util.shared.time;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDays {

    @Test
    void test() {
        assertThat(Days.create(Set.of()).toString())
                .isEqualTo("None");
        assertThat(Days.create(new HashSet<>(Day.ALL)).toString())
                .isEqualTo("All");
        assertThat(Days.create(Set.of(Day.MONDAY, Day.WEDNESDAY, Day.FRIDAY)).toString())
                .isEqualTo("Mon, Wed, Fri");
        assertThat(Days.create(Set.of(Day.WEDNESDAY, Day.FRIDAY)).toString())
                .isEqualTo("Wed, Fri");
        assertThat(Days.create(Set.of(Day.WEDNESDAY, Day.FRIDAY, Day.SUNDAY)).toString())
                .isEqualTo("Wed, Fri, Sun");
        assertThat(Days.create(Set.of(Day.SATURDAY, Day.SUNDAY)).toString())
                .isEqualTo("Sat-Sun");
        assertThat(Days.create(Set.of(Day.WEDNESDAY, Day.THURSDAY, Day.FRIDAY)).toString())
                .isEqualTo("Wed-Fri");
    }
}
