package stroom.credentials.impl.db;

import stroom.credentials.api.StoredSecret;
import stroom.credentials.impl.CredentialsDao;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.FindCredentialRequest;
import stroom.util.shared.ResultPage;

import com.google.inject.AbstractModule;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

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

        private final Map<String, StoredSecret> uuidToCred = new HashMap<>();
        private final Map<String, StoredSecret> nameToCred = new HashMap<>();

        @Override
        public ResultPage<CredentialWithPerms> findCredentialsWithPermissions(final FindCredentialRequest request,
                                                                              final Function<Credential, CredentialWithPerms> permissionDecorator) {
            return ResultPage.createUnboundedList(uuidToCred.values()
                    .stream()
                    .map(StoredSecret::credential)
                    .map(credential -> new CredentialWithPerms(credential, true, true))
                    .toList());
        }

        @Override
        public ResultPage<Credential> findCredentials(final FindCredentialRequest request,
                                                      final Predicate<Credential> permissionFilter) {
            return ResultPage.createUnboundedList(uuidToCred.values()
                    .stream()
                    .map(StoredSecret::credential)
                    .toList());
        }

        @Override
        public Credential getCredentialByUuid(final String uuid) {
            return Optional.ofNullable(uuidToCred.get(uuid)).map(StoredSecret::credential).orElse(null);
        }

        @Override
        public Credential getCredentialByName(final String name) {
            return Optional.ofNullable(nameToCred.get(name)).map(StoredSecret::credential).orElse(null);
        }

        @Override
        public void deleteCredentialsAndSecret(final String uuid) {
            final StoredSecret secret = uuidToCred.remove(uuid);
            if (secret != null) {
                nameToCred.remove(secret.credential().getName());
            }
        }

        @Override
        public StoredSecret getStoredSecretByName(final String name) {
            return nameToCred.get(name);
        }

        @Override
        public void putStoredSecret(final StoredSecret secret, final boolean update) {
            deleteCredentialsAndSecret(secret.credential().getUuid());
            uuidToCred.put(secret.credential().getUuid(), secret);
            nameToCred.put(secret.credential().getName(), secret);
        }
    }
}
