/*
 * Copyright 2023 Crown Copyright
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

package stroom.proxy.repo.store;

import stroom.util.concurrent.CachedValue;
import stroom.util.io.FileUtil;
import stroom.util.io.PathWithAttributes;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.metrics.Metrics;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.Predicate;

@Singleton
public class FileStores {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FileStores.class);

    private final Map<Key, Path> fileStores = new ConcurrentHashMap<>();

    private final CachedValue<Map<Key, StoreStats>, Void> statsMapUpdater;
    private final Metrics metrics;

    @Inject
    public FileStores(final Metrics metrics) {
        this.metrics = metrics;
        statsMapUpdater = CachedValue.builder()
                .withMaxCheckIntervalSeconds(30)
                .withoutStateSupplier()
                .withValueSupplier(this::buildStoreState)
                .build();
    }

    private Map<Key, StoreStats> buildStoreState() {
        LOGGER.debug("Capturing store stats");
        final Map<Key, StoreStats> map = new HashMap<>();
        for (final Entry<Key, Path> entry : fileStores.entrySet()) {
            final Key key = entry.getKey();
            final Path path = entry.getValue();
            final LongAdder size = new LongAdder();
            final LongAdder count = new LongAdder();
            if (Files.isDirectory(path)) {
                addRegularFileCountAndSizes(key, path, size, count);
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

        metrics.registrationBuilder(getClass())
                .addNamePart(key.name)
                .addNamePart(Metrics.FILE_COUNT)
                .gauge(() ->
                        getStat(key, StoreStats::count))
                .register();

        metrics.registrationBuilder(getClass())
                .addNamePart(key.name)
                .addNamePart(Metrics.SIZE_IN_BYTES)
                .gauge(() ->
                        getStat(key, StoreStats::sizeInBytes))
                .register();
    }

    private long getStat(final Key key, final Function<StoreStats, Long> statFunc) {
        return NullSafe.getOrElse(
                statsMapUpdater.getValue(),
                map -> map.get(key),
                statFunc,
                0L);
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

    /**
     * This used {@code FileUtil.deepListContents(..).size()}, which builds a {@link java.util.List}
     * of every regular file under the store purely to call {@code size()} on it - the predicate was
     * already doing the real work as a side effect, and the list was discarded. On a store holding a
     * large backlog that is one {@code PathWithAttributes} allocated per file, on a gauge that runs
     * every time the metric is read. Counted in the predicate instead, so nothing is retained.
     * <p>
     * The {@code key} parameter was also unread. Kept, because it is what identifies the store this
     * count belongs to and it is worth having in a failure message; previously it was accepted and
     * ignored, which reads as an oversight rather than a decision.
     * </p>
     */
    private static void addRegularFileCountAndSizes(final Key key,
                                                    final Path path,
                                                    final LongAdder sizeAdder,
                                                    final LongAdder fileCountAdder) {
        final Predicate<PathWithAttributes> isFilePredicate = pathWithAttributes -> {
            final boolean isRegularFile = pathWithAttributes.isRegularFile();
            if (isRegularFile) {
                sizeAdder.add(pathWithAttributes.size());
                fileCountAdder.increment();
            }
            // Always false: the predicate is being used as a visitor, so nothing needs collecting.
            return false;
        };

        try {
            FileUtil.deepListContents(path, true, isFilePredicate);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message(
                    "Could not size file store {} at {}", key, path), e);
        }
    }


    // --------------------------------------------------------------------------------


    private record Key(int order, String name) {

    }


    // --------------------------------------------------------------------------------


    private record StoreStats(long count,
                              long sizeInBytes) {

    }
}
