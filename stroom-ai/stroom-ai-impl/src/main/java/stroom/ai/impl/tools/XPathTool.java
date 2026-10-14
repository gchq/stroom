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

package stroom.ai.impl.tools;

import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SafeXPath;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.json.JsonUtil;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.inject.Inject;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code xpath} - queries XML in the workspace, such as event XML, with an XPath expression, using a locked-down
 * engine (see {@link SafeXPath}).
 */
class XPathTool extends AbstractWorkspaceTool {

    static final String NAME = "xpath";
    static final String EXPRESSION_ARG = "expression";
    static final String NAMESPACES_ARG = "namespaces";
    static final String AS_XML_ARG = "asXml";
    /**
     * The prefix given to the document's own namespace, if it has one and no prefix is given for it.
     */
    static final String DEFAULT_PREFIX = "d";

    @Inject
    XPathTool(final ChatWorkspace workspace) {
        super(workspace);
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Runs an XPath 3.1 expression on an XML file in the workspace, e.g. "
                             + "'//d:Event/d:EventSource/d:Device/d:HostName'. If the document is in a namespace, "
                             + "it is bound to the prefix '" + DEFAULT_PREFIX + "' unless you give namespaces. "
                             + "Each result is one line: its text, or its XML if asXml is true. Give out to "
                             + "write the results to a file. Only the document can be read, and regular "
                             + "expression functions are not available.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(PATH_ARG, "The XML file.")
                        .addStringProperty(EXPRESSION_ARG, "The XPath expression.")
                        .addProperty(NAMESPACES_ARG, JsonObjectSchema.builder()
                                .description("Namespace prefixes to use, e.g. {\"e\": \"event-logging:3\"}.")
                                .build())
                        .addBooleanProperty(AS_XML_ARG, "True to give element results as XML.")
                        .addStringProperty(OUT_ARG, "A file in /outputs/ to write the results to.")
                        .required(PATH_ARG, EXPRESSION_ARG)
                        .build())
                .build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        final WorkspacePath path = pathArg(request, PATH_ARG);
        final WorkspacePath out = outArg(request);
        final XdmNode document = SafeXPath.parse(workspace.localFile(chatId, path));
        final Map<String, String> namespaces = namespaces(request);
        final String documentNamespace = documentNamespace(document);
        if (documentNamespace != null && !namespaces.containsValue(documentNamespace)) {
            namespaces.putIfAbsent(DEFAULT_PREFIX, documentNamespace);
        }
        final XPathExecutable executable = SafeXPath.compile(rawArg(request, EXPRESSION_ARG), namespaces);
        final boolean asXml = booleanArg(request, AS_XML_ARG, false);

        final List<String> results = new ArrayList<>();
        final long[] total = {0};
        final Path temp = out != null
                ? workspace.tempFile()
                : null;
        try (final BufferedWriter writer = temp != null
                ? Files.newBufferedWriter(temp, StandardCharsets.UTF_8)
                : null) {
            SafeXPath.evaluate(executable, document, asXml, result -> {
                total[0]++;
                final String line = result.replace('\n', ' ');
                if (writer != null) {
                    try {
                        writer.write(line);
                        writer.newLine();
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                } else if (results.size() < limits().getMaxLinesPerRead()) {
                    results.add(line);
                }
            });
            if (writer != null) {
                writer.close();
                workspace.write(chatId, out, temp, workspace.sourceDocUuid(chatId, path));
                return "Wrote " + total[0] + " result(s) to " + out + ".";
            }
            return boundedLines(results, total[0]);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (temp != null) {
                ChatWorkspace.deleteQuietly(temp);
            }
        }
    }

    private Map<String, String> namespaces(final ToolExecutionRequest request) {
        final Map<String, String> namespaces = new LinkedHashMap<>();
        try {
            final Map<?, ?> args = JsonUtil.readValue(request.arguments(), Map.class);
            if (args != null && args.get(NAMESPACES_ARG) instanceof final Map<?, ?> given) {
                given.forEach((prefix, uri) -> namespaces.put(String.valueOf(prefix), String.valueOf(uri)));
            }
        } catch (final RuntimeException e) {
            // No namespaces given.
        }
        return namespaces;
    }

    private static String documentNamespace(final XdmNode document) {
        for (final XdmNode child : document.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                final String uri = child.getNodeName().getNamespaceURI();
                return uri == null || uri.isEmpty()
                        ? null
                        : uri;
            }
        }
        return null;
    }
}
