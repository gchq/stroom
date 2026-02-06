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

import stroom.event.logging.api.ObjectInfoProvider;
import stroom.node.shared.NodeGroup;

import event.logging.BaseObject;
import event.logging.OtherObject;

class NodeGroupObjectInfoProvider implements ObjectInfoProvider {

    @Override
    public BaseObject createBaseObject(final Object obj) {
        final NodeGroup nodeGroup = (NodeGroup) obj;

        return OtherObject.builder()
                .withType("Node Group")
                .withId(String.valueOf(nodeGroup.getId()))
                .withName(nodeGroup.getName())
                .build();
    }

    @Override
    public String getObjectType(final Object object) {
        return object.getClass().getSimpleName();
    }
}
