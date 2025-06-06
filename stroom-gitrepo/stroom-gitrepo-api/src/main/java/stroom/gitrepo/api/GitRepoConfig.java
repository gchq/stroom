package stroom.gitrepo.api;

/**
 * Interface for the configuration for the GitRepo objects.
 */
public interface GitRepoConfig {

    /**
     * Returns the storage location for local Git repositories.
     * Will be relative to the home directory.
     * @return The storage location for local Git repositories.
     */
    String getLocalDir();

}
