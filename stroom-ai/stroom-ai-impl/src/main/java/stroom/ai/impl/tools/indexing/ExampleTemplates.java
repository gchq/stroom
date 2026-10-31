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

package stroom.ai.impl.tools.indexing;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/// The final index template for a new index, built from the field plan's template and the user's example (an index
/// template or an index's mapping, with the component templates it is composed of), or from the plan alone with the
/// component templates the user (or their standing instructions) nominates; and the fields such a template leaves
/// to Elastic's ecs@mappings.
///
/// Templates are JSON-like values: maps (in key order), lists, strings, numbers, booleans and nulls.
public final class ExampleTemplates {

    private ExampleTemplates() {
        // Static only.
    }

    /// The components a new template may compose: the example's, less those excluded, then those nominated (by the
    /// user, or their standing instructions) that the example doesn't already compose, in the order given.
    ///
    /// @param exampleComposedOf The example's composed_of.
    /// @param include           The components nominated.
    /// @param exclude           The components excluded.
    /// @return The components.
    public static List<String> composition(final List<String> exampleComposedOf,
                                           final List<String> include,
                                           final List<String> exclude) {
        final List<String> names = exampleComposedOf.stream().filter(n -> !exclude.contains(n)).toList();
        final List<String> added = new LinkedHashSet<>(include).stream().filter(n -> !names.contains(n)).toList();
        // Elastic's ECS base first, nominated or not: the other components and the template's own mappings
        // override it.
        final List<String> first = added.stream().filter(EcsSchema.COMPONENT::equals).toList();
        final List<String> out = new ArrayList<>(first);
        out.addAll(names);
        added.stream().filter(n -> !first.contains(n)).forEach(out::add);
        return out;
    }

    /// A template built from the field plan alone, composing the nominated components (and not the excluded): the
    /// fields they map are left to them, not mapped here again.
    ///
    /// @param body       The plan's template body.
    /// @param components The component templates given, by name.
    /// @param include    The components nominated.
    /// @param exclude    The components excluded.
    /// @param ecs        The Elastic Common Schema.
    /// @return The body, and notes saying what was done.
    @SuppressWarnings("unchecked")
    public static Built withComponents(final Map<String, Object> body,
                                       final Map<String, Map<String, Object>> components,
                                       final List<String> include,
                                       final List<String> exclude,
                                       final EcsSchema ecs) {
        final Map<String, Object> out = JsonValues.deepCopy(body);
        final List<String> composedOf = composition(JsonValues.strings(out.get("composed_of")), include, exclude);
        final List<String> notes = new ArrayList<>();
        Map<String, Object> mappings = TemplateMappings.mappingsOf(out);
        final List<String> left = new ArrayList<>();
        for (final String name : include) {
            final Map<String, Map<String, Object>> fields = TemplateMappings.readMapping(
                    TemplateMappings.mappingsOf(JsonValues.mapOrEmpty(components.get(name)))).fields();
            for (final Map.Entry<String, Map<String, Object>> entry : fields.entrySet()) {
                if (!"object".equals(entry.getValue().get("type"))
                    && TemplateMappings.drop(propertiesOf(mappings), entry.getKey(),
                        Boolean.FALSE.equals(mappings.get("subobjects")))) {
                    left.add(entry.getKey());
                }
            }
        }
        if (composedOf.contains(EcsSchema.COMPONENT) && include.contains(EcsSchema.COMPONENT)) {
            // Seen in testing: asked to use ecs@mappings, the template composed it but still mapped every ECS
            // field, with dynamic false, so the component mapped nothing. Built in, it has no content given: what
            // it maps is known.
            final LeftToEcs leftToEcs = leaveToEcsComponent(mappings, ecs);
            mappings = leftToEcs.mappings();
            ((Map<String, Object>) out.computeIfAbsent("template", key -> new LinkedHashMap<String, Object>()))
                    .put("mappings", mappings);
            left.addAll(leftToEcs.left());
        }
        if (composedOf.isEmpty()) {
            out.remove("composed_of");
        } else {
            out.put("composed_of", composedOf);
        }
        if (!include.isEmpty()) {
            notes.add("component templates nominated (by the user or their standing instructions), referenced: "
                      + ToolText.list(new LinkedHashSet<>(include)));
        }
        if (!left.isEmpty()) {
            notes.add("left to the component templates (they map them): "
                      + ToolText.list(left.stream().sorted().toList()));
        }
        return new Built(out, notes);
    }

