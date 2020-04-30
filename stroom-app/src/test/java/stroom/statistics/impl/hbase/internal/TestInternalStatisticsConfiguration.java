package stroom.statistics.impl.hbase.internal;

import stroom.docref.DocRef;
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.impl.InternalStatisticsConfig;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.test.AbstractCoreIntegrationTest;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
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
                StatisticStoreDoc.DOCUMENT_TYPE));

        List<String> enabledStoreTypes = internalStatisticsConfig.getEnabledStoreTypes();

        Arrays.stream(InternalStatisticKey.values()).forEach(key -> {
            LOGGER.info("Checking docRefs for key {}", key);
            List<DocRef> docRefs = internalStatisticsConfig.getEnabledDocRefs(key);

            assertThat(docRefs)
                    .isNotNull();

            assertThat(docRefs)
                    .hasSize(enabledStoreTypes.size());

            assertThat(docRefs.stream()
                    .map(DocRef::getType)
                    .collect(Collectors.toList())
            ).containsExactlyInAnyOrder(
                    enabledStoreTypes.toArray(new String[]{ }));
        });
    }

    @Test
    void testGetEnabledDocRefs_both() {
        assertThat(internalStatisticsConfig).isNotNull();

        internalStatisticsConfig.setEnabledStoreTypes(List.of(
                StatisticStoreDoc.DOCUMENT_TYPE,
                StroomStatsStoreDoc.DOCUMENT_TYPE));

        List<String> enabledStoreTypes = internalStatisticsConfig.getEnabledStoreTypes();

        Arrays.stream(InternalStatisticKey.values()).forEach(key -> {
            LOGGER.info("Checking docRefs for key {}", key);
            List<DocRef> docRefs = internalStatisticsConfig.getEnabledDocRefs(key);

            assertThat(docRefs)
                    .isNotNull();

            assertThat(docRefs)
                    .hasSize(enabledStoreTypes.size());

            assertThat(docRefs.stream()
                    .map(DocRef::getType)
                    .collect(Collectors.toList())
            ).containsExactlyInAnyOrder(
                    enabledStoreTypes.toArray(new String[]{ }));
        });
    }
}
