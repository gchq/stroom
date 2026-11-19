/*
 * Copyright 2025 Crown Copyright
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

package stroom.query.api;

import stroom.query.api.ExpressionTerm.Condition;

public class StringExpressionUtil {

    /// Creates quick filter text that matches a value equal to the supplied string, e.g. `="StreamId"`.
    ///
    /// Uses [Condition#EQUALS] rather than [Condition#EQUALS_CASE_SENSITIVE] because every text
    /// `ConditionSet` declares `EQUALS`, whereas the SQL backed field lists do not support the case
    /// sensitive variant and the quick filter parser will reject it.
    ///
    /// @param string The value to match.
    /// @return The quick filter text.
    public static String equalTo(final String string) {
        return Condition.EQUALS.getOperator() + "\"" + string + "\"";
    }

}
