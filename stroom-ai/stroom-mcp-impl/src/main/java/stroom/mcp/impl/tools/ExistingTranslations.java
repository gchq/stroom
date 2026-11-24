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
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.generation.LeafReading;
import stroom.ai.impl.tools.generation.RuleReading;
import stroom.ai.impl.tools.generation.XsltReader;
import stroom.ai.impl.tools.processing.MergedPipeline.TranslationDoc;
import stroom.ai.impl.tools.sample.SourceTypeProfiles;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/// The pipeline already translating what a build is to translate (decided by the user, 2026-11-24): its feed, or its
/// source type group's source types, whoever wrote it (promoted by the tools, written by hand, a content pack's). The
/// plan offers to start from it (copy_pipeline, then rebuild_mapping) rather than draft a new translation: seen in
/// chat 192, an existing promoted pipeline translated the build's source types, the plan routed to drafting from
/// scratch, and the agent spent some ten calls copying the old catalogue by hand.
class ExistingTranslations {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExistingTranslations.class);

    /// The build record's value: the build starts from the existing pipeline.
    static final String EXISTING = "existing";
    /// The build record's value: the build drafts a new translation.
    static final String DRAFT = "draft";
    /// The most example TypeIds said.
    private static final int EXAMPLES = 3;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final Provider<SourceTypeRegistries> registries;
    private final Provider<DocFinder> docFinder;
    private final DocumentAccess documentAccess;
    private final WriteGuard guard;
    private final PipelineShapes pipelineShapes;

    /// @param registries     The feeds' source type registries.
    /// @param docFinder      Finds a pipeline by name.
    /// @param documentAccess Reads documents as the user.
    /// @param guard          Tells a build's documents from others, and reads tags.
    /// @param pipelineShapes Reads a pipeline's own XSLT.
    @Inject
    ExistingTranslations(final Provider<SourceTypeRegistries> registries,
                         final Provider<DocFinder> docFinder,
                         final DocumentAccess documentAccess,
                         final WriteGuard guard,
                         final PipelineShapes pipelineShapes) {
        this.registries = registries;
        this.docFinder = docFinder;
        this.documentAccess = documentAccess;
        this.guard = guard;
        this.pipelineShapes = pipelineShapes;
    }

    /// The pipeline translating what the build is to: for a build scoped to some of an aggregated feed's source
    /// types, the pipeline the registry's rows of those source types name (by its UUID, else by its name, as the
    /// versioning lookup finds a group's pipeline); else the pipeline translating the feed now (its processor filter
    /// reads the raw data). Never one of the build's own.
    ///
    /// @param build       The build.
    /// @param sources     The existing feeds it works from.
    /// @param translating The pipelines translating feeds now ([BuildFacts#translating]).
    /// @return The pipeline (`name`, `uuid`, and `source_types` for a group's), or empty when none is found.
    Optional<Map<String, Object>> find(final String build,
                                       final List<String> sources,
                                       final Function<List<String>, List<Map<String, Object>>> translating) {
        if (NullSafe.isBlankString(build) || NullSafe.isEmptyCollection(sources)) {
            return Optional.empty();
        }
        try {
            boolean aggregated = false;
            for (final String feed : sources) {
                final Optional<SourceTypeRegistries.Found> registry = registries.get().registry(build, feed);
                if (registry.isPresent()) {
                    aggregated = true;
                    final Optional<Map<String, Object>> group = groupPipeline(build, registry.get().state());
                    if (group.isPresent()) {
                        return group;
                    }
                }
            }
            if (aggregated) {
                // An aggregated feed's pipelines each translate a group of its source types: only the one the
                // build's scope names is the build's (found in an e2e walk: a new group was offered the first
                // group's pipeline, which reads every stream of the feed).
                return Optional.empty();
            }
            return translating.apply(sources).stream()
                    .filter(pipeline -> !inBuild(String.valueOf(pipeline.get("uuid"))))
                    .findFirst();
        } catch (final RuntimeException e) {
            // Advice: never a reason for the plan or a call to fail.
            LOGGER.debug(() -> LogUtil.message("No existing translation found for build {}: {}", build,
                    e.getMessage()), e);
            return Optional.empty();
        }
    }

    /// What the user is told of the pipeline before choosing: its own XSLT, whether the tools built it, when and by
    /// whom it was last changed, and the event types it writes (read from its XSLT).
    ///
    /// @param pipeline The pipeline ([#find]).
    /// @return It with `xslt` (`name`, `uuid`), `built_by_tools`, `changed`, `changed_by`, `event_types` and
    /// `examples` where they can be read.
    Map<String, Object> describe(final Map<String, Object> pipeline) {
        final Map<String, Object> out = new LinkedHashMap<>(pipeline);
        final String uuid = String.valueOf(pipeline.get("uuid"));
        try {
            final Optional<DocRef> own = pipelineShapes.merged(uuid).translationDocs(uuid).stream()
                    .filter(doc -> !doc.inheritedFromTemplate() && XsltDoc.TYPE.equals(doc.doc().getType()))
                    .map(TranslationDoc::doc)
                    .findFirst();
            if (own.isEmpty()) {
                return out;
            }
            final Optional<XsltDoc> xslt = documentAccess.read(new DocRef(XsltDoc.TYPE, own.get().getUuid()),
                    XsltDoc.class);
            if (xslt.isEmpty()) {
                return out;
            }
            final XsltDoc doc = xslt.get();
            final Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("name", doc.getName());
            ref.put("uuid", doc.getUuid());
            out.put("xslt", ref);
            out.put("built_by_tools", KeptMapping.readMapping(doc.getDescription()).isPresent()
                                      || guard.tags(new DocRef(XsltDoc.TYPE, doc.getUuid()))
                                              .contains(WriteGuard.GENERATED));
            if (doc.getUpdateTimeMs() != null) {
                out.put("changed", DAY.format(Instant.ofEpochMilli(doc.getUpdateTimeMs())));
            }
            if (NullSafe.isNonBlankString(doc.getUpdateUser())) {
                out.put("changed_by", doc.getUpdateUser());
            }
            final List<String> typeIds = typeIds(NullSafe.string(doc.getData()));
            out.put("event_types", typeIds.size());
            out.put("examples", typeIds.subList(0, Math.min(EXAMPLES, typeIds.size())));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The existing translation {} wasn't described: {}", uuid,
                    e.getMessage()), e);
        }
        return out;
    }

    /// @param described The pipeline as [#describe] gave it.
    /// @return It in words, for the user: its name, what it translates, who wrote it and its event types.
    static String said(final Map<String, Object> described) {
        final StringBuilder out = new StringBuilder("'").append(ToolText.str(described.get("name"))).append("'");
        final List<String> types = JsonValues.strings(described.get("source_types"));
        if (!types.isEmpty()) {
            out.append(" translates ").append(types.size() == 1
                    ? types.getFirst()
                    : String.join(", ", types.subList(0, types.size() - 1)) + " and " + types.getLast());
        }
        final List<String> parts = new ArrayList<>();
        if (Boolean.TRUE.equals(described.get("built_by_tools"))) {
            parts.add("built by Stroom AI");
        } else if (described.containsKey("built_by_tools")) {
            parts.add("not built by Stroom AI: its XSLT was written by hand" + (described.get("changed") == null
                    ? ""
                    : ", last changed " + described.get("changed") + (described.get("changed_by") == null
                            ? ""
                            : " by " + described.get("changed_by"))));
        }
        if (described.get("event_types") instanceof final Integer count && count > 0) {
            final List<String> examples = JsonValues.strings(described.get("examples"));
            parts.add(count + " event type" + (count == 1
                    ? ""
                    : "s") + (examples.isEmpty()
                    ? ""
                    : ", e.g. " + String.join(", ", examples)));
        }
        if (!parts.isEmpty()) {
            out.append(" (").append(String.join("; ", parts)).append(")");
        }
        return out.toString();
    }

    // The TypeIds the XSLT's rules write as constants, each once, in order; one per rule written otherwise.
    private static List<String> typeIds(final String code) {
        final Set<String> out = new LinkedHashSet<>();
        int other = 0;
        for (final RuleReading rule : XsltReader.read(code).rules()) {
            if (rule.drop()) {
                continue;
            }
            final Optional<String> typeId = rule.leaves().stream()
                    .filter(leaf -> "EventDetail/TypeId".equals(leaf.path()))
                    .map(LeafReading::value)
                    .filter(NullSafe::isNonBlankString)
                    .findFirst();
            if (typeId.isPresent()) {
                out.add(typeId.get());
            } else {
                other++;
            }
        }
        final List<String> listed = new ArrayList<>(out);
        for (int n = 0; n < other; n++) {
            listed.add("(a TypeId from the record)");
        }
        return listed;
    }

    // The pipeline the registry's rows of the build's scoped source types name, with those source types.
    private Optional<Map<String, Object>> groupPipeline(final String build, final Map<String, Object> state) {
        final Map<String, Object> scope = JsonValues.mapOrEmpty(state.get(SourceTypeProfiles.SCOPE));
        if (!build.equals(JsonValues.text(scope.get(SourceTypeScope.BUILD)))) {
            return Optional.empty();
        }
        final Set<String> scoped = new LinkedHashSet<>(JsonValues.strings(scope.get(SourceTypeScope.SOURCE_TYPES)));
        final Map<String, Map<String, Object>> byPipeline = new LinkedHashMap<>();
        for (final Map<String, Object> row : JsonValues.maps(state.get("rows"))) {
            final String type = JsonValues.text(row.get("name"));
            final Map<String, Object> pipeline = JsonValues.mapOrEmpty(row.get(SourceTypeGroups.PIPELINE));
            if (!scoped.contains(type) || pipeline.isEmpty()) {
                continue;
            }
            resolve(JsonValues.text(pipeline.get("uuid")), JsonValues.text(pipeline.get("name")))
                    .ifPresent(ref -> {
                        final Map<String, Object> entry = byPipeline.computeIfAbsent(ref.getUuid(), uuid -> {
                            final Map<String, Object> made = new LinkedHashMap<>();
                            made.put("name", ref.getName());
                            made.put("uuid", ref.getUuid());
                            made.put("source_types", new ArrayList<String>());
                            return made;
                        });
                        @SuppressWarnings("unchecked") final List<String> types =
                                (List<String>) entry.get("source_types");
                        types.add(type);
                    });
        }
        return byPipeline.values().stream().findFirst();
    }

    // A pipeline outside any build the user can view: by its UUID, else the one pipeline of its name.
    private Optional<DocRef> resolve(final String uuid, final String name) {
        if (!uuid.isEmpty() && !inBuild(uuid)) {
            final Optional<PipelineDoc> doc = documentAccess.read(new DocRef(PipelineDoc.TYPE, uuid),
                    PipelineDoc.class);
            if (doc.isPresent()) {
                return Optional.of(new DocRef(PipelineDoc.TYPE, uuid, doc.get().getName()));
            }
        }
        if (name.isEmpty()) {
            return Optional.empty();
        }
        final List<DocRef> named = NullSafe.list(docFinder.get().findByName(PipelineDoc.TYPE, name, false)).stream()
                .filter(ref -> name.equals(ref.getName()))
                .filter(ref -> !inBuild(ref.getUuid()))
                .filter(documentAccess::canView)
                .toList();
        return named.size() == 1
                ? Optional.of(named.getFirst())
                : Optional.empty();
    }

    private boolean inBuild(final String uuid) {
        return guard.buildOf(new DocRef(PipelineDoc.TYPE, uuid)).isPresent();
    }
}
