/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
