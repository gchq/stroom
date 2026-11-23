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
import stroom.ai.impl.tools.sample.JsonFieldNames;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.sample.XmlTree;
import stroom.ai.impl.tools.validation.XPathChecks;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

/// The records a source type group's pipeline passed over while stepped: records of the feed's other source types
/// (or of none), which the group's pipeline is meant to leave to the other groups. They are told as the group's XSLT
/// tells them, from the feed's registry kept with the group's mapping: the source type field's value of the record
/// the XSLT read (or of its part's meta, for a `meta:` field), the rows tried in order, the first match winning. They
/// are not records with errors and never change a verdict: the stepping tools count them here, by source type.
///
/// The group's XSLT logs nothing of them (a line a document, as XSLTs generated before 2026-11-15 logged, made
/// Stroom write an Error stream for every raw stream each group processed); that older line
/// ([XsltGenerator#PASSED_OVER]) is still taken out of a record's markers, and marks the record passed over where
/// its source type can't be told.
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
    /// What a record is counted under when its source type can't be told from the step (no group mapping kept, or
    /// input that isn't XML) but an older XSLT's line said it was passed over.
    static final String NOT_TOLD = "(not told from the step)";

    private static final Pattern LINE = Pattern.compile(Pattern.quote(XsltGenerator.PASSED_OVER) + "\\d+"
                                                        + Pattern.quote(XsltGenerator.PASSED_OVER_END));
    private static final Pattern DECLARATION = Pattern.compile("^\\s*<\\?xml[^>]*\\?>");
    // NUL, which no XML holds, raw or as a reference: a JSON value's \u0000 as Stroom's parser passes it on.
    private static final Pattern NUL = Pattern.compile("\u0000|&#0+;|&#[xX]0+;");

    // The group's rows and source types, to tell a record's source type by; null when they aren't known.
    private final SourceTypeScope scope;
    // The XSLT element whose input is the record the group's XSLT judges.
    private final String inputElement;
    // The element whose output is what the pipeline writes.
    private final String outputElement;
    // The parts' meta, for a source type field read from it; null when not needed.
    private final PartAttributes attributes;
    private final Map<String, Integer> bySourceType = new TreeMap<>(ToolText::compare);
    // The warnings and notes of the records passed over, by the source type they are of: listed apart, benign.
    private final Map<String, List<ErrorTriage.Marker>> setAside = new TreeMap<>(ToolText::compare);
    private int count;

    private PassedOverRecords(final SourceTypeScope scope,
                              final String inputElement,
                              final String outputElement,
                              final PartAttributes attributes) {
        this.scope = scope;
        this.inputElement = inputElement;
        this.outputElement = outputElement;
        this.attributes = attributes;
    }

    /// @param module        The group's module kept with the pipeline's XSLT, or null when it keeps none (or not a
    ///                      group's): no record is then told passed over, but by an older XSLT's line.
    /// @param inputElement  The group's XSLT element, whose input is the record it judges.
    /// @param outputElement The element whose output is what the pipeline writes.
    /// @param attributes    The parts' meta, read for a source type field of a part's meta (`meta:<Attribute>`).
    /// @return A count of the records passed over, none yet.
    static PassedOverRecords of(final SourceTypeModule module,
                                final String inputElement,
                                final String outputElement,
                                final PartAttributes attributes) {
        if (module == null || !module.isGroup() || module.field() == null || module.rows().isEmpty()
            || module.sourceTypes().isEmpty()) {
            return none();
        }
        return new PassedOverRecords(new SourceTypeScope(null, module.field(), module.toolRows(),
                module.sourceTypes()), inputElement, outputElement, attributes);
    }

    /// @return A count of the records passed over for a pipeline that isn't a group's: only an older XSLT's line
    /// tells one.
    static PassedOverRecords none() {
        return new PassedOverRecords(null, null, null, null);
    }

    /// @param marker A marker a step met.
    /// @return True if it is the line a source type group's XSLT generated before 2026-11-15 logged of the records
    /// it passed over.
    static boolean isPassOver(final ErrorTriage.Marker marker) {
        return marker != null && "INFO".equals(marker.severity()) && LINE.matcher(marker.message()).find();
    }

    /// Tells whether one record stepped was passed over by the group's pipeline, counting it by its source type if
    /// so: a record whose source type (by the registry's rows) is not one of the group's, and that the pipeline wrote
    /// no Event for. A record passed over keeps only its ERROR and FATAL markers (see
    /// [#breaksTheStream(ErrorTriage.Marker)]), its others set aside by its source type
    /// ([#addSetAsideTo]); an older XSLT's line of the records passed over is taken out of every record's markers.
    ///
    /// @param markers  What the record's step met.
    /// @param result   The step, whose XSLT's input is the record.
    /// @param location Where the record is: its stream and part, for a source type field read from the part's meta.
    /// @return The markers left, whether the record was passed over, and its source type if so.
    Sifted sift(final List<ErrorTriage.Marker> markers, final SteppingResult result, final StepLocation location) {
        final List<ErrorTriage.Marker> left = new ArrayList<>();
        boolean lineSaid = false;
        for (final ErrorTriage.Marker marker : markers) {
            if (isPassOver(marker)) {
                lineSaid = true;
            } else {
                left.add(marker);
            }
        }
        final String told = sourceType(result, location);
        final boolean passedOver = told != null
                ? !scope.names().contains(told) && !wroteAnEvent(result)
                : lineSaid;
        if (!passedOver) {
            return new Sifted(left, false);
        }
        count++;
        final String counted = told == null
                ? NOT_TOLD
                : told;
        bySourceType.merge(counted, 1, Integer::sum);
        left.stream()
                .filter(marker -> !breaksTheStream(marker))
                .forEach(marker -> setAside.computeIfAbsent(counted, type -> new ArrayList<>()).add(marker));
        return new Sifted(left.stream()
                .filter(PassedOverRecords::breaksTheStream)
                .toList(), true, counted);
    }

    /// @return The source types the group translates; empty for a pipeline that isn't a group's (or whose rows
    /// aren't known).
    Optional<Set<String>> groupSourceTypes() {
        return scope == null
                ? Optional.empty()
                : Optional.of(Set.copyOf(scope.names()));
    }

    /// Adds to a step's triage the warnings and notes met in the records passed over, as benign groups apart, one
    /// each by source type, element and message, each saying the source type they are of (decided by the user
    /// 2026-11-23: seen in chat 189, an Entra ID group's step said the JSON parser cut long values in Exchange mail
    /// records it passes over, and the agent asked the user to keep or cut values of a field the group doesn't
    /// map). They are not this build's to decide: the pipeline's parser settings are its own, and change nothing
    /// in the Events of the source type's own group.
    ///
    /// @param summary A step's triage ([ErrorTriage#triage]), its groups and counts by class added to.
    /// @param triage  Groups markers as the step's triage does.
    void addSetAsideTo(final Map<String, Object> summary,
                       final Function<List<ErrorTriage.Marker>, Map<String, Object>> triage) {
        for (final Map.Entry<String, List<ErrorTriage.Marker>> entry : setAside.entrySet()) {
            final List<ErrorTriage.Marker> markers = entry.getValue();
            for (final Map<String, Object> group : SteppingVerdicts.groups(triage.apply(markers))) {
                group.put("class", ErrorTriage.BENIGN);
                group.put("reason", setAsideReason(entry.getKey()));
                group.put("passed_over_source_type", entry.getKey());
                group.put("records", new TreeSet<>(markers.stream()
                        .filter(marker -> Objects.equals(marker.severity(), group.get("severity"))
                                          && Objects.equals(marker.element(), group.get("element")))
                        .map(ErrorTriage.Marker::record)
                        .filter(Objects::nonNull)
                        .toList()).stream().limit(SteppingVerdicts.MAX_RECORDS_LISTED).toList());
                group.remove("cause");
                group.remove("may_follow_from");
                SteppingVerdicts.groups(summary).add(group);
                SteppingVerdicts.byClass(summary).merge(ErrorTriage.BENIGN, 1, Integer::sum);
            }
        }
    }

    /// @param sourceType The source type of the records passed over.
    /// @return Why their warnings are benign for the group's build.
    static String setAsideReason(final String sourceType) {
        return "In records of " + (NOT_TOLD.equals(sourceType)
                ? "other source types"
                : sourceType) + ", which this group passes over: not this build's to decide (don't ask the user "
               + "of it for this group). The pipeline's parser settings are its own: changing one here changes "
               + "nothing in the Events of that source type's own group.";
    }

    /// Whether a marker of a record the group passes over still counts against the group: an ERROR or FATAL can
    /// break the stream whoever's record it is, but a WARNING or INFO (a parser's note on a long value of another
    /// group's source type, say) is about data that isn't this group's: set aside, benign ([#addSetAsideTo]).
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
               + "records_with_errors or the verdict. Their warnings are listed apart, as benign groups naming "
               + "the source type (passed_over_source_type): not this build's to decide. Only an ERROR or FATAL "
               + "on one of them counts against the group.";
    }

    // The source type of the record stepped, by the group's rows ([SourceTypeScope#UNMATCHED] for a value no row
    // matches, or none); null when it can't be told (no group's rows, or input that isn't XML).
    private String sourceType(final SteppingResult result, final StepLocation location) {
        if (scope == null) {
            return null;
        }
        if (scope.byPart()) {
            if (location == null || attributes == null) {
                return null;
            }
            final int part = (int) location.getPartIndex();
            return scope.sourceType(attributes.fields(scope.field(), location.getMetaId(), part))
                    .orElse(SourceTypeScope.UNMATCHED);
        }
        final String input = SteppingWalks.input(result, inputElement);
        if (input == null || input.isBlank()) {
            return null;
        }
        final Map<String, Object> fields = fieldsOf(input);
        if (fields == null) {
            return null;
        }
        return scope.sourceType(fields).orElse(SourceTypeScope.UNMATCHED);
    }

    // Whether the pipeline wrote an Event for the record: then it is the group's, whatever its fields read as here.
    private boolean wroteAnEvent(final SteppingResult result) {
        return outputElement != null && EventXml.countEvents(SteppingWalks.output(result, outputElement)) > 0;
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
            root = XmlParser.parse(readable(xml));
        } catch (final RuntimeException e) {
            return null;
        }
        final Map<String, Object> fields = new LinkedHashMap<>();
        flatten(root, "", fields);
        return fields;
    }

    // The record as XML 1.1, as Stroom writes it: control characters (raw, or by reference) are XML 1.1's, and NUL,
    // which no XML holds, left out. Seen in chat 189: Teams and Exchange records holding NUL, BEL and ESC in their
    // message bodies weren't read, so their source type wasn't told, and records the group passes over were said
    // to produce no Event, their parser's warnings this build's.
    private static String readable(final String xml) {
        final String body = NUL.matcher(DECLARATION.matcher(xml).replaceFirst("")).replaceAll("");
        final String referenced = XPathChecks.xml11(body);
        return referenced.startsWith(XmlTree.XML_11_DECLARATION)
                ? referenced
                : XmlTree.XML_11_DECLARATION + referenced;
    }

    private static void flatten(final XmlElement element, final String path, final Map<String, Object> fields) {
        final String name = element.get("key") != null
                ? element.get("key")
                : element.get("name");
        // A JSON key spelt as the tools spell JSON fields; a Data Splitter name dotted under its parent.
        final String at = name == null
                ? path
                : element.get("key") != null
                        ? JsonFieldNames.child(path, name)
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


    /// One record's markers, an older XSLT's pass-over line taken out.
    ///
    /// @param markers    The markers left.
    /// @param passedOver True if the group's pipeline passed the record over.
    /// @param sourceType The source type of a record passed over ([#NOT_TOLD] where it can't be told); null for
    ///                   one not passed over.
    record Sifted(List<ErrorTriage.Marker> markers, boolean passedOver, String sourceType) {

        /// @param markers    The markers left.
        /// @param passedOver True if the group's pipeline passed the record over.
        Sifted(final List<ErrorTriage.Marker> markers, final boolean passedOver) {
            this(markers, passedOver, null);
        }
    }
}
