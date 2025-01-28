package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.DataDirProvider;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.ProxyServices;
import stroom.util.io.FileName;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.metrics.Metrics;

import com.codahale.metrics.Histogram;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Singleton
public class PreAggregator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PreAggregator.class);
    public static final String AGGREGATE_NAME_PART = "aggregate";

    private final NumberedDirProvider tempSplittingDirProvider;
    private final Path stagedSplittingDir;
    private final CleanupDirQueue deleteDirQueue;
    private final Provider<AggregatorConfig> aggregatorConfigProvider;
    private final DataDirProvider dataDirProvider;
    private final Metrics metrics;

    private final Path aggregatingDir;

    private final Map<FeedKey, AggregateState> aggregateStateMap = new ConcurrentHashMap<>();

    private final Histogram aggregateItemCountHistogram;
    private final Histogram aggregateByteSizeHistogram;
    private final Histogram aggregateAgeHistogram;

    private Consumer<Path> destination;

    @Inject
    public PreAggregator(final CleanupDirQueue deleteDirQueue,
                         final DataDirProvider dataDirProvider,
                         final ProxyServices proxyServices,
                         final Provider<AggregatorConfig> aggregatorConfigProvider,
                         final Metrics metrics) {
        this.deleteDirQueue = deleteDirQueue;
        this.aggregatorConfigProvider = aggregatorConfigProvider;
        this.dataDirProvider = dataDirProvider;

        // Get or create the aggregating dir.
        aggregatingDir = dataDirProvider.get().resolve(DirNames.PRE_AGGREGATES);
        this.metrics = metrics;
        DirUtil.ensureDirExists(aggregatingDir);

        // Read all the current aggregates and establish the aggregation state.
        initialiseAggregateStateMap();

        // Make splitting dir.
        final Path tempSplittingDir = dataDirProvider.get().resolve(DirNames.PRE_AGGREGATE_SPLITTING);
        DirUtil.ensureDirExists(tempSplittingDir);

        // This is a temporary location and can be cleaned completely on startup.
        if (!FileUtil.deleteContents(tempSplittingDir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(tempSplittingDir));
        }
        tempSplittingDirProvider = new NumberedDirProvider(tempSplittingDir);

        // Get or create the post split data dir.
        stagedSplittingDir = dataDirProvider.get().resolve(DirNames.PRE_AGGREGATE_SPLIT_OUTPUT);
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

        aggregateItemCountHistogram = metrics.registrationBuilder(getClass())
                .addNamePart(AGGREGATE_NAME_PART)
                .addNamePart(Metrics.COUNT)
                .histogram()
                .createAndRegister();
        aggregateByteSizeHistogram = metrics.registrationBuilder(getClass())
                .addNamePart(AGGREGATE_NAME_PART)
                .addNamePart(Metrics.SIZE_IN_BYTES)
                .histogram()
                .createAndRegister();
        aggregateAgeHistogram = metrics.registrationBuilder(getClass())
                .addNamePart(AGGREGATE_NAME_PART)
                .addNamePart(Metrics.AGE_MS)
                .histogram()
                .createAndRegister();

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
                final AggregateState aggregateState = new AggregateState(
                        aggregatorConfigProvider.get(), aggregateDir);
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
            final AggregatorConfig aggregatorConfig = aggregatorConfigProvider.get();
            final FileGroup fileGroup = new FileGroup(dir);
            final AttributeMap attributeMap = new AttributeMap();
            AttributeMapUtil.read(fileGroup.getMeta(), attributeMap);

            final String feed = attributeMap.get(StandardHeaderArguments.FEED);
            final String type = attributeMap.get(StandardHeaderArguments.TYPE);
            final FeedKey feedKey = new FeedKey(feed, type);

            // TODO : We could use separate threads for each feed key.

            // Calculate where we might want to split the incoming data.
            final List<Part> parts;
            if (aggregatorConfig.isSplitSources()) {
                parts = calculateSplitParts(feedKey, fileGroup, aggregatorConfig);
            } else {
                parts = calculateOverflowingParts(fileGroup);
            }

            // If there is only one part then just add directly to the aggregate.
            if (parts.size() == 1) {
                // Just add the single part to the current aggregate.
                addPartToAggregate(feedKey, dir, parts.get(0));

            } else {
                // Split the data.
                final PartDirs partDirSet = split(dir, parts);
                final List<PartDir> partDirs = partDirSet.children();

                // Assert that we have created matching splits.
                if (parts.size() != partDirs.size()) {
                    throw new RuntimeException("Unexpected split dir count");
                }

                // Prepare destination for staged split data.
                final Path splitStaging = stagedSplittingDir.resolve(partDirSet.parent.getFileName());

                // Atomically move the temporary split data into the split staging area.
                Files.move(partDirSet.parent, splitStaging, StandardCopyOption.ATOMIC_MOVE);

                // NOTE : If we get failure here before we delete the source then we will end up duplicating data, but
                // we can't do much about that.

                // Delete source file set as we have split and duplicated.
                deleteDirQueue.add(dir);

                // Create complete aggregates from all parts except the last one.
                for (int i = 0; i < partDirs.size() - 1; i++) {
                    final PartDir partDir = partDirs.get(i);
                    final Path splitDir = splitStaging.resolve(partDir.dir.getFileName());
                    final AggregateState aggregateState = addPartToAggregate(feedKey, splitDir, partDir.part);

                    // Close the aggregate.
                    closeAggregate(feedKey, aggregateState);
                }

                // Add final part as new aggregate.
                final PartDir partDir = partDirs.get(partDirs.size() - 1);
                final Path splitDir = splitStaging.resolve(partDir.dir.getFileName());
                addPartToAggregate(feedKey, splitDir, partDir.part);
            }

            // If we have an aggregate we can close now then do so.
            final AggregateState aggregateState = aggregateStateMap
                    .computeIfAbsent(feedKey, this::createAggregate);
            if (aggregateState.isTooBig()) {
                closeAggregate(feedKey, aggregateState);
            }
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    /**
     * Add a part to the current aggregate by moving it's source dir to the aggregate output dir and incrementing the
     * item count and total bytes for the aggregate.
     *
     * @param feedKey The feed key of the aggregate to add to.
     * @param dir     The source dir to add.
     * @param part    The part details associated with the source.
     * @return The updated aggregate state.
     * @throws IOException Could be thrown when moving the source dir to the aggregate.
     */
    private AggregateState addPartToAggregate(final FeedKey feedKey,
                                              final Path dir,
                                              final Part part) throws IOException {
        final AggregateState aggregateState = aggregateStateMap
                .computeIfAbsent(feedKey, this::createAggregate);
        Files.move(
                dir,
                aggregateState.aggregateDir.resolve(dir.getFileName()),
                StandardCopyOption.ATOMIC_MOVE);
        aggregateState.add(part);
        return aggregateState;
    }

    /**
     * Calculate the number of logical parts the source zip will need to be split into in order to fit output aggregates
     * without them exceeding the size and item count constraints.
     *
     * @param feedKey          The feed
     * @param fileGroup        the file group to examine.
     * @param aggregatorConfig
     * @return A list of parts to split the zip data by.
     * @throws IOException Could be throws when reading entries.
     */
    private List<Part> calculateSplitParts(final FeedKey feedKey,
                                           final FileGroup fileGroup,
                                           final AggregatorConfig aggregatorConfig) throws IOException {
        // Determine if we need to split this data into parts.
        final List<Part> parts = new ArrayList<>();
        AggregateState aggregateState = aggregateStateMap.computeIfAbsent(feedKey, this::createAggregate);

        // Calculate where we might want to split the incoming data.
        final long maxItemsPerAggregate = aggregatorConfig.getMaxItemsPerAggregate();
        final long maxUncompressedByteSize = aggregatorConfig.getMaxUncompressedByteSize();
        long currentAggregateItemCount = aggregateState.itemCount;
        long currentAggregateBytes = aggregateState.totalBytes;
        long partItems = 0;
        long partBytes = 0;
        boolean firstEntry = true;

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

        return parts;
    }

    /**
     * Just get a single part for the entire file group.
     *
     * @param fileGroup The file group to get the zip item count and total uncompressed size from.
     * @return A single part to add to teh current aggregate.
     * @throws IOException Could be throws when reading entries.
     */
    private List<Part> calculateOverflowingParts(final FileGroup fileGroup) throws IOException {
        long partItems = 0;
        long partBytes = 0;
        try (final BufferedReader bufferedReader = Files.newBufferedReader(fileGroup.getEntries())) {
            String line = bufferedReader.readLine();
            while (line != null) {
                final ZipEntryGroup zipEntryGroup = ZipEntryGroup.read(line);
                final long totalUncompressedSize = zipEntryGroup.getTotalUncompressedSize();
                partItems++;
                partBytes += totalUncompressedSize;
                line = bufferedReader.readLine();
            }
        }
        return Collections.singletonList(new Part(partItems, partBytes));
    }

    private synchronized void closeAggregate(final FeedKey feedKey,
                                             final AggregateState aggregateState) {
        LOGGER.debug(() -> "Closing aggregate: " + FileUtil.getCanonicalPath(aggregateState.aggregateDir));
        destination.accept(aggregateState.aggregateDir);
        aggregateStateMap.remove(feedKey);
        captureAggregateMetrics(aggregateState);
        LOGGER.debug(() -> "Closed aggregate: " + FileUtil.getCanonicalPath(aggregateState.aggregateDir));
    }

    private void captureAggregateMetrics(final AggregateState aggregateState) {
        try {
            aggregateItemCountHistogram.update(aggregateState.itemCount);
            aggregateByteSizeHistogram.update(aggregateState.totalBytes);
            aggregateAgeHistogram.update(aggregateState.getAge().toMillis());
        } catch (Exception e) {
            LOGGER.error("Error capturing aggregate stats: {}", LogUtil.exceptionMessage(e), e);
        }
    }

    private PartDirs split(final Path dir, final List<Part> parts) throws IOException {
        final String inputDirName = dir.getFileName().toString();
        final FileGroup fileGroup = new FileGroup(dir);
        // Get a buffer to help us transfer data.
        final byte[] buffer = LocalByteBuffer.get();

        final Path parentDir = tempSplittingDirProvider.get();
        final List<PartDir> partDirs = new ArrayList<>();
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
                partDirs.add(new PartDir(part, outputDir));
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

        return new PartDirs(parentDir, partDirs);
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
            final Path aggregateDir = aggregatingDir.resolve(sb.toString());
            LOGGER.debug(() -> "Creating aggregate: " + FileUtil.getCanonicalPath(aggregateDir));

            // Ensure the dir exists.
            Files.createDirectories(aggregateDir);

            LOGGER.debug(() -> "Created aggregate: " + FileUtil.getCanonicalPath(aggregateDir));
            return new AggregateState(aggregatorConfigProvider.get(), aggregateDir);

        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    private synchronized void closeOldAggregates() {
        aggregateStateMap.forEach((feedKey, aggregateState) -> {
            if (aggregateState.isTooOld()) {
                // Close the current aggregate.
                closeAggregate(feedKey, aggregateState);
            }
        });
    }

    public void setDestination(final Consumer<Path> destination) {
        this.destination = destination;
    }


    // --------------------------------------------------------------------------------


    private static class AggregateState {

        final Instant createTime;
        final Instant aggregateAfter;
        final long maxItemsPerAggregate;
        final long maxUncompressedByteSize;
        final Path aggregateDir;
        long itemCount;
        long totalBytes;

        public AggregateState(final AggregatorConfig aggregatorConfig,
                              final Path aggregateDir) {
            this.createTime = Instant.now();
            this.aggregateAfter = createTime.plus(aggregatorConfig.getAggregationFrequency().getDuration());
            this.maxItemsPerAggregate = aggregatorConfig.getMaxItemsPerAggregate();
            this.maxUncompressedByteSize = aggregatorConfig.getMaxUncompressedByteSize();
            this.aggregateDir = aggregateDir;
        }

        void add(final Part part) {
            itemCount += part.items;
            totalBytes += part.bytes;
        }

        /**
         * @return True if the aggregate's agg is greater than the configured aggregationFrequency
         */
        boolean isTooOld() {
            return Instant.now().isAfter(aggregateAfter);
        }

        boolean isTooBig() {
            return itemCount >= maxItemsPerAggregate
                   || totalBytes >= maxUncompressedByteSize;
        }

        /**
         * @return Current age of the aggregate, i.e. time between its creation time and now
         */
        Duration getAge() {
            return Duration.between(createTime, Instant.now());
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * Record of a part items and total byte size.
     *
     * @param items The number of items.
     * @param bytes The total byte size of the part.
     */
    record Part(long items, long bytes) {

        static Part ZERO = new Part(0, 0);

        Part addItem(final long bytes) {
            return new Part(
                    this.items + 1,
                    this.bytes + bytes);
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * Associate a dir with a part.
     *
     * @param part The part.
     * @param dir  The associated dir that the part describes.
     */
    private record PartDir(Part part, Path dir) {

    }


    // --------------------------------------------------------------------------------


    /**
     * Output of a split operation.
     *
     * @param parent   The parent dir of the split output.
     * @param children The child part dirs of the split output.
     */
    private record PartDirs(Path parent, List<PartDir> children) {

    }
}
