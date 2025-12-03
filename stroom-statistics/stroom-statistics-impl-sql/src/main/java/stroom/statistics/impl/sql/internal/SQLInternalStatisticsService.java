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

package stroom.statistics.impl.sql.internal;

import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.impl.InternalStatisticsService;
import stroom.statistics.impl.sql.SQLStatisticsConfig;
import stroom.statistics.impl.sql.StatisticEvent;
import stroom.statistics.impl.sql.StatisticTag;
import stroom.statistics.impl.sql.Statistics;

import com.google.common.base.Preconditions;
import io.vavr.Tuple2;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
class SQLInternalStatisticsService implements InternalStatisticsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SQLInternalStatisticsService.class);

    private final SecurityContext securityContext;
    private final Statistics statisticsService;
    private final String docRefType;

    @Inject
    SQLInternalStatisticsService(final SecurityContext securityContext,
                                 final SQLStatisticsConfig config,
                                 final Statistics statisticsService) {
        this.securityContext = securityContext;
        this.statisticsService = statisticsService;
        this.docRefType = config.getDocRefType();
    }

    @Override
    public void putEvents(final Map<DocRef, List<InternalStatisticEvent>> eventsMap) {
        // This may be called by pipe processing which runs as a lowly user so record
        // the stat as the proc user.
        securityContext.asProcessingUser(() -> {
            final List<StatisticEvent> statisticEvents = Preconditions.checkNotNull(eventsMap).entrySet().stream()
                    .flatMap(entry ->
                            entry.getValue().stream()
                                    .map(event -> new Tuple2<>(entry.getKey(), event)))
                    .map(tuple2 -> internalEventMapper(tuple2._1(), tuple2._2()))
                    .collect(Collectors.toList());
            statisticsService.putEvents(statisticEvents);
        });
    }

    private StatisticEvent internalEventMapper(final DocRef docRef,
                                               final InternalStatisticEvent internalStatisticEvent) {

        Preconditions.checkNotNull(internalStatisticEvent);
        switch (internalStatisticEvent.getType()) {
            case COUNT:
                return mapCountEvent(docRef, internalStatisticEvent);
            case VALUE:
                return mapValueEvent(docRef, internalStatisticEvent);
            default:
                throw new IllegalArgumentException("Unknown type: " + internalStatisticEvent.getType());
        }
    }

    private StatisticEvent mapCountEvent(final DocRef docRef,
                                         final InternalStatisticEvent internalStatisticEvent) {
        return StatisticEvent.createCount(
                internalStatisticEvent.getTimeMs(),
                docRef.getName(),
                internalStatisticEvent.getTags().entrySet().stream()
                        .map(entry -> new StatisticTag(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList()),
                internalStatisticEvent.getValueAsLong());
    }

    private StatisticEvent mapValueEvent(final DocRef docRef,
                                         final InternalStatisticEvent internalStatisticEvent) {
        return StatisticEvent.createValue(
                internalStatisticEvent.getTimeMs(),
                docRef.getName(),
                internalStatisticEvent.getTags().entrySet().stream()
                        .map(entry -> new StatisticTag(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList()),
                internalStatisticEvent.getValueAsDouble());
    }

    @Override
    public String getDocRefType() {
        return docRefType;
    }
}
