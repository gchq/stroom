package stroom.credentials.impl.db;

import stroom.credentials.impl.CredentialsDao;
import stroom.credentials.impl.db.jooq.tables.records.CredentialsRecord;
import stroom.credentials.impl.db.jooq.tables.records.CredentialsSecretRecord;
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
import java.util.UUID;

import static stroom.credentials.impl.db.jooq.tables.Credentials.CREDENTIALS;
import static stroom.credentials.impl.db.jooq.tables.CredentialsSecret.CREDENTIALS_SECRET;

/**
 * Implementation of the Credentials DAO.
 */
public class CredentialsDaoImpl implements CredentialsDao, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CredentialsDaoImpl.class);

    /** Bootstrap connection to DB */
    private final CredentialsDbConnProvider credentialsDbConnProvider;

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
            return new Credentials(
                    record.getName(),
                    record.getUuid(),
                    CredentialsType.valueOf(record.getType()),
                    record.getCredsexpire() == 1,
                    record.getExpires());
        }
    }

    /**
     * Converts the Jooq generated CredentialsSecretRecord into a CredentialsSecret object.
     * @param record The Jooq object to convert
     * @return Our CredentialsSecret object.
     */
    private CredentialsSecret recordToSecret(final CredentialsSecretRecord record) {
        if (record == null) {
            return null;
        } else {
            return JsonUtil.readValue(record.getSecret(), CredentialsSecret.class);

        }
    }

    @Override
    public List<Credentials> listCredentials() throws IOException {
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
    public List<Credentials> listCredentials(final CredentialsType type)
        throws IOException {

        final List<Credentials> retval;

        if (type == null) {
            retval = this.listCredentials();
        } else {
            try {
                final Result<CredentialsRecord> result =
                        JooqUtil.contextResult(credentialsDbConnProvider, context -> context
                                .fetch(CREDENTIALS, CREDENTIALS.TYPE.eq(type.name()))
                                .sortAsc(CREDENTIALS.NAME));

                retval = new ArrayList<>(result.size());
                for (final CredentialsRecord record : result) {
                    retval.add(recordToCredentials(record));
                }

            } catch (final DataAccessException e) {
                LOGGER.error("Error listing credentials of type '{}': {}", type, e.getMessage(), e);
                throw new IOException("Error listing credentials of type '"
                                      + type + "': " + e.getMessage(), e);
            }
        }

        return retval;
    }

    @Override
    public Credentials createCredentials(final Credentials clientCredentials) throws IOException {
        // New UUID for credentials
        final String dbUuid = UUID.randomUUID().toString();
        final Credentials dbCredentials = clientCredentials.copyWithUuid(dbUuid);

        LOGGER.info("Creating credentials: {}", dbCredentials);
        try {
            JooqUtil.context(credentialsDbConnProvider, context -> context
                    .insertInto(CREDENTIALS)
                    .columns(CREDENTIALS.UUID,
                            CREDENTIALS.NAME,
                            CREDENTIALS.TYPE,
                            CREDENTIALS.CREDSEXPIRE,
                            CREDENTIALS.EXPIRES)
                    .values(dbCredentials.getUuid(),
                            dbCredentials.getName(),
                            dbCredentials.getType().name(),
                            (byte) (dbCredentials.isCredsExpire()
                                    ? 1
                                    : 0),
                            dbCredentials.getExpires())
                    .execute());
        } catch (final DataAccessException e) {
            LOGGER.error("Error creating credentials: {}", e.getMessage(), e);
            throw new IOException("Error creating credentials: '" + e.getMessage() + "'", e);
        }

        return dbCredentials;
    }

    @Override
    public Credentials getCredentials(final String uuid) throws IOException {
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
    public void storeCredentials(final Credentials credentials) throws IOException {
        try {
            JooqUtil.context(credentialsDbConnProvider, context -> context
                    .update(CREDENTIALS)
                    .set(CREDENTIALS.NAME, credentials.getName())
                    .set(CREDENTIALS.TYPE, credentials.getType().name())
                    .set(CREDENTIALS.CREDSEXPIRE, (byte) (credentials.isCredsExpire() ? 1 : 0))
                    .set(CREDENTIALS.EXPIRES, credentials.getExpires())
                    .where(CREDENTIALS.UUID.eq(credentials.getUuid()))
                    .execute());
        } catch (final DataAccessException e) {
            LOGGER.error("Error storing credentials: {}", e.getMessage(), e);
            throw new IOException("Error storing credentials: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteCredentialsAndSecret(final String uuid) throws IOException {
        DataAccessException exception = null;
        try {
            JooqUtil.context(credentialsDbConnProvider, context -> context
                    .deleteFrom(CREDENTIALS)
                    .where(CREDENTIALS.UUID.eq(uuid))
                    .execute());
        } catch (final DataAccessException e) {
            // Store the exception for later so we can try to delete the secrets
            exception = e;
        }

        try {
            JooqUtil.context(credentialsDbConnProvider, context -> context
                    .deleteFrom(CREDENTIALS_SECRET)
                    .where(CREDENTIALS_SECRET.UUID.eq(uuid))
                    .execute());

            // Throw any exception from deleting the credentials
            if (exception != null) {
                throw exception;
            }

        } catch (final DataAccessException e) {
            LOGGER.error("Error deleting credentials: {}", e.getMessage(), e);
            throw new IOException("Error deleting credentials: " + e.getMessage(), e);
        }
    }

    @Override
    public void storeSecret(final CredentialsSecret secret) throws IOException {
        try {
            JooqUtil.context(credentialsDbConnProvider, context -> context
                    .insertInto(CREDENTIALS_SECRET)
                    .columns(CREDENTIALS_SECRET.UUID,
                            CREDENTIALS_SECRET.SECRET)
                    .values(secret.getUuid(),
                            JsonUtil.writeValueAsString(secret))
                    .onDuplicateKeyUpdate()
                    .set(CREDENTIALS_SECRET.UUID, secret.getUuid())
                    .set(CREDENTIALS_SECRET.SECRET, JsonUtil.writeValueAsString(secret))
                    .execute());
        } catch (final DataAccessException e) {
            // TODO Ensure nothing secret is logged
            LOGGER.error("Error storing credential's secrets: {}", e.getMessage(), e);
            throw new IOException("Error storing credential's secrets");
        }
    }

    @Override
    public CredentialsSecret getSecret(final String uuid) throws IOException {
        try {
            final CredentialsSecretRecord secretRecord =
                    JooqUtil.contextResult(credentialsDbConnProvider, context -> context
                            .fetchOne(CREDENTIALS_SECRET,
                                    CREDENTIALS_SECRET.UUID.eq(uuid)));

            return recordToSecret(secretRecord);

        } catch (final DataAccessException e) {
            LOGGER.error("Error getting credentials: {}", e.getMessage(), e);
            throw new IOException("Error getting credentials for '" + uuid + "': " + e.getMessage(), e);
        }
    }

    /**
     * Used by tests to clear the DB.
     */
    @Override
    public void clear() {
        JooqUtil.context(credentialsDbConnProvider,
                context -> context.deleteFrom(CREDENTIALS).execute());
        JooqUtil.context(credentialsDbConnProvider,
                context -> context.deleteFrom(CREDENTIALS_SECRET).execute());
    }

}