    // The properties of mappings, as `mappings.get('properties') or {}` gives them (a new map when there are none).
    @SuppressWarnings("unchecked")
    private static Map<String, Object> propertiesOf(final Map<String, Object> mappings) {
        final Object properties = mappings.get("properties");
        return properties instanceof final Map<?, ?> map && !map.isEmpty()
                ? (Map<String, Object>) map
                : new LinkedHashMap<>();
    }

    /// A template's mappings with the ECS fields it maps just as ecs@mappings does taken out, left to the
    /// component, and dynamic mapping on, without which the component's dynamic templates map nothing.
    ///
    /// @param given The mappings.
    /// @param ecs   The Elastic Common Schema.
    /// @return The mappings (a copy) and the fields left to the component, sorted.
    @SuppressWarnings("unchecked")
    public static LeftToEcs leaveToEcsComponent(final Map<String, Object> given, final EcsSchema ecs) {
        final Map<String, Object> mappings = JsonValues.deepCopy(given);
        final boolean flat = Boolean.FALSE.equals(mappings.get("subobjects"));
        final List<String> left = new ArrayList<>();
        TemplateMappings.readMapping(mappings).fields().forEach((path, spec) -> {
            if (!"object".equals(spec.get("type")) && ecs.mappedAsComponent(path, spec)) {
                left.add(path);
            }
        });
        for (final String path : left) {
            final Map<String, Object> properties = (Map<String, Object>) mappings.computeIfAbsent("properties",
                    key -> new LinkedHashMap<String, Object>());
            TemplateMappings.drop(properties, path, flat);
        }
        mappings.put("dynamic", true);
        return new LeftToEcs(mappings, left.stream().sorted().toList());
    }

    /// @param body An index template's body.
    /// @param ecs  The Elastic Common Schema.
    /// @return The fields a template composing ecs@mappings maps explicitly just as the component does (mapped
    /// twice), sorted; none if it doesn't compose it.
    public static List<String> ecsDuplicates(final Map<String, Object> body, final EcsSchema ecs) {
        if (!JsonValues.strings(body.get("composed_of")).contains(EcsSchema.COMPONENT)) {
            return List.of();
        }
        final Set<String> found = new TreeSet<>();
        TemplateMappings.readMappingFields(body).forEach((path, spec) -> {
            if (!"object".equals(spec.get("type")) && ecs.mappedAsComponent(path, spec)) {
                found.add(path);
            }
        });
        return List.copyOf(found);
    }

    /// The example's component templates the new template references, in their order. A component is referenced
    /// only if it maps a field the pipeline writes (asked for by the user); ecs@mappings, if a field is left to it.
    /// Those whose fields can't be weighed are referenced, saying why (unsure): one holding settings alone (a
    /// lifecycle policy, shards), one mapping by dynamic templates only, one the user couldn't give, and every one
    /// for a discovery index, whose fields are only known as documents arrive.
    ///
    /// @param composedOf The components the template may compose.
    /// @param components The component templates given, by name.
    /// @param leaves     The plan's fields by path.
    /// @param toEcs      The fields left to ecs@mappings.
    /// @param discovery  True for a discovery index.
    /// @param nominated  The components nominated (referenced whatever they map).
    /// @return The components used, those not, and those referenced unweighed with why.
    public static ComponentsUsed componentsUsed(final List<String> composedOf,
                                                final Map<String, Map<String, Object>> components,
                                                final Map<String, ?> leaves,
                                                final List<String> toEcs,
                                                final boolean discovery,
                                                final List<String> nominated) {
        final List<String> used = new ArrayList<>();
        final List<String> unused = new ArrayList<>();
        final Map<String, String> unsure = new LinkedHashMap<>();
        for (final String name : composedOf) {
            if (nominated.contains(name)) {
                // The user's, or their standing instructions': referenced whatever it maps.
                used.add(name);
                continue;
            }
            if (discovery) {
                unsure.put(name, "a discovery index: its fields arrive with its documents");
            } else if (EcsSchema.COMPONENT.equals(name)) {
                if (toEcs.isEmpty()) {
                    unused.add(name);
                    continue;
                }
            } else if (!components.containsKey(name)) {
                unsure.put(name, "not given");
            } else {
                final Map<String, Object> mappings = TemplateMappings.mappingsOf(components.get(name));
                final Set<String> fields = new HashSet<>();
                TemplateMappings.readMapping(mappings).fields().forEach((path, spec) -> {
                    if (!"object".equals(spec.get("type"))) {
                        fields.add(path);
                    }
                });
                if (fields.isEmpty()) {
                    unsure.put(name, JsonValues.truthy(mappings.get("dynamic_templates"))
                            ? "dynamic templates only"
                            : "settings only, no fields");
                } else if (fields.stream().noneMatch(leaves::containsKey)) {
                    unused.add(name);
                    continue;
                }
            }
            used.add(name);
        }
        return new ComponentsUsed(used, unused, unsure);
    }

