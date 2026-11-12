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

import software.amazon.awssdk.services.athena.AthenaClient;

/// Makes Amazon Athena clients.
///
/// A client is made for each query or connection test and closed after it. Athena queries take
/// seconds, so making a client costs little, and a client is then never shared between users or
/// closed while in use.
@FunctionalInterface
public interface AthenaClientFactory {

    /// Makes a client for a connection.
    ///
    /// @param connection The resolved connection, giving the region and credentials.
    /// @return A client the caller must close.
    AthenaClient create(ResolvedAthenaConnection connection);
}
