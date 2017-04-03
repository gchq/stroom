/*
 * Copyright 2017 Crown Copyright
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

package stroom.pipeline.server.filter;

import net.sf.saxon.Configuration;
import net.sf.saxon.jaxp.TemplatesImpl;
import net.sf.saxon.jaxp.TransformerImpl;
import net.sf.saxon.s9api.XsltExecutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import stroom.cache.server.StoredXsltExecutable;
import stroom.cache.server.XSLTPool;
import stroom.entity.shared.StringCriteria;
import stroom.entity.shared.VersionedEntityDecorator;
import stroom.node.server.StroomPropertyService;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.SupportsCodeInjection;
import stroom.pipeline.server.errorhandler.ErrorListenerAdaptor;
import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.pipeline.server.errorhandler.ErrorReceiverIdDecorator;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.errorhandler.StoredErrorReceiver;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.server.writer.PathCreator;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.FindXSLTCriteria;
import stroom.pipeline.shared.XSLT;
import stroom.pipeline.shared.XSLTService;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.state.PipelineContext;
import stroom.pool.PoolItem;
import stroom.util.CharBuffer;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.TransformerHandler;
import java.util.ArrayList;
import java.util.List;

/**
 * An XML filter for performing inline XSLT transformation of XML.
 */
@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(type = "XSLTFilter", category = Category.FILTER, roles = { PipelineElementType.ROLE_TARGET,
        PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.VISABILITY_SIMPLE,
        PipelineElementType.VISABILITY_STEPPING, PipelineElementType.ROLE_MUTATOR,
        PipelineElementType.ROLE_HAS_CODE }, icon = ElementIcons.XSLT)
public class XSLTFilter extends AbstractXMLFilter implements SupportsCodeInjection {
    private static final Logger LOGGER = LoggerFactory.getLogger(XSLTFilter.class);

    private static final int DEFAULT_MAX_ELEMENTS = 1000000;

    private final XSLTPool xsltPool;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final XSLTService xsltService;
    private final StroomPropertyService stroomPropertyService;
    private final LocationFactoryProxy locationFactory;
    private final PipelineContext pipelineContext;
    private final PathCreator pathCreator;

    private ErrorListener errorListener;

    private boolean suppressXSLTNotFoundWarnings;
    private XSLT xsltRef;
    private String xsltNamePattern;

    /**
     * We only need a single transformer factory here as it actually doesn't do
     * much internally when creating a transformer handler.
     */
    private PoolItem<VersionedEntityDecorator<XSLT>, StoredXsltExecutable> poolItem;
    private XsltExecutable xsltExecutable;
    private TransformerHandler handler;
    private Locator locator;
    private boolean xsltRequired = false;
    private boolean passThrough = true;
    private String injectedCode;
    private boolean usePool = true;
    private List<PipelineReference> pipelineReferences;

    private int elementCount;
    private int maxElementCount;

    @Inject
    public XSLTFilter(final XSLTPool xsltPool, final ErrorReceiverProxy errorReceiverProxy,
            final XSLTService xsltService, final StroomPropertyService stroomPropertyService,
            final LocationFactoryProxy locationFactory, final PipelineContext pipelineContext,
            final PathCreator pathCreator) {
        this.xsltPool = xsltPool;
        this.errorReceiverProxy = errorReceiverProxy;
        this.xsltService = xsltService;
        this.stroomPropertyService = stroomPropertyService;
        this.locationFactory = locationFactory;
        this.pipelineContext = pipelineContext;
        this.pathCreator = pathCreator;
    }

