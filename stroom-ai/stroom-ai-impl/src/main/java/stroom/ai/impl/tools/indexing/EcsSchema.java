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

import stroom.ai.impl.tools.AgentKnowledge;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.validation.CloseMatches;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The Elastic Common Schema, as Elastic publishes it (the bundled `conventions/ecs_fields.json`), and what
/// Elasticsearch's built-in `ecs@mappings` component template maps otherwise than ECS says (`ecs_component.json`,
/// measured against a real Elasticsearch). An index plan that follows ECS is checked against it, field by field; a
/// Data element whose name is an ECS field's (source_ip as source.ip) is drafted under that name; and an ECS plan's
/// index template leaves to `ecs@mappings` the fields it maps as ECS says. Ported from the Stroom MCP server's
/// `utils/ecs.py`.
///
/// The files are bundled with the agent knowledge ([AgentKnowledge#conventionsFile(String)]): read one with
/// [#from(AgentKnowledge)], or [#parse(String, String)] given their text.
public final class EcsSchema {

    /// Elasticsearch's built-in component template for ECS (8.13 and later).
    public static final String COMPONENT = "ecs@mappings";
    /// The bundled file holding the schema.
    public static final String FIELDS_FILE = "ecs_fields.json";
    /// The bundled file holding what the component maps otherwise than ECS says.
    public static final String COMPONENT_FILE = "ecs_component.json";
    /// Elasticsearch types Stroom leaves out of an Elastic Index doc's fields, so it can neither search nor show a
    /// field of them (nor a subfield of one). Measured on the local stack (Stroom 7.x, Elasticsearch 9.5); seen in
    /// production: ecs@mappings maps message as match_only_text, and every search on it found nothing.
    public static final Set<String> STROOM_UNLISTED = JsonValues.nullSafeSet("match_only_text", "flattened",
            "geo_point", "search_as_you_type");
    /// An index plan's types, and the ECS types each stands for, in the order they are tried.
    static final Map<String, Set<String>> PLAN_TYPES = planTypes();

    // Fields Stroom needs in every document, whatever the convention.
    private static final Set<String> STROOM = JsonValues.nullSafeSet("StreamId", "EventId");
    private static final Pattern VALUE_PART = Pattern.compile("\\{[^}]*\\}");
    private static final Pattern NOT_WORD = Pattern.compile("[^a-z0-9]+");
    private static final Pattern VERSION_WORD = Pattern.compile("v?\\d+");
    private static final Pattern NAME_WORDS = Pattern.compile("[A-Z]+(?![a-z])|[A-Z]?[a-z]+|\\d+",
            Pattern.UNICODE_CHARACTER_CLASS);

    private static volatile Cached cached;

    private final String version;
    private final List<String> fieldSets;
    private final Map<String, Map<String, Object>> fields;
    private final Map<String, String> differs;

    private EcsSchema(final String version,
                      final List<String> fieldSets,
                      final Map<String, Map<String, Object>> fields,
                      final Map<String, String> differs) {
        this.version = version;
        this.fieldSets = fieldSets;
        this.fields = fields;
        this.differs = differs;
    }

    /// @param fieldsJson    The text of `ecs_fields.json`.
    /// @param componentJson The text of `ecs_component.json`.
    /// @return The schema they hold.
    @SuppressWarnings("unchecked")
    public static EcsSchema parse(final String fieldsJson, final String componentJson) {
        final Map<String, Object> schema = (Map<String, Object>) PyJson.loads(fieldsJson);
        final Map<String, Object> component = (Map<String, Object>) PyJson.loads(componentJson);
        final Map<String, Map<String, Object>> fields = new LinkedHashMap<>();
        ((Map<String, Object>) schema.get("fields")).forEach((name, spec) ->
                fields.put(name, (Map<String, Object>) spec));
        final Map<String, String> differs = new LinkedHashMap<>();
        ((Map<String, Object>) component.get("differs")).forEach((name, kind) -> differs.put(name, (String) kind));
        return new EcsSchema((String) schema.get("version"), List.copyOf((List<String>) schema.get("field_sets")),
                fields, differs);
    }

