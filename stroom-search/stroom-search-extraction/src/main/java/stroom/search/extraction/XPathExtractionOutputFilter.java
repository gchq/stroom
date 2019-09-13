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

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceivingContentHandler;
import net.sf.saxon.s9api.*;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.tree.tiny.TinyTree;
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
import stroom.pipeline.xml.event.simple.StartPrefixMapping;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import static stroom.index.shared.IndexConstants.EVENT_ID;
import static stroom.index.shared.IndexConstants.STREAM_ID;


@ConfigurableElement(type = "XPathExtractionOutputFilter", category = Category.FILTER, roles = {
        PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_HAS_TARGETS}, icon = ElementIcons.SEARCH)
public class XPathExtractionOutputFilter extends SearchResultOutputFilter {
    private static final String MULTIPLE_VALUE_DELIMITER = ", ";
    private final ErrorReceiverProxy errorReceiverProxy;
    private final SecurityContext securityContext;
    private final LocationFactoryProxy locationFactory;

    private Locator locator;

    private final Configuration config = new Configuration();
    private final PipelineConfiguration pipeConfig = config.makePipelineConfiguration();
    final Processor processor = new Processor(config);
    final XPathCompiler compiler = processor.newXPathCompiler();

    private TinyBuilder builder = null;

    private ReceivingContentHandler rch = null;
    private String topLevelElementToSkip = "";
    private String secondLevelElementToCreateDocs = "";
    private int depth = 0;

    private HashMap<String, String> prefixMappings = new HashMap<>();
    private final ArrayList<XPathExecutable> xPathExecutables = new ArrayList<>();


//    //Track of current node (the selected "top level" element), separately to lower level elenent
//    private Document currentDoc = null;
//
//    //Also track element under node, in order that errors may be recovered for next node
//    private Node currentNode = null;

