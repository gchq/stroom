/*
 * Copyright 2019 Crown Copyright
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

package stroom.proxy.repo.dao;

import stroom.db.util.JooqUtil;
import stroom.proxy.repo.FeedKey;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Feed.FEED;

@Singleton
public class FeedDao {

    private final SqliteJooqHelper jooq;
    private final AtomicLong feedId = new AtomicLong();
    private final Cache<FeedKey, Long> idCache;
    private final Cache<Long, FeedKey> keyCache;

    @Inject
    FeedDao(final SqliteJooqHelper jooq) {
        this.jooq = jooq;

        idCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .build();
        keyCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .build();

        init();
    }

    private void init() {
        jooq.readOnlyTransaction(context -> feedId.set(JooqUtil
                .getMaxId(context, FEED, FEED.ID)
                .orElse(0L)));
    }

    public long getId(final FeedKey feedKey) {
        final Long result = idCache.get(feedKey, k -> {
            final Optional<Long> optional = jooq.readOnlyTransactionResult(context -> context
                    .select(FEED.ID)
                    .from(FEED)
                    .where(feedKey.feed() == null
                            ? FEED.FEED_NAME.isNull()
                            : FEED.FEED_NAME.eq(k.feed()))
                    .and(feedKey.type() == null
                            ? FEED.TYPE_NAME.isNull()
                            : FEED.TYPE_NAME.eq(k.type()))
                    .fetchOptional(FEED.ID));
            if (optional.isPresent()) {
                return optional.get();
            }

            return jooq.transactionResult(context -> {
                final long id = feedId.incrementAndGet();
                context
                        .insertInto(FEED, FEED.ID, FEED.FEED_NAME, FEED.TYPE_NAME)
                        .values(id, feedKey.feed(), feedKey.type())
                        .execute();
                return id;
            });
        });

        if (result == null) {
            throw new NullPointerException("Unexpected");
        }
        return result;
    }

    public FeedKey getKey(final long id) {
        return keyCache.get(id, k -> jooq.readOnlyTransactionResult(context -> context
                .select(FEED.FEED_NAME, FEED.TYPE_NAME)
                .from(FEED)
                .where(FEED.ID.eq(id))
                .fetchOne(r -> new FeedKey(r.get(FEED.FEED_NAME), r.get(FEED.TYPE_NAME)))));
    }

    public void clear() {
        jooq.transaction(context -> {
            JooqUtil.deleteAll(context, FEED);
            JooqUtil.checkEmpty(context, FEED);
        });
        idCache.invalidateAll();
        init();
    }

    public int countFeeds() {
        return jooq.readOnlyTransactionResult(context -> JooqUtil.count(context, FEED));
    }
}
