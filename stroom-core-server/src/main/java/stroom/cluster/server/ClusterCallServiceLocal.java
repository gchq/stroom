/*
 * Copyright 2016 Crown Copyright
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

package stroom.cluster.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.EntityServiceException;
import stroom.node.server.NodeCache;
import stroom.node.shared.Node;
import stroom.security.Insecure;
import stroom.util.logging.LogExecutionTime;
import stroom.util.spring.StroomBeanStore;
import stroom.util.thread.ThreadScopeContextHolder;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.lang.reflect.Method;

/**
 * Basic implementation of ClusterCallService that calls the local service by
 * bean name.
 */
@Component("clusterCallServiceLocal")
class ClusterCallServiceLocal implements ClusterCallService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterCallServiceLocal.class);

    private final StroomBeanStore beanStore;
    private final NodeCache nodeCache;

    @Inject
    ClusterCallServiceLocal(final StroomBeanStore beanStore, final NodeCache nodeCache) {
        this.beanStore = beanStore;
        this.nodeCache = nodeCache;
    }

    @Override
    @Insecure
    public Object call(final Node sourceNode, final Node targetNode, final String beanName, final String methodName,
                       final Class<?>[] parameterTypes, final Object[] args) throws Exception {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        final Node thisNode = nodeCache.getDefaultNode();
        if (!targetNode.equals(thisNode)) {
            throw new EntityServiceException("Something wrong with routing rules as we have just had a request for "
                    + targetNode.getName() + " when we are " + thisNode.getName());
        }

        try {
            ThreadScopeContextHolder.getContext().put("sourceNode", sourceNode);
            ThreadScopeContextHolder.getContext().put("targetNode", targetNode);

            final Object service = beanStore.getBean(beanName);
            final Method method = service.getClass().getMethod(methodName, parameterTypes);

            return method.invoke(service, args);

        } finally {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(ClusterCallUtil.logString("call() - remoting ", sourceNode, targetNode, beanName,
                        methodName, logExecutionTime.getDuration()));
            }
        }
    }
}
