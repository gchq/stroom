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

package stroom.data.store.impl.fs.shared;

import stroom.util.shared.Severity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.function.BooleanSupplier;

@JsonInclude(Include.NON_NULL)
public class ValidationResult {

    private static final ValidationResult OK = new ValidationResult(null, null);

    @JsonProperty
    private final Severity severity;
    @JsonProperty
    private final String message;

    @JsonCreator
    public ValidationResult(@JsonProperty("severity") final Severity severity,
                            @JsonProperty("message") final String message) {
        this.severity = severity;
        this.message = message;
    }

    public static ValidationResult ok() {
        return OK;
    }

    public static ValidationResult error(final String message) {
        return new ValidationResult(Severity.ERROR, message);
    }

    public static ValidationResult warning(final String message) {
        return new ValidationResult(Severity.WARNING, message);
    }

    public static ValidationResult fatal(final String message) {
        return new ValidationResult(Severity.FATAL_ERROR, message);
    }

    /**
     * If this is OK, calls test and returns a new {@link ValidationResult} based on the outcome of test.
     */
    public ValidationResult errorIf(final String message, final BooleanSupplier test) {
        return validate(Severity.ERROR, message, test);
    }

    public ValidationResult validate(final Severity severity, final String message, final BooleanSupplier test) {
        if (isOk() && test != null) {
            final boolean didPass = test.getAsBoolean();
            if (didPass) {
                return this;
            } else {
                return new ValidationResult(
                        Objects.requireNonNull(severity),
                        Objects.requireNonNull(message));
            }
        } else {
            return this;
        }
    }

    public ValidationResult errorIfNull(final String message, final Object object) {
        if (isOk()) {
            if (object == null) {
                return new ValidationResult(Severity.ERROR, message);
            } else {
                return this;
            }
        } else {
            return this;
        }
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    @JsonIgnore
    public boolean isOk() {
        return severity == null;
    }

    @JsonIgnore
    public boolean isWarning() {
        return Severity.WARNING.equals(severity);
    }

    @JsonIgnore
    public boolean isError() {
        return Severity.ERROR.equals(severity);
    }

    public boolean hasMessage() {
        return message != null && !message.isEmpty();
    }
}
