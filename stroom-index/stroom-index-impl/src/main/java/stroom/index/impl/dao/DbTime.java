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

package stroom.index.impl.dao;

import org.jooq.Field;
import org.jooq.impl.DSL;

/// The database's clock, so that times recorded by different nodes do not depend on their clocks agreeing.
/// Taken from UTC so that it does not depend on the session's time zone and never jumps when the clocks
/// change.
final class DbTime {

    /// The current time in the database, in milliseconds since the epoch.
    static final Field<Long> NOW_MS = DSL.field(
            "CAST(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', UTC_TIMESTAMP(3)) DIV 1000 AS SIGNED)",
            Long.class);

    private DbTime() {
        // Constants only.
    }
}
