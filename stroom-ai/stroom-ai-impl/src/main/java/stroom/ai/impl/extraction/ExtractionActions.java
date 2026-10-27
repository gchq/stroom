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

package stroom.ai.impl.extraction;

import stroom.ai.impl.DashboardChanges;
import stroom.ai.impl.DashboardChanges.Change;
import stroom.ai.impl.extraction.ExtractionPlan.Existing;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.vis.VisualisationActions;
import stroom.ai.shared.AiChangeExtraction;
import stroom.ai.shared.AiCreateExtraction;
import stroom.ai.shared.AiWriteExtractionRequest;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.xslt.XsltStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Makes the search extraction the agent proposed for a dashboard table, when the user clicks its
/// `create_extraction_pipeline` button: first [#prepare] says exactly what would be written (an XSLT, written by
/// Stroom from the fields, and a pipeline using it); once the user has chosen the name and folder, [#write] writes
/// it, as the user, refusing anything but what was prepared. The browser then sets the table up to use it, as
/// unsaved changes.
///
/// The pipeline is a child of the content pack's Search Extraction template, setting only its XSLT, if the user can
/// use the template; else a pipeline of its own as the template makes.
public class ExtractionActions {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExtractionActions.class);

    /// The longest a new pipeline's name may be.
    static final int MAX_NAME_CHARS = 100;
    /// The table setting holding its extraction pipeline (see `DashboardSettings`), which turns extraction on.
    static final String EXTRACTION_PIPELINE_SETTING = "extractionPipeline";
    // How far the documents that use an XSLT are followed through pipelines (e.g. XSLT, pipeline, child pipeline,
    // dashboard).
    private static final int MAX_USE_DEPTH = 4;
    // The most documents that use an XSLT that are named.
    private static final int MAX_USED_BY = 30;
    // The most links read at each step.
    private static final int MAX_DEPENDENCIES = 1_000;

    private final Provider<ExplorerService> explorerServiceProvider;
    private final Provider<ExplorerNodeService> explorerNodeServiceProvider;
    private final Provider<XsltStore> xsltStoreProvider;
    private final Provider<PipelineStore> pipelineStoreProvider;
    private final Provider<DocDependencyService> docDependencyServiceProvider;
    private final ExtractionEvents extractionEvents;
    private final DocumentAccess documentAccess;
    private final SecurityContext securityContext;

    @Inject
    public ExtractionActions(final Provider<ExplorerService> explorerServiceProvider,
                             final Provider<ExplorerNodeService> explorerNodeServiceProvider,
                             final Provider<XsltStore> xsltStoreProvider,
                             final Provider<PipelineStore> pipelineStoreProvider,
                             final Provider<DocDependencyService> docDependencyServiceProvider,
                             final ExtractionEvents extractionEvents,
                             final DocumentAccess documentAccess,
                             final SecurityContext securityContext) {
        this.explorerServiceProvider = explorerServiceProvider;
        this.explorerNodeServiceProvider = explorerNodeServiceProvider;
        this.xsltStoreProvider = xsltStoreProvider;
        this.pipelineStoreProvider = pipelineStoreProvider;
        this.docDependencyServiceProvider = docDependencyServiceProvider;
        this.extractionEvents = extractionEvents;
        this.documentAccess = documentAccess;
        this.securityContext = securityContext;
    }

    /// Says exactly what would be written for an extraction, with the hash the write must be given. Writes
    /// nothing.
    ///
    /// @param dashboard The dashboard.
    /// @param name      The name to suggest for the new pipeline and its XSLT.
    /// @param plan      The extraction.
    /// @param session   The chat it is for, to record the events read.
    /// @return What would be written, with the hash the write must be given.
    public AiCreateExtraction prepare(final DocRef dashboard,
                                      final String name,
                                      final ExtractionPlan plan,
                                      final ToolSession session) {
        final Content content = content(dashboard, plan, session);
        final ExplorerNode folder = explorerNodeServiceProvider.get().getParent(dashboard)
                .map(VisualisationActions::inExplorerTree)
                .orElse(null);
        final List<String> fields = plan.fields().stream()
                .map(field -> field.name() + " (" + field.type() + "), from " + field.xpath())
                .toList();
        return new AiCreateExtraction(name, folder, dashboard, plan.componentId(), plan.component(),
                content.base(), content.xslt(), fields, describeChanges(plan), content.xsltDescription(),
                content.pipelineDescription(), NullSafe.get(plan.existing(), Existing::pipeline), content.hash());
    }

    /// Writes the extraction once the user has chosen its name and folder, as the user: the XSLT, then the pipeline
    /// using it, both in the folder they chose, with the name they chose. If either can't be written, any already made is removed (as
    /// the processing user, as the user may not be able to delete what they made); if that fails too, the user is
    /// told what was kept. (A failure inside Stroom's explorer between making a document and its node leaves the
    /// document with no node, which can't be removed from here.)
    ///
    /// @param dashboard The dashboard.
    /// @param plan      The extraction.
    /// @param request   The name and folder the user chose, and the hash of what they were shown.
    /// @param session   The chat it is for, to record the events read.
    /// @return The new pipeline, and the changes to make to the table to use it.
    /// @throws IllegalStateException saying why nothing was written, e.g. what would be written has changed
    /// since the user was shown it, or they can't create documents in the folder.
    public Written write(final DocRef dashboard,
                         final ExtractionPlan plan,
                         final AiWriteExtractionRequest request,
                         final ToolSession session) {
        final Content content = content(dashboard, plan, session);
        if (request == null || !content.hash().equals(request.getContentHash())) {
            throw new IllegalStateException("What would be written has changed since you were shown it, so nothing "
                                            + "was written. Use the button again to see what would be.");
        }
        final String name = NullSafe.get(request.getName(), String::strip);
        if (NullSafe.isBlankString(name) || name.length() > MAX_NAME_CHARS
            || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("Give the pipeline a name of up to " + MAX_NAME_CHARS + " characters.");
        }
        final PermissionInheritance inheritance = Objects.requireNonNullElse(request.getPermissionInheritance(),
                PermissionInheritance.DESTINATION);
        // The New dialog offers only these; the others would leave the new documents without their owner's
        // permissions.
        if (inheritance != PermissionInheritance.NONE && inheritance != PermissionInheritance.DESTINATION) {
            throw new IllegalStateException("New documents take their permissions from their folder or from none "
                                            + "of it, so nothing was written.");
        }
        final DocRef folderRef = request.getFolder();
        final ExplorerNodeService nodeService = explorerNodeServiceProvider.get();
        final ExplorerNode folder = folderRef == null || NullSafe.isBlankString(folderRef.getUuid())
                ? null
                : nodeService.getNode(folderRef).orElse(null);
        if (folder == null) {
            throw new IllegalStateException("The folder chosen no longer exists, so nothing was written.");
        }
        for (final String type : List.of(XsltDoc.TYPE, PipelineDoc.TYPE)) {
            if (!securityContext.hasDocumentCreatePermission(folder.getDocRef(), type)) {
                throw new IllegalStateException("You can't create " + type + " documents in folder '"
                                                + folder.getName() + "', so nothing was written. Choose another "
                                                + "folder.");
            }
        }
        final ExplorerService explorerService = explorerServiceProvider.get();
        final List<ExplorerNode> created = new ArrayList<>();
        try {
            final ExplorerNode xsltNode = explorerService.create(XsltDoc.TYPE, name, folder, inheritance);
            created.add(xsltNode);
            final XsltStore xsltStore = xsltStoreProvider.get();
            final XsltDoc xsltDoc = xsltStore.readDocument(xsltNode.getDocRef());
            xsltStore.writeDocument(xsltDoc.copy()
                    .description(content.xsltDescription())
                    .data(content.xslt())
                    .build());

            final ExplorerNode pipelineNode = explorerService.create(PipelineDoc.TYPE, name, folder, inheritance);
            created.add(pipelineNode);
            final PipelineStore pipelineStore = pipelineStoreProvider.get();
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineNode.getDocRef());
            final DocRef xsltRef = new DocRef(XsltDoc.TYPE, xsltNode.getUuid(), name);
            pipelineStore.writeDocument(pipelineDoc.copy()
                    .description(content.pipelineDescription())
                    .parentPipeline(content.template())
                    .pipelineData(ExtractionPipelines.data(content.template(), content.element(), xsltRef))
                    .build());
            final DocRef pipeline = new DocRef(PipelineDoc.TYPE, pipelineNode.getUuid(), name);
            return new Written(pipeline, xsltRef, tableChanges(plan, pipeline));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to write the extraction " + name, e);
            final String why = NullSafe.nonBlankStringElse(e.getMessage(), e.getClass().getSimpleName());
            final String kept = remove(created, name);
            throw new IllegalStateException(kept == null
                    ? "The extraction couldn't be written, so nothing was kept: " + why
                    : "The extraction couldn't be written (" + why + "), and what was made of it couldn't all be "
                      + "removed: " + kept + ". Delete it from the explorer.", e);
        }
    }

    // Removes the documents made of an extraction that couldn't be written, as the processing user. Returns what
    // couldn't be removed, or null if all were.
    private String remove(final List<ExplorerNode> created, final String name) {
        if (created.isEmpty()) {
            return null;
        }
        String problem;
        try {
            problem = NullSafe.get(securityContext.asProcessingUserResult(() ->
                    explorerServiceProvider.get().delete(created)), BulkActionResult::getMessage);
        } catch (final RuntimeException e) {
            problem = NullSafe.nonBlankStringElse(e.getMessage(), e.getClass().getSimpleName());
        }
        if (NullSafe.isBlankString(problem)) {
            return null;
        }
        final String kept = String.join(", ", created.stream()
                .map(node -> node.getType() + " '" + node.getName() + "'")
                .toList());
        final String reason = problem;
        LOGGER.error(() -> LogUtil.message("Unable to remove {} after failing to write extraction {}: {}",
                kept, name, reason));
        return kept;
    }

    // What would be written for a new pipeline: the XSLT, the pipeline it inherits (the template, the table's
    // pipeline, or none) and its element given the XSLT, and the hash of these and the table changes, which the
    // write must be given.
    Content content(final DocRef dashboard, final ExtractionPlan plan, final ToolSession session) {
        // The events' namespace, as they are: not as the agent said.
        final String namespace = extractionEvents.namespace(plan.sample(), session);
        final Existing existing = plan.existing();
        final String xslt;
        final DocRef template;
        final String element;
        final String base;
        final String xsltDescription;
        if (existing == null) {
            xslt = ExtractionXslt.xslt(namespace, plan.fields());
            template = template();
            element = ExtractionPipelines.XSLT_ELEMENT;
            base = template != null
                    ? "a child of the Search Extraction template ('" + template.getName() + "'), setting only its "
                      + "XSLT"
                    : "a pipeline of its own, as the Search Extraction template makes (the template isn't one you "
                      + "can use)";
            xsltDescription = "The search extraction of table '" + plan.component() + "' on dashboard '"
                              + dashboard.getName() + "', written by Stroom AI from its fields: " + fieldNames(plan)
                              + ".";
        } else {
            // As it is now: a child gives its XSLT to the element that writes its records, which must still be the
            // one it was proposed for, and find its XSLT by the XSLT it names.
            final MergedPipeline parent = MergedPipeline.load(documentAccess, existing.pipeline().getUuid())
                    .orElseThrow(() -> new IllegalStateException("Pipeline '" + name(existing.pipeline())
                                                                 + "' no longer exists, or you can't see it, so a "
                                                                 + "new pipeline can't inherit it."));
            if (!ExtractionPipelines.outputXslt(parent).filter(existing.element()::equals).isPresent()) {
                throw new IllegalStateException("Pipeline '" + name(existing.pipeline()) + "' has changed since "
                                                + "this was proposed: its records aren't written by element '"
                                                + existing.element() + "' now. Ask for the extraction again.");
            }
            final String unchangeable = ExtractionPipelines.unchangeable(parent, existing.element()).orElse(null);
            if (unchangeable != null) {
                throw new IllegalStateException(unchangeable);
            }
            final ExtractionChange.Changed changed = current(existing, plan, namespace).changed();
            xslt = changed.code();
            template = existing.pipeline();
            element = existing.element();
            base = "a child of pipeline '" + name(existing.pipeline()) + "', with this XSLT in place of XSLT '"
                   + name(existing.xslt()) + "' (which is left as it is)";
            xsltDescription = changed.generated()
                    ? "The search extraction of table '" + plan.component() + "' on dashboard '" + dashboard.getName()
                      + "', written by Stroom AI from its fields: " + fieldNames(plan) + "."
                    : "XSLT '" + name(existing.xslt()) + "', with fields added by Stroom AI for table '"
                      + plan.component() + "' on dashboard '" + dashboard.getName() + "': " + fieldNames(plan)
                      + ".";
        }
        final String pipelineDescription = "Extracts " + fieldNames(plan) + " from the events of table '"
                                           + plan.component() + "' on dashboard '" + dashboard.getName()
                                           + "', with the XSLT of the same name. Made by Stroom AI.";
        // The pipeline's data follows from the template and the XSLT, so they stand for it.
        final String hash = ExtractionChange.sha256(String.join("\n---\n", xslt,
                NullSafe.getOrElse(template, DocRef::getUuid, "none"), element,
                String.join("\n", describeChanges(plan)), xsltDescription, pipelineDescription));
        return new Content(xslt, template, element, base, xsltDescription, pipelineDescription, hash);
    }

    // The table's extraction XSLT's code, read once, and it changed as proposed, if it is as it was then.
    private Current current(final Existing existing, final ExtractionPlan plan, final String namespace) {
        final String code = documentAccess.read(existing.xslt(), XsltDoc.class)
                .map(XsltDoc::getData)
                .orElseThrow(() -> new IllegalStateException("XSLT '" + name(existing.xslt()) + "' no longer "
                                                             + "exists, or you can't read it, so nothing was "
                                                             + "written."));
        if (!ExtractionChange.sha256(code).equals(existing.codeHash())) {
            throw new IllegalStateException(changedSince(existing));
        }
        try {
            return new Current(code, ExtractionChange.change(code, namespace, plan.fields()));
        } catch (final IllegalArgumentException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static String changedSince(final Existing existing) {
        return "XSLT '" + name(existing.xslt()) + "' has changed since this was proposed, so nothing was written. "
               + "Ask for the change again, to be shown it against the XSLT as it is now.";
    }

    /// Says exactly what changing a table's extraction pipeline in place would write: its XSLT changed, shown
    /// against the code it has now, and the other documents that use it, which change too. Writes nothing.
    ///
    /// @param dashboard The dashboard.
    /// @param plan      The extraction, a change to the table's pipeline.
    /// @param session   The chat it is for, to record the events read.
    /// @return What would be written, with the hash the write must be given.
    /// @throws IllegalStateException if the XSLT has changed since the change was proposed, can't be changed so,
    /// the user can't edit it, or what else uses it can't be found.
    public AiChangeExtraction prepareChange(final DocRef dashboard,
                                            final ExtractionPlan plan,
                                            final ToolSession session) {
        final Existing existing = requireExisting(plan);
        requireEdit(existing);
        final Current current = current(existing, plan, extractionEvents.namespace(plan.sample(), session));
        final ExtractionChange.Changed changed = current.changed();
        final String change = changed.generated()
                ? "XSLT '" + name(existing.xslt()) + "' written again by Stroom AI from the fields: "
                  + fieldNames(plan) + (changed.removed().isEmpty()
                        ? ""
                        : ". It no longer gives " + String.join(", ", changed.removed())
                          + ": anything else using it loses them")
                : "The fields " + fieldNames(plan) + " added to XSLT '" + name(existing.xslt())
                  + "', written by hand (what it gives already is left as it is)";
        final List<String> usedBy = usedBy(existing, dashboard);
        return new AiChangeExtraction(dashboard, plan.componentId(), plan.component(), existing.pipeline(),
                existing.xslt(), current.code(), changed.code(), change, usedBy, describeColumns(plan),
                changeHash(existing, changed, plan, usedBy));
    }

    /// Changes a table's extraction pipeline in place as the user confirmed, as the user: its XSLT is written with
    /// the code they were shown, only if it still has the code it was shown against, what uses it is as they were
    /// shown, and they may edit it.
    ///
    /// @param dashboard   The dashboard.
    /// @param plan        The extraction, a change to the table's pipeline.
    /// @param contentHash The hash of what the user was shown.
    /// @param session     The chat it is for, to record the events read.
    /// @return The pipeline and its XSLT, and the changes to make to the table (columns for the new fields).
    /// @throws IllegalStateException saying why nothing was written.
    public Written writeChange(final DocRef dashboard,
                               final ExtractionPlan plan,
                               final String contentHash,
                               final ToolSession session) {
        final Existing existing = requireExisting(plan);
        final ExtractionChange.Changed changed = current(existing, plan,
                extractionEvents.namespace(plan.sample(), session)).changed();
        if (!changeHash(existing, changed, plan, usedBy(existing, dashboard)).equals(contentHash)) {
            throw new IllegalStateException("What would be written, or what uses it, has changed since you were "
                                            + "shown it, so nothing was written. Use the button again to see what "
                                            + "would be.");
        }
        requireEdit(existing);
        final XsltStore xsltStore = xsltStoreProvider.get();
        try {
            final XsltDoc doc = xsltStore.readDocument(existing.xslt());
            // Never over a newer version.
            if (doc == null || !ExtractionChange.sha256(doc.getData()).equals(existing.codeHash())) {
                throw new IllegalStateException(changedSince(existing));
            }
            xsltStore.writeDocument(doc.copy().data(changed.code()).build());
        } catch (final IllegalStateException e) {
            throw e;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to write the change to " + existing.xslt(), e);
            throw new IllegalStateException("XSLT '" + name(existing.xslt()) + "' couldn't be written, so nothing "
                                            + "was changed: " + NullSafe.nonBlankStringElse(e.getMessage(),
                    e.getClass().getSimpleName()), e);
        }
        return new Written(existing.pipeline(), existing.xslt(), columnChanges(plan));
    }

    private static Existing requireExisting(final ExtractionPlan plan) {
        if (plan.existing() == null) {
            throw new IllegalStateException("The table has no extraction pipeline to change.");
        }
        return plan.existing();
    }

    private void requireEdit(final Existing existing) {
        if (!securityContext.hasDocumentPermission(existing.xslt(), DocumentPermission.EDIT)) {
            throw new IllegalStateException("You can't edit XSLT '" + name(existing.xslt()) + "', so nothing was "
                                            + "written. Save a new pipeline with 'Create extraction pipeline' "
                                            + "instead.");
        }
    }

    // The hash of a change in place: the XSLT, the code it changes, the code written, what else uses it, and the
    // table changes.
    private static String changeHash(final Existing existing,
                                     final ExtractionChange.Changed changed,
                                     final ExtractionPlan plan,
                                     final List<String> usedBy) {
        return ExtractionChange.sha256(String.join("\n---\n", existing.xslt().getUuid(), existing.codeHash(),
                changed.code(), String.join("\n", usedBy), String.join("\n", describeColumns(plan))));
    }

    // The other documents that use an XSLT, directly or through pipelines (e.g. a child pipeline, then dashboards,
    // queries and indexes using it), which a change to it in place changes too. They are found as the processing
    // user, as Stroom only links documents the user can see both ends of; those the user can't see are counted, not
    // named.
    private List<String> usedBy(final Existing existing, final DocRef dashboard) {
        final Set<String> own = Set.of(existing.xslt().getUuid(), existing.pipeline().getUuid());
        final Map<String, DocRef> found = securityContext.asProcessingUserResult(() -> {
            final Map<String, DocRef> users = new LinkedHashMap<>();
            List<String> frontier = List.of(existing.xslt().getUuid());
            for (int depth = 0; depth < MAX_USE_DEPTH && !frontier.isEmpty(); depth++) {
                final List<String> next = new ArrayList<>();
                for (final DocRef user : dependants(frontier)) {
                    if (users.putIfAbsent(user.getUuid(), user) == null && PipelineDoc.TYPE.equals(user.getType())) {
                        next.add(user.getUuid());
                    }
                }
                frontier = next;
            }
            own.forEach(users::remove);
            return users;
        });
        final List<String> names = new ArrayList<>();
        int hidden = 0;
        int more = 0;
        for (final DocRef user : found.values()) {
            if (!documentAccess.canView(user)) {
                hidden++;
            } else if (names.size() < MAX_USED_BY) {
                names.add(user.getType() + " '" + name(user) + "'" + (user.getUuid().equals(dashboard.getUuid())
                        ? " (this dashboard)"
                        : ""));
            } else {
                more++;
            }
        }
        if (more > 0) {
            names.add(more + " more");
        }
        if (hidden > 0) {
            names.add(hidden + " other document(s) you can't see");
        }
        return names;
    }

    // The documents that use some others, by the links Stroom's Dependencies screen shows.
    private List<DocRef> dependants(final List<String> uuids) {
        final DependencyCriteria criteria = new DependencyCriteria();
        criteria.setPageRequest(new PageRequest(0, MAX_DEPENDENCIES));
        final String qualifier = DependencyCriteria.FIELD_DEF_TO_UUID.getFilterQualifier();
        criteria.setPartialName(String.join(" or ", uuids.stream().map(uuid -> qualifier + ":=" + uuid).toList()));
        try {
            return NullSafe.list(NullSafe.get(docDependencyServiceProvider.get().fetchDependencies(criteria,
                            Set.of()), ResultPage::getValues)).stream()
                    .filter(dependency -> dependency.getTo() != null && uuids.contains(dependency.getTo().getUuid()))
                    .map(Dependency::getFrom)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read what uses " + uuids, e);
            // Saying nothing else uses it would mislead.
            throw new IllegalStateException("What else uses the XSLT can't be found, so it can't be changed in "
                                            + "place. Save a new pipeline with 'Create extraction pipeline' "
                                            + "instead.", e);
        }
    }

    private static String name(final DocRef docRef) {
        return NullSafe.nonBlankStringElse(docRef.getName(), docRef.getUuid());
    }

    // The Search Extraction template, if the user can use it and its XSLT element writes its records.
    private DocRef template() {
        try {
            return MergedPipeline.load(documentAccess, ExtractionPipelines.SEARCH_EXTRACTION_TEMPLATE_UUID)
                    .filter(merged -> ExtractionPipelines.outputXslt(merged)
                            .filter(ExtractionPipelines.XSLT_ELEMENT::equals)
                            .isPresent())
                    .map(MergedPipeline::pipeline)
                    .orElse(null);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the Search Extraction template", e);
            return null;
        }
    }

    /// The changes to make to the table to use a new extraction pipeline, as the browser applies them: the pipeline
    /// set (which turns extraction on), and a column added for each new field.
    ///
    /// @param plan     The extraction.
    /// @param pipeline The new pipeline.
    /// @return The changes, as JSON operations.
    static String tableChanges(final ExtractionPlan plan, final DocRef pipeline) {
        final List<Object> changes = new ArrayList<>();
        changes.add(Change.builder(DashboardChanges.SET_SETTING)
                .setting(EXTRACTION_PIPELINE_SETTING)
                .value(pipeline.getUuid())
                .doc(pipeline)
                .build()
                .toMap());
        changes.addAll(columns(plan));
        return JsonUtil.writeValueAsString(changes, false);
    }

    // The changes to make to a table whose extraction pipeline was changed in place: a column for each new field.
    static String columnChanges(final ExtractionPlan plan) {
        return JsonUtil.writeValueAsString(columns(plan), false);
    }

    private static List<Object> columns(final ExtractionPlan plan) {
        final List<Object> changes = new ArrayList<>();
        for (final String column : plan.newColumns()) {
            final ExtractionField field = plan.fields().stream()
                    .filter(each -> each.name().equals(column))
                    .findFirst()
                    .orElseThrow();
            changes.add(Change.builder(DashboardChanges.ADD_COLUMN)
                    .name(field.name())
                    .expression("${" + field.name() + "}")
                    .format(format(field))
                    .build()
                    .toMap());
        }
        return changes;
    }

    // The changes made to the table, in words.
    static List<String> describeChanges(final ExtractionPlan plan) {
        final List<String> changes = new ArrayList<>();
        changes.add("Set table '" + plan.component() + "' to extract its values with the new pipeline"
                    + (plan.existing() == null
                ? " (turning extraction on)"
                : " in place of pipeline '" + name(plan.existing().pipeline()) + "'"));
        changes.addAll(describeColumns(plan));
        return changes;
    }

    // The columns added to the table, in words.
    static List<String> describeColumns(final ExtractionPlan plan) {
        final List<String> changes = new ArrayList<>();
        for (final String column : plan.newColumns()) {
            final ExtractionField field = plan.fields().stream()
                    .filter(each -> each.name().equals(column))
                    .findFirst()
                    .orElseThrow();
            changes.add("Add column '" + field.name() + "' (${" + field.name() + "}, formatted as "
                        + format(field) + ")");
        }
        return changes;
    }

    private static String format(final ExtractionField field) {
        return switch (field.type()) {
            case ExtractionField.NUMBER -> "Number";
            case ExtractionField.DATE -> "Date Time";
            default -> "Text";
        };
    }

    private static String fieldNames(final ExtractionPlan plan) {
        return String.join(", ", plan.fields().stream().map(ExtractionField::name).toList());
    }


    // --------------------------------------------------------------------------------


    /// What would be written for an extraction.
    ///
    /// @param xslt     The XSLT.
    /// @param template The pipeline the new pipeline inherits, or null for a pipeline of its own.
    /// @param element  The element of the pipeline given the XSLT.
    /// @param base                What the pipeline is built from, in words.
    /// @param xsltDescription     The XSLT's description.
    /// @param pipelineDescription The pipeline's description.
    /// @param hash                The hash of what would be written.
    record Content(String xslt,
                   DocRef template,
                   String element,
                   String base,
                   String xsltDescription,
                   String pipelineDescription,
                   String hash) {

    }

    /// The code of a table's extraction XSLT, and it changed as proposed.
    ///
    /// @param code    Its code now.
    /// @param changed It changed.
    private record Current(String code, ExtractionChange.Changed changed) {

    }

    /// An extraction written.
    ///
    /// @param pipeline     The new pipeline.
    /// @param xslt         Its XSLT.
    /// @param tableChanges The changes to make to the table to use it, as JSON operations.
    public record Written(DocRef pipeline, DocRef xslt, String tableChanges) {

    }
}
