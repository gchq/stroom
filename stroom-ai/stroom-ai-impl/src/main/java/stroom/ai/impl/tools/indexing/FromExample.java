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

import stroom.ai.impl.tools.generation.PythonRepr;
import stroom.ai.impl.tools.indexing.ExampleTemplates.Built;
import stroom.ai.impl.tools.indexing.ExampleTemplates.ComponentsUsed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// One template built from the user's example ([ExampleTemplates#fromExample]), step by step as the Stroom MCP
/// server's `from_example` builds it.
final class FromExample {

    private static final List<String> MAPPING_PARAMS = List.of("dynamic", "dynamic_templates", "date_detection",
            "numeric_detection", "subobjects", "_source", "_routing");

    private final Map<String, Object> planned;
    private final Map<String, Object> example;
    private final Map<String, Map<String, Object>> components;
    private final boolean discovery;
    private final List<String> include;
    private final List<String> exclude;
    private final boolean ecsBase;
    private final EcsSchema ecs;
    private final List<String> notes = new ArrayList<>();
    private final Map<String, Map<String, Object>> fields = new LinkedHashMap<>();
    private final List<String> left = new ArrayList<>();
    private final List<String> kept = new ArrayList<>();
    private final List<String> added = new ArrayList<>();
    private final List<String> styled = new ArrayList<>();
    private final List<String> aliased = new ArrayList<>();
    private final List<String> toEcs = new ArrayList<>();

    /// @param planned    The plan's template body.
    /// @param example    The example template's body, or an index's mapping as `{template: {mappings}}`.
    /// @param components The component templates given, by name.
    /// @param discovery  True for a discovery index.
    /// @param include    The components nominated.
    /// @param exclude    The components excluded.
    /// @param ecsBase    True for an ECS plan.
    /// @param ecs        The Elastic Common Schema.
    FromExample(final Map<String, Object> planned,
                final Map<String, Object> example,
                final Map<String, Map<String, Object>> components,
                final boolean discovery,
                final List<String> include,
                final List<String> exclude,
                final boolean ecsBase,
                final EcsSchema ecs) {
        this.planned = planned;
        this.example = example;
        this.components = components;
        this.discovery = discovery;
        this.include = include;
        this.exclude = exclude;
        this.ecsBase = ecsBase && !discovery && !exclude.contains(EcsSchema.COMPONENT);
        this.ecs = ecs;
    }