    /// @param knowledge The bundled knowledge.
    /// @return The schema it bundles, read once, or empty if it bundles none.
    public static Optional<EcsSchema> from(final AgentKnowledge knowledge) {
        final Optional<String> fieldsJson = knowledge.conventionsFile(FIELDS_FILE);
        final Optional<String> componentJson = knowledge.conventionsFile(COMPONENT_FILE);
        if (fieldsJson.isEmpty() || componentJson.isEmpty()) {
            return Optional.empty();
        }
        final Cached known = cached;
        if (known != null && known.fieldsJson().equals(fieldsJson.get())
            && known.componentJson().equals(componentJson.get())) {
            return Optional.of(known.schema());
        }
        final EcsSchema schema = parse(fieldsJson.get(), componentJson.get());
        cached = new Cached(fieldsJson.get(), componentJson.get(), schema);
        return Optional.of(schema);
    }

    /// @return The ECS version, e.g. `9.5.0`.
    public String version() {
        return version;
    }

    /// @return ECS's field sets (agent, client, ...), in its order.
    public List<String> fieldSets() {
        return fieldSets;
    }

    /// @return The names of ECS's fields, in its order.
    public Set<String> fieldNames() {
        return Collections.unmodifiableSet(fields.keySet());
    }

    /// @param name A field's name, e.g. `source.ip`.
    /// @return Its definition (type, level, short, and for some allowed), or null if it isn't an ECS field.
    public Map<String, Object> field(final String name) {
        return fields.get(name);
    }

    /// @param name A field's name.
    /// @return What ecs@mappings maps it as when a document writes it (dynamic mapping on), or null: not an ECS
    /// field, or one it doesn't map.
    public String componentType(final String name) {
        final Map<String, Object> spec = field(name);
        if (spec == null || spec.isEmpty()) {
            return null;
        }
        final String kind = differs.getOrDefault(name, (String) spec.get("type"));
        return "unmapped".equals(kind)
                ? null
                : kind;
    }

    /// Whether a planned field is mapped by ecs@mappings as ECS says, and the plan agrees with ECS: the index
    /// template then leaves it to the component, mapping only the rest (asked for by the user). Not when the
    /// component maps it as a type Stroom can't search (message as match_only_text): the template maps it as the
    /// plan says.
    ///
    /// @param name     A planned field's name.
    /// @param planType Its plan type.
    /// @return True if it is left to the component.
    public boolean leftToComponent(final String name, final String planType) {
        final String component = componentType(name);
        return !STROOM.contains(name) && !differs.containsKey(name) && component != null
               && !STROOM_UNLISTED.contains(component)
               && PLAN_TYPES.containsKey(planType) && check(name, planType) == null;
    }

    /// Whether a template's explicit mapping of a field is just what ecs@mappings would give it (its type, and for
    /// a keyword at most ignore_above 1024, the component's own), so the template can leave it to the component.
    ///
    /// @param name A field's path.
    /// @param spec The template's mapping of it.
    /// @return True if the component maps it the same.
    public boolean mappedAsComponent(final String name, final Map<String, Object> spec) {
        final Object kind = spec.get("type");
        final String plan = planTypeOf(kind);
        return JsonValues.truthy(kind) && kind.equals(componentType(name))
               && Set.of("type", "ignore_above").containsAll(spec.keySet())
               && JsonValues.equalsNumber(spec.getOrDefault("ignore_above", 1024L), 1024)
               && plan != null && leftToComponent(name, plan);
    }

    /// @param name An ECS field's name.
    /// @return The index plan type for it, or null for an object, nested or geo field (or a name ECS lacks).
    public String planType(final String name) {
        final Map<String, Object> spec = field(name);
        return spec == null || spec.isEmpty()
                ? null
                : planTypeOf(spec.get("type"));
    }

    /// @param prefix A field set or prefix, e.g. `process` or `user.name`.
    /// @param limit  The most to give.
    /// @return The ECS fields under it, each with its type and short description.
    public Map<String, String> fieldsIn(final String prefix, final int limit) {
        final String at = PyText.rstrip(PyText.strip(prefix), ".");
        final Map<String, String> found = new LinkedHashMap<>();
        for (final Map.Entry<String, Map<String, Object>> entry : fields.entrySet()) {
            final String name = entry.getKey();
            if (found.size() < limit && (name.equals(at) || name.startsWith(at + "."))) {
                found.put(name, entry.getValue().get("type") + ": " + entry.getValue().get("short"));
            }
        }
        return found;
    }

