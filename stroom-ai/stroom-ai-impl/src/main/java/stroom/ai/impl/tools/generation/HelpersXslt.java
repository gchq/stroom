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

import java.util.LinkedHashMap;
import java.util.Map;

/// The feed's helpers module (`<feed> helpers`): the generator's helper functions (`mcp:json-to-xml`,
/// `mcp:parse-xml`, `mcp:zone`), written once for an aggregated feed and imported by its common module, which each
/// source type group imports; never inline in a group, so every group of a feed runs one copy of them. Versioned with
/// the generator: [#VERSION] changes whenever what it writes does, and a build writes it again when the saved one
/// differs.
public final class HelpersXslt {

    /// The version of the helpers this generator writes, in the module's mark.
    public static final int VERSION = 1;
    /// What a feed's helpers module is named after.
    public static final String SUFFIX = " helpers";

    private HelpersXslt() {
        // Static only.
    }

    /// @param feed A feed's name.
    /// @return The name of its helpers module.
    public static String docName(final String feed) {
        return feed + SUFFIX;
    }

    /// @return The helpers module's XSLT, the same to the byte for a version.
    public static String generate() {
        final Map<String, String> namespaces = new LinkedHashMap<>();
        namespaces.put("xsl", XmlTrees.XSL);
        namespaces.put("xs", MappingFacts.XS);
        namespaces.put("mcp", MappingFacts.MCP_NS);
        final XmlElement sheet = XmlElement.create(XmlTrees.XSL, "stylesheet", namespaces);
        sheet.set("version", "3.0");
        sheet.set("exclude-result-prefixes", "xs mcp");
        sheet.addElement(MappingFacts.MCP_NS, ModuleChecks.MARKER, "kind", ModuleChecks.HELPERS, "name", "helpers",
                "mode", "mcp", "version", String.valueOf(VERSION));
        sheet.append(new XmlComment(XsltComments.comment("The generator's helper functions, imported by each source "
                                                         + "type group of the feed and its common module. Generated: "
                                                         + "changed only by the tools, with the generator")));
        sheet.append(XmlParser.parse(XsltGenerator.SAFE_JSON));
        sheet.append(XmlParser.parse(XsltGenerator.SAFE_XML));
        sheet.append(XsltGenerator.zoneFunction());
        return XmlSerialiser.toPrettyDocument(sheet);
    }

    /// @param xslt A helpers module's code.
    /// @return True if it is what this generator writes.
    public static boolean isCurrent(final String xslt) {
        if (xslt == null) {
            return false;
        }
        try {
            return XmlSerialiser.toPrettyDocument(XmlParser.parse(xslt)).equals(generate());
        } catch (final RuntimeException e) {
            // Not XML: not what the generator writes.
            return false;
        }
    }
}
