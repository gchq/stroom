package stroom.alert.impl;


import stroom.alert.api.AlertManager;


import stroom.alert.api.AlertProcessor;
import stroom.dashboard.impl.DashboardStore;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.index.impl.IndexStructure;
import stroom.index.impl.IndexStructureCache;
import stroom.search.impl.SearchConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;

@Singleton
public class AlertManagerImpl implements AlertManager {

    private final ExplorerNodeService explorerNodeService;
    private final DashboardStore dashboardStore;
    private final int maxBooleanClauseCount;
    private final WordListProvider wordListProvider;
    private final IndexStructureCache indexStructureCache;

    @Inject
    AlertManagerImpl (final ExplorerNodeService explorerNodeService,
                      final DashboardStore dashboardStore,
                      final WordListProvider wordListProvider,
                      final SearchConfig searchConfig,
                      final IndexStructureCache indexStructureCache){
        this.explorerNodeService = explorerNodeService;
        this.dashboardStore = dashboardStore;
        this.wordListProvider = wordListProvider;
        this.maxBooleanClauseCount = searchConfig.getMaxBooleanClauseCount();
        this.indexStructureCache = indexStructureCache;
    }

    @Override
    public AlertProcessor createAlertProcessor(final DocRef indexDocRef) {
        IndexStructure indexStructure = indexStructureCache.get(indexDocRef);

        if (indexStructure == null)
            throw new IllegalStateException("Unable to load index " + indexDocRef);

        final String [] path = {"Rules","Active"};
        final List<String> rulesFolderPath = Arrays.asList(path);

        //todo give the path to indexDoc
        return new AlertProcessorImpl(rulesFolderPath, indexStructure, explorerNodeService,
                dashboardStore, wordListProvider, maxBooleanClauseCount);
    }
}
