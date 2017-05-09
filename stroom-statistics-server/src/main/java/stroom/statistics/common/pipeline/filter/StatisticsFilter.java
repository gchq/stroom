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

package stroom.statistics.common.pipeline.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.server.filter.AbstractXMLFilter;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.StatisticStoreEntityService;
import stroom.statistics.common.StatisticTag;
import stroom.statistics.common.Statistics;
import stroom.statistics.common.StatisticsEventValidatorFactory;
import stroom.statistics.common.StatisticsFactory;
import stroom.statistics.shared.common.StatisticField;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticType;
import stroom.util.date.DateUtil;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The statistic filter used to gather event based statistics.
 */
@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(type = "StatisticsFilter", category = Category.FILTER, roles = {
        PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_HAS_TARGETS,
        PipelineElementType.VISABILITY_SIMPLE }, icon = ElementIcons.STATISTICS)
public class StatisticsFilter extends AbstractXMLFilter {
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
    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsFilter.class);
    private final ErrorReceiverProxy errorReceiverProxy;
    private final LocationFactoryProxy locationFactory;
    private final StatisticsFactory statisticEventStoreFactory;
    private final StatisticStoreEntityService statisticsDataSourceService;
    private final List<StatisticEvent> statisticEventList = new ArrayList<>(EVENT_BUFFER_SIZE);
    private final StringBuilder textBuffer = new StringBuilder();
    private final Map<String, String> emptyTagToValueMap = new HashMap<String, String>();
    private StatisticStoreEntity statisticsDataSource;
    private Statistics statisticEventStore;
    /**
     * Events attributes
     */
    private Map<String, String> currentTagToValueMap = new HashMap<String, String>();
    private String currentTagName;
    private String currentTagValue;
    private Long currentEventTimeMs;
    private Double currentStatisticValue;
    private Long currentStatisticCount;
    private Locator locator;

    @Inject
    public StatisticsFilter(final ErrorReceiverProxy errorReceiverProxy, final LocationFactoryProxy locationFactory,
                            final StatisticsFactory statisticEventStoreFactory,
                            final StatisticStoreEntityService statisticsDataSourceService) {
        this.errorReceiverProxy = errorReceiverProxy;
        this.locationFactory = locationFactory;
        this.statisticEventStoreFactory = statisticEventStoreFactory;
        this.statisticsDataSourceService = statisticsDataSourceService;
    }

    @Override
    public void startProcessing() {
        if (statisticsDataSource == null) {
            log(Severity.FATAL_ERROR, "Statistics data source has not been set", null);
            throw new LoggedException("Statistics data source has not been set");
        }

        // Reload the data source as we might have new fields.
        statisticsDataSource = statisticsDataSourceService.load(statisticsDataSource);

        if (statisticsDataSource == null) {
            log(Severity.FATAL_ERROR, "Unable to load Statistics data source ", null);
            throw new LoggedException("Unable to load Statistics data source ");
        }

        if (!statisticsDataSource.isEnabled()) {
            final String msg = "Statistics data source with name [" + statisticsDataSource.getName() + "] is disabled";
            log(Severity.FATAL_ERROR, msg, null);
            throw new LoggedException(msg);
        }

        // clean out the map of field names to values
        for (final String fieldName : statisticsDataSource.getFieldNames()) {
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
        return statisticEventStoreFactory.instance(this.statisticsDataSource.getEngineName());
    }

    private void flush() {
        // Have we any events to write?
        if (statisticEventList.size() > 0) {
            if (statisticEventStore == null) {
                statisticEventStore = getStatisticEventStore();
            }
            LOGGER.debug("Flushing {} stats from the statisticfilter", statisticEventList.size());
            statisticEventStore.putEvents(statisticEventList, statisticsDataSource);
            statisticEventList.clear();
        }
    }

    private void putEvent(final StatisticEvent statisticEvent) {
        final List<String> warnings = StatisticsEventValidatorFactory.getInstance(statisticsDataSource.getEngineName())
                .validateEvent(statisticEvent);

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
        if (timeString != null && timeString.length() > 0) {
            currentEventTimeMs = DateUtil.parseNormalDateTimeString(timeString);
        }
    }

    /**
     * Handle a start element
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        textBuffer.setLength(0);

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
                        if (!statisticsDataSource.isValidField(currentTagName)) {
                            throw new RuntimeException(String.format(
                                    "Statistic record contains a tag name [%s] that is not valid for this statistic data source [%s]",
                                    currentTagName, statisticsDataSource.getName()));
                        }
                    } else if (TAG_VALUE.equals(attLocalName)) {
                        currentTagValue = attValue;
                    }
                }
            }
        } catch (final Exception e) {
            error(e);
        }

        super.startElement(uri, localName, qName, atts);
    }

    private void error(final Exception e) {
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

    void clearCurrentVariables() {
        currentEventTimeMs = null;
        currentStatisticValue = null;
        currentStatisticCount = null;

        // swap the map out with a version that contains null for each field
        // name
        currentTagToValueMap = emptyTagToValueMap;

        clearCurrentTagNameAndValue();

    }

    void clearCurrentTagNameAndValue() {
        currentTagName = null;
        currentTagValue = null;
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        try {
            if (TIME.equals(localName)) {
                processTime(textBuffer.toString());
            } else if (TAG.equals(localName)) {
                currentTagToValueMap.put(currentTagName, currentTagValue);
                clearCurrentTagNameAndValue();
            } else if (VALUE.equals(localName)) {
                if (!StatisticType.VALUE.equals(statisticsDataSource.getStatisticType())) {
                    throw new RuntimeException(String.format("Found <%s> XML element for a statistic type of %s", VALUE,
                            statisticsDataSource.getStatisticType()));
                }
                try {
                    currentStatisticValue = Double.valueOf(textBuffer.toString());
                } catch (final Exception e) {
                    throw new RuntimeException(String.format("Statistic vlaue [%s] cannot be converted to a double",
                            textBuffer.toString()), e);
                }
            } else if (COUNT.equals(localName)) {
                if (!StatisticType.COUNT.equals(statisticsDataSource.getStatisticType())) {
                    throw new RuntimeException(String.format("Found <%s> XML element for a statistic type of %s", COUNT,
                            statisticsDataSource.getStatisticType()));
                }
                if (textBuffer.length() == 0) {
                    // no value supplied so assume 1
                    currentStatisticCount = 1L;
                } else {
                    try {
                        currentStatisticCount = Long.valueOf(textBuffer.toString());
                    } catch (final Exception e) {
                        throw new RuntimeException(String.format("Statistic count [%s] cannot be converted to a long",
                                textBuffer.toString()), e);
                    }
                }
            } else if (STATISTIC.equals(localName)) {
                StatisticEvent statisticEvent;

                final List<StatisticTag> tagList = new ArrayList<StatisticTag>(
                        statisticsDataSource.getStatisticFieldCount());

                // construct a list of stat tags in the correct order, as
                // defined by the SDS
                for (final StatisticField statisticField : statisticsDataSource.getStatisticFields()) {
                    final String tagName = statisticField.getFieldName();
                    tagList.add(new StatisticTag(tagName, currentTagToValueMap.get(tagName)));
                }

                if (currentTagToValueMap.size() != statisticsDataSource.getFieldNames().size()) {
                    throw new RuntimeException(String.format(
                            "Number of tags in the data source [%s] does not agree with the number in the record passed to the filter [%s]",
                            statisticsDataSource.getFieldNames().size(), currentTagToValueMap.size()));
                }

                if (statisticsDataSource.getStatisticType().equals(StatisticType.COUNT)) {
                    if (currentStatisticCount == null) {
                        // assume count of 1 if element was omitted
                        currentStatisticCount = 1L;
                    }

                    statisticEvent = new StatisticEvent(currentEventTimeMs, statisticsDataSource.getName(),
                            new ArrayList<StatisticTag>(tagList), currentStatisticCount);

                } else {
                    if (currentStatisticValue == null) {
                        throw new RuntimeException(
                                "No <value> element was found. Value statistics must have a valid value");
                    }
                    statisticEvent = new StatisticEvent(currentEventTimeMs, statisticsDataSource.getName(),
                            new ArrayList<StatisticTag>(tagList), currentStatisticValue);
                }

                putEvent(statisticEvent);
            } else if (TAGS.equals(localName) || STATISTICS.equals(localName)) {
                // nothing to do
            } else {
                throw new RuntimeException(String.format("Encountered unexpected element with name [%s]", localName));
            }
        } catch (final Exception e) {
            error(e);
        }

        textBuffer.setLength(0);
        super.endElement(uri, localName, qName);
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        textBuffer.append(ch, start, length);
        super.characters(ch, start, length);
    }

    @PipelineProperty(description = "The statistics data source to record statistics against.")
    public void setStatisticsDataSource(final StatisticStoreEntity statisticsDataSource) {
        this.statisticsDataSource = statisticsDataSource;
    }

    private void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
    }
}
