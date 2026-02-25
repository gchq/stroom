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

package stroom.planb.impl.pipeline;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferPoolOutput;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.MetaHolder;
import stroom.planb.impl.data.RangeState;
import stroom.planb.impl.data.Session;
import stroom.planb.impl.data.SpanKV;
import stroom.planb.impl.data.State;
import stroom.planb.impl.data.TemporalRangeState;
import stroom.planb.impl.data.TemporalState;
import stroom.planb.impl.data.TemporalValue;
import stroom.planb.impl.db.ShardWriters;
import stroom.planb.impl.db.ShardWriters.ShardWriter;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.keyprefix.Tag;
import stroom.planb.impl.serde.temporalkey.TemporalKey;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValXml;
import stroom.svg.shared.SvgImage;
import stroom.util.CharBuffer;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.util.time.StroomDuration;

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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * This XML filter captures XML content that defines key, value maps to be
 * stored as state data. The key, value map content is likely to have been
 * produced as the result of an XSL transformation of some reference data.
 * <p>
 * This filter will typically fire
 */
@ConfigurableElement(
        type = "PlanBFilter",
        displayValue = "Plan B Filter",
        description = """
                Takes XML input (conforming to the reference-data:2 schema) and \
                loads the data into the Plan B State Store.
                Reference data values can be either simple strings or XML fragments.""",
        category = Category.FILTER,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS},
        icon = SvgImage.DOCUMENT_PLAN_B)
