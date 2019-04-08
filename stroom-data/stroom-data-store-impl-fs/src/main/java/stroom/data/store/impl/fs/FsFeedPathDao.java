package stroom.data.store.impl.fs;

/**
 * This class exists to map feed id's to file paths using old data from the DB.
 */
public interface FsFeedPathDao {
    String getOrCreatePath(String feedName);
}