    /// Why a planned field doesn't follow ECS. A name outside ECS's field sets is a custom field, which ECS allows;
    /// one inside a field set must be an ECS field, of a type that maps to ECS's.
    ///
    /// @param name A planned field's name.
    /// @param type Its plan type.
    /// @return Why it doesn't follow ECS, or null if it does.
    public String check(final String name, final String type) {
        if (STROOM.contains(name)) {
            return null;
        }
        final Map<String, Object> spec = field(name);
        if (spec != null && !spec.isEmpty()) {
            final Set<String> kinds = PLAN_TYPES.getOrDefault(type, JsonValues.nullSafeSet());
            final Object ecsType = spec.get("type");
            if (!kinds.contains(ecsType) && !"id".equals(type)) {
                final String fits = planTypeOf(ecsType);
                return name + " is typed " + type + ", where ECS " + version + " has " + ecsType
                       + (fits != null
                        ? " (plan type " + fits + ")"
                        : "");
            }
            return null;
        }
        final String top = name.split("\\.", -1)[0];
        if (fieldSets.contains(top) && name.contains(".")) {
            final List<String> under = fields.keySet().stream().filter(n -> n.startsWith(top + ".")).toList();
            final List<String> near = CloseMatches.closeMatches(name, under, 3, 0.6);
            return name + " is in ECS's " + top + " field set but isn't an ECS " + version + " field"
                   + (near.isEmpty()
                    ? ""
                    : ": did you mean " + String.join(", ", near) + "?")
                   + " (a field of your own goes outside ECS's field sets, e.g. under labels or your organisation's "
                   + "name)";
        }
        return null;
    }

    /// @return ECS's own top-level names: its field sets, and its base fields (@timestamp, message, tags, labels).
    public Set<String> topLevel() {
        final Set<String> names = new HashSet<>(fieldSets);
        for (final String name : fields.keySet()) {
            names.add(name.split("\\.", -1)[0]);
        }
        return names;
    }

    /// A namespace for a source's own fields, as ECS asks, named after the source as the index name says it
    /// (secretserver from stroom-ecs-secretserver-v1): the index name less Stroom's, the convention's and version
    /// words, and any XSLT variable in it.
    ///
    /// @param indexName An index name.
    /// @return The namespace.
    public String sourceNamespace(final String indexName) {
        final String lower = VALUE_PART.matcher(indexName.toLowerCase(Locale.ROOT)).replaceAll("-");
        final List<String> words = new ArrayList<>();
        for (final String word : NOT_WORD.split(lower, -1)) {
            if (!word.isEmpty() && !"stroom".equals(word) && !"ecs".equals(word)
                && !VERSION_WORD.matcher(word).matches()) {
                words.add(word);
            }
        }
        final String name = words.isEmpty()
                ? "custom"
                : String.join("_", words);
        return topLevel().contains(name)
                ? name + "_custom"
                : name;
    }

