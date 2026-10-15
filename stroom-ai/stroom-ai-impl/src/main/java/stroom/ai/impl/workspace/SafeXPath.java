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

package stroom.ai.impl.workspace;

import stroom.util.xml.SAXParserFactoryFactory;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.EnvironmentVariableResolver;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;

/// Runs XPath expressions given by the model on XML in the workspace, locked down:
///
/// - XML is parsed with DOCTYPE declarations refused, so there are no external entities, external DTDs or entity
///   expansion attacks (XXE), and with XInclude off;
/// - nothing outside the document can be read: {@code doc()}, {@code collection()}, {@code unparsed-text()} and
///   similar are refused, both by name and by resolvers that refuse everything, and there are no environment
///   variables and no extension functions;
/// - there are no regular expression functions, as their matching cannot be stopped part way (use grep or the
///   table tool);
/// - results are read with the tool call's time limit checked.
public final class SafeXPath {

    static final int MAX_EXPRESSION_LENGTH = 2_000;

    private static final Pattern FORBIDDEN_FUNCTIONS = Pattern.compile(
            "(?<![\\w.-])(matches|replace|tokenize|analyze-string|doc|doc-available|collection|uri-collection"
            + "|unparsed-text|unparsed-text-lines|unparsed-text-available|environment-variable"
            + "|available-environment-variables|function-lookup|parse-xml|parse-xml-fragment|json-doc"
            + "|load-xquery-module|transform|system-property)\\s*[(#]");

    private static final Processor PROCESSOR = createProcessor();

    private SafeXPath() {
        // Static only.
    }

    private static Processor createProcessor() {
        final Processor processor = new Processor(false);
        final Configuration config = processor.getUnderlyingConfiguration();
        config.setConfigurationProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, false);
        config.setConfigurationProperty(Feature.XINCLUDE, false);
        config.setConfigurationProperty(Feature.DTD_VALIDATION, false);
        config.setConfigurationProperty(Feature.ENVIRONMENT_VARIABLE_RESOLVER, new EnvironmentVariableResolver() {
            @Override
            public Set<String> getAvailableEnvironmentVariables() {
                return Set.of();
            }

            @Override
            public String getEnvironmentVariable(final String name) {
                return null;
            }
        });
        config.setURIResolver((href, base) -> {
            throw new TransformerException("Reading other documents is not allowed");
        });
        config.setCollectionFinder((context, uri) -> {
            throw new XPathException("Reading collections is not allowed");
        });
        config.setUnparsedTextURIResolver((uri, encoding, configuration) -> {
            throw new XPathException("Reading other files is not allowed");
        });
        return processor;
    }

    /// Parses XML from a workspace file.
    ///
    /// @param file The file.
    /// @return The document.
    /// @throws WorkspaceException if it is not well-formed XML, or has a DOCTYPE.
    public static XdmNode parse(final Path file) {
        try (final InputStream inputStream = Files.newInputStream(file)) {
            final DocumentBuilder builder = PROCESSOR.newDocumentBuilder();
            builder.setDTDValidation(false);
            return builder.build(new SAXSource(secureReader(), new InputSource(inputStream)));
        } catch (final SaxonApiException e) {
            throw new WorkspaceException("The file is not XML that can be read: " + e.getMessage()
                                         + " (DOCTYPE declarations are not allowed.)");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// @return An XML reader that refuses a DOCTYPE and never loads anything from outside the document.
    public static XMLReader secureReader() {
        try {
            final SAXParserFactory factory = SAXParserFactoryFactory.newInstance();
            // Set here whatever the parser config says, as nothing from the model's input should need them.
            factory.setFeature(SAXParserFactoryFactory.FEATURE_DISALLOW_DOCTYPE, true);
            factory.setFeature(SAXParserFactoryFactory.FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
            factory.setFeature(SAXParserFactoryFactory.FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
            factory.setFeature(SAXParserFactoryFactory.FEATURE_LOAD_EXTERNAL_DTD, false);
            factory.setXIncludeAware(false);
            final XMLReader reader = factory.newSAXParser().getXMLReader();
            reader.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External entities are not allowed");
            });
            return reader;
        } catch (final ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("Unable to create a secure XML parser", e);
        }
    }

    /// @param expression An XPath expression.
    /// @param namespaces Namespace prefixes to declare, and their URIs.
    /// @return The expression, compiled.
    /// @throws WorkspaceException if it is not allowed or not valid.
    public static XPathExecutable compile(final String expression, final Map<String, String> namespaces) {
        if (expression == null || expression.isBlank()) {
            throw new WorkspaceException("Give an XPath expression, e.g. //Event/EventSource/Device/HostName.");
        }
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            throw new WorkspaceException("The XPath expression may be at most " + MAX_EXPRESSION_LENGTH
                                         + " characters.");
        }
        if (FORBIDDEN_FUNCTIONS.matcher(expression).find()) {
            throw new WorkspaceException("The XPath expression uses a function that is not available here: "
                                         + "nothing outside the document can be read, and regular expression "
                                         + "functions are not available (use grep or the table tool).");
        }
        return compileWithoutFunctionCheck(expression, namespaces);
    }

    /// Compiles an expression without checking the functions it names. The engine still refuses to read anything
    /// outside the document; this is separate so that can be tested on its own.
    static XPathExecutable compileWithoutFunctionCheck(final String expression, final Map<String, String> namespaces) {
        final XPathCompiler compiler = PROCESSOR.newXPathCompiler();
        namespaces.forEach(compiler::declareNamespace);
        try {
            return compiler.compile(expression);
        } catch (final SaxonApiException e) {
            throw new WorkspaceException("'" + expression + "' is not a valid XPath expression: " + e.getMessage());
        }
    }

    /// @param node A node of a document, e.g. one event of many.
    /// @return A copy of the node as a document of its own, so that an expression evaluated against it (even one
    /// starting //) only sees that node.
    public static XdmNode asDocument(final XdmNode node) {
        try {
            return PROCESSOR.newDocumentBuilder().build(node.asSource());
        } catch (final SaxonApiException e) {
            throw new WorkspaceException("Unable to read an event: " + e.getMessage());
        }
    }

    /// Evaluates an expression against a document.
    ///
    /// @param executable The compiled expression.
    /// @param document   The document.
    /// @param asXml      True to give nodes as XML, false to give their text.
    /// @param output     Given each result.
    public static void evaluate(final XPathExecutable executable,
                                final XdmNode document,
                                final boolean asXml,
                                final Consumer<String> output) {
        try {
            final XPathSelector selector = executable.load();
            selector.setContextItem(document);
            for (final XdmItem item : selector) {
                ToolDeadline.check();
                if (asXml && item instanceof final XdmNode node && node.getNodeKind() != XdmNodeKind.TEXT
                    && node.getNodeKind() != XdmNodeKind.ATTRIBUTE) {
                    output.accept(node.toString());
                } else {
                    output.accept(item.getStringValue());
                }
            }
        } catch (final SaxonApiException | RuntimeException e) {
            if (e instanceof final WorkspaceException workspaceException) {
                throw workspaceException;
            }
            throw new WorkspaceException("The XPath expression failed: " + e.getMessage());
        }
    }
}
