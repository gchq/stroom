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

package stroom.gitrepo.impl.db;

import stroom.db.util.JooqUtil;
import stroom.gitrepo.impl.GitRepoDao;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.jooq.Record1;
import org.jooq.Result;

import static stroom.gitrepo.impl.db.jooq.tables.GitRepo.GIT_REPO;

/**
 * DAO implementation for GitRepoDoc state.
 * Stores state that is set on the server rather than in the UI,
 * thus avoids version issues between the server and UI.
 */
public class GitRepoDaoImpl implements GitRepoDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GitRepoDaoImpl.class);

    /** Bootstraps connection */
    private final GitRepoDbConnProvider gitRepoDbConnProvider;

    /**
     * Injected constructor.
     * @param gitRepoDbConnProvider Parameter to bootstrap DB connection.
     */
    @SuppressWarnings("unused")
    @Inject
    GitRepoDaoImpl(final GitRepoDbConnProvider gitRepoDbConnProvider) {
        this.gitRepoDbConnProvider = gitRepoDbConnProvider;
    }

    /**
     * Stores the Git commit hash for a given GitRepoDoc UUID.
     * @param uuid The UUID of the doc that this matches.
     * @param hash The Git commit hash that we've obtained from Git.
     */
    @Override
    public void storeHash(final String uuid, final String hash) {
        LOGGER.debug("Storing GitRepo UUID '{}' -> Git Commit Hash '{}'", uuid, hash);
        JooqUtil.context(gitRepoDbConnProvider, context -> context
                .insertInto(GIT_REPO)
                .columns(GIT_REPO.GIT_REPO_UUID, GIT_REPO.GIT_COMMIT_HASH)
                .values(uuid, hash)
                .onDuplicateKeyUpdate()
                .set(GIT_REPO.GIT_REPO_UUID, uuid)
                .set(GIT_REPO.GIT_COMMIT_HASH, hash)
                .execute()
        );
    }

    /**
     * Returns the Git commit hash for a given GitRepoDoc UUID.
     * @param uuid The UUID of the doc that we want the hash for.
     * @return The Git commit hash, or null if nothing has been stored
     * for the given UUID.
     */
    @Override
    public String getHash(final String uuid) {
        final Result<Record1<String>> result = JooqUtil.contextResult(gitRepoDbConnProvider, context -> context
                .select(GIT_REPO.GIT_COMMIT_HASH)
                .from(GIT_REPO)
                .where(GIT_REPO.GIT_REPO_UUID.eq(uuid))
                .fetch());

        final String hash;
        if (result.isEmpty()) {
            hash = null;
        } else {
            hash = result.getValues(GIT_REPO.GIT_COMMIT_HASH).getFirst();
        }
        LOGGER.debug("Retrieving Git Repo UUID '{}' -> Git Commit Hash '{}'", uuid, hash);
        return hash;
    }

}
