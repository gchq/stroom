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

package stroom.state.impl.pipeline;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferPoolOutput;
import stroom.docref.DocRef;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.MetaHolder;
import stroom.state.impl.CqlSessionFactory;
import stroom.state.impl.RangedState;
import stroom.state.impl.RangedStateDao;
import stroom.state.impl.State;
import stroom.state.impl.StateDao;
import stroom.state.impl.StateDocCache;
import stroom.state.impl.ValueTypeId;
import stroom.state.shared.StateDoc;
import stroom.svg.shared.SvgImage;
import stroom.util.CharBuffer;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;
import stroom.util.shared.Severity;

import com.sun.xml.fastinfoset.sax.SAXDocumentSerializer;
import jakarta.inject.Inject;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * This XML filter captures XML content that defines key, value maps to be
 * stored as reference data. The key, value map content is likely to have been
 * produced as the result of an XSL transformation of some reference data.
 * <p>
 * This filter will typically fire
 */
@ConfigurableElement(
        type = "StateFilter",
        description = """
                Takes XML input (conforming to the reference-data:2 schema) and \
                loads the data into the State Store.
                Reference data values can be either simple strings or XML fragments.""",
        category = Category.FILTER,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS},
        icon = SvgImage.DOCUMENT_STATE_STORE)
