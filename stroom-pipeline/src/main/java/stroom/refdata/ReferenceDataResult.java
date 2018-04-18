/*
 * Copyright 2018 Crown Copyright
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

package stroom.refdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;
import stroom.xml.event.EventList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ReferenceDataResult implements ErrorReceiver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceDataResult.class);

    private EventList eventList;
    private List<LazyMessage> messages = new ArrayList<>();

    public void setEventList(final EventList eventList) {
        this.eventList = eventList;
    }

    public EventList getEventList() {
        return eventList;
    }

    public void log(final Severity severity, final Supplier<String> message) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(message.get());
        }

        messages.add(new LazyMessage(severity, null, null, message));
    }

    @Override
    public void log(final Severity severity, final Location location, final String elementId, final String message, final Throwable e) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(message);

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(e.getMessage(), e);
            }
        }

        messages.add(new LazyMessage(severity, location, elementId, () -> message));
    }

    public void append(final StringBuilder sb) {
        messages.forEach(message -> {
            sb.append(message.toString());
            sb.append("\n");
        });
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb);
        return sb.toString();
    }

    public List<LazyMessage> getMessages() {
        return messages;
    }

    public static class LazyMessage {
        private static final String SPACE = " ";
        private static final String CLOSE_BRACKET = "] ";
        private static final String COLON = ":";
        private static final String OPEN_BRACKET = "[";

        private Severity severity;
        private Location location;
        private String elementId;
        private Supplier<String> message;

        LazyMessage(final Severity severity, final Location location, final String elementId, final Supplier<String> message) {
            this.severity = severity;
            this.location = location;
            this.elementId = elementId;
            this.message = message;
        }

        public Severity getSeverity() {
            return severity;
        }

        public Location getLocation() {
            return location;
        }

        public String getElementId() {
            return elementId;
        }

        public Supplier<String> getMessage() {
            return message;
        }

        public void append(final StringBuilder sb) {
            if (elementId != null) {
                sb.append(elementId);
                sb.append(SPACE);
            }
            if (location != null) {
                sb.append(OPEN_BRACKET);
                sb.append(location);
                sb.append(CLOSE_BRACKET);
            }
            if (severity != null) {
                sb.append(severity.getDisplayValue());
                sb.append(COLON);
                sb.append(SPACE);
            }
            if (message != null) {
                sb.append(message.get());
            }
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            append(sb);
            return sb.toString();
        }
    }
}
