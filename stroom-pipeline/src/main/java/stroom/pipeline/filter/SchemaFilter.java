/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.filter;

import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.cache.PoolItem;
import stroom.pipeline.cache.SchemaKey;
import stroom.pipeline.cache.SchemaPool;
import stroom.pipeline.cache.StoredSchema;
import stroom.pipeline.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.state.PipelineContext;
import stroom.pipeline.xmlschema.FindXMLSchemaCriteria;
import stroom.pipeline.xmlschema.XmlSchemaCache;
import stroom.pipeline.xmlschema.XmlSchemaCache.SchemaSet;
import stroom.util.CharBuffer;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.xmlschema.shared.XmlSchemaDoc;

import com.google.common.base.Strings;
import jakarta.inject.Inject;
import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.ValidatorHandler;

/**
 * An XML filter for performing inline schema validation of XML.
 */
public class SchemaFilter extends AbstractXMLFilter implements Locator {

    private static final int INDENT = 2;
    private static final String SPACE = " ";
    private static final String SCHEMA_LOCATION = "schemaLocation";
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("[\\s]+");
    private static final Pattern CVC_PATTERN = Pattern.compile("cvc-[^:]*:");
    private static final Pattern NS_REDUCTION_PATTERN = Pattern.compile("\"[^\"]*\":");

    private final SchemaPool schemaPool;
    private final XmlSchemaCache xmlSchemaCache;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final LocationFactoryProxy locationFactory;
    private final PipelineContext pipelineContext;

    private final Map<String, String> prefixes = new TreeMap<>();
    private final CharBuffer sb = new CharBuffer(10);
    private ErrorHandler errorHandler;
    private Map<String, String> schemaLocations;
    private ValidatorHandler validator;
    private PoolItem<StoredSchema> poolItem;
    private FindXMLSchemaCriteria schemaConstraint;
    private int lineNo;
    private int colNo;
    private int depth;
    private boolean inStartElement;
    private String schemaLanguage = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    // private boolean cacheSchemas = true;
    private boolean schemaValidation = true;
    private boolean useOriginalLocator;
    private Locator locator;

    @Inject
    public SchemaFilter(final SchemaPool schemaPool,
                        final XmlSchemaCache xmlSchemaCache,
                        final ErrorReceiverProxy errorReceiverProxy,
                        final LocationFactoryProxy locationFactory,
                        final PipelineContext pipelineContext) {
        this.schemaPool = schemaPool;
        this.xmlSchemaCache = xmlSchemaCache;
        this.errorReceiverProxy = errorReceiverProxy;
        this.locationFactory = locationFactory;
        this.pipelineContext = pipelineContext;
    }

    /**
     * @see stroom.pipeline.filter.AbstractXMLFilter#startProcessing()
     */
    @Override
    public void startProcessing() {
        try {
            if (errorHandler == null) {
                errorHandler = new ErrorHandlerAdaptor(getElementId(), locationFactory, errorReceiverProxy) {
                    @Override
                    protected void log(final Severity severity, final SAXParseException exception) {
                        String message = exception.getMessage();

                        if (message.contains("cvc-")) {
                            message = CVC_PATTERN.matcher(message).replaceAll("");
                        }
                        if (message.contains("One of")) {
                            message = NS_REDUCTION_PATTERN.matcher(message).replaceAll("");
                        }
                        message = message.trim();

                        final SAXParseException ex = new SAXParseException(message, exception.getPublicId(),
                                exception.getSystemId(), exception.getLineNumber(), exception.getColumnNumber());

                        super.log(severity, ex);
                    }
                };
            }

            schemaLocations = null;
            prefixes.clear();
            validator = null;
        } finally {
            super.startProcessing();
        }
    }

    @Override
    public void endProcessing() {
        try {
            // Return the current schema to the pool if we have one.
            returnCurrentSchema();
        } finally {
            super.endProcessing();
        }
    }

