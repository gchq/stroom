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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"severity", "location", "elementId", "message"})
@JsonInclude(Include.NON_NULL)
public final class StoredError implements Marker, Comparable<StoredError> {

    public static final String MESSAGE_CAUSE_DELIMITER = " > ";
    private static final String SPACE = " ";
    private static final String CLOSE_BRACKET = "] ";
    private static final String COLON = ":";
    private static final String OPEN_BRACKET = "[";

    @JsonProperty
    private final Severity severity;
    @JsonProperty
    private final Location location;
    @JsonProperty
    private final ElementId elementId;
    @JsonProperty
    private final String message;
    @JsonProperty
    private final ErrorType errorType;

    public StoredError(final Severity severity,
                       final Location location,
                       final ElementId elementId,
                       final String message) {
        this(severity, location, elementId, message, null);

    }

    @JsonCreator
    public StoredError(@JsonProperty("severity") final Severity severity,
                       @JsonProperty("location") final Location location,
                       @JsonProperty("elementId") final ElementId elementId,
                       @JsonProperty("message") final String message,
                       @JsonProperty("errorType") final ErrorType errorType) {
        this.severity = severity;
        this.location = location;
        this.elementId = elementId;
        this.message = message;
        this.errorType = NullSafe.requireNonNullElse(errorType, ErrorType.UNKNOWN);
    }

    @Override
    public Severity getSeverity() {
        return severity;
    }

    public Location getLocation() {
        return location;
    }

    public ElementId getElementId() {
        return elementId;
    }

    public String getMessage() {
        return message;
    }

    /**
     * @return A non-null {@link ErrorType}
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StoredError that = (StoredError) o;
        return Objects.equals(location, that.location) &&
               Objects.equals(message, that.message) &&
               Objects.equals(errorType, that.errorType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, message, errorType);
    }

    @Override
    public int compareTo(final StoredError o) {
        if (!severity.equals(o.severity)) {
            if (severity.greaterThan(o.severity)) {
                return -1;
            } else {
                return 1;
            }
        }

        if (location == null && o.location == null) {
            return 0;
        } else if (location == null) {
            return -1;
        } else if (o.location == null) {
            return 1;
        }

        return location.compareTo(o.location);
    }

    public void append(final StringBuilder sb) {
        sb.append(elementId);
        sb.append(SPACE);
        if (location != null) {
            sb.append(OPEN_BRACKET);
            sb.append(location);
            sb.append(CLOSE_BRACKET);
        }
        sb.append(severity.getDisplayValue());
        sb.append(COLON);
        sb.append(SPACE);
        sb.append(message);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb);
        return sb.toString();
    }
}
