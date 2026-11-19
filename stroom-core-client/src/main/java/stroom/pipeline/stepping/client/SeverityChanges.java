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

import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Finds the elements whose severity a step has made new or worse, so that the pipeline bar can
/// draw attention to them (pulse them, and tell screen reader users).
public final class SeverityChanges {

    private SeverityChanges() {
        // Static utility
    }

    /// Gets the elements whose severity is now at least a minimum and worse than it was.
    ///
    /// @param before  The severities before, keyed by element ID. Can be null for none.
    /// @param after   The severities after, keyed by element ID. Can be null for none.
    /// @param minimum The least severity to count, e.g. [Severity#WARNING].
    /// @return The IDs of the elements whose severity is now at least the minimum and either had
    ///         none before or had a lesser one.
    public static Set<String> getWorsened(final Map<String, Severity> before,
                                          final Map<String, Severity> after,
                                          final Severity minimum) {
        Objects.requireNonNull(minimum);
        final Map<String, Severity> beforeMap = NullSafe.map(before);
        final Set<String> worsened = new HashSet<>();
        NullSafe.map(after).forEach((elementId, severity) -> {
            if (severity != null && severity.greaterThanOrEqual(minimum)) {
                final Severity previous = beforeMap.get(elementId);
                if (previous == null || severity.greaterThan(previous)) {
                    worsened.add(elementId);
                }
            }
        });
        return worsened;
    }
}
