package stroom.explorer.fav.impl.db;

import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.explorer.fav.impl.ExplorerFavDao;
import stroom.explorer.impl.db.ExplorerDbConnProvider;

import java.time.Instant;
import java.util.List;
import javax.inject.Inject;

import static stroom.explorer.fav.impl.db.jooq.Tables.EXPLORER_FAVOURITE;

public class ExplorerFavDaoImpl implements ExplorerFavDao {

    private final ExplorerDbConnProvider docStoreDbConnProvider;

    @Inject
    ExplorerFavDaoImpl(final ExplorerDbConnProvider docStoreDbConnProvider) {
        this.docStoreDbConnProvider = docStoreDbConnProvider;
    }

    @Override
    public void createFavouriteForUser(final DocRef docRef, final String userId) {
        final int count = JooqUtil.contextResult(docStoreDbConnProvider, context -> context
                .insertInto(EXPLORER_FAVOURITE,
                        EXPLORER_FAVOURITE.DOC_TYPE,
                        EXPLORER_FAVOURITE.DOC_UUID,
                        EXPLORER_FAVOURITE.USER_UUID,
                        EXPLORER_FAVOURITE.CREATE_TIME_MS)
                .values(docRef.getType(),
                        docRef.getUuid(),
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
        final int count = JooqUtil.contextResult(docStoreDbConnProvider, context -> context
                .deleteFrom(EXPLORER_FAVOURITE)
                .where(EXPLORER_FAVOURITE.USER_UUID.eq(userId))
                .and(EXPLORER_FAVOURITE.DOC_TYPE.eq(docRef.getType()))
                .and(EXPLORER_FAVOURITE.DOC_UUID.eq(docRef.getUuid()))
                .execute());

        if (count != 1) {
            throw new RuntimeException("Failed to remove EXPLORER_FAVOURITE entry for doc: " +
                    docRef.getUuid() + ", user: " + userId);
        }
    }

    @Override
    public List<DocRef> getUserFavourites(final String userId) {
        return JooqUtil.contextResult(docStoreDbConnProvider, context -> context
                .select()
                .from(EXPLORER_FAVOURITE)
                .where(EXPLORER_FAVOURITE.USER_UUID.eq(userId))
                .fetch(r -> new DocRef(
                        r.get(EXPLORER_FAVOURITE.DOC_TYPE),
                        r.get(EXPLORER_FAVOURITE.DOC_UUID)
                )));
    }

    @Override
    public boolean isFavourite(final DocRef docRef, final String userId) {
        return JooqUtil.contextResult(docStoreDbConnProvider, context -> context
                .fetchCount(EXPLORER_FAVOURITE,
                        EXPLORER_FAVOURITE.USER_UUID.eq(userId)
                                .and(EXPLORER_FAVOURITE.DOC_TYPE.eq(docRef.getType()))
                                .and(EXPLORER_FAVOURITE.DOC_UUID.eq(docRef.getUuid()))) > 0);
    }
}
