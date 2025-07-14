package stroom.gitrepo.impl.db;

import javax.sql.DataSource;

/**
 * Interface for hooking into Flyway
 */
interface GitRepoDbConnProvider extends DataSource {
    // No code
}
