/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package stroom.node.api;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Response;

/**
 * <p>
 * Class to manage nodes.
 * </p>
 */
public interface NodeService {

    String getBaseEndpointUrl(String nodeName);

    boolean isEnabled(String nodeName);

    int getPriority(String nodeName);

    /**
     * @return A list of node names sorted by descending priority (i.e. 3, 2, 1)
     * then by node name to ensure a deterministic result when nodes have the same
     * priority
     */
    List<String> getEnabledNodesByPriority();

    List<String> findNodeNames(FindNodeCriteria criteria);

    /**
     * Call out to the specified node using the rest request defined by fullPath and
     * responseBuilderFunc, if nodeName is this node then use localSupplier.
     *
     * @param nodeName            The name of the node to call
     * @param responseType        The type of the result that will be returned.
     * @param fullPathSupplier    A supplier of the full path to use for a remote rest call, if needed, e.g.
     *                            /api/permission/changeEvent/v1/fireChange
     * @param localSupplier       The supplier of the result if this node matches nodeName
     * @param responseBuilderFunc A function to use the provided {@link Invocation.Builder} to execute a REST
     *                            call and get the result from a node where nodeName is not equal to this node's
     *                            name.
     */
    default <T_RESP> T_RESP remoteRestResult(final String nodeName,
                                             final Class<T_RESP> responseType,
                                             final Supplier<String> fullPathSupplier,
                                             final Supplier<T_RESP> localSupplier,
                                             final Function<Invocation.Builder, Response> responseBuilderFunc) {

        return remoteRestResult(
                nodeName,
                fullPathSupplier,
                localSupplier,
                responseBuilderFunc,
                response ->
                        response.readEntity(responseType));

    }


    /**
     * Call out to the specified node using the rest request defined by fullPath and
     * responseBuilderFunc, if nodeName is this node then use localSupplier.
     *
     * @param nodeName            The name of the node to call
     * @param fullPathSupplier    A supplier of the full path to use for a remote rest call, if needed, e.g.
     *                            /api/permission/changeEvent/v1/fireChange
     * @param localSupplier       The supplier of the result if this node matches nodeName
     * @param responseBuilderFunc A function to use the provided {@link Invocation.Builder} to execute a REST
     *                            call and get the result from a node where nodeName is not equal to this node's
     *                            name.
     * @param responseMapper      A function to map the response of the local supplier or rest call to into another
     *                            type.
     */
    <T_RESP> T_RESP remoteRestResult(final String nodeName,
                                     final Supplier<String> fullPathSupplier,
                                     final Supplier<T_RESP> localSupplier,
                                     final Function<Invocation.Builder, Response> responseBuilderFunc,
                                     final Function<Response, T_RESP> responseMapper);

    /**
     * Call out to the specified node using the rest request defined by fullPath and
     * responseBuilderFunc, if nodeName is this node then use localRunnable.
     *
     * @param nodeName            The name of the node to call
     * @param fullPathSupplier    A supplier of the full path to use for a remote rest call, if needed, e.g.
     *                            /api/permission/changeEvent/v1/fireChange
     * @param localRunnable       The local code to run if this node matches nodeName
     * @param responseBuilderFunc A function to use the provided {@link Invocation.Builder} to execute a REST
     *                            call and get the result from a node where nodeName is not equal to this node's
     *                            name.
     */
    void remoteRestCall(final String nodeName,
                        final Supplier<String> fullPathSupplier,
                        final Runnable localRunnable,
                        final Function<Builder, Response> responseBuilderFunc);

}
