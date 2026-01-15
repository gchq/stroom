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

package stroom.explorer.impl.db;

import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.explorer.impl.ExplorerFavDao;
import stroom.explorer.impl.db.jooq.tables.records.ExplorerFavouriteRecord;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static stroom.explorer.impl.db.jooq.tables.ExplorerFavourite.EXPLORER_FAVOURITE;
import static stroom.explorer.impl.db.jooq.tables.ExplorerNode.EXPLORER_NODE;

public class ExplorerFavDaoImpl implements ExplorerFavDao {

    private final ExplorerDbConnProvider explorerDbConnProvider;

    @Inject
    ExplorerFavDaoImpl(final ExplorerDbConnProvider explorerDbConnProvider) {
        this.explorerDbConnProvider = explorerDbConnProvider;
    }

    @Override
    public void createFavouriteForUser(final DocRef docRef, final UserRef userRef) {
        final Integer explorerNodeId = JooqUtil.contextResult(explorerDbConnProvider, context ->
                getExplorerNodeId(context, docRef).fetchAny(EXPLORER_NODE.ID));
        Objects.requireNonNull(explorerNodeId);
        final ExplorerFavouriteRecord record = new ExplorerFavouriteRecord(
                null,
                explorerNodeId,
                userRef.getUuid(),
                Instant.now().toEpochMilli());
        JooqUtil.tryCreate(explorerDbConnProvider, record, EXPLORER_FAVOURITE.EXPLORER_NODE_ID,
                EXPLORER_FAVOURITE.USER_UUID);
    }

    @Override
    public void deleteFavouriteForUser(final DocRef docRef, final UserRef userRef) {
        JooqUtil.contextResult(explorerDbConnProvider, context -> context
                .deleteFrom(EXPLORER_FAVOURITE)
                .where(EXPLORER_FAVOURITE.USER_UUID.eq(userRef.getUuid())
                        .and(EXPLORER_FAVOURITE.EXPLORER_NODE_ID.in(getExplorerNodeId(context, docRef))))
                .execute());
    }

    @Override
    public List<DocRef> getUserFavourites(final UserRef userRef) {
        return JooqUtil.contextResult(explorerDbConnProvider, context -> context
                .select()
                .from(EXPLORER_FAVOURITE
                        .innerJoin(EXPLORER_NODE).on(EXPLORER_NODE.ID.eq(EXPLORER_FAVOURITE.EXPLORER_NODE_ID)))
                .where(EXPLORER_FAVOURITE.USER_UUID.eq(userRef.getUuid()))
                .fetch(r -> new DocRef(
                        r.get(EXPLORER_NODE.TYPE),
                        r.get(EXPLORER_NODE.UUID)
                )));
    }

    @Override
    public boolean isFavourite(final DocRef docRef, final UserRef userRef) {
        return JooqUtil.contextResult(
                explorerDbConnProvider,
                context -> context
                                   .fetchCount(EXPLORER_FAVOURITE,
                                           EXPLORER_FAVOURITE.USER_UUID.eq(
                                                           userRef.getUuid())
                                                   .and(EXPLORER_FAVOURITE.EXPLORER_NODE_ID.in(
                                                           getExplorerNodeId(
                                                                   context,
                                                                   docRef)))) > 0);
    }

    private SelectConditionStep<Record1<Integer>> getExplorerNodeId(final DSLContext context, final DocRef docRef) {
        return context
                .select(EXPLORER_NODE.ID)
                .from(EXPLORER_NODE)
                .where(EXPLORER_NODE.UUID.eq(docRef.getUuid()));
    }
}
