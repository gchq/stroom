package stroom.proxy.app.handler;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.handler.ZipEntryGroup.Entry;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.ProxyServices;
import stroom.proxy.repo.RepoDirProvider;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class PreAggregator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PreAggregator.class);

    private final Path tempSplittingDir;
    private final Path stagedSplittingDir;
    private final CleanupDirQueue deleteDirQueue;
    private final AggregatorConfig aggregatorConfig;
    private final RepoDirProvider repoDirProvider;

    private final Path aggregatingDir;

    private final Map<FeedKey, AggregateState> aggregateStateMap = new ConcurrentHashMap<>();

    private Consumer<Path> destination;

    @Inject
    public PreAggregator(final CleanupDirQueue deleteDirQueue,
                         final Provider<ProxyConfig> proxyConfigProvider,
                         final TempDirProvider tempDirProvider,
                         final RepoDirProvider repoDirProvider,
                         final ProxyServices proxyServices) {
        this.deleteDirQueue = deleteDirQueue;
        this.aggregatorConfig = proxyConfigProvider.get().getAggregatorConfig();
        this.repoDirProvider = repoDirProvider;

        // Get or create the aggregating dir.
        aggregatingDir = repoDirProvider.get().resolve("03_pre_aggregate");
        DirUtil.ensureDirExists(aggregatingDir);

        // Read all the current aggregates and establish the aggregation state.
        initialiseAggregateStateMap();

        // Make splitting dir.
        tempSplittingDir = tempDirProvider.get().resolve("04_splitting");
        DirUtil.ensureDirExists(tempSplittingDir);

        // This is a temporary location and can be cleaned completely on startup.
        if (!FileUtil.deleteContents(tempSplittingDir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(tempSplittingDir));
        }

        // Get or create the post split data dir.
        stagedSplittingDir = repoDirProvider.get().resolve("05_split_output");
        DirUtil.ensureDirExists(stagedSplittingDir);

        // Move any split data from previous proxy usage to the aggregates.
        // We will assume that data has been split appropriately for the current aggregate state.
        try (final Stream<Path> stream = Files.list(stagedSplittingDir)) {
            stream.forEach(splitGroup -> {
                try (final Stream<Path> fileGroupStream = Files.list(splitGroup)) {
                    fileGroupStream.forEach(this::addDir);
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                    throw new UncheckedIOException(e);
                }
            });
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }

        // Periodically close old aggregates.
        proxyServices
                .addFrequencyExecutor(
                        "Close Old Aggregates",
                        () -> this::closeOldAggregates,
                        Duration.ofSeconds(10).toMillis());
    }

    private void initialiseAggregateStateMap() {
        // Read all the current aggregates and establish the aggregation state.
        try (final Stream<Path> stream = Files.list(aggregatingDir)) {
            // Look at each aggregate dir.
            stream.forEach(aggregateDir -> {
                final AggregateState aggregateState = new AggregateState(Instant.now(), aggregateDir);
                final AtomicReference<FeedKey> feedKeyRef = new AtomicReference<>();

                // Now examine each file group to read state.
                try (final Stream<Path> groupStream = Files.list(aggregateDir)) {
                    // Now read the entries.
                    groupStream.forEach(groupDir -> {
                        final FileGroup fileGroup = new FileGroup(groupDir);
                        try (final BufferedReader bufferedReader = Files.newBufferedReader(fileGroup.getEntries())) {
                            String line = bufferedReader.readLine();
                            while (line != null) {
                                final ZipEntryGroup zipEntryGroup = ZipEntryGroupUtil.read(line);
                                final long totalUncompressedSize = zipEntryGroup.getTotalUncompressedSize();
                                aggregateState.itemCount++;
                                aggregateState.totalBytes += totalUncompressedSize;

                                final FeedKey existingFeedKey = feedKeyRef.get();
                                final FeedKey newFeedKey = new FeedKey(
                                        zipEntryGroup.getFeedName(),
                                        zipEntryGroup.getTypeName());
                                if (existingFeedKey != null) {
                                    if (!existingFeedKey.equals(newFeedKey)) {
                                        LOGGER.error("Unexpected feed key mismatch!!!");
                                    }
                                } else {
                                    feedKeyRef.set(newFeedKey);
                                }

                                line = bufferedReader.readLine();
                            }
                        } catch (final IOException e) {
                            LOGGER.error(e::getMessage, e);
                            throw new UncheckedIOException(e);
                        }
                    });
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                    throw new UncheckedIOException(e);
                }

                if (feedKeyRef.get() != null) {
                    aggregateStateMap.put(feedKeyRef.get(), aggregateState);
                }
            });
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    public synchronized void addDir(final Path dir) {
        try {
            final FileGroup fileGroup = new FileGroup(dir);
            final AttributeMap attributeMap = new AttributeMap();
            AttributeMapUtil.read(fileGroup.getMeta(), attributeMap);

            final String feed = attributeMap.get(StandardHeaderArguments.FEED);
            final String type = attributeMap.get(StandardHeaderArguments.TYPE);
            final FeedKey feedKey = new FeedKey(feed, type);

            // TODO : We could use separate threads for each feed key.

            AggregateState aggregateState = aggregateStateMap.computeIfAbsent(feedKey, this::createAggregate);

            // Calculate where we might want to split the incoming data.
            long remainingItems = aggregatorConfig.getMaxItemsPerAggregate() - aggregateState.itemCount;
            long remainingBytes = aggregatorConfig.getMaxUncompressedByteSize() - aggregateState.totalBytes;
            long addedItems = 0;
            long addedBytes = 0;
            boolean firstEntry = true;

            // TODO : We could add an option to never split incoming zip files and just allow them to form output
            //  aggregates that always overflow the aggregation bounds before being closed.

            // Determine if we need to split this data into parts.
            final List<Part> parts = new ArrayList<>();
            try (final BufferedReader bufferedReader = Files.newBufferedReader(fileGroup.getEntries())) {
                String line = bufferedReader.readLine();
                while (line != null) {
                    final ZipEntryGroup zipEntryGroup = ZipEntryGroupUtil.read(line);
                    final long totalUncompressedSize = zipEntryGroup.getTotalUncompressedSize();

                    // If the current aggregate has items then we might want to close and start a new one.
                    if (aggregateState.itemCount > 0 &&
                            (remainingItems == 0 || remainingBytes < totalUncompressedSize)) {
                        if (firstEntry) {
                            // If the first entry immediately causes the current aggregate to exceed the required bounds
                            // then close it and create a new one.

                            // Close the current aggregate.
                            closeAggregate(aggregateState.aggregateDir);
                            aggregateStateMap.remove(feedKey);

                            // Create a new aggregate.
                            aggregateState = aggregateStateMap
                                    .computeIfAbsent(feedKey, this::createAggregate);

                        } else {

                            // Split.
                            parts.add(new Part(addedItems, addedBytes));
                        }

                        // Reset.
                        remainingItems = aggregatorConfig.getMaxItemsPerAggregate();
                        remainingBytes = aggregatorConfig.getMaxUncompressedByteSize();
                        addedItems = 0;
                        addedBytes = 0;
                    }

                    // Add to the aggregate.
                    remainingItems--;
                    remainingBytes -= totalUncompressedSize;
                    addedItems++;
                    addedBytes += totalUncompressedSize;

                    line = bufferedReader.readLine();
                    firstEntry = false;
                }

                parts.add(new Part(addedItems, addedBytes));
            }

            // If there is only one part then just add directly to the aggregate.
            if (parts.size() == 1) {
                // Just add the data.
                Files.move(
                        dir,
                        aggregateState.aggregateDir.resolve(dir.getFileName()),
                        StandardCopyOption.ATOMIC_MOVE);
                Part split = parts.get(0);
                aggregateState.itemCount += split.items;
                aggregateState.totalBytes += split.bytes;

            } else {
                // Split the data.
                final SplitDirSet splitDirSet = split(dir, parts);

                // Prepare destination for staged split data.
                final Path splitStaging = stagedSplittingDir.resolve(splitDirSet.parent.getFileName());

                // Atomically move the temporary split data into the split staging area.
                Files.move(splitDirSet.parent, splitStaging, StandardCopyOption.ATOMIC_MOVE);

                // NOTE : If we get failure here before we delete the source then we will end up duplicating data, but
                // we can't do much about that.

                // Delete source file set as we have split and duplicated.
                deleteDirQueue.add(dir);

                // Create aggregates from parts.
                for (int i = 0; i < parts.size() - 1; i++) {
                    final Path splitDir = splitStaging.resolve(splitDirSet.children.get(i).getFileName());
                    aggregateState = aggregateStateMap
                            .computeIfAbsent(feedKey, this::createAggregate);
                    Files.move(
                            splitDir,
                            aggregateState.aggregateDir.resolve(splitDir.getFileName()),
                            StandardCopyOption.ATOMIC_MOVE);
                    // Close the aggregate.
                    closeAggregate(aggregateState.aggregateDir);
                    aggregateStateMap.remove(feedKey);
                }

                // Add final part as new aggregate.
                final Path splitDir = splitStaging.resolve(splitDirSet.children.get(parts.size() - 1).getFileName());
                Part part = parts.get(parts.size() - 1);
                aggregateState = aggregateStateMap
                        .computeIfAbsent(feedKey, this::createAggregate);
                aggregateState.itemCount += part.items;
                aggregateState.totalBytes += part.bytes;
                Files.move(
                        splitDir,
                        aggregateState.aggregateDir.resolve(splitDir.getFileName()),
                        StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void closeAggregate(final Path partialAggregateDir) {
        destination.accept(partialAggregateDir);
    }

    private SplitDirSet split(final Path dir, final List<Part> parts) throws IOException {
        final FileGroup fileGroup = new FileGroup(dir);
        // Get a buffer to help us transfer data.
        final byte[] buffer = LocalByteBuffer.get();

        final Path parentDir = tempSplittingDir.resolve(UUID.randomUUID().toString());
        Files.createDirectory(parentDir);
        final List<Path> outputDirs = new ArrayList<>();
        try (final ZipArchiveInputStream zipInputStream =
                new ZipArchiveInputStream(new BufferedInputStream(Files.newInputStream(fileGroup.getZip())))) {
            try (final BufferedReader entryReader = Files.newBufferedReader(fileGroup.getEntries())) {
                for (final Part part : parts) {
                    int count = 1;
                    final List<ZipEntryGroup> zipEntryGroups = new ArrayList<>();
                    final Path outputDir = parentDir.resolve(UUID.randomUUID().toString());
                    Files.createDirectory(outputDir);
                    outputDirs.add(outputDir);
                    final FileGroup outputFileGroup = new FileGroup(outputDir);

                    // Write the zip.
                    try (final ZipWriter zipWriter = new ZipWriter(outputFileGroup.getZip(), buffer)) {
                        for (int i = 0; i < part.items; i++) {
                            // Add entry.
                            final String entryLine = entryReader.readLine();
                            final ZipEntryGroup zipEntryGroup = ZipEntryGroupUtil.read(entryLine);
                            final String baseNameOut = NumericFileNameUtil.create(count);
                            final ZipEntryGroup.Entry manifestEntry = transferEntry(zipEntryGroup.getManifestEntry(),
                                    zipInputStream,
                                    zipWriter,
                                    StroomZipFileType.MANIFEST,
                                    baseNameOut);
                            final ZipEntryGroup.Entry metaEntry = transferEntry(zipEntryGroup.getMetaEntry(),
                                    zipInputStream,
                                    zipWriter,
                                    StroomZipFileType.META,
                                    baseNameOut);
                            final ZipEntryGroup.Entry contextEntry = transferEntry(zipEntryGroup.getContextEntry(),
                                    zipInputStream,
                                    zipWriter,
                                    StroomZipFileType.CONTEXT,
                                    baseNameOut);
                            final ZipEntryGroup.Entry dataEntry = transferEntry(zipEntryGroup.getDataEntry(),
                                    zipInputStream,
                                    zipWriter,
                                    StroomZipFileType.DATA,
                                    baseNameOut);

                            final ZipEntryGroup outZipEntryGroup = new ZipEntryGroup(
                                    zipEntryGroup.getFeedName(),
                                    zipEntryGroup.getTypeName(),
                                    manifestEntry,
                                    metaEntry,
                                    contextEntry,
                                    dataEntry);
                            zipEntryGroups.add(outZipEntryGroup);
                        }
                    }

                    // Write the entries.
                    ZipEntryGroupUtil.write(outputFileGroup.getEntries(), zipEntryGroups);

                    // Copy the meta.
                    Files.copy(fileGroup.getMeta(), outputFileGroup.getMeta());
                }
            }
        }

        return new SplitDirSet(parentDir, outputDirs);
    }

    private ZipEntryGroup.Entry transferEntry(final ZipEntryGroup.Entry entry,
                                              final ZipArchiveInputStream zipInputStream,
                                              final ZipWriter zipWriter,
                                              final StroomZipFileType stroomZipFileType,
                                              final String baseNameOut) throws IOException {
        if (entry != null) {
            final ZipArchiveEntry zipEntry = zipInputStream.getNextZipEntry();
            if (zipEntry == null) {
                throw new RuntimeException("Expected entry: " + entry.getName());
            }
            if (!zipEntry.getName().equals(entry.getName())) {
                throw new RuntimeException("Unexpected entry: " + zipEntry.getName());
            }

            final String outEntryName = baseNameOut + stroomZipFileType.getDotExtension();
            final long bytes = zipWriter.writeStream(outEntryName, zipInputStream);
            return new Entry(outEntryName, bytes);
        }
        return null;
    }

    private AggregateState createAggregate(final FeedKey feedKey) {
        try {
            // Make a dir name.
            final StringBuilder sb = new StringBuilder();
            if (feedKey.feed() != null) {
                sb.append(feedKey.feed().replaceAll("[^a-zA-Z0-9_-]", "_"));
            }
            sb.append("__");
            if (feedKey.type() != null) {
                sb.append(feedKey.type().replaceAll("[^a-zA-Z0-9_-]", "_"));
            }

            // Get or create the aggregate dir.
            final Path repoDir = repoDirProvider.get();
            final Path aggregatesDir = repoDir.resolve("aggregates");
            final Path aggregateDir = aggregatesDir.resolve(sb.toString());
            // Ensure the dir exists.
            Files.createDirectories(aggregateDir);

            return new AggregateState(Instant.now(), aggregateDir);

        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    private synchronized void closeOldAggregates() {
        aggregateStateMap.forEach((k, v) -> {
            final Instant createTime = v.createTime;
            final Instant aggregateAfter = createTime.plus(aggregatorConfig.getAggregationFrequency().getDuration());
            if (aggregateAfter.isBefore(Instant.now())) {
                // Close the current aggregate.
                closeAggregate(v.aggregateDir);
                aggregateStateMap.remove(k);
            }
        });
    }

    public void setDestination(final Consumer<Path> destination) {
        this.destination = destination;
    }

    private static class AggregateState {

        final Instant createTime;
        final Path aggregateDir;
        long itemCount;
        long totalBytes;

        public AggregateState(final Instant createTime, final Path aggregateDir) {
            this.createTime = createTime;
            this.aggregateDir = aggregateDir;
        }
    }

    private record Part(long items, long bytes) {

    }

    private record SplitDirSet(Path parent, List<Path> children) {

    }
}
