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

package stroom.core.welcome;

import stroom.config.global.shared.SessionInfoResource;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.SessionInfo;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
public class SessionInfoResourceImpl implements SessionInfoResource {

    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<BuildInfo> buildInfoProvider;

    @Inject
    SessionInfoResourceImpl(final Provider<NodeInfo> nodeInfoProvider,
                            final Provider<SecurityContext> securityContextProvider,
                            final Provider<BuildInfo> buildInfoProvider) {
        this.nodeInfoProvider = nodeInfoProvider;
        this.securityContextProvider = securityContextProvider;
        this.buildInfoProvider = buildInfoProvider;
    }

    @Override
    public SessionInfo get() {
        final UserRef userRef = securityContextProvider.get().getUserRef();
        return new SessionInfo(
                userRef,
                nodeInfoProvider.get().getThisNodeName(),
                buildInfoProvider.get());
    }
}
