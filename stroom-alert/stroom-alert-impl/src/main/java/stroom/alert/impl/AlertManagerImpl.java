package stroom.alert.impl;


import stroom.alert.api.AlertManager;


import stroom.alert.api.AlertProcessor;
import stroom.dashboard.impl.DashboardStore;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.search.impl.SearchConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class AlertManagerImpl implements AlertManager {

    private final ExplorerNodeService explorerNodeService;
    private final DashboardStore dashboardStore;
    private final int maxBooleanClauseCount;
    private final WordListProvider wordListProvider;

    @Inject
    AlertManagerImpl (final ExplorerNodeService explorerNodeService,
                      final DashboardStore dashboardStore,
                      final WordListProvider wordListProvider,
                      final SearchConfig searchConfig){
        this.explorerNodeService = explorerNodeService;
        this.dashboardStore = dashboardStore;
        this.wordListProvider = wordListProvider;
        this.maxBooleanClauseCount = searchConfig.getMaxBooleanClauseCount();
    }

    @Override
    public AlertProcessor createAlertProcessor(final List<String> rulesFolderPath) {
        return new AlertProcessorImpl(rulesFolderPath, explorerNodeService,
                dashboardStore, wordListProvider, maxBooleanClauseCount);
    }
}
