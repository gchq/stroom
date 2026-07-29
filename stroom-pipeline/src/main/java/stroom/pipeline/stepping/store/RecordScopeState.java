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

package stroom.pipeline.stepping.store;

import stroom.pipeline.shared.SourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The state a record leaves in <b>shared pipeline scope</b> - state that belongs to no single element, so it
 * cannot be stored as part of any element's IO chunk.
 * <p>
 * It is captured once per record and held in the store's per-part state slot, un-fingerprinted, because it is
 * a property of the run rather than of one element's configuration. Two things live here:
 * <ul>
 *   <li>{@code sourceLocation} - the per-record {@link SourceLocation} the {@code LocationHolder} computed
 *   from the parser {@code Locator}, giving the served step its source highlight and letting a reprocess
 *   below the split report source-parse locations rather than defaults.</li>
 *   <li>{@code scopeMap} - the {@code stroom:put} key/values visible at the end of the record. A reprocess
 *   does not re-run the elements above the edit, so without this an upstream {@code put} would simply not
 *   happen and a downstream {@code stroom:get} would see nothing.</li>
 *   <li>{@code elementCounts} - the running count of each {@link
 *   stroom.pipeline.stepping.capture.SteppingCounter} element, by element id. Element-local rather than
 *   shared, but it lives here rather than in the element's own chunk for a specific reason: to replay record
 *   N an element needs its count as of record <b>N-1</b>, so the value is read from the previous record's
 *   snapshot. Keying it by fingerprint like IO would make it unreadable exactly when it is needed - after an
 *   edit, when the element's fingerprint has just changed.</li>
 * </ul>
 *
 * @param sourceLocation the record's source location, or null if none was available.
 * @param scopeMap       the {@code stroom:put}/{@code get} map contents at the end of the record. Never null;
 *                       an empty map means the record put nothing.
 * @param elementCounts  each counting element's total at the end of the record, by element id. Never null; an
 *                       empty map means the pipeline has no counting elements.
 */
public record RecordScopeState(SourceLocation sourceLocation,
                               Map<String, String> scopeMap,
                               Map<String, Long> elementCounts) {

    public RecordScopeState {
        // Defensively copy - the live map keeps mutating as later records are processed. HashMap rather than
        // Map.copyOf because stroom:put tolerates a null key or value and Map.copyOf would reject them.
        scopeMap = scopeMap == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(scopeMap));
        elementCounts = elementCounts == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(elementCounts));
    }

    /**
     * Convenience for the common case of a pipeline with no counting elements.
     */
    public RecordScopeState(final SourceLocation sourceLocation, final Map<String, String> scopeMap) {
        this(sourceLocation, scopeMap, null);
    }

    /**
     * @return the count the named element had reached at the end of this record, or empty if it was not
     * counting. Distinguished from zero deliberately: "counted nothing" and "does not count" restore
     * differently - the latter must leave the element alone.
     */
    public java.util.OptionalLong countFor(final String elementId) {
        final Long count = elementCounts.get(elementId);
        return count == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(count);
    }

    /**
     * @return true if this snapshot carries nothing worth restoring.
     */
    public boolean isEmpty() {
        return sourceLocation == null && scopeMap.isEmpty() && elementCounts.isEmpty();
    }
}