    @Override
    public void startProcessing() {
        try {
            errorListener = new ErrorListenerAdaptor(getElementId(), locationFactory, errorReceiverProxy);
            maxElementCount = getMaxElements();

            // Load XSLT from a name pattern if one has been specified.
            XSLT xslt = xsltRef;
            String xsltName = null;

            if (xslt != null) {
                xsltName = xslt.getName();
            }

            if (xsltNamePattern != null) {
                // Resolve replacement variables.
                final String resolvedName = pathCreator.replaceContextVars(xsltNamePattern);
                // Make sure there are no replacement vars left.
                final String[] vars = PathCreator.findVars(resolvedName);
                if (vars.length > 0) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("XSLT name pattern \"");
                    sb.append(xsltNamePattern);
                    sb.append("\" contains invalid replacement variables (");
                    for (final String var : vars) {
                        sb.append(var);
                        sb.append(", ");
                    }
                    sb.setLength(sb.length() - 2);
                    sb.append(")");
                    throw new ProcessException(sb.toString());
                }

                LOGGER.debug("Finding XSLT with resolved name '{}' from pattern '{}'", resolvedName, xsltNamePattern);
                final FindXSLTCriteria criteria = new FindXSLTCriteria();
                criteria.setName(new StringCriteria(resolvedName));
                criteria.setOrderBy(FindXSLTCriteria.ORDER_BY_ID);
                final List<XSLT> xsltList = xsltService.find(criteria);
                if (xsltList == null || xsltList.size() == 0) {
                    if (!suppressXSLTNotFoundWarnings) {
                        final StringBuilder sb = new StringBuilder();
                        sb.append("No XSLT found with name '");
                        sb.append(resolvedName);
                        sb.append("' from pattern '");
                        sb.append(xsltNamePattern);

                        if (xslt != null) {
                            sb.append("' - using default '");
                            sb.append(xsltName);
                            sb.append("'");
                        } else {
                            sb.append("' - no default specified");
                        }

                        errorReceiverProxy.log(Severity.WARNING, null, getElementId(), sb.toString(), null);
                    }
                } else {
                    xslt = xsltList.get(0);
                    xsltName = xslt.getName();

                    if (xsltList.size() > 1) {
                        final StringBuilder sb = new StringBuilder();
                        sb.append("Multiple XSLT found with name '");
                        sb.append(resolvedName);
                        sb.append("' from pattern '");
                        sb.append(xsltNamePattern);
                        sb.append("' - using XSLT with lowest id (");
                        sb.append(xslt.getId());
                        sb.append(")");
                        errorReceiverProxy.log(Severity.WARNING, null, getElementId(), sb.toString(), null);
                    }
                }
            }

            // If we have found XSLT then load it and get a template.
            if (xslt != null) {
                // Load the latest XSLT to get round the issue of the pipeline
                // being cached and therefore holding onto stale XSLT.

                // TODO: We need to use the cached XSLT service ideally but
                // before we do it needs to be aware cluster wide when XSLT has
                // been updated.
                xslt = xsltService.load(xslt);
                if (xslt == null) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("XSLT \"");
                    sb.append(xsltName);
                    sb.append("\" appears to have been deleted");
                    throw new ProcessException(sb.toString());
                }

                // If we are in stepping mode and have made code changes then we
                // want to add them to the newly loaded XSLT.

                if (injectedCode != null) {
                    xslt.setData(injectedCode);
                    usePool = false;
                }

                // If no XSLT has been provided then don't try and get compiled
                // XSLT for it.
                if (xslt.getData() != null && xslt.getData().trim().length() > 0) {
                    // Get compiled XSLT from the pool.
                    final ErrorReceiver errorReceiver = new ErrorReceiverIdDecorator(getElementId(),
                            errorReceiverProxy);
                    poolItem = xsltPool.borrowConfiguredTemplate(new VersionedEntityDecorator<>(xslt), errorReceiver,
                            locationFactory, pipelineReferences, usePool);
                    final StoredXsltExecutable storedXsltExecutable = poolItem.getValue();
                    // Get the errors.
                    final StoredErrorReceiver storedErrors = storedXsltExecutable.getErrorReceiver();
                    // Get the XSLT executable.
                    xsltExecutable = storedXsltExecutable.getXsltExecutable();

                    if (storedErrors.getTotalErrors() > 0) {
                        // Replay any exceptions that were created when
                        // compiling the XSLT into the pipeline error handler.
                        storedErrors.replay(errorReceiver);
                    }

                    if (xsltExecutable == null) {
                        // If the XSLT has previously failed to compile they
                        // will have stored null in the pool. Throw an exception
                        // to record this.
                        final CharBuffer sb = new CharBuffer(100);
                        sb.append("There is a problem with the XSLT \"");
                        sb.append(xslt.getName());
                        sb.append("\", see previous logs for details");
                        final String msg = sb.toString();
                        throw new ProcessException(msg);
                    }
                }
            }

