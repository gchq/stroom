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

import java.util.Objects;
import java.util.Optional;

@JsonPropertyOrder({"lineNo", "colNo"})
@JsonInclude(Include.NON_NULL)
public final class DefaultLocation implements Location {

    @JsonProperty
    private final int lineNo;
    @JsonProperty
    private final int colNo;

    @JsonCreator
    public DefaultLocation(@JsonProperty("lineNo") final int lineNo,
                           @JsonProperty("colNo") final int colNo) {
        this.lineNo = lineNo;
        this.colNo = colNo;
    }

    public static Location of(final int lineNo, final int colNo) {
        return new DefaultLocation(lineNo, colNo);
    }

    public static Location beginning() {
        return new DefaultLocation(1, 1);
    }

    @Override
    public int getLineNo() {
        return lineNo;
    }

    @Override
    public int getColNo() {
        return colNo;
    }

    @JsonIgnore
    @Override
    public boolean isUnknown() {
        return colNo <= 0 && lineNo <= 0;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultLocation that = (DefaultLocation) o;
        return lineNo == that.lineNo &&
                colNo == that.colNo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineNo, colNo);
    }

    @Override
    public int compareTo(final Location o) {
        if (o == this) {
            return 0;
        }
        if (!(o instanceof DefaultLocation)) {
            return 1;
        }

        return LINE_COL_COMPARATOR.compare(this, o);
    }

    @Override
    public String toString() {
        return lineNo +
                ":" +
                colNo;
    }

    public static Optional<Location> parse(final String value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        } else {
            final String[] parts = value.split(":");
            if (parts.length == 1) {
                return Optional.of(DefaultLocation.of(Integer.parseInt(parts[0]), 1));
            } else if (parts.length == 2) {
                return Optional.of(DefaultLocation.of(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1])));
            } else {
                throw new NumberFormatException("Value [" + value + "] is not valid");
            }
        }
    }
}
