/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.docref.DocRef;
import stroom.node.shared.FindNodeGroupRequest;
import stroom.node.shared.NodeGroup;
import stroom.node.shared.NodeGroupChange;
import stroom.node.shared.NodeGroupState;
import stroom.util.shared.ResultPage;

public interface NodeGroupService {

    String ENTITY_TYPE = "NODE_GROUP";
    DocRef EVENT_DOCREF = new DocRef(ENTITY_TYPE, ENTITY_TYPE, ENTITY_TYPE);

    ResultPage<NodeGroup> find(FindNodeGroupRequest request);

    NodeGroup create(String name);

    NodeGroup update(NodeGroup indexVolumeGroup);

    NodeGroup fetchByName(String name);

    NodeGroup fetchById(int id);

    void delete(int id);

    ResultPage<NodeGroupState> getNodeGroupState(Integer id);

    boolean updateNodeGroupState(NodeGroupChange change);
}
