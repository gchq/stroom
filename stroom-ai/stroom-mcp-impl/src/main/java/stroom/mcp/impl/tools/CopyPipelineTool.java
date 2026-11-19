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

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.shared.AbstractDoc;
import stroom.mcp.impl.tools.PipelineWrites.Typed;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/// `copy_pipeline` - copies an existing source's pipeline into the build with exactly the original's structure and
/// settings, its XSLTs and text converters copied with it. A template is refused: templates are inherited
/// (create_pipeline), never copied.
class CopyPipelineTool extends AbstractBuiltInTool {

    static final String NAME = "copy_pipeline";
    static final String BUILD_ARG = "build";
    static final String SOURCE_UUID_ARG = "source_uuid";
    static final String NEW_NAME_ARG = "new_name";
    static final String RENAME_ARG = "rename";
    static final String SET_PROPERTIES_ARG = "set_properties";
    static final String WORKING_COPY_ARG = "working_copy";

    private static final String ELASTIC_FILTER = "ElasticIndexingFilter";
    private static final String INDEX_NAME = "indexName";
    // The form's keys for a new version's names besides the pipeline's (new_name).
    private static final String DOC_KEY = "doc:";
    private static final String INDEX_KEY = "index_name";
    private static final String INDEX_DOC_KEY = "index_doc:";

    private final PipelineWrites writes;
    private final PipelineDocs pipelineDocs;
    private final PipelineShapes pipelineShapes;
    private final PipelineTemplates templates;
    private final DocumentAccess documentAccess;
    private final WriteGuard guard;
    private final BuildRecords records;
    private final Consent consent;
    private final BuildPlan plan;
    private final IndexTemplateAgreements agreements;
    private final ProductionIndexes productionIndexes;
    // An aggregated feed's source type groups; none in tests that make the tool themselves.
    private Provider<SourceTypeGroups> groupsProvider;
    // The build's test indexes; none in tests that make the tool themselves.
    private Provider<BuildTestIndexes> testIndexesProvider;
    // The build's catalogue of event types; none in tests that make the tool themselves.
    private Provider<CatalogueNotes> catalogueNotesProvider;

    @Inject
    CopyPipelineTool(final PipelineWrites writes,
                     final PipelineDocs pipelineDocs,
                     final PipelineShapes pipelineShapes,
                     final PipelineTemplates templates,
                     final DocumentAccess documentAccess,
                     final WriteGuard guard,
                     final BuildRecords records,
                     final Consent consent,
                     final BuildPlan plan,
                     final IndexTemplateAgreements agreements,
                     final ProductionIndexes productionIndexes) {
        this.writes = writes;
        this.pipelineDocs = pipelineDocs;
        this.pipelineShapes = pipelineShapes;
        this.templates = templates;
        this.documentAccess = documentAccess;
        this.guard = guard;
        this.records = records;
        this.consent = consent;
        this.plan = plan;
        this.agreements = agreements;
        this.productionIndexes = productionIndexes;
    }

    /// Where an aggregated feed's source type groups are found (set by injection; none in tests that make the tool
    /// themselves).
    ///
    /// @param groups The feed's source type groups.
    @Inject
    void setGroups(final Provider<SourceTypeGroups> groups) {
        this.groupsProvider = groups;
    }

    /// Where the build's test indexes are made (set by injection; none in tests that make the tool themselves): a
    /// copy writing to a production Lucene index writes to the build's test index from the start.
    ///
    /// @param testIndexes The build's test indexes.
    @Inject
    void setTestIndexes(final Provider<BuildTestIndexes> testIndexes) {
        this.testIndexesProvider = testIndexes;
    }

    /// Where the build's catalogue of event types is kept (set by injection; none in tests that make the tool
    /// themselves): a new version of a promoted pipeline starts from the promoted catalogue.
    ///
    /// @param catalogueNotes The build's catalogue.
    @Inject
    void setCatalogueNotes(final Provider<CatalogueNotes> catalogueNotes) {
        this.catalogueNotesProvider = catalogueNotes;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // It creates a pipeline and the documents it owns.
        return false;
    }

