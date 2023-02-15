package stroom.explorer.impl.db;

import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.explorer.impl.ExplorerFavDao;

import java.time.Instant;
import java.util.List;
import javax.inject.Inject;

import static stroom.explorer.impl.db.jooq.Tables.EXPLORER_FAVOURITE;
import static stroom.explorer.impl.db.jooq.Tables.EXPLORER_NODE;

public class ExplorerFavDaoImpl implements ExplorerFavDao {

    private final ExplorerDbConnProvider explorerDbConnProvider;

    @Inject
    ExplorerFavDaoImpl(final ExplorerDbConnProvider explorerDbConnProvider) {
        this.explorerDbConnProvider = explorerDbConnProvider;
    }

    @Override
    public void createFavouriteForUser(final DocRef docRef, final String userId) {
        final int explorerNodeId = getExplorerNodeId(docRef);
        final int count = JooqUtil.contextResult(explorerDbConnProvider, context -> context
                .insertInto(EXPLORER_FAVOURITE,
                        EXPLORER_FAVOURITE.EXPLORER_NODE_ID,
                        EXPLORER_FAVOURITE.USER_UUID,
                        EXPLORER_FAVOURITE.CREATE_TIME_MS)
                .values(explorerNodeId,
                        userId,
                        Instant.now().toEpochMilli())
                .onDuplicateKeyIgnore()
                .execute());

        if (count != 1) {
            throw new RuntimeException("Failed to create an EXPLORER_FAVOURITE entry for doc: " +
                    docRef.getUuid() + ", user: " + userId);
        }
    }

    @Override
    public void deleteFavouriteForUser(final DocRef docRef, final String userId) {
        final int explorerNodeId = getExplorerNodeId(docRef);
        final int count = JooqUtil.contextResult(explorerDbConnProvider, context -> context
                .deleteFrom(EXPLORER_FAVOURITE)
                .where(EXPLORER_FAVOURITE.USER_UUID.eq(userId))
                .and(EXPLORER_FAVOURITE.EXPLORER_NODE_ID.eq(explorerNodeId))
                .execute());

        if (count != 1) {
            throw new RuntimeException("Failed to remove EXPLORER_FAVOURITE entry for doc: " +
                    docRef.getUuid() + ", user: " + userId);
        }
    }

    @Override
    public List<DocRef> getUserFavourites(final String userId) {
        return JooqUtil.contextResult(explorerDbConnProvider, context -> context
                .select()
                .from(EXPLORER_FAVOURITE
                        .innerJoin(EXPLORER_NODE).on(EXPLORER_NODE.ID.eq(EXPLORER_FAVOURITE.EXPLORER_NODE_ID)))
                .where(EXPLORER_FAVOURITE.USER_UUID.eq(userId))
                .fetch(r -> new DocRef(
                        r.get(EXPLORER_NODE.TYPE),
                        r.get(EXPLORER_NODE.UUID)
                )));
    }

    @Override
    public boolean isFavourite(final DocRef docRef, final String userId) {
        final int explorerNodeId = getExplorerNodeId(docRef);
        return JooqUtil.contextResult(explorerDbConnProvider, context -> context
                .fetchCount(EXPLORER_FAVOURITE,
                        EXPLORER_FAVOURITE.USER_UUID.eq(userId)
                                .and(EXPLORER_FAVOURITE.EXPLORER_NODE_ID.eq(explorerNodeId))) > 0);
    }

    private int getExplorerNodeId(final DocRef docRef) {
        return JooqUtil.contextResult(explorerDbConnProvider, context -> context
                .select()
                .from(EXPLORER_NODE)
                .where(EXPLORER_NODE.UUID.eq(docRef.getUuid()))
                .fetchOne(EXPLORER_NODE.ID));
    }
}
