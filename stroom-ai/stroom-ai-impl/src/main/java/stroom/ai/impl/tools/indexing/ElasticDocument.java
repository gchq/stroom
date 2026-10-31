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
import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.generation.StyledXslt;
import stroom.ai.impl.tools.indexing.IndexingXslt.Part;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/// The lines of an Elasticsearch indexing XSLT's document: its top-level fields and objects, and the templates for
/// the objects it repeats.
///
/// Dotted names are nested as objects (user.id -> `<map key="user"><string key="id">`), each object written only
/// when one of its fields is present. A name below another field's name (time.min beside time) cannot be nested, so
/// it is written as a flat key: an index with subobjects: false takes it, and otherwise [FieldPlan#required()]
/// reports it. An object whose fields all lie below one element, with the same shape in more than one place (an
/// action's Outcome, a Resource with its Id and Type, a Source or Destination holding one), is written by a template
/// of its own, applied to that element wherever it occurs, as the translation XSLT writes each kind of event once:
/// the same object the same way everywhere, and one place to change it.
final class ElasticDocument {

    private static final Map<String, String> JSON_ELEMENT = Map.of("id", "number", "long", "number", "double",
            "number", "boolean", "boolean");
    private static final Pattern SINGLE = Pattern.compile("<xsl:if test=\"[^\"]*\">(.*)</xsl:if>$", Pattern.UNIX_LINES);

    private final FieldPlan plan;
    private final Set<String> taken;
    // Whether the user chose to write a stand-in for none as null (IndexingXslt#AS_NULL).
    private final boolean asNull;
    private final Map<List<Object>, Integer> counts = new HashMap<>();
    private final Map<List<Object>, String> modes = new HashMap<>();
    private final List<String> templates = new ArrayList<>();
    private final List<Part> parts = new ArrayList<>();

    /// Works out the document's lines.
    ///
    /// @param plan  An Elasticsearch plan.
    /// @param taken The names in use in the stylesheet; the modes made are added.
    ElasticDocument(final FieldPlan plan, final Set<String> taken) {
        this.plan = plan;
        this.taken = taken;
        this.asNull = !plan.standIns(Discovery.NULL).isEmpty();
        final Map<String, Object> tree = tree();
        count(tree, null);
        for (final Map.Entry<String, Object> entry : tree.entrySet()) {
            final Map<String, Object> one = new LinkedHashMap<>();
            one.put(entry.getKey(), entry.getValue());
            parts.add(new Part(entry.getKey(), entry.getValue() instanceof Map, render(one, "      ", null, null)));
        }
    }

    /// @return The document's top-level fields and objects, each with its lines.
    List<Part> parts() {
        return parts;
    }

    /// @return The templates for the objects the document repeats.
    List<String> templates() {
        return templates;
    }

    // The fields as a tree of objects: a field a shared template writes is not written here, the template is
    // called in its place.
    @SuppressWarnings("unchecked")
    private Map<String, Object> tree() {
        final List<Object> items = new ArrayList<>();
        plan.fields().stream().filter(f -> plan.writtenBy(f.name()) == null).forEach(items::add);
        items.addAll(plan.shared());
        final Set<String> names = new HashSet<>();
        items.forEach(item -> names.add(nameOf(item)));
        final Map<String, Object> tree = new LinkedHashMap<>();
        for (final Object item : items) {
            final String name = nameOf(item);
            if (isFlat(name, names)) {
                tree.put(name, item);
                continue;
            }
            Map<String, Object> node = tree;
            final String[] parts = name.split("\\.", -1);
            for (int i = 0; i < parts.length - 1; i++) {
                node = (Map<String, Object>) node.computeIfAbsent(parts[i], key -> new LinkedHashMap<String, Object>());
            }
            node.put(parts[parts.length - 1], item);
        }
        return tree;
    }

    // A name with no dot, or below another item's name, is a key of its own.
    private static boolean isFlat(final String name, final Set<String> names) {
        if (!name.contains(".")) {
            return true;
        }
        final String[] parts = name.split("\\.", -1);
        for (int n = 1; n < parts.length; n++) {
            if (names.contains(String.join(".", List.of(parts).subList(0, n)))) {
                return true;
            }
        }
        return false;
    }