            if (xsltRequired && xsltExecutable == null && !pipelineContext.isStepping()) {
                passThrough = false;
                final String msg = "XSLT is required but either no XSLT was found or there is an error in the XSLT";
                throw new ProcessException(msg);
            }
        } catch (final Exception e) {
            errorReceiverProxy.log(Severity.FATAL_ERROR, null, getElementId(), e.getMessage(), e);
            // If we aren't stepping then throw an exception to terminate early.
            if (!pipelineContext.isStepping()) {
                throw new LoggedException(e.getMessage(), e);
            }
        } finally {
            super.startProcessing();
        }
    }

    @Override
    public void endProcessing() {
        try {
            if (poolItem != null) {
                xsltPool.returnObject(poolItem, usePool);
                poolItem = null;
            }
        } finally {
            super.endProcessing();
        }
    }

    /**
     * @param locator
     *            an object that can return the location of any SAX document
     *            event
     * @see org.xml.sax.Locator
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#setDocumentLocator(org.xml.sax.Locator)
     */
    @Override
    public void setDocumentLocator(final Locator locator) {
        if (this.locator == null) {
            this.locator = locator;
            super.setDocumentLocator(locator);
        }
    }

    /**
     * @throws org.xml.sax.SAXException
     *             any SAX exception, possibly wrapping another exception
     * @see #endDocument
     * @see org.xml.sax.helpers.XMLFilterImpl#startDocument()
     */
    @Override
    public void startDocument() throws SAXException {
        try {
            if (xsltExecutable != null) {
                // Make sure the executable points at the local error handler.
                final Configuration configuration = xsltExecutable.getUnderlyingCompiledStylesheet().getConfiguration();
                configuration.setErrorListener(errorListener);
                configuration.setLineNumbering(!pipelineContext.isStepping());

                // Create a handler to receive all SAX events.
                final TemplatesImpl templates = new TemplatesImpl(xsltExecutable);
                final TransformerImpl transformer = (TransformerImpl) templates.newTransformer();
                transformer.setErrorListener(errorListener);

                handler = transformer.newTransformerHandler();
                handler.setResult(new SAXResult(getFilter()));
                if (locator != null) {
                    handler.setDocumentLocator(locator);
                }
                handler.startDocument();

            } else if (passThrough) {
                super.startDocument();
            }

        } catch (final TransformerConfigurationException e) {
            errorReceiverProxy.log(Severity.FATAL_ERROR, locationFactory.create(e.getLocator()), getElementId(),
                    e.toString(), e);
            // If we aren't stepping then throw an exception to terminate early.
            if (!pipelineContext.isStepping()) {
                throw new LoggedException(e.getMessage(), e);
            }
        }
    }

    /**
     * @throws org.xml.sax.SAXException
     *             any SAX exception, possibly wrapping another exception
     * @see #startDocument
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#endDocument()
     */
    @Override
    public void endDocument() throws SAXException {
        if (handler != null) {
            try {
                handler.endDocument();

            } catch (final Exception e) {
                try {
                    final ProcessException processException = getNestedProcessException(e);
                    if (processException != null) {
                        throw processException;
                    }

                    if (e.getCause() != null) {
                        throw getRuntimeException(e.getCause());
                    }

                    throw e;

                } finally {
                    // We don't want the whole pipeline to terminate processing
                    // if there is a problem with the transform.
                    super.endDocument();
                }
            }

            handler = null;
            elementCount = 0;

        } else if (passThrough) {
            super.endDocument();
        }
    }

    private ProcessException getNestedProcessException(Throwable e) {
        Throwable nested = e;
        while (nested != null && nested != nested.getCause()) {
            if (nested instanceof ProcessException) {
                return (ProcessException) nested;
            }

            nested = nested.getCause();
        }

        return null;
    }

    private RuntimeException getRuntimeException(Throwable e) {
        if (e instanceof RuntimeException) {
            return (RuntimeException) e;
        }

        return new RuntimeException(e.getMessage(), e);
    }

    /**
     * @param prefix
     *            the Namespace prefix being declared. An empty string is used
     *            for the default element namespace, which has no prefix.
     * @param uri
     *            the Namespace URI the prefix is mapped to
     * @throws org.xml.sax.SAXException
     *             the client may throw an exception during processing
     * @see #endPrefixMapping
     * @see #startElement
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#startPrefixMapping(java.lang.String,
     *      java.lang.String)
     */
    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        if (handler != null) {
            handler.startPrefixMapping(prefix, uri);
        } else if (passThrough) {
            super.startPrefixMapping(prefix, uri);
        }
    }

    /**
     * the prefix that was being mapped. This is the empty string when a default
     * mapping scope ends.
     *
     * @param prefix
     *            the prefix that was being mapped. This is the empty string
     *            when a default mapping scope ends.
     * @throws org.xml.sax.SAXException
     *             the client may throw an exception during processing
     * @see #startPrefixMapping
     * @see #endElement
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#endPrefixMapping(java.lang.String)
     */
    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        if (handler != null) {
            handler.endPrefixMapping(prefix);
        } else if (passThrough) {
            super.endPrefixMapping(prefix);
        }
    }

    /**
     * @param uri
     *            the Namespace URI, or the empty string if the element has no
     *            Namespace URI or if Namespace processing is not being
     *            performed
     * @param localName
     *            the local name (without prefix), or the empty string if
     *            Namespace processing is not being performed
     * @param qName
     *            the qualified name (with prefix), or the empty string if
     *            qualified names are not available
     * @param atts
     *            the attributes attached to the element. If there are no
     *            attributes, it shall be an empty Attributes object. The value
     *            of this object after startElement returns is undefined
     * @throws org.xml.sax.SAXException
     *             any SAX exception, possibly wrapping another exception
     * @see #endElement
     * @see org.xml.sax.Attributes
     * @see org.xml.sax.helpers.AttributesImpl
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#startElement(java.lang.String,
     *      java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        if (handler != null) {
            elementCount++;
            if (elementCount > maxElementCount) {
                final StringBuilder sb = new StringBuilder();
                sb.append("Max element count of ");
                sb.append(maxElementCount);
                sb.append(
                        " has been exceeded. Please ensure a split filter is present and is configured correctly for this pipeline.");
                final String message = sb.toString();
                final ProcessException exception = new ProcessException(message);
                if (pipelineContext.isStepping()) {
                    errorReceiverProxy.log(Severity.FATAL_ERROR, null, getElementId(), exception.getMessage(),
                            exception);
                } else {
                    errorReceiverProxy.log(Severity.FATAL_ERROR, locationFactory.create(locator), getElementId(),
                            exception.getMessage(), exception);
                }

                // If we aren't stepping then throw an exception to terminate
                // early.
                if (!pipelineContext.isStepping()) {
                    throw new LoggedException(exception.getMessage(), exception);
                }
            }

            handler.startElement(uri, localName, qName, atts);
        } else if (passThrough) {
            super.startElement(uri, localName, qName, atts);
        }
    }

    /**
     * @param uri
     *            the Namespace URI, or the empty string if the element has no
     *            Namespace URI or if Namespace processing is not being
     *            performed
     * @param localName
     *            the local name (without prefix), or the empty string if
     *            Namespace processing is not being performed
     * @param qName
     *            the qualified XML name (with prefix), or the empty string if
     *            qualified names are not available
     * @throws org.xml.sax.SAXException
     *             any SAX exception, possibly wrapping another exception
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#endElement(java.lang.String,
     *      java.lang.String, java.lang.String)
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (handler != null) {
            handler.endElement(uri, localName, qName);
        } else if (passThrough) {
            super.endElement(uri, localName, qName);
        }
    }

    /**
     * @param ch
     *            the characters from the XML document
     * @param start
     *            the start position in the array
     * @param length
     *            the number of characters to read from the array
     * @throws org.xml.sax.SAXException
     *             any SAX exception, possibly wrapping another exception
     * @see #ignorableWhitespace
     * @see org.xml.sax.Locator
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#characters(char[],
     *      int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (handler != null) {
            handler.characters(ch, start, length);
        } else if (passThrough) {
            super.characters(ch, start, length);
        }
    }

    /**
     * @param ch
     *            the characters from the XML document
     * @param start
     *            the start position in the array
     * @param length
     *            the number of characters to read from the array
     * @throws org.xml.sax.SAXException
     *             any SAX exception, possibly wrapping another exception
     * @see #characters
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#ignorableWhitespace(char[],
     *      int, int)
     */
    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        if (handler != null) {
            handler.ignorableWhitespace(ch, start, length);
        } else if (passThrough) {
            super.ignorableWhitespace(ch, start, length);
        }
    }

    /**
     * @param target
     *            the processing instruction target
     * @param data
     *            the processing instruction data, or null if none was supplied.
     *            The data does not include any whitespace separating it from
     *            the target
     * @throws org.xml.sax.SAXException
     *             any SAX exception, possibly wrapping another exception
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#processingInstruction(java.lang.String,
     *      java.lang.String)
     */
    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        if (handler != null) {
            handler.processingInstruction(target, data);
        } else if (passThrough) {
            super.processingInstruction(target, data);
        }
    }

    /**
     * @param name
     *            the name of the skipped entity. If it is a parameter entity,
     *            the name will begin with '%', and if it is the external DTD
     *            subset, it will be the string "[dtd]"
     * @throws org.xml.sax.SAXException
     *             any SAX exception, possibly wrapping another exception
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#skippedEntity(java.lang.String)
     */
    @Override
    public void skippedEntity(final String name) throws SAXException {
        if (handler != null) {
            handler.skippedEntity(name);
        } else if (passThrough) {
            super.skippedEntity(name);
        }
    }

    public boolean isXsltRequired() {
        return xsltRequired;
    }

    public void setXsltRequired(final boolean xsltRequired) {
        this.xsltRequired = xsltRequired;
    }

    public List<PipelineReference> getPipelineReferences() {
        return pipelineReferences;
    }

    private int getMaxElements() {
        int maxElements = DEFAULT_MAX_ELEMENTS;
        if (stroomPropertyService != null) {
            try {
                final String property = stroomPropertyService.getProperty("stroom.pipeline.xslt.maxElements");
                if (property != null) {
                    maxElements = Integer.parseInt(property);
                }
            } catch (final Exception ex) {
                LOGGER.error("getMaxElements() - Integer.parseInt stroom.pipeline.xslt.maxElements", ex);
            }
        }
        return maxElements;
    }

    @PipelineProperty(description = "The XSLT to use.")
    public void setXslt(final XSLT xsltRef) {
        this.xsltRef = xsltRef;
    }

    @PipelineProperty(description = "A name pattern to load XSLT dynamically.")
    public void setXsltNamePattern(final String xsltNamePattern) {
        this.xsltNamePattern = xsltNamePattern;
    }

    @PipelineProperty(description = "If XSLT cannot be found to match the name pattern suppress warnings.", defaultValue = "true")
    public void setSuppressXSLTNotFoundWarnings(final boolean suppressXSLTNotFoundWarnings) {
        this.suppressXSLTNotFoundWarnings = suppressXSLTNotFoundWarnings;
    }

    @PipelineProperty(description = "A list of places to load reference data from if required.")
    public void setPipelineReference(final PipelineReference pipelineReference) {
        if (pipelineReferences == null) {
            pipelineReferences = new ArrayList<>();
        }

        pipelineReferences.add(pipelineReference);
    }

    @Override
    public void setInjectedCode(final String injectedCode) {
        this.injectedCode = injectedCode;
    }
}
