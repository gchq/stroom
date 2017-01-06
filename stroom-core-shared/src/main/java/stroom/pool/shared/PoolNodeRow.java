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

import stroom.node.shared.Node;
import stroom.util.shared.SharedObject;

public class PoolNodeRow implements SharedObject {
    private static final long serialVersionUID = -7367500560554774611L;

    private Node node;
    private PoolInfo poolInfo;

    public PoolNodeRow() {
        // Default constructor necessary for GWT serialisation.
    }

    public PoolNodeRow(final Node node, final PoolInfo poolInfo) {
        this.node = node;
        this.poolInfo = poolInfo;
    }

    public Node getNode() {
        return node;
    }

    public PoolInfo getPoolInfo() {
        return poolInfo;
    }
}
