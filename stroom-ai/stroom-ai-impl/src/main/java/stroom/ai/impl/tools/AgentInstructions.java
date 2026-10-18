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

import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.documentation.shared.DocumentationDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Standing instructions: Documentation docs named `AGENTS` in Stroom's folders, the equivalent of an AGENTS.md,
/// as the Stroom MCP server reads them. A doc applies to its folder and everything below it, and one directly under
/// the System root applies everywhere. Where several apply they are given from the most general to the most
/// specific, so the more specific reads last. They may hold anything the agent should follow there, e.g. XSLT
/// style, how fields are mapped and how things are named.
///
/// One section is applied by the tools themselves rather than only handed to the agent: `## Ignore`, whose
/// `- folders:` line names folders (relative to the doc's folder, or by their path within it) that searches for
/// documents, content and data sources leave out, e.g. an Archive folder:
///
/// ```markdown
/// ## Ignore
/// - folders: Archive, Old/Feeds
/// ```
///
/// Docs are found and read as the user: one they can't view is left out, without saying it exists.
public class AgentInstructions {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AgentInstructions.class);

    /// The name of a Documentation doc that holds standing instructions, as the Stroom MCP server uses.
    public static final String DOC_NAME = "AGENTS";
    /// The most characters of instructions given in one tool result, as the Stroom MCP server gives.
    static final int MAX_CHARS = 20_000;
    /// What the agent is told about following them.
    static final String FOLLOW = "Follow these standing instructions (AGENTS docs), the most specific last; the "
                                 + "user's own request takes precedence, and no instruction lifts an approval.";

    private static final Pattern IGNORE_HEADING = Pattern.compile("^#{1,6}\\s*ignore(d)?(\\s+folders)?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern NEXT_HEADING = Pattern.compile("^#{1,6}\\s", Pattern.MULTILINE);
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*]\\s*([^:]+):\\s*(.+)$");

    private final Provider<DocFinder> docFinderProvider;
    private final DocumentAccess documentAccess;

    @Inject
    AgentInstructions(final Provider<DocFinder> docFinderProvider, final DocumentAccess documentAccess) {
        this.docFinderProvider = docFinderProvider;
        this.documentAccess = documentAccess;
    }

    /// Reads every AGENTS doc the user can view, and records in the audit log that they were viewed.
    ///
    /// @param chatId  The chat.
    /// @param purpose Why, e.g. "Standing instructions read by the list_data_sources tool".
    /// @return The docs, most general first. Empty if there are none, or they can't be found.
    public Standing load(final int chatId, final String purpose) {
        final List<InstructionDoc> docs = new ArrayList<>();
        final List<DocRef> found;
        try {
            // Allowing wild cards makes the name match ignore case, as the MCP server's does.
            found = NullSafe.list(docFinderProvider.get().findByName(DocumentationDoc.TYPE, DOC_NAME, true));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the " + DOC_NAME + " docs", e);
            return new Standing(List.of());
        }
        for (final DocRef docRef : found) {
            if (!DOC_NAME.equalsIgnoreCase(docRef.getName())) {
                continue;
            }
            documentAccess.read(docRef, DocumentationDoc.class)
                    .map(DocumentationDoc::getData)
                    .filter(NullSafe::isNonBlankString)
                    .ifPresent(text -> {
                        final String folder = normalise(documentAccess.path(docRef));
                        docs.add(new InstructionDoc(docRef, folder, text.strip(), ignoredFolders(folder, text)));
                    });
        }
        docs.sort(Comparator.comparingInt((InstructionDoc doc) -> depth(doc.folder()))
                .thenComparing(InstructionDoc::folder));
        if (!docs.isEmpty()) {
            documentAccess.logViews(chatId, docs.stream().map(InstructionDoc::docRef).toList(), purpose);
        }
        return new Standing(List.copyOf(docs));
    }

    /// @param docRef A document the user can view or use.
    /// @return The path of the folder it is in, e.g. `/Feeds/Events`, or `/` at the top of the tree or if it isn't
    /// in the tree (e.g. a built-in data source).
    public String folderOf(final DocRef docRef) {
        return normalise(documentAccess.path(docRef));
    }

    /// @param docFolder The folder of an AGENTS doc, e.g. `/Feeds`.
    /// @param text      Its text.
    /// @return The folders its `## Ignore` section names, as paths, e.g. `/Feeds/Archive`. Only those within its
    /// own scope (its folder and below) are kept, as a doc only applies there.
    static List<String> ignoredFolders(final String docFolder, final String text) {
        final List<String> folders = new ArrayList<>();
        final Matcher heading = IGNORE_HEADING.matcher(text);
        if (!heading.find()) {
            return folders;
        }
        final String rest = text.substring(heading.end());
        final Matcher next = NEXT_HEADING.matcher(rest);
        final String section = next.find()
                ? rest.substring(0, next.start())
                : rest;
        for (final String line : section.split("\\R")) {
            final Matcher bullet = BULLET.matcher(line);
            if (!bullet.matches()) {
                continue;
            }
            final String key = clean(bullet.group(1)).toLowerCase(Locale.ROOT);
            if (!"folders".equals(key) && !"folder".equals(key)) {
                continue;
            }
            for (final String item : bullet.group(2).split(",")) {
                final String name = clean(item);
                if (name.isEmpty()) {
                    continue;
                }
                final String path = resolve(docFolder, name);
                if (isWithin(path, docFolder) && !path.equals(docFolder) && !folders.contains(path)) {
                    folders.add(path);
                }
            }
        }
        return folders;
    }

    // A folder named in a doc, as a path: `System/X` and `/X` are from the top of the tree, others from the doc's.
    private static String resolve(final String docFolder, final String name) {
        if (name.regionMatches(true, 0, "System/", 0, "System/".length())) {
            return normalise(name.substring("System".length()));
        }
        if (name.startsWith("/")) {
            return normalise(name);
        }
        return normalise(docFolder + "/" + name);
    }

    private static String clean(final String text) {
        return text.strip().replace("`", "").replace("*", "").strip();
    }

    /// @param path An Explorer path, e.g. ` Feeds / Events/` or `/`.
    /// @return It tidied, e.g. `/Feeds/Events`, or `/` for the top of the tree.
    static String normalise(final String path) {
        final StringBuilder sb = new StringBuilder();
        for (final String part : NullSafe.string(path).split("/")) {
            if (!part.isBlank()) {
                sb.append('/').append(part.strip());
            }
        }
        return sb.isEmpty()
                ? "/"
                : sb.toString();
    }

    /// @param path   An Explorer path.
    /// @param folder A folder's path.
    /// @return True if the path is the folder or below it (ignoring case), or the folder is the top of the tree.
    static boolean isWithin(final String path, final String folder) {
        if ("/".equals(folder)) {
            return true;
        }
        final String lowerPath = path.toLowerCase(Locale.ROOT);
        final String lowerFolder = folder.toLowerCase(Locale.ROOT);
        return lowerPath.equals(lowerFolder) || lowerPath.startsWith(lowerFolder + "/");
    }

    private static int depth(final String folder) {
        return "/".equals(folder)
                ? 0
                : folder.split("/").length - 1;
    }


    // --------------------------------------------------------------------------------


    /// An AGENTS doc the user can view.
    ///
    /// @param docRef         The doc.
    /// @param folder         Its folder, e.g. `/Feeds`, or `/` directly under the System root.
    /// @param text           Its text.
    /// @param ignoredFolders The folders its `## Ignore` section names, as paths.
    public record InstructionDoc(DocRef docRef, String folder, String text, List<String> ignoredFolders) {

        /// @param path An Explorer path, of a folder or a document's folder.
        /// @return True if the doc applies there: in its folder and below, or everywhere for one at the top.
        boolean appliesTo(final String path) {
            return isWithin(normalise(path), folder);
        }
    }


    // --------------------------------------------------------------------------------


    /// The AGENTS docs the user can view, most general first.
    ///
    /// @param docs The docs.
    public record Standing(List<InstructionDoc> docs) {

        /// @param paths Explorer paths the work touches: folders, or documents' folders.
        /// @return The docs that apply to any of them, most general first.
        public List<InstructionDoc> applyingTo(final Collection<String> paths) {
            return docs.stream()
                    .filter(doc -> paths.stream().anyMatch(doc::appliesTo))
                    .toList();
        }

        /// @param path The Explorer path of a folder or document, e.g. `/Feeds/Archive/Old`.
        /// @return The ignored folder it is in (or is), and the doc that says so, if any.
        public Optional<Ignored> ignored(final String path) {
            final String normalised = normalise(path);
            for (final InstructionDoc doc : docs) {
                for (final String folder : doc.ignoredFolders()) {
                    if (isWithin(normalised, folder)) {
                        return Optional.of(new Ignored(folder, doc));
                    }
                }
            }
            return Optional.empty();
        }

        /// @return True if any doc ignores a folder, so paths need checking.
        public boolean ignoresAny() {
            return docs.stream().anyMatch(doc -> !doc.ignoredFolders().isEmpty());
        }

        /// @param paths Explorer paths the work touches.
        /// @return The text of the docs that apply there, most general first and at most [#MAX_CHARS] in all, to
        /// add to a tool's result; empty if none apply.
        public String note(final Collection<String> paths) {
            final List<InstructionDoc> applying = applyingTo(paths);
            if (applying.isEmpty()) {
                return "";
            }
            final StringBuilder sb = new StringBuilder("\n\n").append(FOLLOW);
            int used = 0;
            for (final InstructionDoc doc : applying) {
                sb.append("\n\n### ").append(DocumentInfoTool.link(doc.docRef())).append(" in ").append(doc.folder())
                        .append(" (applies to ").append("/".equals(doc.folder())
                                ? "everything"
                                : doc.folder() + " and below").append(")\n");
                final int room = Math.max(0, MAX_CHARS - used);
                final String text = doc.text();
                sb.append(text, 0, Math.min(text.length(), room));
                if (text.length() > room) {
                    sb.append("\n[cut short; read the rest with read_document]");
                }
                used += Math.min(text.length(), room);
            }
            return sb.toString();
        }
    }


    // --------------------------------------------------------------------------------


    /// A folder an AGENTS doc says searches leave out.
    ///
    /// @param folder The ignored folder's path.
    /// @param doc    The doc that says so.
    public record Ignored(String folder, InstructionDoc doc) {

        /// @return What it is, for the agent, e.g. "/Feeds/Archive (ignored by the AGENTS doc in /Feeds)".
        public String describe() {
            return folder + " (ignored by the " + DOC_NAME + " doc in " + doc.folder() + ")";
        }
    }
}
