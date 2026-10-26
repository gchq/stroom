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

package stroom.ai.impl.extraction;

import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Writes the XSLT of a dashboard table's search extraction from the fields it gives (see [ExtractionField]): for
/// each event, a `records:2` record with the event's `StreamId` and `EventId` (which Stroom's id enrichment puts on
/// it) and each field's value, the first its XPath finds. That is what Stroom's search result output reads, by
/// name.
///
/// The fields are kept in a comment at the top of the XSLT, so it can be written again from them as they change
/// (see [#read(String)]). The model never writes the XSLT itself.
public final class ExtractionXslt {

    /// What starts the comment holding a generated XSLT's fields.
    static final String MARKER = "stroom-ai-extraction:";
    /// The name the namespace probe gives the events' namespace (see [#NAMESPACE_PROBE]).
    public static final String PROBE_NAMESPACE = "namespace";
    /// An XSLT giving the namespace of the events it is given, as a record with one value, named
    /// [#PROBE_NAMESPACE]: the namespace of the root element, which an Events stream's events share.
    public static final String NAMESPACE_PROBE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="2.0" xmlns="records:2" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:template match="/">
                <records version="2.0">
                  <record>
                    <data name="namespace">
                      <xsl:attribute name="value" select="namespace-uri(/*)"/>
                    </data>
                  </record>
                </records>
              </xsl:template>
            </xsl:stylesheet>
            """;

    private static final Pattern MARKED = Pattern.compile("<!--\\s*" + Pattern.quote(MARKER) + "\\s*(.*?)\\s*-->",
            Pattern.DOTALL);

    private ExtractionXslt() {
        // Static only.
    }

    /// Writes the XSLT.
    ///
    /// @param namespace The events' namespace (e.g. `event-logging:3`), or blank for none.
    /// @param fields    The fields, checked (see [ExtractionField#problems(List)]).
    /// @return The XSLT.
    public static String xslt(final String namespace, final List<ExtractionField> fields) {
        final String ns = NullSafe.string(namespace).strip();
        final StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!-- ").append(MARKER).append(' ')
                .append(JsonUtil.writeValueAsString(new Generated(ns, fields), false).replace("--", "-\\u002d"))
                .append(" -->\n");
        sb.append("<!-- Written by Stroom AI from the fields above: each event's StreamId and EventId, and each "
                  + "field's value,\n     the first its XPath finds. Stroom AI writes it again from them as they "
                  + "change. -->\n");
        sb.append("<xsl:stylesheet version=\"2.0\"");
        if (!ns.isEmpty()) {
            sb.append("\n    xpath-default-namespace=\"").append(escape(ns)).append('"');
        }
        sb.append("\n    xmlns=\"records:2\"\n    xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n"
                  + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n\n");
        sb.append("  <xsl:template match=\"/*\">\n");
        sb.append("    <records xsi:schemaLocation=\"records:2 file://records-v2.0.xsd\" version=\"2.0\">\n");
        sb.append("      <xsl:apply-templates select=\"*\"/>\n");
        sb.append("    </records>\n");
        sb.append("  </xsl:template>\n\n");
        sb.append("  <xsl:template match=\"/*/*\">\n");
        sb.append("    <record>\n");
        sb.append("      <data name=\"StreamId\" value=\"{@StreamId}\"/>\n");
        sb.append("      <data name=\"EventId\" value=\"{@EventId}\"/>\n");
        for (final ExtractionField field : fields) {
            sb.append("      <data name=\"").append(escape(field.name())).append("\">\n");
            sb.append("        <xsl:attribute name=\"value\" select=\"string((")
                    .append(escape(field.xpath())).append(")[1])\"/>\n");
            sb.append("      </data>\n");
        }
        sb.append("    </record>\n");
        sb.append("  </xsl:template>\n");
        sb.append("</xsl:stylesheet>\n");
        return sb.toString();
    }

    /// Reads what a generated XSLT was written from.
    ///
    /// @param xslt An extraction XSLT.
    /// @return The namespace and fields it was written from, if Stroom AI wrote it (from its comment), and it is
    /// as written; empty if it is someone's own, or has been changed by hand.
    public static Optional<Generated> read(final String xslt) {
        if (NullSafe.isBlankString(xslt)) {
            return Optional.empty();
        }
        final Matcher matcher = MARKED.matcher(xslt);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            final Generated generated = JsonUtil.readValue(matcher.group(1), Generated.class);
            if (generated == null || generated.fields() == null
                || !ExtractionField.problems(generated.fields()).isEmpty()) {
                return Optional.empty();
            }
            // Changed by hand: not what it was written from.
            return normalise(xslt(generated.namespace(), generated.fields())).equals(normalise(xslt))
                    ? Optional.of(generated)
                    : Optional.empty();
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }

    // XML text escaped for an attribute value in double quotes.
    static String escape(final String text) {
        return NullSafe.string(text)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String normalise(final String xslt) {
        return xslt.replace("\r\n", "\n").strip();
    }


    // --------------------------------------------------------------------------------


    /// What a generated extraction XSLT was written from.
    ///
    /// @param namespace The events' namespace, or blank for none.
    /// @param fields    The fields it gives.
    public record Generated(@JsonProperty("namespace") String namespace,
                            @JsonProperty("fields") List<ExtractionField> fields) {

        @JsonCreator
        public Generated {
            namespace = NullSafe.string(namespace);
            fields = NullSafe.list(fields);
        }
    }
}
