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
import stroom.util.shared.NullSafe;
import stroom.util.string.StringIdUtil;

import com.codahale.metrics.Histogram;
import com.google.common.util.concurrent.Striped;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Singleton
public class PreAggregator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PreAggregator.class);
    public static final String AGGREGATE_NAME_PART = "aggregate";

    // TODO How many stripes we have is open to question as we are ultimately IO bound
    //  when adding all the parts
    private static final int FEED_KEY_LOCK_STRIPES = 32;

    private final NumberedDirProvider tempSplittingDirProvider;
    private final Path stagedSplittingDir;
    private final CleanupDirQueue deleteDirQueue;
    private final Provider<AggregatorConfig> aggregatorConfigProvider;
    private final Metrics metrics;

    private final Path aggregatingDir;
    private final Map<FeedKey, AggregateState> aggregateStateMap = new ConcurrentHashMap<>();
    private final Striped<Lock> feedKeyLock = Striped.lock(FEED_KEY_LOCK_STRIPES);

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
        this.metrics = metrics;

        // Get or create the aggregating dir.
        aggregatingDir = dataDirProvider.get().resolve(DirNames.PRE_AGGREGATES);
        LOGGER.info("Initialising PreAggregator with aggregateDir: {}", aggregatingDir);
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
        final AtomicInteger movedSplitCount = new AtomicInteger();
        final AtomicInteger delCount = new AtomicInteger();
        try (final Stream<Path> stream = Files.list(stagedSplittingDir)) {
            stream.forEach(splitGroup -> {
                final AtomicInteger splitGroupItemCount = new AtomicInteger();
                try (final Stream<Path> fileGroupStream = Files.list(splitGroup)) {
                    fileGroupStream.forEach(dir -> {
                        splitGroupItemCount.incrementAndGet();
                        // No need for locking as we are a single thread as this is a singleton
                        addDirWithoutLocking(dir);
                        movedSplitCount.incrementAndGet();
                    });
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                    throw new UncheckedIOException(e);
                }
                if (splitGroupItemCount.get() == 0) {
                    deleteEmptyDir(splitGroup, delCount::incrementAndGet);
                }
            });
            if (delCount.get() > 0) {
                LOGGER.info("Deleted {} empty directories in {}", delCount, stagedSplittingDir);
            }
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
        LOGGER.info("Found {} existing pre-aggregate splits", movedSplitCount);

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
        // This need to be started at the end of the ctor, so we know that everything above can
        // run on the assumption that it is the only thread in play
        proxyServices
                .addFrequencyExecutor(
                        "Close Old Aggregates",
                        () -> this::closeOldAggregates,
                        Duration.ofSeconds(10).toMillis());
    }

    private void initialiseAggregateStateMap() {
        LOGGER.info("Initialising the state of existing pre-aggregates");
        // Read all the current aggregates and establish the aggregation state.
        final AggregatorConfig aggregatorConfig = aggregatorConfigProvider.get();
        try (final Stream<Path> stream = Files.list(aggregatingDir)) {
            // Look at each aggregate dir.
            stream.forEach(aggregateDir -> {
                final AggregateState aggregateState = new AggregateState(aggregatorConfig, aggregateDir);
                final AtomicReference<FeedKey> feedKeyRef = new AtomicReference<>();

                // Now examine each file group to read state.
                try (final Stream<Path> groupStream = Files.list(aggregateDir)) {
                    // Now read the entries.
                    groupStream.forEach(groupDir -> {
                        final FileGroup fileGroup = new FileGroup(groupDir);
                        final Path entriesFile = fileGroup.getEntries();
                        aggregateState.partCount++;
                        try (final BufferedReader bufferedReader = Files.newBufferedReader(entriesFile)) {
                            String line = bufferedReader.readLine();
                            while (line != null) {
                                final ZipEntryGroup zipEntryGroup = ZipEntryGroup.read(line);
                                final long totalUncompressedSize = zipEntryGroup.getTotalUncompressedSize();
                                aggregateState.addItem(totalUncompressedSize);

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

                LOGGER.debug("Initialised aggregateState {}", aggregateState);
                NullSafe.consume(feedKeyRef.get(), feedKey ->
                        aggregateStateMap.put(feedKey, aggregateState));
            });
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
        LOGGER.info(() ->
                LogUtil.message("Completed initialisation of {} pre-aggregates", aggregateStateMap.size()));
    }

    private FeedKey readFeedKeyFromMeta(final FileGroup fileGroup) throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.read(fileGroup.getMeta(), attributeMap);
        final String feed = attributeMap.get(StandardHeaderArguments.FEED);
        final String type = attributeMap.get(StandardHeaderArguments.TYPE);
        return new FeedKey(feed, type);
    }

    public void addDir(final Path dir) {
        LOGGER.trace("addDir '{}'", dir);
        try {
            final FileGroup fileGroup = new FileGroup(dir);
            final FeedKey feedKey = readFeedKeyFromMeta(fileGroup);

            // Striped lock to ensure calls with the same feedKey block each other, but
            // other feedKeys may not be blocked (depending on stripe count)
            final Lock lock = feedKeyLock.get(feedKey);
            lock.lock();
            try {
                addDir(dir, fileGroup, feedKey);
            } finally {
                lock.unlock();
            }
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void addDirWithoutLocking(final Path dir) {
        LOGGER.trace("addDirFromSplit '{}'", dir);
        try {
            // This is only called by the singleton ctor so no feedKey locking needed
            final FileGroup fileGroup = new FileGroup(dir);
            final FeedKey feedKey = readFeedKeyFromMeta(fileGroup);
            addDir(dir, fileGroup, feedKey);
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void addDir(final Path dir, final FileGroup fileGroup, final FeedKey feedKey)
            throws IOException {

        LOGGER.trace("addDir() - dir: '{}', feedKey: {}", dir, feedKey);
        final AggregatorConfig aggregatorConfig = aggregatorConfigProvider.get();

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
            LOGGER.trace("Single part, dir: {}", dir);
            addPartToAggregate(feedKey, dir, parts.get(0), aggregatorConfig);
        } else {
            LOGGER.trace(() -> LogUtil.message("Multiple parts, dir: {}, count: {}", dir, parts.size()));
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
            final Path source = partDirSet.parent;
            LOGGER.trace("Moving {} => {}", source, splitStaging);
            Files.move(source, splitStaging, StandardCopyOption.ATOMIC_MOVE);

            // NOTE : If we get failure here before we delete the source then we will end up duplicating data, but
            // we can't do much about that.

            // Delete source file set as we have split and duplicated.
            LOGGER.debug("Sending {} to deleteDirQueue", dir);
            deleteDirQueue.add(dir);

            // Create complete aggregates from all parts except the last one.
            for (int i = 0; i < partDirs.size() - 1; i++) {
                final PartDir partDir = partDirs.get(i);
                final Path splitDir = splitStaging.resolve(partDir.dir.getFileName());
                final AggregateState aggregateState = addPartToAggregate(
                        feedKey, splitDir, partDir.part, aggregatorConfig);
                LOGGER.trace("Split idx: {}, partDir: {}, splitDir: {}, aggregateState: {}",
                        i, partDir, splitDir, aggregateState);

                // Close the aggregate.
                closeAggregate(feedKey, aggregateState);
            }

            // Add final part as new aggregate.
            final PartDir partDir = partDirs.get(partDirs.size() - 1);
            final Path splitDir = splitStaging.resolve(partDir.dir.getFileName());
            final AggregateState aggregateState = addPartToAggregate(
                    feedKey, splitDir, partDir.part, aggregatorConfig);
            LOGGER.trace("Final split, partDir: {}, splitDir: {}, aggregateState: {}",
                    partDir, splitDir, aggregateState);

            // We have moved all the splits, so get rid of the empty parent
            deleteEmptyDir(splitStaging, null);
        }

        // If we have an aggregate we can close now then do so.
        final AggregateState aggregateState = aggregateStateMap.computeIfAbsent(feedKey, k ->
                createAggregate(k, aggregatorConfig));
        if (aggregateState.isReadyToClose()) {
            closeAggregate(feedKey, aggregateState);
        }
    }

    private void deleteEmptyDir(final Path dir, final Runnable onSuccessfulDelete) {
        Objects.requireNonNull(dir);
        try {
            Files.delete(dir);
            LOGGER.debug("Deleted empty dir {}", dir);
            NullSafe.run(onSuccessfulDelete);
        } catch (IOException e) {
            LOGGER.error("Unable to delete empty dir {}", dir, e);
        }
    }

    /**
     * Add a part to the current aggregate by moving its source dir to the aggregate
     * output dir and incrementing the item count and total bytes for the aggregate.
     * <p>
     * MUST be called under the feedKeyLock.
     * </p>
     *
     * @param feedKey The feed key of the aggregate to add to.
     * @param dir     The source dir to add.
     * @param part    The part details associated with the source.
     * @return The updated aggregate state.
     * @throws IOException Could be thrown when moving the source dir to the aggregate.
     */
    private AggregateState addPartToAggregate(final FeedKey feedKey,
                                              final Path dir,
                                              final Part part,
                                              final AggregatorConfig aggregatorConfig) throws IOException {
        final AggregateState aggregateState = aggregateStateMap
                .computeIfAbsent(feedKey, k -> createAggregate(k, aggregatorConfig));
        final long newPartCount = aggregateState.partCount + 1;
        final Path destDir = aggregateState.aggregateDir.resolve(StringIdUtil.idToString(newPartCount));
        Files.move(dir, destDir, StandardCopyOption.ATOMIC_MOVE);
        aggregateState.addPart(part);
        LOGGER.debug(() -> LogUtil.message("addPartToAggregate() - feedKey: {}, dir: {}, part: {}, " +
                                           "destDir: {}, itemCount: {}, totalBytes: {}",
                feedKey, dir, part, destDir, aggregateState.itemCount, aggregateState.totalBytes));
        return aggregateState;
    }

    /**
     * Calculate the number of logical parts the source zip will need to be split into
     * in order to fit output aggregates without them exceeding the size and item
     * count constraints.
     *
     * <p>
     * MUST be called under feedKeyLock.
     * </p>
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
        AggregateState aggregateState = aggregateStateMap.computeIfAbsent(feedKey,
                k -> createAggregate(k, aggregatorConfig));

        // Calculate where we might want to split the incoming data.
        final long maxItemsPerAggregate = aggregatorConfig.getMaxItemsPerAggregate();
        final long maxUncompressedByteSize = aggregatorConfig.getMaxUncompressedByteSize();
        long currentAggregateItemCount = aggregateState.itemCount;
        long currentAggregateBytes = aggregateState.totalBytes;
        long partItems = 0;
        long partBytes = 0;
        final List<ZipEntryGroup> partEntries = new ArrayList<>();
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
                                .computeIfAbsent(feedKey, k -> createAggregate(k, aggregatorConfig));
                    } else {
                        // Split. Copy the list as the source is about to be cleared
                        final Part part = new Part(partItems, partBytes, List.copyOf(partEntries));
                        parts.add(part);
                    }

                    // Reset.
                    currentAggregateItemCount = 0;
                    currentAggregateBytes = 0;
                    partItems = 0;
                    partBytes = 0;
                    partEntries.clear();
                }

                // Add to the aggregate.
                currentAggregateItemCount++;
                currentAggregateBytes += totalUncompressedSize;
                partItems++;
                partBytes += totalUncompressedSize;
                partEntries.add(zipEntryGroup);

                line = bufferedReader.readLine();
                firstEntry = false;
            }
            // Add final part.
            parts.add(new Part(partItems, partBytes, List.copyOf(partEntries)));
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
        final List<ZipEntryGroup> partEntries = new ArrayList<>();
        try (final BufferedReader bufferedReader = Files.newBufferedReader(fileGroup.getEntries())) {
            String line = bufferedReader.readLine();
            while (line != null) {
                final ZipEntryGroup zipEntryGroup = ZipEntryGroup.read(line);
                final long totalUncompressedSize = zipEntryGroup.getTotalUncompressedSize();
                partItems++;
                partBytes += totalUncompressedSize;
                partEntries.add(zipEntryGroup);
                line = bufferedReader.readLine();
            }
        }
        return Collections.singletonList(new Part(partItems, partBytes, List.copyOf(partEntries)));
    }

    private void closeAggregate(final FeedKey feedKey,
                                final AggregateState aggregateState) {
        LOGGER.debug("Closing aggregate: {}", aggregateState);
        final Lock lock = feedKeyLock.get(feedKey);
        lock.lock();
        try {
            destination.accept(aggregateState.aggregateDir);
            aggregateStateMap.remove(feedKey);
            captureAggregateMetrics(aggregateState);
            LOGGER.debug("Closed aggregate: {}", aggregateState);
        } finally {
            lock.unlock();
        }
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
        LOGGER.debug(() -> LogUtil.message("split() - dir: {}, parts count: {}", dir, parts.size()));
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
                LOGGER.trace("Creating outputDir: {}", outputDir);
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
                // Write the entries for this split part
                final Path outputEntriesFile = outputFileGroup.getEntries();
                try (final Writer entryWriter = Files.newBufferedWriter(outputEntriesFile)) {
                    for (final ZipEntryGroup outputZipEntryGroup : part.zipEntryGroups) {
                        outputZipEntryGroup.write(entryWriter);
                    }
                }
                // Copy the meta from the split source
                LOGGER.trace(() ->
                        LogUtil.message("Copy {} => {}", fileGroup.getMeta(), outputFileGroup.getMeta()));
                Files.copy(fileGroup.getMeta(), outputFileGroup.getMeta());
            }
        }

        return new PartDirs(parentDir, partDirs);
    }

    private AggregateState createAggregate(final FeedKey feedKey,
                                           final AggregatorConfig aggregatorConfig) {
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
            return new AggregateState(aggregatorConfig, aggregateDir);

        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Synchronised to stop closeOldAggregates being called concurrently (which
     * shouldn't really happen with the frequency executor), but
     * closeAggregate() will still happen under the striped lock to protect it
     * from other threads working on aggregates
     */
    private synchronized void closeOldAggregates() {
        final AtomicInteger count = new AtomicInteger();
        aggregateStateMap.forEach((feedKey, aggregateState) -> {
            if (aggregateState.isAggregateTooOld()) {
                // Close the current aggregate.
                count.incrementAndGet();
                closeAggregate(feedKey, aggregateState);
            }
        });
        if (LOGGER.isDebugEnabled()) {
            if (count.get() > 0) {
                LOGGER.debug("closeOldAggregates() - closed {} old aggregates", count);
            }
        }
    }

    public void setDestination(final Consumer<Path> destination) {
        this.destination = destination;
    }


    // --------------------------------------------------------------------------------


    /**
     * NOT thread safe
     */
    private static class AggregateState {

        private final Instant createTime;
        private final Instant aggregateAfter;
        // Bake the config in when the aggregate is started
        private final AggregatorConfig aggregatorConfig;
        private final Path aggregateDir;
        private long partCount;
        private long itemCount;
        private long totalBytes;

        private AggregateState(final AggregatorConfig aggregatorConfig,
                               final Path aggregateDir) {
            this(aggregatorConfig, Instant.now(), aggregateDir);
        }

        private AggregateState(final AggregatorConfig aggregatorConfig,
                               final Instant createTime,
                               final Path aggregateDir) {
            this.createTime = createTime;
            this.aggregateDir = aggregateDir;
            this.aggregatorConfig = aggregatorConfig;
            this.aggregateAfter = createTime.plus(aggregatorConfig.getAggregationFrequency());
        }

        private void addItem(final long uncompressedSize) {
            itemCount++;
            totalBytes += uncompressedSize;
        }

        private void addPart(final Part part) {
            partCount++;
            itemCount += part.items;
            totalBytes += part.bytes;
        }

        private boolean isAggregateTooOld() {
            final boolean isTooOld = Instant.now().isAfter(aggregateAfter);
            LOGGER.trace("createTime {}, aggregateAfter: {}, isTooOld: {}", createTime, aggregateAfter, isTooOld);
            return isTooOld;
        }

        private boolean isReadyToClose() {
            final boolean isReadyToClose;
            if (itemCount >= aggregatorConfig.getMaxItemsPerAggregate()) {
                LOGGER.trace(() -> LogUtil.message("Item count {} reached/passed limit {}, aggregateDir: {}",
                        itemCount, aggregatorConfig.getMaxItemsPerAggregate(), aggregateDir));
                isReadyToClose = true;
            } else if (totalBytes >= aggregatorConfig.getMaxUncompressedByteSize()) {
                LOGGER.trace(() -> LogUtil.message("Aggregate size {} reached/passed limit {}, aggregateDir: {}",
                        itemCount, aggregatorConfig.getMaxItemsPerAggregate(), aggregateDir));
                isReadyToClose = true;
            } else if (isAggregateTooOld()) {
                isReadyToClose = true;
            } else {
                LOGGER.trace("Not ready to close, itemCount: {}, totalBytes: {}, createTime: {}, aggregateDir: {}",
                        itemCount, totalBytes, createTime, aggregateDir);
                isReadyToClose = false;
            }
            return isReadyToClose;
        }

        /**
         * @return Current age of the aggregate, i.e. time between its creation time and now
         */
        private Duration getAge() {
            return Duration.between(createTime, Instant.now());
        }

        @Override
        public String toString() {
            return "AggregateState{" +
                   "createTime=" + createTime +
                   ", aggregateDir=" + aggregateDir +
                   ", partCount=" + partCount +
                   ", itemCount=" + itemCount +
                   ", totalBytes=" + totalBytes +
                   '}';
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * Record of a part items and total byte size.
     *
     * @param items The number of items.
     * @param bytes The total byte size of the part.
     */
    record Part(long items, long bytes, List<ZipEntryGroup> zipEntryGroups) {

        private static final Part EMPTY = new Part(0, 0, Collections.emptyList());

//        private Part addItem(final long bytes) {
//            return new Part(
//                    this.items + 1,
//                    this.bytes + bytes);
//        }
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

