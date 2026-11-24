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

import stroom.ai.impl.tools.ToolText;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/// What a timeline's lanes are, as a chart's lane axis (and its table's lane column) is titled, rather than the
/// generic "lane" (seen in chat 195): the spans' lanes by the field holding the host, by its description where
/// the index documents one (e.g. `Device host name (host.name)`), else by the field's name; `Source` for lanes by
/// source; `Day (<zone>)` for the activities by day; `Activity` for the activities by activity.
final class TimelineLaneTitles {

    /// The title of lanes by source.
    static final String SOURCE = "Source";
    /// The title of the activities' lanes by activity.
    static final String ACTIVITY = "Activity";
    /// The title of lanes by host where no source names the field holding it (the host from the events' XML).
    static final String HOST = "Host";
    /// The longest description a title keeps.
    static final int MAX_DESCRIPTION_CHARS = 60;
    // Where a lane is a source's, as an event with no host is.
    private static final String ELSE_SOURCE = " (else source)";

    private TimelineLaneTitles() {
        // Static only.
    }

    /// The title of the spans' lanes by host: each source's host field, described where its index documents it, `/`
    /// between them; a source with none has its events' hosts from their XML, else its name.
    ///
    /// @param sources     The sources read.
    /// @param description Gives a source's field's description, or null if it has none.
    /// @return The title.
    static String hosts(final List<TimelineReader.Source> sources,
                        final BiFunction<TimelineReader.Source, String, String> description) {
        final Set<String> titles = new LinkedHashSet<>();
        boolean withoutField = false;
        for (final TimelineReader.Source source : NullSafe.list(sources)) {
            final String field = source.hostField();
            if (NullSafe.isBlankString(field)) {
                withoutField = true;
            } else {
                titles.add(field(field, description.apply(source, field)));
            }
        }
        if (titles.isEmpty()) {
            return HOST + ELSE_SOURCE;
        }
        final List<String> parts = new ArrayList<>(titles);
        return String.join(" / ", parts) + (withoutField
                ? ELSE_SOURCE
                : "");
    }

    /// @param field       A field's name.
    /// @param description Its description, or null.
    /// @return The description with the field's name, e.g. `Device host name (host.name)`; the name alone without a
    /// description.
    static String field(final String field, final String description) {
        final String text = NullSafe.trim(description);
        if (text.isEmpty()) {
            return field;
        }
        final String firstLine = text.lines().findFirst().orElse(text).strip();
        final String cut = ToolText.len(firstLine) > MAX_DESCRIPTION_CHARS
                ? ToolText.head(firstLine, MAX_DESCRIPTION_CHARS - 1).strip() + "…"
                : firstLine;
        return cut + " (" + field + ")";
    }

    /// @param lanes     The lanes asked for (`source`), or null for by host.
    /// @param hostTitle The title of lanes by host ([#hosts]).
    /// @return The title of the spans' lanes.
    static String spans(final String lanes, final String hostTitle) {
        return "source".equals(lanes)
                ? SOURCE
                : NullSafe.nonBlankStringElse(hostTitle, HOST);
    }

    /// @param lanes The lanes asked for (`activity`), or null for by day.
    /// @param zone  The user's zone, the days', by its id (`UTC`, a region or an offset).
    /// @return The title of the activities' lanes.
    static String activities(final String lanes, final String zone) {
        return "activity".equals(lanes)
                ? ACTIVITY
                : "Day (" + zone + ")";
    }
}
