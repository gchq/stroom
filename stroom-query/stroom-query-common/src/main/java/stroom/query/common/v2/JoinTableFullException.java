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

package stroom.query.common.v2;

import stroom.util.shared.ModelStringUtil;

/// Thrown when the bounded side of a join has more rows or bytes than a [JoinTable] may hold.
public class JoinTableFullException extends RuntimeException {

    /// Creates the exception.
    ///
    /// @param name   The name of the joined source, e.g. its alias.
    /// @param limits The limits that were passed.
    public JoinTableFullException(final String name, final JoinLimits limits) {
        super("'" + name + "' has more than " + ModelStringUtil.formatCsv(limits.maxRows()) + " rows or "
              + ModelStringUtil.formatIECByteSizeString(limits.maxBytes()) + " to join, the most a join can hold. "
              + "Filter its rows in a where clause, or join a bracketed query that groups them.");
    }
}
