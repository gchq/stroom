package stroom.alert.impl;


import stroom.alert.api.AlertManager;


import stroom.alert.api.AlertProcessor;
import stroom.dashboard.impl.DashboardStore;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class AlertManagerImpl implements AlertManager {

    final ExplorerNodeService explorerNodeService;
    final DashboardStore dashboardStore;

    @Inject
    AlertManagerImpl (final ExplorerNodeService explorerNodeService, final DashboardStore dashboardStore){
        this.explorerNodeService = explorerNodeService;
        this.dashboardStore = dashboardStore;
    }

    @Override
    public AlertProcessor createAlertProcessor(final List<String> rulesFolderPath) {
        return new AlertProcessorImpl(rulesFolderPath, explorerNodeService, dashboardStore);
    }
}
