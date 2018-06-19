package stroom.proxy.handler;

import org.slf4j.Logger;
import stroom.data.meta.api.AttributeMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class LogStream {
    private final Set<String> metaKeySet;

    @Inject
    public LogStream(final LogStreamConfig logStreamConfig) {
        if (logStreamConfig != null) {
            metaKeySet = getMetaKeySet(logStreamConfig.getMetaKeys());
        } else {
            metaKeySet = Collections.emptySet();
        }
    }

    public void log(final Logger logger, final AttributeMap attributeMap, final String type, final String url, final int responseCode, final long bytes, final long duration) {
        if (logger.isInfoEnabled() && metaKeySet.size() > 0) {
            final Map<String, String> filteredMap = attributeMap.entrySet().stream()
                    .filter(entry -> metaKeySet.contains(entry.getKey().toLowerCase()))
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

    private Set<String> getMetaKeySet(final String csv) {
        if (csv == null || csv.length() == 0) {
            return Collections.emptySet();
        }

        return Arrays.stream(csv.toLowerCase().split(",")).collect(Collectors.toSet());
    }
}
