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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.DispatcherSpec;
import stroom.ai.impl.tools.generation.DispatcherXslt;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.HelpersXslt;
import stroom.ai.impl.tools.generation.ModuleChecks;
import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.generation.SourceTypeModule;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.generation.XsltNames;
import stroom.ai.impl.tools.sample.FeedSampling.SurveyRecord;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// An aggregated feed's source type groups, as `build_translation_xslt group=` builds them: the group's mapping
/// split from the feed's common module (the wrapper's entries every group writes one way, its parts), the common
/// module and the feed's helpers made when the feed has none, and, once the group is saved, the registry's rows for
/// its source types set `translated`, placed in the pipeline the user chose, and every dispatcher of the feed
/// generated from the registry ([#regenerate]): the feed's pipeline's, and one for each group with a pipeline of its
/// own, so no two pipelines claim a source type.
///
/// A later build finds the feed's modules through the registry, beside the feed. It changes a promoted one through a
/// working copy with a UUID of its own ([#workingCopy]), and every promoted module importing that one is copied too,
/// importing the copy, so stepping in the build runs the copies; promotion points them all at the originals again
/// ([#toOriginals]), the dispatchers regenerated against the originals' UUIDs, before the copies are written back.
class SourceTypeGroups {

    /// What a feed's common module is named after: `<feed> common`.
    static final String COMMON_SUFFIX = " common";
    // The kept mapping's rule of the common module, which has none of its own.
    private static final String COMMON_RULE = "common";
    private static final String SYSTEM_NAME = "EventSource/System/Name";
    private static final String ENVIRONMENT = "EventSource/System/Environment";
    /// A group placed in the feed's pipeline, shared by its groups.
    static final String SHARED = "shared";
    /// A group placed in a pipeline of its own.
    static final String OWN = "own";
    /// A group placed in a new version of the feed's pipeline (copy_pipeline).
    static final String NEW_VERSION = "new_version";
    /// The placements a new group may be given.
    static final List<String> PLACEMENTS = List.of(SHARED, OWN, NEW_VERSION);
    // A row's keys: where it is translated, and its group's reference data.
    static final String PIPELINE = "pipeline";
    static final String OUTPUT_FEED = "output_feed";
    static final String REFERENCES = "references";
    private static final String MODULES = "modules";
    private static final String DISPATCHER = "dispatcher";
    private static final String GROUP_XSLT = "group_xslt";

    private final WriteGuard guard;
    private final DocumentAccess documentAccess;
    private final SourceTypeRegistries registries;
    private final TranslationDocs translationDocs;
    private final TranslationBuilds builds;
    private final BuildRecords records;

    @Inject
    SourceTypeGroups(final WriteGuard guard,
                     final DocumentAccess documentAccess,
                     final SourceTypeRegistries registries,
                     final TranslationDocs translationDocs,
                     final TranslationBuilds builds,
                     final BuildRecords records) {
        this.guard = guard;
        this.documentAccess = documentAccess;
        this.registries = registries;
        this.translationDocs = translationDocs;
        this.builds = builds;
        this.records = records;
    }

    /// @param feed A feed's name.
    /// @return The name of its common module.
    static String commonName(final String feed) {
        return feed + COMMON_SUFFIX;
    }

    /// @param uuid A document's UUID.
    /// @param name Its name.
    /// @return The href importing it by UUID, as Stroom resolves `uuid='…', name='…'` (a rename doesn't break it,
    /// and a name two XSLTs share can't be imported by name).
    static String href(final String uuid, final String name) {
        return "uuid='" + uuid + "', name='" + name.replace("'", "") + "'";
    }

    /// @param uuid An XSLT's UUID.
    /// @return The build it is in; empty outside a build.
    Optional<String> buildOf(final String uuid) {
        return guard.buildOf(new DocRef(XsltDoc.TYPE, uuid));
    }

    /// @param build A build.
    /// @param name  A name.
    /// @return The UUID of the build's XSLT of that name, if it has one.
    Optional<String> xsltNamed(final String build, final String name) {
        return Optional.ofNullable(xsltInBuild(build, name)).map(WriteGuard.BuildDoc::uuid);
    }

    /// The registry of the build's aggregated feed.
    ///
    /// @param build The build.
    /// @param feeds The feeds given, or none: then the feed whose registry the build holds.
    /// @return The feed and its registry.
    /// @throws ToolRefusal if no feed of the build has a registry.
    Registry registry(final String build, final List<String> feeds) {
        final List<String> candidates = new ArrayList<>(NullSafe.list(feeds));
        if (candidates.isEmpty()) {
            guard.folderContents(build).stream()
                    .filter(doc -> DocumentationDoc.TYPE.equals(doc.type())
                                   && doc.name().endsWith(SourceTypeRegistry.SUFFIX))
                    .map(doc -> doc.name().substring(0, doc.name().length() - SourceTypeRegistry.SUFFIX.length()))
                    .forEach(candidates::add);
        }
        for (final String feed : candidates) {
            final Optional<SourceTypeRegistries.Found> found = registries.registry(build, feed);
            if (found.isPresent()) {
                return new Registry(feed, found.get());
            }
        }
        throw new ToolRefusal("group= translates some of an aggregated feed's source types, and build '" + build
                              + "' has no source type registry" + (candidates.isEmpty()
                ? ""
                : " for " + ToolText.list(candidates)) + ": record_source_types writes it (give feeds= when the "
                              + "build has several feeds)");
    }

    /// Splits a mapping into a source type group's and the feed's common module's.
    ///
    /// The common module, where the feed has one (the build's, or promoted beside the feed), is kept as it is: a
    /// group's entries for one of its parts that are the same as the module's (the System Name and Environment
    /// aside) are left to it; different ones are the group's own, overriding the part for the group. Where the feed
    /// has none, it is made from the mapping's common entries for its parts (EventTime, System, Generator, Device)
    /// that read only the record's own fields or constants (never a field held in a message, an extraction, an
    /// xpath), and the mapping's shared calls; the registry's Environment and default System Name replacing the
    /// mapping's.
    ///
    /// @param build       The build.
    /// @param registry    The feed's registry.
    /// @param group       The group's name.
    /// @param sourceTypes The source types it translates; none for the build's scope's.
    /// @param given       The mapping given for the group.
    /// @param schema      The event-logging schema the Events are written in.
    /// @param version     Its version.
    /// @return The plan.
    /// @throws ToolRefusal if a source type isn't the registry's.
    Plan plan(final String build,
              final Registry registry,
              final String group,
              final List<String> sourceTypes,
              final TranslationMapping given,
              final EventSchema schema,
              final String version) {
        final Map<String, Object> state = registry.found().state();
        final String feed = registry.feed();
        final String field = JsonValues.text(state.get("field"));
        final List<String> types = sourceTypes(build, state, sourceTypes);
        final String naming = given.style().naming();
        final String mode = XsltNames.styleName(group, naming);
        if (mode.equals(XsltNames.styleName(feed + "-common", naming))
            || mode.equals(XsltNames.styleName(feed + "-dispatch", naming))) {
            throw new ToolRefusal("The group's name '" + group + "' makes the mode of the feed's common module or "
                                  + "dispatcher: name it for its source types");
        }
        final Module helpersDoc = module(build, state, "helpers", HelpersXslt.docName(feed));
        final Module commonDoc = module(build, state, "common", commonName(feed));
        final String helpersHref = helpersDoc == null
                ? HelpersXslt.docName(feed)
                : href(helpersDoc.uuid(), helpersDoc.name());
        final String commonHref = commonDoc == null
                ? commonName(feed)
                : href(commonDoc.uuid(), commonDoc.name());
        final List<String> notes = new ArrayList<>();
        final String systemName = systemName(state, types, given);
        final TranslationMapping common;
        final TranslationMapping groupMapping;
        if (commonDoc == null) {
            final Split split = firstSplit(given, state, feed, naming, helpersHref);
            common = split.common();
            groupMapping = split.group();
            notes.addAll(split.notes());
        } else {
            common = keptCommon(commonDoc);
            groupMapping = leftToCommon(given, common, notes);
        }
        final SourceTypeModule module = new SourceTypeModule(SourceTypeModule.GROUP, group, mode, types, commonHref,
                helpersHref, SourceTypeModule.commonParts(common), systemName, field);
        final Map<String, String> unsaved = new LinkedHashMap<>();
        final List<String> problems = new ArrayList<>();
        if (helpersDoc == null) {
            unsaved.put(helpersHref, HelpersXslt.generate());
        }
        if (commonDoc == null) {
            final XsltGenerator.Generated generated = XsltGenerator.generate(common, schema, version);
            generated.problems().forEach(problem -> problems.add("the feed's common module: " + problem));
            if (generated.ok()) {
                unsaved.put(commonHref, generated.xslt());
            }
        }
        return new Plan(feed, group, mode, types, field, systemName, registry, helpersDoc, commonDoc, common,
                groupMapping.withModule(module), effective(groupMapping, common, systemName, field), notes, unsaved,
                problems);
    }

