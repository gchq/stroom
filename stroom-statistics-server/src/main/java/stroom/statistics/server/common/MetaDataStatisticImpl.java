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

package stroom.statistics.server.common;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.util.StringUtils;

import stroom.statistic.server.MetaDataStatistic;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.StatisticTag;
import stroom.statistics.common.Statistics;
import stroom.statistics.common.StatisticsFactory;
import stroom.util.date.DateUtil;
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomStartup;
import stroom.util.zip.HeaderMap;

/**
 * This is deliberately not declared as a component as the StatisticsConfiguration creates the bean.
 */
public class MetaDataStatisticImpl implements MetaDataStatistic {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(MetaDataStatisticImpl.class);

    private List<MetaDataStatisticTemplate> templates;
    private StatisticsFactory statisticEventStoreFactory;
    private volatile Statistics statisticEventStore;

    @Resource
    public void setStatisticEventStore(StatisticsFactory statisticEventStoreFactory) {
        this.statisticEventStoreFactory = statisticEventStoreFactory;
    }

    @StroomStartup
    public void afterPropertiesSet() throws Exception {
        statisticEventStore = statisticEventStoreFactory.instance();
    }

    /**
     * @return build the STAT or return null for not valid
     */
    private StatisticEvent buildStatisticEvent(final MetaDataStatisticTemplate template, final HeaderMap metaData) {
        Long timeMs = null;
        final String timeValue = metaData.get(template.getTimeMsAttribute());
        if (StringUtils.hasText(timeValue)) {
            try {
                timeMs = DateUtil.parseNormalDateTimeString(timeValue);
            } catch (final Exception ex) {
                // Quit!
                return null;
            }
        }

        final List<StatisticTag> statisticTagList = new ArrayList<StatisticTag>();

        for (final String tagName : template.getTagAttributeList()) {
            final String tagValue = metaData.get(tagName);

            if (StringUtils.hasText(tagValue)) {
                statisticTagList.add(new StatisticTag(tagName, tagValue));
            } else {
                // Quit!
                return null;
            }
        }

        long increment = 1;
        if (template.getIncrementAttribute() != null) {
            final String incrementValue = metaData.get(template.getIncrementAttribute());
            if (StringUtils.hasText(incrementValue)) {
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
        return new StatisticEvent(timeMs, template.getName(), statisticTagList, increment);

    }

    @Override
    public void recordStatistics(final HeaderMap metaData) {
        if (templates != null && templates.size() > 0) {
            for (final MetaDataStatisticTemplate template : templates) {
                try {
                    final StatisticEvent statisticEvent = buildStatisticEvent(template, metaData);
                    if (statisticEvent != null) {
                        statisticEventStore.putEvent(statisticEvent);
                    } else {
                        LOGGER.trace("recordStatistics() - abort %s", metaData);
                    }
                } catch (final Exception ex) {
                    LOGGER.trace("recordStatistics() - abort %s", metaData, ex);
                }
            }
        }
    }

    @StroomFrequencySchedule("1m")
    public void flush() {
        // Get a new instance
        statisticEventStore = statisticEventStoreFactory.instance();
    }

    public void setTemplates(final List<MetaDataStatisticTemplate> templates) {
        this.templates = templates;
    }
}
