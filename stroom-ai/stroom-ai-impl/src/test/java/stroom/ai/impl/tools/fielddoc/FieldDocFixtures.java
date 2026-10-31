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

package stroom.ai.impl.tools.fielddoc;

import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemasWorld;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.Xslt30Transformer;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.transform.stream.StreamSource;

/// The documentation tests' fixtures (the generator tests' schema, base mapping and records), the golden outputs of
/// the field documentation and CEF, and a plain Saxon run of an XSLT.
public final class FieldDocFixtures {

    /// The event-logging 4.1.0 schema.
    public static final EventSchema SCHEMA = EventSchema.parse(HeldSchemasWorld.XSD_4_1_0);
    /// The common fields of the base mapping.
    public static final List<Map<String, Object>> BASE = json("""
            [{"path": "EventTime/TimeCreated", "field": "time"},
             {"path": "EventSource/System/Name", "value": "Acme VPN"},
             {"path": "EventSource/System/Environment", "value": "Dev"},
             {"path": "EventSource/Generator", "value": "vpnd"},
             {"path": "EventSource/Device/HostName", "field": "host", "default": "unknown"},
             {"path": "EventSource/User/Id", "field": "user"}]""");
    /// The logon rule's fields.
    public static final List<Map<String, Object>> LOGON = json("""
            [{"path": "EventDetail/TypeId", "value": "Logon"},
             {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
             {"path": "EventDetail/Authenticate/User/Id", "field": "user"},
             {"path": "EventDetail/Authenticate/Outcome/Success", "field": "result",
              "map": {"ok": "true", "fail": "false"}},
             {"path": "EventDetail/Authenticate/Data", "data_name": "session", "field": "sid"}]""");
    /// Two records, a login and a keepalive.
    public static final String RECORDS = """
            <records xmlns="records:2">
            <record><data name="time" value="2026-09-28T10:00:00.000Z"/><data name="action" value="login"/>\
            <data name="user" value="o'neil"/>
            <data name="result" value="fail"/><data name="sid" value="s1"/></record>
            <record><data name="time" value="2026-09-28T10:01:00.000Z"/><data name="action" value="keepalive"/>\
            <data name="host" value="ws01"/></record>
            </records>""";

    // Plain Saxon has no Stroom functions: the log calls (unmatched and kept-Unknown records) do nothing here.
    private static final String STROOM_STUBS = "<xsl:function name=\"stroom:log\"><xsl:param name=\"level\"/>"
                                               + "<xsl:param name=\"message\"/><xsl:sequence select=\"()\"/>"
                                               + "</xsl:function><xsl:function name=\"stroom:record-no\">"
                                               + "<xsl:sequence select=\"0\"/></xsl:function>";

    private FieldDocFixtures() {
        // Static only.
    }

    /// @param overrides Keys replaced in the base mapping.
    /// @return The base mapping with them (`mapping(**overrides)`).
    public static TranslationMapping mapping(final Map<String, Object> overrides) {
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("input", "data_splitter");
        mapping.put("unmatched", "skip");
        mapping.put("common", BASE);
        mapping.put("events", json("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}], "fields": %s},
                 {"name": "other", "fields": [{"path": "EventDetail/TypeId", "field": "action"},
                   {"path": "EventDetail/Unknown/Data", "data_name": "action", "field": "action"}]}]"""
                .formatted(ToolJson.dumps(LOGON))));
        mapping.putAll(overrides);
        return TranslationMapping.fromJson(mapping);
    }

    /// @return The base mapping.
    public static TranslationMapping mapping() {
        return mapping(Map.of());
    }

    /// @param text JSON.
    /// @param <T>  What it is.
    /// @return It read by [ToolJson].
    @SuppressWarnings("unchecked")
    public static <T> T json(final String text) {
        return (T) ToolJson.loads(text);
    }

    /// @param name A golden file's path below `stroom/ai/impl/golden/`, e.g. `cef/drafted.md`.
    /// @return Its text.
    public static String golden(final String name) {
        try (final InputStream in = FieldDocFixtures.class.getResourceAsStream("/stroom/ai/impl/golden/" + name)) {
            if (in == null) {
                throw new IllegalStateException("No golden file " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// @param name A golden JSON file's path below `stroom/ai/impl/golden/`.
    /// @param <T>  What it holds.
    /// @return It read.
    public static <T> T goldenJson(final String name) {
        return json(golden(name));
    }

    /// @param xslt An XSLT.
    /// @param xml  What it runs over.
    /// @return What it writes, serialised as the XSLT's output says.
    public static String transform(final String xslt, final String xml) {
        String code = xslt;
        if (code.contains("stroom:log(") && !code.contains("name=\"stroom:log\"")) {
            code = code.replace("</xsl:stylesheet>", STROOM_STUBS + "</xsl:stylesheet>");
        }
        try {
            final Processor processor = new Processor(false);
            final Xslt30Transformer transformer = processor.newXsltCompiler()
                    .compile(new StreamSource(new StringReader(code), "urn:main"))
                    .load30();
            final XdmNode source = processor.newDocumentBuilder().build(new StreamSource(new StringReader(xml)));
            final StringWriter out = new StringWriter();
            final Serializer serializer = processor.newSerializer(out);
            transformer.applyTemplates(source, serializer);
            return out.toString();
        } catch (final SaxonApiException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
