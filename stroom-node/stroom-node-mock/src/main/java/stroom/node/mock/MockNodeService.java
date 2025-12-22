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

package stroom.node.mock;

import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeService;

import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.core.Response;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Mock class that manages one node.
 */
@Singleton
public class MockNodeService implements NodeService {

    private MockNodeInfo nodeInfo = new MockNodeInfo();

    @Override
    public String getBaseEndpointUrl(final String nodeName) {
        return null;
    }

    @Override
    public boolean isEnabled(final String nodeName) {
        return nodeName.equals(nodeInfo.getThisNodeName());
    }

    @Override
    public int getPriority(final String nodeName) {
        if (nodeName.equals(nodeInfo.getThisNodeName())) {
            return 1;
        }
        return 0;
    }

    @Override
    public List<String> getEnabledNodesByPriority() {
        return Collections.singletonList(nodeInfo.getThisNodeName());
    }

    @Override
    public List<String> findNodeNames(final FindNodeCriteria criteria) {
        return Collections.singletonList(nodeInfo.getThisNodeName());
    }

    @Override
    public <T_RESP> T_RESP remoteRestResult(final String nodeName,
                                            final Supplier<String> fullPathSupplier,
                                            final Supplier<T_RESP> localSupplier,
                                            final Function<Builder, Response> responseBuilderFunc,
                                            final Function<Response, T_RESP> responseMapper,
                                            final Map<String, Object> queryParams) {
        return localSupplier.get();
    }

    @Override
    public void remoteRestCall(final String nodeName,
                               final Supplier<String> fullPathSupplier,
                               final Runnable localRunnable,
                               final Function<Builder, Response> responseBuilderFunc,
                               final Map<String, Object> queryParams) {
        localRunnable.run();
    }
}