    @Override
    public boolean confirmsItself() {
        // The user confirms the names in a form before anything is copied.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("The copies are created in the build folder as the user, who needs View on the pipeline "
                      + "and the XSLTs and text converters it owns, Create in the build folder, and Edit on the "
                      + "copies.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Copy an existing source's pipeline (not a template: those are inherited with "
                             + "create_pipeline) into the build with exactly the original's structure and "
                             + "settings: same parent template, same element changes (removed or re-linked steps, "
                             + "extra XSLT steps), reference loaders and property values. The XSLTs and text "
                             + "converters it owns are copied too and the copy is rewired to them; other documents "
                             + "(shared libraries, indexes, clusters) stay shared; one the build has copied already "
                             + "(an aggregated feed's source type group, copied when the group was saved) is used, "
                             + "not copied again (refused where it has the original's name: rename it). A copy of "
                             + "an aggregated feed's source type group imports the build's working copies of the "
                             + "feed's common module and helpers where an older build has them, so stepping in the "
                             + "build runs the build's chain. A new version of a group's "
                             + "pipeline: the registry's rows of the group move to it, and to its copy of the "
                             + "group's XSLT. A promoted pipeline (outside any build) is only ever copied as a "
                             + "new version, never changed in place: its names are proposed from the original's "
                             + "version (V1.0 to V1.1, an "
                             + "Elasticsearch index -v1 to -v2, with an Elastic Index doc for it) and confirmed or "
                             + "edited by the user, never the original's, and its index name is looked up first: "
                             + "one that matches anything on the cluster is refused. The user confirms the names "
                             + "first.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, CreateFeedTool.BUILD_DESCRIPTION)
                        .addStringProperty(SOURCE_UUID_ARG, "Pipeline to copy.")
                        .addStringProperty(NEW_NAME_ARG, "Name of the copy, e.g. 'Acme-Door-V1.3-Events'.")
                        .addProperty(RENAME_ARG, ObjectSchema.builder()
                                .description("Text replacements applied to the names of the copied XSLTs and "
                                             + "text converters, e.g. {'V1.2': 'V1.3'}.")
                                .build())
                        .addProperty(SET_PROPERTIES_ARG, CreatePipelineTool.propertiesSchema(
                                "Properties to change on the copy, as objects, e.g. [{\"element\": "
                                + "\"elasticIndexingFilter\", \"name\": \"indexName\", \"value\": "
                                + "\"ecs-acme-v2\"}] for a new index version."))
                        .addBooleanProperty(WORKING_COPY_ARG, "Not used: a copy is always a new version, never "
                                                              + "a working copy written back over the original; "
                                                              + "true is refused.")
                        .required(BUILD_ARG, SOURCE_UUID_ARG, NEW_NAME_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(copy(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final PipelineUnreadableException e) {
            return JsonResults.refused("Stroom can't load the pipeline: " + e.getMessage());
        }
    }

    private Map<String, Object> copy(final ToolCall call, final ToolCallContext context) {
        final String build = required(call, BUILD_ARG);
        final String sourceUuid = required(call, SOURCE_UUID_ARG);
        final String newName = required(call, NEW_NAME_ARG);
        final Map<String, String> rename = PipelineArguments.textMap(argsMap(call).get(RENAME_ARG), RENAME_ARG);
        final List<PropertyValue> changes = PropertyValue.listOf(argsMap(call).get(SET_PROPERTIES_ARG),
                SET_PROPERTIES_ARG);
        final PipelineDoc source = pipelineDocs.read(sourceUuid);
        final String reason = templates.templateReason(sourceUuid);
        if (reason != null) {
            throw new ToolRefusal("'" + source.getName() + "' is a template (" + reason + "): templates are "
                                  + "inherited, not copied. A new source's pipeline is a child of it: "
                                  + "create_pipeline(name=..., template_uuid='" + sourceUuid + "'), which keeps "
                                  + "the template's structure and takes later fixes to it. copy_pipeline is for a "
                                  + "new version of a source's own pipeline (e.g. "
                                  + "Acme-Door-V1.2-Events).");
        }
        final List<String> notes = new ArrayList<>();
        // Promoted: outside any build (in production), whoever made it.
        final boolean promoted = guard.buildOf(new DocRef(PipelineDoc.TYPE, sourceUuid)).isEmpty();
        final List<DocRef> owned = owned(source, changes);
        final Map<String, String> names = new LinkedHashMap<>();
        for (final DocRef entity : owned) {
            String name = entity.getName();
            for (final Map.Entry<String, String> replacement : rename.entrySet()) {
                name = name.replace(replacement.getKey(), replacement.getValue());
            }
            names.put(entity.getUuid(), name);
        }
        refuseWorkingCopy(call, source, promoted);
        if (promoted) {
            refuseCopiesUnderOriginalNames(build, source, owned);
        }
        final NewVersion version = promoted
                ? newVersion(build, source, newName, owned, names, changes)
                : null;
        if (version != null && !context.userQuestions().canAsk()) {
            return namesAsked(source, version);
        }
        final Typed typed = changes.stream().anyMatch(property -> property.docUuid() == null)
                ? writes.typedProperties(writes.layout(sourceUuid).elements(), changes)
                : new Typed(changes, Map.of());
        final String proposedName = version == null
                ? newName
                : version.pipeline();
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("build", build);
        details.put("copy of", source.getName());
        details.put("pipeline name", proposedName);
        details.put("copied documents", List.copyOf(new TreeSet<>(version == null
                ? names.values()
                : version.documents().values())));
        details.put("changes", changes.stream().map(PropertyValue::dotted).toList());
        if (version != null) {
            version.addTo(details);
        }
        final Consent.Request request = version == null
                ? Consent.Request.confirmation(NAME, "Copy pipeline '" + source.getName() + "' as '" + proposedName
                                                     + "'", details)
                : version.editable(Consent.Request.confirmation(NAME, "Copy pipeline '" + source.getName() + "' as "
                                                                      + "a new version, '" + proposedName + "'",
                        details));
        consent.require(call, context, request);
        final NewVersion settled = version == null
                ? null
                : version.settled(consent, context);
        if (settled != null) {
            refuseUnchanged(source, version, settled);
            refuseTakenIndex(build, source.getUuid(), settled);
            names.putAll(settled.documents());
        }
        final String pipelineName = settled == null
                ? newName
                : settled.pipeline();

        final String role = pipelineShapes.role(sourceUuid);
        final Map<String, DocRef> copies = new LinkedHashMap<>();
        for (final DocRef entity : owned) {
            if (!copies.containsKey(entity.getUuid())) {
                // One the build copied already (an aggregated feed's source type group, copied when the group was
                // saved) is run, not copied again.
                copies.put(entity.getUuid(), alreadyCopied(build, entity)
                        .orElseGet(() -> copyDocument(entity, names.get(entity.getUuid()), build, role)));
            }
        }
        final List<PipelineProperty> rewired = new ArrayList<>();
        for (final PipelineProperty property : PipelineDocs.addedProperties(source)) {
            final DocRef entity = NullSafe.get(property.getValue(), PipelinePropertyValue::getEntity);
            final boolean copied = entity != null && copies.containsKey(entity.getUuid());
            rewired.add(copied
                    ? rewire(property, copies.get(entity.getUuid()))
                    : property);
        }
        PipelineDoc data = PipelineDocs.withAddedProperties(source, rewired);
        for (final PropertyValue property : typed.properties()) {
            data = PipelineDocs.withProperty(data, property.element(), property.name(),
                    writes.value(property, typed.keys().get(property.dotted())));
        }
        if (settled != null && settled.indexName() != null) {
            // The new version's own index, as set_properties would set it.
            for (final String element : settled.elasticElements()) {
                data = PipelineDocs.withProperty(data, element, INDEX_NAME, new PipelinePropertyValue(
                        settled.indexName()));
            }
        }
        final PipelineDoc changed = data;
        final DocRef ref = guard.create(PipelineDoc.TYPE, pipelineName, build, null, false, role);
        records.update(build, ref, entry -> entry.put("version_of", sourceUuid));
        final PipelineDoc doc = pipelineDocs.write(pipelineDocs.read(ref.getUuid()).copy()
                .parentPipeline(source.getParentPipeline())
                .description(source.getDescription())
                .pipelineData(changed.getPipelineData())
                .build());
        final List<DocRef> indexDocs = settled == null
                ? List.of()
                : copyIndexDocs(build, settled);
        final Map<String, Object> result = new LinkedHashMap<>();
        if (groupsProvider != null) {
            // An aggregated feed's group copied: it imports the build's copies of the feed's modules, and a new
            // version runs the group's rows from now on.
            groupsProvider.get().chainCopies(context, build);
            final List<String> types = groupsProvider.get().newVersion(context, build, sourceUuid, copies,
                    new DocRef(PipelineDoc.TYPE, doc.getUuid(), doc.getName()));
            if (!types.isEmpty()) {
                result.put("source_types", types);
            }
        }
        result.put("type", PipelineDoc.TYPE);
        result.put("uuid", doc.getUuid());
        result.put("name", doc.getName());
        result.put("copy_of", source.getName());
        result.put("copied_documents", copies.values().stream().map(MergedPipeline::docRef).toList());
        if (settled != null && settled.indexName() != null) {
            result.put("index_name", settled.indexName());
            result.put("index_docs", indexDocs.stream().map(MergedPipeline::docRef).toList());
            notes.add("the new version writes to its own index '" + settled.indexName() + "', checked free on "
                      + "the cluster: agree its index template (propose_index_template) for that name");
        }
        // A copy writing to a production Lucene index writes to the build's test index from the start, made now.
        // Seen in an eval (case 61): the copy of the group's production indexing pipeline said nothing of a test
        // index (one was made only once it processed), so the agent made test indexes of its own, which promotion
        // then took to production.
        final List<BuildTestIndexes.TestIndex> testIndexes = testIndexesProvider == null
                ? List.of()
                : testIndexesProvider.get().redirect(new DocRef(PipelineDoc.TYPE, doc.getUuid(), doc.getName()));
        if (!testIndexes.isEmpty()) {
            result.put("test_index", testIndexes.stream().map(BuildTestIndexes.TestIndex::toMap).toList());
            result.put("test_index_note", String.join(" ", testIndexes.stream()
                    .map(BuildTestIndexes.TestIndex::said)
                    .toList()) + " Make no test index of your own: this one is the build's, and is tidied up at "
                               + "promotion.");
        }
        if (promoted && catalogueNotesProvider != null) {
            // A next version starts from the promoted catalogue of event types: its source notes carried in.
            catalogueNotesProvider.get().carryInto(build, new DocRef(PipelineDoc.TYPE, sourceUuid,
                    source.getName())).ifPresent(notes::add);
        }
        if (!notes.isEmpty()) {
            result.put("note", String.join("; ", notes));
        }
        return plan.withNext(context, build, result);
    }

    // A copy is always a new version (decided by the user, 2026-11-17: no change in place of a promoted pipeline
    // or the code it runs; seen in a user's test, a promoted feed's pipelines copied as working copies under the
    // same names where a new version was meant). A pipeline of a build is changed where it is.
    private static void refuseWorkingCopy(final ToolCall call, final PipelineDoc source, final boolean promoted) {
        if (!ToolArgs.flag(call, WORKING_COPY_ARG)) {
            return;
        }
        throw new ToolRefusal("'" + source.getName() + "' " + (promoted
                ? "is promoted (in production): it changes only by a new version"
                : "is the build's own: change it where it is, a copy of it is a new version") + ", so only a new "
                              + "version of it is copied, never a working copy. Call again without "
                              + WORKING_COPY_ARG + "=true: the new version's names are proposed for the user to "
                              + "confirm or edit. Nothing was copied.");
    }

    // Where no form can be shown: the new version's proposed names, for the agent to put to the user; the copy
    // itself is confirmed in a form once one can be shown.
    private static Map<String, Object> namesAsked(final PipelineDoc source, final NewVersion version) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "needs_names");
        out.put("pipeline", source.getName());
        out.put("new_version_names", version.toMap());
        out.put("hint", "Nothing was copied. " + source.getName() + " is promoted, so it changes only by a new "
                        + "version, whose names are proposed in new_version_names. Ask the user whether they suit "
                        + "them, or which they want instead (never the original's), then call copy_pipeline again "
                        + "with " + NEW_NAME_ARG + "= the pipeline name, " + RENAME_ARG + "= the version change for "
                        + "its documents and, for an Elasticsearch indexing pipeline, " + SET_PROPERTIES_ARG
                        + "= its new indexName, once the user can confirm them in a form.");
        return out;
    }

