package stroom.gitrepo.impl.db;

public interface GitRepoDao {
    /** TODO correct method */
    void storeHash(String uuid, String hash);

    /** TODO correct method */
    String getHash(String uuid);

}
