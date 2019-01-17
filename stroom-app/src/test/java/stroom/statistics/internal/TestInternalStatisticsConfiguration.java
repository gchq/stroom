package stroom.statistics.internal;


import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.statistics.shared.StatisticStoreDoc;
import stroom.stats.shared.StroomStatsStoreDoc;
import stroom.test.AbstractCoreIntegrationTest;

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
    void testGet() {
        assertThat(internalStatisticsConfig).isNotNull();

        Arrays.stream(InternalStatisticKey.values()).forEach(key -> {
            LOGGER.info("Checking docRefs for key {}", key);
            List<DocRef> docRefs = internalStatisticsConfig.get(key);

            assertThat(docRefs).isNotNull();
            assertThat(docRefs).hasSize(2);
            assertThat(docRefs.stream()
                    .map(DocRef::getType)
                    .collect(Collectors.toList())
            ).containsExactlyInAnyOrder(
                    StroomStatsStoreDoc.DOCUMENT_TYPE,
                    StatisticStoreDoc.DOCUMENT_TYPE);
        });
    }
}