    // The names proposed for a new version of a promoted pipeline: the agent's where they differ from the
    // original's, else the next version's (VersionNames); for an Elasticsearch indexing pipeline, its index's next
    // version and an Elastic Index doc for it beside each one naming the old index.
    private NewVersion newVersion(final String build,
                                  final PipelineDoc source,
                                  final String newName,
                                  final List<DocRef> owned,
                                  final Map<String, String> names,
                                  final List<PropertyValue> changes) {
        // A version token inside the name of a feed the build works from is the feed's, not the pipeline's version
        // (found in review, 2026-11-18).
        final List<String> held = heldNames(build);
        final String pipeline = newName.equals(source.getName())
                ? VersionNames.next(source.getName(), held)
                : newName;
        final String from = VersionNames.token(source.getName(), held).orElse(null);
        final String to = VersionNames.token(pipeline, held).orElse(null);
        final Map<String, String> documents = new LinkedHashMap<>();
        final Map<String, String> originals = new LinkedHashMap<>();
        for (final DocRef entity : owned) {
            if (alreadyCopied(build, entity).isPresent()) {
                continue;
            }
            final String given = names.get(entity.getUuid());
            documents.put(entity.getUuid(), given.equals(entity.getName())
                    ? VersionNames.following(entity.getName(), from, to, held)
                    : given);
            originals.put(entity.getUuid(), entity.getName());
        }
        final Map<String, Object> destination = agreements.elasticDestination(source.getUuid()).orElse(null);
        final String oldIndex = destination == null
                ? null
                : NullSafe.get(destination.get("index name"), String::valueOf);
        if (oldIndex == null) {
            return new NewVersion(source.getName(), pipeline, VersionNames.nextMajor(source.getName(), held),
                    documents, originals, null, null, null, List.of(), Map.of(), Map.of());
        }
        final String givenIndex = changes.stream()
                .filter(property -> INDEX_NAME.equals(property.name()) && property.value() != null)
                .map(property -> String.valueOf(property.value()))
                .findFirst()
                .orElse(null);
        final String index = givenIndex == null || givenIndex.equals(oldIndex)
                ? VersionNames.nextIndexName(oldIndex, held)
                : givenIndex;
        final List<String> elements = pipelineShapes.merged(source.getUuid()).elements().entrySet().stream()
                .filter(entry -> ELASTIC_FILTER.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        final Map<String, String> indexDocs = new LinkedHashMap<>();
        final Map<String, String> indexDocOriginals = new LinkedHashMap<>();
        for (final ProductionIndexes.Claim claim : productionIndexes.claims(build, oldIndex)) {
            // A doc naming exactly the old index (one naming a single index a {variable} stands for is left: the
            // new version's name for it isn't known).
            if (ElasticIndexDoc.TYPE.equals(claim.doc().getType()) && claim.indexName().equalsIgnoreCase(oldIndex)) {
                indexDocs.put(claim.doc().getUuid(), VersionNames.following(claim.doc().getName(), from, to,
                        held));
                indexDocOriginals.put(claim.doc().getUuid(), claim.doc().getName());
            }
        }
        return new NewVersion(source.getName(), pipeline, VersionNames.nextMajor(source.getName(), held), documents,
                originals, oldIndex, index, destination, elements, indexDocs, indexDocOriginals);
    }

    // The names a pipeline's name may hold whose version tokens are theirs: the feeds the build works from.
    private List<String> heldNames(final String build) {
        return JsonValues.strings(records.load(build).get(BuildRecords.SOURCE_FEEDS));
    }

    // A new version under any of the original's names is refused: it would be taken for the original, and promoted
    // over or beside it under the same name (seen in a user's test: a new version copied as V1.0, where the user
    // expected V1.1 and a new index name).
    private static void refuseUnchanged(final PipelineDoc source, final NewVersion proposed, final NewVersion settled) {
        final List<String> same = new ArrayList<>();
        if (settled.pipeline().equals(source.getName())) {
            same.add("pipeline name '" + source.getName() + "'");
        }
        settled.documents().forEach((uuid, name) -> {
            if (name.equals(proposed.originals().get(uuid))) {
                same.add("document name '" + name + "'");
            }
        });
        if (settled.indexName() != null && settled.indexName().equals(settled.oldIndexName())) {
            same.add("index name '" + settled.oldIndexName() + "'");
        }
        settled.indexDocs().forEach((uuid, name) -> {
            if (name.equals(proposed.indexDocOriginals().get(uuid))) {
                same.add("Elastic Index doc name '" + name + "'");
            }
        });
        if (!same.isEmpty()) {
            throw new ToolRefusal("A new version of " + source.getName() + " needs names of its own, and "
                                  + ToolText.list(same) + " are the original's. Give it new ones (" + NEW_NAME_ARG
                                  + ", " + RENAME_ARG + ", " + SET_PROPERTIES_ARG + " for its indexName), e.g. '"
                                  + proposed.pipeline() + "', and call again. Nothing was copied.");
        }
    }

    // A document of the pipeline the build has a new version of already under the original's name (a source type
    // group's XSLT, made by build_translation_xslt before its new versions were named for them): refused, as a new
    // version under the original's names is. It is run, not copied again, so it isn't renamed here.
    private void refuseCopiesUnderOriginalNames(final String build,
                                                final PipelineDoc source,
                                                final List<DocRef> owned) {
        final List<String> same = new ArrayList<>();
        String example = null;
        for (final DocRef entity : owned) {
            final Optional<DocRef> copy = alreadyCopied(build, entity)
                    .filter(found -> Objects.equals(found.getName(), entity.getName()));
            if (copy.isPresent()) {
                same.add("'" + copy.get().getName() + "' (" + copy.get().getUuid() + ")");
                example = Objects.requireNonNullElse(example, VersionNames.next(entity.getName(), heldNames(build)));
            }
        }
        if (!same.isEmpty()) {
            throw new ToolRefusal("A new version of " + source.getName() + " needs names of its own, and the build's "
                                  + "new version(s) " + ToolText.list(same) + " of its documents have the "
                                  + "original's: they would be taken for the original. Rename each in Stroom (e.g. '"
                                  + example + "'), then call again. Nothing was copied.");
        }
    }

    // The candidate index of a new version of an Elasticsearch indexing pipeline, looked up before anything is
    // copied (asked for by the user): anything it matches, even an empty index, or a lookup that fails, is refused.
    private void refuseTakenIndex(final String build, final String sourceUuid, final NewVersion settled) {
        if (settled.indexName() == null) {
            return;
        }
        final Map<String, Object> destination = new LinkedHashMap<>(settled.destination());
        destination.put("index name", settled.indexName());
        productionIndexes.taken(build, sourceUuid, destination, "Nothing was copied.").ifPresent(taken -> {
            throw new ToolRefusal(taken + " Call again with " + SET_PROPERTIES_ARG + " setting the "
                                  + "elasticIndexingFilter's indexName to the name the user settles on.");
        });
    }

    // An Elastic Index doc in the build for the new version's index, beside each one outside it naming the old: the
    // original's settings and fields, its index name the new one.
    @SuppressWarnings("unchecked")
    private List<DocRef> copyIndexDocs(final String build, final NewVersion settled) {
        final List<DocRef> made = new ArrayList<>();
        for (final Map.Entry<String, String> entry : settled.indexDocs().entrySet()) {
            final ElasticIndexDoc original = documentAccess.read(new DocRef(ElasticIndexDoc.TYPE, entry.getKey()),
                    ElasticIndexDoc.class).orElse(null);
            if (original == null) {
                continue;
            }
            final DocRef ref = guard.create(ElasticIndexDoc.TYPE, entry.getValue(), build, null, false,
                    IndexExamples.ROLE);
            records.update(build, ref, kept -> kept.put("version_of", entry.getKey()));
            final ElasticIndexDoc fresh = documentAccess.read(ref, ElasticIndexDoc.class)
                    .orElseThrow(() -> new ToolRefusal("Elastic Index doc '" + entry.getValue() + "' was made but "
                                                       + "can't be read"));
            final DocumentActionHandler<AbstractDoc> handler = documentAccess.handler(ElasticIndexDoc.TYPE)
                    .orElseThrow(() -> new ToolRefusal("Stroom has no " + ElasticIndexDoc.TYPE + " store"));
            final Object saved = handler.writeDocument(fresh.copy()
                    .clusterRef(original.getClusterRef())
                    .description(original.getDescription())
                    .indexName(settled.indexName())
                    .searchSlices(original.getSearchSlices())
                    .searchScrollSize(original.getSearchScrollSize())
                    .fields(original.getFields())
                    .timeField(original.getTimeField())
                    .defaultExtractionPipeline(original.getDefaultExtractionPipeline())
                    .build());
            made.add(new DocRef(ElasticIndexDoc.TYPE, ref.getUuid(), saved instanceof final AbstractDoc doc
                    ? doc.getName()
                    : entry.getValue()));
        }
        return made;
    }

    // The XSLTs and text converters the pipeline's own layer names, but not those set_properties replaces: a copy
    // nothing uses, and its name may be the replacement's (seen: v2's XSLT made first, then V1's copied and renamed
    // to the same name, which the build refuses).
    private static List<DocRef> owned(final PipelineDoc source, final List<PropertyValue> changes) {
        final Set<String> replaced = new HashSet<>();
        changes.forEach(property -> replaced.add(property.dotted()));
        return PipelineDocs.addedProperties(source).stream()
                .filter(property -> !replaced.contains(property.getElement() + "." + property.getName()))
                .map(property -> NullSafe.get(property.getValue(), PipelinePropertyValue::getEntity))
                .filter(entity -> entity != null && PipelineWrites.OWNED_TYPES.contains(entity.getType()))
                .toList();
    }

    // The build's new version of a document already made (an aggregated feed's group XSLT's), if it has one.
    private Optional<DocRef> alreadyCopied(final String build, final DocRef original) {
        final String key = "version_of";
        final Map<String, Object> kept = BuildRecords.docs(records.load(build));
        return guard.folderContents(build).stream()
                .filter(doc -> doc.type().equals(original.getType()))
                .filter(doc -> kept.get(doc.uuid()) instanceof final Map<?, ?> entry
                               && original.getUuid().equals(entry.get(key)))
                .findFirst()
                .map(WriteGuard.BuildDoc::docRef);
    }

    // The property, naming the copy of its document.
    private static PipelineProperty rewire(final PipelineProperty property, final DocRef copy) {
        return new PipelineProperty(property.getElement(), property.getName(), new PipelinePropertyValue(copy));
    }

    // A new version's copy of an XSLT or text converter in the build, its content as the original's.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private DocRef copyDocument(final DocRef original,
                                final String name,
                                final String build,
                                final String role) {
        final Object content = documentAccess.read(original)
                .orElseThrow(() -> new ToolRefusal(original.getType() + " " + original.getUuid() + " can't be "
                                                   + "read: it doesn't exist, or you don't have permission to "
                                                   + "view it"));
        final DocumentActionHandler handler = documentAccess.handler(original.getType())
                .orElseThrow(() -> new ToolRefusal("Stroom has no " + original.getType() + " store"));
        final DocRef ref = guard.create(original.getType(), name, build, null, false, role);
        records.update(build, ref, entry -> entry.put("version_of", original.getUuid()));
        final Object made = documentAccess.read(ref)
                .orElseThrow(() -> new ToolRefusal(original.getType() + " '" + name + "' was made but can't be "
                                                   + "read"));
        final Object filled = switch (content) {
            case final XsltDoc xslt -> ((XsltDoc) made).copy()
                    .data(xslt.getData())
                    .description(xslt.getDescription())
                    .build();
            case final TextConverterDoc converter -> ((TextConverterDoc) made).copy()
                    .data(converter.getData())
                    .converterType(converter.getConverterType())
                    .description(converter.getDescription())
                    .build();
            default -> made;
        };
        final Object saved = handler.writeDocument((AbstractDoc) filled);
        final String savedName = saved instanceof final AbstractDoc doc
                ? doc.getName()
                : name;
        return new DocRef(original.getType(), ref.getUuid(), savedName);
    }

