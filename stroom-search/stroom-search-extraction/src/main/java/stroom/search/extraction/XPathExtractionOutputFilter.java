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

package stroom.search.extraction;

import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.query.common.v2.StringFieldValue;
import stroom.query.language.functions.FieldIndex;
import stroom.svg.shared.SvgImage;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceivingContentHandler;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.tree.tiny.TinyTree;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static stroom.index.shared.IndexConstants.EVENT_ID;
import static stroom.index.shared.IndexConstants.STREAM_ID;


@ConfigurableElement(
        type = "XPathExtractionOutputFilter",
        displayValue = "XPath Extraction Output Filter",
        category = Category.FILTER,
        roles = {
                PipelineElementType.ROLE_TARGET},
        icon = SvgImage.PIPELINE_XML_SEARCH)
public class XPathExtractionOutputFilter extends AbstractXMLFilter {

    private static final String DEFAULT_MULTIPLE_STRING_DELIMITER = ",";
    private final ErrorReceiverProxy errorReceiverProxy;
    private final QueryInfoHolder queryInfoHolder;
    private final FieldListConsumerHolder fieldListConsumerHolder;
    private final LocationFactoryProxy locationFactory;
    private final Configuration config = new Configuration();
    private final PipelineConfiguration pipeConfig = config.makePipelineConfiguration();
    private final Processor processor = new Processor(config);
    private final XPathCompiler compiler = processor.newXPathCompiler();
    private String multipleValueDelimiter = DEFAULT_MULTIPLE_STRING_DELIMITER;
    private Locator locator;
    private TinyBuilder builder = null;

    private ReceivingContentHandler contentHandler = null;
    private String topLevelElementToSkip = "";
    private String secondLevelElementToCreateDocs = "";
    private int depth = 0;

    private Map<String, String> prefixMappings = new HashMap<>();
    private XPathExecutable[] xPathExecutables = null;

    private String topLevelUri = null;
    private String topLevelQName = null;
    private Attributes topLevelAtts = null;

