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
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.impl.InternalStatisticsConfig;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.test.AbstractCoreIntegrationTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestInternalStatisticsConfiguration extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestInternalStatisticsConfiguration.class);

    @Inject
    private InternalStatisticsConfig internalStatisticsConfig;

    /**
     * This test is here to make sure that an {@link InternalStatisticKey}
     * has been created for each of the internal statistic types in
     * {@link InternalStatisticsConfig}.  It expects each key to correspond to
     * two docrefs, one for stroom stats and one for sql stats, as defined
     * in dev.yml
     */
    @Test
    void testGetEnabledDocRefs_one() {
        assertThat(internalStatisticsConfig).isNotNull();

        internalStatisticsConfig.setEnabledStoreTypes(List.of(
                StatisticStoreDoc.TYPE));

        final List<String> enabledStoreTypes = internalStatisticsConfig.getEnabledStoreTypes();

        Arrays.stream(InternalStatisticKey.values()).forEach(key -> {
            LOGGER.info("Checking docRefs for key {}", key);
            final List<DocRef> docRefs = internalStatisticsConfig.getEnabledDocRefs(key);

            assertThat(docRefs)
                    .isNotNull();

            assertThat(docRefs)
                    .hasSize(enabledStoreTypes.size());

            assertThat(docRefs.stream()
                    .map(DocRef::getType)
                    .collect(Collectors.toList())
            ).containsExactlyInAnyOrder(
                    enabledStoreTypes.toArray(new String[]{}));
        });
    }

    @Test
    void testGetEnabledDocRefs_both() {
        assertThat(internalStatisticsConfig).isNotNull();

        internalStatisticsConfig.setEnabledStoreTypes(List.of(
                StatisticStoreDoc.TYPE,
                StroomStatsStoreDoc.TYPE));

        final List<String> enabledStoreTypes = internalStatisticsConfig.getEnabledStoreTypes();

        Arrays.stream(InternalStatisticKey.values()).forEach(key -> {
            LOGGER.info("Checking docRefs for key {}", key);
            final List<DocRef> docRefs = internalStatisticsConfig.getEnabledDocRefs(key);

            assertThat(docRefs)
                    .isNotNull();

            assertThat(docRefs)
                    .hasSize(enabledStoreTypes.size());

            assertThat(docRefs.stream()
                    .map(DocRef::getType)
                    .collect(Collectors.toList())
            ).containsExactlyInAnyOrder(
                    enabledStoreTypes.toArray(new String[]{}));
        });
    }
}
