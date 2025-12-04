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


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Comparator;
import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StreamLocation.class, name = "stream"),
        @JsonSubTypes.Type(value = DefaultLocation.class, name = "default")
})
public sealed interface Location extends Comparable<Location> permits StreamLocation, DefaultLocation {

    int UNKNOWN_VALUE = -1;

    Comparator<Location> LINE_COL_COMPARATOR = Comparator
            .comparingInt(Location::getLineNo)
            .thenComparingInt(Location::getColNo);

    /**
     * @return The line number of the location, one based.
     */
    int getLineNo();

    /**
     * @return The column number of the location, one based.
     */
    int getColNo();

    /**
     * @return True if no location information is known.
     * False if full/partial location information is known.
     */
    boolean isUnknown();

    default boolean hasLineAndCol() {
        return hasLineNo()
                && getColNo() > 0;
    }

    default boolean hasLineNo() {
        return getLineNo() > 0;
    }

    @JsonIgnore
    default boolean isBefore(final Location other) {
        Objects.requireNonNull(other);
        return LINE_COL_COMPARATOR.compare(this, other) < 0;
    }

    @JsonIgnore
    default boolean isAfter(final Location other) {
        Objects.requireNonNull(other);
        return LINE_COL_COMPARATOR.compare(this, other) > 0;
    }
}
