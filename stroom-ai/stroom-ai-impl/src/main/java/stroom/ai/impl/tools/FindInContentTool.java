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

package stroom.ai.impl.tools;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.AgentInstructions.Ignored;
import stroom.ai.impl.tools.AgentInstructions.Standing;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SafePattern;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.Word;
import stroom.dictionary.shared.WordList;
import stroom.docref.DocRef;
import stroom.docstore.api.ContentIndex;
import stroom.docstore.api.ContentIndexable;
import stroom.explorer.shared.DocContentMatch;
import stroom.explorer.shared.FindInContentRequest;
import stroom.explorer.shared.StringMatch;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/// `find_in_content` - finds text in the content of Stroom documents (e.g. XSLTs, text converters,
/// dictionaries), as the user, returning each matching document with its matching lines.
///
/// - Given UUIDs (e.g. those document_links found), it reads just those documents.
/// - Given types, it searches Find in Content's index if it is ready. The index is built in full the first time
///   it is needed, which can take minutes, so if it isn't ready the tool reads the documents of those types
///   directly, and starts building the index in the background for later searches.
///
/// Every document is checked for View before it is read, as reading a document without View silently gives
/// nothing. Matches are always confirmed against the document's own text with the ReDoS-safe patterns the
/// workspace tools use. Dictionaries are searched with their imports, as the combined word list.
class FindInContentTool extends AbstractWorkspaceTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FindInContentTool.class);

    static final String NAME = "find_in_content";
    static final String PATTERN_ARG = "pattern";
    static final String REGEX_ARG = "regex";
    static final String MATCH_CASE_ARG = "matchCase";
    static final String UUIDS_ARG = "uuids";
    static final String TYPES_ARG = "types";
    static final String LIMIT_ARG = "limit";
    static final String INCLUDE_IGNORED_ARG = "includeIgnored";
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;
    /// The most matching lines shown for each document.
    static final int MAX_LINES_PER_DOC = 3;
    /// The most documents read directly in one call.
    static final int MAX_DOCS_READ = 5_000;
    /// The most documents the index is asked for.
    static final int MAX_INDEX_RESULTS = 1_000;
    static final int MAX_MATCH_CHARS = 200;
    static final String HOW_UUIDS = "read the documents given";
    static final String HOW_INDEX = "searched the content index";
    static final String HOW_DIRECT = "read the documents directly, as the content index isn't ready yet (it is "
                                     + "now being built, so later searches will be quicker)";

    // Only one background build of the index is started at a time.
    private static final AtomicBoolean WARMING = new AtomicBoolean();

    private final Provider<ContentIndex> contentIndexProvider;
    private final Provider<Set<ContentIndexable>> contentIndexablesProvider;
    private final Provider<WordListProvider> wordListProviderProvider;
    private final DocumentAccess access;
    private final AgentInstructions agentInstructions;
    private final AiAuditLog auditLog;

    @Inject
    FindInContentTool(final ChatWorkspace workspace,
                      final Provider<ContentIndex> contentIndexProvider,
                      final Provider<Set<ContentIndexable>> contentIndexablesProvider,
                      final Provider<WordListProvider> wordListProviderProvider,
                      final DocumentAccess access,
                      final AgentInstructions agentInstructions,
                      final AiAuditLog auditLog) {
        super(workspace);
        this.contentIndexProvider = contentIndexProvider;
        this.contentIndexablesProvider = contentIndexablesProvider;
        this.wordListProviderProvider = wordListProviderProvider;
        this.access = access;
        this.agentInstructions = agentInstructions;
        this.auditLog = auditLog;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CORE;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Finds text in the content of Stroom documents (e.g. XSLTs, text converters, "
                                 + "dictionaries with their imports), returning each matching document's type, name, "
                                 + "UUID and folder with its matching lines. Give uuids to search just those "
                                 + "documents (quickest")
                        .chatOnly(", e.g. those document_links found")
                        .general("), or types to search every document of those types you can view. Says how it "
                                 + "searched and how many documents it read. Documents in folders an AGENTS doc says "
                                 + "to ignore (e.g. an Archive) are left out.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PATTERN_ARG, "The text to find.")
                        .addBooleanProperty(REGEX_ARG, "True if pattern is a regular expression.")
                        .addBooleanProperty(MATCH_CASE_ARG, "True to match case (default false).")
                        .addProperty(UUIDS_ARG, stringArraySchema("The documents to search."))
                        .addProperty(TYPES_ARG, stringArraySchema("The document types to search, e.g. "
                                                                     + "['XSLT', 'TextConverter'], if no uuids "
                                                                     + "are given."))
                        .addBooleanProperty(INCLUDE_IGNORED_ARG, "Optional. True to search ignored folders too, "
                                                                 + "only when the user asks for them.")
                        .addIntegerProperty(LIMIT_ARG, "The most matching documents to return (default "
                                                       + DEFAULT_LIMIT + ", at most " + MAX_LIMIT + ").")
                        .addStringProperty(OUT_ARG, ToolDescription.builder()
                                .chatOnly("A file in /outputs/ to write all the matching lines to.")
                                .build())
                        .required(PATTERN_ARG)
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final long startNanos = System.nanoTime();
        final WorkspacePath out = outArg(request);
        final String text = rawArg(request, PATTERN_ARG);
        final boolean regex = booleanArg(request, REGEX_ARG, false);
        final boolean matchCase = booleanArg(request, MATCH_CASE_ARG, false);
        final SafePattern pattern = SafePattern.of(text, regex, !matchCase);
        final List<String> uuids = stringListArg(request, UUIDS_ARG);
        final List<String> types = stringListArg(request, TYPES_ARG);
        if (uuids.isEmpty() && types.isEmpty()) {
            throw new WorkspaceException("Give uuids or types to search.");
        }
        final Integer limitArg = intArg(request, LIMIT_ARG);
        final int limit = limitArg == null
                ? DEFAULT_LIMIT
                : Math.max(1, Math.min(limitArg, MAX_LIMIT));
        final Map<String, ContentIndexable> indexables = indexables();
        // Stop reading before the call's time limit, to return what was found.
        final Standing standing = agentInstructions.load(context.session(), "Standing instructions read by the " + NAME
                                                                 + " tool");
        final Search search = new Search(pattern, limit, indexables, startNanos + timeLimit(context).toNanos() * 3 / 4,
                booleanArg(request, INCLUDE_IGNORED_ARG, false)
                        ? null
                        : standing);

        final List<String> notFound = new ArrayList<>();
        final String how;
        if (!uuids.isEmpty()) {
            how = HOW_UUIDS;
            for (final String uuid : uuids) {
                final Optional<DocRef> docRef = access.findDocRef(uuid);
                if (docRef.isPresent()) {
                    search.search(docRef.get());
                } else {
                    notFound.add(uuid);
                }
            }
        } else {
            final List<ContentIndexable> typeIndexables = typeIndexables(types, indexables);
            how = searchTypes(text, regex, matchCase, typeIndexables, search);
        }

        final List<String> lines = new ArrayList<>();
        search.matches.forEach(match -> lines.addAll(match.lines(access)));
        logSearch(context, text, uuids, types, how, search);

        final StringBuilder sb = new StringBuilder();
        sb.append(search.matches.size()).append(" document(s) matched '").append(text).append("'. It ")
                .append(how).append(", and read ").append(search.read).append(" document(s).");
        if (search.full) {
            sb.append(" It stopped at the limit of ").append(limit).append(" matching documents; give a larger "
                                                                           + "limit or narrow the search.");
        }
        if (search.outOfTime) {
            sb.append(" It ran out of time before reading them all; narrow the search, e.g. with "
                      + "document_links, to read the rest.");
        }
        if (search.tooMany) {
            sb.append(" It stopped after reading ").append(MAX_DOCS_READ).append(" documents.");
        }
        if (search.ignored > 0) {
            sb.append(" Left out ").append(search.ignored).append(" document(s) in folders an ")
                    .append(AgentInstructions.DOC_NAME).append(" doc says to ignore: ")
                    .append(String.join(", ", search.ignoredBy)).append("; pass ").append(INCLUDE_IGNORED_ARG)
                    .append(" only if the user asks for them.");
        }
        if (!notFound.isEmpty()) {
            sb.append(" Not found, or you don't have permission to view them: ").append(String.join(", ", notFound))
                    .append('.');
        }
        if (out != null) {
            workspace.write(out, String.join("\n", lines) + "\n", null);
            sb.append(" Wrote the matching lines to ").append(out).append('.');
        }
        if (!lines.isEmpty()) {
            sb.append("\n\n").append(boundedLines(context, lines, lines.size()));
        }
        return ToolResult.text(sb.toString());
    }

    private Map<String, ContentIndexable> indexables() {
        final Map<String, ContentIndexable> indexables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (final ContentIndexable indexable : NullSafe.set(contentIndexablesProvider.get())) {
            indexables.put(indexable.getType(), indexable);
        }
        return indexables;
    }

    private static List<ContentIndexable> typeIndexables(final List<String> types,
                                                         final Map<String, ContentIndexable> indexables) {
        final List<ContentIndexable> list = new ArrayList<>();
        for (final String type : types) {
            final ContentIndexable indexable = indexables.get(type);
            if (indexable == null) {
                throw new WorkspaceException("'" + type + "' isn't a type of document with content that can be "
                                             + "searched. The types are: "
                                             + String.join(", ", indexables.keySet()) + ".");
            }
            list.add(indexable);
        }
        return list;
    }

    /// Searches every document of some types: through the index if it is ready, else by reading them.
    ///
    /// @return How it searched.
    private String searchTypes(final String text,
                               final boolean regex,
                               final boolean matchCase,
                               final List<ContentIndexable> typeIndexables,
                               final Search search) {
        final Set<String> typeNames = typeIndexables.stream()
                .map(ContentIndexable::getType)
                .collect(Collectors.toSet());
        final ContentIndex contentIndex = contentIndexProvider.get();
        if (isReady(contentIndex)) {
            try {
                final StringMatch filter = regex
                        ? StringMatch.regex(text, matchCase)
                        : StringMatch.contains(text, matchCase);
                final ResultPage<DocContentMatch> page = contentIndex.findInContent(
                        new FindInContentRequest(new PageRequest(0, MAX_INDEX_RESULTS), null, filter));
                final Set<DocRef> candidates = new LinkedHashSet<>();
                for (final DocContentMatch match : NullSafe.list(NullSafe.get(page, ResultPage::getValues))) {
                    if (match.getDocRef() != null && typeNames.contains(match.getDocRef().getType())) {
                        candidates.add(match.getDocRef());
                    }
                }
                for (final DocRef candidate : candidates) {
                    if (!search.search(candidate)) {
                        break;
                    }
                }
                return HOW_INDEX;
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to search the content index: " + e.getMessage(), e);
                search.reset();
            }
        } else {
            warm(contentIndex);
        }
        for (final ContentIndexable indexable : typeIndexables) {
            final List<DocRef> docRefs = NullSafe.set(indexable.listDocuments()).stream()
                    .sorted(Comparator.comparing(docRef -> NullSafe.string(docRef.getName())))
                    .toList();
            for (final DocRef docRef : docRefs) {
                if (!search.search(docRef)) {
                    return HOW_DIRECT;
                }
            }
        }
        return HOW_DIRECT;
    }

    private static boolean isReady(final ContentIndex contentIndex) {
        try {
            return contentIndex != null && contentIndex.isReady();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to tell if the content index is ready: " + e.getMessage(), e);
            return false;
        }
    }

    /// Starts building the content index in the background, without waiting for it. A search starts the build;
    /// this one looks for text that can't match, so it reads nothing.
    private static void warm(final ContentIndex contentIndex) {
        if (contentIndex == null || !WARMING.compareAndSet(false, true)) {
            return;
        }
        Thread.ofVirtual().name("Ask Stroom AI content index warm-up").start(() -> {
            try {
                contentIndex.findInContent(new FindInContentRequest(PageRequest.oneRow(), null,
                        StringMatch.contains(UUID.randomUUID().toString(), true)));
            } catch (final RuntimeException e) {
                // Expected while the index is being built.
                LOGGER.debug(() -> "Started building the content index: " + e.getMessage());
            } finally {
                WARMING.set(false);
            }
        });
    }

    private void logSearch(final ToolCallContext context,
                           final String text,
                           final List<String> uuids,
                           final List<String> types,
                           final String how,
                           final Search search) {
        final List<DocRef> found = search.matches.stream()
                .map(Match::docRef)
                .toList();
        final String scope = uuids.isEmpty()
                ? "documents of type " + String.join(", ", types)
                : uuids.size() + " document(s) given";
        final Map<String, String> data = new LinkedHashMap<>();
        data.put("DocumentsRead", String.valueOf(search.read));
        data.put("How", how);
        auditLog.documentsSearched(context.session(), "'" + text + "' in " + scope, found, data,
                "Text found in the content of documents by the " + NAME + " tool");
    }

    @Override
    public boolean readsUntrustedContent() {
        // Document content is written by users.
        return true;
    }

    /// A document that matched, with its matching lines.
    ///
    /// @param docRef The document.
    /// @param lines  The matching lines, each with its part and line number.
    /// @param more   How many more lines matched.
    record Match(DocRef docRef, List<String> lines, int more) {

        /// @param access Where to find the document's folder.
        /// @return The match as lines for the model: the document, then its matching lines.
        List<String> lines(final DocumentAccess access) {
            final List<String> result = new ArrayList<>();
            result.add("- " + docRef.getType() + " '" + NullSafe.string(docRef.getName()) + "' ("
                       + docRef.getUuid() + ") in " + access.path(docRef) + ":");
            lines.forEach(line -> result.add("    " + line));
            if (more > 0) {
                result.add("    (" + more + " more matching line(s))");
            }
            return result;
        }
    }

    /// One search: reads documents, matches them, and counts.
    private final class Search {

        private final SafePattern pattern;
        private final int limit;
        private final Map<String, ContentIndexable> indexables;
        private final long stopNanos;
        private final List<Match> matches = new ArrayList<>();
        private final Set<String> seen = new LinkedHashSet<>();
        // The AGENTS docs whose ignored folders are left out, or null to search them too.
        private final Standing standing;
        private final Set<String> ignoredBy = new LinkedHashSet<>();
        private int ignored;
        private int read;
        private boolean full;
        private boolean outOfTime;
        private boolean tooMany;

        private Search(final SafePattern pattern,
                       final int limit,
                       final Map<String, ContentIndexable> indexables,
                       final long stopNanos,
                       final Standing standing) {
            this.pattern = pattern;
            this.limit = limit;
            this.indexables = indexables;
            this.stopNanos = stopNanos;
            this.standing = standing;
        }

        private void reset() {
            matches.clear();
            seen.clear();
            ignoredBy.clear();
            ignored = 0;
            read = 0;
            full = false;
        }

        /// Reads and matches a document, if the user can view it.
        ///
        /// @return False if the search should stop.
        private boolean search(final DocRef docRef) {
            if (matches.size() >= limit) {
                full = true;
                return false;
            }
            if (System.nanoTime() - stopNanos > 0) {
                outOfTime = true;
                return false;
            }
            if (read >= MAX_DOCS_READ) {
                tooMany = true;
                return false;
            }
            ToolDeadline.check();
            if (docRef == null || !seen.add(docRef.getUuid()) || !access.canView(docRef)) {
                return true;
            }
            if (standing != null && standing.ignoresAny()) {
                final Optional<Ignored> ignoring = standing.ignored(agentInstructions.folderOf(docRef));
                if (ignoring.isPresent()) {
                    ignored++;
                    ignoredBy.add(ignoring.get().describe());
                    return true;
                }
            }
            final Map<String, List<String>> parts = read(docRef);
            read++;
            final List<String> lines = new ArrayList<>();
            int count = 0;
            for (final Map.Entry<String, List<String>> part : parts.entrySet()) {
                final List<String> partLines = part.getValue();
                for (int i = 0; i < partLines.size(); i++) {
                    final String line = partLines.get(i);
                    if (pattern.find(line)) {
                        count++;
                        if (lines.size() < MAX_LINES_PER_DOC) {
                            lines.add(part.getKey() + " line " + (i + 1) + ": " + shorten(line.strip()));
                        }
                    }
                }
            }
            if (count > 0) {
                matches.add(new Match(docRef, lines, count - lines.size()));
            }
            return true;
        }

        /// @return The document's text, as lines, by part.
        private Map<String, List<String>> read(final DocRef docRef) {
            final Map<String, List<String>> parts = new LinkedHashMap<>();
            try {
                if (DictionaryDoc.TYPE.equals(docRef.getType())) {
                    final WordList wordList = wordListProviderProvider.get().getCombinedWordList(docRef);
                    final List<String> words = new ArrayList<>();
                    for (final Word word : NullSafe.list(NullSafe.get(wordList, WordList::getWordList))) {
                        final String source = wordList.getSource(word)
                                .filter(sourceRef -> !docRef.getUuid().equals(sourceRef.getUuid()))
                                .map(sourceRef -> " (from Dictionary '" + sourceRef.getName() + "')")
                                .orElse("");
                        words.add(word.getWord() + source);
                    }
                    parts.put("words", words);
                    return parts;
                }
                final ContentIndexable indexable = indexables.get(docRef.getType());
                if (indexable == null) {
                    return parts;
                }
                new TreeMap<>(NullSafe.map(indexable.getIndexableData(docRef))).forEach((part, text) ->
                        parts.put(part, NullSafe.string(text).lines().toList()));
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to read " + docRef + ": " + e.getMessage(), e);
            }
            return parts;
        }

        private static String shorten(final String line) {
            return line.length() > MAX_MATCH_CHARS
                    ? line.substring(0, MAX_MATCH_CHARS) + "…"
                    : line;
        }
    }
}
