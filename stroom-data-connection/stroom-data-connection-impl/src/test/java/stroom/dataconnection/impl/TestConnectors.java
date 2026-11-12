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

package stroom.dataconnection.impl;

import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestConnectors {

    private final JdbcConnector jdbcConnector = connector(JdbcConnector.class, DataConnectionType.JDBC);
    private final AthenaConnector athenaConnector = connector(AthenaConnector.class, DataConnectionType.ATHENA);
    private final CsvConnector csvConnector = connector(CsvConnector.class, DataConnectionType.CSV);
    private final ExcelConnector excelConnector = connector(ExcelConnector.class, DataConnectionType.EXCEL);
    private final LdapConnector ldapConnector = connector(LdapConnector.class, DataConnectionType.LDAP);

    private static <T extends Connector> T connector(final Class<T> type, final DataConnectionType sourceType) {
        final T connector = Mockito.mock(type);
        Mockito.when(connector.getType()).thenReturn(sourceType);
        return connector;
    }

    @ParameterizedTest
    @EnumSource(DataConnectionType.class)
    void everyTypeHasItsConnector(final DataConnectionType type) {
        final Connectors connectors = new Connectors(jdbcConnector, athenaConnector, csvConnector, excelConnector,
                ldapConnector);

        assertThat(connectors.get(type).getType()).isEqualTo(type);
    }

    @Test
    void documentWithNoTypeIsJdbc() {
        // Documents saved before there were other types have none.
        final Connectors connectors = new Connectors(jdbcConnector, athenaConnector, csvConnector, excelConnector,
                ldapConnector);
        final DataConnectionDoc doc = DataConnectionDoc.builder()
                .uuid("uuid-1")
                .sourceType(null)
                .build();

        assertThat(connectors.get(doc)).isSameAs(jdbcConnector);
    }

    @Test
    void documentGetsTheConnectorForItsType() {
        final Connectors connectors = new Connectors(jdbcConnector, athenaConnector, csvConnector, excelConnector,
                ldapConnector);
        final DataConnectionDoc doc = DataConnectionDoc.builder()
                .uuid("uuid-1")
                .sourceType(DataConnectionType.ATHENA)
                .build();

        assertThat(connectors.get(doc)).isSameAs(athenaConnector);
    }

    @Test
    void typeWithoutAConnectorIsRefusedAtStartUp() {
        // Two connectors claiming JDBC leave Athena without one.
        final JdbcConnector secondJdbc = connector(JdbcConnector.class, DataConnectionType.JDBC);
        final AthenaConnector misnamed = connector(AthenaConnector.class, DataConnectionType.JDBC);

        assertThatThrownBy(() -> new Connectors(secondJdbc, misnamed, csvConnector, excelConnector, ldapConnector))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ATHENA");
    }
}
