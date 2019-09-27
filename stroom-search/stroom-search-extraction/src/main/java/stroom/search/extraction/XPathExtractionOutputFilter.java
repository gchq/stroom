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
import org.json.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.security.api.SecurityContext;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Iterator;

import static stroom.index.shared.IndexConstants.EVENT_ID;
import static stroom.index.shared.IndexConstants.STREAM_ID;


@ConfigurableElement(type = "XPathExtractionOutputFilter", category = Category.FILTER, roles = {
        PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_HAS_TARGETS}, icon = ElementIcons.SEARCH)
public class XPathExtractionOutputFilter extends SearchResultOutputFilter {
    private String multipleValueDelimiter = DEFAULT_MULTIPLE_STRING_DELIMITER;
    private static final String DEFAULT_MULTIPLE_STRING_DELIMITER = ",";

    private boolean createJson = false;

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
    private XPathExecutable[] xPathExecutables = null;;

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
                    xPathExecutables = null;
                    topLevelElementToSkip = localName;
                }
                topLevelUri = uri;
                topLevelQName = qName;
                topLevelAtts = atts;
            }else if (depth == 2){
                if (!secondLevelElementToCreateDocs.equals((localName))) {
                    secondLevelElementToCreateDocs = localName;
                    xPathExecutables = null;
                }
                if(xPathExecutables == null)
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
        xPathExecutables = new XPathExecutable[fieldIndexes.size()];

        for (String xpathPart : fieldIndexes.getMap().keySet()){
            int index = fieldIndexes.get(xpathPart);

            if (EVENT_ID.equals (xpathPart))
                xpathPart = "@" + EVENT_ID;
            else if (STREAM_ID.equals(xpathPart))
                xpathPart = "@" + STREAM_ID;

            String xpath = "/" + topLevelElementToSkip + "/" + secondLevelElementToCreateDocs + "/" + xpathPart;

            try {
                xPathExecutables[index] = compiler.compile(xpath);
            } catch (SaxonApiException e) {
                log(Severity.FATAL_ERROR, "Error in XPath Expression: " + xpath, e);
            }
        }

    }


    private int stringifyItem (XdmItem item, StringBuilder thisVal, int numberOfVals){
        if (item instanceof XdmAtomicValue) {
            if (numberOfVals > 0)
                thisVal.append(multipleValueDelimiter);
            String value = item.getStringValue();
            thisVal.append(value);
            numberOfVals++;
        } else if (item instanceof  XdmNode){
            XdmNode node = (XdmNode) item;
            XdmNodeKind type = node.getNodeKind();

            if (type == XdmNodeKind.ELEMENT) {
                boolean hasChildElement = false;


                XdmSequenceIterator iterator = node.axisIterator(Axis.ATTRIBUTE);

                while (iterator.hasNext()) {

                    XdmItem child = iterator.next();
                    if (item instanceof XdmNode){
                        XdmNode childNode = (XdmNode) child;
                        XdmNodeKind childType = childNode.getNodeKind();

                        if (childType == XdmNodeKind.ATTRIBUTE) {
                            hasChildElement = true;
                            break;
                        }
                    }
                }
                if (!hasChildElement)
                {
                    iterator = node.axisIterator(Axis.CHILD);

                    while (iterator.hasNext()) {

                        XdmItem child = iterator.next();
                        if (item instanceof XdmNode) {
                            XdmNode childNode = (XdmNode) child;
                            XdmNodeKind childType = childNode.getNodeKind();

                            if (childType == XdmNodeKind.ELEMENT) {
                                hasChildElement = true;
                                break;
                            }
                        }
                    }
                }

                if (hasChildElement){
                    if (createJson) {
                        JSONObject json = org.json.XML.toJSONObject(node.toString());
//                        String json = jsonFromXML(node.toString());
                        thisVal.append(json);
                    }
                    else {
                        thisVal.append(node.toString());
                    }
                    numberOfVals++;
                }
                else {
                    iterator = node.axisIterator(Axis.CHILD);
                    while (iterator.hasNext()) {
                        XdmItem child = iterator.next();
                        if (item instanceof XdmAtomicValue) {
                            if (numberOfVals > 0)
                                thisVal.append(multipleValueDelimiter);
                            String value = item.getStringValue();
                            thisVal.append(value);
                            numberOfVals++;
                        } else if (item instanceof XdmNode) {
                            XdmNode childNode = (XdmNode) child;
                            XdmNodeKind childType = childNode.getNodeKind();

                            if (childType == XdmNodeKind.TEXT) {
                                if (numberOfVals > 0)
                                    thisVal.append(multipleValueDelimiter);
                                String value = item.getStringValue();
                                thisVal.append(value);
                                numberOfVals++;
                            }
                        }
                    }
                }
            }
            else {
                if (numberOfVals > 0)
                    thisVal.append(multipleValueDelimiter);
                numberOfVals++;
                thisVal.append(item.getStringValue());
            }
        } else {
            log(Severity.ERROR, "Unknown type of XdmItem:" + item.getClass().getName(), null);
        }

        return numberOfVals;
    }
//
//    private String jsonFromXML (String xml){
//        try {
//            SAXParserFactory factory = SAXParserFactory.newInstance();
//            factory.setNamespaceAware(true);
//            SAXParser parser = factory.newSAXParser();
//
//            JSONWriter j = null;
//            DefaultHandler handler = j;
//            parser.parse(xmlFile, j);
//        }
//        catch (FactoryConfigurationError e) {
//            // unable to get a document builder factory
//        }
//        catch (ParserConfigurationException e) {
//            // parser was unable to be configured
//        }
//catch (SAXException e) {
//                // parsing error
//            }
//catch (IOException e) {
//                // i/o error
//            }
//    }

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

                    Val [] values = new Val[xPathExecutables.length];

                    for (int field = 0; field < values.length; field++) {
                        final XPathSelector selector = xPathExecutables[field].load();

                        selector.setContextItem(new XdmNode(tree.getRootNode()));
                        final Iterator<XdmItem> iterator = selector.iterator();

                        StringBuilder thisVal = new StringBuilder();
                        int numVals = 0;
                        while (iterator.hasNext()) {
                            XdmItem item = iterator.next();

                            numVals = stringifyItem(item, thisVal, numVals);

                        }

                        values[field] = ValString.create(thisVal.toString());
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


    @PipelineProperty(description = "The string to delimit multiple simple values.", defaultValue = DEFAULT_MULTIPLE_STRING_DELIMITER,
            displayPriority = 1)
    public void setMultipleValueDelimiter(final String delimiter) {
        this.multipleValueDelimiter = delimiter;
    }

    @PipelineProperty(description = "Whether XML elements should be returned in JSON instead of XML.",
            defaultValue = "false",
            displayPriority = 2)
    public void setCreateJson(final boolean createJson) {
        this.createJson = createJson;
    }

}
