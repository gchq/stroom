package stroom.appstore.api;

import java.util.ArrayList;

/**
 * Interface for the configuration for the GitRepo objects.
 */
public interface AppStoreConfig {

    /**
     * @return the URLs of the app stores for Stroom content.
     * Never returns null.
     */
    @SuppressWarnings("unused")
    ArrayList<String> getAppStoreUrls();

}
