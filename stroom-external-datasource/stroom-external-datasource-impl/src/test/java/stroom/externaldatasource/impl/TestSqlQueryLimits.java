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

package stroom.externaldatasource.impl;

import stroom.externaldatasource.shared.ExternalDataSourceDoc;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestSqlQueryLimits {

    private static ExternalDataSourceConfig config(final StroomDuration maxQueryTimeout, final int maxRowsLimit) {
        return new ExternalDataSourceConfig(null, null, null, maxQueryTimeout, maxRowsLimit, null, null, null, null,
                null, null, null, null);
    }

    @Test
    void documentLimitsWithinTheCeilingsAreKept() {
        final ExternalDataSourceDoc doc = ExternalDataSourceDoc.builder()
                .uuid("uuid-1")
                .queryTimeoutSeconds(30)
                .maxRows(500)
                .build();

        assertThat(SqlQueryLimits.of(doc, new ExternalDataSourceConfig())).isEqualTo(new SqlQueryLimits(30, 500));
    }

    @Test
    void documentLimitsAreReducedToTheCeilings() {
        final ExternalDataSourceDoc doc = ExternalDataSourceDoc.builder()
                .uuid("uuid-1")
                .queryTimeoutSeconds(3_600)
                .maxRows(5_000_000)
                .build();

        assertThat(SqlQueryLimits.of(doc, config(StroomDuration.ofMinutes(2), 1_000)))
                .isEqualTo(new SqlQueryLimits(120, 1_000));
    }

    @Test
    void missingLimitsTakeTheDefaults() {
        final ExternalDataSourceDoc doc = ExternalDataSourceDoc.builder()
                .uuid("uuid-1")
                .queryTimeoutSeconds(null)
                .maxRows(null)
                .build();

        assertThat(SqlQueryLimits.of(doc, new ExternalDataSourceConfig())).isEqualTo(new SqlQueryLimits(
                ExternalDataSourceDoc.DEFAULT_QUERY_TIMEOUT_SECONDS,
                ExternalDataSourceDoc.DEFAULT_MAX_ROWS));
    }

    @Test
    void limitsAreAtLeastOne() {
        // A timeout of 0 means none to JDBC, and a row limit of 0 means no limit.
        final ExternalDataSourceDoc doc = ExternalDataSourceDoc.builder()
                .uuid("uuid-1")
                .queryTimeoutSeconds(0)
                .maxRows(-5)
                .build();

        assertThat(SqlQueryLimits.of(doc, config(StroomDuration.ZERO, 0))).isEqualTo(new SqlQueryLimits(1, 1));
    }
}
