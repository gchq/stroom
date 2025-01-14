package stroom.proxy.repo;

import stroom.meta.api.AttributeMap;
import stroom.util.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            metaKeys.forEach(key ->
                    map.put(key, attributeMap.get(key)));
            return map;
        } else {
            return Collections.emptyMap();
        }
    }

    public void log(final Logger logger,
                    final AttributeMap attributeMap,
                    final String type,
                    final String url,
                    final int responseCode,
                    final long bytes,
                    final long duration) {
        log(logger, attributeMap, type, url, responseCode, bytes, duration, null);
    }

    public void log(final Logger logger,
                    final AttributeMap attributeMap,
                    final String type,
                    final String url,
                    final int responseCode,
                    final long bytes,
                    final long duration,
                    final String message) {

        if (logger.isInfoEnabled()) {
            final Map<String, String> filteredAttributes = filterAttributes(attributeMap);

            final String kvPairs = CSVFormatter.format(filteredAttributes, false);
            final String logLine = CSVFormatter.escape(type) +
                                   "," +
                                   CSVFormatter.escape(url) +
                                   "," +
                                   responseCode +
                                   "," +
                                   bytes +
                                   "," +
                                   duration +
                                   "," +
                                   CSVFormatter.escape(message) +
                                   "," +
                                   kvPairs;
            logger.info(logLine);
        }
    }
}
