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
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.mcp.impl.tools.PipelineIndex.Entry;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/// `find_reference_data` - the reference maps this environment loads, for stroom:lookup() in a translation: each
/// map's name, key and value shape, the pipeline and feed(s) that load it, the loader pipeline to name as a
/// pipeline reference, and which pipelines already use that feed. Also the loader pipelines themselves and the
/// template for new reference-data pipelines. Only the XSLTs and pipelines the user can view are read.
///
/// [stroom.ai.impl.tools.XsltLookupsTool] (xslt_lookups) works from the other end: the lookups an XSLT makes, and
/// which of its pipelines' loaders write each map.
class FindReferenceDataTool extends AbstractBuiltInTool {

    static final String NAME = "find_reference_data";
    static final String HINT = "A mapping entry {lookup: {map, field}} reads a map; the pipeline must name the map's "
                               + "feed and loader as a reference (create_pipeline references=[{feed, "
                               + "loader_pipeline}] or update_pipeline (references=...)). No map for what you "
                               + "need: build the reference data (create_feed stream_type='Raw Reference', "
                               + "upload_sample stream_type='Raw Reference', build_reference_xslt, create_pipeline "
                               + "from the reference-data template (reference_data_template; "
                               + "find_pipeline_templates stage=reference), process, wait_for_processing), or keep "
                               + "a small static table in a Dictionary (save_dictionary) and use `dictionary` in "
                               + "the mapping.";

    private final PipelineIndex pipelineIndex;
    private final PipelineShapes pipelineShapes;
    private final PipelineTemplates pipelineTemplates;
    private final ReferenceData referenceData;
    private final ProcessorFilters processorFilters;
    private final DocumentAccess documentAccess;

