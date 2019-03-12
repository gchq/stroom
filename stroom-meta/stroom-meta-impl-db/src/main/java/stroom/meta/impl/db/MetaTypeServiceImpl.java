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

package stroom.meta.impl.db;

import stroom.db.util.JooqUtil;
import stroom.meta.impl.db.jooq.tables.records.MetaTypeRecord;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static stroom.meta.impl.db.jooq.tables.MetaType.META_TYPE;

@Singleton
class MetaTypeServiceImpl implements MetaTypeService {
    private final Map<String, Integer> cache = new ConcurrentHashMap<>();

    private final ConnectionProvider connectionProvider;

    @Inject
    MetaTypeServiceImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Integer getOrCreate(final String name) {
        Integer id = get(name);
        if (id == null) {
            // Create.
            id = create(name);
        }

        return id;
    }

    @Override
    public List<String> list() {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(META_TYPE.NAME)
                .from(META_TYPE)
                .fetch(META_TYPE.NAME)
        );
    }

    private Integer get(final String name) {
        Integer id = cache.get(name);
        if (id != null) {
            return id;
        }

        final Optional<Integer> optional = JooqUtil.contextResult(connectionProvider, context -> context
                .select(META_TYPE.ID)
                .from(META_TYPE)
                .where(META_TYPE.NAME.eq(name))
                .fetchOptional(META_TYPE.ID));
        optional.ifPresent(i -> cache.put(name, i));
        return optional.orElse(null);
    }

    private Integer create(final String name) {
        return JooqUtil.contextResult(connectionProvider, context -> {
            final Optional<MetaTypeRecord> optional = context
                    .insertInto(META_TYPE, META_TYPE.NAME)
                    .values(name)
                    .onDuplicateKeyIgnore()
                    .returning(META_TYPE.ID)
                    .fetchOptional();

            return optional
                    .map(record -> {
                        final Integer id = record.getId();
                        cache.put(name, id);
                        return id;
                    })
                    .orElseGet(() -> get(name));
        });
    }

    void clear() {
        deleteAll();
        cache.clear();
    }

    int deleteAll() {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .delete(META_TYPE)
                .execute());
    }
}
