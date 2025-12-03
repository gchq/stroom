/*
 * Copyright 2016-2025 Crown Copyright
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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Comparator;
import java.util.Objects;

@JsonPropertyOrder({"from", "to"})
@JsonInclude(Include.NON_NULL)
public class TextRange implements Comparable<TextRange> {

    private static final Comparator<TextRange> COMPARATOR = Comparator
            .comparing(TextRange::getFrom, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(TextRange::getTo, Comparator.nullsFirst(Comparator.naturalOrder()));

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

    public static TextRange of(final Location from,
                               final Location to) {
        return new TextRange(from, to);
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
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
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
        return COMPARATOR.compare(this, o);
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
     * Move the highlight forwards or backwards only the same line by charDelta chars
     */
    public TextRange advance(final int delta) {
        return new TextRange(
                DefaultLocation.of(from.getLineNo(), from.getColNo() + delta),
                DefaultLocation.of(to.getLineNo(), to.getColNo() + delta));
    }

    /**
     * Move a highlight on the same line
     */
    public TextRange withNewStartPosition(final Location from) {
        final int highlightLen = this.to.getColNo() - this.from.getColNo() + 1;
        return new TextRange(
                from,
                DefaultLocation.of(from.getLineNo(), from.getColNo() + highlightLen - 1));
    }

    @JsonIgnore
    public boolean isOnOneLine() {
        return from.getLineNo() == to.getLineNo();
    }

    /**
     * @return True if all of this range is inside or is identical to
     * the other range.
     */
    public boolean isInsideRange(final TextRange other) {
        if (other == null) {
            return false;
        } else {
            return isInsideRange(other.from, other.to);
        }
    }

    public boolean isInsideRange(final Location from, final Location to) {
        final boolean result;
        if (this.from == null
                || this.to == null
                || from == null
                || to == null) {
            result = false;
        } else {
            result = this.getFrom().compareTo(from) >= 0
                    && this.getTo().compareTo(to) <= 0;
        }
        return result;
    }

    /**
     * @return True if the from location of this is before the from location of other
     */
    public boolean isBefore(final TextRange other) {
        Objects.requireNonNull(other);
        return this.from.isBefore(other.from);
    }

    /**
     * @return True if the from location of this is before the from location of other
     */
    public boolean isAfter(final TextRange other) {
        Objects.requireNonNull(other);
        return this.from.isAfter(other.from);
    }
}
