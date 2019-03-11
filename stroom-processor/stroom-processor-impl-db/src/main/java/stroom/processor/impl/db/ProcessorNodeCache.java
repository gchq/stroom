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

package stroom.processor.impl.db;

import stroom.db.util.JooqUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static stroom.processor.impl.db.jooq.tables.ProcessorNode.PROCESSOR_NODE;

@Singleton
class ProcessorNodeCache {
    private final Map<String, Integer> cache = new ConcurrentHashMap<>();

    private final ConnectionProvider connectionProvider;

    @Inject
    ProcessorNodeCache(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

//    @Override
    public Integer getOrCreate(final String name) {
        Integer id = get(name);
        if (id == null) {
            // Create.
            id = create(name);
        }

        return id;
    }

//    @Override
//    public List<String> list() {
//        return JooqUtil.contextResult(connectionProvider, context -> context
//                .select(PROCESSOR_NODE.NAME)
//                .from(PROCESSOR_NODE)
//                .fetch(PROCESSOR_NODE.NAME));
//    }

    private Integer get(final String name) {
        Integer id = cache.get(name);
        if (id != null) {
            return id;
        }

        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(PROCESSOR_NODE.ID)
                .from(PROCESSOR_NODE)
                .where(PROCESSOR_NODE.NAME.eq(name))
                .fetchOptional(PROCESSOR_NODE.ID))
                .map(i -> cache.put(name, i))
                .orElse(null);
    }

    Integer create(final String name) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .insertInto(PROCESSOR_NODE, PROCESSOR_NODE.NAME)
                .values(name)
                .onDuplicateKeyIgnore()
                .returning(PROCESSOR_NODE.ID)
                .fetchOptional()
                .map(record -> {
                    final Integer id = record.getId();
                    cache.put(name, id);
                    return id;
                })
                .orElseGet(() -> get(name))
        );
    }

    void clear() {
        deleteAll();
        cache.clear();
    }

    int deleteAll() {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .delete(PROCESSOR_NODE)
                .execute());
    }
}
