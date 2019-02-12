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

package stroom.meta.statistics.impl;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import stroom.meta.statistics.api.MetaStatistics;
import stroom.statistics.internal.InternalStatisticKey;
import stroom.statistics.internal.InternalStatisticsReceiver;

import javax.inject.Provider;
import java.util.Arrays;
import java.util.Collections;

public class MetaStatisticsModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(MetaStatistics.class).to(MetaStatisticsImpl.class);
    }

    @Provides
    public MetaStatisticsImpl metaStatistics(final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider) {
        final MetaStatisticsImpl metaDataStatistic = new MetaStatisticsImpl(internalStatisticsReceiverProvider);
        metaDataStatistic.setTemplates(Arrays.asList(
                new MetaStatisticsTemplate(
                        InternalStatisticKey.METADATA_STREAMS_RECEIVED,
                        "receivedTime",
                        Collections.singletonList("Feed")),
                new MetaStatisticsTemplate(
                        InternalStatisticKey.METADATA_STREAM_SIZE,
                        "receivedTime",
                        "StreamSize",
                        Collections.singletonList("Feed"))));
        return metaDataStatistic;
    }
}