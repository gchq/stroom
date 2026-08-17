/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.query.language.functions;

import stroom.query.language.functions.ref.StoredValues;
import stroom.util.xml.XMLReaderPool;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

import java.io.StringReader;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.xml.XMLConstants;
import javax.xml.transform.sax.SAXSource;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = XPath.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValString.class,
        commonReturnDescription = "The concatenated string values of all the items matched by the expression.",
        signatures = @FunctionSignature(
                description = "Extracts values from an XML string using an XPath 3.1 expression. The string values " +
                              "of all matched items are concatenated in document order. If no namespace prefix " +
                              "mappings are supplied then the XML is treated as being namespace unaware, i.e. " +
                              "element and attribute names are matched on their local name only.",
                args = {
                        @FunctionArg(
                                name = "xml",
                                description = "The XML string to evaluate.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "xpath",
                                description = "The XPath expression to use for extraction.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "namespaces",
                                description = "A space delimited list of namespace prefix to URI mappings in the " +
                                              "form 'prefix:uri prefix2:uri2'. A mapping with no prefix, e.g. " +
                                              "':uri', sets the default element namespace. If this argument is " +
                                              "omitted or empty then namespaces in the XML are ignored.",
                                argType = ValString.class,
                                isOptional = true),
                        @FunctionArg(
                                name = "delimiter",
                                description = "The delimiter to insert between the values of each matched item. " +
                                              "Defaults to no delimiter.",
                                argType = ValString.class,
                                isOptional = true)}))
class XPath extends AbstractManyChildFunction {

    static final String NAME = "xpath";

    private static final int XML_ARG = 0;
    private static final int XPATH_ARG = 1;
    private static final int NAMESPACES_ARG = 2;
    private static final int DELIMITER_ARG = 3;

    private static final Processor PROCESSOR = new Processor(false);
    // Borrow a reader per document rather than creating one for every row. Saxon has its own parser pool but it
    // builds parsers with plain JAXP, i.e. without the hardening that SAXParserFactoryFactory applies.
    private static final XMLReaderPool READER_POOL = XMLReaderPool.getDefault();

    private Generator gen;
    private boolean simple;

    public XPath(final String name) {
        super(name, 2, 4);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        // See if this is a static computation.
        simple = true;
        for (final Param param : params) {
            if (!(param instanceof Val)) {
                simple = false;
                break;
            }
        }

        if (simple) {
            // Static computation. Any bad arguments are reported as an error value rather than a parse failure as
            // all of the arguments, including the XML, are known at this point.
            gen = new StaticValueFunction(staticEval(params)).createGenerator();

        } else {
            // Validate whatever we can up front so the user gets told about bad arguments at parse time. Note that
            // the expression itself is compiled once per search by the generator, not here, as arguments supplied
            // as query parameters only become constant after the parameters have been statically mapped.
            String namespaces = null;
            if (params.length <= NAMESPACES_ARG) {
                namespaces = "";
            } else if (params[NAMESPACES_ARG] instanceof Val) {
                namespaces = params[NAMESPACES_ARG].toString();
                // Check the mappings are well formed.
                parseNamespaces(namespaces, name);
            }

            if (params[XPATH_ARG] instanceof Val) {
                final String xpathPattern = params[XPATH_ARG].toString();
                if (xpathPattern.isEmpty()) {
                    throw new ParseException(
                            "An empty XPath expression has been defined for second argument of '" + name
                            + "' function", 0);
                }

                if (namespaces != null) {
                    // The namespaces are known so we can fully validate the expression.
                    try {
                        compile(xpathPattern, namespaces);
                    } catch (final SaxonApiException e) {
                        throw new ParseException("Error in XPath expression: " + getMessage(e), 0);
                    }
                }
            }
        }
    }

    private Val staticEval(final Param[] params) {
        final Val valXml = (Val) params[XML_ARG];
        if (!valXml.type().isValue()) {
            return valXml;
        }

        final String xpathPattern = params[XPATH_ARG].toString();
        if (xpathPattern.isEmpty()) {
            return ValErr.create("An empty XPath expression has been defined for second argument of '" + name
                                 + "' function");
        }

        final Compiled compiled;
        try {
            compiled = compile(xpathPattern, getArg(params, NAMESPACES_ARG));
        } catch (final ParseException e) {
            return ValErr.create(e.getMessage());
        } catch (final SaxonApiException e) {
            return ValErr.create("Error in XPath expression: " + getMessage(e));
        }

        return evaluate(compiled, valXml.toString(), getArg(params, DELIMITER_ARG));
    }

