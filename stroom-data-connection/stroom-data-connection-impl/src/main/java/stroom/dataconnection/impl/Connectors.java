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

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/// Finds the [Connector] for a Data Connection's type.
@Singleton
class Connectors {

    private final Map<DataConnectionType, Connector> connectors = new EnumMap<>(DataConnectionType.class);

    /// Creates the instance. Guice supplies every argument.
    @Inject
    Connectors(final JdbcConnector jdbcConnector,
               final AthenaConnector athenaConnector,
               final CsvConnector csvConnector,
               final ExcelConnector excelConnector,
               final LdapConnector ldapConnector) {
        add(jdbcConnector);
        add(athenaConnector);
        add(csvConnector);
        add(excelConnector);
        add(ldapConnector);
        for (final DataConnectionType type : DataConnectionType.values()) {
            if (!connectors.containsKey(type)) {
                throw new IllegalStateException("No connector for Data Connection type " + type);
            }
        }
    }

    private void add(final Connector connector) {
        connectors.put(connector.getType(), connector);
    }

    /// @param doc A Data Connection.
    /// @return The connector for its type. A document with no type is JDBC, as documents were before
    ///         there were other types.
    Connector get(final DataConnectionDoc doc) {
        return get(doc.getSourceType());
    }

    /// @param type A type of Data Connection, or `null` for JDBC.
    /// @return The connector for the type.
    Connector get(final DataConnectionType type) {
        return connectors.get(Objects.requireNonNullElse(type, DataConnectionType.JDBC));
    }
}
