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

package stroom.pool.shared;

import stroom.dispatch.shared.Action;
import stroom.node.shared.Node;
import stroom.util.shared.VoidResult;

public class PoolClearAction extends Action<VoidResult> {
    private static final long serialVersionUID = 6319893515607847166L;

    private String poolName;
    private Node node;

    public PoolClearAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public PoolClearAction(final String poolName, final Node node) {
        this.poolName = poolName;
        this.node = node;
    }

    public String getPoolName() {
        return poolName;
    }

    public Node getNode() {
        return node;
    }

    @Override
    public String getTaskName() {
        return "Clear pool";
    }
}
