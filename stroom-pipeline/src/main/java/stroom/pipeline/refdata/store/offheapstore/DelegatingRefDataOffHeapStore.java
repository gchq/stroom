/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.refdata.store.offheapstore;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb.LmdbEnv;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.ProcessingInfoResponse;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataLoader;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefStoreEntry;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.offheapstore.RefDataLmdbEnv.Factory;
import stroom.security.api.SecurityContext;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PropertyPath;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.time.StroomDuration;
import stroom.util.time.TimeUtils;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Is a front for multiple {@link RefDataOffHeapStore} instances, one per feed.
 * It either delegates to the appropriate store if the stream id is know (so that the feed can be derived
 * from the stream ID), or it delegates to all stores and aggregates the results.
 */
@Singleton
public class DelegatingRefDataOffHeapStore implements RefDataStore, HasSystemInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DelegatingRefDataOffHeapStore.class);

    private static final String META_ID_TO_REF_STORE_CACHE_NAME = "Reference Data - Meta ID to Ref Store Cache";
    private static final String DELIMITER = "___";
    private static final Pattern DELIMITER_PATTERN = Pattern.compile(DELIMITER);
    private static final Pattern FEED_NAME_CLEAN_PATTERN = Pattern.compile("[^A-Z0-9_-]");
    private static final String FEED_NAME_CLEAN_REPLACEMENT = "_";

    static final String PARAM_NAME_FEED = "feed";

    private final Provider<ReferenceDataConfig> referenceDataConfigProvider;
    private final RefDataLmdbEnv.Factory refDataLmdbEnvFactory;
    private final RefDataOffHeapStore.Factory refDataOffHeapStoreFactory;
    private final MetaService metaService;
    private final SecurityContext securityContext;
    private final PathCreator pathCreator;
    private final FeedStore feedStore;

    // feed => refDataOffHeapStore, shouldn't be that many ref feeds
    // Feeds are immutable things too so no TTL needed
    // Ideally this would be a loading cache but that presents all sorts of issues around closure
    // of the store when other threads are using it.
    private final Map<String, RefDataOffHeapStore> feedNameToStoreMap = new ConcurrentHashMap<>();

    // Following items all relate to migration of a legacy ref data store that may or may not
    // be present depending on when the instance was first deployed.
    private volatile RefDataOffHeapStore legacyRefDataStore;
    private volatile boolean migrationCheckRequired = false;
    private final Set<Long> migratedRefStreamIds = ConcurrentHashMap.newKeySet();

    // Save us hitting the db all the time
    private final LoadingStroomCache<Long, FeedSpecificStore> metaIdToFeedStoreCache;

    @Inject
    @SuppressWarnings("unused")
    public DelegatingRefDataOffHeapStore(final Provider<ReferenceDataConfig> referenceDataConfigProvider,
                                         final CacheManager cacheManager,
                                         final Factory refDataLmdbEnvFactory,
                                         final RefDataOffHeapStore.Factory refDataOffHeapStoreFactory,
                                         final MetaService metaService,
                                         final SecurityContext securityContext,
                                         final PathCreator pathCreator,
                                         final FeedStore feedStore) {
        this.referenceDataConfigProvider = referenceDataConfigProvider;
        this.refDataLmdbEnvFactory = refDataLmdbEnvFactory;
        this.refDataOffHeapStoreFactory = refDataOffHeapStoreFactory;
        this.metaService = metaService;
        this.securityContext = securityContext;
        this.pathCreator = pathCreator;
        this.feedStore = feedStore;

        // Try and ensure up front that the dirs we use for ref data are there and can be written to
        final Path localDir = ensureLmdbDirectories();

        // If an exception is thrown when initialising the stores in the code below then
        // and guice may later attempt to re-construct this class in which case the cache will
        // already exist
        final LoadingStroomCache<Long, FeedSpecificStore> cache = cacheManager.getLoadingCache(
                META_ID_TO_REF_STORE_CACHE_NAME);
        if (cache == null) {
            // This is a singleton so no need to worry about locking
            metaIdToFeedStoreCache = cacheManager.createLoadingCache(
                    META_ID_TO_REF_STORE_CACHE_NAME,
                    () -> referenceDataConfigProvider.get().getMetaIdToRefStoreCache(),
                    this::getOrCreateFeedSpecificStore);
        } else {
            metaIdToFeedStoreCache = cache;
        }

        initLegacyStore(false, localDir);
        // Set up all the stores we find on disk so NodeStatusServiceUtil can get all the size on disk
        // values
        securityContext.asProcessingUser(() -> discoverFeedSpecificStores(localDir));
    }

    private Path ensureLmdbDirectories() {
        final ReferenceDataConfig referenceDataConfig = referenceDataConfigProvider.get();

        // This is used by all feed specific (and legacy) stores
        final String localDirStr = referenceDataConfig.getLmdbConfig().getLocalDir();
        final Path localDir = ensureDirectoryIsWritable(
                localDirStr,
                referenceDataConfig.getLmdbConfig().getFullPath(LmdbConfig.LOCAL_DIR_PROP_NAME));

        // stagingDir is the parent for all transient staging stores created during a load, so worth ensuring
        // it is ok now
        final String stagingDirStr = referenceDataConfig.getStagingLmdbConfig().getLocalDir();
        ensureDirectoryIsWritable(
                stagingDirStr,
                referenceDataConfig.getStagingLmdbConfig().getFullPath(LmdbConfig.LOCAL_DIR_PROP_NAME));

        return localDir;
    }

    private Path ensureDirectoryIsWritable(final String pathStr, final PropertyPath propertyPath) {
        final Path path = pathCreator.toAppPath(pathStr);
        LOGGER.debug("Ensuring directory {}", path);
        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error ensuring directory {} for property '{}': {}",
                    path,
                    propertyPath,
                    LogUtil.exceptionMessage(e)), e);
        }

        LOGGER.debug("Testing directory {} is writable", path);
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(path, "", null);
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error writing test file in directory {} for property '{}': {}",
                    path,
                    propertyPath,
                    LogUtil.exceptionMessage(e), e));
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (final IOException e) {
                    // Swallow and log
                    LOGGER.error("Error writing test file in directory {} for property {}: {}",
                            path,
                            propertyPath,
                            e.getMessage());
                }
            }
        }
        return path;
    }

    /**
     * Get all ref stores keyed by feed name
     *
     * @return Map of feedName => {@link RefDataOffHeapStore}
     */
    public Map<String, RefDataOffHeapStore> getFeedNameToStoreMap() {
        return Collections.unmodifiableMap(feedNameToStoreMap);
    }

    public Optional<String> getFeedName(final RefStreamDefinition refStreamDefinition) {
        final long streamId = Objects.requireNonNull(refStreamDefinition).getStreamId();
        return metaIdToFeedStoreCache.getIfPresent(streamId)
                .map(FeedSpecificStore::feedName);
    }

    @Override
    public Set<String> getMapNames(final RefStreamDefinition refStreamDefinition) {
        return getEffectiveStore(refStreamDefinition).getMapNames(refStreamDefinition);
    }

    @Override
    public Optional<ProcessingState> getLoadState(final RefStreamDefinition refStreamDefinition) {
        return getEffectiveStore(refStreamDefinition).getLoadState(refStreamDefinition);
    }

    @Override
    public boolean exists(final MapDefinition mapDefinition) {
        return getEffectiveStore(mapDefinition).exists(mapDefinition);
    }

    @Override
    public Optional<RefDataValue> getValue(final MapDefinition mapDefinition, final String key) {
        return getEffectiveStore(mapDefinition).getValue(mapDefinition, key);
    }

    @Override
    public RefDataValueProxy getValueProxy(final MapDefinition mapDefinition, final String key) {
        return getEffectiveStore(mapDefinition).getValueProxy(mapDefinition, key);
    }

    @Override
    public boolean consumeValueBytes(final MapDefinition mapDefinition,
                                     final String key,
                                     final Consumer<TypedByteBuffer> valueBytesConsumer) {
        return getEffectiveStore(mapDefinition).consumeValueBytes(mapDefinition, key, valueBytesConsumer);
    }

    @Override
    public boolean doWithLoaderUnlessComplete(final RefStreamDefinition refStreamDefinition,
                                              final long effectiveTimeMs,
                                              final Consumer<RefDataLoader> work) {
        return getEffectiveStore(refStreamDefinition)
                .doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, work);
    }

    @Override
    public List<RefStoreEntry> list(final int limit) {
        return getListOnAllStores(limit, store ->
                store.list(limit));
    }

    @Override
    public List<RefStoreEntry> list(final int limit, final Predicate<RefStoreEntry> filter) {
        return getListOnAllStores(limit, store ->
                store.list(limit, filter));
    }

    @Override
    public void consumeEntries(final Predicate<RefStoreEntry> filter,
                               final Predicate<RefStoreEntry> takeWhilePredicate,
                               final Consumer<RefStoreEntry> entryConsumer) {

        // It is possible this would benefit from some form of parallelism but have not used .parallel() as IO
        // is involved, so it may tie up the fork join pool
        Stream<RefStoreEntry> stream = getAllStoresAsStream()
                .flatMap(store ->
                        store.list(Integer.MAX_VALUE, filter).stream());

        if (takeWhilePredicate != null) {
            stream = stream.takeWhile(takeWhilePredicate);
        }
        stream
                .map(entry -> {
                    final String feedName = entry.getFeedName();
                    if (RefDataLmdbEnv.LEGACY_STORE_NAME.equals(feedName)) {
                        return renameLegacyFeed(entry, feedName);
                    } else {
                        return entry;
                    }
                })
                .forEach(entryConsumer);
    }

    private RefStoreEntry renameLegacyFeed(final RefStoreEntry entry, final String feedName) {
        // Replace 'Legacy' with 'ACTUAL_FEED_NAME (Legacy)'
        final String actualFeedName = NullSafe.get(
                entry.getMapDefinition(),
                MapDefinition::getRefStreamDefinition,
                RefStreamDefinition::getStreamId,
                this::lookUpFeedName);
        return NullSafe.getOrElse(
                actualFeedName,
                name -> name + " (" + feedName + ")",
                newName -> new RefStoreEntry(
                        newName,
                        entry.getMapDefinition(),
                        entry.getKey(),
                        entry.getValue(),
                        entry.getValueReferenceCount(),
                        entry.getRefDataProcessingInfo()),
                entry);
    }

    @Override
    public List<ProcessingInfoResponse> listProcessingInfo(final int limit) {
        return getListOnAllStores(
                limit, store ->
                        store.listProcessingInfo(limit)
        );
    }

    private <T> List<T> getListOnAllStores(final int limit,
                                           final Function<RefDataOffHeapStore, List<T>> listFunction) {

        // It is possible this would benefit from some form of parallelism but have not used .parallel() as IO
        // is involved, so it may tie up the fork join pool
        Stream<T> stream = getAllStoresAsStream()
                .flatMap(store ->
                        listFunction.apply(store).stream());
        if (limit > 0 && limit < Integer.MAX_VALUE) {
            stream = stream.limit(limit);
        }
        return stream.collect(Collectors.toList());
    }

    @Override
    public List<ProcessingInfoResponse> listProcessingInfo(final int limit,
                                                           final Predicate<ProcessingInfoResponse> filter) {
        // It is possible this would benefit from some form of parallelism but have not used .parallel() as IO
        // is involved, so it may tie up the fork join pool
        return getAllStoresAsStream()
                .flatMap(store ->
                        store.listProcessingInfo(limit, filter).stream())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public long getKeyValueEntryCount() {
        return getStoresAggregateValue(RefDataOffHeapStore::getKeyValueEntryCount);
    }

    @Override
    public long getRangeValueEntryCount() {
        return getStoresAggregateValue(RefDataOffHeapStore::getRangeValueEntryCount);
    }

    public long getCombinedEntryCount() {
        return getStoresAggregateValue(store ->
                store.getKeyValueEntryCount() + store.getRangeValueEntryCount());
    }

    @Override
    public long getProcessingInfoEntryCount() {
        return getStoresAggregateValue(RefDataOffHeapStore::getProcessingInfoEntryCount);
    }

    private long getStoresAggregateValue(final ToLongFunction<RefDataOffHeapStore> valueFunc) {
        // It is possible this would benefit from some form of parallelism but have not used .parallel() as IO
        // is involved, so it may tie up the fork join pool
        return getAllStoresAsStream()
                .mapToLong(valueFunc)
                .sum();
    }

    @Override
    public void purgeOldData() {
        LOGGER.debug("purgeOldData() called");
        // Purge sequentially
        getAllStoresAsStream()
                .forEach(refDataOffHeapStore -> {
                    LOGGER.debug("Calling purgeOldData() on store {}", refDataOffHeapStore);
                    refDataOffHeapStore.purgeOldData();
                });
        checkLegacyStoreState();
    }

    @Override
    public void purgeOldData(final StroomDuration purgeAge) {
        LOGGER.debug("purgeOldData({}) called", purgeAge);
        // Purge sequentially
        getAllStoresAsStream()
                .forEach(refDataOffHeapStore -> {
                    LOGGER.debug("Calling purgeOldData({}) on store {}", purgeAge, refDataOffHeapStore);
                    refDataOffHeapStore.purgeOldData(purgeAge);
                });
        checkLegacyStoreState();
    }

    @Override
    public void purge(final long refStreamId, final long partIndex) {
        // Purge sequentially
        final RefDataOffHeapStore effectiveStore = getEffectiveStore(refStreamId);
        LOGGER.debug("Calling purge() - refStreamId {}, partIndex: {}, store: {}",
                refStreamId, partIndex, effectiveStore);
        effectiveStore.purge(refStreamId, partIndex);
        checkLegacyStoreState();
    }

    @Override
    public void logAllContents() {
        // Sequential so we don't get a disordered mess
        getAllStoresAsStream()
                .sequential()
                .forEach(refDataOffHeapStore -> {
                    LOGGER.debug("Dumping contents of store {}", refDataOffHeapStore);
                    refDataOffHeapStore.logAllContents();
                });
    }

    @Override
    public void logAllContents(final Consumer<String> logEntryConsumer) {
        // Sequential so we don't get a disordered mess
        getAllStoresAsStream()
                .sequential()
                .forEach(store -> {
                    logEntryConsumer.accept("Logging all contents for store " + store);
                    store.logAllContents(logEntryConsumer);
                });
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.OFF_HEAP;
    }

    @Override
    public long getSizeOnDisk() {
        // It is possible this would benefit from some form of parallelism but have not used .parallel() as IO
        // is involved, so it may tie up the fork join pool
        return getAllStoresAsStream()
                .mapToLong(RefDataOffHeapStore::getSizeOnDisk)
                .sum();
    }

    @Override
    public String getName() {
        return "Delegating store";
    }

    @Override
    public SystemInfoResult getSystemInfo(final Map<String, String> params) {
        // Let the caller specify the feed to inspect. Too much info for each feed store
        // to show it all
        return Optional.ofNullable(params.get(PARAM_NAME_FEED))
                .map(feedName ->
                        Optional.ofNullable(feedNameToStoreMap.get(feedName))
                                .map(RefDataOffHeapStore::getSystemInfo)
                                .orElseGet(() -> SystemInfoResult.builder(this)
                                        .build()))
                .orElseGet(this::getSystemInfo);
    }

    @Override
    public List<ParamInfo> getParamInfo() {
        return List.of(
                ParamInfo.optionalParam(PARAM_NAME_FEED,
                        "The name of the feed to get the store system info for."));
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        try {
            final ReferenceDataConfig referenceDataConfig = referenceDataConfigProvider.get();

            final SystemInfoResult.Builder builder = SystemInfoResult.builder(this)
                    .addDetail("Store count", feedNameToStoreMap.size())
                    .addDetail("Feed store paths", feedNameToStoreMap.entrySet()
                            .stream()
                            .collect(Collectors.toMap(
                                    Entry::getKey,
                                    entry -> entry.getValue()
                                            .getLmdbEnvironment()
                                            .getLocalDir()
                                            .toAbsolutePath()
                                            .toString())))
                    .addDetail("Environment max size", referenceDataConfig.getLmdbConfig().getMaxStoreSize())
                    .addDetail("Environment current size (total)",
                            ModelStringUtil.formatIECByteSizeString(getSizeOnDisk()))
                    .addDetail("Purge age", referenceDataConfig.getPurgeAge())
                    .addDetail("Purge cut off",
                            TimeUtils.durationToThreshold(referenceDataConfig.getPurgeAge()).toString())
                    .addDetail("Max readers", referenceDataConfig.getLmdbConfig().getMaxReaders())
                    .addDetail("Read-ahead enabled", referenceDataConfig.getLmdbConfig().isReadAheadEnabled())
                    .addDetail("Total reference entries", getCombinedEntryCount());

            if (legacyRefDataStore != null) {
                builder.addDetail("Legacy store", legacyRefDataStore.getSystemInfo());
            }

            return builder.build();
        } catch (final RuntimeException e) {
            return SystemInfoResult.builder(this)
                    .addError(e)
                    .build();
        }
    }

    private Stream<RefDataOffHeapStore> getAllStoresAsStream() {
        return legacyRefDataStore != null
                ? Stream.concat(Stream.of(legacyRefDataStore), feedNameToStoreMap.values().stream())
                : feedNameToStoreMap.values().stream();
    }

    private String lookUpFeedName(final long refSteamId) {
        // Ref store is not specific to one user, so we need to see all feeds.
        // Ref lookups will take care of perm checks
        final String feedName = securityContext.asProcessingUserResult(() ->
                NullSafe.getAsOptional(metaService.getMeta(refSteamId),
                                Meta::getFeedName)
                        .orElseThrow(() -> new RuntimeException("No meta record found for meta ID " + refSteamId)));
        LOGGER.debug("Looked up feedName '{}' for refStreamId: {}", feedName, refSteamId);
        return feedName;
    }

    public RefDataOffHeapStore getEffectiveStore(final MapDefinition mapDefinition) {
        Objects.requireNonNull(mapDefinition);
        return getEffectiveStore(mapDefinition.getRefStreamDefinition().getStreamId());
    }

    public RefDataOffHeapStore getEffectiveStore(final RefStreamDefinition refStreamDefinition) {
        Objects.requireNonNull(refStreamDefinition);
        return getEffectiveStore(refStreamDefinition.getStreamId());
    }

    public RefDataOffHeapStore getEffectiveStore(final long refStreamId) {
        final FeedSpecificStore feedSpecificStore = metaIdToFeedStoreCache.get(refStreamId);
        // Loading cache so should not be null
        Objects.requireNonNull(feedSpecificStore);
        LOGGER.debug("getEffectiveStore() - refStreamId: {}, feedSpecificStore: {}", refStreamId, feedSpecificStore);
        return feedSpecificStore.refDataOffHeapStore;
    }

    public RefDataOffHeapStore getEffectiveStore(final String feedName) {
        // TODO: 26/05/2023 Do we always want to be creating stores?
        return feedNameToStoreMap.computeIfAbsent(
                feedName,
                this::getOrCreateFeedSpecificStore);
    }

    private FeedSpecificStore getOrCreateFeedSpecificStore(final long refStreamId) {
        final String feedName = lookUpFeedName(refStreamId);
        Objects.requireNonNull(feedName);

        final RefDataOffHeapStore feedSpecificStore = feedNameToStoreMap.computeIfAbsent(
                feedName,
                this::getOrCreateFeedSpecificStore);

        // We migrate at the refStreamId level, so we just hold a set of IDs rather than a load of
        // RefStreamDefinition objects which will be more costly to check against. In most cases there
        // will only be one RefStreamDefinition per refStreamId
        if (legacyRefDataStore != null
            && migrationCheckRequired
            && !migratedRefStreamIds.contains(refStreamId)) {

            if (feedSpecificStore.exists(refStreamId)) {
                // Already migrated, therefore add it to our set, so we don't need to do the
                // costly exists check again.
                LOGGER.trace("refStreamId: {} already migrated", refStreamId);
                migratedRefStreamIds.add(refStreamId);
            } else {
                try {
                    migrateRefStream(refStreamId, feedSpecificStore);
                } catch (final Exception e) {
                    // We can't risk the migration holding up processing, so if it fails just ignore it.
                    // The caller will then get a feed specific store that doesn't contain the stream so will try to
                    // load it as normal.
                    LOGGER.error("Error migrating refStreamId: {} to feed store '{}'. " +
                                 "This stream will not be migrated so will have to be re-loaded as normal. " +
                                 "Migration may be tried again if stroom is re-booted. {}",
                            refStreamId, feedName, e.getMessage(), e);
                } finally {
                    // Even though we haven't migrated it, mark it as such, so we don't try again.
                    migratedRefStreamIds.add(refStreamId);
                }
            }
        } else {
            LOGGER.trace("No migration required");
        }

        return new FeedSpecificStore(feedName, feedSpecificStore);
    }

    long getEntryCount(final String dbName) {
        // It is possible this would benefit from some form of parallelism but have not used .parallel() as IO
        // is involved, so it may tie up the fork join pool
        return getAllStoresAsStream()
                .mapToLong(store -> store.getEntryCount(dbName))
                .sum();
    }

    private void migrateRefStream(final long refStreamId,
                                  final RefDataOffHeapStore feedSpecificStore) {
        LOGGER.info("Migrating ref stream {} from legacy ref store to feed specific store '{}'",
                refStreamId, feedSpecificStore);

        legacyRefDataStore.migrateRefStreams(refStreamId, feedSpecificStore);

        // Record the ID, so we don't have to migrate it again.
        migratedRefStreamIds.add(refStreamId);
    }

    private void discoverFeedSpecificStores(final Path localDir) {
        LOGGER.debug(() -> "Attempting to discover feed specific ref data stores in " + localDir.toAbsolutePath());
        if (Files.isDirectory(localDir)) {
            try (final Stream<Path> pathStream = Files.list(localDir)) {
                final int storeCount = pathStream.filter(Files::isDirectory)
                        .filter(path -> path.getFileName().toString().contains(DELIMITER))
                        .mapToInt(this::initFeedSpecificStore)
                        .sum();
                LOGGER.info("Discovered {} feed specific ref data stores in {}",
                        storeCount, localDir.toAbsolutePath());

            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            LOGGER.debug("localDir '{}' doesn't exist, no stores to create", localDir);
        }
    }

    /**
     * @return The count of feed stores initialised, 1 or 0.
     */
    private int initFeedSpecificStore(final Path storeDir) {
        final String dirName = storeDir.getFileName().toString();
        final String[] parts = DELIMITER_PATTERN.split(dirName);
        if (parts.length != 2) {
            LOGGER.error("Unable to parse ref store directory {} (parts: {}). Ignoring this store",
                    storeDir, parts);
            return 0;
        } else {
            // The dir name looks like '<feed name>___<feed uuid>' but the feed name has been
            // sanitised for the FS so we need to get the true feedName via a uuid lookup
            final String feedDocUuid = parts[1];
            final String feedName;
            try {
                feedName = NullSafe.get(
                        feedStore.info(DocRef.builder().type(FeedDoc.TYPE).uuid(feedDocUuid).build()),
                        DocRefInfo::getDocRef,
                        DocRef::getName);
            } catch (final DocumentNotFoundException e) {
                LOGGER.error("Feed with UUID '{}' not found for ref store directory {} (parts: {}). " +
                             "Ignoring this directory. Consider deleting it if refers to a non-existent fee.",
                        feedDocUuid, storeDir, parts);
                return 0;
            } catch (final Exception e) {
                LOGGER.error("Error initialising ref store directory {} (parts: {}). Ignoring this store. Error: {}",
                        storeDir, parts, e.getMessage(), e);
                return 0;
            }

            if (!NullSafe.isBlankString(feedName)) {
                LOGGER.debug("Found store for feed {}", feedName);
                final RefDataOffHeapStore store = getOrCreateFeedSpecificStore(feedName);
                if (store.isEmpty()) {
                    try {
                        closeAndDeleteStore(store);
                    } finally {
                        feedNameToStoreMap.remove(feedName);
                    }
                } else {
                    feedNameToStoreMap.put(feedName, store);
                }
                return 1;
            } else {
                LOGGER.error("No feed name for UUID {}. Ignoring store {}", feedDocUuid, storeDir);
                return 0;
            }
        }
    }

    private void initLegacyStore(final boolean createIfNotExists, final Path localDir) {
        // Legacy store may not exist, e.g. for a recent deployment, or it may be empty
        try (final Stream<Path> pathStream = Files.list(localDir).filter(Files::isRegularFile)) {
            final boolean foundDataFile = pathStream.anyMatch(LmdbEnv::isLmdbDataFile);
            if (foundDataFile) {
                legacyRefDataStore = getOrCreateLegacyStore();
                if (legacyRefDataStore.isEmpty()) {
                    // This likely means all data has been migrated so delete it
                    closeAndDeleteLegacyStore();
                } else {
                    LOGGER.info("Found a non-empty legacy reference data store in {}. Migration of reference data " +
                                "will be performed on a stream by stream basis on demand.",
                            localDir.toAbsolutePath());
                    migrationCheckRequired = true;
                }
            } else {
                if (createIfNotExists) {
                    LOGGER.warn("TESTING ONLY! No legacy store found in {}, creating an empty one", localDir);
                    legacyRefDataStore = getOrCreateLegacyStore();
                    // createIfNotExists is only really here for migration testing so require a mig check
                    migrationCheckRequired = true;
                } else {
                    LOGGER.info("No legacy reference data store found in {}. No migration required.", localDir);
                    legacyRefDataStore = null;
                    migrationCheckRequired = false;
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error listing files in {}: {}", localDir, e.getMessage()), e);
        }
    }

    private void closeAndDeleteLegacyStore() {
        try {
            closeAndDeleteStore(legacyRefDataStore);
        } finally {
            this.legacyRefDataStore = null;
            migrationCheckRequired = false;
        }
    }

    private void closeAndDeleteStore(final RefDataOffHeapStore store) {
        // This likely means all data has been migrated so delete it
        final RefDataLmdbEnv lmdbEnvironment = store.getLmdbEnvironment();
        LOGGER.info("Closing and deleting empty ref data LMDB env from {}", lmdbEnvironment.getLocalDir());
        try {
            lmdbEnvironment.close();
            try {
                lmdbEnvironment.delete();
            } catch (final Exception e) {
                LOGGER.error("Error deleting ref data lmdb env {}: {}", lmdbEnvironment, e.getMessage(), e);
            }
        } catch (final Exception e) {
            LOGGER.error("Error closing ref data lmdb env {}: {}", lmdbEnvironment, e.getMessage(), e);
        }
    }

    private RefDataOffHeapStore getOrCreateLegacyStore() {
        return getOrCreateFeedSpecificStore(null);
    }

    private RefDataOffHeapStore getOrCreateFeedSpecificStore(final String feedName) {
        if (!NullSafe.isBlankString(feedName)) {
            // No need to validate legacy
            validateFeedName(feedName);
        }
        final String subDirName = NullSafe.get(feedName, this::feedNameToSubDirName);
        // This will get/create the env on disk
        final RefDataLmdbEnv refDataLmdbEnv = refDataLmdbEnvFactory.create(feedName, subDirName);
        final RefDataOffHeapStore refDataOffHeapStore = refDataOffHeapStoreFactory.create(refDataLmdbEnv);
        LOGGER.debug("Created {} ref data store {}in dir: '{}'",
                (feedName == null
                        ? "legacy"
                        : "feed specific"),
                (feedName == null
                        ? ""
                        : "for feed '" + refDataLmdbEnv.getFeedName() + "' "),
                refDataLmdbEnv.getLocalDir());
        return refDataOffHeapStore;
    }

    private void validateFeedName(final String feedName) {
        final List<DocRef> docRefs = feedStore.findByName(feedName);
        if (docRefs.isEmpty()) {
            throw new RuntimeException(LogUtil.message("No feed exists with name '{}'", feedName));
        } else if (docRefs.size() > 1) {
            throw new RuntimeException(LogUtil.message("Multiple feeds exist with name '{}': {}",
                    feedName,
                    docRefs.stream()
                            .map(DocRef::getUuid)
                            .collect(Collectors.joining(", "))));
        }
    }

    private String feedNameToSubDirName(final String feedName) {
        Objects.requireNonNull(feedName);
        final String cleanedFeedName = FEED_NAME_CLEAN_PATTERN.matcher(feedName.toUpperCase())
                .replaceAll(FEED_NAME_CLEAN_REPLACEMENT);

        final List<DocRef> feedDocRefs = feedStore.findByName(feedName);
        if (feedDocRefs.isEmpty()) {
            throw new RuntimeException(LogUtil.message("Expecting to find feed doc for name " + feedName));
        } else if (feedDocRefs.size() > 1) {
            throw new RuntimeException(LogUtil.message("Found " + feedDocRefs.size() + " feed docs for name "
                                                       + feedName + ". Feed names should be unique."));
        }
        final String feedDocUuid = feedDocRefs.getFirst().getUuid();

        // It is possible for two feed names to share the same cleaned name, so add a UUID on the end to make it
        // is involved, so it may tie up the fork join pool
        final String subDirName = String.join(DELIMITER, cleanedFeedName, feedDocUuid);
        LOGGER.debug("Using subDirName: '{}' for feedName: '{}', ", feedName, subDirName);
        return subDirName;
    }

    private void checkLegacyStoreState() {
        LOGGER.debug("checkLegacyStoreState()");
        final RefDataOffHeapStore legacyStore = this.legacyRefDataStore;
        if (legacyStore != null) {
            if (legacyStore.isEmpty()) {
                // Purge may have cleaned it out, so don't need to check for migrations in future
                if (LOGGER.isDebugEnabled()) {
                    if (migrationCheckRequired) {
                        LOGGER.debug("Setting migrationCheckRequired to false");
                    }
                }
                migrationCheckRequired = false;
            }
        } else {
            migrationCheckRequired = false;
        }
    }

    /**
     * For testing only!
     */
    RefDataOffHeapStore getLegacyRefDataStore(final boolean createIfNotExists) {
        if (legacyRefDataStore == null) {
            final Path localDir = ensureLmdbDirectories();
            initLegacyStore(createIfNotExists, localDir);
        }
        return legacyRefDataStore;
    }


    // --------------------------------------------------------------------------------


    private record FeedSpecificStore(String feedName, RefDataOffHeapStore refDataOffHeapStore) {

        @Override
        public String toString() {
            return "FeedSpecificStore{" +
                   "feedName='" + feedName + '\'' +
                   ", refDataOffHeapStore=" + refDataOffHeapStore +
                   '}';
        }
    }
}
