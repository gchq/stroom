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
import stroom.cluster.api.ClusterCallService;
import stroom.entity.shared.EntityServiceException;
import stroom.lifecycle.StroomBeanStore;
import stroom.node.NodeCache;
import stroom.node.shared.Node;
import stroom.security.Security;
import stroom.util.logging.LogExecutionTime;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Basic implementation of ClusterCallService that calls the local service by
 * bean name.
 */
class ClusterCallServiceLocal implements ClusterCallService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterCallServiceLocal.class);

    private final StroomBeanStore beanStore;
    private final NodeCache nodeCache;
    private final Security security;

    @Inject
    ClusterCallServiceLocal(final StroomBeanStore beanStore,
                            final NodeCache nodeCache,
                            final Security security) {
        this.beanStore = beanStore;
        this.nodeCache = nodeCache;
        this.security = security;
    }

    @Override
    public Object call(final Node sourceNode, final Node targetNode, final String beanName, final String methodName, final Class<?>[] parameterTypes, final Object[] args) {
        return security.insecureResult(() -> {
            final LogExecutionTime logExecutionTime = new LogExecutionTime();

            final Node thisNode = nodeCache.getDefaultNode();
            if (!targetNode.equals(thisNode)) {
                throw new EntityServiceException("Something wrong with routing rules as we have just had a request for "
                        + targetNode.getName() + " when we are " + thisNode.getName());
            }

            try {
                final Object service = beanStore.getInstance(beanName);
                final Method method = service.getClass().getMethod(methodName, parameterTypes);

                return method.invoke(service, args);
            } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(ClusterCallUtil.logString("call() - remoting ", sourceNode, targetNode, beanName,
                            methodName, logExecutionTime.getDuration()));
                }
            }
        });
    }
}
