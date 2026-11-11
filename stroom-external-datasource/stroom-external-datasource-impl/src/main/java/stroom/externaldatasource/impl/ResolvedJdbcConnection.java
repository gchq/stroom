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

import java.util.Map;
import java.util.Objects;

/// The JDBC settings of an External Data Source with its credential resolved and the administrator's
/// ceilings applied: everything needed to open a connection.
///
/// It is the key of the connection pool cache, so a change to the URL, the properties, the pool size
/// or the secret leads to a new pool rather than reuse of one built with the old values.
///
/// @param url                  The JDBC URL, already checked against the allow list.
/// @param username             The user to connect as, or `null` for none.
/// @param password             The password to connect with, or `null` for none.
/// @param connectionProperties Extra driver properties, never `null`.
/// @param maxPoolSize          The most pooled connections, within the administrator's ceiling.
public record ResolvedJdbcConnection(String url,
                                     String username,
                                     String password,
                                     Map<String, String> connectionProperties,
                                     int maxPoolSize) {

    /// Creates a resolved connection, copying the properties so later changes cannot alter the key.
    public ResolvedJdbcConnection {
        Objects.requireNonNull(url);
        connectionProperties = connectionProperties == null
                ? Map.of()
                : Map.copyOf(connectionProperties);
    }

    @Override
    public String toString() {
        // The password and property values are left out, as this may be logged.
        return "ResolvedJdbcConnection{" +
               "url='" + url + '\'' +
               ", username='" + username + '\'' +
               ", connectionPropertyNames=" + connectionProperties.keySet() +
               ", maxPoolSize=" + maxPoolSize +
               '}';
    }
}
