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

package stroom.gwt.workbench.framework.client.manager;

import stroom.gwt.workbench.framework.client.tree.TagFilter;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestChangeRequests {

    @Test
    void testBatch() {
        final List<String> items = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            items.add("class" + i);
        }
        final List<List<String>> batches = ChangeRequests.batch(items, ChangeRequests.MAX_CLASSES_PER_REQUEST);
        assertThat(batches).extracting(List::size).containsExactly(50, 50, 20);
        assertThat(batches.get(0).get(0)).isEqualTo("class0");
        assertThat(batches.get(2).get(19)).isEqualTo("class119");
    }

    @Test
    void testBatch_exactAndEmpty() {
        assertThat(ChangeRequests.batch(List.of("a", "b"), 2)).containsExactly(List.of("a", "b"));
        assertThat(ChangeRequests.batch(List.of("a"), 2)).containsExactly(List.of("a"));
        assertThat(ChangeRequests.batch(List.of(), 2)).isEmpty();
    }

    @Test
    void testBatch_invalidSize() {
        assertThatThrownBy(() -> ChangeRequests.batch(List.of("a"), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPutStatus_keepsMostSignificant() {
        final Map<String, String> statuses = new HashMap<>();
        ChangeRequests.putStatus(statuses, "A", TagFilter.RELATED);
        ChangeRequests.putStatus(statuses, "A", TagFilter.MODIFIED);
        ChangeRequests.putStatus(statuses, "B", TagFilter.NEW);
        // A later batch saying a new class is only related doesn't downgrade it
        ChangeRequests.putStatus(statuses, "B", TagFilter.RELATED);
        ChangeRequests.putStatus(statuses, "C", TagFilter.RELATED);

        assertThat(statuses)
                .containsEntry("A", TagFilter.MODIFIED)
                .containsEntry("B", TagFilter.NEW)
                .containsEntry("C", TagFilter.RELATED);
    }

    @Test
    void testPutStatus_ignoresInvalid() {
        final Map<String, String> statuses = new HashMap<>();
        ChangeRequests.putStatus(statuses, "A", "\"><script>");
        ChangeRequests.putStatus(statuses, "B", null);
        ChangeRequests.putStatus(statuses, null, TagFilter.NEW);
        assertThat(statuses).isEmpty();
    }
}