public class StateFilter extends AbstractXMLFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StateFilter.class);

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
    private static final int BUFFER_OUTPUT_STREAM_INITIAL_CAPACITY = 192;

    private static final String REFERENCE_ELEMENT = "reference";
    private static final String MAP_ELEMENT = "map";
    private static final String KEY_ELEMENT = "key";
    private static final String FROM_ELEMENT = "from";
    private static final String TO_ELEMENT = "to";
    private static final String VALUE_ELEMENT = "value";

    private final ErrorReceiverProxy errorReceiverProxy;

    private final SAXDocumentSerializer saxDocumentSerializer = new SAXDocumentSerializer();
    private final ByteBufferFactory byteBufferFactory;
    private ByteBufferPoolOutput stagingValueOutputStream;
    private ValueTypeId typeId;
    private final CharBuffer contentBuffer = new CharBuffer(20);

    private final MetaHolder metaHolder;
    private String mapName;
    private String key;
    private boolean insideValueElement;
    private boolean haveSeenXmlInValueElement = false;
    private Long rangeFrom;
    private Long rangeTo;
    //    private RefDataValue refDataValue;
    private boolean warnOnDuplicateKeys = false;
    private boolean overrideExistingValues = true;

    // Track all prefix=>uri mappings that are in scope in the wrapper xml
    private final Map<String, String> prefixToUriMap = new HashMap<>();
    // Track all prefix=>uri mappings that have been applied to the current scope of the fastInfoset fragment
    private final Map<String, String> appliedPrefixToUriMap = new HashMap<>();

    private final Map<Integer, Set<String>> manuallyAddedLevelToPrefixMap = new HashMap<>();

    private int depthLevel = 0;

    private boolean insideElement = false;
    private boolean isFastInfosetDocStarted = false;
    private String valueXmlDefaultNamespaceUri = null;
    private int valueCount = 0;


    private DocRef stateDocRef;
    private StateDoc stateDoc;
    private Instant effectiveTime;
    private final LocationFactoryProxy locationFactory;
    private final StateDocCache stateDocCache;
    private final CqlSessionFactory cqlSessionFactory;
    private final List<State> stateList = new ArrayList<>();
    private final List<RangedState> rangedStateList = new ArrayList<>();
    private Locator locator;

    @Inject
    public StateFilter(final ErrorReceiverProxy errorReceiverProxy,
                       final LocationFactoryProxy locationFactory,
                       final MetaHolder metaHolder,
                       final StateDocCache stateDocCache,
                       final CqlSessionFactory cqlSessionFactory,
                       final ByteBufferFactory byteBufferFactory) {
        this.errorReceiverProxy = errorReceiverProxy;
        this.locationFactory = locationFactory;
        this.metaHolder = metaHolder;
        this.stateDocCache = stateDocCache;
        this.cqlSessionFactory = cqlSessionFactory;
        this.byteBufferFactory = byteBufferFactory;
    }


    @Override
    public void startProcessing() {
        try {
            if (stateDocRef == null) {
                log(Severity.FATAL_ERROR, "State doc has not been set", null);
                throw LoggedException.create("State doc has not been set");
            }

            // Get the index and index fields from the cache.
            stateDoc = stateDocCache.get(stateDocRef);
            if (stateDoc == null) {
                log(Severity.FATAL_ERROR, "Unable to load state doc", null);
                throw LoggedException.create("Unable to load state doc");
            }

            final Long effectiveMs = metaHolder.getMeta().getEffectiveMs();
            if (effectiveMs != null) {
                effectiveTime = Instant.ofEpochMilli(effectiveMs);
            } else {
                final long createMs = metaHolder.getMeta().getCreateMs();
                effectiveTime = Instant.ofEpochMilli(createMs);
            }
        } finally {
            super.startProcessing();
        }
    }

    @Override
    public void startStream() {
        super.startStream();
        LOGGER.debug("StartStream called");
    }

    @Override
    public void endStream() {
        super.endStream();
        insert();
    }

    private void insert() {
        if (!stateList.isEmpty()) {
            StateDao.insert(cqlSessionFactory.getSession(stateDoc), stateList);
            stateList.forEach(state -> byteBufferFactory.release(state.value()));
            stateList.clear();
        }
        if (!rangedStateList.isEmpty()) {
            RangedStateDao.insert(cqlSessionFactory.getSession(stateDoc), rangedStateList);
            rangedStateList.forEach(state -> byteBufferFactory.release(state.value()));
            rangedStateList.clear();
        }
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
     * @throws SAXException The client may throw an exception during processing.
     * @see stroom.pipeline.filter.AbstractXMLFilter#startElement(String,
     * String, String, Attributes)
     */
    @Override
    public void startElement(final String uri,
                             final String localName,
                             final String qName,
                             final Attributes atts)
            throws SAXException {

        // For slowing down a load in testing
//        try {
//            Thread.sleep(2_000);
//            LOGGER.info("Finished sleep");
//        } catch (InterruptedException e) {
//            LOGGER.info("==================INTERRUPTED=======================");
//            Thread.currentThread().interrupt();
//        }

        depthLevel++;
        insideElement = true;
        contentBuffer.clear();

        LOGGER.trace("startElement {} {} {}, level:{}", uri, localName, qName, depthLevel);

        String newQName = qName;
        if (VALUE_ELEMENT.equalsIgnoreCase(localName)) {
            insideValueElement = true;

            // Prepare to store new value.
            stagingValueOutputStream =
                    new ByteBufferPoolOutput(byteBufferFactory, BUFFER_OUTPUT_STREAM_INITIAL_CAPACITY, -1);
            saxDocumentSerializer.setOutputStream(stagingValueOutputStream);

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
        // and delegate to the fastinfoset content handler to serialise it.
        if (insideValueElement && !haveSeenXmlInValueElement) {
            // This is the first startElement inside the value element so we are dealing with XML refdata
            haveSeenXmlInValueElement = true;
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("================================================");
                LOGGER.trace("Start of value XML fragment");
                LOGGER.trace("saxDocumentSerializer - reset()");
            }
            fastInfoSetStartDocumentIfNeeded();
        }
    }

    private String getPrefix(final String qName) {
        if (qName == null) {
            return null;
        } else if (!qName.contains(":")) {
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
     * @throws SAXException The client may throw an exception during processing.
     * @see stroom.pipeline.filter.AbstractXMLFilter#endElement(String,
     * String, String)
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        LOGGER.trace("endElement {} {} {} level:{}", uri, localName, qName, depthLevel);

        insideElement = false;
        if (VALUE_ELEMENT.equalsIgnoreCase(localName)) {
            handleValueEndElement();
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
        final Set<String> manuallyAddedPrefixes = manuallyAddedLevelToPrefixMap.getOrDefault(
                depthLevel,
                Collections.emptySet());

        if (!manuallyAddedPrefixes.isEmpty()) {
            LOGGER.trace(() ->
                    LogUtil.message("Ending {} manually added prefixes at level {}",
                            manuallyAddedPrefixes.size(),
                            depthLevel));

            // Can't use .forEach() due to the SaxException
            for (final String manuallyAddedPrefix : manuallyAddedPrefixes) {
                fastInfosetEndPrefixMapping(manuallyAddedPrefix);
            }

            // We are leaving this level so can now delete the prefix mappings for this level
            manuallyAddedLevelToPrefixMap.get(depthLevel)
                    .clear();
        }

        super.endElement(uri, localName, qName);

        // Leaving this level so
        depthLevel--;
    }

    private void handleValueEndElement() throws SAXException {
        LOGGER.trace("End of value XML fragment");
        LOGGER.trace("================================================");
        insideValueElement = false;

        if (haveSeenXmlInValueElement) {
            // Complete the fastInfoSet serialisation to stagingValueOutputStream
            fastInfosetEndDocument();
            typeId = ValueTypeId.FAST_INFOSET;
        } else {
            // Simple string value
            final String value = contentBuffer.toString();
            if (NullSafe.isBlankString(value)) {
                typeId = ValueTypeId.NULL;
            } else {
                typeId = ValueTypeId.STRING;
                stagingValueOutputStream.write(value.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private void handleReferenceEndElement() {
        // end of the ref data item so ensure it is persisted in the store
        valueCount++;
        try {
            if (mapName != null && key != null) {
                LOGGER.trace("Putting key {} into map {}", key, mapName);

                final ByteBuffer value = stagingValueOutputStream.getByteBuffer();
                value.flip();
                stateList.add(new State(mapName, key, effectiveTime, typeId, value));
                if (stateList.size() > 1000) {
                    insert();
                }
            } else if (rangeFrom != null && rangeTo != null) {
                if (rangeFrom > rangeTo) {
                    errorReceiverProxy.log(Severity.ERROR, null, getElementId(),
                            "Range from '" + rangeFrom
                                    + "' must be less than or equal to range to '" + rangeTo + "'",
                            null);
                } else if (rangeFrom < 0) {
                    // negative values cause problems for the ordering of data in LMDB so prevent their use
                    // when using byteBuffer.putLong, -10, 0 & 10 will be stored in LMDB as 0, 10, -10
                    errorReceiverProxy.log(Severity.ERROR, null, getElementId(),
                            LogUtil.message(
                                    "Only non-negative numbers are supported (from: {}, to: {})",
                                    rangeFrom, rangeTo), null);

                } else {
                    final ByteBuffer value = stagingValueOutputStream.getByteBuffer();
                    value.flip();
                    rangedStateList.add(new RangedState(mapName, rangeFrom, rangeTo, effectiveTime, typeId, value));
                    if (rangedStateList.size() > 1000) {
                        insert();
                    }
                }
            }
        } catch (final BufferOverflowException boe) {
            final String msg = LogUtil.message("Value for key {} in map {} is too big for the buffer",
                    key,
                    mapName);
            errorReceiverProxy.log(Severity.ERROR, null, getElementId(), msg, boe);
            LOGGER.error(msg, boe);
        } catch (final RuntimeException e) {
            errorReceiverProxy.log(Severity.ERROR, null, getElementId(), e.getMessage(), e);
            LOGGER.error("Error putting key {} into map {}: {} {}",
                    key, mapName, e.getClass().getSimpleName(), e.getMessage());
            LOGGER.debug("Error putting key {} into map {}: {}", key, mapName, e.getMessage(), e);
        }

        // Set keys to null.
        mapName = null;
        key = null;
        rangeFrom = null;
        rangeTo = null;
        haveSeenXmlInValueElement = false;
        valueXmlDefaultNamespaceUri = null;
    }

    private String getKeyText(final String key) {
        return LogUtil.message("key [{}]", key);
    }

    private String getRangeText(final Range<Long> range) {
        return LogUtil.message("range [{}] to [{}]", range.getFrom(), range.getTo());
    }

//    private void validateRangeValuePutSuccess(final Supplier<MapDefinition> mapDefSupplier,
//                                              final Range<Long> range,
//                                              final PutOutcome putOutcome) {
//        LOGGER.debug(() -> LogUtil.message("PutOutcome {} for {} in map {}",
//                putOutcome, getRangeText(range), mapDefSupplier.get().getMapName()));
//        validatePutSuccess(mapDefSupplier, () -> getRangeText(range), putOutcome);
//    }
//
//    private void validateKeyValuePutSuccess(final Supplier<MapDefinition> mapDefSupplier,
//                                            final String key,
//                                            final PutOutcome putOutcome) {
//        LOGGER.debug(() -> LogUtil.message("PutOutcome {} for {} in map {}",
//                putOutcome, getKeyText(key), mapDefSupplier.get().getMapName()));
//        validatePutSuccess(mapDefSupplier, () -> getKeyText(key), putOutcome);
//    }
//
//    private void validatePutSuccess(final Supplier<MapDefinition> mapDefSupplier,
//                                    final Supplier<String> keyTextSupplier,
//                                    final PutOutcome putOutcome) {
//        if (warnOnDuplicateKeys) {
//            if (overrideExistingValues
//                    && putOutcome.isSuccess()
//                    && putOutcome.isDuplicate().orElse(false)) {
//
//                final MapDefinition mapDefinition = mapDefSupplier.get();
//                errorReceiverProxy.log(Severity.WARNING, null, getElementId(),
//                        LogUtil.message(
//                                "Replaced entry for {} in map {} from stream {} as an entry already exists in the " +
//                                        "store and overrideExistingValues is set to true on the reference " +
//                                        "loader pipeline. Set warnOnDuplicateKeys to false to hide these warnings.",
//                                keyTextSupplier.get(),
//                                mapDefinition.getMapName(),
//                                mapDefinition.getRefStreamDefinition().getStreamId()), null);
//
//            } else if (!overrideExistingValues
//                    && putOutcome.isDuplicate().orElse(false)) {
//                final MapDefinition mapDefinition = mapDefSupplier.get();
//                errorReceiverProxy.log(Severity.WARNING, null, getElementId(),
//                        LogUtil.message(
//                                "Unable to load entry for {} into map {} from stream {} as an entry already exists " +
//                                        "in the store and overrideExistingValues is set to false on the reference " +
//                                        "loader pipeline. Set warnOnDuplicateKeys to false to hide these warnings.",
//                                keyTextSupplier.get(),
//                                mapDefinition.getMapName(),
//                                mapDefinition.getRefStreamDefinition().getStreamId()), null);
//            }
//        }
//    }

    /**
     * @param ch     An array of characters.
     * @param start  The starting position in the array.
     * @param length The number of characters to use from the array.
     * @throws SAXException The client may throw an exception during processing.
     * @see stroom.pipeline.filter.AbstractXMLFilter#characters(char[],
     * int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (insideValueElement) {
            if (haveSeenXmlInValueElement) {
                // This is an XML FastInfoSet value
                LOGGER.trace(() -> LogUtil.message(
                        "characters(\"{}\")", new String(ch, start, length).trim()));
                if (insideElement || !isAllWhitespace(ch, start, length)) {
                    // Delegate to the fastInfoset content handler which will write to stagingValueOutputStream
                    fastInfosetCharacters(ch, start, length);
                }
            } else {
                contentBuffer.append(ch, start, length);
//                // This is a simple String value
//                final String str = new String(ch, start, length);
//                try {
//                    stagingValueOutputStream.write(str.getBytes(StandardCharsets.UTF_8));
//                } catch (IOException e) {
//                    throw new RuntimeException(LogUtil.message(
//                            "Error writing chars '{}' to stagingValueOutputStream", str), e);
//                }
            }
        } else {
            // outside the value element so capture the chars, so we can get keys, map names, etc.
            contentBuffer.append(ch, start, length);
        }

        super.characters(ch, start, length);
    }

    @Override
    public void endProcessing() {
        LOGGER.debug("Processed {} XML ref data entries for ref stream", valueCount);
        try {
            LOGGER.debug("closing stagingValueOutputStream");
            stagingValueOutputStream.close();
        } finally {
            super.endProcessing();
        }
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
        // Done like this because isOnlyWhitespace is not final so can't use a lambda
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("isOnlyWhitespace(\"{}\") - returning {}",
                    new String(ch, start, length),
                    isOnlyWhitespace);
        }
        return isOnlyWhitespace;
    }

    private void fastInfoSetStartDocumentIfNeeded() throws SAXException {
        if (!isFastInfosetDocStarted) {
            LOGGER.trace("saxDocumentSerializer - startDocument()");
            saxDocumentSerializer.reset();
            stagingValueOutputStream.reset();
            appliedPrefixToUriMap.clear();
            saxDocumentSerializer.startDocument();
            isFastInfosetDocStarted = true;
        }
    }

    private void fastInfosetManuallyAddPrefixMapping(final String prefix, final String uri) throws SAXException {
        LOGGER.trace("Manually starting prefix mapping {}:{}", prefix, uri);
        manuallyAddedLevelToPrefixMap.computeIfAbsent(depthLevel, key -> new HashSet<>())
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
        LOGGER.trace(() -> LogUtil.message(
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
        return appliedPrefixToUriMap.entrySet()
                .stream()
                .anyMatch(prefixToUriEntry ->
                        Objects.equals(prefixToUriEntry.getKey(), prefix)
                                && Objects.equals(prefixToUriEntry.getValue(), uri));
    }

    private boolean hasUriBeenApplied(final String prefix) {
        return appliedPrefixToUriMap.containsKey(prefix);
    }

    private void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
    }

    @PipelineProperty(description = "The store to send data to.", displayPriority = 1)
    @PipelinePropertyDocRef(types = StateDoc.DOCUMENT_TYPE)
    public void setStateDoc(final DocRef storeRef) {
        this.stateDocRef = storeRef;
    }
}
