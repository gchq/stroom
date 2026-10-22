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
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TabSnapshots.UnsavedPart;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.XsltLookupAnalyser.Lookup;
import stroom.ai.impl.tools.XsltLookupAnalyser.MapWrite;
import stroom.ai.impl.tools.XsltLookupAnalyser.Resolution;
import stroom.ai.impl.tools.XsltLookupAnalyser.Stylesheet;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiSteppingElement;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocFinder;
import stroom.docstore.shared.AbstractDoc;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.security.shared.DocumentPermission;
import stroom.util.concurrent.LazyValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// `xslt_lookups` - works out the reference data lookups XSLTs make (see [XsltLookupAnalyser]) and, with
/// loaders, which reference loaders write each map: it follows each XSLT's pipelines (through the dependency
/// graph and pipeline inheritance) to their effective reference pipelines (the parents' references, plus those
/// added, less those removed), and finds the `<map>` elements each loader's XSLT writes. It flags lookups whose
/// map no loader writes, and maps a loader writes that no XSLT in the pipeline looks up.
///
/// It reports structure only, not the reference data itself. Every XSLT, pipeline and loader read is checked
/// for View first; loaders the user can't view are left out silently and only counted.
class XsltLookupsTool extends AbstractWorkspaceTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(XsltLookupsTool.class);

    static final String NAME = "xslt_lookups";
    static final String UUIDS_ARG = "uuids";
    static final String MAP_ARG = "map";
    static final String LOADERS_ARG = "loaders";
    /// The most pipelines followed in one call.
    static final int MAX_PIPELINES = 50;
    /// How far pipeline inheritance is followed, down or up.
    static final int MAX_INHERITANCE = 10;
    /// How many levels of child pipelines are followed from a pipeline that uses an XSLT.
    static final int MAX_CHILD_DEPTH = 3;
    static final String NESTED_MAP_SEPARATOR = "/";

    private static final Pattern UUID_IN_HREF = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final Provider<DocDependencyService> docDependencyServiceProvider;
    private final Provider<DocFinder> docFinderProvider;
    private final DocumentAccess access;
    private final AiAuditLog auditLog;
    private final TabSnapshots tabSnapshots;

    @Inject
    XsltLookupsTool(final ChatWorkspace workspace,
                    final Provider<DocDependencyService> docDependencyServiceProvider,
                    final Provider<DocFinder> docFinderProvider,
                    final DocumentAccess access,
                    final AiAuditLog auditLog,
                    final TabSnapshots tabSnapshots) {
        super(workspace);
        this.docDependencyServiceProvider = docDependencyServiceProvider;
        this.docFinderProvider = docFinderProvider;
        this.access = access;
        this.auditLog = auditLog;
        this.tabSnapshots = tabSnapshots;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Works out the reference data lookups (stroom:lookup and stroom:bitmap-lookup) XSLTs "
                             + "make, from the XSLT itself: each with its line, template, key expression and map "
                             + "name, resolved through variables, parameters and included XSLTs (a literal, one "
                             + "of several values, or dynamic with its constant parts - never guessed). With "
                             + "loaders (default true), also which reference loader pipelines (the XSLT's "
                             + "pipelines' reference pipelines) write each map, from the <map> elements their "
                             + "XSLTs write; flags lookups whose map no loader writes (a likely cause of empty "
                             + "lookups) and maps no XSLT looks up. Give XSLT UUIDs, or pipeline UUIDs to analyse "
                             + "their XSLTs. Structure only: it doesn't read the reference data itself.")
                .parameters(ObjectSchema.builder()
                        .addProperty(UUIDS_ARG, stringArraySchema("XSLTs, or pipelines whose XSLTs to "
                                                                     + "analyse."))
                        .addStringProperty(MAP_ARG, "Only report lookups that are, or may be, of this map.")
                        .addBooleanProperty(LOADERS_ARG, "False to skip finding the loaders (default true).")
                        .addStringProperty(OUT_ARG, ToolDescription.builder()
                                .chatOnly("A file in /outputs/ to write the whole report to.")
                                .build())
                        .required(UUIDS_ARG)
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final WorkspacePath out = outArg(request);
        final List<String> uuids = stringListArg(request, UUIDS_ARG);
        if (uuids.isEmpty()) {
            throw new WorkspaceException("Give the UUIDs of XSLTs or pipelines.");
        }
        final String mapFilter = stringArg(request, MAP_ARG);
        final boolean loaders = booleanArg(request, LOADERS_ARG, true);

        final Analysis analysis = new Analysis(context);
        final List<String> problems = new ArrayList<>();
        for (final String uuid : uuids) {
            final Optional<DocRef> found = access.findDocRef(uuid);
            if (found.isEmpty()) {
                problems.add(uuid + ": " + DocumentLinksTool.NOT_FOUND);
            } else if (XsltDoc.TYPE.equals(found.get().getType())) {
                analysis.addXslt(found.get(), null);
                if (loaders) {
                    for (final DocRef pipeline : analysis.pipelinesUsing(found.get())) {
                        analysis.addXslt(found.get(), pipeline);
                    }
                }
            } else if (PipelineDoc.TYPE.equals(found.get().getType())) {
                final Effective effective = analysis.effective(found.get());
                if (effective == null) {
                    problems.add(uuid + ": the pipeline, or one it inherits from, couldn't be read.");
                } else {
                    analysis.pipelinesInScope.add(found.get());
                    for (final DocRef xslt : effective.xslts()) {
                        if (access.canView(xslt)) {
                            analysis.addXslt(xslt, found.get());
                        } else {
                            analysis.notVisible++;
                        }
                    }
                }
            } else {
                problems.add(uuid + ": " + DocumentLinksTool.describe(found.get())
                             + " isn't an XSLT or a pipeline.");
            }
        }

        final List<String> lines = new ArrayList<>();
        for (final Map.Entry<DocRef, Set<DocRef>> entry : analysis.xsltPipelines.entrySet()) {
            ToolDeadline.check();
            report(entry.getKey(), entry.getValue(), analysis, loaders, mapFilter, lines, problems);
        }
        if (loaders) {
            reportUnusedMaps(analysis, lines);
        }

        logView(context, analysis);
        final StringBuilder sb = new StringBuilder();
        sb.append("Analysed ").append(analysis.xsltPipelines.size()).append(" XSLT(s)");
        if (loaders) {
            sb.append(" in ").append(analysis.pipelinesInScope.size()).append(" pipeline(s), with ")
                    .append(analysis.loaders.size()).append(" reference loader(s)");
        }
        sb.append(": ").append(analysis.lookupCount).append(" lookup(s)");
        if (mapFilter != null) {
            sb.append(" that are, or may be, of map '").append(mapFilter).append('\'');
        }
        sb.append('.');
        if (analysis.notVisible > 0) {
            sb.append(' ').append(analysis.notVisible).append(" document(s) weren't visible to you, so were left "
                                                             + "out.");
        }
        final List<String> unreadable = analysis.analyser.unreadableIncludes();
        if (!unreadable.isEmpty()) {
            sb.append(" Included XSLTs that couldn't be read (not found, or not visible to you), so variables "
                      + "defined in them couldn't be followed: ").append(String.join(", ", unreadable))
                    .append('.');
        }
        problems.forEach(problem -> sb.append("\n").append(problem));
        if (out != null) {
            workspace.write(out, String.join("\n", lines) + "\n", null);
            sb.append("\nWrote the whole report to ").append(out).append('.');
        }
        if (!lines.isEmpty()) {
            sb.append("\n\n").append(boundedLines(context, lines, lines.size()));
        }
        return ToolResult.text(sb.toString());
    }

    private void report(final DocRef xslt,
                        final Set<DocRef> pipelines,
                        final Analysis analysis,
                        final boolean loaders,
                        final String mapFilter,
                        final List<String> lines,
                        final List<String> problems) {
        final List<Lookup> lookups;
        try {
            lookups = analysis.lookups(xslt);
        } catch (final WorkspaceException e) {
            problems.add(e.getMessage());
            return;
        }
        if (lookups == null) {
            problems.add(xslt.getUuid() + ": the XSLT couldn't be read.");
            return;
        }
        lines.add("XSLT '" + xslt.getName() + "' (" + xslt.getUuid() + ") in " + access.path(xslt)
                  + (analysis.unsaved.contains(xslt.getUuid())
                ? ", as edited in the tab the user attached (unsaved)"
                : "")
                  + (loaders
                ? (pipelines.isEmpty()
                        ? ", used by no pipeline you can view, so its loaders can't be found"
                        : ", used by pipeline(s) " + pipelines.stream()
                                .map(pipeline -> "'" + pipeline.getName() + "' (" + pipeline.getUuid() + ")")
                                .collect(Collectors.joining(", ")))
                : "") + ":");
        int shown = 0;
        for (final Lookup lookup : lookups) {
            if (mapFilter != null && !mayBeMap(lookup.map(), mapFilter)) {
                continue;
            }
            shown++;
            analysis.lookupCount++;
            lines.add("- line " + lookup.line() + ", column " + lookup.column() + ", in " + lookup.where()
                      + ": stroom:" + lookup.function() + " of map " + lookup.map().describe() + ", key "
                      + lookup.keyExpression());
            if (!lookup.chain().isEmpty()) {
                lines.add("    resolved through: " + String.join(" → ", lookup.chain()));
            }
            if (loaders && !pipelines.isEmpty()) {
                lines.addAll(loaderLines(lookup, pipelines, analysis));
            }
        }
        if (shown == 0) {
            lines.add("- no lookups" + (mapFilter == null
                    ? ""
                    : " that are, or may be, of map '" + mapFilter + "'"));
        }
    }

    /// @return Which loaders write a lookup's map, or a flag if none do.
    private List<String> loaderLines(final Lookup lookup, final Set<DocRef> pipelines, final Analysis analysis) {
        final List<String> lines = new ArrayList<>();
        final List<Loader> loaders = pipelines.stream()
                .flatMap(pipeline -> analysis.loadersOf(pipeline).stream())
                .distinct()
                .toList();
        final Resolution map = lookup.map();
        if (map.values().isEmpty()) {
            final List<String> possible = loaders.stream()
                    .filter(loader -> loader.writes().stream().anyMatch(write -> writes(write, map)))
                    .map(Loader::describe)
                    .toList();
            lines.add("    CAN'T TELL which loader it uses, as the map name is worked out at run time; possible "
                      + "loaders: " + (possible.isEmpty()
                    ? "none that write a map it could be"
                    : String.join("; ", possible)));
            return lines;
        }
        for (final String name : map.values()) {
            for (final String segment : name.split(NESTED_MAP_SEPARATOR)) {
                final List<String> writers = new ArrayList<>();
                for (final Loader loader : loaders) {
                    for (final MapWrite write : loader.writes()) {
                        if (write.map().values().contains(segment)) {
                            writers.add(loader.describe() + ", XSLT '" + write.xsltName() + "' line " + write.line()
                                        + " (definite)");
                        } else if (write.map().mayBe(segment)) {
                            writers.add(loader.describe() + ", XSLT '" + write.xsltName() + "' line " + write.line()
                                        + " (possible: its map name is " + write.map().describe() + ")");
                        }
                    }
                }
                if (writers.isEmpty()) {
                    lines.add("    NO LOADER writes map '" + segment + "' in the reference pipelines of "
                              + pipelines.stream().map(pipeline -> "'" + pipeline.getName() + "'")
                                      .collect(Collectors.joining(", "))
                              + (map.dynamic() || map.values().size() > 1
                            ? " (possible: the map name isn't certain)"
                            : "")
                              + "; this is a likely cause of empty lookups.");
                } else {
                    lines.add("    map '" + segment + "' is written by: " + String.join("; ", writers));
                }
            }
        }
        if (map.dynamic()) {
            lines.add("    The map name may also be worked out at run time, so other loaders may be used.");
        }
        return lines;
    }

    private void reportUnusedMaps(final Analysis analysis, final List<String> lines) {
        for (final DocRef pipeline : analysis.pipelinesInScope) {
            final List<String> unused = new ArrayList<>();
            final List<Lookup> lookups = new ArrayList<>();
            boolean incomplete = false;
            final Effective effective = analysis.effective(pipeline);
            for (final DocRef xslt : effective == null
                    ? List.<DocRef>of()
                    : effective.xslts()) {
                final List<Lookup> xsltLookups = access.canView(xslt)
                        ? analysis.lookupsQuietly(xslt)
                        : null;
                if (xsltLookups == null) {
                    incomplete = true;
                } else {
                    lookups.addAll(xsltLookups);
                }
            }
            for (final Loader loader : analysis.loadersOf(pipeline)) {
                for (final MapWrite write : loader.writes()) {
                    final boolean used = lookups.stream().anyMatch(lookup -> writes(write, lookup.map()));
                    if (!used) {
                        unused.add("- map " + write.map().describe() + " written by " + loader.describe()
                                   + ", XSLT '" + write.xsltName() + "' line " + write.line()
                                   + (write.map().dynamic() || incomplete
                                ? " (possible)"
                                : ""));
                    }
                }
            }
            if (!unused.isEmpty()) {
                lines.add("Pipeline '" + pipeline.getName() + "' (" + pipeline.getUuid() + "): maps its loaders "
                          + "write that no XSLT in the pipeline looks up" + (incomplete
                        ? " (not all of its XSLTs could be read)"
                        : "") + ":");
                lines.addAll(unused);
            }
        }
    }

    /// @return True if a loader's map write may be one a lookup needs.
    private static boolean writes(final MapWrite write, final Resolution lookupMap) {
        if (write.map().dynamic()) {
            // A map name worked out at run time may be any.
            return true;
        }
        return write.map().values().stream().anyMatch(written -> mayBeMap(lookupMap, written));
    }

    private static boolean mayBeMap(final Resolution map, final String name) {
        if (map.mayBe(name)) {
            return true;
        }
        return map.values().stream()
                .anyMatch(value -> List.of(value.split(NESTED_MAP_SEPARATOR)).contains(name));
    }

    private void logView(final ToolCallContext context, final Analysis analysis) {
        auditLog.documentsViewed(context.session(), List.copyOf(analysis.read), "Read by the " + NAME + " tool to find "
                                                                     + "reference data lookups");
    }

    @Override
    public boolean readsUntrustedContent() {
        // XSLTs are written by users.
        return true;
    }

    /// A pipeline's structure, with what it inherits.
    ///
    /// @param xslts      The XSLTs its elements use.
    /// @param references Its reference pipelines.
    record Effective(List<DocRef> xslts, List<PipelineReference> references) {

    }

    /// A reference loader of a pipeline, and the maps its XSLTs write.
    ///
    /// @param pipeline   The loader pipeline.
    /// @param feed       The feed its reference data comes from, if the user can view it, else null.
    /// @param streamType The type of stream it loads.
    /// @param writes     The `<map>` elements its XSLTs write.
    record Loader(DocRef pipeline, DocRef feed, String streamType, List<MapWrite> writes) {

        /// @return The loader in words: its pipeline, feed and stream type.
        String describe() {
            return "loader pipeline '" + pipeline.getName() + "' (" + pipeline.getUuid() + ")"
                   + (feed == null
                    ? ""
                    : ", feed '" + feed.getName() + "'")
                   + (streamType == null
                    ? ""
                    : ", stream type '" + streamType + "'");
        }
    }

    /// One call's analysis: what has been read, and what was found.
    private final class Analysis {

        private final XsltLookupAnalyser analyser = new XsltLookupAnalyser(this::include);
        private final Map<DocRef, Set<DocRef>> xsltPipelines = new LinkedHashMap<>();
        private final Set<DocRef> pipelinesInScope = new LinkedHashSet<>();
        private final Map<String, Optional<Effective>> effective = new HashMap<>();
        private final Map<String, Optional<List<Lookup>>> lookups = new HashMap<>();
        private final Map<String, Optional<List<MapWrite>>> mapWrites = new HashMap<>();
        private final Map<String, List<Loader>> pipelineLoaders = new HashMap<>();
        private final Set<Loader> loaders = new LinkedHashSet<>();
        private final Set<DocRef> read = new LinkedHashSet<>();
        private int notVisible;
        private int lookupCount;
        /// The UUIDs of the XSLTs read as the user has them in their attached tab, unsaved.
        private final Set<String> unsaved = new LinkedHashSet<>();
        private final ToolCallContext context;
        // The snapshot sent with the latest question, read once, when first needed.
        private final LazyValue<Optional<StoredTabSnapshot>> tab;

        private Analysis(final ToolCallContext context) {
            this.context = context;
            this.tab = LazyValue.initialisedBy(() -> tabSnapshots.forLatestQuestion(chatId()));
        }

        // The chat, for the tab the user attached to it.
        private int chatId() {
            return context.session().requireChatId(NAME);
        }

        private void addXslt(final DocRef xslt, final DocRef pipeline) {
            final Set<DocRef> pipelines = xsltPipelines.computeIfAbsent(withName(xslt), k -> new LinkedHashSet<>());
            if (pipeline != null) {
                pipelines.add(pipeline);
                pipelinesInScope.add(pipeline);
            }
        }

        /// @return The lookups of an XSLT, or null if it couldn't be read.
        /// @throws WorkspaceException if it isn't XML.
        private List<Lookup> lookups(final DocRef xslt) {
            final Optional<List<Lookup>> cached = lookups.get(xslt.getUuid());
            if (cached != null) {
                return cached.orElse(null);
            }
            final Optional<Stylesheet> stylesheet = stylesheet(xslt);
            final List<Lookup> result = stylesheet.map(analyser::lookups).orElse(null);
            lookups.put(xslt.getUuid(), Optional.ofNullable(result));
            return result;
        }

        private List<Lookup> lookupsQuietly(final DocRef xslt) {
            try {
                return lookups(xslt);
            } catch (final WorkspaceException e) {
                return null;
            }
        }

        private Optional<Stylesheet> stylesheet(final DocRef xslt) {
            final Optional<XsltDoc> doc = access.read(xslt, XsltDoc.class);
            doc.ifPresent(found -> read.add(xslt));
            return doc.map(found -> XsltLookupAnalyser.parse(xslt.getName(), xslt.getUuid(), text(xslt, found)));
        }

        /// @return An XSLT's text: as the user has it in their attached tab, unsaved, if it is shown there (as the
        /// tab's document, or the selected element while stepping) and was shared in full; else as saved.
        private String text(final DocRef xslt, final XsltDoc saved) {
            final Optional<UnsavedPart> edited = tabSnapshots.unsavedPart(chatId(), xslt, AiTabSnapshotPart.DATA)
                    .or(() -> steppingCode(xslt))
                    .filter(part -> !part.cut());
            edited.ifPresent(part -> unsaved.add(xslt.getUuid()));
            return edited.map(UnsavedPart::text).orElse(saved.getData());
        }

        /// @return The code of the element selected in the user's stepping screen, if it uses the XSLT.
        private Optional<UnsavedPart> steppingCode(final DocRef xslt) {
            return tab.getValueWithoutLocks().filter(snapshot -> snapshot.stepping() != null)
                    .filter(snapshot -> {
                        final AiSteppingElement selected = snapshot.stepping().getSelectedElement();
                        return selected != null
                               && selected.getDocRef() != null
                               && Objects.equals(selected.getDocRef().getUuid(), xslt.getUuid());
                    })
                    .flatMap(snapshot -> tabSnapshots.part(chatId(), snapshot, AiTabSnapshotPart.STEP_CODE));
        }

        /// Finds an included XSLT as Stroom does: by name, else by UUID.
        private Optional<Stylesheet> include(final String href) {
            try {
                List<DocRef> docRefs = NullSafe.list(docFinderProvider.get().findByName(XsltDoc.TYPE, href, false));
                if (docRefs.isEmpty()) {
                    final Matcher matcher = UUID_IN_HREF.matcher(href);
                    docRefs = matcher.find()
                            ? List.of(new DocRef(XsltDoc.TYPE, matcher.group()))
                            : List.of();
                }
                if (docRefs.size() != 1 || !access.canView(docRefs.getFirst())) {
                    return Optional.empty();
                }
                final DocRef docRef = docRefs.getFirst();
                return access.read(docRef, XsltDoc.class).map(doc -> {
                    read.add(docRef);
                    return XsltLookupAnalyser.parse(Objects.requireNonNullElse(docRef.getName(), href),
                            docRef.getUuid(), text(docRef, doc));
                });
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to read included XSLT " + href + ": " + e.getMessage(), e);
                return Optional.empty();
            }
        }

        /// @return The pipelines the user can view that use an XSLT, directly or through inheritance.
        private List<DocRef> pipelinesUsing(final DocRef xslt) {
            final List<DocRef> result = new ArrayList<>();
            List<DocRef> level = dependantPipelines(xslt);
            final Set<String> seen = new LinkedHashSet<>();
            for (int depth = 0; depth <= MAX_CHILD_DEPTH && !level.isEmpty(); depth++) {
                final List<DocRef> next = new ArrayList<>();
                for (final DocRef pipeline : level) {
                    if (result.size() >= MAX_PIPELINES || !seen.add(pipeline.getUuid())) {
                        continue;
                    }
                    final Effective found = effective(pipeline);
                    if (found != null && found.xslts().contains(xslt)) {
                        result.add(pipeline);
                        // Pipelines that inherit from this one use it too, unless they replace it.
                        for (final DocRef child : dependantPipelines(pipeline)) {
                            if (isChildOf(child, pipeline)) {
                                next.add(child);
                            }
                        }
                    }
                }
                level = next;
            }
            return result;
        }

        private boolean isChildOf(final DocRef child, final DocRef parent) {
            return access.read(child, PipelineDoc.class)
                    .map(PipelineDoc::getParentPipeline)
                    .map(parentRef -> parent.getUuid().equals(parentRef.getUuid()))
                    .orElse(false);
        }

        /// @return The pipelines with a link to a document, that the user can view.
        private List<DocRef> dependantPipelines(final DocRef docRef) {
            final DependencyCriteria criteria = new DependencyCriteria();
            criteria.setPageRequest(new PageRequest(0, DocumentLinksTool.MAX_EDGES_PER_BATCH));
            criteria.setPartialName(DependencyCriteria.FIELD_DEF_TO_UUID.getFilterQualifier() + ":="
                                    + docRef.getUuid());
            try {
                final ResultPage<Dependency> page = docDependencyServiceProvider.get()
                        .fetchDependencies(criteria, Set.of());
                return NullSafe.list(NullSafe.get(page, ResultPage::getValues)).stream()
                        .map(Dependency::getFrom)
                        .filter(from -> from != null && PipelineDoc.TYPE.equals(from.getType()))
                        .filter(access::canView)
                        .distinct()
                        .toList();
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to read the dependants of " + docRef + ": " + e.getMessage(), e);
                return List.of();
            }
        }

        /// @return A pipeline's structure with what it inherits, or null if it, or a pipeline it inherits from,
        /// can't be read.
        private Effective effective(final DocRef pipeline) {
            return effective.computeIfAbsent(pipeline.getUuid(), uuid -> Optional.ofNullable(merge(pipeline)))
                    .orElse(null);
        }

        private Effective merge(final DocRef pipeline) {
            final List<PipelineLayer> layers = new ArrayList<>();
            final Set<String> seen = new LinkedHashSet<>();
            DocRef current = pipeline;
            while (current != null) {
                if (!seen.add(current.getUuid()) || seen.size() > MAX_INHERITANCE) {
                    return null;
                }
                final Optional<PipelineDoc> doc = access.read(current, PipelineDoc.class);
                if (doc.isEmpty()) {
                    // Failing closed: what it inherits can't be known.
                    return null;
                }
                read.add(current);
                if (doc.get().getPipelineData() != null) {
                    layers.addFirst(new PipelineLayer(current, doc.get().getPipelineData()));
                }
                current = doc.get().getParentPipeline();
            }
            try {
                final PipelineDataMerger merger = new PipelineDataMerger().merge(layers);
                final List<DocRef> xslts = merger.getProperties().values().stream()
                        .flatMap(properties -> properties.values().stream())
                        .map(PipelineProperty::getValue)
                        .filter(value -> value != null && value.getEntity() != null)
                        .map(PipelinePropertyValue::getEntity)
                        .filter(entity -> XsltDoc.TYPE.equals(entity.getType()))
                        .distinct()
                        .toList();
                final List<PipelineReference> references = merger.getPipelineReferences().values().stream()
                        .flatMap(byName -> byName.values().stream())
                        .flatMap(Collection::stream)
                        .distinct()
                        .toList();
                return new Effective(xslts, references);
            } catch (final PipelineModelException e) {
                LOGGER.debug(() -> "Unable to merge pipeline " + pipeline + ": " + e.getMessage(), e);
                return null;
            }
        }

        /// @return The reference loaders of a pipeline the user can view, with the maps they write.
        private List<Loader> loadersOf(final DocRef pipeline) {
            return pipelineLoaders.computeIfAbsent(pipeline.getUuid(), uuid -> {
                final Effective found = effective(pipeline);
                if (found == null) {
                    return List.of();
                }
                final List<Loader> result = new ArrayList<>();
                for (final PipelineReference reference : found.references()) {
                    final DocRef loaderRef = reference.getPipeline();
                    final Effective loader = loaderRef != null && access.canView(loaderRef)
                            ? effective(loaderRef)
                            : null;
                    if (loader == null) {
                        notVisible++;
                        continue;
                    }
                    final List<MapWrite> writes = new ArrayList<>();
                    for (final DocRef xslt : loader.xslts()) {
                        final List<MapWrite> xsltWrites = mapWrites(xslt);
                        if (xsltWrites == null) {
                            notVisible++;
                        } else {
                            writes.addAll(xsltWrites);
                        }
                    }
                    final DocRef feed = reference.getFeed() != null && access.canView(reference.getFeed())
                            ? reference.getFeed()
                            : null;
                    final Loader loaderInfo = new Loader(withName(loaderRef), feed, reference.getStreamType(),
                            List.copyOf(writes));
                    result.add(loaderInfo);
                    loaders.add(loaderInfo);
                }
                return List.copyOf(result);
            });
        }

        private DocRef withName(final DocRef docRef) {
            if (docRef.getName() != null) {
                return docRef;
            }
            return access.read(docRef, AbstractDoc.class)
                    .map(doc -> new DocRef(docRef.getType(), docRef.getUuid(), doc.getName()))
                    .orElse(docRef);
        }

        /// @return The maps a loader's XSLT writes, or null if it can't be read.
        private List<MapWrite> mapWrites(final DocRef xslt) {
            return mapWrites.computeIfAbsent(xslt.getUuid(), uuid -> {
                if (!access.canView(xslt)) {
                    return Optional.empty();
                }
                try {
                    return stylesheet(xslt).map(analyser::mapWrites);
                } catch (final WorkspaceException e) {
                    LOGGER.debug(() -> "Unable to read loader XSLT " + xslt + ": " + e.getMessage(), e);
                    return Optional.empty();
                }
            }).orElse(null);
        }
    }
}
