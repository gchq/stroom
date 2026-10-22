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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.EventRule;
import stroom.ai.impl.tools.generation.Extraction;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.XsltCompileCheck;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// What build_translation_xslt and draft_translation_mapping share (the Stroom MCP server's `tools/generation.py`
/// `_mapping_to_change`, `_kept_mapping`, `_generated`, `_meta_not_sent`, `zone_not_kept`, `_unknown_markdown`,
/// `_unknown_key`, `_unknown_agreed`, `_remember_unknown_agreed` and `_fix_hint`, and `utils/sourcenotes.py`
/// `notes_in_build`): the mapping a change starts from, the XSLT generated and compiled, and what the sample and
/// the user's documentation say of it.
class TranslationBuilds {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TranslationBuilds.class);

    /// The block an XSLT keeps the rules the user agreed to keep as Unknown in.
    static final String UNKNOWN_AGREED = "unknown agreed";
    /// What changes_applied_to says when changes went onto the session's draft.
    static final String ON_THE_DRAFT = "the draft draft_translation_mapping made";

    private static final Pattern META_CALL = Pattern.compile("stroom:meta\\(\\s*'([^']+)'\\s*\\)");

    private final WriteGuard guard;
    private final DocumentAccess documentAccess;
    private final TranslationDocs translationDocs;
    private final XsltCompileCheck compileCheck;
    private final StreamReader streamReader;

    /// @param guard           Finds an XSLT of the build by name.
    /// @param documentAccess  Reads and writes documents as the user.
    /// @param translationDocs Reads XSLTs, and says why a kept mapping is gone.
    /// @param compileCheck    Compiles an XSLT as Stroom compiles it.
    /// @param streamReader    Reads the sample streams' attributes.
    @Inject
    TranslationBuilds(final WriteGuard guard,
                      final DocumentAccess documentAccess,
                      final TranslationDocs translationDocs,
                      final XsltCompileCheck compileCheck,
                      final StreamReader streamReader) {
        this.guard = guard;
        this.documentAccess = documentAccess;
        this.translationDocs = translationDocs;
        this.compileCheck = compileCheck;
        this.streamReader = streamReader;
    }

    /// The mapping changes= apply to: the one last sent for the XSLT, else the one kept with it (uuid, or the
    /// build's XSLT of that name), else the draft draft_translation_mapping made for the build this session. Seen
    /// in testing: a first save from a 50 KB draft had to send it whole, which the agent couldn't write out in one
    /// call.
    ///
    /// @param context The call's context.
    /// @param target  The XSLT's uuid or `build/name`, or "".
    /// @param uuid    The XSLT's UUID, or null.
    /// @param build   The build, or null.
    /// @param name    The XSLT's name in the build, or null.
    /// @return The mapping, and what it was when it was the draft.
    /// @throws ToolRefusal if there is none to change.
    Base mappingToChange(final ToolCallContext context,
                         final String target,
                         final String uuid,
                         final String build,
                         final String name) {
        final Optional<Map<String, Object>> sent = SentMappings.sent(context, target);
        if (sent.isPresent()) {
            return new Base(sent.get(), null);
        }
        String found = uuid;
        if (found == null && NullSafe.isNonBlankString(build) && NullSafe.isNonBlankString(name)) {
            found = xsltNamed(build, name).orElse(null);
            if (found == null) {
                final Optional<Map<String, Object>> draft = SentMappings.sent(context, SentMappings.DRAFT + build)
                        .or(() -> SentMappings.sent(context, SentMappings.DRAFT));
                if (draft.isPresent()) {
                    return new Base(draft.get(), ON_THE_DRAFT);
                }
            }
        }
        if (found != null) {
            return new Base(keptOrRefuse(found, "XSLT " + found + " keeps no translation mapping to change: give the "
                                                + "whole mapping").payload().get("mapping") instanceof final Map<?, ?> m
                    ? copy(m)
                    : Map.of(), null);
        }
        throw new ToolRefusal("changes needs a mapping to apply to: uuid= the saved XSLT, the same build and name as "
                              + "a mapping sent or saved before, or a draft from draft_translation_mapping this "
                              + "session (build= the same build). None was found for those: draft one, or give the "
                              + "whole mapping.");
    }

    /// The mapping to regenerate from, and the schema version it was saved for: the one kept with the XSLT (uuid),
    /// else the one last sent for the build and name.
    ///
    /// @param context The call's context.
    /// @param target  The XSLT's uuid or `build/name`.
    /// @param uuid    The XSLT's UUID, or null.
    /// @return The mapping and its version (null when not known).
    /// @throws ToolRefusal if there is none.
    Kept keptMapping(final ToolCallContext context, final String target, final String uuid) {
        if (uuid != null) {
            final KeptMapping.Kept kept = keptOrRefuse(uuid, "XSLT " + uuid + " keeps no translation mapping to "
                                                             + "regenerate from: give the whole mapping "
                                                             + "(draft_translation_mapping drafts one)");
            return new Kept(kept.payload().get("mapping") instanceof final Map<?, ?> m
                    ? copy(m)
                    : Map.of(), KeptCode.keptVersion(kept.payload()));
        }
        return new Kept(mappingToChange(context, target, null, null, null).mapping(), null);
    }

    /// generate's result, compiled as Stroom compiles it: seen, an XSLT saved ok:true whose every record failed at
    /// stepping with "Variable kv has not been declared" (an xpath of $kv, an extract's name). One that doesn't
    /// compile isn't saved.
    ///
    /// @param mapping The mapping.
    /// @param schema  The event-logging schema.
    /// @param version Its version.
    /// @return The generator's result, as the Stroom MCP server's dict, its lists open to more.
    Map<String, Object> generated(final TranslationMapping mapping, final EventSchema schema, final String version) {
        final Map<String, Object> result = open(XsltGenerator.generate(mapping, schema, version).toMap());
        if (Boolean.TRUE.equals(result.get("ok"))) {
            final List<String> errors = compileCheck.compile(String.valueOf(result.get("xslt"))).errors();
            if (!errors.isEmpty()) {
                final List<String> problems = problems(result);
                errors.forEach(error -> problems.add("The XSLT doesn't compile, so it would fail every record: "
                                                     + error + ". Fix the mapping entry it comes from (an xpath "
                                                     + "reads only the record and the XSLT's functions: a field, an "
                                                     + "extract's name included, is read with field=)"));
                result.put("ok", false);
                result.put("xslt", null);
            }
        }
        return result;
    }

    /// Stream meta the mapping reads (stroom:meta('Name')) that no sample stream carries: the element would stay
    /// empty. Compared with the streams' own attributes, whatever the header is called.
    ///
    /// @param mapping   The mapping.
    /// @param streamIds The sample streams.
    /// @return A warning for each.
    List<String> metaNotSent(final TranslationMapping mapping, final List<Long> streamIds) {
        final List<String> xpaths = new ArrayList<>();
        mapping.allEntries().stream().map(FieldMapping::xpath).filter(NullSafe::isNonEmptyString).forEach(xpaths::add);
        mapping.extract().stream().map(Extraction::xpath).filter(NullSafe::isNonEmptyString).forEach(xpaths::add);
        final Set<String> wanted = new TreeSet<>();
        xpaths.forEach(xpath -> {
            final Matcher matcher = META_CALL.matcher(xpath);
            while (matcher.find()) {
                wanted.add(matcher.group(1));
            }
        });
        if (wanted.isEmpty()) {
            return List.of();
        }
        final Set<String> carried = new TreeSet<>();
        for (final Long streamId : streamIds.subList(0, Math.min(3, streamIds.size()))) {
            try {
                final Map<String, Object> got = streamReader.streamAttributes(streamId);
                for (final String part : List.of("attributes", "stream")) {
                    if (got.get(part) instanceof final Map<?, ?> map) {
                        map.keySet().forEach(key -> carried.add(String.valueOf(key).toLowerCase(Locale.ROOT)));
                    }
                }
            } catch (final RuntimeException e) {
                // Attributes unreadable: nothing to say either way.
                return List.of();
            }
        }
        return wanted.stream()
                .filter(name -> !carried.contains(name.toLowerCase(Locale.ROOT)))
                .map(name -> "stroom:meta('" + name + "') is not among the sample streams' attributes ("
                             + (carried.isEmpty()
                        ? "none"
                        : String.join(", ", carried)) + "): the element stays empty unless the source sends it as a "
                             + "header")
                .toList();
    }

    /// A time read in a named zone (timezone 'Australia/Sydney') with that zone nowhere in the event: the schema has
    /// a place for it (asked for by the user). From the mapping's own setting, not field names.
    ///
    /// @param mapping The mapping.
    /// @return The warning, or none.
    static List<String> zoneNotKept(final TranslationMapping mapping) {
        final List<FieldMapping> entries = mapping.allEntries();
        final Set<String> zones = new TreeSet<>();
        entries.stream()
                .filter(e -> e.path().endsWith("TimeCreated") && NullSafe.isNonEmptyString(e.timezone())
                             && e.timezone().contains("/"))
                .forEach(e -> zones.add(e.timezone()));
        if (zones.isEmpty() || entries.stream().anyMatch(e -> e.path().endsWith("TimeZoneName"))) {
            return List.of();
        }
        return List.of("times are read in " + String.join(", ", zones) + ", which the event doesn't keep: map "
                       + "EventSource/Device/Location/TimeZoneName (value '" + zones.iterator().next() + "') so the "
                       + "event says which zone its source writes in");
    }

    /// @param uuid  The XSLT being replaced, or null.
    /// @param build The build, or null.
    /// @param name  The XSLT's name, or null.
    /// @return The hint for a mapping with problems.
    static String fixHint(final String uuid, final String build, final String name) {
        return "Fix the problems in the mapping (not the XSLT) and call again with changes= only the rules or "
               + "entries that change" + (uuid != null
                ? " and uuid='" + uuid + "'"
                : NullSafe.isNonBlankString(build) && NullSafe.isNonBlankString(name)
                        ? " and the same build and name"
                        : ", or the whole mapping") + ".";
    }

    /// @param rule A rule kept as Unknown.
    /// @return What the user agreed to in keeping it so: its conditions and the reason given.
    static String unknownKey(final EventRule rule) {
        final Map<String, Object> key = new LinkedHashMap<>();
        key.put("when", rule.when().stream().map(c -> c.toJson(DumpMode.WITHOUT_NONE)).toList());
        key.put("reason", rule.allowUnknown());
        key.put("keep", rule.keepUnknown());
        return PyJson.dumpsSortedKeys(key);
    }

    /// The records rules keep as Unknown, for the user in the chat: per rule, how many of the sample, what they
    /// hold, what their values suggest instead and the reason given.
    ///
    /// @param kept    The rules.
    /// @param sampled What the sample check says each holds (`kept_unknown`), by rule.
    /// @param total   How many sample records were checked.
    /// @return The markdown.
    static String unknownMarkdown(final List<EventRule> kept, final Map<String, Map<String, Object>> sampled,
                                  final Object total) {
        final List<String> lines = new ArrayList<>(List.of("**Records to translate as Unknown events**", "",
                "An Unknown event says what happened isn't known: it has no Authenticate, Create or other action.",
                ""));
        for (final EventRule rule : kept) {
            final Map<String, Object> held = sampled.get(rule.name());
            lines.add(rule.when().isEmpty()
                    ? "**Records no other rule matches** (rule `" + rule.name() + "`)"
                    : "**Records rule `" + rule.name() + "` matches**");
            lines.add("");
            lines.add("- " + held.get("records") + " of the " + total + " sample records");
            lines.add("- What they hold:");
            for (final String part : NullSafe.string(NullSafe.get(held.get("sample"), String::valueOf))
                    .split("; ", -1)) {
                if (!part.isEmpty()) {
                    lines.add("  - " + part);
                }
            }
            if (JsonValues.truthy(held.get("suggested"))) {
                lines.add("- Their values suggest: " + held.get("suggested") + (JsonValues.truthy(held.get(
                        "against_suggestion"))
                        ? " (kept as Unknown though, as asked)"
                        : ""));
            }
            if (NullSafe.isNonEmptyString(rule.allowUnknown())) {
                lines.add("- Reason given: " + rule.allowUnknown());
            }
            lines.add("");
        }
        return String.join("\n", lines).stripTrailing() + "\n";
    }

    /// @param uuid  The XSLT, or null.
    /// @param build The build, or null.
    /// @param name  The build's XSLT of that name, or null.
    /// @return The rules the user agreed to keep as Unknown in this XSLT, kept with it: rule name to what they
    /// agreed to. Nothing known to have been agreed when it can't be read: the user is asked.
    Map<String, String> unknownAgreed(final String uuid, final String build, final String name) {
        try {
            String found = uuid;
            if (found == null && NullSafe.isNonBlankString(build) && NullSafe.isNonBlankString(name)) {
                found = xsltNamed(build, name).orElse(null);
            }
            if (found == null) {
                return Map.of();
            }
            final Map<String, String> agreed = new LinkedHashMap<>();
            KeptMapping.readBlock(translationDocs.readXslt(found).getDescription(), UNKNOWN_AGREED)
                    .ifPresent(block -> block.forEach((rule, key) -> agreed.put(rule, String.valueOf(key))));
            return agreed;
        } catch (final RuntimeException e) {
            return Map.of();
        }
    }

    /// Keeps the rules agreed as Unknown with the saved XSLT (only those it still keeps).
    ///
    /// @param uuid   The XSLT.
    /// @param agreed Rule name to what the user agreed to.
    /// @return Its new version.
    @SuppressWarnings("unchecked")
    String rememberUnknownAgreed(final String uuid, final Map<String, String> agreed) {
        final XsltDoc doc = translationDocs.readXslt(uuid);
        final DocumentActionHandler<XsltDoc> handler = documentAccess.handler(XsltDoc.TYPE)
                .orElseThrow(() -> new ToolRefusal("Stroom has no XSLT store"));
        return handler.writeDocument(doc.copy()
                .description(KeptMapping.withBlock(doc.getDescription(), UNKNOWN_AGREED, agreed))
                .build()).getVersion();
    }

    /// The source notes kept in a build (its `<source> source notes` docs), read as one.
    ///
    /// @param build The build.
    /// @return `fields` and `events`; none when the build can't be listed (notes are optional).
    Map<String, Object> notesInBuild(final String build) {
        final List<Map<String, Object>> found = new ArrayList<>();
        try {
            for (final WriteGuard.BuildDoc doc : guard.folderContents(build)) {
                if (DocumentationDoc.TYPE.equals(doc.type()) && doc.name().endsWith(SourceNotes.NOTES_SUFFIX)) {
                    documentAccess.read(doc.docRef(), DocumentationDoc.class)
                            .map(read -> SourceNotes.readNotes(BuildRecords.bodyText(read)))
                            .ifPresent(found::add);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "No source notes read from build " + build + ": " + e.getMessage());
            found.clear();
        }
        return SourceNotes.merged(found);
    }

    /// @param mapping A mapping.
    /// @param imports The imports it is to have.
    /// @return It with them.
    static TranslationMapping withImports(final TranslationMapping mapping, final List<String> imports) {
        return new TranslationMapping(mapping.input(), mapping.root(), mapping.record(), mapping.xmlNamespace(),
                mapping.jsonLayout(), mapping.jsonFields(), mapping.xmlFields(), mapping.pairs(), mapping.extract(),
                mapping.forEach(), mapping.nilValues(), mapping.dropWhen(), mapping.common(), mapping.events(),
                mapping.unmatched(), mapping.style(), imports, mapping.shared(), mapping.functions());
    }

    /// @param result A generator's result ([#generated]).
    /// @return Its problems, open to more.
    @SuppressWarnings("unchecked")
    static List<String> problems(final Map<String, Object> result) {
        return (List<String>) result.get("problems");
    }

    /// @param result A generator's result ([#generated]).
    /// @return Its warnings, open to more.
    @SuppressWarnings("unchecked")
    static List<String> warnings(final Map<String, Object> result) {
        return (List<String>) result.get("warnings");
    }

    // The translation mapping kept with an XSLT, or why there is none.
    private KeptMapping.Kept keptOrRefuse(final String uuid, final String otherwise) {
        final XsltDoc doc = translationDocs.readXslt(uuid);
        final Optional<KeptMapping.Kept> kept = KeptMapping.readMapping(doc.getDescription())
                .filter(found -> KeptMapping.TRANSLATION.equals(found.kind())
                                 && JsonValues.truthy(found.payload().get("mapping")));
        if (kept.isPresent()) {
            return kept.get();
        }
        throw new ToolRefusal(translationDocs.lostMapping(new DocRef(XsltDoc.TYPE, uuid, doc.getName()),
                doc.getDescription()).orElse(otherwise));
    }

    // The build's XSLT of that name.
    private Optional<String> xsltNamed(final String build, final String name) {
        try {
            return guard.folderContents(build).stream()
                    .filter(doc -> XsltDoc.TYPE.equals(doc.type()) && doc.name().equals(name))
                    .map(WriteGuard.BuildDoc::uuid)
                    .findFirst();
        } catch (final RuntimeException e) {
            // No such build yet: nothing saved in it.
            return Optional.empty();
        }
    }

    private static Map<String, Object> open(final Map<String, Object> generated) {
        final Map<String, Object> result = new LinkedHashMap<>(generated);
        result.put("problems", new ArrayList<>(NullSafe.list(problems(generated))));
        result.put("warnings", new ArrayList<>(NullSafe.list(warnings(generated))));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copy(final Map<?, ?> map) {
        return PyJson.loads(PyJson.dumps(map)) instanceof final Map<?, ?> copied
                ? (Map<String, Object>) copied
                : new LinkedHashMap<>((Map<String, Object>) map);
    }


    // --------------------------------------------------------------------------------


    /// The mapping changes= apply to.
    ///
    /// @param mapping The mapping, as JSON maps and lists.
    /// @param basedOn What it was, when it was the session's draft ([#ON_THE_DRAFT]), else null.
    record Base(Map<String, Object> mapping, String basedOn) {

    }

    /// A mapping to regenerate from.
    ///
    /// @param mapping       The mapping, as JSON maps and lists.
    /// @param schemaVersion The event-logging version it was saved for, or null.
    record Kept(Map<String, Object> mapping, String schemaVersion) {

    }
}
