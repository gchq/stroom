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

package stroom.dashboard.client.main;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestDashboardContextImpl {

    @Test
    void aSelectedValueIsPutInAsSelectedWithoutQuotes() {
        // Seen in testing: clicking a node named "Skyler White" filtered the search by 'Skyler White', quotes
        // included, which no value holds, so the dashboard showed nothing.
        final Map<String, String> selection = Map.of("component.vis-1.selection.node", "Skyler White");

        assertThat(DashboardContextImpl.selectionTermValue("${component.vis-1.selection.node}", selection))
                .isEqualTo("Skyler White");
        assertThat(DashboardContextImpl.selectionTermValue("${component.vis-1.selection.node}*", selection))
                .isEqualTo("Skyler White*");
    }

    @Test
    void aSelectionThatIsntHeldLeavesNothing() {
        // So the term drops out (see ExpressionUtil.isValidTerm) while nothing is selected.
        assertThat(DashboardContextImpl.selectionTermValue("${component.vis-1.selection.node}", Map.of()))
                .isEmpty();
    }
}
