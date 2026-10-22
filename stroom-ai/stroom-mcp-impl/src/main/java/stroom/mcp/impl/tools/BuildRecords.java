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
import stroom.ai.impl.tools.MergedPipeline.TranslationDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.api.ExplorerService;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasData;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The build record: one Documentation doc, [#NAME], in each build's folder (ported from the Stroom MCP server's
/// `security/record.py`, in the same format, so a record either server wrote reads in the other).
///
/// It holds what is remembered about the build's documents between calls: which are working copies of production
/// documents (and of which), the code digests that stepped clean, passed index verification or gave Events that
/// passed check_events (what promotion checks for), and the feeds whose samples are files on the user's disk.
///
/// Earlier versions kept these as explorer tags (`mcp-build-<build>`, `mcp-copy-of-<uuid>`,
/// `mcp-stepped-<digest>`, ...). Tags are what people filter the explorer by, and those grew Stroom's tag list with
/// every build and every change of code; only mcp-generated and mcp-managed (and mcp-kept-mapping on XSLTs) are
/// tags now. Like the tags, the record is seen by every node and survives restarts. Two writing it at once lose
/// nothing: Stroom refuses a save of a doc changed since it was read, and the change is made again on the newer
/// doc. A build's old tags are folded into its record the first time it is read, then removed.
///
/// A record is `{"docs": {uuid: entry}}`, with `source_feeds` once a build works from existing feeds; an entry may
/// hold `type`, `name`, `copy_of`, `version_of`, `stepped`, `verified`, `validated` (lists of digests),
/// `stepped_streams` and `sample_files`.
@Singleton
public class BuildRecords {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BuildRecords.class);

    /// The record's name in each build folder.
    public static final String NAME = "Build record";
    /// Clean steps (by the digest of the code that stepped clean).
    public static final String STEPPED = "stepped";
    /// Passed index verifications.
    public static final String VERIFIED = "verified";
    /// Events that passed check_events.
    public static final String VALIDATED = "validated";
    /// What the record keeps digests of.
    public static final List<String> KINDS = List.of(STEPPED, VERIFIED, VALIDATED);
    /// What a record holds the entry of each document in.
    public static final String DOCS = "docs";
    /// What a record holds the existing feeds a build works from in.
    public static final String SOURCE_FEEDS = "source_feeds";

    static final Pattern FENCE = Pattern.compile("```json build-record\\n(.*?)\\n```", Pattern.DOTALL);
    // The tags earlier versions kept these in, and the entry each one fills (null: the build, now the folder it is
    // in).
    private static final Map<String, String> LEGACY = legacy();
    private static final int ATTEMPTS = 5;

    private final Provider<WriteGuard> guardProvider;
    private final DocumentAccess documentAccess;
    private final Provider<ExplorerService> explorerServiceProvider;
    private final PipelineShapes pipelineShapes;

    /// @param guardProvider           Finds the build's documents and creates its record.
    /// @param documentAccess          Reads and writes the record, as the user.
    /// @param explorerServiceProvider Deletes a record once it holds nothing.
    /// @param pipelineShapes          Reads the code a pipeline runs, for its fingerprint.
    @Inject
    public BuildRecords(final Provider<WriteGuard> guardProvider,
                        final DocumentAccess documentAccess,
                        final Provider<ExplorerService> explorerServiceProvider,
                        final PipelineShapes pipelineShapes) {
        this.guardProvider = guardProvider;
        this.documentAccess = documentAccess;
        this.explorerServiceProvider = explorerServiceProvider;
        this.pipelineShapes = pipelineShapes;
    }

    /// @param build A build's name.
    /// @return The build's record, empty (`{"docs": {}}`) when it has none; old tags folded in first.
    public Map<String, Object> load(final String build) {
        final List<WriteGuard.BuildDoc> contents = guardProvider.get().folderContents(build, true);
        final Optional<WriteGuard.BuildDoc> found = recordDoc(contents);
        final Map<WriteGuard.BuildDoc, List<String>> old = new LinkedHashMap<>();
        contents.forEach(doc -> {
            final List<String> legacy = legacyTags(doc.tags());
            if (!legacy.isEmpty()) {
                old.put(doc, legacy);
            }
        });
        if (!old.isEmpty()) {
            final Map<String, Object> state = save(build, found.orElse(null), record ->
                    old.forEach((doc, tags) -> {
                        final Map<String, Object> entry = entryOf(record, doc.docRef());
                        tags.forEach(tag -> fold(entry, tag));
                    }));
            old.forEach((doc, tags) -> guardProvider.get().untag(List.of(doc.docRef()), new LinkedHashSet<>(tags)));
            return state;
        }
        return found.map(doc -> parse(bodyText(read(doc.docRef()))))
                .orElseGet(BuildRecords::empty);
    }

    /// @param build A build's name, or null.
    /// @param uuid  A document's UUID.
    /// @return The document's entry in its build's record: empty when it has none, or `build` is null.
    public Map<String, Object> entry(final String build, final String uuid) {
        if (NullSafe.isBlankString(build)) {
            return new LinkedHashMap<>();
        }
        return docs(load(build)).get(uuid) instanceof final Map<?, ?> found
                ? asMap(found)
                : new LinkedHashMap<>();
    }

    /// Changes one document's entry and saves the record (made if the build has none).
    ///
    /// @param build  A build's name.
    /// @param doc    The document.
    /// @param change Changes its entry in place.
    /// @return The record as saved.
    public Map<String, Object> update(final String build,
                                      final DocRef doc,
                                      final Consumer<Map<String, Object>> change) {
        final Optional<WriteGuard.BuildDoc> found = recordDoc(guardProvider.get().folderContents(build, true));
        return save(build, found.orElse(null), state -> change.accept(entryOf(state, doc)));
    }

    /// Records existing feeds the build works from (start_build feeds=...): the build then builds on their data,
    /// not on samples it uploads, and promotion's filters name them, not a test feed.
    ///
    /// @param build A build's name.
    /// @param feeds The feeds' names.
    /// @return The record as saved.
    public Map<String, Object> addSourceFeeds(final String build, final List<String> feeds) {
        final Optional<WriteGuard.BuildDoc> found = recordDoc(guardProvider.get().folderContents(build, true));
        return save(build, found.orElse(null), state -> {
            final Set<String> all = new LinkedHashSet<>(stringList(state.get(SOURCE_FEEDS)));
            all.addAll(feeds);
            state.put(SOURCE_FEEDS, new ArrayList<>(all));
        });
    }

    /// Drops documents' entries (promoted out of the build); the record goes once it holds nothing.
    ///
    /// @param build A build's name.
    /// @param uuids The documents' UUIDs.
    public void forget(final String build, final Collection<String> uuids) {
        final Optional<WriteGuard.BuildDoc> found = recordDoc(guardProvider.get().folderContents(build, true));
        if (found.isEmpty()) {
            return;
        }
        final Map<String, Object> state = parse(bodyText(read(found.get().docRef())));
        if (uuids.containsAll(docs(state).keySet())) {
            documentAccess.node(found.get().docRef()).ifPresent(node ->
                    explorerServiceProvider.get().delete(List.of(node)));
            return;
        }
        save(build, found.get(), record -> uuids.forEach(docs(record)::remove));
    }

    /// Per element the pipeline sets its own code for, a hash of the code that runs: the draft where one is
    /// given, else the saved XSLT or text converter. Not the version history, which isn't code: a draft stepped
    /// clean is still clean once saved, its history added to it.
    ///
    /// @param pipelineUuid A pipeline.
    /// @param draftCode    Draft code by element, or null.
    /// @return Each element's code digest.
    /// @throws PipelineUnreadableException if Stroom can't load the pipeline.
    public Map<String, String> codeFingerprint(final String pipelineUuid, final Map<String, String> draftCode) {
        final Map<String, String> prints = new LinkedHashMap<>();
        for (final TranslationDoc doc : pipelineShapes.merged(pipelineUuid).translationDocs(pipelineUuid)) {
            if (doc.inheritedFromTemplate()) {
                continue;
            }
            String code = NullSafe.map(draftCode).get(doc.element());
            if (code == null) {
                code = documentAccess.read(doc.doc())
                        .filter(HasData.class::isInstance)
                        .map(found -> NullSafe.string(((HasData) found).getData()))
                        .orElse("");
            }
            prints.put(doc.element(), XsltCode.sha256(XsltCode.strip(code)));
        }
        return prints;
    }

    /// @param prints Each element's code digest, from [#codeFingerprint].
    /// @return The fingerprint's digest, as the record keeps it.
    public static String fingerprintDigest(final Map<String, String> prints) {
        return XsltCode.sha256(PythonJson.dumps(prints)).substring(0, 16);
    }

    /// Records that the pipeline's code (or the draft stepped) passed. Only pipelines the tools manage (in a build)
    /// are recorded: stepping anything else stays read-only. Each kind keeps only what can still matter: the new
    /// record, and the one for the code saved now (a draft stepped clean is recorded beside it, for when it is
    /// saved).
    ///
    /// @param pipeline  The pipeline.
    /// @param kind      One of [#KINDS].
    /// @param draftCode The draft code stepped, by element, or null for the saved code.
    /// @param extra     More to put in its entry (e.g. `stepped_streams`), or null.
    /// @return `recorded`; empty for a pipeline outside a build (never recorded); else why it couldn't be saved.
    public Optional<String> recordPassed(final DocRef pipeline,
                                         final String kind,
                                         final Map<String, String> draftCode,
                                         final Map<String, Object> extra) {
        final WriteGuard guard = guardProvider.get();
        try {
            if (!guard.tags(pipeline).contains(WriteGuard.MANAGED)) {
                return Optional.empty();
            }
            final String build = guard.buildOf(pipeline).orElseThrow(() -> new ToolRefusal(
                    "it is managed by the server but not in a build folder (moved out of the workspace?)"));
            final String saved = fingerprintDigest(codeFingerprint(pipeline.getUuid(), null));
            final String digest = draftCode != null
                    ? fingerprintDigest(codeFingerprint(pipeline.getUuid(), draftCode))
                    : saved;
            update(build, pipeline, entry -> {
                final Set<String> kept = new TreeSet<>();
                stringList(entry.get(kind)).stream()
                        .filter(saved::equals)
                        .forEach(kept::add);
                kept.add(digest);
                entry.put(kind, new ArrayList<>(kept));
                entry.putAll(NullSafe.map(extra));
            });
            return Optional.of("recorded");
        } catch (final RuntimeException e) {
            // The record is a convenience; never fail the step over it, but say so.
            LOGGER.warn(() -> "Couldn't record " + kind + " on pipeline " + pipeline.getUuid() + ": "
                              + e.getMessage());
            return Optional.of("Passed, but the record of it couldn't be saved in the build's record ("
                               + shorten(e.getMessage(), 160) + "): promotion will report it as not done.");
        }
    }

    /// @param pipeline A pipeline.
    /// @param kind     One of [#KINDS].
    /// @return Whether the pipeline's saved code is code that passed (on any node, or in the Stroom MCP server).
    public boolean passed(final DocRef pipeline, final String kind) {
        final WriteGuard guard = guardProvider.get();
        final List<String> mine = stringList(entry(guard.buildOf(pipeline).orElse(null), pipeline.getUuid())
                .get(kind));
        if (mine.isEmpty()) {
            return false;
        }
        return mine.contains(fingerprintDigest(codeFingerprint(pipeline.getUuid(), null)));
    }

    /// @param pipeline A pipeline.
    /// @return Whether its saved code has stepped clean.
    public boolean steppedClean(final DocRef pipeline) {
        return passed(pipeline, STEPPED);
    }

    /// @param pipeline An indexing pipeline.
    /// @return Whether verify_index passed for its current code.
    public boolean verified(final DocRef pipeline) {
        return passed(pipeline, VERIFIED);
    }

    /// @param pipeline A pipeline.
    /// @return Whether Events from its current code passed check_events.
    public boolean validated(final DocRef pipeline) {
        return passed(pipeline, VALIDATED);
    }

    /// @return An empty record.
    public static Map<String, Object> empty() {
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put(DOCS, new LinkedHashMap<String, Object>());
        return state;
    }

    /// @param text A record doc's text, or null.
    /// @return The record its fenced JSON holds, or an empty one if it holds none (or one that doesn't parse).
    public static Map<String, Object> parse(final String text) {
        final Matcher matcher = FENCE.matcher(NullSafe.string(text));
        if (!matcher.find()) {
            return empty();
        }
        try {
            final Object state = JsonUtil.readValue(matcher.group(1), Object.class);
            if (state instanceof final Map<?, ?> map && map.get(DOCS) instanceof Map<?, ?>) {
                return asMap(map);
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "A build record that doesn't parse: " + e.getMessage());
        }
        return empty();
    }

    /// @param tags A document's tags.
    /// @return Those an earlier version kept the record in.
    public static List<String> legacyTags(final Collection<String> tags) {
        return NullSafe.collection(tags).stream()
                .filter(tag -> LEGACY.keySet().stream().anyMatch(tag::startsWith))
                .toList();
    }

    /// Puts what an old tag recorded into the doc's entry.
    ///
    /// @param entry A document's entry.
    /// @param tag   One of its [#legacyTags].
    public static void fold(final Map<String, Object> entry, final String tag) {
        final String prefix = LEGACY.keySet().stream()
                .filter(tag::startsWith)
                .findFirst()
                .orElseThrow();
        final String key = LEGACY.get(prefix);
        if ("copy_of".equals(key)) {
            entry.putIfAbsent("copy_of", tag.substring(prefix.length()));
        } else if ("sample_files".equals(key)) {
            entry.put("sample_files", true);
        } else if (key != null && KINDS.contains(key)) {
            // 'mcp-stepped-<digest>', or the older 'mcp-stepped-<UTC time>-<digest>'.
            final String digest = tag.substring(tag.lastIndexOf('-') + 1);
            final Set<String> digests = new TreeSet<>(stringList(entry.get(key)));
            digests.add(digest);
            entry.put(key, new ArrayList<>(digests));
        }
    }

    /// @param build A build's name.
    /// @param state Its record.
    /// @return The record doc's text: a table people can read, then the record as fenced JSON.
    public static String render(final String build, final Map<String, Object> state) {
        final List<String> lines = new ArrayList<>(List.of(
                "# " + NAME + ": " + build, "",
                "What the MCP server remembers about this build's documents: which are working copies of production "
                + "documents, and the code (by digest) that stepped clean, passed index verification, or gave Events "
                + "that passed check_events, which promotion checks for. The server writes it; don't edit it. Don't "
                + "move or rename it or anything else in this folder either: the server finds a build's documents "
                + "by the folder they are in. Promotion removes it.", "",
                "| Document | Type | Working copy of | Stepped clean | Index verified | Events checked "
                + "| Sample files |",
                "| --- | --- | --- | --- | --- | --- | --- |"));
        final List<Entry<String, Object>> entries = new ArrayList<>(docs(state).entrySet());
        entries.sort(Comparator.comparing((Entry<String, Object> e) -> NullSafe.string(text(e, "type")))
                .thenComparing(e -> NullSafe.string(text(e, "name"))));
        for (final Entry<String, Object> e : entries) {
            final Map<String, Object> entry = e.getValue() instanceof final Map<?, ?> map
                    ? asMap(map)
                    : Map.of();
            lines.add("| " + NullSafe.nonBlankStringElse(text(e, "name"), e.getKey()) + " | "
                      + NullSafe.string(text(e, "type")) + " | "
                      + NullSafe.nonBlankStringElse(NullSafe.get(entry.get("copy_of"), String::valueOf), "-")
                      + " | " + digests(entry, STEPPED) + " | " + digests(entry, VERIFIED) + " | "
                      + digests(entry, VALIDATED) + " | " + (truthy(entry.get("sample_files"))
                              ? "yes"
                              : "-") + " |");
        }
        final List<String> sources = stringList(state.get(SOURCE_FEEDS));
        if (!sources.isEmpty()) {
            lines.add("");
            lines.add("Works from existing feeds: " + String.join(", ", sources) + ".");
        }
        lines.addAll(List.of("", "## Record", "", "Read by the server; do not edit.", "",
                "```json build-record", PythonJson.dumps(state, 1), "```", ""));
        return String.join("\n", lines);
    }

    /// @param doc A Documentation doc.
    /// @return Its text: its body (data), which the Stroom UI shows and edits, else its Documentation tab, where
    /// the Stroom MCP server 0.6.0 and earlier wrote it, and where people sometimes write.
    public static String bodyText(final DocumentationDoc doc) {
        if (doc == null) {
            return "";
        }
        return NullSafe.isNonEmptyString(doc.getData())
                ? doc.getData()
                : documentationTab(doc);
    }

    /// @param doc  A Documentation doc.
    /// @param text Its new body.
    /// @return It with the body written. Text an earlier version put in the Documentation tab instead (the body
    /// empty) is moved, not left behind as a stale copy.
    public static DocumentationDoc withBodyText(final DocumentationDoc doc, final String text) {
        final DocumentationDoc.Builder builder = doc.copy().data(text);
        if (NullSafe.isEmptyString(doc.getData()) && NullSafe.isNonEmptyString(documentationTab(doc))) {
            builder.documentation(null);
        }
        return builder.build();
    }

    // The doc's Documentation tab, which DocumentationDoc has no getter for: as Stroom serialises it.
    private static String documentationTab(final DocumentationDoc doc) {
        try {
            final Object value = JsonUtil.readValue(JsonUtil.writeValueAsString(doc, false), Map.class)
                    .get("documentation");
            return value == null
                    ? ""
                    : String.valueOf(value);
        } catch (final RuntimeException e) {
            return "";
        }
    }

    private Map<String, Object> save(final String build,
                                     final WriteGuard.BuildDoc found,
                                     final Consumer<Map<String, Object>> change) {
        WriteGuard.BuildDoc current = found;
        for (int attempt = 0; ; attempt++) {
            try {
                if (current == null) {
                    return guardProvider.get().createFilled(DocumentationDoc.TYPE, NAME, build, ref -> {
                        final Map<String, Object> state = empty();
                        change.accept(state);
                        write(withBodyText(read(ref), render(build, state)));
                        return state;
                    }, true, null);
                }
                final DocumentationDoc doc = read(current.docRef());
                final Map<String, Object> state = parse(bodyText(doc));
                change.accept(state);
                write(withBodyText(doc, render(build, state)));
                return state;
            } catch (final RuntimeException e) {
                // Another node (or the Stroom MCP server) saved or created it since it was read: do the change
                // again on what it saved.
                final String message = NullSafe.string(e.getMessage());
                if (attempt == ATTEMPTS - 1 || !(message.contains("modified by another user")
                                                 || message.contains("already in build"))) {
                    throw e;
                }
                LOGGER.info(() -> "Build record of " + build + " changed while writing it (" + shorten(message, 120)
                                  + "); again");
                current = recordDoc(guardProvider.get().folderContents(build, true)).orElse(null);
            }
        }
    }

    private DocumentationDoc read(final DocRef ref) {
        return documentAccess.read(ref, DocumentationDoc.class)
                .orElseThrow(() -> new ToolRefusal("The build record " + ref.getUuid() + " can't be read"));
    }

    @SuppressWarnings("unchecked")
    private void write(final DocumentationDoc doc) {
        final DocumentActionHandler<DocumentationDoc> handler = documentAccess.handler(DocumentationDoc.TYPE)
                .orElseThrow(() -> new ToolRefusal("Stroom has no Documentation store"));
        handler.writeDocument(doc);
    }

    private static Optional<WriteGuard.BuildDoc> recordDoc(final List<WriteGuard.BuildDoc> contents) {
        return contents.stream()
                .filter(doc -> DocumentationDoc.TYPE.equals(doc.type()) && NAME.equals(doc.name()))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> docs(final Map<String, Object> state) {
        final Object docs = state.get(DOCS);
        if (docs instanceof final Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        final Map<String, Object> made = new LinkedHashMap<>();
        state.put(DOCS, made);
        return made;
    }

    private static Map<String, Object> entryOf(final Map<String, Object> state, final DocRef doc) {
        final Map<String, Object> docs = docs(state);
        final Map<String, Object> entry = docs.get(doc.getUuid()) instanceof final Map<?, ?> map
                ? asMap(map)
                : new LinkedHashMap<>();
        docs.put(doc.getUuid(), entry);
        if (NullSafe.isNonEmptyString(doc.getType())) {
            entry.put("type", doc.getType());
        }
        if (NullSafe.isNonEmptyString(doc.getName())) {
            entry.put("name", doc.getName());
        }
        return entry;
    }

    // A map read from JSON (or made here), as one that can be changed, its nested maps too.
    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(final Map<?, ?> map) {
        final Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), value instanceof final Map<?, ?> nested
                ? asMap(nested)
                : value instanceof final List<?> list
                        ? new ArrayList<>((List<Object>) list)
                        : value));
        return copy;
    }

    static List<String> stringList(final Object value) {
        if (!(value instanceof final Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    private static String text(final Entry<String, Object> entry, final String key) {
        return entry.getValue() instanceof final Map<?, ?> map && map.get(key) != null
                ? String.valueOf(map.get(key))
                : null;
    }

    private static String digests(final Map<String, Object> entry, final String kind) {
        final List<String> digests = stringList(entry.get(kind)).stream()
                .map(digest -> digest.substring(0, Math.min(8, digest.length())))
                .toList();
        return digests.isEmpty()
                ? "-"
                : String.join(", ", digests);
    }

    private static boolean truthy(final Object value) {
        return value != null && !Boolean.FALSE.equals(value) && !"".equals(value);
    }

    private static String shorten(final String text, final int max) {
        final String value = NullSafe.string(text);
        return value.length() > max
                ? value.substring(0, max)
                : value;
    }

    private static Map<String, String> legacy() {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("mcp-copy-of-", "copy_of");
        map.put("mcp-stepped-", STEPPED);
        map.put("mcp-verified-", VERIFIED);
        map.put("mcp-validated-", VALIDATED);
        map.put("mcp-sample-files", "sample_files");
        map.put("mcp-build-", null);
        return Collections.unmodifiableMap(map);
    }
}
