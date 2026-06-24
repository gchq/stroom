/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.contentstore.impl;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * Provides configuration for the ContentStore stuff on the server.
 */
@JsonPropertyOrder(alphabetic = true)
public class ContentStoreConfig extends AbstractConfig implements IsStroomConfig {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContentStoreConfig.class);

    /**
     * Default locations where the ContentStore config is stored.
     */
    @SuppressWarnings("checkstyle:LineLength")
    static final List<String> DEFAULT_URLS = List.of(
            "https://raw.githubusercontent.com/gchq/stroom-content/refs/heads/master/source/content-store.yml");

    /**
     * List of App Store URLS
     */
    private final List<String> contentStores;

    /**
     * Default constructor. Configuration created with default values.
     */
    public ContentStoreConfig() {
        contentStores = DEFAULT_URLS;
    }

    /**
     * Constructor called when creating configuration from JSON or YAML.
     *
     * @param contentStores The list of contentstore URLs
     */
    @SuppressWarnings("unused")
    @JsonCreator
    public ContentStoreConfig(@JsonProperty("urls") final List<String> contentStores) {
        final List<String> urls = NullSafe.stream(contentStores)
                .filter(NullSafe::isNonBlankString)
                .toList();
        if (urls.isEmpty()) {
            LOGGER.debug("No Content Store URLs supplied in the configuration file; using default of '{}'",
                    DEFAULT_URLS);
            this.contentStores = DEFAULT_URLS;
        } else {
            this.contentStores = urls;
        }
    }

    /**
     * @return Where to download Content Stores from
     */
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The URLs of the Content Stores for Stroom Content")
    @JsonProperty("urls")
    public List<String> getContentStoreUrls() {
        return contentStores;
    }

    /**
     * @return debug info about this object.
     */
    @Override
    public String toString() {
        return "ContentStoreConfig { contentStoreUrls='" + contentStores + "'}";
    }
}
