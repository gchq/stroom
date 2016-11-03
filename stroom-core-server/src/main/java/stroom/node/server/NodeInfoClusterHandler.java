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

package stroom.node.server;

import javax.annotation.Resource;

import stroom.node.shared.NodeInfoResult;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;

@TaskHandlerBean(task = NodeInfoClusterTask.class)
public class NodeInfoClusterHandler extends AbstractTaskHandler<NodeInfoClusterTask, NodeInfoResult> {
    @Resource
    private NodeCache nodeCache;

    @Override
    public NodeInfoResult exec(final NodeInfoClusterTask action) {
        final NodeInfoResult nodeInfoResult = new NodeInfoResult();
        nodeInfoResult.setEntity(nodeCache.getDefaultNode());
        nodeInfoResult.setPingMs(System.currentTimeMillis());
        return nodeInfoResult;
    }
}
