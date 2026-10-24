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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.DropRule;
import stroom.ai.impl.tools.generation.EventRule;
import stroom.ai.impl.tools.generation.Extraction;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.SharedFunctions;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.XmlTree;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.XPathChecks;
import stroom.util.shared.NullSafe;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// A translation mapping checked against the sample's records before anything is generated or stepped (the Stroom
/// MCP server's `tools/generation.py` `_sample_check`, with `utils/xpathcheck.py` `check_xpaths`,
/// `check_extractions`, `record_documents` and `xpath_inputs`, on [LocalCheck] and [XPathChecks]): fields no record
/// has, time formats the values don't fit, schema values a constant or map gives, xpaths that select nothing and
/// extraction regexes that match nothing.
final class MappingSampleCheck {

    /// How long the check may take, as the Stroom MCP server's `sample_check_seconds` default.
    static final Duration CHECK_TIME = Duration.ofSeconds(60);

    private MappingSampleCheck() {
        // Static only.
    }

    /// The mapping checked against the sample's records. Seen in production: a Fortigate sample's check (10 streams
    /// of 1 MB, 60 extract entries) held the server for over a minute; past [#CHECK_TIME], the extractions left are
    /// named, not checked.
    ///
    /// @param mapping  The mapping.
    /// @param samples  The sample's texts.
    /// @param splitter The Data Splitter spec that reads text, or null.
    /// @param schema   The event-logging schema.
    /// @param inferred True if the spec was inferred from the sample rather than given.
    /// @param clock    The clock the check's time is read against.
    /// @return `records`, `problems`, `warnings`, `fields_seen`, `not_checked`, `kept_unknown` and `note`, as the
    /// Stroom MCP server gave them.
    static Map<String, Object> check(final TranslationMapping mapping,
                                     final List<String> samples,
                                     final SplitterSpec splitter,
                                     final EventSchema schema,
                                     final boolean inferred,
                                     final Clock clock) {
        final Instant deadline = clock.instant().plus(CHECK_TIME);
        final LocalCheck.SampleRecords found = LocalCheck.sampleRecords(mapping, samples, splitter);
        final Map<String, Object> check = new LinkedHashMap<>(LocalCheck.checkMapping(mapping, found.records()));
        final List<String> problems = new ArrayList<>(JsonValues.strings(check.get("problems")));
        final List<String> warnings = new ArrayList<>(JsonValues.strings(check.get("warnings")));
        problems.addAll(LocalCheck.checkSchemaValues(mapping, found.records(), schema));
        warnings.addAll(LocalCheck.domainQualifiedUserIds(mapping, found.records()));
        warnings.addAll(checkXPaths(mapping, samples, splitter));
        final XPathChecks.Findings extractions = checkExtractions(mapping, samples, splitter, found.records(),
                deadline, clock);
        problems.addAll(extractions.problems());
        warnings.addAll(extractions.warnings());
        if (inferred) {
            // Read with a guess at the converter, a field the guess doesn't name may be one the build's own
            // converter does (seen: a hand-written syslog splitter's 'pwd' refused, as the inferred one has no such
            // field).
            final List<String> missing = problems.stream()
                    .filter(p -> p.startsWith("field '") && p.contains(" is in none of the "))
                    .toList();
            problems.removeAll(missing);
            missing.forEach(p -> warnings.add(p + " (the sample was read with an inferred Data Splitter: give "
                                              + "splitter if the build's converter names its fields otherwise)"));
        }
        check.put("problems", problems);
        check.put("warnings", warnings);
        if (NullSafe.isNonEmptyString(found.note())) {
            check.put("note", found.note());
        }
        return check;
    }

    /// Warnings for xpaths that select nothing in any sample record (or fail to evaluate).
    ///
    /// @param mapping  The mapping.
    /// @param samples  The sample's texts.
    /// @param splitter The Data Splitter spec, or null.
    /// @return The warnings.
    static List<String> checkXPaths(final TranslationMapping mapping,
                                    final List<String> samples,
                                    final SplitterSpec splitter) {
        if (NullSafe.isNonEmptyString(mapping.forEach())) {
            return List.of();
        }
        final Map<String, List<String>> inputs = xpathInputs(mapping);
        if (inputs.isEmpty()) {
            return List.of();
        }
        return XPathChecks.checkXPaths(inputs, MappingFacts.mappingNamespaces(mapping),
                recordDocuments(mapping, samples, splitter), MappingFacts.inputNamespace(mapping));
    }

