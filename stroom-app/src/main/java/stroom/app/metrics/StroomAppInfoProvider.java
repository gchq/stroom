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

package stroom.app.metrics;

import stroom.config.common.NodeUriConfig;
import stroom.config.common.PublicUriConfig;
import stroom.dropwizard.common.prometheus.AbstractAppInfoProvider;
import stroom.node.api.NodeInfo;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Map;

public class StroomAppInfoProvider extends AbstractAppInfoProvider {

    public static final String NODE_NAME_KEY = "node_name";
    private final NodeInfo nodeInfo;
    private final Provider<PublicUriConfig> publicUriConfigProvider;
    private final Provider<NodeUriConfig> nodeUriConfigProvider;

    @Inject
    public StroomAppInfoProvider(final NodeInfo nodeInfo,
                                 final Provider<PublicUriConfig> publicUriConfigProvider,
                                 final Provider<NodeUriConfig> nodeUriConfigProvider) {
        this.nodeInfo = nodeInfo;
        this.publicUriConfigProvider = publicUriConfigProvider;
        this.nodeUriConfigProvider = nodeUriConfigProvider;
    }

    @Override
    protected Map<String, String> getAdditionalAppInfo() {
        return Map.of(
                NODE_NAME_KEY, nodeInfo.getThisNodeName(),
                "public_uri", publicUriConfigProvider.get().asUri(),
                "node_uri", nodeUriConfigProvider.get().asUri());
    }

    @Override
    public Map<String, String> getNodeLabels() {
        return Map.of(NODE_NAME_KEY, nodeInfo.getThisNodeName());
    }
}
