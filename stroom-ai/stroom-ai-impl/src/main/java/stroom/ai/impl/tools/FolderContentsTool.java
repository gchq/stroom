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
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.entity.shared.ExpressionCriteria;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.Status;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterExpressionUtil;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorTaskExpressionUtil;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.query.api.ExpressionOperator;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
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
import java.util.TreeMap;

/// `folder_contents` - what is in an Explorer folder, as the folder's own tabs show it: the documents in it (and
/// optionally the folders below it), the streams of its feeds and pipelines (the Data tab), the processor filters
/// of its pipelines and analytic rules (the Processors tab), and their tasks (the Active Tasks tab).
///
/// Read as the user. A folder the user can't view is refused without saying whether it exists, and only the
/// documents in it they can view are listed, without looking inside folders they can't view. Streams are found
/// through [MetaService], which only finds those of feeds the user may view. Processor filters and tasks need the
/// Manage Processors permission, as their tabs do, and of those only the filters [ProcessorFilterService] lets the
/// user see are listed, and only those (and tasks) whose pipeline or analytic rule, and for tasks whose feed, the
/// user can view. Every check fails closed.
class FolderContentsTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FolderContentsTool.class);

    static final String NAME = "folder_contents";
    static final String FOLDER_ARG = "folder";
    static final String SHOW_ARG = "show";
    static final String DEPTH_ARG = "depth";
    static final String TYPES_ARG = "types";
    static final String INCLUDE_IGNORED_ARG = "includeIgnored";

    static final String DOCUMENTS = "documents";
    static final String STREAMS = "streams";
    static final String PROCESSORS = "processors";
    static final String TASKS = "tasks";
    static final List<String> SECTIONS = List.of(DOCUMENTS, STREAMS, PROCESSORS, TASKS);

    static final int DEFAULT_DEPTH = 1;
    static final int MAX_DEPTH = 5;
    /// The most documents listed.
    static final int MAX_DOCUMENTS = 200;
    /// The most recent streams listed.
    static final int MAX_STREAMS = 20;
    /// The most processor filters listed.
    static final int MAX_FILTERS = 100;
    /// The most rows of task counts read.
    static final int MAX_TASK_ROWS = 500;

    private final DocumentAccess documentAccess;
    private final SecurityContext securityContext;
    private final Provider<MetaService> metaServiceProvider;
    private final Provider<ProcessorFilterService> processorFilterServiceProvider;
    private final Provider<ProcessorTaskService> processorTaskServiceProvider;
    private final Provider<DocFinder> docFinderProvider;
    private final AgentInstructions agentInstructions;
    private final AiAuditLog auditLog;

    @Inject
    FolderContentsTool(final DocumentAccess documentAccess,
                       final SecurityContext securityContext,
                       final Provider<MetaService> metaServiceProvider,
                       final Provider<ProcessorFilterService> processorFilterServiceProvider,
                       final Provider<ProcessorTaskService> processorTaskServiceProvider,
                       final Provider<DocFinder> docFinderProvider,
                       final AgentInstructions agentInstructions,
                       final AiAuditLog auditLog) {
        this.documentAccess = documentAccess;
        this.securityContext = securityContext;
        this.metaServiceProvider = metaServiceProvider;
        this.processorFilterServiceProvider = processorFilterServiceProvider;
        this.processorTaskServiceProvider = processorTaskServiceProvider;
        this.docFinderProvider = docFinderProvider;
        this.agentInstructions = agentInstructions;
        this.auditLog = auditLog;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Lists what is in an Explorer folder, as its tabs show it, e.g. for 'what's in this "
                             + "folder?'. Give show with any of: 'documents' (the default: the documents and "
                             + "folders in it, each as a link; give depth to look in the folders below too), "
                             + "'streams' (how many streams its feeds received and its pipelines produced, and "
                             + "the most recent; use stream_meta with folder for more), 'processors' (the "
                             + "processor filters of its pipelines and analytic rules: enabled, priority, "
                             + "progress) and 'tasks' (counts of their processing tasks by status). Ask for the "
                             + "streams, processors or tasks when the user mentions data, processing or tasks. "
                             + "Folders an AGENTS doc says to ignore (e.g. an Archive) are shown but not looked "
                             + "in, and the AGENTS docs that apply to the folder are given.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(FOLDER_ARG, "The folder's UUID, or its Explorer path, e.g. "
                                                       + "'/Security/Lookups'. Omit, or give '/', for the top of "
                                                       + "the tree.")
                        .addProperty(SHOW_ARG, stringArraySchema("What to list, any of: "
                                                                 + String.join(", ", SECTIONS) + " (default "
                                                                 + DOCUMENTS + ")."))
                        .addIntegerProperty(DEPTH_ARG, "For documents, how many levels of folders to list: 1 "
                                                       + "(the default) for what is directly in the folder, up "
                                                       + "to " + MAX_DEPTH + ".")
                        .addProperty(TYPES_ARG, stringArraySchema("For documents, only these types, e.g. "
                                                                  + "['Feed', 'Pipeline', 'XSLT']. Folders are "
                                                                  + "still followed."))
                        .addBooleanProperty(INCLUDE_IGNORED_ARG, "Optional. True to look in ignored folders too, "
                                                                 + "only when the user asks for them.")
                        .build())
                .build();
    }

    @Override
    public String execute(final ToolExecutionRequest request) {
        return "Error: " + NAME + " can only be used in a chat.";
    }

    @Override
    public String execute(final ToolExecutionRequest request, final int chatId) {
        final DocRef folder = documentAccess.findFolder(stringArg(request, FOLDER_ARG)).orElse(null);
        if (folder == null) {
            return "No folder was found with that UUID or path, or you don't have permission to view it.";
        }
        final List<String> show;
        final Set<String> types;
        try {
            show = show(request);
            types = Set.copyOf(stringListArg(request, TYPES_ARG));
        } catch (final IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
        final Integer depthArg = intArg(request, DEPTH_ARG);
        final int depth = depthArg == null
                ? DEFAULT_DEPTH
                : Math.max(1, Math.min(depthArg, MAX_DEPTH));

        final boolean root = ExplorerConstants.SYSTEM_DOC_REF.equals(folder);
        final String path = root
                ? "/"
                : joinPath(documentAccess.path(folder), folder.getName());
        final Listing listing = new Listing();
        final Standing standing = agentInstructions.load(chatId, "Standing instructions read by the " + NAME
                                                                 + " tool");
        final Optional<Ignored> folderIgnored = standing.ignored(path);
        // A folder the user asks about is listed in full, even if an AGENTS doc says to ignore it.
        final Scan scan = new Scan(standing, "true".equalsIgnoreCase(stringArg(request, INCLUDE_IGNORED_ARG))
                                             || folderIgnored.isPresent());
        final StringBuilder sb = new StringBuilder();
        sb.append(root
                ? "The top of the Explorer tree (/)"
                : "Folder '" + folder.getName() + "' (" + path + ", uuid " + folder.getUuid() + ", "
                  + DocumentInfoTool.link(folder) + ")").append('\n');
        folderIgnored.ifPresent(ignored -> sb.append("It is in ").append(ignored.describe())
                .append(", so searches leave it out; it is listed as it was asked for.\n"));
        if (show.contains(DOCUMENTS)) {
            appendDocuments(sb, folder, path, depth, types, scan, listing);
        }
        if (show.contains(STREAMS)) {
            appendStreams(sb, folder, root, listing);
        }
        if (show.contains(PROCESSORS)) {
            appendProcessors(sb, folder, root, listing);
        }
        if (show.contains(TASKS)) {
            appendTasks(sb, folder, root, listing);
        }

        final Map<String, String> data = new LinkedHashMap<>();
        data.put("Shown", String.join(", ", show));
        data.put("Documents", String.valueOf(listing.documents.size()));
        data.put("ProcessorFilters", String.valueOf(listing.filters));
        auditLog.documentsSearched(chatId, "contents of " + (root
                        ? "the top of the Explorer tree"
                        : "Folder '" + path + "'") + ", showing " + String.join(", ", show)
                                          + (show.contains(DOCUMENTS)
                        ? ", depth " + depth
                        : ""),
                listing.documents, data, "Folder contents listed by the " + NAME + " tool");
        sb.append(standing.note(List.of(path)));
        if (!listing.streams.isEmpty()) {
            auditLog.streamsSearched(chatId, "streams of the feeds and pipelines in " + path, listing.streams,
                    Map.of(), "Folder streams listed by the " + NAME + " tool");
        }
        return sb.toString().stripTrailing();
    }

    private static List<String> show(final ToolExecutionRequest request) {
        final List<String> show = new ArrayList<>();
        for (final String item : stringListArg(request, SHOW_ARG)) {
            final String lower = item.toLowerCase(Locale.ROOT);
            if (!SECTIONS.contains(lower)) {
                throw new IllegalArgumentException("can't show '" + item + "'. Show any of: "
                                                   + String.join(", ", SECTIONS) + ".");
            }
            if (!show.contains(lower)) {
                show.add(lower);
            }
        }
        if (show.isEmpty()) {
            show.add(DOCUMENTS);
        }
        return show;
    }

    // Lists the documents in the folder, and in the folders below it to the depth asked for, each as a link.
    private void appendDocuments(final StringBuilder sb,
                                 final DocRef folder,
                                 final String path,
                                 final int depth,
                                 final Set<String> types,
                                 final Scan scan,
                                 final Listing listing) {
        final List<Entry> entries = new ArrayList<>();
        final boolean cut = collect(folder, path, 1, depth, types, scan, entries);
        final Map<String, Integer> byType = new TreeMap<>();
        entries.stream()
                .filter(entry -> !entry.onlyFollowed())
                .forEach(entry -> byType.merge(entry.node().getDocRef().getType(), 1, Integer::sum));

        sb.append("\nDocuments");
        if (depth > 1) {
            sb.append(" (").append(depth).append(" levels down)");
        }
        sb.append(": ");
        if (entries.isEmpty()) {
            sb.append("none you can see").append(types.isEmpty()
                    ? ""
                    : " of type " + String.join(", ", types.stream().sorted().toList())).append(".\n");
            return;
        }
        final int documents = byType.values().stream().mapToInt(Integer::intValue).sum();
        sb.append(documents).append(cut
                ? " (it stopped after " + MAX_DOCUMENTS + " entries, so there are more: narrow it with types or a "
                  + "lower depth)"
                : "").append(" — ");
        final List<String> counts = new ArrayList<>();
        byType.forEach((type, count) -> counts.add(count + " " + type));
        sb.append(String.join(", ", counts)).append('\n');
        for (final Entry entry : entries) {
            final ExplorerNode node = entry.node();
            final DocRef docRef = node.getDocRef();
            sb.append("  ".repeat(entry.level() - 1)).append("- ").append(DocumentInfoTool.link(docRef))
                    .append(" (").append(docRef.getType()).append(", uuid ").append(docRef.getUuid()).append(')');
            if (NullSafe.hasItems(node.getTags())) {
                sb.append(" tags: ").append(String.join(", ", node.getTags().stream().sorted().toList()));
            }
            if (entry.ignored() != null) {
                sb.append(" — not looked in: ").append(entry.ignored().describe());
            }
            sb.append('\n');
            listing.documents.add(docRef);
        }
        sb.append("Documents in folders you can't view aren't listed.");
        if (scan.ignoredFolders > 0 || scan.ignoredDocuments > 0) {
            sb.append(" Folders an ").append(AgentInstructions.DOC_NAME).append(" doc says to ignore weren't "
                                                                                  + "looked in");
            if (scan.ignoredDocuments > 0) {
                sb.append(", and ").append(scan.ignoredDocuments).append(" document(s) it names were left out");
            }
            sb.append("; pass ").append(INCLUDE_IGNORED_ARG).append(" only if the user asks for them.");
        }
        sb.append('\n');
    }

    /// Adds the folder's children, then those of each folder among them, to the depth asked for.
    ///
    /// @return True if it stopped at [#MAX_DOCUMENTS].
    private boolean collect(final DocRef folder,
                            final String path,
                            final int level,
                            final int depth,
                            final Set<String> types,
                            final Scan scan,
                            final List<Entry> entries) {
        final List<ExplorerNode> children = new ArrayList<>(documentAccess.children(folder));
        // Folders first, then by type and name, as the Explorer tree sorts them.
        children.sort(Comparator.comparing((ExplorerNode node) -> !ExplorerConstants.isFolder(node))
                .thenComparing(node -> NullSafe.string(node.getDocRef().getType()))
                .thenComparing(node -> NullSafe.string(node.getDocRef().getName())));
        for (final ExplorerNode child : children) {
            final boolean isFolder = ExplorerConstants.isFolder(child);
            final String childPath = joinPath(path, child.getDocRef().getName());
            final Ignored ignored = scan.ignored(childPath);
            if (ignored != null && !isFolder) {
                scan.ignoredDocuments++;
                continue;
            }
            final boolean wanted = types.isEmpty() || types.contains(child.getDocRef().getType());
            final boolean descend = isFolder && level < depth && ignored == null;
            if (ignored != null) {
                scan.ignoredFolders++;
                if (!types.isEmpty()) {
                    continue;
                }
            } else if (!wanted && !descend) {
                continue;
            }
            if (entries.size() >= MAX_DOCUMENTS) {
                return true;
            }
            final int index = entries.size();
            entries.add(new Entry(child, level, !wanted, ignored));
            if (descend && collect(child.getDocRef(), childPath, level + 1, depth, types, scan, entries)) {
                return true;
            }
            // A folder only listed to show where the documents below it are, with none of them below it.
            if (!wanted && entries.size() == index + 1) {
                entries.remove(index);
            }
        }
        return false;
    }

    // Counts the streams of the folder's feeds and pipelines, and lists the most recent, as its Data tab does.
    private void appendStreams(final StringBuilder sb,
                               final DocRef folder,
                               final boolean root,
                               final Listing listing) {
        sb.append("\nStreams (Unlocked, of feeds in the folder or produced by its pipelines): ");
        final ExpressionOperator expression = root
                ? MetaExpressionUtil.createStatusExpression(Status.UNLOCKED)
                : MetaExpressionUtil.createFolderExpression(folder);
        try {
            final MetaService metaService = metaServiceProvider.get();
            final SelectionSummary summary = metaService.getSelectionSummary(new FindMetaCriteria(expression));
            if (summary == null || summary.getItemCount() == 0) {
                sb.append("none you can see.\n");
                return;
            }
            sb.append(summary.getItemCount()).append(" in ").append(summary.getFeedCount()).append(" feed(s)");
            if (NullSafe.hasItems(summary.getDistinctFeeds())) {
                sb.append(" (").append(String.join(", ", summary.getDistinctFeeds().stream().sorted().toList()))
                        .append(summary.getDistinctFeeds().size() < summary.getFeedCount()
                                ? ", …"
                                : "")
                        .append(')');
            }
            sb.append(", of ").append(summary.getTypeCount()).append(" type(s)");
            if (NullSafe.hasItems(summary.getDistinctTypes())) {
                sb.append(" (").append(String.join(", ", summary.getDistinctTypes().stream().sorted().toList()))
                        .append(')');
            }
            if (summary.getAgeRange() != null && summary.getAgeRange().getFrom() != null
                && summary.getAgeRange().getTo() != null) {
                sb.append(", created ").append(DateUtil.createNormalDateTimeString(summary.getAgeRange().getFrom()))
                        .append(" to ").append(DateUtil.createNormalDateTimeString(summary.getAgeRange().getTo()));
            }
            sb.append(".\n");

            final FindMetaCriteria recent = new FindMetaCriteria(expression);
            recent.setPageRequest(new PageRequest(0, MAX_STREAMS));
            recent.setSort(MetaFields.ID.getFldName(), true, false);
            final List<MetaRow> rows = NullSafe.list(NullSafe.get(metaService.findRows(recent),
                    ResultPage::getValues));
            if (!rows.isEmpty()) {
                sb.append("The most recent ").append(rows.size()).append(":\n");
                for (final MetaRow row : rows) {
                    final Meta meta = row.getMeta();
                    sb.append("- [stream ").append(meta.getId()).append("](?action=open-stream&id=")
                            .append(meta.getId()).append(") ").append(meta.getFeedName()).append(", ")
                            .append(meta.getTypeName()).append(", created ")
                            .append(DateUtil.createNormalDateTimeString(meta.getCreateMs()));
                    final String pipeline = NullSafe.get(row.getPipeline(), DocRef::getName);
                    if (NullSafe.isNonBlankString(pipeline)) {
                        sb.append(", produced by pipeline '").append(pipeline).append('\'');
                    }
                    sb.append('\n');
                    listing.streams.add(meta.getId());
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the streams of " + folder, e);
            sb.append("couldn't be found.\n");
        }
    }

    // Lists the processor filters of the folder's pipelines and analytic rules, as its Processors tab does.
    private void appendProcessors(final StringBuilder sb,
                                  final DocRef folder,
                                  final boolean root,
                                  final Listing listing) {
        sb.append("\nProcessor filters: ");
        if (!canManageProcessors()) {
            sb.append("you need the Manage Processors permission to see them, as the Processors tab does.\n");
            return;
        }
        final List<ProcessorFilter> filters = new ArrayList<>();
        try {
            final ExpressionOperator expression = root
                    ? ProcessorFilterExpressionUtil.createBasicExpression()
                    : ProcessorFilterExpressionUtil.createFolderExpression(folder);
            final ResultPage<ProcessorListRow> rows = processorFilterServiceProvider.get()
                    .find(new FetchProcessorRequest(expression));
            for (final ProcessorListRow row : NullSafe.list(NullSafe.get(rows, ResultPage::getValues))) {
                if (row instanceof final ProcessorFilterRow filterRow
                    && filterRow.getProcessorFilter() != null
                    && !filterRow.getProcessorFilter().isDeleted()
                    && documentAccess.canView(filterRow.getProcessorFilter().getPipeline())) {
                    filters.add(filterRow.getProcessorFilter());
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the processor filters of " + folder, e);
            sb.append("couldn't be found.\n");
            return;
        }
        if (filters.isEmpty()) {
            sb.append("none you can see.\n");
            return;
        }
        filters.sort(Comparator.comparing((ProcessorFilter filter) -> NullSafe.string(filter.getPipelineName()))
                .thenComparing(ProcessorFilter::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        sb.append(filters.size()).append(filters.size() > MAX_FILTERS
                ? " (the first " + MAX_FILTERS + " are listed)"
                : "").append('\n');
        for (final ProcessorFilter filter : filters.subList(0, Math.min(filters.size(), MAX_FILTERS))) {
            final DocRef pipeline = filter.getPipeline();
            sb.append("- Filter ").append(filter.getId()).append(" (uuid ").append(filter.getUuid()).append(") of ")
                    .append(pipeline.getType()).append(' ').append(DocumentInfoTool.link(pipeline.copy()
                            .name(NullSafe.nonBlankStringElse(filter.getPipelineName(), pipeline.getName()))
                            .build()))
                    .append(": ").append(filter.isEnabled()
                            ? "enabled"
                            : "disabled")
                    .append(", priority ").append(filter.getPriority());
            if (filter.isReprocess()) {
                sb.append(", reprocessing");
            }
            final ProcessorFilterTracker tracker = filter.getProcessorFilterTracker();
            if (tracker != null) {
                if (tracker.getStatus() != null) {
                    sb.append(", ").append(tracker.getStatus().getDisplayValue());
                }
                if (tracker.getMetaCount() != null) {
                    sb.append(", ").append(tracker.getMetaCount()).append(" stream(s)");
                }
                if (tracker.getEventCount() != null) {
                    sb.append(", ").append(tracker.getEventCount()).append(" event(s)");
                }
                if (tracker.getLastPollMs() != null) {
                    sb.append(", last polled ").append(DateUtil.createNormalDateTimeString(tracker.getLastPollMs()));
                }
                if (NullSafe.isNonBlankString(tracker.getMessage())) {
                    sb.append(", message: ").append(tracker.getMessage().strip());
                }
            }
            if (filter.getRunAsUser() != null) {
                sb.append(", runs as ").append(filter.getRunAsUser().toDisplayString());
            }
            sb.append('\n');
            listing.documents.add(new DocRef(ProcessorFilter.ENTITY_TYPE, filter.getUuid()));
            listing.filters++;
        }
    }

    // Counts the processing tasks of the folder's pipelines and feeds by status, as its Active Tasks tab does.
    private void appendTasks(final StringBuilder sb,
                             final DocRef folder,
                             final boolean root,
                             final Listing listing) {
        sb.append("\nProcessing tasks: ");
        if (!canManageProcessors()) {
            sb.append("you need the Manage Processors permission to see them, as the Active Tasks tab does.\n");
            return;
        }
        final List<ProcessorTaskSummary> summaries = new ArrayList<>();
        final boolean cut;
        try {
            final ExpressionCriteria criteria = new ExpressionCriteria(root
                    ? ExpressionOperator.builder().build()
                    : ProcessorTaskExpressionUtil.createFolderExpression(folder));
            // One more than the most, to tell if there are more.
            criteria.setPageRequest(new PageRequest(0, MAX_TASK_ROWS + 1));
            final List<ProcessorTaskSummary> found = NullSafe.list(NullSafe.get(
                    processorTaskServiceProvider.get().findSummary(criteria), ResultPage::getValues));
            cut = found.size() > MAX_TASK_ROWS;
            final Map<String, Boolean> feeds = new HashMap<>();
            for (final ProcessorTaskSummary summary : found.subList(0, Math.min(found.size(), MAX_TASK_ROWS))) {
                if (documentAccess.canView(summary.getPipeline())
                    && feeds.computeIfAbsent(NullSafe.string(summary.getFeed()), this::canViewFeed)) {
                    summaries.add(summary);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the processing tasks of " + folder, e);
            sb.append("couldn't be found.\n");
            return;
        }
        if (summaries.isEmpty()) {
            sb.append("none you can see.\n");
            return;
        }
        // By pipeline and feed, with the count of each status.
        final Map<String, Map<String, Long>> byPipelineAndFeed = new TreeMap<>();
        for (final ProcessorTaskSummary summary : summaries) {
            final String key = summary.getPipeline().getType() + " "
                               + DocumentInfoTool.link(summary.getPipeline()) + ", feed "
                               + NullSafe.nonBlankStringElse(summary.getFeed(), "?");
            byPipelineAndFeed.computeIfAbsent(key, k -> new TreeMap<>())
                    .merge(summary.getStatus() == null
                            ? "Unknown"
                            : summary.getStatus().getDisplayValue(), summary.getCount(), Long::sum);
        }
        sb.append(cut
                ? "the first " + MAX_TASK_ROWS + " rows of counts (there are more)"
                : "counts by status").append('\n');
        byPipelineAndFeed.forEach((key, counts) -> {
            final List<String> parts = new ArrayList<>();
            counts.forEach((status, count) -> parts.add(count + " " + status));
            sb.append("- ").append(key).append(": ").append(String.join(", ", parts)).append('\n');
        });
    }

    /// @return True if the user may manage processors, as the Processors and Active Tasks tabs need. False if they
    /// may not, or if checking fails (failing closed).
    private boolean canManageProcessors() {
        try {
            return securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to check the Manage Processors permission", e);
            return false;
        }
    }

    /// @return True if the user can view a feed of this name. False if not, or if it can't be found.
    private boolean canViewFeed(final String name) {
        if (name.isBlank()) {
            return false;
        }
        try {
            return docFinderProvider.get().findByName(FeedDoc.TYPE, name).stream()
                    .anyMatch(documentAccess::canView);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the feed " + name, e);
            return false;
        }
    }

    private static String joinPath(final String parent, final String name) {
        return ("/".equals(parent)
                ? ""
                : NullSafe.string(parent)) + "/" + name;
    }

    @Override
    public boolean readsUntrustedContent() {
        // Names, tags and processor filter messages are given by other users.
        return true;
    }


    // --------------------------------------------------------------------------------


    /// A document listed, at its level below the folder (1 for one directly in it).
    ///
    /// @param onlyFollowed True for a folder listed only to show where the documents below it are.
    /// @param ignored The ignored folder it is (or is in), if an AGENTS doc says to ignore it, else null.
    private record Entry(ExplorerNode node, int level, boolean onlyFollowed, Ignored ignored) {

    }


    // --------------------------------------------------------------------------------


    /// How the documents are looked through: which folders the AGENTS docs ignore, and what was left out.
    private static final class Scan {

        private final Standing standing;
        private final boolean includeIgnored;
        private int ignoredFolders;
        private int ignoredDocuments;

        private Scan(final Standing standing, final boolean includeIgnored) {
            this.standing = standing;
            this.includeIgnored = includeIgnored;
        }

        /// @return The ignored folder the path is (or is in), or null if it isn't ignored or ignoring is off.
        private Ignored ignored(final String path) {
            if (includeIgnored || !standing.ignoresAny()) {
                return null;
            }
            return standing.ignored(path).orElse(null);
        }
    }


    // --------------------------------------------------------------------------------


    /// What was listed, for the audit log.
    private static final class Listing {

        private final List<DocRef> documents = new ArrayList<>();
        private final List<Long> streams = new ArrayList<>();
        private int filters;
    }
}
