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

import stroom.ai.impl.tools.AgentInstructions;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.InstructionRules;
import stroom.ai.impl.tools.InstructionRules.RequiredImport;
import stroom.ai.impl.tools.ToolSession;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// The shared XSLTs standing instructions require each stage's XSLTs to import (the Stroom MCP server's
/// `tools/generation.py` `_required_for` and `_import_notes`, and `tools/translation.py`
/// `_refuse_without_required_imports`, with `xslt_role` and `_ROLE_STAGE`).
class RequiredImports {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RequiredImports.class);

    /// The stage of an XSLT of each build role.
    static final Map<String, String> ROLE_STAGE = Map.of(
            "Events", InstructionRules.TRANSLATION,
            "Records", InstructionRules.RECORDS,
            "Indexing", InstructionRules.INDEXING,
            "Reference", InstructionRules.REFERENCE,
            "CEF", InstructionRules.CEF);

    private static final Pattern EVENTS = Pattern.compile("<(?:\\w+:)?Events[\\s/>]");
    private static final Pattern TEXT_OUTPUT = Pattern.compile("<xsl:output[^>]*method=\"text\"");
    private static final Pattern ARRAY = Pattern.compile("<(?:\\w+:)?array[\\s/>]");
    private static final Pattern RECORDS = Pattern.compile("<(?:\\w+:)?records[\\s/>]");

    private final AgentInstructions agentInstructions;
    private final Provider<WriteGuard> guardProvider;
    private final Provider<DocFinder> docFinderProvider;
    private final DocumentAccess documentAccess;

    /// @param agentInstructions The AGENTS docs, and the imports they require.
    /// @param guardProvider     Finds the build's folder.
    /// @param docFinderProvider Finds a required import by name.
    /// @param documentAccess    Reads it, as the user.
    @Inject
    RequiredImports(final AgentInstructions agentInstructions,
                    final Provider<WriteGuard> guardProvider,
                    final Provider<DocFinder> docFinderProvider,
                    final DocumentAccess documentAccess) {
        this.agentInstructions = agentInstructions;
        this.guardProvider = guardProvider;
        this.docFinderProvider = docFinderProvider;
        this.documentAccess = documentAccess;
    }

    /// @param session The session, for the audit log.
    /// @param stage   The stage, e.g. [InstructionRules#TRANSLATION].
    /// @param build   The build, or null.
    /// @param feeds   The feeds the work is for, or none.
    /// @return The imports standing instructions require of this stage's XSLTs, for the build and its feeds;
    /// none when the instructions can't be read.
    List<RequiredImport> requiredFor(final ToolSession session,
                                     final String stage,
                                     final String build,
                                     final List<String> feeds) {
        final List<String> folders = new ArrayList<>();
        if (NullSafe.isNonBlankString(build)) {
            try {
                guardProvider.get().buildFolder(build, false).ifPresent(folder -> folders.add(folder.path()));
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "No folder for build " + build + ": " + e.getMessage());
            }
        }
        try {
            return agentInstructions.importsRequired(session, stage, folders, NullSafe.list(feeds));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the required imports: " + e.getMessage(), e);
            return List.of();
        }
    }

    /// Each required import, with what it offers: its named templates (called with shared entries where they
    /// write) and template rules (which apply without a call); with functions (a translation, whose xpaths can
    /// call them), its xsl:functions and the functions entry that binds their prefix. A required import may
    /// define functions an agent told only of its templates never calls (e.g. geo:lookup-ip() in GeoIP-V1).
    ///
    /// @param required  The required imports.
    /// @param functions True to say what functions each offers.
    /// @return A note on each.
    List<Map<String, Object>> importNotes(final List<RequiredImport> required, final boolean functions) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final RequiredImport r : required) {
            final Map<String, Object> note = new LinkedHashMap<>();
            note.put("import", r.href());
            note.put("required_by", "the AGENTS doc in " + root(r.from()));
            try {
                final Optional<XsltDoc> found = named(r.href());
                if (found.isEmpty()) {
                    note.put("problem", "no XSLT named '" + r.href() + "' in Stroom: the import will fail until it "
                                        + "exists");
                } else {
                    describe(note, NullSafe.string(found.get().getData()), functions);
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to read the required import " + r.href() + ": " + e.getMessage(), e);
            }
            out.add(note);
        }
        return out;
    }

    /// An XSLT of a stage whose standing instructions require shared imports, without them, is refused (asked for
    /// by the user): generated ones get them from the generator; one written by hand must add them.
    ///
    /// @param session The session, for the audit log.
    /// @param code    The XSLT.
    /// @param build   Its build.
    /// @param role    Its role, or null to tell it from the code ([#xsltRole(String)]).
    /// @throws ToolRefusal if it lacks one.
    void refuseWithout(final ToolSession session, final String code, final String build, final String role) {
        final String stage = ROLE_STAGE.get(NullSafe.nonBlankStringElse(role, NullSafe.string(xsltRole(code))));
        if (stage == null) {
            return;
        }
        final Set<String> have = new LinkedHashSet<>(SharedXslt.importsOf(code));
        final List<RequiredImport> missing = requiredFor(session, stage, build, List.of()).stream()
                .filter(r -> !have.contains(r.href()))
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        final Set<String> from = new TreeSet<>();
        missing.forEach(r -> from.add(root(r.from())));
        throw new ToolRefusal("Standing instructions require every " + stage + " XSLT to import "
                              + missing.stream().map(r -> SampleArguments.pythonRepr(r.href()))
                                      .collect(Collectors.joining(", "))
                              + " (the AGENTS doc in " + String.join(", ", from) + "): add "
                              + missing.stream().map(r -> "<xsl:import href=\"" + r.href() + "\"/>")
                                      .collect(Collectors.joining(" "))
                              + " first in the stylesheet, or build it from a mapping (build_translation_xslt adds "
                              + "them)");
    }

    /// The build subfolder an XSLT belongs in, from what it writes: Events (a translation), reference data, CEF
    /// lines or Kafka records, or documents for an index.
    ///
    /// @param code An XSLT.
    /// @return Its role, or null when it can't be told (the build's top).
    static String xsltRole(final String code) {
        final String text = NullSafe.string(code);
        if (EVENTS.matcher(text).find()) {
            return "Events";
        }
        if (text.contains("<referenceData") || text.contains("reference-data:2")) {
            return "Reference";
        }
        if (text.contains("kafka-records") || TEXT_OUTPUT.matcher(text).find()) {
            return "CEF";
        }
        final boolean records = RECORDS.matcher(text).find();
        if (ARRAY.matcher(text).find() || (records && text.contains("event-logging:"))) {
            // Documents for an index, from Events.
            return "Indexing";
        }
        if (records) {
            // Records from a source's own: written as a Records stream.
            return "Records";
        }
        return null;
    }

    private void describe(final Map<String, Object> note, final String data, final boolean functions) {
        final Map<String, Object> described = SharedXslt.describe(data);
        final Map<String, Object> named = new LinkedHashMap<>();
        if (described.get("templates") instanceof final Map<?, ?> templates) {
            templates.forEach((name, about) -> named.put(String.valueOf(name), about instanceof final Map<?, ?> map
                    ? map.get("writes")
                    : null));
        }
        note.put("named_templates", named);
        note.put("template_rules", described.get("template_rules") instanceof final List<?> rules
                ? rules.size()
                : 0);
        if (functions && described.get("functions") instanceof final Map<?, ?> offered && !offered.isEmpty()) {
            final Map<String, Object> shown = new LinkedHashMap<>();
            final Map<List<String>, Boolean> entries = new LinkedHashMap<>();
            offered.forEach((name, about) -> {
                final Map<?, ?> function = about instanceof final Map<?, ?> map
                        ? map
                        : Map.of();
                final Map<String, Object> kept = new LinkedHashMap<>();
                for (final String key : List.of("params", "returns")) {
                    if (function.containsKey(key)) {
                        kept.put(key, function.get(key));
                    }
                }
                shown.put(String.valueOf(name), kept);
                if (BuildPlan.truthy(function.get("prefix")) && BuildPlan.truthy(function.get("namespace"))) {
                    entries.put(List.of(String.valueOf(function.get("prefix")),
                            String.valueOf(function.get("namespace"))), true);
                }
            });
            note.put("functions", shown);
            final List<Map<String, Object>> bound = new ArrayList<>();
            entries.keySet().forEach(entry -> {
                final Map<String, Object> binding = new LinkedHashMap<>();
                binding.put("href", note.get("import"));
                binding.put("prefix", entry.get(0));
                binding.put("namespace", entry.get(1));
                bound.add(binding);
            });
            note.put("functions_entries", bound);
        }
    }

    // The XSLT of exactly that name the user may view: Stroom imports by name.
    private Optional<XsltDoc> named(final String href) {
        for (final DocRef ref : NullSafe.list(docFinderProvider.get().findByName(XsltDoc.TYPE, href, false))) {
            if (href.equals(ref.getName())) {
                final Optional<XsltDoc> doc = documentAccess.read(ref, XsltDoc.class);
                if (doc.isPresent()) {
                    return doc;
                }
            }
        }
        return Optional.empty();
    }

    // The folder of an AGENTS doc, the top of the tree as "(root)".
    private static String root(final String from) {
        return NullSafe.isBlankString(from) || "/".equals(from.strip())
                ? "(root)"
                : from;
    }
}
