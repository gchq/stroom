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

import stroom.proxy.repo.ForwardUrl;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.ForwardUrl.FORWARD_URL;

@Singleton
public class ForwardUrlDao {

    private final SqliteJooqHelper jooq;
    private final AtomicInteger forwardUrlRecordId = new AtomicInteger();

    @Inject
    ForwardUrlDao(final SqliteJooqHelper jooq) {
        this.jooq = jooq;
        init();
    }

    private void init() {
        final int maxForwardUrlRecordId = jooq.getMaxId(FORWARD_URL, FORWARD_URL.ID).orElse(0);
        forwardUrlRecordId.set(maxForwardUrlRecordId);
    }

    public void clear() {
        jooq.deleteAll(FORWARD_URL);
        jooq.checkEmpty(FORWARD_URL);
        init();
    }

    public List<ForwardUrl> getAllForwardUrls() {
        return jooq.contextResult(context -> context
                        .select(FORWARD_URL.ID, FORWARD_URL.URL)
                        .from(FORWARD_URL)
                        .fetch())
                .map(r -> new ForwardUrl(r.get(FORWARD_URL.ID), r.get(FORWARD_URL.URL)));
    }

    public int getForwardUrlId(final String forwardUrl) {
        Objects.requireNonNull(forwardUrl, "The forward URL is null");
        return jooq.contextResult(context -> {
            final Optional<Integer> optionalId = context
                    .select(FORWARD_URL.ID)
                    .from(FORWARD_URL)
                    .where(FORWARD_URL.URL.equal(forwardUrl))
                    .fetchOptional(FORWARD_URL.ID);

            return optionalId.orElseGet(() -> {
                final int newId = forwardUrlRecordId.incrementAndGet();
                context
                        .insertInto(FORWARD_URL, FORWARD_URL.ID, FORWARD_URL.URL)
                        .values(newId, forwardUrl)
                        .execute();
                return newId;
            });
        });
    }

    public int countForwardUrl() {
        return jooq.count(FORWARD_URL);
    }
}
