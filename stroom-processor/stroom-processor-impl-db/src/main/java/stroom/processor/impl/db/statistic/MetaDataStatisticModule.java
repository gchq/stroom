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

package stroom.processor.impl.db.statistic;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import stroom.processor.statistic.MetaDataStatistic;
import stroom.statistics.internal.InternalStatisticKey;
import stroom.statistics.internal.InternalStatisticsReceiver;

import javax.inject.Provider;
import java.util.Arrays;
import java.util.Collections;

public class MetaDataStatisticModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(MetaDataStatistic.class).to(MetaDataStatisticImpl.class);
    }

    @Provides
    public MetaDataStatisticImpl metaDataStatistic(final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider) {
        final MetaDataStatisticImpl metaDataStatistic = new MetaDataStatisticImpl(internalStatisticsReceiverProvider);
        metaDataStatistic.setTemplates(Arrays.asList(
                new MetaDataStatisticTemplate(
                        InternalStatisticKey.METADATA_STREAMS_RECEIVED,
                        "receivedTime",
                        Collections.singletonList("Feed")),
                new MetaDataStatisticTemplate(
                        InternalStatisticKey.METADATA_STREAM_SIZE,
                        "receivedTime",
                        "StreamSize",
                        Collections.singletonList("Feed"))));
        return metaDataStatistic;
    }
}