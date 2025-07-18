package stroom.app.metrics;

import stroom.config.common.NodeUriConfig;
import stroom.config.common.PublicUriConfig;
import stroom.dropwizard.common.prometheus.AbstractAppInfoProvider;
import stroom.node.api.NodeInfo;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Map;

public class StroomAppInfoProvider extends AbstractAppInfoProvider {

    public static final String NODE_NAME_KEY = "node_name";
    private final NodeInfo nodeInfo;
    private final Provider<PublicUriConfig> publicUriConfigProvider;
    private final Provider<NodeUriConfig> nodeUriConfigProvider;

    @Inject
    public StroomAppInfoProvider(final NodeInfo nodeInfo,
                                 final Provider<PublicUriConfig> publicUriConfigProvider,
                                 final Provider<NodeUriConfig> nodeUriConfigProvider) {
        this.nodeInfo = nodeInfo;
        this.publicUriConfigProvider = publicUriConfigProvider;
        this.nodeUriConfigProvider = nodeUriConfigProvider;
    }

    @Override
    protected Map<String, String> getAdditionalAppInfo() {
        return Map.of(
                NODE_NAME_KEY, nodeInfo.getThisNodeName(),
                "public_uri", publicUriConfigProvider.get().asUri(),
                "node_uri", nodeUriConfigProvider.get().asUri());
    }

    @Override
    public Map<String, String> getNodeLabels() {
        return Map.of(NODE_NAME_KEY, nodeInfo.getThisNodeName());
    }
}
