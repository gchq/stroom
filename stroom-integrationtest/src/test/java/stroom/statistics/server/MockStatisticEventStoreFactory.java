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

package stroom.statistics.server;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.datasource.api.DataSourceField;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.Statistics;
import stroom.statistics.common.StatisticsFactory;
import stroom.statistics.shared.StatisticStore;
import stroom.util.spring.StroomSpringProfiles;

import java.util.List;
import java.util.Set;

@Component
@Profile(StroomSpringProfiles.TEST)
public class MockStatisticEventStoreFactory implements StatisticsFactory {
    @Override
    public void initStatisticEventStoreBeanNames() {
    }

    @Override
    public Statistics instance() {
        return new MockStatisticEventStore();
    }

    @Override
    public Statistics instance(final String engineName) {
        return new MockStatisticEventStore();
    }

    @Override
    public Statistics instance(final List<String> engineNames) {
        return new MockStatisticEventStore();
    }

    public static class MockStatisticEventStore implements Statistics {
        @Override
        public String getEngineName() {
            return null;
        }

        @Override
        public void putEvent(final StatisticEvent statisticEvent, final StatisticStore statisticsDataSource) {
        }

        @Override
        public void putEvents(final List<StatisticEvent> statisticEvents,
                final StatisticStore statisticsDataSource) {
        }

        @Override
        public void putEvent(final StatisticEvent statisticEvent) {
        }

        @Override
        public void putEvents(final List<StatisticEvent> statisticEvents) {
        }

        @Override
        public List<String> getValuesByTag(final String tagName) {
            return null;
        }

        @Override
        public List<String> getValuesByTagAndPartialValue(final String tagName, final String partialValue) {
            return null;
        }

        @Override
        public List<DataSourceField> getSupportedFields(final List<DataSourceField> indexFields) {
            return null;
        }

        @Override
        public void flushAllEvents() {
        }
    }

    @Override
    public Set<String> getAllEngineNames() {
        return null;
    }
}
