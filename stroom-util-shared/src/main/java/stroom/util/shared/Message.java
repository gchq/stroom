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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Message {

    @JsonProperty
    private final Severity severity;
    @JsonProperty
    private final String message;

    @JsonCreator
    public Message(@JsonProperty("severity") final Severity severity,
                   @JsonProperty("message") final String message) {
        this.severity = severity;
        this.message = message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    /**
     * @return A {@link Message} with {@link Severity#INFO}.
     */
    public static Message info(final String message) {
        return new Message(Severity.INFO, message);
    }

    /**
     * @return A {@link Message} with {@link Severity#WARNING}.
     */
    public static Message warning(final String message) {
        return new Message(Severity.WARNING, message);
    }

    /**
     * @return A {@link Message} with {@link Severity#ERROR}.
     */
    public static Message error(final String message) {
        return new Message(Severity.ERROR, message);
    }

    /**
     * @return A {@link Message} with {@link Severity#FATAL_ERROR}.
     */
    public static Message fatalError(final String message) {
        return new Message(Severity.FATAL_ERROR, message);
    }

    /**
     * @return An immutable list containing this {@link Message}
     */
    public List<Message> asList() {
        return Collections.singletonList(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Message message1 = (Message) o;
        return severity == message1.severity &&
               Objects.equals(message, message1.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(severity, message);
    }

    @Override
    public String toString() {
        return severity.name() + ": " + message;
    }
}