    /**
     * This method tells filters that a stream is about to be parsed so that
     * they can complete any setup necessary.
     */
    @Override
    public void startStream() {
        // Assume the first line is the XML declaration.
        lineNo = 1;
        colNo = 0;
        depth = 0;
        inStartElement = false;
        super.startStream();
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        try {
            if (useOriginalLocator) {
                this.locator = locator;
            } else {
                this.locator = this;
            }

            if (validator != null) {
                validator.setDocumentLocator(this.locator);
            }
        } catch (final RuntimeException e) {
            unexpectedError("setDocumentLocator()", e);
        } finally {
            super.setDocumentLocator(locator);
        }
    }

    @Override
    public void startDocument() throws SAXException {
        try {
            if (validator != null) {
                validator.startDocument();
            }
        } catch (final RuntimeException e) {
            unexpectedError("startDocument()", e);
        } finally {
            super.startDocument();
        }
    }

    /**
     * Fires necessary end document event for the current validator.
     *
     * @throws SAXException Could be thrown by validator.
     * @see stroom.pipeline.filter.AbstractXMLFilter#endDocument()
     */
    @Override
    public void endDocument() throws SAXException {
        try {
            if (validator != null) {
                validator.endDocument();
            }
        } catch (final RuntimeException e) {
            unexpectedError("endDocument()", e);
        } finally {
            super.endDocument();
        }
    }

