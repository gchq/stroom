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
import stroom.pipeline.shared.PipelineDoc;
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
import java.util.function.Predicate;

/// An aggregated feed's source type groups, as `build_translation_xslt group=` builds them: each group the XSLT of a
/// pipeline of its own, translating its source types' records and passing over the rest. The group's mapping is
/// split from the feed's common module (the wrapper's entries every group writes one way, its parts, and the site's
/// shared calls); the common module and the feed's helpers are made when the feed has none. Imports form a chain:
/// the group imports the common module, which imports the helpers and the site's libraries. Once the group is saved,
/// the registry's rows for its source types are set `translated`, naming the group and its XSLT, its pipeline
/// (`<feed> <group>` until create_pipeline makes it) and its output feed (absent for the feed's own Events).
///
/// A later build finds the feed's modules through the registry, beside the feed. It changes a promoted one through a
/// working copy with a UUID of its own ([#workingCopy]), and every promoted module importing that one is copied too,
/// importing the copy, so stepping in the build runs the copies; promotion points them all at the originals again
/// ([#toOriginals]) before the copies are written back.
class SourceTypeGroups {

    /// What a feed's common module is named after: `<feed> common`.
    static final String COMMON_SUFFIX = " common";
    // The kept mapping's rule of the common module, which has none of its own.
    private static final String COMMON_RULE = "common";
    private static final String SYSTEM_NAME = "EventSource/System/Name";
    private static final String ENVIRONMENT = "EventSource/System/Environment";
    /// A build changing a group whose pipeline is promoted changes that pipeline (through working copies).
    static final String CHANGE = "change";
    /// A build changing a group whose pipeline is promoted makes a new version of it (copy_pipeline).
    static final String NEW_VERSION = "new_version";
    /// What a build changing a group whose pipeline is promoted may do with that pipeline.
    static final List<String> PIPELINE_CHOICES = List.of(CHANGE, NEW_VERSION);
    // A row's keys: where it is translated, and its group's reference data.
    static final String PIPELINE = "pipeline";
    static final String OUTPUT_FEED = "output_feed";
    static final String REFERENCES = "references";
    private static final String MODULES = "modules";
    private static final String GROUP_XSLT = "group_xslt";
    // A build record's key naming the promoted document a build's document is a new version of.
    private static final String VERSION_OF = "version_of";

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
                if (found.get().promoted()) {
                    // The build starts work on the feed from the registry beside it: one open build per feed.
                    registries.refuseOpenBuild(feed, build, true);
                }
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
    /// The common module, where the feed has one (the build's, or promoted beside the feed), is kept: a group's
    /// entries for one of its parts that are the same as the module's (the System Name and Environment aside) are
    /// left to it; different ones are the group's own, overriding the part for the group. A shared call of the
    /// group's that the module lacks is added to the module (a group imports only the common module, so a site
    /// library is never imported twice in its stylesheet), and the module is saved again with it. Where the feed
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
        final List<String> types = sourceTypes(build, state, group, sourceTypes);
        final String naming = given.style().naming();
        final String mode = XsltNames.styleName(group, naming);
        if (mode.equals(XsltNames.styleName(feed + "-common", naming))) {
            throw new ToolRefusal("The group's name '" + group + "' makes the mode of the feed's common module: name "
                                  + "it for its source types");
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
        final Split split = commonDoc == null
                ? firstSplit(given, state, feed, naming, helpersHref)
                : leftToCommon(given, keptCommon(commonDoc), helpersHref);
        final TranslationMapping common = split.common();
        final TranslationMapping groupMapping = split.group();
        notes.addAll(split.notes());
        final boolean commonChanged = commonDoc != null && split.commonChanged();
        final SourceTypeModule module = new SourceTypeModule(SourceTypeModule.GROUP, group, mode, types, commonHref,
                null, SourceTypeModule.commonParts(common), systemName, field, rows(state));
        final Map<String, String> unsaved = new LinkedHashMap<>();
        final List<String> problems = new ArrayList<>();
        if (helpersDoc == null) {
            unsaved.put(helpersHref, HelpersXslt.generate());
        }
        if (commonDoc == null || commonChanged) {
            final XsltGenerator.Generated generated = XsltGenerator.generate(common, schema, version);
            generated.problems().forEach(problem -> problems.add("the feed's common module: " + problem));
            if (generated.ok()) {
                unsaved.put(commonHref, generated.xslt());
            }
        }
        return new Plan(feed, group, mode, types, field, systemName, registry, helpersDoc, commonDoc, common,
                commonChanged, groupMapping.withModule(module), effective(groupMapping, common, systemName, field),
                notes, unsaved, problems);
    }

