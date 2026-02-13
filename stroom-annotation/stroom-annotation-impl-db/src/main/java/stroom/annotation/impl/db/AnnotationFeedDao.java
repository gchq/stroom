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

package stroom.annotation.impl.db;

import stroom.db.util.JooqUtil;
import stroom.db.util.JooqUtil.BooleanOperator;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static stroom.annotation.impl.db.jooq.tables.AnnotationFeed.ANNOTATION_FEED;

/**
 * Cache for mapping annotation feed names to ids.
 * <p>
 * This cache uses async execution to avoid thread-local connection conflicts
 * when accessed from within existing database transaction contexts.
 */
@Singleton
class AnnotationFeedDao implements Clearable {

    private final AnnotationDbConnProvider connectionProvider;

    @Inject
    AnnotationFeedDao(final AnnotationDbConnProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public Set<Integer> fetchWithWildCards(final List<String> wildCardedTypeNames) {
        final Condition condition = JooqUtil.createWildCardedStringsCondition(
                ANNOTATION_FEED.NAME, wildCardedTypeNames, true, BooleanOperator.OR);
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(ANNOTATION_FEED.NAME, ANNOTATION_FEED.ID)
                .from(ANNOTATION_FEED)
                .where(condition)
                .fetchSet(ANNOTATION_FEED.ID));
    }

    public Optional<String> fetchById(final Integer id) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(ANNOTATION_FEED.NAME)
                .from(ANNOTATION_FEED)
                .where(ANNOTATION_FEED.ID.eq(id))
                .fetchOptional(ANNOTATION_FEED.NAME));
    }

    public Optional<Integer> fetchByName(final String name) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(ANNOTATION_FEED.ID)
                .from(ANNOTATION_FEED)
                .where(ANNOTATION_FEED.NAME.eq(name))
                .fetchOptional(ANNOTATION_FEED.ID));
    }

    public int create(final String name) {
        return JooqUtil.transactionResult(connectionProvider, context -> {
            context
                    .insertInto(ANNOTATION_FEED, ANNOTATION_FEED.NAME)
                    .values(name)
                    .onDuplicateKeyUpdate()
                    .set(ANNOTATION_FEED.NAME, name)
                    .execute();
            return context
                    .select(ANNOTATION_FEED.ID)
                    .from(ANNOTATION_FEED)
                    .where(ANNOTATION_FEED.NAME.eq(name))
                    .fetchAny(ANNOTATION_FEED.ID);
        });
    }

    @Override
    public void clear() {
        JooqUtil.context(connectionProvider, context -> context.deleteFrom(ANNOTATION_FEED).execute());
    }

    /**
     * Executes the supplier asynchronously on a different thread to avoid thread-local
     * connection conflicts when this cache is accessed from within an existing database
     * transaction context.
     * <p>
     * This is necessary because JOOQ connection providers may use thread-local storage,
     * and attempting to open a new connection on the same thread that already has an
     * open connection can cause conflicts or deadlocks.
     *
     * @param supplier the operation to execute
     * @param <R>      the return type
     * @return the result from the supplier
     * @throws UncheckedInterruptedException if interrupted while waiting
     * @throws RuntimeException              if the async execution fails
     */
    public static <R> R async(final Supplier<R> supplier) {
        try {
            return CompletableFuture.supplyAsync(supplier).get();
        } catch (final InterruptedException e) {
            throw new UncheckedInterruptedException(e);
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        }
    }
}
