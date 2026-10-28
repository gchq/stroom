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

package stroom.pipeline.stepping.client;

import stroom.util.shared.Severity;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestSeverityChanges {

    @Test
    void testGetWorsened_new() {
        assertThat(SeverityChanges.getWorsened(
                Map.of(),
                Map.of("a", Severity.ERROR, "b", Severity.WARNING),
                Severity.WARNING))
                .containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void testGetWorsened_worse() {
        assertThat(SeverityChanges.getWorsened(
                Map.of("a", Severity.WARNING, "b", Severity.ERROR),
                Map.of("a", Severity.ERROR, "b", Severity.FATAL_ERROR),
                Severity.WARNING))
                .containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void testGetWorsened_sameOrBetter() {
        assertThat(SeverityChanges.getWorsened(
                Map.of("a", Severity.ERROR, "b", Severity.ERROR),
                Map.of("a", Severity.ERROR, "b", Severity.WARNING),
                Severity.WARNING))
                .isEmpty();
    }

    @Test
    void testGetWorsened_belowMinimum() {
        // Info is new, but less than the minimum; the warning is new but less than errors
        assertThat(SeverityChanges.getWorsened(
                Map.of(),
                Map.of("a", Severity.INFO, "b", Severity.WARNING),
                Severity.ERROR))
                .isEmpty();
    }

    @Test
    void testGetWorsened_againAfterClearing() {
        // An error, then none, then the error again, is new again
        final Map<String, Severity> none = Map.of();
        final Map<String, Severity> error = Map.of("a", Severity.ERROR);

        assertThat(SeverityChanges.getWorsened(error, none, Severity.WARNING))
                .isEmpty();
        assertThat(SeverityChanges.getWorsened(none, error, Severity.WARNING))
                .containsExactly("a");
    }

    @Test
    void testGetWorsened_nulls() {
        final Map<String, Severity> withNull = new HashMap<>();
        withNull.put("a", null);

        assertThat(SeverityChanges.getWorsened(null, null, Severity.WARNING))
                .isEmpty();
        assertThat(SeverityChanges.getWorsened(null, withNull, Severity.WARNING))
                .isEmpty();
        assertThat(SeverityChanges.getWorsened(null, Map.of("b", Severity.ERROR), Severity.WARNING))
                .containsExactly("b");
    }
}
