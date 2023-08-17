package stroom.analytics.impl;

import stroom.analytics.impl.AnalyticDataStores.AnalyticDataStore;
import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.analytics.shared.AnalyticProcessorFilter;
import stroom.analytics.shared.AnalyticProcessorFilterTracker;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleType;
import stroom.docref.DocRef;
import stroom.node.api.NodeInfo;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.CurrentDbState;
import stroom.query.common.v2.LmdbDataStore;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.view.impl.ViewStore;
import stroom.view.shared.ViewDoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Inject;

public class AnalyticLoader {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticLoader.class);

    private final AnalyticRuleStore analyticRuleStore;
    private final AnalyticProcessorFilterDao analyticProcessorFilterDao;
    private final AnalyticProcessorFilterTrackerDao analyticProcessorFilterTrackerDao;
    private final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper;
    private final AnalyticDataStores analyticDataStores;
    private final TaskContextFactory taskContextFactory;
    private final NodeInfo nodeInfo;
    private final ViewStore viewStore;

    @Inject
    public AnalyticLoader(final AnalyticRuleStore analyticRuleStore,
                          final AnalyticProcessorFilterDao analyticProcessorFilterDao,
                          final AnalyticProcessorFilterTrackerDao analyticProcessorFilterTrackerDao,
                          final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper,
                          final AnalyticDataStores analyticDataStores,
                          final TaskContextFactory taskContextFactory,
                          final NodeInfo nodeInfo,
                          final ViewStore viewStore) {
        this.analyticRuleStore = analyticRuleStore;
        this.analyticProcessorFilterDao = analyticProcessorFilterDao;
        this.analyticProcessorFilterTrackerDao = analyticProcessorFilterTrackerDao;
        this.analyticRuleSearchRequestHelper = analyticRuleSearchRequestHelper;
        this.analyticDataStores = analyticDataStores;
        this.taskContextFactory = taskContextFactory;
        this.nodeInfo = nodeInfo;
        this.viewStore = viewStore;
    }

    public List<LoadedAnalytic> loadAnalyticRules(final Set<AnalyticRuleType> types) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        info(() -> "Loading rules");
        final List<LoadedAnalytic> loadedAnalyticList = new ArrayList<>();
        final List<DocRef> docRefList = analyticRuleStore.list();
        for (final DocRef docRef : docRefList) {
            final AnalyticRuleDoc analyticRuleDoc = analyticRuleStore.readDocument(docRef);
            if (analyticRuleDoc != null) {
                final Optional<AnalyticProcessorFilter> optionalFilter =
                        analyticProcessorFilterDao.getByAnalyticUuid(analyticRuleDoc.getUuid());
                optionalFilter.ifPresent(filter -> {
                    if (filter.isEnabled() &&
                            nodeInfo.getThisNodeName().equals(filter.getNode()) &&
                            types.contains(analyticRuleDoc.getAnalyticRuleType())) {
                        final AnalyticProcessorFilterTracker tracker = getFilterTracker(filter);
                        final AnalyticProcessorFilterTracker.Builder trackerBuilder = tracker.copy();
                        try {
                            ViewDoc viewDoc = null;

                            // Try and get view.
                            final String ruleIdentity = getAnalyticRuleIdentity(analyticRuleDoc);
                            final SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(analyticRuleDoc);
                            final DocRef dataSource = searchRequest.getQuery().getDataSource();

                            if (dataSource == null || !ViewDoc.DOCUMENT_TYPE.equals(dataSource.getType())) {
                                trackerBuilder.message("Error: Rule needs to reference a view").build();

                            } else {
                                // Load view.
                                viewDoc = loadViewDoc(ruleIdentity, dataSource);

                                // Update tracker state from LMDB if we are using LMDB.
                                if (useLmdb(analyticRuleDoc)) {
                                    final AnalyticDataStore dataStore = analyticDataStores.get(analyticRuleDoc);

                                    // Get or create LMDB data store.
                                    final LmdbDataStore lmdbDataStore = dataStore.lmdbDataStore();
                                    final CurrentDbState currentDbState = lmdbDataStore.sync();

                                    // Establish the analytic tracker state.
                                    updateTrackerWithLmdbState(trackerBuilder, currentDbState);
                                }
                            }

                            loadedAnalyticList.add(new LoadedAnalytic(
                                    ruleIdentity,
                                    analyticRuleDoc,
                                    filter,
                                    trackerBuilder,
                                    searchRequest,
                                    viewDoc));

                        } catch (final RuntimeException e) {
                            LOGGER.debug(e.getMessage(), e);
                            try {
                                trackerBuilder.message(e.getMessage());
                                analyticProcessorFilterTrackerDao.update(trackerBuilder.build());
                            } catch (final RuntimeException e2) {
                                LOGGER.error(e2::getMessage, e2);
                            }
                        }
                    }
                });
            }
        }
        info(() -> LogUtil.message("Finished loading rules in {}", logExecutionTime));
        return loadedAnalyticList;
    }

    private ViewDoc loadViewDoc(final String ruleIdentity,
                                final DocRef viewDocRef) {
        final ViewDoc viewDoc = viewStore.readDocument(viewDocRef);
        if (viewDoc == null) {
            throw new RuntimeException("Unable to process analytic: " +
                    ruleIdentity +
                    " because selected view cannot be found");
        }
        if (viewDoc.getPipeline() == null) {
            throw new RuntimeException("Unable to process analytic: " +
                    ruleIdentity +
                    " because view does not specify a pipeline");
        }
        return viewDoc;
    }

    private String getAnalyticRuleIdentity(final AnalyticRuleDoc analyticRuleDoc) {
        return analyticRuleDoc.getName() +
                " (" +
                analyticRuleDoc.getUuid() +
                ")";
    }

    private AnalyticProcessorFilterTracker getFilterTracker(final AnalyticProcessorFilter filter) {
        Optional<AnalyticProcessorFilterTracker> optionalTracker =
                analyticProcessorFilterTrackerDao.get(filter.getUuid());
        while (optionalTracker.isEmpty()) {
            final AnalyticProcessorFilterTracker tracker = AnalyticProcessorFilterTracker.builder()
                    .filterUuid(filter.getUuid())
                    .build();
            analyticProcessorFilterTrackerDao.create(tracker);
            optionalTracker = analyticProcessorFilterTrackerDao.get(filter.getUuid());
        }
        return optionalTracker.get();
    }

    private void updateTrackerWithLmdbState(final AnalyticProcessorFilterTracker.Builder trackerBuilder,
                                            final CurrentDbState currentDbState) {
        if (currentDbState != null) {
            trackerBuilder
                    .lastMetaId(currentDbState.getStreamId())
                    .lastEventId(currentDbState.getEventId())
                    .lastEventTime(currentDbState.getLastEventTime());
        }
    }

    private void info(final Supplier<String> messageSupplier) {
        LOGGER.info(messageSupplier);
        taskContextFactory.current().info(messageSupplier);
    }

    private boolean useLmdb(final AnalyticRuleDoc analyticRuleDoc) {
        return analyticRuleDoc.getAnalyticRuleType() == AnalyticRuleType.TABLE_BUILDER ||
                analyticRuleDoc.getAnalyticRuleType() == null;
    }
}
