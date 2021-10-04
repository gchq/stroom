package stroom.security.identity.db;

import stroom.db.util.JooqUtil;
import stroom.security.identity.db.jooq.tables.records.JsonWebKeyRecord;
import stroom.security.identity.token.JwkDao;
import stroom.security.identity.token.KeyTypeDao;
import stroom.security.openid.api.JsonWebKeyFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.jose4j.jwk.PublicJsonWebKey;

import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.security.identity.db.jooq.tables.JsonWebKey.JSON_WEB_KEY;

@Singleton
class JwkDaoImpl implements JwkDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JwkDaoImpl.class);
    private static final int MIN_KEY_AGE_MS = 1000 * 60 * 60 * 24;
    private static final int MAX_KEY_AGE_MS = 1000 * 60 * 60 * 24 * 2;

    private final IdentityDbConnProvider identityDbConnProvider;
    private final JsonWebKeyFactory jsonWebKeyFactory;
    private final KeyTypeDao keyTypeDao;

    @Inject
    JwkDaoImpl(final IdentityDbConnProvider identityDbConnProvider,
               final KeyTypeDao keyTypeDao,
               final JsonWebKeyFactory jsonWebKeyFactory) {
        this.identityDbConnProvider = identityDbConnProvider;
        this.keyTypeDao = keyTypeDao;
        this.jsonWebKeyFactory = jsonWebKeyFactory;
    }

    /**
     * This will always return a list of public keys creating them if needed.
     */
    @Override
    public List<PublicJsonWebKey> readJwk() {
//        // Delete old records.
//        deleteOldJwkRecords();

        // Add new records.
        addRecords();

        // Fetch back all records.
        return JooqUtil.contextResult(identityDbConnProvider, context ->
                        context
                                .selectFrom(JSON_WEB_KEY)
                                .fetch())
                .map(jsonWebKeyRecord -> jsonWebKeyFactory.fromJson(jsonWebKeyRecord.getJson()));
    }

    private void addRecords() {
        // FOr the time being just make sure there is a record.
        final List<JsonWebKeyRecord> list = JooqUtil.contextResult(identityDbConnProvider, context ->
                context.selectFrom(JSON_WEB_KEY).fetch());
        if (list.size() < 1) {
            addRecord();
        }
    }

    private void addRecord() {
        final long now = System.currentTimeMillis();
        final String uuid = UUID.randomUUID().toString();
        // We need to set up the jwkId so we know which JWTs were signed by which JWKs.
        final PublicJsonWebKey publicJsonWebKey = jsonWebKeyFactory.createPublicKey();
        final int typeId = keyTypeDao.getTypeId("JWK");

        LOGGER.debug(() -> LogUtil.message("Creating a {}", JSON_WEB_KEY.getName()));
        final JsonWebKeyRecord record = JSON_WEB_KEY.newRecord();
        record.setKeyId(uuid);
        record.setJson(jsonWebKeyFactory.asJson(publicJsonWebKey));
        record.setCreateTimeMs(now);
        record.setCreateUser("admin");
        record.setUpdateTimeMs(now);
        record.setUpdateUser("admin");
        record.setFkTokenTypeId(typeId);
        record.setEnabled(true);
        JooqUtil.create(identityDbConnProvider, record);
    }
}
