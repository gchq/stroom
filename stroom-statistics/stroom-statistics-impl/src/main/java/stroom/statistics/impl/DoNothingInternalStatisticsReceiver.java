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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Provides protection in case client code calls create when the proper facade has not been initialised, allowing
 * the system to function albeit with the loss of the stats.
 */
class DoNothingInternalStatisticsReceiver implements InternalStatisticsReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(DoNothingInternalStatisticsReceiver.class);

    private long lastLog = 0;

    @Override
    public void putEvent(final InternalStatisticEvent event) {
        logWarn("putEvent");
    }

    @Override
    public void putEvents(final List<InternalStatisticEvent> events) {
        logWarn("putEvents");
    }

    private void logWarn(final String method) {
        final long now = System.currentTimeMillis();
        if (lastLog < now - 600000) {
            lastLog = now;
            LOGGER.warn(method + " called when internalStatisticsReceiver has not been initialised. " +
                    "The statistics will not be recorded");
        }
    }
}