    // The registry's rows, in the order they are tried, as a group tells its records by them.
    private static List<SourceTypeModule.Row> rows(final Map<String, Object> state) {
        return JsonValues.maps(state.get("rows")).stream()
                .map(row -> new SourceTypeModule.Row(JsonValues.text(row.get("name")), JsonValues.text(row.get(
                        "match")), JsonValues.text(row.get("value"))))
                .toList();
    }

    /// Saves what the group's XSLT imports before it: the feed's helpers (made, or written again when the generator
    /// writes them otherwise now: a promoted one through a working copy) and its common module (made, where the feed
    /// has none; written again with a shared call the group added to it, a promoted one through a working copy).
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
                    TranslationDocs.Source.NONE, "The generator's helpers, version " + HelpersXslt.VERSION);
        }
        saved.put("helpers", helpers);
        final String helpersHref = href(String.valueOf(helpers.get("uuid")), String.valueOf(helpers.get("name")));
        final String commonHref;
        if (plan.commonDoc() == null || plan.commonChanged()) {
            final TranslationMapping common = plan.common().withModule(plan.common().module().withImports(null,
                    helpersHref));
            final XsltGenerator.Generated generated = XsltGenerator.generate(common, schema, version);
            if (!generated.ok()) {
                throw new ToolRefusal("The feed's common module doesn't generate: " + String.join("; ",
                        generated.problems()));
            }
            // The build's copy where it has one (the helpers' working copy may have brought one), else a working
            // copy of the promoted one; a new one for a feed with none.
            final Module existing = plan.commonDoc() == null
                    ? null
                    : Objects.requireNonNullElse(module(build, state, "common", commonName(plan.feed())),
                            plan.commonDoc());
            final String into = existing == null
                    ? null
                    : existing.inBuild()
                            ? existing.uuid()
                            : workingCopy(context, build, state, existing.uuid());
            final Map<String, Object> made = translationDocs.saveModule(context, build, commonName(plan.feed()),
                    into, generated.xslt(), TranslationDocs.Source.of(common), existing == null
                            ? "The parts every source type group of " + plan.feed() + " writes one way"
                            : "The shared calls of group '" + plan.group() + "' added");
            saved.put("common", made);
            commonHref = href(String.valueOf(made.get("uuid")), String.valueOf(made.get("name")));
        } else {
            // The build's copy, where the helpers' working copy brought one.
            final Module common = Objects.requireNonNullElse(module(build, state, "common", commonName(plan.feed())),
                    plan.commonDoc());
            saved.put("common", moduleSaid(common, false));
            commonHref = href(common.uuid(), common.name());
        }
        // The group imports the common module alone, which imports the helpers: a chain.
        final TranslationMapping group = plan.groupMapping().withModule(plan.groupMapping().module()
                .withImports(commonHref, null));
        return new Imports(group, helpersHref, saved);
    }

    /// The XSLT a group is saved in: the build's own (the one the registry names, or of the group's name), else a
    /// copy of the group's promoted XSLT made now: a working copy, written back over it at promotion, or, where the
    /// user chose a new version of the group's pipeline, a new version of it, promoted beside its predecessor. A copy
    /// imports the build's copies of the feed's modules, so the chain stays the build's.
    ///
    /// @param context The call's context.
    /// @param build   The build.
    /// @param plan    The plan.
    /// @param name    The name the group's XSLT is saved with.
    /// @param choice  [#CHANGE] or [#NEW_VERSION] for a group whose pipeline is promoted, or null.
    /// @return The XSLT's UUID; empty when a new one is made.
    Optional<String> groupTarget(final ToolCallContext context,
                                 final String build,
                                 final Plan plan,
                                 final String name,
                                 final String choice) {
        final Map<String, Object> state = plan.registry().found().state();
        final String uuid = groupXslt(state, plan.group());
        if (uuid.isEmpty() || !readable(uuid)) {
            return xsltNamed(build, name);
        }
        if (inBuild(build, uuid)) {
            return Optional.of(uuid);
        }
        final Optional<String> have = copyInBuild(build, uuid).or(() -> versionInBuild(build, uuid));
        if (have.isPresent()) {
            return have;
        }
        return Optional.of(NEW_VERSION.equals(choice)
                ? versionCopy(context, build, uuid)
                : workingCopy(context, build, state, uuid));
    }

    // The UUID of the XSLT the registry names for a group, or "".
    private static String groupXslt(final Map<String, Object> state, final String group) {
        return groupRows(state, group).stream()
                .map(row -> JsonValues.text(JsonValues.mapOrEmpty(row.get(GROUP_XSLT)).get("uuid")))
                .filter(uuid -> !uuid.isEmpty())
                .findFirst()
                .orElse("");
    }

    // The registry's rows of a group, in order.
    private static List<Map<String, Object>> groupRows(final Map<String, Object> state, final String group) {
        return JsonValues.maps(state.get("rows")).stream()
                .filter(row -> group.equals(JsonValues.text(row.get("group"))))
                .toList();
    }

    /// Whether the user chooses where a group's Events go ([Placement]): for a new group, none of whose rows is
    /// placed. A source type added to a group placed already joins the group's pipeline and output feed.
    ///
    /// @param plan A group's plan.
    /// @return True if the user is asked where the group's Events go.
    static boolean needsPlacement(final Plan plan) {
        return groupPlacement(plan.registry().found().state(), plan.group()).isEmpty();
    }

    // Where a group's rows already run: the first of its rows placed (its pipeline and output feed); empty for a
    // group none of whose rows is placed.
    private static Optional<Map<String, Object>> groupPlacement(final Map<String, Object> state, final String group) {
        return groupRows(state, group).stream()
                .filter(row -> !JsonValues.mapOrEmpty(row.get(PIPELINE)).isEmpty())
                .findFirst();
    }

    /// The group's pipeline, when it is promoted and the build changes the group for the first time: the user then
    /// chooses whether the change goes to that pipeline or to a new version of it. Not asked again once the build has
    /// a copy of the group's XSLT (a working copy, made by a change or by copy_pipeline, or a new version's).
    ///
    /// @param build A build.
    /// @param plan  A group's plan.
    /// @return The pipeline (`name`, `uuid`), as the registry names it; empty for a group whose pipeline is still to
    /// be made or is the build's, or whose change is decided already.
    Optional<Map<String, Object>> promotedPipeline(final String build, final Plan plan) {
        final Map<String, Object> state = plan.registry().found().state();
        final Optional<Map<String, Object>> placed = groupPlacement(state, plan.group())
                .map(row -> JsonValues.mapOrEmpty(row.get(PIPELINE)));
        final String pipeline = placed.map(map -> JsonValues.text(map.get("uuid"))).orElse("");
        if (pipeline.isEmpty() || guard.buildOf(new DocRef(PipelineDoc.TYPE, pipeline)).isPresent()
            || !documentAccess.read(new DocRef(PipelineDoc.TYPE, pipeline), PipelineDoc.class).isPresent()) {
            return Optional.empty();
        }
        final String xslt = groupXslt(state, plan.group());
        if (!xslt.isEmpty() && (inBuild(build, xslt) || copyInBuild(build, xslt).isPresent()
                                || versionInBuild(build, xslt).isPresent())) {
            return Optional.empty();
        }
        return placed;
    }

    /// @param feed  An aggregated feed's name.
    /// @param group One of its source type groups.
    /// @return The name its pipeline is given by default: `<feed> <group>`.
    static String pipelineName(final String feed, final String group) {
        return feed + " " + group;
    }

    /// Saves the group in the registry: its rows set `translated`, naming the group, its XSLT, its pipeline (by the
    /// name it is to be made with, for a new group), the output feed and its reference data; and the feed's modules
    /// (helpers, common).
    ///
    /// @param context    The call's context.
    /// @param build      The build.
    /// @param plan       The plan.
    /// @param imports    What the group imports, as saved.
    /// @param groupUuid  The group's XSLT's UUID.
    /// @param groupName  Its name.
    /// @param placement  Where a new group's Events go, or null for one placed already.
    /// @param choice     [#NEW_VERSION] when the user chose a new version of the group's promoted pipeline, else
    ///                   null.
    /// @param references The reference data its mapping looks up, or null to keep what the registry has.
    /// @return What was saved: the registry, and where the group runs.
    Map<String, Object> markTranslated(final ToolCallContext context,
                                       final String build,
                                       final Plan plan,
                                       final Imports imports,
                                       final String groupUuid,
                                       final String groupName,
                                       final Placement placement,
                                       final String choice,
                                       final List<Map<String, Object>> references) {
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
        // A group placed already: the source types added to it join its pipeline and output feed.
        final Optional<Map<String, Object>> joined = placement == null
                ? groupPlacement(state, plan.group()).map(JsonValues::deepCopy)
                : Optional.empty();
        final List<String> joining = new ArrayList<>();
        final List<Map<String, Object>> rows = new ArrayList<>();
        for (final Map<String, Object> given : JsonValues.maps(state.get("rows"))) {
            final Map<String, Object> row = new LinkedHashMap<>(given);
            final boolean ours = plan.sourceTypes().contains(JsonValues.text(row.get("name")));
            if (ours) {
                row.put("status", SourceTypeRegistry.TRANSLATED);
                row.put("group", plan.group());
                row.put(GROUP_XSLT, Map.of("uuid", groupUuid, "name", groupName));
                if (placement != null) {
                    row.put(PIPELINE, Map.of("name", pipelineName(plan.feed(), plan.group())));
                    if (placement.ownOutputFeed()) {
                        row.put(OUTPUT_FEED, "");
                    } else {
                        row.remove(OUTPUT_FEED);
                    }
                } else if (joined.isPresent() && JsonValues.mapOrEmpty(row.get(PIPELINE)).isEmpty()) {
                    row.put(PIPELINE, JsonValues.deepCopy(joined.get().get(PIPELINE)));
                    if (joined.get().get(OUTPUT_FEED) != null) {
                        row.put(OUTPUT_FEED, joined.get().get(OUTPUT_FEED));
                    } else {
                        row.remove(OUTPUT_FEED);
                    }
                    joining.add(JsonValues.text(row.get("name")));
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
            if ((ours || plan.group().equals(JsonValues.text(row.get("group")))) && NEW_VERSION.equals(choice)) {
                // Every row of the group: its pipeline's new version is still to be copied (copy_pipeline).
                final Map<String, Object> pipeline = new LinkedHashMap<>(JsonValues.mapOrEmpty(row.get(PIPELINE)));
                pipeline.put(NEW_VERSION, true);
                row.put(PIPELINE, pipeline);
            }
            rows.add(row);
        }
        state.put("rows", rows);
        state.put("updated", now);
        final SurveyRecord written = registries.writeRegistry(context.session(), build, plan.feed(),
                plan.registry().found(), SourceTypeRegistry.render(state));
        final Map<String, Object> out = new LinkedHashMap<>();
        final Map<String, Object> row = rows.stream()
                .filter(each -> plan.sourceTypes().contains(JsonValues.text(each.get("name"))))
                .findFirst()
                .orElse(Map.of());
        out.put("pipeline", JsonValues.mapOrEmpty(row.get(PIPELINE)));
        if (row.get(OUTPUT_FEED) != null) {
            out.put(OUTPUT_FEED, row.get(OUTPUT_FEED));
        }
        if (!joining.isEmpty()) {
            final String pipeline = JsonValues.text(JsonValues.mapOrEmpty(joined.get().get(PIPELINE)).get("name"));
            final String outputFeed = JsonValues.text(joined.get().get(OUTPUT_FEED));
            out.put("notes", List.of("Source type(s) " + ToolText.list(joining) + " joined group '" + plan.group()
                                     + "' where it runs: its pipeline" + (pipeline.isEmpty()
                    ? ""
                    : " '" + pipeline + "'") + (joined.get().get(OUTPUT_FEED) != null
                    ? ", output feed " + (outputFeed.isEmpty()
                            ? "(still to be made)"
                            : "'" + outputFeed + "'")
                    : ", into the feed's Events") + ". Nothing was asked: where the group's Events go is theirs."));
        }
        final Map<String, Object> registry = new LinkedHashMap<>();
        registry.put("uuid", written.uuid());
        registry.put("name", written.name());
        registry.put("translated", plan.sourceTypes());
        out.put("registry", registry);
        return out;
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

    // A new version of a promoted group's XSLT, in the build: its content as the original's (so a later save is
    // checked for hand edits as a working copy is), promoted beside its predecessor, importing the build's copies of
    // the feed's modules.
    private String versionCopy(final ToolCallContext context, final String build, final String original) {
        final XsltDoc doc = documentAccess.read(new DocRef(XsltDoc.TYPE, original), XsltDoc.class)
                .orElseThrow(() -> new ToolRefusal("XSLT " + original + " can't be read: it doesn't exist, or you "
                                                   + "don't have permission to view it"));
        final DocRef ref = guard.create(XsltDoc.TYPE, doc.getName(), build, null, false, "Events");
        records.update(build, ref, entry -> entry.put(VERSION_OF, original));
        final XsltDoc made = documentAccess.read(ref, XsltDoc.class)
                .orElseThrow(() -> new ToolRefusal("The new version of '" + doc.getName() + "' can't be read"));
        writeXslt(made.copy().data(doc.getData()).description(doc.getDescription()).build());
        if (guard.tags(new DocRef(XsltDoc.TYPE, original)).contains(WriteGuard.KEPT_MAPPING)) {
            guard.tag(List.of(ref), Set.of(WriteGuard.KEPT_MAPPING));
        }
        chainCopies(context, build);
        return ref.getUuid();
    }

    /// Keeps the build's chain of imports its own: every module of the build importing a promoted module the build
    /// has a working copy of imports the copy instead (a group copied by copy_pipeline imports the build's copy of
    /// the common module, which imports the build's copy of the helpers, where those are copied).
    ///
    /// @param context The call's context.
    /// @param build   The build.
    /// @return The names of the XSLTs changed.
    List<String> chainCopies(final ToolCallContext context, final String build) {
        final Map<String, Object> docs = BuildRecords.docs(records.load(build));
        // An aggregated feed's modules only (the generator's mark in each): other XSLTs are left as they are.
        final List<WriteGuard.BuildDoc> xslts = guard.folderContents(build).stream()
                .filter(doc -> XsltDoc.TYPE.equals(doc.type())
                               && ModuleChecks.marker(readXsltData(doc.uuid())).isPresent())
                .toList();
        final Map<String, String> copies = new LinkedHashMap<>();
        for (final WriteGuard.BuildDoc doc : xslts) {
            if (docs.get(doc.uuid()) instanceof final Map<?, ?> entry && entry.get("copy_of") != null) {
                copies.put(String.valueOf(entry.get("copy_of")), doc.uuid());
            }
        }
        final List<String> changed = new ArrayList<>();
        if (copies.isEmpty()) {
            return changed;
        }
        for (final WriteGuard.BuildDoc doc : xslts) {
            if (translationDocs.rewriteImports(context, doc.uuid(), copies, "Imports the build's working copies")) {
                changed.add(doc.name());
            }
        }
        return changed;
    }

    /// @param build    A build.
    /// @param xsltUuid An XSLT's UUID.
    /// @return The source type group whose XSLT it is (by the module mark the generator writes in it), with its
    /// registry's rows; empty for any other XSLT, or a build with no registry naming the group.
    Optional<GroupRun> groupOf(final String build, final String xsltUuid) {
        final Optional<ModuleChecks.Marker> marker = documentAccess.read(new DocRef(XsltDoc.TYPE, xsltUuid),
                        XsltDoc.class)
                .map(XsltDoc::getData)
                .flatMap(ModuleChecks::marker)
                .filter(found -> SourceTypeModule.GROUP.equals(found.kind()) && found.name() != null);
        if (marker.isEmpty()) {
            return Optional.empty();
        }
        final Registry registry;
        try {
            registry = registry(build, List.of());
        } catch (final ToolRefusal e) {
            return Optional.empty();
        }
        final List<Map<String, Object>> rows = groupRows(registry.found().state(), marker.get().name());
        return rows.isEmpty()
                ? Optional.empty()
                : Optional.of(new GroupRun(registry.feed(), marker.get().name(), xsltUuid, rows));
    }

    /// @param build A build.
    /// @param group A source type group of its registry.
    /// @return The group, with its rows.
    /// @throws ToolRefusal if the build's registry has no such group, or it has no XSLT yet.
    GroupRun groupNamed(final String build, final String group) {
        final Registry registry = registry(build, List.of());
        final List<Map<String, Object>> rows = groupRows(registry.found().state(), group);
        if (rows.isEmpty()) {
            throw new ToolRefusal("The registry of " + registry.feed() + " has no source type group '" + group
                                  + "': build_translation_xslt group= saves it first");
        }
        final String xslt = groupXslt(registry.found().state(), group);
        if (xslt.isEmpty()) {
            throw new ToolRefusal("Group '" + group + "' has no XSLT yet: build_translation_xslt group= saves it");
        }
        return new GroupRun(registry.feed(), group, xslt, rows);
    }

    /// The pipeline made in the build for a group, named in the registry's rows of the group.
    ///
    /// @param context  The call's context.
    /// @param build    The build.
    /// @param group    The group.
    /// @param pipeline The pipeline made.
    /// @return The source types it translates.
    List<String> pipelineMade(final ToolCallContext context,
                              final String build,
                              final String group,
                              final DocRef pipeline) {
        return movePipeline(context, build, row -> group.equals(JsonValues.text(row.get("group"))), pipeline,
                Map.of());
    }

    /// A new version of a group's promoted pipeline copied into the build (copy_pipeline): the group's rows move to
    /// it, naming the copy of the group's XSLT it runs.
    ///
    /// @param context  The call's context.
    /// @param build    The build.
    /// @param original The pipeline copied.
    /// @param copies   The copies of the documents it owns, by the original's UUID.
    /// @param pipeline The new version.
    /// @return The source types it translates; none when the registry names no group in the pipeline copied.
    List<String> newVersion(final ToolCallContext context,
                            final String build,
                            final String original,
                            final Map<String, DocRef> copies,
                            final DocRef pipeline) {
        final Registry registry;
        try {
            registry = registry(build, List.of());
        } catch (final ToolRefusal e) {
            return List.of();
        }
        final boolean named = JsonValues.maps(registry.found().state().get("rows")).stream()
                .anyMatch(row -> original.equals(JsonValues.text(JsonValues.mapOrEmpty(row.get(PIPELINE))
                        .get("uuid"))));
        if (!named) {
            return List.of();
        }
        return movePipeline(context, build, row -> original.equals(JsonValues.text(JsonValues.mapOrEmpty(
                row.get(PIPELINE)).get("uuid"))), pipeline, copies);
    }

    // The rows chosen run by the pipeline given: its name and UUID (a new version still to be copied no longer),
    // and the copy of the group's XSLT where the copies have one.
    private List<String> movePipeline(final ToolCallContext context,
                                      final String build,
                                      final Predicate<Map<String, Object>> chosen,
                                      final DocRef pipeline,
                                      final Map<String, DocRef> copies) {
        final Registry registry = registry(build, List.of());
        final Map<String, Object> state = JsonValues.deepCopy(registry.found().state());
        final List<String> types = new ArrayList<>();
        final List<Map<String, Object>> rows = new ArrayList<>();
        for (final Map<String, Object> given : JsonValues.maps(state.get("rows"))) {
            final Map<String, Object> row = new LinkedHashMap<>(given);
            if (chosen.test(row)) {
                final Map<String, Object> placed = new LinkedHashMap<>();
                placed.put("name", pipeline.getName());
                placed.put("uuid", pipeline.getUuid());
                row.put(PIPELINE, placed);
                final DocRef copy = copies.get(JsonValues.text(JsonValues.mapOrEmpty(row.get(GROUP_XSLT)).get("uuid")));
                if (copy != null) {
                    row.put(GROUP_XSLT, Map.of("uuid", copy.getUuid(), "name", NullSafe.string(copy.getName())));
                }
                types.add(JsonValues.text(row.get("name")));
            }
            rows.add(row);
        }
        if (!rows.equals(JsonValues.maps(state.get("rows")))) {
            state.put("rows", rows);
            state.put("updated", SurveyDoc.at(Instant.now()));
            registries.writeRegistry(context.session(), build, registry.feed(), registry.found(),
                    SourceTypeRegistry.render(state));
        }
        return types;
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

    // The feed's modules promoted beside it, as its registry names them: helpers, common module and groups.
    private List<String> promotedModules(final String build, final Map<String, Object> state) {
        final Set<String> found = new LinkedHashSet<>();
        JsonValues.mapOrEmpty(state.get(MODULES)).values()
                .forEach(module -> found.add(JsonValues.text(JsonValues.mapOrEmpty(module).get("uuid"))));
        for (final Map<String, Object> row : JsonValues.maps(state.get("rows"))) {
            found.add(JsonValues.text(JsonValues.mapOrEmpty(row.get(GROUP_XSLT)).get("uuid")));
        }
        return found.stream()
                .filter(uuid -> !uuid.isEmpty() && readable(uuid)
                                && guard.buildOf(new DocRef(XsltDoc.TYPE, uuid)).isEmpty())
                .toList();
    }

    /// Before a build's working copies are written back at promotion: every module of the build imports the originals
    /// again (each working copy's UUID replaced by its original's, in its code and in the mapping kept with it), and
    /// the registry names the originals. A working copy the user kept in the build (its original changed in production
    /// since it was copied) is left as it is: the modules promoted import its original, as production has it
    /// ([#keptProblems] says when that original lacks what they need of it).
    ///
    /// @param context The call's context.
    /// @param build   The build.
    /// @param docs    The build's documents, as the plan lists them (`working_copy_of`).
    /// @param kept    The UUIDs of the working copies the user kept in the build.
    /// @return What was done, said for the promotion's result; none for a build with no working copy of a module.
    List<String> toOriginals(final ToolCallContext context,
                             final String build,
                             final List<Map<String, Object>> docs,
                             final Set<String> kept) {
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
            if (!XsltDoc.TYPE.equals(doc.get("type")) || kept.contains(uuid)) {
                continue;
            }
            if (translationDocs.rewriteImports(context, uuid, back, change)) {
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

    /// What stops a promotion that keeps working copies in the build: each module promoted that imports a kept copy
    /// imports its original instead, as production has it, and the names it uses of the copy (templates, functions,
    /// variables, modes) must be the original's too.
    ///
    /// @param build The build.
    /// @param docs  The build's documents, as the plan lists them (`working_copy_of`).
    /// @param kept  The UUIDs of the working copies the user kept in the build.
    /// @return Each module promoted that would import an original lacking what it uses, saying which; none when
    /// every original has it.
    List<String> keptProblems(final String build, final List<Map<String, Object>> docs, final Set<String> kept) {
        final List<String> out = new ArrayList<>();
        for (final Map<String, Object> copy : docs) {
            final String copyUuid = String.valueOf(copy.get("uuid"));
            if (!XsltDoc.TYPE.equals(copy.get("type")) || !kept.contains(copyUuid)
                || copy.get("working_copy_of") == null) {
                continue;
            }
            final String original = String.valueOf(copy.get("working_copy_of"));
            final Set<String> lacking = new LinkedHashSet<>(ModuleChecks.declaredNames(readXsltData(copyUuid)));
            lacking.removeAll(ModuleChecks.declaredNames(readXsltData(original)));
            if (lacking.isEmpty()) {
                continue;
            }
            for (final Map<String, Object> importer : docs) {
                final String uuid = String.valueOf(importer.get("uuid"));
                if (!XsltDoc.TYPE.equals(importer.get("type")) || kept.contains(uuid)) {
                    continue;
                }
                // Importing the copy, or its original already (pointed back at it).
                final String code = readXsltData(uuid);
                if (!code.contains("uuid='" + copyUuid + "'") && !code.contains("uuid='" + original + "'")) {
                    continue;
                }
                final List<String> used = lacking.stream().filter(name -> ModuleChecks.uses(code, name)).toList();
                if (!used.isEmpty()) {
                    out.add("'" + importer.get("name") + "' imports '" + copy.get("name") + "', kept in the build: "
                            + "promoted, it imports production's '" + copy.get("name") + "' (" + original + "), "
                            + "which lacks " + ToolText.list(used) + " that it uses");
                }
            }
        }
        return out;
    }

    /// @param build A build.
    /// @param group A source type group of its registry.
    /// @throws ToolRefusal if the registry has no such group, or its Events go into the feed's.
    void checkOwnOutputFeed(final String build, final String group) {
        final Registry registry = registry(build, List.of());
        final List<Map<String, Object>> rows = groupRows(registry.found().state(), group);
        if (rows.isEmpty()) {
            throw new ToolRefusal("The registry of " + registry.feed() + " has no source type group '" + group
                                  + "': build_translation_xslt group= saves it first");
        }
        if (rows.stream().noneMatch(row -> row.get(OUTPUT_FEED) != null)) {
            throw new ToolRefusal(intoFeedsEvents(group));
        }
    }

    // Why a group whose Events go into the feed's has no output feed of its own.
    private static String intoFeedsEvents(final String group) {
        return "Group '" + group + "''s Events go into the feed's Events, as the user chose when the group was "
               + "saved: only a group the user gave an output feed of its own has one";
    }

    /// Sets the output feed of a group whose Events go to a feed of its own, made in the build.
    ///
    /// @param context The call's context.
    /// @param build   The build.
    /// @param group   The group.
    /// @param feed    The output feed's name.
    /// @return The source types whose Events go there.
    /// @throws ToolRefusal if the build's registry has no such group, or its Events go into the feed's.
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
                own |= row.get(OUTPUT_FEED) != null;
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
            throw new ToolRefusal(intoFeedsEvents(group));
        }
        state.put("rows", rows);
        registries.writeRegistry(context.session(), build, registry.feed(), registry.found(),
                SourceTypeRegistry.render(state));
        return types;
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

    // The build's new version of a document.
    private Optional<String> versionInBuild(final String build, final String original) {
        final Map<String, Object> docs = BuildRecords.docs(records.load(build));
        for (final WriteGuard.BuildDoc doc : guard.folderContents(build)) {
            if (docs.get(doc.uuid()) instanceof final Map<?, ?> entry && original.equals(entry.get(VERSION_OF))) {
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
                                     final String group,
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
        final List<String> theirs = new ArrayList<>();
        for (final Map<String, Object> row : JsonValues.maps(state.get("rows"))) {
            final String other = JsonValues.text(row.get("group"));
            if (types.contains(JsonValues.text(row.get("name")))
                && SourceTypeRegistry.TRANSLATED.equals(row.get("status")) && !other.isEmpty()
                && !other.equals(group)) {
                theirs.add("'" + row.get("name") + "' (group '" + other + "')");
            }
        }
        if (!theirs.isEmpty()) {
            throw new ToolRefusal("Source type(s) " + String.join(", ", theirs) + " are translated by another group "
                                  + "already: a source type belongs to one group. Leave them out of source_types, or "
                                  + "change that group first. Nothing was saved.");
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
                "ignored_fields", "unmatched", "log_unmapped_fields", "imports", "functions", "module")) {
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
        return new Split(common, given.withCommonAndShared(kept, List.of()), notes, true);
    }

    // The group's entries the build's common module writes the same way left to it; different ones are the group's.
    // The group's shared calls the module lacks are added to it: a group imports only the common module, which
    // imports the site's libraries, so none is imported twice in the group's stylesheet.
    private static Split leftToCommon(final TranslationMapping given,
                                      final TranslationMapping common,
                                      final String helpersHref) {
        final List<String> notes = new ArrayList<>();
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
        final List<SharedTemplate> added = given.shared().stream()
                .filter(use -> common.shared().stream().noneMatch(theirs -> theirs.template().equals(use.template())
                                                                           && theirs.at().equals(use.at())))
                .toList();
        if (added.isEmpty()) {
            return new Split(common, given.withCommonAndShared(kept, List.of()), notes, false);
        }
        final List<SharedTemplate> shared = new ArrayList<>(common.shared());
        shared.addAll(added);
        final TranslationMapping withCalls = common.withCommonAndShared(common.common(), shared)
                .withModule(common.module().withImports(null, helpersHref));
        notes.add("Shared call(s) " + ToolText.list(added.stream().map(SharedTemplate::template).toList())
                  + " added to the feed's common module '" + (common.module() == null
                ? "common"
                : common.module().name() + COMMON_SUFFIX) + "', which imports their libraries: a group imports only "
                  + "the common module, and calls them through it");
        return new Split(withCalls, given.withCommonAndShared(kept, List.of()), notes, true);
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
        // The source type field is read to tell the group's records from the rest, not a field the group leaves
        // unread.
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
    /// @param commonChanged True when the build's or promoted common module is saved again (a shared call of the
    ///                     group's added to it).
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
                boolean commonChanged,
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
                    common, commonChanged, withModule, SourceTypeGroups.effective(withModule.withModule(null), common,
                    systemName, field), notes, unsaved, problems);
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

    /// Where a new group's Events go, as the user chose in the placement form.
    ///
    /// @param ownOutputFeed True for an output feed of its own; false for the feed's Events.
    record Placement(boolean ownOutputFeed) {

    }

    /// A source type group, as a pipeline runs it.
    ///
    /// @param feed  The aggregated feed.
    /// @param group The group's name.
    /// @param xslt  Its XSLT's UUID.
    /// @param rows  The registry's rows of the group.
    record GroupRun(String feed, String group, String xslt, List<Map<String, Object>> rows) {

        /// @return The feed its Events go to, where it has one of its own: its name, or "" while it is still to be
        /// made; empty for the feed's own Events.
        Optional<String> outputFeed() {
            return rows.stream()
                    .map(row -> row.get(OUTPUT_FEED))
                    .filter(Objects::nonNull)
                    .map(JsonValues::text)
                    .findFirst();
        }

        /// @return The reference data the group's lookups read, each once.
        List<Map<String, Object>> references() {
            final List<Map<String, Object>> out = new ArrayList<>();
            rows.forEach(row -> JsonValues.maps(row.get(REFERENCES)).forEach(reference -> {
                if (!out.contains(reference)) {
                    out.add(reference);
                }
            }));
            return out;
        }

        /// @return The pipeline the registry names for the group (`name`, `uuid` once made), or empty.
        Map<String, Object> pipeline() {
            return rows.stream()
                    .map(row -> JsonValues.mapOrEmpty(row.get(PIPELINE)))
                    .filter(pipeline -> !pipeline.isEmpty())
                    .findFirst()
                    .orElse(Map.of());
        }
    }

    /// The group's mapping importing the saved modules.
    ///
    /// @param group       The group's mapping.
    /// @param helpersHref The helpers module's href.
    /// @param saved       What was saved: `helpers` and `common`.
    record Imports(TranslationMapping group, String helpersHref, Map<String, Object> saved) {

    }

    private record Split(TranslationMapping common,
                         TranslationMapping group,
                         List<String> notes,
                         boolean commonChanged) {

    }
}
