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

import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.AbstractElement;
import stroom.pipeline.factory.Processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.List;

/**
 * An XML filter that is capable of firing SAX events at multiple child filters
 * rather than just one. This is likely to be more inefficient than the standard
 * XMLFilterBase as it has to loop around multiple child filters for each event.
 * For this reason it should be used only when necessary.
 */
public class XMLFilterFork extends AbstractElement implements XMLFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(XMLFilterFork.class);

    private final XMLFilter[] filters;

    public XMLFilterFork(final XMLFilter[] filters) {
        this.filters = filters;
    }

    /**
     * Called just before a pipeline begins processing a file.
     *
     * @throws SAXException Could be thrown by a filter.
     * @see stroom.pipeline.filter.XMLFilter#startProcessing()
     */
    @Override
    public void startProcessing() {
        for (int i = 0; i < filters.length; i++) {
            try {
                final XMLFilter filter = filters[i];
                filter.startProcessing();
            } catch (final RuntimeException e1) {
                LOGGER.trace("Unable to start processing!", e1);

                // Make sure the rest of the filters get the event.
                for (i = i + 1; i < filters.length; i++) {
                    final XMLFilter f = filters[i];
                    try {
                        f.startProcessing();
                    } catch (final RuntimeException e2) {
                        LOGGER.trace("Unable to start processing!", e2);
                    }
                }

                throw ProcessException.wrap(e1);
            }
        }
    }

    /**
     * Called just after a pipeline has finished processing a file.
     *
     * @throws SAXException Could be thrown by a filter.
     * @see stroom.pipeline.filter.XMLFilter#endProcessing()
     */
    @Override
    public void endProcessing() {
        for (int i = 0; i < filters.length; i++) {
            try {
                final XMLFilter filter = filters[i];
                filter.endProcessing();
            } catch (final RuntimeException e1) {
                LOGGER.trace("Unable to end processing!", e1);

                // Make sure the rest of the filters get the event.
                for (i = i + 1; i < filters.length; i++) {
                    final XMLFilter f = filters[i];
                    try {
                        f.endProcessing();
                    } catch (final RuntimeException e2) {
                        LOGGER.trace("Unable to end processing!", e2);
                    }
                }

                throw ProcessException.wrap(e1);
            }
        }
    }

    /**
     * This method tells filters that a stream is about to be parsed so that
     * they can complete any setup necessary.
     *
     * @throws SAXException Could be thrown by an implementing class.
     */
    @Override
    public void startStream() {
        for (int i = 0; i < filters.length; i++) {
            try {
                final XMLFilter filter = filters[i];
                filter.startStream();
            } catch (final RuntimeException e1) {
                LOGGER.trace("Unable to start stream!", e1);

                // Make sure the rest of the filters get the event.
                for (i = i + 1; i < filters.length; i++) {
                    final XMLFilter f = filters[i];
                    try {
                        f.startStream();
                    } catch (final RuntimeException e2) {
                        LOGGER.trace("Unable to start stream!", e2);
                    }
                }

                throw ProcessException.wrap(e1);
            }
        }
    }

    /**
     * This method tells filters that a stream has finished parsing so cleanup
     * can be performed.
     *
     * @throws SAXException Could be thrown by an implementing class.
     */
    @Override
    public void endStream() {
        for (int i = 0; i < filters.length; i++) {
            try {
                final XMLFilter filter = filters[i];
                filter.endStream();
            } catch (final RuntimeException e1) {
                LOGGER.trace("Unable to end stream!", e1);

                // Make sure the rest of the filters get the event.
                for (i = i + 1; i < filters.length; i++) {
                    final XMLFilter f = filters[i];
                    try {
                        f.endStream();
                    } catch (final RuntimeException e2) {
                        LOGGER.trace("Unable to end stream!", e2);
                    }
                }

                throw ProcessException.wrap(e1);
            }
        }
    }

    /**
     * Receive an object for locating the origin of SAX document events.
     * <p>
     * <p>
     * SAX parsers are strongly encouraged (though not absolutely required) to
     * supply a locator: if it does so, it must supply the locator to the
     * application by invoking this method before invoking any of the other
     * methods in the ContentHandler interface.
     * </p>
     * <p>
     * <p>
     * The locator allows the application to determine the end position of any
     * document-related event, even if the parser is not reporting an error.
     * Typically, the application will use this information for reporting its
     * own errors (such as character content that does not match an
     * application's business rules). The information returned by the locator is
     * probably not sufficient for use with a search engine.
     * </p>
     * <p>
     * <p>
     * Note that the locator will return correct information only during the
     * invocation SAX event callbacks after {@link #startDocument startDocument}
     * returns and before {@link #endDocument endDocument} is called. The
     * application should not attempt to use it at any other time.
     * </p>
     *
     * @param locator an object that can return the location of any SAX document
     *                event
     * @see org.xml.sax.Locator
     */
    @Override
    public void setDocumentLocator(final Locator locator) {
        for (int i = 0; i < filters.length; i++) {
            try {
                final XMLFilter filter = filters[i];
                filter.setDocumentLocator(locator);
            } catch (final RuntimeException e1) {
                LOGGER.trace("Unable to set locator", e1);

                // Make sure the rest of the filters get the event.
                for (i = i + 1; i < filters.length; i++) {
                    final XMLFilter f = filters[i];
                    try {
                        f.setDocumentLocator(locator);
                    } catch (final RuntimeException e2) {
                        LOGGER.trace("Unable to set locator!", e2);
                    }
                }

                throw ProcessException.wrap(e1);
            }
        }
    }

    /**
     * Receive notification of the beginning of a document.
     * <p>
     * <p>
     * The SAX parser will invoke this method only once, before any other event
     * callbacks (except for {@link #setDocumentLocator setDocumentLocator}).
     * </p>
     *
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #endDocument
     */
    @Override
    public void startDocument() throws SAXException {
        for (int i = 0; i < filters.length; i++) {
            try {
                final XMLFilter filter = filters[i];
                filter.startDocument();
            } catch (final RuntimeException e1) {
                LOGGER.trace("Unable to start document!", e1);

                // Make sure the rest of the filters get the event.
                for (i = i + 1; i < filters.length; i++) {
                    final XMLFilter f = filters[i];
                    try {
                        f.startDocument();
                    } catch (final RuntimeException e2) {
                        LOGGER.trace("Unable to start document!", e2);
                    }
                }

                throw ProcessException.wrap(e1);
            }
        }
    }

    /**
     * Receive notification of the end of a document.
     * <p>
     * <p>
     * <strong>There is an apparent contradiction between the documentation for
     * this method and the documentation for
     * {@link org.xml.sax.ErrorHandler#fatalError}. Until this ambiguity is
     * resolved in a future major release, clients should make no assumptions
     * about whether endDocument() will or will not be invoked when the parser
     * has reported a fatalError() or thrown an exception.</strong>
     * </p>
     * <p>
     * <p>
     * The SAX parser will invoke this method only once, and it will be the last
     * method invoked during the parse. The parser shall not invoke this method
     * until it has either abandoned parsing (because of an unrecoverable error)
     * or reached the end of input.
     * </p>
     *
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #startDocument
     */
    @Override
    public void endDocument() throws SAXException {
        for (int i = 0; i < filters.length; i++) {
            try {
                final XMLFilter filter = filters[i];
                filter.endDocument();
            } catch (final RuntimeException e1) {
                LOGGER.trace("Unable to end document!", e1);

                // Make sure the rest of the filters get the event.
                for (i = i + 1; i < filters.length; i++) {
                    final XMLFilter f = filters[i];
                    try {
                        f.endDocument();
                    } catch (final RuntimeException e2) {
                        LOGGER.trace("Unable to end document!", e2);
                    }
                }

                throw ProcessException.wrap(e1);
            }
        }
    }

    /**
     * Begin the scope of a prefix-URI Namespace mapping.
     * <p>
     * <p>
     * The information from this event is not necessary for normal Namespace
     * processing: the SAX XML reader will automatically replace prefixes for
     * element and attribute names when the
     * <code>http://xml.org/sax/features/namespaces</code> feature is
     * <var>true</var> (the default).
     * </p>
     * <p>
     * <p>
     * There are cases, however, when applications need to use prefixes in
     * character data or in attribute values, where they cannot safely be
     * expanded automatically; the start/endPrefixMapping event supplies the
     * information to the application to expand prefixes in those contexts
     * itself, if necessary.
     * </p>
     * <p>
     * <p>
     * Note that start/endPrefixMapping events are not guaranteed to be properly
     * nested relative to each other: all startPrefixMapping events will occur
     * immediately before the corresponding {@link #startElement startElement}
     * event, and all {@link #endPrefixMapping endPrefixMapping} events will
     * occur immediately after the corresponding {@link #endElement endElement}
     * event, but their order is not otherwise guaranteed.
     * </p>
     * <p>
     * <p>
     * There should never be start/endPrefixMapping events for the "xml" prefix,
     * since it is predeclared and immutable.
     * </p>
     *
     * @param prefix the Namespace prefix being declared. An empty string is used
     *               for the default element namespace, which has no prefix.
     * @param uri    the Namespace URI the prefix is mapped to
     * @throws org.xml.sax.SAXException the client may throw an exception during processing
     * @see #endPrefixMapping
     * @see #startElement
     */
    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        for (int i = 0; i < filters.length; i++) {
            try {
                final XMLFilter filter = filters[i];
                filter.startPrefixMapping(prefix, uri);
            } catch (final RuntimeException e1) {
                LOGGER.trace("Unable to start prefix mapping!", e1);

                // Make sure the rest of the filters get the event.
                for (i = i + 1; i < filters.length; i++) {
                    final XMLFilter f = filters[i];
                    try {
                        f.startPrefixMapping(prefix, uri);
                    } catch (final RuntimeException e2) {
                        LOGGER.trace("Unable to start prefix mapping!", e2);
                    }
                }

                throw ProcessException.wrap(e1);
            }
        }
    }

    /**
     * End the scope of a prefix-URI mapping.
     * <p>
     * <p>
     * See {@link #startPrefixMapping startPrefixMapping} for details. These
     * events will always occur immediately after the corresponding
     * {@link #endElement endElement} event, but the order of
     * {@link #endPrefixMapping endPrefixMapping} events is not otherwise
     * guaranteed.
     * </p>
     *
     * @param prefix the prefix that was being mapped. This is the empty string
     *               when a default mapping scope ends.
     * @throws org.xml.sax.SAXException the client may throw an exception during processing
     * @see #startPrefixMapping
     * @see #endElement
     */
    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        for (int i = 0; i < filters.length; i++) {
            try {
                final XMLFilter filter = filters[i];
                filter.endPrefixMapping(prefix);
            } catch (final RuntimeException e1) {
                LOGGER.trace("Unable to end prefix mapping!", e1);

                // Make sure the rest of the filters get the event.
                for (i = i + 1; i < filters.length; i++) {
                    final XMLFilter f = filters[i];
                    try {
                        f.endPrefixMapping(prefix);
                    } catch (final RuntimeException e2) {
                        LOGGER.trace("Unable to end prefix mapping!", e2);
                    }
                }

                throw ProcessException.wrap(e1);
            }
        }
    }

    /**
     * Receive notification of the beginning of an element.
     * <p>
     * <p>
     * The Parser will invoke this method at the beginning of every element in
     * the XML document; there will be a corresponding {@link #endElement
     * endElement} event for every startElement event (even when the element is
     * empty). All of the element's content will be reported, in order, before
     * the corresponding endElement event.
     * </p>
     * <p>
     * <p>
     * This event allows up to three name components for each element:
     * </p>
     * <p>
     * <ol>
     * <li>the Namespace URI;</li>
     * <li>the local name; and</li>
     * <li>the qualified (prefixed) name.</li>
     * </ol>
     * <p>
     * <p>
     * Any or all of these may be provided, depending on the values of the
     * <var>http://xml.org/sax/features/namespaces</var> and the
     * <var>http://xml.org/sax/features/namespace-prefixes</var> properties:
     * </p>
     * <p>
     * <ul>
     * <li>the Namespace URI and local name are required when the namespaces
     * property is <var>true</var> (the default), and are optional when the
     * namespaces property is <var>false</var> (if one is specified, both must
     * be);</li>
     * <li>the qualified name is required when the namespace-prefixes property
     * is <var>true</var>, and is optional when the namespace-prefixes property
     * is <var>false</var> (the default).</li>
     * </ul>
     * <p>
     * <p>
     * Note that the attribute list provided will contain only attributes with
     * explicit values (specified or defaulted): #IMPLIED attributes will be
     * omitted. The attribute list will contain attributes used for Namespace
     * declarations (xmlns* attributes) only if the
     * <code>http://xml.org/sax/features/namespace-prefixes</code> property is
     * true (it is false by default, and support for a true value is optional).
     * </p>
     * <p>
     * <p>
     * Like {@link #characters characters()}, attribute values may have
     * characters that need more than one <code>char</code> value.
     * </p>
     *
     * @param uri       the Namespace URI, or the empty string if the element has no
     *                  Namespace URI or if Namespace processing is not being
     *                  performed
     * @param localName the local name (without prefix), or the empty string if
     *                  Namespace processing is not being performed
     * @param qName     the qualified name (with prefix), or the empty string if
     *                  qualified names are not available
     * @param atts      the attributes attached to the element. If there are no
     *                  attributes, it shall be an empty Attributes object. The value
     *                  of this object after startElement returns is undefined
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #endElement
     * @see org.xml.sax.Attributes
     * @see org.xml.sax.helpers.AttributesImpl
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        for (int i = 0; i < filters.length; i++) {
            try {
                final XMLFilter filter = filters[i];
                filter.startElement(uri, localName, qName, atts);
            } catch (final RuntimeException e1) {
                LOGGER.trace("Unable to start element!", e1);

                // Make sure the rest of the filters get the event.
                for (i = i + 1; i < filters.length; i++) {
                    final XMLFilter f = filters[i];
                    try {
                        f.startElement(uri, localName, qName, atts);
                    } catch (final RuntimeException e2) {
                        LOGGER.trace("Unable to start element!", e2);
                    }
                }

                throw ProcessException.wrap(e1);
            }
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
        for (int i = 0; i < filters.length; i++) {
            try {
                final XMLFilter filter = filters[i];
                filter.endElement(uri, localName, qName);
            } catch (final RuntimeException e1) {
                LOGGER.trace("Unable to end element!", e1);

                // Make sure the rest of the filters get the event.
                for (i = i + 1; i < filters.length; i++) {
                    final XMLFilter f = filters[i];
                    try {
                        f.endElement(uri, localName, qName);
                    } catch (final RuntimeException e2) {
                        LOGGER.trace("Unable to end element!", e2);
                    }
                }

                throw ProcessException.wrap(e1);
            }
        }
    }

    /**
     * Receive notification of character data.
     * <p>
     * <p>
     * The Parser will call this method to report each chunk of character data.
     * SAX parsers may return all contiguous character data in a single chunk,
     * or they may split it into several chunks; however, all of the characters
     * in any single event must come from the same external entity so that the
     * Locator provides useful information.
     * </p>
     * <p>
     * <p>
     * The application must not attempt to read from the array outside of the
     * specified range.
     * </p>
     * <p>
     * <p>
     * Individual characters may consist of more than one Java <code>char</code>
     * value. There are two important cases where this happens, because
     * characters can't be represented in just sixteen bits. In one case,
     * characters are represented in a <em>Surrogate Pair</em>, using two
     * special Unicode values. Such characters are in the so-called
     * "Astral Planes", with a code point above U+FFFF. A second case involves
     * composite characters, such as a base character combining with one or more
     * accent characters.
     * </p>
     * <p>
     * <p>
     * Your code should not assume that algorithms using <code>char</code>
     * -at-a-time idioms will be working in character units; in some cases they
     * will split characters. This is relevant wherever XML permits arbitrary
     * characters, such as attribute values, processing instruction data, and
     * comments as well as in data reported from this method. It's also
     * generally relevant whenever Java code manipulates internationalized text;
     * the issue isn't unique to XML.
     * </p>
     * <p>
     * <p>
     * Note that some parsers will report whitespace in element content using
     * the {@link #ignorableWhitespace ignorableWhitespace} method rather than
     * this one (validating parsers <em>must</em> do so).
     * </p>
     *
     * @param ch     the characters from the XML document
     * @param start  the start position in the array
     * @param length the number of characters to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #ignorableWhitespace
     * @see org.xml.sax.Locator
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        for (int i = 0; i < filters.length; i++) {
            try {
                final XMLFilter filter = filters[i];
                filter.characters(ch, start, length);
            } catch (final RuntimeException e1) {
                LOGGER.trace(e1.getMessage(), e1);

                // Make sure the rest of the filters get the event.
                for (i = i + 1; i < filters.length; i++) {
                    final XMLFilter f = filters[i];
                    try {
                        f.characters(ch, start, length);
                    } catch (final RuntimeException e2) {
                        LOGGER.trace(e2.getMessage(), e2);
                    }
                }

                throw ProcessException.wrap(e1);
            }
        }
    }

    /**
     * Receive notification of ignorable whitespace in element content.
     * <p>
     * <p>
     * Validating Parsers must use this method to report each chunk of
     * whitespace in element content (see the W3C XML 1.0 recommendation,
     * section 2.10): non-validating parsers may also use this method if they
     * are capable of parsing and using content models.
     * </p>
     * <p>
     * <p>
     * SAX parsers may return all contiguous whitespace in a single chunk, or
     * they may split it into several chunks; however, all of the characters in
     * any single event must come from the same external entity, so that the
     * Locator provides useful information.
     * </p>
     * <p>
     * <p>
     * The application must not attempt to read from the array outside of the
     * specified range.
     * </p>
     *
     * @param ch     the characters from the XML document
     * @param start  the start position in the array
     * @param length the number of characters to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #characters
     */
    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        for (int i = 0; i < filters.length; i++) {
            try {
                final XMLFilter filter = filters[i];
                filter.ignorableWhitespace(ch, start, length);
            } catch (final RuntimeException e1) {
                LOGGER.trace(e1.getMessage(), e1);

                // Make sure the rest of the filters get the event.
                for (i = i + 1; i < filters.length; i++) {
                    final XMLFilter f = filters[i];
                    try {
                        f.ignorableWhitespace(ch, start, length);
                    } catch (final RuntimeException e2) {
                        LOGGER.trace(e2.getMessage(), e2);
                    }
                }

                throw ProcessException.wrap(e1);
            }
        }
    }

    /**
     * Receive notification of a processing instruction.
     * <p>
     * <p>
     * The Parser will invoke this method once for each processing instruction
     * found: note that processing instructions may occur before or after the
     * main document element.
     * </p>
     * <p>
     * <p>
     * A SAX parser must never report an XML declaration (XML 1.0, section 2.8)
     * or a text declaration (XML 1.0, section 4.3.1) using this method.
     * </p>
     * <p>
     * <p>
     * Like {@link #characters characters()}, processing instruction data may
     * have characters that need more than one <code>char</code> value.
     * </p>
     *
     * @param target the processing instruction target
     * @param data   the processing instruction data, or null if none was supplied.
     *               The data does not include any whitespace separating it from
     *               the target
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     */
    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        for (int i = 0; i < filters.length; i++) {
            try {
                final XMLFilter filter = filters[i];
                filter.processingInstruction(target, data);
            } catch (final RuntimeException e1) {
                LOGGER.trace(e1.getMessage(), e1);

                // Make sure the rest of the filters get the event.
                for (i = i + 1; i < filters.length; i++) {
                    final XMLFilter f = filters[i];
                    try {
                        f.processingInstruction(target, data);
                    } catch (final RuntimeException e2) {
                        LOGGER.trace(e2.getMessage(), e2);
                    }
                }

                throw ProcessException.wrap(e1);
            }
        }
    }

    /**
     * Receive notification of a skipped entity. This is not called for entity
     * references within markup constructs such as element start tags or markup
     * declarations. (The XML recommendation requires reporting skipped external
     * entities. SAX also reports internal entity expansion/non-expansion,
     * except within markup constructs.)
     * <p>
     * <p>
     * The Parser will invoke this method each time the entity is skipped.
     * Non-validating processors may skip entities if they have not seen the
     * declarations (because, for example, the entity was declared in an
     * external DTD subset). All processors may skip external entities,
     * depending on the values of the
     * <code>http://xml.org/sax/features/external-general-entities</code> and
     * the <code>http://xml.org/sax/features/external-parameter-entities</code>
     * properties.
     * </p>
     *
     * @param name the name of the skipped entity. If it is a parameter entity,
     *             the name will begin with '%', and if it is the external DTD
     *             subset, it will be the string "[dtd]"
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     */
    @Override
    public void skippedEntity(final String name) throws SAXException {
        for (int i = 0; i < filters.length; i++) {
            try {
                final XMLFilter filter = filters[i];
                filter.skippedEntity(name);
            } catch (final RuntimeException e1) {
                LOGGER.trace(e1.getMessage(), e1);

                // Make sure the rest of the filters get the event.
                for (i = i + 1; i < filters.length; i++) {
                    final XMLFilter f = filters[i];
                    try {
                        f.skippedEntity(name);
                    } catch (final RuntimeException e2) {
                        LOGGER.trace(e2.getMessage(), e2);
                    }
                }

                throw ProcessException.wrap(e1);
            }
        }
    }

    public XMLFilter[] getFilters() {
        return filters;
    }

    @Override
    public List<Processor> createProcessors() {
        final List<Processor> processors = new ArrayList<>(filters.length);
        for (final XMLFilter filter : filters) {
            processors.addAll(filter.createProcessors());
        }
        return processors;
    }
}
