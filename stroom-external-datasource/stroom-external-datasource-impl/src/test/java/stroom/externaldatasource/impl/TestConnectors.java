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
import stroom.externaldatasource.shared.ExternalDataSourceType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestConnectors {

    private final JdbcConnector jdbcConnector = connector(JdbcConnector.class, ExternalDataSourceType.JDBC);
    private final AthenaConnector athenaConnector = connector(AthenaConnector.class, ExternalDataSourceType.ATHENA);
    private final CsvConnector csvConnector = connector(CsvConnector.class, ExternalDataSourceType.CSV);
    private final ExcelConnector excelConnector = connector(ExcelConnector.class, ExternalDataSourceType.EXCEL);

    private static <T extends Connector> T connector(final Class<T> type, final ExternalDataSourceType sourceType) {
        final T connector = Mockito.mock(type);
        Mockito.when(connector.getType()).thenReturn(sourceType);
        return connector;
    }

    @ParameterizedTest
    @EnumSource(ExternalDataSourceType.class)
    void everyTypeHasItsConnector(final ExternalDataSourceType type) {
        final Connectors connectors = new Connectors(jdbcConnector, athenaConnector, csvConnector, excelConnector);

        assertThat(connectors.get(type).getType()).isEqualTo(type);
    }

    @Test
    void documentWithNoTypeIsJdbc() {
        // Documents saved before there were other types have none.
        final Connectors connectors = new Connectors(jdbcConnector, athenaConnector, csvConnector, excelConnector);
        final ExternalDataSourceDoc doc = ExternalDataSourceDoc.builder()
                .uuid("uuid-1")
                .sourceType(null)
                .build();

        assertThat(connectors.get(doc)).isSameAs(jdbcConnector);
    }

    @Test
    void documentGetsTheConnectorForItsType() {
        final Connectors connectors = new Connectors(jdbcConnector, athenaConnector, csvConnector, excelConnector);
        final ExternalDataSourceDoc doc = ExternalDataSourceDoc.builder()
                .uuid("uuid-1")
                .sourceType(ExternalDataSourceType.ATHENA)
                .build();

        assertThat(connectors.get(doc)).isSameAs(athenaConnector);
    }

    @Test
    void typeWithoutAConnectorIsRefusedAtStartUp() {
        // Two connectors claiming JDBC leave Athena without one.
        final JdbcConnector secondJdbc = connector(JdbcConnector.class, ExternalDataSourceType.JDBC);
        final AthenaConnector misnamed = connector(AthenaConnector.class, ExternalDataSourceType.JDBC);

        assertThatThrownBy(() -> new Connectors(secondJdbc, misnamed, csvConnector, excelConnector))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ATHENA");
    }
}
