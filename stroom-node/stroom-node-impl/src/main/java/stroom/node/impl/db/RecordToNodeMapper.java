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
        return Node
                .builder()
                .id(record.get(NODE.ID))
                .version(record.get(NODE.VERSION))
                .createTimeMs(record.get(NODE.CREATE_TIME_MS))
                .createUser(record.get(NODE.CREATE_USER))
                .updateTimeMs(record.get(NODE.UPDATE_TIME_MS))
                .updateUser(record.get(NODE.UPDATE_USER))
                .name(record.get(NODE.NAME))
                .url(record.get(NODE.URL))
                .priority(record.get(NODE.PRIORITY))
                .enabled(record.get(NODE.ENABLED))
                .buildVersion(record.get(NODE.BUILD_VERSION))
                .lastBootMs(record.get(NODE.LAST_BOOT_MS))
                .build();
    }
}
