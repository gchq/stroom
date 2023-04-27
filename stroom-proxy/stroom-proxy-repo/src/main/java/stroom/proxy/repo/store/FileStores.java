package stroom.proxy.repo.store;

import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import javax.inject.Singleton;

@Singleton
public class FileStores {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FileStores.class);

    private final Map<Key, Path> fileStores = new ConcurrentHashMap<>();

    public void add(final int order, final String name, final Path directory) {
        fileStores.put(new Key(order, name), directory);
    }

    public synchronized String log() {
        final Map<Key, Long> sizes = new HashMap<>();
        final Map<Key, Long> fileCounts = new HashMap<>();
        for (final Entry<Key, Path> entry : fileStores.entrySet()) {
            final Path path = entry.getValue();
            final AtomicLong size = new AtomicLong();
            final AtomicLong count = new AtomicLong();
            if (Files.isDirectory(path)) {
                try (final Stream<Path> stream = Files.walk(path)) {
                    stream.forEach(p -> {
                        if (Files.isRegularFile(p)) {
                            count.incrementAndGet();
                            try {
                                size.addAndGet(Files.size(p));
                            } catch (final IOException e) {
                                LOGGER.trace(e::getMessage, e);
                            }
                        }
                    });
                } catch (final IOException e) {
                    LOGGER.trace(e::getMessage, e);
                }
                sizes.put(entry.getKey(), size.get());
                fileCounts.put(entry.getKey(), count.get());
            }
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("<table>");
        sb.append("<tr>");
        sb.append("<th>Name</th>");
        sb.append("<th>File Count</th>");
        sb.append("<th>Bytes</th>");
        sb.append("<th>Directory</th>");
        sb.append("</tr>");
        fileStores.keySet()
                .stream()
                .sorted(Comparator
                        .comparing(Key::order)
                        .thenComparing(Key::name))
                .forEach(key -> {
                    Long size = sizes.get(key);
                    Long count = fileCounts.get(key);

                    sb.append("<tr>");
                    sb.append("<td>");
                    sb.append(key.name);
                    sb.append("</td>");
                    sb.append("<td>");
                    if (count != null) {
                        sb.append(count);
                    }
                    sb.append("</td>");
                    sb.append("<td>");
                    if (size != null) {
                        sb.append(ModelStringUtil.formatMetricByteSizeString(size));
                    }
                    sb.append("</td>");
                    sb.append("<td>");
                    sb.append(FileUtil.getCanonicalPath(fileStores.get(key)));
                    sb.append("</td>");
                    sb.append("</tr>");
                });
        sb.append("</table>");

        return sb.toString();
    }

    private record Key(int order, String name) {

    }
}
