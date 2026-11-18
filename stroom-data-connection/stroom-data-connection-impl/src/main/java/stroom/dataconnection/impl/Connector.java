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
import stroom.dataconnection.shared.DataConnectionTestResponse;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.Query;
import stroom.query.api.datasource.QueryField;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.List;

/// The code for one [DataConnectionType]: where its rows come from and how it is queried.
///
/// [DataConnectionSearchProvider] checks permissions, builds the result store and runs the search;
/// it asks the document's connector for everything that depends on the type. [Connectors] finds the
/// connector for a document.
interface Connector {

    /// @return The type of Data Connection this connector serves.
    DataConnectionType getType();

    /// Whether queries of this type carry their own native query, such as SQL, in a `sql` clause.
    ///
    /// The provider answers true for a document the user may not use, so it reveals nothing about the
    /// document. Once some types answer false, the answer reveals whether a document the user may use
    /// takes SQL, which querying it would show them anyway.
    ///
    /// @return True if every query must have a native query; false if none may.
    boolean takesNativeQuery();

    /// Lists the fields a query returns.
    ///
    /// @param doc         The Data Connection, which the user may use.
    /// @param nativeQuery The query's native query, such as its SQL. Never blank for a type that
    ///                    [#takesNativeQuery()]; the provider returns no fields without one. Ignored by
    ///                    other types.
    /// @return The fields, in column order.
    /// @throws RuntimeException If the document's settings are incomplete, or the native query cannot be
    ///                          described.
    List<QueryField> getFields(DataConnectionDoc doc, String nativeQuery);

    /// The shortest interval at which a dashboard may rerun a query of the document automatically.
    ///
    /// @param doc The Data Connection.
    /// @return The minimum in minutes, 0 for none.
    int getMinRefreshMinutes(DataConnectionDoc doc);

    /// Checks a query and prepares it to run, so a mistake in it is reported before the search starts.
    ///
    /// @param doc              The Data Connection, which the user may use.
    /// @param query            The query, with its expression parameters already replaced.
    /// @param dateTimeSettings The settings for reading dates in the query.
    /// @return The prepared query.
    /// @throws RuntimeException If the query cannot run, e.g. its native query is wrong or refused.
    PreparedQuery prepare(DataConnectionDoc doc, Query query, DateTimeSettings dateTimeSettings);

    /// Tests the document's connection, or for stored data that it can be read.
    ///
    /// @param doc The Data Connection, which need not have been saved. The caller has checked that
    ///            the user may edit it.
    /// @return Whether the test passed, with a description or the error.
    default DataConnectionTestResponse test(final DataConnectionDoc doc) {
        try {
            return testConnection(doc);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) {
                // The test was stopped, so whatever runs next on this thread must know too.
                Thread.currentThread().interrupt();
            }
            // Logged as the connector's own class, so DEBUG on e.g. LdapConnector shows its failures
            LambdaLoggerFactory.getLogger(getClass())
                    .debug(() -> LogUtil.message("{} test failed for {}", getType(), doc.getUuid()), e);
            return new DataConnectionTestResponse(false, describeFailure(e));
        }
    }

    /// Tests the document's connection, or for stored data that it can be read, as [#test] does, but
    /// throws if it fails.
    ///
    /// @param doc The Data Connection, which need not have been saved.
    /// @return Whether the test passed, with a description. A test can find a problem without
    ///         failing, such as an LDAP base DN that can't be read.
    /// @throws Exception If the test fails.
    DataConnectionTestResponse testConnection(DataConnectionDoc doc) throws Exception;

    /// @param e Why a test failed.
    /// @return The message to show the user.
    default String describeFailure(final Exception e) {
        return e.getMessage();
    }
}
