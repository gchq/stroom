package stroom.proxy.repo;

import stroom.meta.api.AttributeMap;
import stroom.util.NullSafe;

import org.slf4j.Logger;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class LogStream {

    private final Provider<LogStreamConfig> logStreamConfigProvider;

    @Inject
    public LogStream(final Provider<LogStreamConfig> logStreamConfigProvider) {
        this.logStreamConfigProvider = logStreamConfigProvider;
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
            final Set<String> metaKeys = logStreamConfigProvider.get().getMetaKeys();

            if (NullSafe.hasItems(metaKeys)) {
                final Map<String, String> filteredMap = attributeMap.entrySet()
                        .stream()
                        .filter(entry -> metaKeys.contains(entry.getKey().toLowerCase()))
                        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
                final String kvPairs = CSVFormatter.format(filteredMap);
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
}
