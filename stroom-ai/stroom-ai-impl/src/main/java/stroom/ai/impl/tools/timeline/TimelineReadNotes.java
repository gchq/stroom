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

import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.timeline.TimelineFields.Found;
import stroom.ai.impl.tools.timeline.TimelineReading.XmlPaths;
import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Where a timeline would read the Events XML, or trace streams to their indexing pipelines, rather than read an
/// index's fields, for `check_skill`'s suggestions on a timeline skill: before it runs, from the fields found for
/// each of its sources ([#planned]); and from what its recent runs said ([#seen], reading the lines
/// [TimelineResult] writes with [#xmlLine]). Each points at a fix: the field in the skill's source
/// (`propose_skill`), a `## Fields` line (`propose_instructions`), or the path mapped in the index plan.
public final class TimelineReadNotes {

    /// The things whose fields a timeline reads of every source, where the plans may differ.
    static final List<String> THINGS = List.of(InstructionRules.HOST_FIELD, InstructionRules.OBJECT_FIELD,
            InstructionRules.EVENT_TYPE_FIELD);
    // A run's line of the Events XML read for what an index doesn't map.
    private static final Pattern XML_LINE = Pattern.compile(
            "^- Events XML read for (\\d+) event\\(s\\) of '([^']*)', as it doesn't map ([^:]+):", Pattern.MULTILINE);
    private static final Pattern TRACED = Pattern.compile("\\((?:" + Pattern.quote(StreamFields.BY_TASKS) + "|"
                                                          + Pattern.quote(StreamFields.BY_FILTERS) + ")\\) (\\d+)");
    private static final Pattern FROM_XML = Pattern.compile("\\(" + Pattern.quote(StreamFields.BY_XML)
                                                            + "\\) (\\d+)");
    private static final Pattern NOT_READ = Pattern.compile("[,:] " + Pattern.quote(StreamFields.NOT_READ)
                                                            + " (\\d+)");

    private TimelineReadNotes() {
        // Static only.
    }

    /// @param paths The Events XML read of one source for what its index doesn't map.
    /// @return Its line in a run's result, read back by [#seen].
    static String xmlLine(final XmlPaths paths) {
        return "- Events XML read for " + paths.events() + " event(s) of '" + paths.index() + "', as it doesn't map "
               + String.join(", ", paths.paths()) + ": " + paths.suggestion();
    }

    /// What a timeline would read other than the index's fields for one source, from the fields found for it.
    ///
    /// @param fields  Finds the index's fields (the AGENTS docs' `## Fields`, the plans, the conventions).
    /// @param session The session, for the AGENTS docs' audit.
    /// @param index   The source's index.
    /// @param given   The skill's own fields for the source, by thing (e.g. `host` to `HostName`).
    /// @param typed   True if the source is classified (no roles given), so its events' types are read.
    /// @param has     True for a field the index has.
    /// @return A note each: a thing the feeding plans put in different fields with nothing naming one, and the
    ///         events' type no field holds.
    public static List<String> planned(final TimelineFields fields,
                                       final ToolSession session,
                                       final DocRef index,
                                       final Map<String, String> given,
                                       final boolean typed,
                                       final Predicate<String> has) {
        final List<String> notes = new ArrayList<>();
        final String name = NullSafe.nonBlankStringElse(index.getName(), index.getUuid());
        for (final String thing : THINGS) {
            if (NullSafe.isNonBlankString(NullSafe.map(given).get(thing))) {
                continue;
            }
            final Optional<Found> found = fields.find(session, index, thing, List.of(), has);
            if (found.isPresent() && found.get().varies()) {
                notes.add("'" + name + "': its feeding pipelines' index plans put the " + thing + " in different "
                          + "fields (" + String.join(", ", found.get().byPipeline().keySet()) + ") and nothing "
                          + "names one, so each run traces every stream to its indexing pipeline, else reads the "
                          + "Events XML at " + String.join(" or ", paths(thing)) + ".");
            }
        }
        if (typed && NullSafe.isBlankString(NullSafe.map(given).get(InstructionRules.EVENT_TYPE_FIELD))
            && NullSafe.isBlankString(NullSafe.map(given).get(InstructionRules.ELEMENT_FIELD))
            && fields.find(session, index, InstructionRules.EVENT_TYPE_FIELD, List.of(), has).isEmpty()
            && fields.find(session, index, InstructionRules.ELEMENT_FIELD, List.of(), has).isEmpty()) {
            notes.add("'" + name + "': no field holds its events' type (" + String.join(", ",
                    ActivityTimelineTool.TYPE_PATHS) + "), so each run reads the Events XML of a few events per "
                      + "code, and of every event with none.");
        }
        return notes;
    }

    /// @param results The text of a skill's recent timeline runs' results.
    /// @return What they read other than the index's fields.
    public static Seen seen(final List<String> results) {
        int runs = 0;
        int xmlEvents = 0;
        final Map<String, Set<String>> paths = new LinkedHashMap<>();
        long traced = 0;
        long fromXml = 0;
        long notRead = 0;
        for (final String text : NullSafe.list(results)) {
            if (text == null) {
                continue;
            }
            runs++;
            final Matcher xml = XML_LINE.matcher(text);
            while (xml.find()) {
                xmlEvents += Integer.parseInt(xml.group(1));
                for (final String path : xml.group(3).split(", ")) {
                    paths.computeIfAbsent(xml.group(2), k -> new LinkedHashSet<>()).add(path.strip());
                }
            }
            traced += sum(TRACED, text);
            fromXml += sum(FROM_XML, text);
            notRead += sum(NOT_READ, text);
        }
        return new Seen(runs, xmlEvents, paths, traced, fromXml, notRead);
    }

    private static long sum(final Pattern pattern, final String text) {
        long total = 0;
        final Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            total += Long.parseLong(matcher.group(1));
        }
        return total;
    }

    // A thing's event-logging paths.
    private static List<String> paths(final String thing) {
        final String convention = TimelineFields.CONVENTION_PATHS.get(thing);
        return convention != null
                ? List.of(convention)
                : TimelineFields.PLAN_PATHS.getOrDefault(thing, List.of("its schema path"));
    }


    // --------------------------------------------------------------------------------


    /// What a skill's recent timeline runs read other than the index's fields.
    ///
    /// @param runs         The runs read.
    /// @param xmlEvents    The events whose Events XML was read for what an index doesn't map (types, content).
    /// @param pathsByIndex The paths read so, by index.
    /// @param traced       The events whose differing fields were read by their stream traced to its pipeline.
    /// @param fromXml      The events whose differing fields were read from their Events XML.
    /// @param notRead      The events whose differing fields weren't read.
    public record Seen(int runs,
                       int xmlEvents,
                       Map<String, Set<String>> pathsByIndex,
                       long traced,
                       long fromXml,
                       long notRead) {

        /// Copies the map.
        public Seen {
            pathsByIndex = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(pathsByIndex)));
        }

        /// @return True if the runs read anything other than the index's fields.
        public boolean any() {
            return xmlEvents > 0 || traced > 0 || fromXml > 0 || notRead > 0;
        }
    }
}
