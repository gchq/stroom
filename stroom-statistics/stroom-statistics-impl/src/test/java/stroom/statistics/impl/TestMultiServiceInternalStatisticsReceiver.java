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

import stroom.docref.DocRef;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticKey;

import com.google.common.collect.ImmutableMap;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestMultiServiceInternalStatisticsReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestMultiServiceInternalStatisticsReceiver.class);

    private static final InternalStatisticKey STAT_KEY_A = InternalStatisticKey.CPU;
    private static final InternalStatisticKey STAT_KEY_B = InternalStatisticKey.MEMORY;
    private static final String DOC_REF_TYPE_1 = "DocRefType1";
    private static final String DOC_REF_TYPE_2 = "DocRefType2";
    private static final String STAT_NAME_A1 = "MyStatA1";
    private static final String STAT_NAME_A2 = "MyStatA2";
    private static final String STAT_NAME_B1 = "MyStatB1";
    private static final DocRef DOC_REF_A1 = new DocRef(DOC_REF_TYPE_1, UUID.randomUUID().toString(), STAT_NAME_A1);
    private static final DocRef DOC_REF_A2 = new DocRef(DOC_REF_TYPE_2, UUID.randomUUID().toString(), STAT_NAME_A2);
    private static final DocRef DOC_REF_B1 = new DocRef(DOC_REF_TYPE_1, UUID.randomUUID().toString(), STAT_NAME_B1);
    private static final InternalStatisticEvent EVENT_A601 = createEvent(STAT_KEY_A, 601L);
    private static final InternalStatisticEvent EVENT_A602 = createEvent(STAT_KEY_A, 602L);
    private static final InternalStatisticEvent EVENT_A603 = createEvent(STAT_KEY_A, 603L);
    private static final InternalStatisticEvent EVENT_B701 = createEvent(STAT_KEY_B, 701L);
    private static final InternalStatisticEvent EVENT_B702 = createEvent(STAT_KEY_B, 702L);
    private static final InternalStatisticEvent EVENT_B703 = createEvent(STAT_KEY_B, 703L);

    @Mock
    private InternalStatisticsConfig internalStatisticsConfig;
    @Mock
    private InternalStatisticsService internalStatisticsService1;
    @Mock
    private InternalStatisticsService internalStatisticsService2;
    @Captor
    private ArgumentCaptor<Map<DocRef, List<InternalStatisticEvent>>> argCaptor1;
    @Captor
    private ArgumentCaptor<Map<DocRef, List<InternalStatisticEvent>>> argCaptor2;

    private static InternalStatisticEvent createEvent(final InternalStatisticKey key, final Long timeMs) {
        return InternalStatisticEvent.createPlusOneCountStat(key, timeMs, null);
    }

    @Test
    void putEvents() {

        //keyA has a docRef for both services
        Mockito.when(internalStatisticsConfig.getEnabledDocRefs(Mockito.eq(STAT_KEY_A)))
                .thenReturn(Arrays.asList(DOC_REF_A1, DOC_REF_A2));
        assertThat(internalStatisticsConfig.getEnabledDocRefs(STAT_KEY_A))
                .containsExactly(DOC_REF_A1, DOC_REF_A2);

        //keyB only has a docref for service1
        Mockito.when(internalStatisticsConfig.getEnabledDocRefs(Mockito.eq(STAT_KEY_B)))
                .thenReturn(Arrays.asList(DOC_REF_B1));
        assertThat(internalStatisticsConfig.getEnabledDocRefs(STAT_KEY_B))
                .containsExactly(DOC_REF_B1);

        //service1 supports docRefType1, etc.
        final Map<String, InternalStatisticsService> docRefTypeToServiceMap = ImmutableMap.of(
                DOC_REF_TYPE_1, internalStatisticsService1, //i.e. docRefA1 and B1
                DOC_REF_TYPE_2, internalStatisticsService2); //i.e. docRefA2

        final MultiServiceInternalStatisticsReceiver facade = new MultiServiceInternalStatisticsReceiver(
                docRefTypeToServiceMap,
                () -> internalStatisticsConfig);

        //fire 6 events at the facade, 3 for each key
        facade.putEvents(Arrays.asList(
                EVENT_A601,
                EVENT_A602,
                EVENT_A603,
                EVENT_B701,
                EVENT_B702,
                EVENT_B703));

        Mockito.verify(internalStatisticsService1, Mockito.times(1))
                .putEvents(argCaptor1.capture());
        assertThat(argCaptor1.getValue().keySet())
                .containsExactlyInAnyOrder(DOC_REF_A1, DOC_REF_B1);
        assertThat(argCaptor1.getValue().get(DOC_REF_A1))
                .containsExactly(EVENT_A601, EVENT_A602, EVENT_A603);
        assertThat(argCaptor1.getValue().get(DOC_REF_B1))
                .containsExactly(EVENT_B701, EVENT_B702, EVENT_B703);

        Mockito.verify(internalStatisticsService2, Mockito.times(1))
                .putEvents(argCaptor2.capture());
        assertThat(argCaptor2.getValue().keySet())
                .containsExactly(DOC_REF_A2);
        assertThat(argCaptor2.getValue().get(DOC_REF_A2))
                .containsExactly(EVENT_A601, EVENT_A602, EVENT_A603);
    }

}
