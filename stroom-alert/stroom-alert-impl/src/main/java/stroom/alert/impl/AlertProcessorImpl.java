package stroom.alert.impl;

import org.apache.lucene.document.Document;
import stroom.alert.api.AlertProcessor;
import stroom.dashboard.impl.DashboardStore;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;

import org.apache.lucene.index.memory.MemoryIndex;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class AlertProcessorImpl implements AlertProcessor {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AlertProcessorImpl.class);
    private final DocRef rulesFolder;
    private final ExplorerNodeService explorerNodeService;
    private final DashboardStore dashboardStore;

    public AlertProcessorImpl (final List<String> folderPath, final ExplorerNodeService explorerNodeService,
                               final DashboardStore dashboardStore){
        this.explorerNodeService = explorerNodeService;
        this.dashboardStore = dashboardStore;
        this.rulesFolder = findRulesFolder(folderPath);
        System.out.println("Creating AlertProcessorImpl");
        loadRules();
    }

    @Override
    public void createAlerts(Document document) {
        System.out.println("Alerting " + document + " with rules from " + rulesFolder);
    }

    private final DocRef findRulesFolder(List<String> folderPath){

        ExplorerNode currentNode = explorerNodeService.getRoot().get();

        if (currentNode == null){
            throw new IllegalStateException("Unable to find root explorer node.");
        }

        for (String name : folderPath) {
            List <ExplorerNode> matchingChildren = explorerNodeService.getNodesByName(currentNode, name).stream().filter
                    (explorerNode -> ExplorerConstants.FOLDER.equals(explorerNode.getDocRef().getType())).collect(Collectors.toList());

            if (matchingChildren.size() == 0){
                LOGGER.error(()->"Unable to find folder called " + name + " when opening rules path " + folderPath.stream().collect(Collectors.joining("/")));
                return null;
            } else if (matchingChildren.size() > 1){
                final ExplorerNode node = currentNode;
                LOGGER.warn(()->"There are multiple folders called " + name + " under " + node.getName()  +
                        " when opening rules path " + folderPath.stream().collect(Collectors.joining("/")) + " using first...");
            }
            currentNode = matchingChildren.get(0);
        }

        return currentNode.getDocRef();
    }

    private void loadRules(){
        if (rulesFolder == null)
            return;
        List<ExplorerNode> childNodes = explorerNodeService.getChildren(rulesFolder);
        for (ExplorerNode childNode : childNodes){
            if (DashboardDoc.DOCUMENT_TYPE.equals(childNode.getDocRef().getType())){
                DashboardDoc dashboard = dashboardStore.readDocument(childNode.getDocRef());
                final List<ComponentConfig> componentConfigs = dashboard.getDashboardConfig().getComponents();
                for (ComponentConfig componentConfig : componentConfigs){
                    if (componentConfig.getSettings() instanceof QueryComponentSettings){
                        QueryComponentSettings queryComponentSettings = (QueryComponentSettings) componentConfig.getSettings();
                        System.out.println("Found query " + queryComponentSettings.getExpression());
                    } else if (componentConfig.getSettings() instanceof TableComponentSettings){
                        TableComponentSettings tableComponentSettings = (TableComponentSettings) componentConfig.getSettings();
                        System.out.println ("Found table with " + tableComponentSettings.getFields().size()
                                +" fields for query " + tableComponentSettings.getQueryId());
                    }
                }
            }
        }
    }
}