    @Inject
    public XPathExtractionOutputFilter(final LocationFactoryProxy locationFactory,
                                       final ErrorReceiverProxy errorReceiverProxy,
                                       final QueryInfoHolder queryInfoHolder,
                                       final FieldListConsumerHolder fieldListConsumerHolder) {
        this.locationFactory = locationFactory;
        this.errorReceiverProxy = errorReceiverProxy;
        this.queryInfoHolder = queryInfoHolder;
        this.fieldListConsumerHolder = fieldListConsumerHolder;
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

            } else if (depth == 2) {
                if (!secondLevelElementToCreateDocs.equals((localName))) {
                    secondLevelElementToCreateDocs = localName;
                    xPathExecutables = null;
                }
                if (xPathExecutables == null) {
                    createXPathExecutables();
                }

                //Start new document
                builder = new TinyBuilder(pipeConfig);
                contentHandler = new ReceivingContentHandler();
                contentHandler.setPipelineConfiguration(pipeConfig);
                contentHandler.setReceiver(builder);

                for (final String key : prefixMappings.keySet()) {
                    contentHandler.startPrefixMapping(key, prefixMappings.get(key));
                }
                contentHandler.startDocument();

                contentHandler.startElement(topLevelUri, topLevelElementToSkip, topLevelQName, topLevelAtts);

                contentHandler.startElement(uri, localName, qName, atts);
            } else {
                contentHandler.startElement(uri, localName, qName, atts);
            }
        } catch (final SAXException saxException) {

            log(Severity.ERROR, LogUtil.message("XML error creating element {}", localName), saxException);
        }

    }


    private void createXPathExecutables() {
        final FieldIndex fieldIndex = queryInfoHolder.getFieldIndex();
        xPathExecutables = new XPathExecutable[fieldIndex.size()];

        for (int pos = 0; pos < fieldIndex.size(); pos++) {
            final String fieldName = fieldIndex.getField(pos);
            if (fieldName != null) {
                String xpathPart = fieldName;

                if (EVENT_ID.equals(xpathPart)) {
                    xpathPart = "@" + EVENT_ID;
                } else if (STREAM_ID.equals(xpathPart)) {
                    xpathPart = "@" + STREAM_ID;
                }

                final String xpath = "/" + topLevelElementToSkip
                                     + "/" + secondLevelElementToCreateDocs
                                     + "/" + xpathPart;

                try {
                    xPathExecutables[pos] = compiler.compile(xpath);
                } catch (final SaxonApiException e) {
                    log(Severity.FATAL_ERROR, LogUtil.message("Error in XPath Expression: {}", xpath), e);
                }
            }
        }
    }

    private int stringifyItem(final XdmItem item, final StringBuilder thisVal, int numberOfVals) {
        if (item instanceof XdmAtomicValue) {
            if (numberOfVals > 0) {
                thisVal.append(multipleValueDelimiter);
            }
            final String value = item.getStringValue();
            thisVal.append(value);
            numberOfVals++;
        } else if (item instanceof final XdmNode node) {
            final XdmNodeKind type = node.getNodeKind();

            if (type == XdmNodeKind.ELEMENT) {
                boolean hasChildElement = false;
                XdmSequenceIterator<XdmNode> iterator = node.axisIterator(Axis.ATTRIBUTE);
                while (iterator.hasNext()) {
                    final XdmNode childNode = iterator.next();
                    final XdmNodeKind childType = childNode.getNodeKind();
                    if (childType == XdmNodeKind.ATTRIBUTE) {
                        hasChildElement = true;
                        break;
                    }
                }
                if (!hasChildElement) {
                    iterator = node.axisIterator(Axis.CHILD);
                    while (iterator.hasNext()) {
                        final XdmNode childNode = iterator.next();
                        final XdmNodeKind childType = childNode.getNodeKind();
                        if (childType == XdmNodeKind.ELEMENT) {
                            hasChildElement = true;
                            break;
                        }
                    }
                }

                if (hasChildElement) {
                    final boolean createJson = false;
                    if (createJson) {
                        final String serialisedForm = complexElementToJson(node.toString());
                        thisVal.append(serialisedForm);
                    } else {
                        thisVal.append(node);
                    }
                    numberOfVals++;
                } else {
                    iterator = node.axisIterator(Axis.CHILD);
                    while (iterator.hasNext()) {
                        final XdmNode childNode = iterator.next();
//                        if (item instanceof XdmAtomicValue) {
//                            if (numberOfVals > 0) {
//                                thisVal.append(multipleValueDelimiter);
//                            }
//                            String value = item.getStringValue();
//                            thisVal.append(value);
//                            numberOfVals++;
//                        } else if (item instanceof XdmNode) {
//                            XdmNode childNode = child;
                        final XdmNodeKind childType = childNode.getNodeKind();

                        if (childType == XdmNodeKind.TEXT) {
                            if (numberOfVals > 0) {
                                thisVal.append(multipleValueDelimiter);
                            }
                            final String value = item.getStringValue();
                            thisVal.append(value);
                            numberOfVals++;
                        }
//                        }
                    }
                }
            } else {
                if (numberOfVals > 0) {
                    thisVal.append(multipleValueDelimiter);
                }
                numberOfVals++;
                thisVal.append(item.getStringValue());
            }
        } else {
            log(Severity.ERROR, LogUtil.message("Unknown type of XdmItem:{}", item.getClass().getName()), null);
        }

        return numberOfVals;
    }

    protected String complexElementToJson(final String xml) {
        return "Not Supported in this Version";
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        depth--;

        if (depth == 1) {
            if (secondLevelElementToCreateDocs.equals(localName)) {
                try {
                    contentHandler.endElement(uri, localName, qName);
                    contentHandler.endElement(topLevelUri, topLevelElementToSkip, topLevelQName);

                    contentHandler.endDocument();
                    //Finish new document and extract XPaths
                    // Get the tree.

                    final TinyTree tree = builder.getTree();

                    final List<StringFieldValue> stringFieldValues = new ArrayList<>(xPathExecutables.length);
                    for (int pos = 0; pos < xPathExecutables.length; pos++) {
                        final String fieldName = queryInfoHolder.getFieldIndex().getField(pos);
                        final XPathSelector selector = xPathExecutables[pos].load();

                        selector.setContextItem(new XdmNode(tree.getRootNode()));
                        final Iterator<XdmItem> iterator = selector.iterator();

                        final StringBuilder thisVal = new StringBuilder();
                        int numVals = 0;
                        while (iterator.hasNext()) {
                            final XdmItem item = iterator.next();
                            numVals = stringifyItem(item, thisVal, numVals);
                        }
                        stringFieldValues.add(new StringFieldValue(fieldName, thisVal.toString()));
                    }
                    fieldListConsumerHolder.acceptStringValues(stringFieldValues);

                } catch (final SaxonApiException ex) {
                    log(Severity.ERROR, "Unable to evaluate XPaths", ex);
                } finally {
                    contentHandler = null;
                }
            } else {
                contentHandler = null;
                secondLevelElementToCreateDocs = "";
                log(Severity.ERROR,
                        LogUtil.message("Unable to finding closing tag for {}", secondLevelElementToCreateDocs),
                        null);
            }

        } else if (depth == 0) {
            if (topLevelElementToSkip.equals(localName)) {
                topLevelElementToSkip = "";
            } else {
                topLevelElementToSkip = "";
                log(Severity.ERROR,
                        LogUtil.message("Unable to finding closing tag for {}", topLevelElementToSkip),
                        null);
            }

        } else {
            //Pop element
            contentHandler.endElement(uri, localName, qName);
        }

        super.endElement(uri, localName, qName);
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (contentHandler != null) {
            contentHandler.characters(ch, start, length);
        } else {
            log(Severity.ERROR, LogUtil.message("Unexpected text node {} at position {}",
                    new String(ch, start, length), start), null);
        }

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
        if (contentHandler != null) {
            contentHandler.startPrefixMapping(prefix, uri);
        }

        prefixMappings.put(prefix, uri);
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
        if (contentHandler != null) {
            contentHandler.endPrefixMapping(prefix);
        }

        super.endPrefixMapping(prefix);
    }

    @PipelineProperty(
            description = "The string to delimit multiple simple values.",
            defaultValue = DEFAULT_MULTIPLE_STRING_DELIMITER,
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
