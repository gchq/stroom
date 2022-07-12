package stroom.cluster.api;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Response;

public interface RemoteRestService {

    /**
     * Call out to the specified member using the rest request defined by fullPath and
     * responseBuilderFunc, if member is this member then use localSupplier.
     *
     * @param member              The cluster member to call
     * @param responseType        The type of the result that will be returned.
     * @param fullPathSupplier    A supplier of the full path to use for a remote rest call, if needed, e.g.
     *                            /api/permission/changeEvent/v1/fireChange
     * @param localSupplier       The supplier of the result if this member matches member
     * @param responseBuilderFunc A function to use the provided {@link Invocation.Builder} to execute a REST
     *                            call and get the result from a member.
     */
    default <T_RESP> T_RESP remoteRestResult(final ClusterMember member,
                                             final Class<T_RESP> responseType,
                                             final Supplier<String> fullPathSupplier,
                                             final Supplier<T_RESP> localSupplier,
                                             final Function<Builder, Response> responseBuilderFunc) {

        return remoteRestResult(
                member,
                fullPathSupplier,
                localSupplier,
                responseBuilderFunc,
                response ->
                        response.readEntity(responseType),
                Collections.emptyMap());

    }

    /**
     * Call out to the specified member using the rest request defined by fullPath and
     * responseBuilderFunc, if member is this member then use localSupplier.
     *
     * @param member              The cluster member to call
     * @param responseType        The type of the result that will be returned.
     * @param fullPathSupplier    A supplier of the full path to use for a remote rest call, if needed, e.g.
     *                            /api/permission/changeEvent/v1/fireChange
     * @param localSupplier       The supplier of the result if this member matches member
     * @param responseBuilderFunc A function to use the provided {@link Invocation.Builder} to execute a REST
     *                            call and get the result from a member.
     */
    default <T_RESP> T_RESP remoteRestResult(final ClusterMember member,
                                             final Class<T_RESP> responseType,
                                             final Supplier<String> fullPathSupplier,
                                             final Supplier<T_RESP> localSupplier,
                                             final Function<Invocation.Builder, Response> responseBuilderFunc,
                                             final Map<String, Object> queryParams) {

        return remoteRestResult(
                member,
                fullPathSupplier,
                localSupplier,
                responseBuilderFunc,
                response ->
                        response.readEntity(responseType),
                queryParams);

    }


    /**
     * Call out to the specified member using the rest request defined by fullPath and
     * responseBuilderFunc, if member is this member then use localSupplier.
     *
     * @param member              The cluster member to call
     * @param fullPathSupplier    A supplier of the full path to use for a remote rest call, if needed, e.g.
     *                            /api/permission/changeEvent/v1/fireChange
     * @param localSupplier       The supplier of the result if this member matches member
     * @param responseBuilderFunc A function to use the provided {@link Invocation.Builder} to execute a REST
     *                            call and get the result from a member where member is not equal to this.
     * @param responseMapper      A function to map the response of the local supplier or rest call to into another
     *                            type.
     */
    default <T_RESP> T_RESP remoteRestResult(final ClusterMember member,
                                             final Supplier<String> fullPathSupplier,
                                             final Supplier<T_RESP> localSupplier,
                                             final Function<Invocation.Builder, Response> responseBuilderFunc,
                                             final Function<Response, T_RESP> responseMapper) {
        return remoteRestResult(
                member,
                fullPathSupplier,
                localSupplier,
                responseBuilderFunc,
                responseMapper,
                Collections.emptyMap());
    }

    /**
     * Call out to the specified member using the rest request defined by fullPath and
     * responseBuilderFunc, if member is this member then use localSupplier.
     *
     * @param member              The cluster member to call
     * @param fullPathSupplier    A supplier of the full path to use for a remote rest call, if needed, e.g.
     *                            /api/permission/changeEvent/v1/fireChange
     * @param localSupplier       The supplier of the result if this member matches member
     * @param responseBuilderFunc A function to use the provided {@link Invocation.Builder} to execute a REST
     *                            call and get the result from a member where member is not equal to this.
     * @param responseMapper      A function to map the response of the local supplier or rest call to into another
     *                            type.
     */
    <T_RESP> T_RESP remoteRestResult(final ClusterMember member,
                                     final Supplier<String> fullPathSupplier,
                                     final Supplier<T_RESP> localSupplier,
                                     final Function<Invocation.Builder, Response> responseBuilderFunc,
                                     final Function<Response, T_RESP> responseMapper,
                                     final Map<String, Object> queryParams);

    /**
     * Call out to the specified member using the rest request defined by fullPath and
     * responseBuilderFunc, if member is this member then use localRunnable.
     *
     * @param member              The cluster member to call
     * @param fullPathSupplier    A supplier of the full path to use for a remote rest call, if needed, e.g.
     *                            /api/permission/changeEvent/v1/fireChange
     * @param localRunnable       The local code to run if this member matches member
     * @param responseBuilderFunc A function to use the provided {@link Invocation.Builder} to execute a REST
     *                            call and get the result from a member.
     */
    default void remoteRestCall(final ClusterMember member,
                                final Supplier<String> fullPathSupplier,
                                final Runnable localRunnable,
                                final Function<Invocation.Builder, Response> responseBuilderFunc) {
        remoteRestCall(member, fullPathSupplier, localRunnable, responseBuilderFunc, Collections.emptyMap());
    }

    /**
     * Call out to the specified member using the rest request defined by fullPath and
     * responseBuilderFunc, if member is this member then use localRunnable.
     *
     * @param member              The cluster member to call
     * @param fullPathSupplier    A supplier of the full path to use for a remote rest call, if needed, e.g.
     *                            /api/permission/changeEvent/v1/fireChange
     * @param localRunnable       The local code to run if this member matches member
     * @param responseBuilderFunc A function to use the provided {@link Invocation.Builder} to execute a REST
     *                            call and get the result from a member.
     */
    void remoteRestCall(final ClusterMember member,
                        final Supplier<String> fullPathSupplier,
                        final Runnable localRunnable,
                        final Function<Invocation.Builder, Response> responseBuilderFunc,
                        final Map<String, Object> queryParams);
}
