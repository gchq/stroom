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

import stroom.ai.impl.tools.sample.PyJson;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.transform.stream.StreamSource;

/// What the indexing tests share: the Elastic Common Schema as Stroom bundles it, a plain Saxon to run the
/// indexing XSLTs with (as the Stroom MCP server's tests ran them with SaxonC), and the test resources.
final class IndexingFixtures {

    /// Where the ECS files are bundled (stroom-mcp-impl's resources), from the stroom-ai folder.
    static final String BUNDLED = "stroom-mcp-impl/src/main/resources/stroom/mcp/impl/knowledge/conventions";
    /// The Elastic Common Schema, as bundled.
    static final EcsSchema ECS = EcsSchema.parse(bundled(EcsSchema.FIELDS_FILE), bundled(EcsSchema.COMPONENT_FILE));

    private static final Pattern SPACE_BETWEEN = Pattern.compile(">\\s+<");
    private static final Pattern NOISE = Pattern.compile(
            "<\\?xml[^>]*\\?>|\\s+(xmlns(:\\w+)?|xsi:schemaLocation)=\"[^\"]*\"");

    private IndexingFixtures() {
        // Static only.
    }

    /// @param plan    A plan.
    /// @param backend Another backend.
    /// @return The plan for it.
    static FieldPlan withBackend(final FieldPlan plan, final String backend) {
        return new FieldPlan(backend, plan.indexName(), plan.timeField(), plan.fields(), plan.dropWhen(),
                plan.discovery(), plan.imports(), plan.shared(), plan.subobjects(), plan.convention(),
                plan.customNamespaces(), plan.style());
    }

    /// @param plan       A plan.
    /// @param subobjects The index template's subobjects setting.
    /// @return The plan with it.
    static FieldPlan withSubobjects(final FieldPlan plan, final boolean subobjects) {
        return new FieldPlan(plan.backend(), plan.indexName(), plan.timeField(), plan.fields(), plan.dropWhen(),
                plan.discovery(), plan.imports(), plan.shared(), subobjects, plan.convention(),
                plan.customNamespaces(), plan.style());
    }

    /// @param plan     A plan.
    /// @param dropWhen Tests on an Event for events the index must not hold.
    /// @return The plan dropping them.
    static FieldPlan withDropWhen(final FieldPlan plan, final List<String> dropWhen) {
        return new FieldPlan(plan.backend(), plan.indexName(), plan.timeField(), plan.fields(), dropWhen,
                plan.discovery(), plan.imports(), plan.shared(), plan.subobjects(), plan.convention(),
                plan.customNamespaces(), plan.style());
    }

    /// @param name A file bundled beside the convention profiles.
    /// @return Its text, read from stroom-mcp-impl's resources (the one copy Stroom bundles), found from the
    /// directory the tests run in.
    static String bundled(final String name) {
        for (Path at = Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
            for (final Path folder : List.of(at.resolve(BUNDLED), at.resolve("stroom-ai").resolve(BUNDLED))) {
                if (Files.isRegularFile(folder.resolve(name))) {
                    try {
                        return Files.readString(folder.resolve(name), StandardCharsets.UTF_8);
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
        }
        throw new IllegalStateException("No bundled " + name + " found above " + Path.of("").toAbsolutePath());
    }

    /// @param path A test resource's path.
    /// @return Its text.
    static String resource(final String path) {
        try (InputStream in = IndexingFixtures.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("No resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// @param json A JSON text.
    /// @return It as JSON-like values.
    @SuppressWarnings("unchecked")
    static Map<String, Object> json(final String json) {
        return (Map<String, Object>) PyJson.loads(json);
    }

    /// @param xslt An XSLT.
    /// @param xml  What it reads.
    /// @return What it writes, as text.
    static String transform(final String xslt, final String xml) {
        try {
            final Processor processor = new Processor(false);
            final Xslt30Transformer transformer = processor.newXsltCompiler()
                    .compile(new StreamSource(new StringReader(xslt), "urn:main"))
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

    /// @param output What an XSLT wrote.
    /// @return It without the declaration, namespaces and space between elements, as the Python tests compared it.
    static String compact(final String output) {
        return SPACE_BETWEEN.matcher(NOISE.matcher(output).replaceAll("")).replaceAll("><").strip();
    }

    /// @param xml JSON as XML (xpath-functions).
    /// @return The JSON it stands for, as Stroom's Elasticsearch indexing filter reads it.
    static String xmlToJson(final String xml) {
        try {
            final Processor processor = new Processor(false);
            final XdmNode source = processor.newDocumentBuilder().build(new StreamSource(new StringReader(xml)));
            final net.sf.saxon.s9api.XPathSelector selector = processor.newXPathCompiler()
                    .compile("xml-to-json(/*)")
                    .load();
            selector.setContextItem(source);
            return selector.evaluateSingle().getStringValue();
        } catch (final SaxonApiException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
