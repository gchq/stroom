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

import stroom.db.util.SqliteJooqHelper;
import stroom.proxy.repo.ForwarderDestinations;
import stroom.proxy.repo.ProxyRepoDbConnProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.ForwardAggregate.FORWARD_AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.ForwardSource.FORWARD_SOURCE;
import static stroom.proxy.repo.db.jooq.tables.ForwardUrl.FORWARD_URL;

@Singleton
public class ForwardUrlDao {

    private final SqliteJooqHelper jooq;
    private final ForwarderDestinations forwarderDestinations;

    private final Map<Integer, String> forwardIdUrlMap = new HashMap<>();

    private final AtomicInteger forwardUrlRecordId = new AtomicInteger();

    @Inject
    ForwardUrlDao(final ProxyRepoDbConnProvider connProvider,
                  final ForwarderDestinations forwarderDestinations) {
        this.jooq = new SqliteJooqHelper(connProvider);
        this.forwarderDestinations = forwarderDestinations;

        init();
    }

    private void init() {
        final int maxForwardUrlRecordId = jooq.getMaxId(FORWARD_URL, FORWARD_URL.ID).orElse(0);
        forwardUrlRecordId.set(maxForwardUrlRecordId);

        // Create a map of forward URLs to DB ids.
        for (final String destinationName : forwarderDestinations.getDestinationNames()) {
            final int id = getForwardUrlId(destinationName);
            forwardIdUrlMap.put(id, destinationName);
        }

        // Delete destinations that are no longer specified.
        final List<Integer> allForwardIds = jooq.contextResult(context -> context
                .select(FORWARD_URL.ID)
                .from(FORWARD_URL)
                .fetch(FORWARD_URL.ID));
        for (final int id : allForwardIds) {
            if (!forwardIdUrlMap.containsKey(id)) {
                jooq.context(context -> context
                        .deleteFrom(FORWARD_AGGREGATE)
                        .where(FORWARD_AGGREGATE.FK_FORWARD_URL_ID.eq(id))
                        .execute());
                jooq.context(context -> context
                        .deleteFrom(FORWARD_SOURCE)
                        .where(FORWARD_SOURCE.FK_FORWARD_URL_ID.eq(id))
                        .execute());
                jooq.context(context -> context
                        .deleteFrom(FORWARD_URL)
                        .where(FORWARD_URL.ID.eq(id))
                        .execute());
            }
        }
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

    public Map<Integer, String> getForwardIdUrlMap() {
        return forwardIdUrlMap;
    }

    public void clear() {
        jooq.deleteAll(FORWARD_URL);
        jooq
                .getMaxId(FORWARD_URL, FORWARD_URL.ID)
                .ifPresent(id -> {
                    throw new RuntimeException("Unexpected ID");
                });
        init();
    }
}
