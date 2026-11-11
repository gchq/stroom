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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.validation.XmlTrees;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Generates a reference-data XSLT: the translation of a reference feed (a user list, an asset register) into the
/// reference-data:2 maps that `stroom:lookup()` reads from an events pipeline. The XSLT is stable to the byte (the
/// reference generator's fixtures pin it).
///
/// A reference-data pipeline parses the feed as any other (Data Splitter or JSON) and its XSLT writes one
/// `<reference>` per record and map: the map's name, the key, and the value (text, or elements). The events pipeline
/// then names the reference feed and a loader (a pipeline with a ReferenceDataFilter) as a pipeline reference, and
/// its translation looks keys up with `stroom:lookup('MAP', key)`.
public final class ReferenceGenerator {

    /// The reference-data namespace.
    public static final String REF = "reference-data:2";
    /// The reference-data schema version written by default.
    public static final String DEFAULT_VERSION = "2.0.1";

    private static final String XSL = XmlTrees.XSL;
    private static final Map<String, String> INPUT_NAMESPACE = Map.of("data_splitter", "records:2", "json",
            "http://www.w3.org/2013/XSL/json");

    private ReferenceGenerator() {
        // Static only.
    }

    /// @param mapping The reference mapping.
    /// @return The XSLT writing reference-data for its maps, at the default schema version (2.0.1).
    public static Generated generate(final ReferenceMapping mapping) {
        return generate(mapping, DEFAULT_VERSION);
    }

    /// @param mapping       The reference mapping.
    /// @param schemaVersion The reference-data schema version, e.g. `2.0.1`.
    /// @return The XSLT writing reference-data:2 for the mapping's maps, or the problems that stop it.
    public static Generated generate(final ReferenceMapping mapping, final String schemaVersion) {
        final List<String> problems = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        for (final ReferenceMap map : mapping.maps()) {
            if (seen.contains(map.name())) {
                problems.add("map '" + map.name() + "' is defined twice");
            }
            seen.add(map.name());
        }
        if (!problems.isEmpty()) {
            return new Generated(false, problems, null, sorted(seen));
        }
        final Map<String, String> namespaces = new LinkedHashMap<>();
        namespaces.put(null, REF);
        namespaces.put("xsl", XSL);
        namespaces.put("xsi", MappingFacts.XSI);
        namespaces.put("stroom", "stroom");
        final XmlElement sheet = XmlElement.create(XSL, "stylesheet", namespaces);
        sheet.set("version", "3.0");
        sheet.set("xpath-default-namespace", INPUT_NAMESPACE.get(mapping.input()));
        sheet.set("exclude-result-prefixes", "stroom");
        // Imports come first in a stylesheet.
        for (final String href : new LinkedHashSet<>(mapping.imports())) {
            sheet.addElement(XSL, "import", "href", href);
        }
        final boolean splitter = "data_splitter".equals(mapping.input());
        final String rootMatch = mapping.root() != null
                ? mapping.root()
                : splitter
                        ? "records"
                        : "/";
        final String records = mapping.record() != null
                ? mapping.record()
                : splitter
                        ? "record"
                        : XsltGenerator.JSON_RECORDS.get(mapping.jsonLayout());
        final XmlElement rootTemplate = sheet.addElement(XSL, "template", "match", rootMatch);
        final XmlElement data = rootTemplate.addElement(REF, "referenceData", "version", schemaVersion);
        data.set(MappingFacts.XSI, "schemaLocation", REF + " file://reference-data-v" + schemaVersion + ".xsd");
        data.addElement(XSL, "apply-templates", "select", records);
        XmlElement recordTemplate = sheet.addElement(XSL, "template", "match", "*");
        if (!mapping.dropWhen().isEmpty()) {
            final List<String> drops = new ArrayList<>();
            final List<String> reasons = new ArrayList<>();
            for (final DropRule drop : mapping.dropWhen()) {
                final List<String> tests = new ArrayList<>();
                for (final Condition condition : drop.when()) {
                    tests.add(test(mapping, condition, problems));
                }
                drops.add("(" + String.join(" and ", tests) + ")");
                reasons.add(drop.reason());
            }
            recordTemplate.append(new XmlComment(" left out: " + String.join("; ", reasons) + " "));
            recordTemplate = recordTemplate.addElement(XSL, "if", "test", "not(" + String.join(" or ", drops) + ")");
        }
        if (!problems.isEmpty()) {
            return new Generated(false, problems, null, sorted(seen));
        }
        for (final ReferenceMap map : mapping.maps()) {
            writeMap(mapping, map, recordTemplate);
        }
        return new Generated(true, List.of(), XmlSerialiser.toPrettyDocument(sheet),
                mapping.maps().stream().map(ReferenceMap::name).toList());
    }

