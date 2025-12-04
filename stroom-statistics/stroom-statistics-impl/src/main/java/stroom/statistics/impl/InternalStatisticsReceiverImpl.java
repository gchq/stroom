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

package stroom.statistics.impl;

import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticsReceiver;

import com.google.common.base.Preconditions;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
@SuppressWarnings("unused")
public class InternalStatisticsReceiverImpl implements InternalStatisticsReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalStatisticsReceiverImpl.class);

    private final Collection<Provider<InternalStatisticsService>> providers;
    private final Provider<InternalStatisticsConfig> internalStatisticsConfigProvider;

    private volatile InternalStatisticsReceiver internalStatisticsReceiver = new DoNothingInternalStatisticsReceiver();

    @Inject
    InternalStatisticsReceiverImpl(final Collection<Provider<InternalStatisticsService>> providers,
                                   final Provider<InternalStatisticsConfig> internalStatisticsConfigProvider) {
        this.providers = providers;
        this.internalStatisticsConfigProvider = internalStatisticsConfigProvider;
    }

    void initStatisticEventStore() {
        final Map<String, InternalStatisticsService> docRefTypeToServiceMap = new HashMap<>();
        providers.forEach(provider -> {
            final InternalStatisticsService internalStatisticsService = provider.get();
            LOGGER.debug("Registering internal statistics service for docRefType {}",
                    internalStatisticsService.getDocRefType());

            docRefTypeToServiceMap.put(
                    Preconditions.checkNotNull(internalStatisticsService.getDocRefType()),
                    internalStatisticsService);
        });

        internalStatisticsReceiver = new MultiServiceInternalStatisticsReceiver(
                docRefTypeToServiceMap,
                internalStatisticsConfigProvider);
    }

    @Override
    public void putEvent(final InternalStatisticEvent event) {
        internalStatisticsReceiver.putEvent(event);
    }

    @Override
    public void putEvents(final List<InternalStatisticEvent> events) {
        internalStatisticsReceiver.putEvents(events);
    }

}
