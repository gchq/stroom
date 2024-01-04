package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.ProxyServices;
import stroom.proxy.repo.RepoDirProvider;
import stroom.util.io.FileName;
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
import java.util.Objects;
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

    private final NumberedDirProvider tempSplittingDirProvider;
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
        aggregatingDir = repoDirProvider.get().resolve(DirNames.PRE_AGGREGATES);
        DirUtil.ensureDirExists(aggregatingDir);

        // Read all the current aggregates and establish the aggregation state.
        initialiseAggregateStateMap();

        // Make splitting dir.
        final Path tempSplittingDir = tempDirProvider.get().resolve(DirNames.PRE_AGGREGATE_SPLITTING);
        DirUtil.ensureDirExists(tempSplittingDir);

        // This is a temporary location and can be cleaned completely on startup.
        if (!FileUtil.deleteContents(tempSplittingDir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(tempSplittingDir));
        }
        tempSplittingDirProvider = new NumberedDirProvider(tempSplittingDir);

        // Get or create the post split data dir.
        stagedSplittingDir = repoDirProvider.get().resolve(DirNames.PRE_AGGREGATE_SPLIT_OUTPUT);
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
                                final ZipEntryGroup zipEntryGroup = ZipEntryGroup.read(line);
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
            final long maxItemsPerAggregate = aggregatorConfig.getMaxItemsPerAggregate();
            final long maxUncompressedByteSize = aggregatorConfig.getMaxUncompressedByteSize();
            long currentAggregateItemCount = aggregateState.itemCount;
            long currentAggregateBytes = aggregateState.totalBytes;
            long partItems = 0;
            long partBytes = 0;
            boolean firstEntry = true;

            // TODO : We could add an option to never split incoming zip files and just allow them to form output
            //  aggregates that always overflow the aggregation bounds before being closed.

            // Determine if we need to split this data into parts.
            final List<Part> parts = new ArrayList<>();
            try (final BufferedReader bufferedReader = Files.newBufferedReader(fileGroup.getEntries())) {
                String line = bufferedReader.readLine();
                while (line != null) {
                    final ZipEntryGroup zipEntryGroup = ZipEntryGroup.read(line);
                    final long totalUncompressedSize = zipEntryGroup.getTotalUncompressedSize();

                    // If the current aggregate has items then we might want to close and start a new one.
                    if (currentAggregateItemCount > 0 &&
                            (currentAggregateItemCount + 1 > maxItemsPerAggregate ||
                                    currentAggregateBytes + totalUncompressedSize > maxUncompressedByteSize)) {
                        if (firstEntry) {
                            // If the first entry immediately causes the current aggregate to exceed the required bounds
                            // then close it and create a new one.

                            // Close the current aggregate.
                            closeAggregate(feedKey, aggregateState);

                            // Create a new aggregate.
                            aggregateState = aggregateStateMap
                                    .computeIfAbsent(feedKey, this::createAggregate);

                        } else {

                            // Split.
                            parts.add(new Part(partItems, partBytes));
                        }

                        // Reset.
                        currentAggregateItemCount = 0;
                        currentAggregateBytes = 0;
                        partItems = 0;
                        partBytes = 0;
                    }

                    // Add to the aggregate.
                    currentAggregateItemCount++;
                    currentAggregateBytes += totalUncompressedSize;
                    partItems++;
                    partBytes += totalUncompressedSize;

                    line = bufferedReader.readLine();
                    firstEntry = false;
                }

                // Add final part.
                parts.add(new Part(partItems, partBytes));
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
                    closeAggregate(feedKey, aggregateState);
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

    private synchronized void closeAggregate(final FeedKey feedKey,
                                             final AggregateState aggregateState) {
        LOGGER.debug(() -> "Closing aggregate: " + FileUtil.getCanonicalPath(aggregateState.aggregateDir));
        destination.accept(aggregateState.aggregateDir);
        aggregateStateMap.remove(feedKey);
        LOGGER.debug(() -> "Closed aggregate: " + FileUtil.getCanonicalPath(aggregateState.aggregateDir));
    }

    private SplitDirSet split(final Path dir, final List<Part> parts) throws IOException {
        final String inputDirName = dir.getFileName().toString();
        final FileGroup fileGroup = new FileGroup(dir);
        // Get a buffer to help us transfer data.
        final byte[] buffer = LocalByteBuffer.get();

        final Path parentDir = tempSplittingDirProvider.get();
        final List<Path> outputDirs = new ArrayList<>();
        try (final ZipArchiveInputStream zipArchiveInputStream =
                new ZipArchiveInputStream(new BufferedInputStream(Files.newInputStream(fileGroup.getZip())))) {
            ZipArchiveEntry entry = zipArchiveInputStream.getNextEntry();
            if (entry == null) {
                throw new RuntimeException("Unexpected empty zip file");
            }

            int partNo = 1;
            for (final Part part : parts) {
                final String outputDirName = inputDirName + "_part_" + partNo++;
                final Path outputDir = parentDir.resolve(outputDirName);
                Files.createDirectory(outputDir);
                outputDirs.add(outputDir);
                final FileGroup outputFileGroup = new FileGroup(outputDir);

                // Write the zip.
                try (final ProxyZipWriter zipWriter = new ProxyZipWriter(outputFileGroup.getZip(), buffer)) {
                    String lastBaseName = null;
                    int count = 0;
                    String baseNameOut = null;
                    boolean add = true;

                    while (entry != null && add) {
                        final FileName fileName = FileName.parse(entry.getName());
                        if (!Objects.equals(fileName.getBaseName(), lastBaseName)) {
                            count++;
                            if (count <= part.items) {
                                baseNameOut = NumericFileNameUtil.create(count);
                                lastBaseName = fileName.getBaseName();
                            } else {
                                add = false;
                            }
                        }

                        if (add) {
                            final String entryName = baseNameOut + "." + fileName.getExtension();
                            zipWriter.writeStream(entryName, zipArchiveInputStream);
                            entry = zipArchiveInputStream.getNextEntry();
                        }
                    }
                }

                // Copy the meta.
                Files.copy(fileGroup.getMeta(), outputFileGroup.getMeta());
            }
        }

        return new SplitDirSet(parentDir, outputDirs);
    }

    private AggregateState createAggregate(final FeedKey feedKey) {
        try {
            // Make a dir name.
            final StringBuilder sb = new StringBuilder();
            if (feedKey.feed() != null) {
                sb.append(DirUtil.makeSafeName(feedKey.feed()));
            }
            sb.append("__");
            if (feedKey.type() != null) {
                sb.append(DirUtil.makeSafeName(feedKey.type()));
            }

            // Get or create the aggregate dir.
            final Path repoDir = repoDirProvider.get();
            final Path aggregatesDir = repoDir.resolve("aggregates");
            final Path aggregateDir = aggregatesDir.resolve(sb.toString());
            LOGGER.debug(() -> "Creating aggregate: " + FileUtil.getCanonicalPath(aggregateDir));

            // Ensure the dir exists.
            Files.createDirectories(aggregateDir);

            LOGGER.debug(() -> "Created aggregate: " + FileUtil.getCanonicalPath(aggregateDir));
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
                closeAggregate(k, v);
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
