package stroom.ignite.mock;

import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.impl.MockClusterNodeManager;
import stroom.node.api.NodeInfo;

import com.google.inject.AbstractModule;

public class MockApacheIgniteModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ClusterNodeManager.class).to(MockClusterNodeManager.class);
        bind(NodeInfo.class).to(MockLocalhostNodeInfo.class);
    }
}