    /// Each planned field's departure from ECS, and each top-level name that is neither ECS's nor a custom
    /// namespace the plan declares. Seen: an ECS plan drafted authenticate.*, authorise.*, copy.* and create.* (one
    /// group per event-logging action), the check passed them as fields outside ECS's field sets, and the agent told
    /// the user only its own secret.* fields weren't ECS.
    ///
    /// @param planned    The planned fields.
    /// @param namespaces The custom namespaces the plan declares.
    /// @return The problems, none if the fields follow ECS.
    public List<String> problems(final Collection<PlannedField> planned, final List<String> namespaces) {
        final List<String> found = new ArrayList<>();
        for (final PlannedField planField : planned) {
            final String problem = check(planField.name(), planField.type());
            if (problem != null) {
                found.add(problem);
            }
        }
        final Set<String> top = topLevel();
        final Map<String, List<String>> outside = new LinkedHashMap<>();
        for (final PlannedField planField : planned) {
            final String head = planField.name().split("\\.", -1)[0];
            if (!STROOM.contains(planField.name()) && !top.contains(head) && !namespaces.contains(head)) {
                outside.computeIfAbsent(head, key -> new ArrayList<>()).add(planField.name());
            }
        }
        outside.forEach((head, names) -> found.add(head + ".* ("
                + String.join(", ", names.subList(0, Math.min(3, names.size())))
                + (names.size() > 3
                ? ", ..."
                : "")
                + "): " + head + " is neither an ECS " + version + " field set nor a custom namespace the plan "
                + "declares (" + PyText.list(namespaces) + "). ECS puts a source's own fields under one namespace "
                + "named after it: move them there"
                + (namespaces.isEmpty()
                ? ""
                : " (" + namespaces.getFirst() + "." + names.getFirst() + ")")
                + ", or add " + head + " to the plan's custom_namespaces if the user wants it as it is"));
        return found;
    }

    /// The ECS field a Data element's name is, written in another case or with other separators: src_ip is not
    /// source.ip (that would be guessing), but source_ip, sourceIp and Source-IP are.
    ///
    /// @param dataName A Data element's Name.
    /// @return The ECS field's name, or null when it isn't one.
    public String forDataName(final String dataName) {
        final List<String> words = new ArrayList<>();
        final Matcher matcher = NAME_WORDS.matcher(dataName.replace('.', ' '));
        while (matcher.find()) {
            words.add(matcher.group());
        }
        if (words.isEmpty()) {
            return null;
        }
        final String snake = String.join("_", words.stream().map(PyText::lower).toList());
        final List<String> candidates = new ArrayList<>(List.of(PyText.lower(dataName), snake));
        final int separators = (int) snake.chars().filter(c -> c == '_').count();
        for (int n = 1; n <= separators; n++) {
            candidates.add(replaceFirst(snake, n));
        }
        for (final String candidate : candidates) {
            if (isPlanned(candidate)) {
                return candidate;
            }
        }
        // Dots only between ECS field set and field: user_full_name as user.full_name, not user.full.name.
        final List<String> parts = List.of(snake.split("_", -1));
        for (int split = 1; split < parts.size(); split++) {
            final String candidate = String.join("_", parts.subList(0, split)) + "."
                                     + String.join("_", parts.subList(split, parts.size()));
            if (isPlanned(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isPlanned(final String candidate) {
        final Map<String, Object> spec = field(candidate);
        return spec != null && !spec.isEmpty() && planType(candidate) != null;
    }

    // The text with its first n underscores made dots, as str.replace('_', '.', n).
    private static String replaceFirst(final String text, final int n) {
        final StringBuilder sb = new StringBuilder(text);
        int done = 0;
        for (int i = 0; i < sb.length() && done < n; i++) {
            if (sb.charAt(i) == '_') {
                sb.setCharAt(i, '.');
                done++;
            }
        }
        return sb.toString();
    }

    private static String planTypeOf(final Object ecsType) {
        for (final Map.Entry<String, Set<String>> entry : PLAN_TYPES.entrySet()) {
            if (entry.getValue().contains(ecsType)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static Map<String, Set<String>> planTypes() {
        final Map<String, Set<String>> types = new LinkedHashMap<>();
        types.put("keyword", JsonValues.nullSafeSet("keyword", "constant_keyword", "wildcard", "flattened"));
        types.put("text", JsonValues.nullSafeSet("text", "match_only_text"));
        types.put("long", JsonValues.nullSafeSet("long", "integer", "short", "byte", "unsigned_long"));
        types.put("double", JsonValues.nullSafeSet("double", "float", "half_float", "scaled_float"));
        types.put("date", JsonValues.nullSafeSet("date"));
        types.put("boolean", JsonValues.nullSafeSet("boolean"));
        types.put("ip", JsonValues.nullSafeSet("ip"));
        return Collections.unmodifiableMap(types);
    }

    // --------------------------------------------------------------------------------


    // The schema last read, and the texts it was read from.
    private record Cached(String fieldsJson, String componentJson, EcsSchema schema) {

    }
}
