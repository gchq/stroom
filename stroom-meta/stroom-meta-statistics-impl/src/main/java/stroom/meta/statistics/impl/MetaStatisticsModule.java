/*
 * Copyright 2018-2024 Crown Copyright
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
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.util.shared.string.CIKeys;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Provider;

import java.util.Collections;
import java.util.List;

public class MetaStatisticsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(MetaStatistics.class).to(MetaStatisticsImpl.class);
    }

    @Provides
    public MetaStatisticsImpl metaStatistics(
            final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider,
            final SecurityContext securityContext) {

        final MetaStatisticsImpl metaDataStatistic = new MetaStatisticsImpl(
                internalStatisticsReceiverProvider,
                securityContext);
        metaDataStatistic.setTemplates(List.of(
                new MetaStatisticsTemplate(
                        InternalStatisticKey.METADATA_STREAMS_RECEIVED,
                        CIKeys.RECEIVED_TIME,
                        Collections.singletonList(CIKeys.FEED)),
                new MetaStatisticsTemplate(
                        InternalStatisticKey.METADATA_STREAM_SIZE,
                        CIKeys.RECEIVED_TIME,
                        CIKeys.STREAM_SIZE,
                        Collections.singletonList(CIKeys.FEED))));
        return metaDataStatistic;
    }
}
