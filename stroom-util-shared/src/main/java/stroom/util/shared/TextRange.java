/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"from", "to"})
@JsonInclude(Include.NON_NULL)
public class TextRange implements Comparable<TextRange> {
    @JsonProperty
    private final Location from;
    @JsonProperty
    private final Location to;

    @JsonCreator
    public TextRange(@JsonProperty("from") final Location from,
                     @JsonProperty("to") final Location to) {
        this.from = from;
        this.to = to;
    }

    /**
     * @return Inclusive from location
     */
    public Location getFrom() {
        return from;
    }

    /**
     * @return Inclusive to location
     */
    public Location getTo() {
        return to;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final TextRange highlight = (TextRange) o;
        return Objects.equals(from, highlight.from) &&
                Objects.equals(to, highlight.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }

    @Override
    public int compareTo(final TextRange o) {
        final CompareBuilder builder = new CompareBuilder();
        builder.append(from, o.from);
        builder.append(to, o.to);
        return builder.toComparison();
    }

    @Override
    public String toString() {
        return "[" +
                from +
                "][" +
                to +
                "]";
    }

    /**
     * @param lineNo One based.
     * @param colNo One based.
     */
    public boolean isOnOrAfterFromLocation(final int lineNo, int colNo) {
        return lineNo > from.getLineNo()
                || (lineNo == from.getLineNo() && colNo >= from.getColNo());
    }

    /**
     * @param lineNo One based.
     * @param colNo One based.
     */
    public boolean isOnOrBeforeToLocation(final int lineNo, int colNo) {
        return lineNo < to.getLineNo()
                || (lineNo == to.getLineNo() && colNo >= to.getColNo());
    }

    /**
     * Inclusive at both ends.
     * One based.
     */
    public boolean isInsideRange(final int lineNo, int colNo) {
        return isOnOrAfterFromLocation(lineNo, colNo)
                && isOnOrBeforeToLocation(lineNo, colNo);
    }
}
