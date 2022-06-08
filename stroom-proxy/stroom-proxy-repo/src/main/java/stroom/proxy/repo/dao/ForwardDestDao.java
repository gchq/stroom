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
import stroom.proxy.repo.ForwardDest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.ForwardDest.FORWARD_DEST;

@Singleton
public class ForwardDestDao {

    private final SqliteJooqHelper jooq;
    private final AtomicInteger forwardDestRecordId = new AtomicInteger();

    @Inject
    ForwardDestDao(final SqliteJooqHelper jooq) {
        this.jooq = jooq;
        init();
    }

    private void init() {
        jooq.readOnlyTransaction(context -> {
            final int maxForwardUrlRecordId = JooqUtil.getMaxId(context, FORWARD_DEST, FORWARD_DEST.ID)
                    .orElse(0);
            forwardDestRecordId.set(maxForwardUrlRecordId);
        });
    }

    public void clear() {
        jooq.transaction(context -> {
            JooqUtil.deleteAll(context, FORWARD_DEST);
            JooqUtil.checkEmpty(context, FORWARD_DEST);
        });
        init();
    }

    public List<ForwardDest> getAllForwardDests() {
        return jooq.readOnlyTransactionResult(context -> context
                        .select(FORWARD_DEST.ID, FORWARD_DEST.NAME)
                        .from(FORWARD_DEST)
                        .fetch())
                .map(r -> new ForwardDest(r.get(FORWARD_DEST.ID), r.get(FORWARD_DEST.NAME)));
    }

    public int getForwardDestId(final String name) {
        Objects.requireNonNull(name, "The forward dest name is null");
        return jooq.transactionResult(context -> {
            final Optional<Integer> optionalId = context
                    .select(FORWARD_DEST.ID)
                    .from(FORWARD_DEST)
                    .where(FORWARD_DEST.NAME.equal(name))
                    .fetchOptional(FORWARD_DEST.ID);

            return optionalId.orElseGet(() -> {
                final int newId = forwardDestRecordId.incrementAndGet();
                context
                        .insertInto(FORWARD_DEST, FORWARD_DEST.ID, FORWARD_DEST.NAME)
                        .values(newId, name)
                        .execute();
                return newId;
            });
        });
    }

    public int countForwardDest() {
        return jooq.readOnlyTransactionResult(context -> JooqUtil.count(context, FORWARD_DEST));
    }
}
