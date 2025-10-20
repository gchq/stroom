package stroom.credentials.impl.db;

import stroom.credentials.impl.CredentialsDao;
import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsSecret;
import stroom.credentials.shared.CredentialsType;

import com.google.inject.AbstractModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Credentials DAO module used in tests.
 */
public class MockCredentialsDaoModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();
        bind(CredentialsDao.class).to(MockCredentialsDao.class);
    }

    /**
     * Mock implementation of credentials DAO based on HashSet.
     */
    public static class MockCredentialsDao implements CredentialsDao {

        private final Map<String, Credentials> idToCred = new HashMap<>();
        private final Map<String, CredentialsSecret> idToSecret = new HashMap<>();

        @Override
        public List<Credentials> listCredentials() {
            return new ArrayList<>(idToCred.values());
        }

        @Override
        public List<Credentials> listCredentials(final CredentialsType type) {
            final List<Credentials> allCreds = this.listCredentials();
            return allCreds.stream().filter(c -> c.getType() == type).toList();
        }

        @Override
        public Credentials createCredentials(final Credentials clientCredentials) {
            final Credentials dbCredentials = clientCredentials.copyWithUuid(UUID.randomUUID().toString());
            idToCred.put(clientCredentials.getUuid(), dbCredentials);
            return dbCredentials;
        }

        @Override
        public void storeCredentials(final Credentials credentials) {
            idToCred.put(credentials.getUuid(), credentials);
        }

        @Override
        public Credentials getCredentials(final String uuid) {
            return idToCred.get(uuid);
        }

        @Override
        public void deleteCredentialsAndSecret(final String uuid) {
            idToCred.remove(uuid);
        }

        @Override
        public void storeSecret(final CredentialsSecret secret) {
            idToSecret.put(secret.getUuid(), secret);
        }

        @Override
        public CredentialsSecret getSecret(final String uuid) {
            return idToSecret.get(uuid);
        }
    }
}
