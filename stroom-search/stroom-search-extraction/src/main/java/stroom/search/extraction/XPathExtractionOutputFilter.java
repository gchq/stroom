/*
 * Copyright 2016 Crown Copyright
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

package stroom.search.extraction;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.security.api.SecurityContext;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;


@ConfigurableElement(type = "XPathExtractionOutputFilter", category = Category.FILTER, roles = {
        PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_HAS_TARGETS}, icon = ElementIcons.SEARCH)
public class XPathExtractionOutputFilter extends SearchResultOutputFilter {
    private static final String EVENT = "Event";

    private static final String RECORD = "record";
    private static final String DATA = "data";
    private static final String NAME = "name";
    private static final String VALUE = "value";
    private final ErrorReceiverProxy errorReceiverProxy;
    private final SecurityContext securityContext;
    private final LocationFactoryProxy locationFactory;

    private DOMImplementation domImplementation = null;
    private Locator locator;

    //Track of current node (the selected "top level" element), separately to lower level elenent
    private org.w3c.dom.Document currentNode = null;

    //Also track element under node, in order that errors may be recovered for next node
    private org.w3c.dom.Document currentElement = null;

    @Inject
    public XPathExtractionOutputFilter(final LocationFactoryProxy locationFactory,
                                       final SecurityContext securityContext,
                                       final ErrorReceiverProxy errorReceiverProxy) {
        this.locationFactory = locationFactory;
        this.errorReceiverProxy = errorReceiverProxy;
        this.securityContext = securityContext;
    }

    /**
     * Sets the locator to use when reporting errors.
     *
     * @param locator The locator to use.
     */
    @Override
    public void setDocumentLocator(final Locator locator) {
        this.locator = locator;
        super.setDocumentLocator(locator);
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        if (EVENT.equals(localName) ){
            if (currentNode != null){
                log(Severity.ERROR, "Invalid XML detected. Closing tag not found", null);
            }

            //Start new document
            currentNode = domImplementation.createDocument("test", EVENT, null);
        } else {
            //Push new element
            if (currentNode != null){
                //This could be an error, but might be a higher level XML structure to the defined "top level"
                currentElement = domImplementation.createDocument("test", "Under", null);
                currentNode.appendChild(currentElement);
            }
        }

        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (EVENT.equals(localName) ){
            //Finish new document and extract XPaths
            System.out.println(nodeToString(currentNode));
        } else {
            //Pop element
            currentElement = (Document) currentElement.getParentNode();
            if (currentElement == currentNode)
                currentElement = null; //Top level
        }

        super.endElement(uri, localName, qName);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (currentElement != null) {
            currentElement.createTextNode(new String(ch, start, length));
        } else {
            log(Severity.WARNING, "Character data in top level element", null);
            currentNode.createTextNode(new String(ch, start, length));
        }

        //Add characters to the current node
        super.characters(ch, start, length);
    }

    /**
     * Initialise
     */
    @Override
    public void startProcessing() {
        try {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setNamespaceAware(true);
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            domImplementation = builder.getDOMImplementation();
        } catch (ParserConfigurationException e) {
            log(Severity.FATAL_ERROR, "XML Configuration Failure", e);
            throw new LoggedException("XML Parser is not configured correctly", e);
        } finally {
            super.startProcessing();
        }
    }

    private void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
    }


    public String nodeToString(Document document) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            StringWriter stringWriter = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(stringWriter));
            return stringWriter.toString();
        } catch (TransformerException ex) {
            log(Severity.ERROR, "Cannot create XML string from DOM", ex);
            return "Error: No XML Created";
        }

    }
}
