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

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.sample.SourceTypeFields;
import stroom.ai.impl.tools.sample.SourceTypeReading;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/// How survey_feed takes a source type field the user gives or chooses: a field of the records, or a reading into
/// the structured data a field holds ([SourceTypeReading]: `event>logger_name`, readings tried in turn joined by
/// ` | `), checked against the records read; and how the candidates are said, a held reading with the formats it
/// reads and the records it gives no value (the `(no value)` source type).
final class SourceTypeReadings {

    private SourceTypeReadings() {
        // Static only.
    }

    /// @param given   A source type field or reading the user gave.
    /// @param records The records read (none when the head held no structured records).
    /// @param arg     The argument it came in, for the refusals.
    /// @return It as the records spell it; empty when it names no field of theirs.
    /// @throws ToolRefusal when a reading isn't one, or reads no value from any record read.
    static Optional<String> spelt(final String given, final List<Map<String, Object>> records, final String arg) {
        if (!SourceTypeReading.isReading(given)) {
            if (records.isEmpty()) {
                return Optional.of(given);
            }
            final List<String> names = new ArrayList<>();
            records.forEach(record -> names.addAll(record.keySet()));
            return names.stream().filter(name -> name.equalsIgnoreCase(given)).findFirst();
        }
        final Optional<String> problem = SourceTypeReading.problem(given);
        if (problem.isPresent()) {
            throw new ToolRefusal(arg + " '" + given + "' isn't a reading the tools take: " + problem.get() + ". A "
                                  + "reading is a field, then '>' into the structured data it holds (JSON, XML, a "
                                  + "syslog header, key=value), '.' into its objects or elements: e.g. "
                                  + "event>logger_name, event>xml:Header.@logger; readings tried in turn are joined "
                                  + "by ' | '.");
        }
        final Optional<String> spelt = SourceTypeReading.asRecordsSpellIt(given, records);
        if (spelt.isEmpty() || records.isEmpty()) {
            return spelt;
        }
        final SourceTypeReading reading = SourceTypeReading.of(spelt.get());
        if (records.stream().noneMatch(record -> reading.valueOf(record) != null)) {
            final Map<String, Integer> formats = new LinkedHashMap<>();
            records.forEach(record -> reading.heldFormats(record).values()
                    .forEach(format -> formats.merge(format, 1, Integer::sum)));
            throw new ToolRefusal(arg + " '" + given + "' reads no value from any of the " + records.size()
                                  + " record(s) read: their held values are " + formats + " (plain text is never "
                                  + "read for a source type). Ask the user which name inside the held value says "
                                  + "the source, for each format it is held in.");
        }
        return spelt;
    }

    /// @param candidate A source type field candidate.
    /// @return It said in a line of a form (plain text): its label, values and records, and for a held reading the
    /// formats it reads and the records it gives no value.
    static String said(final SourceTypeFields.Candidate candidate) {
        final StringBuilder out = new StringBuilder(candidate.label() + ": " + candidate.values().size()
                                                    + " values over " + candidate.records() + " records");
        if (candidate.held()) {
            out.append(", read inside a held field (").append(formats(candidate)).append(")");
            if (candidate.noValue() > 0) {
                out.append(", ").append(candidate.noValue()).append(" records with no value (plain text or "
                                                                    + "another format: the (no value) source type)");
            }
        }
        return out.toString();
    }

    /// @param candidate A source type field candidate.
    /// @return It for the agent: its field (or reading), values, records, and for a held reading its formats and
    /// the records it gives no value.
    static Map<String, Object> json(final SourceTypeFields.Candidate candidate) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("field", candidate.field());
        out.put("values", candidate.values().subList(0, Math.min(10, candidate.values().size())));
        out.put("records", candidate.records());
        if (candidate.held()) {
            out.put("formats", candidate.formats());
            out.put("no_value", candidate.noValue());
        }
        return out;
    }

    /// @param candidates The candidates put to the user.
    /// @return What the agent is told of held readings among them; empty when none is held.
    static Optional<String> heldHint(final List<SourceTypeFields.Candidate> candidates) {
        if (candidates.stream().noneMatch(SourceTypeFields.Candidate::held)) {
            return Optional.empty();
        }
        return Optional.of("A field holding '>' is a reading into the structured data a field holds (JSON, XML, a "
                           + "syslog header, key=value, its format told per record); ' | ' joins readings tried "
                           + "in turn, one per format where the formats name the source differently. Records whose "
                           + "held value is plain text have no value: the (no value) source type, never read for "
                           + "words. Where a held format has no reading yet, propose one to the user (e.g. "
                           + "event>logger_name | event>xml:Header.@logger) and pass it as they confirm it. A "
                           + "source type by the held value's format alone (event>#format) is a last resort, the "
                           + "user's choice only.");
    }

    // The formats a held reading reads, with their records.
    private static String formats(final SourceTypeFields.Candidate candidate) {
        final List<String> said = new ArrayList<>();
        new TreeSet<>(candidate.formats().keySet()).forEach(format -> said.add(format + " "
                                                                              + candidate.formats().get(format)));
        return String.join(", ", said);
    }
}
