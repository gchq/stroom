/*
 * Copyright 2026 Crown Copyright
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

package stroom.config.global.impl.dao;

import stroom.config.global.impl.QuickFilterHistoryDao;
import stroom.config.global.impl.db.GlobalConfigDbConnProvider;
import stroom.db.util.JooqUtil;
import stroom.quickfilter.shared.QuickFilterHistoryKey;

import jakarta.inject.Inject;
import org.jooq.Condition;

import java.util.List;

import static stroom.config.impl.db.jooq.tables.QuickFilterHistory.QUICK_FILTER_HISTORY;

class QuickFilterHistoryDaoImpl implements QuickFilterHistoryDao {

    private final GlobalConfigDbConnProvider connProvider;

    @Inject
    QuickFilterHistoryDaoImpl(final GlobalConfigDbConnProvider connProvider) {
        this.connProvider = connProvider;
    }

    @Override
    public List<String> fetch(final String userUuid, final QuickFilterHistoryKey key, final int limit) {
        return JooqUtil.contextResult(connProvider, context -> context
                .select(QUICK_FILTER_HISTORY.FILTER_TEXT)
                .from(QUICK_FILTER_HISTORY)
                .where(keyCondition(userUuid, key))
                .orderBy(QUICK_FILTER_HISTORY.LAST_USED_MS.desc(), QUICK_FILTER_HISTORY.ID.desc())
                .limit(limit)
                .fetch(QUICK_FILTER_HISTORY.FILTER_TEXT));
    }

    @Override
    public void recordUse(final String userUuid,
                          final QuickFilterHistoryKey key,
                          final String filterText,
                          final long nowMs,
                          final int limit) {
        JooqUtil.context(connProvider, context -> {
            // The unique key on (user, context, data source, text) is what de-duplicates; a
            // repeat use becomes an update of the existing row rather than a second row.
            context
                    .insertInto(QUICK_FILTER_HISTORY,
                            QUICK_FILTER_HISTORY.USER_UUID,
                            QUICK_FILTER_HISTORY.CONTEXT,
                            QUICK_FILTER_HISTORY.DATA_SOURCE_UUID,
                            QUICK_FILTER_HISTORY.FILTER_TEXT,
                            QUICK_FILTER_HISTORY.LAST_USED_MS,
                            QUICK_FILTER_HISTORY.USE_COUNT)
                    .values(userUuid,
                            key.getContext(),
                            key.getDataSourceUuid(),
                            filterText,
                            nowMs,
                            1)
                    .onDuplicateKeyUpdate()
                    .set(QUICK_FILTER_HISTORY.LAST_USED_MS, nowMs)
                    .set(QUICK_FILTER_HISTORY.USE_COUNT, QUICK_FILTER_HISTORY.USE_COUNT.plus(1))
                    .execute();

            // Trim at write time rather than in a scheduled job: there are never more than
            // limit + 1 rows per key, so this is one cheap read and, at most, one delete.
            final List<Integer> stale = context
                    .select(QUICK_FILTER_HISTORY.ID)
                    .from(QUICK_FILTER_HISTORY)
                    .where(keyCondition(userUuid, key))
                    .orderBy(QUICK_FILTER_HISTORY.LAST_USED_MS.desc(), QUICK_FILTER_HISTORY.ID.desc())
                    .offset(limit)
                    .fetch(QUICK_FILTER_HISTORY.ID);
            if (!stale.isEmpty()) {
                context
                        .deleteFrom(QUICK_FILTER_HISTORY)
                        .where(QUICK_FILTER_HISTORY.ID.in(stale))
                        .execute();
            }
        });
    }

    @Override
    public int clear(final String userUuid, final QuickFilterHistoryKey key) {
        return JooqUtil.contextResult(connProvider, context -> context
                .deleteFrom(QUICK_FILTER_HISTORY)
                .where(keyCondition(userUuid, key))
                .execute());
    }

    @Override
    public int deleteAll(final String userUuid) {
        return JooqUtil.contextResult(connProvider, context -> context
                .deleteFrom(QUICK_FILTER_HISTORY)
                .where(QUICK_FILTER_HISTORY.USER_UUID.eq(userUuid))
                .execute());
    }

    private static Condition keyCondition(final String userUuid, final QuickFilterHistoryKey key) {
        return QUICK_FILTER_HISTORY.USER_UUID.eq(userUuid)
                .and(QUICK_FILTER_HISTORY.CONTEXT.eq(key.getContext()))
                .and(QUICK_FILTER_HISTORY.DATA_SOURCE_UUID.eq(key.getDataSourceUuid()));
    }
}
