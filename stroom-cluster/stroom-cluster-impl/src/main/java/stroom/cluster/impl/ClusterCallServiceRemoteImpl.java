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
 */

package stroom.cluster.impl;

import com.caucho.hessian.client.HessianProxyFactory;
import com.caucho.hessian.client.HessianRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.api.ClusterCallService;
import stroom.cluster.api.ClusterCallServiceRemote;
import stroom.cluster.api.ClusterConfig;
import stroom.cluster.api.ServiceName;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.security.api.UserIdentity;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ResourcePaths;
import stroom.util.time.StroomDuration;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Bean that can make remote calls to the cluster using a Hessian Proxy. It also
 * makes a local JVM call if the remote call is to it's self. This helps with
 * performance and testing.
 */
@Singleton
class ClusterCallServiceRemoteImpl implements ClusterCallServiceRemote {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterCallServiceRemoteImpl.class);

    private final NodeInfo nodeInfo;
    private final NodeService nodeService;
    private final Map<ServiceName, Provider<Object>> serviceMap;
    private final boolean clusterCallUseLocal;
    private final StroomDuration clusterCallReadTimeout;
    private final Map<String, ClusterCallService> proxyMap = new HashMap<>();

    private HessianProxyFactory proxyFactory = null;
    private boolean ignoreSSLHostnameVerifier;

    @Inject
    ClusterCallServiceRemoteImpl(final NodeInfo nodeInfo,
                                 final NodeService nodeService,
                                 final Map<ServiceName, Provider<Object>> serviceMap,
                                 final ClusterConfig clusterConfig) {
        this.nodeInfo = nodeInfo;
        this.nodeService = nodeService;
        this.serviceMap = serviceMap;
        this.clusterCallUseLocal = clusterConfig.isClusterCallUseLocal();
        this.clusterCallReadTimeout = clusterConfig.getClusterCallReadTimeout();
        this.ignoreSSLHostnameVerifier = clusterConfig.isClusterCallIgnoreSSLHostnameVerifier();
    }

    private HessianProxyFactory getProxyFactory() {
        if (proxyFactory == null) {
            // In Stroom when we talk to individual nodes in the cluster they present a certificate. For ease of
            // configuration with multiple nodes the certificate is often that of an alias. E.g. A server might
            // present stroom.some.server.domain.co.uk. If the name of this alias is different from the actual host that
            // is talking to us we would need to ignore verification of host names against the certificate and just
            // check that the certificate is OK.
            //
            // To control this behaviour the 'ignoreSSLHostnameVerifier' property must be set.
            final StroomHessianProxyFactory stroomHessianProxyFactory = new StroomHessianProxyFactory();
            stroomHessianProxyFactory.setIgnoreSSLHostnameVerifier(ignoreSSLHostnameVerifier);
            proxyFactory = stroomHessianProxyFactory;

            if (clusterCallReadTimeout != null) {
                proxyFactory.setReadTimeout(clusterCallReadTimeout.toMillis());
            }
        }
        return proxyFactory;
    }

    private ClusterCallService createHessianProxy(final String nodeName) throws MalformedURLException {
        final String nodeServiceUrl = nodeService.getBaseEndpointUrl(nodeName) +
            ResourcePaths.buildAuthenticatedServletPath(ResourcePaths.CLUSTER_CALL_RPC);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("createHessianProxy() - " + nodeName + " - " + nodeServiceUrl);
        }

        if (nodeServiceUrl == null || nodeServiceUrl.trim().length() == 0) {
            throw new MalformedURLException("No cluster call URL has been set for node: " + nodeName);
        }

        return (ClusterCallService) getProxyFactory().create(ClusterCallService.class, nodeServiceUrl);
    }

    @Override
    public Object call(final String sourceNode,
                       final String targetNode,
                       final UserIdentity userIdentity,
                       final ServiceName serviceName,
                       final String methodName,
                       final java.lang.Class<?>[] parameterTypes,
                       final Object[] args) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        Object result;

        if (targetNode == null) {
            throw new RuntimeException("Must have target node to call remote service");
        }

        // Make a local call ?
        boolean local = false;
        if (clusterCallUseLocal) {
            final String thisNodeName = nodeInfo.getThisNodeName();
            if (thisNodeName.equals(targetNode)) {
                local = true;
            }
        }

        if (local) {
            try {
                final Provider<Object> serviceProvider = serviceMap.get(serviceName);
                final Object service = serviceProvider.get();
                final Method method = service.getClass().getMethod(methodName, parameterTypes);
                result = method.invoke(service, args);
            } catch (final IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        } else {
            ClusterCallService api = proxyMap.get(targetNode);

            if (api == null) {
                try {
                    api = createHessianProxy(targetNode);
                } catch (final MalformedURLException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
                proxyMap.put(targetNode, api);
            }

            try {
                result = api.call(sourceNode, targetNode, userIdentity, serviceName, methodName, parameterTypes, args);
            } catch (final HessianRuntimeException e) {
                final String details = getCallDetails(sourceNode, targetNode, serviceName, methodName, args);
                if (e.getCause() != null && e.getCause() instanceof ConnectException) {
                    LOGGER.error("Unable to connect to '" + targetNode + "' " + e.getCause().getMessage() + details);
                } else {
                    LOGGER.error(e.getMessage() + details, e);
                }
                throw e;
            } catch (final RuntimeException e) {
                final String details = getCallDetails(sourceNode, targetNode, serviceName, methodName, args);
                LOGGER.error(e.getMessage() + details, e);
                throw e;
            }
        }

        if (LOGGER.isDebugEnabled()) {
            String api = "local";
            if (!local) {
                api = "remote";
            }

            LOGGER.debug(ClusterCallUtil.logString("call() - " + api, sourceNode, targetNode, serviceName, methodName,
                    logExecutionTime.getDuration()));
        }

        return result;
    }

    private String getCallDetails(final String sourceNodeName,
                                  final String targetNodeName,
                                  final ServiceName serviceName,
                                  final String methodName,
                                  final Object[] args) {
        final StringBuilder sb = new StringBuilder();
        try {
            if (sourceNodeName != null) {
                sb.append("\n\tsourceNode: ");
                sb.append(sourceNodeName);
            }
            if (targetNodeName != null) {
                sb.append("\n\ttargetNode: ");
                sb.append(targetNodeName);
            }
            if (serviceName != null) {
                sb.append("\n\tserviceName: ");
                sb.append(serviceName);
            }
            if (methodName != null) {
                sb.append("\n\tmethodName: ");
                sb.append(methodName);
            }
            if (args != null) {
                sb.append("\n\targs: ");
                sb.append(getArgsString(args));
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return sb.toString();
    }

    private String getArgsString(final Object[] args) {
        final StringBuilder sb = new StringBuilder();
        for (final Object arg : args) {
            if (arg == null) {
                sb.append("null, ");
            } else {
                try {
                    sb.append(arg.toString());
                    sb.append(", ");
                } catch (final RuntimeException e) {
                    LOGGER.error("Error appending arg for: " + arg.getClass().getSimpleName(), e);
                }
            }
        }

        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }
}
