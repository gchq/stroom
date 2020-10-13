/*
 * Copyright 2019 Crown Copyright
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
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.search.coprocessor.Values;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.function.Consumer;

import static stroom.index.shared.IndexConstants.EVENT_ID;
import static stroom.index.shared.IndexConstants.STREAM_ID;


@ConfigurableElement(type = "XPathExtractionOutputFilter", category = Category.FILTER, roles = {
        PipelineElementType.ROLE_TARGET}, icon = ElementIcons.XML_SEARCH)
public class XPathExtractionOutputFilter extends AbstractSearchResultOutputFilter {
    private static final String DEFAULT_MULTIPLE_STRING_DELIMITER = ",";

    private String multipleValueDelimiter = DEFAULT_MULTIPLE_STRING_DELIMITER;

    private boolean createJson = false;

    private final ErrorReceiverProxy errorReceiverProxy;
    private final SecurityContext securityContext;
    private final LocationFactoryProxy locationFactory;

    private Locator locator;

    private final Configuration config = new Configuration();
    private final PipelineConfiguration pipeConfig = config.makePipelineConfiguration();
    private final Processor processor = new Processor(config);
    private final XPathCompiler compiler = processor.newXPathCompiler();

    private TinyBuilder builder = null;

    private ReceivingContentHandler contentHandler = null;
    private String topLevelElementToSkip = "";
    private String secondLevelElementToCreateDocs = "";
    private int depth = 0;

    private HashMap<String, String> prefixMappings = new HashMap<>();
    private XPathExecutable[] xPathExecutables = null;

    private String topLevelUri = null;
    private String topLevelQName = null;
    private Attributes topLevelAtts = null;

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
    public void startDocument() throws SAXException {
        depth = 0;
        super.startDocument();
    }

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
                count++;

            }else if (depth == 2){
                if (!secondLevelElementToCreateDocs.equals((localName))) {
                    secondLevelElementToCreateDocs = localName;
                    xPathExecutables = null;
                }
                if(xPathExecutables == null)
                    createXPathExecutables();

                //Start new document
                builder = new TinyBuilder(pipeConfig);
                contentHandler = new ReceivingContentHandler();
                contentHandler.setPipelineConfiguration(pipeConfig);
                contentHandler.setReceiver(builder);

                for (String key : prefixMappings.keySet()) {
                    contentHandler.startPrefixMapping(key, prefixMappings.get(key));
                }
                contentHandler.startDocument();

                contentHandler.startElement(topLevelUri, topLevelElementToSkip, topLevelQName, topLevelAtts);

                contentHandler.startElement(uri, localName, qName, atts);
            } else {
                contentHandler.startElement(uri, localName, qName, atts);
            }
          } catch (SAXException saxException) {

            log(Severity.ERROR,  LogUtil.message("XML error creating element {}", localName), saxException);
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
                log(Severity.FATAL_ERROR,  LogUtil.message("Error in XPath Expression: {}", xpath), e);
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
                        String serialisedForm = complexElementToJson(node.toString());
                        thisVal.append(serialisedForm);
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
            log(Severity.ERROR,  LogUtil.message("Unknown type of XdmItem:{}", item.getClass().getName()), null);
        }

        return numberOfVals;
    }

    protected String complexElementToJson(String xml){
        return "Not Supported in this Version";
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        depth --;

        if (depth == 1){
            if (secondLevelElementToCreateDocs.equals(localName)) {
                try {
                    contentHandler.endElement(uri, localName, qName);
                    contentHandler.endElement(topLevelUri, topLevelElementToSkip, topLevelQName);

                    contentHandler.endDocument();
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
                    consumer.accept(new Values(values));

                } catch (SaxonApiException ex) {
                    log(Severity.ERROR, "Unable to evaluate XPaths", ex);
                } finally {
                    contentHandler = null;
                }
            } else {
                contentHandler = null;
                secondLevelElementToCreateDocs = "";
                log (Severity.ERROR, LogUtil.message("Unable to finding closing tag for {}", secondLevelElementToCreateDocs), null);
            }

        } else if (depth == 0) {
            if (topLevelElementToSkip.equals(localName)) {
                topLevelElementToSkip = "";
            } else {
                topLevelElementToSkip = "";
                log(Severity.ERROR, LogUtil.message("Unable to finding closing tag for {}", topLevelElementToSkip), null);
            }

        } else {
            //Pop element
            contentHandler.endElement(uri, localName, qName);
        }

        super.endElement(uri, localName, qName);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (contentHandler != null)
            contentHandler.characters(ch, start, length);
        else
            log(Severity.ERROR, LogUtil.message("Unexpected text node {} at position {}",
                    new String(ch, start, length), start), null);

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
        if (contentHandler != null)
            contentHandler.startPrefixMapping(prefix, uri);

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
        if (contentHandler != null)
            contentHandler.endPrefixMapping(prefix);

        super.endPrefixMapping(prefix);
    }


    @PipelineProperty(description = "The string to delimit multiple simple values.", defaultValue = DEFAULT_MULTIPLE_STRING_DELIMITER,
            displayPriority = 1)
    public void setMultipleValueDelimiter(final String delimiter) {
        this.multipleValueDelimiter = delimiter;
    }

//    @PipelineProperty(description = "Whether XML elements should be returned in JSON instead of XML.",
//            defaultValue = "false",
//            displayPriority = 2)
//    public void setCreateJson(final boolean createJson) {
//        this.createJson = createJson;
//    }
}
