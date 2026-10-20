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

package stroom.query.shared;

import stroom.query.api.ExpressionOperator;
import stroom.util.shared.TokenError;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Either the tree the text parsed to, or the positional reason it did not. Blank text parses to a
 * null expression with no error.
 */
@JsonInclude(Include.NON_NULL)
public class ParseQuickFilterResult {

    @JsonProperty
    private final ExpressionOperator expression;
    @JsonProperty
    private final TokenError error;

    @JsonCreator
    public ParseQuickFilterResult(@JsonProperty("expression") final ExpressionOperator expression,
                                  @JsonProperty("error") final TokenError error) {
        this.expression = expression;
        this.error = error;
    }

    public static ParseQuickFilterResult of(final ExpressionOperator expression) {
        return new ParseQuickFilterResult(expression, null);
    }

    public static ParseQuickFilterResult failed(final TokenError error) {
        return new ParseQuickFilterResult(null, error);
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public TokenError getError() {
        return error;
    }

    @JsonIgnore
    public boolean isOk() {
        return error == null;
    }

    @Override
    public String toString() {
        return "ParseQuickFilterResult{" +
               "expression=" + expression +
               ", error=" + error +
               '}';
    }
}
