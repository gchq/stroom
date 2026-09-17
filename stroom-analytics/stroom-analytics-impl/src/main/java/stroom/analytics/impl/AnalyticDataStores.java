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

package stroom.analytics.impl;

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.AnalyticDataShard;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.FindAnalyticDataShardCriteria;
import stroom.analytics.shared.GetAnalyticShardDataRequest;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb2.LmdbEnv;
import stroom.lmdb2.LmdbEnvDir;
import stroom.lmdb2.LmdbEnvDirFactory;
import stroom.node.api.NodeInfo;
import stroom.query.api.FindResultStoreCriteria;
import stroom.query.api.OffsetRange;
import stroom.query.api.ParamUtil;
import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.query.api.ResultStoreInfo;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.api.TableResult;
import stroom.query.api.TableSettings;
import stroom.query.api.TimeFilter;
import stroom.query.common.v2.AbstractResultStoreConfig;
import stroom.query.common.v2.AnalyticResultStoreConfig;
import stroom.query.common.v2.AnnotationMapperFactory;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.ErrorConsumerImpl;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.HasResultStoreInfo;
import stroom.query.common.v2.LmdbDataStore;
import stroom.query.common.v2.TableResultCreator;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.concurrent.StripedLock;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.view.shared.ViewDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class AnalyticDataStores implements HasResultStoreInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticDataStores.class);

    private final LmdbEnvDirFactory lmdbEnvDirFactory;
    private final Provider<AnalyticResultStoreConfig> analyticStoreConfigProvider;
    private final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper;
    private final Provider<Executor> executorProvider;
    private final ExpressionContextFactory expressionContextFactory;
    private final Path analyticResultStoreDir;
    // Keyed on the store dir, which is what identifies the env. NOT on the rule doc: that has
    // value based equality including a version that changes on every save, so an edited rule
    // missed the cache and opened a SECOND env on the same dir.
    //
    // Not on the rule uuid either, as the dir embeds the rule name, so a rename maps to a new
    // store. KNOWN LIMITATION, accepted rather than overlooked: renaming a rule abandons its
    // accumulated data, the sweep below then deletes the old dir, and as the tracker is keyed on
    // uuid alone it keeps the old stream high-water mark, so the rule resumes from there against
    // an empty store and never reprocesses the gap. Keying on uuid would fix it but would need
    // the dirs of every existing deployment migrating, as they all carry the name.
    private final Map<String, AnalyticDataStore> dataStoreCache;
    // Serialises creation of a given store without holding a lock on the map while we do it.
    private final StripedLock storeCreationLocks = new StripedLock();
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;
    private final ByteBufferFactory bufferFactory;
    private final ExpressionPredicateFactory expressionPredicateFactory;
    private final AnnotationMapperFactory annotationMapperFactory;
    private final AnalyticRuleStore analyticRuleStore;
    final WordListProvider wordListProvider;

    @Inject
    public AnalyticDataStores(final LmdbEnvDirFactory lmdbEnvDirFactory,
                              final PathCreator pathCreator,
                              final AnalyticRuleStore analyticRuleStore,
                              final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper,
                              final Provider<AnalyticResultStoreConfig> analyticStoreConfigProvider,
                              final Provider<Executor> executorProvider,
                              final ExpressionContextFactory expressionContextFactory,
                              final NodeInfo nodeInfo,
                              final SecurityContext securityContext,
                              final ByteBufferFactory bufferFactory,
                              final ExpressionPredicateFactory expressionPredicateFactory,
                              final AnnotationMapperFactory annotationMapperFactory,
                              final WordListProvider wordListProvider) {
        this.lmdbEnvDirFactory = lmdbEnvDirFactory;
        this.analyticRuleStore = analyticRuleStore;
        this.analyticStoreConfigProvider = analyticStoreConfigProvider;
        this.analyticRuleSearchRequestHelper = analyticRuleSearchRequestHelper;
        this.executorProvider = executorProvider;
        this.expressionContextFactory = expressionContextFactory;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
        this.bufferFactory = bufferFactory;
        this.expressionPredicateFactory = expressionPredicateFactory;
        this.annotationMapperFactory = annotationMapperFactory;
        this.wordListProvider = wordListProvider;

        this.analyticResultStoreDir = getLocalDir(analyticStoreConfigProvider.get(), pathCreator);

        dataStoreCache = new ConcurrentHashMap<>();
    }

    private Path getLocalDir(final AbstractResultStoreConfig resultStoreConfig,
                             final PathCreator pathCreator) {
        final String dirFromConfig = NullSafe.get(
                resultStoreConfig,
                AbstractResultStoreConfig::getLmdbConfig,
                LmdbConfig::getLocalDir);

        Objects.requireNonNull(dirFromConfig, "localDir not set");
        return pathCreator.toAppPath(dirFromConfig);
    }

    public void deleteOldStores() {
        // Get a set of cached docs and used dirs before we find out what the current rule docs are.
        final Set<String> cachedDirs = new HashSet<>(dataStoreCache.keySet());
        final Set<String> actualDirs = getFileSystemAnalyticStoreDirs();

        // A rule we fail to read, or whose dir we fail to work out, is handled per rule: its dirs
        // are protected and the sweep carries on. Only a failure to list the rules at all aborts,
        // as without the list every dir looks like it belongs to a deleted rule.
        final ExpectedDirs expectedDirs;
        try {
            expectedDirs = getExpectedAnalyticStoreDirs(loadAllForDeletion());
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Not deleting old analytic stores as the current rules could not be " +
                               "listed: " + e.getMessage(), e);
            return;
        }

        // Drop and close exactly the cached stores whose dir the sweep below is about to delete,
        // so no dir is ever deleted with an open env on it. Decided by dir, which is what the
        // cache is keyed on: a rule that has merely been edited keeps its dir, so its store is
        // left alone rather than being closed under an in flight search.
        for (final String cachedDir : cachedDirs) {
            if (!expectedDirs.keep(cachedDir)) {
                // The same stripe getOrCreate() creates under, so a store cannot be handed out
                // while we are closing it, and cannot be created between our close and the
                // matching delete below.
                final Lock lock = storeCreationLocks.getLockForKey(cachedDir);
                lock.lock();
                try {
                    final AnalyticDataStore dataStore = dataStoreCache.remove(cachedDir);
                    if (dataStore != null) {
                        try {
                            dataStore.getLmdbDataStore().close();
                        } catch (final RuntimeException e) {
                            LOGGER.error(() -> "Error closing old analytic store: " + e.getMessage(), e);
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
        for (final String actualDir : actualDirs) {
            try {
                if (!expectedDirs.keep(actualDir)) {
                    final Lock lock = storeCreationLocks.getLockForKey(actualDir);
                    lock.lock();
                    try {
                        // A search may have created a store on this dir since we listed it, in
                        // which case the env is open on these files and they must not be unlinked.
                        if (dataStoreCache.containsKey(actualDir)) {
                            LOGGER.debug(() -> "Not deleting analytic store dir that is now in use: " + actualDir);
                            continue;
                        }
                        final Path path = analyticResultStoreDir.resolve(actualDir);
                        if (Files.isDirectory(path)) {
                            LOGGER.info(() -> "Deleting old analytic store: " + FileUtil.getCanonicalPath(path));
                            FileUtil.deleteDir(path);
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
            }
        }
    }

    private String getAnalyticStoreDir(final AnalyticDataStore dataStore) {
        return getAnalyticStoreDir(dataStore.getSearchRequest());
    }

    private String getAnalyticStoreDir(final SearchRequest searchRequest) {
        return getAnalyticStoreDir(searchRequest.getKey(), getComponentId(searchRequest));
    }

    private String getAnalyticStoreDir(final QueryKey queryKey,
                                       final String componentId) {
        return sanitise(queryKey.getUuid() + "_" + componentId);
    }

    /**
     * Makes a name safe for the file system.
     */
    private String sanitise(final String name) {
        return name.replaceAll("[^A-Za-z0-9]", "_");
    }

    private Set<String> getFileSystemAnalyticStoreDirs() {
        try (final Stream<Path> stream = Files.list(analyticResultStoreDir)) {
            return stream.filter(Files::isDirectory)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toSet());
        } catch (final IOException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return Collections.emptySet();
    }

    /**
     * The dirs of the supplied rules, plus a dir name prefix for each rule we could not read or
     * whose dir we could not work out. Such a rule still exists and must keep its data, but we
     * cannot name its dir, so we protect every dir that could belong to it instead. Protecting
     * rather than aborting means one broken rule does not stop the stores of genuinely deleted
     * rules being reclaimed.
     */
    private ExpectedDirs getExpectedAnalyticStoreDirs(final CurrentRules currentRules) {
        final Set<String> expectedDirs = new HashSet<>();
        final Set<String> protectedPrefixes = new HashSet<>();
        currentRules.unreadableUuids().forEach(uuid -> protectedPrefixes.add(protectedPrefix(uuid)));
        for (final AnalyticRuleDoc analyticRuleDoc : currentRules.rules()) {
            try {
                expectedDirs.add(getAnalyticStoreDir(
                        analyticRuleSearchRequestHelper.create(analyticRuleDoc)));
            } catch (final RuntimeException e) {
                final String prefix = protectedPrefix(analyticRuleDoc.getUuid());
                LOGGER.error(() -> "Not deleting any analytic store dir starting '" + prefix +
                                   "' as the store dir for rule " +
                                   RuleUtil.getRuleIdentity(analyticRuleDoc) +
                                   " could not be resolved: " + e.getMessage(), e);
                protectedPrefixes.add(prefix);
            }
        }
        return new ExpectedDirs(expectedDirs, protectedPrefixes);
    }

    /**
     * Every dir for a rule starts with its query key, which is uuid + " - " + name, so the uuid
     * and the separator make a prefix no other rule's dir can share.
     */
    private String protectedPrefix(final String ruleUuid) {
        return sanitise(ruleUuid + " - ");
    }

    private record ExpectedDirs(Set<String> dirs, Set<String> protectedPrefixes) {

        private boolean keep(final String dir) {
            return dirs.contains(dir) || protectedPrefixes.stream().anyMatch(dir::startsWith);
        }
    }

    /**
     * Seeds the store cache without opening an env. Only for tests that need a cached store to
     * exercise what deleteOldStores() does, and does not do, to it.
     */
    void putStoreForTesting(final AnalyticDataStore dataStore) {
        dataStoreCache.put(getAnalyticStoreDir(dataStore), dataStore);
    }

    public AnalyticDataStore get(final AnalyticRuleDoc analyticRuleDoc) {
        return getOrCreate(analyticRuleSearchRequestHelper.create(analyticRuleDoc));
    }

    /**
     * For callers that have already built the rule's search request. Building one parses the
     * rule's query, which is not free, and the store dir is derived from it.
     */
    public AnalyticDataStore get(final SearchRequest searchRequest) {
        return getOrCreate(searchRequest);
    }

    /**
     * Deliberately not {@link java.util.concurrent.ConcurrentHashMap#computeIfAbsent}: the
     * mapping function opens an LMDB env, which computeIfAbsent would do while synchronized on
     * the key's bin, making every other call for a rule in that bin wait for the env to open.
     * Creation is serialised by a striped lock outside the map instead. See gh-5689.
     * <p>
     * Both the cache and the stripe are keyed on the store dir, so there is only ever one env
     * per dir. An edit that leaves the rule's name alone therefore reuses the existing store
     * rather than opening a second env on its files; the accumulated data belongs to that dir,
     * not to a particular version of the rule.
     */
    private AnalyticDataStore getOrCreate(final SearchRequest searchRequest) {
        final String storeDir = getAnalyticStoreDir(searchRequest);

        final AnalyticDataStore existing = dataStoreCache.get(storeDir);
        if (existing != null) {
            return existing;
        }

        final Lock lock = storeCreationLocks.getLockForKey(storeDir);
        lock.lock();
        try {
            // Another thread may have created it while we were waiting for the lock.
            final AnalyticDataStore created = dataStoreCache.get(storeDir);
            if (created != null) {
                return created;
            }

            final DocRef dataSource = searchRequest.getQuery().getDataSource();
            if (dataSource == null || !ViewDoc.TYPE.equals(dataSource.getType())) {
                LOGGER.error("Rule needs to reference a view");
                throw new RuntimeException("Rule needs to reference a view");
            }
            final LmdbDataStore lmdbDataStore = createStore(searchRequest);
            final AnalyticDataStore dataStore = new AnalyticDataStore(searchRequest, lmdbDataStore);
            dataStoreCache.put(storeDir, dataStore);
            return dataStore;
        } finally {
            lock.unlock();
        }
    }

    public Optional<AnalyticDataStore> getIfExists(final AnalyticRuleDoc analyticRuleDoc) {
        final SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(analyticRuleDoc);
        final String dir = getAnalyticStoreDir(searchRequest);
        AnalyticDataStore analyticDataStore = dataStoreCache.get(dir);
        if (analyticDataStore == null) {
            final Path path = getAnalyticResultStoreDir().resolve(dir);
            if (Files.isDirectory(path)) {
                analyticDataStore = getOrCreate(searchRequest);
            }
        }
        return Optional.ofNullable(analyticDataStore);
    }

    private LmdbDataStore createStore(final SearchRequest searchRequest) {
        final DocRef dataSource = searchRequest.getQuery().getDataSource();
        if (dataSource == null || !ViewDoc.TYPE.equals(dataSource.getType())) {
            LOGGER.error("Rule needs to reference a view");
            throw new RuntimeException("Rule needs to reference a view");
        }

        // Create a field index map.
        final FieldIndex fieldIndex = new FieldIndex();

        // Create a parameter map.
        final Map<String, String> paramMap = ParamUtil.createParamMap(searchRequest.getQuery().getParams());

        // Create error consumer.
        final ErrorConsumer errorConsumer = new ErrorConsumerImpl();

        final String componentId = getComponentId(searchRequest);
        final TableSettings tableSettings = getTableSettings(searchRequest);
        final DataStoreSettings dataStoreSettings = DataStoreSettings.createAnalyticStoreSettings();
        final ExpressionContext expressionContext = expressionContextFactory
                .createContext(searchRequest);
        return createAnalyticLmdbDataStore(
                searchRequest.getKey(),
                componentId,
                tableSettings,
                expressionContext,
                fieldIndex,
                paramMap,
                dataStoreSettings,
                errorConsumer);
    }

    private LmdbDataStore createAnalyticLmdbDataStore(final QueryKey queryKey,
                                                      final String componentId,
                                                      final TableSettings tableSettings,
                                                      final ExpressionContext expressionContext,
                                                      final FieldIndex fieldIndex,
                                                      final Map<String, String> paramMap,
                                                      final DataStoreSettings dataStoreSettings,
                                                      final ErrorConsumer errorConsumer) {
        final AnalyticResultStoreConfig storeConfig = analyticStoreConfigProvider.get();

        final String subDirectory = getAnalyticStoreDir(queryKey, componentId);
        final LmdbEnvDir lmdbEnvDir = lmdbEnvDirFactory
                .builder()
                .config(storeConfig.getLmdbConfig())
                .subDir(subDirectory)
                .build();
        final LmdbEnv.Builder lmdbEnvBuilder = LmdbEnv
                .builder()
                .config(storeConfig.getLmdbConfig())
                .lmdbEnvDir(lmdbEnvDir);
        final SearchRequestSource searchRequestSource = SearchRequestSource
                .builder()
                .sourceType(SourceType.TABLE_BUILDER_ANALYTIC)
                .componentId(componentId)
                .build();
        return new LmdbDataStore(
                searchRequestSource,
                lmdbEnvBuilder,
                storeConfig,
                queryKey,
                componentId,
                tableSettings,
                expressionContext,
                fieldIndex,
                paramMap,
                dataStoreSettings,
                executorProvider,
                errorConsumer,
                bufferFactory,
                expressionPredicateFactory,
                annotationMapperFactory, wordListProvider);
    }

    @Override
    public ResultPage<ResultStoreInfo> find(final FindResultStoreCriteria criteria) {
        final List<ResultStoreInfo> list = new ArrayList<>();
        final List<AnalyticRuleDoc> currentRules = loadAll();
        currentRules.forEach(analyticRuleDoc -> {
            try {
                final DocRef docRef = analyticRuleDoc.asDocRef();
                final SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(analyticRuleDoc);
                final String componentId = getComponentId(searchRequest);
                final String dir = getAnalyticStoreDir(searchRequest.getKey(), componentId);
                final Path path = analyticResultStoreDir.resolve(dir);
                if (Files.isDirectory(path)) {
                    if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
                        list.add(new ResultStoreInfo(
                                new SearchRequestSource(SourceType.TABLE_BUILDER_ANALYTIC,
                                        docRef,
                                        null, null),
                                searchRequest.getKey(),
                                null,
                                analyticRuleDoc.getCreateTimeMs(),
                                nodeInfo.getThisNodeName(),
                                FileUtil.getByteSize(path),
                                false,
                                null,
                                null,
                                null));
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.debug("Error getting result store info for analytic rule {}",
                        analyticRuleDoc, e);
            }
        });

        return new ResultPage<>(list);
    }

    /**
     * As {@link #loadAll()} but does not swallow read failures, so a caller that decides what
     * to delete cannot mistake a rule it failed to read for one that has been deleted. Only a
     * rule that is genuinely gone is omitted.
     *
     * @throws RuntimeException if any current rule could not be read.
     */
    private CurrentRules loadAllForDeletion() {
        final List<AnalyticRuleDoc> currentRules = new ArrayList<>();
        final Set<String> unreadableUuids = new HashSet<>();
        for (final DocRef docRef : analyticRuleStore.list()) {
            try {
                final AnalyticRuleDoc analyticRuleDoc = analyticRuleStore.readDocument(docRef);
                if (analyticRuleDoc != null) {
                    currentRules.add(analyticRuleDoc);
                }
            } catch (final DocumentNotFoundException e) {
                // Deleted since the list above, so genuinely not a current rule.
                LOGGER.debug(e::getMessage, e);
            } catch (final RuntimeException e) {
                // Deliberately not loadAll(), which would just skip this rule: a rule missing
                // from the list is treated as deleted, so skipping would delete the store of a
                // rule that still exists. We cannot tell whether it does, so protect its dirs
                // and carry on rather than stop every other rule's store being reclaimed.
                LOGGER.error(() -> "Not deleting any analytic store of rule " + docRef.getUuid() +
                                   " as it could not be read: " + e.getMessage(), e);
                unreadableUuids.add(docRef.getUuid());
            }
        }
        return new CurrentRules(currentRules, unreadableUuids);
    }

    private record CurrentRules(List<AnalyticRuleDoc> rules, Set<String> unreadableUuids) {

    }

    public List<AnalyticRuleDoc> loadAll() {
        // TODO this is not very efficient. It fetches all the docrefs from the DB,
        //  then loops over them to fetch+deser the associated doc for each one (one by one)
        //  so the caller can filter half of them out by type.
        //  It would be better if we had a json type col in the doc table, so that the
        //  we can pass some kind of json path query to the persistence layer that the DBPersistence
        //  can translate to a MySQL json path query.
        final List<AnalyticRuleDoc> currentRules = new ArrayList<>();
        final List<DocRef> docRefs = analyticRuleStore.list();
        for (final DocRef docRef : docRefs) {
            try {
                final AnalyticRuleDoc analyticRuleDoc = analyticRuleStore.readDocument(docRef);
                if (analyticRuleDoc != null) {
                    currentRules.add(analyticRuleDoc);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        return currentRules;
    }


    private String getComponentId(final SearchRequest searchRequest) {
        for (final ResultRequest resultRequest : searchRequest.getResultRequests()) {
            if (resultRequest.getMappings() != null && !resultRequest.getMappings().isEmpty()) {
                return resultRequest.getComponentId();
            }
        }
        return null;
    }

    private TableSettings getTableSettings(final SearchRequest searchRequest) {
        for (final ResultRequest resultRequest : searchRequest.getResultRequests()) {
            if (resultRequest.getMappings() != null && !resultRequest.getMappings().isEmpty()) {
                return resultRequest.getMappings().getFirst();
            }
        }
        return null;
    }

    public ResultPage<AnalyticDataShard> findShards(final FindAnalyticDataShardCriteria criteria) {
        final List<AnalyticDataShard> list = new ArrayList<>();

        final DocRef docRef = DocRef
                .builder()
                .type(AnalyticRuleDoc.TYPE)
                .uuid(criteria.getAnalyticDocUuid())
                .build();
        try {
            final AbstractAnalyticRuleDoc analyticRuleDoc = analyticRuleStore.readDocument(docRef);
            final SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(analyticRuleDoc);
            final String componentId = getComponentId(searchRequest);
            final String dir = getAnalyticStoreDir(searchRequest.getKey(), componentId);
            final Path path = analyticResultStoreDir.resolve(dir);
            if (Files.isDirectory(path)) {
                if (securityContext.hasDocumentPermission(
                        analyticRuleDoc.asDocRef(), DocumentPermission.VIEW)) {

                    long createTime = 0;
                    try {
                        createTime = Files.getLastModifiedTime(path).to(TimeUnit.MILLISECONDS);
                    } catch (final IOException e) {
                        // Ignore.
                    }

                    list.add(new AnalyticDataShard(
                            nodeInfo.getThisNodeName(),
                            FileUtil.getCanonicalPath(path),
                            createTime,
                            FileUtil.getByteSize(path)));
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }

        return new ResultPage<>(list);
    }

    public Result getData(final GetAnalyticShardDataRequest request) {
        final DocRef docRef = DocRef
                .builder()
                .type(AnalyticRuleDoc.TYPE)
                .uuid(request.getAnalyticDocUuid())
                .build();
        try {
            final AnalyticRuleDoc doc = analyticRuleStore.readDocument(docRef);
            final Optional<AnalyticDataStore> optionalAnalyticDataStore = getIfExists(doc);
            if (optionalAnalyticDataStore.isPresent()) {
                final AnalyticDataStore analyticDataStore = optionalAnalyticDataStore.get();
                final SearchRequest searchRequest = analyticDataStore.searchRequest;
                final LmdbDataStore lmdbDataStore = analyticDataStore.lmdbDataStore;

                final FormatterFactory formatterFactory =
                        new FormatterFactory(searchRequest.getDateTimeSettings());
                final TableResultCreator resultCreator = new TableResultCreator(
                        formatterFactory,
                        expressionPredicateFactory);
                ResultRequest resultRequest = searchRequest.getResultRequests().getFirst();
                TableSettings tableSettings = resultRequest.getMappings().getFirst();
                tableSettings = tableSettings
                        .copy()
                        .aggregateFilter(null)
                        .maxResults(List.of(1000000L))
                        .build();
                final List<TableSettings> mappings = List.of(tableSettings);
                final TimeFilter timeFilter = DateExpressionParser
                        .getTimeFilter(
                                request.getTimeRange(),
                                request.getDateTimeSettings());
                resultRequest = resultRequest
                        .copy()
                        .mappings(mappings)
                        .requestedRange(request.getRequestedRange())
                        .timeFilter(timeFilter)
                        .build();

                Result result = resultCreator
                        .create(lmdbDataStore, resultRequest);

                // If we get no results and the offset > 0 then reset range and query again.
                if (result instanceof final TableResult tableResult) {
                    if (resultRequest.getRequestedRange().getOffset() > 0 &&
                        tableResult.getRows().isEmpty()) {
                        resultRequest = resultRequest
                                .copy()
                                .requestedRange(new OffsetRange(0L, request.getRequestedRange().getLength()))
                                .build();
                        result = resultCreator
                                .create(lmdbDataStore, resultRequest);
                    }
                }

                return result;
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            throw e;
        }

        return null;
    }

    private Path getAnalyticResultStoreDir() {
        return analyticResultStoreDir;
    }


    // --------------------------------------------------------------------------------


    public record AnalyticDataStore(SearchRequest searchRequest, LmdbDataStore lmdbDataStore) {

        public SearchRequest getSearchRequest() {
            return searchRequest;
        }

        public LmdbDataStore getLmdbDataStore() {
            return lmdbDataStore;
        }
    }
}
