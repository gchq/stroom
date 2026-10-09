/*
 * Copyright 2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.document.asset.impl;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.io.ByteSize;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for Document Asset Management, notably mimetype mapping.
 */
public class DocumentAssetConfig extends AbstractConfig {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocumentAssetConfig.class);

    /** Default location where assets will be cached */
    private static final String DEFAULT_ASSET_CACHE_DIR = "asset_cache";

    /** Default switch for whether to wipe the Servlet asset cache on startup - do not wipe */
    private static final boolean DEFAULT_CLEAR_ASSET_CACHE_ON_STARTUP = false;

    /** Default mimetype map */
    private static final Map<String, String> DEFAULT_MIMETYPES = new HashMap<>();

    /**
     * Largest asset upload accepted.
     *
     * <p>50 MiB, chosen against what assets are for — background images, icons, a visualisation's
     * own scripts — rather than against what the column could hold. Nothing bounded this before, so
     * the only limit was the client's patience and the database's; the 512 KiB constant nearby
     * gates <em>editing</em> an asset, not uploading one, so a large file uploaded fine and then
     * could not be opened.</p>
     */
    private static final ByteSize DEFAULT_MAX_UPLOAD_SIZE = ByteSize.ofMebibytes(50);

    /** Mimetype if nothing else matches */
    private static final String DEFAULT_MIMETYPE = "application/octet-stream";

    /** Default mapping from filename extension to ACE Editor mode */
    private static final Map<String, String> DEFAULT_EDITOR_MODES = new HashMap<>();

    /** Mode to use for editor if nothing else matches */
    private static final String DEFAULT_ACE_EDITOR_MODE = "TEXT";

    /** Where assets will be cached */
    private final String assetCacheDir;

    /** Whether to wipe the asset cache on startup */
    private final boolean clearAssetCacheOnStartup;

    /** Map of filename extension to mimetype */
    private final Map<String, String> mimetypes = new HashMap<>();

    /** Mimetype to use if nothing in the map matches */
    private final String defaultMimetype;

    /** Largest upload accepted; see {@link #DEFAULT_MAX_UPLOAD_SIZE}. */
    private final ByteSize maxUploadSize;

    /** Map of filename extension to ACE editor mode */
    private final Map<String, String> aceEditorModes = new HashMap<>();

    /** Default ACE editor mode */
    private final String defaultAceEditorMode;

    /*
     * Initialise mimetype map.
     * Values from here: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types/Common_types
     */
    static {
        DEFAULT_MIMETYPES.put("apng", "image/apng");
        DEFAULT_MIMETYPES.put("bmp", "image/bmp");
        DEFAULT_MIMETYPES.put("css", "text/css");
        DEFAULT_MIMETYPES.put("avif", "image/avif");
        DEFAULT_MIMETYPES.put("gif", "image/gif");
        DEFAULT_MIMETYPES.put("html", "text/html");
        DEFAULT_MIMETYPES.put("ico", "image/vnd.microsoft.icon");
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

        DEFAULT_EDITOR_MODES.put("css",  "CSS");
        DEFAULT_EDITOR_MODES.put("html", "HTML");
        DEFAULT_EDITOR_MODES.put("htm",  "HTML");
        DEFAULT_EDITOR_MODES.put("js",   "JAVASCRIPT");
        DEFAULT_EDITOR_MODES.put("svg",  "XML"); // We don't have SVG javascript loaded into the editor
        DEFAULT_EDITOR_MODES.put("txt",  "TEXT");
        DEFAULT_EDITOR_MODES.put("xml",  "XML");
    }

    /**
     * Configuration created with default values.
     */
    public DocumentAssetConfig() {
        this.mimetypes.putAll(DEFAULT_MIMETYPES);
        this.defaultMimetype = DEFAULT_MIMETYPE;
        this.assetCacheDir = DEFAULT_ASSET_CACHE_DIR;
        this.clearAssetCacheOnStartup = DEFAULT_CLEAR_ASSET_CACHE_ON_STARTUP;
        this.aceEditorModes.putAll(DEFAULT_EDITOR_MODES);
        this.defaultAceEditorMode = DEFAULT_ACE_EDITOR_MODE;
        this.maxUploadSize = DEFAULT_MAX_UPLOAD_SIZE;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public DocumentAssetConfig(@JsonProperty("mimetypes") final Map<String, String> mimetypes,
                               @JsonProperty("default") final String defaultMimetype,
                               @JsonProperty("assetCacheDir") final String assetCacheDir,
                               @JsonProperty("clearAssetCacheOnStartup") final Boolean clearAssetCacheOnStartup,
                               @JsonProperty("aceEditorModes") final Map<String, String> aceEditorModes,
                               @JsonProperty("defaultAceEditorMode") final String defaultAceEditorMode,
                               @JsonProperty("maxUploadSize") final ByteSize maxUploadSize) {

        this.maxUploadSize = Objects.requireNonNullElse(maxUploadSize, DEFAULT_MAX_UPLOAD_SIZE);

        if (mimetypes == null || mimetypes.isEmpty()) {
            LOGGER.info("No mimetypes supplied in the configuration file; using default values");
            this.mimetypes.putAll(DEFAULT_MIMETYPES);
        } else {
            this.mimetypes.putAll(mimetypes);
        }

        if (defaultMimetype == null || defaultMimetype.isEmpty()) {
            LOGGER.info("No default mimetype supplied in the configuration file; using default value of '{}'",
                    DEFAULT_MIMETYPE);
            this.defaultMimetype = DEFAULT_MIMETYPE;
        } else {
            this.defaultMimetype = defaultMimetype;
        }

        if (assetCacheDir == null || assetCacheDir.isEmpty()) {
            LOGGER.info("No asset cache directory supplied in the configuration file; using default value of '{}'",
                    DEFAULT_ASSET_CACHE_DIR);
            this.assetCacheDir = DEFAULT_ASSET_CACHE_DIR;
        } else {
            this.assetCacheDir = assetCacheDir;
        }

        if (clearAssetCacheOnStartup == null) {
            LOGGER.info("No setting for whether to clear the asset cache on startup supplied in the "
                    + "configuration file; using default value of '{}'", DEFAULT_CLEAR_ASSET_CACHE_ON_STARTUP);
            this.clearAssetCacheOnStartup = DEFAULT_CLEAR_ASSET_CACHE_ON_STARTUP;
        } else {
            this.clearAssetCacheOnStartup = clearAssetCacheOnStartup;
        }

        if (aceEditorModes == null || aceEditorModes.isEmpty()) {
            LOGGER.info("No editor modes supplied in the configuration file; using default values");
            this.aceEditorModes.putAll(DEFAULT_EDITOR_MODES);
        } else {
            this.aceEditorModes.putAll(aceEditorModes);
        }

        if (defaultAceEditorMode == null || defaultAceEditorMode.isEmpty()) {
            LOGGER.info("No default editor mode supplied in the configuration file; using default value of '{}'",
                    DEFAULT_ACE_EDITOR_MODE);
            this.defaultAceEditorMode = DEFAULT_ACE_EDITOR_MODE;
        } else {
            this.defaultAceEditorMode = defaultAceEditorMode;
        }
    }

    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("The largest asset upload accepted, e.g. \"50MiB\". Enforced before the "
                             + "data is streamed into the database. Note this is separate from the "
                             + "512KiB limit on editing an asset's content in the UI.")
    @JsonProperty("maxUploadSize")
    public ByteSize getMaxUploadSize() {
        return maxUploadSize;
    }

    @JsonPropertyDescription("The mimetypes map from extension to mimetype for the asset manager")
    @JsonProperty("mimetypes")
    public Map<String, String> getMimetypes() {
        return mimetypes;
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

    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("Whether to clear the asset cache on startup")
    @JsonProperty("clearAssetCacheOnStartup")
    public boolean isClearAssetCacheOnStartup() {
        return clearAssetCacheOnStartup;
    }

    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("The editor mode map from extension to mode name for the asset manager")
    @JsonProperty("aceEditorModes")
    public Map<String, String> getAceEditorModes() {
        return aceEditorModes;
    }

    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("The default editor mode if nothing else matches")
    @JsonProperty("defaultAceEditorMode")
    public String getDefaultAceEditorMode() {
        return defaultAceEditorMode;
    }

}
