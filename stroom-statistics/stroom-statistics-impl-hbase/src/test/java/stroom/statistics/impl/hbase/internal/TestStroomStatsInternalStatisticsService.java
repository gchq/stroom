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

package stroom.statistics.impl.hbase.internal;


import stroom.docref.DocRef;
import stroom.kafka.api.KafkaProducerFactory;
import stroom.kafka.api.SharedKafkaProducer;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticKey;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// The stroom-stats xml schema jaxb model (maven uk.gov.gchq.stroom.stats:stroom-stats-schema)
// uses javax.xml.bind but we now require jakarta.xml.bind.  We could use the eclipse transformer
// to modify the jar but as stroom-stats is not currently in use it can wait.
@Disabled
@ExtendWith(MockitoExtension.class)
class TestStroomStatsInternalStatisticsService {

    private static final String DOC_REF_TYPE_1 = "myDocRefType1";
    private static final String DOC_REF_TYPE_2 = "myDocRefType2";
    private static final Logger LOGGER = LoggerFactory.getLogger(TestStroomStatsInternalStatisticsService.class);

    @Captor
    private ArgumentCaptor<Consumer<Throwable>> exceptionHandlerCaptor;
    @Mock
    private SharedKafkaProducer mockSharedKafkaProducer;
    @Mock
    private KafkaProducer<String, byte[]> mockKafkaProducer;
    @Mock
    private KafkaProducerFactory mockStroomKafkaProducerFactory;

    private void initMocks() {
        Mockito.when(mockSharedKafkaProducer.getKafkaProducer())
                .thenReturn(Optional.of(mockKafkaProducer));

        Mockito.when(mockStroomKafkaProducerFactory.getSharedProducer(Mockito.any()))
                .thenReturn(mockSharedKafkaProducer);
    }

    @Test
    void putEvents_multipleEvents() {
        final HBaseStatisticsConfig hBaseStatisticsConfig = new HBaseStatisticsConfig()
                .withDocRefType(DOC_REF_TYPE_1)
                .withKafkaTopicConfig(new KafkaTopicsConfig("MyTopic", "DUMMY"));

        initMocks();

        final StroomStatsInternalStatisticsService stroomStatsInternalStatisticsService =
                new StroomStatsInternalStatisticsService(
                        mockStroomKafkaProducerFactory,
                        hBaseStatisticsConfig);

        //assemble test data
        final InternalStatisticEvent event1 = InternalStatisticEvent.createPlusOneCountStat(
                InternalStatisticKey.MEMORY, 0, Collections.emptySortedMap());
        final InternalStatisticEvent event2 = InternalStatisticEvent.createPlusOneCountStat(
                InternalStatisticKey.MEMORY, 1, Collections.emptySortedMap());
        final InternalStatisticEvent event3 = InternalStatisticEvent.createPlusOneCountStat(
                InternalStatisticKey.MEMORY, 1, Collections.emptySortedMap());
        final DocRef docRefA = new DocRef(DOC_REF_TYPE_1, UUID.randomUUID().toString(), "myStat1");
        final DocRef docRefB = new DocRef(DOC_REF_TYPE_2, UUID.randomUUID().toString(), "myStat2");
        final Map<DocRef, List<InternalStatisticEvent>> map = Map.of(
                docRefA, Arrays.asList(event1, event2),
                docRefB, Collections.singletonList(event3));

        stroomStatsInternalStatisticsService.putEvents(map);

        //two different doc refs so two calls to producer
        Mockito.verify(mockKafkaProducer, Mockito.times(2))
                .send(Mockito.any(ProducerRecord.class));
    }

    @Test
    void putEvents_largeBatch() {
        final HBaseStatisticsConfig hBaseStatisticsConfig = new HBaseStatisticsConfig()
                .withDocRefType(DOC_REF_TYPE_1)
                .withKafkaTopicConfig(new KafkaTopicsConfig("MyTopic", "DUMMY"))
                .withEventsPerMessage(10);

        initMocks();

        final StroomStatsInternalStatisticsService stroomStatsInternalStatisticsService =
                new StroomStatsInternalStatisticsService(
                        mockStroomKafkaProducerFactory,
                        hBaseStatisticsConfig
                );

        //assemble test data
        final DocRef docRefA = new DocRef(DOC_REF_TYPE_1, UUID.randomUUID().toString(), "myStat1");
        final DocRef docRefB = new DocRef(DOC_REF_TYPE_2, UUID.randomUUID().toString(), "myStat2");
        final Map<DocRef, List<InternalStatisticEvent>> map = Map.of(
                docRefA, createNEvents(InternalStatisticKey.MEMORY, 100),
                docRefB, createNEvents(InternalStatisticKey.CPU, 15));

        stroomStatsInternalStatisticsService.putEvents(map);

        //two different doc refs and batch size of 10,
        //so kafka msg count is 10 for A and 2 for B, thus 12
        Mockito.verify(mockKafkaProducer, Mockito.times(12))
                .send(Mockito.any(ProducerRecord.class));
    }

    private List<InternalStatisticEvent> createNEvents(final InternalStatisticKey key, final int count) {

        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> InternalStatisticEvent.createPlusOneCountStat(key, i, Collections.emptySortedMap()))
                .collect(Collectors.toList());
    }


}
