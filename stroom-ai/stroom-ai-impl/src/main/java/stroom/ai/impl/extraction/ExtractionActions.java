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
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.vis.VisualisationActions;
import stroom.ai.shared.AiCreateExtraction;
import stroom.ai.shared.AiWriteExtractionRequest;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.xslt.XsltStore;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// Makes the search extraction the agent proposed for a dashboard table, when the user clicks its
/// `create_extraction_pipeline` button: first [#prepare] says exactly what would be written (an XSLT, written by
/// Stroom from the fields, and a pipeline using it), for the browser to show the user with the name and folder they
/// choose; nothing is written until they confirm it, when [#write] writes it, as the user, refusing anything but
/// what they were shown. The browser then sets the table up to use it, as unsaved changes.
///
/// The pipeline is a child of the content pack's Search Extraction template, setting only its XSLT, if the user can
/// use the template; else a pipeline of its own as the template makes.
public class ExtractionActions {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExtractionActions.class);

    /// The longest a new pipeline's name may be.
    static final int MAX_NAME_CHARS = 100;
    /// The table setting holding its extraction pipeline (see `DashboardSettings`), which turns extraction on.
    static final String EXTRACTION_PIPELINE_SETTING = "extractionPipeline";

    private final Provider<ExplorerService> explorerServiceProvider;
    private final Provider<ExplorerNodeService> explorerNodeServiceProvider;
    private final Provider<XsltStore> xsltStoreProvider;
    private final Provider<PipelineStore> pipelineStoreProvider;
    private final DocumentAccess documentAccess;
    private final SecurityContext securityContext;

    @Inject
    public ExtractionActions(final Provider<ExplorerService> explorerServiceProvider,
                             final Provider<ExplorerNodeService> explorerNodeServiceProvider,
                             final Provider<XsltStore> xsltStoreProvider,
                             final Provider<PipelineStore> pipelineStoreProvider,
                             final DocumentAccess documentAccess,
                             final SecurityContext securityContext) {
        this.explorerServiceProvider = explorerServiceProvider;
        this.explorerNodeServiceProvider = explorerNodeServiceProvider;
        this.xsltStoreProvider = xsltStoreProvider;
        this.pipelineStoreProvider = pipelineStoreProvider;
        this.documentAccess = documentAccess;
        this.securityContext = securityContext;
    }

    /// Says exactly what would be written for an extraction, for the user to confirm. Writes nothing.
    ///
    /// @param dashboard The dashboard.
    /// @param name      The name to suggest for the new pipeline and its XSLT.
    /// @param plan      The extraction.
    /// @return What would be written, with the hash the write must be given.
    public AiCreateExtraction prepare(final DocRef dashboard, final String name, final ExtractionPlan plan) {
        final Content content = content(dashboard, plan);
        final ExplorerNode folder = explorerNodeServiceProvider.get().getParent(dashboard)
                .map(VisualisationActions::inExplorerTree)
                .orElse(null);
        final List<String> fields = plan.fields().stream()
                .map(field -> field.name() + " (" + field.type() + "), from " + field.xpath())
                .toList();
        return new AiCreateExtraction(name, folder, dashboard, plan.componentId(), plan.component(),
                content.base(), content.xslt(), fields, describeChanges(plan), content.xsltDescription(),
                content.pipelineDescription(), content.hash());
    }

    /// Writes the extraction the user confirmed, as the user: the XSLT, then the pipeline using it, both in the
    /// folder they chose, with the name they chose. If either can't be written, any already made is removed (as
    /// the processing user, as the user may not be able to delete what they made); if that fails too, the user is
    /// told what was kept. (A failure inside Stroom's explorer between making a document and its node leaves the
    /// document with no node, which can't be removed from here.)
    ///
    /// @param dashboard The dashboard.
    /// @param plan      The extraction.
    /// @param request   The name and folder the user chose, and the hash of what they were shown.
    /// @return The new pipeline, and the changes to make to the table to use it.
    /// @throws IllegalStateException saying why nothing was written, e.g. what would be written has changed
    /// since the user was shown it, or they can't create documents in the folder.
    public Written write(final DocRef dashboard, final ExtractionPlan plan, final AiWriteExtractionRequest request) {
        final Content content = content(dashboard, plan);
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
                    .pipelineData(ExtractionPipelines.data(content.template(), xsltRef))
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

    // What would be written: the XSLT, the template the pipeline inherits (or none), and the hash of both and
    // the table changes, which the write must be given.
    Content content(final DocRef dashboard, final ExtractionPlan plan) {
        final String xslt = ExtractionXslt.xslt(plan.namespace(), plan.fields());
        final DocRef template = template();
        final String base = template != null
                ? "a child of the Search Extraction template ('" + template.getName() + "'), setting only its XSLT"
                : "a pipeline of its own, as the Search Extraction template makes (the template isn't one you can "
                  + "use)";
        final String xsltDescription = "The search extraction of table '" + plan.component() + "' on dashboard '"
                                       + dashboard.getName() + "', written by Stroom AI from its fields: "
                                       + fieldNames(plan) + ".";
        final String pipelineDescription = "Extracts " + fieldNames(plan) + " from the events of table '"
                                           + plan.component() + "' on dashboard '" + dashboard.getName()
                                           + "', with the XSLT of the same name. Made by Stroom AI.";
        // The pipeline's data follows from the template and the XSLT, so they stand for it.
        final String hash = sha256(String.join("\n---\n", xslt,
                NullSafe.getOrElse(template, DocRef::getUuid, "none"),
                String.join("\n", describeChanges(plan)), xsltDescription, pipelineDescription));
        return new Content(xslt, template, base, xsltDescription, pipelineDescription, hash);
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
        return JsonUtil.writeValueAsString(changes, false);
    }

    // The changes made to the table, in words.
    static List<String> describeChanges(final ExtractionPlan plan) {
        final List<String> changes = new ArrayList<>();
        changes.add("Set table '" + plan.component() + "' to extract its values with the new pipeline (turning "
                    + "extraction on)");
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

    private static String sha256(final String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8))).toLowerCase(Locale.ROOT);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }


    // --------------------------------------------------------------------------------


    /// What would be written for an extraction.
    ///
    /// @param xslt     The XSLT.
    /// @param template The template the pipeline inherits, or null for a pipeline of its own.
    /// @param base                What the pipeline is built from, in words.
    /// @param xsltDescription     The XSLT's description.
    /// @param pipelineDescription The pipeline's description.
    /// @param hash                The hash of what would be written.
    record Content(String xslt,
                   DocRef template,
                   String base,
                   String xsltDescription,
                   String pipelineDescription,
                   String hash) {

    }

    /// An extraction written.
    ///
    /// @param pipeline     The new pipeline.
    /// @param xslt         Its XSLT.
    /// @param tableChanges The changes to make to the table to use it, as JSON operations.
    public record Written(DocRef pipeline, DocRef xslt, String tableChanges) {

    }
}
