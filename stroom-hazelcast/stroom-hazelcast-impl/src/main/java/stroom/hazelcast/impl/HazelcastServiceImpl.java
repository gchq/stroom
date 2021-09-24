package stroom.hazelcast.impl;

import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.api.ClusterState;
import stroom.hazelcast.api.HazelcastService;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.crdt.pncounter.PNCounter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class HazelcastServiceImpl implements HazelcastService {

    public static String HAZELCAST_CLUSTER_NAME = "Stroom-Hazelcast";

    private final HazelcastInstance instance;

    @Inject
    HazelcastServiceImpl (ClusterNodeManager nodeManager) {
        ClusterState clusterState = nodeManager.getClusterState();
        instance = initHazelcast(clusterState.getEnabledActiveNodes().stream().collect(Collectors.toList()));
    }

    @Override
    public Long incrementAndGetCounter(final long amount, final String keyType, final String key,
                                        final Instant timestamp, final Duration resolution) {

        final String counterName = keyType + key + resolution.toString();

        final PNCounter counter = instance.getPNCounter(counterName);

        final long newValue = counter.addAndGet(amount);

        return newValue;
    }

    private HazelcastInstance initHazelcast(List<String> clusterMembers) {

        Config config = new Config();

        config.setInstanceName(HAZELCAST_CLUSTER_NAME);
        JoinConfig joinConfig = config.getNetworkConfig().getJoin();

        // Need to disable the default (multicast) member join method
        joinConfig.getMulticastConfig().setEnabled(false);

        // Now the TCP method can be enabled.
        TcpIpConfig tcpIpConfig = joinConfig.getTcpIpConfig();

        tcpIpConfig.setMembers(clusterMembers);
        tcpIpConfig.setEnabled(true);


        return Hazelcast.getOrCreateHazelcastInstance(config);
    }
}