    /// The final index template for the new index, built from the field plan's template and the user's example: the
    /// new index's own pattern; the example's composed_of, settings, priority, data_stream and top-level mapping
    /// parameters; a field a component template defines is left to it, a field the example maps keeps the example's
    /// type, and the rest are typed from the plan. Aliases and _meta name the example's own indices: not copied.
    /// ecsBase (an ECS plan): composed of ecs@mappings whatever the example composes, with dynamic mapping on so it
    /// maps the ECS fields left to it (asked for by the user: an index's mapping never shows composed_of, and
    /// followed as it was, the template mapped every field explicitly, dynamic false).
    ///
    /// @param planned    The plan's template body ([FieldPlan#elasticTemplate], every field mapped).
    /// @param example    The example: an index template's body, or an index's mapping as `{template: {mappings}}`.
    /// @param components The component templates given, by name.
    /// @param discovery  True for a discovery index.
    /// @param include    The components nominated.
    /// @param exclude    The components excluded.
    /// @param ecsBase    True for an ECS plan.
    /// @param ecs        The Elastic Common Schema.
    /// @return The body, and notes saying what was done and what to ask the user.
    public static Built fromExample(final Map<String, Object> planned,
                                    final Map<String, Object> example,
                                    final Map<String, Map<String, Object>> components,
                                    final boolean discovery,
                                    final List<String> include,
                                    final List<String> exclude,
                                    final boolean ecsBase,
                                    final EcsSchema ecs) {
        return new FromExample(planned, example, components, discovery, include, exclude, ecsBase, ecs).build();
    }

    /// A discovery index's template as a data stream when every sample document has its time (@timestamp, from the
    /// source's time field), as event indexes are (asked for by the user); else a plain index, as a data stream
    /// rejects a document without @timestamp.
    ///
    /// @param body  The template's body.
    /// @param docs  The sample documents (values as read, [TemplateCheck.DocValue], or plain).
    /// @param field The source's time field.
    /// @return The body, and a note saying which it is and why.
    public static DataStream discoveryDataStream(final Map<String, Object> body,
                                                 final List<Map<String, Object>> docs,
                                                 final String field) {
        final long missing = docs.stream().filter(doc -> timeOf(doc).isEmpty()).count();
        if (missing > 0 || docs.isEmpty()) {
            return new DataStream(body, "a plain index, not a data stream: " + missing + " of " + docs.size()
                                        + " sample records have no time in " + ToolText.repr(field)
                                        + ", and a data stream rejects a document without @timestamp");
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        body.forEach((key, value) -> {
            if (!"template".equals(key)) {
                out.put(key, value);
            }
        });
        out.put("data_stream", new LinkedHashMap<>());
        out.put("template", JsonValues.mapOrEmpty(body.get("template")));
        return new DataStream(out, "a data stream: all " + docs.size() + " sample records have their time in "
                                   + ToolText.repr(field) + ", written as @timestamp");
    }

    private static String timeOf(final Map<String, Object> doc) {
        Object value = doc.get("@timestamp");
        // A document as read: (kind, value).
        if (value instanceof final TemplateCheck.DocValue read) {
            value = read.text();
        }
        return ToolText.strip(JsonValues.truthy(value)
                ? ToolText.str(value)
                : "");
    }

    // --------------------------------------------------------------------------------


    /// A template built, and what was done.
    ///
    /// @param body  The template's body.
    /// @param notes What was done, for the agent.
    public record Built(Map<String, Object> body, List<String> notes) {

    }

    /// Mappings with the ECS fields left to ecs@mappings.
    ///
    /// @param mappings The mappings, without them and with dynamic mapping on.
    /// @param left     The fields left to the component, sorted.
    public record LeftToEcs(Map<String, Object> mappings, List<String> left) {

    }

    /// The component templates a new template references.
    ///
    /// @param used   Referenced, in order.
    /// @param unused Not referenced: none of the fields they map is written.
    /// @param unsure Referenced though their fields can't be weighed, with why.
    public record ComponentsUsed(List<String> used, List<String> unused, Map<String, String> unsure) {

    }

    /// A discovery index's template, a data stream or not.
    ///
    /// @param body The template's body.
    /// @param note Which it is, and why.
    public record DataStream(Map<String, Object> body, String note) {

    }
}
