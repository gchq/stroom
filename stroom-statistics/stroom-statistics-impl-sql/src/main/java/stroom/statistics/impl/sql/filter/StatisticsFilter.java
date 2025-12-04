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

package stroom.statistics.impl.sql.filter;

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
import stroom.statistics.impl.sql.SQLStatisticsEventValidator;
import stroom.statistics.impl.sql.StatisticEvent;
import stroom.statistics.impl.sql.StatisticTag;
import stroom.statistics.impl.sql.Statistics;
import stroom.statistics.impl.sql.entity.StatisticStoreStore;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.svg.shared.SvgImage;
import stroom.util.CharBuffer;
import stroom.util.date.DateUtil;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The statistic filter used to gather event based statistics.
 */
@ConfigurableElement(
        type = "StatisticsFilter",
        description = """
                An element to allow the source data (conforming to the `statistics` XML Schema) \
                to be sent to the MySQL based statistics data store.
                """,
        category = Category.FILTER,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE},
        icon = SvgImage.PIPELINE_STATISTICS)
public class StatisticsFilter extends AbstractXMLFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsFilter.class);

    /**
     * Define all the element and attribute names
     */
    private static final String STATISTIC = "statistic";
    private static final String STATISTICS = "statistics";
    private static final String TIME = "time";
    private static final String TAG = "tag";
    private static final String TAGS = "tags";
    private static final String TAG_NAME = "name";
    private static final String TAG_VALUE = "value";
    private static final String COUNT = "count";
    private static final String VALUE = "value";
    private static final int EVENT_BUFFER_SIZE = 1000;

    private final ErrorReceiverProxy errorReceiverProxy;
    private final LocationFactoryProxy locationFactory;
    private final StatisticStoreStore statisticStoreStore;
    private final Statistics statistics;
    private final List<StatisticEvent> statisticEventList = new ArrayList<>(EVENT_BUFFER_SIZE);
    private final CharBuffer content = new CharBuffer();
    private final Map<String, String> emptyTagToValueMap = new HashMap<>();
    private DocRef statisticStoreRef;
    private StatisticStoreDoc statisticStoreEntity;
    private Statistics statisticEventStore;
    /**
     * Events attributes
     */
    private Map<String, String> currentTagToValueMap = new HashMap<>();
    private String currentTagName;
    private String currentTagValue;
    private Long currentEventTimeMs;
    private Double currentStatisticValue;
    private Long currentStatisticCount;
    private Locator locator;

    @Inject
    public StatisticsFilter(final ErrorReceiverProxy errorReceiverProxy,
                            final LocationFactoryProxy locationFactory,
                            final Statistics statistics,
                            final StatisticStoreStore statisticStoreStore) {
        this.errorReceiverProxy = errorReceiverProxy;
        this.locationFactory = locationFactory;
        this.statistics = statistics;
        this.statisticStoreStore = statisticStoreStore;
    }

    @Override
    public void startProcessing() {
        if (statisticStoreRef == null) {
            log(Severity.FATAL_ERROR, "Statistics data source has not been set", null);
            throw LoggedException.create("Statistics data source has not been set");
        }

        // Reload the data source as we might have new fields.
        statisticStoreEntity = statisticStoreStore.readDocument(statisticStoreRef);

        if (statisticStoreEntity == null) {
            log(Severity.FATAL_ERROR, "Unable to load Statistics data source ", null);
            throw LoggedException.create("Unable to load Statistics data source ");
        }

        if (!statisticStoreEntity.isEnabled()) {
            final String msg = "Statistics data source with name [" + statisticStoreEntity.getName() + "] is disabled";
            log(Severity.FATAL_ERROR, msg, null);
            throw LoggedException.create(msg);
        }

        // clean out the map of field names to values
        for (final String fieldName : statisticStoreEntity.getFieldNames()) {
            emptyTagToValueMap.put(fieldName, null);
        }
    }

    @Override
    public void endProcessing() {
        try {
            flush();
        } finally {
            super.endProcessing();
        }
    }

    private Statistics getStatisticEventStore() {
        return statistics;
    }

    private void flush() {
        // Have we any events to write?
        if (!statisticEventList.isEmpty()) {
            if (statisticEventStore == null) {
                statisticEventStore = getStatisticEventStore();
            }
            LOGGER.debug("Flushing {} stats from the statisticfilter", statisticEventList.size());
            statisticEventStore.putEvents(statisticEventList, statisticStoreEntity);
            statisticEventList.clear();
        }
    }

    private void putEvent(final StatisticEvent statisticEvent) {
        final List<String> warnings = SQLStatisticsEventValidator.validateEvent(statisticEvent);

        for (final String warning : warnings) {
            warn(warning);
        }

        statisticEventList.add(statisticEvent);
        if (statisticEventList.size() >= EVENT_BUFFER_SIZE) {
            flush();
        }
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        super.setDocumentLocator(locator);
        this.locator = locator;
    }

    private void processTime(final String timeString) {
        if (timeString != null && !timeString.isEmpty()) {
            currentEventTimeMs = DateUtil.parseNormalDateTimeString(timeString);
        }
    }

    /**
     * Handle a start element
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        try {
            if (STATISTIC.equals(localName)) {
                // reset all the variables holding current values
                clearCurrentVariables();
            }

            if (TAG.equals(localName)) {
                // this is a tag element so loop through all the attributes on
                // the element and capture the ones we want
                for (int i = 0; i < atts.getLength(); i++) {
                    final String attLocalName = atts.getLocalName(i);
                    final String attValue = atts.getValue(i);
                    if (TAG_NAME.equals(attLocalName)) {
                        currentTagName = attValue;
                        if (!statisticStoreEntity.isValidField(currentTagName)) {
                            throw new RuntimeException(String.format(
                                    "Statistic record contains a tag name [%s] that is not valid for this " +
                                            "statistic data source [%s]",
                                    currentTagName,
                                    statisticStoreEntity.getName()));
                        }
                    } else if (TAG_VALUE.equals(attLocalName)) {
                        currentTagValue = attValue;
                    }
                }
            }
        } catch (final RuntimeException e) {
            error(e);
        }

        content.clear();
        super.startElement(uri, localName, qName, atts);
    }

    private void error(final RuntimeException e) {
        if (locator != null) {
            errorReceiverProxy.log(Severity.ERROR,
                    locationFactory.create(locator.getLineNumber(), locator.getColumnNumber()), getElementId(),
                    e.getMessage(), e);
        } else {
            errorReceiverProxy.log(Severity.ERROR, null, getElementId(), e.getMessage(), e);
        }
    }

    private void warn(final String warningText) {
        if (locator != null) {
            errorReceiverProxy.log(Severity.WARNING,
                    locationFactory.create(locator.getLineNumber(), locator.getColumnNumber()), getElementId(),
                    warningText, null);
        } else {
            errorReceiverProxy.log(Severity.WARNING, null, getElementId(), warningText, null);
        }
    }

    private void clearCurrentVariables() {
        currentEventTimeMs = null;
        currentStatisticValue = null;
        currentStatisticCount = null;

        // swap the map out with a version that contains null for each field
        // name
        currentTagToValueMap = emptyTagToValueMap;

        clearCurrentTagNameAndValue();

    }

    private void clearCurrentTagNameAndValue() {
        currentTagName = null;
        currentTagValue = null;
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        try {
            if (TIME.equals(localName)) {
                processTime(content.toString());
            } else if (TAG.equals(localName)) {
                currentTagToValueMap.put(currentTagName, currentTagValue);
                clearCurrentTagNameAndValue();
            } else if (VALUE.equals(localName)) {
                if (!StatisticType.VALUE.equals(statisticStoreEntity.getStatisticType())) {
                    throw new RuntimeException(String.format("Found <%s> XML element for a statistic type of %s", VALUE,
                            statisticStoreEntity.getStatisticType()));
                }
                try {
                    currentStatisticValue = Double.valueOf(content.toString());
                } catch (final RuntimeException e) {
                    throw new RuntimeException(String.format("Statistic value [%s] cannot be converted to a double",
                            content), e);
                }
            } else if (COUNT.equals(localName)) {
                if (!StatisticType.COUNT.equals(statisticStoreEntity.getStatisticType())) {
                    throw new RuntimeException(String.format("Found <%s> XML element for a statistic type of %s", COUNT,
                            statisticStoreEntity.getStatisticType()));
                }
                if (content.isEmpty()) {
                    // no value supplied so assume 1
                    currentStatisticCount = 1L;
                } else {
                    try {
                        currentStatisticCount = Long.valueOf(content.toString());
                    } catch (final RuntimeException e) {
                        throw new RuntimeException(String.format("Statistic count [%s] cannot be converted to a long",
                                content), e);
                    }
                }
            } else if (STATISTIC.equals(localName)) {
                final StatisticEvent statisticEvent;

                final List<StatisticTag> tagList = new ArrayList<>(
                        statisticStoreEntity.getStatisticFieldCount());

                // construct a list of stat tags in the correct order, as
                // defined by the SDS
                for (final StatisticField statisticField : statisticStoreEntity.getStatisticFields()) {
                    final String tagName = statisticField.getFieldName();
                    tagList.add(new StatisticTag(tagName, currentTagToValueMap.get(tagName)));
                }

                if (currentEventTimeMs == null) {
                    throw new IllegalStateException("Statistic with missing timestamp. Cannot update " +
                            statisticStoreEntity.toString() +
                            " other tags associated with this record are as follows: " +
                            tagList.stream().map(StatisticTag::toString).collect(Collectors.joining(", ")));
                }

                if (currentTagToValueMap.size() != statisticStoreEntity.getFieldNames().size()) {
                    throw new RuntimeException(String.format(
                            "Number of tags in the data source [%s] does not agree with the number in the record " +
                                    "passed to the filter [%s]",
                            statisticStoreEntity.getFieldNames().size(),
                            currentTagToValueMap.size()));
                }

                if (statisticStoreEntity.getStatisticType().equals(StatisticType.COUNT)) {
                    if (currentStatisticCount == null) {
                        // assume count of 1 if element was omitted
                        currentStatisticCount = 1L;
                    }

                    statisticEvent = StatisticEvent.createCount(currentEventTimeMs, statisticStoreEntity.getName(),
                            new ArrayList<>(tagList), currentStatisticCount);

                } else {
                    if (currentStatisticValue == null) {
                        throw new RuntimeException(
                                "No <value> element was found. Value statistics must have a valid value");
                    }
                    statisticEvent = StatisticEvent.createValue(currentEventTimeMs, statisticStoreEntity.getName(),
                            new ArrayList<>(tagList), currentStatisticValue);
                }

                putEvent(statisticEvent);
            } else if (TAGS.equals(localName) || STATISTICS.equals(localName)) {
                // nothing to do
            } else {
                throw new RuntimeException(String.format("Encountered unexpected element with name [%s]", localName));
            }
        } catch (final RuntimeException e) {
            error(e);
        }

        content.clear();
        super.endElement(uri, localName, qName);
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        content.append(ch, start, length);
        super.characters(ch, start, length);
    }

    @PipelineProperty(description = "The statistics data source to record statistics against.", displayPriority = 1)
    @PipelinePropertyDocRef(types = StatisticStoreDoc.TYPE)
    public void setStatisticsDataSource(final DocRef statisticStoreRef) {
        this.statisticStoreRef = statisticStoreRef;
    }

    private void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
    }
}
