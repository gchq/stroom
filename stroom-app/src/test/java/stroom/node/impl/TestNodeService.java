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

package stroom.node.impl;


import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.test.AbstractCoreIntegrationTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestNodeService extends AbstractCoreIntegrationTest {

    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private NodeService nodeService;

    @Test
    void testBasic() {
        assertThat(nodeInfo.getThisNodeName()).isNotNull();
        assertThat(nodeInfo.getThisNodeName()).isEqualTo("node1a");
        assertThat(nodeService).isInstanceOf(NodeServiceImpl.class);
        assertThat(nodeService.findNodeNames(new FindNodeCriteria()).size() > 0).isTrue();
    }
}
