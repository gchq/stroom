package stroom.docstore.fav.impl.db;

import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.docstore.fav.impl.DocFavDao;
import stroom.docstore.impl.db.DocStoreDbConnProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jooq.exception.DataAccessException;

import java.time.Instant;
import java.util.List;
import javax.inject.Inject;

import static stroom.docstore.fav.impl.db.jooq.Tables.DOC_FAVOURITE;

public class DocFavDaoImpl implements DocFavDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocFavDaoImpl.class);

    private final DocStoreDbConnProvider docStoreDbConnProvider;

    @Inject
    DocFavDaoImpl(final DocStoreDbConnProvider docStoreDbConnProvider) {
        this.docStoreDbConnProvider = docStoreDbConnProvider;
    }

    @Override
    public void setDocFavForUser(final DocRef docRef, final String userId) {
        final int count = JooqUtil.contextResult(docStoreDbConnProvider, context -> context
                .insertInto(DOC_FAVOURITE,
                        DOC_FAVOURITE.DOC_TYPE,
                        DOC_FAVOURITE.DOC_UUID,
                        DOC_FAVOURITE.USER_UUID,
                        DOC_FAVOURITE.CREATE_TIME_MS)
                .values(docRef.getType(),
                        docRef.getUuid(),
                        userId,
                        Instant.now().toEpochMilli())
                .onDuplicateKeyIgnore()
                .execute());

        if (count != 1) {
            throw new RuntimeException("Failed to create a doc_favourite entry for doc: " +
                    docRef.getUuid() + ", user: " + userId);
        }
    }

    @Override
    public void deleteDocFavForUser(final DocRef docRef, final String userId) {
        final int count = JooqUtil.contextResult(docStoreDbConnProvider, context -> context
                .deleteFrom(DOC_FAVOURITE)
                .where(DOC_FAVOURITE.DOC_TYPE.eq(docRef.getType()))
                .and(DOC_FAVOURITE.DOC_UUID.eq(docRef.getUuid()))
                .and(DOC_FAVOURITE.USER_UUID.eq(userId))
                .execute());

        if (count != 1) {
            throw new RuntimeException("Failed to remove doc_favourite entry for doc: " +
                    docRef.getUuid() + ", user: " + userId);
        }
    }

    @Override
    public List<DocRef> getUserDocFavs(final String userId) {
        return JooqUtil.contextResult(docStoreDbConnProvider, context -> context
                .select()
                .from(DOC_FAVOURITE)
                .where(DOC_FAVOURITE.USER_UUID.eq(userId))
                .fetch(r -> new DocRef(
                        r.get(DOC_FAVOURITE.DOC_TYPE),
                        r.get(DOC_FAVOURITE.DOC_UUID)
                )));
    }

    @Override
    public boolean isDocFav(final DocRef docRef, final String userId) {
        return JooqUtil.contextResult(docStoreDbConnProvider, context -> context
                .fetchCount(DOC_FAVOURITE,
                        DOC_FAVOURITE.DOC_TYPE.eq(docRef.getType())
                        .and(DOC_FAVOURITE.DOC_UUID.eq(docRef.getUuid()))
                        .and(DOC_FAVOURITE.USER_UUID.eq(userId)))) > 0;
    }
}
