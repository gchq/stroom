package stroom.cluster.mock;

import stroom.cluster.api.ClusterMember;
import stroom.cluster.api.RemoteRestService;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Response;

public class MockRemoteRestService implements RemoteRestService {

    @Override
    public <T_RESP> T_RESP remoteRestResult(final ClusterMember member,
                                            final Class<T_RESP> responseType,
                                            final Supplier<String> fullPathSupplier,
                                            final Supplier<T_RESP> localSupplier,
                                            final Function<Builder, Response> responseBuilderFunc) {
        return RemoteRestService.super.remoteRestResult(member,
                responseType,
                fullPathSupplier,
                localSupplier,
                responseBuilderFunc);
    }

    @Override
    public <T_RESP> T_RESP remoteRestResult(final ClusterMember member,
                                            final Class<T_RESP> responseType,
                                            final Supplier<String> fullPathSupplier,
                                            final Supplier<T_RESP> localSupplier,
                                            final Function<Builder, Response> responseBuilderFunc,
                                            final Map<String, Object> queryParams) {
        return RemoteRestService.super.remoteRestResult(member,
                responseType,
                fullPathSupplier,
                localSupplier,
                responseBuilderFunc,
                queryParams);
    }

    @Override
    public <T_RESP> T_RESP remoteRestResult(final ClusterMember member,
                                            final Supplier<String> fullPathSupplier,
                                            final Supplier<T_RESP> localSupplier,
                                            final Function<Builder, Response> responseBuilderFunc,
                                            final Function<Response, T_RESP> responseMapper) {
        return RemoteRestService.super.remoteRestResult(member,
                fullPathSupplier,
                localSupplier,
                responseBuilderFunc,
                responseMapper);
    }

    @Override
    public <T_RESP> T_RESP remoteRestResult(final ClusterMember member,
                                            final Supplier<String> fullPathSupplier,
                                            final Supplier<T_RESP> localSupplier,
                                            final Function<Builder, Response> responseBuilderFunc,
                                            final Function<Response, T_RESP> responseMapper,
                                            final Map<String, Object> queryParams) {
        return null;
    }

    @Override
    public void remoteRestCall(final ClusterMember member,
                               final Supplier<String> fullPathSupplier,
                               final Runnable localRunnable,
                               final Function<Builder, Response> responseBuilderFunc) {
        RemoteRestService.super.remoteRestCall(member, fullPathSupplier, localRunnable, responseBuilderFunc);
    }

    @Override
    public void remoteRestCall(final ClusterMember member,
                               final Supplier<String> fullPathSupplier,
                               final Runnable localRunnable,
                               final Function<Builder, Response> responseBuilderFunc,
                               final Map<String, Object> queryParams) {

    }
}
