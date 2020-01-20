package stroom.auth.daos;

import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.lang.JoseException;
import stroom.auth.AuthDbConnProvider;
import stroom.auth.db.Tables;
import stroom.auth.db.tables.records.JsonWebKeyRecord;
import stroom.db.util.JooqUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.UUID;

@Singleton
public class JwkDao {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(JwkDao.class);

    private DSLContext database = null;
    private AuthDbConnProvider authDbConnProvider;

    @Inject
    JwkDao(final AuthDbConnProvider authDbConnProvider) {
        this.authDbConnProvider = authDbConnProvider;
    }
    /**
     * This will always return a single public key. If the key doesn't exist it will create it.
     * If it does exist it will return that.
     */
    public PublicJsonWebKey readJwk() {
        try {
            JooqUtil.context(authDbConnProvider, context ->
                    context.selectFrom(Tables.JSON_WEB_KEY).fetchOne());
            JsonWebKeyRecord existingJwkRecord = database.selectFrom(Tables.JSON_WEB_KEY).fetchOne();

            if(existingJwkRecord == null ) {
                LOGGER.info("We don't have a saved JWK so we'll create one and save it for use next time.");
                // We need to set up the jwkId so we know which JWTs were signed by which JWKs.
                String jwkId = UUID.randomUUID().toString();
                RsaJsonWebKey jwk = RsaJwkGenerator.generateJwk(2048);
                jwk.setKeyId(jwkId);

                // Persist the public key
                JsonWebKeyRecord jwkRecord = new JsonWebKeyRecord();
                jwkRecord.setKeyid(jwkId);
                jwkRecord.setJson(jwk.toJson(JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE));
                database.executeInsert(jwkRecord);

                return jwk;
            }
            else {
                LOGGER.info("We do have a saved JWK so we'll re-use it.");
                PublicJsonWebKey jwk = RsaJsonWebKey.Factory.newPublicJwk(existingJwkRecord.getJson());
                return jwk;
            }
        } catch (JoseException e) {
            LOGGER.error("Unable to create JWK!", e);
            throw new RuntimeException(e);
        }
    }
}
