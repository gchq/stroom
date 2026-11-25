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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.SharedFunctions;
import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.processing.MergedPipeline;
import stroom.ai.impl.tools.processing.MergedPipeline.TranslationDoc;
import stroom.ai.impl.tools.processing.XsltStages;
import stroom.ai.impl.tools.processing.XsltStages.Stage;
import stroom.ai.impl.tools.processing.XsltStages.StagedXslt;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// What a build's feed is translated by now, and what its XSLT looks up: what a next version whose translation is
/// written anew loses unless its mapping keeps them (seen in testing: V1.3 drafted from the sample, and V1.2's IP
/// lookups were gone).
class CurrentTranslations {

    /// The key of the lookups the XSLT makes through the shared functions it calls ([SharedXslt#lookupThrough]).
    static final String LOOKUPS_THROUGH_FUNCTIONS = "lookups_through_functions";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CurrentTranslations.class);

    private static final Pattern LOOKUP_MAP = Pattern.compile("stroom:lookup\\(\\s*'([^']+)'");
    private static final int MAX_NAMED = 6;
    // A stream appender's property naming the feed its streams go to.
    private static final String OUTPUT_FEED_PROPERTY = "feed";

    private final Provider<BuildPlan> planProvider;
    private final BuildFacts facts;
    private final PipelineDocs pipelineDocs;
    private final PipelineShapes pipelineShapes;
    private final PipelineTemplates templates;
    private final DocumentAccess documentAccess;
    private final XsltStages xsltStages;

    /// @param planProvider   The build's plan: the feeds it works from.
    /// @param facts          Finds the pipelines translating a feed now.
    /// @param pipelineDocs   Reads pipelines as the user.
    /// @param pipelineShapes Reads a pipeline's layers as Stroom builds them.
    /// @param templates      Reads how a pipeline's XSLTs use shared ones.
    /// @param documentAccess Reads XSLTs as the user.
    /// @param xsltStages     Tells a pipeline's translation XSLT from its others.
    @Inject
    CurrentTranslations(final Provider<BuildPlan> planProvider,
                        final BuildFacts facts,
                        final PipelineDocs pipelineDocs,
                        final PipelineShapes pipelineShapes,
                        final PipelineTemplates templates,
                        final DocumentAccess documentAccess,
                        final XsltStages xsltStages) {
        this.planProvider = planProvider;
        this.facts = facts;
        this.pipelineDocs = pipelineDocs;
        this.pipelineShapes = pipelineShapes;
        this.templates = templates;
        this.documentAccess = documentAccess;
        this.xsltStages = xsltStages;
    }

    /// The pipeline translating the build's feed now (its processor filter reads the raw data), with what its own
    /// XSLT looks up: the reference data the pipeline loads, the `stroom:lookup()` maps the XSLT names, and the
    /// functions and named templates it calls from the XSLTs it imports.
    ///
    /// @param build The build, or null.
    /// @return `pipeline` (`name`, `uuid`), `xslt` (each `name`, `uuid`), and where there are any,
    /// `reference_data`, `lookup_maps`, `functions_called`, `functions_entries`, `templates_called` and
    /// `imports_for_rules`; only `pipeline` when its documents can't be read. Empty when no pipeline translates
    /// the feed, or the build works from no feed.
    Optional<Map<String, Object>> current(final String build) {
        if (NullSafe.isBlankString(build)) {
            return Optional.empty();
        }
        final List<Map<String, Object>> found;
        try {
            found = facts.translating(planProvider.get().worksFrom(build));
        } catch (final RuntimeException e) {
            // Advice: never a reason for the call that asks to fail.
            LOGGER.debug(() -> LogUtil.message("The feed of build {} wasn't read for its translation: {}",
                    build, e.getMessage()));
            return Optional.empty();
        }
        if (found.isEmpty()) {
            return Optional.empty();
        }
        final Map<String, Object> pipeline = found.getFirst();
        final String uuid = String.valueOf(pipeline.get("uuid"));
        final List<PipelineReference> references;
        final List<DocRef> own;
        final List<String> maps = new ArrayList<>();
        final List<Map<String, Object>> uses;
        try {
            references = ownReferences(pipelineDocs.read(uuid));
            own = pipelineShapes.merged(uuid).translationDocs(uuid).stream()
                    .filter(entry -> !entry.inheritedFromTemplate() && XsltDoc.TYPE.equals(entry.doc().getType()))
                    .map(TranslationDoc::doc)
                    .toList();
            for (final DocRef doc : own) {
                maps.addAll(lookupMaps(documentAccess.read(new DocRef(XsltDoc.TYPE, doc.getUuid()), XsltDoc.class)
                        .map(XsltDoc::getData)
                        .orElseThrow(() -> new ToolRefusal("XSLT " + doc.getUuid() + " can't be read"))));
            }
            uses = templates.sharedXsltUsage(List.of(pipeline));
        } catch (final ToolRefusal | PipelineUnreadableException e) {
            LOGGER.debug(() -> LogUtil.message("The current translation {} wasn't read: {}", uuid, e.getMessage()));
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("pipeline", pipeline);
            return Optional.of(result);
        }
        return Optional.of(described(pipeline, own, references, maps, uses));
    }

    // A pipeline's translation XSLT: the translation stage's ([XsltStages]), its own or inherited, never a
    // pre-processor before it; where no XSLT can be told to translate, its first own XSLT.
    private Optional<DocRef> translationXslt(final String uuid) {
        final MergedPipeline merged = pipelineShapes.merged(uuid);
        final Optional<DocRef> staged = XsltStages.first(xsltStages.classify(merged), Stage.TRANSLATION)
                .map(StagedXslt::xslt);
        if (staged.isPresent()) {
            return staged;
        }
        return merged.translationDocs(uuid).stream()
                .filter(entry -> !entry.inheritedFromTemplate() && XsltDoc.TYPE.equals(entry.doc().getType()))
                .map(TranslationDoc::doc)
                .findFirst();
    }

    /// @param pipelineUuid A pipeline.
    /// @return The imports of its own XSLTs that name no XSLT ([PipelineShapes#brokenImports(String)]).
    List<String> brokenImports(final String pipelineUuid) {
        return pipelineShapes.brokenImports(pipelineUuid);
    }

    /// A source type group's pipeline translating its aggregated feed now, found as the build's next version finds
    /// the pipeline it follows (its processor filters read the feed's raw data), by its name: the group's pipeline
    /// name ([SourceTypeGroups#isGroupPipelineName]). Its translation XSLT and where its Events go are read from it.
    ///
    /// @param feed   The aggregated feed.
    /// @param group  The source type group.
    /// @param wanted Whether a pipeline, by its UUID, is one to take: a promoted one, say.
    /// @return The pipeline, its XSLT and its output feed; empty when no such pipeline translates the feed, or it
    /// can't be read.
    Optional<GroupPipeline> groupPipeline(final String feed, final String group, final Predicate<String> wanted) {
        if (NullSafe.isBlankString(feed) || NullSafe.isBlankString(group)) {
            return Optional.empty();
        }
        final List<Map<String, Object>> found;
        try {
            found = facts.translating(List.of(feed));
        } catch (final RuntimeException e) {
            // Advice: never a reason for the call that asks to fail.
            LOGGER.debug(() -> LogUtil.message("The pipelines translating {} weren't read: {}", feed,
                    e.getMessage()));
            return Optional.empty();
        }
        for (final Map<String, Object> pipeline : found) {
            final String name = ToolText.str(pipeline.get("name"));
            final String uuid = ToolText.str(pipeline.get("uuid"));
            if (!SourceTypeGroups.isGroupPipelineName(name, feed, group) || !wanted.test(uuid)) {
                continue;
            }
            try {
                final PipelineDoc doc = pipelineDocs.read(uuid);
                final Optional<DocRef> xslt = translationXslt(uuid);
                if (xslt.isEmpty()) {
                    continue;
                }
                // Where its Events go: a stream appender's feed naming a feed other than the aggregated one is the
                // group's own output feed; none, or the feed itself, is the feed's own Events.
                final String outputFeed = PipelineDocs.addedProperties(doc).stream()
                        .filter(property -> OUTPUT_FEED_PROPERTY.equals(property.getName()))
                        .map(property -> NullSafe.get(property.getValue(), PipelinePropertyValue::getEntity))
                        .filter(entity -> entity != null && NullSafe.isNonBlankString(entity.getName())
                                          && !entity.getName().equalsIgnoreCase(feed))
                        .map(DocRef::getName)
                        .findFirst()
                        .orElse(null);
                return Optional.of(new GroupPipeline(uuid, NullSafe.nonBlankStringElse(doc.getName(), name),
                        xslt.get().getUuid(), xslt.get().getName(), outputFeed));
            } catch (final ToolRefusal | PipelineUnreadableException e) {
                LOGGER.debug(() -> LogUtil.message("Pipeline {} of group {} wasn't read: {}", uuid, group,
                        e.getMessage()));
            }
        }
        return Optional.empty();
    }

    /// @param current What [#current(String)] gave, or null.
    /// @return Whether the current translation looks anything up: reference data, `stroom:lookup()` maps or shared
    /// functions or templates.
    static boolean lookupsOf(final Map<String, Object> current) {
        return current != null && (JsonValues.truthy(current.get("reference_data"))
                                   || JsonValues.truthy(current.get("lookup_maps"))
                                   || JsonValues.truthy(current.get("functions_called"))
                                   || JsonValues.truthy(current.get("templates_called")));
    }

    /// Whether a mapping keeps any of the current translation's lookups: a lookup entry, or an import of a shared
    /// XSLT the current one calls. Read from the mapping's own settings, not its field names.
    ///
    /// @param current What [#current(String)] gave.
    /// @param mapping A translation mapping.
    /// @return True when it keeps one.
    static boolean lookupsKept(final Map<String, Object> current, final TranslationMapping mapping) {
        if (mapping.allEntries().stream().map(FieldMapping::lookup).anyMatch(lookup -> lookup != null)) {
            return true;
        }
        final Set<String> hrefs = new HashSet<>();
        for (final String key : List.of("functions_called", "templates_called")) {
            maps(current.get(key)).forEach(entry -> hrefs.add(String.valueOf(entry.get("href"))));
        }
        final Set<String> used = new HashSet<>(NullSafe.list(mapping.imports()));
        NullSafe.list(mapping.functions()).stream().map(SharedFunctions::href).forEach(used::add);
        NullSafe.list(mapping.shared()).stream().map(SharedTemplate::href).forEach(used::add);
        return hrefs.stream().anyMatch(used::contains);
    }

    /// @param current What [#current(String)] gave, with its `pipeline`.
    /// @return What the current translation looks up, and how a new mapping keeps it, for the user to decide.
    static String lookupsNote(final Map<String, Object> current) {
        final Map<?, ?> pipeline = (Map<?, ?>) current.get("pipeline");
        final List<String> parts = new ArrayList<>();
        if (JsonValues.truthy(current.get("lookup_maps"))) {
            parts.add("looks up reference map(s) " + ToolText.list(JsonValues.strings(current.get("lookup_maps")))
                      + " with stroom:lookup()");
        }
        for (final Map<?, ?> function : first(maps(current.get("functions_called")))) {
            parts.add("calls " + ToolText.str(function.get("function")) + "() from '"
                      + ToolText.str(function.get("href")) + "'" + (JsonValues.truthy(function.get("example"))
                    ? " (as " + ToolText.str(function.get("example")) + ")"
                    : "") + (JsonValues.truthy(function.get(SharedXslt.STROOM_CALLS))
                    ? ", which calls " + String.join(", ", JsonValues.strings(function.get(
                            SharedXslt.STROOM_CALLS)))
                    : ""));
        }
        for (final Map<?, ?> template : first(maps(current.get("templates_called")))) {
            parts.add("calls template " + ToolText.str(template.get("template")) + " from '"
                      + ToolText.str(template.get("href")) + "'" + (JsonValues.truthy(template.get("at"))
                    ? " at " + ToolText.str(template.get("at"))
                    : ""));
        }
        if (JsonValues.truthy(current.get("reference_data"))) {
            parts.add("loads reference data " + ToolText.list(JsonValues.strings(current.get("reference_data"))));
        }
        final Object entries = current.get("functions_entries");
        return "The feed is translated now by '" + ToolText.str(pipeline.get("name")) + "' ("
               + ToolText.str(pipeline.get("uuid"))
               + "), whose XSLT " + String.join("; ", parts) + ". Ask the user whether the new version keeps these "
               + "lookups. To keep them: functions entries " + (JsonValues.truthy(entries)
                ? ToolText.repr(entries)
                : "(href, prefix, namespace)") + " and the fields' xpaths calling the functions as it does, shared "
               + "entries for its templates, lookup entries for its maps; then create_pipeline version_of='"
               + ToolText.str(pipeline.get("uuid")) + "' keep_references=true, so the new version loads the same "
               + "reference data.";
    }

    /// @param pipeline A pipeline.
    /// @return The reference data it loads itself (its own pipelineReferences, not its template's, which a child
    /// of the same template inherits anyway), as Stroom keeps them.
    static List<PipelineReference> ownReferences(final PipelineDoc pipeline) {
        final PipelineData data = pipeline.getPipelineData();
        if (data == null) {
            return List.of();
        }
        // A Context reference needs no feed: its data is each stream's own Context child.
        return NullSafe.list(data.getAddedPipelineReferences()).stream()
                .filter(reference -> (NullSafe.isNonBlankString(NullSafe.get(reference.getFeed(), DocRef::getUuid))
                                      || StreamTypeNames.CONTEXT.equals(reference.getStreamType()))
                                     && NullSafe.isNonBlankString(NullSafe.get(reference.getPipeline(),
                        DocRef::getUuid)))
                .toList();
    }

    /// @param reference A pipeline reference.
    /// @return It in words: `<feed> via <loader pipeline> on <element>`.
    static String referenceText(final PipelineReference reference) {
        return PipelineWrites.describe(reference);
    }

    /// @param xslt An XSLT's text, or null.
    /// @return The reference maps its `stroom:lookup()` calls name, in the order they are written.
    static List<String> lookupMaps(final String xslt) {
        final List<String> maps = new ArrayList<>();
        final Matcher matcher = LOOKUP_MAP.matcher(NullSafe.string(xslt));
        while (matcher.find()) {
            maps.add(matcher.group(1));
        }
        return maps;
    }

    // The current translation as current() gives it, from what was read.
    private static Map<String, Object> described(final Map<String, Object> pipeline,
                                                 final List<DocRef> own,
                                                 final List<PipelineReference> references,
                                                 final List<String> maps,
                                                 final List<Map<String, Object>> uses) {
        final List<Map<String, Object>> functions = new ArrayList<>();
        final List<Map<String, Object>> called = new ArrayList<>();
        final List<Map<String, Object>> rules = new ArrayList<>();
        final Set<List<Object>> entries = new LinkedHashSet<>();
        final List<String> through = new ArrayList<>();
        for (final Map<String, Object> use : uses) {
            if (JsonValues.truthy(use.get("function"))) {
                functions.add(truthy(use, "href", "function", "example", SharedXslt.STROOM_CALLS));
                // A shared function that looks up through Stroom is one of the XSLT's lookups.
                SharedXslt.lookupThrough(ToolText.str(use.get("function")), ToolText.str(use.get("href")), use)
                        .ifPresent(through::add);
                if (JsonValues.truthy(use.get("prefix")) && JsonValues.truthy(use.get("namespace"))) {
                    entries.add(List.of(use.get("href"), use.get("prefix"), use.get("namespace")));
                }
            }
            if (JsonValues.truthy(use.get("template"))) {
                called.add(truthy(use, "href", "template", "at", "with_params"));
            }
            if (JsonValues.truthy(use.get("note")) && use.containsKey("href")) {
                final Map<String, Object> rule = new LinkedHashMap<>();
                rule.put("href", use.get("href"));
                rule.put("note", use.get("note"));
                rules.add(rule);
            }
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipeline", pipeline);
        result.put("xslt", own.stream().map(doc -> {
            final Map<String, Object> xslt = new LinkedHashMap<>();
            xslt.put("name", doc.getName());
            xslt.put("uuid", doc.getUuid());
            return xslt;
        }).toList());
        if (!references.isEmpty()) {
            result.put("reference_data", references.stream().map(CurrentTranslations::referenceText).toList());
        }
        if (!maps.isEmpty()) {
            result.put("lookup_maps", List.copyOf(new TreeSet<>(maps)));
        }
        if (!through.isEmpty()) {
            result.put(LOOKUPS_THROUGH_FUNCTIONS, List.copyOf(new LinkedHashSet<>(through)));
        }
        if (!functions.isEmpty()) {
            result.put("functions_called", functions);
        }
        if (!entries.isEmpty()) {
            result.put("functions_entries", entries.stream().map(entry -> {
                final Map<String, Object> map = new LinkedHashMap<>();
                map.put("href", entry.get(0));
                map.put("prefix", entry.get(1));
                map.put("namespace", entry.get(2));
                return map;
            }).toList());
        }
        if (!called.isEmpty()) {
            result.put("templates_called", called);
        }
        if (!rules.isEmpty()) {
            result.put("imports_for_rules", rules);
        }
        return result;
    }

    // The use's values of those keys that are set (not null, false, zero or empty), in that order.
    private static Map<String, Object> truthy(final Map<String, Object> use, final String... keys) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final String key : keys) {
            if (JsonValues.truthy(use.get(key))) {
                out.put(key, use.get(key));
            }
        }
        return out;
    }

    private static List<Map<?, ?>> maps(final Object value) {
        final List<Map<?, ?>> out = new ArrayList<>();
        if (value instanceof final List<?> list) {
            list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<?, ?>) item)
                    .forEach(out::add);
        }
        return out;
    }

    private static List<Map<?, ?>> first(final List<Map<?, ?>> entries) {
        return entries.subList(0, Math.min(MAX_NAMED, entries.size()));
    }

    /// A source type group's pipeline translating its feed now.
    ///
    /// @param uuid       The pipeline's UUID.
    /// @param name       Its name.
    /// @param xsltUuid   Its translation XSLT's UUID.
    /// @param xsltName   Its translation XSLT's name.
    /// @param outputFeed The feed its Events go to where it is one of the group's own, else null (the feed's own
    ///                   Events).
    record GroupPipeline(String uuid, String name, String xsltUuid, String xsltName, String outputFeed) {

    }
}
