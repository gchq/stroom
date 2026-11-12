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
/// module and the feed's helpers made when the build has none, and, once the group is saved, the feed's dispatcher
/// generated from the registry and the registry's rows for the group's source types set `translated`.
///
/// Every module is the build's own: imports resolve to the build's documents, so the dispatcher saved in the build
/// imports its copies (a later build changing a promoted module works on a copy with its own UUID); promotion
/// regenerating the dispatcher against the originals is phase 4's, and the generator targets whichever UUIDs it is
/// given ([SourceTypeModule#withImports], [DispatcherSpec.Group#href]).
class SourceTypeGroups {

    /// What a feed's common module is named after: `<feed> common`.
    static final String COMMON_SUFFIX = " common";
    // The kept mapping's rule of the common module, which has none of its own.
    private static final String COMMON_RULE = "common";
    private static final String SYSTEM_NAME = "EventSource/System/Name";
    private static final String ENVIRONMENT = "EventSource/System/Environment";

    private final WriteGuard guard;
    private final DocumentAccess documentAccess;
    private final SourceTypeRegistries registries;
    private final TranslationDocs translationDocs;
    private final TranslationBuilds builds;

    @Inject
    SourceTypeGroups(final WriteGuard guard,
                     final DocumentAccess documentAccess,
                     final SourceTypeRegistries registries,
                     final TranslationDocs translationDocs,
                     final TranslationBuilds builds) {
        this.guard = guard;
        this.documentAccess = documentAccess;
        this.registries = registries;
        this.translationDocs = translationDocs;
        this.builds = builds;
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
    /// The common module, where the build has one, is kept as it is: a group's entries for one of its parts that
    /// are the same as the module's (the System Name and Environment aside) are left to it; different ones are the
    /// group's own, overriding the part for the group. Where the build has none, it is made from the mapping's
    /// common entries for its parts (EventTime, System, Generator, Device) that read only the record's own fields or
    /// constants (never a field held in a message, an extraction, an xpath), and the mapping's shared calls; the
    /// registry's Environment and default System Name replacing the mapping's.
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
        final WriteGuard.BuildDoc helpersDoc = xsltInBuild(build, HelpersXslt.docName(feed));
        final WriteGuard.BuildDoc commonDoc = xsltInBuild(build, commonName(feed));
        final WriteGuard.BuildDoc dispatcherDoc = xsltInBuild(build, DispatcherXslt.docName(feed));
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
        return new Plan(feed, group, mode, types, field, systemName, registry, helpersDoc, commonDoc, dispatcherDoc,
                common, groupMapping.withModule(module), effective(groupMapping, common, systemName, field), notes,
                unsaved, problems);
    }

