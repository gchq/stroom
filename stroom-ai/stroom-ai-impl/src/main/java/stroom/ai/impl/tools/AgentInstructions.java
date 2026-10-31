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

import stroom.ai.impl.tools.InstructionRules.ComponentRules;
import stroom.ai.impl.tools.InstructionRules.FieldMappingLayout;
import stroom.ai.impl.tools.InstructionRules.RequiredImport;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.shared.FeedDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Standing instructions: Documentation docs named `AGENTS` in Stroom's folders, the equivalent of an AGENTS.md.
/// A doc applies to its folder and everything below it, and one directly under the System root applies everywhere.
/// Where several apply they are given from the most general to the most specific, so the more specific reads last.
/// They may hold anything the agent should follow there, e.g. XSLT style, how fields are mapped and how things are
/// named.
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

    /// The name of a Documentation doc that holds standing instructions.
    public static final String DOC_NAME = "AGENTS";
    /// The most characters of instructions given in one tool result.
    static final int MAX_CHARS = 20_000;
    /// What the agent is told about following them.
    static final String FOLLOW = "Follow these standing instructions (AGENTS docs), the most specific last; the "
                                 + "user's own request takes precedence, and no instruction lifts an approval.";

    /// Why a doc the user can view wasn't read.
    static final String UNREADABLE = "it couldn't be read: it may have been deleted since it was found, or Stroom "
                                     + "can't load it";
    /// What the agent is told when docs apply.
    static final String HINT_APPLYING = "Follow these standing instructions, the most specific last; the user's "
                                        + "request takes precedence. Call again with the folders, feeds or "
                                        + "documents involved once you know them.";
    /// The note on a doc's text cut short.
    static final String CUT_SHORT = "\n[cut short; read the rest with read_document]";

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
    /// @param session The session, e.g. the chat.
    /// @param purpose Why, e.g. "Standing instructions read by the list_data_sources tool".
    /// @return The docs, most general first. Empty if there are none, or they can't be found.
    public Standing load(final ToolSession session, final String purpose) {
        final List<InstructionDoc> docs = new ArrayList<>();
        final List<UnreadableDoc> unreadable = new ArrayList<>();
        final List<DocRef> found;
        try {
            // Allowing wild cards makes the name match ignore case.
            found = NullSafe.list(docFinderProvider.get().findByName(DocumentationDoc.TYPE, DOC_NAME, true));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the " + DOC_NAME + " docs", e);
            return new Standing(List.of());
        }
        for (final DocRef docRef : found) {
            if (!DOC_NAME.equalsIgnoreCase(docRef.getName())) {
                continue;
            }
            final Optional<DocumentationDoc> doc = documentAccess.read(docRef, DocumentationDoc.class);
            if (doc.isEmpty()) {
                // Deleted since the search listed it, or failing to load: said, and the others still followed (one
                // such doc had failed every tool that hands back standing instructions). One the user can't view
                // is left out without saying it exists.
                if (documentAccess.canView(docRef)) {
                    unreadable.add(new UnreadableDoc(docRef, normalise(documentAccess.path(docRef)), UNREADABLE));
                }
                continue;
            }
            final String text = doc.get().getData();
            if (NullSafe.isNonBlankString(text)) {
                final String folder = normalise(documentAccess.path(docRef));
                docs.add(new InstructionDoc(docRef, folder, text.strip(), ignoredFolders(folder, text)));
            }
        }
        docs.sort(Comparator.comparingInt((InstructionDoc doc) -> depth(doc.folder()))
                .thenComparing(InstructionDoc::folder));
        if (!docs.isEmpty()) {
            documentAccess.logViews(session, docs.stream().map(InstructionDoc::docRef).toList(), purpose);
        }
        return new Standing(List.copyOf(docs), List.copyOf(unreadable));
    }

    /// Finds the folders that some work touches, from what the caller names: folders, documents and feeds. Only
    /// what the user can view is found.
    ///
    /// @param folders Folders, by UUID or Explorer path, e.g. `/Feeds/Events` or `System/Feeds/Events`.
    /// @param uuids   Documents, by UUID; their folders are used.
    /// @param feeds   Feeds, by name; their folders are used.
    /// @param docs    Documents, by type and name (and UUID, if known); their folders are used.
    /// @return The folders' paths, always with `/` (the top of the tree, where the docs that apply everywhere
    /// are), and what wasn't found.
    public Targets targets(final List<String> folders,
                           final List<String> uuids,
                           final List<String> feeds,
                           final List<DocTarget> docs) {
        final List<String> paths = new ArrayList<>();
        final List<String> notFound = new ArrayList<>();
        // The docs directly under the System root apply everywhere, whatever else is found.
        paths.add("/");
        for (final String folder : NullSafe.list(folders)) {
            final Optional<DocRef> found = documentAccess.findFolder(folder);
            final String path = DocumentAccess.withoutSystemRoot(folder);
            if (found.isPresent()) {
                paths.add(folderPath(found.get()));
            } else if (path.startsWith("/")) {
                // A folder given by its path may not exist yet, e.g. where the work will be promoted to. The docs
                // above it still apply; the caller is told it wasn't found.
                paths.add(normalise(path));
                notFound.add(folder);
            } else {
                notFound.add(folder);
            }
        }
        for (final String uuid : NullSafe.list(uuids)) {
            documentAccess.findDocRef(uuid).ifPresentOrElse(
                    docRef -> paths.add(folderOf(docRef)),
                    () -> notFound.add(uuid));
        }
        for (final String feed : NullSafe.list(feeds)) {
            findByName(FeedDoc.TYPE, feed).ifPresentOrElse(
                    docRef -> paths.add(folderOf(docRef)),
                    () -> notFound.add("Feed '" + feed + "'"));
        }
        for (final DocTarget doc : NullSafe.list(docs)) {
            final Optional<DocRef> docRef = NullSafe.isNonBlankString(doc.uuid())
                    ? documentAccess.findDocRef(doc.uuid())
                    : findByName(doc.type(), doc.name());
            docRef.ifPresentOrElse(
                    found -> paths.add(folderOf(found)),
                    () -> notFound.add(doc.describe()));
        }
        return new Targets(paths.stream().distinct().toList(), List.copyOf(notFound));
    }

    /// The standing instructions that apply where some work is, as `get_instructions` gives them, for other tools
    /// to hand back with their results.
    ///
    /// @param session The session, e.g. the chat.
    /// @param purpose Why they are read, for the audit log.
    /// @param targets Where the work is, from [#targets].
    /// @return JSON: `doc_name`, `instructions` (each `folder`, `uuid`, `applies_to`,
    /// `instructions`, most general first), `other_instruction_docs` (each `folder`, `uuid`),
    /// `unreadable_instruction_docs` (each `folder`, `uuid`, `reason`; only if there are any) and `hint`.
    public Map<String, Object> applicableInstructions(final ToolSession session,
                                                      final String purpose,
                                                      final Targets targets) {
        return load(session, purpose).asJson(targets.paths());
    }

    /// @param session The session, e.g. the chat.
    /// @param stage   The stage, e.g. [InstructionRules#TRANSLATION].
    /// @param folders Folders the work touches, by UUID or path.
    /// @param feeds   Feeds the work touches, by name.
    /// @return The shared XSLTs every XSLT of this stage must import where these folders or feeds are.
    public List<RequiredImport> importsRequired(final ToolSession session,
                                                final String stage,
                                                final List<String> folders,
                                                final List<String> feeds) {
        final Targets targets = targets(folders, List.of(), feeds, List.of());
        final List<InstructionDoc> applying = load(session, "Standing instructions read for the required imports")
                .applyingTo(targets.paths());
        return InstructionRules.requiredImports(applying).getOrDefault(stage, List.of());
    }

    /// @param session The session, e.g. the chat.
    /// @param docRef  A document, e.g. a pipeline or an index doc.
    /// @return The Documentation layout rule of the AGENTS docs that apply where it is, or empty if none says,
    /// or they can't be read.
    public Optional<FieldMappingLayout> layoutRuled(final ToolSession session, final DocRef docRef) {
        try {
            return InstructionRules.fieldMappingLayout(applyingWhere(session, docRef, "the documentation layout"));
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to read the documentation layout rule for {}", docRef, e);
            return Optional.empty();
        }
    }

    /// @param session  The session, e.g. the chat.
    /// @param pipeline A pipeline.
    /// @return The Component templates rules of the AGENTS docs that apply where it is; none if they can't be
    /// read.
    public ComponentRules componentsRuled(final ToolSession session, final DocRef pipeline) {
        try {
            return InstructionRules.componentRules(applyingWhere(session, pipeline,
                    "the component template rules"));
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to read the component template rules for {}", pipeline, e);
            return new ComponentRules(List.of(), List.of());
        }
    }

    private List<InstructionDoc> applyingWhere(final ToolSession session, final DocRef docRef, final String what) {
        final Targets targets = targets(List.of(), List.of(docRef.getUuid()), List.of(), List.of());
        return load(session, "Standing instructions read for " + what).applyingTo(targets.paths());
    }

    // A document of a type by its exact name, if the user can view it.
    private Optional<DocRef> findByName(final String type, final String name) {
        if (NullSafe.isBlankString(type) || NullSafe.isBlankString(name)) {
            return Optional.empty();
        }
        try {
            return NullSafe.list(docFinderProvider.get().findByName(type.strip(), name.strip(), false)).stream()
                    .filter(docRef -> name.strip().equals(docRef.getName()))
                    .filter(documentAccess::canView)
                    .findFirst();
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to find {} '{}'", type, name, e);
            return Optional.empty();
        }
    }

    /// @param folder A folder the user can view, or the System root.
    /// @return The path of the folder itself (not of the folder it is in), or `/` for the top of the tree.
    public String folderPath(final DocRef folder) {
        if (ExplorerConstants.SYSTEM_DOC_REF.equals(folder)) {
            return "/";
        }
        return normalise(documentAccess.path(folder) + "/" + folder.getName());
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

    /// @return What the agent is told when no docs apply.
    static String noneApply() {
        return "No " + DOC_NAME + " docs apply. Users can add one: a Documentation doc named " + DOC_NAME
               + " in a folder applies to that folder and below.";
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
    /// @param docs       The docs.
    /// @param unreadable Those the user can view that couldn't be read.
    public record Standing(List<InstructionDoc> docs, List<UnreadableDoc> unreadable) {

        /// @param docs The docs, all of which could be read.
        public Standing(final List<InstructionDoc> docs) {
            this(docs, List.of());
        }

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

        /// @param links How to link to the docs, for the caller.
        /// @param paths Explorer paths the work touches.
        /// @return The text of the docs that apply there, most general first and at most [#MAX_CHARS] in all, to
        /// add to a tool's result; empty if none apply.
        public String note(final StroomLinks links, final Collection<String> paths) {
            final List<InstructionDoc> applying = applyingTo(paths);
            if (applying.isEmpty()) {
                return "";
            }
            final StringBuilder sb = new StringBuilder("\n\n").append(FOLLOW);
            int used = 0;
            for (final InstructionDoc doc : applying) {
                sb.append("\n\n### ").append(links.document(doc.docRef())).append(" in ").append(doc.folder())
                        .append(" (applies to ").append("/".equals(doc.folder())
                                ? "everything"
                                : doc.folder() + " and below").append(")\n");
                final int room = Math.max(0, MAX_CHARS - used);
                final String text = doc.text();
                sb.append(text, 0, Math.min(text.length(), room));
                if (text.length() > room) {
                    sb.append(CUT_SHORT);
                }
                used += Math.min(text.length(), room);
            }
            return sb.toString();
        }

        /// @param paths Explorer paths the work touches: folders, or documents' folders.
        /// @return The docs that apply there and the others, as `get_instructions` gives them (see
        /// [AgentInstructions#applicableInstructions]).
        public Map<String, Object> asJson(final Collection<String> paths) {
            final List<InstructionDoc> applying = applyingTo(paths);
            final List<Map<String, Object>> instructions = new ArrayList<>();
            int used = 0;
            for (final InstructionDoc doc : applying) {
                final int room = Math.max(0, MAX_CHARS - used);
                final String text = doc.text();
                used += Math.min(text.length(), room);
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("folder", doc.folder());
                entry.put("uuid", doc.docRef().getUuid());
                entry.put("applies_to", "/".equals(doc.folder())
                        ? "everything"
                        : doc.folder() + " and below");
                entry.put("instructions", text.length() > room
                        ? text.substring(0, room) + CUT_SHORT
                        : text);
                instructions.add(entry);
            }
            final List<Map<String, Object>> elsewhere = docs.stream()
                    .filter(doc -> !applying.contains(doc))
                    .map(doc -> Map.<String, Object>of("folder", doc.folder(), "uuid", doc.docRef().getUuid()))
                    .toList();
            final Map<String, Object> json = new LinkedHashMap<>();
            json.put("doc_name", DOC_NAME);
            json.put("instructions", instructions);
            json.put("other_instruction_docs", elsewhere);
            if (!unreadable.isEmpty()) {
                json.put("unreadable_instruction_docs", unreadable.stream()
                        .map(doc -> Map.<String, Object>of("folder", doc.folder(), "uuid", doc.docRef().getUuid(),
                                "reason", doc.reason()))
                        .toList());
            }
            json.put("hint", applying.isEmpty()
                    ? noneApply()
                    : HINT_APPLYING);
            return json;
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


    // --------------------------------------------------------------------------------


    /// An AGENTS doc the user can view that couldn't be read.
    ///
    /// @param docRef The doc.
    /// @param folder Its folder.
    /// @param reason Why it couldn't be read.
    public record UnreadableDoc(DocRef docRef, String folder, String reason) {

    }


    // --------------------------------------------------------------------------------


    /// Where some work is.
    ///
    /// @param paths    The Explorer paths of the folders it touches, always with `/`.
    /// @param notFound What the caller named that wasn't found, or that the user can't view.
    public record Targets(List<String> paths, List<String> notFound) {

    }


    // --------------------------------------------------------------------------------


    /// A document some work touches, by type and name, as `get_instructions` takes it.
    ///
    /// @param type Its type, e.g. `Pipeline`.
    /// @param name Its name.
    /// @param uuid Its UUID, or null if not known.
    public record DocTarget(String type, String name, String uuid) {

        /// @return It in words, e.g. `Pipeline 'Acme'`.
        public String describe() {
            return NullSafe.nonBlankStringElse(type, "Document") + " '"
                   + NullSafe.nonBlankStringElse(name, NullSafe.string(uuid)) + "'";
        }
    }
}