    /// @return The template and the notes.
    Built build() {
        final Map<String, Object> own = TemplateMappings.mappingsOf(example);
        final List<String> composedOf = ExampleTemplates.composition(PyValues.strings(example.get("composed_of")),
                include, exclude);
        if (ecsBase && !composedOf.contains(EcsSchema.COMPONENT)) {
            // First, so the example's own components and the template's mappings override it.
            composedOf.addFirst(EcsSchema.COMPONENT);
            notes.add("composed of " + EcsSchema.COMPONENT + ", Elastic's base for an ECS index template (built in "
                      + "from Elasticsearch 8.13), though the example doesn't compose it: it maps the standard ECS "
                      + "fields");
        }
        final Map<String, Map<String, Object>> fromComponents = new LinkedHashMap<>();
        for (final String name : composedOf) {
            if (components.containsKey(name)) {
                fromComponents.putAll(TemplateMappings.readMappingFields(components.get(name)));
            }
        }
        // ecs@mappings is built in: known here.
        final List<String> missing = composedOf.stream()
                .filter(n -> !components.containsKey(n) && !EcsSchema.COMPONENT.equals(n))
                .toList();
        final Map<String, Map<String, Object>> inExample = TemplateMappings.readMapping(own).fields();
        // The plan's values: not its object nodes (with subobjects: false, time and time.min are both values).
        final Map<String, Map<String, Object>> leaves = new LinkedHashMap<>();
        TemplateMappings.readMappingFields(planned).forEach((path, spec) -> {
            if (spec.get("type") != null && !"object".equals(spec.get("type"))) {
                leaves.put(path, spec);
            }
        });
        final Map<String, Map<String, Object>> every = new LinkedHashMap<>(fromComponents);
        every.putAll(inExample);
        // A discovery index keeps the source's names and Elasticsearch's dynamic mapping: the example gives its
        // settings and components, not its field conventions.
        final Map<String, Map<String, Object>> styles = discovery
                ? Map.of()
                : TemplateMappings.typeStyles(every);
        final Map<String, Map<String, Object>> objects = new LinkedHashMap<>();
        for (final String name : composedOf) {
            if (components.containsKey(name)) {
                objects.putAll(TemplateMappings.objectNodes(TemplateMappings.mappingsOf(components.get(name))));
            }
        }
        objects.putAll(TemplateMappings.objectNodes(own));
        final boolean typed = !objects.isEmpty()
                              && objects.values().stream().filter(o -> o.containsKey("type")).count() * 2
                                 > objects.size();
        // The example composes Elastic's ECS component, mapping as documents bring them: the ECS fields it maps as
        // ECS says are left to it, as an ECS plan's own template leaves them (asked for by the user).
        final boolean dynamicOn = "true".equals(PyValues.lower(PyValues.str(own.getOrDefault("dynamic", true))));
        // Nominated too (seen in testing: asked to use ecs@mappings, the template composed it, kept the example's
        // dynamic false and mapped every field explicitly, so the component mapped nothing).
        final boolean byComponent = composedOf.contains(EcsSchema.COMPONENT)
                                    && (dynamicOn || ecsBase || include.contains(EcsSchema.COMPONENT));
        leaves.forEach((path, spec) -> place(path, spec, fromComponents, inExample, styles, byComponent));
        final List<String> named = every.entrySet().stream()
                .filter(entry -> !"object".equals(entry.getValue().get("type")))
                .map(Map.Entry::getKey)
                .toList();
        final String style = discovery
                ? null
                : ExampleNames.exampleNaming(named);
        final List<String> unlike = style == null
                ? List.of()
                : leaves.keySet().stream()
                        .filter(p -> !every.containsKey(p) && !(ExampleNames.fitsStyle(p, style)
                                                                && (ExampleNames.exampleDotted(named)
                                                                    || !p.contains("."))))
                        .toList();
        final Map<String, Object> mappings = mappings(own, objects, typed, byComponent, dynamicOn);
        final Map<String, Object> template = new LinkedHashMap<>();
        final Map<String, Object> settings = settings();
        if (settings != null) {
            template.put("settings", settings);
        }
        template.put("mappings", mappings);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("index_patterns", planned.get("index_patterns"));
        final Object priority = example.containsKey("priority")
                ? example.get("priority")
                : planned.get("priority");
        if (priority != null) {
            body.put("priority", priority);
        }
        final List<String> nominated = new ArrayList<>(include);
        if (ecsBase) {
            nominated.add(EcsSchema.COMPONENT);
        }
        final ComponentsUsed used = ExampleTemplates.componentsUsed(composedOf, components, leaves, toEcs, discovery,
                nominated);
        if (!used.used().isEmpty()) {
            body.put("composed_of", used.used());
        }
        dataStream(body, own);
        body.put("template", template);
        notes(used, missing, inExample, leaves, unlike, style, typed);
        return new Built(body, notes);
    }

    // Where a field of the plan goes: left to a component, typed as the example types it (or its alias's target),
    // in the example's style for its type, or typed from the plan.
    private void place(final String path,
                       final Map<String, Object> spec,
                       final Map<String, Map<String, Object>> fromComponents,
                       final Map<String, Map<String, Object>> inExample,
                       final Map<String, Map<String, Object>> styles,
                       final boolean byComponent) {
        final String type = spec.get("type") instanceof final String text
                ? text
                : "";
        final Map<String, Object> given = inExample.get(path);
        if (fromComponents.containsKey(path)
            || (byComponent && given == null && ecs.leftToComponent(path, type))) {
            left.add(path);
            if (!fromComponents.containsKey(path)) {
                toEcs.add(path);
            }
        } else if (given != null && "alias".equals(given.get("type"))) {
            // Seen in VS Code: the example's User.Id was an alias of User.Name. Copied, the new index had an alias
            // of a field it lacks (Elasticsearch refused the template), and documents can't write to an alias
            // anyway: the pipeline writes this field, so it is a field here, typed as the example types the
            // alias's target.
            final Object targetPath = given.get("path");
            final Map<String, Object> target = PyValues.mapOrEmpty(inExample.get(PyValues.truthy(targetPath)
                    ? PyValues.str(targetPath)
                    : ""));
            final Object targetType = target.get("type");
            fields.put(path, targetType != null && !"alias".equals(targetType) && !"object".equals(targetType)
                    ? PyValues.deepCopy(target)
                    : spec);
            aliased.add(path);
        } else if (byComponent && given != null && sameAsComponent(path, given) && ecs.leftToComponent(path, type)) {
            // The example maps an ECS field just as ecs@mappings does: left to the component, not copied (asked
            // for by the user: an amended indexing pipeline's template mapped the ECS base fields again,
            // explicitly).
            left.add(path);
            toEcs.add(path);
        } else if (given != null) {
            fields.put(path, given);
            kept.add(path);
        } else if (styles.containsKey(PyValues.str(spec.get("type")))
                   && !styles.get(PyValues.str(spec.get("type"))).equals(Map.of("type", spec.get("type")))) {
            // A field new to the examples, mapped the way they map its type.
            fields.put(path, PyValues.deepCopy(styles.get(PyValues.str(spec.get("type")))));
            styled.add(path);
        } else {
            fields.put(path, spec);
            added.add(path);
        }
    }

