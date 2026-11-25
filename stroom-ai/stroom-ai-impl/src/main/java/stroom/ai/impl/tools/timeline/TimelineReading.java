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

package stroom.ai.impl.tools.timeline;

import stroom.util.shared.NullSafe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// How an activity timeline read its sources (decided with the user 2026-11-25), for its result: the scans made
/// (one per source), the documents of an event read more than once left out, the fields each event's own pipeline gave where the pipelines feeding an index differ, the
/// Events XML paths read because the index doesn't map them, and the `## Event types` lines to offer for the codes
/// classified this run.
///
/// @param scans        The scans opened: one per source (and per user field where the pipelines differ on it).
/// @param byStream     For each source whose pipelines differ, by its place: each thing's fields read and how many
///                     events each ([StreamFields.Resolved#read]).
/// @param xmlPaths     The Events XML read for what the indexes don't map, per source.
/// @param offers       The `## Event types` section to offer for each index's codes classified this run, by the
///                     index's name.
/// @param streamsSeen  The distinct streams whose provenance was looked up.
/// @param duplicates   The documents left out as another document of an event already read (the same event in two
///                     sources, or a reprocessed event's old and new documents), by the source's place
///                     ([EventDedupe]).
/// @param notDeduplicated The places of the sources whose index has no `StreamId` and `EventId`, not deduplicated.
record TimelineReading(int scans,
                       Map<Integer, Map<String, Map<String, Long>>> byStream,
                       List<XmlPaths> xmlPaths,
                       Map<String, String> offers,
                       int streamsSeen,
                       Map<Integer, Integer> duplicates,
                       Set<Integer> notDeduplicated) {

    /// Nothing read.
    static final TimelineReading NONE = new TimelineReading(0, Map.of(), List.of(), Map.of(), 0, Map.of(),
            Set.of());

    /// Copies the collections, in order.
    TimelineReading {
        byStream = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(byStream)));
        xmlPaths = List.copyOf(NullSafe.list(xmlPaths));
        offers = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(offers)));
        duplicates = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(duplicates)));
        notDeduplicated = Collections.unmodifiableSet(new LinkedHashSet<>(NullSafe.set(notDeduplicated)));
    }


    // --------------------------------------------------------------------------------


    /// The Events XML read of one source for what its index doesn't map.
    ///
    /// @param source The source's name.
    /// @param index  Its index's name.
    /// @param paths  The event-logging paths read from the XML, to add to the index plan.
    /// @param events The events whose XML was read for them.
    record XmlPaths(String source, String index, List<String> paths, int events) {

        /// Copies the list.
        XmlPaths {
            paths = List.copyOf(paths);
        }

        /// @return What to do so the index maps them, for the result.
        String suggestion() {
            return "Map " + String.join(", ", paths) + " to fields in the index plan of the pipeline(s) feeding '"
                   + index + "' (save_xslt index_plan=), so the timeline reads them from the index, not the Events "
                   + "XML.";
        }
    }
}
