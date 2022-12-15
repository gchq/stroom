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

package stroom.pipeline.refdata;

import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.util.NullSafe;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class ReferenceDataResult implements ErrorReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceDataResult.class);

    private RefDataValueProxy refDataValueProxy;

    private List<LazyMessage> messages = new ArrayList<>();

    private final List<RefStreamDefinition> refStreamDefinitions = new ArrayList<>();

    public Optional<RefDataValueProxy> getRefDataValueProxy() {
        return Optional.ofNullable(refDataValueProxy);
    }

    void setRefDataValueProxy(final RefDataValueProxy refDataValueProxy) {
        this.refDataValueProxy = refDataValueProxy;
    }

    public void addEffectiveStream(final RefStreamDefinition refStreamDefinition) {
        Objects.requireNonNull(refStreamDefinition);
        refStreamDefinitions.add(refStreamDefinition);
    }

    public List<RefStreamDefinition> getEffectiveStreams() {
        return refStreamDefinitions;
    }

    /**
     * Log a message using a template. SLF style templating.
     */
    // Different name to avoid confusion with varargs
    public void logSimpleTemplate(final Severity severity,
                                  final String messageTemplate,
                                  final Object... messageArguments) {
        // Use trace level as we don't know what level this func is called from
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(messageTemplate, (Object[]) messageArguments);
        }

        messages.add(new LazyMessage(
                severity,
                null,
                null,
                messageTemplate,
                messageArguments));
    }

    /**
     * Log a message using a template with lazily provided template arguments.
     * SLF style templating.
     */
    public void logLazyTemplate(final Severity severity,
                                final String messageTemplate,
                                final Supplier<List<Object>> messageArgumentsSupplier) {
        // Use trace level as we don't know what level this func is called from
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(messageTemplate, LazyMessage.convertMessageArgs(messageArgumentsSupplier));
        }

        messages.add(new LazyMessage(
                severity,
                null,
                null,
                messageTemplate,
                messageArgumentsSupplier));
    }

    @Override
    public void logTemplate(final Severity severity,
                            final Location location,
                            final String elementId,
                            final String messageTemplate,
                            final Throwable e,
                            final Object... messageArgs) {
        // Use trace level as we don't know what level this func is called from
        if (LOGGER.isTraceEnabled()) {
            if ((Object[]) messageArgs == null || messageArgs.length == 0) {
                if (e == null) {
                    LOGGER.trace(messageTemplate);
                } else {
                    LOGGER.trace(messageTemplate, e);
                }
            } else {
                if (e == null) {
                    LOGGER.trace(messageTemplate, messageArgs);
                } else {
                    // Add the ex on the end
                    final Object[] args = Arrays.copyOf(messageArgs, messageArgs.length + 1);
                    args[messageArgs.length] = e;
                    LOGGER.trace(messageTemplate, args);
                }
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(e.getMessage(), e);
            }
        }

        messages.add(new LazyMessage(
                severity,
                location,
                elementId,
                messageTemplate,
                messageArgs));
    }

    @Override
    public void log(final Severity severity,
                    final Location location,
                    final String elementId,
                    final String message,
                    final Throwable e) {

        logTemplate(severity, location, elementId, message, e, (Object[]) null);
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


    // --------------------------------------------------------------------------------


    public static class LazyMessage {

        private static final String SPACE = " ";
        private static final String CLOSE_BRACKET = "] ";
        private static final String COLON = ":";
        private static final String OPEN_BRACKET = "[";

        private final Severity severity;
        private final Location location;
        private final String elementId;
        // Hold template and args separately to save memory, e.g. if we have loads of messages of a similar
        // type.
        private final String messageTemplate;
        private final Object[] messageArgs;
        private final Supplier<List<Object>> messageArgsSupplier;
        private final boolean useSupplierForArgs;

        private LazyMessage(final Severity severity,
                            final Location location,
                            final String elementId,
                            final String messageTemplate,
                            final Object... messageArgs) {
            this.severity = severity;
            this.location = location;
            this.elementId = elementId;
            this.messageTemplate = messageTemplate;
            this.messageArgs = messageArgs;
            this.messageArgsSupplier = null;
            this.useSupplierForArgs = false;
        }

        private LazyMessage(final Severity severity,
                            final Location location,
                            final String elementId,
                            final String messageTemplate,
                            final Supplier<List<Object>> messageArgsSupplier) {
            this.severity = severity;
            this.location = location;
            this.elementId = elementId;
            this.messageTemplate = messageTemplate;
            this.messageArgs = null;
            this.messageArgsSupplier = messageArgsSupplier;
            this.useSupplierForArgs = true;
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

        public String getMessage() {
            final Object[] messageArgs = getMessageArgs();
            if (messageArgs == null || messageArgs.length == 0) {
                return messageTemplate;
            } else {
                return LogUtil.message(messageTemplate, messageArgs);
            }
        }

        private Object[] getMessageArgs() {
            return useSupplierForArgs
                    ? convertMessageArgs(messageArgsSupplier)
                    : messageArgs;
        }

        private static Object[] convertMessageArgs(final Supplier<List<Object>> messageArgsSupplier) {
            final List<Object> argsList = NullSafe.getOrElseGet(
                    messageArgsSupplier,
                    Supplier::get,
                    Collections::emptyList);
            return argsList != null
                    ? argsList.toArray(new Object[0])
                    : null;
        }

        private void append(final StringBuilder sb) {
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
            NullSafe.consume(getMessage(), sb::append);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            append(sb);
            return sb.toString();
        }
    }
}