    /// Saves what the group's XSLT imports before it: the feed's helpers (made, or written again when the generator
    /// writes them otherwise now: a promoted one through a working copy) and its common module (made, where the feed
    /// has none).
    ///
    /// @param context The call's context.
    /// @param build   The build.
    /// @param plan    The plan.
    /// @param schema  The event-logging schema the Events are written in.
    /// @param version Its version.
    /// @return The group's mapping importing them by their UUIDs, and what was saved.
    Imports saveImports(final ToolCallContext context,
                        final String build,
                        final Plan plan,
                        final EventSchema schema,
                        final String version) {
        final Map<String, Object> saved = new LinkedHashMap<>();
        final Map<String, Object> state = plan.registry().found().state();
        final String helpersCode = HelpersXslt.generate();
        final Module promotedHelpers = plan.helpersDoc() != null && !plan.helpersDoc().inBuild()
                ? plan.helpersDoc()
                : null;
        final Map<String, Object> helpers;
        if (promotedHelpers != null && HelpersXslt.isCurrent(readXsltData(promotedHelpers.uuid()))) {
            helpers = moduleSaid(promotedHelpers, false);
        } else {
            final String into = promotedHelpers == null
                    ? NullSafe.get(plan.helpersDoc(), Module::uuid)
                    : workingCopy(context, build, state, promotedHelpers.uuid());
            helpers = translationDocs.saveModule(context, build, HelpersXslt.docName(plan.feed()), into, helpersCode,
                    TranslationDocs.Source.NONE, null, null, "The generator's helpers, version "
                                                             + HelpersXslt.VERSION);
        }
        saved.put("helpers", helpers);
        final String helpersHref = href(String.valueOf(helpers.get("uuid")), String.valueOf(helpers.get("name")));
        String commonHref = plan.groupMapping().module().common();
        if (plan.commonDoc() == null) {
            final TranslationMapping common = plan.common().withModule(plan.common().module().withImports(null,
                    helpersHref));
            final XsltGenerator.Generated generated = XsltGenerator.generate(common, schema, version);
            if (!generated.ok()) {
                throw new ToolRefusal("The feed's common module doesn't generate: " + String.join("; ",
                        generated.problems()));
            }
            final Map<String, Object> made = translationDocs.saveModule(context, build, commonName(plan.feed()),
                    null, generated.xslt(), TranslationDocs.Source.of(common), null, null, "The parts every source "
                                                                                           + "type group of "
                                                                                           + plan.feed()
                                                                                           + " writes one way");
            saved.put("common", made);
            commonHref = href(String.valueOf(made.get("uuid")), String.valueOf(made.get("name")));
        } else {
            // The build's copy, where the helpers' working copy brought one.
            final Module common = Objects.requireNonNullElse(module(build, state, "common", commonName(plan.feed())),
                    plan.commonDoc());
            saved.put("common", moduleSaid(common, false));
            commonHref = href(common.uuid(), common.name());
        }
        final TranslationMapping group = plan.groupMapping().withModule(plan.groupMapping().module()
                .withImports(commonHref, helpersHref));
        return new Imports(group, helpersHref, saved);
    }

    /// The XSLT a group is saved in when the build has none of its name: the build's working copy of the group's
    /// promoted XSLT (made now), for a later build changing a promoted group.
    ///
    /// @param context The call's context.
    /// @param build   The build.
    /// @param plan    The plan.
    /// @return The working copy's UUID; empty when the group was never promoted (a new XSLT is made).
    Optional<String> promotedGroupCopy(final ToolCallContext context, final String build, final Plan plan) {
        final Map<String, Object> state = plan.registry().found().state();
        for (final Map<String, Object> row : JsonValues.maps(state.get("rows"))) {
            if (!plan.group().equals(JsonValues.text(row.get("group")))) {
                continue;
            }
            final String uuid = JsonValues.text(JsonValues.mapOrEmpty(row.get(GROUP_XSLT)).get("uuid"));
            if (!uuid.isEmpty() && !inBuild(build, uuid) && readable(uuid)) {
                return Optional.of(workingCopy(context, build, state, uuid));
            }
        }
        return Optional.empty();
    }

    /// @param plan A group's plan.
    /// @return True if none of its source types is placed in a pipeline yet: a new group, whose pipeline the user
    /// chooses ([Placement]).
    static boolean needsPlacement(final Plan plan) {
        return JsonValues.maps(plan.registry().found().state().get("rows")).stream()
                .filter(row -> plan.sourceTypes().contains(JsonValues.text(row.get("name"))))
                .noneMatch(row -> row.get(PIPELINE) != null
                                  || SourceTypeRegistry.TRANSLATED.equals(row.get("status")));
    }

