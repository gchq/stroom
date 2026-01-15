/*
 * Copyright 2016-2025 Crown Copyright
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
 */

package stroom.node.api;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;

import java.net.ConnectException;

public final class NodeCallUtil {

    private NodeCallUtil() {
    }

    /**
     * @return True if the work should be executed on the local node.
     * I.e. if nodeName equals the name of the local node
     */
    public static boolean shouldExecuteLocally(final NodeInfo nodeInfo,
                                               final String nodeName) {
        final String thisNodeName = nodeInfo.getThisNodeName();
        if (thisNodeName == null) {
            throw new RuntimeException("This node has no name");
        }

        // If this is the node that was contacted then just return our local info.
        return thisNodeName.equals(nodeName);
    }

    /**
     * @param nodeName The name of the node to get the base endpoint for
     * @return The base endpoint url for inter-node communications, e.g. http://some-fqdn:8080
     */
    public static String getBaseEndpointUrl(final NodeInfo nodeInfo,
                                            final NodeService nodeService,
                                            final String nodeName) {
        // A normal url is something like "http://fqdn:8080"
        final String url = nodeService.getBaseEndpointUrl(nodeName);

        if (url == null || url.isBlank()) {
            throw new RuntimeException("Remote node '" + nodeName + "' has no URL set");
        }

        final String thisNodeUrl = nodeService.getBaseEndpointUrl(nodeInfo.getThisNodeName());
        if (url.equals(thisNodeUrl)) {
            throw new RuntimeException("Remote node '" + nodeName + "' is using the same URL as this node");
        }

        return url;
    }

    public static RuntimeException handleExceptionsOnNodeCall(final String nodeName,
                                                              final String url,
                                                              final Throwable throwable) {
        if (throwable instanceof WebApplicationException) {
            throw (WebApplicationException) throwable;
        } else if (throwable instanceof ProcessingException) {
            if (throwable.getCause() != null && throwable.getCause() instanceof ConnectException) {
                return new NodeCallException(nodeName, url, throwable);
            } else {
                return new RuntimeException(throwable);
            }
        } else {
            return new RuntimeException(throwable);
        }
    }
}
