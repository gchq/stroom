package stroom.proxy.repo.store;

import stroom.util.io.FileUtil;
import stroom.util.io.PathWithAttributes;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;

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
            final Key key = entry.getKey();
            final Path path = entry.getValue();
            if (Files.isDirectory(path)) {
                addRegularFileCountAndSizes(key, path, sizes, fileCounts);
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
                    sb.append("<tr>");
                    sb.append("<td>");
                    sb.append(key.name);
                    sb.append("</td>");
                    sb.append("<td>");
                    final Long count = fileCounts.get(key);
                    if (count != null) {
                        sb.append(count);
                    }
                    sb.append("</td>");
                    sb.append("<td>");
                    final Long size = sizes.get(key);
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

    private static void addRegularFileCountAndSizes(final Key key,
                                                    final Path path,
                                                    final Map<Key, Long> sizes,
                                                    final Map<Key, Long> fileCounts) {
        final LongAdder totalSize = new LongAdder();
        final Predicate<PathWithAttributes> isFilePredicate = pathWithAttributes -> {
            final boolean isRegularFile = pathWithAttributes.isRegularFile();
            if (isRegularFile) {
                totalSize.add(pathWithAttributes.size());
            }
            return isRegularFile;
        };

        final long fileCount = FileUtil.deepListContents(path, true, isFilePredicate).size();
        sizes.put(key, totalSize.sum());
        fileCounts.put(key, fileCount);
    }


    // --------------------------------------------------------------------------------


    private record Key(int order, String name) {

    }
}
