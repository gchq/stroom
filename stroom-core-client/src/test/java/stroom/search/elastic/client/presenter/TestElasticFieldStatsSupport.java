/*
 * Copyright 2026 Crown Copyright
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

package stroom.search.elastic.client.presenter;

import stroom.search.elastic.shared.ElasticFieldStats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestElasticFieldStatsSupport {

    @Test
    void testSummaryOfRandomSample() {
        final ElasticFieldStats stats = new ElasticFieldStats("f", false, 5_000, 50_000, 1_234_567L, 4_987L, null,
                null, null, null, null, null, null, null, null);

        assertThat(ElasticFieldStatsSupport.getSummary(stats))
                .isEqualTo("Estimated from a random sample of about 4,987 of 1,234,567 documents in the time range.");
    }

    @Test
    void testSummaryOfExactStats() {
        final ElasticFieldStats stats = new ElasticFieldStats("f", true, 5_000, 50_000, 8_000L, 8_000L, null,
                null, null, null, null, null, null, null, null);

        assertThat(ElasticFieldStatsSupport.getSummary(stats))
                .isEqualTo("Exact, across all 8,000 documents in the time range.");
    }

    @Test
    void testAllowedSampleSizes() {
        assertThat(ElasticFieldStatsSupport.getAllowedSampleSizes(null))
                .isEqualTo(ElasticFieldStatsSupport.SAMPLE_SIZES);
        assertThat(ElasticFieldStatsSupport.getAllowedSampleSizes(10_000))
                .containsExactly(1_000, 5_000, 10_000);
        assertThat(ElasticFieldStatsSupport.getAllowedSampleSizes(7_000))
                .containsExactly(1_000, 5_000);
        // The smallest option is always offered, even if the server allows less
        assertThat(ElasticFieldStatsSupport.getAllowedSampleSizes(500))
                .containsExactly(1_000);
    }

    @ParameterizedTest
    @CsvSource({
            "5000, , 5000",
            "50000, 10000, 10000",
            "10000, 7000, 5000",
            "5000, 500, 1000",
            "7000, 100000, 5000",
            "100, 100000, 1000"
    })
    void testClampSampleSize(final int sampleSize, final Integer maxSampleSize, final int expected) {
        assertThat(ElasticFieldStatsSupport.clampSampleSize(sampleSize, maxSampleSize)).isEqualTo(expected);
    }

    @Test
    void testDefaultFromIsThirtyDaysBefore() {
        final long nowMs = 1_000_000_000_000L;
        assertThat(nowMs - ElasticFieldStatsSupport.getDefaultFromMs(nowMs))
                .isEqualTo(30L * 24 * 60 * 60 * 1000);
    }

    @Test
    void testPercent() {
        assertThat(ElasticFieldStatsSupport.getPercent(1, 4)).isEqualTo(25.0);
        assertThat(ElasticFieldStatsSupport.getPercent(1, 0)).isZero();
        assertThat(ElasticFieldStatsSupport.getPercent(1, -1)).isZero();
        assertThat(ElasticFieldStatsSupport.formatPercent(1, 3)).isEqualTo("33.3%");
        assertThat(ElasticFieldStatsSupport.formatPercent(5, 5)).isEqualTo("100%");
        assertThat(ElasticFieldStatsSupport.formatPercent(0, 0)).isEqualTo("0%");
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "999, 999",
            "1000, '1,000'",
            "123456, '123,456'",
            "1234567, '1,234,567'",
            "-1234, '-1,234'"
    })
    void testFormatCount(final long count, final String expected) {
        assertThat(ElasticFieldStatsSupport.formatCount(count)).isEqualTo(expected);
    }

    @Test
    void testFormatNullCount() {
        assertThat(ElasticFieldStatsSupport.formatCount((Long) null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "1.5, 3, 1.5",
            "2.0, 3, 2",
            "0.8000000119, 3, 0.8",
            "1234.5678, 2, '1,234.57'",
            "0.0005, 3, 0.001",
            "0.05, 3, 0.05",
            "-2.25, 1, -2.3",
            "-0.0001, 2, 0",
            "10.96, 1, 11"
    })
    void testFormatDecimal(final double value, final int decimalPlaces, final String expected) {
        assertThat(ElasticFieldStatsSupport.formatDecimal(value, decimalPlaces)).isEqualTo(expected);
    }

    @Test
    void testFormatNullDecimal() {
        assertThat(ElasticFieldStatsSupport.formatDecimal(null)).isEmpty();
        assertThat(ElasticFieldStatsSupport.formatDecimal(Double.NaN, 2)).isEqualTo("NaN");
        assertThat(List.of(ElasticFieldStatsSupport.formatDecimal(3.14159))).containsExactly("3.142");
    }
}
