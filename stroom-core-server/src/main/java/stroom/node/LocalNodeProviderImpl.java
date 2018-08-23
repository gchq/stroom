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

package stroom.node;

import stroom.node.shared.Node;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LocalNodeProviderImpl implements LocalNodeProvider {
    private final NodeServiceTransactionHelper nodeServiceUtil;
    private final NodeConfig nodeConfig;

    @Inject
    LocalNodeProviderImpl(final NodeServiceTransactionHelper nodeServiceUtil,
                          final NodeConfig nodeConfig) {
        this.nodeServiceUtil = nodeServiceUtil;
        this.nodeConfig = nodeConfig;
    }

    @Override
    public Node get() {
        Node node = nodeServiceUtil.getNode(nodeConfig.getNodeName());

        if (node == null) {
            // This will start a new mini transaction for the update
            node = nodeServiceUtil.buildNode(nodeConfig.getNodeName(), nodeConfig.getRackName());
        }

        return node;
    }
}
