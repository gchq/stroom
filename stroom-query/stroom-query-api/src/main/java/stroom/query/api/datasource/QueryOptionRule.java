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

package stroom.query.api.datasource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// A rule that disables a query option depending on the value of another option in the same
/// {@link QueryOptionSet}, e.g. "disabled when option `mode` is `STRICT`".
/// A disabled option takes its default value.
@JsonPropertyOrder({"optionId", "value", "negate"})
@JsonInclude(Include.NON_NULL)
public final class QueryOptionRule {

    @JsonProperty
    private final String optionId;
    @JsonProperty
    private final String value;
    @JsonProperty
    private final boolean negate;

    /// @param optionId The id of the other option.
    /// @param value    The value of the other option the rule tests for.
    /// @param negate   False if the rule applies when the other option has the value, true if it applies
    ///                 when the other option has any other value.
    @JsonCreator
    public QueryOptionRule(@JsonProperty("optionId") final String optionId,
                           @JsonProperty("value") final String value,
                           @JsonProperty("negate") final Boolean negate) {
        this.optionId = optionId;
        this.value = value;
        this.negate = Boolean.TRUE.equals(negate);
    }

    /// Creates a rule that applies when the other option has the given value.
    ///
    /// @param optionId The id of the other option.
    /// @param value    The value that disables the option the rule belongs to.
    /// @return The rule.
    public static QueryOptionRule disabledWhen(final String optionId, final String value) {
        return new QueryOptionRule(optionId, value, false);
    }

    /// Creates a rule that applies when the other option has any value other than the given value.
    ///
    /// @param optionId The id of the other option.
    /// @param value    The only value that leaves the option the rule belongs to enabled.
    /// @return The rule.
    public static QueryOptionRule disabledUnless(final String optionId, final String value) {
        return new QueryOptionRule(optionId, value, true);
    }

    /// @return The id of the other option this rule tests.
    public String getOptionId() {
        return optionId;
    }

    /// @return The value of the other option the rule tests for.
    public String getValue() {
        return value;
    }

    /// @return False if the rule applies when the other option has the value, true if it applies when the
    /// other option has any other value.
    public boolean isNegate() {
        return negate;
    }

    /// Tests the rule against the other option's effective value.
    ///
    /// @param otherValue The effective value of the option with id {@link #getOptionId()}.
    /// @return True if the option the rule belongs to is disabled.
    public boolean appliesTo(final String otherValue) {
        final boolean matches = Objects.equals(value, otherValue);
        return negate != matches;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QueryOptionRule that = (QueryOptionRule) o;
        return negate == that.negate && Objects.equals(optionId, that.optionId) && Objects.equals(value,
                that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(optionId, value, negate);
    }

    @Override
    public String toString() {
        return "disabled when " + optionId + (negate
                ? " != "
                : " == ") + value;
    }
}
