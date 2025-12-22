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

package stroom.util.shared.validation;

import jakarta.validation.Payload;

import java.util.Set;

/**
 * Severities for jakarta.validation.  Use it like this:
 *
 * <pre>
 * {@code
 * @AssertFalse(message = "some message if not false", payload = ValidationSeverity.Warning.class)
 * }
 * </pre>
 */
public enum ValidationSeverity {
    ERROR(Error.class),
    WARNING(Warning.class);

    private final Class<? extends ValidationSeverityPayload> payloadClass;

    ValidationSeverity(final Class<? extends ValidationSeverityPayload> payloadClass) {
        this.payloadClass = payloadClass;
    }

    public Class<? extends Payload> payload() {
        return payloadClass;
    }

    public static ValidationSeverity fromPayloads(final Set<Class<? extends Payload>> payloads) {
        final ValidationSeverity result;
        if (payloads == null || payloads.isEmpty()) {
            // ERROR is assumed if we have no other info
            result = ERROR;
        } else {
            if (payloads.contains(Error.class)) {
                // ERROR trumps WARNING
                result = ERROR;
            } else if (payloads.contains(Warning.class)) {
                // No errors but a warning
                result = WARNING;
            } else {
                // None of either so assume ERROR
                result = ERROR;
            }
        }
        return result;
    }

    public interface ValidationSeverityPayload extends Payload {

    }

    public class Error implements ValidationSeverityPayload {

    }

    public class Warning implements ValidationSeverityPayload {

    }

}