    private static String getArg(final Param[] params, final int index) {
        return params.length > index
                ? params[index].toString()
                : "";
    }

    /**
     * Parses namespace prefix to URI mappings supplied in the form {@code prefix:uri prefix2:uri2}. A mapping with
     * no prefix, e.g. {@code :uri}, binds the default element namespace.
     */
    private static Map<String, String> parseNamespaces(final String namespaces,
                                                      final String functionName) throws ParseException {
        final Map<String, String> map = new LinkedHashMap<>();
        if (namespaces != null) {
            for (final String mapping : namespaces.trim().split("\\s+")) {
                if (!mapping.isEmpty()) {
                    // Prefixes cannot contain a colon so the first colon always separates the prefix from the URI.
                    final int index = mapping.indexOf(':');
                    if (index == -1 || index == mapping.length() - 1) {
                        throw new ParseException(
                                "Namespaces must be provided as space delimited 'prefix:uri' mappings in '"
                                + functionName + "' function but found '" + mapping + "'", 0);
                    }
                    map.put(mapping.substring(0, index), mapping.substring(index + 1));
                }
            }
        }
        return map;
    }

    /**
     * Compiles the expression. This is only ever done once per set of argument values, not once per row.
     */
    private static Compiled compile(final String xpathPattern,
                                    final String namespaces) throws ParseException, SaxonApiException {
        final Map<String, String> map = parseNamespaces(namespaces, NAME);
        final XPathCompiler compiler = PROCESSOR.newXPathCompiler();
        // A zero length prefix declares the default namespace for elements and types.
        map.forEach(compiler::declareNamespace);
        // If no namespaces have been declared then we parse the XML without namespaces so that expressions match
        // on local names alone.
        return new Compiled(xpathPattern, namespaces, compiler.compile(xpathPattern), !map.isEmpty());
    }

    /**
     * Evaluates the expression and concatenates the string values of all the items it matches.
     */
    private static Val evaluate(final Compiled compiled,
                                final String xml,
                                final String delimiter) {
        try {
            final XPathSelector selector = compiled.executable().load();
            selector.setContextItem(buildDocument(xml, compiled.namespaceAware()));

            final StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (final XdmItem item : selector) {
                if (!first) {
                    sb.append(delimiter);
                }
                sb.append(item.getStringValue());
                first = false;
            }
            return ValString.create(sb.toString());

        } catch (final Exception e) {
            return ValErr.create(getMessage(e));
        }
    }

    private static XdmNode buildDocument(final String xml,
                                         final boolean namespaceAware) throws Exception {
        // Always parse with a reader of our own so that DOCTYPE declarations and external entities are disabled.
        return READER_POOL.use(reader -> {
            final XMLReader source = namespaceAware
                    ? reader
                    : new NamespaceStrippingFilter(reader);
            final DocumentBuilder documentBuilder = PROCESSOR.newDocumentBuilder();
            return documentBuilder.build(new SAXSource(source, new InputSource(new StringReader(xml))));
        });
    }