    /// Each extraction's regex run (with XPath's own regex rules) on the text it reads in the sample.
    ///
    /// @param mapping  The mapping.
    /// @param samples  The sample's texts.
    /// @param splitter The Data Splitter spec, or null.
    /// @param records  The sample's records, as [LocalCheck#sampleRecords] reads them.
    /// @param deadline When to stop checking.
    /// @param clock    The clock it is read against.
    /// @return The problems and warnings.
    @SuppressWarnings("unchecked")
    static XPathChecks.Findings checkExtractions(final TranslationMapping mapping,
                                                 final List<String> samples,
                                                 final SplitterSpec splitter,
                                                 final List<Object> records,
                                                 final Instant deadline,
                                                 final Clock clock) {
        if (NullSafe.isNonEmptyString(mapping.forEach()) || mapping.extract().isEmpty()) {
            return new XPathChecks.Findings(List.of(), List.of());
        }
        final List<XPathChecks.Extraction> extractions = mapping.extract().stream()
                .map(MappingSampleCheck::extraction)
                .toList();
        final List<Map<String, ?>> maps = new ArrayList<>();
        records.stream()
                .filter(Map.class::isInstance)
                .forEach(record -> maps.add((Map<String, ?>) record));
        final boolean fieldTexts = "data_splitter".equals(mapping.input()) || "json".equals(mapping.input());
        return XPathChecks.checkExtractions(extractions, fieldTexts, maps,
                () -> recordDocuments(mapping, samples, splitter), MappingFacts.inputNamespace(mapping),
                MappingFacts.mappingNamespaces(mapping), deadline, clock);
    }

    /// Each sample record as the XML document the translation reads it from (its root element is the record).
    ///
    /// @param mapping  The mapping.
    /// @param samples  The sample's texts.
    /// @param splitter The Data Splitter spec, or null.
    /// @return The documents.
    static List<String> recordDocuments(final TranslationMapping mapping,
                                        final List<String> samples,
                                        final SplitterSpec splitter) {
        final String namespace = MappingFacts.inputNamespace(mapping);
        final List<String> documents = new ArrayList<>();
        if ("json".equals(mapping.input())) {
            samples.forEach(text -> documents.addAll(XPathChecks.jsonRecordDocuments(text, namespace)));
            return documents;
        }
        if ("data_splitter".equals(mapping.input())) {
            if (splitter == null) {
                return documents;
            }
            for (final String text : samples) {
                SplitterDryRun.dryRun(splitter, PyText.strip(text, "﻿\r\n ")).records().forEach(record ->
                        documents.add(XPathChecks.splitterRecordDocument(record, namespace)));
            }
            return documents;
        }
        for (final Object record : LocalCheck.sampleRecords(mapping, samples, splitter).records()) {
            if (record instanceof final XmlTree.Element element) {
                documents.add(XPathChecks.xml11(document(mapping, element, namespace)));
            }
        }
        return documents;
    }

    /// Each xpath the mapping reads, with what it is used for: not those calling a shared XSLT's functions (only
    /// Stroom, which imports it, can run them), nor those reading a variable, which only the XSLT binds.
    ///
    /// @param mapping The mapping.
    /// @return The xpaths, each with what it is used for (e.g. `EventTime/TimeCreated`, `[logon] when`).
    static Map<String, List<String>> xpathInputs(final TranslationMapping mapping) {
        final Map<String, List<String>> used = new LinkedHashMap<>();
        mapping.common().forEach(entry -> entryXPaths(used, entry, entry.path()));
        for (final EventRule rule : mapping.events()) {
            rule.fields().forEach(entry -> entryXPaths(used, entry, "[" + rule.name() + "] " + entry.path()));
        }
        for (final EventRule rule : mapping.events()) {
            for (final Condition condition : rule.when()) {
                add(used, condition.xpath(), "[" + rule.name() + "] when");
            }
        }
        for (final DropRule drop : mapping.dropWhen()) {
            for (final Condition condition : drop.when()) {
                add(used, condition.xpath(), "drop: " + drop.reason());
            }
        }
        for (final Extraction extraction : mapping.extract()) {
            add(used, extraction.xpath(), "extract");
        }
        final List<String> imported = mapping.functions().stream().map(SharedFunctions::prefix).toList();
        final Map<String, List<String>> out = new LinkedHashMap<>();
        used.forEach((xpath, uses) -> {
            if (XPathChecks.evaluable(xpath, imported)) {
                out.put(xpath, uses);
            }
        });
        return out;
    }

    // A record as the XMLFragmentParser's wrapper gives it: a fragment declaring no namespace takes its default.
    // Read without it, the checks passed xpaths that select nothing in Stroom.
    private static String document(final TranslationMapping mapping,
                                   final XmlTree.Element element,
                                   final String namespace) {
        final String text = XmlTree.toText(element);
        if (!"xml_fragments".equals(mapping.input())) {
            return text;
        }
        final XmlTree.Element wrapped = XmlTree.parse("<records xmlns=\"" + NullSafe.string(namespace) + "\">" + text
                                                      + "</records>");
        return XmlTree.toText(wrapped.elements().getFirst());
    }

    private static void entryXPaths(final Map<String, List<String>> used, final FieldMapping entry, final String as) {
        add(used, entry.xpath(), as);
        if (entry.lookup() != null) {
            add(used, entry.lookup().xpath(), as);
        }
    }

    private static void add(final Map<String, List<String>> used, final String xpath, final String as) {
        if (NullSafe.isNonEmptyString(xpath)) {
            used.computeIfAbsent(xpath, key -> new ArrayList<>()).add(as);
        }
    }

    private static XPathChecks.Extraction extraction(final Extraction extraction) {
        return new XPathChecks.Extraction(extraction.field(), extraction.xpath(), extraction.regex(),
                extraction.flags(), extraction.names());
    }
}
