package stroom.cluster.impl;

import stroom.cluster.api.ClusterRole;
import stroom.cluster.api.ClusterService;
import stroom.cluster.api.NodeInfo;
import stroom.config.common.UriFactory;

import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.config.MemberAttributeConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.lock.FencedLock;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ClusterServiceImpl implements ClusterService {

    private static final String CPU_CORE_COUNT = "CPU_CORE_COUNT";
    private static final String NODE_NAME = "NODE_NAME";
    private static final String NODE_ENDPOINT_URL = "NODE_ENDPOINT_URL";

    private final HazelcastInstance instance;

    @Inject
    public ClusterServiceImpl(final NodeInfo nodeInfo,
                              final ClusterConfig clusterConfig,
                              final UriFactory uriFactory) {
        MemberAttributeConfig memberAttributeConfig = new MemberAttributeConfig();
        memberAttributeConfig
                .setAttribute(CPU_CORE_COUNT, String.valueOf(Runtime.getRuntime().availableProcessors()));
        memberAttributeConfig
                .setAttribute(NODE_NAME, nodeInfo.getThisNodeName());
        memberAttributeConfig
                .setAttribute(NODE_ENDPOINT_URL, uriFactory.nodeUri("").toString());

        for (final String clusterRole : clusterConfig.getClusterRoles()) {
            memberAttributeConfig
                    .setAttribute(clusterRole, Boolean.TRUE.toString());
        }

        Config conf = new Config();
        conf.setMemberAttributeConfig(memberAttributeConfig);
        instance = Hazelcast.newHazelcastInstance(conf);

        System.out.println(instance
                .getCluster()
                .getMembers());
    }

    private Member getLeaderMember() {
        return instance
                .getCluster()
                .getMembers()
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No cluster members exist"));
    }

    private Member getLocalMember() {
        return instance
                .getCluster()
                .getMembers()
                .stream()
                .filter(Member::localMember)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No cluster members exist"));
    }

    public String getBaseEndpointUrl(final String nodeName) {
        final List<Member> list = instance
                .getCluster()
                .getMembers()
                .stream()
                .filter(member -> {
                    final String attValue = member.getAttribute(NODE_NAME);
                    return nodeName.equals(attValue);
                })
                .toList();

        if (list.size() > 1) {
            throw new RuntimeException("More than one node found with name " + nodeName);
        } else if (list.size() == 1) {
            return getMemberUrl(list.get(0));
        }
        return null;
    }

    private String getMemberUrl(final Member member) {
        return member.getAttribute(NODE_ENDPOINT_URL);
    }

    private String getNodeName(final Member member) {
        return member.getAttribute("NODE_NAME");
    }

    @Override
    public String getLeader() {
        return getNodeName(getLeaderMember());
    }

    @Override
    public String getLocal() {
        return getNodeName(getLocalMember());
    }

    @Override
    public Set<String> getMembers() {
        return instance
                .getCluster()
                .getMembers()
                .stream()
                .map(this::getNodeName)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isLeader() {
        return getLeader().equals(getLocal());
    }

    @Override
    public boolean isLeaderForRole(final ClusterRole clusterRole) {
        return instance
                .getCluster()
                .getMembers()
                .stream()
                .filter(member -> {
                    final String attValue = member.getAttribute(clusterRole.getName());
                    return Boolean.parseBoolean(attValue);
                })
                .findFirst()
                .map(Member::localMember)
                .orElse(false);
    }

    @Override
    public void lock(final String lockName,
                     final Runnable runnable) {
        final FencedLock fencedLock = instance.getCPSubsystem().getLock(lockName);
        fencedLock.lock();
        try {
            runnable.run();
        } finally {
            fencedLock.unlock();
        }
    }

    @Override
    public void tryLock(final String lockName,
                        final Runnable runnable) {
        final FencedLock fencedLock = instance.getCPSubsystem().getLock(lockName);
        if (fencedLock.tryLock()) {
            try {
                runnable.run();
            } finally {
                fencedLock.unlock();
            }
        }
    }
}
