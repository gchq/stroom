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

package stroom.analytics.impl;

import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.AnalyticDataShard;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.FindAnalyticDataShardCriteria;
import stroom.analytics.shared.GetAnalyticShardDataRequest;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
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
    private final Map<AnalyticRuleDoc, AnalyticDataStore> dataStoreCache;
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
        final Set<AnalyticRuleDoc> cachedDocs = new HashSet<>(dataStoreCache.keySet());
        final Set<String> actualDirs = getFileSystemAnalyticStoreDirs();

        // Remove old cached stuff.
        final List<AnalyticRuleDoc> currentRules = loadAll();
        for (final AnalyticRuleDoc cachedDoc : cachedDocs) {
            if (!currentRules.contains(cachedDoc)) {
                dataStoreCache.remove(cachedDoc);
            }
        }

        final Set<String> expectedDirs = getExpectedAnalyticStoreDirs(currentRules);
        for (final String actualDir : actualDirs) {
            try {
                if (!expectedDirs.contains(actualDir)) {
                    final Path path = analyticResultStoreDir.resolve(actualDir);
                    if (Files.isDirectory(path)) {
                        LOGGER.info(() -> "Deleting old analytic store: " + FileUtil.getCanonicalPath(path));
                        FileUtil.deleteDir(path);
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
            }
        }
    }

    private String getAnalyticStoreDir(final QueryKey queryKey,
                                       final String componentId) {
        final String uuid = queryKey.getUuid() + "_" + componentId;
        // Make safe for the file system.
        return uuid.replaceAll("[^A-Za-z0-9]", "_");
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

    private Set<String> getExpectedAnalyticStoreDirs(final List<AnalyticRuleDoc> currentRules) {
        final Set<String> expectedDirs = new HashSet<>();
        currentRules.forEach(analyticRuleDoc -> {
            try {
                final SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(analyticRuleDoc);
                final String componentId = getComponentId(searchRequest);
                expectedDirs.add(getAnalyticStoreDir(searchRequest.getKey(), componentId));
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
            }
        });
        return expectedDirs;
    }

    public AnalyticDataStore get(final AnalyticRuleDoc analyticRuleDoc) {
        return dataStoreCache.computeIfAbsent(analyticRuleDoc, k -> {
            final SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(k);
            final DocRef dataSource = searchRequest.getQuery().getDataSource();
            if (dataSource == null || !ViewDoc.TYPE.equals(dataSource.getType())) {
                LOGGER.error("Rule needs to reference a view");
                throw new RuntimeException("Rule needs to reference a view");
            }
            final LmdbDataStore lmdbDataStore = createStore(searchRequest);
            return new AnalyticDataStore(searchRequest, lmdbDataStore);
        });
    }

    public Optional<AnalyticDataStore> getIfExists(final AnalyticRuleDoc analyticRuleDoc) {
        AnalyticDataStore analyticDataStore = dataStoreCache.get(analyticRuleDoc);
        if (analyticDataStore == null) {
            final SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(analyticRuleDoc);
            final String componentId = getComponentId(searchRequest);
            final String dir = getAnalyticStoreDir(searchRequest.getKey(), componentId);
            final Path path = getAnalyticResultStoreDir().resolve(dir);
            if (Files.isDirectory(path)) {
                analyticDataStore = dataStoreCache.computeIfAbsent(analyticRuleDoc, k -> {
                    final DocRef dataSource = searchRequest.getQuery().getDataSource();
                    if (dataSource == null || !ViewDoc.TYPE.equals(dataSource.getType())) {
                        LOGGER.error("Rule needs to reference a view");
                        throw new RuntimeException("Rule needs to reference a view");
                    }
                    final LmdbDataStore lmdbDataStore = createStore(searchRequest);
                    return new AnalyticDataStore(searchRequest, lmdbDataStore);
                });
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
