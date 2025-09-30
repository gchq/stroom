package stroom.credentials.impl.db;

import stroom.credentials.impl.CredentialsDao;
import stroom.credentials.impl.db.jooq.tables.records.CredentialsRecord;
import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsSecret;
import stroom.credentials.shared.CredentialsType;
import stroom.db.util.JooqUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import org.jooq.Result;
import org.jooq.exception.DataAccessException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static stroom.credentials.impl.db.jooq.tables.Credentials.CREDENTIALS;

/**
 * Implementation of the Credentials DAO.
 */
public class CredentialsDaoImpl implements CredentialsDao, Clearable {

    /** Bootstrap connection to DB */
    private final CredentialsDbConnProvider credentialsDbConnProvider;

    /** Logger */
    @SuppressWarnings("unused")
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CredentialsDaoImpl.class);

    /**
     * Injected constructor.
     */
    @SuppressWarnings("unused")
    @Inject
    CredentialsDaoImpl(final CredentialsDbConnProvider credentialsDbConnProvider) {
        this.credentialsDbConnProvider = credentialsDbConnProvider;
    }

    /**
     * Converts the Jooq generated CredentialsRecord into a Credentials object.
     * @param record The Jooq object to convert
     * @return Our Credentials object.
     */
    private Credentials recordToCredentials(final CredentialsRecord record) {
        if (record == null) {
            return null;
        } else {
            final CredentialsSecret secret =
                    JsonUtil.readValue(record.getSecret(), CredentialsSecret.class);
            return new Credentials(
                    record.getName(),
                    record.getUuid(),
                    CredentialsType.valueOf(record.getType()),
                    record.getCredsexpire() == 1,
                    record.getExpires(),
                    secret);
        }
    }

    @Override
    public List<Credentials> list() throws IOException {
        try {
            final Result<CredentialsRecord> result =
                    JooqUtil.contextResult(credentialsDbConnProvider, context -> context
                            .fetch(CREDENTIALS)
                            .sortAsc(CREDENTIALS.NAME));

            final List<Credentials> list = new ArrayList<>(result.size());
            for (final CredentialsRecord record : result) {
                list.add(recordToCredentials(record));
            }

            return list;

        } catch (final DataAccessException e) {
            LOGGER.error("Error listing credentials: {}", e.getMessage(), e);
            throw new IOException("Error listing credentials: " + e.getMessage(), e);
        }
    }

    @Override
    public Credentials get(final String uuid) throws IOException {
        try {
            final CredentialsRecord credRecord =
                    JooqUtil.contextResult(credentialsDbConnProvider, context -> context
                            .fetchOne(CREDENTIALS, CREDENTIALS.UUID.eq(uuid)));

            return recordToCredentials(credRecord);

        } catch (final DataAccessException e) {
            LOGGER.error("Error gettings credentials: {}", e.getMessage(), e);
            throw new IOException("Error getting credentials for '" + uuid + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void store(final Credentials credentials) throws IOException {
        try {
            JooqUtil.context(credentialsDbConnProvider, context -> context
                    .insertInto(CREDENTIALS)
                    .columns(CREDENTIALS.UUID,
                            CREDENTIALS.NAME,
                            CREDENTIALS.TYPE,
                            CREDENTIALS.CREDSEXPIRE,
                            CREDENTIALS.EXPIRES,
                            CREDENTIALS.SECRET)
                    .values(credentials.getUuid(),
                            credentials.getName(),
                            credentials.getType().name(),
                            (byte)(credentials.isCredsExpire() ? 1 : 0),
                            credentials.getExpires(),
                            JsonUtil.writeValueAsString(credentials.getSecret()))
                    .onDuplicateKeyUpdate()
                    .set(CREDENTIALS.UUID, credentials.getUuid())
                    .set(CREDENTIALS.NAME, credentials.getName())
                    .set(CREDENTIALS.TYPE, credentials.getType().name())
                    .set(CREDENTIALS.CREDSEXPIRE, (byte)(credentials.isCredsExpire() ? 1 : 0))
                    .set(CREDENTIALS.EXPIRES, credentials.getExpires())
                    .set(CREDENTIALS.SECRET, JsonUtil.writeValueAsString(credentials.getSecret()))
                    .execute());
        } catch (final DataAccessException e) {
            LOGGER.error("Error storing credentials: {}", e.getMessage(), e);
            throw new IOException("Error storing credentials: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(final String uuid) throws IOException {
        try {
            JooqUtil.context(credentialsDbConnProvider, context -> context
                    .deleteFrom(CREDENTIALS)
                    .where(CREDENTIALS.UUID.eq(uuid))
                    .execute());
        } catch (final DataAccessException e) {
            LOGGER.error("Error deleting credentials: {}", e.getMessage(), e);
            throw new IOException("Error deleting credentials: " + e.getMessage(), e);
        }
    }

    /**
     * Used by tests to clear the DB.
     */
    @Override
    public void clear() {
        JooqUtil.context(credentialsDbConnProvider, context -> context.deleteFrom(CREDENTIALS).execute());
    }

}
