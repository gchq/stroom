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
import stroom.ai.impl.tools.sample.PyJson;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// A discovery plan's indexing XSLT: each raw record copied into the xpath-functions JSON the Elasticsearch filter
/// reads, with its own field names, adding StreamId, EventId (the record number), @timestamp and any stream meta.
/// JSONParser records are copied as they are; a Data Splitter record's columns become fields named by the header;
/// an XML record becomes nested objects (repeated elements as arrays, attributes as fields). Ported from the Stroom
/// MCP server's `FieldPlan._discovery_xslt` and `_discovery_text_xslt`; the texts are its own (resources beside
/// this class), and it writes the same XSLT.
final class DiscoveryXslt {

    private static final String JSON_XSLT = resource("discovery-json.xslt");
    private static final String JSON_UNPACK = resource("discovery-json-unpack.xslt");
    private static final String TEXT_XSLT = resource("discovery-text.xslt");
    private static final String TEXT_UNPACK = resource("discovery-text-unpack.xslt");
    private static final String DELIMITED_FIELDS = resource("discovery-delimited-fields.xslt");
    private static final String XML_FIELDS = resource("discovery-xml-fields.xslt");
    private static final String XML_TEMPLATES = resource("discovery-xml-templates.xslt");
    private static final Pattern TOKEN = Pattern.compile("@@([A-Z_]+)@@");

    private DiscoveryXslt() {
        // Static only.
    }

    /// @param plan    A discovery plan.
    /// @param version The XSLT version.
    /// @return The discovery indexing XSLT.
    /// @throws IllegalArgumentException for an XML discovery without its record element's name.
    static String xslt(final FieldPlan plan, final String version) {
        final Discovery discovery = plan.discovery();
        if (!"json".equals(discovery.input())) {
            return textXslt(plan, version);
        }
        final String when = jsonPath(discovery.timestampField());
        final List<String> ours = new ArrayList<>(plan.fields().stream().map(PlannedField::name).toList());
        ours.addAll(discovery.meta().keySet());
        final List<String> clash = new ArrayList<>(FieldPlans.ES_METADATA);
        // Top-level keys to rename (see the template).
        clash.addAll(ours);
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("VERSION", version);
        values.put("INDEX", plan.indexName());
        values.put("WHEN", when);
        values.put("TIME", time(discovery, when));
        values.put("META", meta(discovery));
        values.put("SELECT", discovery.drop().isEmpty()
                ? "*"
                : "*[not(@key = (" + quoted(discovery.drop()) + "))]");
        values.put("CLASH", quoted(clash));
        values.put("UNPACK", discovery.unpackJson()
                ? JSON_UNPACK
                : "");
        return substitute(JSON_XSLT, values);
    }

    // Delimited or XML records into the xpath-functions JSON the Elasticsearch filter reads.
    private static String textXslt(final FieldPlan plan, final String version) {
        final Discovery discovery = plan.discovery();
        final String when;
        final String records;
        final String defaultNamespace;
        final String fields;
        final String xml;
        if ("delimited".equals(discovery.input())) {
            when = "data[@name=" + single(discovery.timestampField()) + "]/@value";
            records = "records/record";
            defaultNamespace = "xpath-default-namespace=\"records:2\" ";
            fields = DELIMITED_FIELDS.replace("@@KEEP@@", discovery.drop().isEmpty()
                    ? ""
                    : "[not(@name = (" + quoted(discovery.drop()) + "))]");
            xml = "";
        } else {
            if (!JsonValues.truthy(discovery.record())) {
                throw new IllegalArgumentException("An XML discovery index needs the record element's name (record)");
            }
            final List<String> steps = new ArrayList<>();
            for (final String part : discovery.timestampField().split("\\.", -1)) {
                steps.add(part.startsWith("@")
                        ? part
                        : "*[local-name() = '" + part + "']");
            }
            when = String.join("/", steps);
            records = "//*[local-name() = '" + discovery.record() + "']";
            defaultNamespace = "";
            fields = XML_FIELDS.replace("@@KEEP@@", discovery.drop().isEmpty()
                    ? ""
                    : "[not(local-name() = (" + quoted(discovery.drop()) + "))]");
            xml = XML_TEMPLATES;
        }
        final List<String> ours = new ArrayList<>(plan.fields().stream().map(PlannedField::name).toList());
        ours.addAll(discovery.meta().keySet());
        // In turn, as the server replaces them.
        String out = TEXT_XSLT;
        out = out.replace("@@DEFAULT_NS@@", defaultNamespace);
        out = out.replace("@@VERSION@@", version);
        out = out.replace("@@INDEX@@", plan.indexName());
        out = out.replace("@@INPUT@@", "delimited".equals(discovery.input())
                ? "delimited"
                : "XML");
        out = out.replace("@@RECORDS@@", records);
        out = out.replace("@@WHEN@@", when);
        out = out.replace("@@TIME@@", time(discovery, when));
        out = out.replace("@@META@@", meta(discovery));
        out = out.replace("@@FIELDS@@", fields);
        out = out.replace("@@XML@@", xml);
        out = out.replace("@@UNPACK@@", discovery.unpackJson()
                ? TEXT_UNPACK
                : "");
        out = out.replace("@@OURS@@", quoted(ours));
        return out;
    }

    private static String time(final Discovery discovery, final String when) {
        return JsonValues.truthy(discovery.timestampFormat())
                ? "stroom:format-date(string(" + when + "), '" + discovery.timestampFormat() + "')"
                : "string(" + when + ")";
    }

    private static String meta(final Discovery discovery) {
        final List<String> lines = new ArrayList<>();
        discovery.meta().forEach((name, attribute) -> lines.add("      <string key=\"" + name
                + "\"><xsl:value-of select=\"stroom:meta('" + attribute + "')\" /></string>"));
        return String.join("\n", lines);
    }

    /// @param dotted A JSON key, dotted for a nested one.
    /// @return An XPath to it in JSONParser's output: `event.created` -> `*[@key='event']/*[@key='created']`.
    static String jsonPath(final String dotted) {
        final List<String> steps = new ArrayList<>();
        for (final String part : dotted.split("\\.", -1)) {
            steps.add("*[@key=" + single(part) + "]");
        }
        return String.join("/", steps);
    }

    // A name as a JSON string in single quotes, as the server writes it.
    private static String single(final String name) {
        return PyJson.dumps(name).replace('"', '\'');
    }

    private static String quoted(final List<String> names) {
        return String.join(", ", names.stream().map(DiscoveryXslt::single).toList());
    }

    // The template with each token replaced at once, as an f-string's fields are.
    private static String substitute(final String template, final Map<String, String> values) {
        final Matcher matcher = TOKEN.matcher(template);
        final StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            final String value = values.get(matcher.group(1));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null
                    ? matcher.group()
                    : value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String resource(final String name) {
        try (InputStream in = DiscoveryXslt.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new UncheckedIOException(new IOException("No resource " + name));
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
