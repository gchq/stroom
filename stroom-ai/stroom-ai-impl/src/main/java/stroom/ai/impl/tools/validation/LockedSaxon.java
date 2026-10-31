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

package stroom.ai.impl.tools.validation;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.EnvironmentVariableResolver;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;

/// The Saxon processor the tools evaluate XPath with on sample records, locked down: it reads nothing of the
/// server's. What it runs is not the tools' own: expressions come from the model, and documents from the samples,
/// written by whoever the logs record. Saxon as it comes reads whatever the process can: doc() and unparsed-text()
/// open local files and URLs, an XML sample's external entity pulls a local file into the record, and
/// environment-variable() reads the process's settings.
///
/// So the processor refuses every URI (no file, no network, no collection, no unparsed text), sees no environment
/// variable or system property (Saxon's external functions are off: system-property() of a name in no namespace
/// would otherwise read the JVM's; integrated functions, as Stroom's are, still work), and its documents load no
/// external entity or DTD. [XsltSafety#refused(String, java.util.Map)] stops
/// such expressions before they reach it too. It may be made and used on any thread.
public final class LockedSaxon {

    /// What a refused URI is said to be.
    public static final String PROHIBITED = "Reading a resource is prohibited here";
    /// How many times a regex may backtrack in one sequence before Saxon stops it (Saxon's default is ten million).
    public static final int REGEX_BACKTRACKING_LIMIT = 1_000_000;

    private static final Processor PROCESSOR = newLocked();

    private LockedSaxon() {
        // Static only.
    }

    /// @return The one locked-down processor. It is safe to share between threads.
    public static Processor processor() {
        return PROCESSOR;
    }

    /// @param xml An XML document's text, e.g. a sample record.
    /// @return It as a Saxon document, read without any external entity or DTD (a DOCTYPE's internal entities are
    /// still expanded).
    /// @throws SaxonApiException if it is not well-formed.
    public static XdmNode parse(final String xml) throws SaxonApiException {
        final DocumentBuilder builder = PROCESSOR.newDocumentBuilder();
        return builder.build(new SAXSource(reader(), new InputSource(new StringReader(xml))));
    }

    /// @param defaultNamespace The namespace unprefixed element names are in, or null or "" for none.
    /// @return An XPath compiler of the locked-down processor, with that default namespace.
    public static XPathCompiler xpath(final String defaultNamespace) {
        final XPathCompiler compiler = PROCESSOR.newXPathCompiler();
        if (defaultNamespace != null && !defaultNamespace.isEmpty()) {
            compiler.declareNamespace("", defaultNamespace);
        }
        return compiler;
    }

    /// @param regex An XPath regular expression.
    /// @param flags Its flags, e.g. `i`.
    /// @return It compiled as XPath's `matches()` reads it.
    /// @throws XPathException if it is not a valid XPath regular expression.
    public static RegularExpression regex(final String regex, final String flags) throws XPathException {
        final List<String> warnings = new ArrayList<>();
        return PROCESSOR.getUnderlyingConfiguration().compileRegularExpression(regex, flags, "XP30", warnings);
    }

    /// A new processor, locked down as the shared one is, for a caller that registers functions of its own on it
    /// (e.g. [XsltCompileCheck], with Stroom's).
    ///
    /// @return A new locked-down processor.
    static Processor newLocked() {
        final Processor processor = new Processor(false);
        final Configuration config = processor.getUnderlyingConfiguration();
        config.setConfigurationProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, false);
        // Saxon's regex engine checks no time limit (it copies the text before matching it); its backtracking
        // limit is what stops a model's regex (in matches(), or an extraction's) that backtracks badly.
        config.setConfigurationProperty(Feature.REGEX_BACKTRACKING_LIMIT, REGEX_BACKTRACKING_LIMIT);
        config.setURIResolver((href, base) -> {
            throw new TransformerException(PROHIBITED + ": " + href);
        });
        config.setUnparsedTextURIResolver((uri, encoding, configuration) -> {
            throw new XPathException(PROHIBITED + ": " + uri);
        });
        config.setCollectionFinder((context, collectionURI) -> {
            throw new XPathException(PROHIBITED + ": " + collectionURI);
        });
        config.setConfigurationProperty(Feature.ENVIRONMENT_VARIABLE_RESOLVER, new NoEnvironment());
        config.getParseOptions().addParserFeature("http://xml.org/sax/features/external-general-entities", false);
        config.getParseOptions().addParserFeature("http://xml.org/sax/features/external-parameter-entities",
                false);
        config.getParseOptions().addParserFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return processor;
    }

    /// @return An XML reader that loads no external entity or DTD (one still asked for resolves to nothing).
    static XMLReader reader() {
        try {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            final XMLReader reader = factory.newSAXParser().getXMLReader();
            // An external entity that is still asked for resolves to nothing.
            reader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            return reader;
        } catch (final ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("Unable to make an XML reader: " + e.getMessage(), e);
        }
    }


    // --------------------------------------------------------------------------------


    // Environment variables, as the locked-down processor sees them: none.
    private static final class NoEnvironment implements EnvironmentVariableResolver {

        @Override
        public Set<String> getAvailableEnvironmentVariables() {
            return Set.of();
        }

        @Override
        public String getEnvironmentVariable(final String name) {
            return null;
        }
    }
}
