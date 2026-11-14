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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.ErrorTriage;
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.SourceTypeModule;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XmlParser;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.pipeline.shared.stepping.SteppingResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/// The records a source type group's pipeline passed over while stepped (its XSLT's INFO line
/// [XsltGenerator#PASSED_OVER]): records of the feed's other source types, which the group's pipeline is meant to
/// leave to the other groups. They are not records with errors and never change a verdict: the stepping tools take
/// their line out of a record's markers and count them here instead, by source type where the record's input tells
/// it.
///
/// Seen in testing: step_sample of an Azure audit group counted 304 of 500 records "with errors", 238 of them only
/// the group's pass-over line, and the agent had to explain them away.
final class PassedOverRecords {

    /// The result's key for how many records were passed over.
    static final String RECORDS_PASSED_OVER = "records_passed_over";
    /// The result's key for the records passed over, by source type.
    static final String BY_SOURCE_TYPE = "passed_over_by_source_type";
    /// The result's key for what is said of them.
    static final String NOTE = "passed_over_note";
    /// What a record is counted under when its source type can't be told from the step (no group mapping kept, a
    /// source type field read from the part's meta, or input that isn't XML).
    static final String NOT_TOLD = "(not told from the step)";

    private static final Pattern LINE = Pattern.compile(Pattern.quote(XsltGenerator.PASSED_OVER) + "\\d+"
                                                        + Pattern.quote(XsltGenerator.PASSED_OVER_END));
    private static final Pattern DECLARATION = Pattern.compile("^\\s*<\\?xml[^>]*\\?>");

    // The group's rows, to tell a record's source type by; null when they aren't known.
    private final SourceTypeScope scope;
    private final Map<String, Integer> bySourceType = new TreeMap<>(ToolText::compare);
    private int count;

    private PassedOverRecords(final SourceTypeScope scope) {
        this.scope = scope;
    }

    /// @param module The group's module kept with the pipeline's XSLT, or null when it keeps none (or not a
    ///               group's): the records are then counted, their source types not told.
    /// @return A count of the records passed over, none yet.
    static PassedOverRecords of(final SourceTypeModule module) {
        if (module == null || !module.isGroup() || module.field() == null || module.byPart()
            || module.rows().isEmpty()) {
            return new PassedOverRecords(null);
        }
        return new PassedOverRecords(new SourceTypeScope(null, module.field(), module.toolRows(),
                module.sourceTypes()));
    }

    /// @param marker A marker a step met.
    /// @return True if it is a source type group's line of the records it passed over.
    static boolean isPassOver(final ErrorTriage.Marker marker) {
        return marker != null && "INFO".equals(marker.severity()) && LINE.matcher(marker.message()).find();
    }

    /// Takes a group's pass-over line out of one record's markers, counting the record as passed over when it had
    /// one, by the source type the input of the element that logged it tells. A record passed over keeps only its
    /// ERROR and FATAL markers (see [#breaksTheStream(ErrorTriage.Marker)]).
    ///
    /// @param markers What the record's step met.
    /// @param result  The step, whose elements' inputs tell the record's source type.
    /// @return The markers left, and whether the record was passed over.
    Sifted sift(final List<ErrorTriage.Marker> markers, final SteppingResult result) {
        final List<ErrorTriage.Marker> left = new ArrayList<>();
        String element = null;
        for (final ErrorTriage.Marker marker : markers) {
            if (isPassOver(marker)) {
                element = marker.element();
            } else {
                left.add(marker);
            }
        }
        if (element == null) {
            return new Sifted(left, false);
        }
        count++;
        bySourceType.merge(sourceType(SteppingWalks.input(result, element)), 1, Integer::sum);
        return new Sifted(left.stream()
                .filter(PassedOverRecords::breaksTheStream)
                .toList(), true);
    }

    /// Whether a marker of a record the group passes over still counts against the group: an ERROR or FATAL can
    /// break the stream whoever's record it is, but a WARNING or INFO (a parser's note on a long value of another
    /// group's source type, say) is about data that isn't this group's and is left out.
    ///
    /// Seen in testing: an Identity group's step_sample verdict was `review` from two jsonParser "String value
    /// truncated" warnings on Exchange and Teams records it passes over, and its processor filter was refused.
    ///
    /// @param marker A marker of a record passed over.
    /// @return True if it is kept.
    static boolean breaksTheStream(final ErrorTriage.Marker marker) {
        return !"WARNING".equals(marker.severity()) && !"INFO".equals(marker.severity());
    }

    /// @return How many records were passed over.
    int count() {
        return count;
    }

    /// @return The records passed over, by source type (sorted by name).
    Map<String, Integer> bySourceType() {
        return Map.copyOf(bySourceType);
    }

    /// Adds [#RECORDS_PASSED_OVER], [#BY_SOURCE_TYPE] and [#NOTE] to a result, when any record was passed over.
    ///
    /// @param result A stepping tool's result.
    void addTo(final Map<String, Object> result) {
        if (count == 0) {
            return;
        }
        result.put(RECORDS_PASSED_OVER, count);
        result.put(BY_SOURCE_TYPE, new LinkedHashMap<>(bySourceType));
        result.put(NOTE, said());
    }

    /// @return What a result says of the records passed over, for its hint.
    String said() {
        return count + " record(s) were passed over by the group's pipeline (not of its source types: "
               + String.join(", ", bySourceType.entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .toList())
               + "). They are left to the other groups' pipelines: not errors, and not counted in "
               + "records_with_errors or the verdict. Their warnings are left out too (about data of other "
               + "groups' source types); only an ERROR or FATAL on one of them is kept.";
    }

    // The source type of the record an element read, by the group's rows.
    private String sourceType(final String input) {
        if (scope == null || input == null || input.isBlank()) {
            return NOT_TOLD;
        }
        final Map<String, Object> fields = fieldsOf(input);
        if (fields == null) {
            return NOT_TOLD;
        }
        return scope.sourceType(fields).orElse(SourceTypeScope.UNMATCHED);
    }

    /// The fields of a record as an XSLT reads it, flattened to dotted names as the survey names them: JSON read
    /// as XML (`<string key="Workload">`, nested maps by their keys) or a Data Splitter's records:2
    /// (`<data name="..." value="...">`, nested data by their names).
    ///
    /// @param xml The record as XML.
    /// @return Its fields; null when it isn't XML.
    static Map<String, Object> fieldsOf(final String xml) {
        final XmlElement root;
        try {
            root = XmlParser.parse(DECLARATION.matcher(xml).replaceFirst(""));
        } catch (final RuntimeException e) {
            return null;
        }
        final Map<String, Object> fields = new LinkedHashMap<>();
        flatten(root, "", fields);
        return fields;
    }

    private static void flatten(final XmlElement element, final String path, final Map<String, Object> fields) {
        final String name = element.get("key") != null
                ? element.get("key")
                : element.get("name");
        final String at = name == null
                ? path
                : path.isEmpty()
                        ? name
                        : path + "." + name;
        final List<XmlElement> children = element.childElements();
        if (name != null && element.get("value") != null) {
            fields.putIfAbsent(at, element.get("value"));
        } else if (name != null && children.isEmpty() && element.text() != null && !element.text().isBlank()) {
            fields.putIfAbsent(at, element.text().strip());
        }
        children.forEach(child -> flatten(child, at, fields));
    }


    // --------------------------------------------------------------------------------


    /// One record's markers, its pass-over line taken out.
    ///
    /// @param markers    The markers left.
    /// @param passedOver True if the group's pipeline passed the record over.
    record Sifted(List<ErrorTriage.Marker> markers, boolean passedOver) {

    }
}
