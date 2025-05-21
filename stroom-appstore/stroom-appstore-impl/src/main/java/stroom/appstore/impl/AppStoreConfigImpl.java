package stroom.appstore.impl;

import stroom.appstore.api.AppStoreConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides configuration for the AppStore stuff on the server.
 */
@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class AppStoreConfigImpl extends AbstractConfig implements AppStoreConfig, IsStroomConfig {
    /**
     * Default location where the AppStore config is stored.
     */
    static final String DEFAULT_URL = "https://raw.githubusercontent.com/stroomworks4092/stroom-appstore/refs/heads/main/stroom-appstore.yml";

    /**
     * List of App Store URLS
     */
    private final ArrayList<String> appStores;

    /**
     * Logger
     */
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AppStoreConfigImpl.class);

    /**
     * Default constructor. Configuration created with default values.
     */
    public AppStoreConfigImpl() {
        this.appStores = new ArrayList<>();
        this.appStores.add(DEFAULT_URL);
    }

    /**
     * Constructor called when creating configuration from JSON or YAML.
     * @param appStores The list of appstore URLs
     */
    @SuppressWarnings("unused")
    @JsonCreator
    public AppStoreConfigImpl(@JsonProperty("appStoreUrls") final ArrayList<String> appStores) {
        this.appStores = new ArrayList<>();
        if (appStores == null || appStores.isEmpty()) {
            //LOGGER.debug("No appstore URLs supplied in the configuration file; using default of '{}'", DEFAULT_URL);
            this.appStores.add(DEFAULT_URL);
        } else {
            this.appStores.addAll(appStores);
        }
    }

    /**
     * @return Where to download AppStores from
     */
    @Override
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The URLs of the App Stores for Stroom Content")
    public ArrayList<String> getAppStoreUrls() {
        return this.appStores;
    }

    /**
     * Sets where to download AppStores from. If the parameter is
     * null or empty then the default URL is used.
     * @param urls The list of URLs for the App Store. Can be null or empty,
     *             in which case the default URL is used.
     */
    @SuppressWarnings("unused")
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The URLs of the App Stores for Stroom Content")
    public void setAppStoreUrls(List<String> urls) {
        this.appStores.clear();
        if (urls != null && !urls.isEmpty()) {
            this.appStores.addAll(urls);
        }
    }

    /**
     * @return debug info about this object.
     */
    @Override
    public String toString() {
        return "AppStoreConfig { appStoreUrls='" + appStores + "'}";
    }

}
