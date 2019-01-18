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


import org.junit.jupiter.api.Test;
import stroom.node.shared.FindNodeCriteria;
import stroom.test.AbstractCoreIntegrationTest;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

class TestDefaultNodeService extends AbstractCoreIntegrationTest {
    @Inject
    private NodeCache nodeCache;
    @Inject
    private NodeService nodeService;

    @Test
    void testBasic() {
        // This should get lazy created
        assertThat(nodeCache.getDefaultNode()).isNotNull();
        assertThat(nodeService.find(new FindNodeCriteria()).size() > 0).isTrue();
    }
}
