package stroom.proxy.repo;

import stroom.meta.api.AttributeMap;
import stroom.proxy.StroomStatusCode;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Singleton
public class LogStream {

    private final Provider<LogStreamConfig> logStreamConfigProvider;

    @Inject
    public LogStream(final Provider<LogStreamConfig> logStreamConfigProvider) {
        this.logStreamConfigProvider = logStreamConfigProvider;
    }

    public Map<String, String> filterAttributes(final AttributeMap attributeMap) {
        // Use a LinkedHashMap to adhere to metaKeys order, which is a LinkedHashSet
        final List<String> metaKeys = logStreamConfigProvider.get().getMetaKeys();
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
            final Map<String, String> filteredAttributes = filterAttributes(attributeMap);

            final String kvPairs = CSVFormatter.format(filteredAttributes, false);
            final String logLine = String.join(",",
                    CSVFormatter.escape(type.name()),
                    CSVFormatter.escape(url),
                    Integer.toString(httpResponseCode),
                    Integer.toString(stroomStatusCode),
                    CSVFormatter.escape(receiptId),
                    Long.toString(bytes),
                    Long.toString(duration),
                    CSVFormatter.escape(message),
                    kvPairs);

            logger.info(logLine);
        }
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
    }
}
