/*
 * Copyright 2017 Crown Copyright
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

package stroom.proxy.repo;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.StroomStreamException;
import stroom.receive.common.StroomStreamStatus;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Singleton
public class LogStream {

    private static final String META_KEY_PREFIX = "meta.";
    private final Provider<LogStreamConfig> logStreamConfigProvider;

    @Inject
    public LogStream(final Provider<LogStreamConfig> logStreamConfigProvider) {
        this.logStreamConfigProvider = logStreamConfigProvider;
    }

    @NonNull
    private Map<String, String> filterAttributes(final LogStreamConfig logStreamConfig,
                                                 final AttributeMap attributeMap) {
        // Use a LinkedHashMap to adhere to metaKeys order, which is a LinkedHashSet
        final List<String> metaKeys = logStreamConfig.getMetaKeys();
        if (NullSafe.hasItems(metaKeys)) {
            final Map<String, String> map = new LinkedHashMap<>(metaKeys.size());
            final Map<String, String> keyMap = attributeMap.getKeyMap();
            metaKeys.forEach(key -> {
                final String originalKey = keyMap.get(key.toLowerCase(Locale.ROOT));
                if (originalKey != null) {
                    map.put(originalKey, attributeMap.get(originalKey));
                } else {
                    map.put(key, null);
                }
            });
            return map;
        } else {
            return Collections.emptyMap();
        }
    }

    public void log(final Logger logger,
                    final StroomStreamException stroomStreamException,
                    final String url,
                    final String receiptId,
                    final long bytes,
                    final long duration) {
        Objects.requireNonNull(stroomStreamException);
        final EventType eventType = EventType.fromStreamException(stroomStreamException);
        final StroomStatusCode stroomStatusCode = stroomStreamException.getStroomStatusCode();
        log(logger,
                stroomStreamException.getAttributeMap(),
                eventType,
                url,
                stroomStatusCode.getHttpCode(),
                stroomStatusCode.getCode(),
                receiptId,
                bytes,
                duration,
                stroomStreamException.getMessage());

    }

    public void log(final Logger logger,
                    final AttributeMap attributeMap,
                    final EventType type,
                    final String url,
                    final StroomStatusCode stroomStatusCode,
                    final String receiptId,
                    final long bytes,
                    final long duration) {
        Objects.requireNonNull(stroomStatusCode);
        log(logger,
                attributeMap,
                type,
                url,
                stroomStatusCode.getHttpCode(),
                stroomStatusCode.getCode(),
                receiptId,
                bytes,
                duration,
                stroomStatusCode.getMessage());
    }

    public void log(final Logger logger,
                    final AttributeMap attributeMap,
                    final EventType type,
                    final String url,
                    final int httpResponseCode,
                    final int stroomStatusCode,
                    final String receiptId,
                    final long bytes,
                    final long duration) {
        log(logger,
                attributeMap,
                type,
                url,
                httpResponseCode,
                stroomStatusCode,
                receiptId,
                bytes,
                duration,
                null);
    }

    public void log(final Logger logger,
                    final AttributeMap attributeMap,
                    final EventType type,
                    final String url,
                    final StroomStatusCode stroomStatusCode,
                    final String receiptId,
                    final long bytes,
                    final long duration,
                    final String message) {
        log(logger,
                attributeMap,
                type,
                url,
                stroomStatusCode.getHttpCode(),
                stroomStatusCode.getCode(),
                receiptId,
                bytes,
                duration,
                message);
    }

    public void log(final Logger logger,
                    final AttributeMap attributeMap,
                    final EventType type,
                    final String url,
                    final int httpResponseCode,
                    final int stroomStatusCode,
                    final String receiptId,
                    final long bytes,
                    final long duration,
                    final String message) {

        if (logger.isInfoEnabled()) {
            final LogStreamConfig logStreamConfig = logStreamConfigProvider.get();
            final Map<String, String> filteredAttributes = filterAttributes(logStreamConfig, attributeMap);

            // Set all the data into the mapped diagnostic context to allow structured JSON logging
            if (logStreamConfig.isUseMappedDiagnosticContext()) {
                try {
                    putContextData(
                            type,
                            url,
                            httpResponseCode,
                            stroomStatusCode,
                            receiptId,
                            bytes,
                            duration,
                            filteredAttributes);
                    logger.info(message);
                } finally {
                    // Clear the MDC context for this thread to ensure the values don't leak into future log messages.
                    // By default, DropWiz uses the asynchronous file appender, but this appender copies the
                    // MDC map in the current thread, so we are OK to clear it at this point.
                    MDC.clear();
                }
            } else {
                // Non-JSON logging requires us to CSV all the data.
                final String kvPairs = CSVFormatter.format(filteredAttributes, false);
                final String logMessage = String.join(",",
                        CSVFormatter.escape(type.name()),
                        CSVFormatter.escape(url),
                        Integer.toString(httpResponseCode),
                        Integer.toString(stroomStatusCode),
                        CSVFormatter.escape(receiptId),
                        Long.toString(bytes),
                        Long.toString(duration),
                        CSVFormatter.escape(message),
                        kvPairs);
                logger.info(logMessage);
            }
        }
    }

    private static void putContextData(final EventType type,
                                       final String url,
                                       final int httpResponseCode,
                                       final int stroomStatusCode,
                                       final String receiptId,
                                       final long bytes,
                                       final long duration,
                                       final Map<String, String> filteredAttributes) {
        // For details on how to configure logging to use the JSON layout to take advantage of
        // this MDC data, see https://www.dropwizard.io/en/stable/manual/configuration.html#json-layout

        MDC.put("eventType", NullSafe.get(type, EventType::name));
        MDC.put("url", url);
        MDC.put("httpResponseCode", Integer.toString(httpResponseCode));
        MDC.put("stroomStatusCode", Integer.toString(stroomStatusCode));
        // receiptId should be in the meta, but just in case it isn't
        if (!filteredAttributes.containsKey(StandardHeaderArguments.RECEIPT_ID)) {
            MDC.put(StandardHeaderArguments.RECEIPT_ID, receiptId);
        }
        MDC.put("bytes", Long.toString(bytes));
        MDC.put("duration", Long.toString(duration));

        // Add the filtered meta attributes to the MDC.
        filteredAttributes.forEach((key, value) -> {
            if (NullSafe.isNonBlankString(key)) {
                MDC.put(META_KEY_PREFIX + key, value);
            }
        });
    }


    // --------------------------------------------------------------------------------


    public enum EventType {
        /**
         * A successful send.
         */
        SEND,
        /**
         * A successful receive.
         */
        RECEIVE,
        /**
         * Data rejected. Used in both send and receive log.
         */
        REJECT,
        /**
         * Data dropped. Used in receive log only.
         */
        DROP,
        /**
         * An error happened on send or receive.
         */
        ERROR,
        ;

        @Override
        public String toString() {
            return name();
        }

        public static EventType fromStroomStatusCode(final StroomStatusCode stroomStatusCode) {
            return switch (stroomStatusCode) {
                case FEED_IS_NOT_SET_TO_RECEIVE_DATA,
                     FEED_IS_NOT_DEFINED,
                     FEED_MUST_BE_SPECIFIED,
                     INVALID_FEED_NAME,
                     REJECTED_BY_POLICY_RULES,
                     INVALID_TYPE,
                     UNEXPECTED_DATA_TYPE,
                     MISSING_MANDATORY_HEADER -> REJECT;
                default -> ERROR;
            };
        }

        public static EventType fromStreamException(final StroomStreamException e) {
            Objects.requireNonNull(e);
            return NullSafe.getOrElse(
                    e.getStroomStreamStatus(),
                    StroomStreamStatus::getStroomStatusCode,
                    EventType::fromStroomStatusCode,
                    EventType.ERROR);
        }
    }
}