    private static String nameOf(final Object item) {
        return item instanceof final SharedTemplate use
                ? use.at()
                : ((PlannedField) item).name();
    }

    // Every source below an object, null for a shared template's.
    @SuppressWarnings("unchecked")
    private static List<String> sources(final Map<String, Object> node) {
        final List<String> out = new ArrayList<>();
        for (final Object value : node.values()) {
            if (value instanceof SharedTemplate) {
                out.add(null);
            } else if (value instanceof final PlannedField field) {
                out.add(field.source());
            } else {
                out.addAll(sources((Map<String, Object>) value));
            }
        }
        return out;
    }

    // The element every field of the object lies below, as steps; null when there is none to apply a template to
    // (a shared template inside, or a source that is not a plain path).
    @SuppressWarnings("unchecked")
    private static List<String> baseOf(final Map<String, Object> node) {
        final List<String> found = sources(node);
        if (found.contains(null) || found.stream().anyMatch(source -> !IndexingXslt.plain(source))) {
            return null;
        }
        // Each field's parent element; an object inside counts from its own element's parent, so a Destination
        // holding only a Resource is the Destination element, not the Resource.
        final List<List<String>> parents = new ArrayList<>();
        for (final Object value : node.values()) {
            if (value instanceof Map) {
                final List<String> inner = baseOf((Map<String, Object>) value);
                if (inner == null || inner.isEmpty()) {
                    return null;
                }
                parents.add(inner.subList(0, inner.size() - 1));
            } else {
                final List<String> steps = FieldPlans.steps(((PlannedField) value).source());
                parents.add(steps.subList(0, steps.size() - 1));
            }
        }
        final int shortest = parents.stream().mapToInt(List::size).min().orElse(0);
        final List<String> common = new ArrayList<>();
        for (int i = 0; i < shortest; i++) {
            final Set<String> steps = new HashSet<>();
            for (final List<String> parent : parents) {
                steps.add(parent.get(i));
            }
            final String step = parents.getFirst().get(i);
            if (steps.size() != 1 || step.startsWith("@")) {
                break;
            }
            common.add(step);
        }
        return common.isEmpty()
                ? null
                : common;
    }

    private static boolean below(final List<String> inner, final List<String> base) {
        return inner != null && !inner.isEmpty() && (base == null || base.isEmpty()
                                                     || (inner.size() > base.size()
                                                         && inner.subList(0, base.size()).equals(base)));
    }

    private static String relative(final String path, final List<String> base) {
        final List<String> steps = FieldPlans.steps(path);
        return String.join("/", steps.subList(Math.min(base.size(), steps.size()), steps.size()));
    }

