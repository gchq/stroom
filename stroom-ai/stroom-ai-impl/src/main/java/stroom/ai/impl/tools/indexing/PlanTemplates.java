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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// A field plan's Elasticsearch index template ([FieldPlan#elasticTemplate]), as the Stroom MCP server's
/// `FieldPlan.elastic_template` builds it.
final class PlanTemplates {

    private PlanTemplates() {
        // Static only.
    }

    /// @param plan             The plan.
    /// @param templateName     The template's name.
    /// @param priority         Its priority, or null.
    /// @param leaveToComponent False to map every field here.
    /// @param ecs              The Elastic Common Schema, or null.
    /// @return `{name, body}`.
    static Map<String, Object> elasticTemplate(final FieldPlan plan,
                                               final String templateName,
                                               final Long priority,
                                               final boolean leaveToComponent,
                                               final EcsSchema ecs) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", templateName);
        if (plan.discovery() != null) {
            out.put("body", discoveryTemplate(plan, priority));
            return out;
        }
        final boolean component = "ecs".equals(plan.convention()) && leaveToComponent;
        final Map<String, Object> properties = new LinkedHashMap<>();
        // A field of objects is mapped as its items: users.login under users, an object array's own mapping.
        for (final PlannedField field : plan.leafFields()) {
            if (component && ecs != null && ecs.leftToComponent(field.name(), field.type())) {
                continue;
            }
            if (!plan.subobjects()) {
                // Each dotted name is a field of its own.
                properties.put(field.name(), typed(field));
                continue;
            }
            put(properties, field.name(), typed(field));
        }
        // ecs@mappings is dynamic templates: they map a field only as a document brings it, so dynamic mapping is
        // on. With it off, as before, the component mapped nothing, and every field had to be mapped here.
        final Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put("dynamic", component);
        if (!plan.subobjects()) {
            mappings.put("subobjects", false);
        }
        mappings.put("properties", properties);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("index_patterns", List.of(plan.indexName() + "*"));
        if (priority != null) {
            body.put("priority", priority);
        }
        if ("ecs".equals(plan.convention())) {
            // Elastic's recommended base for an ECS index template: its built-in component template, mapping ECS
            // fields by name. A field mapped here (not ECS's, or typed otherwise) overrides it.
            body.put("composed_of", List.of(FieldPlans.ECS_COMPONENT));
        }
        // Events are time series, written once: a data stream, as the user's indices generally are (asked for by
        // the user: a plan-only ECS template came out a plain index). Every Event has @timestamp, which a data
        // stream needs (validate requires it). A template built from the user's example follows the example
        // instead. A discovery index is decided from its documents (propose_index_template): a data stream when
        // every record has its time.
        body.put("data_stream", new LinkedHashMap<>());
        body.put("template", new LinkedHashMap<>(Map.of("mappings", mappings)));
        out.put("body", body);
        return out;
    }

    // A dotted name put as nested properties: user.name -> {user: {properties: {name: ...}}}.
    @SuppressWarnings("unchecked")
    private static void put(final Map<String, Object> properties, final String name, final Map<String, Object> spec) {
        Map<String, Object> node = properties;
        final String[] parts = name.split("\\.", -1);
        for (int i = 0; i < parts.length - 1; i++) {
            final Map<String, Object> object = (Map<String, Object>) node.computeIfAbsent(parts[i], key -> {
                final Map<String, Object> made = new LinkedHashMap<>();
                made.put("properties", new LinkedHashMap<String, Object>());
                return made;
            });
            node = (Map<String, Object>) object.get("properties");
        }
        node.put(parts[parts.length - 1], spec);
    }

    private static Map<String, Object> typed(final PlannedField field) {
        final Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("type", FieldPlans.ELASTIC.get(field.type()));
        return spec;
    }

    // Permissive: dynamic mapping, strings as keywords, guardrails; explicit types only for the fields Stroom
    // needs (StreamId and EventId to find each record again, @timestamp for time).
    private static Map<String, Object> discoveryTemplate(final FieldPlan plan, final Long priority) {
        final Discovery discovery = plan.discovery();
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("index_patterns", List.of(plan.indexName() + "*"));
        if (priority != null) {
            body.put("priority", priority);
        }
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("total_fields", new LinkedHashMap<>(Map.of("limit", discovery.totalFieldsLimit())));
        mapping.put("ignore_malformed", true);
        final Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("index", new LinkedHashMap<>(Map.of("mapping", mapping)));
        final Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put("dynamic", true);
        mappings.put("date_detection", false);
        if (!"json".equals(discovery.input())) {
            // Text formats hold every value as text: numbers are recognised (a stray "n/a" is ignored, not fatal).
            mappings.put("numeric_detection", true);
        }
        final Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "keyword");
        keyword.put("ignore_above", discovery.ignoreAbove());
        final Map<String, Object> strings = new LinkedHashMap<>();
        strings.put("match_mapping_type", "string");
        strings.put("mapping", keyword);
        mappings.put("dynamic_templates", List.of(new LinkedHashMap<>(Map.of("strings_as_keywords", strings))));
        final Map<String, Object> properties = new LinkedHashMap<>();
        plan.fields().forEach(field -> properties.put(field.name(), typed(field)));
        mappings.put("properties", properties);
        final Map<String, Object> template = new LinkedHashMap<>();
        template.put("settings", settings);
        template.put("mappings", mappings);
        body.put("template", template);
        return body;
    }
}