    private String required(final ToolCall call, final String name) {
        final String value = stringArg(call, name);
        if (value == null) {
            throw new ToolRefusal("Give " + name);
        }
        return value;
    }


    // --------------------------------------------------------------------------------


    // A new version's names: proposed (the agent's where they differ from the original's, else the next version's),
    // or as the user settled them in the form.
    private record NewVersion(String original,
                              String pipeline,
                              String major,
                              Map<String, String> documents,
                              Map<String, String> originals,
                              String oldIndexName,
                              String indexName,
                              Map<String, Object> destination,
                              List<String> elasticElements,
                              Map<String, String> indexDocs,
                              Map<String, String> indexDocOriginals) {

        // The confirmation, each name editable.
        Consent.Request editable(final Consent.Request request) {
            Consent.Request out = request.withEditable(NEW_NAME_ARG, "Pipeline name", pipeline);
            for (final Map.Entry<String, String> entry : documents.entrySet()) {
                out = out.withEditable(DOC_KEY + entry.getKey(), "Copy of " + originals.get(entry.getKey()),
                        entry.getValue());
            }
            if (indexName != null) {
                out = out.withEditable(INDEX_KEY, "Elasticsearch index name (was " + oldIndexName + ")", indexName);
            }
            for (final Map.Entry<String, String> entry : indexDocs.entrySet()) {
                out = out.withEditable(INDEX_DOC_KEY + entry.getKey(), "Elastic Index doc for it (beside "
                                                                      + indexDocOriginals.get(entry.getKey()) + ")",
                        entry.getValue());
            }
            return out;
        }

        // The names as the user settled them in the form.
        NewVersion settled(final Consent consent, final ToolCallContext context) {
            final Map<String, String> docs = new LinkedHashMap<>();
            documents.forEach((uuid, name) -> docs.put(uuid, consent.edited(context, DOC_KEY + uuid, name)));
            final Map<String, String> indexes = new LinkedHashMap<>();
            indexDocs.forEach((uuid, name) -> indexes.put(uuid, consent.edited(context, INDEX_DOC_KEY + uuid,
                    name)));
            return new NewVersion(original, consent.edited(context, NEW_NAME_ARG, pipeline), major, docs,
                    originals, oldIndexName, indexName == null
                    ? null
                    : consent.edited(context, INDEX_KEY, indexName), destination, elasticElements, indexes,
                    indexDocOriginals);
        }

        // What the form says of it.
        void addTo(final Map<String, Object> details) {
            details.put("new version", "the next of " + original + "; for a major change, " + major + " instead "
                                       + "(edit the names)");
            if (indexName != null) {
                details.put("index", "its own, '" + indexName + "' (was '" + oldIndexName + "'), looked up on the "
                                     + "cluster before anything is copied");
            }
        }

        // The names, for the agent to put to the user in the chat.
        Map<String, Object> toMap() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("pipeline", pipeline);
            out.put("major_version_instead", major);
            final List<Map<String, Object>> docs = new ArrayList<>();
            documents.forEach((uuid, name) -> docs.add(Map.of("was", originals.get(uuid), "name", name)));
            out.put("documents", docs);
            if (indexName != null) {
                out.put("index_name", indexName);
                out.put("was_index_name", oldIndexName);
                final List<Map<String, Object>> indexes = new ArrayList<>();
                indexDocs.forEach((uuid, name) -> indexes.add(Map.of("beside", indexDocOriginals.get(uuid),
                        "name", name)));
                out.put("elastic_index_docs", indexes);
            }
            return out;
        }
    }
}