    /// @param build A build.
    /// @param plan  A group's plan.
    /// @return The feed's pipeline in production, by name, where it has one (a promoted dispatcher's pipeline): the
    /// placement form's default; empty for a feed whose first pipeline this build makes.
    Optional<String> existingPipeline(final String build, final Plan plan) {
        for (final Map<String, Object> row : JsonValues.maps(plan.registry().found().state().get("rows"))) {
            final Map<String, Object> pipeline = JsonValues.mapOrEmpty(row.get(PIPELINE));
            final String name = JsonValues.text(pipeline.get("name"));
            final String dispatcher = JsonValues.text(JsonValues.mapOrEmpty(pipeline.get(DISPATCHER)).get("uuid"));
            if (JsonValues.truthy(pipeline.get(OWN)) || name.isEmpty() || dispatcher.isEmpty()) {
                continue;
            }
            final Optional<String> in = guard.buildOf(new DocRef(XsltDoc.TYPE, dispatcher));
            if (in.isEmpty() || guard.copyOf(new DocRef(XsltDoc.TYPE, dispatcher)).isPresent()) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    /// Saves the group in the registry and the feed's dispatchers: its rows set `translated`, naming the group, its
    /// XSLT, its pipeline (as placed, for a new group), the output feed and its reference data; then every dispatcher
    /// regenerated from the registry ([#regenerate]).
    ///
    /// @param context     The call's context.
    /// @param build       The build.
    /// @param plan        The plan.
    /// @param imports     What the group imports, as saved.
    /// @param groupUuid   The group's XSLT's UUID.
    /// @param groupName   Its name.
    /// @param placement   Where a new group goes, or null for one placed already.
    /// @param references  The reference data its mapping looks up, or null to keep what the registry has.
    /// @param schema      The event-logging schema the Events are written in.
    /// @param version     Its version.
    /// @return What was saved: the group's dispatcher, every dispatcher, the registry, and where the group runs.
    Map<String, Object> dispatch(final ToolCallContext context,
                                 final String build,
                                 final Plan plan,
                                 final Imports imports,
                                 final String groupUuid,
                                 final String groupName,
                                 final Placement placement,
                                 final List<Map<String, Object>> references,
                                 final EventSchema schema,
                                 final String version) {
        final Map<String, Object> state = JsonValues.deepCopy(plan.registry().found().state());
        final Map<String, Object> modules = new LinkedHashMap<>(JsonValues.mapOrEmpty(state.get(MODULES)));
        for (final String key : List.of("helpers", "common")) {
            final Map<String, Object> saved = JsonValues.mapOrEmpty(imports.saved().get(key));
            if (saved.get("uuid") != null) {
                modules.put(key, Map.of("uuid", String.valueOf(saved.get("uuid")), "name",
                        String.valueOf(saved.get("name"))));
            }
        }
        state.put(MODULES, modules);
        final String now = SurveyDoc.at(Instant.now());
        final Map<String, Object> placed = placement == null
                ? null
                : placed(state, plan, placement);
        final List<Map<String, Object>> rows = new ArrayList<>();
        for (final Map<String, Object> given : JsonValues.maps(state.get("rows"))) {
            final Map<String, Object> row = new LinkedHashMap<>(given);
            if (plan.sourceTypes().contains(JsonValues.text(row.get("name")))) {
                row.put("status", SourceTypeRegistry.TRANSLATED);
                row.put("group", plan.group());
                row.put(GROUP_XSLT, Map.of("uuid", groupUuid, "name", groupName));
                if (placed != null) {
                    row.put(PIPELINE, JsonValues.deepCopy(placed));
                    if (placement.ownOutputFeed()) {
                        row.put(OUTPUT_FEED, "");
                    } else {
                        row.remove(OUTPUT_FEED);
                    }
                }
                if (references != null) {
                    row.put(REFERENCES, JsonValues.deepCopy(references));
                }
                final Map<String, Object> decided = new LinkedHashMap<>();
                decided.put("by", BuildTranslationXsltTool.NAME);
                decided.put("at", now);
                decided.put("reason", "translated by source type group '" + plan.group() + "' in build '" + build
                                      + "'");
                row.put("decided", decided);
            }
            rows.add(row);
        }
        state.put("rows", rows);
        state.put("updated", now);
        final List<Map<String, Object>> dispatchers = regenerate(context, build, plan.feed(), state,
                new Regeneration(imports.helpersHref(), schema, version, DispatcherSpec.inputOf(plan.groupMapping()),
                        plan.groupMapping().style().naming()));
        final SurveyRecord written = registries.writeRegistry(context.session(), build, plan.feed(),
                plan.registry().found(), SourceTypeRegistry.render(state));
        final Map<String, Object> out = new LinkedHashMap<>();
        // The rows as regenerating left them: each with its dispatcher saved.
        final Map<String, Object> pipeline = JsonValues.maps(state.get("rows")).stream()
                .filter(row -> plan.sourceTypes().contains(JsonValues.text(row.get("name"))))
                .map(row -> JsonValues.mapOrEmpty(row.get(PIPELINE)))
                .findFirst()
                .orElse(Map.of());
        final String own = JsonValues.text(JsonValues.mapOrEmpty(pipeline.get(DISPATCHER)).get("uuid"));
        dispatchers.stream()
                .filter(saved -> own.equals(saved.get("uuid")))
                .findFirst()
                .ifPresent(saved -> out.put(DISPATCHER, saved));
        out.put("dispatchers", dispatchers);
        out.put("pipeline", pipeline);
        final Map<String, Object> registry = new LinkedHashMap<>();
        registry.put("uuid", written.uuid());
        registry.put("name", written.name());
        registry.put("translated", plan.sourceTypes());
        out.put("registry", registry);
        return out;
    }

    // A new group's rows' pipeline, as placed: the feed's pipeline (its dispatcher, and its name where it has one),
    // a new version of it (copy_pipeline to come), or a pipeline of its own with a dispatcher of its own.
    private static Map<String, Object> placed(final Map<String, Object> state,
                                              final Plan plan,
                                              final Placement placement) {
        final Map<String, Object> pipeline = new LinkedHashMap<>();
        if (OWN.equals(placement.choice())) {
            pipeline.put(OWN, true);
            pipeline.put(DISPATCHER, Map.of("name", ownDispatcherName(plan.feed(), plan.group())));
            return pipeline;
        }
        pipeline.put(OWN, false);
        final Map<String, Object> main = mainPipeline(state, plan.feed());
        pipeline.put(DISPATCHER, main.get(DISPATCHER));
        if (main.get("name") != null) {
            pipeline.put("name", main.get("name"));
        }
        if (NEW_VERSION.equals(placement.choice())) {
            pipeline.put(NEW_VERSION, true);
        }
        return pipeline;
    }

    // The pipeline of the feed's translated rows not in a pipeline of their own: its dispatcher and name; the
    // feed's dispatcher by name where none is placed yet (a registry from before pipelines were placed, too).
    private static Map<String, Object> mainPipeline(final Map<String, Object> state, final String feed) {
        for (final Map<String, Object> row : JsonValues.maps(state.get("rows"))) {
            final Map<String, Object> pipeline = JsonValues.mapOrEmpty(row.get(PIPELINE));
            if (!pipeline.isEmpty() && !JsonValues.truthy(pipeline.get(OWN)) && pipeline.get(DISPATCHER) != null) {
                final Map<String, Object> out = new LinkedHashMap<>();
                out.put(DISPATCHER, pipeline.get(DISPATCHER));
                if (pipeline.get("name") != null) {
                    out.put("name", pipeline.get("name"));
                }
                return out;
            }
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put(DISPATCHER, Map.of("name", DispatcherXslt.docName(feed)));
        return out;
    }

    /// @param feed  A feed's name.
    /// @param group One of its source type groups.
    /// @return The name of the dispatcher of the group's pipeline of its own.
    static String ownDispatcherName(final String feed, final String group) {
        return feed + " " + group + DispatcherXslt.SUFFIX;
    }

    /// Every dispatcher of the feed generated from its registry and saved in the build: each translated row is
    /// routed by its pipeline's dispatcher (the feed's pipeline's, or its group's own), every other pipeline's rows
    /// passed over as handled by that pipeline, and the rest as parked, dropped or new. A promoted dispatcher is
    /// changed through a working copy; the feed's pipeline's waits while its new version is still to be copied
    /// (copy_pipeline regenerates it). Each row's dispatcher is set to the one saved.
    ///
    /// @param context The call's context.
    /// @param build   The build.
    /// @param feed    The feed.
    /// @param state   The registry's machine block, changed in place: each row's dispatcher.
    /// @param how     What a dispatcher made new is made with.
    /// @return Each dispatcher saved: `uuid`, `name`, `changed`, and the `groups` it routes.
    /// @throws ToolRefusal if a dispatcher doesn't generate.
    List<Map<String, Object>> regenerate(final ToolCallContext context,
                                         final String build,
                                         final String feed,
                                         final Map<String, Object> state,
                                         final Regeneration how) {
        final List<Map<String, Object>> rows = new ArrayList<>();
        JsonValues.maps(state.get("rows")).forEach(row -> rows.add(new LinkedHashMap<>(row)));
        state.put("rows", rows);
        // Each translated row's pipeline, the dispatchers in the order first met.
        final Map<String, Map<String, Object>> dispatchers = new LinkedHashMap<>();
        final Map<String, List<Map<String, Object>>> routed = new LinkedHashMap<>();
        final Map<String, String> handledBy = new LinkedHashMap<>();
        for (final Map<String, Object> row : rows) {
            if (!SourceTypeRegistry.TRANSLATED.equals(row.get("status"))) {
                continue;
            }
            Map<String, Object> pipeline = JsonValues.mapOrEmpty(row.get(PIPELINE));
            if (pipeline.isEmpty()) {
                pipeline = new LinkedHashMap<>(mainPipeline(state, feed));
                pipeline.put(OWN, false);
                row.put(PIPELINE, pipeline);
            }
            final Map<String, Object> dispatcher = JsonValues.mapOrEmpty(pipeline.get(DISPATCHER));
            final String key = dispatcherKey(dispatcher);
            dispatchers.putIfAbsent(key, dispatcher);
            routed.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
            final String name = JsonValues.text(pipeline.get("name"));
            handledBy.putIfAbsent(key, name.isEmpty()
                    ? "the pipeline running '" + JsonValues.text(dispatcher.get("name")) + "'"
                    : "pipeline '" + name + "'");
        }
        final List<Map<String, Object>> out = new ArrayList<>();
        DispatcherSpec known = null;
        for (final Map.Entry<String, Map<String, Object>> entry : dispatchers.entrySet()) {
            final List<Map<String, Object>> its = routed.get(entry.getKey());
            final Module existing = dispatcherModule(build, entry.getValue());
            final boolean waits = existing != null && !existing.inBuild()
                                  && its.stream().anyMatch(row -> JsonValues.truthy(JsonValues.mapOrEmpty(
                    row.get(PIPELINE)).get(NEW_VERSION)));
            if (waits) {
                continue;
            }
            final String uuid = existing == null
                    ? null
                    : existing.inBuild()
                            ? existing.uuid()
                            : workingCopy(context, build, state, existing.uuid());
            final DispatcherSpec kept = uuid == null
                    ? null
                    : keptSpec(uuid).orElse(null);
            known = kept != null
                    ? kept
                    : known;
            final boolean own = its.stream().anyMatch(row -> JsonValues.truthy(JsonValues.mapOrEmpty(
                    row.get(PIPELINE)).get(OWN)));
            final String firstGroup = JsonValues.text(its.getFirst().get("group"));
            final String mode = kept != null
                    ? kept.mode()
                    : XsltNames.styleName(own
                            ? feed + "-" + firstGroup + "-dispatch"
                            : feed + "-dispatch", NullSafe.nonBlankStringElse(how.naming(), "snake_case"));
            final DispatcherSpec base = kept != null
                    ? kept
                    : known;
            final Map<String, Object> input = base != null
                    ? base.input()
                    : how.input();
            final String helpers = NullSafe.nonBlankStringElse(how.helpersHref(), base == null
                    ? helpersHref(build, state, feed)
                    : base.helpers());
            final List<DispatcherSpec.Row> specRows = new ArrayList<>();
            for (final Map<String, Object> row : rows) {
                final String rowKey = rowKey(row);
                specRows.add(new DispatcherSpec.Row(JsonValues.text(row.get("name")), JsonValues.text(row.get(
                        "match")), JsonValues.text(row.get("value")), JsonValues.text(row.get("status")),
                        rowKey == null || rowKey.equals(entry.getKey())
                                ? null
                                : handledBy.get(rowKey)));
            }
            DispatcherSpec spec = new DispatcherSpec(feed, mode, input, JsonValues.text(state.get("field")), helpers,
                    specRows, groups(build, its, kept), base == null
                    ? Map.of()
                    : base.events());
            if (how.schema() != null) {
                spec = spec.withEvents(how.version(), XsltGenerator.schemaLocation(how.schema(), how.version()));
            }
            final DispatcherXslt.Generated generated = DispatcherXslt.generate(spec, how.schema(), how.version());
            if (!generated.ok()) {
                throw new ToolRefusal("The feed's dispatcher '" + JsonValues.text(entry.getValue().get("name"))
                                      + "' doesn't generate: " + String.join("; ", generated.problems()));
            }
            final String name = existing != null
                    ? existing.name()
                    : JsonValues.text(entry.getValue().get("name"));
            final List<String> groupNames = spec.groups().stream().map(DispatcherSpec.Group::name).toList();
            final Map<String, Object> saved = new LinkedHashMap<>(translationDocs.saveModule(context, build, name,
                    uuid, generated.xslt(), TranslationDocs.Source.NONE, DispatcherXslt.SPEC_BLOCK, spec.toJson(),
                    "Routes " + (groupNames.isEmpty()
                            ? "no group"
                            : "group(s) " + String.join(", ", groupNames))));
            saved.put("groups", groupNames);
            final Map<String, Object> ref = Map.of("uuid", String.valueOf(saved.get("uuid")), "name",
                    String.valueOf(saved.get("name")));
            for (final Map<String, Object> row : its) {
                final Map<String, Object> pipeline = new LinkedHashMap<>(JsonValues.mapOrEmpty(row.get(PIPELINE)));
                pipeline.put(DISPATCHER, ref);
                row.put(PIPELINE, pipeline);
            }
            out.add(saved);
        }
        return out;
    }

    // The groups a dispatcher imports, each once, in the order the dispatcher kept them, then the registry's: each
    // the build's XSLT (its working copy, where it has one) or the promoted one.
    private List<DispatcherSpec.Group> groups(final String build,
                                              final List<Map<String, Object>> routed,
                                              final DispatcherSpec kept) {
        final Map<String, List<String>> types = new LinkedHashMap<>();
        final Map<String, String> xslts = new LinkedHashMap<>();
        if (kept != null) {
            kept.groups().forEach(group -> types.put(group.name(), new ArrayList<>()));
        }
        for (final Map<String, Object> row : routed) {
            final String group = JsonValues.text(row.get("group"));
            final String uuid = JsonValues.text(JsonValues.mapOrEmpty(row.get(GROUP_XSLT)).get("uuid"));
            if (group.isEmpty() || uuid.isEmpty()) {
                continue;
            }
            types.computeIfAbsent(group, ignored -> new ArrayList<>()).add(JsonValues.text(row.get("name")));
            xslts.putIfAbsent(group, copyInBuild(build, uuid).orElse(uuid));
        }
        final List<DispatcherSpec.Group> out = new ArrayList<>();
        types.forEach((group, sourceTypes) -> {
            final String uuid = xslts.get(group);
            if (uuid == null || sourceTypes.isEmpty()) {
                return;
            }
            final Optional<XsltDoc> doc = documentAccess.read(new DocRef(XsltDoc.TYPE, uuid), XsltDoc.class);
            final String mode = doc.flatMap(xslt -> ModuleChecks.marker(NullSafe.string(xslt.getData())))
                    .map(ModuleChecks.Marker::mode)
                    .or(() -> Optional.ofNullable(kept)
                            .flatMap(spec -> spec.groups().stream()
                                    .filter(one -> one.name().equals(group))
                                    .findFirst())
                            .map(DispatcherSpec.Group::mode))
                    .orElse(null);
            out.add(new DispatcherSpec.Group(group, mode, href(uuid, doc.map(XsltDoc::getName).orElse(group)),
                    sourceTypes));
        });
        return out;
    }

    // The helpers' import as the registry names them (the build's working copy, where it has one).
    private String helpersHref(final String build, final Map<String, Object> state, final String feed) {
        final Module helpers = module(build, state, "helpers", HelpersXslt.docName(feed));
        return helpers == null
                ? null
                : href(helpers.uuid(), helpers.name());
    }

    // A row's dispatcher's key, or null for a row no dispatcher routes.
    private static String rowKey(final Map<String, Object> row) {
        if (!SourceTypeRegistry.TRANSLATED.equals(row.get("status"))) {
            return null;
        }
        final Map<String, Object> pipeline = JsonValues.mapOrEmpty(row.get(PIPELINE));
        return pipeline.isEmpty()
                ? null
                : dispatcherKey(JsonValues.mapOrEmpty(pipeline.get(DISPATCHER)));
    }

    // A dispatcher's key: its UUID once saved, else its name.
    private static String dispatcherKey(final Map<String, Object> dispatcher) {
        final String uuid = JsonValues.text(dispatcher.get("uuid"));
        return uuid.isEmpty()
                ? "name:" + JsonValues.text(dispatcher.get("name"))
                : uuid;
    }

    // A dispatcher the registry names: the build's (its own, or its working copy of a promoted one), else the
    // promoted one; null for one not made yet.
    private Module dispatcherModule(final String build, final Map<String, Object> dispatcher) {
        final String uuid = JsonValues.text(dispatcher.get("uuid"));
        if (!uuid.isEmpty()) {
            if (inBuild(build, uuid)) {
                return new Module(uuid, JsonValues.text(dispatcher.get("name")), true);
            }
            final Optional<String> copy = copyInBuild(build, uuid);
            if (copy.isPresent()) {
                return new Module(copy.get(), JsonValues.text(dispatcher.get("name")), true);
            }
            if (readable(uuid) && guard.buildOf(new DocRef(XsltDoc.TYPE, uuid)).isEmpty()) {
                return new Module(uuid, JsonValues.text(dispatcher.get("name")), false);
            }
        }
        final WriteGuard.BuildDoc named = xsltInBuild(build, JsonValues.text(dispatcher.get("name")));
        return named == null
                ? null
                : new Module(named.uuid(), named.name(), true);
    }

    // The spec kept with a dispatcher.
    private Optional<DispatcherSpec> keptSpec(final String uuid) {
        return documentAccess.read(new DocRef(XsltDoc.TYPE, uuid), XsltDoc.class)
                .flatMap(doc -> KeptMapping.readBlock(doc.getDescription(), DispatcherXslt.SPEC_BLOCK))
                .flatMap(DispatcherSpec::fromJson);
    }

    /// The build's working copy of a promoted module of the feed: made now if the build has none, with a UUID of
    /// its own (written back over the original at promotion). Every other promoted module of the feed that imports
    /// the original is copied too, and every module in the build imports the copy instead, so the build steps and
    /// processes what it changes.
    ///
    /// @param context  The call's context.
    /// @param build    The build.
    /// @param state    The registry's machine block, naming the feed's modules.
    /// @param original The promoted module's UUID.
    /// @return The working copy's UUID.
    /// @throws ToolRefusal if the module can't be read.
    String workingCopy(final ToolCallContext context,
                       final String build,
                       final Map<String, Object> state,
                       final String original) {
        final Optional<String> have = copyInBuild(build, original);
        if (have.isPresent()) {
            return have.get();
        }
        final XsltDoc doc = documentAccess.read(new DocRef(XsltDoc.TYPE, original), XsltDoc.class)
                .orElseThrow(() -> new ToolRefusal("XSLT " + original + " can't be read: it doesn't exist, or you "
                                                   + "don't have permission to view it"));
        final DocRef ref = guard.create(XsltDoc.TYPE, doc.getName(), build, original, false, "Events");
        if (doc.getVersion() != null) {
            // As copy_pipeline records it: promotion asks before writing over an original changed since.
            records.update(build, ref, entry -> entry.put(BuildRecords.COPY_OF_VERSION, doc.getVersion()));
        }
        final XsltDoc made = documentAccess.read(ref, XsltDoc.class)
                .orElseThrow(() -> new ToolRefusal("The working copy of '" + doc.getName() + "' can't be read"));
        writeXslt(made.copy().data(doc.getData()).description(doc.getDescription()).build());
        if (guard.tags(new DocRef(XsltDoc.TYPE, original)).contains(WriteGuard.KEPT_MAPPING)) {
            guard.tag(List.of(ref), Set.of(WriteGuard.KEPT_MAPPING));
        }
        final String needle = "uuid='" + original + "'";
        for (final String other : promotedModules(build, state)) {
            if (!other.equals(original) && readXsltData(other).contains(needle)) {
                workingCopy(context, build, state, other);
            }
        }
        rewire(context, build, Map.of(original, ref.getUuid()), "Imports the working copy of '" + doc.getName()
                                                                + "'");
        return ref.getUuid();
    }

    // Every module of the build importing one of the documents given imports the one it maps to instead.
    private void rewire(final ToolCallContext context,
                        final String build,
                        final Map<String, String> uuids,
                        final String change) {
        for (final WriteGuard.BuildDoc doc : guard.folderContents(build)) {
            if (XsltDoc.TYPE.equals(doc.type()) && !uuids.containsValue(doc.uuid())) {
                translationDocs.rewriteImports(context, doc.uuid(), uuids, change);
            }
        }
    }

    // The feed's modules promoted beside it, as its registry names them: helpers, common module, groups and
    // dispatchers.
    private List<String> promotedModules(final String build, final Map<String, Object> state) {
        final Set<String> found = new LinkedHashSet<>();
        JsonValues.mapOrEmpty(state.get(MODULES)).values()
                .forEach(module -> found.add(JsonValues.text(JsonValues.mapOrEmpty(module).get("uuid"))));
        for (final Map<String, Object> row : JsonValues.maps(state.get("rows"))) {
            found.add(JsonValues.text(JsonValues.mapOrEmpty(row.get(GROUP_XSLT)).get("uuid")));
            found.add(JsonValues.text(JsonValues.mapOrEmpty(JsonValues.mapOrEmpty(row.get(PIPELINE))
                    .get(DISPATCHER)).get("uuid")));
        }
        return found.stream()
                .filter(uuid -> !uuid.isEmpty() && readable(uuid)
                                && guard.buildOf(new DocRef(XsltDoc.TYPE, uuid)).isEmpty())
                .toList();
    }

    /// Before a build's working copies are written back at promotion: every module of the build imports the
    /// originals again (each working copy's UUID replaced by its original's, in its code and in the mapping or spec
    /// kept with it), each dispatcher regenerated from its spec against the originals' UUIDs, and the registry names
    /// the originals.
    ///
    /// @param context The call's context.
    /// @param build   The build.
    /// @param docs    The build's documents, as the plan lists them (`working_copy_of`).
    /// @return What was done, said for the promotion's result; none for a build with no working copy of a module.
    List<String> toOriginals(final ToolCallContext context,
                             final String build,
                             final List<Map<String, Object>> docs) {
        final Map<String, String> back = new LinkedHashMap<>();
        for (final Map<String, Object> doc : docs) {
            if (XsltDoc.TYPE.equals(doc.get("type")) && doc.get("working_copy_of") != null) {
                back.put(String.valueOf(doc.get("uuid")), String.valueOf(doc.get("working_copy_of")));
            }
        }
        if (back.isEmpty()) {
            return List.of();
        }
        final List<String> done = new ArrayList<>();
        final String change = "Imports the originals its build's working copies are written back over";
        for (final Map<String, Object> doc : docs) {
            final String uuid = String.valueOf(doc.get("uuid"));
            if (!XsltDoc.TYPE.equals(doc.get("type"))) {
                continue;
            }
            final Optional<DispatcherSpec> spec = keptSpec(uuid);
            if (spec.isPresent()) {
                if (regenerated(context, uuid, spec.get(), back, change)) {
                    done.add("regenerated dispatcher '" + doc.get("name") + "' against the originals' UUIDs");
                }
            } else if (translationDocs.rewriteImports(context, uuid, back, change)) {
                done.add("pointed '" + doc.get("name") + "' at the originals it imports");
            }
        }
        for (final Map<String, Object> doc : docs) {
            final String name = String.valueOf(doc.get("name"));
            if (DocumentationDoc.TYPE.equals(doc.get("type")) && name.endsWith(SourceTypeRegistry.SUFFIX)) {
                final String feed = name.substring(0, name.length() - SourceTypeRegistry.SUFFIX.length());
                registries.registry(build, feed).filter(found -> !found.promoted()).ifPresent(found -> {
                    String json = ToolJson.dumps(found.state());
                    for (final Map.Entry<String, String> entry : back.entrySet()) {
                        json = json.replace(entry.getKey(), entry.getValue());
                    }
                    final Map<String, Object> state = JsonValues.mapOrEmpty(ToolJson.loads(json));
                    if (!state.equals(found.state())) {
                        registries.writeRegistry(context.session(), build, feed, found,
                                SourceTypeRegistry.render(state));
                        done.add("the registry of " + feed + " names the originals");
                    }
                });
            }
        }
        return done;
    }

    // A dispatcher regenerated from its spec, its imports the originals'.
    private boolean regenerated(final ToolCallContext context,
                                final String uuid,
                                final DispatcherSpec spec,
                                final Map<String, String> back,
                                final String change) {
        final List<DispatcherSpec.Group> groups = spec.groups().stream()
                .map(group -> new DispatcherSpec.Group(group.name(), group.mode(), originalHref(group.href(), back),
                        group.sourceTypes()))
                .toList();
        final DispatcherSpec original = new DispatcherSpec(spec.feed(), spec.mode(), spec.input(), spec.field(),
                originalHref(spec.helpers(), back), spec.rows(), groups, spec.events());
        if (original.equals(spec)) {
            return false;
        }
        final DispatcherXslt.Generated generated = DispatcherXslt.generate(original, null, null);
        if (!generated.ok()) {
            throw new ToolRefusal("The dispatcher doesn't generate against the originals: " + String.join("; ",
                    generated.problems()));
        }
        return translationDocs.rewriteModule(context, uuid, generated.xslt(), description -> KeptMapping.withBlock(
                description, DispatcherXslt.SPEC_BLOCK, original.toJson()), change);
    }

    private static String originalHref(final String href, final Map<String, String> back) {
        if (href == null) {
            return null;
        }
        String out = href;
        for (final Map.Entry<String, String> entry : back.entrySet()) {
            out = out.replace("uuid='" + entry.getKey() + "'", "uuid='" + entry.getValue() + "'");
        }
        return out;
    }

    /// The pipeline made in the build to run one of the feed's dispatchers, named in the registry: each row the
    /// dispatcher routes says its pipeline, and every other dispatcher is regenerated to say its records are
    /// handled by it.
    ///
    /// @param context    The call's context.
    /// @param build      The build.
    /// @param dispatcher The dispatcher's UUID (the build's).
    /// @param pipeline   The pipeline's name.
    /// @return The registry's rows for it, empty if the XSLT isn't one of a registry's dispatchers.
    List<String> runBy(final ToolCallContext context,
                       final String build,
                       final String dispatcher,
                       final String pipeline) {
        final Optional<Registry> registry = dispatcherRegistry(build, dispatcher);
        if (registry.isEmpty()) {
            return List.of();
        }
        final Map<String, Object> state = JsonValues.deepCopy(registry.get().found().state());
        final String original = guard.copyOf(new DocRef(XsltDoc.TYPE, dispatcher)).orElse(null);
        final List<String> named = new ArrayList<>();
        final List<Map<String, Object>> rows = new ArrayList<>();
        for (final Map<String, Object> given : JsonValues.maps(state.get("rows"))) {
            final Map<String, Object> row = new LinkedHashMap<>(given);
            final Map<String, Object> placed = new LinkedHashMap<>(JsonValues.mapOrEmpty(row.get(PIPELINE)));
            final String uuid = JsonValues.text(JsonValues.mapOrEmpty(placed.get(DISPATCHER)).get("uuid"));
            if (uuid.equals(dispatcher) || uuid.equals(original)) {
                placed.put("name", pipeline);
                row.put(PIPELINE, placed);
                named.add(JsonValues.text(row.get("name")));
            }
            rows.add(row);
        }
        if (named.isEmpty() || rows.equals(JsonValues.maps(state.get("rows")))) {
            return named;
        }
        state.put("rows", rows);
        regenerate(context, build, registry.get().feed(), state, new Regeneration(null, null, null, Map.of(), null));
        registries.writeRegistry(context.session(), build, registry.get().feed(), registry.get().found(),
                SourceTypeRegistry.render(state));
        return named;
    }

    /// A new version of the feed's pipeline copied into the build (copy_pipeline): the rows its predecessor's
    /// dispatcher routes are the copy's, run by the new version, and every dispatcher is regenerated from the
    /// registry, so the copy routes the groups placed in the new version too.
    ///
    /// @param context  The call's context.
    /// @param build    The build.
    /// @param original The predecessor's dispatcher's UUID.
    /// @param copy     The copy's.
    /// @param pipeline The new version's name.
    /// @return The dispatchers saved; none when the XSLT isn't one of a registry's dispatchers.
    List<Map<String, Object>> newVersion(final ToolCallContext context,
                                         final String build,
                                         final String original,
                                         final String copy,
                                         final String pipeline) {
        final Optional<Registry> registry = dispatcherRegistry(build, original);
        if (registry.isEmpty()) {
            return List.of();
        }
        final Map<String, Object> state = JsonValues.deepCopy(registry.get().found().state());
        final String copyName = documentAccess.read(new DocRef(XsltDoc.TYPE, copy), XsltDoc.class)
                .map(XsltDoc::getName)
                .orElse(null);
        final List<Map<String, Object>> rows = new ArrayList<>();
        for (final Map<String, Object> given : JsonValues.maps(state.get("rows"))) {
            final Map<String, Object> row = new LinkedHashMap<>(given);
            final Map<String, Object> placed = new LinkedHashMap<>(JsonValues.mapOrEmpty(row.get(PIPELINE)));
            if (original.equals(JsonValues.text(JsonValues.mapOrEmpty(placed.get(DISPATCHER)).get("uuid")))) {
                placed.put(DISPATCHER, Map.of("uuid", copy, "name", NullSafe.string(copyName)));
                placed.put("name", pipeline);
                placed.remove(NEW_VERSION);
                row.put(PIPELINE, placed);
            }
            rows.add(row);
        }
        state.put("rows", rows);
        final List<Map<String, Object>> saved = regenerate(context, build, registry.get().feed(), state,
                new Regeneration(null, null, null, Map.of(), null));
        registries.writeRegistry(context.session(), build, registry.get().feed(), registry.get().found(),
                SourceTypeRegistry.render(state));
        return saved;
    }

    /// @param build      A build.
    /// @param dispatcher A dispatcher's UUID: the build's, its working copy's original, or one copied from.
    /// @return The rows of the feed's registry it routes, with the registry's feed; empty for an XSLT no registry
    /// of the build names as a dispatcher.
    Optional<Routed> routedBy(final String build, final String dispatcher) {
        final Optional<Registry> registry = dispatcherRegistry(build, dispatcher);
        if (registry.isEmpty()) {
            return Optional.empty();
        }
        final String original = guard.copyOf(new DocRef(XsltDoc.TYPE, dispatcher)).orElse(dispatcher);
        final List<Map<String, Object>> rows = JsonValues.maps(registry.get().found().state().get("rows")).stream()
                .filter(row -> {
                    final String uuid = JsonValues.text(JsonValues.mapOrEmpty(JsonValues.mapOrEmpty(
                            row.get(PIPELINE)).get(DISPATCHER)).get("uuid"));
                    return SourceTypeRegistry.TRANSLATED.equals(row.get("status"))
                           && (uuid.equals(dispatcher) || uuid.equals(original));
                })
                .toList();
        return Optional.of(new Routed(registry.get().feed(), rows));
    }

    /// @param build A build.
    /// @param group A source type group of its registry.
    /// @throws ToolRefusal if the registry has no such group, or it isn't in a pipeline of its own.
    void checkOwnOutputFeed(final String build, final String group) {
        final Registry registry = registry(build, List.of());
        final List<Map<String, Object>> rows = JsonValues.maps(registry.found().state().get("rows")).stream()
                .filter(row -> group.equals(JsonValues.text(row.get("group"))))
                .toList();
        if (rows.isEmpty()) {
            throw new ToolRefusal("The registry of " + registry.feed() + " has no source type group '" + group
                                  + "': build_translation_xslt group= saves it first");
        }
        if (rows.stream().noneMatch(row -> JsonValues.truthy(JsonValues.mapOrEmpty(row.get(PIPELINE)).get(OWN)))) {
            throw new ToolRefusal("Group '" + group + "' is in the feed's pipeline, whose Events go into the raw "
                                  + "feed's: only a group with a pipeline of its own has an output feed of its own");
        }
    }

    /// Sets the output feed of a group with a pipeline of its own, made in the build.
    ///
    /// @param context The call's context.
    /// @param build   The build.
    /// @param group   The group.
    /// @param feed    The output feed's name.
    /// @return The source types whose Events go there.
    /// @throws ToolRefusal if the build's registry has no such group, or it isn't in a pipeline of its own.
    List<String> outputFeed(final ToolCallContext context,
                            final String build,
                            final String group,
                            final String feed) {
        final Registry registry = registry(build, List.of());
        final Map<String, Object> state = JsonValues.deepCopy(registry.found().state());
        final List<String> types = new ArrayList<>();
        final List<Map<String, Object>> rows = new ArrayList<>();
        boolean own = false;
        for (final Map<String, Object> given : JsonValues.maps(state.get("rows"))) {
            final Map<String, Object> row = new LinkedHashMap<>(given);
            if (group.equals(JsonValues.text(row.get("group")))) {
                own |= JsonValues.truthy(JsonValues.mapOrEmpty(row.get(PIPELINE)).get(OWN));
                row.put(OUTPUT_FEED, feed);
                types.add(JsonValues.text(row.get("name")));
            }
            rows.add(row);
        }
        if (types.isEmpty()) {
            throw new ToolRefusal("The registry of " + registry.feed() + " has no source type group '" + group
                                  + "'");
        }
        if (!own) {
            throw new ToolRefusal("Group '" + group + "' is in the feed's pipeline, whose Events go into the raw "
                                  + "feed's: only a group with a pipeline of its own has an output feed of its own");
        }
        state.put("rows", rows);
        registries.writeRegistry(context.session(), build, registry.feed(), registry.found(),
                SourceTypeRegistry.render(state));
        return types;
    }

    // The registry of the build naming this dispatcher (or its original) for a row: its feed's, as its spec says.
    private Optional<Registry> dispatcherRegistry(final String build, final String dispatcher) {
        final Registry registry;
        try {
            registry = registry(build, keptSpec(dispatcher).map(DispatcherSpec::feed).map(List::of)
                    .orElse(List.of()));
        } catch (final ToolRefusal e) {
            return Optional.empty();
        }
        final String original = guard.copyOf(new DocRef(XsltDoc.TYPE, dispatcher)).orElse(dispatcher);
        final boolean names = JsonValues.maps(registry.found().state().get("rows")).stream()
                .map(row -> JsonValues.text(JsonValues.mapOrEmpty(JsonValues.mapOrEmpty(row.get(PIPELINE))
                        .get(DISPATCHER)).get("uuid")))
                .anyMatch(uuid -> uuid.equals(dispatcher) || uuid.equals(original));
        return names
                ? Optional.of(registry)
                : Optional.empty();
    }

    // A module of the feed: the build's of that name (its own, or its working copy), else the promoted one the
    // registry names.
    private Module module(final String build, final Map<String, Object> state, final String key, final String name) {
        final WriteGuard.BuildDoc doc = xsltInBuild(build, name);
        if (doc != null) {
            return new Module(doc.uuid(), doc.name(), true);
        }
        final Map<String, Object> named = JsonValues.mapOrEmpty(JsonValues.mapOrEmpty(state.get(MODULES)).get(key));
        final String uuid = JsonValues.text(named.get("uuid"));
        if (uuid.isEmpty() || !readable(uuid)) {
            return null;
        }
        return copyInBuild(build, uuid)
                .map(copy -> new Module(copy, JsonValues.text(named.get("name")), true))
                .orElseGet(() -> new Module(uuid, JsonValues.text(named.get("name")), false));
    }

    // The build's working copy of a document.
    private Optional<String> copyInBuild(final String build, final String original) {
        final Map<String, Object> docs = BuildRecords.docs(records.load(build));
        for (final WriteGuard.BuildDoc doc : guard.folderContents(build)) {
            if (docs.get(doc.uuid()) instanceof final Map<?, ?> entry && original.equals(entry.get("copy_of"))) {
                return Optional.of(doc.uuid());
            }
        }
        return Optional.empty();
    }

    private boolean inBuild(final String build, final String uuid) {
        return guard.buildOf(new DocRef(XsltDoc.TYPE, uuid)).filter(build::equals).isPresent();
    }

    private boolean readable(final String uuid) {
        return documentAccess.read(new DocRef(XsltDoc.TYPE, uuid), XsltDoc.class).isPresent();
    }

    private String readXsltData(final String uuid) {
        return documentAccess.read(new DocRef(XsltDoc.TYPE, uuid), XsltDoc.class)
                .map(XsltDoc::getData)
                .map(NullSafe::string)
                .orElse("");
    }

    @SuppressWarnings("unchecked")
    private void writeXslt(final XsltDoc doc) {
        final DocumentActionHandler<XsltDoc> handler = documentAccess.handler(XsltDoc.TYPE)
                .orElseThrow(() -> new ToolRefusal("Stroom has no XSLT store"));
        handler.writeDocument(doc);
    }

    private static Map<String, Object> moduleSaid(final Module module, final boolean changed) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("uuid", module.uuid());
        out.put("name", module.name());
        out.put("changed", changed);
        if (!module.inBuild()) {
            out.put("promoted", true);
        }
        return out;
    }

    // The group's source types: those given, else the build's scope's; each a row of the registry, and translated by
    // no other group.
    private List<String> sourceTypes(final String build,
                                     final Map<String, Object> state,
                                     final List<String> given) {
        final List<String> names = JsonValues.maps(state.get("rows")).stream()
                .map(row -> JsonValues.text(row.get("name")))
                .toList();
        final List<String> types = new ArrayList<>(NullSafe.list(given));
        if (types.isEmpty()) {
            builds.scope(build).map(SourceTypeScope::names).ifPresent(types::addAll);
        }
        if (types.isEmpty()) {
            throw new ToolRefusal("Give source_types: the registry's source types the group translates (the build "
                                  + "has no scope to take them from)");
        }
        final List<String> unknown = types.stream().filter(type -> !names.contains(type)).toList();
        if (!unknown.isEmpty()) {
            throw new ToolRefusal("source_types " + ToolText.list(unknown) + " are not the registry's: it has "
                                  + ToolText.list(names));
        }
        return List.copyOf(new LinkedHashSet<>(types));
    }

    // The group's System Name: its source types' own where they all have the same, else the mapping's (a value).
    private static String systemName(final Map<String, Object> state,
                                     final List<String> types,
                                     final TranslationMapping given) {
        final Set<String> named = new HashSet<>();
        JsonValues.maps(state.get("rows")).stream()
                .filter(row -> types.contains(JsonValues.text(row.get("name"))))
                .map(row -> row.get("system_name"))
                .filter(Objects::nonNull)
                .map(JsonValues::text)
                .filter(name -> !name.isBlank())
                .forEach(named::add);
        if (named.size() == 1) {
            return named.iterator().next();
        }
        return given.common().stream()
                .filter(entry -> SYSTEM_NAME.equals(entry.strippedPath()) && entry.value() != null)
                .map(FieldMapping::value)
                .filter(name -> !name.strip().startsWith("TODO"))
                .findFirst()
                .orElse(null);
    }

    // The first group of a feed: the common module made from the mapping's common entries for its parts that read
    // the record's own fields or constants, and the mapping's shared calls; the rest stays the group's.
    private static Split firstSplit(final TranslationMapping given,
                                    final Map<String, Object> state,
                                    final String feed,
                                    final String naming,
                                    final String helpersHref) {
        final Set<String> moving = new LinkedHashSet<>();
        for (final String root : SourceTypeModule.PART_ROOTS) {
            final List<FieldMapping> entries = given.common().stream()
                    .filter(entry -> root.equals(SourceTypeModule.partOf(entry.strippedPath())))
                    .toList();
            if (!entries.isEmpty() && entries.stream().allMatch(entry -> recordLevel(entry, given))) {
                moving.add(root);
            }
        }
        final List<Map<String, Object>> commonEntries = new ArrayList<>();
        final List<FieldMapping> kept = new ArrayList<>();
        for (final FieldMapping entry : given.common()) {
            if (moving.contains(SourceTypeModule.partOf(entry.strippedPath()))) {
                commonEntries.add(new LinkedHashMap<>(entry.toJson(DumpMode.COMPACT)));
            } else {
                kept.add(entry);
            }
        }
        if (moving.contains("EventSource/System")) {
            // The feed's shared decisions: its Environment, and its System Name by default.
            replaceValue(commonEntries, ENVIRONMENT, JsonValues.text(state.get("environment")));
            replaceValue(commonEntries, SYSTEM_NAME, JsonValues.text(state.get("system_name")));
        }
        final Map<String, Object> json = new LinkedHashMap<>(given.toJson(DumpMode.COMPACT));
        for (final String key : List.of("pairs", "extract", "json_fields", "xml_fields", "for_each", "drop_when",
                "ignored_fields", "unmatched", "log_unmapped_fields", "imports", "module")) {
            json.remove(key);
        }
        json.put("common", commonEntries);
        json.put("events", List.of(Map.of("name", COMMON_RULE)));
        final Map<String, Object> module = new LinkedHashMap<>();
        module.put("kind", SourceTypeModule.COMMON);
        module.put("name", feed);
        module.put("mode", XsltNames.styleName(feed + "-common", naming));
        module.put("helpers", helpersHref);
        json.put("module", module);
        final TranslationMapping common = TranslationMapping.parse(json);
        final List<String> notes = new ArrayList<>();
        if (!moving.isEmpty() || !given.shared().isEmpty()) {
            notes.add("The feed's common module '" + commonName(feed) + "' is made from this mapping's "
                      + ToolText.list(new ArrayList<>(moving)) + (given.shared().isEmpty()
                    ? ""
                    : " and its shared calls") + ": every group of the feed calls them, and a rule mapping one of "
                      + "those elements itself writes its own");
        }
        return new Split(common, given.withCommonAndShared(kept, List.of()), notes);
    }

    // The group's entries the build's common module writes the same way left to it; different ones are the group's.
    private static TranslationMapping leftToCommon(final TranslationMapping given,
                                                   final TranslationMapping common,
                                                   final List<String> notes) {
        final List<FieldMapping> kept = new ArrayList<>(given.common());
        for (final SourceTypeModule.CommonPart part : SourceTypeModule.commonParts(common)) {
            final List<FieldMapping> theirs = given.common().stream()
                    .filter(entry -> part.at().equals(SourceTypeModule.partOf(entry.strippedPath())))
                    .toList();
            if (theirs.isEmpty()) {
                continue;
            }
            final List<FieldMapping> commons = common.common().stream()
                    .filter(entry -> part.at().equals(SourceTypeModule.partOf(entry.strippedPath())))
                    .toList();
            if (compared(theirs).equals(compared(commons))) {
                kept.removeAll(theirs);
            } else {
                notes.add(part.at() + ": this group writes its own (its mapping's differs from the feed's common "
                          + "module's), so the common module's part isn't called for it");
            }
        }
        final List<SharedTemplate> shared = given.shared().stream()
                .filter(use -> common.shared().stream().noneMatch(theirs -> theirs.template().equals(use.template())
                                                                           && theirs.at().equals(use.at())))
                .toList();
        return given.withCommonAndShared(kept, shared);
    }

    // Entries compared as JSON, in any order, the System Name and Environment aside: each group passes its own name,
    // and the Environment is the feed's.
    private static Set<String> compared(final List<FieldMapping> entries) {
        final Set<String> out = new HashSet<>();
        entries.stream()
                .filter(entry -> !SYSTEM_NAME.equals(entry.strippedPath()) && !ENVIRONMENT.equals(entry.strippedPath()))
                .forEach(entry -> out.add(ToolJson.dumps(entry.toJson(DumpMode.COMPACT))));
        return out;
    }

    // An entry reading only the record's own fields, or a constant: one the common module can write for every group.
    private static boolean recordLevel(final FieldMapping entry, final TranslationMapping mapping) {
        if (entry.isGroup() || entry.repeat() || entry.lookup() != null || entry.xpath() != null
            || entry.anyOf() != null || entry.join() != null || NullSafe.isNonBlankString(entry.dictionary())) {
            return false;
        }
        if (entry.value() != null) {
            return true;
        }
        final String field = entry.field();
        if (field == null) {
            return false;
        }
        final Set<String> derived = new HashSet<>();
        mapping.extract().forEach(extraction -> derived.addAll(extraction.names()));
        final List<String> held = new ArrayList<>(mapping.jsonFields());
        held.addAll(mapping.xmlFields());
        return !derived.contains(field) && held.stream().noneMatch(name -> field.equals(name)
                                                                          || field.startsWith(name + ".")
                                                                          || field.startsWith(name + "/"));
    }

    private static void replaceValue(final List<Map<String, Object>> entries, final String path, final String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (final Map<String, Object> entry : entries) {
            if (path.equals(ToolText.strip(JsonValues.text(entry.get("path")), "/"))
                || ("Event/" + path).equals(JsonValues.text(entry.get("path")))) {
                entry.keySet().retainAll(Set.of("path"));
                entry.put("value", value);
                return;
            }
        }
        final Map<String, Object> added = new LinkedHashMap<>();
        added.put("path", path);
        added.put("value", value);
        entries.add(added);
    }

    // The feed's common module's mapping, kept with it.
    private TranslationMapping keptCommon(final Module doc) {
        final XsltDoc xslt = documentAccess.read(new DocRef(XsltDoc.TYPE, doc.uuid(), doc.name()), XsltDoc.class)
                .orElseThrow(() -> new ToolRefusal("The feed's common module '" + doc.name() + "' can't be read"));
        final Object mapping = KeptMapping.readMapping(xslt.getDescription())
                .map(kept -> kept.payload().get("mapping"))
                .orElseThrow(() -> new ToolRefusal("The feed's common module '" + doc.name() + "' keeps no mapping: "
                                                   + "it can't be read to call its parts"));
        final TranslationMapping common = TranslationMapping.parse(mapping);
        if (common.module() == null || !common.module().isCommon()) {
            throw new ToolRefusal("'" + doc.name() + "' is not a feed's common module");
        }
        return common;
    }

    // What the group writes, as one translation reads: the common module's entries for the parts the group leaves to
    // it (with its System Name), and its shared calls, with the group's own.
    private static TranslationMapping effective(final TranslationMapping group,
                                                final TranslationMapping common,
                                                final String systemName,
                                                final String field) {
        final List<FieldMapping> entries = new ArrayList<>();
        for (final FieldMapping entry : common.common()) {
            final String part = SourceTypeModule.partOf(entry.strippedPath());
            final boolean own = group.common().stream()
                    .anyMatch(theirs -> Objects.equals(part, SourceTypeModule.partOf(theirs.strippedPath())));
            if (own) {
                continue;
            }
            if (SYSTEM_NAME.equals(entry.strippedPath()) && systemName != null) {
                final Map<String, Object> json = new LinkedHashMap<>(entry.toJson(DumpMode.WITHOUT_NONE));
                json.keySet().retainAll(Set.of("path"));
                json.put("value", systemName);
                entries.add(FieldMapping.fromJson(json));
            } else {
                entries.add(entry);
            }
        }
        entries.addAll(group.common());
        final List<SharedTemplate> shared = new ArrayList<>(common.shared());
        shared.addAll(group.shared());
        final TranslationMapping effective = group.withCommonAndShared(entries, shared).withModule(null);
        if (field == null || field.isBlank()) {
            return effective;
        }
        // The source type field is the dispatcher's to read, not a field the group leaves unread.
        final Map<String, Object> json = new LinkedHashMap<>(effective.toJson(DumpMode.COMPACT));
        final Set<String> ignored = new LinkedHashSet<>(effective.ignoredFields());
        ignored.add(ToolText.split(ToolText.split(field, ".").getFirst(), "/").getFirst());
        json.put("ignored_fields", new ArrayList<>(ignored));
        return TranslationMapping.parse(json);
    }

    // The build's XSLT of that name.
    private WriteGuard.BuildDoc xsltInBuild(final String build, final String name) {
        return guard.folderContents(build).stream()
                .filter(doc -> XsltDoc.TYPE.equals(doc.type()) && name.equals(doc.name()))
                .findFirst()
                .orElse(null);
    }


    // --------------------------------------------------------------------------------


    /// The feed of a build whose registry a group is built from.
    ///
    /// @param feed  The feed's name.
    /// @param found Its registry.
    record Registry(String feed, SourceTypeRegistries.Found found) {

    }

    /// How a group is built.
    ///
    /// @param feed         The feed.
    /// @param group        The group's name.
    /// @param mode         Its mode.
    /// @param sourceTypes  Its source types.
    /// @param field        The source type field.
    /// @param systemName   Its System Name, or null for the common module's default.
    /// @param registry     The feed's registry.
    /// @param helpersDoc   The feed's helpers module (the build's, or promoted), or null.
    /// @param commonDoc    The feed's common module (the build's, or promoted), or null.
    /// @param common       The common module's mapping.
    /// @param groupMapping The group's mapping (its module importing the modules as they are now).
    /// @param effective    What the group writes, as one translation's mapping: for the sample check and the docs.
    /// @param notes        What was left to the common module, or kept the group's own.
    /// @param unsaved      The code of the modules not saved yet, by the href importing them.
    /// @param problems     What stops the common module made now being generated.
    record Plan(String feed,
                String group,
                String mode,
                List<String> sourceTypes,
                String field,
                String systemName,
                Registry registry,
                Module helpersDoc,
                Module commonDoc,
                TranslationMapping common,
                TranslationMapping groupMapping,
                TranslationMapping effective,
                List<String> notes,
                Map<String, String> unsaved,
                List<String> problems) {

        /// @param mapping The group's mapping as changed since (a domain split, a placeholder left out).
        /// @return This plan with it, its module kept.
        Plan withGroupMapping(final TranslationMapping mapping) {
            final TranslationMapping withModule = mapping.module() == null
                    ? mapping.withModule(groupMapping.module())
                    : mapping;
            return new Plan(feed, group, mode, sourceTypes, field, systemName, registry, helpersDoc, commonDoc,
                    common, withModule, SourceTypeGroups.effective(withModule.withModule(null), common, systemName,
                    field), notes, unsaved, problems);
        }
    }

    /// One of the feed's modules.
    ///
    /// @param uuid    Its UUID.
    /// @param name    Its name.
    /// @param inBuild True for the build's own (made in it, or its working copy); false for one promoted beside the
    ///                feed, which the build changes only through a working copy.
    record Module(String uuid, String name, boolean inBuild) {

    }

    /// Where a new group goes, as the user chose in the placement form.
    ///
    /// @param choice        [#SHARED], [#OWN] or [#NEW_VERSION].
    /// @param ownOutputFeed True when its Events go to an output feed of its own (a pipeline of its own only).
    record Placement(String choice, boolean ownOutputFeed) {

        /// Checks the choice.
        public Placement {
            if (!PLACEMENTS.contains(choice)) {
                throw new ToolRefusal("A group's pipeline is one of " + ToolText.list(PLACEMENTS) + ", not "
                                      + ToolText.repr(choice));
            }
            if (ownOutputFeed && !OWN.equals(choice)) {
                throw new ToolRefusal("Only a group with a pipeline of its own has an output feed of its own");
            }
        }
    }

    /// What a dispatcher made new is made with.
    ///
    /// @param helpersHref The helpers' import, or null for the dispatcher's kept one (the registry's, for a new one).
    /// @param schema      The event-logging schema the Events are written in, or null for the dispatcher's kept one.
    /// @param version     Its version, or null likewise.
    /// @param input       The input as the groups read it, for a dispatcher with no spec of the feed's to start from.
    /// @param naming      The naming style of a new dispatcher's mode, or null for snake_case.
    record Regeneration(String helpersHref,
                        EventSchema schema,
                        String version,
                        Map<String, Object> input,
                        String naming) {

    }

    /// The rows of a feed's registry one dispatcher routes.
    ///
    /// @param feed The feed.
    /// @param rows Its rows.
    record Routed(String feed, List<Map<String, Object>> rows) {

        /// @return The feed its Events go to, if the rows say one of its own: its name, or "" while it is still to
        /// be made; empty for the raw feed's.
        Optional<String> outputFeed() {
            return rows.stream()
                    .filter(row -> row.containsKey(OUTPUT_FEED) && row.get(OUTPUT_FEED) != null)
                    .map(row -> JsonValues.text(row.get(OUTPUT_FEED)))
                    .findFirst();
        }

        /// @return The reference data the rows' groups look up, each once.
        List<Map<String, Object>> references() {
            final List<Map<String, Object>> out = new ArrayList<>();
            rows.forEach(row -> JsonValues.maps(row.get(REFERENCES)).forEach(reference -> {
                if (!out.contains(reference)) {
                    out.add(reference);
                }
            }));
            return out;
        }

        /// @return The groups the rows are of, each once.
        List<String> groups() {
            return rows.stream().map(row -> JsonValues.text(row.get("group"))).distinct().toList();
        }
    }

    /// The group's mapping importing the saved modules.
    ///
    /// @param group       The group's mapping.
    /// @param helpersHref The helpers module's href.
    /// @param saved       What was saved: `helpers` and `common`.
    record Imports(TranslationMapping group, String helpersHref, Map<String, Object> saved) {

    }

    private record Split(TranslationMapping common, TranslationMapping group, List<String> notes) {

    }
}
