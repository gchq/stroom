package stroom.dashboard.impl.visualisation;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for Visualisation Asset Management, notably mimetype mapping.
 */
public class VisualisationAssetConfig extends AbstractConfig {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(VisualisationAssetConfig.class);

    /** Default location where assets will be cached */
    private static final String DEFAULT_ASSET_CACHE_DIR = "asset_cache";

    /** Default mimetype map */
    private static final Map<String, String> DEFAULT_MIMETYPES = new HashMap<>();

    /** Mimetype if nothing else matches */
    private static final String DEFAULT_MIMETYPE = "application/octet-stream";

    /** Where assets will be cached */
    private final String assetCacheDir;

    /** Map of filename extension to mimetype */
    private final Map<String, String> mimetypes = new HashMap<>();

    /** Mimetype to use if nothing in the map matches */
    private final String defaultMimetype;

    /*
     * Initialise mimetype map.
     * Values from here: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types/Common_types
     */
    static {
        DEFAULT_MIMETYPES.put("apng", "image/apng");
        DEFAULT_MIMETYPES.put("bmp", "image/bmp");
        DEFAULT_MIMETYPES.put("css", "text/css");
        DEFAULT_MIMETYPES.put("gif", "image/jpeg");
        DEFAULT_MIMETYPES.put("html", "text/html");
        DEFAULT_MIMETYPES.put("htm", "text/html");
        DEFAULT_MIMETYPES.put("jpg", "image/jpeg");
        DEFAULT_MIMETYPES.put("jpeg", "image/jpeg");
        DEFAULT_MIMETYPES.put("js", "text/javascript");
        DEFAULT_MIMETYPES.put("png", "image/png");
        DEFAULT_MIMETYPES.put("svg", "image/svg+xml");
        DEFAULT_MIMETYPES.put("tif", "image/tiff");
        DEFAULT_MIMETYPES.put("tiff", "image/tiff");
        DEFAULT_MIMETYPES.put("txt", "text/plain");
        DEFAULT_MIMETYPES.put("webp", "image/webp");
        DEFAULT_MIMETYPES.put("xml", "application/xml");
    }

    /**
     * Default constructor. Configuration created with default values.
     */
    public VisualisationAssetConfig() {
        this.mimetypes.putAll(DEFAULT_MIMETYPES);
        this.defaultMimetype = DEFAULT_MIMETYPE;
        this.assetCacheDir = DEFAULT_ASSET_CACHE_DIR;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public VisualisationAssetConfig(@JsonProperty("mimetypes") final Map<String, String> mimetypes,
                                    @JsonProperty("default") final String defaultMimetype,
                                    @JsonProperty("assetCacheDir") final String assetCacheDir) {
        if (mimetypes == null || mimetypes.isEmpty()) {
            LOGGER.info("No mimetypes supplied in the configuration file; using default values");
        } else {
            this.mimetypes.putAll(mimetypes);
        }

        if (defaultMimetype == null || defaultMimetype.isEmpty()) {
            LOGGER.info("No default mimetype supplied in the configuration file; using default value");
            this.defaultMimetype = DEFAULT_MIMETYPE;
        } else {
            this.defaultMimetype = defaultMimetype;
        }

        if (assetCacheDir == null || assetCacheDir.isEmpty()) {
            LOGGER.info("No default asset cache directory supplied in the configuration file; using default value");
            this.assetCacheDir = DEFAULT_ASSET_CACHE_DIR;
        } else {
            this.assetCacheDir = assetCacheDir;
        }
    }

    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("The mimetypes map from extension to mimetype for the asset manager")
    @JsonProperty("mimetypes")
    public Map<String, String> getMimetypes() {
        return Collections.unmodifiableMap(mimetypes);
    }

    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("Mimetype to use if nothing else matches")
    @JsonProperty("default")
    public String getDefaultMimetype() {
        return defaultMimetype;
    }

    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("The path relative to the home directory to use "
                             + "for storing cached assets.")
    @JsonProperty("assetCacheDir")
    public String getAssetCacheDir() {
        return assetCacheDir;
    }

    @BootStrapConfig
    public static class VisualisationAssetDbConfig extends AbstractDbConfig {
        public VisualisationAssetDbConfig() {
            super();
        }

        @JsonCreator
        @SuppressWarnings("unused")
        public VisualisationAssetDbConfig(
                @JsonProperty(AbstractDbConfig.PROP_NAME_CONNECTION)
                final ConnectionConfig connectionConfig,
                @JsonProperty(AbstractDbConfig.PROP_NAME_CONNECTION_POOL)
                final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }

    }

}