public class PlanBFilter extends AbstractXMLFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PlanBFilter.class);

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

    Example histogram
    <histogram>
      <map>histogram_name</map>
      <tags>
        <tag>
          <name>TEST</name>
          <value>TEST</value>
        </tag>
        <tag>
          <name>TEST2</name>
          <value>TEST2</value>
        </tag>
      </tags>
      <time></time>
      <value>10</value>
    </histogram>

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

    private static final String STATE_ELEMENT = "state";
    private static final String TEMPORAL_STATE_ELEMENT = "temporal-state";
    private static final String RANGE_STATE_ELEMENT = "range-state";
    private static final String TEMPORAL_RANGE_STATE_ELEMENT = "temporal-range-state";
    private static final String SESSION_ELEMENT = "session";
    private static final String HISTOGRAM_ELEMENT = "histogram";
    private static final String METRIC_ELEMENT = "metric";
    private static final String MAP_ELEMENT = "map";
    private static final String KEY_ELEMENT = "key";
    private static final String FROM_ELEMENT = "from";
    private static final String TO_ELEMENT = "to";
    private static final String VALUE_ELEMENT = "value";
    private static final String TIME_ELEMENT = "time";
    private static final String TIMEOUT_ELEMENT = "timeout";
    private static final String TRACE_ELEMENT = "trace";

    private final ErrorReceiverProxy errorReceiverProxy;

    private final SAXDocumentSerializer saxDocumentSerializer = new SAXDocumentSerializer();
    private final ByteBufferFactory byteBufferFactory;
    private ByteBufferPoolOutput stagingValueOutputStream;
    private String currentStringValue;
    private Type type;
    private final CharBuffer contentBuffer = new CharBuffer(20);

    private final MetaHolder metaHolder;
    private String mapName;
    private String key;
    private boolean insideValueElement;
    private boolean haveSeenXmlInValueElement = false;
    private Long rangeFrom;
    private Long rangeTo;

    // Track all prefix=>uri mappings that are in scope in the wrapper xml
    private final Map<String, String> prefixToUriMap = new HashMap<>();
    // Track all prefix=>uri mappings that have been applied to the current scope of the fastInfoset fragment
    private final Map<String, String> appliedPrefixToUriMap = new HashMap<>();

    private final Map<Integer, Set<String>> manuallyAddedLevelToPrefixMap = new HashMap<>();

    private int depthLevel = 0;

    private boolean insideElement = false;
    private boolean isFastInfosetDocStarted = false;
    private String valueXmlDefaultNamespaceUri = null;

    private Instant effectiveTime;
    private final LocationFactoryProxy locationFactory;
    private final ShardWriters shardWriters;
    private Locator locator;

    private Instant time;
    private StroomDuration timeout;
    private ShardWriter writer;


    private String currentName;
    private String currentValue;
    private List<Tag> currentTags;

    private boolean inTrace;
    private SpanHandler spanHandler;
    private Span span;


    @Inject
    public PlanBFilter(final ErrorReceiverProxy errorReceiverProxy,
                       final LocationFactoryProxy locationFactory,
                       final MetaHolder metaHolder,
                       final ByteBufferFactory byteBufferFactory,
                       final ShardWriters shardWriters) {
        this.errorReceiverProxy = errorReceiverProxy;
        this.locationFactory = locationFactory;
        this.metaHolder = metaHolder;
        this.byteBufferFactory = byteBufferFactory;
        this.shardWriters = shardWriters;
    }

    @Override
    public void startProcessing() {
        try {
            final long ms = Optional.ofNullable(metaHolder.getMeta().getEffectiveMs())
                    .orElse(metaHolder.getMeta().getCreateMs());
            effectiveTime = Instant.ofEpochMilli(ms);
            writer = shardWriters.createWriter(metaHolder.getMeta());
        } finally {
            super.startProcessing();
        }
    }

    @Override
    public void endProcessing() {
        try {
            if (stagingValueOutputStream != null) {
                LOGGER.debug("closing stagingValueOutputStream");
                stagingValueOutputStream.close();
            }
        } finally {
            try {
                writer.close();
            } finally {
                super.endProcessing();
            }
        }
    }

    @Override
    public void startStream() {
        super.startStream();
        LOGGER.debug("StartStream called");
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
            // capture all the prefix mappings we encounter before we are in the value and hold them for use later
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
    }

    /**
     * This method looks for a post processing function. If it finds one it does
     * not output the element. Instead it stores data about the function and
     * sets a flag so that the function can be performed when the corresponding
     * end element is reached.
     *
     * @param uri       The element's Namespace URI, or the empty string.
     * @param localName The element's local key, or the empty string.
     * @param qName     The element's qualified (prefixed) key, or the empty string.
     * @param atts      The element's attributes.
     * @throws SAXException The client may throw an exception during processing.
     * @see AbstractXMLFilter#startElement(String,
     * String, String, Attributes)
     */
    @Override
    public void startElement(final String uri,
                             final String localName,
                             final String qName,
                             final Attributes atts)
            throws SAXException {
        if (!inTrace && localName.equalsIgnoreCase(TRACE_ELEMENT)) {
            inTrace = true;
        }

        if (inTrace) {
            if (spanHandler != null) {
                spanHandler.startElement(uri, localName, qName, atts);
            } else if (localName.equalsIgnoreCase("span")) {
                spanHandler = new SpanHandler();
            }

        } else {
            depthLevel++;
            insideElement = true;
            contentBuffer.clear();

            LOGGER.trace("startElement {} {} {}, level:{}", uri, localName, qName, depthLevel);

            if (VALUE_ELEMENT.equalsIgnoreCase(localName)) {
                insideValueElement = true;

                // Prepare to store new value.
                stagingValueOutputStream =
                        new ByteBufferPoolOutput(byteBufferFactory, BUFFER_OUTPUT_STREAM_INITIAL_CAPACITY, -1);
                currentStringValue = null;
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

                fastInfosetStartElement(localName, uri, qName, atts);
            } else if ("tags".equalsIgnoreCase(localName)) {
                currentName = null;
                currentValue = null;
                currentTags = new ArrayList<>();
            } else if ("tag".equalsIgnoreCase(localName)) {
                currentName = null;
                currentValue = null;
            }
        }

        super.startElement(uri, localName, qName, atts);
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
     * @param localName The element's local key, or the empty string.
     * @param qName     The element's qualified (prefixed) key, or the empty string.
     * @throws SAXException The client may throw an exception during processing.
     * @see AbstractXMLFilter#endElement(String,
     * String, String)
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        LOGGER.trace("endElement {} {} {} level:{} content:{}", uri, localName, qName, depthLevel, contentBuffer);

        if (inTrace) {
            if (MAP_ELEMENT.equalsIgnoreCase(localName)) {
                // capture the name of the map that the subsequent values will belong to. A ref
                // stream can contain data for multiple maps
                mapName = contentBuffer.toString().toLowerCase(Locale.ROOT);
            } else if (localName.equalsIgnoreCase(TRACE_ELEMENT)) {
                add(StateType.TRACE);
                inTrace = false;
            } else if (spanHandler != null) {
                if (localName.equalsIgnoreCase("span")) {
                    span = spanHandler.build();
                    spanHandler = null;
                } else {
                    spanHandler.endElement(uri, localName, qName);
                }
            }

        } else {
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
                    mapName = contentBuffer.toString().toLowerCase(Locale.ROOT);

                } else if (KEY_ELEMENT.equalsIgnoreCase(localName)) {
                    // the key for the KV pair
                    key = contentBuffer.toString();

                } else if (FROM_ELEMENT.equalsIgnoreCase(localName)) {
                    // the start key for the key range
                    final String string = contentBuffer.toString();
                    try {
                        rangeFrom = Long.parseLong(string);
                    } catch (final RuntimeException e) {
                        error("Unable to parse string \"" + string + "\" as long for range from", e);
                    }
                } else if (TO_ELEMENT.equalsIgnoreCase(localName)) {
                    // the end key for the key range
                    final String string = contentBuffer.toString();
                    try {
                        rangeTo = Long.parseLong(string);
                    } catch (final RuntimeException e) {
                        error("Unable to parse string \"" + string + "\" as long for range to", e);
                    }
                } else if (REFERENCE_ELEMENT.equalsIgnoreCase(localName)) {
                    addReference();
                } else if (HISTOGRAM_ELEMENT.equalsIgnoreCase(localName)) {
                    add(StateType.HISTOGRAM);
                } else if (METRIC_ELEMENT.equalsIgnoreCase(localName)) {
                    add(StateType.METRIC);
                } else if (SESSION_ELEMENT.equalsIgnoreCase(localName)) {
                    add(StateType.SESSION);
                } else if (STATE_ELEMENT.equalsIgnoreCase(localName)) {
                    add(StateType.STATE);
                } else if (RANGE_STATE_ELEMENT.equalsIgnoreCase(localName)) {
                    add(StateType.RANGED_STATE);
                } else if (TEMPORAL_STATE_ELEMENT.equalsIgnoreCase(localName)) {
                    add(StateType.TEMPORAL_STATE);
                } else if (TEMPORAL_RANGE_STATE_ELEMENT.equalsIgnoreCase(localName)) {
                    add(StateType.TEMPORAL_RANGED_STATE);
                } else if (TIME_ELEMENT.equalsIgnoreCase(localName)) {
                    time = DateUtil.parseNormalDateTimeStringToInstant(contentBuffer.toString());
                } else if (TIMEOUT_ELEMENT.equalsIgnoreCase(localName)) {
                    timeout = StroomDuration.parse(contentBuffer.toString());
                } else if ("name".equalsIgnoreCase(localName)) {
                    currentName = contentBuffer.toString();
                } else if (VALUE_ELEMENT.equalsIgnoreCase(localName)) {
                    currentValue = contentBuffer.toString();
                } else if ("tag".equalsIgnoreCase(localName)) {
                    if (currentName == null) {
                        error("Name is null for tag");
                    } else if (currentValue == null) {
                        error("Value is null for tag");
                    } else {
                        currentTags.add(new Tag(currentName, ValString.create(currentValue)));
                    }
                }
            }

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

        contentBuffer.clear();
    }

    private void handleValueEndElement() throws SAXException {
        LOGGER.trace("End of value XML fragment");
        LOGGER.trace("================================================");
        insideValueElement = false;

        if (haveSeenXmlInValueElement) {
            // Complete the fastInfoSet serialisation to stagingValueOutputStream
            fastInfosetEndDocument();
            type = Type.XML;
        } else {
            // Simple string value
            final String value = contentBuffer.toString();
            if (NullSafe.isBlankString(value)) {
                type = Type.NULL;
            } else {
                type = Type.STRING;
                currentStringValue = value;
                stagingValueOutputStream.write(value.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private void addReference() {
        LOGGER.debug(() -> "Adding reference to map: " + mapName);
        final Optional<PlanBDoc> optional = writer.getDoc(mapName, this::error);
        optional.ifPresent(doc -> {
            switch (doc.getStateType()) {
                case STATE -> addState(doc);
                case TEMPORAL_STATE -> addTemporalState(doc);
                case RANGED_STATE -> addRangeState(doc);
                case TEMPORAL_RANGED_STATE -> addTemporalRangeState(doc);
                default -> error("Unexpected Plan B store type: " + doc.getStateType());
            }
        });

        // Set keys to null.
        reset();
    }

    private void add(final StateType stateType) {
        LOGGER.debug(() -> "Adding " + stateType.getDisplayValue() + " to map: " + mapName);
        final Optional<PlanBDoc> optional = writer.getDoc(mapName, this::error);
        optional.ifPresent(doc -> {
            if (stateType.equals(doc.getStateType())) {
                switch (doc.getStateType()) {
                    case STATE -> addState(doc);
                    case TEMPORAL_STATE -> addTemporalState(doc);
                    case RANGED_STATE -> addRangeState(doc);
                    case TEMPORAL_RANGED_STATE -> addTemporalRangeState(doc);
                    case SESSION -> addSession(doc);
                    case HISTOGRAM -> addHistogramValue(doc);
                    case METRIC -> addMetricValue(doc);
                    case TRACE -> addSpanValue(doc);
                    default -> error("Unexpected Plan B store type: " + doc.getStateType());
                }
            } else {
                error("Unexpected Plan B store type for " + stateType.getDisplayValue() + ": " + doc.getStateType());
            }
        });

        // Set keys to null.
        reset();
    }

    private void catchLmdbError(final Runnable runnable) {
        try {
            runnable.run();
        } catch (final BufferOverflowException boe) {
            final String msg = LogUtil.message("Value for key {} in map {} is too big for the buffer",
                    key,
                    mapName);
            error(msg, boe);
            LOGGER.error(msg, boe);
        } catch (final RuntimeException e) {
            error(e);
            LOGGER.error("Error putting key {} into map {}: {} {}",
                    key, mapName, e.getClass().getSimpleName(), e.getMessage());
            LOGGER.debug("Error putting key {} into map {}: {}", key, mapName, e.getMessage(), e);
        }
    }

    private void reset() {
        mapName = null;
        key = null;
        rangeFrom = null;
        rangeTo = null;
        haveSeenXmlInValueElement = false;
        valueXmlDefaultNamespaceUri = null;
        time = null;
        timeout = null;
        currentTags = null;
    }

    private void addState(final PlanBDoc doc) {
        final KeyPrefix prefix = getKeyPrefix();
        if (prefix != null) {
            LOGGER.trace("Putting state key {} into table {}", prefix, mapName);
            final Val v = getVal();
            catchLmdbError(() -> writer.addState(doc, new State(prefix, v)));
        }
    }

    private void addTemporalState(final PlanBDoc doc) {
        final KeyPrefix prefix = getKeyPrefix();
        if (prefix != null) {
            final Instant time = this.time != null
                    ? this.time
                    : this.effectiveTime;
            if (time == null) {
                error(LogUtil.message("Temporal state 'time' is null for {}", mapName));

            } else {
                LOGGER.trace("Putting temporal state key {} into table {}", prefix, mapName);
                final TemporalKey k = TemporalKey
                        .builder()
                        .prefix(prefix)
                        .time(time)
                        .build();
                final Val v = getVal();
                catchLmdbError(() -> writer.addTemporalState(doc, new TemporalState(k, v)));
            }
        }
    }

    private void addRangeState(final PlanBDoc doc) {
        // If key is provided then from/to are the same.
        if (key != null) {
            if (rangeFrom != null) {
                error(LogUtil.message("Range state 'key` provided plus `from' for {}", mapName));
            } else if (rangeTo != null) {
                error(LogUtil.message("Range state 'key` provided plus `to' for {}", mapName));
            } else {
                try {
                    final long longKey = Long.parseLong(key);
                    if (longKey < 0) {
                        // negative values cause problems for the ordering of data in LMDB so prevent
                        // their use when using byteBuffer.putLong, -10, 0 & 10 will be stored in LMDB
                        // as 0, 10, -10
                        error(LogUtil.message(
                                "Range state only supports non-negative numbers (key: {}) for {}",
                                longKey, mapName));
                    } else {
                        final RangeState.Key k = RangeState.Key.builder()
                                .keyStart(longKey)
                                .keyEnd(longKey)
                                .build();
                        final Val v = getVal();
                        catchLmdbError(() -> writer.addRangeState(doc, new RangeState(k, v)));
                    }
                } catch (final RuntimeException e) {
                    error("Unable to parse string \"" + key + "\" as long for range", e);
                }
            }

        } else {
            if (rangeFrom == null) {
                error(LogUtil.message("Range state 'from' is null for {}", mapName));
            } else if (rangeTo == null) {
                error(LogUtil.message("Range state 'to' is null for {}", mapName));
            } else if (rangeFrom > rangeTo) {
                error(LogUtil.message(
                        "Range 'from' must be less than or equal to range 'to' " +
                        "(from: {}, to: {}) for {}",
                        rangeFrom, rangeTo, mapName));
            } else if (rangeFrom < 0) {
                // negative values cause problems for the ordering of data in LMDB so prevent their use
                // when using byteBuffer.putLong, -10, 0 & 10 will be stored in LMDB as 0, 10, -10
                error(LogUtil.message(
                        "Range state only supports non-negative numbers (from: {}, to: {}) for {}",
                        rangeFrom, rangeTo, mapName));
            } else {
                final RangeState.Key k = RangeState.Key.builder()
                        .keyStart(rangeFrom)
                        .keyEnd(rangeTo)
                        .build();
                final Val v = getVal();
                catchLmdbError(() -> writer.addRangeState(doc, new RangeState(k, v)));
            }
        }
    }

    private void addTemporalRangeState(final PlanBDoc doc) {
        final Instant time = this.time != null
                ? this.time
                : this.effectiveTime;
        if (time == null) {
            error(LogUtil.message("Temporal range range 'time' is null for {}", mapName));

        } else {
            // If key is provided then from/to are the same.
            if (key != null) {
                if (rangeFrom != null) {
                    error(LogUtil.message("Temporal range state 'key` provided plus `from' for {}",
                            mapName));
                } else if (rangeTo != null) {
                    error(LogUtil.message("Temporal range state 'key` provided plus `to' for {}",
                            mapName));
                } else {
                    try {
                        final long longKey = Long.parseLong(key);
                        if (longKey < 0) {
                            // negative values cause problems for the ordering of data in LMDB so
                            // prevent their use when using byteBuffer.putLong, -10, 0 & 10 will be
                            // stored in LMDB as 0, 10, -10
                            error(LogUtil.message(
                                    "Temporal range state only supports non-negative numbers " +
                                    "(key: {}) for {}",
                                    longKey,
                                    mapName));
                        } else {
                            final TemporalRangeState.Key k = TemporalRangeState.Key.builder()
                                    .keyStart(longKey)
                                    .keyEnd(longKey)
                                    .time(time)
                                    .build();
                            final Val v = getVal();
                            catchLmdbError(() -> writer.addTemporalRangeState(doc, new TemporalRangeState(k, v)));
                        }
                    } catch (final RuntimeException e) {
                        error("Unable to parse string \"" + key + "\" as long for range", e);
                    }
                }

            } else {
                if (rangeFrom == null) {
                    error(LogUtil.message("Temporal range 'from' is null for {}", mapName));
                } else if (rangeTo == null) {
                    error(LogUtil.message("Temporal range 'to' is null for {}", mapName));
                } else if (rangeFrom > rangeTo) {
                    error(LogUtil.message(
                            "Temporal range 'from' must be less than or equal to range 'to' " +
                            "(from: {}, to: {}) for {}",
                            rangeFrom, rangeTo, mapName));
                } else if (rangeFrom < 0) {
                    // negative values cause problems for the ordering of data in LMDB so prevent their
                    // use when using byteBuffer.putLong, -10, 0 & 10 will be stored in LMDB
                    // as 0, 10, -10
                    error(LogUtil.message(
                            "Temporal range only supports non-negative numbers " +
                            "(from: {}, to: {}) for {}",
                            rangeFrom,
                            rangeTo,
                            mapName));
                } else {
                    final TemporalRangeState.Key k = TemporalRangeState.Key.builder()
                            .keyStart(rangeFrom)
                            .keyEnd(rangeTo)
                            .time(time)
                            .build();
                    final Val v = getVal();
                    catchLmdbError(() -> writer.addTemporalRangeState(doc, new TemporalRangeState(k, v)));
                }
            }
        }
    }

    private void addSession(final PlanBDoc doc) {
        final KeyPrefix prefix = getKeyPrefix();
        if (prefix != null) {
            if (time == null) {
                error(LogUtil.message("Session 'time' is null for {}", mapName));
            } else {
                final Session.Builder sessionBuilder = Session
                        .builder()
                        .prefix(prefix)
                        .start(time)
                        .end(time);
                if (timeout != null) {
                    sessionBuilder.end(time.plus(timeout));
                }

                LOGGER.trace("Putting session {} into table {}", sessionBuilder.build(), mapName);
                catchLmdbError(() -> writer.addSession(doc, sessionBuilder.build()));
            }
        }
    }

    private void addHistogramValue(final PlanBDoc doc) {
        final KeyPrefix prefix = getKeyPrefix();
        if (prefix != null) {
            if (time == null) {
                error(LogUtil.message("Histogram 'time' is null for {}", mapName));
            } else {
                final TemporalKey temporalKey = new TemporalKey(prefix, time);
                final TemporalValue temporalValue = new TemporalValue(temporalKey, Long.parseLong(currentValue));
                LOGGER.trace("Putting histogram value {} into table {}", temporalKey, mapName);
                catchLmdbError(() -> writer.addHistogramValue(doc, temporalValue));
            }
        }
    }

    private void addMetricValue(final PlanBDoc doc) {
        final KeyPrefix prefix = getKeyPrefix();
        if (prefix != null) {
            if (time == null) {
                error(LogUtil.message("Metric 'time' is null for {}", mapName));
            } else {
                final TemporalKey temporalKey = new TemporalKey(prefix, time);
                final TemporalValue temporalValue = new TemporalValue(temporalKey, Long.parseLong(currentValue));
                LOGGER.trace("Putting metric value {} into table {}", temporalKey, mapName);
                catchLmdbError(() -> writer.addMetricValue(doc, temporalValue));
            }
        }
    }

    private void addSpanValue(final PlanBDoc doc) {
        if (span == null) {
            error(LogUtil.message("No span data for {}", mapName));
        } else {
            try {
                final SpanKey spanKey = SpanKey.create(span);
                final SpanValue spanValue = SpanValue.create(span);
                LOGGER.trace("Putting span value {} into table {}", span, mapName);
                catchLmdbError(() -> writer.addSpanValue(doc, new SpanKV(spanKey, spanValue)));
            } catch (final RuntimeException e) {
                log(Severity.ERROR, e.getMessage(), e);
            }

            currentValue = null;
        }
    }

    private KeyPrefix getKeyPrefix() {
        final KeyPrefix prefix;

        if (currentTags == null || currentTags.isEmpty()) {
            if (key == null) {
                error(LogUtil.message("Histogram 'key' is null for {}", mapName));
                prefix = null;
            } else {
                prefix = KeyPrefix.create(key);
            }
        } else {
            prefix = KeyPrefix.create(currentTags);
        }
        return prefix;
    }

    private Val getVal() {
        return switch (type) {
            case STRING -> ValString.create(currentStringValue);
            case XML -> {
                final ByteBuffer value = stagingValueOutputStream.getByteBuffer();
                value.flip();
                yield ValXml.create(ByteBufferUtils.getBytes(value));
            }
            default -> ValNull.INSTANCE;
        };
    }

    /**
     * @param ch     An array of characters.
     * @param start  The starting position in the array.
     * @param length The number of characters to use from the array.
     * @throws SAXException The client may throw an exception during processing.
     * @see AbstractXMLFilter#characters(char[],
     * int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (spanHandler != null) {
            spanHandler.characters(ch, start, length);

        } else {
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
                }
            } else {
                // outside the value element so capture the chars, so we can get keys, map names, etc.
                contentBuffer.append(ch, start, length);
            }
        }

        super.characters(ch, start, length);
    }

    private boolean isAllWhitespace(final char[] ch, final int start, final int length) {

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

    private void error(final String message) {
        log(Severity.ERROR, message, null);
    }

    private void error(final String message, final Exception e) {
        log(Severity.ERROR, message, e);
    }

    private void error(final Exception e) {
        log(Severity.ERROR, e.getMessage(), e);
    }

    private void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
    }
}
