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

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import stroom.datasource.api.DataSourceField;
import stroom.node.server.StroomPropertyService;
import stroom.statistics.common.CommonStatisticConstants;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.Statistics;
import stroom.statistics.common.StatisticsFactory;
import stroom.statistics.shared.StatisticStore;
import stroom.statistics.sql.SQLStatisticEventStore;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomStartup;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class StatisticEventStoreFactoryImpl implements StatisticsFactory, InitializingBean {
    private final Map<String, String> statisticEventStoreEngineMap = new HashMap<>();

    private final String NOT_SUPPORTED_ERROR_TEXT = "This method is not supported for multiple statistics engines";

    @Resource
    StroomBeanStore stroomBeanStore;

    @Resource
    private StroomPropertyService stroomPropertyService;

    @Override
    public void afterPropertiesSet() throws Exception {
        initStatisticEventStoreBeanNames();

    }

    @Override
    @StroomStartup(priority = 100)
    public void initStatisticEventStoreBeanNames() {
        final List<String> allBeans = new ArrayList<>(stroomBeanStore.getStroomBeanByType(Statistics.class));
        for (final String beanName : allBeans) {
            final Statistics statistics = (Statistics) stroomBeanStore.getBean(beanName);

            // only add stores that are spring beans and are enabled
            if (statistics != null
                    && SQLStatisticEventStore.isDataStoreEnabled(statistics.getEngineName(), stroomPropertyService)) {
                statisticEventStoreEngineMap.put(toKey(statistics.getEngineName()), beanName);
            }
        }
    }

    /**
     * @return The engine names for all engines that have registered beans
     */
    @Override
    public Set<String> getAllEngineNames() {
        return statisticEventStoreEngineMap.keySet();
    }

    private List<String> getDefaultEngines() {
        final ArrayList<String> rtnList = new ArrayList<>();

        final String engines = stroomPropertyService
                .getProperty(CommonStatisticConstants.STROOM_STATISTIC_ENGINES_PROPERTY_NAME);

        if (StringUtils.hasText(engines)) {
            final String[] enginesWanted = engines.split(",");
            for (final String engineWanted : enginesWanted) {
                if (statisticEventStoreEngineMap.containsKey(toKey(engineWanted))) {
                    rtnList.add(engineWanted);
                }
            }
        }

        // return an empty list if the property is not set
        return rtnList;
    }

    private String toKey(final String name) {
        return name.trim().toUpperCase();
    }

    @Override
    public Statistics instance() {
        return instance(getDefaultEngines());
    }

    @Override
    public Statistics instance(final String engineName) {
        final List<String> engineNames = new ArrayList<String>();
        engineNames.add(engineName);
        return instance(engineNames);
    }

    @Override
    public Statistics instance(final List<String> engineNames) {
        Statistics storeInstance;

        if (engineNames.isEmpty()) {
            // return an object that will just return silently on all calls
            storeInstance = new DoNothingStatisticEventStore();

        } else {
            // work out which of the stores requested are actually enabled
            final List<String> enabledEngines = new ArrayList<String>();
            for (final String engineName : engineNames) {
                if (statisticEventStoreEngineMap.containsKey(toKey(engineName))) {
                    enabledEngines.add(engineName);
                }
            }

            if (enabledEngines.isEmpty()) {
                // return an object that will just return silently on all calls
                storeInstance = new DoNothingStatisticEventStore();

            } else if (enabledEngines.size() == 1) {
                // just return the actual bean asked for
                storeInstance = getStoreBean(enabledEngines.get(0));
            } else {
                final List<Statistics> implList = new ArrayList<>();
                for (final String engineName : enabledEngines) {
                    implList.add(getStoreBean(engineName));
                }

                // return a class that will act as a proxy to all enabled
                // StatisticeventStore implementations and
                // run the method on each, possibly aggregating results. This
                // proxy
                // approach only applies to the internal
                // statistics generated by the java code, not the pipeline
                // generated
                // stats. Pipeline stats will always be
                // against a StatisticDataSource.
                // Not all methods are valid to be run on multiple engines so
                // will throw
                // an exception if that is done
                storeInstance = new MultiStoreStatisticEventStore(implList);
            }
        }

        return storeInstance;
    }

    private Statistics getStoreBean(final String engineName) {
        return (Statistics) stroomBeanStore.getBean(statisticEventStoreEngineMap.get(toKey(engineName)));
    }

    public static class MultiStoreStatisticEventStore implements Statistics {
        private final String NOT_SUPPORTED_ERROR_TEXT = "This method is not supported for multiple statistics engines";
        private final List<Statistics> implList;

        public MultiStoreStatisticEventStore(final List<Statistics> implList) {
            this.implList = implList;
        }

        @Override
        public void putEvents(final List<StatisticEvent> statisticEvents) {
            for (final Statistics statisticEventStore : implList) {
                statisticEventStore.putEvents(statisticEvents);
            }
        }

        @Override
        public void putEvents(final List<StatisticEvent> statisticEvents,
                final StatisticStore statisticsDataSource) {
            for (final Statistics statisticEventStore : implList) {
                statisticEventStore.putEvents(statisticEvents, statisticsDataSource);
            }
        }

        @Override
        public void putEvent(final StatisticEvent statisticEvent) {
            for (final Statistics statisticEventStore : implList) {
                // OR the results together, i.e. considered a success if we
                // could send to at least one engine
                statisticEventStore.putEvent(statisticEvent);
            }
        }

        @Override
        public void putEvent(final StatisticEvent statisticEvent, final StatisticStore statisticsDataSource) {
            boolean result = false;
            for (final Statistics statisticEventStore : implList) {
                statisticEventStore.putEvent(statisticEvent, statisticsDataSource);
            }
        }

        @Override
        public String getEngineName() {
            final StringBuilder stringBuilder = new StringBuilder();
            for (final Statistics store : implList) {
                stringBuilder.append(store.getEngineName());
            }

            return stringBuilder.toString();
        }

        @Override
        public List<String> getValuesByTag(final String tagName) {
            throw new UnsupportedOperationException(NOT_SUPPORTED_ERROR_TEXT);
        }

        @Override
        public List<String> getValuesByTagAndPartialValue(final String tagName, final String partialValue) {
            throw new UnsupportedOperationException(NOT_SUPPORTED_ERROR_TEXT);
        }

        @Override
        public List<DataSourceField> getSupportedFields(final List<DataSourceField> indexFields) {
            throw new UnsupportedOperationException(NOT_SUPPORTED_ERROR_TEXT);
        }

        @Override
        public void flushAllEvents() {
            for (final Statistics statisticEventStore : implList) {
                statisticEventStore.flushAllEvents();
            }
        }
    }

    public static class DoNothingStatisticEventStore implements Statistics {
        @Override
        public String getEngineName() {
            return "DO_NOTHING";
        }

        @Override
        public void putEvent(final StatisticEvent statisticEvent, final StatisticStore statisticsDataSource) {
            // return silently having done nothing as we have no stat store to
            // put stats to
        }

        @Override
        public void putEvents(final List<StatisticEvent> statisticEvents,
                final StatisticStore statisticsDataSource) {
            // return silently having done nothing as we have no stat store to
            // put stats to
        }

        @Override
        public void putEvent(final StatisticEvent statisticEvent) {
            // return silently having done nothing as we have no stat store to
            // put stats to
        }

        @Override
        public void putEvents(final List<StatisticEvent> statisticEvents) {
            // return silently having done nothing as we have no stat store to
            // put stats to
        }

        // @Override
        // public void refreshMetadata() {
        // // Do nothing as we have no store to refresh
        // }

        @Override
        public List<String> getValuesByTag(final String tagName) {
            // return an empty list as we have no store to fetch data from
            return Collections.emptyList();
        }

        @Override
        public List<String> getValuesByTagAndPartialValue(final String tagName, final String partialValue) {
            // return an empty list as we have no store to fetch data from
            return Collections.emptyList();
        }

        @Override
        public List<DataSourceField> getSupportedFields(final List<DataSourceField> indexFields) {
            return null;
        }

        @Override
        public void flushAllEvents() {
            // Do nothing as we have no store to flush
        }
    }
}
