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

package stroom.ai.impl.tools.cef;

import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XmlSerialiser;
import stroom.ai.impl.tools.validation.LockedSaxon;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.Xslt30Transformer;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import javax.xml.transform.stream.StreamSource;

/// The CEF lines a plan's XSLT writes for sample Events, run with the locked-down Saxon processor (no resource is
/// read), for draft_cef_mapping's example line, documentation examples and its review of its own lines (the Stroom
/// MCP server ran the plan's XSLT with saxonche for the same).
public final class CefSample {

    private CefSample() {
        // Static only.
    }

    /// @param plan   A plan.
    /// @param events Sample Events.
    /// @param cap    The most Events to run it over.
    /// @return The CEF lines the plan's XSLT writes for them ([CefLines#linesIn(String)]).
    /// @throws IllegalArgumentException if the plan's XSLT isn't written (an unsafe expression).
    /// @throws IllegalStateException    if it doesn't compile or run.
    public static List<String> lines(final CefPlan plan, final List<XmlElement> events, final int cap) {
        final StringBuilder xml = new StringBuilder("<Events xmlns=\"" + CefKeys.EL + "\">");
        events.subList(0, Math.min(cap, events.size()))
                .forEach(event -> xml.append(XmlSerialiser.toXmlWithoutTail(event)));
        xml.append("</Events>");
        return CefLines.linesIn(transform(plan.xslt(), xml.toString()));
    }

    /// @param xslt An XSLT.
    /// @param xml  A document.
    /// @return What the XSLT writes for it.
    /// @throws IllegalStateException if it doesn't compile or run.
    static String transform(final String xslt, final String xml) {
        try {
            final Processor processor = LockedSaxon.processor();
            final Xslt30Transformer transformer = processor.newXsltCompiler()
                    .compile(new StreamSource(new StringReader(xslt)))
                    .load30();
            final XdmNode source = LockedSaxon.parse(xml);
            final StringWriter out = new StringWriter();
            final Serializer serializer = processor.newSerializer(out);
            transformer.applyTemplates(source, serializer);
            return out.toString();
        } catch (final SaxonApiException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
