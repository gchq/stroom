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

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/// Finds the [Connector] for an External Data Source's type.
@Singleton
class Connectors {

    private final Map<ExternalDataSourceType, Connector> connectors = new EnumMap<>(ExternalDataSourceType.class);

    /// Creates the instance. Guice supplies every argument.
    @Inject
    Connectors(final JdbcConnector jdbcConnector,
               final AthenaConnector athenaConnector,
               final CsvConnector csvConnector,
               final ExcelConnector excelConnector) {
        add(jdbcConnector);
        add(athenaConnector);
        add(csvConnector);
        add(excelConnector);
        for (final ExternalDataSourceType type : ExternalDataSourceType.values()) {
            if (!connectors.containsKey(type)) {
                throw new IllegalStateException("No connector for External Data Source type " + type);
            }
        }
    }

    private void add(final Connector connector) {
        connectors.put(connector.getType(), connector);
    }

    /// @param doc An External Data Source.
    /// @return The connector for its type. A document with no type is JDBC, as documents were before
    ///         there were other types.
    Connector get(final ExternalDataSourceDoc doc) {
        return get(doc.getSourceType());
    }

    /// @param type A type of External Data Source, or `null` for JDBC.
    /// @return The connector for the type.
    Connector get(final ExternalDataSourceType type) {
        return connectors.get(Objects.requireNonNullElse(type, ExternalDataSourceType.JDBC));
    }
}