    @Inject
    FindReferenceDataTool(final PipelineIndex pipelineIndex,
                          final PipelineShapes pipelineShapes,
                          final PipelineTemplates pipelineTemplates,
                          final ReferenceData referenceData,
                          final ProcessorFilters processorFilters,
                          final DocumentAccess documentAccess) {
        this.pipelineIndex = pipelineIndex;
        this.pipelineShapes = pipelineShapes;
        this.pipelineTemplates = pipelineTemplates;
        this.referenceData = referenceData;
        this.processorFilters = processorFilters;
        this.documentAccess = documentAccess;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CORE;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .unlockedBy(AppPermission.MANAGE_PROCESSORS_PERMISSION)
                .note("The feeds each map is loaded from come from processor filters, which need the Manage "
                      + "Processors permission; without it, they are left empty.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("The reference maps this environment loads, for stroom:lookup() in a translation (a "
                             + "mapping's `lookup`): each map's name, key and value shape, the pipeline and "
                             + "feed(s) that load it, the loader pipeline to name as a pipeline reference, and "
                             + "which pipelines already use that feed. Also the loader pipelines themselves "
                             + "(pipelines with a ReferenceDataFilter, whatever they're called) and the template "
                             + "for new reference-data pipelines (by its output). New reference data: create_feed "
                             + "(stream_type 'Raw Reference'), build_reference_xslt, create_pipeline from that "
                             + "template.")
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final Map<String, Entry> index = pipelineIndex.read();
        final Map<String, Set<String>> feedsByPipeline = ProcessorFilters.feedsByPipeline(processorFilters.all());
        // Production content: one document Stroom can't give (deleted since it was listed, a pipeline it can no
        // longer load) is left out and said, not the whole answer lost.
        final List<String> unreadable = new ArrayList<>();

        final Map<String, Map<String, Object>> maps = maps(index, feedsByPipeline, unreadable);

        // Loader pipelines, and the (feed, loader) pairs pipelines already reference.
        final List<Map<String, Object>> loaders = new ArrayList<>();
        final Map<String, Map<String, Object>> used = new LinkedHashMap<>();
        for (final Entry pipeline : ReferenceData.referencing(index)) {
            final List<PipelineReference> references;
            try {
                references = pipelineShapes.merged(pipeline.uuid()).references();
            } catch (final PipelineUnreadableException e) {
                unreadable.add("pipeline '" + pipeline.name() + "': " + e.getMessage());
                continue;
            }
            for (final PipelineReference reference : references) {
                final String feed = NullSafe.get(reference.getFeed(), DocRef::getName);
                final DocRef loader = reference.getPipeline();
                final Map<String, Object> entry = used.computeIfAbsent(feed, k -> {
                    final Map<String, Object> map = new LinkedHashMap<>();
                    map.put("feed", k);
                    map.put("loader", NullSafe.get(loader, DocRef::getName));
                    map.put("loader_uuid", NullSafe.get(loader, DocRef::getUuid));
                    map.put("used_by", new ArrayList<>());
                    return map;
                });
                addOnce(listOf(entry, "used_by"), pipeline.name());
                if (loader != null && loader.getUuid() != null && !hasUuid(loaders, loader.getUuid())) {
                    loaders.add(ReferenceData.ref(loader.getUuid(), loader.getName()));
                }
            }
        }
        for (final Map<String, Object> map : maps.values()) {
            final Set<Object> usedBy = new TreeSet<>(Comparator.comparing(String::valueOf));
            Object loader = null;
            for (final Object feed : listOf(map, "feeds")) {
                final Map<String, Object> use = used.get(String.valueOf(feed));
                if (use != null) {
                    usedBy.addAll(listOf(use, "used_by"));
                    if (loader == null) {
                        loader = use.get("loader");
                    }
                }
            }
            map.put("used_by", new ArrayList<>(usedBy));
            map.put("loader", loader);
        }
        // Loaders and the template by what they are, not what they're called (no 'Reference Loader' or 'Reference
        // Data' is assumed to exist): a complete pipeline with a ReferenceDataFilter loads reference data; a
        // template whose output is reference data makes it.
        for (final Map<String, Object> loader : referenceData.loaderPipelines(index)) {
            if (!hasUuid(loaders, String.valueOf(loader.get("uuid")))) {
                loaders.add(loader);
            }
        }
        final List<Map<String, Object>> templates = candidates(pipelineTemplates.find("reference"));
        Map<String, Object> template = null;
        if (!templates.isEmpty()) {
            template = new LinkedHashMap<>();
            for (final String key : List.of("uuid", "name", "path", "child_must_supply")) {
                template.put(key, templates.getFirst().get(key));
            }
        }

        final List<Map<String, Object>> sortedMaps = new ArrayList<>(maps.values());
        sortedMaps.sort(Comparator.comparing(map -> String.valueOf(map.get("map"))));
        final List<Map<String, Object>> references = new ArrayList<>(used.values());
        references.sort(Comparator.comparing(use -> Objects.requireNonNullElse((String) use.get("feed"), "")));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("maps", sortedMaps);
        result.put("references_in_use", references);
        result.put("loaders", loaders);
        result.put("reference_data_template", template);
        if (!unreadable.isEmpty()) {
            result.put("unreadable", unreadable);
        }
        result.put("hint", HINT);
        return JsonResults.of(result);
    }

    // The maps the XSLTs that write reference data write, each with the pipelines that run the XSLT and their
    // feeds.
    private Map<String, Map<String, Object>> maps(final Map<String, Entry> index,
                                                  final Map<String, Set<String>> feedsByPipeline,
                                                  final List<String> unreadable) {
        final Map<String, Map<String, Object>> maps = new LinkedHashMap<>();
        for (final DocRef xslt : pipelineIndex.containing(XsltDoc.TYPE, ReferenceData.REF_NS)) {
            final Optional<XsltDoc> doc = documentAccess.read(xslt, XsltDoc.class);
            if (doc.isEmpty()) {
                unreadable.add("XSLT '" + xslt.getName() + "': the document cannot be read (deleted, or no "
                               + "permission)");
                continue;
            }
            final String text = NullSafe.string(doc.get().getData());
            if (!text.contains(ReferenceData.REF_NS)) {
                continue;
            }
            final List<Map<String, Object>> written = ReferenceData.mapsInXslt(text);
            if (written.isEmpty()) {
                continue;
            }
            final List<Entry> pipelines = ReferenceData.naming(index, xslt.getUuid());
            for (final Map<String, Object> entry : written) {
                final Map<String, Object> map = maps.computeIfAbsent(String.valueOf(entry.get("map")), k -> {
                    final Map<String, Object> m = new LinkedHashMap<>(entry);
                    m.put("xslt", xslt.getName());
                    m.put("loaded_by", new ArrayList<>());
                    m.put("feeds", new ArrayList<>());
                    return m;
                });
                for (final Entry pipeline : pipelines) {
                    addOnce(listOf(map, "loaded_by"), pipeline.name());
                    final List<Object> feeds = listOf(map, "feeds");
                    final Set<String> more = new TreeSet<>(feedsByPipeline.getOrDefault(pipeline.uuid(), Set.of()));
                    more.removeAll(new LinkedHashSet<>(feeds.stream().map(String::valueOf).toList()));
                    feeds.addAll(more);
                }
            }
        }
        return maps;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listOf(final Map<String, Object> map, final String key) {
        return (List<Object>) map.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> candidates(final Map<String, Object> found) {
        return (List<Map<String, Object>>) found.getOrDefault("candidates", List.of());
    }

    private static void addOnce(final List<Object> list, final Object value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    private static boolean hasUuid(final List<Map<String, Object>> refs, final String uuid) {
        return refs.stream().anyMatch(ref -> uuid.equals(ref.get("uuid")));
    }
}