    @Inject
    public XPathExtractionOutputFilter(final LocationFactoryProxy locationFactory,
                                       final SecurityContext securityContext,
                                       final ErrorReceiverProxy errorReceiverProxy) {
        super(false);
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
    public void startDocument() throws SAXException {
        depth = 0;
        super.startDocument();
    }


    private String topLevelUri = null;
    private String topLevelQName = null;
    private Attributes topLevelAtts = null;


    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {

        depth++;
        try {
            if (depth == 1) {
                if (!topLevelElementToSkip.equals(localName)) {
                    xPathExecutables.clear();
                    topLevelElementToSkip = localName;
                }
                topLevelUri = uri;
                topLevelQName = qName;
                topLevelAtts = atts;
            }else if (depth == 2){
                if (!secondLevelElementToCreateDocs.equals((localName))) {
                    secondLevelElementToCreateDocs = localName;
                    xPathExecutables.clear();
                }
                if(xPathExecutables.isEmpty())
                    createXPathExecutables();

                //Start new document
                builder = new TinyBuilder(pipeConfig);
                rch = new ReceivingContentHandler();
                rch.setPipelineConfiguration(pipeConfig);
                rch.setReceiver(builder);

                for (String key : prefixMappings.keySet()) {
                    rch.startPrefixMapping(key, prefixMappings.get(key));
                }
                rch.startDocument();

                rch.startElement(topLevelUri, topLevelElementToSkip, topLevelQName, topLevelAtts);

                rch.startElement(uri, localName, qName, atts);
            } else {
                rch.startElement(uri, localName, qName, atts);
            }
          } catch (SAXException saxException) {

            log(Severity.ERROR, "XML error creating element " + localName, saxException);
        }

    }


    private void createXPathExecutables (){
        for (String xpathPart : fieldIndexes.getMap().keySet()){
            if (EVENT_ID.equals (xpathPart))
                xpathPart = "@" + EVENT_ID;
            else if (STREAM_ID.equals(xpathPart))
                xpathPart = "@" + STREAM_ID;

            String xpath = "/" + topLevelElementToSkip + "/" + secondLevelElementToCreateDocs + "/" + xpathPart;
            try {
                xPathExecutables.add(compiler.compile(xpath));
            } catch (SaxonApiException e) {
                log(Severity.FATAL_ERROR, "Error in XPath Expression: " + xpath, e);
            }
        }

    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        depth --;

        if (depth == 1){
            if (secondLevelElementToCreateDocs.equals(localName)) {
                try {
                    rch.endElement(uri, localName, qName);
                    rch.endElement(topLevelUri, topLevelElementToSkip, topLevelQName);

                    rch.endDocument();
                    //Finish new document and extract XPaths
                    // Get the tree.

                    final TinyTree tree = builder.getTree();

                    Val [] values = new Val[xPathExecutables.size()];
                    int v = 0;
                    for (final XPathExecutable executable : xPathExecutables) {
                        final XPathSelector selector = executable.load();

                        selector.setContextItem(new XdmNode(tree.getRootNode()));
                        final Iterator<XdmItem> iterator = selector.iterator();
                        StringBuilder thisVal = new StringBuilder();
                        int i = 0;
                        while (iterator.hasNext()) {
                            if(i++ > 0)
                                thisVal.append(MULTIPLE_VALUE_DELIMITER);
                            String value = iterator.next().getStringValue();
                            thisVal.append(value);
                        }
                        values[v++] = ValString.create(thisVal.toString());
                    }
                    resultReceiver.receive(new Values(values));

                } catch (SaxonApiException ex) {
                    log(Severity.ERROR, "Unable to evaluate XPaths", ex);
                } finally {
                    rch = null;
                }
            } else {
                rch = null;
                secondLevelElementToCreateDocs = "";
                log (Severity.ERROR, "Unable to finding closing tag for " + secondLevelElementToCreateDocs, null);
            }

        } else if (depth == 0) {
            if (topLevelElementToSkip.equals(localName)) {
                topLevelElementToSkip = "";
            } else {
                topLevelElementToSkip = "";
                log(Severity.ERROR, "Unable to finding closing tag for " + topLevelElementToSkip, null);
            }

        } else {
            //Pop element
            rch.endElement(uri, localName, qName);
        }

        super.endElement(uri, localName, qName);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (rch != null)
            rch.characters(ch, start, length);
        else
            log(Severity.ERROR, "Unexpected text node " + new String(ch, start, length) +
                    " at position " + start, null);

        super.characters(ch, start, length);
    }

    /**
     * Initialise
     */
    @Override
    public void startProcessing() {
        prefixMappings = new HashMap<>();
    }

    private void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
    }


    /**
     * Fired when a prefix mapping is in scope.
     *
     * @param prefix The prefix.
     * @param uri    The URI of the prefix.
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#startPrefixMapping(java.lang.String,
     * java.lang.String)
     */
    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        if (rch != null)
            rch.startPrefixMapping(prefix, uri);

        prefixMappings.put (prefix, uri);
        compiler.declareNamespace(prefix, uri);

        super.startPrefixMapping(prefix, uri);
    }

    /**
     * Fired when a prefix mapping moves out of scope.
     *
     * @param prefix The prefix.
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#endPrefixMapping(java.lang.String)
     */
    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        prefixMappings.remove(prefix);
        if (rch != null)
            rch.endPrefixMapping(prefix);

        super.endPrefixMapping(prefix);
    }


//    private String stringify(Document document) {
//        try {
//            TransformerFactory transformerFactory = TransformerFactory.newInstance();
//            Transformer transformer = transformerFactory.newTransformer();
//            StringWriter stringWriter = new StringWriter();
//            transformer.transform(new DOMSource(document), new StreamResult(stringWriter));
//            return stringWriter.toString();
//        } catch (TransformerException ex) {
//            log(Severity.ERROR, "Cannot create XML string from DOM", ex);
//            return "Error: No XML Created";
//        }
//
//    }
}
