package stroom.contentstore.api;

import java.util.ArrayList;

/**
 * Interface for the configuration for the GitRepo objects.
 */
public interface ContentStoreConfig {

    /**
     * @return the URLs of the content stores for Stroom content.
     * Never returns null.
     */
    @SuppressWarnings("unused")
    ArrayList<String> getContentStoreUrls();

}
