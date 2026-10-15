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

package stroom.db.util;

import stroom.query.api.ExpressionTerm;
import stroom.query.api.datasource.QueryField;

/**
 * A term asked for a condition its field does not declare.
 * <p>
 * {@link CommonExpressionMapper} used to only debug-log this, so the term was handed to
 * {@code TermHandler} anyway and failed further down in whichever way that condition happened to
 * fail - a throw from the default case, or, for a field whose value is converted before it reaches
 * SQL, a silent match against nothing. Both reached the user as an empty result indistinguishable
 * from "no matches".
 * <p>
 * Quick filter input is checked earlier and more precisely by
 * {@code SimpleStringExpressionParser}, which can report the offending token's position. This is
 * the backstop for expression trees built elsewhere - the dashboard term editor, saved rules,
 * processor filters - where there is no source text to point at.
 */
public class UnsupportedConditionException extends RuntimeException {

    public UnsupportedConditionException(final QueryField field, final ExpressionTerm term) {
        super("Field '" +
              field.getFldName() +
              "' does not support '" +
              term.getCondition().getDisplayValue() +
              "'. Supported: " +
              field.getConditionSet() +
              ".");
    }
}
