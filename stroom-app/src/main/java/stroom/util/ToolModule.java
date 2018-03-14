/*
 * Copyright 2018 Crown Copyright
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

package stroom.util;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import stroom.internalstatistics.MetaDataStatistic;
import stroom.statistics.internal.InternalStatisticEvent;
import stroom.statistics.internal.InternalStatisticsReceiver;

import java.util.List;

public class ToolModule extends AbstractModule {
    @Provides
    public InternalStatisticsReceiver internalStatisticsReceiver() {
        return new InternalStatisticsReceiver() {
            @Override
            public void putEvent(final InternalStatisticEvent event) {
            }

            @Override
            public void putEvents(final List<InternalStatisticEvent> events) {
            }
        };
    }

    @Provides
    public MetaDataStatistic metaDataStatistic() {
        return metaData -> {
        };
    }
}