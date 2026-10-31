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

import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.ModelReader;
import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.generation.XsltStyle;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/// A backend-neutral field plan for an index, rendered for Lucene or Elasticsearch. Each field has a name (as the
/// index will store it), a logical type, and the XPath in the event it comes from. The same plan becomes a Lucene
/// index doc's field list ([#luceneFields()]) or an Elasticsearch index template ([#elasticTemplate]), plus a draft
/// indexing XSLT ([#xslt(String)]) in the output form each backend's indexing filter reads.
///
/// A discovery plan instead indexes raw structured data (JSON, delimited text or XML) as it is, into
/// Elasticsearch: every record is copied with its own field names and Elasticsearch maps them dynamically, so
/// nothing is surveyed up front. Only StreamId, EventId and @timestamp are mapped explicitly.
///
/// Ported from the Stroom MCP server's `utils/fieldplan.py` `FieldPlan` model. Its JSON ([#toJson()], which Jackson
/// writes too) is the server's, field for field, as plans are kept in documents' descriptions; read one with
/// [#fromJson(Map)] (as Jackson does), which validates it as pydantic does, with pydantic's messages.
///
/// @param backend          `lucene` or `elasticsearch`.
/// @param indexName        `index_name`: Lucene index doc name, or Elasticsearch index / data stream name.
/// @param timeField        `time_field`: name of the field holding the event time.
/// @param fields           The planned fields.
/// @param dropWhen         `drop_when`: XPath tests on an Event (event-logging:3 is the default namespace) for events
///                         the index must not hold, e.g. `EventDetail/TypeId = 'Heartbeat'`; any that holds drops the
///                         event.
/// @param discovery        Set for a discovery index (draft_index_mapping), else null.
/// @param imports          Shared XSLTs imported (xsl:import) with no call, e.g. those an AGENTS doc's Required
///                         imports name.
/// @param shared           Named templates from shared XSLTs (xsl:import) the environment's indexing XSLTs call, each
///                         writing a field (at: its name, e.g. `guid`); the XSLT calls them instead of writing those
///                         fields itself.
/// @param subobjects       Elasticsearch: the index template's subobjects setting, from the user's example (true by
///                         default). Documents are written nested either way (user.id, user.name -> "user": {"id",
///                         "name"}). With false, the template maps each dotted name as a field of its own, and a value
///                         may sit beside its dotted names (time, time.min).
/// @param convention       The naming convention the plan follows (draft_index_mapping's), e.g. `ecs`: its names and
///                         types are checked against it. Null for none.
/// @param customNamespaces `custom_namespaces`: ECS: the top-level names the plan's own fields (those ECS has no
///                         field for) go under, named after the source or organisation, e.g. `['secretserver']` for
///                         secretserver.ticket; any other top-level name that isn't ECS's is reported.
/// @param style            How the indexing XSLT is written, as an Events translation's is (naming, variables,
///                         layout): from an XSLT style section in the standing instructions (AGENTS docs); otherwise
///                         the defaults.
/// @param standIns         `stand_ins`: values the Events hold for none (e.g. `-`), each with what the user chose to
///                         index it as: left out, or null (Elasticsearch), or kept as written (see
///                         [Discovery#STAND_IN_TREATMENTS]). Not in the Stroom MCP server; asked for by the user.
public record FieldPlan(String backend,
                        String indexName,
                        String timeField,
                        List<PlannedField> fields,
                        List<String> dropWhen,
                        Discovery discovery,
                        List<String> imports,
                        List<SharedTemplate> shared,
                        boolean subobjects,
                        String convention,
                        List<String> customNamespaces,
                        XsltStyle style,
                        Map<String, String> standIns) {

    /// The Elasticsearch backend.
    public static final String ELASTICSEARCH = "elasticsearch";
    /// The Lucene backend.
    public static final String LUCENE = "lucene";

    /// Fills in the defaults for nulls; and a discovery plan's fields are always StreamId, EventId and @timestamp
    /// from its time field (seen: an agent passing the plan back with no fields, and the index template then
    /// mapping @timestamp as a keyword).
    public FieldPlan {
        fields = List.copyOf(Objects.requireNonNullElse(fields, List.of()));
        dropWhen = List.copyOf(Objects.requireNonNullElse(dropWhen, List.of()));
        imports = List.copyOf(Objects.requireNonNullElse(imports, List.of()));
        shared = List.copyOf(Objects.requireNonNullElse(shared, List.of()));
        customNamespaces = List.copyOf(Objects.requireNonNullElse(customNamespaces, List.of()));
        style = Objects.requireNonNullElse(style, XsltStyle.DEFAULT);
        standIns = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNullElse(standIns, Map.of())));
        if (discovery != null) {
            fields = discoveryFields(discovery);
            timeField = "@timestamp";
        }
    }

    /// @param backend   `lucene` or `elasticsearch`.
    /// @param indexName The index name.
    /// @param timeField The time field's name.
    /// @param fields    The planned fields.
    /// @return A plan with the other settings at their defaults.
    public static FieldPlan of(final String backend,
                               final String indexName,
                               final String timeField,
                               final List<PlannedField> fields) {
        return new FieldPlan(backend, indexName, timeField, fields, List.of(), null, List.of(), List.of(), true, null,
                List.of(), XsltStyle.DEFAULT, Map.of());
    }

    /// @param indexName The discovery index's name.
    /// @param discovery How its raw records are read.
    /// @return The discovery plan: Elasticsearch, StreamId, EventId and @timestamp.
    public static FieldPlan forDiscovery(final String indexName, final Discovery discovery) {
        return new FieldPlan(ELASTICSEARCH, indexName, "@timestamp", discoveryFields(discovery), List.of(), discovery,
                List.of(), List.of(), true, null, List.of(), XsltStyle.DEFAULT, Map.of());
    }

    /// @param json A plan as JSON-like values, e.g. as kept in an XSLT doc's description.
    /// @return It validated, as the Python model validates it.
    /// @throws stroom.ai.impl.tools.generation.MappingValidationException if it doesn't validate, with every error
    /// pydantic would give.
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static FieldPlan fromJson(final Map<String, Object> json) {
        return parse(json);
    }

    /// @param json A plan as JSON-like values, which may be anything (an error says what it should be).
    /// @return It validated, as the Python model validates it.
    /// @throws stroom.ai.impl.tools.generation.MappingValidationException if it doesn't validate.
    public static FieldPlan parse(final Object json) {
        final ModelReader reader = new ModelReader();
        return reader.orThrow("FieldPlan", read(reader, json, List.of()));
    }

    /// Reads one from JSON-like values, as pydantic validates it in the Stroom MCP server.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static FieldPlan read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "FieldPlan");
        if (fields == null) {
            return null;
        }
        final FieldPlan plan = new FieldPlan(
                fields.requiredLiteral("backend", FieldPlans.BACKENDS),
                fields.requiredString("index_name"),
                fields.requiredString("time_field"),
                fields.requiredModels("fields", (value, at) -> PlannedField.read(reader, value, at), 0),
                fields.strings("drop_when"),
                fields.optionalModel("discovery", (value, at) -> Discovery.read(reader, value, at)),
                fields.strings("imports"),
                fields.models("shared", (value, at) -> SharedTemplate.read(reader, value, at)),
                fields.bool("subobjects", true),
                fields.optionalString("convention"),
                fields.strings("custom_namespaces"),
                fields.model("style", (value, at) -> XsltStyle.read(reader, value, at), XsltStyle.DEFAULT),
                Discovery.standIns(reader, fields.stringMap("stand_ins"), loc));
        return fields.ok()
                ? plan
                : null;
    }

    /// @return It as JSON, as the Python model's `model_dump` writes it (every field).
    @JsonValue
    public Map<String, Object> toJson() {
        final Map<String, Object> json = new LinkedHashMap<>();
        json.put("backend", backend);
        json.put("index_name", indexName);
        json.put("time_field", timeField);
        json.put("fields", fields.stream().map(PlannedField::toJson).toList());
        json.put("drop_when", new ArrayList<>(dropWhen));
        json.put("discovery", discovery == null
                ? null
                : discovery.toJson());
        json.put("imports", new ArrayList<>(imports));
        json.put("shared", shared.stream().map(template -> template.toJson(DumpMode.ALL)).toList());
        json.put("subobjects", subobjects);
        json.put("convention", convention);
        json.put("custom_namespaces", new ArrayList<>(customNamespaces));
        json.put("style", style.toJson(DumpMode.ALL));
        // Only when there are any, so a plan without them is written as the Python model writes it.
        if (!standIns.isEmpty()) {
            json.put("stand_ins", new LinkedHashMap<>(standIns));
        }
        return json;
    }

    /// @param fields Other fields.
    /// @return This plan with them.
    public FieldPlan withFields(final List<PlannedField> fields) {
        return new FieldPlan(backend, indexName, timeField, fields, dropWhen, discovery, imports, shared, subobjects,
                convention, customNamespaces, style, standIns);
    }

    /// @param indexName Another index name.
    /// @return This plan for that index.
    public FieldPlan withIndexName(final String indexName) {
        return new FieldPlan(backend, indexName, timeField, fields, dropWhen, discovery, imports, shared, subobjects,
                convention, customNamespaces, style, standIns);
    }

    /// @param convention       The convention the plan follows, or null.
    /// @param customNamespaces Its custom namespaces.
    /// @return This plan following it.
    public FieldPlan withConvention(final String convention, final List<String> customNamespaces) {
        return new FieldPlan(backend, indexName, timeField, fields, dropWhen, discovery, imports, shared, subobjects,
                convention, customNamespaces, style, standIns);
    }

    /// @param style How the indexing XSLT is written.
    /// @return This plan written so.
    public FieldPlan withStyle(final XsltStyle style) {
        return new FieldPlan(backend, indexName, timeField, fields, dropWhen, discovery, imports, shared, subobjects,
                convention, customNamespaces, style, standIns);
    }

    /// @param chosen What the user chose for stand-ins for none, by value; added to (and replacing) those it has.
    /// @return This plan with them.
    public FieldPlan withStandIns(final Map<String, String> chosen) {
        final Map<String, String> all = new LinkedHashMap<>(standIns);
        all.putAll(chosen);
        return new FieldPlan(backend, indexName, timeField, fields, dropWhen, discovery, imports, shared, subobjects,
                convention, customNamespaces, style, all);
    }

    /// @param treatment One of [Discovery#STAND_IN_TREATMENTS].
    /// @return The stand-ins the user chose to index so, in the order given.
    public List<String> standIns(final String treatment) {
        return standIns.entrySet().stream()
                .filter(entry -> treatment.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /// Every field as the index holds it: a field of objects as its items, each a field of its own under its name
    /// ([PlannedField#leaves()]), as the index template maps them and searches read them.
    ///
    /// @return The fields.
    public List<PlannedField> leafFields() {
        return fields.stream().flatMap(field -> field.leaves().stream()).toList();
    }

    /// @return True for an Elasticsearch plan.
    public boolean isElasticsearch() {
        return ELASTICSEARCH.equals(backend);
    }

    /// Where the plan departs from its convention: for ECS, names in its field sets it doesn't define, types that
    /// don't map to its own, and top-level names neither ECS's nor the plan's custom namespaces.
    ///
    /// @param ecs The Elastic Common Schema.
    /// @return The departures, none for a plan following no convention (or one other than ECS).
    public List<String> conventionProblems(final EcsSchema ecs) {
        if (!"ecs".equals(convention)) {
            return List.of();
        }
        return ecs.problems(leafFields(), customNamespaces);
    }

    /// @return The apply-templates select: every Event, less the ones drop_when names.
    public String events() {
        if (dropWhen.isEmpty()) {
            return "Event";
        }
        final String tests = String.join(" or ", dropWhen.stream().map(test -> "(" + test + ")").toList());
        return ("Event[not(" + tests + ")]").replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
    }

    /// @param name A field's name.
    /// @return The shared template that writes this field (or the object holding it), or null if none does.
    public SharedTemplate writtenBy(final String name) {
        for (final SharedTemplate use : shared) {
            if (name.equals(use.at()) || name.startsWith(use.at() + ".")) {
                return use;
            }
        }
        return null;
    }

    /// @return The xsl:import elements for the shared templates' documents and the imports, each once.
    String importLines() {
        final Set<String> hrefs = new LinkedHashSet<>();
        shared.forEach(use -> hrefs.add(use.href()));
        hrefs.addAll(imports);
        final StringBuilder sb = new StringBuilder();
        hrefs.forEach(href -> sb.append("  <xsl:import href=\"").append(href).append("\" />\n"));
        return sb.toString();
    }

    /// @return Problems that would stop indexing or verification from working.
    public List<String> required() {
        final Set<String> names = new LinkedHashSet<>();
        fields.forEach(field -> names.add(field.name()));
        final Set<String> planned = Set.copyOf(names);
        for (final SharedTemplate use : shared) {
            if (planned.stream().noneMatch(n -> n.equals(use.at()) || n.startsWith(use.at() + "."))) {
                names.add(use.at());
            }
        }
        final List<String> problems = new ArrayList<>();
        for (final String name : List.of("StreamId", "EventId")) {
            if (!names.contains(name)) {
                problems.add("Missing required field '" + name + "'");
            }
        }
        if (!names.contains(timeField)) {
            problems.add("The time field '" + timeField + "' is not in the plan");
        }
        if (isElasticsearch() && !names.contains("@timestamp")) {
            problems.add("Elasticsearch data streams need '@timestamp'");
        }
        final List<String> objects = fields.stream().filter(PlannedField::isObjects).map(PlannedField::name).toList();
        if (!isElasticsearch() && !objects.isEmpty()) {
            problems.add(PyText.list(objects) + ": a Lucene index holds no objects; plan a field of values for each "
                         + "item instead (e.g. user_login from EventSource/User/Id | EventDetail//User/Id), which "
                         + "is several values in one event");
        }
        if (isElasticsearch()) {
            final List<String> hidden = new TreeSet<>(names).stream()
                    .filter(n -> List.of(n.split("\\.", -1)).stream().anyMatch(part -> part.startsWith("_")))
                    .toList();
            if (!hidden.isEmpty()) {
                problems.add(PyText.list(hidden) + ": Stroom's Elasticsearch indexing filter drops fields whose "
                             + "names start with _ (silently); rename them");
            }
            // Nested names make 'time' an object if 'time.min' exists; with subobjects: false (flat names) both
            // are fields of their own.
            if (subobjects) {
                for (final String name : new TreeSet<>(names)) {
                    final List<String> inner = new TreeSet<>(names).stream()
                            .filter(n -> n.startsWith(name + "."))
                            .toList();
                    if (!inner.isEmpty()) {
                        problems.add("'" + name + "' is a value and also the object holding "
                                     + PyText.list(inner) + ": an Elasticsearch field cannot be both; rename one");
                    }
                }
            }
        }
        return problems;
    }

    /// @return The plan as a Lucene index doc's fields: `{fldName, fldType, analyzerType, indexed, stored,
    /// caseSensitive}` each.
    public List<Map<String, Object>> luceneFields() {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final PlannedField field : fields) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("fldName", field.name());
            entry.put("fldType", FieldPlans.LUCENE.get(field.type()).get(0));
            entry.put("analyzerType", FieldPlans.LUCENE.get(field.type()).get(1));
            entry.put("indexed", true);
            entry.put("stored", true);
            entry.put("caseSensitive", false);
            out.add(entry);
        }
        return out;
    }

    /// The index template for the plan. Following ECS, it is composed of Elastic's ecs@mappings, and the ECS fields
    /// that maps as ECS says are left to it: only the rest are mapped here (asked for by the user), with dynamic
    /// mapping on, as the component's mapping is dynamic. No priority unless given: the user's to set (asked for by
    /// the user), Elasticsearch's 0 otherwise.
    ///
    /// @param templateName     The template's name.
    /// @param priority         Its priority, or null for none.
    /// @param leaveToComponent False to map every field (for a template built from the user's example, which says
    ///                         itself what it is composed of).
    /// @param ecs              The Elastic Common Schema, for a plan following ECS (null maps every field here).
    /// @return `{name, body}`, the body as an index template's JSON.
    public Map<String, Object> elasticTemplate(final String templateName,
                                               final Long priority,
                                               final boolean leaveToComponent,
                                               final EcsSchema ecs) {
        return PlanTemplates.elasticTemplate(this, templateName, priority, leaveToComponent, ecs);
    }

    /// @param templateName The template's name.
    /// @param ecs          The Elastic Common Schema, for a plan following ECS.
    /// @return The index template, leaving ECS fields to ecs@mappings as [#elasticTemplate] does, with no priority.
    public Map<String, Object> elasticTemplate(final String templateName, final EcsSchema ecs) {
        return elasticTemplate(templateName, null, true, ecs);
    }

    /// @param version The XSLT version, e.g. `3.0`.
    /// @return A draft indexing XSLT reading Events (xpath-default-namespace event-logging:3), or for a discovery
    /// index, reading the parser's records; the same text the Stroom MCP server writes for the plan.
    /// @throws IllegalArgumentException for an XML discovery plan without its record element's name.
    public String xslt(final String version) {
        if (discovery != null) {
            return DiscoveryXslt.xslt(this, version);
        }
        return IndexingXslt.xslt(this, version);
    }

    /// @return [#xslt(String)] for XSLT 3.0.
    public String xslt() {
        return xslt("3.0");
    }

    private static List<PlannedField> discoveryFields(final Discovery discovery) {
        final List<PlannedField> fields = new ArrayList<>();
        for (final List<String> field : FieldPlans.DISCOVERY_FIELDS) {
            final String source = field.get(2).isEmpty()
                    ? discovery.timestampField()
                    : field.get(2);
            fields.add(PlannedField.of(field.get(0), field.get(1), source));
        }
        return fields;
    }
}
