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

package stroom.streamtask.statistic;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.MetaMap;
import stroom.statistics.internal.InternalStatisticEvent;
import stroom.statistics.internal.InternalStatisticsReceiver;
import stroom.util.date.DateUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
class MetaDataStatisticImpl implements MetaDataStatistic {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetaDataStatisticImpl.class);

    private final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider;

    private List<MetaDataStatisticTemplate> templates;

    @Inject
    MetaDataStatisticImpl(final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider) {
        this.internalStatisticsReceiverProvider = internalStatisticsReceiverProvider;
    }

    @Override
    public void recordStatistics(final MetaMap metaData) {
        final InternalStatisticsReceiver receiver = internalStatisticsReceiverProvider.get();
        if (receiver != null) {
            for (final MetaDataStatisticTemplate template : templates) {
                try {
                    final InternalStatisticEvent statisticEvent = buildStatisticEvent(template, metaData);
                    if (statisticEvent != null) {
                        receiver.putEvent(statisticEvent);
                    } else {
                        LOGGER.trace("recordStatistics() - abort {} {}", metaData, template);
                    }
                } catch (final Exception ex) {
                    LOGGER.error("recordStatistics() - abort {} {}", metaData, template, ex);
                }
            }
        }
    }

    /**
     * @return build the STAT or return null for not valid
     */
    private InternalStatisticEvent buildStatisticEvent(final MetaDataStatisticTemplate template,
                                                       final MetaMap metaData) {
        Preconditions.checkNotNull(template);
        Preconditions.checkNotNull(template.getTimeMsAttribute());
        Preconditions.checkNotNull(template.getKey());
        Preconditions.checkNotNull(metaData);

        Long timeMs = null;
        final String timeValue = metaData.get(template.getTimeMsAttribute());
        if (timeValue != null && !timeValue.isEmpty()) {
            try {
                timeMs = DateUtil.parseNormalDateTimeString(timeValue);
            } catch (final Exception ex) {
                // Quit!
                return null;
            }
        } else {
            LOGGER.error("HeaderMap [{}] has no time attribute, unable to create stat", metaData);
            return null;
        }

        final Map<String, String> statisticTags = new HashMap<>();

        for (final String tagName : template.getTagAttributeList()) {
            final String tagValue = metaData.get(tagName);

            if (tagValue != null && !tagValue.isEmpty()) {
                statisticTags.put(tagName, tagValue);
            } else {
                // Quit!
                return null;
            }
        }

        long increment = 1;
        if (template.getIncrementAttribute() != null) {
            final String incrementValue = metaData.get(template.getIncrementAttribute());
            if (incrementValue != null && !incrementValue.isEmpty()) {
                try {
                    increment = Long.parseLong(incrementValue);
                } catch (final Exception ex) {
                    // Quit!
                    return null;
                }
            } else {
                // Quit!
                return null;
            }
        }
        return InternalStatisticEvent.createPlusNCountStat(template.getKey(), timeMs, statisticTags, increment);
    }

    public void setTemplates(final List<MetaDataStatisticTemplate> templates) {
        this.templates = templates;
    }
}