    private static String getMessage(final Exception e) {
        return e.getMessage() != null
                ? e.getMessage()
                : e.toString();
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }
        return super.createGenerator();
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators);
    }

    @Override
    public boolean hasAggregate() {
        if (simple) {
            return false;
        }
        return super.hasAggregate();
    }


    // --------------------------------------------------------------------------------


    private static final class Gen extends AbstractManyChildGenerator {

        // The expression compiled once when the generator is created, i.e. once per column per search. Only
        // available where the expression and the namespaces are constant, which is the normal case. This is final
        // so that it is safely published to the other threads that share this generator, as a thread seeing null
        // would re-compile the expression for every row.
        private final Compiled constant;

        // Resolved once if the delimiter is constant, which it normally is, else null and resolved per row.
        private final String constantDelimiter;

        // Where the expression or namespaces come from a field or parameter we remember the last compilation so
        // that we only re-compile when the values actually change. Volatile as this generator is shared between
        // threads, but a lost update is harmless as it just results in another compilation.
        private volatile Compiled lastCompiled;

        Gen(final Generator[] childGenerators) {
            super(childGenerators);

            constantDelimiter = constantValue(childGenerators, DELIMITER_ARG);

            // Note that this happens after any query parameters have been statically mapped, so an expression
            // supplied as a parameter will have become a constant by this point.
            Compiled compiled = null;
            final String xpathPattern = constantValue(childGenerators, XPATH_ARG);
            final String namespaces = constantValue(childGenerators, NAMESPACES_ARG);
            if (xpathPattern != null && !xpathPattern.isEmpty() && namespaces != null) {
                try {
                    compiled = compile(xpathPattern, namespaces);
                } catch (final Exception e) {
                    // Ignore, the error is reported as a value during eval.
                }
            }
            constant = compiled;
        }

        /**
         * @return The value of a constant argument, or null if the argument is present but not constant.
         */
        private static String constantValue(final Generator[] childGenerators, final int index) {
            if (childGenerators.length <= index) {
                return "";
            }
            if (childGenerators[index] instanceof final StaticValueGen staticGen) {
                final Val val = staticGen.eval(null, null);
                if (val.type().isValue()) {
                    return val.toString();
                }
            }
            return null;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val valXml = childGenerators[XML_ARG].eval(storedValues, childDataSupplier);
            if (!valXml.type().isValue()) {
                return valXml;
            }
            final String delimiter = constantDelimiter != null
                    ? constantDelimiter
                    : evalArg(DELIMITER_ARG, storedValues, childDataSupplier);

            if (constant != null) {
                return evaluate(constant, valXml.toString(), delimiter);
            }

            final Val valXPath = childGenerators[XPATH_ARG].eval(storedValues, childDataSupplier);
            if (!valXPath.type().isValue()) {
                return ValErr.wrap(valXPath);
            }
            final String xpathPattern = valXPath.toString();
            if (xpathPattern.isEmpty()) {
                return ValErr.create("An empty XPath expression has been defined for second argument of '"
                                     + NAME + "' function");
            }
            final String namespaces = evalArg(NAMESPACES_ARG, storedValues, childDataSupplier);

            // Only re-compile if the expression or namespaces have changed since the last row.
            Compiled compiled = lastCompiled;
            if (compiled == null || !compiled.matches(xpathPattern, namespaces)) {
                try {
                    compiled = compile(xpathPattern, namespaces);
                } catch (final ParseException e) {
                    return ValErr.create(e.getMessage());
                } catch (final SaxonApiException e) {
                    return ValErr.create("Error in XPath expression: " + getMessage(e));
                }
                lastCompiled = compiled;
            }

            return evaluate(compiled, valXml.toString(), delimiter);
        }

        private String evalArg(final int index,
                               final StoredValues storedValues,
                               final Supplier<ChildData> childDataSupplier) {
            if (childGenerators.length <= index) {
                return "";
            }
            final Val val = childGenerators[index].eval(storedValues, childDataSupplier);
            return val.type().isValue()
                    ? val.toString()
                    : "";
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * A compiled expression plus the arguments it was compiled from so that we know when it can be reused.
     */
    private record Compiled(String xpathPattern,
                            String namespaces,
                            XPathExecutable executable,
                            boolean namespaceAware) {

        private boolean matches(final String xpathPattern, final String namespaces) {
            return this.xpathPattern.equals(xpathPattern) && this.namespaces.equals(namespaces);
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * Removes all namespaces from the document being parsed so that expressions can match on local names alone
     * without the caller having to know or declare the namespaces used by the XML.
     */
    private static final class NamespaceStrippingFilter extends XMLFilterImpl {

        NamespaceStrippingFilter(final XMLReader parent) {
            super(parent);
        }

        @Override
        public void startElement(final String uri,
                                 final String localName,
                                 final String qName,
                                 final Attributes atts) throws SAXException {
            super.startElement("", localName, localName, stripAttributes(atts));
        }

        @Override
        public void endElement(final String uri,
                               final String localName,
                               final String qName) throws SAXException {
            super.endElement("", localName, localName);
        }

        @Override
        public void startPrefixMapping(final String prefix, final String uri) {
            // Drop all namespace declarations.
        }

        @Override
        public void endPrefixMapping(final String prefix) {
            // Drop all namespace declarations.
        }

        private static Attributes stripAttributes(final Attributes atts) {
            final AttributesImpl attributes = new AttributesImpl();
            for (int i = 0; i < atts.getLength(); i++) {
                final String uri = atts.getURI(i);
                final String qName = atts.getQName(i);
                if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(uri)
                    && !XMLConstants.XMLNS_ATTRIBUTE.equals(qName)
                    && !qName.startsWith(XMLConstants.XMLNS_ATTRIBUTE + ":")) {
                    final String localName = atts.getLocalName(i);
                    attributes.addAttribute("", localName, localName, atts.getType(i), atts.getValue(i));
                }
            }
            return attributes;
        }
    }
}
