package stroom.proxy.app.handler;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.handler.ZipEntryGroup.Entry;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.RepoDirProvider;
import stroom.receive.common.ProgressHandler;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
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

    private final Map<FeedKey, AggregateState> aggregateStateMap = new HashMap<>();

    private Consumer<Path> destination;

    @Inject
    public PreAggregator(final CleanupDirQueue deleteDirQueue,
                         final Provider<ProxyConfig> proxyConfigProvider,
                         final TempDirProvider tempDirProvider,
                         final RepoDirProvider repoDirProvider) {
        this.deleteDirQueue = deleteDirQueue;
        this.aggregatorConfig = proxyConfigProvider.get().getAggregatorConfig();
        this.repoDirProvider = repoDirProvider;

        // Get or create the aggregating dir.
        aggregatingDir = repoDirProvider.get().resolve("03_pre_aggregate");
        ensureDirExists(aggregatingDir);

        // Read all the current aggregates and establish the aggregation state.
        initialiseAggregateStateMap();

        // Make splitting dir.
        tempSplittingDir = tempDirProvider.get().resolve("04_splitting");
        ensureDirExists(tempSplittingDir);

        // This is a temporary location and can be cleaned completely on startup.
        if (!FileUtil.deleteContents(tempSplittingDir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(tempSplittingDir));
        }

        // Get or create the post split data dir.
        stagedSplittingDir = repoDirProvider.get().resolve("05_split_output");
        ensureDirExists(stagedSplittingDir);

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

    private void ensureDirExists(final Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (final IOException e) {
            LOGGER.error(() -> "Failed to create " + FileUtil.getCanonicalPath(dir), e);
            throw new UncheckedIOException(e);
        }
    }

    public void addDir(final Path dir) {
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
            boolean firstItem = aggregateState.itemCount == 0;
            long itemCount = 0;

            // TODO : We could add an option to never split incoming zip files and just allow them to form output
            //  aggregates that always overflow the aggregation bounds before being closed.

            // Determine if we need to split this data into parts.
            final List<Part> parts = new ArrayList<>();
            try (final BufferedReader bufferedReader = Files.newBufferedReader(fileGroup.getEntries())) {
                String line = bufferedReader.readLine();
                while (line != null) {
                    itemCount++;
                    final ZipEntryGroup zipEntryGroup = ZipEntryGroupUtil.read(line);
                    final long totalUncompressedSize = zipEntryGroup.getTotalUncompressedSize();
                    if (firstItem ||
                            (remainingItems > 0 && remainingBytes > totalUncompressedSize)) {
                        // Add to the aggregate.
                        remainingItems--;
                        remainingBytes -= totalUncompressedSize;
                        addedItems++;
                        addedBytes += totalUncompressedSize;

                    } else {
                        // Split.
                        parts.add(new Part(itemCount, addedItems, addedBytes));
                        remainingItems = aggregatorConfig.getMaxItemsPerAggregate();
                        remainingBytes = aggregatorConfig.getMaxUncompressedByteSize();
                        addedItems = 0;
                        addedBytes = 0;
                        firstItem = true;
                    }

                    line = bufferedReader.readLine();
                }

                parts.add(new Part(itemCount, addedItems, addedBytes));
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

                // Add first part to the current aggregate if the first part has items.
                if (parts.get(0).item > 1) {
                    // If the first split included some items then add it as the final item to the current aggregate.
                    final Path splitDir = splitStaging.resolve(splitDirSet.children.get(0).getFileName());
                    Files.move(
                            splitDir,
                            aggregateState.aggregateDir.resolve(splitDir.getFileName()),
                            StandardCopyOption.ATOMIC_MOVE);
                }

                // Close the current aggregate.
                closeAggregate(aggregateState.aggregateDir);
                aggregateStateMap.remove(feedKey);

                // Create aggregates from other parts.
                for (int i = 1; i < parts.size() - 1; i++) {
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

    private void closeAggregate(final Path partialAggregateDir) throws IOException {
        destination.accept(partialAggregateDir);
    }

    private SplitDirSet split(final Path dir, final List<Part> splits) throws IOException {
        final FileGroup fileGroup = new FileGroup(dir);
        // Get a buffer to help us transfer data.
        final byte[] buffer = LocalByteBuffer.get();

        final Path parentDir = tempSplittingDir.resolve(UUID.randomUUID().toString());
        Files.createDirectory(parentDir);
        final List<Path> outputDirs = new ArrayList<>();
        try (final ZipInputStream zipInputStream =
                new ZipInputStream(new BufferedInputStream(Files.newInputStream(fileGroup.getZip())))) {
            try (final BufferedReader entryReader = Files.newBufferedReader(fileGroup.getEntries())) {
                for (final Part split : splits) {
                    if (split.item > 1) {
                        int count = 1;
                        final List<ZipEntryGroup> zipEntryGroups = new ArrayList<>();
                        final Path outputDir = parentDir.resolve(UUID.randomUUID().toString());
                        Files.createDirectory(outputDir);
                        outputDirs.add(outputDir);
                        final FileGroup outputFileGroup = new FileGroup(outputDir);

                        // Write the zip.
                        try (final ZipOutputStream zipOutputStream =
                                new ZipOutputStream(
                                        new BufferedOutputStream(Files.newOutputStream(outputFileGroup.getZip())))) {
                            for (int i = 0; i < split.items; i++) {
                                // Add entry.
                                final String entryLine = entryReader.readLine();
                                final ZipEntryGroup zipEntryGroup = ZipEntryGroupUtil.read(entryLine);
                                final String baseNameOut = NumericFileNameUtil.create(count);
                                final ZipEntryGroup.Entry manifestEntry = transferEntry(zipEntryGroup.getManifestEntry(),
                                        zipInputStream,
                                        zipOutputStream,
                                        StroomZipFileType.MANIFEST,
                                        baseNameOut,
                                        buffer);
                                final ZipEntryGroup.Entry metaEntry = transferEntry(zipEntryGroup.getMetaEntry(),
                                        zipInputStream,
                                        zipOutputStream,
                                        StroomZipFileType.META,
                                        baseNameOut,
                                        buffer);
                                final ZipEntryGroup.Entry contextEntry = transferEntry(zipEntryGroup.getContextEntry(),
                                        zipInputStream,
                                        zipOutputStream,
                                        StroomZipFileType.CONTEXT,
                                        baseNameOut,
                                        buffer);
                                final ZipEntryGroup.Entry dataEntry = transferEntry(zipEntryGroup.getDataEntry(),
                                        zipInputStream,
                                        zipOutputStream,
                                        StroomZipFileType.DATA,
                                        baseNameOut,
                                        buffer);

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
        }

        return new SplitDirSet(parentDir, outputDirs);
    }

    private ZipEntryGroup.Entry transferEntry(final ZipEntryGroup.Entry entry,
                                              final ZipInputStream zipInputStream,
                                              final ZipOutputStream zipOutputStream,
                                              final StroomZipFileType stroomZipFileType,
                                              final String baseNameOut,
                                              final byte[] buffer) throws IOException {
        if (entry != null) {
            final ZipEntry zipEntry = zipInputStream.getNextEntry();
            if (zipEntry == null) {
                throw new RuntimeException("Expected entry: " + entry.getName());
            }
            if (!zipEntry.getName().equals(entry.getName())) {
                throw new RuntimeException("Unexpected entry: " + zipEntry.getName());
            }

            final String outEntryName = baseNameOut + stroomZipFileType.getDotExtension();
            zipOutputStream.putNextEntry(new ZipEntry(outEntryName));

            final long bytes = transfer(zipInputStream, zipOutputStream, buffer);

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

    private long transfer(final InputStream in, final OutputStream out, final byte[] buffer) {
        return StreamUtil
                .streamToStream(in,
                        out,
                        buffer,
                        new ProgressHandler("Receiving data"));
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

    private record Part(long item, long items, long bytes) {

    }

    private record SplitDirSet(Path parent, List<Path> children) {

    }
}
