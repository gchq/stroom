package stroom.analytics.impl;

import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.docref.DocRef;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb.LmdbEnvFactory;
import stroom.lmdb.LmdbEnvFactory.SimpleEnvBuilder;
import stroom.node.api.NodeInfo;
import stroom.query.api.v2.FindResultStoreCriteria;
import stroom.query.api.v2.ParamUtil;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.ResultStoreInfo;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.AbstractResultStoreConfig;
import stroom.query.common.v2.AnalyticResultStoreConfig;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.ErrorConsumerImpl;
import stroom.query.common.v2.HasResultStoreInfo;
import stroom.query.common.v2.LmdbDataStore;
import stroom.query.common.v2.Serialisers;
import stroom.security.api.SecurityContext;
import stroom.util.NullSafe;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;
import stroom.view.shared.ViewDoc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class AnalyticDataStores implements HasResultStoreInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticDataStores.class);

    private final LmdbEnvFactory lmdbEnvFactory;
    private final Provider<AnalyticResultStoreConfig> analyticStoreConfigProvider;
    private final AnalyticRuleStore analyticRuleStore;
    private final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper;
    private final Provider<Executor> executorProvider;
    private final Path analyticResultStoreDir;
    private final Map<AnalyticRuleDoc, AnalyticDataStore> dataStoreCache;
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;

    @Inject
    public AnalyticDataStores(final LmdbEnvFactory lmdbEnvFactory,
                              final PathCreator pathCreator,
                              final AnalyticRuleStore analyticRuleStore,
                              final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper,
                              final Provider<AnalyticResultStoreConfig> analyticStoreConfigProvider,
                              final Provider<Executor> executorProvider,
                              final NodeInfo nodeInfo,
                              final SecurityContext securityContext) {
        this.lmdbEnvFactory = lmdbEnvFactory;
        this.analyticRuleStore = analyticRuleStore;
        this.analyticStoreConfigProvider = analyticStoreConfigProvider;
        this.analyticRuleSearchRequestHelper = analyticRuleSearchRequestHelper;
        this.executorProvider = executorProvider;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;

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
        final Set<AnalyticRuleDoc> currentRules = getCurrentRules();
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

    private Set<String> getExpectedAnalyticStoreDirs(final Set<AnalyticRuleDoc> currentRules) {
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

    private Set<AnalyticRuleDoc> getCurrentRules() {
        final Set<AnalyticRuleDoc> currentRules = new HashSet<>();
        final List<DocRef> docRefList = analyticRuleStore.list();
        for (final DocRef docRef : docRefList) {
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

    public AnalyticDataStore get(final AnalyticRuleDoc analyticRuleDoc) {
        return dataStoreCache.computeIfAbsent(analyticRuleDoc, k -> {
            final SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(k);
            final DocRef dataSource = searchRequest.getQuery().getDataSource();
            if (dataSource == null || !ViewDoc.DOCUMENT_TYPE.equals(dataSource.getType())) {
                LOGGER.error("Rule needs to reference a view");
                throw new RuntimeException("Rule needs to reference a view");
            }
            final LmdbDataStore lmdbDataStore = createStore(searchRequest);
            return new AnalyticDataStore(searchRequest, lmdbDataStore);
        });
    }

    private LmdbDataStore createStore(final SearchRequest searchRequest) {
        final DocRef dataSource = searchRequest.getQuery().getDataSource();
        if (dataSource == null || !ViewDoc.DOCUMENT_TYPE.equals(dataSource.getType())) {
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
        return createAnalyticLmdbDataStore(
                searchRequest.getKey(),
                componentId,
                tableSettings,
                fieldIndex,
                paramMap,
                dataStoreSettings,
                errorConsumer);
    }

    private LmdbDataStore createAnalyticLmdbDataStore(final QueryKey queryKey,
                                                      final String componentId,
                                                      final TableSettings tableSettings,
                                                      final FieldIndex fieldIndex,
                                                      final Map<String, String> paramMap,
                                                      final DataStoreSettings dataStoreSettings,
                                                      final ErrorConsumer errorConsumer) {
        final AnalyticResultStoreConfig storeConfig = analyticStoreConfigProvider.get();

        final String subDirectory = getAnalyticStoreDir(queryKey, componentId);
        final SimpleEnvBuilder lmdbEnvBuilder = lmdbEnvFactory
                .builder(storeConfig.getLmdbConfig())
                .withSubDirectory(subDirectory);

        return new LmdbDataStore(
                new Serialisers(storeConfig),
                lmdbEnvBuilder,
                storeConfig,
                queryKey,
                componentId,
                tableSettings,
                fieldIndex,
                paramMap,
                dataStoreSettings,
                executorProvider,
                errorConsumer);
    }

    @Override
    public ResultPage<ResultStoreInfo> find(final FindResultStoreCriteria criteria) {
        final List<ResultStoreInfo> list = new ArrayList<>();
        final Set<AnalyticRuleDoc> currentRules = getCurrentRules();
        currentRules.forEach(analyticRuleDoc -> {
            try {
                final SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(analyticRuleDoc);
                final String componentId = getComponentId(searchRequest);
                final String dir = getAnalyticStoreDir(searchRequest.getKey(), componentId);
                final Path path = analyticResultStoreDir.resolve(dir);
                if (Files.isDirectory(path)) {
                    if (securityContext.isAdmin() || analyticRuleDoc.getCreateUser().equals(securityContext.getUserId())) {
                        list.add(new ResultStoreInfo(
                                new SearchRequestSource(SourceType.ANALYTIC_RULE, analyticRuleDoc.getUuid(), null),
                                searchRequest.getKey(),
                                analyticRuleDoc.getCreateUser(),
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
                LOGGER.debug(e::getMessage, e);
            }
        });

        return new ResultPage<>(list);
    }

    private String getComponentId(final SearchRequest searchRequest) {
        for (final ResultRequest resultRequest : searchRequest.getResultRequests()) {
            if (resultRequest.getMappings() != null && resultRequest.getMappings().size() > 0) {
                return resultRequest.getComponentId();
            }
        }
        return null;
    }

    private TableSettings getTableSettings(final SearchRequest searchRequest) {
        for (final ResultRequest resultRequest : searchRequest.getResultRequests()) {
            if (resultRequest.getMappings() != null && resultRequest.getMappings().size() > 0) {
                return resultRequest.getMappings().get(0);
            }
        }
        return null;
    }

    public record AnalyticDataStore(SearchRequest searchRequest, LmdbDataStore lmdbDataStore) {

    }
}
