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

import stroom.util.io.ByteSize;
import stroom.util.shared.ModelStringUtil;

import java.util.Optional;

/// The limit on the data a document may hold, such as CSV or an Excel workbook, which an administrator
/// sets with `stroom.dataConnection.maxStoredDataSize`.
final class StoredDataLimit {

    private StoredDataLimit() {
        // Static methods only.
    }

    /// Finds whether data is larger than the limit.
    ///
    /// @param dataName What the data is, e.g. `CSV` or `workbook`.
    /// @param size     The data's size in bytes.
    /// @param config   The administrator's controls, which set the limit.
    /// @return A message saying the data is too large, naming the property that sets the limit, or
    ///         empty if it is within the limit.
    static Optional<String> findProblem(final String dataName, final long size, final DataConnectionConfig config) {
        final ByteSize limit = config.getMaxStoredDataSize();
        if (size <= limit.getBytes()) {
            return Optional.empty();
        }
        return Optional.of("The " + dataName + " is " + ModelStringUtil.formatIECByteSizeString(size)
                           + ", more than the limit of " + limit + " set by the property "
                           + "stroom.dataConnection.maxStoredDataSize.");
    }
}
