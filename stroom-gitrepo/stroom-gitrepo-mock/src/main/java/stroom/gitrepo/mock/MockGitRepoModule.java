package stroom.gitrepo.mock;

import stroom.gitrepo.api.GitRepoStorageService;
import stroom.gitrepo.api.GitRepoStore;
import stroom.gitrepo.impl.GitRepoDao;
import stroom.gitrepo.impl.GitRepoStorageServiceImpl;
import stroom.gitrepo.impl.GitRepoStoreImpl;

import com.google.inject.AbstractModule;

import java.util.HashMap;
import java.util.Map;

public class MockGitRepoModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(GitRepoStore.class).to(GitRepoStoreImpl.class);
        bind(GitRepoStorageService.class).to(GitRepoStorageServiceImpl.class);
        bind(GitRepoDao.class).to(MockGitRepoDaoImpl.class);
        //bind(CredentialsDao.class).to(MockCredentialsDao.class);
    }

    /**
     * Mock DAO implementation.
     */
    public static class MockGitRepoDaoImpl implements GitRepoDao {

        /** Mock DB */
        private final Map<String, String> mockDb = new HashMap<>();

        @Override
        public void storeHash(final String uuid, final String hash) {
            mockDb.put(uuid, hash);
        }

        @Override
        public String getHash(final String uuid) {
            return mockDb.get(uuid);
        }
    }

}
