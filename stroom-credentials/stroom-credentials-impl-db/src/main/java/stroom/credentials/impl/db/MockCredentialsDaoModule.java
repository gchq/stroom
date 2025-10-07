package stroom.credentials.impl.db;

import stroom.credentials.impl.CredentialsDao;
import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsType;

import com.google.inject.AbstractModule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        @Override
        public List<Credentials> list() throws IOException {
            return new ArrayList<>(idToCred.values());
        }

        @Override
        public List<Credentials> list(final CredentialsType type) throws IOException {
            final List<Credentials> allCreds = this.list();
            return allCreds.stream().filter(c -> c.getType() == type).toList();
        }

        @Override
        public void store(final Credentials credentials) throws IOException {
            idToCred.put(credentials.getUuid(), credentials);
        }

        @Override
        public Credentials get(final String uuid) throws IOException {
            return idToCred.get(uuid);
        }

        @Override
        public void delete(final String uuid) throws IOException {
            idToCred.remove(uuid);
        }
    }
}