    /**
     * Adds prefixes from the prefix map.
     *
     * @param prefix The prefix to add.
     * @param uri    The URI of the prefix.
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#startPrefixMapping(java.lang.String,
     * java.lang.String)
     */
    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        try {
            prefixes.put(prefix, uri);

            if (validator != null) {
                validator.startPrefixMapping(prefix, uri);
            }
        } catch (final RuntimeException e) {
            unexpectedError("startPrefixMapping()", e);
        } finally {
            super.startPrefixMapping(prefix, uri);
        }
    }

    /**
     * Removes prefixes from the prefix map.
     *
     * @param prefix The prefix to remove.
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#endPrefixMapping(java.lang.String)
     */
    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        try {
            prefixes.remove(prefix);

            if (validator != null) {
                validator.endPrefixMapping(prefix);
            }
        } catch (final RuntimeException e) {
            unexpectedError("endPrefixMapping()", e);
        } finally {
            super.endPrefixMapping(prefix);
        }
    }

    /**
     * This method is entered for every start element. If this is the first
     * start element in a document it looks for a schema declaration to use to
     * validate the rest of the document.
     *
     * @param uri       The element's namespace URI, or the empty string.
     * @param localName The element's local name, or the empty string.
     * @param qName     The element's qualified (prefixed) name, or the empty string.
     * @param atts      The element's attributes.
     * @throws org.xml.sax.SAXException The client may throw an exception during processing.
     * @see stroom.pipeline.filter.AbstractXMLFilter#startElement(java.lang.String,
     * java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        try {
            // When we are stepping each XML output record will begin at line 1
            // again.
            if (pipelineContext.isStepping() && depth == 0) {
                lineNo = 1;
                schemaLocations = null;
            }

            colNo = depth * INDENT;
            lineNo++;
            inStartElement = true;

            depth++;

            storeSchemaLocations(uri, atts);

            if (validator != null) {
                validator.startElement(uri, localName, qName, atts);
            }
        } catch (final RuntimeException e) {
            unexpectedError("startElement()", e);
        } finally {
            super.startElement(uri, localName, qName, atts);
        }
    }

    /**
     * Receive notification of the end of an element.
     * <p>
     * <p>
     * The SAX parser will invoke this method at the end of every element in the
     * XML document; there will be a corresponding {@link #startElement
     * startElement} event for every endElement event (even when the element is
     * empty).
     * </p>
     * <p>
     * <p>
     * For information on the names, see startElement.
     * </p>
     *
     * @param uri       the Namespace URI, or the empty string if the element has no
     *                  Namespace URI or if Namespace processing is not being
     *                  performed
     * @param localName the local name (without prefix), or the empty string if
     *                  Namespace processing is not being performed
     * @param qName     the qualified XML name (with prefix), or the empty string if
     *                  qualified names are not available
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        try {
            depth--;

            if (!inStartElement) {
                lineNo++;
                colNo = depth * INDENT;
            }
            inStartElement = false;

            if (validator != null) {
                validator.endElement(uri, localName, qName);
            }
        } catch (final RuntimeException e) {
            unexpectedError("endElement()", e);
        } finally {
            super.endElement(uri, localName, qName);
        }
    }

    /**
     * Sends characters to the current validator handler.
     *
     * @param ch     Characters.
     * @param start  Start of char buffer.
     * @param length Number of chars to send.
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#characters(char[],
     * int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        try {
            colNo += length;
            if (validator != null) {
                validator.characters(ch, start, length);
            }
        } catch (final RuntimeException e) {
            unexpectedError("characters()", e);
        } finally {
            super.characters(ch, start, length);
        }
    }

    private void storeSchemaLocations(final String uri, final Attributes atts) throws SAXException {
        if (schemaValidation && schemaLocations == null && xmlSchemaCache != null) {
            schemaLocations = new TreeMap<>();

            String schemaLocation = atts.getValue(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, SCHEMA_LOCATION);
            if (schemaLocation != null) {
                schemaLocation = MULTI_SPACE_PATTERN.matcher(schemaLocation).replaceAll(SPACE);
                schemaLocation = schemaLocation.trim();
                final String[] locations = schemaLocation.split(SPACE);
                for (int i = 0; i < locations.length; i += 2) {
                    if (i + 1 < locations.length) {
                        final String namespace = locations[i];
                        final String schema = locations[i + 1];
                        schemaLocations.put(namespace, schema);
                    }
                }
            }

            // Make sure all of the schema locations are valid.
            if (validateSchemaLocations(uri, schemaLocations, schemaConstraint)) {
                // Locations are valid so get a validator.
                validator = getValidator();
            }
        }
    }

    private boolean validateSchemaLocations(final String rootURI, final Map<String, String> schemaLocations,
                                            final FindXMLSchemaCriteria schemaConstraint) throws SAXException {
        final SchemaSet schemaSet = xmlSchemaCache.getSchemaSet(schemaConstraint);
        final String validLocations = schemaSet.getLocations();

        // Check that the root has a URI.
        if (rootURI == null || rootURI.trim().isEmpty()) {
            noNamespace(validLocations, schemaConstraint);
            return false;
        }

        // Make sure we have some schema locations.
        if (schemaLocations == null || schemaLocations.isEmpty()) {
            noSchemaLocations(validLocations, schemaConstraint);
            return false;
        }

        // Check that the root URI has a valid schema location.
        final String rootLocation = schemaLocations.get(rootURI);
        if (rootLocation == null) {
            noSchemaLocation(rootURI, validLocations, schemaConstraint);
            return false;
        }

        // Make sure the namespace or location for the root schema can be found
        // within the set of valid schemas.
        final XmlSchemaDoc bestMatch = schemaSet.getBestMatch(rootLocation, rootURI);
        if (bestMatch == null) {
            invalidSchemaLocation(rootLocation, validLocations, schemaConstraint);
            return false;
        }

        // Finally check that all of the schema locations are valid.
        final FindXMLSchemaCriteria findXMLSchemaCriteria = new FindXMLSchemaCriteria();
        findXMLSchemaCriteria.setUserRef(schemaConstraint.getUserRef());
        final SchemaSet allSchemas = xmlSchemaCache.getSchemaSet(findXMLSchemaCriteria);
        for (final Entry<String, String> entry : schemaLocations.entrySet()) {
            final String namespaceURI = entry.getKey();
            final String systemId = entry.getValue();
            final XmlSchemaDoc res = allSchemas.getBestMatch(systemId, namespaceURI);
            if (res == null) {
                invalidSchemaLocation(systemId, allSchemas.getLocations(), null);
                return false;
            } else if (res.isDeprecated()) {
                deprecatedSchema(systemId);
            }
        }

        return true;
    }

    private void noNamespace(final String validLocations, final FindXMLSchemaCriteria schemaConstraint)
            throws SAXException {
        if (errorHandler != null) {
            sb.append("No namespace has been declared for this XML instance.");
            sb.append("\nYou must declare a namespace, ");
            sb.append("e.g. xmlns=\"event-logging:3\".");
            printSchemaLocations(sb, validLocations, schemaConstraint);
            fatalError(sb);
        }
    }

    private void noSchemaLocations(final String validLocations, final FindXMLSchemaCriteria schemaConstraint)
            throws SAXException {
        if (errorHandler != null) {
            sb.append("No schema locations specified.");
            printSchemaLocations(sb, validLocations, schemaConstraint);
            fatalError(sb);
        }
    }

    private void noSchemaLocation(final String uri, final String validLocations,
                                  final FindXMLSchemaCriteria schemaConstraint) throws SAXException {
        sb.append("No schema location specified for: ");
        sb.append(uri);
        printSchemaLocations(sb, validLocations, schemaConstraint);
        fatalError(sb);
    }

    private void invalidSchemaLocation(final String systemId, final String validLocations,
                                       final FindXMLSchemaCriteria schemaConstraint) throws SAXException {
        sb.append("Invalid schema location: ");
        sb.append(systemId);
        printSchemaLocations(sb, validLocations, schemaConstraint);
        fatalError(sb);
    }

    private void printSchemaLocations(final CharBuffer sb, final String validLocations,
                                      final FindXMLSchemaCriteria schemaConstraint) {
        final StringBuilder where = new StringBuilder();

        if (schemaConstraint != null) {
            if (!Strings.isNullOrEmpty(schemaConstraint.getSchemaGroup())) {
                if (where.isEmpty()) {
                    where.append(" where ");
                } else {
                    where.append(" and ");
                }
                where.append("schema group='");
                where.append(schemaConstraint.getSchemaGroup());
                where.append("'");
            }
            if (!Strings.isNullOrEmpty(schemaConstraint.getNamespaceURI())) {
                if (where.isEmpty()) {
                    where.append(" where ");
                } else {
                    where.append(" and ");
                }
                where.append("namespace URI='");
                where.append(schemaConstraint.getNamespaceURI());
                where.append("'");
            }
            if (!Strings.isNullOrEmpty(schemaConstraint.getSystemId())) {
                if (where.isEmpty()) {
                    where.append(" where ");
                } else {
                    where.append(" and ");
                }
                where.append("system id='");
                where.append(schemaConstraint.getSystemId());
                where.append("'");
            }
        }

        sb.append("\nYou must use one of the following schema locations");
        sb.append(where.toString());
        sb.append(":\n");
        sb.append(validLocations);
    }

    private void fatalError(final CharBuffer sb) throws SAXException {
        final String message = sb.toString();
        sb.clear();

        final SAXParseException exception = new SAXParseException(message, locator);
        errorHandler.fatalError(exception);

        // If we aren't stepping then throw an exception to terminate early.
        if (!pipelineContext.isStepping()) {
            throw LoggedException.wrap(message, exception);
        }
    }

    private void deprecatedSchema(final String uri) throws SAXException {
        sb.append("Schema is deprecated: ");
        sb.append(uri);
        errorHandler.warning(new SAXParseException(sb.toString(), locator));
        sb.clear();
    }

    private ValidatorHandler getValidator() throws SAXException {
        ValidatorHandler validatorHandler = null;

        if (schemaValidation && schemaPool != null) {
            // Put back the old schema if we need to.
            returnCurrentSchema();

            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<xsd:schema xmlns:xsd=\"");
            sb.append(schemaLanguage);
            sb.append("\"");
            final CharBuffer namespaces = new CharBuffer(100);
            final CharBuffer locations = new CharBuffer(100);
            for (final String prefix : prefixes.keySet()) {
                final String namespace = prefixes.get(prefix);
                final String location = schemaLocations.get(namespace);
                if (location != null) {
                    namespaces.append(" xmlns");
                    if (!prefix.isEmpty()) {
                        namespaces.append(":");
                        namespaces.append(prefix);
                    }
                    namespaces.append("=\"");
                    namespaces.append(namespace);
                    namespaces.append("\"");

                    locations.append("<xsd:import namespace=\"");
                    locations.append(namespace);
                    locations.append("\" schemaLocation=\"");
                    locations.append(location);
                    locations.append("\"/>\n");
                }
            }
            sb.append(namespaces.toString());
            sb.append(">\n");
            sb.append(locations.toString());
            sb.append("</xsd:schema>");

            final String data = sb.toString();
            sb.clear();

            // Get another schema.
            final SchemaKey schemaKey = new SchemaKey(schemaLanguage, data, schemaConstraint);
            poolItem = schemaPool.borrowObject(schemaKey, true);
            final StoredSchema storedSchema = poolItem.getValue();

            // Replay errors generated when creating schema.
            try {
                for (final StoredError storedError : storedSchema.getErrorReceiver().getList()) {
                    errorReceiverProxy.log(storedError.getSeverity(),
                            locationFactory.create(1, 1),
                            getElementId(),
                            storedError.toString(),
                            null);
                }
            } catch (final RuntimeException e) {
                errorHandler.fatalError(new SAXParseException(e.getMessage(), null));
            }

            // Create a validator handler.
            final Schema schema = storedSchema.getSchema();
            if (schema != null) {
                validatorHandler = schema.newValidatorHandler();
                validatorHandler.setDocumentLocator(locator);
                validatorHandler.setErrorHandler(errorHandler);

                validatorHandler.startDocument();
                for (final String prefix : prefixes.keySet()) {
                    validatorHandler.startPrefixMapping(prefix, prefixes.get(prefix));
                }
            }
        }

        return validatorHandler;
    }

    private void returnCurrentSchema() {
        if (poolItem != null) {
            schemaPool.returnObject(poolItem, true);
            poolItem = null;
        }
    }

    /**
     * This method gets the calculated column number by assuming that the output
     * is pretty printed..
     *
     * @return The assumed pretty printed column number.
     * @see org.xml.sax.Locator#getColumnNumber()
     */
    @Override
    public int getColumnNumber() {
        return colNo;
    }

    /**
     * This method gets the calculated line number by assuming that the output
     * is pretty printed.
     *
     * @return The assumed pretty printed line number.
     * @see org.xml.sax.Locator#getLineNumber()
     */
    @Override
    public int getLineNumber() {
        return lineNo;
    }

    /**
     * Not implemented.
     *
     * @return null.
     * @see org.xml.sax.Locator#getPublicId()
     */
    @Override
    public String getPublicId() {
        return null;
    }

    /**
     * Not implemented.
     *
     * @return null.
     * @see org.xml.sax.Locator#getSystemId()
     */
    @Override
    public String getSystemId() {
        return null;
    }

    private void unexpectedError(final String method, final Throwable t) {
        String message = "";
        if (t != null) {
            if (t.getMessage() != null) {
                message = t.getMessage();
            }
            if (message == null || message.trim().isEmpty()) {
                message = t.getClass().getName();
            }
        }

        errorReceiverProxy.log(Severity.ERROR, null, getElementId(),
                "Unexpected error thrown by schema validator in method " + method + ": " + message, t);
    }

    public void setSchemaLanguage(final String schemaLanguage) {
        this.schemaLanguage = schemaLanguage;
    }

    public void setErrorHandler(final ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public void setUseOriginalLocator(final boolean useOriginalLocator) {
        this.useOriginalLocator = useOriginalLocator;
    }

    public void setSchemaValidation(final boolean schemaValidation) {
        this.schemaValidation = schemaValidation;
    }

    public void setSchemaConstraint(final FindXMLSchemaCriteria schemaConstraint) {
        this.schemaConstraint = schemaConstraint;
    }
}
