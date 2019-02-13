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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static stroom.meta.impl.db.jooq.tables.MetaFeed.META_FEED;

@Singleton
class MetaFeedServiceImpl implements MetaFeedService {
    private final Map<String, Integer> cache = new ConcurrentHashMap<>();

    private final ConnectionProvider connectionProvider;

    @Inject
    MetaFeedServiceImpl(final ConnectionProvider connectionProvider) {
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
                .select(META_FEED.NAME)
                .from(META_FEED)
                .fetch(META_FEED.NAME));
    }

    private Integer get(final String name) {
        Integer id = cache.get(name);
        if (id != null) {
            return id;
        }

        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(META_FEED.ID)
                .from(META_FEED)
                .where(META_FEED.NAME.eq(name))
                .fetchOptional(META_FEED.ID))
                .map(i -> cache.put(name, i))
                .orElse(null);
    }

    Integer create(final String name) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .insertInto(META_FEED, META_FEED.NAME)
                .values(name)
                .onDuplicateKeyIgnore()
                .returning(META_FEED.ID)
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
                .delete(META_FEED)
                .execute());
    }
}
