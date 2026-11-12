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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/// One pair of equal keys in the `on` part of a StroomQL join, such as
/// `lowerCase(UserId) = hr.Username`.
///
/// Each side is a StroomQL expression, as written in an `eval`. The left side is worked out from the
/// rows the join is applied to, the right side from the joined source's rows. Fields of the joined
/// source are named `<alias>.<field>` on both sides.
@JsonPropertyOrder({"left", "right"})
@JsonInclude(Include.NON_NULL)
@Schema(description = "A pair of expressions whose values must be equal for two rows to join")
public final class JoinKey {

    @Schema(description = "The expression worked out from the rows the join is applied to")
    @JsonProperty
    private final String left;

    @Schema(description = "The expression worked out from the joined source's rows")
    @JsonProperty
    private final String right;

    /// Creates a pair of join keys.
    ///
    /// @param left  The expression worked out from the rows the join is applied to.
    /// @param right The expression worked out from the joined source's rows.
    @JsonCreator
    public JoinKey(@JsonProperty("left") final String left,
                   @JsonProperty("right") final String right) {
        this.left = left;
        this.right = right;
    }

    /// Gets the expression worked out from the rows the join is applied to.
    ///
    /// @return The expression, e.g. `lowerCase(UserId)`.
    public String getLeft() {
        return left;
    }

    /// Gets the expression worked out from the joined source's rows.
    ///
    /// @return The expression, e.g. `hr.Username`.
    public String getRight() {
        return right;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final JoinKey joinKey = (JoinKey) o;
        return Objects.equals(left, joinKey.left) &&
               Objects.equals(right, joinKey.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right);
    }

    @Override
    public String toString() {
        return left + " = " + right;
    }
}