    // The example's mapping is ecs@mappings' own for the field: its type, and for a keyword ignore_above 1024.
    private boolean sameAsComponent(final String path, final Map<String, Object> given) {
        final String component = ecs.componentType(path);
        return (component == null
                ? given.get("type") == null
                : component.equals(given.get("type")))
               && Set.of("type", "ignore_above").containsAll(given.keySet())
               && PyValues.equalsNumber(given.getOrDefault("ignore_above", 1024L), 1024);
    }

    // The example's settings, with the plan's own (a discovery index's guardrails) over them; null for none.
    private Map<String, Object> settings() {
        final Object settings = PyValues.mapAt(example, "template").get("settings");
        final Object plannedSettings = PyValues.mapAt(planned, "template").get("settings");
        if (PyValues.truthy(settings)) {
            final Object keys = settings instanceof final Map<?, ?> map && map.containsKey("index")
                    ? map.get("index")
                    : settings;
            notes.add("settings from the example: " + PythonRepr.list(sortedKeys(keys)));
        }
        if (!PyValues.truthy(settings) && !PyValues.truthy(plannedSettings)) {
            return null;
        }
        return TemplateMappings.merge(PyValues.deepCopy(PyValues.mapOrEmpty(settings)),
                PyValues.mapOrEmpty(plannedSettings));
    }

    // sorted(x): a map's keys, a text's characters, a list's items.
    private static List<String> sortedKeys(final Object value) {
        final List<String> out = new ArrayList<>();
        if (value instanceof final Map<?, ?> map) {
            map.keySet().forEach(key -> out.add(String.valueOf(key)));
        } else if (value instanceof final String text) {
            text.codePoints().forEach(cp -> out.add(Character.toString(cp)));
        } else {
            out.addAll(PyValues.strings(value));
        }
        out.sort(null);
        return out;
    }

    private Map<String, Object> mappings(final Map<String, Object> own,
                                         final Map<String, Map<String, Object>> objects,
                                         final boolean typed,
                                         final boolean byComponent,
                                         final boolean dynamicOn) {
        final Map<String, Object> plannedMappings = TemplateMappings.mappingsOf(planned);
        final Map<String, Object> mappings = new LinkedHashMap<>();
        if (discovery) {
            plannedMappings.forEach((key, value) -> {
                if (!"properties".equals(key)) {
                    mappings.put(key, PyValues.deepCopy(value));
                }
            });
            for (final String key : List.of("_source", "_routing")) {
                if (own.containsKey(key)) {
                    mappings.put(key, PyValues.deepCopy(own.get(key)));
                }
            }
        } else {
            for (final String key : MAPPING_PARAMS) {
                if (own.containsKey(key)) {
                    mappings.put(key, PyValues.deepCopy(own.get(key)));
                }
            }
            if (!mappings.containsKey("dynamic")) {
                mappings.put("dynamic", byComponent
                        ? Boolean.TRUE
                        : plannedMappings.getOrDefault("dynamic", false));
            }
            if (byComponent && !dynamicOn) {
                // ecs@mappings is dynamic templates: with dynamic false or strict it maps nothing.
                mappings.put("dynamic", true);
                notes.add("dynamic mapping on, though the example's is " + PyValues.str(own.get("dynamic")) + ": "
                          + EcsSchema.COMPONENT + " maps a field only as a document brings it");
            }
        }
        // subobjects: false keeps each dotted name a field of its own (time beside time.min): no objects to nest.
        if (Boolean.FALSE.equals(mappings.get("subobjects"))) {
            final Map<String, Object> properties = new LinkedHashMap<>();
            fields.forEach((path, spec) -> properties.put(path, PyValues.deepCopy(spec)));
            mappings.put("properties", properties);
        } else {
            mappings.put("properties", TemplateMappings.nest(fields, objects, typed));
        }
        return mappings;
    }

