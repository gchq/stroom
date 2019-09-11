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

import org.w3c.dom.*;
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
    private Document currentDoc = null;

    //Also track element under node, in order that errors may be recovered for next node
    private Node currentNode = null;

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

   private static void setAttributes (Element element, final Attributes attributes){
        for (int a = 0; a < attributes.getLength(); a++){
            element.setAttribute(attributes.getLocalName(a), attributes.getValue(a));
        }
   }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        try {
            if (EVENT.equals(localName)) {
                if (currentNode != null) {
                    log(Severity.ERROR, "Invalid XML detected. Closing tag not found", null);
                }

                //Start new document
                currentDoc = domImplementation.createDocument(uri, EVENT, null);
                //Initally add nodes from this point.
                currentNode = currentDoc;
            } else {
                //Push new element
                if (currentDoc != null) {
                    Element element = currentDoc.createElementNS(uri, qName);
                    setAttributes(element, atts);
                    currentNode.appendChild(element);
                    currentNode = element;
                }
            }
        }catch (DOMException domException){
            //Scrap the current document
            currentDoc = null;
            currentNode = null;
            log (Severity.ERROR, "XML error creating element " + localName, domException);
        }

        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (EVENT.equals(localName) ){
            //Finish new document and extract XPaths
            System.out.println(stringify(currentDoc));
            currentDoc = null;
            currentNode = null;
        } else {
            //Pop element
            currentNode = currentNode.getParentNode();
            if (currentNode == currentDoc) {  //This shouldn't happen
                log(Severity.ERROR, "Parse error in XML", null);
                currentDoc = null; //Top level
                currentNode = null;
            }
        }

        super.endElement(uri, localName, qName);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        String content = new String(ch, start, length).trim();
        if (content.length() > 0) {
            if (currentNode != null) {
                try {
                    currentNode.appendChild(currentDoc.createTextNode(content));
                } catch (DOMException domException){
                    log (Severity.ERROR, "XML error creating text node", domException);
                }
            } else {
                log(Severity.WARNING, "Chars in L1 element: " + content + " (pos " + start + ")", null);
            }
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


    private String stringify(Document document) {
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
