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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.api.ClusterCallServiceLocal;
import stroom.cluster.api.ServiceName;
import stroom.util.shared.EntityServiceException;
import stroom.node.api.NodeInfo;
import stroom.security.api.Security;
import stroom.util.logging.LogExecutionTime;

import javax.inject.Inject;
import javax.inject.Provider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Basic implementation of ClusterCallService that calls the local service by
 * bean name.
 */
class ClusterCallServiceLocalImpl implements ClusterCallServiceLocal {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterCallServiceLocalImpl.class);

    private final Map<ServiceName, Provider<Object>> serviceMap;
    private final NodeInfo nodeInfo;
    private final Security security;

    @Inject
    ClusterCallServiceLocalImpl(final Map<ServiceName, Provider<Object>> serviceMap,
                                final NodeInfo nodeInfo,
                                final Security security) {
        this.serviceMap = serviceMap;
        this.nodeInfo = nodeInfo;
        this.security = security;
    }

    @Override
    public Object call(final String sourceNode, final String targetNode, final ServiceName serviceName, final String methodName, final Class<?>[] parameterTypes, final Object[] args) {
        return security.insecureResult(() -> {
            final LogExecutionTime logExecutionTime = new LogExecutionTime();

            final String thisNodeName = nodeInfo.getThisNodeName();
            if (!targetNode.equals(thisNodeName)) {
                throw new EntityServiceException("Something wrong with routing rules as we have just had a request for "
                        + targetNode + " when we are " + thisNodeName);
            }

            try {
                final Provider<Object> serviceProvider = serviceMap.get(serviceName);
                final Object service = serviceProvider.get();
                final Method method = service.getClass().getMethod(methodName, parameterTypes);

                return method.invoke(service, args);
            } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(ClusterCallUtil.logString("call() - remoting ", sourceNode, targetNode, serviceName,
                            methodName, logExecutionTime.getDuration()));
                }
            }
        });
    }
}
