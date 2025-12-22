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

package stroom.meta.statistics.impl;

import stroom.meta.statistics.api.MetaStatistics;
import stroom.security.api.SecurityContext;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.util.date.DateUtil;

import com.google.common.base.Preconditions;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

@Singleton
class MetaStatisticsImpl implements MetaStatistics {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetaStatisticsImpl.class);

    private final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider;
    private final SecurityContext securityContext;

    private List<MetaStatisticsTemplate> templates;

    @Inject
    MetaStatisticsImpl(final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider,
                       final SecurityContext securityContext) {
        this.internalStatisticsReceiverProvider = internalStatisticsReceiverProvider;
        this.securityContext = securityContext;
    }

    @Override
    public void recordStatistics(final Map<String, String> metaData) {
        securityContext.asProcessingUser(() -> {
            final InternalStatisticsReceiver receiver = internalStatisticsReceiverProvider.get();
            if (receiver != null) {
                for (final MetaStatisticsTemplate template : templates) {
                    try {
                        final InternalStatisticEvent statisticEvent = buildStatisticEvent(template, metaData);
                        if (statisticEvent != null) {
                            receiver.putEvent(statisticEvent);
                        } else {
                            LOGGER.trace("recordStatistics() - abort {} {}", metaData, template);
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.error("recordStatistics() - abort {} {}", metaData, template, e);
                    }
                }
            }
        });
    }

    /**
     * @return build the STAT or return null for not valid
     */
    private InternalStatisticEvent buildStatisticEvent(final MetaStatisticsTemplate template,
                                                       final Map<String, String> metaData) {
        Preconditions.checkNotNull(template);
        Preconditions.checkNotNull(template.getTimeMsAttribute());
        Preconditions.checkNotNull(template.getKey());
        Preconditions.checkNotNull(metaData);

        final long timeMs;
        final String timeValue = metaData.get(template.getTimeMsAttribute());
        if (timeValue != null && !timeValue.isEmpty()) {
            try {
                timeMs = DateUtil.parseNormalDateTimeString(timeValue);
            } catch (final RuntimeException e) {
                // Quit!
                return null;
            }
        } else {
            LOGGER.error("HeaderMap [{}] has no time attribute, unable to create stat", metaData);
            return null;
        }

        final SortedMap<String, String> statisticTags = new TreeMap<>();

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
                } catch (final RuntimeException e) {
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

    public void setTemplates(final List<MetaStatisticsTemplate> templates) {
        this.templates = templates;
    }
}
