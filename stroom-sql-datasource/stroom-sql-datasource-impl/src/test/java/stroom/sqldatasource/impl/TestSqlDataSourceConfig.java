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

package stroom.sqldatasource.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSqlDataSourceConfig {

    @Test
    void noJdbcUrlIsAllowedByDefault() {
        // An administrator must choose which databases can be reached.
        assertThat(new SqlDataSourceConfig().getAllowedUrlPatterns()).isEmpty();
    }

    @Test
    void defaultsDenyKnownDangerousDriverProperties() {
        assertThat(new SqlDataSourceConfig().getDeniedConnectionProperties())
                .contains("allowLoadLocalInfile", "autoDeserialize", "socketFactory", "sslfactory", "loggerFile");
    }

    @Test
    void defaultConstructorSetsLimits() {
        final SqlDataSourceConfig config = new SqlDataSourceConfig();

        assertThat(config.getDriverDirectory()).isNull();
        assertThat(config.getMaxQueryTimeout()).isEqualTo(StroomDuration.ofMinutes(10));
        assertThat(config.getMaxRowsLimit()).isEqualTo(1_000_000);
        assertThat(config.getMaxPoolSizeLimit()).isEqualTo(20);
        assertThat(config.getConnectionPoolCache().getMaximumSize()).isEqualTo(100L);
    }

    @Test
    void jsonCreatorWithNullsMatchesDefaults() {
        // A config file that leaves out the sqlDataSource branch, or any of its properties.
        final SqlDataSourceConfig fromNulls = new SqlDataSourceConfig(
                null, null, null, null, null, null, null, null, null, null, null);
        final SqlDataSourceConfig defaults = new SqlDataSourceConfig();

        assertThat(fromNulls.getAllowedUrlPatterns()).isEqualTo(defaults.getAllowedUrlPatterns());
        assertThat(fromNulls.getDeniedConnectionProperties()).isEqualTo(defaults.getDeniedConnectionProperties());
        assertThat(fromNulls.getDriverDirectory()).isEqualTo(defaults.getDriverDirectory());
        assertThat(fromNulls.getMaxQueryTimeout()).isEqualTo(defaults.getMaxQueryTimeout());
        assertThat(fromNulls.getMaxRowsLimit()).isEqualTo(defaults.getMaxRowsLimit());
        assertThat(fromNulls.getMaxPoolSizeLimit()).isEqualTo(defaults.getMaxPoolSizeLimit());
        assertThat(fromNulls.getConnectionPoolCache()).isEqualTo(defaults.getConnectionPoolCache());
        assertThat(fromNulls.getFieldCache()).isEqualTo(defaults.getFieldCache());
        assertThat(fromNulls.getAllowedAthenaWorkGroups()).isEqualTo(defaults.getAllowedAthenaWorkGroups());
    }

    @Test
    void noAthenaWorkGroupIsAllowedByDefault() {
        // Otherwise any document editor could run Athena queries with Stroom's own AWS identity.
        assertThat(new SqlDataSourceConfig().getAllowedAthenaWorkGroups()).isEmpty();
    }

    @Test
    void jsonCreatorKeepsSuppliedValues() {
        final CacheConfig cache = CacheConfig.builder()
                .maximumSize(5L)
                .build();
        final CacheConfig fieldCache = CacheConfig.builder()
                .maximumSize(7L)
                .build();

        final SqlDataSourceConfig config = new SqlDataSourceConfig(
                List.of("^jdbc:mysql://db:3306/.*$"),
                List.of("onlyThis"),
                "/stroom/jdbc-drivers",
                StroomDuration.ofMinutes(2),
                50,
                4,
                cache,
                fieldCache, null, null, null);

        assertThat(config.getAllowedUrlPatterns()).containsExactly("^jdbc:mysql://db:3306/.*$");
        assertThat(config.getDeniedConnectionProperties())
                .as("an administrator may replace the deny list")
                .containsExactly("onlyThis");
        assertThat(config.getDriverDirectory()).isEqualTo("/stroom/jdbc-drivers");
        assertThat(config.getMaxQueryTimeout()).isEqualTo(StroomDuration.ofMinutes(2));
        assertThat(config.getMaxRowsLimit()).isEqualTo(50);
        assertThat(config.getMaxPoolSizeLimit()).isEqualTo(4);
        assertThat(config.getConnectionPoolCache()).isEqualTo(cache);
        assertThat(config.getFieldCache()).isEqualTo(fieldCache);
    }

    @Test
    void anEmptyDenyListIsKept() {
        // Unlike a missing list, an empty one is a deliberate choice and is not replaced.
        final SqlDataSourceConfig config = new SqlDataSourceConfig(
                null, List.of(), null, null, null, null, null, null, null, null, null);

        assertThat(config.getDeniedConnectionProperties()).isEmpty();
    }
}