    // A data stream, as the example is, or as its index is (a data stream's backing index).
    private void dataStream(final Map<String, Object> body, final Map<String, Object> own) {
        if (example.containsKey("data_stream")) {
            body.put("data_stream", PyValues.deepCopy(example.get("data_stream")));
            notes.add("a data stream, as the example is");
        } else if ("true".equals(PyValues.lower(PyValues.str(PyValues.mapAt(own, "_data_stream_timestamp")
                .getOrDefault("enabled", "")))) && !discovery) {
            // The example is a data stream's backing index (GET <data stream>/_mapping): its mapping says so.
            body.put("data_stream", new LinkedHashMap<>());
            notes.add("a data stream, as the example's index is (its mapping has _data_stream_timestamp)");
        }
    }

    private void notes(final ComponentsUsed used,
                       final List<String> missing,
                       final Map<String, Map<String, Object>> inExample,
                       final Map<String, Map<String, Object>> leaves,
                       final List<String> unlike,
                       final String style,
                       final boolean typed) {
        if (!left.isEmpty()) {
            notes.add("left to the component templates (they map them): " + PythonRepr.list(left));
        }
        if (!used.unused().isEmpty()) {
            notes.add("component templates of the example not referenced: none of the fields they map is written "
                      + "by this pipeline: " + PythonRepr.list(used.unused()));
        }
        if (!include.isEmpty()) {
            notes.add("component templates nominated (by the user or their standing instructions), referenced: "
                      + PythonRepr.list(new LinkedHashSet<>(include)));
        }
        final List<String> dropped = PyValues.strings(example.get("composed_of")).stream()
                .filter(exclude::contains)
                .toList();
        if (!dropped.isEmpty()) {
            notes.add("component templates of the example excluded by standing instructions: "
                      + PythonRepr.list(dropped));
        }
        if (!used.unsure().isEmpty()) {
            final List<String> said = new ArrayList<>();
            used.unsure().forEach((name, why) -> said.add(name + " (" + why + ")"));
            notes.add("component templates referenced though their fields can't be weighed: "
                      + String.join("; ", said));
        }
        if (!kept.isEmpty()) {
            notes.add("typed as in the example: " + PythonRepr.list(kept));
        }
        if (!aliased.isEmpty()) {
            notes.add("aliases in the example, but written by this pipeline, so fields here (typed as each alias's "
                      + "target): " + PythonRepr.list(aliased));
        }
        if (!styled.isEmpty()) {
            notes.add("new to this index, mapped in the example's style for their type: " + PythonRepr.list(styled));
        }
        if (!added.isEmpty()) {
            notes.add("new to this index, typed from the field plan: " + PythonRepr.list(added));
        }
        if (typed) {
            notes.add("objects declared \"type\": \"object\", as the example declares them");
        }
        final List<String> leftOut = inExample.entrySet().stream()
                .filter(entry -> !leaves.containsKey(entry.getKey())
                                 && !"object".equals(entry.getValue().get("type"))
                                 && inExample.keySet().stream().noneMatch(o -> o.startsWith(entry.getKey() + ".")))
                .map(Map.Entry::getKey)
                .toList();
        if (discovery) {
            notes.add("a discovery index: the source's fields are mapped dynamically as documents arrive (strings "
                      + "as keywords); the example's own fields and mapping rules are not copied");
        } else if (!leftOut.isEmpty()) {
            notes.add("in the example but not written by this pipeline, so not in this template: "
                      + PythonRepr.list(leftOut.subList(0, Math.min(15, leftOut.size())))
                      + (leftOut.size() > 15
                    ? " (+" + (leftOut.size() - 15) + ")"
                    : ""));
        }
        if (!unlike.isEmpty()) {
            notes.add("named unlike the example (" + ExampleNames.STYLE_NAMES.get(style) + "): "
                      + PythonRepr.list(unlike) + ". The indexing XSLT writes the plan's names: draft_index_mapping "
                      + "again with example_template (and the component templates), so the plan takes the "
                      + "example's names, save the XSLT from it, step, and build this again");
        }
        if (!missing.isEmpty()) {
            notes.add("composed_of " + PythonRepr.list(missing) + " not given: fields they map may be repeated here "
                      + "with the plan's types; ask the user for them (GET _component_template/<name>) and build "
                      + "again");
        }
        if (PyValues.truthy(PyValues.mapAt(example, "template").get("aliases"))) {
            notes.add("the example's aliases are not copied: they name its own indices");
        }
    }
}
