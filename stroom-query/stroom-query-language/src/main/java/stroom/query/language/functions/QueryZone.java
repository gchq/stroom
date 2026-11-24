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

package stroom.query.language.functions;

import java.time.ZoneId;
import java.util.function.Supplier;

/// The time zone of the query that an expression is in, found when it is first needed so that an expression
/// that doesn't need it works even if it is not a known time zone.
final class QueryZone implements Supplier<ZoneId> {

    private final ExpressionContext expressionContext;
    // Not volatile as it does no harm for two threads to both find it.
    private ZoneId zoneId;

    /// Creates the time zone.
    ///
    /// @param expressionContext The context of the expression, or `null` for UTC.
    QueryZone(final ExpressionContext expressionContext) {
        this.expressionContext = expressionContext;
    }

    @Override
    public ZoneId get() {
        ZoneId result = zoneId;
        if (result == null) {
            result = UserTimeZoneUtil.getZoneId(expressionContext);
            zoneId = result;
        }
        return result;
    }
}
