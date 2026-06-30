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

package stroom.node.impl.db;

import stroom.node.shared.NodeGroup;

import org.jooq.Record;

import java.util.function.Function;

import static stroom.node.impl.db.jooq.tables.NodeGroup.NODE_GROUP;

class RecordToNodeGroupMapper implements Function<Record, NodeGroup> {

    @Override
    public NodeGroup apply(final Record record) {
        return NodeGroup
                .builder()
                .id(record.get(NODE_GROUP.ID))
                .version(record.get(NODE_GROUP.VERSION))
                .createTimeMs(record.get(NODE_GROUP.CREATE_TIME_MS))
                .createUser(record.get(NODE_GROUP.CREATE_USER))
                .updateTimeMs(record.get(NODE_GROUP.UPDATE_TIME_MS))
                .updateUser(record.get(NODE_GROUP.UPDATE_USER))
                .name(record.get(NODE_GROUP.NAME))
                .enabled(record.get(NODE_GROUP.ENABLED))
                .build();
    }
}
