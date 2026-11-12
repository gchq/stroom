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

/// The most a [JoinTable] may hold.
///
/// @param maxRows  The most rows the table may hold.
/// @param maxBytes The most bytes the table's keys and rows may take up.
public record JoinLimits(long maxRows, long maxBytes) {

    /// The default limits: 1 million rows or 256 MiB.
    public static final JoinLimits DEFAULT = new JoinLimits(1_000_000, 256L * 1024 * 1024);

    /// Creates join table limits.
    ///
    /// @param maxRows  The most rows the table may hold, not negative.
    /// @param maxBytes The most bytes the table's keys and rows may take up, not negative.
    public JoinLimits {
        if (maxRows < 0 || maxBytes < 0) {
            throw new IllegalArgumentException("Join table limits can't be negative");
        }
    }
}
