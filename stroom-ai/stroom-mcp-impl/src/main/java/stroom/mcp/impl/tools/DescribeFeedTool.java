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
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.StroomLinks;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.feed.shared.FeedDoc;
import stroom.mcp.impl.tools.GeneratedDocs.Found;
import stroom.mcp.impl.tools.IndexDocs.Index;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// `describe_feed` - what a feed holds and, with a field, what one of its fields means and how to search it: for
/// answering questions about the data (e.g. "what is User.DomainName, and how do I find domains ending in
/// example.com?"), not for building. Read only. From the documentation generated for the feed's pipelines and
/// indexes (found by their mcp-generated tag), the mapping kept with the events XSLT, and the indexes' own field
/// lists as Stroom has them from Elasticsearch or Lucene.
class DescribeFeedTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DescribeFeedTool.class);

    static final String NAME = "describe_feed";
    static final String FEED_ARG = "feed";
    static final String FIELD_ARG = "field";
    /// A doc's Purpose and data section, up to this.
    static final int PURPOSE_CHARS = 2_000;
    /// Rows about the field, across the docs.
    static final int ROWS_SHOWN = 12;
    static final String HINT = "Answer the user in plain words from this: what the feed is, the kinds of event it "
                               + "has, and for a field its meaning (the documented rows' descriptions), where it "
                               + "comes from (the event-logging path and the source field), and how to search it "
                               + "(how_to_search, with the user's value put in). Give the documentation links for "
                               + "more.";
    private static final Map<String, String> KINDS = Map.of("translation", "events", "index", "indexing",
            "cef", "CEF output", "forwarding", "CEF output", "discovery", "discovery index");

    private final FeedNames feedNames;
    private final ProcessorFilters processorFilters;
    private final PipelineShapes pipelineShapes;
    private final KeptMappings keptMappings;
    private final GeneratedDocs generatedDocs;
    private final IndexDocs indexDocs;
    private final DocumentAccess documentAccess;

    @Inject
    DescribeFeedTool(final FeedNames feedNames,
                     final ProcessorFilters processorFilters,
                     final PipelineShapes pipelineShapes,
                     final KeptMappings keptMappings,
                     final GeneratedDocs generatedDocs,
                     final IndexDocs indexDocs,
                     final DocumentAccess documentAccess) {
        this.feedNames = feedNames;
        this.processorFilters = processorFilters;
        this.pipelineShapes = pipelineShapes;
        this.keptMappings = keptMappings;
        this.generatedDocs = generatedDocs;
        this.indexDocs = indexDocs;
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
                .note("The feed's pipelines are found from their processor filters, which need the Manage "
                      + "Processors permission; without it, only the generated documentation named after the feed "
                      + "is read. An index's fields are listed as a query lists them, which needs Use on it.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("What a feed holds and, with field, what one of its fields means and how to search "
                             + "it: for answering questions about the data (e.g. \"what is User.DomainName, and "
                             + "how do I find domains ending in example.com?\"), not for building. Read only. "
                             + "From the documentation generated for the feed's pipelines and indexes (the events "
                             + "pipeline's kinds of event, purpose and field mapping; each index's fields with "
                             + "their descriptions and the event-logging path each comes from) and the indexes' "
                             + "own field lists with their Elasticsearch or Lucene types, which give how a field "
                             + "can be searched through Stroom. Each doc comes with its link, to give the user.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(FEED_ARG, "The feed's name, as the user gives it (case does not "
                                                     + "matter).")
                        .addStringProperty(FIELD_ARG, "A field to explain, as the user names it: an index field "
                                                      + "(User.DomainName, UserId), an event-logging path "
                                                      + "(EventSource/User/Id) or a source field. Omit for an "
                                                      + "overview of the feed.")
                        .required(FEED_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String feed = stringArg(call, FEED_ARG);
        if (feed == null) {
            return JsonResults.refused("Give the feed's name.");
        }
        final String field = stringArg(call, FIELD_ARG);
        try {
            return JsonResults.of(describe(feed, field, call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> describe(final String feed,
                                         final String field,
                                         final ToolCall call,
                                         final ToolCallContext context) {
        final StroomLinks links = context.links();
        final String resolved = feedNames.resolve(feed, NAME, call.id(), context);
        final List<DocRef> named = feedNames.feedsNamed(resolved);
        final FeedDoc feedDoc = named.isEmpty()
                ? null
                : documentAccess.read(named.getFirst(), FeedDoc.class).orElse(null);
        if (feedDoc == null) {
            throw new ToolRefusal(FeedNames.notFound("No feed named '" + resolved + "'"));
        }
        documentAccess.logView(context.session(), named.getFirst(), "Feed described by the " + NAME + " tool");
        final String name = NullSafe.nonBlankStringElse(feedDoc.getName(), named.getFirst().getName());
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("feed", feedSummary(feedDoc, name));

        // The feed's pipelines: those reading its raw data, then those reading their Events (indexing, CEF).
        final List<Map<String, Object>> pipelines = processorFilters.pipelinesReading(name);
        for (final Map<String, Object> pipeline : List.copyOf(pipelines)) {
            if (Boolean.TRUE.equals(pipeline.get("reads_events"))) {
                continue;
            }
            for (final Map<String, Object> follow : processorFilters.followOnPipelines(
                    String.valueOf(pipeline.get("uuid")), Set.of(name))) {
                if (pipelines.stream().noneMatch(p -> p.get("uuid").equals(follow.get("uuid")))) {
                    final Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("uuid", follow.get("uuid"));
                    entry.put("name", follow.get("name"));
                    entry.put("reads_events", true);
                    pipelines.add(entry);
                }
            }
        }

        final Described described = new Described();
        for (final Map<String, Object> pipeline : pipelines) {
            describePipeline(String.valueOf(pipeline.get("uuid")), links, described);
        }
        out.put("pipelines", described.pipelines);
        if (!described.kinds.isEmpty()) {
            out.put("event_kinds", described.kinds);
        }
        if (!described.purposes.isEmpty()) {
            out.put("purpose", described.purposes);
        }
        if (!processorFilters.canManageProcessors()) {
            out.put("processor_filters", "Your processor filters can't be read without the Manage Processors "
                                         + "permission, so the feed's pipelines aren't found from them.");
        }

        final List<String> sources = new ArrayList<>();
        final List<Map<String, Object>> fieldHits = new ArrayList<>();
        final List<Map<String, Object>> indexOut = new ArrayList<>();
        for (final Index index : described.indexes) {
            indexOut.add(describeIndex(index, field, links, described, fieldHits));
        }
        if (!indexOut.isEmpty()) {
            out.put("indexes", indexOut);
        }

        if (described.pipelines.isEmpty() && described.docsText.isEmpty()) {
            // No processor filter names the feed: generated docs whose names hold it (pipelines are named after
            // their feed).
            for (final Found found : generatedDocs.generatedNamedLike(name, 10)) {
                final String text = generatedDocs.text(found.docRef());
                if (text == null) {
                    continue;
                }
                described.docsText.add(new DocText(found.docRef().getName(), text));
                @SuppressWarnings("unchecked") final List<Object> docs = (List<Object>) out.computeIfAbsent(
                        "documentation", k -> new ArrayList<>());
                final Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("name", found.docRef().getName());
                doc.put("link", links.documentTarget(found.docRef()));
                docs.add(doc);
            }
        }

        if (field != null) {
            out.put("field", aboutField(field, described, fieldHits, sources));
        }
        if (described.pipelines.isEmpty() && described.docsText.isEmpty()) {
            out.put("note", "No pipeline's processor filter reads '" + name + "', and no generated documentation "
                            + "is named after it: what the feed holds is not documented yet.");
        }
        out.put("hint", HINT);
        return out;
    }

    private static Map<String, Object> feedSummary(final FeedDoc feedDoc, final String name) {
        final Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", name);
        summary.put("uuid", feedDoc.getUuid());
        if (NullSafe.isNonEmptyString(feedDoc.getDescription())) {
            summary.put("description", feedDoc.getDescription());
        }
        if (NullSafe.isNonEmptyString(feedDoc.getClassification())) {
            summary.put("classification", feedDoc.getClassification());
        }
        if (NullSafe.isNonEmptyString(feedDoc.getStreamType())) {
            summary.put("streamType", feedDoc.getStreamType());
        }
        if (feedDoc.getStatus() != null) {
            summary.put("status", feedDoc.getStatus().name());
        }
        return summary;
    }

    private void describePipeline(final String uuid, final StroomLinks links, final Described described) {
        final DocRef ref = new DocRef(PipelineDoc.TYPE, uuid);
        final Optional<PipelineDoc> pipeline = documentAccess.read(ref, PipelineDoc.class);
        if (pipeline.isEmpty()) {
            return;
        }
        final String pipelineName = pipeline.get().getName();
        try {
            final Optional<KeptMapping.Kept> kept = keptMappings.keptMapping(uuid);
            // The plan kept with its XSLT says what it is; an XSLT written by hand, the pipeline's elements.
            final String stage = kept.map(KeptMapping.Kept::kind).orElseGet(() -> pipelineShapes.shape(uuid).stage());
            final String kind = KINDS.getOrDefault(stage, stage);
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("pipeline", pipelineName);
            entry.put("uuid", uuid);
            entry.put("kind", kind);
            if (kept.isPresent() && "translation".equals(kept.get().kind())) {
                // The kept payload holds the mapping under 'mapping' (with its schema version): passed whole, it
                // never validated, and no event kinds were ever listed.
                final Object mapping = kept.get().payload().get("mapping");
                described.kinds.addAll(FeedDocumentation.eventKinds(mapping instanceof final Map<?, ?> map
                        ? map
                        : Map.of()));
            }
            final DocRef withName = new DocRef(PipelineDoc.TYPE, uuid, pipelineName);
            final Optional<Found> doc = generatedDocs.generatedDoc(pipelineName,
                    PipelineIndex.path(documentAccess, withName));
            final String text = doc.map(found -> generatedDocs.text(found.docRef())).orElse(null);
            if (text != null) {
                entry.put("documentation", documentation(doc.get(), links));
                described.docsText.add(new DocText(doc.get().docRef().getName(), text));
                final String purpose = FeedDocumentation.section(text, "Purpose and data");
                if (!purpose.isEmpty()) {
                    final Map<String, Object> said = new LinkedHashMap<>();
                    said.put("doc", doc.get().docRef().getName());
                    said.put("text", purpose.length() > PURPOSE_CHARS
                            ? purpose.substring(0, PURPOSE_CHARS) + " ..."
                            : purpose);
                    described.purposes.add(said);
                }
            }
            if ("indexing".equals(kind) || "discovery index".equals(kind)) {
                try {
                    for (final Index index : indexDocs.written(pipelineShapes.merged(uuid))) {
                        if (described.indexes.stream().noneMatch(i -> i.docRef().getUuid().equals(
                                index.docRef().getUuid()))) {
                            described.indexes.add(index);
                        }
                    }
                } catch (final PipelineUnreadableException e) {
                    LOGGER.debug(() -> "Unable to find the indexes of " + uuid + ": " + e.getMessage());
                }
            }
            described.pipelines.add(entry);
        } catch (final PipelineUnreadableException e) {
            // Seen in production: a pipeline whose stored structure Stroom no longer loads ('Attempt to set
            // property "compressionLevel" on element "rollingFileAppender" but property is unknown') failed the
            // whole feed's description. It is listed with the reason; the rest of the feed is still described.
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("pipeline", pipelineName);
            entry.put("uuid", uuid);
            entry.put("kind", "unknown");
            entry.put("link", links.documentTarget(ref));
            entry.put("problem", "Stroom couldn't load this pipeline, so it isn't described: " + e.getMessage());
            described.pipelines.add(entry);
        }
    }

    private Map<String, Object> describeIndex(final Index index,
                                              final String field,
                                              final StroomLinks links,
                                              final Described described,
                                              final List<Map<String, Object>> fieldHits) {
        final DocRef ref = index.docRef();
        final String indexName = ref.getName() == null && index.elastic() != null
                ? index.elastic().getName()
                : ref.getName();
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", indexName);
        entry.put("uuid", ref.getUuid());
        entry.put("backend", index.backend());
        if (NullSafe.isNonEmptyString(index.indexName())) {
            entry.put("index_name", index.indexName());
        }
        final Optional<Found> doc = documentAccess.canView(ref)
                ? generatedDocs.generatedDoc(indexName, PipelineIndex.path(documentAccess, ref))
                : Optional.empty();
        final String text = doc.map(found -> generatedDocs.text(found.docRef())).orElse(null);
        if (text != null) {
            entry.put("documentation", documentation(doc.get(), links));
            described.docsText.add(new DocText(doc.get().docRef().getName(), text));
        }
        List<Map<String, Object>> fields;
        try {
            fields = indexDocs.fields(index);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to list the fields of " + ref, e);
            entry.put("fields_error", "Stroom could not list the index's fields: " + PipelineShapes.reason(e));
            fields = List.of();
        }
        entry.put("fields", fields.size());
        if (field != null) {
            final String want = FeedDocumentation.norm(field);
            final List<Map<String, Object>> same = fields.stream()
                    .filter(f -> FeedDocumentation.norm(String.valueOf(f.get("name"))).equals(want))
                    .toList();
            for (final Map<String, Object> f : same) {
                fieldHits.add(fieldHit(indexName, index.backend(), f, fields));
            }
            if (same.isEmpty()) {
                final List<String> like = FeedDocumentation.similar(field, fields.stream()
                        .map(f -> String.valueOf(f.get("name")))
                        .toList());
                if (!like.isEmpty()) {
                    entry.put("similar_fields", like);
                }
            }
        }
        return entry;
    }

    private static Map<String, Object> fieldHit(final String indexName,
                                                final String backend,
                                                final Map<String, Object> field,
                                                final List<Map<String, Object>> fields) {
        final String fieldName = String.valueOf(field.get("name"));
        final List<Map<String, Object>> subs = fields.stream()
                .filter(g -> String.valueOf(g.get("name")).toLowerCase(Locale.ROOT)
                        .startsWith(fieldName.toLowerCase(Locale.ROOT) + "."))
                .toList();
        final String kind = typeOf(field);
        final Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("index", indexName);
        hit.put("field", fieldName);
        hit.put("type", kind);
        if (field.get("analyzer") != null) {
            hit.put("analyzer", field.get("analyzer"));
        }
        if (!subs.isEmpty()) {
            hit.put("sub_fields", subs.stream()
                    .map(g -> g.get("name") + " (" + typeOf(g) + ")")
                    .toList());
        }
        final List<String> advice = new ArrayList<>(FeedDocumentation.searchAdvice(backend, kind,
                NullSafe.string((String) field.get("analyzer")), !Boolean.FALSE.equals(field.get("case_sensitive"))));
        for (final Map<String, Object> sub : subs) {
            if ("keyword".equals(typeOf(sub)) && "text".equals(kind)) {
                advice.add("Its keyword sub-field " + sub.get("name") + ": "
                           + String.join(" ", FeedDocumentation.searchAdvice(backend, "keyword", "", true)));
                break;
            }
        }
        hit.put("how_to_search", advice);
        return hit;
    }

    private Map<String, Object> aboutField(final String field,
                                           final Described described,
                                           final List<Map<String, Object>> fieldHits,
                                           final List<String> sources) {
        // Index docs first: they give the field's source path, which then finds it in the events doc.
        for (final DocText doc : described.docsText) {
            for (final Map<String, String> row : FeedDocumentation.rowsAbout(doc.text(), field, List.of())) {
                row.forEach((key, cell) -> {
                    if (key.startsWith("From")) {
                        FeedDocumentation.named(cell).stream()
                                .filter(source -> source.contains("/"))
                                .forEach(sources::add);
                    }
                });
            }
        }
        final List<Map<String, Object>> rows = new ArrayList<>();
        final Map<String, Object> about = new LinkedHashMap<>();
        about.put("name", field);
        final Set<String> paths = new LinkedHashSet<>(sources);
        final List<Map<String, Object>> mentioned = new ArrayList<>();
        for (final DocText doc : described.docsText) {
            for (final Map<String, String> row : FeedDocumentation.rowsAbout(doc.text(), field, sources)) {
                final Map<String, Object> shown = new LinkedHashMap<>();
                shown.put("doc", doc.name());
                row.forEach((key, value) -> {
                    if (NullSafe.isNonEmptyString(value)) {
                        shown.put(key, value);
                    }
                });
                rows.add(shown);
                // An events doc's row is the path itself (an index doc written by hand has no From column).
                row.forEach((key, cell) -> {
                    if ("XPath".equals(key) || "EventDetail".equals(key)) {
                        for (final String line : cell.split("\\R")) {
                            FeedDocumentation.named(line).stream()
                                    .limit(1)
                                    .filter(path -> path.contains("/"))
                                    .forEach(path -> paths.add(("EventDetail".equals(key)
                                            ? "EventDetail/"
                                            : "") + path));
                        }
                    }
                });
            }
            final List<String> said = FeedDocumentation.mentions(doc.text(), field);
            if (!said.isEmpty()) {
                final Map<String, Object> mention = new LinkedHashMap<>();
                mention.put("doc", doc.name());
                mention.put("lines", said.subList(0, Math.min(6, said.size())));
                mentioned.add(mention);
            }
        }
        if (!mentioned.isEmpty()) {
            about.put("mentioned", mentioned);
        }
        if (!fieldHits.isEmpty()) {
            about.put("in_indexes", fieldHits);
        }
        if (!paths.isEmpty()) {
            about.put("event_logging_paths", List.copyOf(paths));
        }
        if (!rows.isEmpty()) {
            about.put("documented", rows.subList(0, Math.min(ROWS_SHOWN, rows.size())));
            if (rows.size() > ROWS_SHOWN) {
                about.put("note", (rows.size() - ROWS_SHOWN) + " more rows in the docs: give the user the links");
            }
        }
        if (fieldHits.isEmpty() && rows.isEmpty() && mentioned.isEmpty()) {
            about.put("found", false);
            about.put("hint", "Neither the feed's documentation nor its indexes name '" + field + "'. Check the "
                              + "spelling against similar_fields, or ask the user where they saw it.");
        }
        return about;
    }

    private static Map<String, Object> documentation(final Found found, final StroomLinks links) {
        final Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("name", found.docRef().getName());
        doc.put("link", links.documentTarget(new DocRef(DocumentationDoc.TYPE, found.docRef().getUuid(),
                found.docRef().getName())));
        doc.put("generated", found.generated());
        return doc;
    }

    private static String typeOf(final Map<String, Object> field) {
        return String.valueOf(field.get("elasticsearch_type") != null
                ? field.get("elasticsearch_type")
                : field.get("type"));
    }


    // --------------------------------------------------------------------------------


    /// What has been found of the feed so far.
    private static final class Described {

        private final List<Map<String, Object>> pipelines = new ArrayList<>();
        private final List<Index> indexes = new ArrayList<>();
        private final List<String> kinds = new ArrayList<>();
        private final List<Map<String, Object>> purposes = new ArrayList<>();
        private final List<DocText> docsText = new ArrayList<>();
    }

    /// A doc read, by its name.
    private record DocText(String name, String text) {

    }
}