    /// Saves what the group's XSLT imports before it: the feed's helpers (made, or written again when the generator
    /// writes them otherwise now) and its common module (made, where the build has none).
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
        final String helpersCode = HelpersXslt.generate();
        final Map<String, Object> helpers = translationDocs.saveModule(context, build,
                HelpersXslt.docName(plan.feed()), NullSafe.get(plan.helpersDoc(), WriteGuard.BuildDoc::uuid),
                helpersCode, TranslationDocs.Source.NONE, null, null, "The generator's helpers, version "
                                                                         + HelpersXslt.VERSION);
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
            saved.put("common", Map.of("uuid", plan.commonDoc().uuid(), "name", plan.commonDoc().name(), "changed",
                    false));
        }
        final TranslationMapping group = plan.groupMapping().withModule(plan.groupMapping().module()
                .withImports(commonHref, helpersHref));
        return new Imports(group, helpersHref, saved);
    }

    /// Saves the feed's dispatcher, generated from the registry with the group imported (its XSLT now saved), and
    /// sets the registry's rows for the group's source types `translated`, naming the group.
    ///
    /// @param context     The call's context.
    /// @param build       The build.
    /// @param plan        The plan.
    /// @param helpersHref The helpers module's href.
    /// @param groupUuid   The group's XSLT's UUID.
    /// @param groupName   Its name.
    /// @param schema      The event-logging schema the Events are written in.
    /// @param version     Its version.
    /// @return What was saved: the dispatcher, and the registry.
    Map<String, Object> dispatch(final ToolCallContext context,
                                 final String build,
                                 final Plan plan,
                                 final String helpersHref,
                                 final String groupUuid,
                                 final String groupName,
                                 final EventSchema schema,
                                 final String version) {
        final DispatcherSpec spec = spec(plan, helpersHref)
                .withGroup(new DispatcherSpec.Group(plan.group(), plan.mode(), href(groupUuid, groupName),
                        plan.sourceTypes()))
                .withEvents(version, XsltGenerator.schemaLocation(schema, version));
        final DispatcherXslt.Generated generated = DispatcherXslt.generate(spec, schema, version);
        if (!generated.ok()) {
            throw new ToolRefusal("The group's XSLT is saved, but the feed's dispatcher doesn't generate: "
                                  + String.join("; ", generated.problems()));
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("dispatcher", translationDocs.saveModule(context, build, DispatcherXslt.docName(plan.feed()),
                NullSafe.get(plan.dispatcherDoc(), WriteGuard.BuildDoc::uuid), generated.xslt(),
                TranslationDocs.Source.NONE, DispatcherXslt.SPEC_BLOCK, spec.toJson(), "Routes " + String.join(
                        ", ", plan.sourceTypes()) + " to group '" + plan.group() + "'"));
        out.put("registry", translated(context, build, plan, groupUuid, groupName));
        return out;
    }

    // The dispatcher's spec: the build's dispatcher's, its rows refreshed from the registry; else made from the
    // registry and the group's input.
    private DispatcherSpec spec(final Plan plan, final String helpersHref) {
        final Map<String, Object> state = plan.registry().found().state();
        final List<DispatcherSpec.Row> rows = JsonValues.maps(state.get("rows")).stream()
                .map(row -> new DispatcherSpec.Row(JsonValues.text(row.get("name")), JsonValues.text(row.get(
                        "match")), JsonValues.text(row.get("value")), JsonValues.text(row.get("status"))))
                .toList();
        final Optional<DispatcherSpec> kept = Optional.ofNullable(plan.dispatcherDoc())
                .flatMap(doc -> documentAccess.read(doc.docRef(), XsltDoc.class))
                .flatMap(doc -> KeptMapping.readBlock(doc.getDescription(), DispatcherXslt.SPEC_BLOCK))
                .flatMap(DispatcherSpec::fromJson);
        final String naming = plan.groupMapping().style().naming();
        final DispatcherSpec base = kept.orElseGet(() -> new DispatcherSpec(plan.feed(), XsltNames.styleName(
                plan.feed() + "-dispatch", naming), DispatcherSpec.inputOf(plan.groupMapping()), plan.field(),
                helpersHref, rows, List.of(), Map.of()));
        return new DispatcherSpec(base.feed(), base.mode(), base.input(), plan.field(), helpersHref, rows,
                base.groups(), base.events());
    }

    // The registry's rows for the group's source types set translated, naming the group and its XSLT.
    private Map<String, Object> translated(final ToolCallContext context,
                                           final String build,
                                           final Plan plan,
                                           final String groupUuid,
                                           final String groupName) {
        final Map<String, Object> state = JsonValues.deepCopy(plan.registry().found().state());
        final String now = SurveyDoc.at(Instant.now());
        final List<Map<String, Object>> rows = new ArrayList<>();
        for (final Map<String, Object> given : JsonValues.maps(state.get("rows"))) {
            final Map<String, Object> row = new LinkedHashMap<>(given);
            if (plan.sourceTypes().contains(JsonValues.text(row.get("name")))) {
                row.put("status", SourceTypeRegistry.TRANSLATED);
                row.put("group", plan.group());
                row.put("group_xslt", Map.of("uuid", groupUuid, "name", groupName));
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
        final SurveyRecord written = registries.writeRegistry(context.session(), build, plan.feed(),
                plan.registry().found(), SourceTypeRegistry.render(state));
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("uuid", written.uuid());
        out.put("name", written.name());
        out.put("translated", plan.sourceTypes());
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

    // The build's common module's mapping, kept with it.
    private TranslationMapping keptCommon(final WriteGuard.BuildDoc doc) {
        final XsltDoc xslt = documentAccess.read(doc.docRef(), XsltDoc.class)
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
    /// @param feed          The feed.
    /// @param group         The group's name.
    /// @param mode          Its mode.
    /// @param sourceTypes   Its source types.
    /// @param field         The source type field.
    /// @param systemName    Its System Name, or null for the common module's default.
    /// @param registry      The feed's registry.
    /// @param helpersDoc    The build's helpers module, or null.
    /// @param commonDoc     The build's common module, or null.
    /// @param dispatcherDoc The build's dispatcher, or null.
    /// @param common        The common module's mapping.
    /// @param groupMapping  The group's mapping (its module importing the modules as they are now).
    /// @param effective     What the group writes, as one translation's mapping: for the sample check and the docs.
    /// @param notes         What was left to the common module, or kept the group's own.
    /// @param unsaved       The code of the modules not saved yet, by the href importing them.
    /// @param problems      What stops the common module made now being generated.
    record Plan(String feed,
                String group,
                String mode,
                List<String> sourceTypes,
                String field,
                String systemName,
                Registry registry,
                WriteGuard.BuildDoc helpersDoc,
                WriteGuard.BuildDoc commonDoc,
                WriteGuard.BuildDoc dispatcherDoc,
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
                    dispatcherDoc, common, withModule, SourceTypeGroups.effective(withModule.withModule(null), common,
                    systemName, field),
                    notes, unsaved, problems);
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
