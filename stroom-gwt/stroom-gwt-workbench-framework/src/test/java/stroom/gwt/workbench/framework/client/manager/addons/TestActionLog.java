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

package stroom.gwt.workbench.framework.client.manager.addons;

import stroom.gwt.workbench.framework.client.manager.addons.ActionLog.Change;
import stroom.gwt.workbench.framework.client.manager.addons.ActionLog.Entry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class TestActionLog {

    @Test
    void testAdd_repeatsAreCounted() {
        final ActionLog log = new ActionLog();
        assertThat(log.add("onClick", null)).isEqualTo(Change.ADDED);
        assertThat(log.add("onClick", null)).isEqualTo(Change.REPEATED);
        assertThat(log.add("onChange", "a")).isEqualTo(Change.ADDED);
        assertThat(log.add("onChange", "b")).isEqualTo(Change.ADDED);
        assertThat(log.add("onChange", "b")).isEqualTo(Change.REPEATED);

        assertThat(log.getEntries())
                .extracting(Entry::getName, Entry::getDetail, Entry::getCount)
                .containsExactly(
                        tuple("onClick", null, 2),
                        tuple("onChange", "a", 1),
                        tuple("onChange", "b", 2));
        assertThat(log.getTotal()).isEqualTo(5);
    }

    @Test
    void testAdd_nullName() {
        // Regression test: an action without a name used to throw a NullPointerException
        final ActionLog log = new ActionLog();
        assertThat(log.add(null, "x")).isEqualTo(Change.ADDED);
        assertThat(log.add(null, "x")).isEqualTo(Change.REPEATED);
        assertThat(log.getEntries()).extracting(Entry::getName).containsExactly("");
    }

    @Test
    void testAdd_limit() {
        final ActionLog log = new ActionLog();
        for (int i = 0; i < ActionLog.DEFAULT_LIMIT; i++) {
            assertThat(log.add("action" + i, null)).isEqualTo(Change.ADDED);
        }
        // The oldest is dropped to stay within the limit, as in Storybook
        assertThat(log.add("last", null)).isEqualTo(Change.ADDED_AND_DROPPED_OLDEST);
        assertThat(log.getEntries()).hasSize(ActionLog.DEFAULT_LIMIT);
        assertThat(log.getEntries().get(0).getName()).isEqualTo("action1");
        assertThat(log.getEntries().get(ActionLog.DEFAULT_LIMIT - 1).getName()).isEqualTo("last");

        // Repeats don't use up the limit
        assertThat(log.add("last", null)).isEqualTo(Change.REPEATED);
        assertThat(log.getEntries()).hasSize(ActionLog.DEFAULT_LIMIT);
    }

    @Test
    void testClear() {
        final ActionLog log = new ActionLog(2);
        log.add("a", null);
        log.add("b", null);
        log.clear();
        assertThat(log.getEntries()).isEmpty();
        assertThat(log.getTotal()).isZero();
    }

    @Test
    void testInvalidLimit() {
        assertThatThrownBy(() -> new ActionLog(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
