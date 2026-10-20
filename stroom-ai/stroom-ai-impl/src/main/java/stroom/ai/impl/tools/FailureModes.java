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

package stroom.ai.impl.tools;

import stroom.docref.DocRef;
import stroom.util.shared.ElementId;
import stroom.util.shared.Location;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.util.shared.StreamLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/// Groups the markers read from Error streams into failure modes: markers of the same severity, from the same
/// pipeline element, whose messages have the same pattern ([ErrorMessageNormaliser]). For each mode it counts the
/// markers and streams, and keeps which pipelines, feeds and nodes it was met in, when it was first and last met,
/// and a few exact examples.
final class FailureModes {

    /// The most exact examples kept for each mode.
    static final int MAX_EXAMPLES = 2;

    private final Map<Key, Mode> modes = new LinkedHashMap<>();
    private int markers;

    /// Adds a marker read from an Error stream.
    ///
    /// @param marker The marker.
    /// @param source Where it was read from.
    void add(final StoredError marker, final Source source) {
        Objects.requireNonNull(marker);
        Objects.requireNonNull(source);
        final Severity severity = Objects.requireNonNullElse(marker.getSeverity(), Severity.ERROR);
        final String element = NullSafe.getOrElse(marker.getElementId(), ElementId::getId, "");
        final String message = NullSafe.string(marker.getMessage()).strip();
        final Key key = new Key(severity, element, ErrorMessageNormaliser.normalise(message));
        final Mode mode = modes.computeIfAbsent(key, Mode::new);
        mode.add(message, marker.getLocation(), source);
        markers++;
    }

    /// @return How many markers have been added.
    int markers() {
        return markers;
    }

    /// @return The modes, most severe first, then those met most often, then by pattern.
    List<Mode> ranked() {
        return modes.values().stream()
                .sorted(Comparator.comparing((Mode mode) -> mode.key().severity(), Severity.HIGH_TO_LOW_COMPARATOR)
                        .thenComparing(Comparator.comparingLong(Mode::count).reversed())
                        .thenComparing(mode -> mode.key().pattern()))
                .toList();
    }


    // --------------------------------------------------------------------------------


    /// Where a marker was read from.
    ///
    /// @param errorStreamId  The Error stream.
    /// @param sourceStreamId The stream the pipeline was processing (the Error stream's parent), or null if not
    ///                       known or not one the user may see.
    /// @param pipeline       The pipeline that wrote it.
    /// @param feed           The feed of the Error stream.
    /// @param node           The node that ran the task, or null if not known.
    /// @param timeMs         When the Error stream was created.
    record Source(long errorStreamId, Long sourceStreamId, DocRef pipeline, String feed, String node, long timeMs) {

    }


    // --------------------------------------------------------------------------------


    /// What makes markers one mode.
    ///
    /// @param severity Their severity.
    /// @param element  The id of the pipeline element that wrote them, or an empty string.
    /// @param pattern  Their message's pattern.
    record Key(Severity severity, String element, String pattern) {

    }


    // --------------------------------------------------------------------------------


    /// An exact marker of a mode.
    ///
    /// @param message  The message, as written.
    /// @param location Where in the source stream it was, e.g. `part 1, line 3, column 45`, or an empty string.
    /// @param source   Where it was read from.
    record Example(String message, String location, Source source) {

    }


    // --------------------------------------------------------------------------------


    /// One failure mode.
    static final class Mode {

        private final Key key;
        private final Set<Long> streams = new LinkedHashSet<>();
        private final Map<String, DocRef> pipelines = new LinkedHashMap<>();
        private final Set<String> feeds = new TreeSet<>();
        private final Set<String> nodes = new TreeSet<>();
        private final List<Example> examples = new ArrayList<>();
        private long count;
        private long firstMs = Long.MAX_VALUE;
        private long lastMs = Long.MIN_VALUE;

        private Mode(final Key key) {
            this.key = key;
        }

        private void add(final String message, final Location location, final Source source) {
            count++;
            streams.add(source.errorStreamId());
            if (source.pipeline() != null) {
                pipelines.putIfAbsent(source.pipeline().getUuid(), source.pipeline());
            }
            if (NullSafe.isNonBlankString(source.feed())) {
                feeds.add(source.feed());
            }
            if (NullSafe.isNonBlankString(source.node())) {
                nodes.add(source.node());
            }
            firstMs = Math.min(firstMs, source.timeMs());
            lastMs = Math.max(lastMs, source.timeMs());
            // Different messages make better examples than the same one twice.
            if (examples.size() < MAX_EXAMPLES
                && examples.stream().noneMatch(example -> example.message().equals(message))) {
                examples.add(new Example(message, describe(location), source));
            }
        }

        /// @return What makes these markers one mode.
        Key key() {
            return key;
        }

        /// @return How many markers there are.
        long count() {
            return count;
        }

        /// @return The Error streams they were read from, in the order first met.
        Set<Long> streams() {
            return streams;
        }

        /// @return The pipelines that wrote them, in the order first met.
        List<DocRef> pipelines() {
            return List.copyOf(pipelines.values());
        }

        /// @return The feeds of their Error streams, sorted.
        Set<String> feeds() {
            return feeds;
        }

        /// @return The nodes that ran their tasks, where known, sorted.
        Set<String> nodes() {
            return nodes;
        }

        /// @return When the first of their Error streams was created.
        long firstMs() {
            return firstMs;
        }

        /// @return When the last of their Error streams was created.
        long lastMs() {
            return lastMs;
        }

        /// @return Up to [#MAX_EXAMPLES] exact markers, with different messages where there are.
        List<Example> examples() {
            return examples;
        }

        private static String describe(final Location location) {
            if (location == null || location.isUnknown()) {
                return "";
            }
            final List<String> parts = new ArrayList<>();
            if (location instanceof final StreamLocation streamLocation && streamLocation.getPartIndex() >= 0) {
                parts.add("part " + (streamLocation.getPartIndex() + 1));
            }
            if (location.hasLineNo()) {
                parts.add("line " + location.getLineNo());
            }
            if (location.getColNo() > 0) {
                parts.add("column " + location.getColNo());
            }
            return String.join(", ", parts);
        }
    }
}
