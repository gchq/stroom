package stroom.proxy.repo;

import stroom.meta.api.AttributeMap;
import stroom.util.NullSafe;

import org.slf4j.Logger;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

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

        if (logger.isInfoEnabled()) {
            final Set<String> metaKeys = logStreamConfigProvider.get().getMetaKeys();

            if (NullSafe.hasItems(metaKeys)) {
                final Map<String, String> filteredMap = attributeMap.entrySet()
                        .stream()
                        .filter(entry -> metaKeys.contains(entry.getKey().toLowerCase()))
                        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
                final String kvPairs = CSVFormatter.format(filteredMap);
                final String message = CSVFormatter.escape(type) +
                        "," +
                        CSVFormatter.escape(url) +
                        "," +
                        responseCode +
                        "," +
                        bytes +
                        "," +
                        duration +
                        "," +
                        kvPairs;
                logger.info(message);
            }
        }
    }
}
