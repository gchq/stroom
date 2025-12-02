package stroom.contentstore.impl;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides configuration for the ContentStore stuff on the server.
 */
@JsonPropertyOrder(alphabetic = true)
public class ContentStoreConfig extends AbstractConfig implements IsStroomConfig {
    /**
     * Default location where the ContentStore config is stored.
     */
    static final String DEFAULT_URL =
            "https://raw.githubusercontent.com/gchq/stroom-content/refs/heads/master/source/content-store.yml";

    /**
     * List of App Store URLS
     */
    private final List<String> contentStores = new ArrayList<>();

    /**
     * Logger
     */
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContentStoreConfig.class);

    /**
     * Default constructor. Configuration created with default values.
     */
    public ContentStoreConfig() {
        this.contentStores.add(DEFAULT_URL);
    }

    /**
     * Constructor called when creating configuration from JSON or YAML.
     * @param contentStores The list of contentstore URLs
     */
    @SuppressWarnings("unused")
    @JsonCreator
    public ContentStoreConfig(@JsonProperty("urls") final List<String> contentStores) {
        if (contentStores == null || contentStores.isEmpty()) {
            LOGGER.info("No Content Store URLs supplied in the configuration file; using default of '{}'",
                         DEFAULT_URL);
            this.contentStores.add(DEFAULT_URL);
        } else {
            this.contentStores.addAll(contentStores);
        }
    }

   /**
     * @return Where to download Content Stores from
     */
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The URLs of the Content Stores for Stroom Content")
    @JsonProperty("urls")
    public List<String> getContentStoreUrls() {
        return this.contentStores;
    }

    /**
     * Sets where to download  Content Stores from. If the parameter is
     * null or empty then the default URL is used.
     * @param urls The list of URLs for the Content Store. Can be null or empty,
     *             in which case the default URL is used.
     */
    @SuppressWarnings("unused")
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The URLs of the Content Stores for Stroom Content")
    public void setContentStoreUrls(final List<String> urls) {
        this.contentStores.clear();
        if (urls != null && !urls.isEmpty()) {
            this.contentStores.addAll(urls);
        }
    }

    /**
     * @return debug info about this object.
     */
    @Override
    public String toString() {
        return "ContentStoreConfig { contentStoreUrls='" + contentStores + "'}";
    }

}
