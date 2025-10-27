package stroom.gitrepo.impl;

public interface GitRepoDao {

    /**
     * Stores the Git commit hash for a given GitRepoDoc UUID.
     * @param uuid The UUID of the doc that this matches.
     * @param hash The Git commit hash that we've obtained from Git.
     */
    void storeHash(String uuid, String hash);

    /**
     * Returns the Git commit hash for a given GitRepoDoc UUID.
     * @param uuid The UUID of the doc that we want the hash for.
     * @return The Git commit hash, or null.
     */
    String getHash(String uuid);

}
