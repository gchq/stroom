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

import stroom.node.shared.Node;

import org.jooq.Record;

import java.util.function.Function;

import static stroom.node.impl.db.jooq.tables.Node.NODE;

class RecordToNodeMapper implements Function<Record, Node> {

    @Override
    public Node apply(final Record record) {
        final Node node = new Node();
        node.setId(record.get(NODE.ID));
        node.setVersion(record.get(NODE.VERSION));
        node.setCreateTimeMs(record.get(NODE.CREATE_TIME_MS));
        node.setCreateUser(record.get(NODE.CREATE_USER));
        node.setUpdateTimeMs(record.get(NODE.UPDATE_TIME_MS));
        node.setUpdateUser(record.get(NODE.UPDATE_USER));
        node.setName(record.get(NODE.NAME));
        node.setUrl(record.get(NODE.URL));
        node.setPriority(record.get(NODE.PRIORITY));
        node.setEnabled(record.get(NODE.ENABLED));
        node.setBuildVersion(record.get(NODE.BUILD_VERSION));
        node.setLastBootMs(record.get(NODE.LAST_BOOT_MS));
        return node;
    }
}
