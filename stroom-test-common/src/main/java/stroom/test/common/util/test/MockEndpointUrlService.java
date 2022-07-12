package stroom.test.common.util.test;

import stroom.cluster.api.ClusterMember;
import stroom.cluster.api.EndpointUrlService;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest.TestMember;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MockEndpointUrlService implements EndpointUrlService {

    private final TestMember local;
    private final Map<ClusterMember, TestMember> baseEndPointUrl;

    public MockEndpointUrlService(final TestMember local,
                                  final List<TestMember> baseEndPointUrl) {
        this.local = local;
        this.baseEndPointUrl = baseEndPointUrl
                .stream()
                .collect(Collectors.toMap(TestMember::getMember, Function.identity()));
    }

    @Override
    public String getBaseEndpointUrl(final ClusterMember member) {
        return baseEndPointUrl.get(member).getEndpointUrl();
    }

    @Override
    public String getRemoteEndpointUrl(final ClusterMember member) {
        return baseEndPointUrl.get(member).getEndpointUrl();
    }

    @Override
    public boolean shouldExecuteLocally(final ClusterMember member) {
        return local.getMember().equals(member);
    }
}