    private static void writeMap(final ReferenceMapping mapping, final ReferenceMap map, final XmlElement template) {
        final String key = orSelector(mapping, map.keyXpath(), map.key());
        final XmlElement guard = template.addElement(XSL, "if", "test", "normalize-space(" + key + ")");
        final XmlElement reference = guard.addElement(REF, "reference");
        reference.addElement(REF, "map").setText(map.name());
        reference.addElement(REF, "key").addElement(XSL, "value-of", "select", key);
        final XmlElement value = reference.addElement(REF, "value");
        // The schema allows one element inside <value>: several named parts go inside a <details> element. Elements
        // stay in no namespace; a lookup's `path` finds them at any depth (stroom:lookup(...)//*:department).
        final long named = map.values().stream().filter(part -> !part.element().isEmpty()).count();
        final XmlElement parent = named > 1
                ? value.addElement(XSL, "element", "name", "details", "namespace", "")
                : value;
        for (final ReferenceValue part : map.values()) {
            final XmlElement holder = part.element().isEmpty()
                    ? parent
                    : parent.addElement(XSL, "element", "name", part.element(), "namespace", "");
            if (part.value() != null) {
                holder.setText(part.value());
            } else {
                holder.addElement(XSL, "value-of", "select", orSelector(mapping, part.xpath(), part.field()));
            }
        }
    }

    // An XPath given, or else the field's selector.
    private static String orSelector(final ReferenceMapping mapping, final String xpath, final String field) {
        return xpath != null && !xpath.isEmpty()
                ? xpath
                : selector(mapping, field);
    }

    private static String selector(final ReferenceMapping mapping, final String field) {
        final List<String> steps = new ArrayList<>();
        if ("data_splitter".equals(mapping.input())) {
            for (final String part : field.split("/", -1)) {
                steps.add("data[@name=" + XPathText.literal(part) + "]");
            }
            return String.join("/", steps) + "/@value";
        }
        for (final String part : field.split("\\.", -1)) {
            steps.add("*[@key=" + XPathText.literal(part) + "]");
        }
        return String.join("/", steps);
    }

    private static String test(final ReferenceMapping mapping, final Condition condition, final List<String> problems) {
        final String source = orSelector(mapping, condition.xpath(), condition.field());
        if (condition.equalTo() != null) {
            return source + " = " + XPathText.literal(condition.equalTo());
        }
        if (condition.oneOf() != null) {
            return source + " = (" + String.join(", ", condition.oneOf().stream().map(XPathText::literal).toList())
                   + ")";
        }
        if (condition.matches() != null) {
            return "exists(" + source + "[matches(., " + XPathText.literal(condition.matches()) + ")])";
        }
        if (condition.present() != null) {
            final String test = "exists(" + source + "[normalize-space(.)])";
            return condition.present()
                    ? test
                    : "not(" + test + ")";
        }
        problems.add("drop_when: a reference mapping's conditions take equals, one_of, matches or present, not "
                     + "in_dictionary");
        return "false()";
    }

    private static List<String> sorted(final Set<String> names) {
        return names.stream().sorted(ReferenceGenerator::byCodePoint).toList();
    }

    // Names in order by code point (not by UTF-16 unit).
    private static int byCodePoint(final String a, final String b) {
        return Arrays.compare(a.codePoints().toArray(), b.codePoints().toArray());
    }


    // --------------------------------------------------------------------------------


    /// What the generator gives.
    ///
    /// @param ok       Whether it wrote the XSLT.
    /// @param problems What stopped it.
    /// @param xslt     The XSLT, or null.
    /// @param maps     The maps' names: in order, or sorted when there are problems.
    public record Generated(boolean ok, List<String> problems, String xslt, List<String> maps) {

        /// @return It as a map, keys in the order the fixtures pin: ok, problems, xslt, maps.
        public Map<String, Object> toMap() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", ok);
            out.put("problems", problems);
            out.put("xslt", xslt);
            out.put("maps", maps);
            return out;
        }
    }
}
