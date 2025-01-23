package stroom.proxy.repo.store;

import stroom.util.Metrics;
import stroom.util.NullSafe;
import stroom.util.concurrent.CachedValue;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

@Singleton
public class FileStores {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FileStores.class);

    private final Map<Key, Path> fileStores = new ConcurrentHashMap<>();

    private final CachedValue<Map<Key, StoreStats>, Void> statsMapUpdater;
//    private final Queue<NamedMetric> unregisteredMetrics = new ConcurrentLinkedQueue<>();
//    private volatile Consumer<NamedMetric> metricConsumer;

    public FileStores() {
        statsMapUpdater = CachedValue.stateless(Duration.ofSeconds(30), this::buildStoreState);
    }

    private Map<Key, StoreStats> buildStoreState() {
        LOGGER.info("Capturing store stats");
        final Map<Key, StoreStats> map = new HashMap<>();
        for (final Entry<Key, Path> entry : fileStores.entrySet()) {
            final Path path = entry.getValue();
            final LongAdder size = new LongAdder();
            final LongAdder count = new LongAdder();
            if (Files.isDirectory(path)) {
                try (final Stream<Path> stream = Files.walk(path)) {
                    stream.forEach(p -> {
                        if (Files.isRegularFile(p)) {
                            count.increment();
                            try {
                                size.add(Files.size(p));
                            } catch (final IOException e) {
                                LOGGER.trace(e::getMessage, e);
                            }
                        }
                    });
                } catch (final IOException e) {
                    LOGGER.trace(e::getMessage, e);
                }
                map.put(entry.getKey(), new StoreStats(count.longValue(), size.longValue()));
            }
        }
        return map;
    }

    public void add(final int order, final String name, final Path directory) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(directory);
        LOGGER.debug("Adding file store {}, name: {}, dir: {}",
                order, name, directory.toAbsolutePath().normalize());
        final Key key = new Key(order, name);
        final Path prevVal = fileStores.put(key, directory);
        if (prevVal == null) {
            registerMetrics(key);
        }
    }

    private void registerMetrics(final Key key) {
        // Note we don't use a CachedGauge as it is more efficient to capture the
        // size and count as we walk the dir tree, else we would have to walk the
        // dir trees twice each. Instead, we use the CachedValue class to hold on
        // to the size/count stats for future calls.

        Metrics.registrationBuilder(getClass())
                .addNamePart(key.name)
                .addNamePart(Metrics.FILE_COUNT)
                .gauge(() ->
                        NullSafe.getOrElse(
                                statsMapUpdater.getValue(),
                                map -> map.get(key),
                                StoreStats::count,
                                0L))
                .register();

        Metrics.registrationBuilder(getClass())
                .addNamePart(key.name)
                .addNamePart(Metrics.SIZE_IN_BYTES)
                .gauge(() ->
                        NullSafe.getOrElse(
                                statsMapUpdater.getValue(),
                                map -> map.get(key),
                                StoreStats::sizeInBytes,
                                0L))
                .register();
    }

    public synchronized String log() {
        final Map<Key, StoreStats> storeStatsMap = statsMapUpdater.getValue();

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
                    sb.append("<tr>");
                    sb.append("<td>");
                    sb.append(key.name);
                    sb.append("</td>");
                    sb.append("<td>");
                    final StoreStats storeStats = storeStatsMap.get(key);

                    if (storeStats != null) {
                        sb.append(storeStats.count);
                    }
                    sb.append("</td>");
                    sb.append("<td>");
                    if (storeStats != null) {
                        sb.append(ModelStringUtil.formatMetricByteSizeString(storeStats.sizeInBytes));
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


    // --------------------------------------------------------------------------------


    private record Key(int order, String name) {

    }


    // --------------------------------------------------------------------------------


    private record StoreStats(long count,
                              long sizeInBytes) {

    }
}