    // The object's shape relative to its element: what it holds and where, to tell where the same object is
    // written in more than one place.
    @SuppressWarnings("unchecked")
    private static List<Object> shape(final Map<String, Object> node, final List<String> base) {
        final List<Object> out = new ArrayList<>();
        for (final Map.Entry<String, Object> entry : node.entrySet()) {
            if (entry.getValue() instanceof Map) {
                final Map<String, Object> value = (Map<String, Object>) entry.getValue();
                final List<String> inner = baseOf(value);
                if (!below(inner, base)) {
                    return List.of("unshared", new Identity(node));
                }
                out.add(List.of(entry.getKey(), List.copyOf(inner.subList(base.size(), inner.size())),
                        shape(value, inner)));
            } else {
                final PlannedField field = (PlannedField) entry.getValue();
                out.add(List.of(entry.getKey(), field.type(), relative(field.source(), base)));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void count(final Map<String, Object> node, final List<String> base) {
        for (final Map.Entry<String, Object> entry : node.entrySet()) {
            if (entry.getValue() instanceof Map) {
                final Map<String, Object> value = (Map<String, Object>) entry.getValue();
                final List<String> inner = baseOf(value);
                if (below(inner, base)) {
                    counts.merge(List.of(entry.getKey(), shape(value, inner)), 1, Integer::sum);
                }
                count(value, below(inner, base)
                        ? inner
                        : base);
            }
        }
    }

    // The mode of the template writing this object, made the first time; null if it is written in one place only.
    private String modeFor(final String key, final Map<String, Object> value, final List<String> base) {
        final List<Object> signature = List.of(key, shape(value, base));
        if (counts.getOrDefault(signature, 0) < 2) {
            return null;
        }
        if (!modes.containsKey(signature)) {
            modes.put(signature, StyledXslt.partName(key, plan.style(), taken));
            final List<String> body = render(value, "        ", base, null);
            final Set<String> tests = new LinkedHashSet<>();
            sources(value).forEach(source -> tests.add(relative(source, base)));
            templates.add("  <xsl:template match=\"*\" mode=\"" + modes.get(signature) + "\">");
            templates.add("    <xsl:if test=\"" + FieldPlans.quoted(String.join(" or ", tests)) + "\">");
            templates.add("      <map key=\"" + key + "\">");
            templates.addAll(single(value, body));
            templates.add("      </map>");
            templates.add("    </xsl:if>");
            templates.add("  </xsl:template>");
        }
        return modes.get(signature);
    }

    // The object's fields; with base, relative to its element (inside a template). guarded: the test the object is
    // already inside, not repeated for an object inside it that would test the same.
    @SuppressWarnings("unchecked")
    private List<String> render(final Map<String, Object> node,
                                final String indent,
                                final List<String> base,
                                final String guarded) {
        final boolean relativeToBase = base != null && !base.isEmpty();
        final List<String> out = new ArrayList<>();
        for (final Map.Entry<String, Object> entry : node.entrySet()) {
            final String key = entry.getKey();
            if (!(entry.getValue() instanceof Map)) {
                final String at = relativeToBase && entry.getValue() instanceof final PlannedField field
                        ? relative(field.source(), base)
                        : null;
                out.add(leaf(entry.getValue(), key, indent, at, asNull));
                continue;
            }
            final Map<String, Object> value = (Map<String, Object>) entry.getValue();
            final List<String> inner = baseOf(value);
            final String mode = below(inner, base)
                    ? modeFor(key, value, inner)
                    : null;
            if (mode != null) {
                final String element = relativeToBase
                        ? relative(String.join("/", inner), base)
                        : String.join("/", inner);
                out.add(indent + "<xsl:apply-templates select=\"(" + FieldPlans.quoted(element) + ")[1]\" mode=\""
                        + mode + "\" />");
                continue;
            }
            final List<String> sources = sources(value);
            if (sources.contains(null)) {
                // A shared template inside: written whatever the event holds.
                out.add(indent + "<map key=\"" + key + "\">");
                out.addAll(render(value, indent + "  ", base, null));
                out.add(indent + "</map>");
                continue;
            }
            final Set<String> tested = new LinkedHashSet<>();
            sources.forEach(source -> tested.add(relativeToBase
                    ? relative(source, base)
                    : source));
            final String tests = String.join(" or ", tested);
            if (tests.equals(guarded)) {
                // http holding only request: tested once, by the outer object.
                out.add(indent + "<map key=\"" + key + "\">");
                out.addAll(single(value, render(value, indent + "  ", base, tests)));
                out.add(indent + "</map>");
                continue;
            }
            final List<String> body = render(value, indent + "    ", base, tests);
            out.add(indent + "<xsl:if test=\"" + FieldPlans.quoted(tests) + "\">");
            out.add(indent + "  <map key=\"" + key + "\">");
            out.addAll(single(value, body));
            out.add(indent + "  </map>");
            out.add(indent + "</xsl:if>");
        }
        return out;
    }

    // A field: a shared template's call, or the value written when the event has it; with asNull, a stand-in for
    // none the user chose to write as null is written so.
    private static String leaf(final Object item,
                               final String key,
                               final String indent,
                               final String source,
                               final boolean asNull) {
        if (item instanceof final SharedTemplate use) {
            return IndexingXslt.call(use, indent);
        }
        final PlannedField field = (PlannedField) item;
        if (field.isObjects()) {
            return objects(field, key, indent, source, asNull);
        }
        final String element = JSON_ELEMENT.getOrDefault(field.type(), "string");
        final String at = JsonValues.truthy(source)
                ? source
                : field.source();
        final String single = "<" + element + " key=\"" + key + "\"><xsl:value-of select=\""
                              + FieldPlans.quoted(field.value(at)) + "\" /></" + element + ">";
        if (!field.multiple() && asNull) {
            return indent + "<xsl:choose><xsl:when test=\"" + FieldPlans.quoted(at) + "[normalize-space(.) = $"
                   + IndexingXslt.AS_NULL + "]\"><null key=\"" + key + "\" /></xsl:when><xsl:when test=\""
                   + FieldPlans.quoted(at) + "\">" + single + "</xsl:when></xsl:choose>";
        }
        if (!field.multiple()) {
            return indent + "<xsl:if test=\"" + FieldPlans.quoted(at) + "\">" + single + "</xsl:if>";
        }
        // Several values (repeated Data elements, say) as an array of them. Seen (asked about by the user): with
        // value-of over them all, two ports came out as the one number "80 443", two tags as the string "a b".
        return indent + "<xsl:choose><xsl:when test=\"count(" + FieldPlans.quoted(at) + ") gt 1\"><array key=\""
               + key + "\"><xsl:for-each select=\"" + FieldPlans.quoted(at) + "\"><" + element
               + "><xsl:value-of select=\"" + FieldPlans.quoted(field.value(".")) + "\" /></" + element
               + "></xsl:for-each></array></xsl:when><xsl:when test=\"" + FieldPlans.quoted(at) + "\">" + single
               + "</xsl:when></xsl:choose>";
    }

    // A field of objects: an array of one object per element the source selects, made distinct by the key (the first
    // element with each, in the order first met: xsl:for-each-group, which leaves out an element without one), each
    // object written from its element as a field of the document is from the Event. Asked for by the user: every
    // user an event names, once each by login name.
    private static String objects(final PlannedField field,
                                  final String key,
                                  final String indent,
                                  final String source,
                                  final boolean asNull) {
        final String at = JsonValues.truthy(source)
                ? source
                : field.source();
        final String select = JsonValues.truthy(field.distinctBy())
                ? "(" + at + ")[" + field.distinctBy() + "]"
                : at;
        final List<String> lines = new ArrayList<>();
        lines.add(indent + "<xsl:if test=\"" + FieldPlans.quoted(select) + "\">");
        lines.add(indent + "  <array key=\"" + key + "\">");
        lines.add(indent + (JsonValues.truthy(field.distinctBy())
                ? "    <xsl:for-each-group select=\"" + FieldPlans.quoted(select) + "\" group-by=\""
                  + FieldPlans.quoted(field.distinctBy()) + "\">"
                : "    <xsl:for-each select=\"" + FieldPlans.quoted(select) + "\">"));
        lines.add(indent + "      <map>");
        for (final PlannedField item : field.items()) {
            lines.add(leaf(item, item.name(), indent + "        ", item.source(), asNull));
        }
        lines.add(indent + "      </map>");
        lines.add(indent + (JsonValues.truthy(field.distinctBy())
                ? "    </xsl:for-each-group>"
                : "    </xsl:for-each>"));
        lines.add(indent + "  </array>");
        lines.add(indent + "</xsl:if>");
        return String.join("\n", lines);
    }

    // An object of one field: the field's own test is the object's, so it is not tested twice.
    private static List<String> single(final Map<String, Object> node, final List<String> body) {
        if (node.size() == 1 && body.size() == 1 && !(node.values().iterator().next() instanceof Map)) {
            return List.of(SINGLE.matcher(body.getFirst()).replaceAll("$1"));
        }
        return body;
    }


    // --------------------------------------------------------------------------------


    // An object told apart by identity, not content: the shape of an object that can't be shared is its own.
    private static final class Identity {

        private final Object node;

        private Identity(final Object node) {
            this.node = node;
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof final Identity identity && identity.node == node;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(node);
        }
    }
}
