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

package stroom.pipeline.refdata;

import stroom.hadoopcommonshaded.org.apache.commons.collections.map.HashedMap;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefDataLoader;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.util.PooledByteBufferOutputStream;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.util.CharBuffer;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;
import stroom.util.shared.Severity;

import com.sun.xml.fastinfoset.sax.SAXDocumentSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * This XML filter captures XML content that defines key, value maps to be
 * stored as reference data. The key, value map content is likely to have been
 * produced as the result of an XSL transformation of some reference data.
 * <p>
 * This filter will typically fire
 */
@ConfigurableElement(
        type = "ReferenceDataFilter",
        category = Category.FILTER,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS},
        icon = ElementIcons.REFERENCE_DATA)
public class ReferenceDataFilter extends AbstractXMLFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceDataFilter.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ReferenceDataFilter.class);

    private static final Pattern PREFIX_DELIMITER_PATTERN = Pattern.compile(":");

    /*
        Example xml data
        <referenceData xmlns="reference-data:2" xmlns:evt="event-logging:3">
            <reference>
                <map>cityToCountry</map>
                <key>cardiff</key>
                <value>Wales</value>
            </reference>
            <reference>
                <map>countryToCity</map>
                <key>wales</key>
                <value>cardiff</value>
            </reference>
            <reference>
                <map>employeeIdToCountry</map>
                <from>1001</from>
                <to>1700</to>
                <value>UK</value>
            </reference>
            <reference>
                <map>employeeIdToCountry</map>
                <key>1701</key>
                <value>USA</value>
            </reference>
            ...
        </referenceData>

        Note: <Value> can contain either XML or plain string data, e.g.
            <value>
                <evt:Location>
                    <evt:Country>UK></evt:Country>
                </evt:Location>
            </value>
        or
            <value>UK</value>
     */


    /*
    A word about namespaces
    =======================
    The following are all examples of an element inside <value> and how the namespacing is
    treated in the resulting fragment that is stored in the ref store:

        <Location>  =>  <Location xmlns="reference-data:2">  // default ns inherited from above so it is added in
        <evt:Location>  =>  <Location xmlns:evt="event-logging:3"> // ns prefix defined above so it is added in
        <evt:Location xmlns:evt="event-logging:3">  =>  <Location xmlns:evt="event-logging:3">
        <Location xmlns="event-logging:3">  =>  <Location xmlns="event-logging:3">

    The following is valid XML so we don't need to worry about prefix clashes between the ref fragment
    and the XML it is being injected into.

        <root xmlns:evt="event-logging:3">
            <RefFragment xmlns:evt="event-logging:4"></RefFragment>
        </root>
     */
    private static final int BUFFER_OUTPUT_STREAM_INITIAL_CAPACITY = 1_000;

    private static final String REFERENCE_ELEMENT = "reference";
    private static final String MAP_ELEMENT = "map";
    private static final String KEY_ELEMENT = "key";
    private static final String FROM_ELEMENT = "from";
    private static final String TO_ELEMENT = "to";
    private static final String VALUE_ELEMENT = "value";

    private final ErrorReceiverProxy errorReceiverProxy;
    private final RefDataLoaderHolder refDataLoaderHolder;

    private final SAXDocumentSerializer saxDocumentSerializer = new SAXDocumentSerializer();
    private PooledByteBufferOutputStream pooledByteBufferOutputStream;
    private final CharBuffer contentBuffer = new CharBuffer(20);

    private String mapName;
    private String key;
    private boolean insideValueElement;
    private boolean haveSeenXmlInValueElement = false;
    private Long rangeFrom;
    private Long rangeTo;
    private RefDataValue refDataValue;
    private boolean warnOnDuplicateKeys = false;
    private boolean overrideExistingValues = true;

    // Track all prefix=>uri mappings that are in scope in the wrapper xml
    private Map<String, String> prefixToUriMap = new HashMap<>();
    // Track all prefix=>uri mappings that have been applied to the current scope of the fastInfoset fragment
    private Map<String, String> appliedPrefixToUriMap = new HashMap<>();

    private Map<Integer, List<String>> manuallyAddedLevelToPrefixMap = new HashedMap();

    private int level = 0;

    private boolean insideElement = false;
    private boolean isFastInfosetDocStarted = false;
    private String valueXmlDefaultNamespaceUri = null;

    private enum ValueElementType {
        XML, STRING
    }

    @Inject
    public ReferenceDataFilter(final ErrorReceiverProxy errorReceiverProxy,
                               final RefDataLoaderHolder refDataLoaderHolder,
                               final PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory) {

        this.errorReceiverProxy = errorReceiverProxy;
        this.refDataLoaderHolder = refDataLoaderHolder;
        this.pooledByteBufferOutputStream = pooledByteBufferOutputStreamFactory
                .create(BUFFER_OUTPUT_STREAM_INITIAL_CAPACITY);
    }


    @Override
    public void startProcessing() {
        super.startProcessing();
        saxDocumentSerializer.setOutputStream(pooledByteBufferOutputStream);
//        try {
//            saxDocumentSerializer.startDocument();
//        } catch (SAXException e) {
//            throw new RuntimeException(e);
//        }
    }

    @Override
    public void startStream() {
        super.startStream();
        // build the definition of the stream that is being processed

        if (refDataLoaderHolder.getRefDataLoader() == null) {
            errorReceiverProxy.log(
                    Severity.FATAL_ERROR,
                    null,
                    getElementId(),
                    "RefDataLoader is missing",
                    null);
        }
        boolean didInitSucceed = refDataLoaderHolder.getRefDataLoader().initialise(overrideExistingValues);

        if (!didInitSucceed) {
            RefStreamDefinition refStreamDefinition = refDataLoaderHolder.getRefDataLoader()
                    .getRefStreamDefinition();

            errorReceiverProxy.log(Severity.ERROR, null, getElementId(),
                    LogUtil.message(
                            "A processing info entry already exists for this reference pipeline {}, " +
                                    "version {}, streamId {}",
                            refStreamDefinition.getPipelineDocRef(),
                            refStreamDefinition.getPipelineVersion(),
                            refStreamDefinition.getStreamId()), null);
        }
    }

    @Override
    public void endStream() {
        super.endStream();

        if (refDataLoaderHolder.getRefDataLoader() == null) {
            errorReceiverProxy.log(
                    Severity.FATAL_ERROR,
                    null,
                    getElementId(),
                    "RefDataLoader is missing",
                    null);
        }
        refDataLoaderHolder.getRefDataLoader().completeProcessing();
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        super.startPrefixMapping(prefix, uri);
        LOGGER.trace("startPrefixMapping({}, {})", prefix, uri);

        if (insideValueElement) {
            recordHavingSeenXmlContent();
            fastInfosetStartPrefixMapping(prefix, uri);
        } else {
            // capture all the prefixmappings we encounter before we are in the value and hold them for use later
            addWrapperPrefixMapping(prefix, uri);
        }
    }


    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        super.endPrefixMapping(prefix);
        LOGGER.trace("endPrefixMapping({})", prefix);

        if (insideValueElement) {
            fastInfoSetStartDocumentIfNeeded();

            fastInfosetEndPrefixMapping(prefix);
        } else {
            // TODO @AT do we need to remove the prefix from the map?
            removeWrapperPrefixMapping(prefix);
        }
    }

    private void addWrapperPrefixMapping(final String prefix, final String uri) {
        LOGGER.trace("Storing prefix mapping {}:{}, map contains {}", prefix, uri, prefixToUriMap);
        prefixToUriMap.putIfAbsent(prefix, uri);
    }

    private void removeWrapperPrefixMapping(final String prefix) {
        LOGGER.trace("Removing prefix mapping {}, map contains {}", prefix, prefixToUriMap);

        prefixToUriMap.remove(prefix);
//        prefixToUriMap.values()
//                .removeIf(value ->
//                        ((value == null && prefix == null)
//                                || (value != null && value.equals(prefix))));
    }

    /**
     * This method looks for a post processing function. If it finds one it does
     * not output the element. Instead it stores data about the function and
     * sets a flag so that the function can be performed when the corresponding
     * end element is reached.
     *
     * @param uri       The element's Namespace URI, or the empty string.
     * @param localName The element's local name, or the empty string.
     * @param qName     The element's qualified (prefixed) name, or the empty string.
     * @param atts      The element's attributes.
     * @throws org.xml.sax.SAXException The client may throw an exception during processing.
     * @see stroom.pipeline.filter.AbstractXMLFilter#startElement(java.lang.String,
     * java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(final String uri,
                             final String localName,
                             final String qName,
                             final Attributes atts)
            throws SAXException {
        level++;
        insideElement = true;
        contentBuffer.clear();

        LOGGER.trace("startElement {} {} {}, level:{}", uri, localName, qName, level);

        String newQName = qName;
        if (VALUE_ELEMENT.equalsIgnoreCase(localName)) {
            insideValueElement = true;
        } else if (insideValueElement) {
            recordHavingSeenXmlContent();

            final String prefix = getPrefix(qName);
            if (!hasUriBeenApplied(prefix, uri)) {
                // elm has a uri that we have not done a fastInfoSet startPrefixMapping to
                // so do it now
                fastInfosetManuallyAddPrefixMapping(prefix, uri);
            }

            for (int i = 0; i < atts.getLength(); i++) {
                final String attrPrefix = getPrefix(atts.getQName(i));
                if (!hasUriBeenApplied(attrPrefix)) {
                    // attr has a uri that we have not done a fastInfoSet startPrefixMapping
                    // so do it now
                    final String attrUri = prefixToUriMap.get(attrPrefix);
                    if (attrUri != null) {
                        fastInfosetManuallyAddPrefixMapping(attrPrefix, attrUri);
                    }
                }
            }

//            LOGGER.trace("appliedUris {}", appliedUris);
////            if (!appliedUris.contains(uri) && !uri.equals(valueXmlDefaultNamespaceUri)) {
//            uriToPrefixMap.c
//            if (!appliedUris.contains(uri)) {
//                // we haven't seen this uri before so find its prefix and call startPrefixMapping on the
//                // serializer so it understands them
//                String prefix = uriToPrefixMap.get(uri);
//                if (prefix != null) {
////                    fastInfosetStartPrefixMapping(prefix, uri);
//                }
//                appliedUris.add(uri);
//            }

//            if (uri.equals(valueXmlDefaultNamespaceUri)) {
//                // This is the default namespace so remove it from the element
////                newUri = "";
//                newQName = localName;
////                fastInfosetStartPrefixMapping("", uri);
//            }

            fastInfosetStartElement(localName, uri, newQName, atts);
        }

        super.startElement(uri, localName, newQName, atts);
    }


    private void recordHavingSeenXmlContent() throws SAXException {
        // This is an xml element inside the <value></value> element so we need to treat it as XML content
        // and use the fastinfoset to serialise it.
        if (insideValueElement && !haveSeenXmlInValueElement) {
            LOGGER.trace("================================================");
            LOGGER.trace("Start of value XML fragment");
            // This is the first startElement inside the value element so we are dealing with XML refdata
            haveSeenXmlInValueElement = true;
            // As this is the first element in our value we need to make its namespace the default and
            // assume whatever doc it is later injected into will have the same default namespace. Thus
            // we need to strip the prefix.
//            valueXmlDefaultNamespaceUri = uri;

            LOGGER.trace("saxDocumentSerializer - reset()");
            fastInfoSetStartDocumentIfNeeded();
        }
    }

    private String getPrefix(final String qName) {
        if (qName == null) {
            return null;
        } else if (qName.isEmpty() || !qName.contains(":")) {
            return "";
        } else {
            final String[] parts = PREFIX_DELIMITER_PATTERN.split(qName);
            return parts[0];
        }
    }

    /**
     * This method applies a post processing function if we are currently within
     * a function element. At this stage we should have details of the function
     * to apply from the corresponding start element and content to apply it to
     * from the characters event.
     *
     * @param uri       The element's Namespace URI, or the empty string.
     * @param localName The element's local name, or the empty string.
     * @param qName     The element's qualified (prefixed) name, or the empty string.
     * @throws org.xml.sax.SAXException The client may throw an exception during processing.
     * @see stroom.pipeline.filter.AbstractXMLFilter#endElement(java.lang.String,
     * java.lang.String, java.lang.String)
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        LOGGER.trace("endElement {} {} {} level:{}", uri, localName, qName, level);

        insideElement = false;
        if (VALUE_ELEMENT.equalsIgnoreCase(localName)) {
            LOGGER.trace("End of value XML fragment");
            LOGGER.trace("================================================");
            insideValueElement = false;
            refDataValue = getRefDataValueFromBuffers();
        }

        if (insideValueElement) {
            String newUri = uri;
            String newQName = qName;
            if (uri.equals(valueXmlDefaultNamespaceUri)) {
                // This is the default namespace so remove it from the element
                newQName = localName;
                newUri = "";
            }
            fastInfosetEndElement(localName, newUri, newQName);
        } else {
            if (MAP_ELEMENT.equalsIgnoreCase(localName)) {
                // capture the name of the map that the subsequent values will belong to. A ref
                // stream can contain data for multiple maps
                mapName = contentBuffer.toString();

            } else if (KEY_ELEMENT.equalsIgnoreCase(localName)) {
                // the key for the KV pair
                key = contentBuffer.toString();

            } else if (FROM_ELEMENT.equalsIgnoreCase(localName)) {
                // the start key for the key range
                final String string = contentBuffer.toString();
                try {
                    rangeFrom = Long.parseLong(string);
                } catch (final RuntimeException e) {
                    errorReceiverProxy.log(Severity.ERROR, null, getElementId(),
                            "Unable to parse string \"" + string + "\" as long for range from", e);
                }

            } else if (TO_ELEMENT.equalsIgnoreCase(localName)) {
                // the end key for the key range
                final String string = contentBuffer.toString();
                try {
                    rangeTo = Long.parseLong(string);
                } catch (final RuntimeException e) {
                    errorReceiverProxy.log(Severity.ERROR, null, getElementId(),
                            "Unable to parse string \"" + string + "\" as long for range to", e);
                }

            } else if (REFERENCE_ELEMENT.equalsIgnoreCase(localName)) {
                handleReferenceEndElement();
            }
        }

        contentBuffer.clear();

        // Manually call endPrefixMapping for those prefixes we added
        final List<String> manuallyAddedPrefixes = manuallyAddedLevelToPrefixMap.getOrDefault(level, Collections.emptyList());
        for (final String manuallyAddedPrefix : manuallyAddedPrefixes) {
            fastInfosetEndPrefixMapping(manuallyAddedPrefix);
        }

        super.endElement(uri, localName, qName);

        level--;
    }


    private void handleReferenceEndElement() {
        // end of the ref data item so ensure it is persisted in the store
        try {
            if (mapName != null) {
                final RefDataLoader refDataLoader = Objects.requireNonNull(refDataLoaderHolder.getRefDataLoader());
                final MapDefinition mapDefinition = new MapDefinition(refDataLoader.getRefStreamDefinition(), mapName);

                if (key != null) {
                    // TODO We are holding onto a txn most of the time between put calls. It may be
                    // better to hold onto the kv pairs locally then put them all in a batch so the txn is only
                    // used for lmdb CRUD and not xml processing. However this would not work with the current
                    // approach of a reusable bytebuffer.

                    LOGGER.trace("Putting key {} into map {}", key, mapDefinition);
                    boolean didPutSucceed = refDataLoaderHolder.getRefDataLoader()
                            .put(mapDefinition, key, refDataValue);
                    if (!didPutSucceed) {
                        errorReceiverProxy.log(Severity.ERROR, null, getElementId(),
                                LogUtil.message(
                                        "Unable to load entry for key [{}] as an entry already exists in the store",
                                        key), null);

                    }
                } else if (rangeFrom != null && rangeTo != null) {
                    if (rangeFrom > rangeTo) {
                        errorReceiverProxy.log(Severity.ERROR, null, getElementId(),
                                "Range from '" + rangeFrom
                                        + "' must be less than or equal to range to '" + rangeTo + "'",
                                null);
                    } else if (rangeFrom < 0 || rangeTo < 0) {
                        // negative values cause problems for the ordering of data in LMDB so prevent their use
                        // when using byteBuffer.putLong, -10, 0 & 10 will be stored in LMDB as 0, 10, -10
                        errorReceiverProxy.log(Severity.ERROR, null, getElementId(),
                                LogUtil.message(
                                        "Only non-negative numbers are supported (from: {}, to: {})",
                                        rangeFrom, rangeTo), null);

                    } else {
                        // convert from inclusive rangeTo to exclusive rangeTo
                        // if from==to we still record it as a range
                        final Range<Long> range = new Range<>(rangeFrom, rangeTo + 1);
                        LOGGER.trace("Putting range {} into map {}", range, mapDefinition);
                        boolean didPutSucceed = refDataLoaderHolder.getRefDataLoader()
                                .put(mapDefinition, range, refDataValue);
                        if (!didPutSucceed) {
                            errorReceiverProxy.log(Severity.ERROR, null, getElementId(),
                                    LogUtil.message(
                                            "Unable to load entry for range [{}] to [{}] as an entry already exists in the store",
                                            rangeFrom, rangeTo), null);
                        }
                    }
                }
            }
        } catch (final RuntimeException e) {
            if (warnOnDuplicateKeys) {
                errorReceiverProxy.log(Severity.WARNING, null, getElementId(), e.getMessage(), e);
            }
        }

        // Set keys to null.
        mapName = null;
        key = null;
        rangeFrom = null;
        rangeTo = null;
        haveSeenXmlInValueElement = false;
        valueXmlDefaultNamespaceUri = null;

        // reset our buffers ready for the next ref data item
//                contentBuffer.clear();
//                byteArrayOutputStream.reset();
//                LOGGER.trace("saxDocumentSerializer - reset()");
//                saxDocumentSerializer.reset();
//                LOGGER.trace("saxDocumentSerializer - startDocument()");
//                saxDocumentSerializer.startDocument();
    }

    private RefDataValue getRefDataValueFromBuffers() throws SAXException {
        final RefDataValue refDataValue;
        if (haveSeenXmlInValueElement) {
            //serialize the event list using fastInfoset
            fastInfosetEndDocument();
//            appliedUris.clear();
            LOGGER.trace("Serializing fast infoset events");
            ByteBuffer fastInfosetBuffer = pooledByteBufferOutputStream.getPooledByteBuffer().getByteBuffer();

            // we are wrapping a reusable buffer up in our RefDataValue object so this refDataValue
            // MUST not be used after we have finished with this reference xml element else you will
            // risk mutating data that you did not mean to.
            refDataValue = FastInfosetValue.wrap(fastInfosetBuffer);
        } else {
            LOGGER.trace("Getting string data");
            //serialize the event list using fastInfoset
            // simple string value so use content buffer
            refDataValue = StringValue.of(contentBuffer.toString());
        }
        return refDataValue;
    }


    /**
     * If we are within a function element then this method should buffer the
     * character content so that it can be operated on in the function end
     * element.
     *
     * @param ch     An array of characters.
     * @param start  The starting position in the array.
     * @param length The number of characters to use from the array.
     * @throws org.xml.sax.SAXException The client may throw an exception during processing.
     * @see stroom.pipeline.filter.AbstractXMLFilter#characters(char[],
     * int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (insideValueElement && haveSeenXmlInValueElement) {

            LAMBDA_LOGGER.trace(LambdaLogUtil.message(
                    "characters(\"{}\")", new String(ch, start, length).trim()));
            if (insideElement || !isAllWhitespace(ch, start, length)) {
                fastInfosetCharacters(ch, start, length);
            }
        } else {
            // outside the value element so capture the chars so we can get keys, map names, etc.
            contentBuffer.append(ch, start, length);
        }

        super.characters(ch, start, length);
    }


    @Override
    public void endProcessing() {
        pooledByteBufferOutputStream.release();
        super.endProcessing();
    }

    @PipelineProperty(description = "Warn if there are duplicate keys found in the reference data?",
            defaultValue = "false",
            displayPriority = 1)
    public void setWarnOnDuplicateKeys(final boolean warnOnDuplicateKeys) {
        this.warnOnDuplicateKeys = warnOnDuplicateKeys;
    }

    @PipelineProperty(description = "Allow duplicate keys to override existing values?",
            defaultValue = "true",
            displayPriority = 2)
    public void setOverrideExistingValues(final boolean overrideExistingValues) {
        this.overrideExistingValues = overrideExistingValues;
    }

    private boolean isAllWhitespace(char[] ch, final int start, final int length) {

        boolean isOnlyWhitespace = true;
        for (int i = start; i < start + length; i++) {
            if (!Character.isWhitespace(ch[i])) {
                isOnlyWhitespace = false;
                break;
            }
        }
        LOGGER.trace("isOnlyWhitespace(\"{}\") - returning {}", new String(ch, start, length), isOnlyWhitespace);
        return isOnlyWhitespace;
    }

    private void fastInfoSetStartDocumentIfNeeded() throws SAXException {
        if (!isFastInfosetDocStarted) {
            LOGGER.trace("saxDocumentSerializer - startDocument()");
            saxDocumentSerializer.reset();
            pooledByteBufferOutputStream.clear();
            appliedPrefixToUriMap.clear();
            saxDocumentSerializer.startDocument();
            isFastInfosetDocStarted = true;
        }
    }

    private void fastInfosetManuallyAddPrefixMapping(final String prefix, final String uri) throws SAXException {
        LOGGER.trace("Manually starting prefix mapping {}:{}", prefix, uri);
        manuallyAddedLevelToPrefixMap.computeIfAbsent(level, key -> new ArrayList<>())
                .add(prefix);
        fastInfosetStartPrefixMapping(prefix, uri);
    }

    private void fastInfosetStartPrefixMapping(final String prefix, final String uri) throws SAXException {
        LOGGER.trace("saxDocumentSerializer - startPrefixMapping({}, {})", prefix, uri);
        saxDocumentSerializer.startPrefixMapping(prefix, uri);
        appliedPrefixToUriMap.putIfAbsent(prefix, uri);
    }

    private void fastInfosetEndPrefixMapping(final String prefix) throws SAXException {
        LOGGER.trace("saxDocumentSerializer - endPrefixMapping({}})", prefix);
        saxDocumentSerializer.endPrefixMapping(prefix);
        appliedPrefixToUriMap.remove(prefix);
//        appliedPrefixToUriMap.values()
//                .removeIf(value ->
//                        ((value == null && prefix == null)
//                                || (value != null && value.equals(prefix))));
    }

    private void fastInfosetCharacters(final char[] ch, final int start, final int length) throws SAXException {
        LAMBDA_LOGGER.trace(LambdaLogUtil.message(
                "saxDocumentSerializer - characters(\"{}\")", new String(ch, start, length).trim()));
        saxDocumentSerializer.characters(ch, start, length);
    }

    private void fastInfosetEndDocument() throws SAXException {
        LOGGER.trace("saxDocumentSerializer - endDocument()");
        saxDocumentSerializer.endDocument();
        isFastInfosetDocStarted = false;
    }

    private void fastInfosetEndElement(final String localName,
                                       final String newUri,
                                       final String newQName) throws SAXException {
        LOGGER.trace("saxDocumentSerializer - endElement({}, {}, {})", newUri, localName, newQName);
        saxDocumentSerializer.endElement(newUri, localName, newQName);
    }

    private void fastInfosetStartElement(final String localName,
                                         final String newUri,
                                         final String newQName,
                                         final Attributes atts) throws SAXException {
        LOGGER.trace("saxDocumentSerializer - startElement({}, {}, {})", newUri, localName, newQName);
        saxDocumentSerializer.startElement(newUri, localName, newQName, atts);
    }

    private boolean hasUriBeenApplied(final String prefix, final String uri) {
        return appliedPrefixToUriMap.entrySet().stream()
                .anyMatch(prefixToUriEntry ->
                        Objects.equals(prefixToUriEntry.getKey(), prefix)
                                && Objects.equals(prefixToUriEntry.getValue(), uri));
    }

    private boolean hasUriBeenApplied(final String prefix) {
        return appliedPrefixToUriMap.containsKey(prefix);
    }
}
