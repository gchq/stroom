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
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.shared.AppPermission;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/// `document_links` - walks the dependency graph Stroom keeps of which documents use which (the one the
/// Dependencies screen shows), from some documents, to answer "what processes what": e.g. from a feed to the
/// processor filters that pick its streams, the pipelines they run, and the XSLTs those use.
///
/// The graph's own permission filter lets a document through if checking it fails, so every document returned
/// or passed through is checked for View again here, failing closed. A path through a document the user can't
/// see isn't followed, and nothing hints that it exists. A processor filter isn't a document: it is shown, and
/// followed, only if the user can view the pipeline it runs. What a filter selects is said only to a user with the
/// Manage Processors permission, as the Processors tab shows it ([ProcessorFilterAccess]). Anyone else is told
/// that, and given instead the feeds the graph links the filter to, each checked for View like everything else.
class DocumentLinksTool extends AbstractWorkspaceTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocumentLinksTool.class);

    static final String NAME = "document_links";
    /// What the tool says for a document the user can't view, or that doesn't exist, which it doesn't tell apart.
    static final String NOT_FOUND = "No document was found with that UUID, or you don't have permission to view "
                                    + "it.";
    static final String UUIDS_ARG = "uuids";
    static final String DIRECTION_ARG = "direction";
    static final String DEPTH_ARG = "depth";
    static final String TYPES_ARG = "types";
    static final String LIMIT_ARG = "limit";
    static final String DEPENDENCIES = "dependencies";
    static final String DEPENDANTS = "dependants";
    static final int MAX_DEPTH = 4;
    static final int DEFAULT_DEPTH = 2;
    static final int DEFAULT_LIMIT = 100;
    static final int MAX_LIMIT = 500;
    /// The most documents visited in one call, whether returned or not.
    static final int MAX_VISITED = 5_000;
    /// The most edges read for a batch of documents.
    static final int MAX_EDGES_PER_BATCH = 10_000;
    /// How many documents' edges are read at once.
    static final int BATCH_SIZE = 20;

    private final Provider<DocDependencyService> docDependencyServiceProvider;
    private final DocumentAccess access;
    private final ProcessorFilterAccess processorFilterAccess;
    private final AiAuditLog auditLog;

    @Inject
    DocumentLinksTool(final ChatWorkspace workspace,
                      final Provider<DocDependencyService> docDependencyServiceProvider,
                      final DocumentAccess access,
                      final ProcessorFilterAccess processorFilterAccess,
                      final AiAuditLog auditLog) {
        super(workspace);
        this.docDependencyServiceProvider = docDependencyServiceProvider;
        this.access = access;
        this.processorFilterAccess = processorFilterAccess;
        this.auditLog = auditLog;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .unlockedBy(AppPermission.MANAGE_PROCESSORS_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Follows the links between Stroom documents, as the Dependencies screen shows them, "
                             + "from some documents (by UUID), to find e.g. what processes a feed's data or what "
                             + "uses an XSLT. 'dependants' finds what uses each document (e.g. Feed → processor "
                             + "filters that pick its streams → the pipeline each runs → the XSLTs and other "
                             + "parts that pipeline uses); 'dependencies' finds what each document uses. Returns "
                             + "each document reached with its type, name, UUID, folder, how many links away it "
                             + "is and the chain of links that reached it. Each processor filter reached is given "
                             + "with what it selects (e.g. which feeds and stream types) and whether it is "
                             + "enabled, so the dependants of a pipeline at depth 1 answer 'which feeds or "
                             + "streams does this pipeline process?' in one call. The graph only holds the links "
                             + "each document names, e.g. a processor filter that picks streams by type alone has "
                             + "no link to a feed, so if a link you expect is missing, read the filters and "
                             + "pipelines rather than conclude there is none.")
                .parameters(ObjectSchema.builder()
                        .addProperty(UUIDS_ARG, stringArraySchema("The documents to start from."))
                        .addEnumProperty(DIRECTION_ARG, List.of(DEPENDANTS, DEPENDENCIES),
                                "Which way to follow the links (default " + DEPENDANTS + ").")
                        .addIntegerProperty(DEPTH_ARG, "How many links to follow (default " + DEFAULT_DEPTH
                                                       + ", at most " + MAX_DEPTH + ").")
                        .addProperty(TYPES_ARG, stringArraySchema("The document types to return, e.g. "
                                                                     + "['Pipeline', 'XSLT']. Others are still "
                                                                     + "followed, but not returned."))
                        .addIntegerProperty(LIMIT_ARG, "The most documents to return (default " + DEFAULT_LIMIT
                                                       + ", at most " + MAX_LIMIT + ").")
                        .addStringProperty(OUT_ARG, ToolDescription.builder()
                                .chatOnly("A file in /outputs/ to write the whole list to.")
                                .build())
                        .required(UUIDS_ARG)
                        .build())
                .build();
    }

    @Override
    ToolResult run(final ToolCall request, final ToolCallContext context) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final WorkspacePath out = outArg(request);
        final List<String> uuids = stringListArg(request, UUIDS_ARG);
        if (uuids.isEmpty()) {
            throw new WorkspaceException("Give the UUIDs of the documents to start from.");
        }
        final boolean dependants = direction(stringArg(request, DIRECTION_ARG));
        final Integer depthArg = intArg(request, DEPTH_ARG);
        final int depth = depthArg == null
                ? DEFAULT_DEPTH
                : Math.max(1, Math.min(depthArg, MAX_DEPTH));
        final Set<String> types = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        types.addAll(stringListArg(request, TYPES_ARG));
        final Integer limitArg = intArg(request, LIMIT_ARG);
        final int limit = limitArg == null
                ? DEFAULT_LIMIT
                : Math.max(1, Math.min(limitArg, MAX_LIMIT));

        final List<DocRef> starts = new ArrayList<>();
        final List<String> notFound = new ArrayList<>();
        for (final String uuid : uuids) {
            access.findDocRef(uuid).ifPresentOrElse(starts::add, () -> notFound.add(uuid));
        }
        if (starts.isEmpty()) {
            throw new WorkspaceException(NOT_FOUND);
        }

        final Walk walk = new Walk(dependants);
        final Map<String, Reached> reached = walk.run(starts, depth);
        final List<Reached> results = reached.values().stream()
                .filter(node -> node.distance() > 0)
                .filter(node -> types.isEmpty() || types.contains(node.docRef().getType()))
                .sorted(Comparator.comparingInt(Reached::distance)
                        .thenComparing(node -> node.docRef().getType())
                        .thenComparing(node -> NullSafe.string(node.docRef().getName())))
                .toList();

        final boolean canManageProcessors = processorFilterAccess.canManageProcessors();
        final List<String> lines = new ArrayList<>();
        for (final Reached node : results) {
            lines.add(line(node, walk, canManageProcessors));
        }
        final List<Reached> shown = results.stream().limit(limit).toList();
        logSearch(context, starts, dependants, depth, shown);

        final StringBuilder sb = new StringBuilder();
        sb.append("Followed the ").append(dependants
                        ? DEPENDANTS
                        : DEPENDENCIES)
                .append(" of ").append(starts.stream().map(DocumentLinksTool::describe)
                        .collect(Collectors.joining(", ")))
                .append(", up to ").append(depth).append(" link(s): ").append(results.size())
                .append(" document(s) found").append(types.isEmpty()
                        ? ""
                        : " of the types asked for").append('.');
        if (!notFound.isEmpty()) {
            sb.append(" Not found, or you don't have permission to view them: ")
                    .append(String.join(", ", notFound)).append('.');
        }
        if (walk.truncated) {
            sb.append(" The walk stopped after ").append(MAX_VISITED).append(" documents; give a smaller depth "
                                                                             + "or start nearer.");
        }
        if (out != null) {
            workspace.write(out, String.join("\n", lines) + "\n", null);
            sb.append(" Wrote them all to ").append(out).append('.');
        }
        if (!lines.isEmpty()) {
            final List<String> shownLines = lines.stream().limit(limit).toList();
            sb.append("\n\n").append(boundedLines(context, shownLines, shownLines.size()));
            if (lines.size() > limit) {
                sb.append("\n(").append(lines.size() - limit).append(" more not shown; give a larger limit, "
                                                                     + "types, or out.)");
            }
        }
        return ToolResult.text(sb.toString());
    }

    private static boolean direction(final String direction) {
        if (direction == null || DEPENDANTS.equalsIgnoreCase(direction)) {
            return true;
        }
        if (DEPENDENCIES.equalsIgnoreCase(direction)) {
            return false;
        }
        throw new WorkspaceException("The direction must be " + DEPENDANTS + " or " + DEPENDENCIES + ".");
    }

    private String line(final Reached node, final Walk walk, final boolean canManageProcessors) {
        final DocRef docRef = node.docRef();
        final StringBuilder sb = new StringBuilder("- ");
        final boolean filter = ProcessorFilter.ENTITY_TYPE.equals(docRef.getType());
        if (filter) {
            final DocRef pipeline = walk.pipelineOf(docRef).orElse(null);
            sb.append("Processor filter (").append(docRef.getUuid()).append(") of pipeline '")
                    .append(NullSafe.getOrElse(pipeline, DocRef::getName, "?")).append("' (")
                    .append(NullSafe.getOrElse(pipeline, DocRef::getUuid, "?")).append(')');
        } else {
            sb.append(describe(docRef)).append(" (").append(docRef.getUuid()).append(") in ")
                    .append(access.path(docRef));
        }
        sb.append(": ").append(node.distance()).append(" link(s) away, via ").append(node.chain());
        if (filter) {
            sb.append(". ").append(filterDetails(docRef, walk, canManageProcessors)).append('.');
        }
        return sb.toString();
    }

    // What a processor filter selects, for a user who may see it; for anyone else, or if it can't be read, the
    // feeds the graph links it to that the user can view, and why its selection isn't given.
    private String filterDetails(final DocRef filterRef, final Walk walk, final boolean canManageProcessors) {
        if (canManageProcessors) {
            ToolDeadline.check();
            final Optional<ProcessorFilter> filter = processorFilterAccess.find(filterRef.getUuid());
            if (filter.isPresent()) {
                return "It selects " + ProcessorFilterAccess.selection(filter.get()) + "; "
                       + (filter.get().isEnabled()
                        ? "enabled"
                        : "disabled");
            }
        }
        final List<DocRef> feeds = walk.feedsOf(filterRef);
        final String reason = canManageProcessors
                ? "what it selects couldn't be read"
                : ProcessorFilterAccess.NEEDS_MANAGE_PROCESSORS;
        if (feeds.isEmpty()) {
            return reason.substring(0, 1).toUpperCase(Locale.ROOT) + reason.substring(1);
        }
        return "It links to " + feeds.stream()
                .map(DocumentLinksTool::describe)
                .collect(Collectors.joining(", ")) + "; " + reason;
    }

    /// @param docRef A document, or a processor filter.
    /// @return It in words, e.g. `XSLT 'Windows XSLT'`.
    static String describe(final DocRef docRef) {
        if (ProcessorFilter.ENTITY_TYPE.equals(docRef.getType())) {
            return "Processor filter";
        }
        return docRef.getType() + " '" + NullSafe.string(docRef.getName()) + "'";
    }

    private void logSearch(final ToolCallContext context,
                           final List<DocRef> starts,
                           final boolean dependants,
                           final int depth,
                           final List<Reached> results) {
        final List<DocRef> objects = results.stream()
                .map(Reached::docRef)
                .toList();
        final String query = (dependants
                ? DEPENDANTS
                : DEPENDENCIES) + " of " + starts.stream()
                .map(docRef -> describe(docRef) + " (" + docRef.getUuid() + ")")
                .collect(Collectors.joining(", ")) + ", depth " + depth;
        auditLog.documentsSearched(context.session(), query, objects, Map.of(), "Document links followed by the " + NAME
                                                                     + " tool");
    }

    @Override
    public boolean readsUntrustedContent() {
        // Document names are written by users.
        return true;
    }

    /// A document reached.
    ///
    /// @param docRef   The document.
    /// @param distance How many links from the start.
    /// @param chain    The links that reached it, e.g. "Feed 'X' → used by Processor filter → runs Pipeline 'P'".
    /// @param viaRun   True if it is a pipeline reached from a processor filter that runs it.
    record Reached(DocRef docRef, int distance, String chain, boolean viaRun) {

    }

    /// One walk of the graph, with what it has learnt along the way.
    private final class Walk {

        private final boolean dependants;
        private final Map<String, Boolean> visible = new HashMap<>();
        private final Map<String, List<DocRef>> filterLinks = new HashMap<>();
        private boolean truncated;

        private Walk(final boolean dependants) {
            this.dependants = dependants;
        }

        private Map<String, Reached> run(final List<DocRef> starts, final int depth) {
            final Map<String, Reached> reached = new LinkedHashMap<>();
            List<Reached> frontier = new ArrayList<>();
            for (final DocRef start : starts) {
                final Reached node = new Reached(start, 0, describe(start), false);
                if (reached.putIfAbsent(start.getUuid(), node) == null) {
                    frontier.add(node);
                }
            }
            for (int distance = 1; distance <= depth && !frontier.isEmpty() && !truncated; distance++) {
                final List<Reached> next = new ArrayList<>();
                final Map<String, List<DocRef>> forward = edges(frontier.stream()
                        .filter(node -> !ProcessorFilter.ENTITY_TYPE.equals(node.docRef().getType())
                                        || !dependants)
                        .map(node -> node.docRef().getUuid())
                        .toList(), dependants);
                // A pipeline reached from a filter that runs it is followed to the parts it uses.
                final Map<String, List<DocRef>> parts = edges(frontier.stream()
                        .filter(Reached::viaRun)
                        .map(node -> node.docRef().getUuid())
                        .toList(), false);
                for (final Reached node : frontier) {
                    final String label = dependants
                            ? "used by"
                            : "uses";
                    if (dependants && ProcessorFilter.ENTITY_TYPE.equals(node.docRef().getType())) {
                        pipelineOf(node.docRef()).ifPresent(pipeline ->
                                add(reached, next, node, pipeline, "runs", true));
                    }
                    if (!node.viaRun()) {
                        for (final DocRef neighbour : forward.getOrDefault(node.docRef().getUuid(), List.of())) {
                            add(reached, next, node, neighbour, label, false);
                        }
                    } else {
                        for (final DocRef part : parts.getOrDefault(node.docRef().getUuid(), List.of())) {
                            add(reached, next, node, part, "uses", false);
                        }
                    }
                    if (reached.size() >= MAX_VISITED) {
                        truncated = true;
                        break;
                    }
                }
                frontier = next;
            }
            return reached;
        }

        private void add(final Map<String, Reached> reached,
                         final List<Reached> next,
                         final Reached from,
                         final DocRef to,
                         final String label,
                         final boolean viaRun) {
            ToolDeadline.check();
            if (to == null || to.getUuid() == null || reached.containsKey(to.getUuid()) || !isVisible(to)) {
                return;
            }
            final String chain = from.chain() + " → " + label + " " + describe(to);
            final Reached node = new Reached(to, from.distance() + 1, chain, viaRun);
            reached.put(to.getUuid(), node);
            next.add(node);
        }

        /// @return True if the user can view the document, or for a processor filter, the pipeline it runs.
        private boolean isVisible(final DocRef docRef) {
            return visible.computeIfAbsent(docRef.getUuid(), uuid -> {
                if (ProcessorFilter.ENTITY_TYPE.equals(docRef.getType())) {
                    return pipelineOf(docRef).isPresent();
                }
                return access.canView(docRef);
            });
        }

        /// @return The pipeline a processor filter runs, if the user can view it.
        private Optional<DocRef> pipelineOf(final DocRef filter) {
            return linksOf(filter).stream()
                    .filter(docRef -> PipelineDoc.TYPE.equals(docRef.getType()))
                    // Not isVisible, as this is called while it works out a filter's visibility.
                    .filter(access::canView)
                    .findFirst();
        }

        /// @return The feeds the graph links a processor filter to that the user can view, by name.
        private List<DocRef> feedsOf(final DocRef filter) {
            return linksOf(filter).stream()
                    .filter(docRef -> FeedDoc.TYPE.equals(docRef.getType()))
                    .filter(docRef -> docRef.getUuid() != null)
                    .filter(this::isVisible)
                    .sorted(Comparator.comparing(docRef -> NullSafe.string(docRef.getName())))
                    .toList();
        }

        /// @return What a processor filter links to in the graph: the pipeline it runs and the documents its
        /// expression names. None of them is checked for View.
        private List<DocRef> linksOf(final DocRef filter) {
            return filterLinks.computeIfAbsent(filter.getUuid(), uuid ->
                    edges(List.of(uuid), false).getOrDefault(uuid, List.of()));
        }

        /// @return For each document, the documents at the other end of its links in one direction.
        private Map<String, List<DocRef>> edges(final List<String> uuids, final boolean toDependants) {
            final Map<String, List<DocRef>> edges = new HashMap<>();
            for (int i = 0; i < uuids.size(); i += BATCH_SIZE) {
                ToolDeadline.check();
                final List<String> batch = uuids.subList(i, Math.min(uuids.size(), i + BATCH_SIZE));
                final String qualifier = (toDependants
                        ? DependencyCriteria.FIELD_DEF_TO_UUID
                        : DependencyCriteria.FIELD_DEF_FROM_UUID).getFilterQualifier();
                final DependencyCriteria criteria = new DependencyCriteria();
                criteria.setPageRequest(new PageRequest(0, MAX_EDGES_PER_BATCH));
                criteria.setPartialName(batch.stream()
                        .map(uuid -> qualifier + ":=" + uuid)
                        .collect(Collectors.joining(" or ")));
                final ResultPage<Dependency> page;
                try {
                    page = docDependencyServiceProvider.get().fetchDependencies(criteria, Set.of());
                } catch (final RuntimeException e) {
                    LOGGER.debug(() -> "Unable to read the dependencies of " + batch + ": " + e.getMessage(), e);
                    continue;
                }
                for (final Dependency dependency : NullSafe.list(NullSafe.get(page, ResultPage::getValues))) {
                    final DocRef self = toDependants
                            ? dependency.getTo()
                            : dependency.getFrom();
                    final DocRef other = toDependants
                            ? dependency.getFrom()
                            : dependency.getTo();
                    if (self != null && other != null && batch.contains(self.getUuid())) {
                        edges.computeIfAbsent(self.getUuid(), k -> new ArrayList<>()).add(other);
                    }
                }
            }
            return edges;
        }
    }
}
