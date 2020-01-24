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

package stroom.cluster.impl;

import com.caucho.hessian.server.HessianServlet;
import stroom.cluster.api.ClusterCallService;
import stroom.cluster.api.ClusterCallServiceLocal;
import stroom.cluster.api.ServiceName;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.IsServlet;

import javax.inject.Inject;
import java.util.Set;

public class ClusterCallServiceRPC extends HessianServlet implements ClusterCallService, IsServlet {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ClusterCallServiceRPC.class);
    private static final Set<String> PATH_SPECS = Set.of("/clustercall.rpc");

    private final ClusterCallService clusterCallService;
    private final SecurityContext securityContext;

    @Inject
    ClusterCallServiceRPC(final ClusterCallServiceLocal clusterCallService,
                          final SecurityContext securityContext) {
        this.clusterCallService = clusterCallService;
        this.securityContext = securityContext;
    }

    @Override
    public Object call(final String sourceNode, final String targetNode, final UserIdentity userIdentity, final ServiceName serviceName, final String methodName, final Class<?>[] parameterTypes, final Object[] args) {
        // We are receiving a call from another node so login as the supplied user.
        LOGGER.debug(() -> "Hessian call with user " + userIdentity);

        return securityContext.asUserResult(userIdentity, () ->
                clusterCallService.call(sourceNode, targetNode, userIdentity, serviceName, methodName, parameterTypes, args));
    }

    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }
}
