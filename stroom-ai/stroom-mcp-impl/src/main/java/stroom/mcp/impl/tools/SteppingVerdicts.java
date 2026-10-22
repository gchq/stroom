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
import stroom.pipeline.shared.stepping.SteppingResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// What the stepping tools make of what a step wrote (the Stroom MCP server's `tools/stepping.py` helpers
/// `sends_on`, `_empty_output`, `_writes_unknown`, `_what_unknown_holds`, `_no_reference`, `_no_events`, `_block`
/// and `_nothing_stepped`): output that holds nothing, Events with no Event, referenceData with no reference, and
/// EventDetail/Unknown, each as a marker or a blocking group added to a triaged verdict.
final class SteppingVerdicts {

    /// What the marker for output with no elements says first.
    static final String NO_ELEMENTS = "Output contains no XML elements";

    private static final Pattern ELEMENT = Pattern.compile("<[A-Za-z_]");
    private static final Pattern CEF_LINE = Pattern.compile("^\\s*CEF:\\d+\\|", Pattern.MULTILINE);
    private static final Pattern XML_DECLARATION = Pattern.compile("^\\s*<\\?xml[^>]*\\?>");
    // EventDetail/Unknown: the schema's only Unknown.
    private static final Pattern UNKNOWN = Pattern.compile("<(?:[\\w.-]+:)?Unknown[\\s>/]",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern TYPE_ID = Pattern.compile("<(?:[\\w.-]+:)?TypeId>([^<]*)<",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern DATA = Pattern.compile(
            "<(?:[\\w.-]+:)?Data\\s+Name=\"([^\"]*)\"\\s+Value=\"([^\"]*)\"", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern EVENTS_ROOT = Pattern.compile("<(?:[\\w.-]+:)?Events[\\s>/]",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern REFERENCE_ROOT = Pattern.compile("<(?:[\\w.-]+:)?referenceData[\\s>/]",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern REFERENCE = Pattern.compile("<(?:[\\w.-]+:)?reference[\\s>]",
            Pattern.UNICODE_CHARACTER_CLASS);
    /// The most records a group lists.
    private static final int MAX_RECORDS_LISTED = 20;

    private SteppingVerdicts() {
        // Static methods only.
    }

    /// @param output An element's output.
    /// @return True for output a forwarding pipeline writes on purpose: CEF lines as text, or Kafka records. Not
    /// Events, and not a translation whose templates failed to match.
    static boolean sendsOn(final String output) {
        final String text = Objects.requireNonNullElse(output, "");
        return CEF_LINE.matcher(text).find() || text.contains("<kafkaRecord");
    }

    /// A marker when an XSLT produced no elements at all.
    ///
    /// When no template matches the input (typically a wrong xpath-default-namespace), XSLT's built-in rules copy
    /// the text through, nothing raises an error, and processing silently writes nothing.
    ///
    /// @param result  A step.
    /// @param element The element whose output to look at.
    /// @param record  The record stepped.
    /// @return The marker, or none.
    static List<ErrorTriage.Marker> emptyOutput(final SteppingResult result,
                                                final String element,
                                                final String record) {
        final String body = XML_DECLARATION.matcher(SteppingWalks.output(result, element)).replaceFirst("");
        if (ELEMENT.matcher(body).find() || sendsOn(body)) {
            return List.of();
        }
        return List.of(ErrorTriage.marker("ERROR", element, NO_ELEMENTS + ": the XSLT's templates did not match "
                                                            + "the input. Check xpath-default-namespace and the "
                                                            + "match patterns against the element's input.", null)
                .withRecord(record));
    }

    /// @param output An element's output.
    /// @return True if it writes EventDetail/Unknown.
    static boolean writesUnknown(final String output) {
        return UNKNOWN.matcher(Objects.requireNonNullElse(output, "")).find();
    }

    /// @param output An element's output.
    /// @return True if it is event-logging Events.
    static boolean writesEvents(final String output) {
        return EVENTS_ROOT.matcher(Objects.requireNonNullElse(output, "")).find();
    }

    /// @param output An element's output.
    /// @return True if it is reference data (referenceData).
    static boolean writesReferenceData(final String output) {
        return REFERENCE_ROOT.matcher(Objects.requireNonNullElse(output, "")).find();
    }

    /// @param output An element's output.
    /// @return True if it holds a reference.
    static boolean writesAReference(final String output) {
        return REFERENCE.matcher(Objects.requireNonNullElse(output, "")).find();
    }

    /// @param outputs The outputs of the records that came out as EventDetail/Unknown.
    /// @return What the Unknown events hold, for a person to judge: their TypeIds and Data values, a few of each.
    static String whatUnknownHolds(final List<String> outputs) {
        final Map<String, List<String>> values = new LinkedHashMap<>();
        for (final String output : outputs) {
            final List<String[]> found = new ArrayList<>();
            final Matcher typeIds = TYPE_ID.matcher(output);
            while (typeIds.find()) {
                found.add(new String[]{"TypeId", typeIds.group(1)});
            }
            final Matcher data = DATA.matcher(output);
            while (data.find()) {
                found.add(new String[]{data.group(1), data.group(2)});
            }
            for (final String[] nameValue : found) {
                final List<String> seen = values.computeIfAbsent(nameValue[0], k -> new ArrayList<>());
                if (!nameValue[1].isEmpty() && !seen.contains(nameValue[1]) && seen.size() < 5) {
                    seen.add(nameValue[1]);
                }
            }
        }
        final List<String> said = new ArrayList<>();
        values.entrySet().stream()
                .limit(6)
                .filter(entry -> !entry.getValue().isEmpty())
                .forEach(entry -> said.add(entry.getKey() + ": " + String.join(", ", entry.getValue())));
        return String.join("; ", said);
    }

    /// The verdict when a reference-data XSLT wrote referenceData for every record and a reference for none
    /// (seen: a directory whose header line was read as a record, the columns named col1..., and the key field
    /// found nowhere; processing then wrote no Reference stream, and nothing said why).
    ///
    /// @param element The XSLT's element.
    /// @param keys    The records stepped.
    /// @return A blocking group.
    static Map<String, Object> noReference(final String element, final List<String> keys) {
        final String message = "None of the " + keys.size() + " records produced reference data: the XSLT writes "
                               + "referenceData, but no <reference>. Usually the key or value fields aren't in "
                               + "the records (the Data Splitter's field names: a header line read as data names "
                               + "them col1, col2 ...), or the namespace it reads them in.";
        return blocking("No record produced reference data", element, keys.size(), message, keys);
    }

    /// The verdict when a translation wrote Events for every record and an Event for none: its record templates
    /// select nothing. Seen: XML fragments read with no namespace, where the wrapper gives them records:2; every
    /// record came out as an empty Events, and stepping said clean.
    ///
    /// @param element The XSLT's element.
    /// @param keys    The records stepped.
    /// @return A blocking group.
    static Map<String, Object> noEvents(final String element, final List<String> keys) {
        final String message = "None of the " + keys.size() + " records produced an Event: the XSLT writes Events, "
                               + "but its record templates select nothing in the input. Usually the input's "
                               + "namespace (xpath-default-namespace: records:2 for a Data Splitter, and for XML "
                               + "fragments the wrapper root's xmlns (records:2, or event-logging:3 for an <Events> "
                               + "wrapper) unless they declare their own; the mapping's xml_namespace) or the "
                               + "record element's name (the mapping's record). Every record would be lost.";
        return blocking("No record produced an Event", element, keys.size(), message, keys);
    }

    /// A blocking group of the pipeline's own element, as triage gives groups, with the records it is of.
    ///
    /// @param reason  Why it blocks.
    /// @param element The element.
    /// @param count   How many records it affects.
    /// @param message Its one example's message.
    /// @param records The records, of which the first 20 are listed.
    /// @return The group.
    static Map<String, Object> blocking(final String reason,
                                        final String element,
                                        final int count,
                                        final String message,
                                        final List<String> records) {
        final Map<String, Object> example = new LinkedHashMap<>();
        example.put("message", message);
        example.put("location", null);
        final Map<String, Object> group = new LinkedHashMap<>();
        group.put("class", ErrorTriage.BLOCKING);
        group.put("reason", reason);
        group.put("severity", "ERROR");
        group.put("element", element);
        group.put("own_element", true);
        group.put("count", count);
        group.put("records_affected", count);
        group.put("examples", List.of(example));
        group.put("records", records.stream().limit(MAX_RECORDS_LISTED).toList());
        return group;
    }

    /// Adds a blocking group in front of a triaged verdict's groups, making the verdict blocking.
    ///
    /// @param summary A triage, as [ErrorTriage#triage] gives it.
    /// @param group   The group, or null (nothing is added).
    static void block(final Map<String, Object> summary, final Map<String, Object> group) {
        if (group != null) {
            groups(summary).addFirst(group);
            byClass(summary).merge(ErrorTriage.BLOCKING, 1, Integer::sum);
            summary.put("verdict", ErrorTriage.BLOCKING);
        }
    }

    /// No record was stepped: never clean, and say why.
    ///
    /// @param result  The tool's result, its verdict and hint set here.
    /// @param markers The errors met.
    /// @param where   What was stepped, e.g. `streams [7]`.
    static void nothingStepped(final Map<String, Object> result,
                               final List<ErrorTriage.Marker> markers,
                               final String where) {
        result.put("verdict", ErrorTriage.BLOCKING);
        result.put("hint", !markers.isEmpty()
                ? "No record was stepped: the errors in groups stop the pipeline before its first record; fix them "
                  + "and step again."
                : "No record was stepped: Stroom found none in " + where + ". Check the stream ids, and that the "
                  + "pipeline's parser reads this data.");
    }

    /// Gives each triaged group the records it was met in: those of the markers of its severity and element.
    ///
    /// @param summary A triage, as [ErrorTriage#triage] gives it.
    /// @param markers The markers it was made from.
    static void listRecords(final Map<String, Object> summary, final List<ErrorTriage.Marker> markers) {
        for (final Map<String, Object> group : groups(summary)) {
            final List<String> records = markers.stream()
                    .filter(marker -> Objects.equals(marker.severity(), group.get("severity"))
                                      && Objects.equals(marker.element(), group.get("element")))
                    .map(ErrorTriage.Marker::record)
                    .filter(Objects::nonNull)
                    .toList();
            group.put("records", firstSorted(records));
        }
    }

    /// @param summary A triage, as [ErrorTriage#triage] gives it.
    /// @return Its groups, to change.
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> groups(final Map<String, Object> summary) {
        return (List<Map<String, Object>>) summary.get("groups");
    }

    /// @param summary A triage, as [ErrorTriage#triage] gives it.
    /// @return How many groups of each class it has, to change.
    @SuppressWarnings("unchecked")
    static Map<String, Integer> byClass(final Map<String, Object> summary) {
        return (Map<String, Integer>) summary.get("groups_by_class");
    }

    private static List<String> firstSorted(final List<String> records) {
        return new TreeSet<>(records).stream().limit(MAX_RECORDS_LISTED).toList();
    }
}
